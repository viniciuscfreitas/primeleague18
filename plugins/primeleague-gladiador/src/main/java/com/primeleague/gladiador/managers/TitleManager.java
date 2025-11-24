package com.primeleague.gladiador.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.gladiador.GladiadorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de tags temporárias do Gladiador
 * Grug Brain: Tags simples, expiram no próximo Gladiador
 */
public class TitleManager {

    private final GladiadorPlugin plugin;

    // Cache simples para placeholders (TTL 5 segundos)
    private final ConcurrentHashMap<UUID, CachedTitle> titleCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 5_000; // 5 segundos

    private static class CachedTitle {
        private final String display;
        private final long timestamp;

        public CachedTitle(String display, long timestamp) {
            this.display = display;
            this.timestamp = timestamp;
        }

        public String getDisplay() { return display; }
        public long getTimestamp() { return timestamp; }
    }

    public TitleManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Limpa tags antigas (expiraram)
     * Grug Brain: Executa async, simples
     */
    public void clearExpiredTitles() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM gladiador_current_titles WHERE expires_at IS NOT NULL AND expires_at < CURRENT_TIMESTAMP")) {
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Erro ao limpar tags expiradas: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Limpa todas as tags (antes de atribuir novas)
     * Grug Brain: Simples, direto
     */
    public void clearAllTitles() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("DELETE FROM gladiador_current_titles")) {
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Erro ao limpar tags: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Define tag para player
     * Grug Brain: Simples, async, atualiza cache
     */
    public void setTitle(UUID playerUuid, String title, String display) {
        if (playerUuid == null) return;

        // Atualizar cache imediatamente
        titleCache.put(playerUuid, new CachedTitle(display, System.currentTimeMillis()));

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO gladiador_current_titles (player_uuid, title, display, expires_at) " +
                         "VALUES (?, ?, ?, NULL) " +
                         "ON CONFLICT (player_uuid) DO UPDATE SET title = ?, display = ?, expires_at = NULL")) {
                    stmt.setObject(1, playerUuid);
                    stmt.setString(2, title);
                    stmt.setString(3, display);
                    stmt.setString(4, title);
                    stmt.setString(5, display);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Erro ao definir tag: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Obtém tag atual do player
     * Grug Brain: Query async com callback
     */
    public void getTitle(UUID playerUuid, java.util.function.Consumer<String> callback) {
        if (playerUuid == null) {
            callback.accept(null);
            return;
        }

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "SELECT display FROM gladiador_current_titles WHERE player_uuid = ? " +
                         "AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)")) {
                    stmt.setObject(1, playerUuid);
                    ResultSet rs = stmt.executeQuery();
                    String display = null;
                    if (rs.next()) {
                        display = rs.getString("display");
                    }
                    final String finalDisplay = display;
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            callback.accept(finalDisplay);
                        }
                    }.runTask(plugin);
                } catch (SQLException e) {
                    plugin.getLogger().warning("Erro ao obter tag: " + e.getMessage());
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            callback.accept(null);
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Obtém tag atual do player (síncrono, usa cache)
     * Grug Brain: Para placeholders, usa cache com TTL curto
     */
    public String getTitleSync(UUID playerUuid) {
        if (playerUuid == null) return "";

        CachedTitle cached = titleCache.get(playerUuid);
        if (cached != null && System.currentTimeMillis() - cached.getTimestamp() < CACHE_TTL) {
            return cached.getDisplay() != null ? cached.getDisplay() : "";
        }

        // Cache expirado ou não existe - buscar async e atualizar cache
        getTitle(playerUuid, display -> {
            titleCache.put(playerUuid, new CachedTitle(display, System.currentTimeMillis()));
        });

        // Retornar vazio enquanto busca (placeholder será atualizado no próximo tick)
        return "";
    }

    /**
     * Limpa cache de um player (quando tag é atualizada)
     */
    public void invalidateCache(UUID playerUuid) {
        titleCache.remove(playerUuid);
    }
}

