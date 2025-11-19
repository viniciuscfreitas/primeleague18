package com.primeleague.essentials.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FeedCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("essentials.feed.others")) {
                sender.sendMessage(ChatColor.RED + "Sem permissão para alimentar outros.");
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Jogador não encontrado.");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Uso: /feed <player>");
                return true;
            }
            target = (Player) sender;
        }

        if (!sender.hasPermission("essentials.feed")) {
            sender.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        target.setFoodLevel(20);
        target.setSaturation(20);
        
        target.sendMessage(ChatColor.GREEN + "Sua fome foi saciada.");
        if (!target.equals(sender)) {
            sender.sendMessage(ChatColor.GREEN + target.getName() + " foi alimentado.");
        }

        return true;
    }
}
