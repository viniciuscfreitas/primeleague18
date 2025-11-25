package com.primeleague.core.listeners;

import com.primeleague.core.CorePlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Consolidador de recompensas PvP - Envia mensagem única consolidada
 * Grug Brain: Escuta PlayerDeathEvent com MONITOR priority (último), busca caches dos outros plugins via reflection
 *
 * Prioridade MONITOR garante que todos os outros listeners já processaram as recompensas
 * Usa soft dependencies (verifica se plugins estão disponíveis) via reflection
 */
public class PvPRewardConsolidator implements Listener {

    private final CorePlugin plugin;

    public PvPRewardConsolidator(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();

        // APENAS PvP direto (killer != null)
        if (killer == null) {
            return;
        }

        final UUID killerUuid = killer.getUniqueId();

        // CORREÇÃO: Aguardar 2 ticks para garantir que todos os listeners async terminaram
        // Todos os listeners (Elo, Economy, Stats) usam runTaskAsynchronously, então precisamos
        // aguardar mais tempo para garantir que os caches foram populados
        // Grug Brain: Delay suficiente para async tasks completarem
        new BukkitRunnable() {
            @Override
            public void run() {
                Player killerPlayer = plugin.getServer().getPlayer(killerUuid);
                if (killerPlayer == null || !killerPlayer.isOnline()) {
                    return;
                }

                // Buscar dados dos caches temporários via reflection (soft dependencies)
                // Tentar múltiplas vezes se necessário (retry pattern simples)
                Integer eloChange = getEloChange(killerUuid);
                RewardData economyReward = getEconomyReward(killerUuid);
                Integer killstreak = getKillstreak(killerUuid);

                // Se ainda não há dados, tentar novamente após mais 1 tick (async pode estar demorando)
                if (eloChange == null && economyReward == null && killstreak == null) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            Player retryPlayer = plugin.getServer().getPlayer(killerUuid);
                            if (retryPlayer == null || !retryPlayer.isOnline()) {
                                return;
                            }

                            Integer retryElo = getEloChange(killerUuid);
                            RewardData retryEconomy = getEconomyReward(killerUuid);
                            Integer retryKillstreak = getKillstreak(killerUuid);

                            if (retryElo != null || retryEconomy != null || retryKillstreak != null) {
                                sendConsolidatedMessage(retryPlayer, retryElo, retryEconomy, retryKillstreak);
                            }
                        }
                    }.runTaskLater(plugin, 1L);
                    return;
                }

