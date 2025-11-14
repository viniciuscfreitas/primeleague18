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
 * Comando /mute
 * Grug Brain: Comando direto, similar ao ban
 */
public class MuteCommand implements CommandExecutor {

    private final PunishPlugin plugin;

    public MuteCommand(PunishPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 1. Validar permissão
        if (!sender.hasPermission("punish.mute")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        // 2. Validar args
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Uso: /mute <player> [tempo] [reason]");
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

        // 4. Parse tempo
        Long durationSeconds = null;
        int reasonStartIndex = 1;

        if (args.length > 1) {
            String secondArg = args[1];
            long parsedDuration = plugin.getPunishManager().parseDuration(secondArg);

            if (parsedDuration > 0) {
                durationSeconds = parsedDuration;
                reasonStartIndex = 2;
            }
        }

        // 5. Parse reason
        String reason;
        if (args.length > reasonStartIndex) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = reasonStartIndex; i < args.length; i++) {
                if (i > reasonStartIndex) {
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
            reason = plugin.getConfig().getString("templates.spam", "Spam excessivo");
        }

        // 6. Aplicar mute (async)
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        plugin.getPunishManager().applyPunish(playerUuid, null, "mute", reason, staffUuid, durationSeconds);

        // 7. Mensagem de confirmação (aplicação é async, mas mensagem imediata)
        String durationStr = durationSeconds != null ? formatDuration(durationSeconds) : "permanente";
        sender.sendMessage(ChatColor.GREEN + "Jogador " + ChatColor.YELLOW + playerName +
            ChatColor.GREEN + " foi mutado " + ChatColor.YELLOW + durationStr +
            ChatColor.GREEN + " por: " + ChatColor.WHITE + reason);

        return true;
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else if (seconds < 86400) {
            return (seconds / 3600) + "h";
        } else {
            return (seconds / 86400) + "d";
        }
    }
}

