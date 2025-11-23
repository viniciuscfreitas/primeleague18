package com.primeleague.factions.listener;

import com.primeleague.clans.models.ClanData;
import com.primeleague.factions.PrimeFactions;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Iterator;

public class ProtectionListener implements Listener {

    private final PrimeFactions plugin;

    public ProtectionListener(PrimeFactions plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Você não pode quebrar blocos no território de " + getOwnerName(event.getBlock().getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Você não pode colocar blocos no território de " + getOwnerName(event.getBlock().getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getItem() != null && event.getItem().getType() == Material.GOLD_HOE) return; // Allow wand

        if (!canBuild(event.getPlayer(), event.getClickedBlock().getLocation())) {
            // Allow pressure plates, buttons? Usually no in HCF unless specific.
            // Allow opening containers? No.
            // Allow doors? Maybe.
            // For now, block everything except if explicitly allowed.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent event) {
        // Allow TNT to break blocks in claims (Raiding)
        // But maybe protect certain blocks or check for "Peaceful" claims (Safezone)
        
        if (event.getEntityType() == EntityType.PRIMED_TNT || event.getEntityType() == EntityType.MINECART_TNT) {
            // TNT Logic: Allow breaking blocks
            // Water protection is vanilla behavior, so we don't need to do anything special for water.
            // Obsidian durability? That's a separate feature (ObsidianBreaker).
            // For now, standard TNT behavior is allowed.
            
            // Check for Safezone/Warzone if implemented.
            // Assuming ID 0 or -1 is Wilderness.
            // If we have special clans for Safezone, we should check here.
            
            Iterator<Block> it = event.blockList().iterator();
            while (it.hasNext()) {
                Block b = it.next();
                int clanId = plugin.getClaimManager().getClanAt(b.getLocation());
                // If Safezone (e.g. clanId == -2), remove from list.
            }
        } else {
            // Creeper/Other explosions - maybe disable block damage?
            // event.blockList().clear();
        }
    }

    private boolean canBuild(Player player, Location loc) {
        int clanId = plugin.getClaimManager().getClanAt(loc);
        
        // Wilderness
        if (clanId == -1) return true;

        // Check if player is in the clan
        com.primeleague.clans.models.ClanData playerClan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (playerClan != null && playerClan.getId() == clanId) {
            return true;
        }

        // Bypass permission?
        if (player.hasPermission("factions.bypass")) return true;

        return false;
    }

    private String getOwnerName(Location loc) {
        int clanId = plugin.getClaimManager().getClanAt(loc);
        if (clanId == -1) return "Wilderness";
        com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClan(clanId);
        return clan != null ? clan.getName() : "Desconhecido";
    }
}
