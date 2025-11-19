package com.primeleague.essentials.commands;

import com.primeleague.essentials.managers.TeleportManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TopCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;
        Location loc = player.getLocation();
        
        // Buscar bloco mais alto
        int highestY = player.getWorld().getHighestBlockYAt(loc);
        
        // Adicionar 1 para ficar em cima do bloco
        Location topLoc = new Location(loc.getWorld(), loc.getX(), highestY + 1, loc.getZ(), loc.getYaw(), loc.getPitch());
        
        // Salvar last location
        TeleportManager.getInstance().setLastLocation(player, player.getLocation());
        
        player.teleport(topLoc);
        player.sendMessage(ChatColor.GREEN + "Teletransportado para o topo!");
        
        return true;
    }
}
