package com.primeleague.auth;

import com.primeleague.auth.listeners.AuthListener;
import com.primeleague.auth.utils.CodeValidator;
import com.primeleague.core.CoreAPI;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin de autenticação - Primeleague
 * Grug Brain: Plugin simples, depende do Core via CoreAPI
 */
public class AuthPlugin extends JavaPlugin {

    private static AuthPlugin instance;
    private AuthListener authListener;
    private CodeValidator codeValidator;

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

        // Criar CodeValidator
        codeValidator = new CodeValidator(this);

        // Registrar listener
        authListener = new AuthListener(this);
        getServer().getPluginManager().registerEvents(authListener, this);

        getLogger().info("PrimeleagueAuth habilitado");
    }

    @Override
    public void onDisable() {
        getLogger().info("PrimeleagueAuth desabilitado");
    }

    public static AuthPlugin getInstance() {
        return instance;
    }

    public AuthListener getAuthListener() {
        return authListener;
    }

    public CodeValidator getCodeValidator() {
        return codeValidator;
    }
}

