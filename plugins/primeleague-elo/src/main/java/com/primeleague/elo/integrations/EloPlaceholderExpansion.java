package com.primeleague.elo.integrations;

import com.primeleague.elo.EloPlugin;
import com.primeleague.elo.EloAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * PlaceholderAPI Expansion para ELO
 * Grug Brain: Placeholders simples e diretos, queries sync
 * Fornece %elo_symbol% baseado em ranges configuráveis
 */
public class EloPlaceholderExpansion extends PlaceholderExpansion {

    private final EloPlugin plugin;
    private List<EloRange> eloRanges;

    public EloPlaceholderExpansion(EloPlugin plugin) {
        this.plugin = plugin;
        loadEloRanges();
    }

    @Override
    public String getIdentifier() {
        return "elo";
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
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        int elo = EloAPI.getElo(player.getUniqueId());

        switch (identifier) {
            case "symbol":
                return getEloSymbol(elo);

            case "value":
                return String.valueOf(elo);

            default:
                return null;
        }
    }

    /**
     * Obtém símbolo baseado no ELO
     * Grug Brain: Busca direta nos ranges, sem abstrações
     */
    private String getEloSymbol(int elo) {
        if (eloRanges == null || eloRanges.isEmpty()) {
            return String.valueOf(elo); // Fallback para número se não houver ranges
        }

        // Buscar range correspondente (do maior para menor)
        for (EloRange range : eloRanges) {
            if (elo >= range.getMin() && (range.getMax() == -1 || elo <= range.getMax())) {
                return range.getSymbol();
            }
        }

        // Se não encontrou e ELO é menor que o menor range, usar o primeiro (menor)
        // Se ELO é maior que todos, usar o último (maior, geralmente com max = -1)
        if (elo < eloRanges.get(eloRanges.size() - 1).getMin()) {
            return eloRanges.get(eloRanges.size() - 1).getSymbol(); // Menor range
        } else {
            return eloRanges.get(0).getSymbol(); // Maior range (geralmente sem max)
        }
    }

    /**
     * Carrega ranges de ELO do config
     * Grug Brain: Parse direto do config, sem validações excessivas
     */
    private void loadEloRanges() {
        eloRanges = new ArrayList<>();
        ConfigurationSection ranksSection = plugin.getConfig().getConfigurationSection("ranks");

        if (ranksSection == null) {
            // Valores padrão se não houver config
            eloRanges.add(new EloRange(0, 499, "§7●"));
            eloRanges.add(new EloRange(500, 999, "§e●"));
            eloRanges.add(new EloRange(1000, 1499, "§f●"));
            eloRanges.add(new EloRange(1500, 1999, "§a●"));
            eloRanges.add(new EloRange(2000, -1, "§b●"));
            return;
        }

        // Ler ranges do config
        for (String key : ranksSection.getKeys(false)) {
            ConfigurationSection rankSection = ranksSection.getConfigurationSection(key);
            if (rankSection == null) {
                continue;
            }

            int min = rankSection.getInt("min", 0);
            int max = rankSection.getInt("max", -1); // -1 = sem máximo
            String symbol = rankSection.getString("symbol", "●");

            eloRanges.add(new EloRange(min, max, symbol));
        }

        // Ordenar por min (maior primeiro para busca eficiente)
        eloRanges.sort((a, b) -> Integer.compare(b.getMin(), a.getMin()));

        plugin.getLogger().info("Carregados " + eloRanges.size() + " ranges de ELO");
    }

    /**
     * Recarrega ranges do config (útil para reload)
     */
    public void reload() {
        loadEloRanges();
    }

    /**
     * Classe interna para range de ELO
     */
    private static class EloRange {
        private final int min;
        private final int max;
        private final String symbol;

        public EloRange(int min, int max, String symbol) {
            this.min = min;
            this.max = max;
            this.symbol = symbol;
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        public String getSymbol() {
            return symbol;
        }
    }
}

