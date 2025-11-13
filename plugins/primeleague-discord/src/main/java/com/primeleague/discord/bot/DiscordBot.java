package com.primeleague.discord.bot;

import com.primeleague.discord.DiscordPlugin;
import com.primeleague.discord.handlers.ApprovalHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.bukkit.scheduler.BukkitRunnable;

import javax.security.auth.login.LoginException;
import java.util.UUID;

/**
 * Gerenciador do Discord Bot (JDA 4.4.0)
 * Grug Brain: Bot simples, handlers inline
 */
public class DiscordBot {

    private final DiscordPlugin plugin;
    private final String token;
    private JDA jda;
    private ApprovalHandler approvalHandler;

    public DiscordBot(DiscordPlugin plugin, String token) {
        this.plugin = plugin;
        this.token = token;
    }

    public boolean initialize() {
        try {
            approvalHandler = new ApprovalHandler(plugin);

            jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
                .addEventListeners(approvalHandler)
                .setAutoReconnect(true)
                .build();

            jda.awaitReady();

            // Registrar Slash Commands
            CommandListUpdateAction commands = jda.updateCommands();
            commands.addCommands(
                new CommandData("register", "Registre sua conta no servidor Minecraft")
                    .addOptions(
                        new OptionData(OptionType.STRING, "codigo", "Seu código de acesso único", true),
                        new OptionData(OptionType.STRING, "username", "Seu username do Minecraft (3-16 caracteres)", true)
                    )
            );
            commands.queue();

            plugin.getLogger().info("Discord Bot conectado: " + jda.getSelfUser().getName());
            plugin.getLogger().info("Slash Commands registrados: /register");
            return true;
        } catch (LoginException | InterruptedException e) {
            plugin.getLogger().severe("Erro ao conectar Discord Bot: " + e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    /**
     * Envia DM para aprovação de novo IP
     */
    public void sendApprovalRequest(UUID playerUuid, String playerName, String newIp) {
        if (jda == null || !jda.getStatus().equals(JDA.Status.CONNECTED)) {
            plugin.getLogger().warning("Discord Bot não está conectado. Não é possível enviar DM.");
            return;
        }

        // Buscar discord_id do player
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    com.primeleague.core.models.PlayerData data =
                        com.primeleague.core.CoreAPI.getPlayer(playerUuid);

                    if (data == null || data.getDiscordId() == null) {
                        plugin.getLogger().warning("Player não tem Discord vinculado: " + playerName);
                        return;
                    }

                    // Enviar DM via ApprovalHandler
                    approvalHandler.sendApprovalDM(data.getDiscordId(), playerName, newIp, playerUuid);
                } catch (Exception e) {
                    plugin.getLogger().severe("Erro ao enviar DM de aprovação: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public JDA getJDA() {
        return jda;
    }
}

