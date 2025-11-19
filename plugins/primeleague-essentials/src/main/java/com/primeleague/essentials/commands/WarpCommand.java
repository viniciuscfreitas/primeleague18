package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.TeleportManager;
import com.primeleague.essentials.managers.WarpManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WarpCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.performCommand("warps");
            return true;
        }

        String name = args[0].toLowerCase();
        Location loc = WarpManager.getInstance().getWarp(name);

        if (loc == null) {
            player.sendMessage(ChatColor.RED + "Warp não encontrada: " + name);
            return true;
        }

        // Salvar last location
        TeleportManager.getInstance().setLastLocation(player, player.getLocation());

        player.teleport(loc);
        player.sendMessage(ChatColor.GREEN + "Teletransportado para warp " + ChatColor.YELLOW + name);
        return true;
    }
}
