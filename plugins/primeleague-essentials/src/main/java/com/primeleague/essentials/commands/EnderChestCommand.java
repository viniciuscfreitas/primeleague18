package com.primeleague.essentials.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EnderChestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("essentials.enderchest")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        Player target = player;
        if (args.length > 0) {
            if (!player.hasPermission("essentials.enderchest.others")) {
                player.sendMessage(ChatColor.RED + "Sem permissão para ver enderchest de outros.");
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Jogador não encontrado.");
                return true;
            }
        }

        player.openInventory(target.getEnderChest());
        if (!target.equals(player)) {
            player.sendMessage(ChatColor.GREEN + "Abrindo enderchest de " + target.getName());
        }

        return true;
    }
}
