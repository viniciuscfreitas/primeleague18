
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
import org.bukkit.GameMode;

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
    private double knockbackBlockingReduction; // Hardcode movido para config
    private double antiStuckDistanceThreshold; // Threshold para anti-stuck KB
    private double antiStuckMultiplier; // Multiplicador anti-stuck
    private double knockbackRegenReductionPerLevel; // Redução de KB por nível de regeneração
    private double knockbackNoRegenMultiplier; // Multiplicador de KB quando vítima NÃO tem regeneração (reduz KB base)

    private long currentTick = 0;
    
    // Cache de reflection methods do Gladiador (performance)
    private static java.lang.reflect.Method gladiadorGetInstanceMethod;
    private static java.lang.reflect.Method gladiadorGetMatchManagerMethod;
    private static java.lang.reflect.Method gladiadorGetCurrentMatchMethod;
    private static java.lang.reflect.Method gladiadorGetStateMethod;
    private static java.lang.reflect.Method gladiadorHasPlayerMethod;
    private static boolean gladiadorReflectionCached = false;

    public CombatListener(PvP152Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
        // Cache reflection methods do Gladiador (performance)
        cacheGladiadorReflection();
        // Grug Brain: Usar classe anônima ao invés de lambda para compatibilidade total com Paper 1.8.8
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                currentTick++;
            }
        }, 0L, 1L);
    }
    
    /**
     * Cache reflection methods do Gladiador para performance
     * Grug Brain: Cache uma vez, reuse sempre - cache direto da classe (não precisa de instância)
     */
    private void cacheGladiadorReflection() {
        if (gladiadorReflectionCached) {
            return; // Já cached
        }
        
        org.bukkit.plugin.Plugin gladiadorPlugin = 
            plugin.getServer().getPluginManager().getPlugin("PrimeleagueGladiador");
        
        if (gladiadorPlugin == null || !gladiadorPlugin.isEnabled()) {
            return; // Gladiador não disponível
        }
        
        try {
            // Cache getInstance
            gladiadorGetInstanceMethod = gladiadorPlugin.getClass().getMethod("getInstance");
            
            // Cache getMatchManager (precisa do instance)
            Object gladiadorInstance = gladiadorGetInstanceMethod.invoke(null);
            gladiadorGetMatchManagerMethod = gladiadorInstance.getClass().getMethod("getMatchManager");
            
            // Cache getCurrentMatch (precisa do matchManager)
            Object matchManager = gladiadorGetMatchManagerMethod.invoke(gladiadorInstance);
            gladiadorGetCurrentMatchMethod = matchManager.getClass().getMethod("getCurrentMatch");
            
            // Cache getState e hasPlayer diretamente da classe de retorno (não precisa de instância)
            // Grug Brain: Cache direto da classe é mais eficiente e sempre funciona
            Class<?> matchReturnType = gladiadorGetCurrentMatchMethod.getReturnType();
            gladiadorGetStateMethod = matchReturnType.getMethod("getState");
            gladiadorHasPlayerMethod = matchReturnType.getMethod("hasPlayer", java.util.UUID.class);
            
            gladiadorReflectionCached = true;
            plugin.getLogger().info("Gladiador reflection cached - integração otimizada");
        } catch (Exception e) {
            // Falha silenciosa - reflection será feito em runtime se necessário
            plugin.getLogger().fine("Não foi possível cachear reflection do Gladiador: " + e.getMessage());
        }
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
        this.knockbackBlockingReduction = validateRange("knockback.blocking-reduction", plugin.getConfig().getDouble("knockback.blocking-reduction", 0.5), 0.0, 1.0, 0.5);
        this.antiStuckDistanceThreshold = validatePositive("knockback.anti-stuck-distance-threshold", plugin.getConfig().getDouble("knockback.anti-stuck-distance-threshold", 0.5), 0.5);
        this.antiStuckMultiplier = validatePositive("knockback.anti-stuck-multiplier", plugin.getConfig().getDouble("knockback.anti-stuck-multiplier", 1.5), 1.5);
        this.knockbackRegenReductionPerLevel = validateRange("knockback.regen-reduction-per-level", plugin.getConfig().getDouble("knockback.regen-reduction-per-level", 0.15), 0.0, 1.0, 0.15);
        this.knockbackNoRegenMultiplier = validateRange("knockback.no-regen-multiplier", plugin.getConfig().getDouble("knockback.no-regen-multiplier", 0.7), 0.0, 1.0, 0.7);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) {
            return;
        }

        // Verificação universal: Se evento foi cancelado por qualquer plugin (WorldGuard, etc.)
        // Esta verificação funciona para a maioria dos plugins que cancelam PvP
        if (event.isCancelled()) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        // Edge case: Player pode ter desconectado durante o evento
        if (!attacker.isOnline() || !victim.isOnline()) {
            return;
        }

        // Edge case: Não aplicar PvP em Creative/Spectator
        if (attacker.getGameMode() == GameMode.CREATIVE || 
            attacker.getGameMode() == GameMode.SPECTATOR ||
            victim.getGameMode() == GameMode.CREATIVE || 
            victim.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Edge case: Verificação específica para Gladiador
        // (necessária porque Gladiador descancela eventos quando match está ACTIVE)
        // Para outros plugins (WorldGuard, etc.), a verificação isCancelled() acima já funciona
        // Grug Brain: Soft-depend via reflection - não precisa de dependência de compilação
        if (arePlayersInGladiadorMatch(attacker, victim)) {
            return; // Gladiador gerencia PvP, não aplicar mecânicas customizadas
        }

        // Limitar CPS: verificar cooldown do atacante
        PlayerData attackerData = plugin.getPlayerData(attacker.getUniqueId());
        if (currentTick - attackerData.getLastAttackTick() < minTicksBetweenHits) {
            event.setCancelled(true);
            return;
        }
        attackerData.setLastAttackTick(currentTick); // Registrar hit do atacante

        // Verificar reach e calcular distância horizontal (reutilizar para anti-stuck)
        double[] reachResult = isWithinReachWithDistance(attacker, victim);
        if (reachResult == null || reachResult[0] == 0.0) { // reachResult[0] = 0.0 significa fora do reach
            event.setCancelled(true);
            return;
        }
        double horizontalDistance = reachResult[1]; // reachResult[1] = distância horizontal calculada

        // Edge case: Verificar se evento foi cancelado antes de modificar estado do player
        // (outros plugins podem cancelar entre verificações)
        if (event.isCancelled()) {
            return;
        }

        // Modificar I-frames apenas se evento não foi cancelado
        victim.setNoDamageTicks(0);
        victim.setMaximumNoDamageTicks(0);

        double originalDamage = event.getDamage();
        double customDamage = calculateCustomDamage(attacker, victim, originalDamage);
        event.setDamage(customDamage);

        if (isBlocking(victim, attacker)) {
            event.setDamage(event.getDamage() * blockingDamageReduction);
        }

        // Edge case: Verificação final antes de aplicar qualquer efeito
        // (correção universal para Paper 1.8.8 - outros plugins podem cancelar a qualquer momento)
        // Esta verificação garante que não aplicamos efeitos se evento foi cancelado
        if (event.isCancelled()) {
            return;
        }

        // Reset de sprint em hit recebido (1.5.2: sprint frágil, reseta em colisão/hit)
        // Edge case: Verificar novamente antes de aplicar (evento pode ser cancelado durante cálculo de dano)
        if (!event.isCancelled() && victim.isSprinting()) {
            Vector vel = victim.getVelocity();
            vel.setX(0);
            vel.setZ(0);
            victim.setVelocity(vel);
        }

        // Edge case: Verificar novamente antes de aplicar knockback
        // (correção universal - evento pode ser cancelado entre verificações)
        if (event.isCancelled()) {
            return;
        }

        // Aplicar knockback (já verificamos isCancelled() acima)
        // Passar distância horizontal calculada para evitar recalcular
        applyCustomKnockback(attacker, victim, event, horizontalDistance);

        // Edge case: Verificar novamente antes de registrar dano
        if (event.isCancelled()) {
            return;
        }

        // Registrar dano (já verificamos isCancelled() acima)
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
            // Edge case: Verificar se player ainda está online
            if (!victim.isOnline()) {
                return;
            }
            // Aplicar I-frames baseado em regeneração (sem regen = vanilla 10 ticks, com regen = reduzido)
            int dynamicIframes = calculateIframesBasedOnRegen(victim);
            victim.setNoDamageTicks(dynamicIframes);
            victim.setMaximumNoDamageTicks(dynamicIframes);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Edge case: Verificar se evento foi cancelado
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getItemInHand();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        if (item != null && isSword(item.getType()) &&
            (event.getAction() == Action.RIGHT_CLICK_AIR ||
             event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            data.setIsBlocking(true);
            // Grug Brain: Usar classe anônima ao invés de lambda para compatibilidade total com Paper 1.8.8
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    // Edge case: Player pode ter desconectado durante os 5 ticks
                    if (!player.isOnline()) {
                        return;
                    }
                    PlayerData d = plugin.getPlayerData(player.getUniqueId());
                    if (d.getIsBlocking()) {
                        ItemStack currentItem = player.getItemInHand();
                        if (currentItem == null || !isSword(currentItem.getType())) {
                            d.setIsBlocking(false);
                        }
                    }
                }
            }, 5L);
        } else if (event.getAction() == Action.LEFT_CLICK_AIR ||
                   event.getAction() == Action.LEFT_CLICK_BLOCK) {
            data.setIsBlocking(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        boolean currentlySprinting = player.isSprinting();

        if (data.wasSprinting && !currentlySprinting) {
            data.setSprintStopTime(currentTick);
            data.setLastSprintHitTick(0);  // Reset: pode ganhar 2x KB novamente
        } else if (!data.wasSprinting && currentlySprinting) {
            if (data.getSprintStopTime() > 0) {
                data.setSprintStopTime(0);
            }
            data.setLastSprintReset(currentTick);
        }

        data.wasSprinting = currentlySprinting;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.removePlayerData(event.getPlayer().getUniqueId());
    }

    private void registerDamage(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        data.setLastDamageTick(currentTick);
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
    
    /**
     * Calcula multiplicador de knockback baseado no nível de regeneração da vítima
     * Sem regeneração = aplicar multiplicador reduzido (padrão 0.7x = -30% KB)
     * Com regeneração = reduzir ainda mais KB por nível (Regen I=-15%, II=-30%, III=-45%, IV=-60%, mínimo 0.0)
     * Grug Brain: Sem regen = KB reduzido, com regen = KB ainda mais reduzido
     */
    private double calculateKnockbackMultiplierBasedOnRegen(Player victim) {
        // Compatível com 1.8.8: usar getActivePotionEffects() e iterar
        for (PotionEffect effect : victim.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.REGENERATION)) {
                // Com regeneração = reduzir KB por nível (aplicado sobre o multiplicador sem regen)
                int regenLevel = effect.getAmplifier() + 1; // 1-4
                double reduction = regenLevel * knockbackRegenReductionPerLevel; // 0.15, 0.30, 0.45, 0.60 (padrão)
                // Aplicar redução sobre o multiplicador base (sem regen)
                return Math.max(0.0, knockbackNoRegenMultiplier - reduction);
            }
        }
        
        // Sem regeneração = aplicar multiplicador reduzido (padrão 0.7x = -30% KB)
        return knockbackNoRegenMultiplier;
    }

    /**
     * Verifica se está dentro do reach e retorna distância horizontal calculada
     * Grug Brain: Retorna array [isWithinReach (1.0 ou 0.0), horizontalDistance] para reutilizar cálculo
     * @return Array [isWithinReach (1.0 = true, 0.0 = false), horizontalDistance] ou null se inválido
     */
    private double[] isWithinReachWithDistance(Player attacker, Entity target) {
        if (!reachEnabled) {
            // Se reach desabilitado, retornar distância 0 (não precisa calcular)
            return new double[]{1.0, 0.0};
        }
        
        // Production: Verificar worlds diferentes
        if (attacker.getWorld() != target.getWorld()) {
            return null;
        }
        
        Location attackerLoc = attacker.getLocation();
        Location targetLoc = target.getLocation();
        
        // Production: Null safety (improvável, mas seguro)
        if (attackerLoc == null || targetLoc == null) {
            return null;
        }

        double maxDist = reachMaxDistance * reachTolerance;

        // Se vítima está bloqueando, reduz reach
        if (target instanceof Player) {
            PlayerData targetData = plugin.getPlayerData(((Player) target).getUniqueId());
            if (targetData.getIsBlocking()) {
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

        // Retornar [isWithinReach (1.0 ou 0.0), horizontalDistance]
        return new double[]{horizontalDistance <= maxDist ? 1.0 : 0.0, horizontalDistance};
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
        if (!data.getIsBlocking()) {
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

    /**
     * Verifica se ambos os players estão em um match do Gladiador com estado ACTIVE
     * Grug Brain: Soft-depend via reflection cached - performance otimizada
     * Retorna true se AMBOS estão no match E match está ACTIVE (Gladiador gerencia PvP)
     */
    private boolean arePlayersInGladiadorMatch(Player attacker, Player victim) {
        org.bukkit.plugin.Plugin gladiadorPlugin = 
            plugin.getServer().getPluginManager().getPlugin("PrimeleagueGladiador");
        
        if (gladiadorPlugin == null || !gladiadorPlugin.isEnabled()) {
            return false; // Gladiador não está habilitado
        }

        try {
            // Usar métodos cached se disponíveis (performance)
            Object gladiadorInstance;
            Object matchManager;
            Object match;
            
            if (gladiadorReflectionCached && gladiadorGetInstanceMethod != null) {
                // Usar cache
                gladiadorInstance = gladiadorGetInstanceMethod.invoke(null);
                matchManager = gladiadorGetMatchManagerMethod.invoke(gladiadorInstance);
                match = gladiadorGetCurrentMatchMethod.invoke(matchManager);
            } else {
                // Fallback: reflection em runtime (primeira vez ou cache falhou)
                java.lang.reflect.Method getInstance = gladiadorPlugin.getClass().getMethod("getInstance");
                gladiadorInstance = getInstance.invoke(null);
                
                java.lang.reflect.Method getMatchManager = gladiadorInstance.getClass().getMethod("getMatchManager");
                matchManager = getMatchManager.invoke(gladiadorInstance);
                
                java.lang.reflect.Method getCurrentMatch = matchManager.getClass().getMethod("getCurrentMatch");
                match = getCurrentMatch.invoke(matchManager);
            }
            
            if (match == null) {
                return false; // Não há match ativo
            }
            
            // Verificar estado do match - APENAS ACTIVE aplica mecânicas do Gladiador
            Object state;
            if (gladiadorReflectionCached && gladiadorGetStateMethod != null) {
                state = gladiadorGetStateMethod.invoke(match);
            } else {
                java.lang.reflect.Method getState = match.getClass().getMethod("getState");
                state = getState.invoke(match);
            }
            
            // Verificar se estado é ACTIVE (Gladiador gerencia PvP apenas em ACTIVE)
            // Grug Brain: Comparar enum diretamente (mais robusto que toString().contains())
            if (state instanceof Enum) {
                Enum<?> stateEnum = (Enum<?>) state;
                if (!stateEnum.name().equals("ACTIVE")) {
                    return false; // Match não está ativo (WAITING, PREPARATION, ENDING)
                }
            } else {
                // Fallback: se não for enum, usar toString (compatibilidade)
                String stateStr = state.toString();
                if (!stateStr.contains("ACTIVE")) {
                    return false;
                }
            }
            
            // Verificar se AMBOS estão no match
            boolean attackerInMatch;
            boolean victimInMatch;
            
            if (gladiadorReflectionCached && gladiadorHasPlayerMethod != null) {
                attackerInMatch = (Boolean) gladiadorHasPlayerMethod.invoke(match, attacker.getUniqueId());
                victimInMatch = (Boolean) gladiadorHasPlayerMethod.invoke(match, victim.getUniqueId());
            } else {
                java.lang.reflect.Method hasPlayer = match.getClass().getMethod("hasPlayer", java.util.UUID.class);
                attackerInMatch = (Boolean) hasPlayer.invoke(match, attacker.getUniqueId());
                victimInMatch = (Boolean) hasPlayer.invoke(match, victim.getUniqueId());
            }
            
            // Se ambos estão no match E match está ACTIVE, Gladiador gerencia PvP
            return attackerInMatch && victimInMatch;
            
        } catch (Exception e) {
            // Ignorar erros de integração (plugin pode ter mudado API ou não estar disponível)
            // Grug Brain: Falha silenciosa - não quebrar se Gladiador não estiver disponível
            return false;
        }
    }

    /**
     * Aplica knockback customizado com distância horizontal pré-calculada
     * Grug Brain: Recebe distância como parâmetro para evitar recalcular
     */
    private void applyCustomKnockback(Player attacker, Player victim, EntityDamageByEntityEvent event, double horizontalDistance) {
        // Edge case: Verificar se evento foi cancelado antes de aplicar knockback
        // (correção universal para Paper 1.8.8 - outros plugins podem cancelar depois)
        if (event.isCancelled()) {
            return;
        }

        if (!knockbackEnabled) {
            return;
        }

        // Reset velocity se muito alta
        // Edge case: Verificar se evento foi cancelado antes de modificar velocity
        if (!event.isCancelled()) {
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
            (currentTick - attackerData.getLastSprintHitTick()) > sprintResetTicks;

        if (isFirstSprintHit) {
            baseHorizontal *= 2.0;
            baseVertical *= 2.0;
            attackerData.setLastSprintHitTick(currentTick);  // Marcar que deu hit em sprint
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

        // Reduzir KB se vítima está bloqueando (configurável, padrão 50% como 1.5.2)
        if (isBlocking(victim, attacker)) {
            baseHorizontal *= knockbackBlockingReduction;
            baseVertical *= knockbackBlockingReduction;
        }
        
        // Anti-stuck: boost KB se players muito próximos (resolve grudado na parede)
        // Grug Brain: Usar distância já calculada (evita recalcular)
        if (horizontalDistance < antiStuckDistanceThreshold) {
            baseHorizontal *= antiStuckMultiplier;
        }
        
        // Reduzir KB baseado em regeneração da vítima (mesma lógica dos I-frames)
        // Grug Brain: Regen reduz KB - sem regen = KB normal, com regen = KB reduzido
        double regenMultiplier = calculateKnockbackMultiplierBasedOnRegen(victim);
        baseHorizontal *= regenMultiplier;
        baseVertical *= regenMultiplier;

        // Garantir KB vertical mínimo sempre (força "voo up" mesmo grudado)
        // Nota: minVertical é aplicado DEPOIS da redução de regen (garante mínimo mesmo com regen)
        double minVertical = plugin.getConfig().getDouble("knockback.min-vertical", 0.45);
        if (baseVertical < minVertical) {
            baseVertical = minVertical;
        }

        // Boost horizontal se não está em sprint (resolve problema grudado)
        if (!attacker.isSprinting()) {
            baseHorizontal *= horizontalBoost;
        }

        // Edge case: Verificar novamente se evento foi cancelado antes de aplicar velocity
        // (correção universal para Paper 1.8.8 - evento pode ser cancelado durante cálculo)
        if (event.isCancelled()) {
            return;
        }

        // Aplicar direção
        Vector knockback = new Vector(
            direction.getX() * baseHorizontal,
            baseVertical,  // Já garantido >= minVertical acima
            direction.getZ() * baseHorizontal
        );

        victim.setVelocity(victim.getVelocity().add(knockback));

        if (debugLogKnockback) {
            // Calcular regen multiplier para log (mostrar se está reduzindo KB)
            double regenMultiplierForLog = calculateKnockbackMultiplierBasedOnRegen(victim);
            String regenInfo = regenMultiplierForLog < 1.0 ? String.format(" (regen: %.0f%%)", regenMultiplierForLog * 100) : " (sem regen)";
            plugin.getLogger().info(String.format("Knockback aplicado: %.2f,%.2f,%.2f (sprint: %s%s)",
                knockback.getX(), knockback.getY(), knockback.getZ(), attacker.isSprinting(), regenInfo));
        }
    }
}

