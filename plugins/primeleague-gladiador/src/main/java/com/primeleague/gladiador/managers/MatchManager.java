package com.primeleague.gladiador.managers;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.managers.ClansManager;
import com.primeleague.clans.models.ClanData;
import com.primeleague.core.CoreAPI;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.Arena;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
// LeagueAPI usado via reflection (softdepend)
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gerenciador de partidas do Gladiador
 * Grug Brain: Match logic direto, sem abstrações desnecessárias
 */
public class MatchManager {

    private final GladiadorPlugin plugin;
    private GladiadorMatch currentMatch;
    private final RewardManager rewardManager;
    private final BroadcastManager broadcastManager;
    private final BorderManager borderManager;
    private final MatchTeleportManager teleportManager;
    private final MatchDatabaseManager databaseManager;
    private final MatchRankingManager rankingManager;
    private final MatchStateManager stateManager;
    private final MatchPlayerManager playerManager;

    public MatchManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.rewardManager = new RewardManager(plugin);
        this.broadcastManager = new BroadcastManager(plugin);
        this.borderManager = new BorderManager(plugin);
        this.teleportManager = new MatchTeleportManager(plugin);
        this.databaseManager = new MatchDatabaseManager(plugin);
        this.rankingManager = new MatchRankingManager();
        this.stateManager = new MatchStateManager(plugin, broadcastManager, borderManager);
        this.playerManager = new MatchPlayerManager(plugin, teleportManager);
    }

    public GladiadorMatch getCurrentMatch() {
        return currentMatch;
    }

    /**
     * Inicia novo match
     */
    public boolean startMatch() {
        if (currentMatch != null) {
            return false; // Já existe match ativo
        }

        Arena arena = plugin.getArenaManager().getAvailableArena();
        if (arena == null) {
            return false;
        }

        currentMatch = new GladiadorMatch(arena);
        currentMatch.setState(GladiadorMatch.MatchState.WAITING);

        // Broadcast via BroadcastManager
        broadcastManager.broadcastMatchStarted();

        // Iniciar task de preparação automática após X segundos (configurável)
        int waitTime = plugin.getConfig().getInt("arena.preparation-time", 30);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentMatch != null && currentMatch.getState() == GladiadorMatch.MatchState.WAITING) {
                    beginPreparation();
                }
            }
        }.runTaskLater(plugin, 20L * waitTime); // Tempo configurável (padrão 30s para teste)

        return true;
    }

    /**
     * Adiciona player ao match
     * Grug Brain: Delega para MatchPlayerManager
     */
    public boolean addPlayer(Player player) {
        if (currentMatch == null || currentMatch.getState() != GladiadorMatch.MatchState.WAITING) {
            return false;
        }
        return playerManager.addPlayer(player, currentMatch);
    }


    /**
     * Começa preparação (fecha entrada, countdown)
     * Grug Brain: Delega para MatchStateManager
     */
    public void beginPreparation() {
        if (currentMatch == null) return;
        stateManager.beginPreparation(currentMatch);
        
        // Iniciar match após countdown (via callback)
        int prepTime = plugin.getConfig().getInt("arena.preparation-time", 30);
        int countdownTime = Math.max(10, prepTime / 3);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentMatch != null && currentMatch.getState() == GladiadorMatch.MatchState.PREPARATION) {
                    beginMatch();
                }
            }
        }.runTaskLater(plugin, 20L * countdownTime);
    }

    /**
     * Começa match (habilita PvP, inicia border shrink)
     * Grug Brain: Delega para MatchStateManager, configura integrações
     */
    public void beginMatch() {
        if (currentMatch == null) return;

        BukkitTask borderTask = stateManager.activateMatch(currentMatch);
        currentMatch.setBorderTask(borderTask);

        // Notificar Discord via GladiadorIntegration (notificações globais)

        plugin.getDiscordIntegration().sendMatchStarted(
            currentMatch.getClanEntries().size(),
            currentMatch.getTotalPlayers(),
            currentMatch.getArena().getName()
        );

        if (plugin.getScoreboardIntegration().isEnabled()) {
            for (UUID uuid : currentMatch.getAlivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    plugin.getScoreboardIntegration().setScoreboard(p, "gladiador-match");
                }
            }
            plugin.getScoreboardIntegration().startUpdateTask(currentMatch);
        }

        if (plugin.getTabIntegration().isEnabled()) {
            for (UUID uuid : currentMatch.getAlivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    plugin.getTabIntegration().updateTablist(p);
                }
            }
        }
    }


    /**
     * Processa morte de player
     * Grug Brain: Delega para MatchPlayerManager
     */
    public void handleDeath(Player victim, Player killer) {
        if (currentMatch == null || currentMatch.getState() == GladiadorMatch.MatchState.ENDING) return;
        playerManager.handleDeath(victim, killer, currentMatch);
        checkWinCondition();
    }


    /**
     * Verifica condição de vitória
     * Grug Brain: Delega para MatchPlayerManager
     */
    public void checkWinCondition() {
        if (currentMatch == null) return;

        ClanEntry winner = playerManager.checkWinCondition(currentMatch);
        if (winner != null) {
            if (currentMatch.getBorderTask() != null) {
                currentMatch.getBorderTask().cancel();
            }
            endMatch(winner);
            return;
        }

        String cancelReason = playerManager.shouldCancelMatch(currentMatch);
        if (cancelReason != null) {
            if (currentMatch.getBorderTask() != null) {
                currentMatch.getBorderTask().cancel();
            }
            broadcastManager.broadcastCancelled(cancelReason);
            cancelMatch();
        }
    }

    /**
     * Finaliza match
     * Grug Brain: Delega limpeza para MatchStateManager
     */
    private void endMatch(ClanEntry winner) {
        if (currentMatch == null) return;

        stateManager.endMatch(currentMatch);

        // Anúncio via BroadcastManager
        broadcastManager.broadcastWinner(winner);

        // Prêmios via RewardManager
        rewardManager.giveWinnerRewards(winner);

        // Stats do vencedor
        plugin.getStatsManager().incrementWins(winner.getClanId());
        plugin.getStatsManager().incrementParticipation(winner.getClanId());

        // NOVO: Registrar vitórias via LeagueAPI (todos os clans com posições e pontos F1)
        // Usar reflection para acessar LeagueAPI (softdepend)
        try {
            Class<?> leagueApiClass = Class.forName("com.primeleague.league.LeagueAPI");
            java.lang.reflect.Method isEnabledMethod = leagueApiClass.getMethod("isEnabled");
            Boolean isEnabled = (Boolean) isEnabledMethod.invoke(null);
            
            if (isEnabled != null && isEnabled) {
                // Ordenar clans via RankingManager
                List<ClanEntry> rankedClans = rankingManager.getRankedClans(currentMatch);
                java.lang.reflect.Method recordMethod = leagueApiClass.getMethod("recordGladiadorWin", 
                    Integer.class, UUID.class, Integer.class, Integer.class, Integer.class);
                
                for (int i = 0; i < rankedClans.size(); i++) {
                    ClanEntry clan = rankedClans.get(i);
                    int position = i + 1;
                    
                    // Registrar vitória via LeagueAPI (com posição, kills, deaths)
                    recordMethod.invoke(null, clan.getClanId(), currentMatch.getMatchId(), position,
                        clan.getKills(), clan.getDeaths());
                }
            }
        } catch (Exception e) {
            // LeagueAPI não disponível - ignorar silenciosamente
        }

        // Teleportar vencedores e restaurar inv via TeleportManager
        for (UUID uuid : winner.getRemainingPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                teleportManager.teleportToExit(p);
                teleportManager.restoreInventory(p);
                p.sendMessage(ChatColor.GOLD + "Parabéns pela vitória!");
            }
        }

        // Calcular estatísticas finais
        int totalKills = currentMatch.getClanEntries().stream()
            .mapToInt(ClanEntry::getKills)
            .sum();
        long durationSeconds = (System.currentTimeMillis() - currentMatch.getStartTime()) / 1000;

        // Notificar via Discord (se habilitado)
        plugin.getDiscordIntegration().sendMatchWon(winner.getClanTag(), totalKills, durationSeconds);

        // Salvar match no banco via DatabaseManager
        databaseManager.saveMatch(currentMatch, winner.getClanId());

        // Remover scoreboard e tablist customizados
        if (plugin.getScoreboardIntegration().isEnabled()) {
            plugin.getScoreboardIntegration().stopUpdateTask();
            for (UUID uuid : currentMatch.getAlivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    plugin.getScoreboardIntegration().clearScoreboard(p);
                }
            }
        }

        currentMatch = null;
    }

    /**
     * Cancela match
     * Grug Brain: Cancela tasks e restaura jogadores online
     */
    public void cancelMatch() {
        if (currentMatch == null) return;

        // Parar border shrink
        if (currentMatch.getBorderTask() != null) {
            currentMatch.getBorderTask().cancel();
        }

        // Cancelar task de broadcast via BroadcastManager
        broadcastManager.stopStatusBroadcast();

        // Resetar WorldBorder e desativar PvP via BorderManager
        borderManager.resetBorder(currentMatch.getArena());
        borderManager.disablePvP(currentMatch.getArena());

        // Restaurar todos os jogadores via TeleportManager
        for (ClanEntry entry : currentMatch.getClanEntries()) {
            for (UUID uuid : entry.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    teleportManager.teleportToExit(p);
                    teleportManager.restoreInventory(p);
                }
            }
        }

        // Limpar snapshots via TeleportManager
        teleportManager.clearSnapshots();

        // Remover scoreboard e tablist customizados
        if (plugin.getScoreboardIntegration().isEnabled()) {
            plugin.getScoreboardIntegration().stopUpdateTask();
            for (ClanEntry entry : currentMatch.getClanEntries()) {
                for (UUID uuid : entry.getMembers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        plugin.getScoreboardIntegration().clearScoreboard(p);
                    }
                }
            }
        }

        plugin.getLogger().info("Match Gladiador cancelado");
        currentMatch = null;
    }



}
