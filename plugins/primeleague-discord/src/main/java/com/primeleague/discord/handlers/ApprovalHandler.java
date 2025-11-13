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
        if (content.startsWith("/register ") || content.startsWith("/link ") || content.equals("/status")) {
            String channelInfo = channel.getType().name() + (channel.getName() != null ? ":" + channel.getName() : "");
            plugin.getLogger().info("Comando Discord: " + content + " (Canal: " + channelInfo + ")");
        }

        // Comando /register <código> <minecraft_username>
        if (content.startsWith("/register ")) {
            String[] parts = content.substring(10).trim().split("\\s+", 2);
            if (parts.length != 2) {
                channel.sendMessage("❌ Uso: `/register <código> <minecraft_username>`").queue();
                return;
            }

            String code = parts[0].trim();
            String minecraftName = parts[1].trim();
            long discordId = event.getAuthor().getIdLong();

            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        // Validar username (método utilitário inline)
                        if (!isValidMinecraftUsername(minecraftName)) {
                            channel.sendMessage("❌ Username inválido. Use 3-16 caracteres alfanuméricos (letras, números e _).").queue();
                            return;
                        }

                        // Verificar se username já existe
                        PlayerData existingByName = CoreAPI.getPlayerByName(minecraftName);
                        if (existingByName != null) {
                            channel.sendMessage("❌ Username já registrado. Use outro nome.").queue();
                            return;
                        }

                        // Validar código via AuthPlugin (cast direto, sem reflexão)
                        org.bukkit.plugin.Plugin authPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueAuth");
                        if (authPlugin == null || !authPlugin.isEnabled()) {
                            channel.sendMessage("❌ Sistema de autenticação não disponível. Tente novamente mais tarde.").queue();
                            return;
                        }

                        CodeValidator codeValidator;
                        if (!(authPlugin instanceof com.primeleague.auth.AuthPlugin)) {
                            plugin.getLogger().warning("AuthPlugin não é instância correta. Verifique dependências.");
                            channel.sendMessage("❌ Erro de configuração. Contate administrador.").queue();
                            return;
                        }
                        codeValidator = ((com.primeleague.auth.AuthPlugin) authPlugin).getCodeValidator();

                        // Validar se código é válido (está na lista de códigos permitidos)
                        if (!codeValidator.isValid(code)) {
                            channel.sendMessage("❌ Código inválido. Verifique e tente novamente.").queue();
                            return;
                        }

                        // Validar se código já foi usado (cada código = pagamento = uso único)
                        if (CoreAPI.isAccessCodeUsed(code)) {
                            channel.sendMessage("❌ Este código já foi usado. Cada código só pode ser usado uma vez.").queue();
                            return;
                        }

                        // Gerar UUID (sem IP ainda, será preenchido no primeiro login)
                        UUID uuid = UUIDGenerator.generate(minecraftName, null);

                        // Criar PlayerData sem IP (ip_hash = null)
                        PlayerData data = new PlayerData(uuid, minecraftName, null);
                        data.setAccessCode(code);
                        data.setDiscordId(discordId);
                        // access_expires_at será definido pelo payment plugin

                        // Salvar conta
                        CoreAPI.savePlayer(data);

                        // Responder confirmação
                        channel.sendMessage("✅ **Conta criada com sucesso!**\n\n" +
                            "• Jogador: `" + minecraftName + "`\n" +
                            "• Discord vinculado: `" + discordId + "`\n\n" +
                            "Entre no servidor agora! O IP será registrado automaticamente no primeiro login.").queue();

                        plugin.getLogger().info("Conta criada via Discord: " + minecraftName + " (Discord: " + discordId + ", UUID: " + uuid + ")");

                    } catch (Exception e) {
                        plugin.getLogger().severe("Erro ao processar registro via Discord: " + e.getMessage());
                        e.printStackTrace();
                        String errorMsg = "❌ Erro ao processar registro.";
                        if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                            errorMsg += " Username ou Discord já está em uso.";
                        }
                        channel.sendMessage(errorMsg).queue();
                    }
                }
            }.runTaskAsynchronously(plugin);
            return;
        }

        // Comando /link <minecraft_name>
        if (content.startsWith("/link ")) {
            String minecraftName = content.substring(6).trim();
            long discordId = event.getAuthor().getIdLong();

            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        PlayerData data = CoreAPI.getPlayerByName(minecraftName);
                        if (data == null) {
                            channel.sendMessage("❌ Conta Minecraft não encontrada: `" + minecraftName + "`").queue();
                            return;
                        }

                        // Vincular Discord
                        data.setDiscordId(discordId);
                        CoreAPI.savePlayer(data);

                        channel.sendMessage("✅ Conta vinculada com sucesso! Discord ID: `" + discordId + "`").queue();
                    } catch (Exception e) {
                        plugin.getLogger().severe("Erro ao vincular conta: " + e.getMessage());
                        channel.sendMessage("❌ Erro ao vincular conta. Tente novamente.").queue();
                    }
                }
            }.runTaskAsynchronously(plugin);
        }

        // Comando /status
        if (content.equals("/status")) {
            long discordId = event.getAuthor().getIdLong();

            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        PlayerData data = CoreAPI.getPlayerByDiscordId(discordId);
                        if (data == null) {
                            channel.sendMessage("❌ Nenhuma conta Minecraft vinculada ao seu Discord.").queue();
                            return;
                        }

                        StringBuilder status = new StringBuilder();
                        status.append("📊 **Status da Conta**\n\n");
                        status.append("• Jogador: `").append(data.getName()).append("`\n");
                        status.append("• ELO: `").append(data.getElo()).append("`\n");
                        status.append("• Money: `").append(data.getMoney()).append("`\n");

                        if (data.getAccessExpiresAt() != null) {
                            status.append("• Acesso expira em: `").append(data.getAccessExpiresAt()).append("`\n");
                        } else {
                            status.append("• Acesso: `Expirado`\n");
                        }

                        channel.sendMessage(status.toString()).queue();
                    } catch (Exception e) {
                        plugin.getLogger().severe("Erro ao buscar status: " + e.getMessage());
                        channel.sendMessage("❌ Erro ao buscar status. Tente novamente.").queue();
                    }
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    /**
     * Handler para Slash Commands
     * Grug Brain: API nativa JDA 4.4.0, sem reflection
     */
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        if (event.getName().equals("register")) {
            event.deferReply(true).queue(); // Resposta privada

            String code = event.getOption("codigo") != null ?
                event.getOption("codigo").getAsString() : null;
            String minecraftName = event.getOption("username") != null ?
                event.getOption("username").getAsString() : null;
            long discordId = event.getUser().getIdLong();

            if (code == null || minecraftName == null) {
                event.getHook().sendMessage("❌ Uso: `/register codigo:<código> username:<minecraft_username>`").queue();
                return;
            }

            // Reutilizar lógica de registro (async)
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {

                        // Validar username
                        if (!isValidMinecraftUsername(minecraftName)) {
                            event.getHook().sendMessage("❌ Username inválido. Use 3-16 caracteres alfanuméricos (letras, números e _).").queue();
                            return;
                        }

                        // Verificar se username já existe
                        PlayerData existingByName = CoreAPI.getPlayerByName(minecraftName);
                        if (existingByName != null) {
                            event.getHook().sendMessage("❌ Username já registrado. Use outro nome.").queue();
                            return;
                        }

                        // Validar código via AuthPlugin
                        org.bukkit.plugin.Plugin authPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueAuth");
                        if (authPlugin == null || !authPlugin.isEnabled()) {
                            event.getHook().sendMessage("❌ Sistema de autenticação não disponível. Tente novamente mais tarde.").queue();
                            return;
                        }

                        CodeValidator codeValidator;
                        if (!(authPlugin instanceof com.primeleague.auth.AuthPlugin)) {
                            plugin.getLogger().warning("AuthPlugin não é instância correta. Verifique dependências.");
                            event.getHook().sendMessage("❌ Erro de configuração. Contate administrador.").queue();
                            return;
                        }
                        codeValidator = ((com.primeleague.auth.AuthPlugin) authPlugin).getCodeValidator();

                        // Validar se código é válido
                        if (!codeValidator.isValid(code)) {
                            event.getHook().sendMessage("❌ Código inválido. Verifique e tente novamente.").queue();
                            return;
                        }

                        // Validar se código já foi usado
                        if (CoreAPI.isAccessCodeUsed(code)) {
                            event.getHook().sendMessage("❌ Este código já foi usado. Cada código só pode ser usado uma vez.").queue();
                            return;
                        }

                        // Gerar UUID e criar conta
                        UUID uuid = UUIDGenerator.generate(minecraftName, null);
                        PlayerData data = new PlayerData(uuid, minecraftName, null);
                        data.setAccessCode(code);
                        data.setDiscordId(discordId);

                        // Salvar conta
                        CoreAPI.savePlayer(data);

                        // Responder confirmação
                        event.getHook().sendMessage("✅ **Conta criada com sucesso!**\n\n" +
                            "• Jogador: `" + minecraftName + "`\n" +
                            "• Discord vinculado: `" + discordId + "`\n\n" +
                            "Entre no servidor agora! O IP será registrado automaticamente no primeiro login.").queue();

                        plugin.getLogger().info("Conta criada via Discord Slash Command: " + minecraftName + " (Discord: " + discordId + ", UUID: " + uuid + ")");

                    } catch (Exception e) {
                        plugin.getLogger().severe("Erro ao processar registro via Slash Command: " + e.getMessage());
                        e.printStackTrace();
                        String errorMsg = "❌ Erro ao processar registro.";
                        if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                            errorMsg += " Username já está em uso.";
                        }
                        event.getHook().sendMessage(errorMsg).queue();
                    }
                }
            }.runTaskAsynchronously(plugin);
        }
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

