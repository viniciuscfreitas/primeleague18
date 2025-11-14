package com.primeleague.clans.models;

import java.util.Date;
import java.util.UUID;

/**
 * Modelo de dados de alerta do clan
 * Grug Brain: POJO simples
 */
public class ClanAlert {

    private int id;
    private int clanId;
    private UUID playerUuid;
    private String alertType;
    private String punishmentId;
    private String message;
    private UUID createdBy;
    private Date createdAt;
    private boolean removed;
    private Date removedAt;
    private UUID removedBy;

    public ClanAlert() {
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

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getPunishmentId() {
        return punishmentId;
    }

    public void setPunishmentId(String punishmentId) {
        this.punishmentId = punishmentId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    public Date getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Date removedAt) {
        this.removedAt = removedAt;
    }

    public UUID getRemovedBy() {
        return removedBy;
    }

    public void setRemovedBy(UUID removedBy) {
        this.removedBy = removedBy;
    }
}

