package com.primeleague.factions.integrations;

import com.primeleague.factions.PrimeFactions;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integração Discord para Factions
 * Grug Brain: Reutiliza padrão do Gladiador, rate limiting simples, embed direto via JDA
 */
public class DiscordIntegration {

    private final PrimeFactions plugin;
    private final boolean enabled;
    private final String channelId;
    private final int color;
    private final Map<String, Long> rateLimitCache; // Rate limiting para notificações
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public DiscordIntegration(PrimeFactions plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        this.channelId = plugin.getConfig().getString("discord.channel-id", "");
        this.color = plugin.getConfig().getInt("discord.color", 3447003); // Azul padrão
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
     * Envia notificação de claim de território
     */
    public void sendTerritoryClaimed(String clanName, String playerName, int chunkX, int chunkZ, String worldName, int totalClaims) {
        if (!isEnabled()) return;

        // Rate limiting: 30s TTL por clan
        String rateLimitKey = "claim_" + clanName;
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 30000) {
            return; // Rate limited
        }

        TextChannel channel = getChannel();
        if (channel == null) {
            return;
        }

        // Criar embed
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle("🏰 Território Conquistado");
        embed.setDescription(String.format("O clan **%s** conquistou um novo território!\n\n" +
            "**Conquistado por:** %s\n" +
            "**Localização:** X: %d, Z: %d (%s)\n" +
            "**Total de territórios:** %d", clanName, playerName, chunkX, chunkZ, worldName, totalClaims));
        embed.setColor(color);
        embed.setFooter(dateFormat.format(new Date()));

        // Enviar async via queue() (JDA 4.4.0)
        channel.sendMessage(embed.build()).queue(
            (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
            (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
        );
    }

    /**
     * Envia notificação de unclaim (abandono) de território
     */
    public void sendTerritoryUnclaimed(String clanName, String playerName, int chunkX, int chunkZ, String worldName, int totalClaims) {
        if (!isEnabled()) return;

        // Rate limiting: 30s TTL por clan
        String rateLimitKey = "unclaim_" + clanName;
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 30000) {
            return; // Rate limited
        }

        TextChannel channel = getChannel();
        if (channel == null) {
            return;
        }

        // Criar embed
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle("🏚️ Território Abandonado");
        embed.setDescription(String.format("O clan **%s** abandonou um território.\n\n" +
            "**Abandonado por:** %s\n" +
            "**Localização:** X: %d, Z: %d (%s)\n" +
            "**Total de territórios:** %d", clanName, playerName, chunkX, chunkZ, worldName, totalClaims));
        embed.setColor(0xFFA500); // Laranja
        embed.setFooter(dateFormat.format(new Date()));

        // Enviar async via queue() (JDA 4.4.0)
        channel.sendMessage(embed.build()).queue(
            (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
            (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
        );
    }

    /**
     * Envia notificação de power crítico (abaixo de 0)
     */
    public void sendPowerCritical(String playerName, double power, String clanName) {
        if (!isEnabled()) return;

        // Rate limiting: 300s (5min) TTL por player
        String rateLimitKey = "power_critical_" + playerName;
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 300000) {
            return; // Rate limited
        }

        TextChannel channel = getChannel();
        if (channel == null) {
            return;
        }

        // Criar embed
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle("⚠️ Power Crítico");
        embed.setDescription(String.format("**%s** está com power crítico!\n\n" +
            "**Power atual:** %.2f\n" +
            "**Clan:** %s\n\n" +
            "⚔️ Cuidado! Power negativo pode afetar a capacidade de claim.", playerName, power, clanName != null ? clanName : "Nenhum"));
        embed.setColor(0xFF0000); // Vermelho
        embed.setFooter(dateFormat.format(new Date()));

        // Enviar async via queue() (JDA 4.4.0)
        channel.sendMessage(embed.build()).queue(
            (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
            (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
        );
    }

    /**
     * Envia notificação de perda de power (morte)
     */
    public void sendPowerLost(String playerName, double powerBefore, double powerAfter, double penalty, String clanName) {
        if (!isEnabled()) return;

        // Rate limiting: 60s TTL por player
        String rateLimitKey = "power_lost_" + playerName;
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
            return; // Rate limited
        }

        // Só notificar se power ficou negativo ou muito baixo
        if (powerAfter >= 0) {
            return;
        }

        TextChannel channel = getChannel();
        if (channel == null) {
            return;
        }

        // Criar embed
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle("💀 Power Perdido");
        embed.setDescription(String.format("**%s** morreu e perdeu power!\n\n" +
            "**Power anterior:** %.2f\n" +
            "**Power atual:** %.2f\n" +
            "**Perda:** %.2f\n" +
            "**Clan:** %s", playerName, powerBefore, powerAfter, penalty, clanName != null ? clanName : "Nenhum"));
        embed.setColor(0x8B0000); // Vermelho escuro
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
        rateLimitCache.entrySet().removeIf(entry -> now - entry.getValue() > 300000); // Remove após 5 minutos
    }
}






