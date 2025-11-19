package com.primeleague.league.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.league.LeaguePlugin;
import com.primeleague.league.models.LeagueEvent;
import com.primeleague.league.models.RankingEntry;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gerenciador de eventos
 * Grug Brain: Queries diretas, operações async, ON-THE-FLY calculations
 */
public class EventManager {

    private final LeaguePlugin plugin;

    public EventManager(LeaguePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Insere evento no banco (async)
     * Grug Brain: Insert direto, trigger atualiza summary automaticamente
     */
    public void insertEventAsync(int seasonId, String entityType, String entityId, String category,
                                 String action, double value, String reason, String metadataJson, UUID createdBy) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO league_events " +
                         "(season_id, entity_type, entity_id, category, action, value, reason, metadata, created_by) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)")) {

                    stmt.setInt(1, seasonId);
                    stmt.setString(2, entityType);
                    stmt.setString(3, entityId);
                    stmt.setString(4, category);
                    stmt.setString(5, action);
                    stmt.setDouble(6, value);
                    stmt.setString(7, reason);
                    if (metadataJson != null) {
                        stmt.setString(8, metadataJson);
                    } else {
                        stmt.setNull(8, Types.OTHER);
                    }
                    if (createdBy != null) {
                        stmt.setObject(9, createdBy);
                    } else {
                        stmt.setNull(9, Types.OTHER);
                    }

                    stmt.executeUpdate();
                    // Trigger atualiza league_summary automaticamente

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao inserir evento: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Conta eventos (ON-THE-FLY)
     * Grug Brain: Query direta, sem cache
     */
    public int countEvents(int seasonId, String entityType, String entityId, String category, String action) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) as count FROM league_events " +
                 "WHERE season_id = ? AND entity_type = ? AND entity_id = ? " +
                 "AND category = ? AND action = ? AND is_deleted = false")) {

            stmt.setInt(1, seasonId);
            stmt.setString(2, entityType);
            stmt.setString(3, entityId);
            stmt.setString(4, category);
            stmt.setString(5, action);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao contar eventos: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Calcula pontos totais de eventos (ON-THE-FLY)
     * Grug Brain: SUM direto, sem cache
     */
    public int calculatePointsFromEvents(int seasonId, String entityType, String entityId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COALESCE(SUM(value), 0) as total FROM league_events " +
                 "WHERE season_id = ? AND entity_type = ? AND entity_id = ? " +
                 "AND (action = 'POINTS' OR category IN ('GLADIADOR', 'KOTH', 'X1')) " +
                 "AND is_deleted = false")) {

            stmt.setInt(1, seasonId);
            stmt.setString(2, entityType);
            stmt.setString(3, entityId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return (int) rs.getDouble("total");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao calcular pontos: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Query ranking ON-THE-FLY (de eventos)
     * Grug Brain: GROUP BY direto, sem cache agregado
     */
    public List<RankingEntry> queryRankingFromEvents(int seasonId, String entityType, String category,
                                                     String action, int limit) {
        List<RankingEntry> rankings = new ArrayList<>();

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT entity_id, COUNT(*) as count " +
                 "FROM league_events " +
                 "WHERE season_id = ? AND entity_type = ? AND category = ? AND action = ? AND is_deleted = false " +
                 "GROUP BY entity_id " +
                 "ORDER BY count DESC " +
                 "LIMIT ?")) {

            stmt.setInt(1, seasonId);
            stmt.setString(2, entityType);
            stmt.setString(3, category);
            stmt.setString(4, action);
            stmt.setInt(5, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                int position = 1;
                while (rs.next()) {
                    RankingEntry entry = new RankingEntry();
                    entry.setEntityType(entityType);
                    entry.setEntityId(rs.getString("entity_id"));
                    entry.setValue(rs.getInt("count"));
                    entry.setPosition(position++);
                    rankings.add(entry);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar ranking de eventos: " + e.getMessage());
        }

        return rankings;
    }

    /**
     * Query ranking do cache (league_summary)
     * Grug Brain: Query direta do cache agregado
     */
    public List<RankingEntry> queryRankingFromSummary(int seasonId, String entityType, String metric, int limit) {
        List<RankingEntry> rankings = new ArrayList<>();

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT entity_id, points " +
                 "FROM league_summary " +
                 "WHERE season_id = ? AND entity_type = ? " +
                 "ORDER BY points DESC " +
                 "LIMIT ?")) {

            stmt.setInt(1, seasonId);
            stmt.setString(2, entityType);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                int position = 1;
                while (rs.next()) {
                    RankingEntry entry = new RankingEntry();
                    entry.setEntityType(entityType);
                    entry.setEntityId(rs.getString("entity_id"));
                    entry.setValue(rs.getInt("points"));
                    entry.setPosition(position++);
                    rankings.add(entry);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar ranking do summary: " + e.getMessage());
        }

        return rankings;
    }

    /**
     * Obtém histórico de eventos (filtros opcionais)
     */
    public List<LeagueEvent> getEventHistory(int seasonId, String entityType, String entityId,
                                            String category, String action, int limit) {
        List<LeagueEvent> events = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
            "SELECT * FROM league_events " +
            "WHERE season_id = ? AND is_deleted = false");
        List<Object> params = new ArrayList<>();
        params.add(seasonId);

        if (entityType != null) {
            sql.append(" AND entity_type = ?");
            params.add(entityType);
        }
        if (entityId != null) {
            sql.append(" AND entity_id = ?");
            params.add(entityId);
        }
        if (category != null) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (action != null) {
            sql.append(" AND action = ?");
            params.add(action);
        }

        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(limit);

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LeagueEvent event = mapResultSetToEvent(rs);
                    events.add(event);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar histórico: " + e.getMessage());
        }

        return events;
    }

    /**
     * Mapeia ResultSet para LeagueEvent
     */
    private LeagueEvent mapResultSetToEvent(ResultSet rs) throws SQLException {
        LeagueEvent event = new LeagueEvent();
        event.setId(rs.getLong("id"));
        event.setSeasonId(rs.getInt("season_id"));
        event.setEntityType(rs.getString("entity_type"));
        event.setEntityId(rs.getString("entity_id"));
        event.setCategory(rs.getString("category"));
        event.setAction(rs.getString("action"));
        event.setValue(rs.getDouble("value"));
        event.setReason(rs.getString("reason"));

        // Converter metadata JSONB
        String metadataJson = rs.getString("metadata");
        if (metadataJson != null) {
            event.metadataFromJson(metadataJson);
        }

        event.setDeleted(rs.getBoolean("is_deleted"));
        event.setCreatedAt(rs.getTimestamp("created_at"));

        UUID createdBy = (UUID) rs.getObject("created_by");
        event.setCreatedBy(createdBy);

        return event;
    }
}

