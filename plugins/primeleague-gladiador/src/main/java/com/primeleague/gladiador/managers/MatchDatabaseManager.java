package com.primeleague.gladiador.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.sql.*;

/**
 * Gerenciador de persistência de matches no banco
 * Grug Brain: Salva matches async, usa JSONB para participant_clans
 */
public class MatchDatabaseManager {

    private final GladiadorPlugin plugin;

    public MatchDatabaseManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Salva match no banco de dados
     * Grug Brain: Query direta, async para não bloquear
     */
    public void saveMatch(GladiadorMatch match, Integer winnerClanId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO gladiador_matches (arena_id, winner_clan_id, participant_clans, " +
                         "total_kills, duration_seconds, started_at, ended_at) " +
                         "VALUES (?, ?, ?::jsonb, ?, ?, ?, CURRENT_TIMESTAMP)")) {

                    stmt.setInt(1, match.getArena().getId());

                    if (winnerClanId != null) {
                        stmt.setInt(2, winnerClanId);
                    } else {
                        stmt.setNull(2, Types.INTEGER);
                    }

                    JSONArray participantClans = buildParticipantClansJson(match.getClanEntries());
                    stmt.setString(3, participantClans.toJSONString());

                    int totalKills = match.getClanEntries().stream()
                        .mapToInt(ClanEntry::getKills)
                        .sum();
                    stmt.setInt(4, totalKills);

                    long durationSeconds = (System.currentTimeMillis() - match.getStartTime()) / 1000;
                    stmt.setInt(5, (int) durationSeconds);
                    stmt.setTimestamp(6, new Timestamp(match.getStartTime()));

                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao salvar match no banco: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Constrói JSON de participant clans
     * Grug Brain: JSON simples, direto
     * Nota: json-simple não é genérico, mas é compatível com 1.8.8
     */
    @SuppressWarnings("unchecked")
    private JSONArray buildParticipantClansJson(java.util.Collection<ClanEntry> entries) {
        JSONArray participantClans = new JSONArray();
        for (ClanEntry entry : entries) {
            JSONObject clanJson = new JSONObject();
            clanJson.put("clan_id", entry.getClanId());
            clanJson.put("tag", entry.getClanTag());
            clanJson.put("kills", entry.getKills());
            clanJson.put("deaths", entry.getDeaths());
            participantClans.add(clanJson);
        }
        return participantClans;
    }
}





