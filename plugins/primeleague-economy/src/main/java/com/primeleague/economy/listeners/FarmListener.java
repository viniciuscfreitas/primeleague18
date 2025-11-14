package com.primeleague.economy.listeners;

import com.primeleague.economy.EconomyAPI;
import com.primeleague.economy.EconomyPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener de farms - Mineração/Mobs/Farming
 * Grug Brain: Hook custom, recompensas diretas (não items), anti-farm
 */
public class FarmListener implements Listener {

    private final EconomyPlugin plugin;

    // Cooldown de mineração: UUID -> timestamp do último bloco
    private final Map<UUID, Long> lastBlockBreak = new ConcurrentHashMap<>();

    // Contador de mobs por hora: UUID -> contagem + timestamp
    private static class MobCount {
        int count;
        long hourStart;
    }
    private final Map<UUID, MobCount> mobKills = new ConcurrentHashMap<>();

    // Penalty se >50 miners online
    private static final int MAX_MINERS_FOR_PENALTY = 50;

    // Localizações de blocos quebrados (minérios) para cancelar drops
    // Location -> timestamp (limpar após 5 segundos)
    private final Map<Location, Long> brokenOreBlocks = new ConcurrentHashMap<>();

    // Limpar localizações antigas a cada 10 segundos
    private long lastCleanup = System.currentTimeMillis();

    public FarmListener(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * BlockBreakEvent - Mineração e Farming
     * Grug Brain: Recompensa direta, sem dar item
     * HIGH priority para marcar que é minério
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();

        // Verificar se é minério - cancelar drop será feito via ItemSpawnEvent
        if (isOre(material)) {
            // Armazenar localização para cancelar drops
            Location blockLoc = block.getLocation();
            brokenOreBlocks.put(blockLoc, System.currentTimeMillis());

            // Processar recompensa
            final Material finalMaterial = material;
            final Player finalPlayer = player;

            new BukkitRunnable() {
                @Override
                public void run() {
                    processMiningReward(finalPlayer, finalMaterial);
                }
            }.runTask(plugin);

            // Limpar localizações antigas periodicamente (a cada 10 segundos - async)
            long now = System.currentTimeMillis();
            if (now - lastCleanup > 10000) {
                lastCleanup = now;
                // Limpeza async para não bloquear evento
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        cleanupOldLocations();
                    }
                }.runTaskAsynchronously(plugin);
            }

