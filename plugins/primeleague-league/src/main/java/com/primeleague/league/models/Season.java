package com.primeleague.league.models;

import java.sql.Timestamp;

/**
 * Modelo de temporada
 * Grug Brain: POJO simples, sem abstrações
 */
public class Season {

    private int id;
    private String name;
    private Timestamp startDate;
    private Timestamp endDate;
    private String status; // ACTIVE, ENDED
    private Timestamp createdAt;

    public Season() {
    }

    public Season(int id, String name, Timestamp startDate, Timestamp endDate, String status, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.createdAt = createdAt;
    }

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

    public Timestamp getStartDate() {
        return startDate;
    }

    public void setStartDate(Timestamp startDate) {
        this.startDate = startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public void setEndDate(Timestamp endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Verifica se temporada está ativa
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
     * Verifica se temporada terminou
     */
    public boolean isEnded() {
        return "ENDED".equals(status);
    }
}

