package com.arena;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando /arena - Teleporta e dá kit manualmente
 * Grug Brain: Comando simples inline, UX padronizado
 */
public class ArenaCommand implements CommandExecutor {

    private final ArenaPlugin plugin;

    public ArenaCommand(ArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[ARENA]");
            sender.sendMessage(prefixo + " §cApenas players podem usar este comando!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("arena.use")) {
            String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[ARENA]");
            player.sendMessage(prefixo + " §cVocê não tem permissão para usar este comando!");
            return true;
        }

        plugin.teleportToArena(player);
        plugin.giveKit(player);

        String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[ARENA]");
        player.sendMessage(prefixo + " §aTeleportado para arena e kit dado!");

        return true;
    }
}

