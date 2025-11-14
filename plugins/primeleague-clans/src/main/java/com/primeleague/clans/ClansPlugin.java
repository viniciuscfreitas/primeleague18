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
    private ClansManager clansManager;
    private Map<String, TopCache> topCache;
    private long topCacheDuration;
    private Map<Integer, EloCache> eloCache; // Cache de ELO médio por clan (TTL 30s)
    private Map<Integer, AlertCache> alertCache; // Cache de alertas por clan (TTL 60s)
    private ClansPlaceholderExpansion placeholderExpansion;
    private com.primeleague.clans.integrations.DiscordIntegration discordIntegration;

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

        // Criar tabelas PostgreSQL
        createTables();

        // Inicializar ClansManager
        clansManager = new ClansManager(this);

        // Inicializar cache de top (thread-safe)
        topCache = new ConcurrentHashMap<>();
        topCacheDuration = getConfig().getLong("cache.top-clans-duration", 300) * 1000; // Converter para ms

        // Inicializar cache de ELO médio (thread-safe, TTL 30s)
        eloCache = new ConcurrentHashMap<>();

        // Inicializar cache de alertas (thread-safe, TTL 60s)
        alertCache = new ConcurrentHashMap<>();

        // Inicializar integração Discord (se disponível)
        discordIntegration = new com.primeleague.clans.integrations.DiscordIntegration(this);

        // Registrar listeners
        getServer().getPluginManager().registerEvents(new ClanStatsListener(this), this);
        getServer().getPluginManager().registerEvents(new ClanChatListener(this), this);
        getServer().getPluginManager().registerEvents(new com.primeleague.clans.listeners.ClanEventWinListener(this), this);
        getServer().getPluginManager().registerEvents(new com.primeleague.clans.listeners.ClanPunishmentListener(this), this);

        // Registrar comando
        if (getCommand("clan") != null) {
            getCommand("clan").setExecutor(new ClanCommand(this));
        }

        // Setup PlaceholderAPI (se disponível)
        setupPlaceholderAPI();

        // Limpar cache de rate limiting do Discord periodicamente (a cada 5 minutos)
        if (discordIntegration != null && discordIntegration.isDiscordEnabled()) {
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                discordIntegration.cleanRateLimitCache();
            }, 6000L, 6000L); // A cada 5 minutos (6000 ticks)
        }

        getLogger().info("PrimeleagueClans habilitado");
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
        if (eloCache != null) {
            eloCache.clear();
        }
        if (alertCache != null) {
            alertCache.clear();
        }
        getLogger().info("PrimeleagueClans desabilitado");
    }

    /**
     * Cria tabelas PostgreSQL se não existirem
     * Grug Brain: Queries diretas, try-with-resources
     */
    private void createTables() {
        try (java.sql.Connection conn = CoreAPI.getDatabase().getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            // Tabela clans
            stmt.execute("CREATE TABLE IF NOT EXISTS clans (" +
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(50) NOT NULL, " +
                "tag VARCHAR(20) NOT NULL, " +
                "tag_clean VARCHAR(3) NOT NULL, " +
                "leader_uuid UUID NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "description VARCHAR(255), " +
                "discord_channel_id BIGINT, " +
                "discord_role_id BIGINT, " +
                "FOREIGN KEY (leader_uuid) REFERENCES users(uuid)" +
                ")");

            // Índice único case-insensitive para tag_clean
            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_clans_tag_clean ON clans(UPPER(tag_clean))");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }

            // Índice para nome
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clans_name ON clans(name)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }

            // Tabela clan_members
            stmt.execute("CREATE TABLE IF NOT EXISTS clan_members (" +
                "clan_id INTEGER NOT NULL, " +
                "player_uuid UUID NOT NULL, " +
                "role VARCHAR(20) DEFAULT 'MEMBER', " +
                "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (clan_id, player_uuid), " +
                "FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (player_uuid) REFERENCES users(uuid)" +
                ")");

            // Índices para clan_members
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_members_clan ON clan_members(clan_id)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_members_uuid ON clan_members(player_uuid)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }

            // Tabela clan_invites
            stmt.execute("CREATE TABLE IF NOT EXISTS clan_invites (" +
                "id SERIAL PRIMARY KEY, " +
                "clan_id INTEGER NOT NULL, " +
                "invited_uuid UUID NOT NULL, " +
                "inviter_uuid UUID NOT NULL, " +
                "expires_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP + INTERVAL '5 minutes', " +
                "FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (invited_uuid) REFERENCES users(uuid)" +
                ")");

            // Índices para clan_invites
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_invites_invited ON clan_invites(invited_uuid)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_invites_expires ON clan_invites(expires_at)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }

            // Tabela clan_bank
            stmt.execute("CREATE TABLE IF NOT EXISTS clan_bank (" +
                "clan_id INTEGER PRIMARY KEY, " +
                "balance_cents BIGINT DEFAULT 0, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE" +
                ")");

            // Adicionar colunas de home (se não existirem)
            // PostgreSQL não suporta IF NOT EXISTS em ALTER TABLE, então verificamos antes
            try {
                // Verificar se coluna existe antes de adicionar
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "clans", "home_world")) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE clans ADD COLUMN home_world VARCHAR(50)");
                    }
                }
            } catch (java.sql.SQLException e) {
                // Ignorar erro
            }
            try {
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "clans", "home_x")) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE clans ADD COLUMN home_x DOUBLE PRECISION");
                    }
                }
            } catch (java.sql.SQLException e) {
                // Ignorar erro
            }
            try {
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "clans", "home_y")) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE clans ADD COLUMN home_y DOUBLE PRECISION");
                    }
                }
            } catch (java.sql.SQLException e) {
                // Ignorar erro
            }
            try {
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "clans", "home_z")) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE clans ADD COLUMN home_z DOUBLE PRECISION");
                    }
                }
            } catch (java.sql.SQLException e) {
                // Ignorar erro
            }

            // Adicionar colunas de pontos e bloqueio (se não existirem)
            try {
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "clans", "points")) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE clans ADD COLUMN points INTEGER DEFAULT 0");
                    }
                }
            } catch (java.sql.SQLException e) {
                // Ignorar erro
            }
            try {
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "clans", "event_wins_count")) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE clans ADD COLUMN event_wins_count INTEGER DEFAULT 0");
                    }
                }
            } catch (java.sql.SQLException e) {
                // Ignorar erro
            }
            try {
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "clans", "blocked_from_events")) {
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE clans ADD COLUMN blocked_from_events BOOLEAN DEFAULT FALSE");
                    }
                }
            } catch (java.sql.SQLException e) {
                // Ignorar erro
            }

            // Tabela clan_event_wins
            stmt.execute("CREATE TABLE IF NOT EXISTS clan_event_wins (" +
                "id SERIAL PRIMARY KEY, " +
                "clan_id INTEGER NOT NULL, " +
                "event_name VARCHAR(100) NOT NULL, " +
                "points_awarded INTEGER DEFAULT 10, " +
                "awarded_by UUID, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE" +
                ")");

            // Índices para clan_event_wins
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_event_wins_clan ON clan_event_wins(clan_id)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_event_wins_time ON clan_event_wins(created_at DESC)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_event_wins_event ON clan_event_wins(event_name)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }

            // Tabela clan_alerts
            stmt.execute("CREATE TABLE IF NOT EXISTS clan_alerts (" +
                "id SERIAL PRIMARY KEY, " +
                "clan_id INTEGER NOT NULL, " +
                "player_uuid UUID, " +
                "alert_type VARCHAR(50) NOT NULL, " +
                "punishment_id VARCHAR(100), " +
                "message VARCHAR(500) NOT NULL, " +
                "created_by UUID, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "removed BOOLEAN DEFAULT FALSE, " +
                "removed_at TIMESTAMP, " +
                "removed_by UUID, " +
                "FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE" +
                ")");

            // Índices para clan_alerts
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_alerts_clan ON clan_alerts(clan_id)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_alerts_removed ON clan_alerts(removed, created_at DESC)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_alerts_player ON clan_alerts(player_uuid)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_alerts_punish ON clan_alerts(punishment_id)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }

            getLogger().info("Tabelas de clans criadas/verificadas");
        } catch (java.sql.SQLException e) {
            getLogger().severe("Erro ao criar tabelas de clans: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static ClansPlugin getInstance() {
        return instance;
    }

    public ClansManager getClansManager() {
        return clansManager;
    }

    public com.primeleague.clans.integrations.DiscordIntegration getDiscordIntegration() {
        return discordIntegration;
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
     * API pública para plugin de punições futuro
     */
    public void addPunishmentAlert(int clanId, UUID playerUuid, String type, String message, String punishmentId) {
        getClansManager().addAlert(clanId, playerUuid, type, message, null, punishmentId);
    }

    /**
     * Verifica se clan está bloqueado de eventos
     */
    public boolean isClanBlockedFromEvents(int clanId) {
        return getClansManager().isClanBlockedFromEvents(clanId);
    }
}

