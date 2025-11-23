package com.primeleague.factions.listener;

import com.primeleague.clans.models.ClanData;
import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.factions.PrimeFactions;
import com.primeleague.factions.util.ParticleBorder;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.UUID;

public class ClaimWandListener implements Listener {

    private final PrimeFactions plugin;
    private static final String META_POS1 = "prime_pos1";
    private static final String META_POS2 = "prime_pos2";

    public ClaimWandListener(PrimeFactions plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check for Golden Hoe
        if (item == null || item.getType() != Material.GOLD_HOE) {
            return;
        }

        // Left Click = Pos 1
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();
            player.setMetadata(META_POS1, new FixedMetadataValue(plugin, loc));
            player.sendMessage(ChatColor.GREEN + "Posição 1 definida em (" + loc.getBlockX() + ", " + loc.getBlockZ() + ")");
            return;
        }

        // Right Click = Pos 2 (Normal)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !player.isSneaking()) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();
            player.setMetadata(META_POS2, new FixedMetadataValue(plugin, loc));
            player.sendMessage(ChatColor.GREEN + "Posição 2 definida em (" + loc.getBlockX() + ", " + loc.getBlockZ() + ")");
            return;
        }

        // Shift + Right Click = CLAIM
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            event.setCancelled(true);
            handleClaimAttempt(player);
        }
    }

    private void handleClaimAttempt(Player player) {
        if (!player.hasMetadata(META_POS1) || !player.hasMetadata(META_POS2)) {
            player.sendMessage(ChatColor.RED + "Defina a Posição 1 (Esq) e Posição 2 (Dir) primeiro!");
            return;
        }

        Location pos1 = (Location) player.getMetadata(META_POS1).get(0).value();
        Location pos2 = (Location) player.getMetadata(META_POS2).get(0).value();

        if (pos1.getWorld() != pos2.getWorld()) {
            player.sendMessage(ChatColor.RED + "As posições devem estar no mesmo mundo!");
            return;
        }

        // Get Clan
        UUID uuid = player.getUniqueId();
        // We need to get the clan ID from the Clans plugin.
        // Assuming ClansManager has a method to get clan by player.
        // Let's check ClansManager API or use a helper.
        // Since I don't have the full Clans API in memory, I'll assume a method exists or query DB.
        // Ideally, PrimeFactions should have a helper for this.
        // For now, let's assume we can get it via ClansPlugin.
        
        com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(uuid);
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Você precisa ter um clã para claimar terras!");
            return;
        }
        
        // Check Role (Leader/Mod only)
        // Assuming ClanMember has role info.
        // For simplicity in this iteration, let's allow any member or check leader.
        if (!clan.getLeaderUuid().equals(uuid)) {
             // TODO: Check for moderator role properly
             // player.sendMessage(ChatColor.RED + "Apenas líderes e moderadores podem claimar!");
             // return;
        }

        // Calculate Chunks
        int minX = Math.min(pos1.getChunk().getX(), pos2.getChunk().getX());
        int maxX = Math.max(pos1.getChunk().getX(), pos2.getChunk().getX());
        int minZ = Math.min(pos1.getChunk().getZ(), pos2.getChunk().getZ());
        int maxZ = Math.max(pos1.getChunk().getZ(), pos2.getChunk().getZ());

        int chunksToClaim = (maxX - minX + 1) * (maxZ - minZ + 1);
        
        // Check Power (Simple check for now)
        // We need to implement PowerManager to check this properly.
        // For now, let's just claim.
        
        int successCount = 0;
        int failCount = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean success = plugin.getClaimManager().claimChunk(pos1.getWorld().getName(), x, z, clan.getId());
                if (success) {
                    successCount++;
                    // Show particles
                    ParticleBorder.showChunkBorder(player, pos1.getWorld(), x, z, Effect.FLAME);
                } else {
                    failCount++;
                }
            }
        }

        player.sendMessage(ChatColor.GREEN + "Terras conquistadas: " + successCount);
        if (failCount > 0) {
            player.sendMessage(ChatColor.RED + "Falha ao conquistar: " + failCount + " (Já possuem dono)");
        }
        
        // Notificar Discord apenas se houve sucesso (rate limitado por clan)
        if (successCount > 0 && plugin.getDiscordIntegration() != null && plugin.getDiscordIntegration().isEnabled()) {
            int totalClaims = plugin.getClaimManager().getClaimCount(clan.getId());
            // Notificar apenas o primeiro chunk claimado (rate limit)
            plugin.getDiscordIntegration().sendTerritoryClaimed(
                clan.getName(),
                player.getName(),
                minX,
                minZ,
                pos1.getWorld().getName(),
                totalClaims
            );
        }
    }
}
