package com.primeleague.auth.listeners;

import com.primeleague.auth.AuthPlugin;
import com.primeleague.auth.utils.CodeValidator;
import com.primeleague.auth.utils.IpHashGenerator;
import com.primeleague.auth.utils.UUIDGenerator;
import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;

/**
 * Listener de autenticação
 * Grug Brain: PlayerLoginEvent síncrono (login é crítico, query rápida com HikariCP)
 */
public class AuthListener implements Listener {

    private final AuthPlugin plugin;
    private final CodeValidator codeValidator;

    public AuthListener(AuthPlugin plugin) {
        this.plugin = plugin;
        this.codeValidator = new CodeValidator(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        String name = event.getPlayer().getName();
        String ip = event.getAddress().getHostAddress();

        // Validação básica
        if (name == null || name.isEmpty() || name.length() > 16) {
            event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            event.setKickMessage("§cNome inválido.");
            return;
        }

        // Bloquear por padrão até validação completa
        // Grug Brain: Login é crítico, query síncrona é aceitável (login é raro)
        try {
            UUID serverUuid = event.getPlayer().getUniqueId(); // UUID do servidor (fonte de verdade)
            PlayerData data = CoreAPI.getPlayerByName(name);

            if (data == null) {
                // Conta não existe - kick com instrução para Discord
                event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                event.setKickMessage("§cConta não encontrada. Registre-se no Discord primeiro:\n§b/discord");
                return;
            }

            // Grug Brain: UUID deve ser compatível com Paper 1.8.8 (gerado apenas com nome)
            // Se UUID diferente (conta antiga criada com IP), atualizar para UUID do servidor (Paper)
            // ON CONFLICT (name) no savePlayer() garante que atualiza corretamente
            if (!serverUuid.equals(data.getUuid())) {
                plugin.getLogger().info("PlayerLoginEvent: UUID diferente detectado para " + name +
                    " (Banco: " + data.getUuid() + ", Servidor: " + serverUuid + ") - Atualizando UUID (conta antiga)");
                data.setUuid(serverUuid);
                // Salvar UUID atualizado - ON CONFLICT (name) garante que atualiza corretamente
                CoreAPI.savePlayer(data);
            }

            // NOVO: Se conta existe mas ip_hash é NULL, permite entrada (primeiro login após registro Discord)
            if (data.getIpHash() == null) {
                event.setResult(PlayerLoginEvent.Result.ALLOWED);
                // IP será capturado no PlayerJoinEvent
                return;
            }

            // Valida IP hash
            String currentIpHash = IpHashGenerator.generate(name, ip);
            String storedIpHash = data.getIpHash();
            if (!storedIpHash.equals(currentIpHash)) {
                // IP diferente - verifica se tem Discord vinculado
                if (data.getDiscordId() == null) {
                    // Sem Discord - bloqueia
                    event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                    event.setKickMessage("§cIP diferente detectado. Vincule sua conta Discord primeiro.");
                    return;
                }

                // Tem Discord - cria pending_login e notifica Discord Bot (async para não bloquear)
                final UUID playerUuid = data.getUuid();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        createPendingLogin(playerUuid, name, ip, currentIpHash);
                    }
                }.runTaskAsynchronously(plugin);

                event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                event.setKickMessage("§cIP diferente detectado. Aguarde aprovação no Discord.");
                return;
            }

            // Valida acesso (mensalidade)
            if (data.getAccessExpiresAt() != null &&
                data.getAccessExpiresAt().before(new Date())) {
                event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                event.setKickMessage("§cAcesso expirado. Renove sua assinatura.");
                return;
            }

            // Tudo OK - permite entrada
            event.setResult(PlayerLoginEvent.Result.ALLOWED);

        } catch (Exception e) {
            plugin.getLogger().severe("Erro ao validar login: " + e.getMessage());
            e.printStackTrace();
            event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            event.setKickMessage("§cErro interno. Tente novamente.");
        }
    }

    private void createPendingLogin(UUID playerUuid, String name, String ip, String newIpHash) {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO pending_logins (player_uuid, new_ip_hash, new_ip_address, expires_at) " +
                "VALUES (?, ?, ?, NOW() + INTERVAL '5 minutes')");
            stmt.setObject(1, playerUuid);
            stmt.setString(2, newIpHash);
            stmt.setString(3, ip);
            stmt.executeUpdate();

            // Notificar Discord Bot (se estiver habilitado)
            // Grug Brain: Usa casting direto (softdepend) - mais rápido e seguro que reflection
            org.bukkit.plugin.Plugin discordPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueDiscord");
            if (discordPlugin != null && discordPlugin.isEnabled()) {
                try {
                    // Casting direto via instanceof (softdepend) - sem reflection
                    if (discordPlugin instanceof com.primeleague.discord.DiscordPlugin) {
                        com.primeleague.discord.DiscordPlugin dp = (com.primeleague.discord.DiscordPlugin) discordPlugin;
                        com.primeleague.discord.bot.DiscordBot bot = dp.getDiscordBot();
                    if (bot != null) {
                            bot.sendApprovalRequest(playerUuid, name, ip);
                        plugin.getLogger().info("Pending login criado para " + name + " - Discord Bot notificado");
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Erro ao notificar Discord Bot: " + e.getMessage());
                }
            } else {
                plugin.getLogger().info("Pending login criado para " + name + " - Discord Bot não está habilitado");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erro ao criar pending_login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Processa código de acesso e cria conta
     * Chamado por comando ou outro listener
     */
    public boolean processAccessCode(String name, String ip, String code) {
        if (!codeValidator.isValid(code)) {
            return false;
        }

        // Cria conta
        UUID uuid = UUIDGenerator.generate(name, ip);
        String ipHash = IpHashGenerator.generate(name, ip);

        PlayerData data = new PlayerData(uuid, name, ipHash);
        data.setAccessCode(code);
        // access_expires_at será definido pelo payment plugin

        CoreAPI.savePlayer(data);
        return true;
    }

    /**
     * Captura IP no primeiro login após registro via Discord
     * Grug Brain: PlayerJoinEvent é assíncrono, query deve ser async
     * UUID já está correto no PlayerLoginEvent (síncrono) - não precisa atualizar aqui
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        String ip = player.getAddress().getAddress().getHostAddress();

        // Log para debug
        plugin.getLogger().info("PlayerJoinEvent: " + name + " entrou (IP: " + ip + ")");

        // Executar async para não bloquear thread principal
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // UUID já está correto no PlayerLoginEvent (síncrono) - buscar por UUID do servidor
                    PlayerData data = CoreAPI.getPlayer(player.getUniqueId());

                    if (data == null) {
                        plugin.getLogger().warning("PlayerJoinEvent: Conta não encontrada para " + name + " (UUID: " + player.getUniqueId() + ")");
                        return;
                    }

                    // Log estado da conta
                    plugin.getLogger().info("PlayerJoinEvent: " + name + " - ip_hash: " +
                        (data.getIpHash() != null ? "EXISTE" : "NULL") +
                        ", discord_id: " + (data.getDiscordId() != null ? data.getDiscordId() : "NULL"));

                    // Atualizar IP se necessário
                    boolean updated = false;
                    if (data.getIpHash() == null) {
                        // Primeiro login após registro Discord - atualizar IP
                        String ipHash = IpHashGenerator.generate(name, ip);
                        data.setIpHash(ipHash);
                        updated = true;
                        plugin.getLogger().info("IP capturado para " + name + " (IP: " + ip + ", Hash: " + ipHash.substring(0, 8) + "...)");
                    }

                    // Atualizar lastSeenAt sempre
                    data.setLastSeenAt(new Date());

                    // Salvar atualizações (IP, lastSeenAt)
                    CoreAPI.savePlayer(data);

                    if (updated) {
                        // Notificar player (executar na thread principal)
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.sendMessage("§aBem-vindo! IP registrado com sucesso.");
                            }
                        }.runTask(plugin);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Erro ao processar PlayerJoinEvent: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}