            return;
        }

        // Verificar se é crop
        if (isCrop(material)) {
            // Crop pode dropar seeds normalmente, só dar recompensa
            processFarmingReward(player, material);
        }
    }

    /**
     * ItemSpawnEvent - Cancela drops de minérios
     * Grug Brain: Verifica se item spawnou em localização de bloco quebrado (minério)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        Location itemLoc = item.getLocation();
        org.bukkit.inventory.ItemStack itemStack = item.getItemStack();
        Material itemMaterial = itemStack.getType();

        // Verificar se item spawnou próximo a um bloco quebrado (minério)
        for (Map.Entry<Location, Long> entry : brokenOreBlocks.entrySet()) {
            Location blockLoc = entry.getKey();

            // Verificar se está no mesmo mundo
            if (!blockLoc.getWorld().equals(itemLoc.getWorld())) {
                continue;
            }

            // Verificar distância (dentro de 0.5 blocos)
            double distance = blockLoc.distance(itemLoc);
            if (distance <= 0.5) {
                // Verificar se material do item é o minério esperado
                if (isOreMaterial(itemMaterial, itemStack)) {
                    // Cancelar spawn do item
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /**
     * EntityDeathEvent - Mobs
     * Grug Brain: Recompensa direta, limite de mobs/hora
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player)) {
            return;
        }

        Player killer = (Player) event.getEntity().getKiller();
        EntityType entityType = event.getEntityType();

        // Processar recompensa de mob
        processMobReward(killer, entityType);
    }

    /**
     * Processa recompensa de mineração
     */
    private void processMiningReward(Player player, Material material) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Cooldown: verificar última quebra
        Long lastBreak = lastBlockBreak.get(uuid);
        int cooldownSeconds = plugin.getConfig().getInt("economy.farms.mineracao.cooldown-segundos", 30);
        long cooldownMs = cooldownSeconds * 1000L;

        if (lastBreak != null && (now - lastBreak) < cooldownMs) {
            return; // Em cooldown
        }

        // Penalty se >50 miners online
        double penalty = 1.0;
        int onlineCount = plugin.getServer().getOnlinePlayers().size();
        if (onlineCount > MAX_MINERS_FOR_PENALTY) {
            penalty = 0.80; // -20% pay
        }

        // Obter valor do minério
        double reward = getMiningReward(material);
        if (reward > 0) {
            reward = reward * penalty;

            EconomyAPI.addMoney(uuid, reward, "FARM_MINING_" + material.name());

            // Atualizar cooldown
            lastBlockBreak.put(uuid, now);

            // Mensagem opcional
            String currency = plugin.getConfig().getString("economy.simbolo", "¢");
            String msg = plugin.getConfig().getString("mensagens.farm-recompensa", "§a+{amount} {currency} §7({farm_type})")
                .replace("{amount}", String.format("%.2f", reward))
                .replace("{currency}", currency)
                .replace("{farm_type}", "Mineração");
            player.sendMessage(msg);
        }
    }

    /**
     * Processa recompensa de farming
     */
    private void processFarmingReward(Player player, Material material) {
        UUID uuid = player.getUniqueId();

        double reward = getFarmingReward(material);
        if (reward > 0) {
            EconomyAPI.addMoney(uuid, reward, "FARM_FARMING_" + material.name());

            // Mensagem opcional
            String currency = plugin.getConfig().getString("economy.simbolo", "¢");
            String msg = plugin.getConfig().getString("mensagens.farm-recompensa", "§a+{amount} {currency} §7({farm_type})")
                .replace("{amount}", String.format("%.2f", reward))
                .replace("{currency}", currency)
                .replace("{farm_type}", "Farming");
            player.sendMessage(msg);
        }
    }

    /**
     * Processa recompensa de mobs
     */
    private void processMobReward(Player player, EntityType entityType) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Verificar limite de mobs/hora
        MobCount count = mobKills.get(uuid);
        if (count == null) {
            count = new MobCount();
            count.count = 0;
            count.hourStart = now;
            mobKills.put(uuid, count);
        }

        // Reset se passou 1 hora
        if (now - count.hourStart > 3600000) { // 1 hora = 3600000 ms
            count.count = 0;
            count.hourStart = now;
        }

        // Verificar limite
        int maxMobs = plugin.getConfig().getInt("economy.farms.mobs.max-mobs-hora", 20);
        if (count.count >= maxMobs) {
            return; // Limite atingido
        }

        // Obter valor do mob
        double reward = getMobReward(entityType);
        if (reward > 0) {
            EconomyAPI.addMoney(uuid, reward, "FARM_MOB_" + entityType.name());

            // Incrementar contador
            count.count++;

            // Mensagem opcional
            String currency = plugin.getConfig().getString("economy.simbolo", "¢");
            String msg = plugin.getConfig().getString("mensagens.farm-recompensa", "§a+{amount} {currency} §7({farm_type})")
                .replace("{amount}", String.format("%.2f", reward))
                .replace("{currency}", currency)
                .replace("{farm_type}", "Caça");
            player.sendMessage(msg);
        }
    }

    /**
     * Obtém recompensa de mineração (config.yml)
     */
    private double getMiningReward(Material material) {
        String path = "economy.farms.mineracao." + material.name().toLowerCase().replace("ore", "-valor");
        path = path.replace("-ore-", "-");
        path = path.replace("_", "-").toLowerCase();

        // Mapeamento direto (mais simples)
        if (material == Material.IRON_ORE) {
            return plugin.getConfig().getDouble("economy.farms.mineracao.ferro-valor", 0.50);
        } else if (material == Material.DIAMOND_ORE) {
            return plugin.getConfig().getDouble("economy.farms.mineracao.diamante-valor", 2.00);
        }

        return 0;
    }

    /**
     * Obtém recompensa de farming (config.yml)
     */
    private double getFarmingReward(Material material) {
        if (material == Material.WHEAT) {
            return plugin.getConfig().getDouble("economy.farms.farming.trigo-valor", 0.20);
        } else if (material == Material.NETHER_WARTS) {
            return plugin.getConfig().getDouble("economy.farms.farming.nether-wart-valor", 0.50);
        }

        return 0;
    }

    /**
     * Obtém recompensa de mobs (config.yml)
     */
    private double getMobReward(EntityType entityType) {
        if (entityType == EntityType.ZOMBIE) {
            return plugin.getConfig().getDouble("economy.farms.mobs.zumbi-valor", 1.00);
        } else if (entityType == EntityType.SKELETON) {
            return plugin.getConfig().getDouble("economy.farms.mobs.skeleton-valor", 1.50);
        }

        return 0;
    }

    /**
     * Verifica se material é minério
     */
    private boolean isOre(Material material) {
        return material == Material.IRON_ORE ||
               material == Material.DIAMOND_ORE ||
               material == Material.GOLD_ORE ||
               material == Material.COAL_ORE ||
               material == Material.REDSTONE_ORE ||
               material == Material.EMERALD_ORE ||
               material == Material.LAPIS_ORE;
    }

    /**
     * Verifica se material é crop
     */
    private boolean isCrop(Material material) {
        return material == Material.WHEAT ||
               material == Material.CARROT ||
               material == Material.POTATO ||
               material == Material.NETHER_WARTS;
               // BEETROOT_BLOCK não existe no 1.8.8 (foi adicionado em 1.9)
    }

    /**
     * Verifica se material é minério (incluindo formas processadas como DIAMOND, IRON_INGOT, etc)
     */
    private boolean isOreMaterial(Material material, ItemStack itemStack) {
        // Verificar se é minério bruto primeiro
        if (isOre(material)) {
            return true;
        }

        // Verificar materiais processados
        if (material == Material.DIAMOND ||
            material == Material.IRON_INGOT ||
            material == Material.GOLD_INGOT ||
            material == Material.COAL ||
            material == Material.REDSTONE ||
            material == Material.EMERALD) {
            return true;
        }

        // Verificar LAPIS_LAZULI (INK_SACK com durability 4) - no 1.8.8 não há Material.LAPIS_LAZULI
        if (material == Material.INK_SACK && itemStack != null) {
            // No 1.8.8, lapis tem durability 4 (data value)
            short durability = itemStack.getDurability();
            if (durability == 4) {
                return true; // É lapis lazuli
            }
        }

        return false;
    }

    /**
     * Sobrecarga sem ItemStack (para compatibilidade)
     */
    private boolean isOreMaterial(Material material) {
        return isOreMaterial(material, null);
    }

    /**
     * Limpa localizações antigas (>5 segundos)
     */
    private void cleanupOldLocations() {
        long now = System.currentTimeMillis();
        long expireTime = 5000; // 5 segundos

        Iterator<Map.Entry<Location, Long>> iterator = brokenOreBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Long> entry = iterator.next();
            if (now - entry.getValue() > expireTime) {
                iterator.remove();
            }
        }
    }
}

