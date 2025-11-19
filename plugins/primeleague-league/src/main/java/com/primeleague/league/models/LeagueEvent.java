package com.primeleague.league.models;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modelo de evento de league
 * Grug Brain: POJO simples, com métodos de conversão JSONB
 */
public class LeagueEvent {

    private long id;
    private int seasonId;
    private String entityType; // 'CLAN' ou 'PLAYER'
    private String entityId; // UUID (player) ou INT (clan) como string
    private String category; // 'GLADIADOR', 'X1', 'PVP', 'KOTH', 'ECONOMY', 'PUNISH', 'ELO', etc
    private String action; // 'WIN', 'KILL', 'DEATH', 'MVP', 'CAPTURE', 'BAN', 'PURCHASE', 'ELO_CHANGE', etc
    private double value; // +50 pontos, +1 kill, -500 penalidade, 1000000 coins, etc
    private String reason; // "1º lugar", "Kill PvP", "Compra loja", etc
    private Map<String, Object> metadata; // Dados extras (match_id, position, kills, old_elo, new_elo, etc)
    private boolean isDeleted;
    private Timestamp createdAt;
    private UUID createdBy; // Admin (se manual)

    public LeagueEvent() {
        this.metadata = new HashMap<>();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(int seasonId) {
        this.seasonId = seasonId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Converte metadata Map para JSON string (para salvar no banco)
     */
    public String metadataToJson() {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        JSONObject json = new JSONObject();
        json.putAll(metadata);
        return json.toJSONString();
    }

    /**
     * Converte JSON string para metadata Map (ao ler do banco)
     */
    public void metadataFromJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            this.metadata = new HashMap<>();
            return;
        }
        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(jsonString);
            this.metadata = new HashMap<>();
            for (Object key : json.keySet()) {
                this.metadata.put(key.toString(), json.get(key));
            }
        } catch (ParseException e) {
            this.metadata = new HashMap<>();
        }
    }
}

