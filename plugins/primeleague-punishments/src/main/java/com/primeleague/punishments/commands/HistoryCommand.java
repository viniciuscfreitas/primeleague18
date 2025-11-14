package com.primeleague.punishments.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.punishments.PunishPlugin;
import com.primeleague.punishments.models.PunishmentData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Comando /history
 * Grug Brain: Comando direto, mostra histórico de punições
 */
public class HistoryCommand implements CommandExecutor {

    private final PunishPlugin plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public HistoryCommand(PunishPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 1. Validar permissão
        if (!sender.hasPermission("punish.history")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        // 2. Validar args
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Uso: /history <player>");
            return true;
        }

        // 3. Buscar player
        String playerName = args[0];
        PlayerData playerData = CoreAPI.getPlayerByName(playerName);
        if (playerData == null) {
            sender.sendMessage(ChatColor.RED + "Jogador não encontrado: " + playerName);
            return true;
        }

        // 4. Obter histórico (async)
        final CommandSender finalSender = sender;
        final String finalPlayerName = playerName;

        plugin.getPunishManager().getHistory(playerData.getUuid(), (history) -> {
            // 5. Mostrar histórico
            finalSender.sendMessage(ChatColor.GOLD + "=== Histórico de Punições: " + ChatColor.YELLOW + finalPlayerName + ChatColor.GOLD + " ===");

            if (history.isEmpty()) {
                finalSender.sendMessage(ChatColor.GRAY + "Nenhuma punição encontrada.");
            } else {
                for (PunishmentData punishment : history) {
                    String type = punishment.getType().toUpperCase();
                    String typeColor = getTypeColor(punishment.getType());
                    String status = punishment.isActive() ? ChatColor.GREEN + "ATIVA" : ChatColor.RED + "REMOVIDA";

                    StringBuilder line = new StringBuilder();
                    line.append(typeColor).append(type).append(ChatColor.GRAY).append(" - ");
                    line.append(status).append(ChatColor.GRAY).append(" - ");
                    line.append(ChatColor.WHITE).append(punishment.getReason() != null ? punishment.getReason() : "Sem motivo");

                    if (punishment.getCreatedAt() != null) {
                        line.append(ChatColor.GRAY).append(" (").append(dateFormat.format(punishment.getCreatedAt())).append(")");
                    }

                    if (punishment.getExpiresAt() != null) {
                        Date now = new Date();
                        if (punishment.getExpiresAt().after(now)) {
                            long remaining = (punishment.getExpiresAt().getTime() - now.getTime()) / 1000;
                            line.append(ChatColor.GRAY).append(" - Expira em: ").append(formatDuration(remaining));
                        } else {
                            line.append(ChatColor.GRAY).append(" - Expirado");
                        }
                    }

                    finalSender.sendMessage(line.toString());
                }
            }
        });

        return true;
    }

    private String getTypeColor(String type) {
        switch (type.toLowerCase()) {
            case "ban":
                return ChatColor.RED.toString();
            case "mute":
                return ChatColor.YELLOW.toString();
            case "warn":
                return ChatColor.GOLD.toString();
            case "kick":
                return ChatColor.GOLD.toString(); // ORANGE não existe em Paper 1.8.8
            default:
                return ChatColor.WHITE.toString();
        }
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

