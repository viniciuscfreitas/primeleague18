package com.primeleague.punishments.models;

import java.util.Date;
import java.util.UUID;

/**
 * Dados de uma punição
 * Grug Brain: POJO simples, apenas dados
 */
public class PunishmentData {
    private int id;
    private UUID playerUuid;
    private String ip;
    private String type;
    private String reason;
    private UUID staffUuid;
    private Date createdAt;
    private Date expiresAt;
    private boolean active;
    private boolean appealed;

    // Getters e Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getStaffUuid() {
        return staffUuid;
    }

    public void setStaffUuid(UUID staffUuid) {
        this.staffUuid = staffUuid;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAppealed() {
        return appealed;
    }

    public void setAppealed(boolean appealed) {
        this.appealed = appealed;
    }
}

