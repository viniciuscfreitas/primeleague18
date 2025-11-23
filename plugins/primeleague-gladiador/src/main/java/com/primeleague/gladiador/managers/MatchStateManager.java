package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Gerenciador de estados do Match
 * Grug Brain: Gerencia transições de estado (WAITING -> PREPARATION -> ACTIVE -> ENDING)
 */
public class MatchStateManager {

    private final GladiadorPlugin plugin;
    private final BroadcastManager broadcastManager;
    private final BorderManager borderManager;

    public MatchStateManager(GladiadorPlugin plugin, BroadcastManager broadcastManager, BorderManager borderManager) {
        this.plugin = plugin;
        this.broadcastManager = broadcastManager;
        this.borderManager = borderManager;
    }

    /**
     * Inicia preparação (fecha entrada, countdown)
     * Grug Brain: Valida mínimo de clans, inicia countdown
     */
    public void beginPreparation(GladiadorMatch match) {
        if (match == null) return;

        int minClans = plugin.getConfig().getInt("match.min-clans", 2);
        if (match.getClanEntries().size() < minClans) {
            broadcastManager.broadcastCancelled("Número mínimo de clans não atingido (" + minClans + ").");
            return;
        }

        if (match.getClanEntries().isEmpty()) {
            broadcastManager.broadcastCancelled("Falta de participantes.");
            return;
        }

        match.setState(GladiadorMatch.MatchState.PREPARATION);

        int prepTime = plugin.getConfig().getInt("arena.preparation-time", 30);
        broadcastManager.broadcastPreparation(match, prepTime);

        borderManager.setInitialBorder(match.getArena());

        int countdownTime = Math.max(10, prepTime / 3);
        startCountdown(match, countdownTime);
    }

    /**
     * Inicia countdown para PvP
     * Grug Brain: Countdown simples, broadcast periódico
     */
    private void startCountdown(GladiadorMatch match, int countdownTime) {
        new BukkitRunnable() {
            int count = countdownTime;

            @Override
            public void run() {
                if (match == null || match.getState() != GladiadorMatch.MatchState.PREPARATION) {
                    this.cancel();
                    return;
                }

                if (count == countdownTime / 2 || count == 10 || count <= 5) {
                    if (count <= 5) {
                        Bukkit.broadcastMessage(ChatColor.RED.toString() + ChatColor.BOLD.toString() + "⚔ " + count + " segundos!");
                        // Som de countdown (mais alto nos últimos 5 segundos)
                        // Grug Brain: Usa NOTE_PLING (compatível com Paper 1.8.8, usado em outros plugins)
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.isOnline()) {
                                p.playSound(p.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
                            }
                        }
                    } else {
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "O Gladiador começa em " + count + " segundos!");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.isOnline()) {
                                p.playSound(p.getLocation(), Sound.CLICK, 0.5f, 1f);
                            }
                        }
                    }
                }

                if (count <= 0) {
                    this.cancel();
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Ativa match (habilita PvP, inicia border shrink)
     * Grug Brain: Transição para ACTIVE, inicia sistemas
     */
    public BukkitTask activateMatch(GladiadorMatch match) {
        if (match == null) return null;

        match.setState(GladiadorMatch.MatchState.ACTIVE);
        match.setStartTime(System.currentTimeMillis());

        broadcastManager.broadcastPvPActivated();

        // Som de gong quando PvP ativa
        // Grug Brain: Sons compatíveis com Paper 1.8.8
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOnline()) {
                p.playSound(p.getLocation(), Sound.ENDERDRAGON_GROWL, 1f, 0.8f);
                p.playSound(p.getLocation(), Sound.ANVIL_LAND, 1f, 0.5f);
            }
        }

        borderManager.enablePvP(match.getArena());
        BukkitTask borderTask = borderManager.startShrinkTask(match);
        match.setBorderTask(borderTask);

        broadcastManager.startStatusBroadcast(match);

        return borderTask;
    }

    /**
     * Finaliza match
     * Grug Brain: Transição para ENDING, limpa recursos
     */
    public void endMatch(GladiadorMatch match) {
        if (match == null) return;

        if (match.getBorderTask() != null) {
            match.getBorderTask().cancel();
        }

        broadcastManager.stopStatusBroadcast();
        borderManager.resetBorder(match.getArena());
        borderManager.disablePvP(match.getArena());
    }
}

