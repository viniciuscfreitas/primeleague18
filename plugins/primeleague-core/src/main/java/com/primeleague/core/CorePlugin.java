package com.primeleague.core;

import com.primeleague.core.database.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal - Core do Primeleague
 * Grug Brain: Singleton simples, config inline
 */
public class CorePlugin extends JavaPlugin {

    private static CorePlugin instance;
    private DatabaseManager databaseManager;

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

        getLogger().info("PrimeleagueCore habilitado - PostgreSQL conectado");
    }

    @Override
    public void onDisable() {
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
}

