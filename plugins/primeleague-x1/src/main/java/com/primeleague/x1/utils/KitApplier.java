package com.primeleague.x1.utils;

import com.primeleague.x1.models.Kit;
import org.bukkit.entity.Player;

/**
 * Utilitário para aplicar kits a players
 * Grug Brain: Função isolada, reutilizável, sem dependências complexas
 */
public class KitApplier {

    /**
     * Aplica kit a um player
     * Limpa inventário, remove effects, aplica items/armor/effects do kit
     */
    public static void applyKit(Player player, Kit kit) {
        if (player == null || kit == null) {
            return;
        }

        // Limpar inventário
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        // Remover effects
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Aplicar items
        if (kit.getItems() != null) {
            player.getInventory().setContents(kit.getItems());
        }

        // Aplicar armor
        if (kit.getArmor() != null) {
            player.getInventory().setArmorContents(kit.getArmor());
        }

        // Aplicar effects
        if (kit.getEffects() != null) {
            for (org.bukkit.potion.PotionEffect effect : kit.getEffects()) {
                player.addPotionEffect(effect);
            }
        }

        player.updateInventory();
    }
}





