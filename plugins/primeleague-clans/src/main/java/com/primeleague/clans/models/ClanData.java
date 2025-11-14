package com.primeleague.clans.models;

import java.util.Date;
import java.util.UUID;

/**
 * Modelo de dados do clan
 * Grug Brain: POJO simples, seguindo padrão PlayerData
 */
public class ClanData {

    private int id;
    private String name;
    private String tag;
    private String tagClean;
    private UUID leaderUuid;
    private Date createdAt;
    private String description;
    private Long discordChannelId;
    private Long discordRoleId;
    private String homeWorld;
    private Double homeX;
    private Double homeY;
    private Double homeZ;
    private Integer points;
    private Integer eventWinsCount;
    private Boolean blockedFromEvents;

    public ClanData() {
    }

    public ClanData(String name, String tag, String tagClean, UUID leaderUuid) {
        this.name = name;
        this.tag = tag;
        this.tagClean = tagClean;
        this.leaderUuid = leaderUuid;
        this.createdAt = new Date();
    }

    // Getters e Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTagClean() {
        return tagClean;
    }

    public void setTagClean(String tagClean) {
        this.tagClean = tagClean;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getDiscordChannelId() {
        return discordChannelId;
    }

    public void setDiscordChannelId(Long discordChannelId) {
        this.discordChannelId = discordChannelId;
    }

    public Long getDiscordRoleId() {
        return discordRoleId;
    }

    public void setDiscordRoleId(Long discordRoleId) {
        this.discordRoleId = discordRoleId;
    }

    public String getHomeWorld() {
        return homeWorld;
    }

    public void setHomeWorld(String homeWorld) {
        this.homeWorld = homeWorld;
    }

    public Double getHomeX() {
        return homeX;
    }

    public void setHomeX(Double homeX) {
        this.homeX = homeX;
    }

    public Double getHomeY() {
        return homeY;
    }

    public void setHomeY(Double homeY) {
        this.homeY = homeY;
    }

    public Double getHomeZ() {
        return homeZ;
    }

    public void setHomeZ(Double homeZ) {
        this.homeZ = homeZ;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public Integer getEventWinsCount() {
        return eventWinsCount;
    }

    public void setEventWinsCount(Integer eventWinsCount) {
        this.eventWinsCount = eventWinsCount;
    }

    public Boolean getBlockedFromEvents() {
        return blockedFromEvents;
    }

    public void setBlockedFromEvents(Boolean blockedFromEvents) {
        this.blockedFromEvents = blockedFromEvents;
    }
}

