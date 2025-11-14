package com.primeleague.clans.listeners;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.events.ClanPunishmentEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listener para eventos de punição de clan
 * Grug Brain: Listener simples, chama manager diretamente
 */
public class ClanPunishmentListener implements Listener {

    private final ClansPlugin plugin;

    public ClanPunishmentListener(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClanPunishment(ClanPunishmentEvent event) {
        plugin.getClansManager().addAlert(
            event.getClanId(),
            event.getPlayerUuid(),
            event.getAlertType(),
            event.getMessage(),
            null, // Criado automaticamente
            event.getPunishmentId()
        );
        plugin.getLogger().info("[CLAN PUNISHMENT] Clan ID " + event.getClanId() + " recebeu alerta " + event.getAlertType() + " - " + event.getMessage());
    }
}

