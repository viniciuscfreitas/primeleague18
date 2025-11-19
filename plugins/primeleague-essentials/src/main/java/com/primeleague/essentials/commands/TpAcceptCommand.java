package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TpAcceptCommand implements CommandExecutor {

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
        if (requester == null) {
            target.sendMessage(ChatColor.RED + "O jogador que enviou a solicitação está offline.");
            TeleportManager.getInstance().removeTpaRequest(target);
            return true;
        }

        // Salvar last location do requester antes de teleportar
        TeleportManager.getInstance().setLastLocation(requester, requester.getLocation());

        requester.teleport(target.getLocation());
        requester.sendMessage(ChatColor.GREEN + "Teletransportado para " + ChatColor.YELLOW + target.getName());
        target.sendMessage(ChatColor.GREEN + "Você aceitou a solicitação de " + ChatColor.YELLOW + requester.getName());

        TeleportManager.getInstance().removeTpaRequest(target);
        return true;
    }
}
