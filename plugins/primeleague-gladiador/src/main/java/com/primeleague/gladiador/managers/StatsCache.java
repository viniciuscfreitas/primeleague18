package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.models.GladiadorStats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache de estatísticas do Gladiador
 * Grug Brain: Cache thread-safe com TTL simples
 */
public class StatsCache {

    private final Map<Integer, GladiadorStats> cache;
    private final Map<Integer, Long> cacheTimestamps;
    private final long CACHE_TTL_MILLIS;

    public StatsCache(long cacheTtlSeconds) {
        this.cache = new ConcurrentHashMap<>();
        this.cacheTimestamps = new ConcurrentHashMap<>();
        this.CACHE_TTL_MILLIS = cacheTtlSeconds * 1000;
    }

    /**
     * Obtém stats do cache se válido
     * Grug Brain: Retorna null se expirado ou não existe
     */
    public GladiadorStats get(int clanId) {
        GladiadorStats cached = cache.get(clanId);
        Long timestamp = cacheTimestamps.get(clanId);

        if (cached != null && timestamp != null) {
            long age = System.currentTimeMillis() - timestamp;
            if (age < CACHE_TTL_MILLIS) {
                return cached;
            }
        }

        return null;
    }

    /**
     * Atualiza cache
     * Grug Brain: Thread-safe, atualiza timestamp
     */
    public void put(int clanId, GladiadorStats stats) {
        cache.put(clanId, stats);
        cacheTimestamps.put(clanId, System.currentTimeMillis());
    }

    /**
     * Invalida cache de um clan
     * Grug Brain: Remove do cache
     */
    public void invalidate(int clanId) {
        cache.remove(clanId);
        cacheTimestamps.remove(clanId);
    }
}





