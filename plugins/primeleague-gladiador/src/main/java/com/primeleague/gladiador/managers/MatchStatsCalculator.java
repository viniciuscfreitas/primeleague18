package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Calculadora de estatísticas do match (MVP e DAMAGE)
 * Grug Brain: Lógica isolada, simples e direta
 */
public class MatchStatsCalculator {

    /**
     * Resultado do cálculo de MVP e DAMAGE
     */
    public static class StatsResult {
        private final String mvpPlayerName;
        private final UUID mvpPlayerUuid;
        private final int mvpKills;
        private final String damagePlayerName;
        private final UUID damagePlayerUuid;
        private final double damageAmount;

        public StatsResult(String mvpPlayerName, UUID mvpPlayerUuid, int mvpKills,
                         String damagePlayerName, UUID damagePlayerUuid, double damageAmount) {
            this.mvpPlayerName = mvpPlayerName;
            this.mvpPlayerUuid = mvpPlayerUuid;
            this.mvpKills = mvpKills;
            this.damagePlayerName = damagePlayerName;
            this.damagePlayerUuid = damagePlayerUuid;
            this.damageAmount = damageAmount;
        }

        public String getMvpPlayerName() { return mvpPlayerName; }
        public UUID getMvpPlayerUuid() { return mvpPlayerUuid; }
        public int getMvpKills() { return mvpKills; }
        public String getDamagePlayerName() { return damagePlayerName; }
        public UUID getDamagePlayerUuid() { return damagePlayerUuid; }
        public double getDamageAmount() { return damageAmount; }
    }

    /**
     * Calcula MVP (mais kills) e DAMAGE (mais dano) do match
     * Grug Brain: Itera maps, encontra máximo, armazena UUID para robustez
     */
    public static StatsResult calculateStats(GladiadorMatch match) {
        String mvpPlayerName = null;
        UUID mvpPlayerUuid = null;
        int mvpKills = 0;
        String damagePlayerName = null;
        UUID damagePlayerUuid = null;
        double damageAmount = 0.0;

        // MVP: player com mais kills
        Map<UUID, Integer> playerKills = match.getPlayerKills();
        for (Map.Entry<UUID, Integer> entry : playerKills.entrySet()) {
            if (entry.getValue() > mvpKills) {
                mvpKills = entry.getValue();
                mvpPlayerUuid = entry.getKey();
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null) {
                    mvpPlayerName = p.getName();
                } else {
                    // Player offline - tentar obter nome do cache/OfflinePlayer
                    org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
                    if (offlinePlayer != null && offlinePlayer.getName() != null) {
                        mvpPlayerName = offlinePlayer.getName();
                    }
                }
            }
        }

        // DAMAGE: player com mais dano causado
        Map<UUID, Double> playerDamage = match.getPlayerDamage();
        for (Map.Entry<UUID, Double> entry : playerDamage.entrySet()) {
            if (entry.getValue() > damageAmount) {
                damageAmount = entry.getValue();
                damagePlayerUuid = entry.getKey();
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null) {
                    damagePlayerName = p.getName();
                } else {
                    // Player offline - tentar obter nome do cache/OfflinePlayer
                    org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
                    if (offlinePlayer != null && offlinePlayer.getName() != null) {
                        damagePlayerName = offlinePlayer.getName();
                    }
                }
            }
        }

        return new StatsResult(mvpPlayerName, mvpPlayerUuid, mvpKills, damagePlayerName, damagePlayerUuid, damageAmount);
    }
}


