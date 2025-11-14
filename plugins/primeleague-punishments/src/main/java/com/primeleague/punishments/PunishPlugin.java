package com.primeleague.punishments;

import com.primeleague.core.CoreAPI;
import com.primeleague.punishments.commands.*;
import com.primeleague.punishments.integrations.DiscordIntegration;
import com.primeleague.punishments.listeners.PunishListener;
import com.primeleague.punishments.managers.PunishManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin de Punições - Primeleague
 * Grug Brain: Plugin simples, depende do Core via CoreAPI
 */
public class PunishPlugin extends JavaPlugin {

    private static PunishPlugin instance;
    private PunishManager punishManager;
    private DiscordIntegration discordIntegration;

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

        // Inicializar PunishManager
        punishManager = new PunishManager(this);

        // Inicializar integração Discord (se disponível)
        discordIntegration = new DiscordIntegration(this);

        // Registrar listeners
        getServer().getPluginManager().registerEvents(new PunishListener(this), this);

        // Registrar comandos
        if (getCommand("ban") != null) {
            getCommand("ban").setExecutor(new BanCommand(this));
        }
        if (getCommand("unban") != null) {
            getCommand("unban").setExecutor(new UnbanCommand(this));
        }
        if (getCommand("mute") != null) {
            getCommand("mute").setExecutor(new MuteCommand(this));
        }
        if (getCommand("unmute") != null) {
            getCommand("unmute").setExecutor(new UnmuteCommand(this));
        }
        if (getCommand("warn") != null) {
            getCommand("warn").setExecutor(new WarnCommand(this));
        }
        if (getCommand("kick") != null) {
            getCommand("kick").setExecutor(new KickCommand(this));
        }
        if (getCommand("history") != null) {
            getCommand("history").setExecutor(new HistoryCommand(this));
        }

        // Limpar cache de rate limiting do Discord periodicamente (a cada 5 minutos)
        if (discordIntegration != null && discordIntegration.isDiscordEnabled()) {
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                discordIntegration.cleanRateLimitCache();
            }, 6000L, 6000L); // A cada 5 minutos (6000 ticks)
        }

        getLogger().info("PrimeleaguePunishments habilitado");
    }

    @Override
    public void onDisable() {
        getLogger().info("PrimeleaguePunishments desabilitado");
    }

    /**
     * Cria tabelas PostgreSQL se não existirem
     * Grug Brain: Queries diretas, try-with-resources
     */
    private void createTables() {
        try (java.sql.Connection conn = CoreAPI.getDatabase().getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            // Tabela punishments
            stmt.execute("CREATE TABLE IF NOT EXISTS punishments (" +
                "id SERIAL PRIMARY KEY, " +
                "player_uuid UUID NOT NULL, " +
                "ip VARCHAR(45), " +
                "type VARCHAR(20) NOT NULL, " +
                "reason TEXT, " +
                "staff_uuid UUID, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "expires_at TIMESTAMP, " +
                "active BOOLEAN DEFAULT TRUE, " +
                "appealed BOOLEAN DEFAULT FALSE" +
                ")");

            // Índices para punishments
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_punishments_player ON punishments(player_uuid)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_punishments_type_active ON punishments(type, active, expires_at)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_punishments_ip ON punishments(ip) WHERE ip IS NOT NULL");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }

            getLogger().info("Tabela punishments criada/verificada");
        } catch (java.sql.SQLException e) {
            getLogger().severe("Erro ao criar tabela punishments: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static PunishPlugin getInstance() {
        return instance;
    }

    public PunishManager getPunishManager() {
        return punishManager;
    }

    public DiscordIntegration getDiscordIntegration() {
        return discordIntegration;
    }
}

