package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.WarpManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetWarpCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;
        
        if (!player.hasPermission("essentials.admin")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Uso: /setwarp <nome> [permissão]");
            return true;
        }

        String name = args[0].toLowerCase();
        String permission = args.length > 1 ? args[1] : null;

        if (WarpManager.getInstance().setWarp(name, player.getLocation(), permission)) {
            player.sendMessage(ChatColor.GREEN + "Warp " + ChatColor.YELLOW + name + ChatColor.GREEN + " definida com sucesso!");
        } else {
            player.sendMessage(ChatColor.RED + "Erro ao definir warp.");
        }

        return true;
    }
}
