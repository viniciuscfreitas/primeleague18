package com.primeleague.factions.listener;

import com.primeleague.factions.PrimeFactions;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashSet;
import java.util.Set;

/**
 * Listener para aplicar upgrades de Factions no gameplay
 * Grug Brain: Aplicar bônus de spawners, crops e EXP em territórios
 * Prioridade #1 da Fase 1
 */
public class UpgradeEffectListener implements Listener {

    private final PrimeFactions plugin;
    private static final Set<Material> CROPS = new HashSet<>();

    static {
        CROPS.add(Material.WHEAT);
        CROPS.add(Material.CARROT);
        CROPS.add(Material.POTATO);
        // BEETROOT não existe no Paper 1.8.8 (foi adicionado em 1.9)
        CROPS.add(Material.NETHER_WARTS); // Paper 1.8.8: NETHER_WARTS (com S)
        CROPS.add(Material.COCOA);
        CROPS.add(Material.SUGAR_CANE);
        CROPS.add(Material.CACTUS);
    }

    public UpgradeEffectListener(PrimeFactions plugin) {
        this.plugin = plugin;
    }

    /**
     * Crop Growth: Acelera crescimento de plantações baseado no upgrade
     * Cada nível = +5% de chance de crescimento instantâneo
     * CORREÇÃO: Atualizar no próximo tick para evitar problemas com BlockState em Paper 1.8.8
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCropGrow(BlockGrowEvent e) {
        Block block = e.getBlock();
        BlockState newState = e.getNewState();

        // Verificar se é uma crop
        if (!CROPS.contains(newState.getType()) && newState.getType() != Material.PUMPKIN_STEM
                && newState.getType() != Material.MELON_STEM) {
            return;
        }

        // Verificar se está em território claimado
        int clanId = plugin.getClaimManager().getClanAt(block.getLocation());
        if (clanId == -1) {
            return; // Território não claimado
        }

        // Obter bônus de crop growth
        double bonus = plugin.getUpgradeManager().getCropGrowthBonus(clanId);
        if (bonus <= 0) {
            return; // Sem upgrade
        }

        // Chance = bonus% (ex: nível 3 = 15%)
        // Grug Brain: Math.random() < 0.15 = 15% de chance
        if (Math.random() < (bonus / 100.0)) {
            // CORREÇÃO: Atualizar no próximo tick (thread-safe, evita problemas com BlockState)
            final Block finalBlock = block;
            final BlockState finalNewState = newState;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Verificar se bloco ainda é válido e é uma crop
                if (finalBlock.getState() instanceof org.bukkit.block.BlockState) {
                    try {
                        finalNewState.update(true);
                    } catch (Exception ignored) {
                        // Ignorar erros (bloco pode ter sido quebrado ou modificado)
                    }
                }
            });
        }
    }

    /**
     * Spawner Rate: Aumenta taxa de spawn em spawners
     * Cada nível = +5% de chance de spawn duplo
     * CORREÇÃO: Metadata + delay para evitar loops e problemas de thread safety
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        // Apenas spawns de spawners
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) {
            return;
        }

        LivingEntity entity = e.getEntity();

        // CORREÇÃO: Verificar se já foi processado (evitar loop infinito)
        if (entity.hasMetadata("faction_spawn_bonus")) {
            return;
        }

        // Verificar se está em território claimado
        int clanId = plugin.getClaimManager().getClanAt(entity.getLocation());
        if (clanId == -1) {
            return;
        }

        // Obter bônus de spawner rate
        double bonus = plugin.getUpgradeManager().getSpawnerRateBonus(clanId);
        if (bonus <= 0) {
            return;
        }

        // Chance de spawn duplo = bonus% (ex: nível 3 = 15% de chance)
        // Grug Brain: Math.random() < 0.15 = 15% de chance de spawnar mais 1 mob
        if (Math.random() < (bonus / 100.0)) {
            // CORREÇÃO: Marcar entidade atual para evitar processar novamente
            entity.setMetadata("faction_spawn_bonus",
                new FixedMetadataValue(plugin, true));

            // CORREÇÃO: Spawn extra com delay maior (5 ticks) para evitar conflitos
            final org.bukkit.Location spawnLoc = entity.getLocation().clone();
            final org.bukkit.entity.EntityType entityType = entity.getType();

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Verificar se entidade original ainda existe e chunk está carregado
                if (entity.isValid() && !entity.isDead() &&
                    spawnLoc.getChunk().isLoaded()) {
                    try {
                        spawnLoc.getWorld().spawnEntity(spawnLoc, entityType);
                    } catch (Exception ignored) {
                        // Ignorar erros (chunk pode ter descarregado)
                    }
                }
            }, 5L); // 5 ticks = 0.25s delay (evita conflitos)
        }
    }

    /**
     * EXP Boost: Aumenta EXP de mobs mortos em territórios claimados
     * Cada nível = +5% de EXP
     * CORREÇÃO: MONITOR priority para garantir que modifico EXP após todos os processamentos
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        Player killer = entity.getKiller();

        // Apenas mortes por players
        if (killer == null) {
            return;
        }

        // Verificar se está em território claimado
        int clanId = plugin.getClaimManager().getClanAt(entity.getLocation());
        if (clanId == -1) {
            return;
        }

        // Verificar se player está no mesmo clan do território
        com.primeleague.clans.models.ClanData playerClan =
            plugin.getClansPlugin().getClansManager().getClanByMember(killer.getUniqueId());

        if (playerClan == null || playerClan.getId() != clanId) {
            return; // Apenas membros do clan dono do território recebem bônus
        }

        // Obter bônus de EXP
        double bonus = plugin.getUpgradeManager().getExpBoostBonus(clanId);
        if (bonus <= 0) {
            return;
        }

        // Aplicar bônus de EXP
        int originalExp = e.getDroppedExp();
        if (originalExp <= 0) {
            return;
        }

        // Calcular EXP extra: originalExp * (bonus / 100)
        int extraExp = (int) (originalExp * (bonus / 100.0));
        if (extraExp > 0) {
            e.setDroppedExp(originalExp + extraExp);
        }
    }
}

