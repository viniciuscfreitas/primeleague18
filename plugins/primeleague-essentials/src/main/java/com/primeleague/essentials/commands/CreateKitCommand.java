package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.KitManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreateKitCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("essentials.createkit")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /createkit <nome> <delay_segundos>");
            return true;
        }

        String name = args[0].toLowerCase();
        long delay;
        try {
            delay = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Delay inválido.");
            return true;
        }

        KitManager.getInstance().createKit(name, player, delay);
        player.sendMessage(ChatColor.GREEN + "Kit " + ChatColor.YELLOW + name + ChatColor.GREEN + " criado com sucesso!");

        return true;
    }
}
