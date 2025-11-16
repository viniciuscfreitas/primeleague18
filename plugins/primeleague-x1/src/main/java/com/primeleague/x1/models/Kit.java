package com.primeleague.x1.models;

import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * Modelo de Kit
 * Grug Brain: POJO simples, armazena items e effects
 */
public class Kit {

    private String name;
    private ItemStack[] items;
    private ItemStack[] armor;
    private PotionEffect[] effects;
    private boolean enabled;

    public Kit(String name) {
        this.name = name;
        this.items = new ItemStack[36];
        this.armor = new ItemStack[4];
        this.effects = new PotionEffect[0];
        this.enabled = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ItemStack[] getItems() {
        return items;
    }

    public void setItems(ItemStack[] items) {
        this.items = items;
    }

    public ItemStack[] getArmor() {
        return armor;
    }

    public void setArmor(ItemStack[] armor) {
        this.armor = armor;
    }

    public PotionEffect[] getEffects() {
        return effects;
    }

    public void setEffects(PotionEffect[] effects) {
        this.effects = effects;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

