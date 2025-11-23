package com.primeleague.clans.managers;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.models.ClanData;
import com.primeleague.clans.models.ClanMember;
import com.primeleague.clans.models.ClanAlert;
import com.primeleague.clans.models.EventWinRecord;
import com.primeleague.core.CoreAPI;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gerenciador de lógica de negócio dos clans
 * Grug Brain: Queries diretas PostgreSQL, validações inline
 */
public class ClansManager {

    private final ClansPlugin plugin;

    public ClansManager(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Cria um novo clan
     * Validações: nome 1-50 chars, tag 3 chars (sem cores), tag única
     */
    public ClanData createClan(String name, String tag, UUID leaderUuid) {
        // Validar nome
        name = name.trim();
        if (name.length() < 1 || name.length() > 50) {
            return null;
        }

        // Validar tag
        String tagClean = ChatColor.stripColor(tag);
        if (tagClean.length() != 3 || tag.length() > 20) {
            return null;
        }
        tagClean = tagClean.toUpperCase(); // Case-insensitive

        // Verificar se player já está em clan
        if (getClanByMember(leaderUuid) != null) {
            return null;
        }

        // Verificar se tag já existe (case-insensitive)
        if (getClanByTag(tagClean) != null) {
            return null;
        }

        // Verificar limite de membros (se configurado)
        int maxMembers = plugin.getConfig().getInt("clan.max-members", 0);
        if (maxMembers > 0) {
            // Verificar quantos membros o leader já tem em outros clans (não aplicável aqui, mas pode ser usado no futuro)
            // Por enquanto, apenas verificar se o limite está configurado
        }

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Inserir clan
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO clans (name, tag, tag_clean, leader_uuid, created_at) " +
                "VALUES (?, ?, ?, ?, ?) RETURNING id")) {
                stmt.setString(1, name);
                stmt.setString(2, tag);
                stmt.setString(3, tagClean);
                stmt.setObject(4, leaderUuid);
                stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }

                    int clanId = rs.getInt("id");

                    // Adicionar leader como membro
                    addMember(clanId, leaderUuid, "LEADER");

                    // Buscar clan criado
                    ClanData clan = getClan(clanId);

                    // Criar canais no Discord (async)
                    if (plugin.getDiscordIntegration() != null) {
                        plugin.getDiscordIntegration().createDiscordChannels(clan);
                    }

