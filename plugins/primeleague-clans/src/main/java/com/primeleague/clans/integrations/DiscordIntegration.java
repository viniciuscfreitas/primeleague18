package com.primeleague.clans.integrations;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.models.ClanData;
import com.primeleague.core.CoreAPI;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integração Discord para Clans
 * Grug Brain: Integração simples, verifica se plugin está habilitado antes de usar
 */
public class DiscordIntegration {

    private final ClansPlugin plugin;
    private final Map<String, Long> rateLimitCache; // Rate limiting para notificações

    public DiscordIntegration(ClansPlugin plugin) {
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
     * Obtém Guild ID do config do DiscordPlugin
     */
    private long getGuildId() {
        if (!isDiscordEnabled()) {
            return 0;
        }

        try {
            Plugin discordPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueDiscord");
            if (discordPlugin != null) {
                return discordPlugin.getConfig().getLong("discord.guild-id", 0);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao obter guild-id: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Cria canal de texto e role no Discord para o clan
     * Grug Brain: Async via queue(), salva IDs no banco após criação
     */
    public void createDiscordChannels(ClanData clan) {
        if (!isDiscordEnabled()) {
            return;
        }

        JDA jda = getJDA();
        if (jda == null) {
            return;
        }

        long guildId = getGuildId();
        if (guildId == 0) {
            plugin.getLogger().warning("Guild ID não configurado no DiscordPlugin");
            return;
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Guild não encontrada: " + guildId);
            return;
        }

        // Sanitizar nome do clan para nome de canal (sem espaços, lowercase)
        String channelName = "clan-" + clan.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        if (channelName.length() > 100) {
            channelName = channelName.substring(0, 100);
        }

        // Criar canal de texto
        guild.createTextChannel(channelName).queue(
            (TextChannel channel) -> {
                // Criar role
                guild.createRole()
                    .setName("Clan " + clan.getName())
                    .setMentionable(true)
                    .queue(
                        (Role role) -> {
                            // Salvar IDs no banco
                            saveDiscordIds(clan.getId(), channel.getIdLong(), role.getIdLong());
                            plugin.getLogger().info("Canal e role Discord criados para clan: " + clan.getName());
                        },
                        (error) -> {
                            plugin.getLogger().severe("Erro ao criar role Discord: " + error.getMessage());
                            error.printStackTrace();
                        }
                    );
            },
            (error) -> {
                plugin.getLogger().severe("Erro ao criar canal Discord: " + error.getMessage());
                error.printStackTrace();
            }
        );
    }

    /**
     * Salva IDs do Discord no banco
     */
    private void saveDiscordIds(int clanId, long channelId, long roleId) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = com.primeleague.core.CoreAPI.getDatabase().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE clans SET discord_channel_id = ?, discord_role_id = ? WHERE id = ?")) {
                stmt.setLong(1, channelId);
                stmt.setLong(2, roleId);
                stmt.setInt(3, clanId);
                stmt.executeUpdate();
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("Erro ao salvar IDs do Discord: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Envia notificação Discord (com rate limiting)
     * Grug Brain: Rate limiting simples com ConcurrentHashMap
     */
    public void notifyDiscord(ClanData clan, String title, String description) {
        if (!isDiscordEnabled() || clan.getDiscordChannelId() == null) {
            return;
        }

        // Rate limiting: 60s TTL
        String rateLimitKey = clan.getId() + "_" + title;
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
            return; // Rate limited
        }

        JDA jda = getJDA();
        if (jda == null) {
            return;
        }

        TextChannel channel = jda.getTextChannelById(clan.getDiscordChannelId());
        if (channel == null) {
            return;
        }

        // Criar embed
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle(title);
        embed.setDescription(description);
        embed.setColor(0x00FF00); // Verde

        // Enviar embed (JDA 4.4.0: sendMessage com MessageEmbed)
        channel.sendMessage(embed.build()).queue(
            (success) -> {
                rateLimitCache.put(rateLimitKey, System.currentTimeMillis());
            },
            (error) -> {
                plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage());
            }
        );
    }

    /**
     * Limpa cache de rate limiting (chamado periodicamente)
     */
    public void cleanRateLimitCache() {
        long now = System.currentTimeMillis();
        rateLimitCache.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
    }

    /**
     * Notifica Discord sobre alerta do clan
     */
    public void notifyDiscordAlert(ClanData clan, String alertType, String message, UUID playerUuid) {
        if (!isDiscordEnabled() || clan.getDiscordChannelId() == null) {
            return;
        }

        // Rate limiting
        String rateLimitKey = clan.getId() + "_alert_" + alertType;
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
            return; // Rate limited
        }

        JDA jda = getJDA();
        if (jda == null) {
            return;
        }

        TextChannel channel = jda.getTextChannelById(clan.getDiscordChannelId());
        if (channel == null) {
            return;
        }

        // Embed com título "⚠️ Novo Alerta"
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle("⚠️ Novo Alerta");
        embed.addField("Tipo", alertType, true);
        if (playerUuid != null) {
            // Buscar nome do player via CoreAPI
            com.primeleague.core.models.PlayerData playerData = CoreAPI.getPlayer(playerUuid);
            String playerName = playerData != null ? playerData.getName() : "Desconhecido";
            embed.addField("Membro", playerName, true);
        }
        embed.addField("Mensagem", message, false);
        // Cor: Vermelho para PUNISHMENT/BAN, Amarelo para WARNING
        embed.setColor(alertType.equals("PUNISHMENT") || alertType.equals("BAN") ? 0xFF0000 : 0xFFFF00);
        embed.setFooter(new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));

        // Async via queue() (JDA 4.4.0)
        channel.sendMessage(embed.build()).queue(
            (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
            (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
        );
    }

    /**
     * Notifica Discord sobre vitória em evento do clan
     */
    public void notifyDiscordClanWin(ClanData clan, String eventName, int points) {
        if (!isDiscordEnabled() || clan.getDiscordChannelId() == null) {
            return;
        }

        // Rate limiting
        String rateLimitKey = clan.getId() + "_win_" + eventName;
        Long lastSent = rateLimitCache.get(rateLimitKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < 60000) {
            return; // Rate limited
        }

        JDA jda = getJDA();
        if (jda == null) {
            return;
        }

        TextChannel channel = jda.getTextChannelById(clan.getDiscordChannelId());
        if (channel == null) {
            return;
        }

        // Embed com título "🏆 Vitória em Evento"
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
        embed.setTitle("🏆 Vitória em Evento");
        embed.addField("Evento", eventName, true);
        embed.addField("Pontos ganhos", String.valueOf(points), true);
        int totalPoints = clan.getPoints() != null ? clan.getPoints() : 0;
        embed.addField("Total de pontos", String.valueOf(totalPoints), true);
        embed.setColor(0x00FF00); // Verde
        embed.setFooter(new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));

        // Async via queue() (JDA 4.4.0)
        channel.sendMessage(embed.build()).queue(
            (success) -> rateLimitCache.put(rateLimitKey, System.currentTimeMillis()),
            (error) -> plugin.getLogger().warning("Erro ao enviar notificação Discord: " + error.getMessage())
        );
    }
}

