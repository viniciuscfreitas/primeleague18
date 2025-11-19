package com.primeleague.essentials.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GamemodeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("essentials.gamemode")) {
            sender.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Uso: /gamemode <modo> [player]");
            return true;
        }

        GameMode mode = null;
        String modeStr = args[0].toLowerCase();
        
        if (modeStr.equals("0") || modeStr.startsWith("s")) mode = GameMode.SURVIVAL;
        else if (modeStr.equals("1") || modeStr.startsWith("c")) mode = GameMode.CREATIVE;
        else if (modeStr.equals("2") || modeStr.startsWith("a")) mode = GameMode.ADVENTURE;
        else if (modeStr.equals("3") || modeStr.startsWith("sp")) mode = GameMode.SPECTATOR;

        if (mode == null) {
            sender.sendMessage(ChatColor.RED + "Modo inválido. Use 0, 1, 2, 3.");
            return true;
        }

        Player target;
        if (args.length > 1) {
            if (!sender.hasPermission("essentials.gamemode.others")) {
                sender.sendMessage(ChatColor.RED + "Sem permissão para alterar gamemode de outros.");
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Jogador não encontrado.");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Uso: /gamemode <modo> <player>");
                return true;
            }
            target = (Player) sender;
        }

        target.setGameMode(mode);
        target.sendMessage(ChatColor.YELLOW + "Seu modo de jogo foi alterado para " + ChatColor.GOLD + mode.name());
        
        if (!target.equals(sender)) {
            sender.sendMessage(ChatColor.YELLOW + "Modo de jogo de " + target.getName() + " alterado para " + ChatColor.GOLD + mode.name());
        }

        return true;
    }
}
