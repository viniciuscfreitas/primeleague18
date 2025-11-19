package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.KitManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class KitCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            List<String> kits = KitManager.getInstance().getKits();
            if (kits.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Nenhum kit disponível.");
            } else {
                player.sendMessage(ChatColor.GOLD + "Kits disponíveis: " + ChatColor.YELLOW + String.join(", ", kits));
            }
            return true;
        }

        String kitName = args[0].toLowerCase();
        
        if (!player.hasPermission("essentials.kits." + kitName)) {
            player.sendMessage(ChatColor.RED + "Sem permissão para este kit.");
            return true;
        }

        if (KitManager.getInstance().giveKit(player, kitName)) {
            player.sendMessage(ChatColor.GREEN + "Kit " + ChatColor.YELLOW + kitName + ChatColor.GREEN + " recebido!");
        } else {
            player.sendMessage(ChatColor.RED + "Kit não encontrado: " + kitName);
        }

        return true;
    }
}
