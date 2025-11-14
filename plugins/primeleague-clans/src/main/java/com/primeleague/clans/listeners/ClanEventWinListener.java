package com.primeleague.clans.listeners;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.events.ClanWinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listener para eventos de vitória de clan
 * Grug Brain: Listener simples, chama manager diretamente
 */
public class ClanEventWinListener implements Listener {

    private final ClansPlugin plugin;

    public ClanEventWinListener(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClanWin(ClanWinEvent event) {
        plugin.getClansManager().addEventWin(
            event.getClanId(),
            event.getEventName(),
            event.getPoints(),
            event.getAwardedBy()
        );
        plugin.getLogger().info("[CLAN WIN] Clan ID " + event.getClanId() + " ganhou evento " + event.getEventName() + " (+" + event.getPoints() + " pontos)");
    }
}

