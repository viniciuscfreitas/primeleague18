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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all land claims.
 * Uses a ConcurrentHashMap for O(1) access.
 * Syncs with PostgreSQL.
 * Grug Brain: Rastreia builds solo em cache (sem DB extra).
 */
public class ClaimManager {

    private final PrimeFactions plugin;
    private final Map<ChunkKey, Integer> claimCache; // ChunkKey -> ClanID
    // Rastreia chunks onde solo players buildaram (ChunkKey -> UUID do player)
    private final Map<ChunkKey, UUID> soloBuildCache;
    // Contador O(1) de claims por clã (otimização)
    private final Map<Integer, Integer> clanClaimCount;

    public ClaimManager(PrimeFactions plugin) {
        this.plugin = plugin;
        this.claimCache = new ConcurrentHashMap<>();
        this.soloBuildCache = new ConcurrentHashMap<>();
        this.clanClaimCount = new ConcurrentHashMap<>();
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

                ChunkKey key = new ChunkKey(world, x, z);
                claimCache.put(key, clanId);
                // Atualizar contador O(1)
                clanClaimCount.merge(clanId, 1, Integer::sum);
                count++;
            }
            plugin.getLogger().info("Carregados " + count + " claims.");

            // Carregar todos os claims no Dynmap (após cache estar pronto)
            // Delay de 2 segundos para garantir que DynmapIntegration.setup() já foi chamado
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getDynmapIntegration() != null && plugin.getDynmapIntegration().isEnabled()) {
                    int dynmapCount = 0;
                    for (Map.Entry<ChunkKey, Integer> entry : claimCache.entrySet()) {
                        plugin.getDynmapIntegration().updateClaim(entry.getKey(), entry.getValue());
                        dynmapCount++;
                    }
                    plugin.getLogger().info("Dynmap: " + dynmapCount + " claims carregados no mapa!");
                }
            }, 40L); // 2 segundos após onEnable (garante que Dynmap está pronto)
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
        // Atualizar contador O(1)
        clanClaimCount.merge(clanId, 1, Integer::sum);

        // Notify Dynmap (async, não bloqueia)
        if (plugin.getDynmapIntegration() != null && plugin.getDynmapIntegration().isEnabled()) {
            plugin.getDynmapIntegration().updateClaim(key, clanId);
        }

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
        Integer clanId = claimCache.get(key);
        if (clanId == null) {
            return false;
        }

        // Update Cache
        claimCache.remove(key);
        // Atualizar contador O(1)
        clanClaimCount.computeIfPresent(clanId, (k, v) -> v > 0 ? v - 1 : 0);

        // Notify Dynmap (async, não bloqueia)
        if (plugin.getDynmapIntegration() != null && plugin.getDynmapIntegration().isEnabled()) {
            plugin.getDynmapIntegration().removeClaim(key);
        }

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
        // Notify Dynmap (remove todos os markers do clã)
        if (plugin.getDynmapIntegration() != null && plugin.getDynmapIntegration().isEnabled()) {
            plugin.getDynmapIntegration().removeClanClaims(clanId);
        }

        // Remove from cache
        claimCache.entrySet().removeIf(entry -> entry.getValue() == clanId);
        // Limpar contador
        clanClaimCount.remove(clanId);

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
     * O(1) - usa contador em memória
     *
     * @param clanId Clan ID
     * @return Number of claims
     */
    public int getClaimCount(int clanId) {
        return clanClaimCount.getOrDefault(clanId, 0);
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

    /**
     * Rastreia chunk onde solo player buildou
     * Grug Brain: Cache em memória, sem DB (simples e direto)
     */
    public void trackSoloBuild(String world, int x, int z, UUID playerUuid) {
        ChunkKey key = new ChunkKey(world, x, z);
        // Só rastreia se chunk não está claimado
        if (!claimCache.containsKey(key)) {
            soloBuildCache.put(key, playerUuid);
        }
    }

    /**
     * Obtém chunks onde solo player buildou
     * Grug Brain: Retorna Set de ChunkKeys para auto-claim
     */
    public Set<ChunkKey> getSoloBuilds(UUID playerUuid) {
        Set<ChunkKey> builds = new HashSet<>();
        for (Map.Entry<ChunkKey, UUID> entry : soloBuildCache.entrySet()) {
            if (entry.getValue().equals(playerUuid)) {
                builds.add(entry.getKey());
            }
        }
        return builds;
    }

    /**
     * Auto-claima chunks de um solo player para um clan
     * Grug Brain: Quando player entra em clan, transfere seus builds
     * @return Array com [claimedCount, skippedCount]
     */
    public int[] autoClaimSoloBuilds(UUID playerUuid, int clanId, int maxChunks) {
        Set<ChunkKey> soloBuilds = getSoloBuilds(playerUuid);
        int claimedCount = 0;
        int skippedCount = 0;

        for (ChunkKey chunkKey : soloBuilds) {
            // Limite de chunks (evita clãs inchados)
            if (maxChunks > 0 && claimedCount >= maxChunks) {
                break;
            }

            // Verificar se chunk já está claimado (evita tentar claimar novamente)
            if (claimCache.containsKey(chunkKey)) {
                skippedCount++;
                // Remove do cache de solo builds (não é mais solo)
                soloBuildCache.remove(chunkKey);
                continue;
            }

            // Claima chunk para o clan
            // claimChunk() já verifica se está claimado, mas verificamos antes para evitar overhead
            if (claimChunk(chunkKey.getWorld(), chunkKey.getX(), chunkKey.getZ(), clanId)) {
                claimedCount++;
                // Remove do cache de solo builds (agora é claim do clan)
                soloBuildCache.remove(chunkKey);
            } else {
                // Se claimChunk retornou false, chunk foi claimado por outro processo (race condition rara)
                skippedCount++;
                soloBuildCache.remove(chunkKey);
            }
        }

        return new int[]{claimedCount, skippedCount};
    }

    /**
     * Overload sem limite (compatibilidade)
     */
    public int autoClaimSoloBuilds(UUID playerUuid, int clanId) {
        int[] result = autoClaimSoloBuilds(playerUuid, clanId, 0); // 0 = sem limite
        return result[0];
    }
}

