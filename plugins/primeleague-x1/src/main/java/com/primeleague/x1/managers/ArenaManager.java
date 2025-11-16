package com.primeleague.x1.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Arena;
import com.primeleague.x1.utils.X1Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Arenas
 * Grug Brain: Carrega arenas do PostgreSQL, marca como in_use/available
 */
public class ArenaManager {

    private final X1Plugin plugin;
    private final Map<String, Arena> arenas;

    public ArenaManager(X1Plugin plugin) {
        this.plugin = plugin;
        this.arenas = new ConcurrentHashMap<>();
    }

    /**
     * Carrega arenas do banco de dados
     */
    public void loadArenas() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT name, world_name, spawn1_x, spawn1_y, spawn1_z, spawn2_x, spawn2_y, spawn2_z, " +
                        "center_x, center_y, center_z, enabled, in_use FROM x1_arenas WHERE enabled = true");
                    
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        String name = rs.getString("name");
                        String worldName = rs.getString("world_name");
                        World world = Bukkit.getWorld(worldName);
                        
                        if (world == null) {
                            plugin.getLogger().warning("World não encontrado para arena " + name + ": " + worldName);
                            // Tentar carregar o world se não estiver carregado
                            try {
                                world = Bukkit.createWorld(new org.bukkit.WorldCreator(worldName));
                                if (world == null) {
                                    plugin.getLogger().warning("Não foi possível carregar world " + worldName + " - arena será desabilitada");
                                    // Marcar arena como desabilitada no banco
                                    try (Connection conn2 = CoreAPI.getDatabase().getConnection()) {
                                        PreparedStatement disableStmt = conn2.prepareStatement(
                                            "UPDATE x1_arenas SET enabled = false WHERE name = ?");
                                        disableStmt.setString(1, name);
                                        disableStmt.executeUpdate();
                                    } catch (SQLException e2) {
                                        // Ignorar erro ao desabilitar
                                    }
                                    continue;
                                }
                            } catch (Exception e) {
                                plugin.getLogger().warning("Erro ao carregar world " + worldName + ": " + e.getMessage());
                                // Marcar arena como desabilitada
                                try (Connection conn2 = CoreAPI.getDatabase().getConnection()) {
                                    PreparedStatement disableStmt = conn2.prepareStatement(
                                        "UPDATE x1_arenas SET enabled = false WHERE name = ?");
                                    disableStmt.setString(1, name);
                                    disableStmt.executeUpdate();
                                } catch (SQLException e2) {
                                    // Ignorar erro ao desabilitar
                                }
                                continue;
                            }
                        }
                        
                        Location spawn1 = new Location(world, 
                            rs.getDouble("spawn1_x"), 
                            rs.getDouble("spawn1_y"), 
                            rs.getDouble("spawn1_z"));
                        Location spawn2 = new Location(world,
                            rs.getDouble("spawn2_x"),
                            rs.getDouble("spawn2_y"),
                            rs.getDouble("spawn2_z"));
                        Location center = new Location(world,
                            rs.getDouble("center_x"),
                            rs.getDouble("center_y"),
                            rs.getDouble("center_z"));
                        
                        Arena arena = new Arena(name, worldName, spawn1, spawn2, center);
                        arena.setEnabled(rs.getBoolean("enabled"));
                        arena.setInUse(rs.getBoolean("in_use"));
                        
                        arenas.put(X1Utils.normalizeName(name), arena);
                    }
                    
                    plugin.getLogger().info("Carregadas " + arenas.size() + " arenas do banco de dados");
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao carregar arenas: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Obtém arena disponível para um kit
     */
    public Arena getAvailableArena(String kit) {
        // Buscar primeira arena disponível e não em uso
        for (Arena arena : arenas.values()) {
            if (arena.isEnabled() && !arena.isInUse()) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Marca arena como em uso
     */
    public void markArenaInUse(Arena arena) {
        arena.setInUse(true);
        updateArenaInUse(arena, true);
    }

    /**
     * Marca arena como disponível
     */
    public void markArenaAvailable(Arena arena) {
        arena.setInUse(false);
        updateArenaInUse(arena, false);
    }

    /**
     * Atualiza status in_use no banco
     */
    private void updateArenaInUse(Arena arena, boolean inUse) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE x1_arenas SET in_use = ? WHERE name = ?");
                    stmt.setBoolean(1, inUse);
                    stmt.setString(2, arena.getName());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao atualizar status da arena: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Cria nova arena (síncrono para garantir que está disponível imediatamente)
     */
    public void createArena(String name, Location spawn1, Location spawn2, Location center) {
        if (!X1Utils.isValidName(name)) {
            throw new IllegalArgumentException("Nome de arena inválido: " + name);
        }

        Arena arena = new Arena(name, spawn1.getWorld().getName(), spawn1, spawn2, center);
        
        // Adicionar ao cache imediatamente
        arenas.put(X1Utils.normalizeName(name), arena);
        
        // Salvar no banco async
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO x1_arenas (name, world_name, spawn1_x, spawn1_y, spawn1_z, " +
                        "spawn2_x, spawn2_y, spawn2_z, center_x, center_y, center_z, enabled, in_use) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (name) DO UPDATE SET " +
                        "world_name = EXCLUDED.world_name, spawn1_x = EXCLUDED.spawn1_x, " +
                        "spawn1_y = EXCLUDED.spawn1_y, spawn1_z = EXCLUDED.spawn1_z, " +
                        "spawn2_x = EXCLUDED.spawn2_x, spawn2_y = EXCLUDED.spawn2_y, " +
                        "spawn2_z = EXCLUDED.spawn2_z, center_x = EXCLUDED.center_x, " +
                        "center_y = EXCLUDED.center_y, center_z = EXCLUDED.center_z");
                    
                    stmt.setString(1, name);
                    stmt.setString(2, spawn1.getWorld().getName());
                    stmt.setDouble(3, spawn1.getX());
                    stmt.setDouble(4, spawn1.getY());
                    stmt.setDouble(5, spawn1.getZ());
                    stmt.setDouble(6, spawn2.getX());
                    stmt.setDouble(7, spawn2.getY());
                    stmt.setDouble(8, spawn2.getZ());
                    stmt.setDouble(9, center.getX());
                    stmt.setDouble(10, center.getY());
                    stmt.setDouble(11, center.getZ());
                    stmt.setBoolean(12, true);
                    stmt.setBoolean(13, false);
                    
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao criar arena: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Deleta arena
     */
    public void deleteArena(String name) {
        if (!X1Utils.isValidName(name)) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM x1_arenas WHERE name = ?");
                    stmt.setString(1, name);
                    stmt.executeUpdate();
                    
                    arenas.remove(X1Utils.normalizeName(name));
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao deletar arena: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Lista todas as arenas
     */
    public Collection<Arena> getAllArenas() {
        return arenas.values();
    }
}

