package com.primeleague.x1.managers;

import com.primeleague.elo.EloAPI;
import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.QueueEntry;
import com.primeleague.x1.utils.X1Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Queue System
 * Grug Brain: Estrutura thread-safe com matchmaking ELO-based para ranked, FIFO para unranked
 */
public class QueueManager {

    private final X1Plugin plugin;
    // Estrutura: queueKey (kit_ranked ou kit_unranked) -> Map<UUID, QueueEntry>
    private final Map<String, ConcurrentHashMap<UUID, QueueEntry>> queues;
    // Cache de tamanhos de queue
    private final Map<String, Integer> queueSizes;
    // Configurações
    private final int eloRangeInitial;
    private final int eloRangeIncrement;
    private final int eloRangeMax;
    private final long queueTimeout;
    private final int maxMatchesPerTick;

    public QueueManager(X1Plugin plugin) {
        this.plugin = plugin;
        this.queues = new ConcurrentHashMap<>();
        this.queueSizes = new ConcurrentHashMap<>();
        
        // Carregar configurações
        this.eloRangeInitial = plugin.getConfig().getInt("queue.elo-range-initial", 100);
        this.eloRangeIncrement = plugin.getConfig().getInt("queue.elo-range-increment", 50);
        this.eloRangeMax = plugin.getConfig().getInt("queue.elo-range-max", 500);
        this.queueTimeout = plugin.getConfig().getLong("queue.timeout", 300) * 1000; // Converter para ms
        this.maxMatchesPerTick = plugin.getConfig().getInt("queue.max-matches-per-tick", 3); // Processar múltiplos matches

        // Task periódica para matchmaking e timeout
        startMatchmakingTask();
    }

    /**
     * Adiciona player à queue
     * @param playerUuid UUID do player
     * @param kit Nome do kit
     * @param ranked Se é ranked ou não
     * @return true se adicionado com sucesso
     */
    public boolean addToQueue(UUID playerUuid, String kit, boolean ranked) {
        // Validar kit name (regex) - permitir qualquer nome válido
        if (kit == null || kit.isEmpty() || !X1Utils.isValidName(kit)) {
            return false;
        }

        // Verificar se já está na queue
        if (isInQueue(playerUuid)) {
            return false;
        }

        // Buscar ELO se ranked
        int elo = 0;
        if (ranked) {
            try {
                if (EloAPI.isEnabled()) {
                    elo = EloAPI.getElo(playerUuid);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erro ao buscar ELO (EloAPI não disponível): " + e.getMessage());
            }
        }

        // Criar entry
        QueueEntry entry = new QueueEntry(playerUuid, kit, ranked, elo);
        String queueKey = entry.getQueueKey();

        // Adicionar à queue
        queues.computeIfAbsent(queueKey, k -> new ConcurrentHashMap<>()).put(playerUuid, entry);
        updateQueueSize(queueKey);
        
        // Atualizar TAB prefix (se disponível)
        if (plugin.getTabIntegration() != null && plugin.getTabIntegration().isEnabled()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                plugin.getTabIntegration().updateQueuePrefix(player);
            }
        }

        return true;
    }

