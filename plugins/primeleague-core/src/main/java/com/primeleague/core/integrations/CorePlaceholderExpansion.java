package com.primeleague.core.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI Expansion para Core
 * Grug Brain: Retorna health como inteiro para evitar avisos do TAB
 * Compatível com Paper 1.8.8 (player.getHealth() retorna double, cast para int)
 */
public class CorePlaceholderExpansion extends PlaceholderExpansion {

    @Override
    public String getIdentifier() {
        return "core";
    }

    @Override
    public String getAuthor() {
        return "Primeleague";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // Persistir mesmo se PlaceholderAPI recarregar
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        // %core_health% - Health como inteiro (sem decimal)
        // Paper 1.8.8: getHealth() retorna double, cast para int remove decimais
        if (identifier.equals("health")) {
            return String.valueOf((int) player.getHealth());
        }

        return null; // Placeholder desconhecido
    }
}

