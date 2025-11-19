package com.primeleague.league.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.league.LeaguePlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gerenciador de resets de temporada
 * Grug Brain: DROP PARTITION instantâneo, limpa cache
 */
public class ResetManager {

    private final LeaguePlugin plugin;

    public ResetManager(LeaguePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Reseta temporada (marca como ENDED, DROP PARTITION, limpa cache)
     * Grug Brain: DROP PARTITION é instantâneo mesmo com 50M linhas
     */
    public void resetSeason(int seasonId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     Statement stmt = conn.createStatement()) {

                    // 1. Marcar temporada como ENDED
                    try (java.sql.PreparedStatement updateStmt = conn.prepareStatement(
                        "UPDATE seasons SET status = 'ENDED' WHERE id = ?")) {
                        updateStmt.setInt(1, seasonId);
                        updateStmt.executeUpdate();
                    }

                    // 2. DROP PARTITION (instantâneo mesmo com milhões de linhas)
                    dropPartition(conn, seasonId);

                    // 3. Limpar cache agregado (league_summary)
                    clearSummaryCache(conn, seasonId);

                    // 4. Invalidar cache Caffeine (na thread principal)
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            plugin.getCacheManager().invalidateAll();
                        }
                    }.runTask(plugin);

                    plugin.getLogger().info("Temporada " + seasonId + " resetada com sucesso");

                    // 5. Criar nova temporada (se configurado)
                    if (plugin.getConfig().getBoolean("seasons.auto-create", true)) {
                        plugin.getLeagueManager().createNewSeason();
                    }

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao resetar temporada: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * DROP PARTITION da temporada
     * Grug Brain: Instantâneo mesmo com milhões de linhas
     */
    private void dropPartition(Connection conn, int seasonId) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Calcular range da partição (ex: season_id 1-1000 = league_events_1_1000)
            int partitionStart = ((seasonId - 1) / 1000) * 1000 + 1;
            int partitionEnd = partitionStart + 1000;
            String partitionName = "league_events_" + partitionStart + "_" + partitionEnd;

            // Tentar DROP PARTITION (PostgreSQL 10+)
            try {
                stmt.execute("DROP TABLE IF EXISTS " + partitionName);
                plugin.getLogger().info("Partição " + partitionName + " deletada");
            } catch (SQLException e) {
                // Se não for tabela particionada, deletar eventos diretamente
                if (e.getMessage().contains("does not exist") || e.getMessage().contains("não existe")) {
                    // Tabela não particionada - deletar eventos diretamente
                    stmt.execute("DELETE FROM league_events WHERE season_id = " + seasonId);
                    plugin.getLogger().info("Eventos da temporada " + seasonId + " deletados");
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Limpa cache agregado (league_summary)
     */
    private void clearSummaryCache(Connection conn, int seasonId) throws SQLException {
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(
            "DELETE FROM league_summary WHERE season_id = ?")) {
            stmt.setInt(1, seasonId);
            stmt.executeUpdate();
        }
    }

    /**
     * Invalida caches (Caffeine)
     */
    public void invalidateCaches() {
        plugin.getCacheManager().invalidateAll();
    }
}

