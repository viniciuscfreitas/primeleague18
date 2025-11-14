package com.primeleague.economy.integrations;

import com.primeleague.economy.EconomyAPI;
import com.primeleague.economy.EconomyPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * PlaceholderAPI Expansion para Economy
 * Grug Brain: Placeholders simples e diretos
 */
public class PlaceholderAPIExpansion extends PlaceholderExpansion {

    private final EconomyPlugin plugin;

    public PlaceholderAPIExpansion(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "economy";
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

        UUID uuid = player.getUniqueId();

        // %economy_balance% - Saldo atual
        if (identifier.equals("balance")) {
            double balance = EconomyAPI.getBalance(uuid);
            return String.format("%.2f", balance);
        }

        // %economy_balance_formatted% - Saldo formatado com símbolo
        if (identifier.equals("balance_formatted")) {
            double balance = EconomyAPI.getBalance(uuid);
            String currency = plugin.getConfig().getString("economy.simbolo", "¢");
            return String.format("%.2f %s", balance, currency);
        }

        // %economy_top_name_<pos>% - Nome do top player na posição X
        if (identifier.startsWith("top_name_")) {
            try {
                int position = Integer.parseInt(identifier.substring(9));
                if (position < 1 || position > 10) {
                    return "";
                }
                // Query async não é possível aqui - retornar vazio ou fazer sync
                // PlaceholderAPI não suporta async, então retornamos vazio
                // O cache não armazena a lista, apenas a string formatada
                return "";
            } catch (NumberFormatException e) {
                return "";
            }
        }

        // %economy_top_balance_<pos>% - Saldo do top player na posição X
        if (identifier.startsWith("top_balance_")) {
            try {
                int position = Integer.parseInt(identifier.substring(12));
                if (position < 1 || position > 10) {
                    return "";
                }
                // PlaceholderAPI não suporta async - retornar vazio
                return "";
            } catch (NumberFormatException e) {
                return "";
            }
        }

        return null; // Placeholder desconhecido
    }
}

