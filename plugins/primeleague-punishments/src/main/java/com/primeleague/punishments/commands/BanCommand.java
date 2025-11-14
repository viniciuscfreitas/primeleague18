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
 * Comando /ban
 * Grug Brain: Comando direto, sem subcomandos complexos
 */
public class BanCommand implements CommandExecutor {

    private final PunishPlugin plugin;

    public BanCommand(PunishPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 1. Validar permissão: punish.ban
        if (!sender.hasPermission("punish.ban")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        // 2. Validar args (mínimo: player)
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Uso: /ban <player> [tempo] [reason]");
            sender.sendMessage(ChatColor.GRAY + "Exemplos:");
            sender.sendMessage(ChatColor.GRAY + "  /ban Player123 hacking");
            sender.sendMessage(ChatColor.GRAY + "  /ban Player123 7d uso de hacks");
            sender.sendMessage(ChatColor.GRAY + "  /ban Player123 1h spam");
            return true;
        }

        // 3. Buscar player (online ou offline via CoreAPI)
        String playerName = args[0];
        PlayerData playerData = CoreAPI.getPlayerByName(playerName);
        if (playerData == null) {
            sender.sendMessage(ChatColor.RED + "Jogador não encontrado: " + playerName);
            return true;
        }

        UUID playerUuid = playerData.getUuid();
        Player onlinePlayer = plugin.getServer().getPlayer(playerUuid);

        // 4. Parse tempo (se fornecido): 1h, 7d, 30d, etc
        Long durationSeconds = null;
        int reasonStartIndex = 1;

        if (args.length > 1) {
            // Tentar parse tempo (pode ser tempo ou reason)
            String secondArg = args[1];
            long parsedDuration = plugin.getPunishManager().parseDuration(secondArg);

            if (parsedDuration > 0) {
                // É um tempo válido
                durationSeconds = parsedDuration;
                reasonStartIndex = 2;
            }
            // Se parsedDuration == 0, pode ser permanente ou reason inválido
        }

        // 5. Parse reason (resto dos args ou template)
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

            // Verificar se é um template
            String template = plugin.getConfig().getString("templates." + reason.toLowerCase());
            if (template != null) {
                reason = template;
            }
        } else {
            // Reason padrão
            reason = plugin.getConfig().getString("templates.hacking", "Uso de hacks detectado");
        }

        // 6. Chamar punishManager.applyPunish() (async)
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        String ip = onlinePlayer != null ? onlinePlayer.getAddress().getAddress().getHostAddress() : null;

        final CommandSender finalSender = sender;
        final String finalPlayerName = playerName;
        final String finalReason = reason;
        final Long finalDurationSeconds = durationSeconds;

        plugin.getPunishManager().applyPunish(playerUuid, ip, "ban", reason, staffUuid, durationSeconds);

        // 7. Mensagem de confirmação (aplicação é async, mas mensagem imediata)
        String durationStr = durationSeconds != null ? formatDuration(durationSeconds) : "permanente";
        sender.sendMessage(ChatColor.GREEN + "Jogador " + ChatColor.YELLOW + playerName +
            ChatColor.GREEN + " foi banido " + ChatColor.YELLOW + durationStr +
            ChatColor.GREEN + " por: " + ChatColor.WHITE + reason);

        return true;
    }

    /**
     * Formata duração em segundos para string legível
     */
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

