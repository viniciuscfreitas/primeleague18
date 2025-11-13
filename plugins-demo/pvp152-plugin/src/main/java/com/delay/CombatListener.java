package com.delay;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.Effect;

public class CombatListener implements Listener {

    // Constantes para melhorias de UX
    private static final double MAX_HEIGHT_DIFF = 2.0;  // Limite de altura para reach
    private static final double VELOCITY_RESET_THRESHOLD = 0.5;  // Threshold para reset de velocity
    private static final double BLOCK_ANGLE_THRESHOLD = 0.0001;  // Threshold para magnitude de bloqueio

    private final DelayPlugin plugin;
    private boolean reachEnabled;
    private double reachMaxDistance;
    private double reachTolerance;
    private boolean blockingEnabled;
    private double blockingDamageReduction;
    private double blockingAngleDegrees;
    private boolean damageEnabled;
    private double damageWooden;
    private double damageStone;
    private double damageIron;
    private double damageGold;
    private double damageDiamond;
    private double damageSharpnessPerLevel;
    private boolean criticalHitsEnabled;
    private double criticalHitsMultiplier;
    private double criticalHitsMinHeight;
    private boolean knockbackEnabled;
    private double knockbackEnchantLevel1Multiplier;
    private double knockbackEnchantLevel2Multiplier;
    private double blockingReachReduction;
    private boolean debugLogDamage;
    private boolean debugLogKnockback;
    private int iframeSweetspot;
    private double horizontalBoost;

    private long currentTick = 0;

