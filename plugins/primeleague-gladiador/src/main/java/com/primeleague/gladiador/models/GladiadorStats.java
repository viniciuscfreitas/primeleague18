package com.primeleague.gladiador.models;

import java.util.Date;

/**
 * Modelo de estatísticas de clan no Gladiador
 * Grug Brain: POJO simples para estadísticas persistentes
 */
public class GladiadorStats {

    private int clanId;
    private int wins;
    private int participations;
    private int totalKills;
    private int totalDeaths;
    private int seasonPoints;
    private Date lastWin;

    public GladiadorStats() {
    }

    public GladiadorStats(int clanId) {
        this.clanId = clanId;
        this.wins = 0;
        this.participations = 0;
        this.totalKills = 0;
        this.totalDeaths = 0;
        this.seasonPoints = 0;
    }

    // Getters e Setters
    public int getClanId() {
        return clanId;
    }

    public void setClanId(int clanId) {
        this.clanId = clanId;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getParticipations() {
        return participations;
    }

    public void setParticipations(int participations) {
        this.participations = participations;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void setTotalKills(int totalKills) {
        this.totalKills = totalKills;
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public void setTotalDeaths(int totalDeaths) {
        this.totalDeaths = totalDeaths;
    }

    public int getSeasonPoints() {
        return seasonPoints;
    }

    public void setSeasonPoints(int seasonPoints) {
        this.seasonPoints = seasonPoints;
    }

    public Date getLastWin() {
        return lastWin;
    }

    public void setLastWin(Date lastWin) {
        this.lastWin = lastWin;
    }

    /**
     * Calcula win rate (porcentagem de vitórias)
     */
    public double getWinRate() {
        if (participations == 0) return 0.0;
        return ((double) wins / participations) * 100.0;
    }

    /**
     * Calcula K/D ratio
     */
    public double getKDRatio() {
        if (totalDeaths == 0) return totalKills;
        return (double) totalKills / totalDeaths;
    }
}
