package com.primeleague.core.listeners;

import com.primeleague.core.CorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener para desativar mensagens padrão do servidor
 * Grug Brain: Solução direta - cancela mensagens no evento
 * Funciona independente de configurações do Paper/Spigot
 */
public class DefaultMessagesListener implements Listener {

    private final CorePlugin plugin;

    public DefaultMessagesListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Cancela mensagens de morte padrão
     * Grug Brain: setDeathMessage("") remove a mensagem do chat
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (plugin.getConfig().getBoolean("disable-messages.death", true)) {
            event.setDeathMessage(""); // Remove mensagem de morte do chat
        }
    }

    /**
     * Cancela mensagens de join padrão
     * Grug Brain: setJoinMessage("") remove a mensagem do chat
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("disable-messages.join", true)) {
            event.setJoinMessage(""); // Remove mensagem de join do chat
        }
    }

    /**
     * Cancela mensagens de quit padrão
     * Grug Brain: setQuitMessage("") remove a mensagem do chat
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("disable-messages.quit", true)) {
            event.setQuitMessage(""); // Remove mensagem de quit do chat
        }
    }
}

