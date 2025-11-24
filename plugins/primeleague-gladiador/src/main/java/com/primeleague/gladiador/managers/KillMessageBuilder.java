package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Construtor de mensagens de kill
 * Grug Brain: Lógica isolada, formatação direta, compatível com 1.8.8 (sem Unicode)
 */
public class KillMessageBuilder {

    /**
     * Constrói mensagem de morte
     * Grug Brain: Formata mensagem compatível com 1.8.8, sem caracteres Unicode
     * Formato: [GLADIADOR] [CLAN_TAG] player [kills] matou [CLAN_TAG] victim [kills]
     */
    public static String buildDeathMessage(Player victim, Player killer, ClanEntry victimClanEntry,
                                          GladiadorMatch match) {
        if (killer != null) {
            ClanEntry killerClanEntry = match.getClanEntry(killer.getUniqueId());
            String killerClanTag = killerClanEntry != null ? killerClanEntry.getClanTag() : "???";

            // Obter kills do killer e da vítima
            int killerKills = match.getPlayerKills(killer.getUniqueId());
            int victimKills = match.getPlayerKills(victim.getUniqueId());

            // Cor baseada em kills: >7 = roxo (&5), >5 = vermelho escuro (&4), >3 = laranja (&6), senão cinza (&7)
            String killColor;
            if (killerKills > 7) {
                killColor = "&5"; // Roxo
            } else if (killerKills > 5) {
                killColor = "&4"; // Vermelho escuro
            } else if (killerKills > 3) {
                killColor = "&6"; // Laranja
            } else {
                killColor = "&7"; // Cinza claro (padrão)
            }

            // Formato: &6&l[GLADIADOR] &f[CLAN] &fplayer [kills] &ematou &f[CLAN] &fvictim [kills]
            // Grug Brain: Usar cores originais das tags (ClansPlugin armazena com cores), mostrar kills
            // Usar &r dentro de translateAlternateColorCodes para consistência
            return ChatColor.translateAlternateColorCodes('&',
                "&6&l[GLADIADOR] &f[" + killerClanTag + "&r&f] &f" + killer.getName() +
                " " + killColor + "[" + killerKills + "] &ematou &f[" + victimClanEntry.getClanTag() + "&r" +
                "&f] &f" + victim.getName() + " &7[" + victimKills + "]");
        } else if (!victim.isOnline()) {
            return ChatColor.translateAlternateColorCodes('&',
                "&6&l[GLADIADOR] &f[" + victimClanEntry.getClanTag() + "&r&f] &f" + victim.getName() +
                " &edesconectou e foi eliminado!");
        } else {
            return ChatColor.translateAlternateColorCodes('&',
                "&6&l[GLADIADOR] &f[" + victimClanEntry.getClanTag() + "&r&f] &f" + victim.getName() +
                " &efoi eliminado por PvE");
        }
    }
}


