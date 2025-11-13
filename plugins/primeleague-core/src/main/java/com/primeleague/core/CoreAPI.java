package com.primeleague.core;

import com.primeleague.core.database.DatabaseManager;
import com.primeleague.core.models.PlayerData;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.UUID;

/**
 * API pública estática para outros plugins
 * Grug Brain: Métodos estáticos simples, queries diretas
 */
public class CoreAPI {

    private static CorePlugin getPlugin() {
        CorePlugin plugin = (CorePlugin) Bukkit.getPluginManager().getPlugin("PrimeleagueCore");
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("PrimeleagueCore não está habilitado");
        }
        return plugin;
    }

    public static DatabaseManager getDatabase() {
        return getPlugin().getDatabaseManager();
    }

    public static boolean isEnabled() {
        CorePlugin plugin = (CorePlugin) Bukkit.getPluginManager().getPlugin("PrimeleagueCore");
        return plugin != null && plugin.isEnabled();
    }

    public static PlayerData getPlayer(UUID uuid) {
        try (Connection conn = getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid, name, ip_hash, discord_id, access_code, access_expires_at, " +
                "payment_status, money, elo, created_at, kills, deaths, killstreak, " +
                "best_killstreak, last_kill_at, last_death_at, last_seen_at FROM users WHERE uuid = ?");
            stmt.setObject(1, uuid);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToPlayerData(rs);
            }
            return null;
        } catch (SQLException e) {
            getPlugin().getLogger().severe("Erro ao buscar player: " + e.getMessage());
            return null;
        }
    }

    public static PlayerData getPlayerByName(String name) {
        try (Connection conn = getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid, name, ip_hash, discord_id, access_code, access_expires_at, " +
                "payment_status, money, elo, created_at, kills, deaths, killstreak, " +
                "best_killstreak, last_kill_at, last_death_at, last_seen_at FROM users WHERE name = ?");
            stmt.setString(1, name);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToPlayerData(rs);
            }
            return null;
        } catch (SQLException e) {
            getPlugin().getLogger().severe("Erro ao buscar player por nome: " + e.getMessage());
            return null;
        }
    }

    public static PlayerData getPlayerByDiscordId(long discordId) {
        try (Connection conn = getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid, name, ip_hash, discord_id, access_code, access_expires_at, " +
                "payment_status, money, elo, created_at, kills, deaths, killstreak, " +
                "best_killstreak, last_kill_at, last_death_at, last_seen_at FROM users WHERE discord_id = ? LIMIT 1");
            stmt.setLong(1, discordId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToPlayerData(rs);
            }
            return null;
        } catch (SQLException e) {
            getPlugin().getLogger().severe("Erro ao buscar player por Discord ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Verifica se um código de acesso já foi usado
     * Grug Brain: Query simples, direta
     */
    public static boolean isAccessCodeUsed(String accessCode) {
        if (accessCode == null || accessCode.isEmpty()) {
            return false;
        }
        try (Connection conn = getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE access_code = ?");
            stmt.setString(1, accessCode);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        } catch (SQLException e) {
            getPlugin().getLogger().severe("Erro ao verificar código de acesso: " + e.getMessage());
            return true; // Em caso de erro, assumir que está em uso (seguro)
        }
    }

    public static void savePlayer(PlayerData data) {
        try (Connection conn = getDatabase().getConnection()) {
            // Grug Brain: ON CONFLICT (name) - name é UNIQUE, sempre atualiza registro existente
            // UUID deve ser compatível com Paper 1.8.8 (gerado apenas com nome)
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO users (uuid, name, ip_hash, discord_id, access_code, access_expires_at, " +
                "payment_status, money, elo, created_at, kills, deaths, killstreak, best_killstreak, " +
                "last_kill_at, last_death_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (name) DO UPDATE SET " +
                "uuid = EXCLUDED.uuid, ip_hash = EXCLUDED.ip_hash, discord_id = EXCLUDED.discord_id, " +
                "access_code = EXCLUDED.access_code, access_expires_at = EXCLUDED.access_expires_at, " +
                "payment_status = EXCLUDED.payment_status, money = EXCLUDED.money, elo = EXCLUDED.elo, " +
                "kills = EXCLUDED.kills, deaths = EXCLUDED.deaths, killstreak = EXCLUDED.killstreak, " +
                "best_killstreak = EXCLUDED.best_killstreak, last_kill_at = EXCLUDED.last_kill_at, " +
                "last_death_at = EXCLUDED.last_death_at, last_seen_at = EXCLUDED.last_seen_at");

            stmt.setObject(1, data.getUuid());
            stmt.setString(2, data.getName());
            if (data.getIpHash() != null) {
                stmt.setString(3, data.getIpHash());
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            if (data.getDiscordId() != null) {
                stmt.setLong(4, data.getDiscordId());
            } else {
                stmt.setNull(4, Types.BIGINT);
            }
            stmt.setString(5, data.getAccessCode());
            if (data.getAccessExpiresAt() != null) {
                stmt.setTimestamp(6, new Timestamp(data.getAccessExpiresAt().getTime()));
            } else {
                stmt.setNull(6, Types.TIMESTAMP);
            }
            stmt.setString(7, data.getPaymentStatus());
            stmt.setLong(8, data.getMoney());
            stmt.setInt(9, data.getElo());
            if (data.getCreatedAt() != null) {
                stmt.setTimestamp(10, new Timestamp(data.getCreatedAt().getTime()));
            } else {
                stmt.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
            }
            stmt.setInt(11, data.getKills());
            stmt.setInt(12, data.getDeaths());
            stmt.setInt(13, data.getKillstreak());
            stmt.setInt(14, data.getBestKillstreak());
            if (data.getLastKillAt() != null) {
                stmt.setTimestamp(15, new Timestamp(data.getLastKillAt().getTime()));
            } else {
                stmt.setNull(15, Types.TIMESTAMP);
            }
            if (data.getLastDeathAt() != null) {
                stmt.setTimestamp(16, new Timestamp(data.getLastDeathAt().getTime()));
            } else {
                stmt.setNull(16, Types.TIMESTAMP);
            }
            if (data.getLastSeenAt() != null) {
                stmt.setTimestamp(17, new Timestamp(data.getLastSeenAt().getTime()));
            } else {
                stmt.setNull(17, Types.TIMESTAMP);
            }

            stmt.executeUpdate();
        } catch (SQLException e) {
            getPlugin().getLogger().severe("Erro ao salvar player: " + e.getMessage());
        }
    }

    private static PlayerData mapResultSetToPlayerData(ResultSet rs) throws SQLException {
        PlayerData data = new PlayerData();
        data.setUuid((UUID) rs.getObject("uuid"));
        data.setName(rs.getString("name"));
        data.setIpHash(rs.getString("ip_hash"));
        long discordId = rs.getLong("discord_id");
        if (!rs.wasNull()) {
            data.setDiscordId(discordId);
        }
        data.setAccessCode(rs.getString("access_code"));
        Timestamp expiresAt = rs.getTimestamp("access_expires_at");
        if (expiresAt != null) {
            data.setAccessExpiresAt(new Date(expiresAt.getTime()));
        }
        data.setPaymentStatus(rs.getString("payment_status"));
        data.setMoney(rs.getLong("money"));
        data.setElo(rs.getInt("elo"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            data.setCreatedAt(new Date(createdAt.getTime()));
        }
        data.setKills(rs.getInt("kills"));
        data.setDeaths(rs.getInt("deaths"));
        data.setKillstreak(rs.getInt("killstreak"));
        data.setBestKillstreak(rs.getInt("best_killstreak"));
        Timestamp lastKillAt = rs.getTimestamp("last_kill_at");
        if (lastKillAt != null) {
            data.setLastKillAt(new Date(lastKillAt.getTime()));
        }
        Timestamp lastDeathAt = rs.getTimestamp("last_death_at");
        if (lastDeathAt != null) {
            data.setLastDeathAt(new Date(lastDeathAt.getTime()));
        }
        Timestamp lastSeenAt = rs.getTimestamp("last_seen_at");
        if (lastSeenAt != null) {
            data.setLastSeenAt(new Date(lastSeenAt.getTime()));
        }
        return data;
    }
}

