package com.primeleague.clans;

import com.primeleague.clans.commands.ClanCommand;
import com.primeleague.clans.integrations.ClansPlaceholderExpansion;
import com.primeleague.clans.listeners.ClanChatListener;
import com.primeleague.clans.listeners.ClanStatsListener;
import com.primeleague.clans.managers.ClansManager;
import com.primeleague.core.CoreAPI;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin de Clans - Primeleague
 * Grug Brain: Plugin simples, depende do Core via CoreAPI
 */
public class ClansPlugin extends JavaPlugin {

    private static ClansPlugin instance;
     * Obtém cache de top ou null se expirado
     */
    public TopCache getTopCache(String type) {
        TopCache cache = topCache.get(type);
        if (cache != null && System.currentTimeMillis() - cache.getTimestamp() < topCacheDuration) {
            return cache;
        }
        return null;
    }

    /**
     * Define cache de top
     */
    public void setTopCache(String type, TopCache cache) {
        topCache.put(type, cache);
    }

    /**
     * Classe interna para cache de top
     */
    public static class TopCache {
        private final String data;
        private final long timestamp;

        public TopCache(String data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getData() {
            return data;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Classe interna para cache de ELO médio
     */
    public static class EloCache {
        private final double avgElo;
        private final long timestamp;

        public EloCache(double avgElo) {
            this.avgElo = avgElo;
            this.timestamp = System.currentTimeMillis();
        }

        public double getAvgElo() {
            return avgElo;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Classe interna para cache de alertas
     */
    public static class AlertCache {
        private final int count;
        private final int punishmentCount;
        private final long timestamp;

        public AlertCache(int count) {
            this.count = count;
            this.punishmentCount = 0;
            this.timestamp = System.currentTimeMillis();
        }

        public AlertCache(int count, int punishmentCount) {
            this.count = count;
            this.punishmentCount = punishmentCount;
            this.timestamp = System.currentTimeMillis();
        }

        public int getCount() {
            return count;
        }

        public int getPunishmentCount() {
            return punishmentCount;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Obtém cache de ELO médio ou null se expirado
     */
    public EloCache getEloCache(int clanId) {
        EloCache cache = eloCache.get(clanId);
        if (cache != null && System.currentTimeMillis() - cache.getTimestamp() < 30000) { // TTL 30s
            return cache;
        }
        return null;
    }

    /**
     * Define cache de ELO médio
     */
    public void setEloCache(int clanId, EloCache cache) {
        eloCache.put(clanId, cache);
    }

    /**
     * Invalida cache de ELO médio do clan (chamado quando membros fazem PvP)
     */
    public void invalidateEloCache(int clanId) {
        eloCache.remove(clanId);
    }

    /**
     * Obtém cache de alertas ou null se expirado
     */
    public AlertCache getAlertCache(int clanId) {
        AlertCache cache = alertCache.get(clanId);
        if (cache != null && System.currentTimeMillis() - cache.getTimestamp() < 60000) { // TTL 60s
            return cache;
        }
        return null;
    }

    /**
     * Define cache de alertas
     */
    public void setAlertCache(int clanId, AlertCache cache) {
        alertCache.put(clanId, cache);
    }

    /**
     * Invalida cache de alertas do clan
     */
    public void invalidateAlertCache(int clanId) {
        alertCache.remove(clanId);
    }

    /**
     * Invalida cache de ranking por tipo
     */
    public void invalidateTopCache(String type) {
        topCache.remove(type);
    }

    /**
     * Obtém pontos para um evento específico
     */
    public int getPointsForEvent(String eventName) {
        int points = getConfig().getInt("points.events." + eventName, -1);
        if (points == -1) {
            return getConfig().getInt("points.default-award", 10);
        }
        return points;
    }

    /**
     * Setup PlaceholderAPI integration
     */
    private void setupPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI não encontrado - integração desabilitada");
            return;
        }

        try {
            placeholderExpansion = new ClansPlaceholderExpansion(this);
            if (placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI integration habilitada");
            } else {
                getLogger().warning("Falha ao registrar PlaceholderAPI expansion");
            }
        } catch (Exception e) {
            getLogger().warning("Erro ao configurar PlaceholderAPI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * API pública para plugin de punições
     */
    public void addPunishmentAlert(int clanId, UUID playerUuid, String type, String message, String punishmentId, UUID createdBy) {
        getClansManager().addAlert(clanId, playerUuid, type, message, createdBy, punishmentId);
    }

    /**
     * Verifica se clan está bloqueado de eventos
     */
    public boolean isClanBlockedFromEvents(int clanId) {
        return getClansManager().isClanBlockedFromEvents(clanId);
    }
}

