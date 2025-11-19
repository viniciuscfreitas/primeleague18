package com.primeleague.gladiador;

import com.primeleague.core.CoreAPI;
import com.primeleague.gladiador.commands.GladiadorCommand;
import com.primeleague.gladiador.integrations.DiscordIntegration;
import com.primeleague.gladiador.integrations.ScoreboardIntegration;
import com.primeleague.gladiador.integrations.TabIntegration;
import com.primeleague.gladiador.listeners.MatchListener;
import com.primeleague.gladiador.listeners.ProtectionListener;
import com.primeleague.gladiador.managers.ArenaManager;
import com.primeleague.gladiador.managers.MatchManager;
import com.primeleague.gladiador.managers.StatsManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plugin Gladiador - Primeleague
 * Grug Brain: Plugin simples, depende do Core e Clans
 */
public class GladiadorPlugin extends JavaPlugin {

    private static GladiadorPlugin instance;
    private ArenaManager arenaManager;
    private MatchManager matchManager;
    private StatsManager statsManager;
    private DiscordIntegration discordIntegration;
    private TabIntegration tabIntegration;
    private ScoreboardIntegration scoreboardIntegration;

    @Override
    public void onEnable() {
        instance = this;

        // Verificar se Core está habilitado
        if (!CoreAPI.isEnabled()) {
            getLogger().severe("PrimeleagueCore não encontrado! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Verificar se Clans está habilitado
        if (getServer().getPluginManager().getPlugin("PrimeleagueClans") == null) {
            getLogger().severe("PrimeleagueClans não encontrado! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Salvar config padrão
        saveDefaultConfig();

        // Criar schema de banco de dados
        createSchema();

        // Inicializar managers
        arenaManager = new ArenaManager(this);
        matchManager = new MatchManager(this);
        statsManager = new StatsManager(this);
        discordIntegration = new DiscordIntegration(this);

        // Inicializar integrações (soft dependencies)
        tabIntegration = new TabIntegration(this);
        scoreboardIntegration = new ScoreboardIntegration(this);

        // Carregar arenas do banco
        arenaManager.loadArenas();

        // Registrar comando
        GladiadorCommand cmd = new GladiadorCommand(this);
        getCommand("gladiador").setExecutor(cmd);
        getCommand("gladiador").setTabCompleter(cmd);

        // Registrar listeners
        getServer().getPluginManager().registerEvents(new MatchListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);

        // Limpar cache de rate limiting do Discord periodicamente (a cada 5 minutos)
        if (discordIntegration != null && discordIntegration.isDiscordEnabled()) {
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                discordIntegration.cleanRateLimitCache();
            }, 6000L, 6000L); // A cada 5 minutos (6000 ticks)
        }

        // Registrar PlaceholderAPI (softdepend - comentado até PlaceholderAPI estar disponível no classpath)
        // TODO: Implementar via reflection quando PlaceholderAPI estiver disponível
        // if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
        //     new com.primeleague.gladiador.integrations.GladiadorPlaceholderExpansion(this).register();
        // }

        getLogger().info("PrimeleagueGladiador habilitado");
    }

    @Override
    public void onDisable() {
        // Cancelar match ativo se existir
        if (matchManager != null && matchManager.getCurrentMatch() != null) {
            matchManager.cancelMatch();
        }

        getLogger().info("PrimeleagueGladiador desabilitado");
    }

    /**
     * Cria tabelas PostgreSQL se não existirem
     * Grug Brain: Queries diretas, try-with-resources
     */
    private void createSchema() {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             Statement stmt = conn.createStatement()) {

            // Tabela de arenas
            stmt.execute("CREATE TABLE IF NOT EXISTS gladiador_arenas (" +
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(50) NOT NULL UNIQUE, " +
                "world VARCHAR(100) NOT NULL, " +
                "center_x DOUBLE PRECISION NOT NULL, " +
                "center_y DOUBLE PRECISION NOT NULL, " +
                "center_z DOUBLE PRECISION NOT NULL, " +
                "initial_border_size INTEGER NOT NULL DEFAULT 500, " +
                "final_border_size INTEGER NOT NULL DEFAULT 20, " +
                "spectator_world VARCHAR(100) NOT NULL, " +
                "spectator_x DOUBLE PRECISION NOT NULL, " +
                "spectator_y DOUBLE PRECISION NOT NULL, " +
                "spectator_z DOUBLE PRECISION NOT NULL, " +
                "spectator_yaw REAL NOT NULL, " +
                "spectator_pitch REAL NOT NULL, " +
                "spawn_points JSONB NOT NULL DEFAULT '[]', " +
                "enabled BOOLEAN NOT NULL DEFAULT true, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")");

            // Tabela de matches
            stmt.execute("CREATE TABLE IF NOT EXISTS gladiador_matches (" +
                "id SERIAL PRIMARY KEY, " +
                "arena_id INTEGER NOT NULL REFERENCES gladiador_arenas(id), " +
                "winner_clan_id INTEGER REFERENCES clans(id), " +
                "participant_clans JSONB NOT NULL, " +
                "total_kills INTEGER NOT NULL DEFAULT 0, " +
                "duration_seconds INTEGER NOT NULL, " +
                "started_at TIMESTAMP NOT NULL, " +
                "ended_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")");

            // Índices para matches
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_gladiador_matches_ended " +
                "ON gladiador_matches(ended_at DESC)");

            // Tabela de stats
            stmt.execute("CREATE TABLE IF NOT EXISTS gladiador_stats (" +
                "clan_id INTEGER PRIMARY KEY REFERENCES clans(id) ON DELETE CASCADE, " +
                "wins INTEGER NOT NULL DEFAULT 0, " +
                "participations INTEGER NOT NULL DEFAULT 0, " +
                "total_kills INTEGER NOT NULL DEFAULT 0, " +
                "total_deaths INTEGER NOT NULL DEFAULT 0, " +
                "last_win TIMESTAMP, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")");

            // Índices para stats
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_gladiador_stats_wins " +
                "ON gladiador_stats(wins DESC, total_kills DESC)");

            getLogger().info("Tabelas do Gladiador criadas/verificadas");

        } catch (SQLException e) {
            getLogger().severe("Erro ao criar schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Getters
    public static GladiadorPlugin getInstance() {
        return instance;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public DiscordIntegration getDiscordIntegration() {
        return discordIntegration;
    }

    public TabIntegration getTabIntegration() {
        return tabIntegration;
    }

    public ScoreboardIntegration getScoreboardIntegration() {
        return scoreboardIntegration;
    }
}
