package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TpDenyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player target = (Player) sender;
        UUID requesterUuid = TeleportManager.getInstance().getTpaRequester(target);

        if (requesterUuid == null) {
            target.sendMessage(ChatColor.RED + "Você não tem solicitações de teletransporte pendentes.");
            return true;
        }

        Player requester = Bukkit.getPlayer(requesterUuid);
        if (requester != null) {
            requester.sendMessage(ChatColor.RED + target.getName() + " negou sua solicitação de teletransporte.");
        }

        target.sendMessage(ChatColor.GREEN + "Solicitação negada.");
        TeleportManager.getInstance().removeTpaRequest(target);
        return true;
    }
}
