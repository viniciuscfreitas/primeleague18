package com.primeleague.x1.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.elo.EloAPI;
import com.primeleague.league.LeagueAPI;
import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Match;
import com.primeleague.x1.utils.MatchFeedbackHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Date;
import java.util.UUID;

/**
 * Finalizador de matches
 * Grug Brain: Lógica isolada, dependências claras (ELO, stats, Discord)
 */
public class MatchFinisher {

    private final X1Plugin plugin;
    private final MatchManager matchManager;
    private final MatchSnapshotHandler snapshotHandler;

    public MatchFinisher(X1Plugin plugin, MatchManager matchManager, MatchSnapshotHandler snapshotHandler) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.snapshotHandler = snapshotHandler;
    }

    /**
     * Finaliza match com vencedor
     */
    public void finish(Match match, UUID winnerUuid) {
        if (match.getStatus() == Match.MatchStatus.ENDED) {
            return; // Já finalizado
        }

        match.setStatus(Match.MatchStatus.ENDED);
        match.setEndTime(new Date());
        match.setWinner(winnerUuid);

        UUID loserUuid = winnerUuid.equals(match.getPlayer1()) ? match.getPlayer2() : match.getPlayer1();

        Player winner = Bukkit.getPlayer(winnerUuid);
        Player loser = Bukkit.getPlayer(loserUuid);

        // Restaurar inventários logo após definir vencedor
        // Se anywhere, também restaurar localização
        snapshotHandler.restoreSnapshot(winnerUuid, match.isAnywhere());
        snapshotHandler.restoreSnapshot(loserUuid, match.isAnywhere());

        // Calcular duração do match para anti-farm
        long matchDurationSeconds = 0;
        if (match.getStartTime() != null && match.getEndTime() != null) {
            long durationMs = match.getEndTime().getTime() - match.getStartTime().getTime();
            matchDurationSeconds = Math.max(0, durationMs / 1000); // Garantir não negativo
        }

        // Verificar se match é suspeito e obter fator de redução de ELO
        double eloFactor = plugin.getAntiFarmManager().registerMatchAndGetEloFactor(match, matchDurationSeconds);

        // Atualizar ELO se ranked
        int eloChange = 0;
        if (match.isRanked()) {
            try {
                if (EloAPI.isEnabled()) {
                    eloChange = EloAPI.updateEloAfterPvP(winnerUuid, loserUuid);
                    
                    // Aplicar redução de ELO se match foi suspeito
                    if (eloFactor < 1.0 && eloChange != 0) {
                        int originalChange = eloChange;
                        int reducedChange = (int) Math.round(eloChange * eloFactor);
                        int adjustment = reducedChange - originalChange;
                        
                        // Ajustar ELO usando addElo (adicionar valor negativo = reduzir)
                        if (adjustment != 0) {
                            EloAPI.addElo(winnerUuid, adjustment, "Anti-Farm: Match suspeito");
                            EloAPI.addElo(loserUuid, -adjustment, "Anti-Farm: Match suspeito");
                            
                            eloChange = reducedChange;
                            plugin.getLogger().info("ELO reduzido por match suspeito: " + 
                                winnerUuid + " ganhou " + reducedChange + " em vez de " + originalChange);
                        }
                    }
                    
                    // Armazenar mudança de ELO para placeholders
                    plugin.setLastEloChange(winnerUuid, eloChange);
                    plugin.setLastEloChange(loserUuid, -eloChange);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erro ao atualizar ELO (EloAPI não disponível): " + e.getMessage());
            }
        }

        // Salvar match no banco (via async)
        saveMatchToDatabase(match, eloChange);

        // Atualizar stats globais (kills/deaths/killstreak)
        CoreAPI.incrementKillsAndKillstreak(winnerUuid);
        CoreAPI.incrementDeathsAndResetKillstreak(loserUuid);

        // Atualizar stats x1 (wins/losses/winstreak)
        plugin.getStatsManager().updateStats(winnerUuid, loserUuid);

        // Registrar vitória/derrota via LeagueAPI
        if (LeagueAPI.isEnabled()) {
            // Gerar UUID do match (usar hash dos players + timestamp)
            UUID matchId = UUID.nameUUIDFromBytes(
                (winnerUuid.toString() + loserUuid.toString() + match.getStartTime().getTime()).getBytes()
            );
            LeagueAPI.recordX1Win(winnerUuid, loserUuid, matchId);
        }

        // Mensagens
        String winnerName = winner != null ? winner.getName() : "Desconhecido";
        String loserName = loser != null ? loser.getName() : "Desconhecido";
        String msg = plugin.getConfig().getString("messages.match.ended",
            "§a{winner} venceu o match contra {loser}!")
            .replace("{winner}", winnerName)
            .replace("{loser}", loserName);

        // Evitar duplicação: se houver broadcast, não enviar mensagem individual
        boolean broadcastOnEnd = plugin.getConfig().getBoolean("match.broadcast-on-end", true);
        if (!broadcastOnEnd) {
            if (winner != null) winner.sendMessage(msg);
            if (loser != null) loser.sendMessage(msg);
        }

        // Títulos e sons ao vencer/perder
        MatchFeedbackHandler.sendVictoryDefeatFeedback(plugin, winner, loser, winnerName, loserName, match, eloChange);

        // Broadcast (opcional - pode ser desabilitado via config)
        if (broadcastOnEnd) {
            Bukkit.broadcastMessage(msg);
        }

        // Discord webhook (se disponível)
        if (plugin.getDiscordIntegration() != null) {
            plugin.getDiscordIntegration().sendMatchEndWebhook(match, winnerName, loserName, eloChange);
        }

        // Limpar match após delay
        new BukkitRunnable() {
            @Override
            public void run() {
                matchManager.removeMatch(match);
                // Marcar arena como disponível apenas se houver arena
                if (match.getArena() != null) {
                    plugin.getArenaManager().markArenaAvailable(match.getArena());
                }
            }
        }.runTaskLater(plugin, 100L); // 5 segundos
    }


    /**
     * Salva match no banco de dados (async)
     */
    private void saveMatchToDatabase(Match match, int eloChange) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (java.sql.Connection conn = CoreAPI.getDatabase().getConnection()) {
                    java.sql.PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO x1_matches (player1_uuid, player2_uuid, winner_uuid, kit_name, ranked, elo_change, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)");

                    stmt.setObject(1, match.getPlayer1());
                    stmt.setObject(2, match.getPlayer2());
                    stmt.setObject(3, match.getWinner());
                    stmt.setString(4, match.getKit());
                    stmt.setBoolean(5, match.isRanked());
                    stmt.setInt(6, eloChange);
                    // Usar startTime se disponível, senão usar created_at (NOW())
                    java.util.Date startTime = match.getStartTime();
                    if (startTime != null) {
                        stmt.setTimestamp(7, new java.sql.Timestamp(startTime.getTime()));
                    } else {
                        stmt.setTimestamp(7, new java.sql.Timestamp(System.currentTimeMillis()));
                    }

                    stmt.executeUpdate();
                } catch (java.sql.SQLException e) {
                    plugin.getLogger().severe("Erro ao salvar match no banco: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}

