package com.primeleague.clans.models;

import java.util.Date;
import java.util.UUID;

/**
 * Modelo de dados de vitória em evento do clan
 * Grug Brain: POJO simples
 */
public class EventWinRecord {

    private int id;
    private int clanId;
    private String eventName;
    private int pointsAwarded;
    private UUID awardedBy;
    private Date createdAt;

    public EventWinRecord() {
    }

    // Getters e Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getClanId() {
        return clanId;
    }

    public void setClanId(int clanId) {
        this.clanId = clanId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public void setPointsAwarded(int pointsAwarded) {
        this.pointsAwarded = pointsAwarded;
    }

    public UUID getAwardedBy() {
        return awardedBy;
    }

    public void setAwardedBy(UUID awardedBy) {
        this.awardedBy = awardedBy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}

