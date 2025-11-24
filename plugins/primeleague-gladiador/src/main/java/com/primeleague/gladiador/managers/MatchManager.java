package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.Arena;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

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
    private final MatchDatabaseManager databaseManager;
    private final MatchRankingManager rankingManager;
    private final MatchStateHandler stateHandler;
    private final MatchPlayerHandler playerHandler;
    private final MatchIntegrationHandler integrationHandler;

    public MatchManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.rewardManager = new RewardManager(plugin);
        this.broadcastManager = new BroadcastManager(plugin);
        this.borderManager = new BorderManager(plugin);
        this.databaseManager = new MatchDatabaseManager(plugin);
        this.rankingManager = new MatchRankingManager();
        this.playerHandler = new MatchPlayerHandler(plugin, broadcastManager);
        this.stateHandler = new MatchStateHandler(plugin, broadcastManager, borderManager,
            playerHandler, databaseManager, rankingManager);
        this.integrationHandler = new MatchIntegrationHandler(plugin);
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

        // Task de contagem de lobby (mensagem a cada 1 minuto)
        // Grug Brain: Usar valor do config ao invés de hardcoded
        int waitTimeMinutes = plugin.getConfig().getInt("match.wait-time-minutes", 5);
        int waitTimeSeconds = waitTimeMinutes * 60;

        // Broadcast via BroadcastManager (com minutos do config)
        broadcastManager.broadcastMatchStarted(waitTimeMinutes);

        LobbyCountdownTask.start(plugin, currentMatch, waitTimeMinutes);

        // Iniciar task de preparação automática após X segundos
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentMatch != null && currentMatch.getState() == GladiadorMatch.MatchState.WAITING) {
                    beginPreparation();
                }
            }
        }.runTaskLater(plugin, 20L * waitTimeSeconds);

        return true;
    }

    /**
     * Adiciona player ao match
     * Grug Brain: Delega para MatchPlayerHandler
     */
    public boolean addPlayer(Player player) {
        if (currentMatch == null || currentMatch.getState() != GladiadorMatch.MatchState.WAITING) {
            return false;
        }
        return playerHandler.addPlayer(player, currentMatch);
    }


    /**
     * Começa preparação (fecha entrada, countdown)
     * Grug Brain: Delega para MatchStateHandler
     * Nota: Countdown espera 1 minuto, depois mostra 10, 5, 4, 3, 2, 1 segundos
     */
    public void beginPreparation() {
        if (currentMatch == null) return;
        stateHandler.beginPreparation(currentMatch);

        // Iniciar match após countdown completo
        // Grug Brain: 1 minuto de espera + 10 segundos de countdown = 70 segundos total
        int countdownTime = 10; // Countdown de segundos (10, 5, 4, 3, 2, 1)
        int waitTime = 60 + countdownTime; // 1 minuto + countdown

        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentMatch != null && currentMatch.getState() == GladiadorMatch.MatchState.PREPARATION) {
                    beginMatch();
                }
            }
        }.runTaskLater(plugin, 20L * waitTime); // 70 segundos total
    }

    /**
     * Começa match (habilita PvP, inicia border shrink)
     * Grug Brain: Delega para MatchStateHandler, configura integrações
     */
    public void beginMatch() {
        if (currentMatch == null) return;

        // activateMatch já seta borderTask internamente, não duplicar
        stateHandler.activateMatch(currentMatch);

        // Notificar Discord
        plugin.getDiscordIntegration().sendMatchStarted(
            currentMatch.getClanEntries().size(),
            currentMatch.getTotalPlayers(),
            currentMatch.getArena().getName()
        );

        // Configurar integrações (scoreboard, tablist)
        integrationHandler.setupIntegrations(currentMatch);
    }


    /**
     * Processa morte de player
     * Grug Brain: Delega para MatchPlayerHandler
     */
    public void handleDeath(Player victim, Player killer) {
        if (currentMatch == null || currentMatch.getState() == GladiadorMatch.MatchState.ENDING) return;
        playerHandler.handleDeath(victim, killer, currentMatch);
        checkWinCondition();
    }


    /**
     * Verifica condição de vitória
     * Grug Brain: Delega para MatchPlayerHandler
     */
    public void checkWinCondition() {
        if (currentMatch == null) return;

        ClanEntry winner = playerHandler.checkWinCondition(currentMatch);
        if (winner != null) {
            if (currentMatch.getBorderTask() != null) {
                currentMatch.getBorderTask().cancel();
            }
            endMatch(winner);
            return;
        }

        String cancelReason = playerHandler.shouldCancelMatch(currentMatch);
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
     * Grug Brain: Delega para managers especializados
     */
    private void endMatch(ClanEntry winner) {
        if (currentMatch == null) return;

        stateHandler.endMatch(currentMatch);

        // Calcular MVP e DAMAGE via MatchStatsCalculator
        MatchStatsCalculator.StatsResult stats = MatchStatsCalculator.calculateStats(currentMatch);

        // Obter ranking de todos os clans (para pontos por posição)
        List<ClanEntry> rankedClans = rankingManager.getRankedClans(currentMatch);

        // Obter valor de coins da recompensa (para exibir)
        long rewardCents = plugin.getConfig().getLong("rewards.winner-clan-balance", 100000000L);
        double coinsReward = rewardCents / 100.0;

        // Anúncio via BroadcastManager (com MVP e DAMAGE)
        broadcastManager.broadcastWinner(winner, currentMatch, coinsReward,
            stats.getMvpPlayerName(), stats.getMvpKills(),
            stats.getDamagePlayerName(), stats.getDamageAmount());

        // Prêmios completos via RewardManager (pontos, bônus, coins, tags)
        rewardManager.giveAllRewards(currentMatch, rankedClans, winner, stats);

        // Stats do vencedor
        plugin.getStatsManager().incrementWins(winner.getClanId());

        // Incrementar participação de todos os clans
        for (ClanEntry clan : rankedClans) {
            plugin.getStatsManager().incrementParticipation(clan.getClanId());
        }

        // Processar finalização via MatchStateHandler
        stateHandler.handleMatchEnd(currentMatch, winner, stats, coinsReward);

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

        // Restaurar todos os jogadores via MatchPlayerHandler
        for (ClanEntry entry : currentMatch.getClanEntries()) {
            for (UUID uuid : entry.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    playerHandler.teleportToExit(p);
                    playerHandler.restoreInventory(p);
                }
            }
        }

        // Limpar snapshots via MatchPlayerHandler
        playerHandler.clearSnapshots();

        // Remover integrações (scoreboard, tablist)
        integrationHandler.clearIntegrationsOnCancel(currentMatch);

        plugin.getLogger().info("Match Gladiador cancelado");
        currentMatch = null;
    }



}
