package com.primeleague.x1.managers;

import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Match;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gerenciador anti-farm de ELO
 * Grug Brain: Detecta padrões suspeitos sem limitar jogadores legítimos
 * 
 * Estratégia:
 * - Detecta matches muito rápidos (< 10s = suspeito)
 * - Detecta mesmo oponente repetido (3+ vezes em 5 minutos = suspeito)
 * - Reduz ELO ganho em matches suspeitos (50% do normal) mas não bloqueia
 */
public class AntiFarmManager {

    private final X1Plugin plugin;
    
    // Cache de matches recentes por player (últimos 10 matches)
    // UUID do player -> Lista de MatchRecord (thread-safe)
    private final Map<UUID, List<MatchRecord>> recentMatches;
    
    // Configurações
    private final int maxRecentMatches; // Quantos matches recentes manter por player
    private final long suspiciousTimeWindow; // Janela de tempo para considerar suspeito (ms)
    private final int minMatchDurationSeconds; // Duração mínima para não ser suspeito (segundos)
    private final int maxSameOpponentMatches; // Máximo de matches com mesmo oponente na janela
    private final double eloReductionFactor; // Fator de redução de ELO em matches suspeitos (0.5 = 50%)
    
    public AntiFarmManager(X1Plugin plugin) {
        this.plugin = plugin;
        this.recentMatches = new ConcurrentHashMap<>();
        
        // Carregar configurações
        this.maxRecentMatches = plugin.getConfig().getInt("anti-farm.max-recent-matches", 10);
        this.suspiciousTimeWindow = plugin.getConfig().getLong("anti-farm.suspicious-time-window", 300) * 1000; // 5 minutos
        this.minMatchDurationSeconds = plugin.getConfig().getInt("anti-farm.min-match-duration", 10); // 10 segundos
        this.maxSameOpponentMatches = plugin.getConfig().getInt("anti-farm.max-same-opponent-matches", 2); // 2 matches = 3 total
        this.eloReductionFactor = plugin.getConfig().getDouble("anti-farm.elo-reduction-factor", 0.5); // 50% do ELO
        
        // Task periódica para limpar cache antigo (a cada 5 minutos)
        startCleanupTask();
    }
    
    /**
     * Verifica se um match seria suspeito ANTES de criar
     * Retorna true se suspeito, false se normal
     */
    public boolean isSuspiciousMatch(UUID player1, UUID player2) {
        // Verificar se os players se enfrentaram recentemente demais
        int matchesTogether = countRecentMatchesTogether(player1, player2);
        if (matchesTogether >= maxSameOpponentMatches) {
            plugin.getLogger().info("Match suspeito detectado: " + player1 + " vs " + player2 + 
                " (se enfrentaram " + matchesTogether + " vezes recentemente)");
            return true;
        }
        
        return false;
    }
    
    /**
     * Registra um match após terminar e retorna fator de redução de ELO se suspeito
     * @param match Match que terminou
     * @param matchDurationSeconds Duração do match em segundos
     * @return Fator de redução de ELO (1.0 = normal, 0.5 = 50% do normal, etc.)
     */
    public double registerMatchAndGetEloFactor(Match match, long matchDurationSeconds) {
        UUID player1 = match.getPlayer1();
        UUID player2 = match.getPlayer2();
        long now = System.currentTimeMillis();
        
        // Verificar duração suspeita
        boolean suspiciousDuration = matchDurationSeconds < minMatchDurationSeconds;
        
        // Verificar matches recentes juntos
        int matchesTogether = countRecentMatchesTogether(player1, player2);
        boolean suspiciousFrequency = matchesTogether >= maxSameOpponentMatches;
        
        // Se suspeito, reduzir ELO
        if (suspiciousDuration || suspiciousFrequency) {
            String reason = suspiciousDuration ? "duração muito curta (" + matchDurationSeconds + "s)" : 
                          "muitos matches juntos (" + matchesTogether + " vezes)";
            plugin.getLogger().warning("Match suspeito detectado: " + player1 + " vs " + player2 + " - " + reason);
            
            // Registrar match suspeito
            recordMatch(player1, player2, now, matchDurationSeconds, true);
            recordMatch(player2, player1, now, matchDurationSeconds, true);
            
            return eloReductionFactor;
        }
        
        // Match normal - registrar normalmente
        recordMatch(player1, player2, now, matchDurationSeconds, false);
        recordMatch(player2, player1, now, matchDurationSeconds, false);
        
        return 1.0; // ELO normal
    }
    
    /**
     * Conta quantas vezes dois players se enfrentaram recentemente
     */
    private int countRecentMatchesTogether(UUID player1, UUID player2) {
        long now = System.currentTimeMillis();
        List<MatchRecord> player1Matches = recentMatches.getOrDefault(player1, Collections.emptyList());
        
        int count = 0;
        for (MatchRecord record : player1Matches) {
            // Verificar se está na janela de tempo e se é contra o mesmo oponente
            if (now - record.timestamp < suspiciousTimeWindow && 
                record.opponentUuid.equals(player2)) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Registra um match no histórico
     * Grug Brain: Thread-safe usando CopyOnWriteArrayList
     */
    private void recordMatch(UUID playerUuid, UUID opponentUuid, long timestamp, 
                            long durationSeconds, boolean suspicious) {
        List<MatchRecord> matches = recentMatches.computeIfAbsent(playerUuid, 
            k -> new CopyOnWriteArrayList<>());
        
        // Adicionar novo match
        matches.add(new MatchRecord(opponentUuid, timestamp, durationSeconds, suspicious));
        
        // Limitar tamanho da lista (manter apenas os mais recentes)
        // CopyOnWriteArrayList é thread-safe, mas precisa sincronizar para remover
        synchronized (matches) {
            if (matches.size() > maxRecentMatches) {
                matches.remove(0); // Remove o mais antigo
            }
        }
    }
    
    /**
     * Limpa cache antigo (chamado periodicamente)
     * Grug Brain: Thread-safe - CopyOnWriteArrayList permite iteração segura
     */
    private void cleanupOldRecords() {
        long now = System.currentTimeMillis();
        recentMatches.entrySet().removeIf(entry -> {
            List<MatchRecord> matches = entry.getValue();
            // CopyOnWriteArrayList permite iteração segura, mas remove precisa sincronizar
            synchronized (matches) {
                matches.removeIf(record -> now - record.timestamp > suspiciousTimeWindow * 2);
            }
            return matches.isEmpty();
        });
    }
    
    /**
     * Inicia task periódica de limpeza
     */
    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                cleanupOldRecords();
            }
        }, 6000L, 6000L); // A cada 5 minutos (6000 ticks)
    }
    
    /**
     * Classe interna para registrar matches recentes
     */
    private static class MatchRecord {
        final UUID opponentUuid;
        final long timestamp;
        final long durationSeconds;
        final boolean suspicious;
        
        MatchRecord(UUID opponentUuid, long timestamp, long durationSeconds, boolean suspicious) {
            this.opponentUuid = opponentUuid;
            this.timestamp = timestamp;
            this.durationSeconds = durationSeconds;
            this.suspicious = suspicious;
        }
    }
}

