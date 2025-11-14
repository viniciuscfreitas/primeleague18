package com.primeleague.clans.models;

import java.util.Date;
import java.util.UUID;

/**
 * Modelo de dados de membro do clan
 * Grug Brain: POJO simples, seguindo padrão PlayerData
 */
public class ClanMember {

    private int clanId;
    private UUID playerUuid;
    private String role;
    private Date joinedAt;

    public ClanMember() {
    }

    public ClanMember(int clanId, UUID playerUuid, String role) {
        this.clanId = clanId;
        this.playerUuid = playerUuid;
        this.role = role;
        this.joinedAt = new Date();
    }

    // Getters e Setters
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Date getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Date joinedAt) {
        this.joinedAt = joinedAt;
    }
}

