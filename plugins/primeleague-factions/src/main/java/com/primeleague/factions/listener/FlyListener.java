package com.primeleague.factions.listener;

import com.primeleague.factions.PrimeFactions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class FlyListener implements Listener {

    private final PrimeFactions plugin;

    public FlyListener(PrimeFactions plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            
            // Check if attacker is a player or projectile from player
            if (event.getDamager() instanceof Player) {
                Player attacker = (Player) event.getDamager();
                plugin.getFlyManager().onCombat(victim);
                plugin.getFlyManager().onCombat(attacker);
            } else {
                // Even if damaged by mob/environment, we might want to disable fly?
                // For now, let's stick to PvP combat as per "CombatTag" usually implies PvP.
                // But if user wants "disable on hit", we can uncomment below:
                // plugin.getFlyManager().onCombat(victim);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getFlyManager().setFly(event.getPlayer(), false);
    }
}
