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
        // Verificar se é GenBucket (validação real via ItemMeta)
        // Grug Brain: Verifica display name ou lore específica
        // Paper 1.8.8: usar getItemInHand() diretamente
        org.bukkit.inventory.ItemStack item = event.getPlayer().getItemInHand();
        if (!isGenBucket(item)) {
            return;
        }

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

    /**
     * Verifica se item é um GenBucket válido
     * Grug Brain: Valida via display name ou lore específica
     */
    private boolean isGenBucket(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.LAVA_BUCKET &&
            item.getType() != org.bukkit.Material.WATER_BUCKET) {
            return false;
        }

        if (!item.hasItemMeta()) {
            return false;
        }

        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();

        // Verificar display name
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name.contains("GenBucket") || name.contains("Gerador") ||
                name.contains("Generator") || name.contains("Gen")) {
                return true;
            }
        }

        // Verificar lore
        if (meta.hasLore()) {
            java.util.List<String> lore = meta.getLore();
            for (String line : lore) {
                if (line.contains("GenBucket") || line.contains("Gerador") ||
                    line.contains("Generator") || line.contains("Gen")) {
                    return true;
                }
            }
        }

        return false;
    }
}
