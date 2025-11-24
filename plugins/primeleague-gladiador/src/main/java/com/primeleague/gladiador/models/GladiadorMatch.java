package com.primeleague.gladiador.models;

import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modelo de partida de Gladiador ativa
 * Grug Brain: Estado em memória, não persiste até o fim
 */
public class GladiadorMatch {

    private UUID matchId;
    private Arena arena;
    private Map<Integer, ClanEntry> clans;  // clanId -> entry (ConcurrentHashMap para thread-safety)
    private Set<UUID> alivePlayers;
    private long startTime;
    private long preparationEndTime;
    private MatchState state;
    private int currentBorderSize;
    private BukkitTask borderTask;
    // Tracking de kills e dano por player (para MVP e DAMAGE)
    private Map<UUID, Integer> playerKills;  // playerUuid -> kills
    private Map<UUID, Double> playerDamage; // playerUuid -> total damage dealt

    public GladiadorMatch(Arena arena) {
        this.matchId = UUID.randomUUID();
        this.arena = arena;
        this.clans = new ConcurrentHashMap<>(); // Thread-safe
        this.alivePlayers = new HashSet<>(); // Acesso principalmente na thread principal
        this.state = MatchState.WAITING;
        this.currentBorderSize = arena.getInitialBorderSize();
        this.playerKills = new ConcurrentHashMap<>(); // Thread-safe
        this.playerDamage = new ConcurrentHashMap<>(); // Thread-safe
    }

    /**
     * Verifica se jogador está no match
     */
    public boolean hasPlayer(UUID playerUuid) {
        return alivePlayers.contains(playerUuid);
    }

    /**
     * Obtém total de players no match
     * Grug Brain: Conta pelos clans (mais confiável que alivePlayers)
     */
    public int getTotalPlayers() {
        int total = 0;
        for (ClanEntry entry : clans.values()) {
            total += entry.getMembers().size(); // Todos os membros do clan
        }
        return total;
    }

    /**
     * Obtém total de players vivos
     */
    public int getAlivePlayersCount() {
        return alivePlayers.size();
    }

    public java.util.Collection<ClanEntry> getClanEntries() {
        return clans.values();
    }

    public void addClanEntry(ClanEntry entry) {
        clans.put(entry.getClanId(), entry);
    }

    public ClanEntry getClanEntry(int clanId) {
        return clans.get(clanId);
    }

    /**
     * Obtém clan do jogador
     */
    public ClanEntry getClanEntry(UUID playerUuid) {
        for (ClanEntry entry : clans.values()) {
            if (entry.getMembers().contains(playerUuid)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Conta quantos clans ainda têm jogadores vivos
     */
    public int getAliveClansCount() {
        int count = 0;
        for (ClanEntry entry : clans.values()) {
            if (!entry.isEliminated()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Obtém clan vencedor (se apenas um vivo)
     */
    public ClanEntry getWinnerClan() {
        if (getAliveClansCount() != 1) {
            return null;
        }
        for (ClanEntry entry : clans.values()) {
            if (!entry.isEliminated()) {
                return entry;
            }
        }
        return null;
    }

    // Estado da partida
    public enum MatchState {
        WAITING,      // Esperando clans se juntarem
        PREPARATION,  // 5 minutos de preparação (PvP off)
        ACTIVE,       // PvP ativo, border shrinking
        ENDING        // Partida encerrada
    }

    // Getters e Setters
    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }

    public Arena getArena() {
        return arena;
    }

    public void setArena(Arena arena) {
        this.arena = arena;
    }

    public Map<Integer, ClanEntry> getClans() {
        return clans;
    }

    public void setClans(Map<Integer, ClanEntry> clans) {
        this.clans = clans;
    }

    public Set<UUID> getAlivePlayers() {
        return alivePlayers;
    }

    public void setAlivePlayers(Set<UUID> alivePlayers) {
        this.alivePlayers = alivePlayers;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getPreparationEndTime() {
        return preparationEndTime;
    }

    public void setPreparationEndTime(long preparationEndTime) {
        this.preparationEndTime = preparationEndTime;
    }

    public MatchState getState() {
        return state;
    }

    public void setState(MatchState state) {
        this.state = state;
    }

    public int getCurrentBorderSize() {
        return currentBorderSize;
    }

    public void setCurrentBorderSize(int currentBorderSize) {
        this.currentBorderSize = currentBorderSize;
    }

    public BukkitTask getBorderTask() {
        return borderTask;
    }

    public void setBorderTask(BukkitTask borderTask) {
        this.borderTask = borderTask;
    }

    /**
     * Incrementa kills de um player
     * Grug Brain: Usar merge atômico para thread safety
     */
    public void incrementPlayerKills(UUID playerUuid) {
        playerKills.merge(playerUuid, 1, Integer::sum);
    }

    /**
     * Obtém kills de um player
     */
    public int getPlayerKills(UUID playerUuid) {
        return playerKills.getOrDefault(playerUuid, 0);
    }

    /**
     * Adiciona dano causado por um player
     * Grug Brain: Usar merge atômico para thread safety
     */
    public void addPlayerDamage(UUID playerUuid, double damage) {
        playerDamage.merge(playerUuid, damage, Double::sum);
    }

    /**
     * Obtém dano total causado por um player
     */
    public double getPlayerDamage(UUID playerUuid) {
        return playerDamage.getOrDefault(playerUuid, 0.0);
    }

    /**
     * Obtém todos os player kills (para MVP)
     */
    public Map<UUID, Integer> getPlayerKills() {
        return new java.util.HashMap<>(playerKills);
    }

    /**
     * Obtém todos os player damage (para DAMAGE)
     */
    public Map<UUID, Double> getPlayerDamage() {
        return new java.util.HashMap<>(playerDamage);
    }
}
