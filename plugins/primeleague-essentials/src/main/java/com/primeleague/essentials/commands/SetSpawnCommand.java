package com.primeleague.essentials.commands;

import com.primeleague.essentials.EssentialsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;
        
        if (!player.hasPermission("essentials.admin")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        Location loc = player.getLocation();
        FileConfiguration config = EssentialsPlugin.getInstance().getConfig();
        
        config.set("spawn.world", loc.getWorld().getName());
        config.set("spawn.x", loc.getX());
        config.set("spawn.y", loc.getY());
        config.set("spawn.z", loc.getZ());
        config.set("spawn.yaw", loc.getYaw());
        config.set("spawn.pitch", loc.getPitch());
        
        EssentialsPlugin.getInstance().saveConfig();
        
        player.sendMessage(ChatColor.GREEN + "Spawn definido com sucesso!");
        return true;
    }
}
