package com.primeleague.chat.managers;

import com.primeleague.chat.ChatPlugin;
import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de chat
 * Grug Brain: Formatação, anti-spam, filtros inline
 */
public class ChatManager {

    private final ChatPlugin plugin;

    // Cooldown entre mensagens
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

    // Duplicatas (últimas 3 mensagens por player)
    private final Map<UUID, List<String>> recentMessages = new ConcurrentHashMap<>();

    // Cache último remetente para /reply
    private final Map<UUID, UUID> lastSender = new ConcurrentHashMap<>();

    public ChatManager(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Formata mensagem de chat via PlaceholderAPI
     * Grug Brain: Fallback simples se PAPI não disponível
     * Nota: AsyncPlayerChatEvent usa %1$s para player name e %2$s para message
     */
    public String formatChat(Player player, String formatString) {
        // Substituir %message% por %2$s (padrão do AsyncPlayerChatEvent) se presente
        formatString = formatString.replace("%message%", "%2$s");

        // Verificar se PlaceholderAPI está disponível
        Plugin papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            // Fallback simples se PAPI não disponível
            String result = ChatColor.translateAlternateColorCodes('&',
                formatString.replace("%player_name%", player.getName())
                          .replace("%player_displayname%", player.getDisplayName())
                          .replace("%clans_tag%", "")
                          .replace("%chat_elo%", "")
                          .replace("%chat_kills%", "")
                          .replace("%chat_deaths%", "")
                          .replace("%chat_kdr%", ""));
            // Garantir que %2$s está presente (necessário para AsyncPlayerChatEvent)
            if (!result.contains("%2$s") && !result.contains("%1$s")) {
                result = result + " %2$s";
            }
            return result;
        }

        // Usar PlaceholderAPI.setPlaceholders() (thread-safe para AsyncPlayerChatEvent)
        // PlaceholderAPI processa %2$s corretamente (placeholder padrão do evento)
        try {
            String result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatString);

            // Se PlaceholderAPI não substituiu placeholders padrão, fazer fallback manual
            if (result.contains("%player_name%")) {
                result = result.replace("%player_name%", player.getName());
            }
            if (result.contains("%player_displayname%")) {
                result = result.replace("%player_displayname%", player.getDisplayName());
            }

            // Validar formato: garantir que não há % inválidos (exceto %1$s, %2$s, %%, e cores §)
            // Escapar % soltos que não sejam placeholders válidos
            result = sanitizeFormat(result);

            return result;
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao formatar chat com PlaceholderAPI: " + e.getMessage());
            e.printStackTrace();
            // Fallback completo
            String result = ChatColor.translateAlternateColorCodes('&',
                formatString.replace("%player_name%", player.getName())
                          .replace("%player_displayname%", player.getDisplayName())
                          .replace("%clans_tag%", "")
                          .replace("%chat_elo%", "")
                          .replace("%chat_kills%", "")
                          .replace("%chat_deaths%", "")
                          .replace("%chat_kdr%", ""));
            // Garantir que %2$s está presente
            if (!result.contains("%2$s") && !result.contains("%1$s")) {
                result = result + " %2$s";
            }
            return result;
        }
    }

    /**
     * Sanitiza formato do chat para evitar erros de String.format()
     * Grug Brain: Remove % inválidos, mantém apenas %1$s, %2$s e %%
     * Nota: PlaceholderAPI já substituiu seus placeholders, agora só sanitizar placeholders do evento
     */
    private String sanitizeFormat(String format) {
        if (format == null || format.isEmpty()) {
            return "%1$s: %2$s"; // Formato padrão seguro
        }

        // Substituir % que não são placeholders válidos do String.format() por %%
        // Placeholders válidos: %1$s, %2$s, %%, ou % seguido de número+$+[sSdD]
        // Regex mais permissiva: manter qualquer % seguido de dígito+$+letra, ou %%
        String sanitized = format.replaceAll("%(?!\\d+\\$[sSdD]|%)", "%%");

        // Garantir que há pelo menos %2$s (necessário para AsyncPlayerChatEvent mostrar a mensagem)
        if (!sanitized.contains("%2$s")) {
            // Se não tem %2$s, adicionar no final
            sanitized = sanitized + " %2$s";
        }

        return sanitized;
    }

    /**
     * Verifica se mensagem é spam (cooldown, duplicatas)
     */
    public boolean isSpam(Player player, String message) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("anti-spam.cooldown", 2) * 1000;

        // Verificar cooldown
        Long lastTime = lastMessageTime.get(uuid);
        if (lastTime != null && now - lastTime < cooldown) {
            return true;
        }

        // Verificar duplicatas (últimas 3 mensagens)
        if (plugin.getConfig().getBoolean("anti-spam.duplicate-check", true)) {
            if (isDuplicate(player, message)) {
                return true;
            }
        }

        // Verificar comprimento máximo
        int maxLength = plugin.getConfig().getInt("anti-spam.max-length", 256);
        if (message.length() > maxLength) {
            return true;
        }

        lastMessageTime.put(uuid, now);
        return false;
    }

    /**
     * Verifica se mensagem é duplicada (últimas 3)
     */
    private boolean isDuplicate(Player player, String message) {
        UUID uuid = player.getUniqueId();
        List<String> recent = recentMessages.computeIfAbsent(uuid, k -> new ArrayList<>());

        // Verificar se todas as últimas 3 mensagens são iguais
        if (recent.size() >= 3 && recent.stream().allMatch(msg -> msg.equalsIgnoreCase(message))) {
            return true;
        }

        // Adicionar mensagem atual ao início
        recent.add(0, message);
        if (recent.size() > 3) {
            recent.remove(recent.size() - 1);
        }

        return false;
    }

    /**
     * Verifica se mensagem tem palavras proibidas (swear)
     */
    public boolean hasSwear(String message) {
        List<String> blacklist = plugin.getConfig().getStringList("filters.swear-blacklist");
        if (blacklist.isEmpty()) {
            return false;
        }

        String lower = message.toLowerCase();
        return blacklist.stream().anyMatch(lower::contains);
    }

    /**
     * Verifica se mensagem tem muitas letras maiúsculas (caps)
     */
    public boolean hasExcessiveCaps(String message) {
        int capsLimit = plugin.getConfig().getInt("filters.caps-limit", 70);
        if (message.length() < 5) {
            return false; // Ignorar mensagens curtas
        }

        long capsCount = message.chars().filter(Character::isUpperCase).count();
        if (capsCount == 0) {
            return false;
        }

        int capsPercent = (int) ((capsCount * 100) / message.length());
        return capsPercent > capsLimit;
    }

    /**
     * Verifica se mensagem tem anúncios (ads)
     */
    public boolean hasAds(String message) {
        List<String> adsList = plugin.getConfig().getStringList("filters.ads-blacklist");
        if (adsList.isEmpty()) {
            return false;
        }

        String lower = message.toLowerCase();
        return adsList.stream().anyMatch(lower::contains);
    }

    /**
     * Envia mensagem privada entre dois players
     */
    public void sendPrivateMessage(Player from, Player to, String message) {
        // Validar ambos online
        if (!from.isOnline() || !to.isOnline()) {
            return;
        }

        // Formatar PM: quem envia vê "Você » Destinatário", quem recebe vê "Remetente » Você"
        String formattedFrom = ChatColor.GOLD + "Você" + ChatColor.WHITE + " » " + ChatColor.GRAY + to.getName();
        String formattedTo = ChatColor.GOLD + from.getName() + ChatColor.WHITE + " » " + ChatColor.GRAY + "Você";

        from.sendMessage(formattedFrom + ": " + message);
        to.sendMessage(formattedTo + ": " + message);

        // Notificar social spy (admins)
        // Grug Brain: Loop simples, verifica permissão inline
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission("chat.socialspy") && online != from && online != to) {
                // Mostrar para admin: [SocialSpy] Remetente » Destinatário: mensagem
                online.sendMessage(ChatColor.GRAY + "[SocialSpy] " +
                    ChatColor.GOLD + from.getName() + ChatColor.WHITE + " » " +
                    ChatColor.GRAY + to.getName() + ": " + message);
            }
        }

        // Cache para /reply
        lastSender.put(to.getUniqueId(), from.getUniqueId());
        lastSender.put(from.getUniqueId(), to.getUniqueId());

        // Log async
        if (plugin.getConfig().getBoolean("logs.enabled", true)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                logChat(from.getUniqueId(), message, "pm", to.getUniqueId());
                logChat(to.getUniqueId(), message, "pm", from.getUniqueId());
            });
        }
    }

    /**
     * Obtém último remetente para /reply
     */
    public UUID getLastSender(UUID playerUuid) {
        return lastSender.get(playerUuid);
    }

    /**
     * Limpa último remetente (quando player sai)
     */
    public void clearLastSender(UUID playerUuid) {
        lastSender.remove(playerUuid);
        lastMessageTime.remove(playerUuid);
        recentMessages.remove(playerUuid);
    }

    /**
     * Loga mensagem de chat no PostgreSQL (async)
     */
    public void logChat(UUID playerUuid, String message, String channel) {
        logChat(playerUuid, message, channel, null);
    }

    /**
     * Loga mensagem de chat no PostgreSQL (async)
     */
    public void logChat(UUID playerUuid, String message, String channel, UUID targetUuid) {
        if (!plugin.getConfig().getBoolean("logs.enabled", true)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO chat_logs (player_uuid, message, channel, target_uuid, timestamp) " +
                    "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)")) {
                stmt.setObject(1, playerUuid);
                stmt.setString(2, message);
                stmt.setString(3, channel);
                if (targetUuid != null) {
                    stmt.setObject(4, targetUuid);
                } else {
                    stmt.setNull(4, java.sql.Types.OTHER);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Erro ao logar chat: " + e.getMessage());
            }
        });
    }

    /**
     * Obtém prefixo do clan chat (para verificar se deve deixar passar)
     */
    public String getClanChatPrefix() {
        // Verificar se ClansPlugin está habilitado e obter prefixo do config
        Plugin clansPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueClans");
        if (clansPlugin != null && clansPlugin.isEnabled()) {
            return clansPlugin.getConfig().getString("clan-chat.prefix", "!");
        }
        return plugin.getConfig().getString("clan-chat.prefix", "!");
    }

    /**
     * Verifica se PlaceholderAPI está habilitado
     */
    public boolean isPlaceholderAPIEnabled() {
        Plugin papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        return papi != null && papi.isEnabled();
    }
}

