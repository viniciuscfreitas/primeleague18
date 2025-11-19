package com.primeleague.gladiador.models;

/**
 * Modelo de dados da arena
 * Grug Brain: POJO simples, spawn points armazenados como JSONB no banco
 */
public class Arena {

    private int id;
    private String name;
    private String world;
    private double centerX;
    private double centerY;
    private double centerZ;
    private int initialBorderSize;
    private int finalBorderSize;
    private String spectatorWorld;
    private double spectatorX;
    private double spectatorY;
    private double spectatorZ;
    private float spectatorYaw;
    private float spectatorPitch;
    private boolean enabled;

    public Arena() {
    }

    public Arena(String name, String world, double centerX, double centerY, double centerZ) {
        this.name = name;
        this.world = world;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.initialBorderSize = 500;
        this.finalBorderSize = 20;
        this.enabled = true;
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

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public double getCenterX() {
        return centerX;
    }

    public void setCenterX(double centerX) {
        this.centerX = centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public void setCenterY(double centerY) {
        this.centerY = centerY;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public void setCenterZ(double centerZ) {
        this.centerZ = centerZ;
    }

    public int getInitialBorderSize() {
        return initialBorderSize;
    }

    public void setInitialBorderSize(int initialBorderSize) {
        this.initialBorderSize = initialBorderSize;
    }

    public int getFinalBorderSize() {
        return finalBorderSize;
    }

    public void setFinalBorderSize(int finalBorderSize) {
        this.finalBorderSize = finalBorderSize;
    }

    public String getSpectatorWorld() {
        return spectatorWorld;
    }

    public void setSpectatorWorld(String spectatorWorld) {
        this.spectatorWorld = spectatorWorld;
    }

    public double getSpectatorX() {
        return spectatorX;
    }

    public void setSpectatorX(double spectatorX) {
        this.spectatorX = spectatorX;
    }

    public double getSpectatorY() {
        return spectatorY;
    }

    public void setSpectatorY(double spectatorY) {
        this.spectatorY = spectatorY;
    }

    public double getSpectatorZ() {
        return spectatorZ;
    }

    public void setSpectatorZ(double spectatorZ) {
        this.spectatorZ = spectatorZ;
    }

    public float getSpectatorYaw() {
        return spectatorYaw;
    }

    public void setSpectatorYaw(float spectatorYaw) {
        this.spectatorYaw = spectatorYaw;
    }

    public float getSpectatorPitch() {
        return spectatorPitch;
    }

    public void setSpectatorPitch(float spectatorPitch) {
        this.spectatorPitch = spectatorPitch;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