                sendConsolidatedMessage(killerPlayer, eloChange, economyReward, killstreak);
            }
        }.runTaskLater(plugin, 2L); // 2 ticks delay para garantir que async tasks completaram
    }

    /**
     * Envia mensagem consolidada e limpa caches
     */
    private void sendConsolidatedMessage(Player killerPlayer, Integer eloChange, RewardData economyReward, Integer killstreak) {
        final UUID killerUuid = killerPlayer.getUniqueId();

        // Se não há recompensas, não enviar mensagem
        if (eloChange == null && economyReward == null && killstreak == null) {
            return;
        }

        // Construir mensagem consolidada
        StringBuilder message = new StringBuilder();

        // ELO
        if (eloChange != null && eloChange != 0) {
            String eloColor = eloChange > 0 ? "§a" : "§c";
            String eloSign = eloChange > 0 ? "+" : "";
            message.append(eloColor).append(eloSign).append(eloChange).append(" ELO");
        }

        // Dinheiro (kill reward + killstreak bonus)
        if (economyReward != null) {
            double totalMoney = economyReward.killReward + economyReward.killstreakBonus;
            if (totalMoney > 0) {
                if (message.length() > 0) {
                    message.append(" §7| ");
                }
                message.append("§a+$").append(String.format("%.0f", totalMoney));
            }
        }

        // Killstreak
        if (killstreak != null && killstreak > 0) {
            if (message.length() > 0) {
                message.append(" §7| ");
            }
            message.append("§6Streak ").append(killstreak).append("! ");

            // Emoji para killstreaks especiais
            if (killstreak >= 10) {
                message.append("§e🔥");
            } else if (killstreak >= 5) {
                message.append("§e⚡");
            } else if (killstreak >= 3) {
                message.append("§e🎉");
            }
        }

        // Enviar mensagem consolidada
        if (message.length() > 0) {
            killerPlayer.sendMessage(message.toString());
        }

        // Som épico para killstreaks
        if (killstreak != null && killstreak > 0 && (killstreak == 3 || killstreak == 5 || killstreak == 10 || killstreak % 5 == 0)) {
            // Paper 1.8.8: Usar Sound.LEVEL_UP diretamente (não valueOf)
            killerPlayer.playSound(killerPlayer.getLocation(),
                Sound.LEVEL_UP, 0.5f, 1.2f);
        }

        // Limpar caches após enviar
        clearEloChange(killerUuid);
        clearEconomyReward(killerUuid);
        clearKillstreak(killerUuid);
    }

    /**
     * Busca mudança de ELO via reflection (soft dependency)
     */
    private Integer getEloChange(UUID playerUuid) {
        try {
            Plugin eloPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueElo");
            if (eloPlugin == null || !eloPlugin.isEnabled()) {
                return null;
            }

            Class<?> pvpListenerClass = Class.forName("com.primeleague.elo.listeners.PvPListener");
            Method getMethod = pvpListenerClass.getMethod("getEloChange", UUID.class);
            return (Integer) getMethod.invoke(null, playerUuid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Limpa cache de ELO via reflection
     */
    private void clearEloChange(UUID playerUuid) {
        try {
            Plugin eloPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueElo");
            if (eloPlugin == null || !eloPlugin.isEnabled()) {
                return;
            }

            Class<?> pvpListenerClass = Class.forName("com.primeleague.elo.listeners.PvPListener");
            Method clearMethod = pvpListenerClass.getMethod("clearEloChange", UUID.class);
            clearMethod.invoke(null, playerUuid);
        } catch (Exception ignored) {
        }
    }

    /**
     * Busca recompensa de economy via reflection
     */
    private RewardData getEconomyReward(UUID playerUuid) {
        try {
            Plugin economyPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueEconomy");
            if (economyPlugin == null || !economyPlugin.isEnabled()) {
                return null;
            }

            Class<?> rewardListenerClass = Class.forName("com.primeleague.economy.listeners.PvPRewardListener");
            Method getMethod = rewardListenerClass.getMethod("getReward", UUID.class);
            Object rewardObj = getMethod.invoke(null, playerUuid);
            if (rewardObj == null) {
                return null;
            }

            // Converter objeto para RewardData (RewardData é classe pública do PvPRewardListener)
            Object killRewardObj = rewardObj.getClass().getMethod("getKillReward").invoke(rewardObj);
            Object killstreakBonusObj = rewardObj.getClass().getMethod("getKillstreakBonus").invoke(rewardObj);

            double killReward = killRewardObj != null ? (Double) killRewardObj : 0.0;
            double killstreakBonus = killstreakBonusObj != null ? (Double) killstreakBonusObj : 0.0;

            return new RewardData(killReward, killstreakBonus);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Limpa cache de economy via reflection
     */
    private void clearEconomyReward(UUID playerUuid) {
        try {
            Plugin economyPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueEconomy");
            if (economyPlugin == null || !economyPlugin.isEnabled()) {
                return;
            }

            Class<?> rewardListenerClass = Class.forName("com.primeleague.economy.listeners.PvPRewardListener");
            Method clearMethod = rewardListenerClass.getMethod("clearReward", UUID.class);
            clearMethod.invoke(null, playerUuid);
        } catch (Exception ignored) {
        }
    }

    /**
     * Busca killstreak via reflection
     */
    private Integer getKillstreak(UUID playerUuid) {
        try {
            Plugin statsPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueStats");
            if (statsPlugin == null || !statsPlugin.isEnabled()) {
                return null;
            }

            Class<?> combatListenerClass = Class.forName("com.primeleague.stats.listeners.CombatListener");
            Method getMethod = combatListenerClass.getMethod("getKillstreak", UUID.class);
            return (Integer) getMethod.invoke(null, playerUuid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Limpa cache de killstreak via reflection
     */
    private void clearKillstreak(UUID playerUuid) {
        try {
            Plugin statsPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueStats");
            if (statsPlugin == null || !statsPlugin.isEnabled()) {
                return;
            }

            Class<?> combatListenerClass = Class.forName("com.primeleague.stats.listeners.CombatListener");
            Method clearMethod = combatListenerClass.getMethod("clearKillstreak", UUID.class);
            clearMethod.invoke(null, playerUuid);
        } catch (Exception ignored) {
        }
    }

    /**
     * Classe simples para armazenar dados de recompensa
     */
    private static class RewardData {
        final double killReward;
        final double killstreakBonus;

        RewardData(double killReward, double killstreakBonus) {
            this.killReward = killReward;
            this.killstreakBonus = killstreakBonus;
        }
    }
}
