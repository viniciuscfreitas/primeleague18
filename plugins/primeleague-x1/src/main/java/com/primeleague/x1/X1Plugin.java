package com.primeleague.x1;

import com.primeleague.core.CoreAPI;
import com.primeleague.x1.integrations.X1PlaceholderExpansion;
import com.primeleague.x1.managers.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin de Practice PvP Duels X1 - Primeleague
 * Grug Brain: Plugin simples, depende do Core via CoreAPI
 */
public class X1Plugin extends JavaPlugin {

    private static X1Plugin instance;
    private QueueManager queueManager;
    private MatchManager matchManager;
    private KitManager kitManager;
    private ArenaManager arenaManager;
    private StatsManager statsManager;
    private com.primeleague.x1.managers.AntiFarmManager antiFarmManager;
    private com.primeleague.x1.integrations.DiscordIntegration discordIntegration;
    private com.primeleague.x1.integrations.TabIntegration tabIntegration;
    private com.primeleague.x1.integrations.ScoreboardIntegration scoreboardIntegration;
    private X1PlaceholderExpansion placeholderExpansion;
    private com.primeleague.x1.commands.DuelCommand duelCommand;
    private Map<String, TopCache> topCache;
    private long topCacheDuration;
    // Cache de última mudança de ELO por player (para placeholders)
    private Map<UUID, Integer> lastEloChange;
    // Cache de stats para placeholders (TTL curto - 5 segundos)
    private Map<UUID, PlaceholderStatsCache> placeholderStatsCache;

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

