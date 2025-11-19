package com.primeleague.essentials.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PTimeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("essentials.ptime")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Uso: /ptime <day|night|reset>");
            return true;
        }

        String time = args[0].toLowerCase();
        if (time.equals("day") || time.equals("dia")) {
            player.setPlayerTime(6000, false);
            player.sendMessage(ChatColor.YELLOW + "Tempo pessoal definido para dia.");
        } else if (time.equals("night") || time.equals("noite")) {
            player.setPlayerTime(18000, false);
            player.sendMessage(ChatColor.YELLOW + "Tempo pessoal definido para noite.");
        } else if (time.equals("reset")) {
            player.resetPlayerTime();
            player.sendMessage(ChatColor.YELLOW + "Tempo pessoal resetado.");
        } else {
            try {
                long ticks = Long.parseLong(time);
                player.setPlayerTime(ticks, false);
                player.sendMessage(ChatColor.YELLOW + "Tempo pessoal definido para " + ticks + " ticks.");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Tempo inválido.");
            }
        }

        return true;
    }
}
