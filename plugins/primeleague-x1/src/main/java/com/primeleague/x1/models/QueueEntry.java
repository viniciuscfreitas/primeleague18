package com.primeleague.x1.models;

import java.util.UUID;

/**
 * Entrada na Queue
 * Grug Brain: POJO simples, armazena dados do player na queue
 */
public class QueueEntry {

    private UUID playerUuid;
    private String kit;
    private boolean ranked;
    private long timestamp;
    private int elo;

    public QueueEntry(UUID playerUuid, String kit, boolean ranked, int elo) {
        this.playerUuid = playerUuid;
        this.kit = kit;
        this.ranked = ranked;
        this.timestamp = System.currentTimeMillis();
        this.elo = elo;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getKit() {
        return kit;
    }

    public void setKit(String kit) {
        this.kit = kit;
    }

    public boolean isRanked() {
        return ranked;
    }

    public void setRanked(boolean ranked) {
        this.ranked = ranked;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getElo() {
        return elo;
    }

    public void setElo(int elo) {
        this.elo = elo;
    }

    /**
     * Gera chave única para a queue (kit_ranked ou kit_unranked)
     */
    public String getQueueKey() {
        return kit + "_" + (ranked ? "ranked" : "unranked");
    }
}

