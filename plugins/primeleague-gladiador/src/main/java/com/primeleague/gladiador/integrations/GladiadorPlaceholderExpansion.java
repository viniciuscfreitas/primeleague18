package com.primeleague.gladiador.integrations;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.managers.MatchManager;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

/**
 * PlaceholderAPI expansion para Gladiador
 * Grug Brain: Softdepend - só funciona se PlaceholderAPI estiver presente
 */
public class GladiadorPlaceholderExpansion extends PlaceholderExpansion {

    private final GladiadorPlugin plugin;

    public GladiadorPlaceholderExpansion(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "gladiador";
    }

    @Override
    public String getAuthor() {
        return "Primeleague";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MatchManager matchManager = plugin.getMatchManager();
        GladiadorMatch match = matchManager.getCurrentMatch();

        if (match == null) {
            // Retornar valores padrão quando não há match
            if (params.equals("status")) return "Off";
            if (params.equals("players") || params.equals("players_alive")) return "0";
            if (params.equals("clans") || params.equals("clans_alive")) return "0";
            if (params.equals("arena")) return "Nenhuma";
            if (params.equals("my_clan")) return "Nenhum";
            if (params.equals("my_clan_kills")) return "0";
            if (params.equals("my_clan_players")) return "0";
            if (params.equals("time")) return "0s";
            return null;
        }

        if (params.equals("status")) {
            switch (match.getState()) {
                case WAITING: return "Aguardando";
                case PREPARATION: return "Preparando";
                case ACTIVE: return "Em Andamento";
                case ENDING: return "Finalizando";
                default: return "Off";
            }
        }

        if (params.equals("players")) {
            return String.valueOf(match.getTotalPlayers());
        }

        if (params.equals("players_alive")) {
            return String.valueOf(match.getAlivePlayersCount());
        }

        if (params.equals("clans")) {
            return String.valueOf(match.getClanEntries().size());
        }

        if (params.equals("clans_alive")) {
            return String.valueOf(match.getAliveClansCount());
        }

        if (params.equals("arena")) {
            return match.getArena().getName();
        }

        if (params.equals("time")) {
            if (match.getStartTime() == 0) return "0s";
            long elapsedSeconds = (System.currentTimeMillis() - match.getStartTime()) / 1000;
            long minutes = elapsedSeconds / 60;
            long seconds = elapsedSeconds % 60;
            return (minutes > 0 ? minutes + "m " : "") + seconds + "s";
        }

        // Placeholders do player atual
        if (player != null && match.hasPlayer(player.getUniqueId())) {
            ClanEntry playerClan = match.getClanEntry(player.getUniqueId());
            if (playerClan != null) {
                if (params.equals("my_clan")) {
                    return playerClan.getClanTag();
                }
                if (params.equals("my_clan_kills")) {
                    return String.valueOf(playerClan.getKills());
                }
                if (params.equals("my_clan_players")) {
                    return String.valueOf(playerClan.getRemainingPlayersCount());
                }
            }
        }

        if (params.startsWith("clan_count_")) {
            String tag = params.replace("clan_count_", "");
            for (ClanEntry entry : match.getClanEntries()) {
                if (entry.getClanTag().equalsIgnoreCase(tag)) {
                    return String.valueOf(entry.getRemainingPlayersCount());
                }
            }
            return "0";
        }

        if (params.equals("clans_list")) {
            return match.getClanEntries().stream()
                    .filter(c -> c.getRemainingPlayersCount() > 0)
                    .map(c -> c.getClanTag() + "(" + c.getRemainingPlayersCount() + ")")
                    .collect(Collectors.joining(", "));
        }

        // Placeholder de tag temporária
        if (params.equals("tag") && player != null) {
            return plugin.getTitleManager().getTitleSync(player.getUniqueId());
        }

        return null;
    }
}
