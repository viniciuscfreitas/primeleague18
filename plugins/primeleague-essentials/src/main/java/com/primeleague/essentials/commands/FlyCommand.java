package com.primeleague.essentials.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Uso: /fly <player>");
            return true;
        }

        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("essentials.fly.others")) {
                sender.sendMessage(ChatColor.RED + "Sem permissão para alterar voo de outros.");
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Jogador não encontrado.");
                return true;
            }
        } else {
            target = (Player) sender;
        }

        if (!sender.hasPermission("essentials.fly")) {
            sender.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        boolean flying = !target.getAllowFlight();
        target.setAllowFlight(flying);
        
        String status = flying ? ChatColor.GREEN + "ativado" : ChatColor.RED + "desativado";
        target.sendMessage(ChatColor.YELLOW + "Modo de voo " + status);
        
        if (!target.equals(sender)) {
            sender.sendMessage(ChatColor.YELLOW + "Modo de voo de " + target.getName() + " " + status);
        }

        return true;
    }
}
