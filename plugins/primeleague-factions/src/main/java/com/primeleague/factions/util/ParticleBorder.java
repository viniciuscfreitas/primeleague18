package com.primeleague.factions.util;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Utility to show claim borders using particles.
 * Optimized for 1.8.8.
 */
public class ParticleBorder {

    /**
     * Shows the border of a chunk to a player.
     *
     * @param player The player to show the border to.
     * @param world  The world of the chunk.
     * @param x      The chunk X coordinate.
     * @param z      The chunk Z coordinate.
     * @param effect The particle effect to use.
     */
    public static void showChunkBorder(Player player, World world, int x, int z, Effect effect) {
        if (player == null || !player.isOnline() || player.getWorld() != world) {
            return;
        }

        int minX = x << 4;
        int minZ = z << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        double y = player.getLocation().getY() + 1.0;

        // Show corners
        playParticle(player, new Location(world, minX, y, minZ), effect);
        playParticle(player, new Location(world, maxX, y, minZ), effect);
        playParticle(player, new Location(world, minX, y, maxZ), effect);
        playParticle(player, new Location(world, maxX, y, maxZ), effect);

        // Show lines (simplified for performance - every 4 blocks)
        for (int i = 0; i <= 16; i += 4) {
            playParticle(player, new Location(world, minX + i, y, minZ), effect);
            playParticle(player, new Location(world, minX + i, y, maxZ), effect);
            playParticle(player, new Location(world, minX, y, minZ + i), effect);
            playParticle(player, new Location(world, maxX, y, minZ + i), effect);
        }
    }

    private static void playParticle(Player player, Location loc, Effect effect) {
        // 1.8.8 particle spawning
        // Using spigot() method or standard Bukkit effect
        player.playEffect(loc, effect, 0);
    }
}