    /**
     * Remove player da queue
     * @param playerUuid UUID do player
     * @return true se removido
     */
    public boolean removeFromQueue(UUID playerUuid) {
        for (ConcurrentHashMap<UUID, QueueEntry> queue : queues.values()) {
            if (queue.remove(playerUuid) != null) {
                // Atualizar tamanhos de todas as queues (simplificado)
                updateAllQueueSizes();
                
                // Limpar TAB prefix (se disponível)
                if (plugin.getTabIntegration() != null && plugin.getTabIntegration().isEnabled()) {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null) {
                        plugin.getTabIntegration().clearPrefix(player);
                    }
                }
                
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica se player está na queue
     */
    public boolean isInQueue(UUID playerUuid) {
        for (ConcurrentHashMap<UUID, QueueEntry> queue : queues.values()) {
            if (queue.containsKey(playerUuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Busca match para um entry
     * @param entry Entry do player
     * @return QueueEntry do oponente ou null se não encontrado
     */
    public QueueEntry findMatch(QueueEntry entry) {
        String queueKey = entry.getQueueKey();
        ConcurrentHashMap<UUID, QueueEntry> queue = queues.get(queueKey);
        
        if (queue == null || queue.size() < 2) {
            return null;
        }

        if (entry.isRanked()) {
            // Matchmaking ELO-based: buscar player com ELO dentro de range
            return findMatchByElo(entry, queue);
        } else {
            // Matchmaking FIFO: primeiro da queue (exceto o próprio)
            return findMatchFIFO(entry, queue);
        }
    }

    /**
     * Matchmaking ELO-based: busca player com ELO dentro de range
     */
    private QueueEntry findMatchByElo(QueueEntry entry, ConcurrentHashMap<UUID, QueueEntry> queue) {
        int playerElo = entry.getElo();
        int range = eloRangeInitial;

        while (range <= eloRangeMax) {
            int minElo = playerElo - range;
            int maxElo = playerElo + range;

            // Buscar player com ELO dentro do range
            for (QueueEntry other : queue.values()) {
                if (other.getPlayerUuid().equals(entry.getPlayerUuid())) {
                    continue; // Pular o próprio
                }

                int otherElo = other.getElo();
                if (otherElo >= minElo && otherElo <= maxElo) {
                    return other;
                }
            }

            // Expandir range se não encontrou
            range += eloRangeIncrement;
        }

        return null; // Não encontrou match
    }

    /**
     * Matchmaking FIFO: primeiro da queue
     */
    private QueueEntry findMatchFIFO(QueueEntry entry, ConcurrentHashMap<UUID, QueueEntry> queue) {
        // Buscar entry mais antiga (menor timestamp)
        QueueEntry oldest = null;
        long oldestTimestamp = Long.MAX_VALUE;

        for (QueueEntry other : queue.values()) {
            if (other.getPlayerUuid().equals(entry.getPlayerUuid())) {
                continue; // Pular o próprio
            }

            if (other.getTimestamp() < oldestTimestamp) {
                oldestTimestamp = other.getTimestamp();
                oldest = other;
            }
        }

        return oldest;
    }

    /**
     * Obtém tamanho da queue
     */
    public int getQueueSize(String queueKey) {
        if (queueKey == null || queueKey.equals("total")) {
            // Retornar total de todas as queues
            int total = 0;
            for (ConcurrentHashMap<UUID, QueueEntry> queue : queues.values()) {
                total += queue.size();
            }
            return total;
        }
        return queueSizes.getOrDefault(queueKey, 0);
    }

    /**
     * Obtém posição do player na queue (1-based)
     * Grug Brain: Calcula posição baseado em timestamp (FIFO)
     */
    public int getQueuePosition(UUID playerUuid) {
        for (ConcurrentHashMap<UUID, QueueEntry> queue : queues.values()) {
            QueueEntry playerEntry = queue.get(playerUuid);
            if (playerEntry == null) {
                continue;
            }
            
            // Contar quantos players entraram antes (timestamp menor)
            int position = 1;
            long playerTimestamp = playerEntry.getTimestamp();
            for (QueueEntry other : queue.values()) {
                if (!other.getPlayerUuid().equals(playerUuid) && other.getTimestamp() < playerTimestamp) {
                    position++;
                }
            }
            return position;
        }
        return 0; // Não está na queue
    }

    /**
     * Obtém queue key do player (se estiver na queue)
     */
    public String getPlayerQueueKey(UUID playerUuid) {
        for (Map.Entry<String, ConcurrentHashMap<UUID, QueueEntry>> entry : queues.entrySet()) {
            if (entry.getValue().containsKey(playerUuid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Atualiza tamanho de uma queue específica
     */
    private void updateQueueSize(String queueKey) {
        ConcurrentHashMap<UUID, QueueEntry> queue = queues.get(queueKey);
        queueSizes.put(queueKey, queue != null ? queue.size() : 0);
    }

    /**
     * Atualiza tamanhos de todas as queues
     */
    private void updateAllQueueSizes() {
        for (String queueKey : queues.keySet()) {
            updateQueueSize(queueKey);
        }
    }

    /**
     * Inicia task periódica para matchmaking e timeout
     */
    private void startMatchmakingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Verificar timeouts
                long currentTime = System.currentTimeMillis();
                for (ConcurrentHashMap<UUID, QueueEntry> queue : queues.values()) {
                    Iterator<Map.Entry<UUID, QueueEntry>> it = queue.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<UUID, QueueEntry> entry = it.next();
                        if (currentTime - entry.getValue().getTimestamp() > queueTimeout) {
                            // Timeout - remover da queue
                            it.remove();
                            UUID playerId = entry.getKey();
                            plugin.getLogger().info("Player removido da queue por timeout: " + playerId);

                            // Notificar jogador (se online)
                            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerId);
                            if (player != null) {
                                String msg = plugin.getConfig().getString("messages.queue.timeout",
                                    "§cVocê foi removido da fila por tempo excedido");
                                player.sendMessage(msg);

                                // Limpar TAB prefix (se disponível)
                                if (plugin.getTabIntegration() != null && plugin.getTabIntegration().isEnabled()) {
                                    plugin.getTabIntegration().clearPrefix(player);
                                }
                            }
                        }
                    }
                }

                // Matchmaking: tentar encontrar matches (processar múltiplos por tick)
                int matchesProcessed = 0;
                for (ConcurrentHashMap<UUID, QueueEntry> queue : new ArrayList<>(queues.values())) {
                    if (queue.size() < 2 || matchesProcessed >= maxMatchesPerTick) {
                        continue;
                    }

                    // Buscar matches (criar cópia para evitar ConcurrentModificationException)
                    List<QueueEntry> entries = new ArrayList<>(queue.values());
                    if (entries.size() < 2) {
                        continue;
                    }

                    // Tentar encontrar match para o primeiro entry
                    QueueEntry entry1 = entries.get(0);
                    QueueEntry match = findMatch(entry1);
                    
                    if (match != null) {
                        // Verificar se ainda estão na queue (evitar race condition)
                        if (!queue.containsKey(entry1.getPlayerUuid()) || !queue.containsKey(match.getPlayerUuid())) {
                            continue; // Já foram removidos por outra thread
                        }
                        
                        // Remover ambos da queue (atômico)
                        QueueEntry removed1 = queue.remove(entry1.getPlayerUuid());
                        QueueEntry removed2 = queue.remove(match.getPlayerUuid());
                        
                        if (removed1 == null || removed2 == null) {
                            // Alguém já removeu - re-adicionar se necessário
                            if (removed1 != null) queue.put(entry1.getPlayerUuid(), entry1);
                            if (removed2 != null) queue.put(match.getPlayerUuid(), match);
                            continue;
                        }
                        
                        updateAllQueueSizes();

                        // Criar match via MatchManager (na thread principal)
                        plugin.getServer().getScheduler().runTask(plugin, new MatchCreationRunnable(entry1, match));
                        matchesProcessed++;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // A cada 1 segundo
    }

    /**
     * Runnable para criar match na thread principal
     * Grug Brain: Classe interna nomeada para evitar problemas com classes anônimas
     */
    private class MatchCreationRunnable implements Runnable {
        private final QueueEntry entry1;
        private final QueueEntry entry2;

        public MatchCreationRunnable(QueueEntry entry1, QueueEntry entry2) {
            this.entry1 = entry1;
            this.entry2 = entry2;
        }

        @Override
        public void run() {
            plugin.getMatchManager().createMatchFromQueue(entry1, entry2);
        }
    }
}

