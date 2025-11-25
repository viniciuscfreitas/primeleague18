package com.primeleague.factions.listener;

import com.primeleague.factions.PrimeFactions;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener para limpar confirmações pendentes quando player sair
 * Grug Brain: Previne confirmações fantasmas na memória
 */
public class ConfirmationCleanupListener implements Listener {

    private final PrimeFactions plugin;

    public ConfirmationCleanupListener(PrimeFactions plugin) {
        this.plugin = plugin;
    }

    /**
     * Limpa confirmações pendentes quando player desconecta
     * CORREÇÃO: Previne memory leak de confirmações expiradas
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // CORREÇÃO: Limpar confirmação do player que saiu (previne memory leak)
        com.primeleague.factions.command.FactionsCommand cmd = plugin.getFactionsCommand();
        if (cmd != null) {
            cmd.clearPendingConfirmation(event.getPlayer().getUniqueId());
        }
    }
}

