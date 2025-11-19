package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.TeleportManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BackCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;
        Location lastLoc = TeleportManager.getInstance().getLastLocation(player);

        if (lastLoc == null) {
            player.sendMessage(ChatColor.RED + "Nenhuma localização anterior salva.");
            return true;
        }

        // Salvar localização atual como last location (para poder voltar de novo)
        TeleportManager.getInstance().setLastLocation(player, player.getLocation());

        player.teleport(lastLoc);
        player.sendMessage(ChatColor.GREEN + "Voltando para a última localização...");
        return true;
    }
}
