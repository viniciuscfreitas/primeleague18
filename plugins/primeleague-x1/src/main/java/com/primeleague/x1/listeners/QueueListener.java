package com.primeleague.x1.listeners;

import com.primeleague.x1.X1Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener para gerenciar queue cleanup
 * Grug Brain: Remove player da queue quando desconectar
 */
public class QueueListener implements Listener {

    private final X1Plugin plugin;

    public QueueListener(X1Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Remove player da queue ao desconectar
     * Grug Brain: Previne players fantasmas na queue
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Remove from queue if player quits
        plugin.getQueueManager().removeFromQueue(event.getPlayer().getUniqueId());
        
        // Log para debug (opcional)
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().fine("Player " + event.getPlayer().getName() + " removed from queue (disconnect)");
        }
    }
}
