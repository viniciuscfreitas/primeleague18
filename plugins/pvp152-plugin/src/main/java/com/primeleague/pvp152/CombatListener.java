package com.primeleague.pvp152;

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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CombatListener implements Listener {

    // Constantes para melhorias de UX
    private static final double MAX_HEIGHT_DIFF = 2.0;  // Limite de altura para reach
    private static final double VELOCITY_RESET_THRESHOLD = 0.5;  // Threshold para reset de velocity
    private static final double BLOCK_ANGLE_THRESHOLD = 0.0001;  // Threshold para magnitude de bloqueio

    private final PvP152Plugin plugin;
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
    private int cpsLimit;
    private int minTicksBetweenHits;
    private double damageGlobalMultiplier;

    private long currentTick = 0;

    public CombatListener(PvP152Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
        // Grug Brain: Usar classe anônima ao invés de lambda para compatibilidade total com Paper 1.8.8
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                currentTick++;
            }
        }, 0L, 1L);
    }

    /**
     * Carrega todas as configurações do config.yml
     * Grug Brain: Método único usado por construtor e reloadConfig()
     * Production: Valida valores para evitar NaN, Infinity, negativos
     */
    private void loadConfig() {
        this.reachEnabled = plugin.getConfig().getBoolean("reach.enabled", true);
        this.reachMaxDistance = validatePositive("reach.max-distance", plugin.getConfig().getDouble("reach.max-distance", 3.0), 3.0);
        this.reachTolerance = validatePositive("reach.hitbox-tolerance", plugin.getConfig().getDouble("reach.hitbox-tolerance", 1.05), 1.05);
        this.blockingEnabled = plugin.getConfig().getBoolean("blocking.enabled", true);
        this.blockingDamageReduction = validateRange("blocking.damage-reduction", plugin.getConfig().getDouble("blocking.damage-reduction", 0.5), 0.0, 1.0, 0.5);
        this.blockingAngleDegrees = validateRange("blocking.angle-degrees", plugin.getConfig().getDouble("blocking.angle-degrees", 60.0), 0.0, 180.0, 60.0);
        this.damageEnabled = plugin.getConfig().getBoolean("damage.enabled", true);
        this.damageWooden = validatePositive("damage.swords.wooden", plugin.getConfig().getDouble("damage.swords.wooden", 4.0), 4.0);
        this.damageStone = validatePositive("damage.swords.stone", plugin.getConfig().getDouble("damage.swords.stone", 5.0), 5.0);
        this.damageIron = validatePositive("damage.swords.iron", plugin.getConfig().getDouble("damage.swords.iron", 6.0), 6.0);
        this.damageGold = validatePositive("damage.swords.gold", plugin.getConfig().getDouble("damage.swords.gold", 4.0), 4.0);
        this.damageDiamond = validatePositive("damage.swords.diamond", plugin.getConfig().getDouble("damage.swords.diamond", 7.0), 7.0);
        this.damageSharpnessPerLevel = validatePositive("damage.enchantments.sharpness-per-level", plugin.getConfig().getDouble("damage.enchantments.sharpness-per-level", 1.25), 1.25);
        this.criticalHitsEnabled = plugin.getConfig().getBoolean("combat.critical-hits.enabled", true);
        this.criticalHitsMultiplier = validatePositive("combat.critical-hits.damage-multiplier", plugin.getConfig().getDouble("combat.critical-hits.damage-multiplier", 1.5), 1.5);
        this.criticalHitsMinHeight = validatePositive("combat.critical-hits.min-height-above-ground", plugin.getConfig().getDouble("combat.critical-hits.min-height-above-ground", 0.1), 0.1);
        this.knockbackEnabled = plugin.getConfig().getBoolean("knockback.enabled", true);
        this.knockbackEnchantLevel1Multiplier = validatePositive("knockback.enchant-level-1-multiplier", plugin.getConfig().getDouble("knockback.enchant-level-1-multiplier", 1.05), 1.05);
        this.knockbackEnchantLevel2Multiplier = validatePositive("knockback.enchant-level-2-multiplier", plugin.getConfig().getDouble("knockback.enchant-level-2-multiplier", 1.90), 1.90);
        this.blockingReachReduction = validateRange("blocking.reach-reduction", plugin.getConfig().getDouble("blocking.reach-reduction", 0.7), 0.1, 1.0, 0.7);
        this.debugLogDamage = plugin.getConfig().getBoolean("debug.log-damage", false);
        this.debugLogKnockback = plugin.getConfig().getBoolean("debug.log-knockback", false);
        this.iframeSweetspot = validateRange("combat.iframe-sweetspot", plugin.getConfig().getInt("combat.iframe-sweetspot", 3), 0, 20, 3);
        this.horizontalBoost = validatePositive("knockback.horizontal-boost", plugin.getConfig().getDouble("knockback.horizontal-boost", 1.2), 1.2);
        this.cpsLimit = validateRange("combat.cps-limit", plugin.getConfig().getInt("combat.cps-limit", 16), 1, 100, 16);
        this.minTicksBetweenHits = Math.max(1, (int) Math.ceil(20.0 / this.cpsLimit)); // Garantir mínimo 1 tick
        this.damageGlobalMultiplier = validatePositive("damage.global-multiplier", plugin.getConfig().getDouble("damage.global-multiplier", 0.75), 0.75);
    }

    /**
     * Valida valor positivo (evita NaN, Infinity, negativos)
     * Production: Garante valores seguros
     */
    private double validatePositive(String key, double value, double defaultValue) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
            plugin.getLogger().warning("Config '" + key + "' inválido (" + value + "), usando padrão: " + defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Valida valor dentro de range (evita NaN, Infinity, fora do range)
     * Production: Garante valores seguros
     */
    private double validateRange(String key, double value, double min, double max, double defaultValue) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            plugin.getLogger().warning("Config '" + key + "' inválido (" + value + "), usando padrão: " + defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Valida valor inteiro dentro de range
     * Production: Garante valores seguros
     */
    private int validateRange(String key, int value, int min, int max, int defaultValue) {
        if (value < min || value > max) {
            plugin.getLogger().warning("Config '" + key + "' inválido (" + value + "), usando padrão: " + defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Recarrega todas as configurações do config.yml
     * Grug Brain: Reutiliza loadConfig() pra evitar duplicação
     */
    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfig();
    }

    /**
     * Recarrega configurações apenas da memória (sem recarregar do disco)
     * Grug Brain: Usado quando config já foi atualizado em memória via set()
     */
    public void reloadFromMemory() {
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

        // Limitar CPS: verificar cooldown do atacante
        PlayerData attackerData = plugin.getPlayerData(attacker.getUniqueId());
        if (currentTick - attackerData.lastAttackTick < minTicksBetweenHits) {
            event.setCancelled(true);
            return;
        }
        attackerData.lastAttackTick = currentTick; // Registrar hit do atacante

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
            // Aplicar I-frames baseado em regeneração (sem regen = vanilla 10 ticks, com regen = reduzido)
            int dynamicIframes = calculateIframesBasedOnRegen(victim);
            victim.setNoDamageTicks(dynamicIframes);
            victim.setMaximumNoDamageTicks(dynamicIframes);
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
            // Grug Brain: Usar classe anônima ao invés de lambda para compatibilidade total com Paper 1.8.8
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    PlayerData d = plugin.getPlayerData(player.getUniqueId());
                    if (d.isBlocking) {
                        ItemStack currentItem = player.getItemInHand();
                        if (currentItem == null || !isSword(currentItem.getType())) {
                            d.isBlocking = false;
                        }
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

    /**
     * Calcula I-frames baseado no nível de regeneração da vítima
     * Usa iframeSweetspot como base (padrão 3 ticks)
     * Com regeneração = reduzir 3 ticks por nível (Regen I=0, II=-3, III=-6, IV=-9, mínimo 0)
     * Grug Brain: Usa getActivePotionEffects() para compatibilidade 1.8.8
     */
    private int calculateIframesBasedOnRegen(Player victim) {
        // Base: usar iframeSweetspot do config (padrão 3)
        int baseIframes = iframeSweetspot;
        
        // Compatível com 1.8.8: usar getActivePotionEffects() e iterar
        for (PotionEffect effect : victim.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.REGENERATION)) {
                // Com regeneração = reduzir 3 ticks por nível
                int regenLevel = effect.getAmplifier() + 1; // 1-4
                int reduction = regenLevel * 3; // 3, 6, 9, 12
                return Math.max(0, baseIframes - reduction);
            }
        }
        
        // Sem regeneração = usar base do config
        return baseIframes;
    }

    private boolean isWithinReach(Player attacker, Entity target) {
        if (!reachEnabled) {
            return true;
        }
        
        // Production: Verificar worlds diferentes
        if (attacker.getWorld() != target.getWorld()) {
            return false;
        }
        
        Location attackerLoc = attacker.getLocation();
        Location targetLoc = target.getLocation();
        
        // Production: Null safety (improvável, mas seguro)
        if (attackerLoc == null || targetLoc == null) {
            return false;
        }

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

        // Aplicar multiplicador global (para balancear com alto CPS)
        double finalDamage = enchantDamage * damageGlobalMultiplier;
        return finalDamage;
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
            // Usar valor do config (configurado via damage.enchantments.sharpness-per-level)
            // Fórmula: base * (1 + Sharpness level * damageSharpnessPerLevel)
            // Exemplo com config padrão 1.25: Diamond (7) + Sharp V = 7 * (1 + 5 * 1.25) = 7 * 7.25 = 50.75
            // Para replicar 1.5.2 exato, configurar 0.5: 7 * (1 + 5 * 0.5) = 7 * 3.5 = 24.5
            baseDamage *= (1.0 + sharpnessLevel * damageSharpnessPerLevel);
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
        // Grug Brain: isOnGround() pode estar deprecated em algumas versões, mas funciona em 1.8.8
        // Alternativa: verificar se bloco abaixo é sólido e altura acima do bloco é pequena
        if (!attacker.isSprinting()) {
            return false;
        }

        // Verificar se está no chão: bloco abaixo é sólido E altura acima do bloco é pequena
        Location loc = attacker.getLocation();
        double heightAboveBlock = loc.getY() - loc.getBlockY();
        
        // Se altura acima do bloco é muito pequena (< 0.1), provavelmente está no chão
        if (heightAboveBlock < 0.1) {
            return false;
        }

        // Verificar altura mínima para critical hit
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
        
        // Production: Verificar worlds diferentes e null safety
        if (attackerLoc == null || victimLoc == null || attacker.getWorld() != victim.getWorld()) {
            return; // Não aplicar knockback se worlds diferentes ou locations inválidas
        }
        
        Vector direction = victimLoc.toVector().subtract(attackerLoc.toVector());
        
        // Production: Evitar NaN/Infinity se vetor for zero (mesma posição)
        double lengthSquared = direction.lengthSquared();
        if (lengthSquared < 0.0001) {
            // Mesma posição, usar direção padrão (norte)
            direction = new Vector(0, 0, 1);
        } else {
            direction.normalize();
        }

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

