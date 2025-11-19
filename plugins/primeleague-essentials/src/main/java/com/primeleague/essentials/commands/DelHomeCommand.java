package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.HomeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DelHomeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;
        String homeName = "home";

        if (args.length > 0) {
            homeName = args[0].toLowerCase();
        }

        if (HomeManager.getInstance().deleteHome(player.getUniqueId(), homeName)) {
            player.sendMessage(ChatColor.GREEN + "Home " + ChatColor.YELLOW + homeName + ChatColor.GREEN + " removida com sucesso.");
        } else {
            player.sendMessage(ChatColor.RED + "Home não encontrada: " + homeName);
        }

        return true;
    }
}
