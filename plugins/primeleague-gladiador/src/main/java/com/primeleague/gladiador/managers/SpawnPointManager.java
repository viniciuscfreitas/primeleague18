package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.Arena;
import org.bukkit.Location;

import java.util.List;

/**
 * Gerenciador de spawn points das arenas
 * Grug Brain: Delega para SpawnPointCache e SpawnPointDAO
 */
public class SpawnPointManager {

    private final SpawnPointCache cache;
    private final SpawnPointDAO dao;

    public SpawnPointManager(GladiadorPlugin plugin) {
        this.cache = new SpawnPointCache();
        this.dao = new SpawnPointDAO(plugin, cache);
    }

    /**
     * Obtém spawn points da arena (do cache)
     * Grug Brain: Delega para SpawnPointCache
     */
    public List<Location> getSpawnPoints(Arena arena) {
        return cache.getSpawnPoints(arena);
    }

    /**
     * Define spawn point da arena
     * Grug Brain: Delega para SpawnPointDAO
     */
    public void setSpawnPoint(int arenaId, Location loc) {
        dao.saveSpawnPoint(arenaId, loc);
    }

    /**
     * Carrega spawn points para cache
     * Grug Brain: Delega para SpawnPointDAO
     */
    public void loadSpawnPointsToCache(Arena arena) {
        dao.loadSpawnPoints(arena);
    }

    /**
     * Limpa cache
     */
    public void clearCache() {
        cache.clear();
    }
}

