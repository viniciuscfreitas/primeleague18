package com.primeleague.elo;

import com.primeleague.core.CoreAPI;
import com.primeleague.elo.commands.EloCommand;
import com.primeleague.elo.listeners.PvPListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin de ELO Rating - Primeleague
 * Grug Brain: Plugin simples, depende do Core via CoreAPI
 */
public class EloPlugin extends JavaPlugin {

    private static EloPlugin instance;
    private PvPListener pvpListener;
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

        // Inicializar cache de top ELO (thread-safe)
        topCache = new ConcurrentHashMap<>();
        topCacheDuration = getConfig().getLong("cache.top-elo-duration", 300) * 1000; // Converter para ms

        // Registrar listener
        pvpListener = new PvPListener(this);
        getServer().getPluginManager().registerEvents(pvpListener, this);

        // Registrar comandos
        if (getCommand("elo") != null) {
            getCommand("elo").setExecutor(new EloCommand(this));
        }
        if (getCommand("topelo") != null) {
            getCommand("topelo").setExecutor(new EloCommand(this));
        }

        getLogger().info("PrimeleagueElo habilitado");
    }

    @Override
    public void onDisable() {
        if (topCache != null) {
            topCache.clear();
        }
        getLogger().info("PrimeleagueElo desabilitado");
    }

    public static EloPlugin getInstance() {
        return instance;
    }

    public PvPListener getPvPListener() {
        return pvpListener;
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

