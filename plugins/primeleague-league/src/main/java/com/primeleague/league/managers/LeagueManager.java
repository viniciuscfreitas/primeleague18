package com.primeleague.league.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.league.LeaguePlugin;
import com.primeleague.league.models.Season;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.Calendar;

/**
 * Gerenciador de temporadas
 * Grug Brain: Queries diretas, cache simples, uma temporada ativa por vez
 */
public class LeagueManager {

    private final LeaguePlugin plugin;
    private Season currentSeason;
    private long seasonCacheTimestamp;
    private static final long CACHE_TTL_MILLIS = 60000; // 1 minuto

    public LeagueManager(LeaguePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Obtém temporada ativa (com cache)
     * Grug Brain: Cache simples com TTL
     */
    public Season getCurrentSeason() {
        // Verificar cache
        if (currentSeason != null && System.currentTimeMillis() - seasonCacheTimestamp < CACHE_TTL_MILLIS) {
            if (currentSeason.isActive()) {
                return currentSeason;
            }
        }

        // Cache expirado ou temporada não ativa - buscar do banco
        loadCurrentSeason();
        return currentSeason;
    }

    /**
     * Carrega temporada ativa do banco
     */
    private void loadCurrentSeason() {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM seasons WHERE status = 'ACTIVE' ORDER BY start_date DESC LIMIT 1")) {

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    currentSeason = mapResultSetToSeason(rs);
                    seasonCacheTimestamp = System.currentTimeMillis();
                } else {
                    currentSeason = null;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar temporada ativa: " + e.getMessage());
            currentSeason = null;
        }
    }

    /**
     * Cria nova temporada automaticamente
     * Grug Brain: Query direta, nome formatado
     */
    public void createNewSeason() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    // Calcular datas
                    Calendar cal = Calendar.getInstance();
                    Timestamp startDate = new Timestamp(cal.getTimeInMillis());

                    int durationDays = plugin.getConfig().getInt("seasons.duration-days", 90);
                    cal.add(Calendar.DAY_OF_YEAR, durationDays);
                    Timestamp endDate = new Timestamp(cal.getTimeInMillis());

                    // Gerar nome da temporada
                    String nameFormat = plugin.getConfig().getString("seasons.name-format", "Temporada {year}-Q{quarter}");
                    int year = cal.get(Calendar.YEAR);
                    int quarter = (cal.get(Calendar.MONTH) / 3) + 1;
                    String name = nameFormat
                        .replace("{year}", String.valueOf(year))
                        .replace("{quarter}", String.valueOf(quarter));

                    // Inserir temporada
                    try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO seasons (name, start_date, end_date, status) " +
                        "VALUES (?, ?, ?, 'ACTIVE') RETURNING id")) {
                        stmt.setString(1, name);
                        stmt.setTimestamp(2, startDate);
                        stmt.setTimestamp(3, endDate);

                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                int seasonId = rs.getInt("id");
                                plugin.getLogger().info("Nova temporada criada: " + name + " (ID: " + seasonId + ")");

                                // Invalidar cache
                                currentSeason = null;
                                seasonCacheTimestamp = 0;

                                // Carregar nova temporada
                                loadCurrentSeason();
                            }
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao criar temporada: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Marca temporada como ENDED
     */
    public void markSeasonAsEnded(int seasonId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE seasons SET status = 'ENDED' WHERE id = ?")) {
                    stmt.setInt(1, seasonId);
                    stmt.executeUpdate();

                    // Invalidar cache
                    currentSeason = null;
                    seasonCacheTimestamp = 0;
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao marcar temporada como ENDED: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Verifica se temporada expirou e cria nova se necessário
     */
    public void checkAndCreateNewSeason() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Season season = getCurrentSeason();
                if (season == null) {
                    // Não há temporada ativa - criar nova
                    createNewSeason();
                    return;
                }

                // Verificar se temporada expirou
                Timestamp now = new Timestamp(System.currentTimeMillis());
                if (season.getEndDate().before(now)) {
                    // Temporada expirada - marcar como ENDED e criar nova
                    markSeasonAsEnded(season.getId());
                    createNewSeason();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Mapeia ResultSet para Season
     */
    private Season mapResultSetToSeason(ResultSet rs) throws SQLException {
        Season season = new Season();
        season.setId(rs.getInt("id"));
        season.setName(rs.getString("name"));
        season.setStartDate(rs.getTimestamp("start_date"));
        season.setEndDate(rs.getTimestamp("end_date"));
        season.setStatus(rs.getString("status"));
        season.setCreatedAt(rs.getTimestamp("created_at"));
        return season;
    }
}

