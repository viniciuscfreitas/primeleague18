package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * Formatador de mensagens de broadcast
 * Grug Brain: Formata mensagens simples, direto
 */
public class BroadcastFormatter {

    /**
     * Formata e envia broadcast de início de match
     * Grug Brain: Mensagem consolidada, minimalista
     */
    public void broadcastMatchStarted(int waitTimeMinutes) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&6&l[GLADIADOR] &eEvento iniciado! Digite &6/gladiador &epara entrar (&c" + waitTimeMinutes + " min restantes&e)"));
    }

    /**
     * Formata e envia broadcast de preparação
     * Grug Brain: Mensagem clara sobre countdown de 10 segundos
     */
    public void broadcastPreparation(GladiadorMatch match, int prepTimeSeconds) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&6&l[GLADIADOR] &eEvento fechado! Preparando combate..."));

        // Mostrar lista de clans participantes antes do combate
        if (match != null && !match.getClanEntries().isEmpty()) {
            ClanListFormatter.broadcastClanList(match.getClanEntries(), 4);
        }
    }

    /**
     * Formata e envia broadcast de PvP ativado
     * Grug Brain: Mensagem consolidada, minimalista
     */
    public void broadcastPvPActivated(GladiadorMatch match) {
        int totalClans = match.getClanEntries().size();
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&6&l[GLADIADOR] &eCombate iniciado! Participando: &b" + totalClans + " &eclans"));

        // Formatar lista de clans via ClanListFormatter
        ClanListFormatter.broadcastClanList(match.getClanEntries(), 4);
    }


    /**
     * Formata e envia broadcast de status do match
     * Grug Brain: Delega para MatchStatusFormatter
     */
    public void broadcastMatchStatus(GladiadorMatch match) {
        MatchStatusFormatter.broadcastStatus(match);
    }

    /**
     * Formata e envia broadcast de vitória
     * Grug Brain: Mensagem completa com tempo, kills, coins
     */
    public void broadcastWinner(ClanEntry winner, GladiadorMatch match, double coinsReward,
                                String mvpPlayerName, int mvpKills, String damagePlayerName, double damageAmount) {
        // Calcular tempo total do match
        long durationSeconds = (System.currentTimeMillis() - match.getStartTime()) / 1000;
        long minutes = durationSeconds / 60;
        long seconds = durationSeconds % 60;
        String timeStr = (minutes > 0 ? minutes + "m " : "") + seconds + "s";

        // Formatar coins via MessageFormatter
        String coinsStr = MessageFormatter.formatCoins(coinsReward);

        // Total de kills do clan vencedor
        int winnerKills = winner.getKills();

        // Usar cores originais da tag (ClansPlugin armazena com cores)
        // Grug Brain: Usar &r dentro de translateAlternateColorCodes para consistência
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&6&l[GLADIADOR] &eClan &f[" + winner.getClanTag() + "&r&f] &evenceu! &7(" + timeStr + "&7) | &b" + winnerKills + " kills &e| &2" + coinsStr + " &ecoins"));

        // MVP e DAMAGE em linha única se ambos existirem
        // Usar cores originais das tags (ClansPlugin armazena com cores)
        if (mvpPlayerName != null && damagePlayerName != null) {
            org.bukkit.entity.Player damagePlayer = org.bukkit.Bukkit.getPlayer(damagePlayerName);
            String damageClanTag = "???";
            if (damagePlayer != null) {
                ClanEntry damageClan = match.getClanEntry(damagePlayer.getUniqueId());
                if (damageClan != null) {
                    damageClanTag = damageClan.getClanTag();
                }
            }
            String damageStr = MessageFormatter.formatDamage(damageAmount);
            // Usar >> ao invés de ➜ para compatibilidade 1.8.8
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&7>> &e[MVP] &f[" + winner.getClanTag() + "&r&f] " + mvpPlayerName + " (&b" + mvpKills + "&e kills) &7| &c[DAMAGE] &f[" + damageClanTag + "&r&f] " + damagePlayerName + " (&b" + damageStr + "&e)"));
        } else if (mvpPlayerName != null) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&7>> &e[MVP] &f[" + winner.getClanTag() + "&r&f] " + mvpPlayerName + " (&b" + mvpKills + "&e kills)"));
        } else if (damagePlayerName != null) {
            org.bukkit.entity.Player damagePlayer = org.bukkit.Bukkit.getPlayer(damagePlayerName);
            String damageClanTag = "???";
            if (damagePlayer != null) {
                ClanEntry damageClan = match.getClanEntry(damagePlayer.getUniqueId());
                if (damageClan != null) {
                    damageClanTag = damageClan.getClanTag();
                }
            }
            String damageStr = MessageFormatter.formatDamage(damageAmount);
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&7>> &c[DAMAGE] &f[" + damageClanTag + "&r&f] " + damagePlayerName + " (&b" + damageStr + "&e de dano)"));
        }
    }


    /**
     * Formata e envia broadcast de clan eliminado
     * Grug Brain: Mensagem consolidada, símbolos ASCII compatíveis com 1.8.8
     */
    public void broadcastClanEliminated(ClanEntry eliminated, int remainingClans) {
        // Usar cores originais da tag (ClansPlugin armazena com cores)
        // Grug Brain: Usar &r dentro de translateAlternateColorCodes para consistência
        // Usar [X] ao invés de ❌ para compatibilidade 1.8.8
        String message;
        if (remainingClans > 1) {
            message = "&c[X] Clan &l" + eliminated.getClanTag() + "&r &celiminado! &eRestam " + remainingClans + " clans";
        } else if (remainingClans == 1) {
            message = "&c[X] Clan &l" + eliminated.getClanTag() + "&r &celiminado! &6&lAPENAS 1 CLAN RESTA!";
        } else {
            message = "&c[X] Clan &l" + eliminated.getClanTag() + "&r &celiminado!";
        }
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Formata e envia broadcast de cancelamento
     */
    public void broadcastCancelled(String reason) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&cGladiador cancelado: " + reason));
    }
}




