package com.primeleague.stats;

import com.primeleague.core.CoreAPI;
import com.primeleague.stats.commands.StatsCommand;
import com.primeleague.stats.listeners.CombatListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin de stats - Rastreamento de combate PvP
 * Grug Brain: Plugin simples, depende do Core via CoreAPI
 */
public class StatsPlugin extends JavaPlugin {

    private static StatsPlugin instance;
    private CombatListener combatListener;
    private Map<String, TopCache> topCache;
    private long topCacheDuration;

    @Override
    public void onEnable() {
        instance = this;

        // Verificar se Core está habilitado
        if (!CoreAPI.isEnabled()) {
            getLogger().severe("PrimeleagueCore não encontrado! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Salvar config padrão se não existir
        saveDefaultConfig();

        // Inicializar cache de top (thread-safe)
        topCache = new ConcurrentHashMap<>();
        topCacheDuration = getConfig().getLong("stats.top-cache-duration", 300) * 1000; // Converter para ms

        // Registrar listener
        combatListener = new CombatListener(this);
        getServer().getPluginManager().registerEvents(combatListener, this);

        // Registrar comandos
        if (getCommand("stats") != null) {
            getCommand("stats").setExecutor(new StatsCommand(this));
        }
        if (getCommand("top") != null) {
            getCommand("top").setExecutor(new StatsCommand(this));
        }

        getLogger().info("PrimeleagueStats habilitado");
    }

    @Override
    public void onDisable() {
        if (topCache != null) {
            topCache.clear();
        }
        getLogger().info("PrimeleagueStats desabilitado");
    }

    public static StatsPlugin getInstance() {
        return instance;
    }

    public CombatListener getCombatListener() {
        return combatListener;
    }

    /**
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
}


