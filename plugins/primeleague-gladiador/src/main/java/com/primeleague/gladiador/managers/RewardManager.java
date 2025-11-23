package com.primeleague.gladiador.managers;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.managers.ClansManager;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.ClanEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Gerenciador de recompensas do Gladiador
 * Grug Brain: Rewards via Economy plugin ou ClansManager, configurável
 */
public class RewardManager {

    private final GladiadorPlugin plugin;

    public RewardManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Distribui recompensas para o clan vencedor
     * Grug Brain: Usa config ou Economy plugin, fallback para ClansManager
     */
    public void giveWinnerRewards(ClanEntry winner) {
        // Reward via Economy plugin (se disponível)
        if (plugin.getServer().getPluginManager().getPlugin("PrimeleagueEconomy") != null) {
            giveEconomyRewards(winner);
        } else {
            // Fallback: usar ClansManager (balance do clan)
            giveClanBalanceReward(winner);
        }

        // Registrar vitória no ClansManager
        ClansPlugin clansPlugin = (ClansPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("PrimeleagueClans");
        if (clansPlugin == null) return;
        ClansManager clansManager = clansPlugin.getClansManager();
        int eventPoints = plugin.getConfig().getInt("rewards.event-points", 100);
        clansManager.addEventWin(winner.getClanId(), "Gladiador", eventPoints, null);
    }

    /**
     * Distribui rewards via Economy plugin (para cada player)
     * Grug Brain: Usa EconomyAPI.addMoney() - valores em dólares
     * 
     * NOTA: Config deve estar em dólares (ex: 10000.0 = $100.00)
     * Se config estiver em centavos, converter automaticamente (valores > 1000)
     */
    private void giveEconomyRewards(ClanEntry winner) {
        double rewardPerPlayer = plugin.getConfig().getDouble("rewards.winner-coins-per-player", 10000.0);
        
        // Auto-detectar: se valor > 1000, assumir que está em centavos e converter
        // Exemplo: 1000000 centavos = $10000.00 dólares
        if (rewardPerPlayer > 1000) {
            rewardPerPlayer = rewardPerPlayer / 100.0;
        }

        int rewardedCount = 0;
        for (UUID uuid : winner.getRemainingPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                try {
                    // Usar reflection para EconomyAPI (softdepend)
                    Class<?> economyApiClass = Class.forName("com.primeleague.economy.EconomyAPI");
                    java.lang.reflect.Method addMoneyMethod = economyApiClass.getMethod("addMoney", 
                        UUID.class, double.class, String.class);
                    addMoneyMethod.invoke(null, uuid, rewardPerPlayer, "GLADIADOR_WIN");
                    rewardedCount++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Erro ao dar reward via Economy: " + e.getMessage());
                }
            }
        }

        if (rewardedCount > 0) {
            plugin.getLogger().info("Rewards distribuídos via Economy: " + rewardedCount + " players, $" + rewardPerPlayer + " cada");
        }
    }

    /**
     * Distribui reward via ClansManager (balance do clan)
     * Grug Brain: Fallback se Economy não estiver disponível
     */
    private void giveClanBalanceReward(ClanEntry winner) {
        // Reward em centavos (1kk = 100000000 centavos)
        long rewardCents = plugin.getConfig().getLong("rewards.winner-clan-balance", 100000000L);
        
        ClansPlugin clansPlugin = (ClansPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("PrimeleagueClans");
        if (clansPlugin == null) return;
        ClansManager clansManager = clansPlugin.getClansManager();
        clansManager.addClanBalance(winner.getClanId(), rewardCents);
        
        plugin.getLogger().info("Reward distribuído via ClansManager: " + rewardCents + " centavos para clan " + winner.getClanTag());
    }
}

