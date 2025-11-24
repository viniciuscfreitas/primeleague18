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
import java.util.UUID;

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
            return;
        }

        // Rastrear build solo (quando player sem clan coloca bloco em wilderness)
        // Grug Brain: Verificação async para não bloquear main thread
        Location loc = event.getBlock().getLocation();
        int clanId = plugin.getClaimManager().getClanAt(loc);
        if (clanId == -1) { // Wilderness
            // Verificar se player está em clan de forma async (não bloqueia evento)
            // Nota: Rastreamento é não-crítico, pode ser async
            final UUID playerUuid = event.getPlayer().getUniqueId();
            final org.bukkit.Chunk chunk = loc.getChunk();
            final String worldName = chunk.getWorld().getName();
            final int chunkX = chunk.getX();
            final int chunkZ = chunk.getZ();

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                // Verificar se player está em clan (query async)
                com.primeleague.clans.models.ClanData playerClan = plugin.getClansPlugin().getClansManager().getClanByMember(playerUuid);
                if (playerClan == null) { // Solo player
                    // Rastrear build solo (voltar para main thread para atualizar cache)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getClaimManager().trackSoloBuild(worldName, chunkX, chunkZ, playerUuid);
                    });
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Location loc = event.getClickedBlock().getLocation();

        // Verificar se é container (baú, furnace, hopper, etc)
        if (isContainer(event.getClickedBlock().getType())) {
            // Verificar acesso a containers baseado em rank
            if (!canAccessContainer(event.getPlayer(), loc)) {
                event.setCancelled(true);
                return;
            }
        }

        // Para outras interações, usar canBuild normal
        if (!canBuild(event.getPlayer(), loc)) {
            // Allow pressure plates, buttons? Usually no in HCF unless specific.
            event.setCancelled(true);
        }
    }

    /**
     * Verifica se player tem acesso a containers baseado em rank
     * Grug Brain: RECRUIT nunca tem acesso (anti-spy). Apenas MEMBER/OFFICER/LEADER podem acessar.
     */
    private boolean canAccessContainer(Player player, Location loc) {
        int clanId = plugin.getClaimManager().getClanAt(loc);

        // Wilderness: sempre permite
        if (clanId == -1) return true;

        // Verificar se player está no clan
        com.primeleague.clans.models.ClanData playerClan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (playerClan == null || playerClan.getId() != clanId) {
            return false; // Não está no clan
        }

        // Verificar acesso baseado em rank
        boolean hasAccess = plugin.getClansPlugin().getClansManager().hasContainerAccess(clanId, player.getUniqueId());
        if (!hasAccess) {
            String role = plugin.getClansPlugin().getClansManager().getMemberRole(clanId, player.getUniqueId());
            if ("RECRUIT".equals(role)) {
                player.sendMessage(ChatColor.RED + "Recrutas não têm acesso a containers. Peça ao líder para promover você! (Anti-spy)");
            } else {
                player.sendMessage(ChatColor.RED + "Seu rank não tem permissão para acessar containers.");
            }
        }

        return hasAccess;
    }

    /**
     * Verifica se material é um container
     * Grug Brain: Lista simples de containers do Minecraft 1.8.8
     */
    private boolean isContainer(Material material) {
        return material == Material.CHEST ||
               material == Material.TRAPPED_CHEST ||
               material == Material.FURNACE ||
               material == Material.BURNING_FURNACE ||
               material == Material.DISPENSER ||
               material == Material.DROPPER ||
               material == Material.HOPPER ||
               material == Material.BREWING_STAND ||
               material == Material.ENCHANTMENT_TABLE ||
               material == Material.ANVIL ||
               material == Material.BEACON ||
               material == Material.ENDER_CHEST;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        // TNT permite raiding em claims normais, mas bloqueia se clã tiver shield ativo
        if (event.getEntityType() == EntityType.PRIMED_TNT || event.getEntityType() == EntityType.MINECART_TNT) {
            // TNT Logic: Bloquear se clã tiver shield ativo
            // Grug Brain: Shield = proteção total contra TNT (raiding bloqueado)
            Iterator<Block> it = event.blockList().iterator();
            while (it.hasNext()) {
                Block b = it.next();
                int clanId = plugin.getClaimManager().getClanAt(b.getLocation());

                // Wilderness: permite TNT
                if (clanId == -1) {
                    continue; // Wilderness permite raiding
                }

                // Verificar se clã tem shield ativo
                if (isShieldActive(clanId)) {
                    it.remove(); // Bloqueia TNT se shield estiver ativo
                }
                // Se não tem shield, permite TNT (raiding normal)
            }
        } else {
            // Creeper/Outras explosões: bloquear dano em claims (proteção contra grief)
            // Grug Brain: Apenas TNT permite raiding, outras explosões são bloqueadas
            Iterator<Block> it = event.blockList().iterator();
            while (it.hasNext()) {
                Block b = it.next();
                int clanId = plugin.getClaimManager().getClanAt(b.getLocation());

                // Se está em claim (não wilderness), bloquear dano de creeper/etc
                if (clanId != -1) {
                    it.remove();
                }
            }
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

    /**
     * Verifica se clã tem shield ativo
     * Grug Brain: Shield baseado em horas (shieldStartHour e shieldEndHour)
     * + horas extras do upgrade (extra_shield_hours)
     */
    private boolean isShieldActive(int clanId) {
        if (clanId <= 0) {
            return false; // Wilderness ou inválido
        }

        com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClan(clanId);
        if (clan == null) {
            return false;
        }

        Integer shieldStart = clan.getShieldStartHour();
        Integer shieldEnd = clan.getShieldEndHour();

        // Se não tem shield configurado, não está ativo
        if (shieldStart == null || shieldEnd == null) {
            return false;
        }

        // Obter horas extras do upgrade
        int extraHours = plugin.getUpgradeManager().getExtraShieldHours(clanId);
        int adjustedEnd = shieldEnd + extraHours;

        // Calcular hora atual (0-23)
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY);

        // Verificar se está dentro do range de shield
        // Grug Brain: Shield pode passar meia-noite (ex: 22h-6h)
        // Horas extras podem fazer o shield passar meia-noite mesmo que originalmente não passasse

        // Se adjustedEnd >= 24, shield passa meia-noite
        if (adjustedEnd >= 24) {
            // Shield passa meia-noite: verifica se currentHour >= start OU currentHour < (adjustedEnd % 24)
            int endHour = adjustedEnd % 24;
            return currentHour >= shieldStart || currentHour < endHour;
        } else if (shieldStart < adjustedEnd) {
            // Shield normal (não passa meia-noite)
            return currentHour >= shieldStart && currentHour < adjustedEnd;
        } else {
            // Shield originalmente passava meia-noite, e ainda passa com horas extras
            return currentHour >= shieldStart || currentHour < adjustedEnd;
        }
    }
}
