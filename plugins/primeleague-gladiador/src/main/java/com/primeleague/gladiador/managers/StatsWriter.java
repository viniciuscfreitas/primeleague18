package com.primeleague.gladiador.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.gladiador.GladiadorPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;

/**
 * Escritor de estatísticas do Gladiador
 * Grug Brain: Updates async, invalida cache após escrita
 */
public class StatsWriter {

    private final GladiadorPlugin plugin;
    private final StatsReader statsReader;

    public StatsWriter(GladiadorPlugin plugin, StatsReader statsReader) {
        this.plugin = plugin;
        this.statsReader = statsReader;
    }

    /**
     * Incrementa vitórias
     * Grug Brain: UPSERT (cria se não existe, atualiza se existe) - async
     */
    public void incrementWins(int clanId) {
        statsReader.invalidateCache(clanId);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO gladiador_stats (clan_id, wins, last_win) " +
                         "VALUES (?, 1, CURRENT_TIMESTAMP) " +
                         "ON CONFLICT (clan_id) DO UPDATE SET " +
                         "wins = gladiador_stats.wins + 1, last_win = CURRENT_TIMESTAMP")) {

                    stmt.setInt(1, clanId);
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao incrementar vitórias: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Incrementa participações
     * Grug Brain: UPSERT (cria se não existe, atualiza se existe) - async
     */
    public void incrementParticipation(int clanId) {
        statsReader.invalidateCache(clanId);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO gladiador_stats (clan_id, participations) " +
                         "VALUES (?, 1) " +
                         "ON CONFLICT (clan_id) DO UPDATE SET " +
                         "participations = gladiador_stats.participations + 1")) {

                    stmt.setInt(1, clanId);
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao incrementar participações: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Adiciona kills
     * Grug Brain: UPSERT (cria se não existe, atualiza se existe) - async
     */
    public void addKills(int clanId, int kills) {
        statsReader.invalidateCache(clanId);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO gladiador_stats (clan_id, total_kills) " +
                         "VALUES (?, ?) " +
                         "ON CONFLICT (clan_id) DO UPDATE SET " +
                         "total_kills = gladiador_stats.total_kills + ?")) {

                    stmt.setInt(1, clanId);
                    stmt.setInt(2, kills);
                    stmt.setInt(3, kills);
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao adicionar kills: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Adiciona deaths
     * Grug Brain: UPSERT (cria se não existe, atualiza se existe) - async
     */
    public void addDeaths(int clanId, int deaths) {
        statsReader.invalidateCache(clanId);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO gladiador_stats (clan_id, total_deaths) " +
                         "VALUES (?, ?) " +
                         "ON CONFLICT (clan_id) DO UPDATE SET " +
                         "total_deaths = gladiador_stats.total_deaths + ?")) {

                    stmt.setInt(1, clanId);
                    stmt.setInt(2, deaths);
                    stmt.setInt(3, deaths);
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao adicionar deaths: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Adiciona pontos de temporada
     * Grug Brain: UPSERT (cria se não existe, atualiza se existe) - async
     */
    public void addSeasonPoints(int clanId, int points) {
        statsReader.invalidateCache(clanId);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO gladiador_stats (clan_id, season_points) " +
                         "VALUES (?, ?) " +
                         "ON CONFLICT (clan_id) DO UPDATE SET " +
                         "season_points = gladiador_stats.season_points + ?")) {

                    stmt.setInt(1, clanId);
                    stmt.setInt(2, points);
                    stmt.setInt(3, points);
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao adicionar pontos de temporada: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}

