package com.primeleague.chat.listeners;

import com.primeleague.chat.ChatPlugin;
import com.primeleague.chat.managers.ChatManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener de chat
 * Grug Brain: Prioridade LOWEST para processar antes de outros plugins
 */
public class ChatListener implements Listener {

    private final ChatPlugin plugin;
    private final ChatManager chatManager;

    public ChatListener(ChatPlugin plugin) {
        this.plugin = plugin;
        this.chatManager = plugin.getChatManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        String message = event.getMessage();

        // Verificar se evento já foi cancelado (ex: player mutado)
        // Grug Brain: Se já foi cancelado (mute), não processar spam/filtros
        if (event.isCancelled()) {
            return; // Já foi cancelado (ex: mute) - não processar mais nada
        }

        // Verificar mute (ANTES de processar spam/filtros)
        // Grug Brain: Verificar mute primeiro para evitar mensagens duplicadas de spam
        org.bukkit.plugin.Plugin punishPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleaguePunishments");
        if (punishPlugin != null && punishPlugin.isEnabled()) {
            // Usar API do PunishPlugin (método estático ou getInstance)
            if (com.primeleague.punishments.PunishPlugin.getInstance().getPunishManager().isMuted(player.getUniqueId())) {
                event.setCancelled(true);
                // Mensagem já enviada pelo PunishListener - não processar spam/filtros
                return;
            }
        }

        // Clan chat - deixar passar (ClansPlugin cuida)
        String clanPrefix = chatManager.getClanChatPrefix();
        if (message.startsWith(clanPrefix)) {
            return; // Deixar ClansPlugin processar
        }

        // Anti-spam (cooldown, duplicatas, comprimento) - só verificar se não está mutado
        if (chatManager.isSpam(player, message)) {
            event.setCancelled(true);
            player.sendMessage(org.bukkit.ChatColor.RED + "Aguarde antes de enviar outra mensagem!");
            return;
        }

        // Filtros (swear, caps, ads)
        if (chatManager.hasSwear(message)) {
            event.setCancelled(true);
            player.sendMessage("§cPalavra proibida detectada!");
            // Punir player (pode adicionar mais lógica aqui)
            plugin.getLogger().warning("Player " + player.getName() + " tentou usar palavra proibida: " + message);
            return;
        }

        if (chatManager.hasExcessiveCaps(message)) {
            event.setCancelled(true);
            player.sendMessage("§cEvite usar muitas letras maiúsculas!");
            return;
        }

        if (chatManager.hasAds(message)) {
            event.setCancelled(true);
            player.sendMessage("§cAnúncios não são permitidos!");
            return;
        }

        // Formatar via PlaceholderAPI
        // Grug Brain: Formato usa %2$s para mensagem (padrão AsyncPlayerChatEvent)
        String format = plugin.getConfig().getString("format",
            "§8[%clans_tag%] §6%player_name% §7» §f%2$s");
        String formatted = chatManager.formatChat(player, format);
        event.setFormat(formatted);

        // Log async
        if (plugin.getConfig().getBoolean("logs.enabled", true)) {
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                chatManager.logChat(player.getUniqueId(), message, "global");
            });
        }

        // Discord relay (opcional)
        // Grug Brain: Chamado no AsyncPlayerChatEvent (thread async) - relayChat() já é thread-safe
        if (plugin.getConfig().getBoolean("discord.relay-enabled", false)) {
            com.primeleague.chat.integrations.DiscordIntegration discordIntegration =
                plugin.getDiscordIntegration();
            if (discordIntegration != null) {
                discordIntegration.relayChat(player, message);
            }
        }
    }

    /**
     * Limpar cache quando player sai
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        chatManager.clearLastSender(event.getPlayer().getUniqueId());
    }
}

