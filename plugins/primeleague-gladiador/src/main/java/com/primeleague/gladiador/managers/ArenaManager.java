package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.Arena;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Arenas do Gladiador
 * Grug Brain: Cache de arenas, delega persistência para ArenaDAO
 */
public class ArenaManager {

    private final GladiadorPlugin plugin;
    private final Map<String, Arena> arenas;
    private final SpawnPointManager spawnPointManager;
    private final ArenaDAO arenaDAO;

    public ArenaManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.arenas = new ConcurrentHashMap<>();
        this.spawnPointManager = new SpawnPointManager(plugin);
        this.arenaDAO = new ArenaDAO(plugin);
    }

    /**
     * Carrega arenas do banco
     * Grug Brain: Delega para ArenaDAO
     */
    public void loadArenas() {
        arenaDAO.loadAllArenas(arena -> {
            arenas.put(arena.getName().toLowerCase(), arena);
            spawnPointManager.loadSpawnPointsToCache(arena);
        });
    }

    /**
     * Obtém arena disponível
     */
    public Arena getAvailableArena() {
        for (Arena arena : arenas.values()) {
            if (arena.isEnabled()) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Obtém arena por nome
     */
    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    /**
     * Lista todas as arenas
     */
    public Collection<Arena> getAllArenas() {
        return arenas.values();
    }

    /**
     * Cria nova arena
     */
    public void createArena(String name, Location center) {
        if (arenas.containsKey(name.toLowerCase())) {
            throw new IllegalArgumentException("Arena já existe!");
        }

        Arena arena = new Arena(name, center.getWorld().getName(),
            center.getX(), center.getY(), center.getZ());

        // Usar centro como spectator spawn padrão
        arena.setSpectatorWorld(center.getWorld().getName());
        arena.setSpectatorX(center.getX());
        arena.setSpectatorY(center.getY() + 50);  // 50 blocos acima
        arena.setSpectatorZ(center.getZ());

        arenas.put(name.toLowerCase(), arena);

        // Salvar via ArenaDAO
        arenaDAO.createArena(name, center, arena);
    }

    /**
     * Define spawn point da arena (Único)
     * Grug Brain: Delega para SpawnPointManager
     */
    public void setSpawnPoint(int arenaId, Location loc) {
        spawnPointManager.setSpawnPoint(arenaId, loc);
    }

    /**
     * Recarrega uma arena específica do banco
     * Grug Brain: Delega para ArenaDAO
     */
    private void reloadArena(int arenaId) {
        arenaDAO.reloadArena(arenaId, arena -> {
            arenas.put(arena.getName().toLowerCase(), arena);
            spawnPointManager.loadSpawnPointsToCache(arena);
            plugin.getLogger().info("Arena '" + arena.getName() + "' recarregada com spawn point");
        });
    }

    /**
     * Obtém spawn points da arena (do cache)
     * Grug Brain: Delega para SpawnPointManager
     */
    public List<Location> getSpawnPoints(Arena arena) {
        return spawnPointManager.getSpawnPoints(arena);
    }

    /**
     * Obtém SpawnPointManager
     */
    public SpawnPointManager getSpawnPointManager() {
        return spawnPointManager;
    }

}
