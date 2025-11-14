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

/**
 * Listener de recompensas PvP - Kill/Death/Killstreak
 * Grug Brain: Listener simples, recompensas automáticas, async
 */
public class PvPRewardListener implements Listener {

    private final EconomyPlugin plugin;

    public PvPRewardListener(EconomyPlugin plugin) {
        this.plugin = plugin;
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

        // Bonus de killstreak x3
        if (newKillstreak == 3) {
            double streak3Reward = plugin.getConfig().getDouble("economy.recompensas.killstreak-3-valor", 20.0);
            EconomyAPI.addMoney(killer.getUniqueId(), streak3Reward, "KILLSTREAK_3");

            // Preparar mensagem (voltar à thread principal)
            final String streak3Msg = plugin.getConfig().getString("mensagens.killstreak-bonus", "§6+{amount} {currency} §7(Killstreak {killstreak})")
                .replace("{amount}", String.valueOf((int) streak3Reward))
                .replace("{currency}", plugin.getConfig().getString("economy.simbolo", "¢"))
                .replace("{killstreak}", String.valueOf(newKillstreak));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (killer.isOnline()) {
                        killer.sendMessage(streak3Msg);
                    }
                }
            }.runTask(plugin);
        }

        // Bonus de killstreak x5
        if (newKillstreak == 5) {
            double streak5Reward = plugin.getConfig().getDouble("economy.recompensas.killstreak-5-valor", 50.0);
            EconomyAPI.addMoney(killer.getUniqueId(), streak5Reward, "KILLSTREAK_5");

            // Pot grátis se configurado
            if (plugin.getConfig().getBoolean("economy.recompensas.killstreak-5-pot", true)) {
                final Player finalKiller = killer;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (finalKiller.isOnline()) {
                            // Dar pot de força II (1:30) - Paper 1.8.8
                            finalKiller.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 1800, 1), true);
                        }
                    }
                }.runTask(plugin);
            }

            // Preparar mensagem
            final String streak5Msg = plugin.getConfig().getString("mensagens.killstreak-bonus", "§6+{amount} {currency} §7(Killstreak {killstreak})")
                .replace("{amount}", String.valueOf((int) streak5Reward))
                .replace("{currency}", plugin.getConfig().getString("economy.simbolo", "¢"))
                .replace("{killstreak}", String.valueOf(newKillstreak));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (killer.isOnline()) {
                        killer.sendMessage(streak5Msg);
                        if (plugin.getConfig().getBoolean("economy.recompensas.killstreak-5-pot", true)) {
                            killer.sendMessage("§aPoção de Força recebida!");
                        }
                    }
                }
            }.runTask(plugin);
        }

        // Preparar mensagens de kill reward (voltar à thread principal)
        final String killMsg = plugin.getConfig().getString("mensagens.kill-recompensa", "§a+{amount} {currency} §7(PvP Kill)")
            .replace("{amount}", String.valueOf((int) killReward))
            .replace("{currency}", plugin.getConfig().getString("economy.simbolo", "¢"));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (killer.isOnline()) {
                    killer.sendMessage(killMsg);
                }
            }
        }.runTask(plugin);
    }
}

