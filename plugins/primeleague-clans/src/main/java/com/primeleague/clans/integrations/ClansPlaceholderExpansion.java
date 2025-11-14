package com.primeleague.clans.integrations;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.models.ClanData;
import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.economy.EconomyAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * PlaceholderAPI Expansion para Clans
 * Grug Brain: Placeholders simples e diretos, queries sync (PlaceholderAPI não suporta async)
 */
public class ClansPlaceholderExpansion extends PlaceholderExpansion {

    private final ClansPlugin plugin;

    public ClansPlaceholderExpansion(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "clans";
    }

    @Override
    public String getAuthor() {
        return "Primeleague";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // Persistir mesmo se PlaceholderAPI recarregar
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        UUID uuid = player.getUniqueId();
        ClanData clan = plugin.getClansManager().getClanByMember(uuid);

        // Se player não tem clan, retornar vazio
        if (clan == null) {
            return "";
        }

        // %clans_name% - Nome do clan
        if (identifier.equals("name")) {
            return clan.getName();
        }

        // %clans_tag% - Tag colorida do clan
        if (identifier.equals("tag")) {
            return clan.getTag();
        }

        // %clans_elo% - ELO médio do clan (usa cache TTL 30s)
        if (identifier.equals("elo")) {
            try {
                double avgElo = plugin.getClansManager().getClanAverageElo(clan.getId());
                return String.format("%.0f", avgElo);
            } catch (Exception e) {
                return "";
            }
        }

        // %clans_kills% - Kills totais do clan (query sync)
        if (identifier.equals("kills")) {
            try {
                int totalKills = getTotalKills(clan.getId());
                return String.valueOf(totalKills);
            } catch (Exception e) {
                return "";
            }
        }

        // %clans_members_online% - Membros online
        if (identifier.equals("members_online")) {
            int online = getOnlineMembers(clan.getId());
            return String.valueOf(online);
        }

        // %clans_balance% - Saldo do clan bank (se Economy habilitado)
        if (identifier.equals("balance")) {
            if (!EconomyAPI.isEnabled()) {
                return "";
            }
            long balanceCents = plugin.getClansManager().getClanBalance(clan.getId());
            double balance = balanceCents / 100.0;
            return String.format("%.2f", balance);
        }

        // %clans_points% - Pontos do clan
        if (identifier.equals("points")) {
            try {
                int points = plugin.getClansManager().getClanPoints(clan.getId());
                return String.valueOf(points);
            } catch (Exception e) {
                return "";
            }
        }

        // %clans_event_wins% - Número de vitórias em eventos
        if (identifier.equals("event_wins")) {
            try {
                Integer eventWinsCount = clan.getEventWinsCount();
                if (eventWinsCount != null) {
                    return String.valueOf(eventWinsCount);
                }
                return "0";
            } catch (Exception e) {
                return "";
            }
        }

        return null; // Placeholder desconhecido
    }

    /**
     * Calcula ELO médio do clan (query sync)
     * Grug Brain: Query direta, try-with-resources
     */
    private double getAverageElo(int clanId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT COALESCE(AVG(u.elo), 0) as avg_elo " +
                "FROM clan_members cm " +
                "JOIN users u ON cm.player_uuid = u.uuid " +
                "WHERE cm.clan_id = ?")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("avg_elo");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao calcular ELO médio do clan: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Calcula kills totais do clan (query sync)
     * Grug Brain: Query direta, try-with-resources
     */
    private int getTotalKills(int clanId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT COALESCE(SUM(u.kills), 0) as total_kills " +
                "FROM clan_members cm " +
                "JOIN users u ON cm.player_uuid = u.uuid " +
                "WHERE cm.clan_id = ?")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_kills");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao calcular kills totais do clan: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Conta membros online do clan
     * Grug Brain: Contagem em tempo real, sem query
     */
    private int getOnlineMembers(int clanId) {
        int count = 0;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            ClanData playerClan = plugin.getClansManager().getClanByMember(onlinePlayer.getUniqueId());
            if (playerClan != null && playerClan.getId() == clanId) {
                count++;
            }
        }
        return count;
    }
}

