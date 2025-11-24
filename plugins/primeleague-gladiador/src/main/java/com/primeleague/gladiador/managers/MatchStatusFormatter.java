package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Formatador de status do match
 * Grug Brain: Utilitário simples para broadcast de status
 */
public class MatchStatusFormatter {

    /**
     * Formata e envia broadcast de status do match
     * Grug Brain: Lógica direta, formatação simples
     */
    public static void broadcastStatus(GladiadorMatch match) {
        if (match == null || match.getState() != GladiadorMatch.MatchState.ACTIVE) {
            return;
        }

        List<ClanEntry> aliveClans = match.getClanEntries().stream()
                .filter(c -> c.getRemainingPlayersCount() > 0)
                .collect(Collectors.toList());

        int alivePlayers = match.getAlivePlayersCount();
        long elapsedSeconds = (System.currentTimeMillis() - match.getStartTime()) / 1000;
        long minutes = elapsedSeconds / 60;
        long seconds = elapsedSeconds % 60;
        String timeStr = (minutes > 0 ? minutes + "m " : "") + seconds + "s";

        StringBuilder clansList = new StringBuilder();
        for (int i = 0; i < aliveClans.size(); i++) {
            ClanEntry clan = aliveClans.get(i);
            if (i > 0) clansList.append(ChatColor.GRAY).append(", ");
            // Usar cores originais da tag (ClansPlugin armazena com cores)
            clansList.append(ChatColor.WHITE).append("[").append(clan.getClanTag()).append(ChatColor.RESET).append(ChatColor.WHITE).append("]")
                    .append(ChatColor.GRAY).append(" (").append(clan.getRemainingPlayersCount()).append(")");
        }

        // Consolidar em 2 linhas: status + lista de clans
        // Usar [GLADIADOR] ao invés de ⚔ para compatibilidade 1.8.8
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&6&l[GLADIADOR] &e&l" + aliveClans.size() + " &eclans, &f&l" + alivePlayers + " &eplayers | &f&l" + timeStr));
        if (clansList.length() > 0) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&e" + clansList.toString()));
        }
    }
}


