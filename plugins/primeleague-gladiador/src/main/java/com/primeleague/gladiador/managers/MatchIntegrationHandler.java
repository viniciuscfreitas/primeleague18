package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handler para integrações do match (scoreboard, tablist)
 * Grug Brain: Lógica isolada, delega para integrações
 */
public class MatchIntegrationHandler {

    private final GladiadorPlugin plugin;

    public MatchIntegrationHandler(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Configura integrações no início do match
     * Grug Brain: Ativa scoreboard e tablist para todos os players
     */
    public void setupIntegrations(GladiadorMatch match) {
        if (plugin.getScoreboardIntegration().isEnabled()) {
            for (UUID uuid : match.getAlivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    plugin.getScoreboardIntegration().setScoreboard(p, "gladiador-match");
                }
            }
            plugin.getScoreboardIntegration().startUpdateTask(match);
        }

        if (plugin.getTabIntegration().isEnabled()) {
            for (UUID uuid : match.getAlivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    plugin.getTabIntegration().updateTablist(p);
                }
            }
        }
    }

    /**
     * Limpa integrações no cancelamento
     * Grug Brain: Remove scoreboard e tablist
     */
    public void clearIntegrationsOnCancel(GladiadorMatch match) {
        if (plugin.getScoreboardIntegration().isEnabled()) {
            plugin.getScoreboardIntegration().stopUpdateTask();
            for (ClanEntry entry : match.getClanEntries()) {
                for (UUID uuid : entry.getMembers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        plugin.getScoreboardIntegration().clearScoreboard(p);
                    }
                }
            }
        }
    }
}


