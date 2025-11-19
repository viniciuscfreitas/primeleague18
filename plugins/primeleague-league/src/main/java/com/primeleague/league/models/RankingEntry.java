package com.primeleague.league.models;

/**
 * Modelo de entrada de ranking
 * Grug Brain: POJO simples para rankings
 */
public class RankingEntry {

    private String entityType; // 'CLAN' ou 'PLAYER'
    private String entityId; // UUID (player) ou INT (clan) como string
    private String entityName; // Nome do clan ou player (para exibição)
    private double value; // Valor do ranking (pontos, kills, etc)
    private int position; // Posição no ranking

    public RankingEntry() {
    }

    public RankingEntry(String entityType, String entityId, String entityName, double value, int position) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityName = entityName;
        this.value = value;
        this.position = position;
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

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}

