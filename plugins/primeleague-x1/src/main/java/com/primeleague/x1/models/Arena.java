package com.primeleague.x1.models;

import org.bukkit.Location;

/**
 * Modelo de Arena
 * Grug Brain: POJO simples, armazena locações
 */
public class Arena {

    private String name;
    private String worldName;
    private Location spawn1;
    private Location spawn2;
    private Location center;
    private boolean enabled;
    private boolean inUse;

    public Arena(String name, String worldName, Location spawn1, Location spawn2, Location center) {
        this.name = name;
        this.worldName = worldName;
        this.spawn1 = spawn1;
        this.spawn2 = spawn2;
        this.center = center;
        this.enabled = true;
        this.inUse = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public Location getSpawn1() {
        return spawn1;
    }

    public void setSpawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    public Location getSpawn2() {
        return spawn2;
    }

    public void setSpawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    public Location getCenter() {
        return center;
    }

    public void setCenter(Location center) {
        this.center = center;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }
}

