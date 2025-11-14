package com.primeleague.punishments.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.punishments.PunishPlugin;
import com.primeleague.punishments.models.PunishmentData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de punições
 * Grug Brain: Cache simples com TTL, queries diretas, sem DAO/Repository
 */
public class PunishManager {

    private final PunishPlugin plugin;
    private final Map<UUID, PunishmentCache> cache = new ConcurrentHashMap<>();
    private final long cacheTtl;

    public PunishManager(PunishPlugin plugin) {
        this.plugin = plugin;
        this.cacheTtl = plugin.getConfig().getLong("cache-ttl", 60) * 1000L; // Converter para ms

        // Task periódica para limpar cache expirado (a cada 60s)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanExpiredCache();
        }, 1200L, 1200L); // A cada 60s (1200 ticks)
    }

    /**
     * Verifica se player está banido (UUID ou IP)
     */
    public boolean isBanned(UUID uuid, String ip) {
        // 1. Check cache (TTL 60s)
        PunishmentCache cached = cache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return cached.isBanned();
        }

        // 2. Query DB
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM punishments WHERE " +
                "(player_uuid = ? OR (ip = ? AND ip IS NOT NULL)) AND " +
                "type = 'ban' AND active = TRUE AND " +
                "(expires_at IS NULL OR expires_at > NOW()) " +
                "ORDER BY created_at DESC LIMIT 1"
            );
            stmt.setObject(1, uuid);
            stmt.setString(2, ip);

            ResultSet rs = stmt.executeQuery();
            boolean banned = rs.next();
            String banReason = null;

            // 3. Update cache
            if (cached == null) {
                cached = new PunishmentCache();
            }
            cached.setBanned(banned);
            if (banned) {
                banReason = rs.getString("reason");
                cached.setBanReason(banReason);
            }
            cache.put(uuid, cached);

            return banned;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao verificar ban: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtém motivo do ban
     */
    public String getBanReason(UUID uuid, String ip) {
        // Check cache
        PunishmentCache cached = cache.get(uuid);
        if (cached != null && !cached.isExpired() && cached.getBanReason() != null) {
            return cached.getBanReason();
        }

        // Query DB
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT reason FROM punishments WHERE " +
                "(player_uuid = ? OR (ip = ? AND ip IS NOT NULL)) AND " +
                "type = 'ban' AND active = TRUE AND " +
                "(expires_at IS NULL OR expires_at > NOW()) " +
                "ORDER BY created_at DESC LIMIT 1"
            );
            stmt.setObject(1, uuid);
            stmt.setString(2, ip);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String reason = rs.getString("reason");
                if (reason == null || reason.isEmpty()) {
                    return "Sem motivo especificado";
                }
                return reason;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao obter motivo do ban: " + e.getMessage());
            e.printStackTrace();
        }

        return "Sem motivo especificado";
    }

    /**
     * Verifica se player está mutado
     */
    public boolean isMuted(UUID uuid) {
        // 1. Check cache (TTL 60s)
        PunishmentCache cached = cache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return cached.isMuted();
        }

        // 2. Query DB
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM punishments WHERE " +
                "player_uuid = ? AND type = 'mute' AND active = TRUE AND " +
                "(expires_at IS NULL OR expires_at > NOW()) " +
                "ORDER BY created_at DESC LIMIT 1"
            );
            stmt.setObject(1, uuid);

            ResultSet rs = stmt.executeQuery();
            boolean muted = rs.next();
            String muteReason = null;

            // 3. Update cache
            if (cached == null) {
                cached = new PunishmentCache();
            }
            cached.setMuted(muted);
            if (muted) {
                muteReason = rs.getString("reason");
                cached.setMuteReason(muteReason);
            }
            cache.put(uuid, cached);

            return muted;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao verificar mute: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtém motivo do mute
     */
    public String getMuteReason(UUID uuid) {
        // Check cache
        PunishmentCache cached = cache.get(uuid);
        if (cached != null && !cached.isExpired() && cached.getMuteReason() != null) {
            return cached.getMuteReason();
        }

        // Query DB
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT reason FROM punishments WHERE " +
                "player_uuid = ? AND type = 'mute' AND active = TRUE AND " +
                "(expires_at IS NULL OR expires_at > NOW()) " +
                "ORDER BY created_at DESC LIMIT 1"
            );
            stmt.setObject(1, uuid);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String reason = rs.getString("reason");
                if (reason == null || reason.isEmpty()) {
                    return "Sem motivo especificado";
                }
                return reason;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao obter motivo do mute: " + e.getMessage());
            e.printStackTrace();
        }

        return "Sem motivo especificado";
    }

    /**
     * Aplica punição (ban/mute/warn/kick)
     * Grug Brain: Insert async para não bloquear thread principal
     */
    public void applyPunish(UUID playerUuid, String ip, String type, String reason,
                            UUID staffUuid, Long durationSeconds) {
        // Se type = 'kick', não salvar no banco (conforme plano)
        if (type.equals("kick")) {
            // Kick player diretamente sem salvar no DB
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // Usar ChatColor ao invés de § para evitar problemas de encoding
                    String message = org.bukkit.ChatColor.RED + "Voce foi expulso!\n" +
                                    org.bukkit.ChatColor.GRAY + "Motivo: " +
                                    org.bukkit.ChatColor.WHITE + reason;
                    player.kickPlayer(message);
                });
            }
            // Integrar Discord (notifyDiscord) mesmo sem salvar no DB
            if (plugin.getConfig().getBoolean("integrations.discord-notify", true)) {
                plugin.getDiscordIntegration().notifyDiscord(playerUuid, type, reason, staffUuid, durationSeconds);
            }
            return;
        }

        // Executar insert em thread async (ban/mute/warn)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // 1. Insert DB
            Timestamp expiresAt = null;
            if (durationSeconds != null && durationSeconds > 0) {
                expiresAt = new Timestamp(System.currentTimeMillis() + (durationSeconds * 1000L));
            }

            try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO punishments (player_uuid, ip, type, reason, staff_uuid, expires_at, active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, TRUE)"
                );
                stmt.setObject(1, playerUuid);
                if (ip != null && !ip.isEmpty()) {
                    stmt.setString(2, ip);
                } else {
                    stmt.setNull(2, Types.VARCHAR);
                }
                stmt.setString(3, type);
                stmt.setString(4, reason);
                if (staffUuid != null) {
                    stmt.setObject(5, staffUuid);
                } else {
                    stmt.setNull(5, Types.OTHER);
                }
                if (expiresAt != null) {
                    stmt.setTimestamp(6, expiresAt);
                } else {
                    stmt.setNull(6, Types.TIMESTAMP);
                }

                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao aplicar punição: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            // 2. Invalidar cache
            invalidateCache(playerUuid);

            // 3. Ações síncronas (voltar à thread principal)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Se type = 'ban', kick player
                if (type.equals("ban")) {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null) {
                        // Usar ChatColor ao invés de § para evitar problemas de encoding
                        String message = org.bukkit.ChatColor.RED + "Voce esta banido!\n" +
                                        org.bukkit.ChatColor.GRAY + "Motivo: " +
                                        org.bukkit.ChatColor.WHITE + reason;
                        player.kickPlayer(message);
                    }
                }

                // Se type = 'mute', notificar player
                if (type.equals("mute")) {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null) {
                        player.sendMessage(org.bukkit.ChatColor.RED + "Voce foi mutado!\n" +
                                         org.bukkit.ChatColor.GRAY + "Motivo: " +
                                         org.bukkit.ChatColor.WHITE + reason);
                    }
                }

                // Se type = 'warn', notificar player
                if (type.equals("warn")) {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null) {
                        player.sendMessage(org.bukkit.ChatColor.YELLOW + "Voce recebeu um aviso!\n" +
                                         org.bukkit.ChatColor.GRAY + "Motivo: " +
                                         org.bukkit.ChatColor.WHITE + reason);
                    }
                }

                // Integrar Clans (addAlert se player tem clan)
                if (plugin.getConfig().getBoolean("integrations.clans-alert", true)) {
                    integrateClans(playerUuid, type, reason, staffUuid);
                }

                // Integrar Discord (notifyDiscord)
                if (plugin.getConfig().getBoolean("integrations.discord-notify", true)) {
                    plugin.getDiscordIntegration().notifyDiscord(playerUuid, type, reason, staffUuid, durationSeconds);
                }
            });
        });
    }

    /**
     * Remove punição (unban/unmute)
     * Grug Brain: Update async para não bloquear thread principal
     */
    public void removePunish(UUID playerUuid, String type, UUID staffUuid,
                             java.util.function.Consumer<Boolean> callback) {
        // Executar update em thread async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE punishments SET active = FALSE WHERE player_uuid = ? AND type = ? AND active = TRUE"
                );
                stmt.setObject(1, playerUuid);
                stmt.setString(2, type);

                int updated = stmt.executeUpdate();

                // Invalidar cache
                invalidateCache(playerUuid);

                // Callback na thread principal
                final boolean success = updated > 0;
                if (callback != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        callback.accept(success);
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao remover punição: " + e.getMessage());
                e.printStackTrace();
                if (callback != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        callback.accept(false);
                    });
                }
            }
        });
    }

    /**
     * Histórico de punições
     * Grug Brain: Query async para não bloquear thread principal
     */
    public void getHistory(UUID playerUuid, java.util.function.Consumer<List<PunishmentData>> callback) {
        // Executar query em thread async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PunishmentData> history = new ArrayList<>();
            try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM punishments WHERE player_uuid = ? ORDER BY created_at DESC LIMIT 50"
                );
                stmt.setObject(1, playerUuid);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    PunishmentData data = new PunishmentData();
                    data.setId(rs.getInt("id"));
                    data.setPlayerUuid((UUID) rs.getObject("player_uuid"));
                    data.setIp(rs.getString("ip"));
                    data.setType(rs.getString("type"));
                    data.setReason(rs.getString("reason"));
                    Object staffUuidObj = rs.getObject("staff_uuid");
                    if (staffUuidObj != null) {
                        data.setStaffUuid((UUID) staffUuidObj);
                    }
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) {
                        data.setCreatedAt(new java.util.Date(createdAt.getTime()));
                    }
                    Timestamp expiresAt = rs.getTimestamp("expires_at");
                    if (expiresAt != null) {
                        data.setExpiresAt(new java.util.Date(expiresAt.getTime()));
                    }
                    data.setActive(rs.getBoolean("active"));
                    data.setAppealed(rs.getBoolean("appealed"));
                    history.add(data);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao obter histórico: " + e.getMessage());
                e.printStackTrace();
            }

            // Callback na thread principal
            final List<PunishmentData> finalHistory = history;
            if (callback != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    callback.accept(finalHistory);
                });
            }
        });
    }

    /**
     * Parse duração (1h, 7d, 30d, etc) - Java 8 compatível
     */
    public long parseDuration(String input) {
        if (input == null || input.isEmpty()) {
            return 0; // Permanente
        }

        // Regex: (\d+)([smhd])?
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)([smhd])?");
        java.util.regex.Matcher matcher = pattern.matcher(input.toLowerCase());

        if (matcher.find()) {
            long num = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            if (unit == null || unit.isEmpty()) {
                return num; // Segundos default
            }

            // Java 8: if/else ao invés de switch expression
            if ("s".equals(unit)) {
                return num;
            } else if ("m".equals(unit)) {
                return num * 60;
            } else if ("h".equals(unit)) {
                return num * 3600;
            } else if ("d".equals(unit)) {
                return num * 86400;
            }
        }

        return 0; // Inválido = permanente
    }

    /**
     * Invalidar cache (chamado em applyPunish/removePunish)
     */
    private void invalidateCache(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Limpar cache expirado (task periódica)
     */
    private void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    /**
     * Integrar ClansPlugin (adicionar alerta se player tem clan)
     * Grug Brain: Usar API pública ao invés de acessar manager diretamente
     */
    private void integrateClans(UUID playerUuid, String type, String reason, UUID staffUuid) {
        org.bukkit.plugin.Plugin clansPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueClans");
        if (clansPlugin != null && clansPlugin.isEnabled()) {
            try {
                com.primeleague.clans.ClansPlugin cp = (com.primeleague.clans.ClansPlugin) clansPlugin;
                com.primeleague.clans.models.ClanData clan = cp.getClansManager().getClanByMember(playerUuid);
                if (clan != null) {
                    String alertType = type.equals("ban") ? "BAN" :
                                      type.equals("mute") ? "PUNISHMENT" : "WARNING";
                    String alertMsg = "Punição aplicada: " + type.toUpperCase() + " - " + reason;
                    // Gerar punishmentId único (playerUuid + timestamp)
                    String punishmentId = playerUuid.toString() + "_" + System.currentTimeMillis();
                    // Usar API pública ao invés de acessar manager diretamente
                    cp.addPunishmentAlert(clan.getId(), playerUuid, alertType, alertMsg, punishmentId, staffUuid);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erro ao integrar ClansPlugin: " + e.getMessage());
            }
        }
    }

    /**
     * Classe interna para cache de punições
     */
    private class PunishmentCache {
        private boolean banned;
        private boolean muted;
        private String banReason;
        private String muteReason;
        private final long timestamp;

        public PunishmentCache() {
            this.timestamp = System.currentTimeMillis();
            this.banned = false;
            this.muted = false;
        }

        public boolean isBanned() {
            return banned;
        }

        public void setBanned(boolean banned) {
            this.banned = banned;
        }

        public boolean isMuted() {
            return muted;
        }

        public void setMuted(boolean muted) {
            this.muted = muted;
        }

        public String getBanReason() {
            return banReason;
        }

        public void setBanReason(String banReason) {
            this.banReason = banReason;
        }

        public String getMuteReason() {
            return muteReason;
        }

        public void setMuteReason(String muteReason) {
            this.muteReason = muteReason;
        }

        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        public boolean isExpired(long now) {
            return (now - timestamp) > cacheTtl;
        }
    }
}

