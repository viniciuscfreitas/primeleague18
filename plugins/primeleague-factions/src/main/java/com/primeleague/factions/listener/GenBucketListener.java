package com.primeleague.factions.listener;

import com.primeleague.factions.PrimeFactions;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class GenBucketListener implements Listener {

    private final PrimeFactions plugin;

    public GenBucketListener(PrimeFactions plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        // Check if it's a GenBucket (Lore/Name check)
        // For now, let's assume any Lava/Water bucket placed while sneaking is a GenBucket for testing.
        // In production, we would check NBT or ItemMeta.
        
        if (!event.getPlayer().isSneaking()) return;
        
        Block clicked = event.getBlockClicked();
        BlockFace face = event.getBlockFace();
        Block startBlock = clicked.getRelative(face);
        
        Material type = event.getBucket(); // LAVA_BUCKET or WATER_BUCKET
        Material genMaterial = (type == Material.LAVA_BUCKET) ? Material.COBBLESTONE : Material.OBSIDIAN;
        
        // Simple Vertical Gen
        if (type == Material.LAVA_BUCKET || type == Material.WATER_BUCKET) {
            // event.setCancelled(true); // Don't place the liquid
            // event.getPlayer().setItemInHand(...); // Consume bucket
            
            new BukkitRunnable() {
                Block current = startBlock;
                int count = 0;
                
                @Override
                public void run() {
                    if (count > 256 || current.getType() != Material.AIR) {
                        this.cancel();
                        return;
                    }
                    
                    // Check protection
                    int clanId = plugin.getClaimManager().getClanAt(current.getLocation());
                    // Only allow in own territory or wilderness?
                    
                    current.setType(genMaterial);
                    current = current.getRelative(BlockFace.DOWN);
                    count++;
                }
            }.runTaskTimer(plugin, 0L, 5L); // 4 blocks per second
        }
    }
}
