package com.primeleague.essentials.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public class HealCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("essentials.heal.others")) {
                sender.sendMessage(ChatColor.RED + "Sem permissão para curar outros.");
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Jogador não encontrado.");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Uso: /heal <player>");
                return true;
            }
            target = (Player) sender;
        }

        if (!sender.hasPermission("essentials.heal")) {
            sender.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        target.setHealth(target.getMaxHealth());
        target.setFoodLevel(20);
        target.setSaturation(20);
        target.setFireTicks(0);
        for (PotionEffect effect : target.getActivePotionEffects()) {
            target.removePotionEffect(effect.getType());
        }
        
        target.sendMessage(ChatColor.GREEN + "Você foi curado.");
        if (!target.equals(sender)) {
            sender.sendMessage(ChatColor.GREEN + target.getName() + " foi curado.");
        }

        return true;
    }
}
