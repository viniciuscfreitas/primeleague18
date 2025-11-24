package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.scheduler.BukkitTask;

/**
 * Gerenciador de broadcasts do Gladiador
 * Grug Brain: Agendamento de broadcasts, delega formatação para BroadcastFormatter
 */
public class BroadcastManager {

    private final GladiadorPlugin plugin;
    private final BroadcastFormatter formatter;
    private BukkitTask statusBroadcastTask;

    public BroadcastManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.formatter = new BroadcastFormatter();
    }

    /**
     * Inicia broadcast periódico de status
     * Grug Brain: Task a cada 30 segundos durante match ativo
     */
    public void startStatusBroadcast(GladiadorMatch match) {
        if (statusBroadcastTask != null) {
            statusBroadcastTask.cancel();
        }

        statusBroadcastTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (match == null || match.getState() != GladiadorMatch.MatchState.ACTIVE) {
                    this.cancel();
                    return;
                }
                formatter.broadcastMatchStatus(match);
            }
        }.runTaskTimer(plugin, 600L, 600L);
    }

    /**
     * Para broadcast periódico
     */
    public void stopStatusBroadcast() {
        if (statusBroadcastTask != null) {
            statusBroadcastTask.cancel();
            statusBroadcastTask = null;
        }
    }

    /**
     * Broadcast de início de match
     * Grug Brain: Recebe waitTimeMinutes do config
     */
    public void broadcastMatchStarted(int waitTimeMinutes) {
        formatter.broadcastMatchStarted(waitTimeMinutes);
    }

    /**
     * Broadcast de início de preparação
     */
    public void broadcastPreparation(GladiadorMatch match, int prepTimeSeconds) {
        formatter.broadcastPreparation(match, prepTimeSeconds);
    }

    /**
     * Broadcast de PvP ativado
     */
    public void broadcastPvPActivated(GladiadorMatch match) {
        formatter.broadcastPvPActivated(match);
    }

    /**
     * Broadcast de vitória
     */
    public void broadcastWinner(ClanEntry winner, GladiadorMatch match, double coinsReward,
                               String mvpPlayerName, int mvpKills, String damagePlayerName, double damageAmount) {
        formatter.broadcastWinner(winner, match, coinsReward, mvpPlayerName, mvpKills, damagePlayerName, damageAmount);
    }

    /**
     * Broadcast de clan eliminado
     */
    public void broadcastClanEliminated(ClanEntry eliminated, int remainingClans) {
        formatter.broadcastClanEliminated(eliminated, remainingClans);
    }

    /**
     * Broadcast de cancelamento
     */
    public void broadcastCancelled(String reason) {
        formatter.broadcastCancelled(reason);
    }
}

