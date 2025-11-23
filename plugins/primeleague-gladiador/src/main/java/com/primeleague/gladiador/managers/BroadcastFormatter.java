package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Formatador de mensagens de broadcast
 * Grug Brain: Formata mensagens simples, direto
 */
public class BroadcastFormatter {

    /**
     * Formata e envia broadcast de início de match
     */
    public void broadcastMatchStarted() {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "⚔ " + ChatColor.YELLOW + ChatColor.BOLD + "EVENTO GLADIADOR" + ChatColor.GOLD + " ⚔");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "O evento foi iniciado! Digite " + ChatColor.GREEN + ChatColor.BOLD + "/gladiador" + ChatColor.YELLOW + " para participar");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Requisito: Estar em um clan");
        Bukkit.broadcastMessage("");
    }

    /**
     * Formata e envia broadcast de preparação
     */
    public void broadcastPreparation(GladiadorMatch match, int prepTimeSeconds) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "⚔ " + ChatColor.YELLOW + ChatColor.BOLD + "GLADIADOR" + ChatColor.GOLD + " ⚔");
        Bukkit.broadcastMessage(ChatColor.RED + "As entradas foram fechadas!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Clans: " + ChatColor.WHITE + ChatColor.BOLD + match.getClanEntries().size() +
                                ChatColor.YELLOW + " | Jogadores: " + ChatColor.WHITE + ChatColor.BOLD + match.getTotalPlayers());

        int minutes = prepTimeSeconds / 60;
        int seconds = prepTimeSeconds % 60;
        String timeStr = minutes > 0 ? minutes + " minuto" + (minutes > 1 ? "s" : "") + (seconds > 0 ? " e " + seconds + "s" : "") : seconds + " segundos";
        Bukkit.broadcastMessage(ChatColor.YELLOW + "O PvP será ativado em " + ChatColor.WHITE + ChatColor.BOLD + timeStr);
        Bukkit.broadcastMessage("");
    }

    /**
     * Formata e envia broadcast de PvP ativado
     */
    public void broadcastPvPActivated() {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.RED.toString() + ChatColor.BOLD.toString() + "⚔ " + ChatColor.DARK_RED.toString() + ChatColor.BOLD.toString() + "VALENDO!" + ChatColor.RED.toString() + ChatColor.BOLD.toString() + " ⚔");
        Bukkit.broadcastMessage(ChatColor.RED + "O PvP foi ativado! " + ChatColor.YELLOW + "Boa sorte aos clans!");
        Bukkit.broadcastMessage("");
    }

    /**
     * Formata e envia broadcast de status do match
     */
    public void broadcastMatchStatus(GladiadorMatch match) {
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
            clansList.append(ChatColor.WHITE).append("[").append(clan.getClanTag()).append("]")
                    .append(ChatColor.GRAY).append(" (").append(clan.getRemainingPlayersCount()).append(")");
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "⚔ " + ChatColor.YELLOW + ChatColor.BOLD + "GLADIADOR EM ANDAMENTO" + ChatColor.GOLD + " ⚔");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Clans vivos: " + ChatColor.WHITE + ChatColor.BOLD + aliveClans.size() + 
                                ChatColor.YELLOW + " | Players vivos: " + ChatColor.WHITE + ChatColor.BOLD + alivePlayers);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Tempo decorrido: " + ChatColor.WHITE + ChatColor.BOLD + timeStr);
        if (clansList.length() > 0) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Clans: " + clansList.toString());
        }
        Bukkit.broadcastMessage("");
    }

    /**
     * Formata e envia broadcast de vitória
     */
    public void broadcastWinner(ClanEntry winner) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "🏆 GLADIADOR FINALIZADO 🏆");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Clan vencedor: " + ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "[" + winner.getClanTag() + "]");
        Bukkit.broadcastMessage(ChatColor.GREEN + "Parabéns pela vitória!");
        Bukkit.broadcastMessage("");
    }

    /**
     * Formata e envia broadcast de clan eliminado
     */
    public void broadcastClanEliminated(ClanEntry eliminated, int remainingClans) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.RED + "❌ O clan " + ChatColor.BOLD + eliminated.getClanTag() + ChatColor.RED + " foi eliminado!");

        if (remainingClans > 1) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Restam " + remainingClans + " clans na arena.");
        } else if (remainingClans == 1) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "Apenas 1 clan resta na arena!");
        }
        Bukkit.broadcastMessage("");
    }

    /**
     * Formata e envia broadcast de cancelamento
     */
    public void broadcastCancelled(String reason) {
        Bukkit.broadcastMessage(ChatColor.RED + "Gladiador cancelado: " + reason);
    }
}

