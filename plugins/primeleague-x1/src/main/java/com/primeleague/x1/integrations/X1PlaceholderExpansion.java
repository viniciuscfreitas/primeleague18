package com.primeleague.x1.integrations;

import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.X1Stats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * PlaceholderAPI Expansion para X1
 * Grug Brain: Seguir padrão CorePlaceholderExpansion.java
 */
public class X1PlaceholderExpansion extends PlaceholderExpansion {

    private final X1Plugin plugin;

    public X1PlaceholderExpansion(X1Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "x1";
    }

    @Override
    public String getAuthor() {
        return "Primeleague";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // Persistir mesmo se PlaceholderAPI recarregar
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        UUID uuid = player.getUniqueId();

        // Buscar stats uma vez e reutilizar (com cache)
        X1Stats stats = null;
        String lowerId = identifier.toLowerCase();
        
        if (lowerId.equals("wins") || lowerId.equals("losses") || lowerId.equals("wlr") || 
            lowerId.equals("streak") || lowerId.equals("best_streak") || lowerId.equals("winstreak_color")) {
            // Tentar obter do cache primeiro (TTL 5 segundos)
            stats = plugin.getPlaceholderStats(uuid);
            if (stats == null) {
                // Cache expirado ou não existe - buscar do banco
                stats = plugin.getStatsManager().getStatsSync(uuid);
                if (stats != null) {
                    // Atualizar cache
                    plugin.setPlaceholderStats(uuid, stats);
                }
            }
        }
        
        switch (lowerId) {
            case "wins":
                return String.valueOf(stats != null ? stats.getWins() : 0);
            
            case "losses":
                return String.valueOf(stats != null ? stats.getLosses() : 0);
            
            case "wlr":
                return String.format("%.2f", stats != null ? stats.getWLR() : 0.0);
            
            case "streak":
                return String.valueOf(stats != null ? stats.getWinstreak() : 0);
            
            case "best_streak":
                return String.valueOf(stats != null ? stats.getBestWinstreak() : 0);
            
            case "winstreak_color":
                // Cor baseada em winstreak (para TAB)
                if (stats == null) {
                    return "§7";
                }
                int streak = stats.getWinstreak();
                if (streak >= 10) {
                    return "§c"; // Vermelho - excelente
                } else if (streak >= 5) {
                    return "§e"; // Amarelo - bom
                } else if (streak >= 3) {
                    return "§a"; // Verde - ok
                } else {
                    return "§7"; // Cinza - baixo
                }
            
            case "queue_position":
                // Posição na queue (0 se não estiver)
                int position = plugin.getQueueManager().getQueuePosition(uuid);
                return String.valueOf(position);
            
            case "in_match":
                return plugin.getMatchManager().isInMatch(uuid) ? "true" : "false";
            
            case "opponent":
                // Nome do oponente (se em match)
                com.primeleague.x1.models.Match match = plugin.getMatchManager().getMatch(uuid);
                if (match != null) {
                    UUID opponentUuid = match.getPlayer1().equals(uuid) ? 
                        match.getPlayer2() : match.getPlayer1();
                    org.bukkit.entity.Player opponent = org.bukkit.Bukkit.getPlayer(opponentUuid);
                    return opponent != null ? opponent.getName() : "Desconhecido";
                }
                return "";
            
            case "match_time":
                // Tempo de match em segundos
                com.primeleague.x1.models.Match currentMatch = plugin.getMatchManager().getMatch(uuid);
                if (currentMatch != null && currentMatch.getStartTime() != null) {
                    long seconds = (System.currentTimeMillis() - currentMatch.getStartTime().getTime()) / 1000;
                    return String.valueOf(seconds);
                }
                return "0";
            
            case "kit":
                // Kit atual (se em match ou queue)
                com.primeleague.x1.models.Match matchForKit = plugin.getMatchManager().getMatch(uuid);
                if (matchForKit != null) {
                    return matchForKit.getKit();
                }
                // Verificar queue
                String queueKey = plugin.getQueueManager().getPlayerQueueKey(uuid);
                if (queueKey != null) {
                    // queueKey formato: "kit_ranked" ou "kit_unranked"
                    int underscoreIndex = queueKey.lastIndexOf('_');
                    if (underscoreIndex > 0) {
                        return queueKey.substring(0, underscoreIndex);
                    }
                }
                return "";
            
            case "elo_change":
                // Última mudança de ELO
                int change = plugin.getLastEloChange(uuid);
                if (change == 0) {
                    return "";
                }
                return change > 0 ? "+" + change : String.valueOf(change);
            
            case "queue_mode":
            case "queue_kit":
            case "queue_size":
                // Informações da queue
                String playerQueueKey = plugin.getQueueManager().getPlayerQueueKey(uuid);
                if (playerQueueKey == null) {
                    // Se não está na queue, retornar vazio ou tamanho total
                    if (lowerId.equals("queue_size")) {
                        return String.valueOf(plugin.getQueueManager().getQueueSize("total"));
                    }
                    return "";
                }
                int queueUnderscoreIndex = playerQueueKey.lastIndexOf('_');
                if (queueUnderscoreIndex <= 0) {
                    return "";
                }
                String queueKit = playerQueueKey.substring(0, queueUnderscoreIndex);
                String queueMode = playerQueueKey.substring(queueUnderscoreIndex + 1);
                
                if (lowerId.equals("queue_mode")) {
                    return queueMode.equals("ranked") ? "Ranked" : "Unranked";
                } else if (lowerId.equals("queue_kit")) {
                    return queueKit;
                } else if (lowerId.equals("queue_size")) {
                    return String.valueOf(plugin.getQueueManager().getQueueSize(playerQueueKey));
                }
                return "";
            
            case "match_kit":
            case "match_mode":
            case "match_arena":
            case "opponent_elo":
                // Informações do match
                com.primeleague.x1.models.Match matchInfo = plugin.getMatchManager().getMatch(uuid);
                if (matchInfo == null) {
                    return "";
                }
                
                if (lowerId.equals("match_kit")) {
                    return matchInfo.getKit();
                } else if (lowerId.equals("match_mode")) {
                    return matchInfo.isRanked() ? "Ranked" : "Unranked";
                } else if (lowerId.equals("match_arena")) {
                    return matchInfo.getArena() != null ? matchInfo.getArena().getName() : "Desconhecida";
                } else if (lowerId.equals("opponent_elo")) {
                    UUID opponentUuid = matchInfo.getPlayer1().equals(uuid) ? 
                        matchInfo.getPlayer2() : matchInfo.getPlayer1();
                    try {
                        if (com.primeleague.elo.EloAPI.isEnabled()) {
                            int opponentElo = com.primeleague.elo.EloAPI.getElo(opponentUuid);
                            return String.valueOf(opponentElo);
                        }
                    } catch (Exception e) {
                        // EloAPI não disponível
                    }
                    return "?";
                }
                return "";
            
            default:
                return null; // Placeholder desconhecido
        }
    }
}

