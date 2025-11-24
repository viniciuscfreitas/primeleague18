package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Task de countdown do lobby
 * Grug Brain: Lógica isolada, mensagem a cada 1 minuto
 */
public class LobbyCountdownTask {

    /**
     * Inicia task de countdown do lobby
     * Grug Brain: Mensagem consolidada, mostra lista de clans, avisa fechamento
     */
    public static BukkitTask start(GladiadorPlugin plugin, GladiadorMatch match, int waitTimeMinutes) {
        return new BukkitRunnable() {
            int minutesLeft = waitTimeMinutes;

            @Override
            public void run() {
                if (match == null || match.getState() != GladiadorMatch.MatchState.WAITING) {
                    this.cancel();
                    return;
                }

                int totalPlayers = match.getTotalPlayers();
                int totalClans = match.getClanEntries().size();
                String minuteColor = minutesLeft == 1 ? "&4" : "&c";
                String timeText = minutesLeft == 1 ? "minuto" : "minutos";

                // Mensagem principal: tempo + players/clans
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6&l[GLADIADOR] &eDigite &6/gladiador &epara entrar | &c" + minutesLeft + " " + timeText + " restantes&e | &b" + totalPlayers + " players&e, &b" + totalClans + " clans"));

                // Mostrar lista de clans participantes (se houver)
                if (totalClans > 0 && totalClans <= 8) {
                    ClanListFormatter.broadcastClanList(match.getClanEntries(), 4);
                }

                // Aviso especial quando está perto de fechar (último minuto)
                if (minutesLeft == 1) {
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        "&c&l>> ÚLTIMOS 60 SEGUNDOS PARA ENTRAR! <<"));
                }

                minutesLeft--;

                if (minutesLeft < 0) {
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L); // A cada 1 minuto
    }
}


