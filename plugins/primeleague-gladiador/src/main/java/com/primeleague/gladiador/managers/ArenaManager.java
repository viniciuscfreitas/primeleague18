package com.primeleague.gladiador.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Arenas do Gladiador
 * Grug Brain: CRUD direto no PostgreSQL, spawn points como JSONB
 */
public class ArenaManager {

    private final GladiadorPlugin plugin;
    private final Map<String, Arena> arenas;
    private final Map<Integer, List<Location>> spawnCache; // Cache de spawn points

    public ArenaManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.arenas = new ConcurrentHashMap<>();
        this.spawnCache = new ConcurrentHashMap<>();
    }

    /**
     * Carrega arenas do banco
     */
    public void loadArenas() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM gladiador_arenas WHERE enabled = true")) {

                    ResultSet rs = stmt.executeQuery();
                    int count = 0;
                    while (rs.next()) {
                        Arena arena = mapResultSetToArena(rs);
                        if (arena != null) {
                            arenas.put(arena.getName().toLowerCase(), arena);
                            // Carregar spawn points para cache
                            loadSpawnPointsToCache(arena);
                            count++;
                        }
                    }

                    plugin.getLogger().info("Carregadas " + count + " arenas do Gladiador");
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao carregar arenas: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Obtém arena disponível
     */
    public Arena getAvailableArena() {
        for (Arena arena : arenas.values()) {
            if (arena.isEnabled()) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Obtém arena por nome
     */
    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    /**
     * Lista todas as arenas
     */
    public Collection<Arena> getAllArenas() {
        return arenas.values();
    }

    /**
     * Cria nova arena
     */
    public void createArena(String name, Location center) {
        if (arenas.containsKey(name.toLowerCase())) {
            throw new IllegalArgumentException("Arena já existe!");
        }

        Arena arena = new Arena(name, center.getWorld().getName(),
            center.getX(), center.getY(), center.getZ());

        // Usar centro como spectator spawn padrão
        arena.setSpectatorWorld(center.getWorld().getName());
        arena.setSpectatorX(center.getX());
        arena.setSpectatorY(center.getY() + 50);  // 50 blocos acima
        arena.setSpectatorZ(center.getZ());

        arenas.put(name.toLowerCase(), arena);

        // Salvar async
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO gladiador_arenas (name, world, center_x, center_y, center_z, " +
                         "spectator_world, spectator_x, spectator_y, spectator_z, spectator_yaw, spectator_pitch) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)")) {

                    stmt.setString(1, name);
                    stmt.setString(2, arena.getWorld());
                    stmt.setDouble(3, arena.getCenterX());
                    stmt.setDouble(4, arena.getCenterY());
                    stmt.setDouble(5, arena.getCenterZ());
                    stmt.setString(6, arena.getSpectatorWorld());
                    stmt.setDouble(7, arena.getSpectatorX());
                    stmt.setDouble(8, arena.getSpectatorY());
                    stmt.setDouble(9, arena.getSpectatorZ());
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao criar arena: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Define spawn point da arena (Único)
     * Grug Brain: Salva async mas com verificação imediata no getSpawnPoints
     */
    public void setSpawnPoint(int arenaId, Location loc) {
        // Salvar de forma assíncrona (não bloqueia thread principal)
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    // Criar JSON array com um único spawn
                    JSONArray spawns = new JSONArray();
                    JSONObject newSpawn = new JSONObject();
                    newSpawn.put("x", loc.getX());
                    newSpawn.put("y", loc.getY());
                    newSpawn.put("z", loc.getZ());
                    newSpawn.put("yaw", (double) loc.getYaw());
                    newSpawn.put("pitch", (double) loc.getPitch());
                    spawns.add(newSpawn);

                    // Atualizar
                    try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE gladiador_arenas SET spawn_points = ?::jsonb WHERE id = ?")) {
                        stmt.setString(1, spawns.toJSONString());
                        stmt.setInt(2, arenaId);
                        int updated = stmt.executeUpdate();

                        if (updated > 0) {
                            plugin.getLogger().info("Spawn point salvo para arena ID " + arenaId);
                            // Recarregar arena em cache
                            reloadArena(arenaId);
                        } else {
                            plugin.getLogger().warning("Nenhuma arena atualizada ao salvar spawn (ID: " + arenaId + ")");
                        }
                    }

                } catch (Exception e) {
                    plugin.getLogger().severe("Erro ao definir spawn: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Recarrega uma arena específica do banco
     */
    private void reloadArena(int arenaId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM gladiador_arenas WHERE id = ?")) {

                    stmt.setInt(1, arenaId);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        Arena arena = mapResultSetToArena(rs);
                        if (arena != null) {
                            arenas.put(arena.getName().toLowerCase(), arena);
                            // Recarregar spawn points no cache
                            loadSpawnPointsToCache(arena);
                            plugin.getLogger().info("Arena '" + arena.getName() + "' recarregada com spawn point");
                        }
                    }

                } catch (SQLException e) {
                    plugin.getLogger().warning("Erro ao recarregar arena: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Define world border inicial
     */
    public void setWorldBorder(Arena arena, int size) {
        World world = Bukkit.getWorld(arena.getWorld());
        if (world == null) return;

        org.bukkit.WorldBorder border = world.getWorldBorder();
        Location center = new Location(world, arena.getCenterX(), arena.getCenterY(), arena.getCenterZ());
        border.setCenter(center);
        border.setSize(size);
    }

    /**
     * Reduz world border gradualmente
     */
    public void shrinkBorder(Arena arena, int size, long timeSeconds) {
        World world = Bukkit.getWorld(arena.getWorld());
        if (world == null) return;

        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setSize(size, timeSeconds);
    }

    /**
     * Obtém spawn points da arena (do cache)
     * Grug Brain: Cache evita queries sync na thread principal
     */
    public List<Location> getSpawnPoints(Arena arena) {
        if (arena == null || arena.getId() == 0) {
            plugin.getLogger().warning("Arena inválida ao buscar spawn points");
            return new ArrayList<>();
        }

        // Retornar do cache (thread-safe)
        List<Location> cached = spawnCache.get(arena.getId());
        if (cached != null) {
            return new ArrayList<>(cached); // Retornar cópia para evitar modificação externa
        }

        // Se não está em cache, retornar vazio (será carregado async)
        return new ArrayList<>();
    }

    /**
     * Carrega spawn points para cache (async)
     * Grug Brain: Query async, atualiza cache na thread principal
     */
    private void loadSpawnPointsToCache(Arena arena) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "SELECT spawn_points FROM gladiador_arenas WHERE id = ?")) {

                    stmt.setInt(1, arena.getId());
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        String json = rs.getString("spawn_points");
                        List<Location> spawns = parseSpawnPoints(json, arena.getWorld());

                        // Atualizar cache na thread principal
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                spawnCache.put(arena.getId(), spawns);
                            }
                        }.runTask(plugin);
                    }

                } catch (Exception e) {
                    plugin.getLogger().warning("Erro ao carregar spawn points para cache: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Parse JSON de spawn points para List<Location>
     */
    private List<Location> parseSpawnPoints(String json, String worldName) {
        List<Location> spawns = new ArrayList<>();

        if (json == null || json.trim().isEmpty() || json.equals("[]") || json.equals("null")) {
            return spawns;
        }

        try {
            JSONParser parser = new JSONParser();
            Object parsed = parser.parse(json);
            if (!(parsed instanceof JSONArray)) {
                return spawns;
            }

            JSONArray array = (JSONArray) parsed;
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return spawns;
            }

            for (Object obj : array) {
                if (obj instanceof JSONObject) {
                    JSONObject spawn = (JSONObject) obj;
                    double x = ((Number) spawn.get("x")).doubleValue();
                    double y = ((Number) spawn.get("y")).doubleValue();
                    double z = ((Number) spawn.get("z")).doubleValue();
                    float yaw = ((Number) spawn.get("yaw")).floatValue();
                    float pitch = ((Number) spawn.get("pitch")).floatValue();

                    Location loc = new Location(world, x, y, z, yaw, pitch);
                    spawns.add(loc);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao parsear spawn points JSON: " + e.getMessage());
        }

        return spawns;
    }

    /**
     * Mapeia ResultSet para Arena
     */
    private Arena mapResultSetToArena(ResultSet rs) throws SQLException {
        Arena arena = new Arena();
        arena.setId(rs.getInt("id"));
        arena.setName(rs.getString("name"));
        arena.setWorld(rs.getString("world"));
        arena.setCenterX(rs.getDouble("center_x"));
        arena.setCenterY(rs.getDouble("center_y"));
        arena.setCenterZ(rs.getDouble("center_z"));
        arena.setInitialBorderSize(rs.getInt("initial_border_size"));
        arena.setFinalBorderSize(rs.getInt("final_border_size"));
        arena.setSpectatorWorld(rs.getString("spectator_world"));
        arena.setSpectatorX(rs.getDouble("spectator_x"));
        arena.setSpectatorY(rs.getDouble("spectator_y"));
        arena.setSpectatorZ(rs.getDouble("spectator_z"));
        arena.setSpectatorYaw(rs.getFloat("spectator_yaw"));
        arena.setSpectatorPitch(rs.getFloat("spectator_pitch"));
        arena.setEnabled(rs.getBoolean("enabled"));

        return arena;
    }
}
