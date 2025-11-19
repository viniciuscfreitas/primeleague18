package com.primeleague.gladiador.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.GladiadorStats;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de estatísticas do Gladiador
 * Grug Brain: CRUD direto no PostgreSQL, cache simples com TTL
 */
public class StatsManager {

    private final GladiadorPlugin plugin;
    private final Map<Integer, GladiadorStats> cache;
    private final Map<Integer, Long> cacheTimestamps;
    private final long CACHE_TTL_MILLIS;

    public StatsManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.cache = new ConcurrentHashMap<>();
        this.cacheTimestamps = new ConcurrentHashMap<>();
        this.CACHE_TTL_MILLIS = plugin.getConfig().getLong("cache.stats-duration", 300) * 1000;
    }

    /**
     * Obtém stats do clan (com cache)
     * Grug Brain: Retorna do cache se disponível, senão busca async e atualiza cache
     */
    public GladiadorStats getStats(int clanId) {
        // Verificar cache primeiro
        GladiadorStats cached = cache.get(clanId);
        Long timestamp = cacheTimestamps.get(clanId);

        if (cached != null && timestamp != null) {
            long age = System.currentTimeMillis() - timestamp;
            if (age < CACHE_TTL_MILLIS) {
                return cached; // Cache válido
            }
        }

        // Cache expirado ou não existe - buscar do banco async e atualizar cache
        loadStatsAsync(clanId);

        // Retornar do cache se disponível (mesmo que expirado), senão retornar novo objeto
        return cache.getOrDefault(clanId, new GladiadorStats(clanId));
    }

    /**
     * Carrega stats do banco de forma assíncrona e atualiza cache
     */
    private void loadStatsAsync(int clanId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM gladiador_stats WHERE clan_id = ?")) {

                    stmt.setInt(1, clanId);
                    ResultSet rs = stmt.executeQuery();

                    GladiadorStats stats;
                    if (rs.next()) {
                        stats = mapResultSetToStats(rs);
                    } else {
                        // Criar stats se não existir
                        createStats(clanId);
                        stats = new GladiadorStats(clanId);
                    }

                    // Atualizar cache na thread principal
                    final GladiadorStats finalStats = stats;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            cache.put(clanId, finalStats);
                            cacheTimestamps.put(clanId, System.currentTimeMillis());
                        }
                    }.runTask(plugin);

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao buscar stats: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Incrementa vitórias
     * Grug Brain: Atualiza banco async e invalida cache
     */
    public void incrementWins(int clanId) {
        // Invalidar cache
        cache.remove(clanId);
        cacheTimestamps.remove(clanId);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE gladiador_stats SET wins = wins + 1, last_win = CURRENT_TIMESTAMP WHERE clan_id = ?")) {

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
     * Grug Brain: Atualiza banco async e invalida cache
     */
    public void incrementParticipation(int clanId) {
        // Invalidar cache
        cache.remove(clanId);
        cacheTimestamps.remove(clanId);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE gladiador_stats SET participations = participations +1 WHERE clan_id = ?")) {

                    stmt.setInt(1, clanId);
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao incrementar participações: "+ e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Adiciona kills
     * Grug Brain: Atualiza banco async e invalida cache
     */
    public void addKills(int clanId, int kills) {
        // Invalidar cache
        cache.remove(clanId);
        cacheTimestamps.remove(clanId);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE gladiador_stats SET total_kills = total_kills + ? WHERE clan_id = ?")) {

                    stmt.setInt(1, kills);
                    stmt.setInt(2, clanId);
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao adicionar kills: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Adiciona deaths
     * Grug Brain: Atualiza banco async e invalida cache
     */
    public void addDeaths(int clanId, int deaths) {
        // Invalidar cache
        cache.remove(clanId);
        cacheTimestamps.remove(clanId);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE gladiador_stats SET total_deaths = total_deaths + ? WHERE clan_id = ?")) {

                    stmt.setInt(1, deaths);
                    stmt.setInt(2, clanId);
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao adicionar deaths: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Obtém top clans por vitórias
     */
    public List<GladiadorStats> getTopByWins(int limit) {
        List<GladiadorStats> stats = new ArrayList<>();

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM gladiador_stats ORDER BY wins DESC, total_kills DESC LIMIT ?")) {

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                stats.add(mapResultSetToStats(rs));
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar top: " + e.getMessage());
        }

        return stats;
    }

    /**
     * Cria stats se não existir
     */
    private void createStats(int clanId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO gladiador_stats (clan_id) VALUES (?) ON CONFLICT (clan_id) DO NOTHING")) {

            stmt.setInt(1, clanId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().warning("Erro ao criar stats: " + e.getMessage());
        }
    }

    /**
     * Mapeia ResultSet para GladiadorStats
     */
    private GladiadorStats mapResultSetToStats(ResultSet rs) throws SQLException {
        GladiadorStats stats = new GladiadorStats();
        stats.setClanId(rs.getInt("clan_id"));
        stats.setWins(rs.getInt("wins"));
        stats.setParticipations(rs.getInt("participations"));
        stats.setTotalKills(rs.getInt("total_kills"));
        stats.setTotalDeaths(rs.getInt("total_deaths"));

        Timestamp lastWin = rs.getTimestamp("last_win");
        if (lastWin != null) {
            stats.setLastWin(new java.util.Date(lastWin.getTime()));
        }

        return stats;
    }
}
