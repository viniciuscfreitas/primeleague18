package com.primeleague.factions.manager;

import com.primeleague.clans.models.ClanData;
import com.primeleague.factions.PrimeFactions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

public class FlyManager {

    private final PrimeFactions plugin;
    private final Set<UUID> flyingPlayers = new HashSet<>();
    private final Map<UUID, Long> lastCombatTime = new HashMap<>();
    private static final long COMBAT_TAG_DURATION = 15000; // 15 seconds
    private static final double ENEMY_CHECK_RADIUS = 32.0;

    public FlyManager(PrimeFactions plugin) {
        this.plugin = plugin;
        // Task to check flying players periodically (every 1 second)
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                checkFlyStatus();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void toggleFly(Player player) {
        if (flyingPlayers.contains(player.getUniqueId())) {
            setFly(player, false);
            player.sendMessage(ChatColor.RED + "Modo de voo desativado.");
        } else {
            if (canFly(player)) {
                setFly(player, true);
                player.sendMessage(ChatColor.GREEN + "Modo de voo ativado!");
            }
        }
    }

    public void setFly(Player player, boolean enable) {
        if (enable) {
            flyingPlayers.add(player.getUniqueId());
            player.setAllowFlight(true);
            player.setFlying(true);
        } else {
            flyingPlayers.remove(player.getUniqueId());
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setFallDistance(0); // Prevent fall damage on disable
        }
    }

    public boolean isFlying(Player player) {
        return flyingPlayers.contains(player.getUniqueId());
    }

    public boolean canFly(Player player) {
        // 1. Check Combat
        if (isInCombat(player)) {
            player.sendMessage(ChatColor.RED + "Você está em combate!");
            return false;
        }

        // 2. Check Territory
        ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Você precisa de um clã.");
            return false;
        }

        int chunkClanId = plugin.getClaimManager().getClanAt(player.getLocation());
        if (chunkClanId != clan.getId()) {
            player.sendMessage(ChatColor.RED + "Você só pode voar no território do seu clã.");
            return false;
        }

        // 3. Check Nearby Enemies
        if (hasNearbyEnemies(player, clan.getId())) {
            player.sendMessage(ChatColor.RED + "Há inimigos por perto!");
            return false;
        }

        return true;
    }

    public void onCombat(Player player) {
        lastCombatTime.put(player.getUniqueId(), System.currentTimeMillis());
        if (isFlying(player)) {
            setFly(player, false);
            player.sendMessage(ChatColor.RED + "Voo desativado por entrar em combate!");
        }
    }

    private boolean isInCombat(Player player) {
        Long lastHit = lastCombatTime.get(player.getUniqueId());
        return lastHit != null && (System.currentTimeMillis() - lastHit) < COMBAT_TAG_DURATION;
    }

    private boolean hasNearbyEnemies(Player player, int clanId) {
        for (Entity entity : player.getNearbyEntities(ENEMY_CHECK_RADIUS, ENEMY_CHECK_RADIUS, ENEMY_CHECK_RADIUS)) {
            if (entity instanceof Player) {
                Player other = (Player) entity;
                if (other.getGameMode() == GameMode.SPECTATOR) continue;
                
                ClanData otherClan = plugin.getClansPlugin().getClansManager().getClanByMember(other.getUniqueId());
                // If other player has no clan or different clan, they are enemy
                if (otherClan == null || otherClan.getId() != clanId) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkFlyStatus() {
        Iterator<UUID> it = flyingPlayers.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Player player = Bukkit.getPlayer(uuid);
            
            if (player == null || !player.isOnline()) {
                it.remove();
                continue;
            }

            // Re-validate conditions
            ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(uuid);
            if (clan == null) {
                setFly(player, false);
                player.sendMessage(ChatColor.RED + "Voo desativado (sem clã).");
                continue;
            }

            int chunkClanId = plugin.getClaimManager().getClanAt(player.getLocation());
            if (chunkClanId != clan.getId()) {
                setFly(player, false);
                player.sendMessage(ChatColor.RED + "Voo desativado (saiu do território).");
                continue;
            }
            
            if (hasNearbyEnemies(player, clan.getId())) {
                setFly(player, false);
                player.sendMessage(ChatColor.RED + "Voo desativado (inimigos próximos).");
                continue;
            }
        }
    }
}
