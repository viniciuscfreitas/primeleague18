package com.primeleague.gladiador.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.GladiadorStats;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Queries de estatísticas do Gladiador
 * Grug Brain: Queries diretas, async quando apropriado
 */
public class StatsQuery {

    private final GladiadorPlugin plugin;

    public StatsQuery(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Carrega stats do banco de forma assíncrona
     * Grug Brain: Query async, callback na thread principal
     */
    public void loadStatsAsync(int clanId, java.util.function.Consumer<GladiadorStats> onLoaded) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM gladiador_stats WHERE clan_id = ?")) {

                    stmt.setInt(1, clanId);
                    ResultSet rs = stmt.executeQuery();

                    GladiadorStats stats;
                    if (rs.next()) {
                        stats = mapResultSetToStats(rs);
                    } else {
                        createStatsIfNotExists(clanId);
                        stats = new GladiadorStats(clanId);
                    }

                    final GladiadorStats finalStats = stats;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            onLoaded.accept(finalStats);
                        }
                    }.runTask(plugin);

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao buscar stats: " + e.getMessage());
                    onLoaded.accept(new GladiadorStats(clanId));
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Obtém top clans por vitórias (assíncrono)
     * Grug Brain: Query async com callback na thread principal
     */
    public void getTopByWinsAsync(int limit, java.util.function.Consumer<List<GladiadorStats>> onLoaded) {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<GladiadorStats> stats = new ArrayList<>();

                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM gladiador_stats ORDER BY wins DESC, total_kills DESC LIMIT ?")) {

                    stmt.setInt(1, limit);
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        stats.add(mapResultSetToStats(rs));
                    }

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao buscar top: " + e.getMessage());
                }

                final List<GladiadorStats> finalStats = stats;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        onLoaded.accept(finalStats);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Obtém top clans por pontos de temporada (assíncrono)
     * Grug Brain: Query async com callback na thread principal
     */
    public void getTopBySeasonPointsAsync(int limit, java.util.function.Consumer<List<GladiadorStats>> onLoaded) {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<GladiadorStats> stats = new ArrayList<>();

                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM gladiador_stats ORDER BY season_points DESC, wins DESC LIMIT ?")) {

                    stmt.setInt(1, limit);
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        stats.add(mapResultSetToStats(rs));
                    }

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao buscar tabela: " + e.getMessage());
                }

                final List<GladiadorStats> finalStats = stats;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        onLoaded.accept(finalStats);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }


    /**
     * Cria stats se não existir
     */
    private void createStatsIfNotExists(int clanId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO gladiador_stats (clan_id) VALUES (?) ON CONFLICT (clan_id) DO NOTHING")) {

            stmt.setInt(1, clanId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().warning("Erro ao criar stats: " + e.getMessage());
        }
    }

    /**
     * Mapeia ResultSet para GladiadorStats
     */
    private GladiadorStats mapResultSetToStats(ResultSet rs) throws SQLException {
        GladiadorStats stats = new GladiadorStats();
        stats.setClanId(rs.getInt("clan_id"));
        stats.setWins(rs.getInt("wins"));
        stats.setParticipations(rs.getInt("participations"));
        stats.setTotalKills(rs.getInt("total_kills"));
        stats.setTotalDeaths(rs.getInt("total_deaths"));

        // season_points pode não existir em instalações antigas
        try {
            stats.setSeasonPoints(rs.getInt("season_points"));
        } catch (SQLException e) {
            stats.setSeasonPoints(0);
        }

        Timestamp lastWin = rs.getTimestamp("last_win");
        if (lastWin != null) {
            stats.setLastWin(new java.util.Date(lastWin.getTime()));
        }

        return stats;
    }
}