    public CombatListener(DelayPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> currentTick++, 0L, 1L);
    }

    /**
     * Carrega todas as configurações do config.yml
     * Grug Brain: Método único usado por construtor e reloadConfig()
     */
    private void loadConfig() {
        this.reachEnabled = plugin.getConfig().getBoolean("reach.enabled", true);
        this.reachMaxDistance = plugin.getConfig().getDouble("reach.max-distance", 3.0);
        this.reachTolerance = plugin.getConfig().getDouble("reach.hitbox-tolerance", 1.05);
        this.blockingEnabled = plugin.getConfig().getBoolean("blocking.enabled", true);
        this.blockingDamageReduction = plugin.getConfig().getDouble("blocking.damage-reduction", 0.5);
        this.blockingAngleDegrees = plugin.getConfig().getDouble("blocking.angle-degrees", 60.0);
        this.damageEnabled = plugin.getConfig().getBoolean("damage.enabled", true);
        this.damageWooden = plugin.getConfig().getDouble("damage.swords.wooden", 4.0);
        this.damageStone = plugin.getConfig().getDouble("damage.swords.stone", 5.0);
        this.damageIron = plugin.getConfig().getDouble("damage.swords.iron", 6.0);
        this.damageGold = plugin.getConfig().getDouble("damage.swords.gold", 4.0);
        this.damageDiamond = plugin.getConfig().getDouble("damage.swords.diamond", 7.0);
        this.damageSharpnessPerLevel = plugin.getConfig().getDouble("damage.enchantments.sharpness-per-level", 1.25);
        this.criticalHitsEnabled = plugin.getConfig().getBoolean("combat.critical-hits.enabled", true);
        this.criticalHitsMultiplier = plugin.getConfig().getDouble("combat.critical-hits.damage-multiplier", 1.5);
        this.criticalHitsMinHeight = plugin.getConfig().getDouble("combat.critical-hits.min-height-above-ground", 0.1);
        this.knockbackEnabled = plugin.getConfig().getBoolean("knockback.enabled", true);
        this.knockbackEnchantLevel1Multiplier = plugin.getConfig().getDouble("knockback.enchant-level-1-multiplier", 1.05);
        this.knockbackEnchantLevel2Multiplier = plugin.getConfig().getDouble("knockback.enchant-level-2-multiplier", 1.90);
        this.blockingReachReduction = plugin.getConfig().getDouble("blocking.reach-reduction", 0.7);
        this.debugLogDamage = plugin.getConfig().getBoolean("debug.log-damage", false);
        this.debugLogKnockback = plugin.getConfig().getBoolean("debug.log-knockback", false);
        this.iframeSweetspot = plugin.getConfig().getInt("combat.iframe-sweetspot", 3);
        this.horizontalBoost = plugin.getConfig().getDouble("knockback.horizontal-boost", 1.2);
    }

    /**
     * Recarrega todas as configurações do config.yml
     * Grug Brain: Reutiliza loadConfig() pra evitar duplicação
     */
    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) {
            return;
        }

        // Verificar se evento já foi cancelado (ex: por arena-pvp-plugin)
        if (event.isCancelled()) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        victim.setNoDamageTicks(0);
        victim.setMaximumNoDamageTicks(0);

        if (!isWithinReach(attacker, victim)) {
            event.setCancelled(true);
            return;
        }

        double originalDamage = event.getDamage();
        double customDamage = calculateCustomDamage(attacker, victim, originalDamage);
        event.setDamage(customDamage);

        if (isBlocking(victim, attacker)) {
            event.setDamage(event.getDamage() * blockingDamageReduction);
        }

        // Reset de sprint em hit recebido (1.5.2: sprint frágil, reseta em colisão/hit)
        if (victim.isSprinting()) {
            Vector vel = victim.getVelocity();
            vel.setX(0);
            vel.setZ(0);
            victim.setVelocity(vel);
        }

        // Só aplicar knockback se evento não foi cancelado
        if (!event.isCancelled()) {
            applyCustomKnockback(attacker, victim, event);
        }
        registerDamage(victim);

        if (debugLogDamage) {
            plugin.getLogger().info(String.format("Dano: %.2f -> %.2f (bloqueado: %s)",
                originalDamage, event.getDamage(), isBlocking(victim, attacker)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntityMonitor(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player && event.getDamage() > 0) {
            Player victim = (Player) event.getEntity();
            // Aplicar sweetspot após dano ser processado (cria gaps naturais para próximo hit)
            victim.setNoDamageTicks(iframeSweetspot);
            victim.setMaximumNoDamageTicks(iframeSweetspot);

            if (debugLogDamage) {
                plugin.getLogger().info(String.format("I-frames set to: %d for %s", iframeSweetspot, victim.getName()));
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getItemInHand();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        if (item != null && isSword(item.getType()) &&
            (event.getAction() == Action.RIGHT_CLICK_AIR ||
             event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            data.isBlocking = true;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PlayerData d = plugin.getPlayerData(player.getUniqueId());
                if (d.isBlocking) {
                    ItemStack currentItem = player.getItemInHand();
                    if (currentItem == null || !isSword(currentItem.getType())) {
                        d.isBlocking = false;
                    }
                }
            }, 5L);
        } else if (event.getAction() == Action.LEFT_CLICK_AIR ||
                   event.getAction() == Action.LEFT_CLICK_BLOCK) {
            data.isBlocking = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        boolean currentlySprinting = player.isSprinting();

        if (data.wasSprinting && !currentlySprinting) {
            data.sprintStopTime = currentTick;
            data.lastSprintHitTick = 0;  // Reset: pode ganhar 2x KB novamente
        } else if (!data.wasSprinting && currentlySprinting) {
            if (data.sprintStopTime > 0) {
                data.sprintStopTime = 0;
            }
            data.lastSprintReset = currentTick;
        }

        data.wasSprinting = currentlySprinting;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.removePlayerData(event.getPlayer().getUniqueId());
    }

    private void registerDamage(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        data.lastDamageTick = currentTick;
    }

    private boolean isWithinReach(Player attacker, Entity target) {
        if (!reachEnabled) {
            return true;
        }
        Location attackerLoc = attacker.getLocation();
        Location targetLoc = target.getLocation();

        double maxDist = reachMaxDistance * reachTolerance;

        // Se vítima está bloqueando, reduz reach
        if (target instanceof Player) {
            PlayerData targetData = plugin.getPlayerData(((Player) target).getUniqueId());
            if (targetData.isBlocking) {
                maxDist *= blockingReachReduction;
            }
        }

        // High ground bonus: +0.1-0.2 reach se atacante está acima
        double heightDiff = attackerLoc.getY() - targetLoc.getY();
        if (heightDiff > 0) {
            heightDiff = Math.min(heightDiff, MAX_HEIGHT_DIFF);
            maxDist += Math.min(heightDiff * 0.1, 0.2);
        }

        double dx = targetLoc.getX() - attackerLoc.getX();
        double dz = targetLoc.getZ() - attackerLoc.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        return horizontalDistance <= maxDist;
    }

    private boolean isBlocking(Player blocker, Player attacker) {
        if (!blockingEnabled) {
            return false;
        }

        // Verificar item na mão
        ItemStack item = blocker.getItemInHand();
        if (item == null || !isSword(item.getType())) {
            return false;
        }

        PlayerData data = plugin.getPlayerData(blocker.getUniqueId());
        if (!data.isBlocking) {
            return false;
        }

        // Verificar ângulo de ataque
        return isWithinBlockAngle(blocker, attacker);
    }

    private boolean isWithinBlockAngle(Player blocker, Player attacker) {
        double maxAngleRad = Math.toRadians(blockingAngleDegrees);

        Location blockerLoc = blocker.getLocation();
        Location attackerLoc = attacker.getLocation();

        Vector blockerDirection = blockerLoc.getDirection();
        Vector toAttacker = attackerLoc.toVector().subtract(blockerLoc.toVector());

        // Verificar magnitude antes de normalizar (evita NaN)
        if (toAttacker.lengthSquared() < BLOCK_ANGLE_THRESHOLD) {
            return true;
        }

        toAttacker.normalize();

        double dot = blockerDirection.dot(toAttacker);
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));

        return angle <= maxAngleRad;
    }

    private double calculateCustomDamage(Player attacker, Player victim, double originalDamage) {
        if (!damageEnabled) {
            return originalDamage;
        }

        ItemStack weapon = attacker.getItemInHand();
        if (weapon == null || !isSword(weapon.getType())) {
            return originalDamage;
        }

        // Obter dano base por tipo de espada
        double baseDamage = getBaseDamage(weapon.getType());

        // Aplicar encantamentos
        double enchantDamage = calculateEnchantmentDamage(weapon, baseDamage);

        // Aplicar crítico se condições atendidas
        if (isCriticalHit(attacker)) {
            enchantDamage *= criticalHitsMultiplier;
            // Particles amarelas (crit) - usar Effect.CRIT se disponível em 1.8.8
            if (victim != null) {
                victim.getWorld().playEffect(victim.getLocation().add(0, 1, 0), Effect.CRIT, 0);
            }
        }

        return enchantDamage;
    }

    private double getBaseDamage(Material weaponType) {
        String materialName = weaponType.name();

        if (materialName.contains("WOOD")) {
            return damageWooden;
        } else if (materialName.contains("STONE")) {
            return damageStone;
        } else if (materialName.contains("IRON")) {
            return damageIron;
        } else if (materialName.contains("GOLD")) {
            return damageGold;
        } else if (materialName.contains("DIAMOND")) {
            return damageDiamond;
        }

        return 4.0; // Padrão
    }

    private double calculateEnchantmentDamage(ItemStack weapon, double baseDamage) {
        int sharpnessLevel = weapon.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
        if (sharpnessLevel > 0) {
            // Fórmula 1.5.2: base * (1 + Sharpness level * 0.5)
            // Exemplo: Diamond (7) + Sharp V = 7 * (1 + 5 * 0.5) = 7 * 3.5 = 24.5
            baseDamage *= (1.0 + sharpnessLevel * 0.5);
        }
        return baseDamage;
    }

    private boolean isCriticalHit(Player attacker) {
        if (!criticalHitsEnabled) {
            return false;
        }

        // Verificar falling primeiro (mais rápido - acesso direto a velocity)
        if (attacker.getVelocity().getY() >= 0) {
            return false;
        }

        // Verificar outras condições
        if (!attacker.isSprinting() || attacker.isOnGround()) {
            return false;
        }

        // Verificar altura (cache location)
        Location loc = attacker.getLocation();
        double heightAboveBlock = loc.getY() - loc.getBlockY();
        return heightAboveBlock > criticalHitsMinHeight;
    }

    private boolean isSword(Material material) {
        String name = material.name();
        return name.contains("SWORD");
    }

    private void applyCustomKnockback(Player attacker, Player victim, EntityDamageByEntityEvent event) {
        if (!knockbackEnabled) {
            return;
        }

        // Reset velocity se muito alta
        Vector currentVel = victim.getVelocity();
        boolean needsReset = false;

        if (Math.abs(currentVel.getY()) > VELOCITY_RESET_THRESHOLD) {
            currentVel.setY(0);
            needsReset = true;
        }
        if (Math.abs(currentVel.getX()) > VELOCITY_RESET_THRESHOLD || Math.abs(currentVel.getZ()) > VELOCITY_RESET_THRESHOLD) {
            currentVel.setX(0);
            currentVel.setZ(0);
            needsReset = true;
        }

        if (needsReset) {
            victim.setVelocity(currentVel);
        }

        Location attackerLoc = attacker.getLocation();
        Location victimLoc = victim.getLocation();
        Vector direction = victimLoc.toVector().subtract(attackerLoc.toVector()).normalize();

        // Base knockback 1.5.2: 0.4 horizontal e 0.4 vertical
        double baseHorizontal = 0.4;
        double baseVertical = 0.4;

        // Sprint = 2x multiplier APENAS se não deu hit recente em sprint
        PlayerData attackerData = plugin.getPlayerData(attacker.getUniqueId());
        int sprintResetTicks = plugin.getConfig().getInt("knockback.sprint-reset-ticks", 1);
        boolean isFirstSprintHit = attacker.isSprinting() &&
            (currentTick - attackerData.lastSprintHitTick) > sprintResetTicks;

        if (isFirstSprintHit) {
            baseHorizontal *= 2.0;
            baseVertical *= 2.0;
            attackerData.lastSprintHitTick = currentTick;  // Marcar que deu hit em sprint
        }
        // Hits consecutivos em sprint = KB base apenas (não aplicar 2x)

        // Enchant KB stack (valores exatos 1.5.2)
        ItemStack weapon = attacker.getItemInHand();
        if (weapon != null) {
            int knockbackLevel = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
            if (knockbackLevel == 1) {
                baseHorizontal *= knockbackEnchantLevel1Multiplier;
                baseVertical *= knockbackEnchantLevel1Multiplier;
            } else if (knockbackLevel == 2) {
                baseHorizontal *= knockbackEnchantLevel2Multiplier;
                baseVertical *= knockbackEnchantLevel2Multiplier;
            }
        }

        // Reduzir KB em 50% se vítima está bloqueando (1.5.2)
        if (isBlocking(victim, attacker)) {
            baseHorizontal *= 0.5;
            baseVertical *= 0.5;
        }

        // Garantir KB vertical mínimo sempre (força "voo up" mesmo grudado)
        double minVertical = plugin.getConfig().getDouble("knockback.min-vertical", 0.45);
        if (baseVertical < minVertical) {
            baseVertical = minVertical;
        }

        // Boost horizontal se não está em sprint (resolve problema grudado)
        if (!attacker.isSprinting()) {
            baseHorizontal *= horizontalBoost;
        }

        // Aplicar direção
        Vector knockback = new Vector(
            direction.getX() * baseHorizontal,
            baseVertical,  // Já garantido >= minVertical acima
            direction.getZ() * baseHorizontal
        );

        victim.setVelocity(victim.getVelocity().add(knockback));

        if (debugLogKnockback) {
            plugin.getLogger().info(String.format("Knockback aplicado: %.2f,%.2f,%.2f (sprint: %s)",
                knockback.getX(), knockback.getY(), knockback.getZ(), attacker.isSprinting()));
        }
    }
}

