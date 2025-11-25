package com.primeleague.core;

import com.primeleague.core.database.DatabaseManager;
import com.primeleague.core.integrations.CorePlaceholderExpansion;
import com.primeleague.core.listeners.DefaultMessagesListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal - Core do Primeleague
 * Grug Brain: Singleton simples, config inline
 */
public class CorePlugin extends JavaPlugin {

    private static CorePlugin instance;
    private DatabaseManager databaseManager;
    private CorePlaceholderExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        instance = this;

        // Salvar config padrão se não existir
        saveDefaultConfig();

        // Inicializar database manager
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Falha ao conectar ao PostgreSQL. Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Setup PlaceholderAPI (se disponível)
        setupPlaceholderAPI();

        // Registrar listener para desativar mensagens padrão
        getServer().getPluginManager().registerEvents(new DefaultMessagesListener(this), this);

        // Registrar consolidador de recompensas PvP (Fase 2)
        getServer().getPluginManager().registerEvents(new com.primeleague.core.listeners.PvPRewardConsolidator(this), this);

        getLogger().info("PrimeleagueCore habilitado - PostgreSQL conectado");
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

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("PrimeleagueCore desabilitado");
    }

    public static CorePlugin getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Setup PlaceholderAPI integration
     * Grug Brain: Método separado seguindo padrão dos outros plugins
     */
    private void setupPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI não encontrado - placeholders de Core não estarão disponíveis");
            return;
        }

        try {
            placeholderExpansion = new CorePlaceholderExpansion();
            if (placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI integration habilitada (%core_health%)");
            } else {
                getLogger().warning("Falha ao registrar PlaceholderAPI expansion");
            }
        } catch (Exception e) {
            getLogger().warning("Erro ao configurar PlaceholderAPI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

