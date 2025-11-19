package com.primeleague.gladiador.listeners;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Listener de proteção da arena
 * Grug Brain: Bloqueia quebrar/colocar blocos durante evento Gladiador
 */
public class ProtectionListener implements Listener {

    private final GladiadorPlugin plugin;

    public ProtectionListener(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        GladiadorMatch match = plugin.getMatchManager().getCurrentMatch();
        if (match == null) return;

        if (match.hasPlayer(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        GladiadorMatch match = plugin.getMatchManager().getCurrentMatch();
        if (match == null) return;

        if (match.hasPlayer(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
