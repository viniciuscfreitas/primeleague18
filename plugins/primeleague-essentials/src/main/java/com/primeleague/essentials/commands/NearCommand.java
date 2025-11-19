package com.primeleague.essentials.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class NearCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;
        double radius = 100.0; // Raio padrão

        List<String> nearbyPlayers = new ArrayList<>();
        
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player) {
                Player target = (Player) entity;
                // Verificar se não está invisível/spectator (opcional)
                nearbyPlayers.add(target.getName() + " (" + (int) player.getLocation().distance(target.getLocation()) + "m)");
            }
        }

        if (nearbyPlayers.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Nenhum jogador encontrado num raio de " + (int) radius + " blocos.");
        } else {
            player.sendMessage(ChatColor.GOLD + "Jogadores próximos: " + ChatColor.YELLOW + String.join(", ", nearbyPlayers));
        }

        return true;
    }
}
