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
import java.util.List;

/**
 * DAO de spawn points (acesso ao banco)
 * Grug Brain: Queries async, parse JSON simples
 */
public class SpawnPointDAO {

    private final GladiadorPlugin plugin;
    private final SpawnPointCache cache;

    public SpawnPointDAO(GladiadorPlugin plugin, SpawnPointCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    /**
     * Salva spawn point no banco
     * Grug Brain: Salva async, atualiza cache depois
     * Nota: json-simple não é genérico, mas é compatível com 1.8.8
     */
    @SuppressWarnings("unchecked")
    public void saveSpawnPoint(int arenaId, Location loc) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    JSONArray spawns = new JSONArray();
                    JSONObject newSpawn = new JSONObject();
                    newSpawn.put("x", loc.getX());
                    newSpawn.put("y", loc.getY());
                    newSpawn.put("z", loc.getZ());
                    newSpawn.put("yaw", (double) loc.getYaw());
                    newSpawn.put("pitch", (double) loc.getPitch());
                    spawns.add(newSpawn);

                    try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE gladiador_arenas SET spawn_points = ?::jsonb WHERE id = ?")) {
                        stmt.setString(1, spawns.toJSONString());
                        stmt.setInt(2, arenaId);
                        int updated = stmt.executeUpdate();

                        if (updated > 0) {
                            plugin.getLogger().info("Spawn point salvo para arena ID " + arenaId);
                            reloadSpawnPoints(arenaId);
                        } else {
                            plugin.getLogger().warning("Nenhuma arena atualizada ao salvar spawn (ID: " + arenaId + ")");
                        }
                    }

                } catch (Exception e) {
                    plugin.getLogger().severe("Erro ao definir spawn: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Carrega spawn points do banco e atualiza cache
     * Grug Brain: Query async, atualiza cache na thread principal
     */
    public void loadSpawnPoints(Arena arena) {
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

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                cache.updateCache(arena.getId(), spawns);
                            }
                        }.runTask(plugin);
                    }

                } catch (Exception e) {
                    plugin.getLogger().warning("Erro ao carregar spawn points: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Recarrega spawn points de uma arena
     */
    private void reloadSpawnPoints(int arenaId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "SELECT spawn_points, world FROM gladiador_arenas WHERE id = ?")) {

                    stmt.setInt(1, arenaId);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        String json = rs.getString("spawn_points");
                        String worldName = rs.getString("world");
                        List<Location> spawns = parseSpawnPoints(json, worldName);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                cache.updateCache(arenaId, spawns);
                            }
                        }.runTask(plugin);
                    }

                } catch (Exception e) {
                    plugin.getLogger().warning("Erro ao recarregar spawn points: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Parse JSON de spawn points para List<Location>
     * Grug Brain: Parse simples, validação básica
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
}





