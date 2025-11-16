package com.primeleague.x1.models;

import org.bukkit.Location;

import java.util.Date;
import java.util.UUID;

/**
 * Modelo de Match (Duelo)
 * Grug Brain: POJO simples, sem lógica de negócio
 */
public class Match {

    private UUID player1;
    private UUID player2;
    private String kit;
    private Arena arena;
    private boolean ranked;
    private MatchStatus status;
    private Date startTime;
    private Date endTime;
    private UUID winner;

    public enum MatchStatus {
        WAITING,
        FIGHTING,
        ENDED
    }

    public Match(UUID player1, UUID player2, String kit, Arena arena, boolean ranked) {
        this.player1 = player1;
        this.player2 = player2;
        this.kit = kit;
        this.arena = arena;
        this.ranked = ranked;
        this.status = MatchStatus.WAITING;
        this.startTime = new Date();
    }

    public UUID getPlayer1() {
        return player1;
    }

    public void setPlayer1(UUID player1) {
        this.player1 = player1;
    }

    public UUID getPlayer2() {
        return player2;
    }

    public void setPlayer2(UUID player2) {
        this.player2 = player2;
    }

    public String getKit() {
        return kit;
    }

    public void setKit(String kit) {
        this.kit = kit;
    }

    public Arena getArena() {
        return arena;
    }

    public void setArena(Arena arena) {
        this.arena = arena;
    }

    public boolean isRanked() {
        return ranked;
    }

    public void setRanked(boolean ranked) {
        this.ranked = ranked;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public UUID getWinner() {
        return winner;
    }

    public void setWinner(UUID winner) {
        this.winner = winner;
    }
}

