package com.primeleague.league;

import com.primeleague.league.models.LeagueEvent;
import com.primeleague.league.models.RankingEntry;
import org.bukkit.Bukkit;

import java.util.*;

/**
 * API pública estática para TODOS os plugins registrarem eventos
 * Grug Brain: Event sourcing, única fonte da verdade
 * Similar ao CoreAPI, EloAPI, EconomyAPI
 */
public class LeagueAPI {

    private static LeaguePlugin getPlugin() {
        LeaguePlugin plugin = (LeaguePlugin) Bukkit.getPluginManager().getPlugin("PrimeleagueLeague");
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("PrimeleagueLeague não está habilitado");
        }
        return plugin;
    }

    public static boolean isEnabled() {
        LeaguePlugin plugin = (LeaguePlugin) Bukkit.getPluginManager().getPlugin("PrimeleagueLeague");
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Registra um evento (qualquer plugin pode usar)
     * Grug Brain: Método único, genérico, thread-safe
     *
     * @param category Categoria ("GLADIADOR", "X1", "PVP", "KOTH", "ECONOMY", "ELO", etc)
     * @param action Ação ("WIN", "KILL", "DEATH", "MVP", "CAPTURE", "BAN", "PURCHASE", "ELO_CHANGE", etc)
     * @param entityType Tipo da entidade ("CLAN" ou "PLAYER")
     * @param entityId ID da entidade (UUID string para player, INT string para clan)
     * @param value Valor do evento (+50 pontos, +1 kill, -500 penalidade, etc)
     * @param reason Motivo ("1º lugar", "Kill PvP", "Compra loja", etc)
     * @param metadata Dados extras (JSONB)
     * @param createdBy UUID do admin (se manual)
     */
    public static synchronized void recordEvent(
        String category,
        String action,
        String entityType,
        String entityId,
        double value,
        String reason,
        Map<String, Object> metadata,
        UUID createdBy
    ) {
        if (!isEnabled()) {
            return;
        }

        LeaguePlugin plugin = getPlugin();
        com.primeleague.league.models.Season season = plugin.getLeagueManager().getCurrentSeason();
        if (season == null) {
            plugin.getLogger().warning("Nenhuma temporada ativa. Evento não registrado: " + category + "/" + action);
            return;
        }

        int seasonId = season.getId();

        // Converter metadata para JSON
        String metadataJson = null;
        if (metadata != null && !metadata.isEmpty()) {
            org.json.simple.JSONObject json = new org.json.simple.JSONObject();
            json.putAll(metadata);
            metadataJson = json.toJSONString();
        }

        // Inserir evento no banco (async)
        // Trigger atualiza league_summary automaticamente
        plugin.getEventManager().insertEventAsync(seasonId, entityType, entityId, category, action, value, reason, metadataJson, createdBy);

        // Invalidar cache Caffeine
        plugin.getCacheManager().invalidatePoints(seasonId, entityType, entityId);
    }

    // ========== WRAPPERS CONVENIENTES ==========

    /**
     * Registra kill PvP
     */
    public static synchronized void recordKill(UUID killerUuid, UUID victimUuid, String weapon) {
        if (!isEnabled()) {
            return;
        }

        Map<String, Object> killMetadata = new HashMap<>();
        killMetadata.put("victim", victimUuid.toString());
        killMetadata.put("weapon", weapon);

        recordEvent("PVP", "KILL", "PLAYER", killerUuid.toString(), 1, "Kill PvP",
            killMetadata, null);

        Map<String, Object> deathMetadata = new HashMap<>();
        deathMetadata.put("killer", killerUuid.toString());
        deathMetadata.put("weapon", weapon);

        recordEvent("PVP", "DEATH", "PLAYER", victimUuid.toString(), 1, "Death PvP",
            deathMetadata, null);
    }

    /**
     * Registra mudança de ELO
     */
    public static synchronized void recordEloChange(UUID playerUuid, int oldElo, int newElo, String reason) {
        if (!isEnabled()) {
            return;
        }

        int change = newElo - oldElo;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("old_elo", oldElo);
        metadata.put("new_elo", newElo);

        recordEvent("ELO", "ELO_CHANGE", "PLAYER", playerUuid.toString(), change, reason,
            metadata, null);
    }

    /**
     * Registra transação de dinheiro
     */
    public static synchronized void recordMoneyTransaction(UUID playerUuid, long cents, String type, String reason) {
        if (!isEnabled()) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", type);

        recordEvent("ECONOMY", "TRANSACTION", "PLAYER", playerUuid.toString(), cents, reason,
            metadata, null);
    }

    /**
     * Registra vitória em Gladiador
     */
    public static synchronized void recordGladiadorWin(int clanId, UUID matchId, int position, int kills, int deaths) {
        if (!isEnabled()) {
            return;
        }

        LeaguePlugin plugin = getPlugin();
        int points = calculateGladiadorPoints(plugin, position);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("match_id", matchId.toString());
        metadata.put("position", position);
        metadata.put("kills", kills);
        metadata.put("deaths", deaths);

        recordEvent("GLADIADOR", "WIN", "CLAN", String.valueOf(clanId), points, position + "º lugar",
            metadata, null);
    }

    /**
     * Registra vitória em X1
     */
    public static synchronized void recordX1Win(UUID winnerUuid, UUID loserUuid, UUID matchId) {
        if (!isEnabled()) {
            return;
        }

        Map<String, Object> winMetadata = new HashMap<>();
        winMetadata.put("opponent", loserUuid.toString());
        winMetadata.put("match_id", matchId.toString());

        recordEvent("X1", "WIN", "PLAYER", winnerUuid.toString(), 1, "Vitória X1",
            winMetadata, null);

        Map<String, Object> lossMetadata = new HashMap<>();
        lossMetadata.put("opponent", winnerUuid.toString());
        lossMetadata.put("match_id", matchId.toString());

        recordEvent("X1", "LOSS", "PLAYER", loserUuid.toString(), 1, "Derrota X1",
            lossMetadata, null);
    }

    /**
     * Adiciona pontos (wrapper para recordEvent)
     */
    public static synchronized void awardPoints(String entityType, String entityId, int points, String reason, Map<String, Object> metadata) {
        if (!isEnabled()) {
            return;
        }

        recordEvent("POINTS", "AWARD", entityType, entityId, points, reason, metadata, null);
    }

    /**
     * Penaliza (wrapper para recordEvent com valor negativo)
     */
    public static synchronized void penalize(String entityType, String entityId, int penaltyPoints, String reason, UUID adminUuid) {
        if (!isEnabled()) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("admin", adminUuid.toString());
        metadata.put("penalty", true);

        recordEvent("PUNISH", "PENALTY", entityType, entityId, -penaltyPoints, reason,
            metadata, adminUuid);
    }

    // ========== QUERIES E RANKINGS ==========

    /**
     * Obtém pontos totais de uma entidade (com cache Caffeine)
     */
    public static int getPoints(String entityType, String entityId) {
        if (!isEnabled()) {
            return 0;
        }

        LeaguePlugin plugin = getPlugin();
        com.primeleague.league.models.Season season = plugin.getLeagueManager().getCurrentSeason();
        if (season == null) {
            return 0;
        }

        return plugin.getCacheManager().getPoints(season.getId(), entityType, entityId);
    }

    /**
     * Obtém kills totais de um player
     */
    public static int getKills(UUID playerUuid) {
        if (!isEnabled()) {
            return 0;
        }

        LeaguePlugin plugin = getPlugin();
        com.primeleague.league.models.Season season = plugin.getLeagueManager().getCurrentSeason();
        if (season == null) {
            return 0;
        }

        return plugin.getEventManager().countEvents(season.getId(), "PLAYER", playerUuid.toString(), "PVP", "KILL");
    }

    /**
     * Obtém deaths totais de um player
     */
    public static int getDeaths(UUID playerUuid) {
        if (!isEnabled()) {
            return 0;
        }

        LeaguePlugin plugin = getPlugin();
        com.primeleague.league.models.Season season = plugin.getLeagueManager().getCurrentSeason();
        if (season == null) {
            return 0;
        }

        return plugin.getEventManager().countEvents(season.getId(), "PLAYER", playerUuid.toString(), "PVP", "DEATH");
    }

    /**
     * Obtém killstreak atual de um player
     * Grug Brain: Calcula ON-THE-FLY baseado em eventos consecutivos
     */
    public static int getKillstreak(UUID playerUuid) {
        if (!isEnabled()) {
            return 0;
        }

        // TODO: Implementar cálculo de killstreak baseado em eventos consecutivos
        // Por enquanto, retorna 0 (pode usar CoreAPI.getPlayer().getKillstreak() temporariamente)
        return 0;
    }

    /**
     * Obtém best killstreak de um player
     */
    public static int getBestKillstreak(UUID playerUuid) {
        if (!isEnabled()) {
            return 0;
        }

        // TODO: Implementar cálculo de best killstreak baseado em eventos
        // Por enquanto, retorna 0 (pode usar CoreAPI.getPlayer().getBestKillstreak() temporariamente)
        return 0;
    }

    /**
     * Obtém vitórias em Gladiador de um clan
     */
    public static int getGladiadorWins(int clanId) {
        if (!isEnabled()) {
            return 0;
        }

        LeaguePlugin plugin = getPlugin();
        com.primeleague.league.models.Season season = plugin.getLeagueManager().getCurrentSeason();
        if (season == null) {
            return 0;
        }

        return plugin.getEventManager().countEvents(season.getId(), "CLAN", String.valueOf(clanId), "GLADIADOR", "WIN");
    }

    /**
     * Ranking de clans por pontos
     */
    public static List<RankingEntry> getClanRankingByPoints(int limit) {
        if (!isEnabled()) {
            return new ArrayList<>();
        }

        LeaguePlugin plugin = getPlugin();
        com.primeleague.league.models.Season season = plugin.getLeagueManager().getCurrentSeason();
        if (season == null) {
            return new ArrayList<>();
        }

        // Usar cache agregado se disponível
        return plugin.getEventManager().queryRankingFromSummary(season.getId(), "CLAN", "points", limit);
    }

    /**
     * Ranking de players por kills
     */
    public static List<RankingEntry> getPlayerRankingByKills(int limit) {
        if (!isEnabled()) {
            return new ArrayList<>();
        }

        LeaguePlugin plugin = getPlugin();
        com.primeleague.league.models.Season season = plugin.getLeagueManager().getCurrentSeason();
        if (season == null) {
            return new ArrayList<>();
        }

        // Calcular ON-THE-FLY (não tem cache para kills)
        return plugin.getEventManager().queryRankingFromEvents(season.getId(), "PLAYER", "PVP", "KILL", limit);
    }

    /**
     * Ranking customizado (múltiplas métricas)
     * Exemplo: kills × 10 + glad_wins × 50 + money/1000
     * TODO: Implementar parser de fórmula
     */
    public static List<RankingEntry> getCustomRanking(String formula, int limit) {
        // TODO: Parse formula e calcular ON-THE-FLY
        // Por enquanto, retorna lista vazia
        return new ArrayList<>();
    }

    /**
     * Histórico completo de eventos (filtros opcionais)
     */
    public static List<LeagueEvent> getEventHistory(
        String entityType,
        String entityId,
        String category,
        String action,
        int limit
    ) {
        if (!isEnabled()) {
            return new ArrayList<>();
        }

        LeaguePlugin plugin = getPlugin();
        com.primeleague.league.models.Season season = plugin.getLeagueManager().getCurrentSeason();
        if (season == null) {
            return new ArrayList<>();
        }

        return plugin.getEventManager().getEventHistory(season.getId(), entityType, entityId, category, action, limit);
    }

    // ========== RESET E PREMIAÇÃO ==========

    /**
     * Reseta temporada (deleta eventos da temporada)
     * MELHORIA: Com partição, DELETE vira DROP PARTITION (instantâneo mesmo com 50M linhas)
     */
    public static synchronized void resetSeason(int seasonId) {
        if (!isEnabled()) {
            return;
        }

        LeaguePlugin plugin = getPlugin();
        plugin.getResetManager().resetSeason(seasonId);
    }

    /**
     * Soft-delete evento (staff pode desfazer eventos errados)
     * MELHORIA: Não quebra histórico, apenas marca como deletado
     */
    public static synchronized void softDeleteEvent(long eventId, UUID adminUuid) {
        if (!isEnabled()) {
            return;
        }

        LeaguePlugin plugin = getPlugin();
        com.primeleague.league.models.Season season = plugin.getLeagueManager().getCurrentSeason();
        if (season == null) {
            return;
        }

        // Soft-delete via EventManager (async)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = com.primeleague.core.CoreAPI.getDatabase().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE league_events SET is_deleted = true WHERE id = ? AND season_id = ?")) {
                stmt.setLong(1, eventId);
                stmt.setInt(2, season.getId());
                stmt.executeUpdate();

                // Reverter pontos no summary (se for evento de pontos)
                // Nota: Trigger não atualiza se is_deleted = true, então precisamos reverter manualmente
                try (java.sql.PreparedStatement revertStmt = conn.prepareStatement(
                    "UPDATE league_summary SET points = points - " +
                    "(SELECT value FROM league_events WHERE id = ? AND season_id = ?) " +
                    "WHERE season_id = ? AND entity_type = " +
                    "(SELECT entity_type FROM league_events WHERE id = ? AND season_id = ?) " +
                    "AND entity_id = (SELECT entity_id FROM league_events WHERE id = ? AND season_id = ?)")) {
                    revertStmt.setLong(1, eventId);
                    revertStmt.setInt(2, season.getId());
                    revertStmt.setInt(3, season.getId());
                    revertStmt.setLong(4, eventId);
                    revertStmt.setInt(5, season.getId());
                    revertStmt.setLong(6, eventId);
                    revertStmt.setInt(7, season.getId());
                    revertStmt.executeUpdate();
                }

                // Invalidar cache
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getCacheManager().invalidateAll();
                });
            } catch (Exception e) {
                plugin.getLogger().severe("Erro ao soft-delete evento: " + e.getMessage());
            }
        });
    }

    /**
     * Reseta eventos específicos (admin)
     */
    public static synchronized void resetEvents(
        String entityType,
        String entityId,
        String category,
        String action,
        UUID adminUuid
    ) {
        if (!isEnabled()) {
            return;
        }

        LeaguePlugin plugin = getPlugin();
        com.primeleague.league.models.Season season = plugin.getLeagueManager().getCurrentSeason();
        if (season == null) {
            return;
        }

        // Soft-delete eventos específicos (async)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = com.primeleague.core.CoreAPI.getDatabase().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE league_events SET is_deleted = true " +
                     "WHERE season_id = ? AND entity_type = ? AND entity_id = ? " +
                     "AND category = ? AND action = ? AND is_deleted = false")) {
                stmt.setInt(1, season.getId());
                stmt.setString(2, entityType);
                stmt.setString(3, entityId);
                stmt.setString(4, category);
                stmt.setString(5, action);
                int deleted = stmt.executeUpdate();

                // Registrar evento de auditoria
                if (deleted > 0) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("deleted_count", deleted);
                    metadata.put("category", category);
                    metadata.put("action", action);
                    recordEvent("AUDIT", "RESET_EVENTS", entityType, entityId, 0,
                        "Reset de eventos específicos", metadata, adminUuid);
                }

                // Invalidar cache
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getCacheManager().invalidatePoints(season.getId(), entityType, entityId);
                });
            } catch (Exception e) {
                plugin.getLogger().severe("Erro ao resetar eventos: " + e.getMessage());
            }
        });
    }

    /**
     * Calcula e distribui prêmios no fim da temporada
     */
    public static synchronized void calculateAndDistributeRewards(int seasonId) {
        if (!isEnabled()) {
            return;
        }

        LeaguePlugin plugin = getPlugin();
        plugin.getRewardsManager().calculateAndDistributeRewards(seasonId);
    }

    // ========== MÉTODOS PRIVADOS ==========

    /**
     * Calcula pontos do Gladiador por posição
     */
    private static int calculateGladiadorPoints(LeaguePlugin plugin, int position) {
        // Ler do config.yml
        String path = "points.gladiador-clan." + position;
        int points = plugin.getConfig().getInt(path, 0);

        if (points == 0) {
            // Tentar range (ex: "11-20")
            if (position >= 11 && position <= 20) {
                points = plugin.getConfig().getInt("points.gladiador-clan.11-20", 5);
            } else {
                // Default (participação mínima)
                points = plugin.getConfig().getInt("points.gladiador-clan.default", 2);
            }
        }

        return points;
    }
}

