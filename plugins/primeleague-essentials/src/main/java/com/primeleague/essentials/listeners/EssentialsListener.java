package com.primeleague.essentials.listeners;

import com.primeleague.essentials.EssentialsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class EssentialsListener implements Listener {

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage().toLowerCase();
        
        // Bloquear TPA em combate (Integração simples por enquanto)
        // TODO: Verificar CombatLogAPI quando existir
        if (msg.startsWith("/tpa") || msg.startsWith("/tpaccept") || msg.startsWith("/home") || msg.startsWith("/warp") || msg.startsWith("/spawn")) {
            // Se tiver CombatLog, verificar aqui
            // if (CombatLogAPI.isInCombat(player)) {
            //     event.setCancelled(true);
            //     player.sendMessage(ChatColor.RED + "Você não pode usar comandos em combate!");
            // }
        }
        
        // Logar comandos importantes (Integração com League)
        // TODO: Enviar evento para LeagueAPI
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        // Cancelar teleporte se mover (opcional, configurável)
    }
}
