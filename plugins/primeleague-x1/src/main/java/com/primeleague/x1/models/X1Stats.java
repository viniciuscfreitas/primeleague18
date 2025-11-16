package com.primeleague.x1.models;

import java.util.Date;
import java.util.UUID;

/**
 * Stats específicas de duels x1
 * Grug Brain: POJO simples, separado de stats globais
 */
public class X1Stats {

    private UUID playerUuid;
    private int wins;
    private int losses;
    private int winstreak;
    private int bestWinstreak;
    private Date lastMatchAt;

    public X1Stats(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.wins = 0;
        this.losses = 0;
        this.winstreak = 0;
        this.bestWinstreak = 0;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public int getWinstreak() {
        return winstreak;
    }

    public void setWinstreak(int winstreak) {
        this.winstreak = winstreak;
    }

    public int getBestWinstreak() {
        return bestWinstreak;
    }

    public void setBestWinstreak(int bestWinstreak) {
        this.bestWinstreak = bestWinstreak;
    }

    public Date getLastMatchAt() {
        return lastMatchAt;
    }

    public void setLastMatchAt(Date lastMatchAt) {
        this.lastMatchAt = lastMatchAt;
    }

    /**
     * Calcula Win/Loss Ratio
     */
    public double getWLR() {
        if (losses == 0) {
            return wins > 0 ? wins : 0.0;
        }
        return (double) wins / losses;
    }
}

