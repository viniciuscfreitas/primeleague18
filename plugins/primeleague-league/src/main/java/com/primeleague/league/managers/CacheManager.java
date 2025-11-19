package com.primeleague.league.managers;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.primeleague.league.LeaguePlugin;

import java.util.concurrent.TimeUnit;

/**
 * Gerenciador de cache Caffeine
 * Grug Brain: Cache automático com TTL, thread-safe por padrão
 */
public class CacheManager {

    private final LeaguePlugin plugin;
    private final LoadingCache<String, Integer> pointsCache;

    public CacheManager(LeaguePlugin plugin) {
        this.plugin = plugin;

        // Cache de pontos (TTL 45s, max 10k entidades)
        int ttlSeconds = plugin.getConfig().getInt("cache.points-ttl-seconds", 45);
        int maxSize = plugin.getConfig().getInt("cache.points-max-size", 10000);

        pointsCache = Caffeine.newBuilder()
            .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
            .maximumSize(maxSize)
            .build(key -> {
                // Parse key: "seasonId:entityType:entityId"
                String[] parts = key.split(":");
                if (parts.length != 3) {
                    return 0;
                }

                int seasonId = Integer.parseInt(parts[0]);
                String entityType = parts[1];
                String entityId = parts[2];

                // Tentar cache agregado primeiro (league_summary)
                Integer summaryPoints = getSummaryCache(seasonId, entityType, entityId);
                if (summaryPoints != null) {
                    return summaryPoints;
                }

                // Calcular ON-THE-FLY se cache não disponível
                return plugin.getEventManager().calculatePointsFromEvents(seasonId, entityType, entityId);
            });
    }

    /**
     * Obtém pontos do cache (ou calcula se não estiver em cache)
     */
    public int getPoints(int seasonId, String entityType, String entityId) {
        String cacheKey = seasonId + ":" + entityType + ":" + entityId;
        return pointsCache.get(cacheKey);
    }

    /**
     * Invalida cache de pontos
     */
    public void invalidatePoints(int seasonId, String entityType, String entityId) {
        String cacheKey = seasonId + ":" + entityType + ":" + entityId;
        pointsCache.invalidate(cacheKey);
    }

    /**
     * Invalida todo o cache
     */
    public void invalidateAll() {
        pointsCache.invalidateAll();
    }

    /**
     * Fecha cache (cleanup)
     */
    public void close() {
        if (pointsCache != null) {
            pointsCache.invalidateAll();
        }
    }

    /**
     * Obtém pontos do cache agregado (league_summary)
     * Grug Brain: Query direta, sem cache adicional
     */
    private Integer getSummaryCache(int seasonId, String entityType, String entityId) {
        try (java.sql.Connection conn = com.primeleague.core.CoreAPI.getDatabase().getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(
                 "SELECT points FROM league_summary " +
                 "WHERE season_id = ? AND entity_type = ? AND entity_id = ?")) {

            stmt.setInt(1, seasonId);
            stmt.setString(2, entityType);
            stmt.setString(3, entityId);

            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("points");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao buscar summary cache: " + e.getMessage());
        }
        return null;
    }
}

