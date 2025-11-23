package com.primeleague.x1.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler de snapshots de inventário/armadura/efeitos
 * Grug Brain: Classe isolada, thread-safe, sem dependências complexas
 */
public class MatchSnapshotHandler {

    // Snapshot de inventário/armadura/efeitos por jogador
    private final Map<java.util.UUID, PlayerSnapshot> snapshots;

    public MatchSnapshotHandler() {
        this.snapshots = new ConcurrentHashMap<>();
    }

    /**
     * Captura snapshot do jogador (inventário, armadura, efeitos, localização)
     */
    public void captureSnapshot(Player player) {
        if (player == null || !player.isOnline()) return;
        if (snapshots.containsKey(player.getUniqueId())) return;
        
        org.bukkit.inventory.ItemStack[] inv = player.getInventory().getContents();
        org.bukkit.inventory.ItemStack[] armor = player.getInventory().getArmorContents();
        java.util.Collection<PotionEffect> effects = new ArrayList<>(player.getActivePotionEffects());
        Location location = player.getLocation().clone(); // Clone para evitar referência mutável
        
        snapshots.put(player.getUniqueId(), new PlayerSnapshot(inv, armor, effects, location));
    }

    /**
     * Restaura snapshot se existir e limpa o kit aplicado
     * @param restoreLocation Se true, restaura também a localização
     */
    public void restoreSnapshot(java.util.UUID playerId, boolean restoreLocation) {
        if (playerId == null) return;
        
        Player player = Bukkit.getPlayer(playerId);
        PlayerSnapshot snap = snapshots.get(playerId);
        
        if (player == null || snap == null) return;
        
        try {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            
            if (snap.inventory != null) {
                player.getInventory().setContents(snap.inventory);
            }
            if (snap.armor != null) {
                player.getInventory().setArmorContents(snap.armor);
            }
            if (snap.effects != null) {
                for (PotionEffect e : snap.effects) {
                    player.addPotionEffect(e);
                }
            }
            
            // Restaurar localização se solicitado e disponível
            if (restoreLocation && snap.location != null) {
                player.teleport(snap.location);
            }
            
            player.updateInventory();
        } catch (Exception ignored) {
            // Ignorar erros ao restaurar (player pode ter desconectado)
        }
    }

    /**
     * Restaura snapshot sem restaurar localização (compatibilidade)
     */
    public void restoreSnapshot(java.util.UUID playerId) {
        restoreSnapshot(playerId, false);
    }

    /**
     * Remove snapshot de um jogador (limpeza)
     */
    public void clearSnapshot(java.util.UUID playerId) {
        snapshots.remove(playerId);
    }

    /**
     * Limpa todos os snapshots (usado em cleanup)
     */
    public void clearAll() {
        snapshots.clear();
    }

    /**
     * Estrutura simples de snapshot
     * Grug Brain: POJO simples, sem lógica
     */
    private static class PlayerSnapshot {
        private final org.bukkit.inventory.ItemStack[] inventory;
        private final org.bukkit.inventory.ItemStack[] armor;
        private final java.util.Collection<PotionEffect> effects;
        private final Location location;

        private PlayerSnapshot(org.bukkit.inventory.ItemStack[] inventory,
                               org.bukkit.inventory.ItemStack[] armor,
                               java.util.Collection<PotionEffect> effects,
                               Location location) {
            this.inventory = inventory != null ? inventory.clone() : null;
            this.armor = armor != null ? armor.clone() : null;
            this.effects = effects;
            this.location = location != null ? location.clone() : null;
        }
    }
}

