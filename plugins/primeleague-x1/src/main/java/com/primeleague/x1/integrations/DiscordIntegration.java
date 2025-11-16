package com.primeleague.x1.integrations;

import com.primeleague.discord.DiscordPlugin;
import com.primeleague.discord.bot.DiscordBot;
import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Match;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;

/**
 * Integração com Discord para webhooks
 * Grug Brain: Verifica disponibilidade antes de usar
 */
public class DiscordIntegration {

    private final X1Plugin plugin;
    private final boolean enabled;
    private final String channelId;

    public DiscordIntegration(X1Plugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("integrations.discord.enabled", true);
        this.channelId = plugin.getConfig().getString("integrations.discord.webhook-channel-id", "");
    }

    /**
     * Envia webhook quando match termina
     */
    public void sendMatchEndWebhook(Match match, String winnerName, String loserName, int eloChange) {
        if (!enabled || channelId == null || channelId.isEmpty()) {
            return;
        }

        // Verificar se Discord está disponível
        DiscordPlugin discordPlugin = DiscordPlugin.getInstance();
        if (discordPlugin == null || discordPlugin.getDiscordBot() == null) {
            return;
        }

        DiscordBot discordBot = discordPlugin.getDiscordBot();
        net.dv8tion.jda.api.JDA jda = discordBot != null ? discordBot.getJDA() : null;
        if (jda == null || jda.getStatus() != net.dv8tion.jda.api.JDA.Status.CONNECTED) {
            return;
        }

        // Capturar JDA antes de entrar no runnable
        final net.dv8tion.jda.api.JDA finalJda = jda;
        
        // Enviar em thread async
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    MessageChannel channel = finalJda.getTextChannelById(channelId);
                    if (channel == null) {
                        plugin.getLogger().warning("Canal Discord não encontrado: " + channelId);
                        return;
                    }

                    // Criar embed
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("Vitória no x1");
                    embed.setColor(Color.GREEN);
                    embed.addField("Vencedor", winnerName, true);
                    embed.addField("Perdedor", loserName, true);
                    embed.addField("Kit", match.getKit(), true);
                    embed.addField("Modo", match.isRanked() ? "Ranked" : "Unranked", true);
                    
                    if (match.isRanked() && eloChange != 0) {
                        String changeStr = eloChange > 0 ? "+" + eloChange : String.valueOf(eloChange);
                        embed.addField("Mudança de ELO", changeStr, true);
                    }

                    // Enviar mensagem
                    channel.sendMessage(embed.build()).queue();
                } catch (Exception e) {
                    plugin.getLogger().warning("Erro ao enviar webhook Discord: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}

