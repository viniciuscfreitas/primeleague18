package com.primeleague.essentials.listeners;

import com.primeleague.economy.EconomyAPI;
import com.primeleague.essentials.managers.KitManager;
import com.primeleague.essentials.managers.WarpManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class SignListener implements Listener {

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        String line0 = event.getLine(0);

        if (line0 != null && line0.equalsIgnoreCase("[Heal]")) {
            if (player.hasPermission("essentials.signs.create.heal")) {
                event.setLine(0, ChatColor.DARK_BLUE + "[Heal]");
                player.sendMessage(ChatColor.GREEN + "Placa de Heal criada!");
            }
        } else if (line0 != null && line0.equalsIgnoreCase("[Feed]")) {
            if (player.hasPermission("essentials.signs.create.feed")) {
                event.setLine(0, ChatColor.DARK_BLUE + "[Feed]");
                player.sendMessage(ChatColor.GREEN + "Placa de Feed criada!");
            }
        } else if (line0 != null && line0.equalsIgnoreCase("[Kit]")) {
            if (player.hasPermission("essentials.signs.create.kit")) {
                event.setLine(0, ChatColor.DARK_BLUE + "[Kit]");
                player.sendMessage(ChatColor.GREEN + "Placa de Kit criada!");
            }
        } else if (line0 != null && line0.equalsIgnoreCase("[Warp]")) {
            if (player.hasPermission("essentials.signs.create.warp")) {
                event.setLine(0, ChatColor.DARK_BLUE + "[Warp]");
                player.sendMessage(ChatColor.GREEN + "Placa de Warp criada!");
            }
        } else if (line0 != null && line0.equalsIgnoreCase("[Spawn]")) {
            if (player.hasPermission("essentials.signs.create.spawn")) {
                event.setLine(0, ChatColor.DARK_BLUE + "[Spawn]");
                player.sendMessage(ChatColor.GREEN + "Placa de Spawn criada!");
            }
        }
        // TODO: Buy/Sell signs (mais complexo, requer parsing de item/preço)
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.SIGN_POST && event.getClickedBlock().getType() != Material.WALL_SIGN) return;

        Sign sign = (Sign) event.getClickedBlock().getState();
        String line0 = sign.getLine(0);
        Player player = event.getPlayer();

        if (line0.equals(ChatColor.DARK_BLUE + "[Heal]")) {
            if (player.hasPermission("essentials.signs.use.heal")) {
                player.performCommand("heal");
            }
        } else if (line0.equals(ChatColor.DARK_BLUE + "[Feed]")) {
            if (player.hasPermission("essentials.signs.use.feed")) {
                player.performCommand("feed");
            }
        } else if (line0.equals(ChatColor.DARK_BLUE + "[Kit]")) {
            if (player.hasPermission("essentials.signs.use.kit")) {
                String kitName = sign.getLine(1);
                if (kitName != null && !kitName.isEmpty()) {
                    player.performCommand("kit " + kitName);
                }
            }
        } else if (line0.equals(ChatColor.DARK_BLUE + "[Warp]")) {
            if (player.hasPermission("essentials.signs.use.warp")) {
                String warpName = sign.getLine(1);
                if (warpName != null && !warpName.isEmpty()) {
                    player.performCommand("warp " + warpName);
                }
            }
        } else if (line0.equals(ChatColor.DARK_BLUE + "[Spawn]")) {
            if (player.hasPermission("essentials.signs.use.spawn")) {
                player.performCommand("spawn");
            }
        }
    }
}
