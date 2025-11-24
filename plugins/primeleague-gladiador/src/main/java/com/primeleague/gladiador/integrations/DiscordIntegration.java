package com.primeleague.gladiador.integrations;

import com.primeleague.gladiador.GladiadorPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integração Discord para Gladiador
 * Grug Brain: Reutiliza padrão do ClansPlugin, rate limiting simples, embed direto via JDA
 */
public class DiscordIntegration {

    private final GladiadorPlugin plugin;
    private final boolean enabled;
    private final String channelId;
    private final int color;
    private final Map<String, Long> rateLimitCache; // Rate limiting para notificações
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public DiscordIntegration(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        this.channelId = plugin.getConfig().getString("discord.channel-id", "");
        this.color = plugin.getConfig().getInt("discord.color", 15844367); // Gold default
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
     * Verifica se integração está habilitada e configurada
     */
    public boolean isEnabled() {
        return enabled && isDiscordEnabled() && channelId != null && !channelId.isEmpty();
    }

    /**
     * Obtém JDA do DiscordPlugin
     * Grug Brain: Usa reflection para evitar NoClassDefFoundError se plugin não estiver no classpath
     */
    private JDA getJDA() {
        if (!isDiscordEnabled()) {
            return null;
        }

        try {
            Plugin discordPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueDiscord");
            if (discordPlugin == null) {
                return null;
            }

            // Usar reflection para evitar NoClassDefFoundError
            Class<?> discordPluginClass = Class.forName("com.primeleague.discord.DiscordPlugin");
            if (!discordPluginClass.isInstance(discordPlugin)) {
                return null;
            }

            java.lang.reflect.Method getDiscordBotMethod = discordPluginClass.getMethod("getDiscordBot");
            Object bot = getDiscordBotMethod.invoke(discordPlugin);
            if (bot == null) {
                return null;
            }

            java.lang.reflect.Method getJDAMethod = bot.getClass().getMethod("getJDA");
            return (JDA) getJDAMethod.invoke(bot);
        } catch (ClassNotFoundException e) {
            // Plugin não está no classpath - ignorar silenciosamente
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao obter JDA: " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtém canal de texto configurado
     */
    private TextChannel getChannel() {
        JDA jda = getJDA();
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) {
            return null;
        }

        try {
            long channelIdLong = Long.parseLong(channelId);
            return jda.getTextChannelById(channelIdLong);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Channel ID inválido: " + channelId);
            return null;
        }
    }

    /**
     * Envia notificação de match iniciado
     */
    public void sendMatchStarted(int clanCount, int playerCount, String arenaName) {
        if (!isEnabled()) return;

        // Rate limiting: 60s TTL
        String rateLimitKey = "match_started";
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
            return; // Rate limited
        }

        TextChannel channel = getChannel();
        if (channel == null) {
            return;
        }

        // Criar embed
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle("⚔ Gladiador Iniciado");
        embed.setDescription(String.format("O evento Gladiador começou na arena **%s**!\n\n" +
            "**%d** clans participando\n" +
            "**%d** jogadores na arena", arenaName, clanCount, playerCount));
        embed.setColor(color);
        embed.setFooter(dateFormat.format(new Date()));

        // Enviar async via queue() (JDA 4.4.0)
        channel.sendMessage(embed.build()).queue(
            (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
            (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
        );
    }

    /**
     * Envia notificação de clan eliminado
     */
    public void sendClanEliminated(String clanTag, int remainingClans) {
        if (!isEnabled()) return;

        // Rate limiting: 60s TTL
        String rateLimitKey = "clan_eliminated_" + clanTag;
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
            return; // Rate limited
        }

        TextChannel channel = getChannel();
        if (channel == null) {
            return;
        }

        // Criar embed
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle("❌ Clan Eliminado");
        embed.setDescription(String.format("O clan **%s** foi eliminado do Gladiador!\n\n" +
            "Restam **%d** clans na arena.", clanTag, remainingClans));
        embed.setColor(0xFF0000); // Vermelho
        embed.setFooter(dateFormat.format(new Date()));

        // Enviar async via queue() (JDA 4.4.0)
        channel.sendMessage(embed.build()).queue(
            (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
            (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
        );
    }

    /**
     * Envia notificação de vitória
     */
    public void sendMatchWon(String winnerTag, int totalKills, long durationSeconds) {
        if (!isEnabled()) return;

        String duration = formatDuration(durationSeconds);

        // Rate limiting: 60s TTL
        String rateLimitKey = "match_won_" + winnerTag;
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
            return; // Rate limited
        }

        TextChannel channel = getChannel();
        if (channel == null) {
            return;
        }

        // Criar embed
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle("🏆 Gladiador Finalizado");
        embed.setDescription(String.format("O clan **%s** venceu o Gladiador!\n\n" +
            "**%d** kills totais\n" +
            "Duração: **%s**", winnerTag, totalKills, duration));
        embed.setColor(0xFFD700); // Gold
        embed.setFooter(dateFormat.format(new Date()));

        // Enviar async via queue() (JDA 4.4.0)
        channel.sendMessage(embed.build()).queue(
            (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
            (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
        );
    }

    /**
     * Formata duração em formato legível
     */
    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    /**
     * Limpa cache de rate limiting (chamado periodicamente)
     */
    public void cleanRateLimitCache() {
        long now = System.currentTimeMillis();
        rateLimitCache.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
    }
}

