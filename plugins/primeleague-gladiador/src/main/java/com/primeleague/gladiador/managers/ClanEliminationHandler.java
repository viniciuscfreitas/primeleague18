package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handler para eliminação de clans
 * Grug Brain: Lógica isolada, efeitos e notificações
 */
public class ClanEliminationHandler {

    private final GladiadorPlugin plugin;
    private final BroadcastManager broadcastManager;

    public ClanEliminationHandler(GladiadorPlugin plugin, BroadcastManager broadcastManager) {
        this.plugin = plugin;
        this.broadcastManager = broadcastManager;
    }

    /**
     * Processa eliminação de clan
     * Grug Brain: Broadcast via BroadcastFormatter, stats, efeitos, Discord
     */
    public void handleElimination(ClanEntry clanEntry, GladiadorMatch match, int clansBeforeElimination) {
        int remainingClans = match.getAliveClansCount();

        // Usar BroadcastFormatter ao invés de duplicar código
        broadcastManager.broadcastClanEliminated(clanEntry, remainingClans);

        plugin.getStatsManager().incrementParticipation(clanEntry.getClanId());
        plugin.getDiscordIntegration().sendClanEliminated(clanEntry.getClanTag(), remainingClans);

        playEliminationEffects(clanEntry);
        checkTwoClansRemaining(clansBeforeElimination, remainingClans);
    }

    /**
     * Toca efeitos de eliminação
     * Grug Brain: Partículas e som
     */
    private void playEliminationEffects(ClanEntry clanEntry) {
        for (UUID uuid : clanEntry.getRemainingPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                Location loc = p.getLocation();
                for (int i = 0; i < 10; i++) {
                    double offsetX = (Math.random() - 0.5) * 2.0;
                    double offsetY = Math.random() * 2.0;
                    double offsetZ = (Math.random() - 0.5) * 2.0;
                    Location effectLoc = loc.clone().add(offsetX, offsetY, offsetZ);
                    p.getWorld().playEffect(effectLoc, org.bukkit.Effect.MOBSPAWNER_FLAMES, 0);
                    p.getWorld().playEffect(effectLoc, org.bukkit.Effect.SMOKE, 0);
                }
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOnline()) {
                p.playSound(p.getLocation(), org.bukkit.Sound.AMBIENCE_THUNDER, 1f, 1f);
            }
        }
    }

    /**
     * Verifica se restam apenas 2 clans
     * Grug Brain: Anúncio especial quando chega em 2
     * Nota: clansBeforeElimination é calculado após removePlayer, então já reflete o estado após eliminação
     * Se remainingClans == 2, significa que agora há 2 clans (antes havia 3+)
     */
    private void checkTwoClansRemaining(int clansBeforeElimination, int remainingClans) {
        // clansBeforeElimination já conta após remover o player do clan eliminado
        // Se remainingClans == 2, significa que agora há 2 clans vivos
        // Mostrar mensagem quando restam exatamente 2 (batalha final)
        // Usar [GLADIADOR] ao invés de ⚔ para compatibilidade 1.8.8
        if (remainingClans == 2) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&6&l[GLADIADOR] &c&lAPENAS 2 CLANS RESTAM! &eA batalha final começou!"));

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOnline()) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.WITHER_SPAWN, 1f, 1f);
                }
            }
        }
    }
}


