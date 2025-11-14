package com.primeleague.discord.handlers;

import com.primeleague.auth.AuthPlugin;
import com.primeleague.auth.utils.CodeValidator;
import com.primeleague.auth.utils.UUIDGenerator;
import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.discord.DiscordPlugin;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;

/**
 * Handler de aprovação de IP novo via Discord
 * Grug Brain: Handler simples, queries diretas
 */
public class ApprovalHandler extends ListenerAdapter {

    private final DiscordPlugin plugin;

    public ApprovalHandler(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Envia DM com botões de aprovação
     */
    public void sendApprovalDM(long discordId, String playerName, String newIp, UUID playerUuid) {
        User user = plugin.getDiscordBot().getJDA().getUserById(discordId);
        if (user == null) {
            plugin.getLogger().warning("Usuário Discord não encontrado: " + discordId);
            return;
        }

        user.openPrivateChannel().queue(channel -> {
            channel.sendMessage("🔐 **Novo Login Detectado**\n\n" +
                "Tentativa de login da sua conta:\n" +
                "• Jogador: `" + playerName + "`\n" +
                "• IP: `" + newIp + "`\n" +
                "• Data: " + new java.util.Date() + "\n\n" +
                "Aprovar este login?")
                .setActionRows(ActionRow.of(
                    Button.success("approve_login_" + playerUuid.toString(), "✅ Aprovar"),
                    Button.danger("reject_login_" + playerUuid.toString(), "❌ Rejeitar")
                ))
                .queue();
        });
    }

    @Override
    public void onButtonClick(ButtonClickEvent event) {
        // JDA 4.4.0: ButtonClickEvent.getComponentId() retorna o ID do botão
        String buttonId = event.getComponentId();

        if (buttonId.startsWith("approve_login_")) {
            UUID playerUuid = UUID.fromString(buttonId.substring("approve_login_".length()));
            event.deferReply(true).queue();

            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        String newIpHash;

                        // Buscar pending_login (try-with-resources para evitar leak)
                        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                            PreparedStatement stmt = conn.prepareStatement(
                                "SELECT new_ip_hash, new_ip_address FROM pending_logins " +
                                "WHERE player_uuid = ? AND expires_at > NOW() ORDER BY created_at DESC LIMIT 1");
                            stmt.setObject(1, playerUuid);
                            ResultSet rs = stmt.executeQuery();

                            if (!rs.next()) {
                                event.getHook().sendMessage("❌ Solicitação de login expirada ou não encontrada.").queue();
                                return;
                            }

                            newIpHash = rs.getString("new_ip_hash");
                        }

                        // Atualizar IP hash do player
                        PlayerData data = CoreAPI.getPlayer(playerUuid);
                        if (data != null) {
                            data.setIpHash(newIpHash);
                            CoreAPI.savePlayer(data);

                            // Remover pending_login
                            try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                                PreparedStatement deleteStmt = conn.prepareStatement(
                                    "DELETE FROM pending_logins WHERE player_uuid = ?");
                                deleteStmt.setObject(1, playerUuid);
                                deleteStmt.executeUpdate();
                            }

                            event.getHook().sendMessage("✅ Login aprovado! Você pode entrar no servidor agora.").queue();
                        } else {
                            event.getHook().sendMessage("❌ Player não encontrado.").queue();
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Erro ao aprovar login: " + e.getMessage());
                        e.printStackTrace();
                        event.getHook().sendMessage("❌ Erro ao processar aprovação. Tente novamente.").queue();
                    }
                }
            }.runTaskAsynchronously(plugin);
        } else if (buttonId.startsWith("reject_login_")) {
            UUID playerUuid = UUID.fromString(buttonId.substring("reject_login_".length()));
            event.deferReply(true).queue();

            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        // Remover pending_login (try-with-resources)
                        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                            PreparedStatement stmt = conn.prepareStatement(
                                "DELETE FROM pending_logins WHERE player_uuid = ?");
                            stmt.setObject(1, playerUuid);
                            stmt.executeUpdate();
                        }

                        event.getHook().sendMessage("❌ Login rejeitado. A tentativa foi bloqueada.").queue();
                    } catch (Exception e) {
                        plugin.getLogger().severe("Erro ao rejeitar login: " + e.getMessage());
                        event.getHook().sendMessage("❌ Erro ao processar rejeição.").queue();
                    }
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        String content = event.getMessage().getContentRaw();
        MessageChannel channel = event.getChannel();

        // Log apenas para comandos (reduzir spam)
        if (content.startsWith("/registrar ")) {
            String channelInfo = channel.getType().name() + (channel.getName() != null ? ":" + channel.getName() : "");
            plugin.getLogger().info("Comando Discord (texto): " + content + " (Canal: " + channelInfo + ")");
        }

        // Comando /registrar <código> <nome_minecraft> (PT-BR)
        if (content.startsWith("/registrar ")) {
            String[] parts = content.substring(11).trim().split("\\s+", 2);
            if (parts.length != 2) {
                channel.sendMessage("❌ Uso: `/registrar <código> <nome_minecraft>`").queue();
                return;
            }

            String code = parts[0].trim();
            String minecraftName = parts[1].trim();
            long discordId = event.getAuthor().getIdLong();

            // Processar registro (lógica reutilizável)
            processRegistration(code, minecraftName, discordId, new MessageSender() {
                @Override
                public void send(String message) {
                    channel.sendMessage(message).queue();
                }
            }, "Message Command");
            return;
        }

        // Comando /vincular removido - inseguro (sem validação de propriedade)
        // Use /registrar com código de acesso para criar/vincular contas de forma segura
    }

    /**
     * Handler para Slash Commands
     * Grug Brain: API nativa JDA 4.4.0, sem reflection
     */
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        String commandName = event.getName();
        plugin.getLogger().info("Slash Command recebido: /" + commandName + " (User: " + event.getUser().getName() + ")");

        // Handler para /status (deve vir ANTES de /registrar para evitar conflitos)
        if (commandName.equals("status")) {
            plugin.getLogger().info("Processando comando /status para user: " + event.getUser().getName());
            event.deferReply(true).queue(); // Resposta privada
            long discordId = event.getUser().getIdLong();
            processStatus(discordId, new MessageSender() {
                @Override
                public void send(String message) {
                    event.getHook().sendMessage(message).queue();
                }
            }, "Slash Command");
            return; // IMPORTANTE: return para não processar outros comandos
        }

        // Handler para /registrar
        if (commandName.equals("registrar")) {
            event.deferReply(true).queue(); // Resposta privada

            String code = event.getOption("codigo") != null ?
                event.getOption("codigo").getAsString() : null;
            String minecraftName = event.getOption("usuario") != null ?
                event.getOption("usuario").getAsString() : null;
            long discordId = event.getUser().getIdLong();

            if (code == null || minecraftName == null) {
                event.getHook().sendMessage("❌ Uso: `/registrar codigo:<código> usuario:<nome_minecraft>`").queue();
                return;
            }

            // Processar registro (lógica reutilizável)
            processRegistration(code, minecraftName, discordId, new MessageSender() {
                @Override
                public void send(String message) {
                    event.getHook().sendMessage(message).queue();
                }
            }, "Slash Command");
            return; // IMPORTANTE: return para não processar outros comandos
        }

        // Comando desconhecido
        plugin.getLogger().warning("Slash Command desconhecido: /" + commandName);
        event.reply("❌ Comando desconhecido: /" + commandName).setEphemeral(true).queue();
    }

    /**
     * Interface simples para enviar mensagens (MessageChannel ou InteractionHook)
     * Grug Brain: Interface mínima, sem abstrações complexas
     */
    private interface MessageSender {
        void send(String message);
    }

    /**
     * Processa status de contas (lógica reutilizável)
     * Grug Brain: Método único, sem duplicação, mostra todas as contas vinculadas
     */
    private void processStatus(long discordId, MessageSender responder, String source) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    java.util.List<PlayerData> accounts = CoreAPI.getPlayersByDiscordId(discordId);
                    if (accounts.isEmpty()) {
                        responder.send("❌ Nenhuma conta Minecraft vinculada ao seu Discord.");
                        return;
                    }

                    StringBuilder status = new StringBuilder();
                    status.append("📊 **Status das Contas** (").append(accounts.size()).append(" conta").append(accounts.size() > 1 ? "s" : "").append(")\n\n");

                    for (int i = 0; i < accounts.size(); i++) {
                        PlayerData data = accounts.get(i);
                        if (i > 0) {
                            status.append("\n");
                        }
                        status.append("**").append(data.getName()).append("**\n");
                        status.append("• ELO: `").append(data.getElo()).append("`\n");
                        status.append("• Dinheiro: `").append(data.getMoney()).append("`\n");
                        status.append("• Kills: `").append(data.getKills()).append("` | Deaths: `").append(data.getDeaths()).append("`\n");

                        // Verificar se acesso está válido (não null e não expirado)
                        Date now = new Date();
                        if (data.getAccessExpiresAt() != null && data.getAccessExpiresAt().after(now)) {
                            // Acesso válido - mostrar data de expiração
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");
                            status.append("• Acesso válido até: `").append(sdf.format(data.getAccessExpiresAt())).append("`\n");
                        } else {
                            // Acesso expirado ou não definido
                            status.append("• Acesso: `Expirado`\n");
                        }
                    }

                    responder.send(status.toString());
                } catch (Exception e) {
                    plugin.getLogger().severe("Erro ao buscar status via " + source + ": " + e.getMessage());
                    responder.send("❌ Erro ao buscar status. Tente novamente.");
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Processa registro de conta (lógica reutilizável)
     * Grug Brain: Método único, sem duplicação, aceita MessageSender para flexibilidade
     */
    private void processRegistration(String code, String minecraftName, long discordId,
                                     MessageSender responder, String source) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Validar nome
                    if (!isValidMinecraftUsername(minecraftName)) {
                        responder.send("❌ Nome inválido. Use 3-16 caracteres alfanuméricos (letras, números e _).");
                        return;
                    }

                    // Verificar se nome já existe
                    PlayerData existingByName = CoreAPI.getPlayerByName(minecraftName);
                    if (existingByName != null) {
                        responder.send("❌ Nome já registrado. Use outro nome.");
                        return;
                    }

                    // Validar código via AuthPlugin
                    org.bukkit.plugin.Plugin authPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueAuth");
                    if (authPlugin == null || !authPlugin.isEnabled()) {
                        responder.send("❌ Sistema de autenticação indisponível. Tente novamente mais tarde.");
                        return;
                    }

                    CodeValidator codeValidator;
                    if (!(authPlugin instanceof com.primeleague.auth.AuthPlugin)) {
                        plugin.getLogger().warning("AuthPlugin não é instância correta. Verifique dependências.");
                        responder.send("❌ Erro de configuração. Contate administrador.");
                        return;
                    }
                    codeValidator = ((com.primeleague.auth.AuthPlugin) authPlugin).getCodeValidator();

                    // Validar se código é válido
                    if (!codeValidator.isValid(code)) {
                        responder.send("❌ Código inválido. Verifique e tente novamente.");
                        return;
                    }

                    // Validar se código já foi usado
                    if (CoreAPI.isAccessCodeUsed(code)) {
                        responder.send("❌ Este código já foi usado. Cada código só pode ser usado uma vez.");
                        return;
                    }

                    // Gerar UUID e criar conta
                    UUID uuid = UUIDGenerator.generate(minecraftName, null);
                    PlayerData data = new PlayerData(uuid, minecraftName, null);
                    data.setAccessCode(code);
                    data.setDiscordId(discordId);
                    // access_expires_at será definido pelo payment plugin

                    // Salvar conta
                    CoreAPI.savePlayer(data);

                    // Responder confirmação (PT-BR)
                    responder.send("✅ **Conta criada com sucesso!**\n\n" +
                        "• Jogador: `" + minecraftName + "`\n" +
                        "• Discord vinculado: `" + discordId + "`\n\n" +
                        "Entre no servidor agora! O IP será registrado automaticamente no primeiro login.");

                    plugin.getLogger().info("Conta criada via Discord " + source + ": " + minecraftName +
                        " (Discord: " + discordId + ", UUID: " + uuid + ")");

                } catch (Exception e) {
                    plugin.getLogger().severe("Erro ao processar registro via " + source + ": " + e.getMessage());
                    e.printStackTrace();
                    String errorMsg = "❌ Erro ao processar registro.";
                    if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                        errorMsg += " Nome ou Discord já está em uso.";
                    }
                    responder.send(errorMsg);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Valida username do Minecraft
     * Grug Brain: Método utilitário simples, inline
     */
    private boolean isValidMinecraftUsername(String username) {
        if (username == null) return false;
        if (username.length() < 3 || username.length() > 16) return false;
        return username.matches("^[a-zA-Z0-9_]+$");
    }
}

