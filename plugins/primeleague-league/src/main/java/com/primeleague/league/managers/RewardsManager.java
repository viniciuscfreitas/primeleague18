package com.primeleague.league.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.league.LeaguePlugin;
import com.primeleague.league.models.RankingEntry;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Gerenciador de prêmios finais de temporada
 * Grug Brain: Distribui prêmios via EconomyAPI, tags, VIP
 */
public class RewardsManager {

    private final LeaguePlugin plugin;

    public RewardsManager(LeaguePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Calcula e distribui prêmios no fim da temporada
     * Grug Brain: Query rankings, distribui prêmios async
     */
    public void calculateAndDistributeRewards(int seasonId) {
        if (!plugin.getConfig().getBoolean("rewards.enabled", true)) {
            plugin.getLogger().info("Prêmios desabilitados. Pulando distribuição.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                // Top clans
                List<RankingEntry> topClans = plugin.getEventManager().queryRankingFromSummary(seasonId, "CLAN", "points", 10);
                distributeClanRewards(topClans);

                // Top players
                List<RankingEntry> topPlayers = plugin.getEventManager().queryRankingFromEvents(seasonId, "PLAYER", "PVP", "KILL", 20);
                distributePlayerRewards(topPlayers);

                plugin.getLogger().info("Prêmios da temporada " + seasonId + " distribuídos com sucesso");
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Distribui prêmios para top clans
     */
    private void distributeClanRewards(List<RankingEntry> topClans) {
        for (RankingEntry entry : topClans) {
            int position = entry.getPosition();
            int clanId = Integer.parseInt(entry.getEntityId());

            // Prêmios de coins
            if (position == 1) {
                long coins = plugin.getConfig().getLong("rewards.top-clan-1-coins", 50000000);
                distributeClanCoins(clanId, coins);
            } else if (position == 2) {
                long coins = plugin.getConfig().getLong("rewards.top-clan-2-coins", 30000000);
                distributeClanCoins(clanId, coins);
            } else if (position == 3) {
                long coins = plugin.getConfig().getLong("rewards.top-clan-3-coins", 20000000);
                distributeClanCoins(clanId, coins);
            }

            // Tags permanentes (integração com chat - TODO)
            if (position == 1) {
                String tag = plugin.getConfig().getString("rewards.top-clan-1-tag", "[★ Campeão {year}]");
                // TODO: Aplicar tag permanente via chat plugin
                plugin.getLogger().info("Tag " + tag + " deve ser aplicada ao clan " + clanId);
            } else if (position == 2) {
                String tag = plugin.getConfig().getString("rewards.top-clan-2-tag", "[★ Vice {year}]");
                // TODO: Aplicar tag permanente
            } else if (position == 3) {
                String tag = plugin.getConfig().getString("rewards.top-clan-3-tag", "[★ Terceiro {year}]");
                // TODO: Aplicar tag permanente
            }

            // Troféu no spawn (opcional, manual)
            if (position == 1 && plugin.getConfig().getBoolean("rewards.top-clan-1-trophy", false)) {
                // TODO: Criar troféu físico no spawn (manual ou via WorldEdit)
                plugin.getLogger().info("Troféu deve ser criado no spawn para clan " + clanId);
            }
        }
    }

    /**
     * Distribui prêmios para top players
     */
    private void distributePlayerRewards(List<RankingEntry> topPlayers) {
        for (RankingEntry entry : topPlayers) {
            int position = entry.getPosition();
            java.util.UUID playerUuid = java.util.UUID.fromString(entry.getEntityId());

            // Prêmios de coins
            if (position == 1) {
                long coins = plugin.getConfig().getLong("rewards.top-player-1-coins", 10000000);
                distributePlayerCoins(playerUuid, coins);
            } else if (position == 2) {
                long coins = plugin.getConfig().getLong("rewards.top-player-2-coins", 5000000);
                distributePlayerCoins(playerUuid, coins);
            } else if (position == 3) {
                long coins = plugin.getConfig().getLong("rewards.top-player-3-coins", 3000000);
                distributePlayerCoins(playerUuid, coins);
            }

            // Tags permanentes
            if (position == 1) {
                String tag = plugin.getConfig().getString("rewards.top-player-1-tag", "[★ Piloto Lendário {year}]");
                // TODO: Aplicar tag permanente
            }

            // VIP
            if (position == 1) {
                String vip = plugin.getConfig().getString("rewards.top-player-1-vip", "eternal");
                // TODO: Aplicar VIP via auth/permissions plugin
            } else if (position == 2 || position == 3) {
                String vip = plugin.getConfig().getString("rewards.top-player-2-vip", "supreme-1month");
                // TODO: Aplicar VIP
            } else if (position >= 4 && position <= 10) {
                String vip = plugin.getConfig().getString("rewards.top-players-4-10-vip", "premium-1month");
                // TODO: Aplicar VIP
            }
        }
    }

    /**
     * Distribui coins para um clan (divide entre membros)
     */
    private void distributeClanCoins(int clanId, long coins) {
        // TODO: Buscar membros do clan e dividir coins
        // Por enquanto, apenas log
        plugin.getLogger().info("Distribuir " + coins + " coins para clan " + clanId);
    }

    /**
     * Distribui coins para um player
     */
    private void distributePlayerCoins(java.util.UUID playerUuid, long coins) {
        // Usar EconomyAPI se disponível
        if (Bukkit.getPluginManager().getPlugin("PrimeleagueEconomy") != null) {
            try {
                com.primeleague.economy.EconomyAPI.addMoney(playerUuid, coins / 100.0, "Prêmio temporada");
            } catch (Exception e) {
                plugin.getLogger().warning("Erro ao distribuir coins: " + e.getMessage());
            }
        }
    }
}

