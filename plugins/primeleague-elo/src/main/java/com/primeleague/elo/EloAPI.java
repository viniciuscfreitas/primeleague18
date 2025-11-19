package com.primeleague.elo;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.elo.utils.EloCalculator;
import com.primeleague.league.LeagueAPI;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.UUID;

/**
 * API pública estática para outros plugins
 * Grug Brain: Métodos estáticos thread-safe, queries diretas
 */
public class EloAPI {

    private static EloPlugin getPlugin() {
        EloPlugin plugin = (EloPlugin) Bukkit.getPluginManager().getPlugin("PrimeleagueElo");
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("PrimeleagueElo não está habilitado");
        }
        return plugin;
    }

    public static boolean isEnabled() {
        EloPlugin plugin = (EloPlugin) Bukkit.getPluginManager().getPlugin("PrimeleagueElo");
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Atualiza ELO após PvP (vitória/derrota)
     * Thread-safe: synchronized para operação atômica
     * @param winnerUuid UUID do vencedor
     * @param loserUuid UUID do perdedor
     * @return Mudança de ELO do vencedor
     */
    public static synchronized int updateEloAfterPvP(UUID winnerUuid, UUID loserUuid) {
        PlayerData winnerData = CoreAPI.getPlayer(winnerUuid);
        PlayerData loserData = CoreAPI.getPlayer(loserUuid);

        if (winnerData == null || loserData == null) {
            getPlugin().getLogger().warning("Player não encontrado para atualizar ELO PvP");
            return 0;
        }

        // Calcular ELO
        int winnerOldElo = winnerData.getElo();
        int loserOldElo = loserData.getElo();

        int kFactor = getKFactor();
        int minElo = getMinElo();
        int winnerNewElo = Math.max(minElo, EloCalculator.calculateElo(winnerOldElo, loserOldElo, true, kFactor));
        int loserNewElo = Math.max(minElo, EloCalculator.calculateElo(loserOldElo, winnerOldElo, false, kFactor));

        // Atualizar
        winnerData.setElo(winnerNewElo);
        loserData.setElo(loserNewElo);

        // Salvar (CoreAPI.savePlayer() já é thread-safe via HikariCP)
        CoreAPI.savePlayer(winnerData);
        CoreAPI.savePlayer(loserData);

        int winnerEloChange = winnerNewElo - winnerOldElo;
        int loserEloChange = loserNewElo - loserOldElo;

        // NOVO: Registrar mudança de ELO via LeagueAPI
        if (LeagueAPI.isEnabled()) {
            LeagueAPI.recordEloChange(winnerUuid, winnerOldElo, winnerNewElo, "PvP Win");
            LeagueAPI.recordEloChange(loserUuid, loserOldElo, loserNewElo, "PvP Loss");
        }

        getPlugin().getLogger().info("ELO PvP atualizado: " + winnerData.getName() +
            " +" + winnerEloChange + " (" + winnerNewElo + "), " +
            loserData.getName() + " " + loserEloChange + " (" + loserNewElo + ")");

        return winnerEloChange;
    }

    /**
     * Adiciona ELO fixo (eventos, recompensas)
     * Thread-safe: synchronized para operação atômica
     * @param playerUuid UUID do player
     * @param amount Quantidade de ELO a adicionar
     * @param reason Motivo (para logs)
     * @return Mudança de ELO
     */
    public static synchronized int addElo(UUID playerUuid, int amount, String reason) {
        PlayerData data = CoreAPI.getPlayer(playerUuid);
        if (data == null) {
            getPlugin().getLogger().warning("Player não encontrado para adicionar ELO: " + playerUuid);
            return 0;
        }

        int oldElo = data.getElo();
        int newElo = Math.max(getMinElo(), oldElo + amount);
        data.setElo(newElo);

        CoreAPI.savePlayer(data);

        int eloChange = newElo - oldElo;

        // NOVO: Registrar mudança de ELO via LeagueAPI
        if (LeagueAPI.isEnabled()) {
            LeagueAPI.recordEloChange(playerUuid, oldElo, newElo, reason);
        }

        getPlugin().getLogger().info("ELO atualizado: " + data.getName() + " +" + eloChange +
            " (" + reason + ") - ELO: " + oldElo + " -> " + newElo);

        return eloChange;
    }

    /**
     * Adiciona ELO para múltiplos players (clans, eventos)
     * Thread-safe: synchronized para operação atômica
     * @param playerUuids Lista de UUIDs dos players
     * @param amount Quantidade de ELO a adicionar
     * @param reason Motivo (para logs)
     */
    public static synchronized void addEloToPlayers(List<UUID> playerUuids, int amount, String reason) {
        for (UUID uuid : playerUuids) {
            addElo(uuid, amount, reason);
        }
    }

    /**
     * Obtém ELO atual do player
     * Thread-safe: apenas leitura
     * @param playerUuid UUID do player
     * @return ELO atual ou ELO inicial se não encontrado
     */
    public static int getElo(UUID playerUuid) {
        PlayerData data = CoreAPI.getPlayer(playerUuid);
        return data != null ? data.getElo() : getInitialElo();
    }

    private static int getKFactor() {
        return getPlugin().getConfig().getInt("elo.k-factor", 32);
    }

    private static int getMinElo() {
        return getPlugin().getConfig().getInt("elo.min-elo", 0);
    }

    private static int getInitialElo() {
        return getPlugin().getConfig().getInt("elo.initial-elo", 1000);
    }
}

