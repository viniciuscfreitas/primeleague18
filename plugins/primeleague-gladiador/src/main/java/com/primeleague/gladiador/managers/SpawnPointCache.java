package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.models.Arena;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache de spawn points das arenas
 * Grug Brain: Cache thread-safe, evita queries sync
 */
public class SpawnPointCache {

    private final Map<Integer, List<Location>> spawnCache;

    public SpawnPointCache() {
        this.spawnCache = new ConcurrentHashMap<>();
    }

    /**
     * Obtém spawn points da arena (do cache)
     * Grug Brain: Retorna cópia para evitar modificação externa
     */
    public List<Location> getSpawnPoints(Arena arena) {
        if (arena == null || arena.getId() == 0) {
            return new ArrayList<>();
        }

        List<Location> cached = spawnCache.get(arena.getId());
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        return new ArrayList<>();
    }

    /**
     * Atualiza cache de spawn points
     * Grug Brain: Thread-safe, chamado na thread principal
     */
    public void updateCache(int arenaId, List<Location> spawns) {
        spawnCache.put(arenaId, spawns);
    }

    /**
     * Limpa cache
     */
    public void clear() {
        spawnCache.clear();
    }
}





