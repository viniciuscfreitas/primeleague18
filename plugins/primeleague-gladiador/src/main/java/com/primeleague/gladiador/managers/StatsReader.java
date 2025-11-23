package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.GladiadorStats;

import java.util.List;

/**
 * Leitor de estatísticas do Gladiador
 * Grug Brain: Delega para StatsCache e StatsQuery
 */
public class StatsReader {

    private final GladiadorPlugin plugin;
    private final StatsCache cache;
    private final StatsQuery query;

    public StatsReader(GladiadorPlugin plugin) {
        this.plugin = plugin;
        long cacheTtl = plugin.getConfig().getLong("cache.stats-duration", 300);
        this.cache = new StatsCache(cacheTtl);
        this.query = new StatsQuery(plugin);
    }

    /**
     * Obtém stats do clan (com cache)
     * Grug Brain: Retorna do cache se disponível, senão busca async
     */
    public GladiadorStats getStats(int clanId) {
        GladiadorStats cached = cache.get(clanId);
        if (cached != null) {
            return cached;
        }

        query.loadStatsAsync(clanId, stats -> cache.put(clanId, stats));
        return cache.get(clanId) != null ? cache.get(clanId) : new GladiadorStats(clanId);
    }

    /**
     * Obtém top clans por vitórias
     * Grug Brain: Delega para StatsQuery
     */
    public List<GladiadorStats> getTopByWins(int limit) {
        return query.getTopByWins(limit);
    }

    /**
     * Invalida cache de um clan
     */
    public void invalidateCache(int clanId) {
        cache.invalidate(clanId);
    }
}

