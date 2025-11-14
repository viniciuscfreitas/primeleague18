package com.primeleague.punishments.integrations;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.punishments.PunishPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integração Discord para Punições
 * Grug Brain: Reutiliza padrão do ClansPlugin, rate limiting simples, embed direto
 */
public class DiscordIntegration {

    private final PunishPlugin plugin;
    private final Map<String, Long> rateLimitCache; // Rate limiting para notificações
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public DiscordIntegration(PunishPlugin plugin) {
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
     * Obtém canal de moderação (config do PunishPlugin)
     */
    private long getModChannelId() {
        if (!isDiscordEnabled()) {
            return 0;
        }

        return plugin.getConfig().getLong("discord.mod-channel-id", 0);
    }

    /**
     * Obtém nome do player via CoreAPI
     */
    private String getPlayerName(UUID uuid) {
        PlayerData playerData = CoreAPI.getPlayer(uuid);
        if (playerData != null) {
            return playerData.getName();
        }
        return "Desconhecido";
    }

    /**
     * Formata duração em segundos para string legível
     */
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " segundos";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutos";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " horas";
        } else {
            return (seconds / 86400) + " dias";
        }
    }

    /**
     * Notifica Discord sobre punição (com rate limiting)
     * Grug Brain: Rate limiting simples com ConcurrentHashMap
     */
    public void notifyDiscord(UUID playerUuid, String type, String reason, UUID staffUuid, Long durationSeconds) {
        // Verificar se Discord está habilitado
        if (!isDiscordEnabled()) {
            return;
        }

        // Rate limiting (60s TTL)
        String rateLimitKey = playerUuid.toString() + "_" + type;
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
            return; // Rate limited
        }

        // Obter JDA
        JDA jda = getJDA();
        if (jda == null) {
            return;
        }

        // Obter canal de moderação
        long channelId = getModChannelId();
        if (channelId == 0) {
            return; // Canal não configurado
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }

        // Criar embed
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle("🔨 Nova Punição");
        embed.addField("Player", getPlayerName(playerUuid), true);
        embed.addField("Tipo", type.toUpperCase(), true);
        embed.addField("Motivo", reason != null ? reason : "Sem motivo especificado", false);

        if (staffUuid != null) {
            embed.addField("Staff", getPlayerName(staffUuid), true);
        }

        if (durationSeconds != null && durationSeconds > 0) {
            embed.addField("Duração", formatDuration(durationSeconds), true);
        } else {
            embed.addField("Duração", "Permanente", true);
        }

        // Cor: Vermelho para ban, Amarelo para mute, Dourado para warn, Laranja para kick
        int color;
        if (type.equals("ban")) {
            color = 0xFF0000; // Vermelho
        } else if (type.equals("mute")) {
            color = 0xFFAA00; // Amarelo/Laranja
        } else if (type.equals("warn")) {
            color = 0xFFFF00; // Amarelo
        } else if (type.equals("kick")) {
            color = 0xFF8800; // Laranja
        } else {
            color = 0xFFFFFF; // Branco
        }
        embed.setColor(color);
        embed.setFooter(dateFormat.format(new Date()));

        // Enviar async via queue() (JDA 4.4.0)
        channel.sendMessage(embed.build()).queue(
            (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
            (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
        );
    }

    /**
     * Limpa cache de rate limiting (chamado periodicamente)
     */
    public void cleanRateLimitCache() {
        long now = System.currentTimeMillis();
        rateLimitCache.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
    }
}

