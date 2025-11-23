package com.primeleague.x1.managers;

import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Match;
import com.primeleague.x1.utils.TitleCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Date;

/**
 * Task de countdown para início de match
 * Grug Brain: Runnable isolado, sem estado compartilhado complexo
 */
public class MatchCountdownTask extends BukkitRunnable {

    private final X1Plugin plugin;
    private final Match match;
    private int countdown;

    public MatchCountdownTask(X1Plugin plugin, Match match, int countdownSeconds) {
        this.plugin = plugin;
        this.match = match;
        this.countdown = countdownSeconds;
    }

    /**
     * Inicia o countdown
     */
    public void start() {
        this.runTaskTimer(plugin, 0L, 20L); // A cada segundo
    }

    @Override
    public void run() {
        if (match.getStatus() != Match.MatchStatus.WAITING) {
            cancel();
            return;
        }

        Player player1 = Bukkit.getPlayer(match.getPlayer1());
        Player player2 = Bukkit.getPlayer(match.getPlayer2());

        if (player1 == null || player2 == null || !player1.isOnline() || !player2.isOnline()) {
            cancel();
            plugin.getMatchManager().cancelMatch(match);
            return;
        }

        // Validar durante countdown - apenas coisas críticas
        // Verificar se ainda estão no mesmo match (não entraram em outro)
        Match currentMatch1 = plugin.getMatchManager().getMatch(player1.getUniqueId());
        Match currentMatch2 = plugin.getMatchManager().getMatch(player2.getUniqueId());
        
        if (currentMatch1 != match || currentMatch2 != match) {
            cancel();
            plugin.getMatchManager().cancelMatch(match);
            return;
        }

        // Validar durante countdown (anywhere mode) - apenas distância e mundo (pragmático)
        // Grug Brain: Não cancelar por GameMode/flying durante countdown (players podem ajustar)
        if (match.isAnywhere()) {
            double maxDistance = plugin.getConfig().getDouble("match.anywhere.max-distance", 50.0);
            // Validar apenas distância e mundo (não estado do player)
            String validationError = com.primeleague.x1.utils.AnywhereMatchValidator.validate(
                player1, player2, maxDistance);
            
            if (validationError != null) {
                cancel();
                String msg = validationError + " §7Match cancelado.";
                player1.sendMessage(msg);
                player2.sendMessage(msg);
                plugin.getMatchManager().cancelMatch(match);
                return;
            }
        }

        if (countdown > 0) {
            String msg = plugin.getConfig().getString("messages.match.starting",
                "§aPartida iniciando em {countdown} segundos!")
                .replace("{countdown}", String.valueOf(countdown));
            player1.sendMessage(msg);
            player2.sendMessage(msg);

            // Título visual durante countdown
            boolean countdownTitles = plugin.getConfig().getBoolean("ux.countdown-titles", true);
            if (countdownTitles) {
                try {
                    String title = "§a§l" + countdown;
                    String brandingNome = plugin.getConfig().getString("ux.branding.nome", "PRIME LEAGUE");
                    String brandingCor = plugin.getConfig().getString("ux.branding.cor", "§b");
                    boolean mostrarBranding = plugin.getConfig().getBoolean("ux.branding.mostrar-em-titulos", true);
                    
                    String subtitle = mostrarBranding ?
                        brandingCor + "§l" + brandingNome + " §7Prepare-se!" :
                        "§7Prepare-se!";
                    TitleCompat.send(player1, title, subtitle);
                    TitleCompat.send(player2, title, subtitle);
                } catch (Exception e) {
                    // Fallback para versões sem sendTitle
                }
            }

            countdown--;
        } else {
            // Iniciar match
            match.setStatus(Match.MatchStatus.FIGHTING);
            match.setStartTime(new Date());

            String msg = plugin.getConfig().getString("messages.match.started",
                "§aPartida iniciada! Boa sorte!");
            player1.sendMessage(msg);
            player2.sendMessage(msg);

            // Título ao iniciar match
            boolean countdownTitles = plugin.getConfig().getBoolean("ux.countdown-titles", true);
            if (countdownTitles) {
                try {
                    String brandingNome = plugin.getConfig().getString("ux.branding.nome", "PRIME LEAGUE");
                    String brandingCor = plugin.getConfig().getString("ux.branding.cor", "§b");
                    boolean mostrarBranding = plugin.getConfig().getBoolean("ux.branding.mostrar-em-titulos", true);
                    
                    String title = mostrarBranding ?
                        brandingCor + "§l" + brandingNome :
                        "§a§lPARTIDA INICIADA";
                    String subtitle = "§a§lBOA SORTE!";
                    TitleCompat.send(player1, title, subtitle);
                    TitleCompat.send(player2, title, subtitle);
                } catch (Exception e) {
                    // Fallback
                }
            }

            // Atualizar scoreboard para status FIGHTING
            if (plugin.getScoreboardIntegration() != null && plugin.getScoreboardIntegration().isEnabled()) {
                plugin.getScoreboardIntegration().updateScoreboard(player1);
                plugin.getScoreboardIntegration().updateScoreboard(player2);
            }

            cancel();
        }
    }
}

