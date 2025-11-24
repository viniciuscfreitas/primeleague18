package com.primeleague.x1.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.X1Stats;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Stats de duels x1
 * Grug Brain: Stats específicas de duels (wins/losses/winstreak), separadas de stats globais
 */
public class StatsManager {

    private final X1Plugin plugin;
    // Cache de stats: UUID -> CachedStats (com timestamp)
    private final Map<UUID, CachedStats> statsCache;
    private final long cacheDuration;

    public StatsManager(X1Plugin plugin) {
        this.plugin = plugin;
        this.statsCache = new ConcurrentHashMap<>();
        this.cacheDuration = plugin.getConfig().getLong("cache.stats-duration", 300) * 1000; // 5 minutos
    }

    /**
     * Classe interna para cache com TTL individual
     */
    private static class CachedStats {
        private final X1Stats stats;
        private final long timestamp;

        public CachedStats(X1Stats stats) {
            this.stats = stats;
            this.timestamp = System.currentTimeMillis();
        }

        public X1Stats getStats() {
            return stats;
        }

        public boolean isExpired(long cacheDuration) {
            return System.currentTimeMillis() - timestamp > cacheDuration;
        }
    }

    /**
     * Atualiza stats após match (atômico via SQL)
     */
    public void updateStats(UUID winnerUuid, UUID loserUuid) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    // Atualizar winner: wins++, winstreak++, best_winstreak = GREATEST(...)
                    PreparedStatement winnerStmt = conn.prepareStatement(
                        "INSERT INTO x1_stats (player_uuid, wins, losses, winstreak, best_winstreak, last_match_at) " +
                        "VALUES (?, 1, 0, 1, 1, ?) " +
                        "ON CONFLICT (player_uuid) DO UPDATE SET " +
                        "wins = x1_stats.wins + 1, " +
                        "winstreak = x1_stats.winstreak + 1, " +
                        "best_winstreak = GREATEST(x1_stats.best_winstreak, x1_stats.winstreak + 1), " +
                        "last_match_at = EXCLUDED.last_match_at");
                    
                    winnerStmt.setObject(1, winnerUuid);
                    winnerStmt.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
                    winnerStmt.executeUpdate();

                    // Atualizar loser: losses++, winstreak = 0
                    PreparedStatement loserStmt = conn.prepareStatement(
                        "INSERT INTO x1_stats (player_uuid, wins, losses, winstreak, best_winstreak, last_match_at) " +
                        "VALUES (?, 0, 1, 0, 0, ?) " +
                        "ON CONFLICT (player_uuid) DO UPDATE SET " +
                        "losses = x1_stats.losses + 1, " +
                        "winstreak = 0, " +
                        "last_match_at = EXCLUDED.last_match_at");
                    
                    loserStmt.setObject(1, loserUuid);
                    loserStmt.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
                    loserStmt.executeUpdate();

                    // Limpar cache (invalidar)
                    statsCache.remove(winnerUuid);
                    statsCache.remove(loserUuid);
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao atualizar stats: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Obtém stats de um player (com cache e TTL individual)
     * Grug Brain: Retorna stats vazias imediatamente, busca async em background
     */
    public X1Stats getStats(UUID playerUuid) {
        // Verificar cache com TTL individual
        CachedStats cached = statsCache.get(playerUuid);
        if (cached != null && !cached.isExpired(cacheDuration)) {
            return cached.getStats();
        }

        // Cache expirado ou não existe - buscar do banco async
        // Retornar stats vazias imediatamente (evita lag)
        X1Stats emptyStats = new X1Stats(playerUuid);
        
        // Buscar do banco em background e atualizar cache
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT wins, losses, winstreak, best_winstreak, last_match_at FROM x1_stats WHERE player_uuid = ?");
                    stmt.setObject(1, playerUuid);

                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        X1Stats stats = new X1Stats(playerUuid);
                        stats.setWins(rs.getInt("wins"));
                        stats.setLosses(rs.getInt("losses"));
                        stats.setWinstreak(rs.getInt("winstreak"));
                        stats.setBestWinstreak(rs.getInt("best_winstreak"));
                        java.sql.Timestamp lastMatch = rs.getTimestamp("last_match_at");
                        if (lastMatch != null) {
                            stats.setLastMatchAt(new Date(lastMatch.getTime()));
                        }

                        // Atualizar cache
                        statsCache.put(playerUuid, new CachedStats(stats));
                    } else {
                        // Criar stats vazias e cachear
                        X1Stats stats = new X1Stats(playerUuid);
                        statsCache.put(playerUuid, new CachedStats(stats));
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao buscar stats: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
        
        return emptyStats; // Retornar imediatamente (stats vazias ou do cache se disponível)
    }

    /**
     * Obtém stats de um player de forma síncrona (para uso interno quando necessário)
     * Grug Brain: Usar apenas quando realmente necessário (ex: PlaceholderAPI pode esperar)
     */
    public X1Stats getStatsSync(UUID playerUuid) {
        // Verificar cache primeiro
        CachedStats cached = statsCache.get(playerUuid);
        if (cached != null && !cached.isExpired(cacheDuration)) {
            return cached.getStats();
        }

        // Buscar do banco (síncrono)
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT wins, losses, winstreak, best_winstreak, last_match_at FROM x1_stats WHERE player_uuid = ?");
            stmt.setObject(1, playerUuid);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                X1Stats stats = new X1Stats(playerUuid);
                stats.setWins(rs.getInt("wins"));
                stats.setLosses(rs.getInt("losses"));
                stats.setWinstreak(rs.getInt("winstreak"));
                stats.setBestWinstreak(rs.getInt("best_winstreak"));
                java.sql.Timestamp lastMatch = rs.getTimestamp("last_match_at");
                if (lastMatch != null) {
                    stats.setLastMatchAt(new Date(lastMatch.getTime()));
                }

                // Atualizar cache
                statsCache.put(playerUuid, new CachedStats(stats));
                return stats;
            } else {
                // Criar stats vazias
                X1Stats stats = new X1Stats(playerUuid);
                statsCache.put(playerUuid, new CachedStats(stats));
                return stats;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar stats: " + e.getMessage());
            e.printStackTrace();
            return new X1Stats(playerUuid); // Retornar stats vazias em caso de erro
        }
    }

    /**
     * Limpa cache expirado (task periódica)
     * Grug Brain: TTL individual - remove apenas entradas expiradas
     */
    public void clearExpiredCache() {
        statsCache.entrySet().removeIf(entry -> entry.getValue().isExpired(cacheDuration));
    }
}

