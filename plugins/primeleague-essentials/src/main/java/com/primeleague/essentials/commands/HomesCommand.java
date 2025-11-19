package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.HomeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class HomesCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;
        List<String> homes = HomeManager.getInstance().getHomes(player.getUniqueId());

        if (homes.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Você não tem homes definidas.");
            return true;
        }

        String homesList = String.join(", ", homes);
        player.sendMessage(ChatColor.GOLD + "Suas homes: " + ChatColor.YELLOW + homesList);

        return true;
    }
}
