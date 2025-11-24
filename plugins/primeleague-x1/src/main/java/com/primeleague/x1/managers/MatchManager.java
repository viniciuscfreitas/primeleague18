package com.primeleague.x1.managers;

import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Kit;
import com.primeleague.x1.models.Match;
import com.primeleague.x1.models.QueueEntry;
import com.primeleague.x1.utils.KitApplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Matches
 * Grug Brain: Thread-safe, integra com EloAPI e CoreAPI
 */
public class MatchManager {

    private final X1Plugin plugin;
    // Matches ativos: UUID do player -> Match
    private final Map<UUID, Match> activeMatches;
    private final int countdownSeconds;
    private final MatchSnapshotHandler snapshotHandler;
    private final MatchCreator matchCreator;
    private final MatchFinisher matchFinisher;

    public MatchManager(X1Plugin plugin) {
        this.plugin = plugin;
        this.activeMatches = new ConcurrentHashMap<>();
        this.snapshotHandler = new MatchSnapshotHandler();
        this.matchCreator = new MatchCreator(plugin, this);
        this.matchFinisher = new MatchFinisher(plugin, this, snapshotHandler);
        int countdown = plugin.getConfig().getInt("match.countdown", 5);
        // Validar countdown (deve ser >= 0)
        if (countdown < 0) {
            plugin.getLogger().warning("Countdown inválido (" + countdown + "), usando 5 segundos");
            this.countdownSeconds = 5;
        } else {
            this.countdownSeconds = countdown;
        }
    }

    /**
     * Cria match a partir de queue entries
     * Grug Brain: Delega para MatchCreator
     */
    public void createMatchFromQueue(QueueEntry entry1, QueueEntry entry2) {
        matchCreator.createFromQueue(entry1, entry2);
    }

    /**
     * Adiciona match aos ativos (usado por MatchCreator)
     */
    public void addActiveMatch(Match match) {
        activeMatches.put(match.getPlayer1(), match);
        activeMatches.put(match.getPlayer2(), match);
    }

    /**
     * Inicia match com countdown
     */
    public void startMatch(Match match) {
        Player player1 = Bukkit.getPlayer(match.getPlayer1());
        Player player2 = Bukkit.getPlayer(match.getPlayer2());

        if (player1 == null || player2 == null || !player1.isOnline() || !player2.isOnline()) {
            cancelMatch(match);
            return;
        }

        // Snapshot antes de aplicar kit (evita duplicação/perda)
        // Sempre capturar snapshot para restaurar depois
        snapshotHandler.captureSnapshot(player1);
        snapshotHandler.captureSnapshot(player2);

        // Teleportar players para arena apenas se não for anywhere
        if (!match.isAnywhere() && match.getArena() != null) {
            player1.teleport(match.getArena().getSpawn1());
            player2.teleport(match.getArena().getSpawn2());
        } else if (match.isAnywhere()) {
            // Validar apenas distância e mundo no início (pragmático)
            // Grug Brain: Não validar GameMode/flying aqui (players podem ajustar)
            double maxDistance = plugin.getConfig().getDouble("match.anywhere.max-distance", 50.0);
            String validationError = com.primeleague.x1.utils.AnywhereMatchValidator.validate(
                player1, player2, maxDistance);
            
            if (validationError != null) {
                String msg = validationError + " §7Match cancelado.";
                player1.sendMessage(msg);
                player2.sendMessage(msg);
                cancelMatch(match);
                return;
            }
            
            // Avisar distância se necessário
            double distance = com.primeleague.x1.utils.AnywhereMatchValidator.getDistance(player1, player2);
            int blocks = (int) distance;
            if (blocks > 20) {
                String distMsg = "§7Distância: " + blocks + " blocos";
                player1.sendMessage(distMsg);
                player2.sendMessage(distMsg);
            }
        }

        // Aplicar kit apenas se não for noKit
        if (!match.isNoKit()) {
            Kit kit = plugin.getKitManager().getKit(match.getKit());
            if (kit != null) {
                KitApplier.applyKit(player1, kit);
                KitApplier.applyKit(player2, kit);
            }
        }
        // Se noKit, mantém os itens atuais do jogador (já capturados no snapshot)

        // Iniciar countdown
        new MatchCountdownTask(plugin, match, countdownSeconds).start();
    }

    /**
     * Finaliza match com vencedor
     * Grug Brain: Delega para MatchFinisher
     */
    public void endMatch(Match match, UUID winnerUuid) {
        matchFinisher.finish(match, winnerUuid);
    }

    /**
     * Cancela match (disconnect, etc)
     */
    public void cancelMatch(Match match) {
        if (match.getStatus() == Match.MatchStatus.ENDED) {
            return;
        }

        Player player1 = Bukkit.getPlayer(match.getPlayer1());
        Player player2 = Bukkit.getPlayer(match.getPlayer2());

        String msg = plugin.getConfig().getString("messages.match.cancelled", "§cMatch cancelado");
        if (player1 != null) player1.sendMessage(msg);
        if (player2 != null) player2.sendMessage(msg);

        // Restaurar snapshots em cancelamento (com localização se anywhere)
        snapshotHandler.restoreSnapshot(match.getPlayer1(), match.isAnywhere());
        snapshotHandler.restoreSnapshot(match.getPlayer2(), match.isAnywhere());

        removeMatch(match);
        // Marcar arena como disponível apenas se houver arena
        if (match.getArena() != null) {
            plugin.getArenaManager().markArenaAvailable(match.getArena());
        }
    }

    /**
     * Remove match dos ativos (usado por MatchFinisher)
     */
    public void removeMatch(Match match) {
        activeMatches.remove(match.getPlayer1());
        activeMatches.remove(match.getPlayer2());
        // Limpar snapshots armazenados
        snapshotHandler.clearSnapshot(match.getPlayer1());
        snapshotHandler.clearSnapshot(match.getPlayer2());

        // Limpar TAB prefix (se disponível)
        if (plugin.getTabIntegration() != null && plugin.getTabIntegration().isEnabled()) {
            Player p1 = Bukkit.getPlayer(match.getPlayer1());
            Player p2 = Bukkit.getPlayer(match.getPlayer2());
            if (p1 != null) {
                plugin.getTabIntegration().clearPrefix(p1);
                // Limpar scoreboard contextual
                if (plugin.getScoreboardIntegration() != null && plugin.getScoreboardIntegration().isEnabled()) {
                    plugin.getScoreboardIntegration().clearScoreboard(p1);
                }
            }
            if (p2 != null) {
                plugin.getTabIntegration().clearPrefix(p2);
                // Limpar scoreboard contextual
                if (plugin.getScoreboardIntegration() != null && plugin.getScoreboardIntegration().isEnabled()) {
                    plugin.getScoreboardIntegration().clearScoreboard(p2);
                }
            }
        }
    }

    /**
     * Obtém match de um player
     */
    public Match getMatch(UUID playerUuid) {
        return activeMatches.get(playerUuid);
    }

    /**
     * Verifica se player está em match
     */
    public boolean isInMatch(UUID playerUuid) {
        return activeMatches.containsKey(playerUuid);
    }

    /**
     * Obtém o MatchCreator (para criar matches diretos)
     */
    public MatchCreator getMatchCreator() {
        return matchCreator;
    }
}

