package com.primeleague.x1;

import com.primeleague.x1.models.Match;
import com.primeleague.x1.models.X1Stats;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * API pública estática para outros plugins
 * Grug Brain: Métodos estáticos thread-safe, seguindo padrão CoreAPI.java e EloAPI.java
 */
public class X1API {

    private static X1Plugin getPlugin() {
        X1Plugin plugin = (X1Plugin) Bukkit.getPluginManager().getPlugin("PrimeleagueX1");
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("PrimeleagueX1 não está habilitado");
        }
        return plugin;
    }

    public static boolean isEnabled() {
        X1Plugin plugin = (X1Plugin) Bukkit.getPluginManager().getPlugin("PrimeleagueX1");
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Obtém match ativo de um player
     * @param playerUuid UUID do player
     * @return Match ou null se não estiver em match
     */
    public static Match getMatch(UUID playerUuid) {
        return getPlugin().getMatchManager().getMatch(playerUuid);
    }

    /**
     * Verifica se player está em um match
     * @param playerUuid UUID do player
     * @return true se estiver em match
     */
    public static boolean isInMatch(UUID playerUuid) {
        return getPlugin().getMatchManager().isInMatch(playerUuid);
    }

    /**
     * Obtém tamanho da queue para um kit/mode específico
     * @param queueKey Chave da queue (ex: "nodebuff_ranked")
     * @return Tamanho da queue
     */
    public static int getQueueSize(String queueKey) {
        return getPlugin().getQueueManager().getQueueSize(queueKey);
    }

    /**
     * Obtém stats de duels x1 de um player
     * @param playerUuid UUID do player
     * @return X1Stats ou null se não encontrado
     */
    public static X1Stats getStats(UUID playerUuid) {
        return getPlugin().getStatsManager().getStats(playerUuid);
    }
}

