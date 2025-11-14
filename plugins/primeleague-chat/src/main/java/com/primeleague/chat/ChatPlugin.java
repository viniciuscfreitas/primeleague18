package com.primeleague.chat;

import com.primeleague.chat.commands.MsgCommand;
import com.primeleague.chat.commands.ReplyCommand;
import com.primeleague.chat.integrations.ChatPlaceholderExpansion;
import com.primeleague.chat.integrations.DiscordIntegration;
import com.primeleague.chat.listeners.ChatListener;
import com.primeleague.chat.managers.ChatManager;
import com.primeleague.core.CoreAPI;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin de Chat - Primeleague
 * Grug Brain: Plugin simples, depende do Core via CoreAPI
 */
public class ChatPlugin extends JavaPlugin {

    private static ChatPlugin instance;
    private ChatManager chatManager;
    private ChatListener chatListener;
    private ChatPlaceholderExpansion placeholderExpansion;
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

        // Criar tabela PostgreSQL
        createTables();

        // Inicializar ChatManager
        chatManager = new ChatManager(this);

        // Inicializar ChatListener
        chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);

        // Registrar comandos
        if (getCommand("msg") != null) {
            getCommand("msg").setExecutor(new MsgCommand(this));
        }
        if (getCommand("reply") != null) {
            getCommand("reply").setExecutor(new ReplyCommand(this));
        }
        if (getCommand("clearchat") != null) {
            getCommand("clearchat").setExecutor(new com.primeleague.chat.commands.ClearChatCommand(this));
        }

        // Setup PlaceholderAPI (se disponível)
        setupPlaceholderAPI();

        // Inicializar integração Discord (se disponível)
        discordIntegration = new DiscordIntegration(this);

        // Iniciar auto-broadcast se habilitado
        if (getConfig().getBoolean("broadcast.enabled", false)) {
            startAutoBroadcast();
        }

        getLogger().info("PrimeleagueChat habilitado");
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

        getLogger().info("PrimeleagueChat desabilitado");
    }

    /**
     * Cria tabela PostgreSQL se não existir
     * Grug Brain: Queries diretas, try-with-resources
     */
    private void createTables() {
        try (java.sql.Connection conn = CoreAPI.getDatabase().getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            // Tabela chat_logs
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_logs (" +
                "id SERIAL PRIMARY KEY, " +
                "player_uuid UUID NOT NULL, " +
                "message TEXT NOT NULL, " +
                "channel VARCHAR(20) DEFAULT 'global', " +
                "target_uuid UUID, " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (player_uuid) REFERENCES users(uuid)" +
                ")");

            // Índices para chat_logs
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_logs_player ON chat_logs(player_uuid)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_logs_time ON chat_logs(timestamp DESC)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_logs_channel ON chat_logs(channel)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }

            getLogger().info("Tabela chat_logs criada/verificada");
        } catch (java.sql.SQLException e) {
            getLogger().severe("Erro ao criar tabela chat_logs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Setup PlaceholderAPI integration
     */
    private void setupPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI não encontrado - formatação básica será usada");
            return;
        }

        try {
            placeholderExpansion = new ChatPlaceholderExpansion(this);
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
     * Inicia auto-broadcast periódico
     * Grug Brain: Broadcast message deve ser síncrono (Bukkit 1.8.8)
     */
    private void startAutoBroadcast() {
        long interval = getConfig().getLong("broadcast.interval", 300) * 20L; // Converter para ticks

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                java.util.List<String> messages = getConfig().getStringList("broadcast.messages");
                if (messages.isEmpty()) {
                    return;
                }

                String message = messages.get(new java.util.Random().nextInt(messages.size()));
                // Broadcast deve ser síncrono (não async) em Bukkit 1.8.8
                org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', message));
            }
        }.runTaskTimer(this, 0L, interval); // runTaskTimer (síncrono), não runTaskTimerAsynchronously
    }

    public static ChatPlugin getInstance() {
        return instance;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }

    public DiscordIntegration getDiscordIntegration() {
        return discordIntegration;
    }
}

