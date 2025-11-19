package com.primeleague.essentials.commands;

import com.primeleague.essentials.EssentialsPlugin;
import com.primeleague.essentials.managers.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;
        FileConfiguration config = EssentialsPlugin.getInstance().getConfig();
        
        String worldName = config.getString("spawn.world");
        if (worldName == null) {
            player.sendMessage(ChatColor.RED + "Spawn não definido. Contate um administrador.");
            return true;
        }
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Mundo do spawn não encontrado.");
            return true;
        }
        
        double x = config.getDouble("spawn.x");
        double y = config.getDouble("spawn.y");
        double z = config.getDouble("spawn.z");
        float yaw = (float) config.getDouble("spawn.yaw");
        float pitch = (float) config.getDouble("spawn.pitch");
        
        Location spawnLoc = new Location(world, x, y, z, yaw, pitch);
        
        // Salvar last location
        TeleportManager.getInstance().setLastLocation(player, player.getLocation());
        
        player.teleport(spawnLoc);
        player.sendMessage(ChatColor.GREEN + "Teletransportado para o spawn!");
        
        return true;
    }
}
