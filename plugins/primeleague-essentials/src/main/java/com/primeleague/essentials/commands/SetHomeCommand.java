package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.HomeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetHomeCommand implements CommandExecutor {

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

        // Verificar limite de homes (TODO: integrar com permissões/VIP)
        int limit = 1;
        if (player.hasPermission("essentials.homes.vip")) limit = 5;
        if (player.hasPermission("essentials.homes.mvp")) limit = 10;
        if (player.hasPermission("essentials.homes.unlimited")) limit = 100;

        int currentCount = HomeManager.getInstance().getHomeCount(player.getUniqueId());
        
        // Se já existe a home, é update, não conta pro limite
        boolean exists = HomeManager.getInstance().getHome(player.getUniqueId(), homeName) != null;
        
        if (!exists && currentCount >= limit) {
            player.sendMessage(ChatColor.RED + "Você atingiu o limite de homes (" + limit + ").");
            return true;
        }

        if (HomeManager.getInstance().setHome(player.getUniqueId(), homeName, player.getLocation())) {
            player.sendMessage(ChatColor.GREEN + "Home " + ChatColor.YELLOW + homeName + ChatColor.GREEN + " definida com sucesso!");
        } else {
            player.sendMessage(ChatColor.RED + "Erro ao definir home.");
        }

        return true;
    }
}
