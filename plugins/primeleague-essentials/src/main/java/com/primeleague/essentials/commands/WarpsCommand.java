package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.WarpManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class WarpsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;
        List<String> warps = WarpManager.getInstance().getWarps();

        if (warps.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Nenhuma warp definida.");
            return true;
        }

        String warpsList = String.join(", ", warps);
        player.sendMessage(ChatColor.GOLD + "Warps disponíveis: " + ChatColor.YELLOW + warpsList);

        return true;
    }
}