        // Criar schema de tabelas no banco (CRÍTICO - não no Core)
        if (!createSchema()) {
            getLogger().severe("Falha ao criar schema do banco de dados. Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Inicializar cache de top (thread-safe)
        topCache = new ConcurrentHashMap<>();
        topCacheDuration = getConfig().getLong("cache.top-duration", 300) * 1000; // Converter para ms
        // Cache de última mudança de ELO
        lastEloChange = new ConcurrentHashMap<>();
        // Cache de stats para placeholders (TTL curto)
        placeholderStatsCache = new ConcurrentHashMap<>();

        // Inicializar managers
        queueManager = new QueueManager(this);
        matchManager = new MatchManager(this);
        kitManager = new KitManager(this);
        arenaManager = new ArenaManager(this);
        statsManager = new StatsManager(this);
        antiFarmManager = new com.primeleague.x1.managers.AntiFarmManager(this);
        
        // Inicializar integrações
        if (getConfig().getBoolean("integrations.discord.enabled", true)) {
            discordIntegration = new com.primeleague.x1.integrations.DiscordIntegration(this);
        }
        
        // Integração TAB (opcional)
        tabIntegration = new com.primeleague.x1.integrations.TabIntegration(this);
        
        // Integração Scoreboard (opcional)
        scoreboardIntegration = new com.primeleague.x1.integrations.ScoreboardIntegration(this);

        // Inicializar comando de duelo (usado via /x1)
        duelCommand = new com.primeleague.x1.commands.DuelCommand(this);

        // Carregar dados do banco (aguardar conclusão)
        kitManager.loadKits();
        arenaManager.loadArenas();
        
        // Tarefa periódica para limpar cache expirado de placeholders (a cada 30 segundos)
        getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                clearExpiredPlaceholderCache();
            }
        }, 600L, 600L); // 30 segundos (600 ticks)
        
        // Aguardar um tick para garantir que kits/arenas foram carregados
        getServer().getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                getLogger().info("Kits carregados: " + kitManager.getAllKits().size());
                getLogger().info("Arenas carregadas: " + arenaManager.getAllArenas().size());
            }
        }, 20L); // 1 segundo

        // Registrar listeners
        getServer().getPluginManager().registerEvents(new com.primeleague.x1.listeners.MatchListener(this), this);
        getServer().getPluginManager().registerEvents(new com.primeleague.x1.listeners.QueueListener(this), this);
        getServer().getPluginManager().registerEvents(new com.primeleague.x1.listeners.MatchMovementListener(this), this);

        // Registrar comandos (verificar se estão definidos no plugin.yml)
        org.bukkit.command.PluginCommand x1Cmd = getCommand("x1");
        if (x1Cmd != null) {
            x1Cmd.setExecutor(new com.primeleague.x1.commands.X1Command(this));
            getLogger().info("Comando /x1 registrado");
        } else {
            getLogger().warning("Comando /x1 não encontrado no plugin.yml!");
        }
        
        // Grug Brain: Tudo via /x1 - sem comandos separados /kit e /arena
        // Use: /x1 admin kit ... e /x1 admin arena ...

        // Setup PlaceholderAPI (se disponível)
        setupPlaceholderAPI();

        getLogger().info("PrimeleagueX1 habilitado");
    }

    @Override
    public void onDisable() {
        // Desregistrar PlaceholderAPI expansion
        if (placeholderExpansion != null) {
            try {
                placeholderExpansion.unregister();
            } catch (Exception e) {
                // Ignorar erros ao desregistrar
            }
        }

        if (topCache != null) {
            topCache.clear();
        }
        
        if (lastEloChange != null) {
            lastEloChange.clear();
        }
        
        if (placeholderStatsCache != null) {
            placeholderStatsCache.clear();
        }

        getLogger().info("PrimeleagueX1 desabilitado");
    }

    /**
     * Criar schema de tabelas no banco de dados
     * Grug Brain: Seguir padrão de migrations inline do DatabaseManager.createSchema()
     */
    private boolean createSchema() {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                // Tabela x1_matches
                stmt.execute("CREATE TABLE IF NOT EXISTS x1_matches (" +
                    "id SERIAL PRIMARY KEY, " +
                    "player1_uuid UUID NOT NULL REFERENCES users(uuid), " +
                    "player2_uuid UUID NOT NULL REFERENCES users(uuid), " +
                    "winner_uuid UUID REFERENCES users(uuid), " +
                    "kit_name VARCHAR(50) NOT NULL, " +
                    "ranked BOOLEAN DEFAULT false, " +
                    "elo_change INT, " +
                    "created_at TIMESTAMP DEFAULT NOW()" +
                    ")");

                // Índices para x1_matches
                try {
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_x1_matches_player1 ON x1_matches(player1_uuid)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_x1_matches_player2 ON x1_matches(player2_uuid)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_x1_matches_winner ON x1_matches(winner_uuid)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_x1_matches_ranked_created ON x1_matches(ranked, created_at DESC)");
                } catch (SQLException e) {
                    // Índices já existem - ignorar
                }

                // Tabela x1_stats
                stmt.execute("CREATE TABLE IF NOT EXISTS x1_stats (" +
                    "player_uuid UUID PRIMARY KEY REFERENCES users(uuid), " +
                    "wins INT DEFAULT 0, " +
                    "losses INT DEFAULT 0, " +
                    "winstreak INT DEFAULT 0, " +
                    "best_winstreak INT DEFAULT 0, " +
                    "last_match_at TIMESTAMP" +
                    ")");

                // Tabela x1_arenas
                stmt.execute("CREATE TABLE IF NOT EXISTS x1_arenas (" +
                    "id SERIAL PRIMARY KEY, " +
                    "name VARCHAR(50) UNIQUE NOT NULL, " +
                    "world_name VARCHAR(100) NOT NULL, " +
                    "spawn1_x DOUBLE PRECISION NOT NULL, " +
                    "spawn1_y DOUBLE PRECISION NOT NULL, " +
                    "spawn1_z DOUBLE PRECISION NOT NULL, " +
                    "spawn2_x DOUBLE PRECISION NOT NULL, " +
                    "spawn2_y DOUBLE PRECISION NOT NULL, " +
                    "spawn2_z DOUBLE PRECISION NOT NULL, " +
                    "center_x DOUBLE PRECISION NOT NULL, " +
                    "center_y DOUBLE PRECISION NOT NULL, " +
                    "center_z DOUBLE PRECISION NOT NULL, " +
                    "enabled BOOLEAN DEFAULT true, " +
                    "in_use BOOLEAN DEFAULT false" +
                    ")");

                // Tabela x1_kits
                stmt.execute("CREATE TABLE IF NOT EXISTS x1_kits (" +
                    "id SERIAL PRIMARY KEY, " +
                    "name VARCHAR(50) UNIQUE NOT NULL, " +
                    "items JSONB, " +
                    "armor JSONB, " +
                    "effects JSONB, " +
                    "enabled BOOLEAN DEFAULT true" +
                    ")");

                getLogger().info("Schema PostgreSQL criado/verificado com sucesso");
                return true;
            }
        } catch (SQLException e) {
            getLogger().severe("Erro ao criar schema do banco de dados: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Setup PlaceholderAPI integration
     */
    private void setupPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI não encontrado - placeholders de X1 não estarão disponíveis");
            return;
        }

        try {
            placeholderExpansion = new X1PlaceholderExpansion(this);
            if (placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI integration habilitada (%x1_wins%, %x1_losses%, etc.)");
            } else {
                getLogger().warning("Falha ao registrar PlaceholderAPI expansion");
            }
        } catch (Exception e) {
            getLogger().warning("Erro ao configurar PlaceholderAPI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static X1Plugin getInstance() {
        return instance;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public com.primeleague.x1.managers.AntiFarmManager getAntiFarmManager() {
        return antiFarmManager;
    }

    public com.primeleague.x1.integrations.DiscordIntegration getDiscordIntegration() {
        return discordIntegration;
    }

    public com.primeleague.x1.integrations.TabIntegration getTabIntegration() {
        return tabIntegration;
    }

    public com.primeleague.x1.integrations.ScoreboardIntegration getScoreboardIntegration() {
        return scoreboardIntegration;
    }

    public com.primeleague.x1.commands.DuelCommand getDuelCommand() {
        return duelCommand;
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
     * Obtém última mudança de ELO do player
     */
    public int getLastEloChange(UUID playerUuid) {
        return lastEloChange.getOrDefault(playerUuid, 0);
    }

    /**
     * Define última mudança de ELO do player
     */
    public void setLastEloChange(UUID playerUuid, int change) {
        lastEloChange.put(playerUuid, change);
    }

    /**
     * Obtém stats do cache de placeholders (com TTL de 5 segundos)
     */
    public com.primeleague.x1.models.X1Stats getPlaceholderStats(UUID playerUuid) {
        PlaceholderStatsCache cached = placeholderStatsCache.get(playerUuid);
        if (cached != null && System.currentTimeMillis() - cached.getTimestamp() < 5000) {
            return cached.getStats();
        }
        return null; // Cache expirado ou não existe
    }

    /**
     * Define stats no cache de placeholders
     */
    public void setPlaceholderStats(UUID playerUuid, com.primeleague.x1.models.X1Stats stats) {
        placeholderStatsCache.put(playerUuid, new PlaceholderStatsCache(stats, System.currentTimeMillis()));
    }

    /**
     * Limpa cache expirado de placeholders (chamado periodicamente)
     */
    public void clearExpiredPlaceholderCache() {
        long now = System.currentTimeMillis();
        placeholderStatsCache.entrySet().removeIf(entry -> 
            now - entry.getValue().getTimestamp() > 5000);
    }

    /**
     * Classe interna para cache de stats de placeholders
     */
    public static class PlaceholderStatsCache {
        private final com.primeleague.x1.models.X1Stats stats;
        private final long timestamp;

        public PlaceholderStatsCache(com.primeleague.x1.models.X1Stats stats, long timestamp) {
            this.stats = stats;
            this.timestamp = timestamp;
        }

        public com.primeleague.x1.models.X1Stats getStats() {
            return stats;
        }

        public long getTimestamp() {
            return timestamp;
        }
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

