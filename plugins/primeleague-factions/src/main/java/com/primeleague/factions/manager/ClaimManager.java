package com.primeleague.factions.manager;

import com.primeleague.core.CoreAPI;
import com.primeleague.factions.PrimeFactions;
import com.primeleague.factions.util.ChunkKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all land claims.
 * Uses a ConcurrentHashMap for O(1) access.
 * Syncs with PostgreSQL.
 */
public class ClaimManager {

    private final PrimeFactions plugin;
    private final Map<ChunkKey, Integer> claimCache; // ChunkKey -> ClanID

    public ClaimManager(PrimeFactions plugin) {
        this.plugin = plugin;
        this.claimCache = new ConcurrentHashMap<>();
        loadClaims();
    }

    /**
     * Loads all claims from the database into memory.
     * Should be called on startup.
     */
    public void loadClaims() {
        plugin.getLogger().info("Carregando claims...");
        int count = 0;
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT world, x, z, clan_id FROM faction_claims")) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int z = rs.getInt("z");
                int clanId = rs.getInt("clan_id");

                claimCache.put(new ChunkKey(world, x, z), clanId);
                count++;
            }
            plugin.getLogger().info("Carregados " + count + " claims.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao carregar claims!", e);
        }
    }

    /**
     * Gets the clan ID that owns the chunk at the given location.
     *
     * @param location The location to check.
     * @return The Clan ID, or -1 if not claimed.
     */
    public int getClanAt(Location location) {
        if (location == null || location.getWorld() == null) return -1;
        return getClanAt(location.getWorld().getName(), location.getChunk().getX(), location.getChunk().getZ());
    }

    /**
     * Gets the clan ID that owns the chunk.
     *
     * @param chunk The chunk to check.
     * @return The Clan ID, or -1 if not claimed.
     */
    public int getClanAt(Chunk chunk) {
        return getClanAt(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    /**
     * Gets the clan ID that owns the chunk.
     *
     * @param world World name
     * @param x     Chunk X
     * @param z     Chunk Z
     * @return The Clan ID, or -1 if not claimed.
     */
    public int getClanAt(String world, int x, int z) {
        return claimCache.getOrDefault(new ChunkKey(world, x, z), -1);
    }

    /**
     * Claims a chunk for a clan.
     *
     * @param world  World name
     * @param x      Chunk X
     * @param z      Chunk Z
     * @param clanId Clan ID
     * @return true if successful, false if already claimed or error.
     */
    public boolean claimChunk(String world, int x, int z, int clanId) {
        ChunkKey key = new ChunkKey(world, x, z);
        if (claimCache.containsKey(key)) {
            return false; // Already claimed
        }

        // Update Cache
        claimCache.put(key, clanId);

        // Async DB Update
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO faction_claims (world, x, z, clan_id) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, world);
                stmt.setInt(2, x);
                stmt.setInt(3, z);
                stmt.setInt(4, clanId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao salvar claim no banco!", e);
                // Rollback cache if DB fails? For now, keep it simple (Grug).
                // In a perfect world we would revert, but this is rare.
            }
        });

        return true;
    }

    /**
     * Unclaims a chunk.
     *
     * @param world World name
     * @param x     Chunk X
     * @param z     Chunk Z
     * @return true if successful (was claimed), false otherwise.
     */
    public boolean unclaimChunk(String world, int x, int z) {
        ChunkKey key = new ChunkKey(world, x, z);
        if (!claimCache.containsKey(key)) {
            return false;
        }

        // Update Cache
        claimCache.remove(key);

        // Async DB Update
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM faction_claims WHERE world = ? AND x = ? AND z = ?")) {
                stmt.setString(1, world);
                stmt.setInt(2, x);
                stmt.setInt(3, z);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao remover claim do banco!", e);
            }
        });

        return true;
    }

    /**
     * Unclaims all chunks for a clan.
     *
     * @param clanId Clan ID
     */
    public void unclaimAll(int clanId) {
        // Remove from cache
        claimCache.entrySet().removeIf(entry -> entry.getValue() == clanId);

        // Async DB Update
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM faction_claims WHERE clan_id = ?")) {
                stmt.setInt(1, clanId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao remover todos os claims do clan " + clanId, e);
            }
        });
    }

    /**
     * Gets the number of claims a clan has.
     *
     * @param clanId Clan ID
     * @return Number of claims
     */
    public int getClaimCount(int clanId) {
        // This is O(N) on the map size.
        // If this becomes slow, we should maintain a separate Map<ClanID, Count>.
        // For < 10k claims, this is instant.
        int count = 0;
        for (Integer id : claimCache.values()) {
            if (id == clanId) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Gets all claims within a radius of a location.
     * Useful for /f map.
     *
     * @param center Center location
     * @param radius Radius in chunks
     * @return Set of ChunkKeys
     */
    public Set<ChunkKey> getClaimsInRadius(Location center, int radius) {
        Set<ChunkKey> claims = new HashSet<>();
        String world = center.getWorld().getName();
        int centerX = center.getChunk().getX();
        int centerZ = center.getChunk().getZ();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                ChunkKey key = new ChunkKey(world, x, z);
                if (claimCache.containsKey(key)) {
                    claims.add(key);
                }
            }
        }
        return claims;
    }
}
