package com.primeleague.core.models;

import java.util.Date;
import java.util.UUID;

/**
 * Modelo de dados do player
 * Grug Brain: POJO simples, sem getters/setters complexos
 */
public class PlayerData {

    private UUID uuid;
    private String name;
    private String ipHash;
    private Long discordId;
    private String accessCode;
    private Date accessExpiresAt;
    private String paymentStatus;
    private long money;
    private int elo;
    private Date createdAt;

    // Stats de combate
    private int kills = 0;
    private int deaths = 0;
    private int killstreak = 0;
    private int bestKillstreak = 0;
    private Date lastKillAt;
    private Date lastDeathAt;
    private Date lastSeenAt;

    public PlayerData() {
    }

    public PlayerData(UUID uuid, String name, String ipHash) {
        this.uuid = uuid;
        this.name = name;
        this.ipHash = ipHash;
        this.money = 0;
        this.elo = 1000;
        this.createdAt = new Date();
    }

    // Getters e Setters
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIpHash() {
        return ipHash;
    }

    public void setIpHash(String ipHash) {
        this.ipHash = ipHash;
    }

    public Long getDiscordId() {
        return discordId;
    }

    public void setDiscordId(Long discordId) {
        this.discordId = discordId;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    public Date getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public void setAccessExpiresAt(Date accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public long getMoney() {
        return money;
    }

    public void setMoney(long money) {
        this.money = money;
    }

    public int getElo() {
        return elo;
    }

    public void setElo(int elo) {
        this.elo = elo;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    // Getters e Setters - Stats de combate
    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public int getKillstreak() {
        return killstreak;
    }

    public void setKillstreak(int killstreak) {
        this.killstreak = killstreak;
    }

    public int getBestKillstreak() {
        return bestKillstreak;
    }

    public void setBestKillstreak(int bestKillstreak) {
        this.bestKillstreak = bestKillstreak;
    }

    public Date getLastKillAt() {
        return lastKillAt;
    }

    public void setLastKillAt(Date lastKillAt) {
        this.lastKillAt = lastKillAt;
    }

    public Date getLastDeathAt() {
        return lastDeathAt;
    }

    public void setLastDeathAt(Date lastDeathAt) {
        this.lastDeathAt = lastDeathAt;
    }

    public Date getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Date lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}

