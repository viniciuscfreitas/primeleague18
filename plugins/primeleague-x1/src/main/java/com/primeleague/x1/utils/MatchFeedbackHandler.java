package com.primeleague.x1.utils;

import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Match;
import org.bukkit.entity.Player;

/**
 * Handler de feedback visual/sonoro para matches
 * Grug Brain: Lógica isolada, reutilizável, sem dependências complexas
 */
public class MatchFeedbackHandler {

    /**
     * Envia feedback de vitória/derrota (títulos e sons)
     */
    public static void sendVictoryDefeatFeedback(X1Plugin plugin, Player winner, Player loser,
                                                  String winnerName, String loserName,
                                                  Match match, int eloChange) {
        boolean victorySound = plugin.getConfig().getBoolean("ux.victory-sound", true);
        boolean defeatSound = plugin.getConfig().getBoolean("ux.defeat-sound", true);
        String brandingNome = plugin.getConfig().getString("ux.branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("ux.branding.cor", "§b");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.branding.mostrar-em-titulos", true);

        // Título de vitória
        if (winner != null) {
            try {
                String title = mostrarBranding ?
                    brandingCor + "§l" + brandingNome :
                    "§a§lVITÓRIA!";
                String subtitle = "§7Você venceu " + loserName;
                if (match.isRanked() && eloChange != 0) {
                    String eloStr = eloChange > 0 ? "§a+" + eloChange : "§c" + eloChange;
                    subtitle += " §7| ELO: " + eloStr;
                }
                TitleCompat.send(winner, title, subtitle);
            } catch (Exception e) {
                // Fallback
            }

            if (victorySound) {
                try {
                    winner.playSound(winner.getLocation(), org.bukkit.Sound.LEVEL_UP, 1.0f, 1.5f);
                } catch (Exception e) {
                    // Fallback
                    try {
                        winner.playSound(winner.getLocation(), org.bukkit.Sound.NOTE_PLING, 1.0f, 1.5f);
                    } catch (Exception e2) {
                        // Ignorar
                    }
                }
            }
        }

        // Título de derrota
        if (loser != null) {
            try {
                String title = mostrarBranding ?
                    brandingCor + "§l" + brandingNome :
                    "§c§lDERROTA";
                String subtitle = "§7Você perdeu para " + winnerName;
                if (match.isRanked() && eloChange != 0) {
                    int loserEloChange = -eloChange;
                    String eloStr = loserEloChange > 0 ? "§a+" + loserEloChange : "§c" + loserEloChange;
                    subtitle += " §7| ELO: " + eloStr;
                }
                TitleCompat.send(loser, title, subtitle);
            } catch (Exception e) {
                // Fallback
            }

            if (defeatSound) {
                try {
                    loser.playSound(loser.getLocation(), org.bukkit.Sound.ANVIL_LAND, 0.5f, 0.8f);
                } catch (Exception e) {
                    // Fallback
                    try {
                        loser.playSound(loser.getLocation(), org.bukkit.Sound.ANVIL_BREAK, 0.5f, 0.8f);
                    } catch (Exception e2) {
                        // Ignorar
                    }
                }
            }
        }
    }

    /**
     * Envia feedback ao encontrar match (títulos e sons)
     */
    public static void sendMatchFoundFeedback(X1Plugin plugin, Player p1, Player p2) {
        boolean matchFoundSound = plugin.getConfig().getBoolean("ux.match-found-sound", true);
        String brandingNome = plugin.getConfig().getString("ux.branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("ux.branding.cor", "§b");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.branding.mostrar-em-titulos", true);

        if (p1 != null && p1.isOnline()) {
            String opponentName = p2 != null ? p2.getName() : "Oponente";
            try {
                String title = mostrarBranding ?
                    brandingCor + "§l" + brandingNome :
                    "§a§lPARTIDA ENCONTRADA!";
                String subtitle = "§7Oponente: §e" + opponentName;
                TitleCompat.send(p1, title, subtitle);
            } catch (Exception e) {
                // Fallback
                p1.sendMessage(plugin.getConfig().getString("messages.queue.match-found",
                    "§aPartida encontrada! Preparando arena..."));
            }

            if (matchFoundSound) {
                try {
                    p1.playSound(p1.getLocation(), org.bukkit.Sound.LEVEL_UP, 1.0f, 1.2f);
                } catch (Exception e) {
                    // Fallback para som alternativo
                    try {
                        p1.playSound(p1.getLocation(), org.bukkit.Sound.NOTE_PLING, 1.0f, 1.2f);
                    } catch (Exception e2) {
                        // Ignorar se som não disponível
                    }
                }
            }
        }

        if (p2 != null && p2.isOnline()) {
            String opponentName = p1 != null ? p1.getName() : "Oponente";
            try {
                String title = mostrarBranding ?
                    brandingCor + "§l" + brandingNome :
                    "§a§lPARTIDA ENCONTRADA!";
                String subtitle = "§7Oponente: §e" + opponentName;
                TitleCompat.send(p2, title, subtitle);
            } catch (Exception e) {
                // Fallback
                p2.sendMessage(plugin.getConfig().getString("messages.queue.match-found",
                    "§aPartida encontrada! Preparando arena..."));
            }

            if (matchFoundSound) {
                try {
                    p2.playSound(p2.getLocation(), org.bukkit.Sound.LEVEL_UP, 1.0f, 1.2f);
                } catch (Exception e) {
                    // Fallback para som alternativo
                    try {
                        p2.playSound(p2.getLocation(), org.bukkit.Sound.NOTE_PLING, 1.0f, 1.2f);
                    } catch (Exception e2) {
                        // Ignorar se som não disponível
                    }
                }
            }
        }
    }
}

