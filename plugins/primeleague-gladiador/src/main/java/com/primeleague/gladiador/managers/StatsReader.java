package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.GladiadorStats;

import java.util.List;

/**
 * Leitor de estatísticas do Gladiador
 * Grug Brain: Delega para StatsCache e StatsQuery
 */
public class StatsReader {

    private final StatsCache cache;
    private final StatsQuery query;

    public StatsReader(GladiadorPlugin plugin) {
        long cacheTtl = plugin.getConfig().getLong("cache.stats-duration", 300);
        this.cache = new StatsCache(cacheTtl);
        this.query = new StatsQuery(plugin);
    }

    /**
     * Obtém stats do clan (com cache)
     * Grug Brain: Retorna do cache se disponível, senão busca async e retorna objeto vazio temporário
     */
    public GladiadorStats getStats(int clanId) {
        GladiadorStats cached = cache.get(clanId);
        if (cached != null) {
            return cached;
        }

        // Buscar async e atualizar cache quando carregar
        query.loadStatsAsync(clanId, stats -> {
            if (stats != null) {
                cache.put(clanId, stats);
            }
        });

        // Retornar objeto vazio temporário (será atualizado quando async carregar)
        // Grug Brain: Melhor que null, mas caller deve saber que pode estar incompleto
        return new GladiadorStats(clanId);
    }

    /**
     * Obtém stats do clan (assíncrono com callback)
     * Grug Brain: Versão async que garante dados completos via callback
     */
    public void getStatsAsync(int clanId, java.util.function.Consumer<GladiadorStats> onLoaded) {
        GladiadorStats cached = cache.get(clanId);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }

        query.loadStatsAsync(clanId, stats -> {
            if (stats != null) {
                cache.put(clanId, stats);
                onLoaded.accept(stats);
            } else {
                // Se não encontrou, criar novo e cachear
                GladiadorStats newStats = new GladiadorStats(clanId);
                cache.put(clanId, newStats);
                onLoaded.accept(newStats);
            }
        });
    }

    /**
     * Obtém top clans por vitórias (assíncrono)
     * Grug Brain: Delega para StatsQuery async
     */
    public void getTopByWinsAsync(int limit, java.util.function.Consumer<List<GladiadorStats>> onLoaded) {
        query.getTopByWinsAsync(limit, onLoaded);
    }

    /**
     * Obtém top clans por pontos de temporada (assíncrono)
     * Grug Brain: Delega para StatsQuery async
     */
    public void getTopBySeasonPointsAsync(int limit, java.util.function.Consumer<List<GladiadorStats>> onLoaded) {
        query.getTopBySeasonPointsAsync(limit, onLoaded);
    }


    /**
     * Invalida cache de um clan
     */
    public void invalidateCache(int clanId) {
        cache.invalidate(clanId);
    }
}

