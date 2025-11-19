package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.HomeManager;
import com.primeleague.essentials.managers.TeleportManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HomeCommand implements CommandExecutor {

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

        Location loc = HomeManager.getInstance().getHome(player.getUniqueId(), homeName);
        if (loc == null) {
            if (args.length == 0) {
                // Tentar listar homes se não achou 'home' padrão
                player.performCommand("homes");
            } else {
                player.sendMessage(ChatColor.RED + "Home não encontrada: " + homeName);
            }
            return true;
        }

        // Salvar last location
        TeleportManager.getInstance().setLastLocation(player, player.getLocation());

        player.teleport(loc);
        player.sendMessage(ChatColor.GREEN + "Teletransportado para home " + ChatColor.YELLOW + homeName);
        return true;
    }
}
