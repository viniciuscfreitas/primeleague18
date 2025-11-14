package com.primeleague.clans.listeners;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.models.ClanData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Listener de Clan Chat - Envia mensagens apenas para membros do clan
 * Grug Brain: Lógica inline, prefixo configurável (default: !)
 */
public class ClanChatListener implements Listener {

    private final ClansPlugin plugin;
    private final String chatPrefix;

    public ClanChatListener(ClansPlugin plugin) {
        this.plugin = plugin;
        // Prefixo para clan chat (default: !)
        this.chatPrefix = plugin.getConfig().getString("clan-chat.prefix", "!");
    }

    /**
     * AsyncPlayerChatEvent - Escuta com prioridade LOWEST para interceptar antes de outros plugins
     * Grug Brain: Verifica prefixo, cancela evento original, envia apenas para membros online
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Verificar se mensagem começa com prefixo do clan chat
        if (!message.startsWith(chatPrefix)) {
            return; // Não é clan chat, deixar passar
        }

        // Remover prefixo da mensagem
        String clanMessage = message.substring(chatPrefix.length()).trim();
        if (clanMessage.isEmpty()) {
            return; // Mensagem vazia após remover prefixo
        }

        // Verificar se player tem clan
        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            // Player não tem clan, enviar mensagem de erro
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + "Você não está em um clan!");
            });
            event.setCancelled(true);
            return;
        }

        // Cancelar evento original
        event.setCancelled(true);

        // Formato: §7[§e{tag}§7] §b{player}: §7{message}
        String formattedMessage = ChatColor.GRAY + "[" + ChatColor.YELLOW + clan.getTag() +
            ChatColor.GRAY + "] " + ChatColor.AQUA + player.getName() +
            ChatColor.WHITE + ": " + ChatColor.GRAY + clanMessage;

        // Enviar apenas para membros online do clan
        // Grug Brain: Usar scheduler para voltar à thread principal (Bukkit API requer thread principal)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                ClanData playerClan = plugin.getClansManager().getClanByMember(onlinePlayer.getUniqueId());
                if (playerClan != null && playerClan.getId() == clan.getId()) {
                    onlinePlayer.sendMessage(formattedMessage);
                }
            }
        });

        // Log no console
        plugin.getLogger().info(String.format("[CLAN CHAT] [%s] %s: %s", clan.getName(), player.getName(), clanMessage));
    }
}

