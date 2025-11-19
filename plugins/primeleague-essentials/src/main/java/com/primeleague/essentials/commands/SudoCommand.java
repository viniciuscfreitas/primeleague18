package com.primeleague.essentials.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SudoCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("essentials.sudo")) {
            sender.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /sudo <player> <comando/chat>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jogador não encontrado.");
            return true;
        }

        StringBuilder commandBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) commandBuilder.append(" ");
            commandBuilder.append(args[i]);
        }
        String commandToRun = commandBuilder.toString();

        if (commandToRun.startsWith("c:")) {
            target.chat(commandToRun.substring(2));
            sender.sendMessage(ChatColor.GREEN + "Forçado " + target.getName() + " a falar: " + commandToRun.substring(2));
        } else {
            target.performCommand(commandToRun);
            sender.sendMessage(ChatColor.GREEN + "Forçado " + target.getName() + " a executar: /" + commandToRun);
        }

        return true;
    }
}
