package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.models.ClanEntry;
import org.bukkit.ChatColor;

import java.util.Collection;

/**
 * Formatador de lista de clans para mensagens
 * Grug Brain: Utilitário simples, sem dependências
 */
public class ClanListFormatter {

    /**
     * Formata lista de clans para broadcast
     * Grug Brain: Quebra linha automaticamente se necessário
     */
    public static void broadcastClanList(Collection<ClanEntry> clans, int maxClansPerLine) {
        StringBuilder clansList = new StringBuilder();
        int clansPerLine = 0;

        for (ClanEntry clan : clans) {
            if (clansPerLine >= maxClansPerLine) {
                org.bukkit.Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&e" + clansList.toString()));
                clansList = new StringBuilder();
                clansPerLine = 0;
            }

            if (clansPerLine > 0) {
                clansList.append(ChatColor.WHITE).append(", ");
            }

            // Usar cores originais da tag (ClansPlugin armazena com cores)
            clansList.append(clan.getClanTag())
                    .append(ChatColor.RESET).append(ChatColor.WHITE).append(" ⌈")
                    .append(ChatColor.AQUA).append(clan.getRemainingPlayersCount())
                    .append(ChatColor.WHITE).append("⌋");

            clansPerLine++;
        }

        if (clansList.length() > 0) {
            org.bukkit.Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&e" + clansList.toString()));
        }
    }

}






