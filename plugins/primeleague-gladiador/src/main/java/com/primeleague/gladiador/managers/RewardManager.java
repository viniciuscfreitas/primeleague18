package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.integrations.ClansAPI;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
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
     * Distribui recompensas completas (coins, pontos, tags)
     * Grug Brain: Delega para métodos específicos
     */
    public void giveAllRewards(GladiadorMatch match, List<ClanEntry> rankedClans,
                               ClanEntry winner, MatchStatsCalculator.StatsResult stats) {
        // 1. Pontos por posição (F1 style)
        givePositionPoints(rankedClans);

        // 2. Bônus por performance (MVP + Top Damage)
        givePerformanceBonus(match, winner, stats);

        // 3. Coins para vencedores
        giveWinnerRewards(winner);

        // 4. Recompensas individuais (MVP e Top Damage)
        giveIndividualRewards(stats);

        // 5. Tags temporárias
        giveTitles(match, winner, stats);
    }

    /**
     * Distribui pontos por posição (F1 style)
     * Grug Brain: Config simples, pontos por posição
     */
    private void givePositionPoints(List<ClanEntry> rankedClans) {
        for (int i = 0; i < rankedClans.size(); i++) {
            int position = i + 1;
            ClanEntry clan = rankedClans.get(i);

            // Obter pontos do config (padrão F1: 25, 18, 15, 12, 10, 8, 6, 4, 2, 1)
            int points = plugin.getConfig().getInt("rewards.position-points." + position, 0);
            if (points > 0) {
                plugin.getStatsManager().addSeasonPoints(clan.getClanId(), points);
                plugin.getLogger().info("Clan " + clan.getClanTag() + " ganhou " + points + " pontos (posição " + position + ")");
            }
        }
    }

    /**
     * Distribui bônus de pontos por performance
     * Grug Brain: +10 MVP, +8 Top Damage
     */
    private void givePerformanceBonus(GladiadorMatch match, ClanEntry winner, MatchStatsCalculator.StatsResult stats) {
        // Bônus MVP: +10 pontos para o clan do MVP
        if (stats.getMvpPlayerUuid() != null) {
            ClanEntry mvpClan = match.getClanEntry(stats.getMvpPlayerUuid());
            if (mvpClan != null) {
                int mvpBonus = plugin.getConfig().getInt("rewards.performance-bonus.mvp-clan", 10);
                plugin.getStatsManager().addSeasonPoints(mvpClan.getClanId(), mvpBonus);
                plugin.getLogger().info("Clan " + mvpClan.getClanTag() + " ganhou +" + mvpBonus + " pontos (MVP)");
            }
        }

        // Bônus Top Damage: +8 pontos para o clan do Top Damage (se não for o mesmo do MVP)
        if (stats.getDamagePlayerUuid() != null &&
            !stats.getDamagePlayerUuid().equals(stats.getMvpPlayerUuid())) {
            ClanEntry damageClan = match.getClanEntry(stats.getDamagePlayerUuid());
            if (damageClan != null) {
                int damageBonus = plugin.getConfig().getInt("rewards.performance-bonus.top-damage-clan", 8);
                plugin.getStatsManager().addSeasonPoints(damageClan.getClanId(), damageBonus);
                plugin.getLogger().info("Clan " + damageClan.getClanTag() + " ganhou +" + damageBonus + " pontos (Top Damage)");
            }
        }
    }

    /**
     * Distribui recompensas individuais (coins para MVP e Top Damage)
     * Grug Brain: Coins extras via Economy (usa UUID para robustez)
     */
    private void giveIndividualRewards(MatchStatsCalculator.StatsResult stats) {
        if (plugin.getServer().getPluginManager().getPlugin("PrimeleagueEconomy") == null) {
            return; // Economy não disponível
        }

        // MVP: coins extras
        if (stats.getMvpPlayerUuid() != null) {
            Player mvpPlayer = Bukkit.getPlayer(stats.getMvpPlayerUuid());
            if (mvpPlayer != null && mvpPlayer.isOnline()) {
                double mvpCoins = plugin.getConfig().getDouble("rewards.individual-rewards.mvp-coins", 10000.0);
                if (mvpCoins > 1000) mvpCoins = mvpCoins / 100.0; // Converter centavos
                givePlayerCoins(stats.getMvpPlayerUuid(), mvpCoins, "GLADIADOR_MVP");
            } else {
                // Player offline - dar coins mesmo assim (EconomyAPI aceita UUID offline)
                double mvpCoins = plugin.getConfig().getDouble("rewards.individual-rewards.mvp-coins", 10000.0);
                if (mvpCoins > 1000) mvpCoins = mvpCoins / 100.0;
                givePlayerCoins(stats.getMvpPlayerUuid(), mvpCoins, "GLADIADOR_MVP");
            }
        }

        // Top Damage: coins extras
        if (stats.getDamagePlayerUuid() != null &&
            !stats.getDamagePlayerUuid().equals(stats.getMvpPlayerUuid())) {
            Player damagePlayer = Bukkit.getPlayer(stats.getDamagePlayerUuid());
            if (damagePlayer != null && damagePlayer.isOnline()) {
                double damageCoins = plugin.getConfig().getDouble("rewards.individual-rewards.top-damage-coins", 7500.0);
                if (damageCoins > 1000) damageCoins = damageCoins / 100.0;
                givePlayerCoins(stats.getDamagePlayerUuid(), damageCoins, "GLADIADOR_TOP_DAMAGE");
            } else {
                // Player offline - dar coins mesmo assim
                double damageCoins = plugin.getConfig().getDouble("rewards.individual-rewards.top-damage-coins", 7500.0);
                if (damageCoins > 1000) damageCoins = damageCoins / 100.0;
                givePlayerCoins(stats.getDamagePlayerUuid(), damageCoins, "GLADIADOR_TOP_DAMAGE");
            }
        }
    }

    /**
     * Distribui coins para player individual
     * Grug Brain: Reflection para EconomyAPI
     */
    private void givePlayerCoins(UUID playerUuid, double coins, String reason) {
        try {
            Class<?> economyApiClass = Class.forName("com.primeleague.economy.EconomyAPI");
            java.lang.reflect.Method addMoneyMethod = economyApiClass.getMethod("addMoney",
                UUID.class, double.class, String.class);
            addMoneyMethod.invoke(null, playerUuid, coins, reason);
            plugin.getLogger().info("Player " + playerUuid + " ganhou $" + coins + " (" + reason + ")");
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao dar coins individuais: " + e.getMessage());
        }
    }

    /**
     * Distribui tags temporárias
     * Grug Brain: Tags simples, expiram no próximo Gladiador (usa UUID para robustez)
     * Nota: clearAllTitles() é async, mas setTitle() usa ON CONFLICT DO UPDATE, então sobrescreve mesmo assim.
     */
    private void giveTitles(GladiadorMatch match, ClanEntry winner, MatchStatsCalculator.StatsResult stats) {
        // Limpar tags antigas (async - executa em background)
        plugin.getTitleManager().clearAllTitles();

        // Definir novas tags (async também, mas ON CONFLICT DO UPDATE garante sobrescrita)

        // MVP: [* MVP] (se for do vencedor) ou [# Matador] (se não for)
        if (stats.getMvpPlayerUuid() != null) {
            ClanEntry mvpClan = match.getClanEntry(stats.getMvpPlayerUuid());
            if (mvpClan != null && mvpClan.getClanId() == winner.getClanId()) {
                // MVP do vencedor = tag MVP
                plugin.getTitleManager().setTitle(stats.getMvpPlayerUuid(), "MVP",
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&l[* MVP]"));
            } else {
                // MVP de outro clan = tag Matador
                plugin.getTitleManager().setTitle(stats.getMvpPlayerUuid(), "MATADOR",
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', "&c[# Matador]"));
            }
        }

        // Top Damage: [! Exterminador] (se não for MVP)
        if (stats.getDamagePlayerUuid() != null &&
            !stats.getDamagePlayerUuid().equals(stats.getMvpPlayerUuid())) {
            plugin.getTitleManager().setTitle(stats.getDamagePlayerUuid(), "EXTERMINADOR",
                org.bukkit.ChatColor.translateAlternateColorCodes('&', "&4[! Exterminador]"));
        }

        // Campeões: [* Campeao] (todos os vivos do vencedor, exceto MVP)
        for (UUID uuid : winner.getRemainingPlayers()) {
            if (stats.getMvpPlayerUuid() == null || !uuid.equals(stats.getMvpPlayerUuid())) {
                plugin.getTitleManager().setTitle(uuid, "CAMPEAO",
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6[* Campeao]"));
            }
        }
    }

    /**
     * Distribui recompensas para o clan vencedor (método antigo, mantido para compatibilidade)
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

        // Registrar vitória via ClansAPI
        if (ClansAPI.isEnabled()) {
            int eventPoints = plugin.getConfig().getInt("rewards.event-points", 100);
            ClansAPI.addEventWin(winner.getClanId(), "Gladiador", eventPoints, null);
        }
    }

    /**
     * Distribui rewards via Economy plugin (para cada player)
     * Grug Brain: Tenta casting direto primeiro (mais rápido), fallback para reflection
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
                    // Tentar casting direto primeiro (mais rápido)
                    org.bukkit.plugin.Plugin economyPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueEconomy");
                    if (economyPlugin != null) {
                        try {
                            // Tentar casting direto
                            Class<?> economyPluginClass = economyPlugin.getClass();
                            java.lang.reflect.Method getEconomyApiMethod = economyPluginClass.getMethod("getEconomyAPI");
                            Object economyApi = getEconomyApiMethod.invoke(economyPlugin);

                            if (economyApi != null) {
                                java.lang.reflect.Method addMoneyMethod = economyApi.getClass().getMethod("addMoney",
                                    UUID.class, double.class, String.class);
                                addMoneyMethod.invoke(economyApi, uuid, rewardPerPlayer, "GLADIADOR_WIN");
                                rewardedCount++;
                                continue;
                            }
                        } catch (Exception e) {
                            // Fallback para reflection estático
                        }
                    }

                    // Fallback: reflection estático (método original)
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
     * Distribui reward via ClansAPI (balance do clan)
     * Grug Brain: Fallback se Economy não estiver disponível
     */
    private void giveClanBalanceReward(ClanEntry winner) {
        // Reward em centavos (1kk = 100000000 centavos)
        long rewardCents = plugin.getConfig().getLong("rewards.winner-clan-balance", 100000000L);

        if (ClansAPI.isEnabled()) {
            ClansAPI.addClanBalance(winner.getClanId(), rewardCents);
            plugin.getLogger().info("Reward distribuído via ClansAPI: " + rewardCents + " centavos para clan " + winner.getClanTag());
        }
    }
}

