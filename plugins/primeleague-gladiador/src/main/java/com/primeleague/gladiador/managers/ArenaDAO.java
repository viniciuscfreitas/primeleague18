package com.primeleague.gladiador.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.Arena;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;

/**
 * DAO de arenas (acesso ao banco)
 * Grug Brain: Queries async, mapeamento direto
 */
public class ArenaDAO {

    private final GladiadorPlugin plugin;

    public ArenaDAO(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Carrega todas as arenas do banco
     * Grug Brain: Query async, retorna via callback
     */
    public void loadAllArenas(java.util.function.Consumer<Arena> onArenaLoaded) {
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
                            onArenaLoaded.accept(arena);
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
     * Cria nova arena no banco
     * Grug Brain: Query async
     */
    public void createArena(String name, Location center, Arena arena) {
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
     * Recarrega uma arena específica do banco
     */
    public void reloadArena(int arenaId, java.util.function.Consumer<Arena> onArenaLoaded) {
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
                            onArenaLoaded.accept(arena);
                        }
                    }

                } catch (SQLException e) {
                    plugin.getLogger().warning("Erro ao recarregar arena: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
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