                    return clan;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao criar clan: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Busca clan por ID
     */
    public ClanData getClan(int clanId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name, tag, tag_clean, leader_uuid, created_at, description, " +
                "discord_channel_id, discord_role_id, home_world, home_x, home_y, home_z, " +
                "points, event_wins_count, blocked_from_events, shield_start_hour, shield_end_hour " +
                "FROM clans WHERE id = ?")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToClanData(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar clan: " + e.getMessage());
            return null;
        }
    }

    /**
     * Busca clan por tag (case-insensitive)
     */
    public ClanData getClanByTag(String tagClean) {
        tagClean = tagClean.toUpperCase();
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name, tag, tag_clean, leader_uuid, created_at, description, " +
                "discord_channel_id, discord_role_id, home_world, home_x, home_y, home_z, " +
                "points, event_wins_count, blocked_from_events, shield_start_hour, shield_end_hour " +
                "FROM clans WHERE UPPER(tag_clean) = ?")) {
            stmt.setString(1, tagClean);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToClanData(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar clan por tag: " + e.getMessage());
            return null;
        }
    }

    /**
     * Busca clan por membro
     */
    public ClanData getClanByMember(UUID playerUuid) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT c.id, c.name, c.tag, c.tag_clean, c.leader_uuid, c.created_at, c.description, " +
                "c.discord_channel_id, c.discord_role_id, c.home_world, c.home_x, c.home_y, c.home_z, " +
                "c.points, c.event_wins_count, c.blocked_from_events, c.shield_start_hour, c.shield_end_hour " +
                "FROM clans c JOIN clan_members cm ON c.id = cm.clan_id " +
                "WHERE cm.player_uuid = ?")) {
            stmt.setObject(1, playerUuid);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToClanData(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar clan por membro: " + e.getMessage());
            return null;
        }
    }

    /**
     * Adiciona membro ao clan
     * Verifica limite de membros se configurado
     */
    public boolean addMember(int clanId, UUID playerUuid, String role) {
        // Verificar limite de membros (se configurado)
        int maxMembers = plugin.getConfig().getInt("clan.max-members", 0);
        if (maxMembers > 0) {
            List<ClanMember> currentMembers = getMembers(clanId);
            if (currentMembers.size() >= maxMembers) {
                return false; // Limite atingido
            }
        }
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO clan_members (clan_id, player_uuid, role, joined_at) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT (clan_id, player_uuid) DO NOTHING")) {
     * Remove membro do clan
     */
    public boolean removeMember(int clanId, UUID playerUuid) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM clan_members WHERE clan_id = ? AND player_uuid = ?")) {
            stmt.setInt(1, clanId);
            stmt.setObject(2, playerUuid);

            int rows = stmt.executeUpdate();
            if (rows > 0) {
                // Notificar Discord (se disponível)
                ClanData clan = getClan(clanId);
                if (clan != null && plugin.getDiscordIntegration() != null) {
                    com.primeleague.core.models.PlayerData playerData = CoreAPI.getPlayer(playerUuid);
                    String playerName = playerData != null ? playerData.getName() : "Desconhecido";
                    plugin.getDiscordIntegration().notifyDiscord(clan,
                        "👋 Membro Saiu",
                        "**" + playerName + "** saiu do clan.");
                }
            }
            return rows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao remover membro: " + e.getMessage());
            return false;
        }
    }

    /**
     * Busca membros do clan
     */
    public List<ClanMember> getMembers(int clanId) {
        List<ClanMember> members = new ArrayList<>();
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT clan_id, player_uuid, role, joined_at FROM clan_members WHERE clan_id = ?")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ClanMember member = new ClanMember();
                    member.setClanId(rs.getInt("clan_id"));
                    member.setPlayerUuid((UUID) rs.getObject("player_uuid"));
                    member.setRole(rs.getString("role"));
                    member.setJoinedAt(new Date(rs.getTimestamp("joined_at").getTime()));
                    members.add(member);
                }
            }
            return members;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar membros: " + e.getMessage());
            return members;
        }
    }

    /**
     * Busca role do membro no clan
     */
    public String getMemberRole(int clanId, UUID playerUuid) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT role FROM clan_members WHERE clan_id = ? AND player_uuid = ?")) {
            stmt.setInt(1, clanId);
            stmt.setObject(2, playerUuid);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("role");
                }
                return null;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar role: " + e.getMessage());
            return null;
        }
    }

    /**
     * Cria invite para o clan
     */
    public boolean invitePlayer(int clanId, UUID invitedUuid, UUID inviterUuid) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO clan_invites (clan_id, invited_uuid, inviter_uuid, expires_at) " +
                "VALUES (?, ?, ?, ?)")) {
            stmt.setInt(1, clanId);
            stmt.setObject(2, invitedUuid);
            stmt.setObject(3, inviterUuid);
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis() + 300000)); // 5 minutos

            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao criar invite: " + e.getMessage());
            return false;
        }
    }

    /**
     * Busca invites pendentes do player
     */
    public List<Integer> getPendingInvites(UUID playerUuid) {
        List<Integer> invites = new ArrayList<>();
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT clan_id FROM clan_invites " +
                "WHERE invited_uuid = ? AND expires_at > CURRENT_TIMESTAMP")) {
            stmt.setObject(1, playerUuid);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    invites.add(rs.getInt("clan_id"));
                }
            }
            return invites;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar invites: " + e.getMessage());
            return invites;
        }
    }

    /**
     * Aceita invite e adiciona ao clan
     */
    public boolean acceptInvite(int clanId, UUID playerUuid) {
        // Verificar se invite existe e não expirou
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT id FROM clan_invites " +
                "WHERE clan_id = ? AND invited_uuid = ? AND expires_at > CURRENT_TIMESTAMP")) {
                checkStmt.setInt(1, clanId);
                checkStmt.setObject(2, playerUuid);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        return false; // Invite não existe ou expirou
                    }
                }
            }

            // Adicionar membro
            if (!addMember(clanId, playerUuid, "MEMBER")) {
                return false;
            }

            // Remover invite
            try (PreparedStatement deleteStmt = conn.prepareStatement(
                "DELETE FROM clan_invites WHERE clan_id = ? AND invited_uuid = ?")) {
                deleteStmt.setInt(1, clanId);
                deleteStmt.setObject(2, playerUuid);
                deleteStmt.executeUpdate();
            }

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao aceitar invite: " + e.getMessage());
            return false;
        }
    }

    /**
     * Mapeia ResultSet para ClanData
     */
    private ClanData mapResultSetToClanData(ResultSet rs) throws SQLException {
        ClanData clan = new ClanData();
        clan.setId(rs.getInt("id"));
        clan.setName(rs.getString("name"));
        clan.setTag(rs.getString("tag"));
        clan.setTagClean(rs.getString("tag_clean"));
        clan.setLeaderUuid((UUID) rs.getObject("leader_uuid"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            clan.setCreatedAt(new Date(createdAt.getTime()));
        }
        clan.setDescription(rs.getString("description"));
        Long discordChannelId = rs.getLong("discord_channel_id");
        if (rs.wasNull()) {
            discordChannelId = null;
        }
        clan.setDiscordChannelId(discordChannelId);
        Long discordRoleId = rs.getLong("discord_role_id");
        if (rs.wasNull()) {
            discordRoleId = null;
        }
        clan.setDiscordRoleId(discordRoleId);

        // Home (pode ser null)
        String homeWorld = rs.getString("home_world");
        clan.setHomeWorld(homeWorld);
        if (homeWorld != null) {
            double homeX = rs.getDouble("home_x");
            if (!rs.wasNull()) {
                clan.setHomeX(homeX);
            }
            double homeY = rs.getDouble("home_y");
            if (!rs.wasNull()) {
                clan.setHomeY(homeY);
            }
            double homeZ = rs.getDouble("home_z");
            if (!rs.wasNull()) {
                clan.setHomeZ(homeZ);
            }
        }

        // Pontos e bloqueio
        int points = rs.getInt("points");
        if (!rs.wasNull()) {
            clan.setPoints(points);
        }
        int eventWinsCount = rs.getInt("event_wins_count");
        if (!rs.wasNull()) {
            clan.setEventWinsCount(eventWinsCount);
        }
        boolean blockedFromEvents = rs.getBoolean("blocked_from_events");
        if (!rs.wasNull()) {
            clan.setBlockedFromEvents(blockedFromEvents);
        }

        // Shield
        int shieldStart = rs.getInt("shield_start_hour");
        if (!rs.wasNull()) {
            clan.setShieldStartHour(shieldStart);
        }
        int shieldEnd = rs.getInt("shield_end_hour");
        if (!rs.wasNull()) {
            clan.setShieldEndHour(shieldEnd);
        }

        return clan;
    }

    /**
     * Obtém saldo do clan bank (em centavos)
     */
    public long getClanBalance(int clanId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT balance_cents FROM clan_bank WHERE clan_id = ?")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("balance_cents");
                }
                // Se não existe, criar registro com saldo 0
                createClanBank(clanId);
                return 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar saldo do clan: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Cria registro de clan bank se não existir
     */
    private void createClanBank(int clanId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO clan_bank (clan_id, balance_cents) VALUES (?, 0) " +
                "ON CONFLICT (clan_id) DO NOTHING")) {
            stmt.setInt(1, clanId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao criar clan bank: " + e.getMessage());
        }
    }

    /**
     * Adiciona saldo ao clan bank (em centavos)
     */
    public boolean addClanBalance(int clanId, long cents) {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Criar se não existir
            createClanBank(clanId);

            // Atualizar saldo
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE clan_bank SET balance_cents = balance_cents + ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE clan_id = ?")) {
                stmt.setLong(1, cents);
                stmt.setInt(2, clanId);
                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao adicionar saldo ao clan: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove saldo do clan bank (em centavos)
     */
    public boolean removeClanBalance(int clanId, long cents) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "UPDATE clan_bank SET balance_cents = balance_cents - ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE clan_id = ? AND balance_cents >= ?")) {
            stmt.setLong(1, cents);
            stmt.setInt(2, clanId);
            stmt.setLong(3, cents);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao remover saldo do clan: " + e.getMessage());
            return false;
        }
    }

    /**
     * Atualiza role de um membro
     */
    public boolean updateMemberRole(int clanId, UUID playerUuid, String newRole) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "UPDATE clan_members SET role = ? WHERE clan_id = ? AND player_uuid = ?")) {
            stmt.setString(1, newRole);
            stmt.setInt(2, clanId);
            stmt.setObject(3, playerUuid);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao atualizar role: " + e.getMessage());
            return false;
        }
    }

    /**
     * Transfere liderança do clan
     */
    public boolean transferLeadership(int clanId, UUID newLeaderUuid) {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Verificar se novo leader é membro do clan
            String currentRole = getMemberRole(clanId, newLeaderUuid);
            if (currentRole == null) {
                return false; // Não é membro
            }

            // Atualizar leader_uuid na tabela clans
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE clans SET leader_uuid = ? WHERE id = ?")) {
                stmt.setObject(1, newLeaderUuid);
                stmt.setInt(2, clanId);
                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    return false;
                }
            }

            // Atualizar roles: novo leader vira LEADER, antigo leader vira MEMBER
            ClanData clan = getClan(clanId);
            if (clan == null) {
                return false;
            }

            // Novo leader vira LEADER
            updateMemberRole(clanId, newLeaderUuid, "LEADER");

            // Antigo leader vira MEMBER (se ainda for membro)
            if (!newLeaderUuid.equals(clan.getLeaderUuid())) {
                updateMemberRole(clanId, clan.getLeaderUuid(), "MEMBER");
            }

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao transferir liderança: " + e.getMessage());
            return false;
        }
    }

    /**
     * Define home do clan
     */
    public boolean setClanHome(int clanId, String world, double x, double y, double z) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "UPDATE clans SET home_world = ?, home_x = ?, home_y = ?, home_z = ? WHERE id = ?")) {
            stmt.setString(1, world);
            stmt.setDouble(2, x);
            stmt.setDouble(3, y);
            stmt.setDouble(4, z);
            stmt.setInt(5, clanId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao definir home do clan: " + e.getMessage());
            return false;
        }
    }

    /**
     * Define horário do shield do clan
     */
    public boolean setClanShield(int clanId, int startHour, int endHour) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "UPDATE clans SET shield_start_hour = ?, shield_end_hour = ? WHERE id = ?")) {
            stmt.setInt(1, startHour);
            stmt.setInt(2, endHour);
            stmt.setInt(3, clanId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao definir shield do clan: " + e.getMessage());
            return false;
        }
    }

    /**
     * Calcula ELO médio do clan em tempo real (com cache TTL 30s)
     * Grug Brain: Query direta, cache para performance
     */
    public double getClanAverageElo(int clanId) {
        // Verificar cache
        ClansPlugin.EloCache cache = plugin.getEloCache(clanId);
        if (cache != null) {
            return cache.getAvgElo();
        }

        // Calcular ELO médio
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT COALESCE(AVG(u.elo), 0) as avg_elo " +
                "FROM clan_members cm " +
                "JOIN users u ON cm.player_uuid = u.uuid " +
                "WHERE cm.clan_id = ?")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    double avgElo = rs.getDouble("avg_elo");
                    // Salvar no cache
                    plugin.setEloCache(clanId, new ClansPlugin.EloCache(avgElo));
                    return avgElo;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao calcular ELO médio do clan: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Adiciona vitória em evento ao clan
     */
    public boolean addEventWin(int clanId, String eventName, int points, UUID awardedBy) {
        ClanData clan = getClan(clanId);
        if (clan == null) {
            return false;
        }
        if (isClanBlockedFromEvents(clanId)) {
            plugin.getLogger().warning("Clan " + clan.getName() + " está bloqueado de eventos!");
            return false;
        }

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Inserir em clan_event_wins
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO clan_event_wins (clan_id, event_name, points_awarded, awarded_by, created_at) " +
                "VALUES (?, ?, ?, ?, ?)")) {
                stmt.setInt(1, clanId);
                stmt.setString(2, eventName);
                stmt.setInt(3, points);
                if (awardedBy != null) {
                    stmt.setObject(4, awardedBy);
                } else {
                    stmt.setNull(4, Types.OTHER);
                }
                stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            }

            // Atualizar points e event_wins_count na tabela clans
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE clans SET points = points + ?, event_wins_count = event_wins_count + 1 WHERE id = ?")) {
                stmt.setInt(1, points);
                stmt.setInt(2, clanId);
                stmt.executeUpdate();
            }

            // Invalidar cache de ranking
            plugin.invalidateTopCache("points");

            // Notificar Discord async
            /*
            if (plugin.getDiscordIntegration() != null) {
                ClanData updatedClan = getClan(clanId);
                if (updatedClan != null) {
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getDiscordIntegration().notifyDiscordClanWin(updatedClan, eventName, points);
                    });
                }
            }
            */

            plugin.getLogger().info("[CLAN WIN] Clan " + clan.getName() + " ganhou evento " + eventName + " (+" + points + " pontos)");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao adicionar vitória: " + e.getMessage());
            return false;
        }
    }

    /**
     * Adiciona pontos manualmente ao clan
     */
    public boolean addPoints(int clanId, int points, UUID addedBy) {
        ClanData clan = getClan(clanId);
        if (clan == null) {
            return false;
        }

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Atualizar points
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE clans SET points = points + ? WHERE id = ?")) {
                stmt.setInt(1, points);
                stmt.setInt(2, clanId);
                stmt.executeUpdate();
            }

            // Inserir registro em clan_event_wins com points_awarded positivo (histórico)
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO clan_event_wins (clan_id, event_name, points_awarded, awarded_by, created_at) " +
                "VALUES (?, ?, ?, ?, ?)")) {
                stmt.setInt(1, clanId);
                stmt.setString(2, "MANUAL");
                stmt.setInt(3, points);
                if (addedBy != null) {
                    stmt.setObject(4, addedBy);
                } else {
                    stmt.setNull(4, Types.OTHER);
                }
                stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            }

            // Invalidar cache de ranking
            plugin.invalidateTopCache("points");

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao adicionar pontos: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove pontos manualmente do clan (pode ficar negativo)
     */
    public boolean removePoints(int clanId, int points, UUID removedBy) {
        ClanData clan = getClan(clanId);
        if (clan == null) {
            return false;
        }

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Atualizar points (pode ficar negativo)
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE clans SET points = points - ? WHERE id = ?")) {
                stmt.setInt(1, points);
                stmt.setInt(2, clanId);
                stmt.executeUpdate();
            }

            // Inserir registro em clan_event_wins com points_awarded negativo (histórico)
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO clan_event_wins (clan_id, event_name, points_awarded, awarded_by, created_at) " +
                "VALUES (?, ?, ?, ?, ?)")) {
                stmt.setInt(1, clanId);
                stmt.setString(2, "MANUAL_REMOVAL");
                stmt.setInt(3, -points);
                if (removedBy != null) {
                    stmt.setObject(4, removedBy);
                } else {
                    stmt.setNull(4, Types.OTHER);
                }
                stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            }

            // Invalidar cache de ranking
            plugin.invalidateTopCache("points");

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao remover pontos: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtém pontos do clan
     */
    public int getClanPoints(int clanId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT points FROM clans WHERE id = ?")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("points");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar pontos: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Obtém histórico de vitórias do clan
     */
    public List<EventWinRecord> getClanEventWins(int clanId, int limit) {
        List<EventWinRecord> wins = new ArrayList<>();
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM clan_event_wins WHERE clan_id = ? ORDER BY created_at DESC LIMIT ?")) {
            stmt.setInt(1, clanId);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    EventWinRecord win = new EventWinRecord();
                    win.setId(rs.getInt("id"));
                    win.setClanId(rs.getInt("clan_id"));
                    win.setEventName(rs.getString("event_name"));
                    win.setPointsAwarded(rs.getInt("points_awarded"));
                    UUID awardedBy = (UUID) rs.getObject("awarded_by");
                    win.setAwardedBy(awardedBy);
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) {
                        win.setCreatedAt(new Date(createdAt.getTime()));
                    }
                    wins.add(win);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar vitórias: " + e.getMessage());
        }
        return wins;
    }

    /**
     * Obtém contagem de vitórias por evento
     */
    public Map<String, Integer> getClanEventWinsByEvent(int clanId) {
        Map<String, Integer> winsByEvent = new HashMap<>();
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT event_name, COUNT(*) as count FROM clan_event_wins WHERE clan_id = ? GROUP BY event_name")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    winsByEvent.put(rs.getString("event_name"), rs.getInt("count"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar vitórias por evento: " + e.getMessage());
        }
        return winsByEvent;
    }

    /**
     * Verifica se clan está bloqueado de eventos
     */
    public boolean isClanBlockedFromEvents(int clanId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT blocked_from_events FROM clans WHERE id = ?")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("blocked_from_events");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao verificar bloqueio: " + e.getMessage());
        }
        return false;
    }

    /**
     * Adiciona alerta ao clan
     */
    public boolean addAlert(int clanId, UUID playerUuid, String alertType, String message, UUID createdBy, String punishmentId) {
        ClanData clan = getClan(clanId);
        if (clan == null) {
            return false;
        }

        // Se playerUuid não null, validar que é membro do clan
        if (playerUuid != null) {
            String role = getMemberRole(clanId, playerUuid);
            if (role == null) {
                return false; // Não é membro
            }
        }

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Inserir em clan_alerts
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO clan_alerts (clan_id, player_uuid, alert_type, punishment_id, message, created_by, created_at, removed) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)")) {
                stmt.setInt(1, clanId);
                if (playerUuid != null) {
                    stmt.setObject(2, playerUuid);
                } else {
                    stmt.setNull(2, Types.OTHER);
                }
                stmt.setString(3, alertType);
                if (punishmentId != null) {
                    stmt.setString(4, punishmentId);
                } else {
                    stmt.setNull(4, Types.VARCHAR);
                }
                stmt.setString(5, message);
                if (createdBy != null) {
                    stmt.setObject(6, createdBy);
                } else {
                    stmt.setNull(6, Types.OTHER);
                }
                stmt.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            }

            // Aplicar penalidades
            applyAlertPenalties(clanId);

            // Invalidar cache de alertas
            plugin.invalidateAlertCache(clanId);

            // Notificar Discord async
            /*
            if (plugin.getDiscordIntegration() != null) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getDiscordIntegration().notifyDiscordAlert(clan, alertType, message, playerUuid);
                });
            }
            */

            // Notificar membros online do clan (thread principal via scheduler)
            if (plugin.getConfig().getBoolean("alerts.auto-notify-online", true)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    List<ClanMember> members = getMembers(clanId);
                    String alertMsg = "§c[ALERTA] §7" + alertType + " §7- §e" + message;
                    for (ClanMember member : members) {
                        org.bukkit.entity.Player player = Bukkit.getPlayer(member.getPlayerUuid());
                        if (player != null && player.isOnline()) {
                            player.sendMessage(alertMsg);
                        }
                    }
                });
            }

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao adicionar alerta: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove alerta (se punição foi revisada)
     */
    public boolean removeAlert(int alertId, UUID removedBy) {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Buscar clan_id do alerta
            int clanId = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT clan_id FROM clan_alerts WHERE id = ?")) {
                stmt.setInt(1, alertId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        clanId = rs.getInt("clan_id");
                    } else {
                        return false; // Alerta não existe
                    }
                }
            }

            // Atualizar alerta
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE clan_alerts SET removed = TRUE, removed_at = CURRENT_TIMESTAMP, removed_by = ? WHERE id = ?")) {
                if (removedBy != null) {
                    stmt.setObject(1, removedBy);
                } else {
                    stmt.setNull(1, Types.OTHER);
                }
                stmt.setInt(2, alertId);
                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    return false;
                }
            }

            // Recalcular penalidades
            applyAlertPenalties(clanId);

            // Invalidar cache de alertas
            plugin.invalidateAlertCache(clanId);

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao remover alerta: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtém alertas do clan
     */
    public List<ClanAlert> getClanAlerts(int clanId, boolean includeRemoved) {
        List<ClanAlert> alerts = new ArrayList<>();
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM clan_alerts WHERE clan_id = ? AND (removed = ? OR ?) ORDER BY removed ASC, created_at DESC")) {
            stmt.setInt(1, clanId);
            stmt.setBoolean(2, false);
            stmt.setBoolean(3, includeRemoved);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ClanAlert alert = new ClanAlert();
                    alert.setId(rs.getInt("id"));
                    alert.setClanId(rs.getInt("clan_id"));
                    UUID playerUuid = (UUID) rs.getObject("player_uuid");
                    alert.setPlayerUuid(playerUuid);
                    alert.setAlertType(rs.getString("alert_type"));
                    alert.setPunishmentId(rs.getString("punishment_id"));
                    alert.setMessage(rs.getString("message"));
                    UUID createdBy = (UUID) rs.getObject("created_by");
                    alert.setCreatedBy(createdBy);
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) {
                        alert.setCreatedAt(new Date(createdAt.getTime()));
                    }
                    alert.setRemoved(rs.getBoolean("removed"));
                    Timestamp removedAt = rs.getTimestamp("removed_at");
                    if (removedAt != null) {
                        alert.setRemovedAt(new Date(removedAt.getTime()));
                    }
                    UUID removedBy = (UUID) rs.getObject("removed_by");
                    alert.setRemovedBy(removedBy);
                    alerts.add(alert);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar alertas: " + e.getMessage());
        }
        return alerts;
    }

    /**
     * Obtém contagem de alertas do clan (não removidos)
     */
    public int getClanAlertCount(int clanId) {
        // Verificar cache
        ClansPlugin.AlertCache cache = plugin.getAlertCache(clanId);
        if (cache != null) {
            return cache.getCount();
        }

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) as count FROM clan_alerts WHERE clan_id = ? AND removed = FALSE")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    // Salvar no cache
                    plugin.setAlertCache(clanId, new ClansPlugin.AlertCache(count));
                    return count;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao contar alertas: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Obtém contagem de punições do clan (não removidos)
     */
    public int getClanPunishmentCount(int clanId) {
        // Verificar cache
        ClansPlugin.AlertCache cache = plugin.getAlertCache(clanId);
        if (cache != null) {
            return cache.getPunishmentCount();
        }

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) as count FROM clan_alerts WHERE clan_id = ? AND removed = FALSE AND alert_type IN ('PUNISHMENT', 'BAN')")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    // Salvar no cache (usar mesmo cache, mas com contagem de punições)
                    int alertCount = getClanAlertCount(clanId);
                    plugin.setAlertCache(clanId, new ClansPlugin.AlertCache(alertCount, count));
                    return count;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao contar punições: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Aplica penalidades baseadas em alertas
     */
    private void applyAlertPenalties(int clanId) {
        // 1. Obter contagem de alertas (removed = FALSE)
        int alertCount = getClanAlertCount(clanId);

        // 2. Verificar config: alerts.points-removal-threshold e alerts.points-removal-amount
        int threshold = plugin.getConfig().getInt("alerts.points-removal-threshold", 3);
        int amount = plugin.getConfig().getInt("alerts.points-removal-amount", 10);

        // 3. Calcular quantos pontos remover (a cada threshold, remove amount)
        int timesThreshold = alertCount / threshold;
        int pointsToRemove = timesThreshold * amount;

        // 4. Calcular pontos atuais e pontos que deveriam ter (baseado em histórico)
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Obter pontos históricos (soma de todos os pontos_awarded)
            int historicalPoints = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COALESCE(SUM(points_awarded), 0) as total FROM clan_event_wins WHERE clan_id = ?")) {
                stmt.setInt(1, clanId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        historicalPoints = rs.getInt("total");
                    }
                }
            }

            // Obter pontos atuais
            int currentPoints = getClanPoints(clanId);

            // Pontos esperados = histórico - penalidades
            int expectedPoints = historicalPoints - pointsToRemove;

            // Se pontos atuais != pontos esperados, atualizar
            if (currentPoints != expectedPoints) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE clans SET points = ? WHERE id = ?")) {
                    stmt.setInt(1, expectedPoints);
                    stmt.setInt(2, clanId);
                    stmt.executeUpdate();
                }
                // Invalidar cache de ranking
                plugin.invalidateTopCache("points");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao aplicar penalidades: " + e.getMessage());
        }
    }
}
