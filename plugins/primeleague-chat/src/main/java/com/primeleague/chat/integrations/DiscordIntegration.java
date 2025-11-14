package com.primeleague.chat.integrations;

import com.primeleague.chat.ChatPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integração Discord para Chat
 * Grug Brain: Casting direto sem reflection, rate limiting simples
 */
public class DiscordIntegration {

    private final ChatPlugin plugin;
    private final Map<UUID, Long> rateLimitCache; // Rate limiting para relay (TTL 60s)

    public DiscordIntegration(ChatPlugin plugin) {
        this.plugin = plugin;
        this.rateLimitCache = new ConcurrentHashMap<>();
    }

    /**
     * Verifica se Discord está habilitado
     */
    public boolean isDiscordEnabled() {
        Plugin discordPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueDiscord");
        return discordPlugin != null && discordPlugin.isEnabled();
    }

    /**
     * Obtém JDA do DiscordPlugin
     * Grug Brain: Usa casting direto (softdepend) - mais rápido e seguro que reflection
     */
    private JDA getJDA() {
        if (!isDiscordEnabled()) {
            return null;
        }

        try {
            // Usar casting direto via getPlugin() (softdepend) - sem reflection
            Plugin discordPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueDiscord");
            if (discordPlugin instanceof com.primeleague.discord.DiscordPlugin) {
                com.primeleague.discord.DiscordPlugin dp = (com.primeleague.discord.DiscordPlugin) discordPlugin;
                com.primeleague.discord.bot.DiscordBot bot = dp.getDiscordBot();
                if (bot != null) {
                    return bot.getJDA();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao obter JDA: " + e.getMessage());
        }
        return null;
    }

    /**
     * Obtém clan tag do player via ClansPlugin (se disponível)
     */
    private String getClanTag(Player player) {
        Plugin clansPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueClans");
        if (clansPlugin == null || !clansPlugin.isEnabled()) {
            return "";
        }

        try {
            // Verificar se é instância do ClansPlugin
            if (clansPlugin instanceof com.primeleague.clans.ClansPlugin) {
                com.primeleague.clans.ClansPlugin cp = (com.primeleague.clans.ClansPlugin) clansPlugin;
                com.primeleague.clans.models.ClanData clan = cp.getClansManager().getClanByMember(player.getUniqueId());
                if (clan != null && clan.getTag() != null && !clan.getTag().isEmpty()) {
                    return "[" + clan.getTag() + "] ";
                }
            }
        } catch (Exception e) {
            // Ignorar erros ao buscar clan tag
        }
        return "";
    }

    /**
     * Relaya mensagem de chat para Discord (opcional)
     * Grug Brain: Rate limiting simples (60s TTL), casting direto sem reflection
     */
    public void relayChat(Player player, String message) {
        if (!isDiscordEnabled() || !plugin.getConfig().getBoolean("discord.relay-enabled", false)) {
            return;
        }

        long channelId = plugin.getConfig().getLong("discord.channel-id", 0);
        if (channelId == 0) {
            return;
        }

        // Rate limiting (60s TTL por player)
        UUID playerUuid = player.getUniqueId();
        Long lastSent = rateLimitCache.get(playerUuid);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
            return; // Rate limited
        }

        // Obter JDA via casting direto
        JDA jda = getJDA();
        if (jda == null) {
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }

        // Formatar mensagem Discord
        String clanTag = getClanTag(player);
        String discordMessage = String.format("**%s%s**: %s", clanTag, player.getName(), message);

        // Enviar async via queue() (JDA 4.4.0)
        channel.sendMessage(discordMessage).queue(
            (success) -> rateLimitCache.put(playerUuid, System.currentTimeMillis()),
            (error) -> plugin.getLogger().warning("Erro ao enviar chat Discord: " + error.getMessage())
        );
    }
}

