package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.WarpManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DelWarpCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;
        
        if (!player.hasPermission("essentials.admin")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Uso: /delwarp <nome>");
            return true;
        }

        String name = args[0].toLowerCase();

        if (WarpManager.getInstance().deleteWarp(name)) {
            player.sendMessage(ChatColor.GREEN + "Warp " + ChatColor.YELLOW + name + ChatColor.GREEN + " removida com sucesso.");
        } else {
            player.sendMessage(ChatColor.RED + "Warp não encontrada: " + name);
        }

        return true;
    }
}
