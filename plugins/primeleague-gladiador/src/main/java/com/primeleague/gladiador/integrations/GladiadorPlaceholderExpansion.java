package com.primeleague.gladiador.integrations;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.managers.MatchManager;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

/**
 * PlaceholderAPI expansion para Gladiador
 * Grug Brain: Softdepend - só funciona se PlaceholderAPI estiver presente
 */
public class GladiadorPlaceholderExpansion {

    private final GladiadorPlugin plugin;

    public GladiadorPlaceholderExpansion(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    public String getIdentifier() {
        return "gladiador";
    }

    public String getAuthor() {
        return "Primeleague";
    }

    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onPlaceholderRequest(Player player, String params) {
        MatchManager matchManager = plugin.getMatchManager();
        GladiadorMatch match = matchManager.getCurrentMatch();

        if (params.equals("status")) {
            if (match == null) return "Off";
            switch (match.getState()) {
                case WAITING: return "Aguardando";
                case PREPARATION: return "Preparando";
                case ACTIVE: return "Em Andamento";
                case ENDING: return "Finalizando";
                default: return "Off";
            }
        }

        if (params.equals("players")) {
            return match == null ? "0" : String.valueOf(match.getTotalPlayers());
        }

        if (params.equals("clans")) {
            return match == null ? "0" : String.valueOf(match.getAliveClansCount());
        }

        if (params.startsWith("clan_count_")) {
            if (match == null) return "0";
            String tag = params.replace("clan_count_", "");
            for (ClanEntry entry : match.getClanEntries()) {
                if (entry.getClanTag().equalsIgnoreCase(tag)) {
                    return String.valueOf(entry.getRemainingPlayersCount());
                }
            }
            return "0";
        }

        if (params.equals("clans_list")) {
            if (match == null) return "";
            return match.getClanEntries().stream()
                    .filter(c -> c.getRemainingPlayersCount() > 0)
                    .map(c -> c.getClanTag() + "(" + c.getRemainingPlayersCount() + ")")
                    .collect(Collectors.joining(", "));
        }

        return null;
    }
}
