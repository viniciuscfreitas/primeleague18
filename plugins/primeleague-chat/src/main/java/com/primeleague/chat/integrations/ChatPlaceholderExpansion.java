package com.primeleague.chat.integrations;

import com.primeleague.chat.ChatPlugin;
import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI Expansion para Chat
 * Grug Brain: Placeholders simples e diretos, queries sync (PlaceholderAPI não suporta async)
 * Cache opcional 60s para performance
 */
public class ChatPlaceholderExpansion extends PlaceholderExpansion {

    private final ChatPlugin plugin;

    // Cache simples para evitar queries excessivas (TTL 60s)
    private final ConcurrentHashMap<UUID, CachedPlayerData> dataCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 60_000; // 60 segundos

    public ChatPlaceholderExpansion(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "chat";
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
        PlayerData data = getCachedPlayerData(uuid);

        if (data == null) {
            return "";
        }

        // Placeholders de chat
        switch (identifier) {
            case "elo":
                return String.valueOf(data.getElo());

            case "kills":
                return String.valueOf(data.getKills());

            case "deaths":
                return String.valueOf(data.getDeaths());

            case "kdr":
                int deaths = data.getDeaths();
                if (deaths == 0) {
                    return String.valueOf(data.getKills());
                }
                double kdr = (double) data.getKills() / deaths;
                return String.format("%.2f", kdr);

            case "killstreak":
                return String.valueOf(data.getKillstreak());

            case "best_killstreak":
                return String.valueOf(data.getBestKillstreak());

            default:
                return null; // Placeholder desconhecido
        }
    }

    /**
     * Obtém PlayerData com cache (TTL 60s)
     * Grug Brain: Cache simples para reduzir queries
     */
    private PlayerData getCachedPlayerData(UUID uuid) {
        CachedPlayerData cached = dataCache.get(uuid);

        // Verificar se cache ainda é válido
        if (cached != null && System.currentTimeMillis() - cached.getTimestamp() < CACHE_TTL) {
            return cached.getData();
        }

        // Buscar dados do CoreAPI
        PlayerData data = CoreAPI.getPlayer(uuid);
        if (data != null) {
            // Atualizar cache
            dataCache.put(uuid, new CachedPlayerData(data, System.currentTimeMillis()));
        }

        return data;
    }

    /**
     * Limpa cache quando necessário
     */
    public void clearCache(UUID uuid) {
        dataCache.remove(uuid);
    }

    /**
     * Limpa todo o cache
     */
    public void clearAllCache() {
        dataCache.clear();
    }

    /**
     * Classe interna para cache
     */
    private static class CachedPlayerData {
        private final PlayerData data;
        private final long timestamp;

        public CachedPlayerData(PlayerData data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }

        public PlayerData getData() {
            return data;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}

