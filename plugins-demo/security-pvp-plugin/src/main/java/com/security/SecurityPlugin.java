package com.security;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal - Segurança e bloqueio de ações não permitidas
 * Grug Brain: Config inline, sem abstrações desnecessárias
 */
public class SecurityPlugin extends JavaPlugin {

    private SecurityListener securityListener;

    @Override
    public void onEnable() {
        // Salvar config padrão se não existir
        saveDefaultConfig();

        // Registrar listener
        securityListener = new SecurityListener(this);
        getServer().getPluginManager().registerEvents(securityListener, this);

        getLogger().info("SecurityPvP plugin habilitado");
    }

    @Override
    public void onDisable() {
        getLogger().info("SecurityPvP plugin desabilitado");
    }

    /**
     * Obtém o listener de segurança
     */
    public SecurityListener getSecurityListener() {
        return securityListener;
    }
}

