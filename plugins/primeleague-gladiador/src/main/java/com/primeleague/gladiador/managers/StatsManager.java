package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.GladiadorStats;

import java.util.List;

/**
 * Gerenciador de estatísticas do Gladiador
 * Grug Brain: Delega para StatsReader e StatsWriter
 */
public class StatsManager {

    private final StatsReader reader;
    private final StatsWriter writer;

    public StatsManager(GladiadorPlugin plugin) {
        this.reader = new StatsReader(plugin);
        this.writer = new StatsWriter(plugin, reader);
    }

    /**
     * Obtém stats do clan (com cache)
     * Grug Brain: Delega para StatsReader
     */
    public GladiadorStats getStats(int clanId) {
        return reader.getStats(clanId);
    }

    /**
     * Incrementa vitórias
     * Grug Brain: Delega para StatsWriter
     */
    public void incrementWins(int clanId) {
        writer.incrementWins(clanId);
    }

    /**
     * Incrementa participações
     * Grug Brain: Delega para StatsWriter
     */
    public void incrementParticipation(int clanId) {
        writer.incrementParticipation(clanId);
    }

    /**
     * Adiciona kills
     * Grug Brain: Delega para StatsWriter
     */
    public void addKills(int clanId, int kills) {
        writer.addKills(clanId, kills);
    }

    /**
     * Adiciona deaths
     * Grug Brain: Delega para StatsWriter
     */
    public void addDeaths(int clanId, int deaths) {
        writer.addDeaths(clanId, deaths);
    }

    /**
     * Adiciona pontos de temporada
     * Grug Brain: Delega para StatsWriter
     */
    public void addSeasonPoints(int clanId, int points) {
        writer.addSeasonPoints(clanId, points);
    }

    /**
     * Obtém top clans por vitórias (assíncrono)
     * Grug Brain: Delega para StatsReader async
     */
    public void getTopByWinsAsync(int limit, java.util.function.Consumer<List<GladiadorStats>> onLoaded) {
        reader.getTopByWinsAsync(limit, onLoaded);
    }

    /**
     * Obtém top clans por pontos de temporada (assíncrono)
     * Grug Brain: Delega para StatsReader async
     */
    public void getTopBySeasonPointsAsync(int limit, java.util.function.Consumer<List<GladiadorStats>> onLoaded) {
        reader.getTopBySeasonPointsAsync(limit, onLoaded);
    }

}
