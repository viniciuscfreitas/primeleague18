package com.primeleague.essentials.commands;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class HatCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("essentials.hat")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Você precisa ter um item na mão.");
            return true;
        }

        ItemStack head = player.getInventory().getHelmet();
        
        player.getInventory().setHelmet(hand);
        player.setItemInHand(head);
        
        player.sendMessage(ChatColor.GREEN + "Belo chapéu!");

        return true;
    }
}
