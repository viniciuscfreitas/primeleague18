package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpaCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Uso: /tpa <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Jogador não encontrado.");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "Você não pode enviar TPA para si mesmo.");
            return true;
        }

        TeleportManager.getInstance().sendTpaRequest(player, target);
        
        player.sendMessage(ChatColor.GREEN + "Solicitação de teletransporte enviada para " + ChatColor.YELLOW + target.getName());
        target.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " deseja se teletransportar até você.");
        target.sendMessage(ChatColor.GREEN + "Use " + ChatColor.YELLOW + "/tpaccept" + ChatColor.GREEN + " para aceitar ou " + ChatColor.RED + "/tpdeny" + ChatColor.GREEN + " para negar.");

        return true;
    }
}
