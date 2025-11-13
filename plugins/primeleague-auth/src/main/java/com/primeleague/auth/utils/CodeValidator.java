package com.primeleague.auth.utils;

import com.primeleague.auth.AuthPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Validador de códigos de acesso
 * Grug Brain: Validação simples, direta
 */
public class CodeValidator {

    private final AuthPlugin plugin;

    public CodeValidator(AuthPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isValid(String code) {
        FileConfiguration config = plugin.getConfig();
        List<String> validCodes = config.getStringList("auth.access-codes");
        return validCodes.contains(code);
    }
}

