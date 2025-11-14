package com.primeleague.punishments.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.punishments.PunishPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Comando /warn
 * Grug Brain: Comando direto, sempre permanente, sem tempo
 */
public class WarnCommand implements CommandExecutor {

    private final PunishPlugin plugin;

    public WarnCommand(PunishPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 1. Validar permissão
        if (!sender.hasPermission("punish.warn")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        // 2. Validar args
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Uso: /warn <player> [reason]");
            return true;
        }

        // 3. Buscar player
        String playerName = args[0];
        PlayerData playerData = CoreAPI.getPlayerByName(playerName);
        if (playerData == null) {
            sender.sendMessage(ChatColor.RED + "Jogador não encontrado: " + playerName);
            return true;
        }

        UUID playerUuid = playerData.getUuid();

        // 4. Parse reason
        String reason;
        if (args.length > 1) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) {
                    reasonBuilder.append(" ");
                }
                reasonBuilder.append(args[i]);
            }
            reason = reasonBuilder.toString();

            String template = plugin.getConfig().getString("templates." + reason.toLowerCase());
            if (template != null) {
                reason = template;
            }
        } else {
            reason = plugin.getConfig().getString("templates.toxicity", "Comportamento tóxico");
        }

        // 5. Aplicar warn (sempre permanente, sem tempo) (async)
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        plugin.getPunishManager().applyPunish(playerUuid, null, "warn", reason, staffUuid, null);

        // 6. Mensagem de confirmação (aplicação é async, mas mensagem imediata)
        sender.sendMessage(ChatColor.GREEN + "Jogador " + ChatColor.YELLOW + playerName +
            ChatColor.GREEN + " foi advertido por: " + ChatColor.WHITE + reason);

        return true;
    }
}

