package com.primeleague.punishments.listeners;

import com.primeleague.punishments.PunishPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Listener de punições
 * Grug Brain: Listeners simples, prioridades corretas
 */
public class PunishListener implements Listener {

    private final PunishPlugin plugin;

    public PunishListener(PunishPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();
        String ip = event.getAddress().getHostAddress();

        // Verificar ban (UUID ou IP)
        if (plugin.getPunishManager().isBanned(uuid, ip)) {
            // Buscar motivo do ban (query DB ou cache)
            String reason = plugin.getPunishManager().getBanReason(uuid, ip);
            // Usar ChatColor ao invés de § para evitar problemas de encoding
            String message = org.bukkit.ChatColor.RED + "Voce esta banido!\n" +
                            org.bukkit.ChatColor.GRAY + "Motivo: " +
                            org.bukkit.ChatColor.WHITE + reason;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, message);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();

        // Verificar mute (LOW para executar antes de LOWEST)
        // Grug Brain: Verificar primeiro para evitar que spam check processe mensagens mutadas
        if (plugin.getPunishManager().isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            String reason = plugin.getPunishManager().getMuteReason(player.getUniqueId());
            // Scheduler para thread principal (Bukkit API)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(org.bukkit.ChatColor.RED + "Voce esta mutado!\n" +
                                 org.bukkit.ChatColor.GRAY + "Motivo: " +
                                 org.bukkit.ChatColor.WHITE + reason);
            });
        }
    }
}

