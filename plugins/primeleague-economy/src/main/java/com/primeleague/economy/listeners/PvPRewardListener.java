package com.primeleague.economy.listeners;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.economy.EconomyAPI;
import com.primeleague.economy.EconomyPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener de recompensas PvP - Kill/Death/Killstreak
 * Grug Brain: Listener simples, recompensas automáticas, async
 *
 * Fase 2: Armazena recompensas em cache temporário para consolidação
 */
public class PvPRewardListener implements Listener {

    private final EconomyPlugin plugin;

    // Cache temporário para recompensas (UUID -> RewardData)
    // TTL: 5 segundos (suficiente para consolidator processar)
    private static final ConcurrentHashMap<UUID, RewardData> rewardCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5000; // 5 segundos

    /**
     * Classe pública para armazenar dados de recompensa
     */
    public static class RewardData {
        private final double killReward;
        private final double killstreakBonus;
        private final long timestamp;

        public RewardData(double killReward, double killstreakBonus) {
            this.killReward = killReward;
            this.killstreakBonus = killstreakBonus;
            this.timestamp = System.currentTimeMillis();
        }

        public double getKillReward() {
            return killReward;
        }

        public double getKillstreakBonus() {
            return killstreakBonus;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    public PvPRewardListener(EconomyPlugin plugin) {
        this.plugin = plugin;

        // Task periódica para limpar cache expirado (a cada 30s)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanupExpiredCache();
        }, 600L, 600L); // A cada 30 segundos
    }

    /**
     * Limpa cache expirado
     */
    private static void cleanupExpiredCache() {
        rewardCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * API pública estática para consolidator acessar recompensa
     */
    public static RewardData getReward(UUID playerUuid) {
        RewardData data = rewardCache.get(playerUuid);
        if (data == null || data.isExpired()) {
            return null;
        }
        return data;
    }

    /**
     * Limpa cache de recompensa para um player
     */
    public static void clearReward(UUID playerUuid) {
        rewardCache.remove(playerUuid);
    }

    /**
     * PlayerDeathEvent é assíncrono em Paper 1.8.8
     * Grug Brain: Query async recomendada, apenas PvP direto
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();

        // APENAS PvP direto (killer != null)
        if (killer == null) {
            return;
        }

        Player victim = event.getEntity();

        // Executar em thread assíncrona (PlayerDeathEvent é async)
        new BukkitRunnable() {
            @Override
            public void run() {
                processPvPRewards(killer, victim);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Processa recompensas PvP (kill, killstreak)
     * Grug Brain: Lógica direta, usa EconomyAPI
     *
     * Nota: CoreAPI.getPlayer() é thread-safe (HikariCP) e funciona perfeitamente
     * em threads async. Esta query é rápida e não bloqueia a thread principal.
     */
    private void processPvPRewards(Player killer, Player victim) {
        // Buscar dados dos players (query async-safe via HikariCP)
        PlayerData killerData = CoreAPI.getPlayer(killer.getUniqueId());
        PlayerData victimData = CoreAPI.getPlayer(victim.getUniqueId());

        if (killerData == null || victimData == null) {
            plugin.getLogger().warning("Player não encontrado para recompensa PvP: killer=" +
                killer.getUniqueId() + ", victim=" + victim.getUniqueId());
            return;
        }

        // Recompensa por kill
        double killReward = plugin.getConfig().getDouble("economy.recompensas.kill-valor", 7.0);
        EconomyAPI.addMoney(killer.getUniqueId(), killReward, "KILL");

        // Recompensa por death (opcional, negativo)
        double deathPenalty = plugin.getConfig().getDouble("economy.recompensas.morte-valor", 0.0);
        if (deathPenalty > 0) {
            EconomyAPI.removeMoney(victim.getUniqueId(), deathPenalty, "DEATH");
        }

        // Verificar killstreak
        int killstreak = killerData.getKillstreak();
        int newKillstreak = killstreak + 1;

        // Calcular bônus de killstreak
        double killstreakBonus = 0.0;

        // Bonus de killstreak x3
        if (newKillstreak == 3) {
            double streak3Reward = plugin.getConfig().getDouble("economy.recompensas.killstreak-3-valor", 20.0);
            EconomyAPI.addMoney(killer.getUniqueId(), streak3Reward, "KILLSTREAK_3");
            killstreakBonus += streak3Reward;
        }

        // Bonus de killstreak x5
        if (newKillstreak == 5) {
            double streak5Reward = plugin.getConfig().getDouble("economy.recompensas.killstreak-5-valor", 50.0);
            EconomyAPI.addMoney(killer.getUniqueId(), streak5Reward, "KILLSTREAK_5");
            killstreakBonus += streak5Reward;

            // Pot grátis se configurado (ainda funciona normalmente)
            if (plugin.getConfig().getBoolean("economy.recompensas.killstreak-5-pot", true)) {
                final Player finalKiller = killer;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (finalKiller.isOnline()) {
                            // Dar pot de força II (1:30) - Paper 1.8.8
                            finalKiller.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 1800, 1), true);
                            finalKiller.sendMessage("§aPoção de Força recebida!");
                        }
                    }
                }.runTask(plugin);
            }
        }

        // Fase 2: Armazenar recompensas em cache temporário para consolidação
        // Ao invés de enviar mensagem imediatamente, armazena no cache
        // O PvPRewardConsolidator no Core buscará e consolidará com outras recompensas
        rewardCache.put(killer.getUniqueId(), new RewardData(killReward, killstreakBonus));
    }
}

