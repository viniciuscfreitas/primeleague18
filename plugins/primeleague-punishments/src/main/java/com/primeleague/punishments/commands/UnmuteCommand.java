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
 * Comando /unmute
 * Grug Brain: Comando direto, remove mute
 */
public class UnmuteCommand implements CommandExecutor {

    private final PunishPlugin plugin;

    public UnmuteCommand(PunishPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 1. Validar permissão
        if (!sender.hasPermission("punish.unmute")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        // 2. Validar args
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Uso: /unmute <player>");
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

        // 4. Remover mute (async)
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        final CommandSender finalSender = sender;
        final String finalPlayerName = playerName;

        plugin.getPunishManager().removePunish(playerUuid, "mute", staffUuid, (success) -> {
            // 5. Mensagem de confirmação
            if (success) {
                finalSender.sendMessage(ChatColor.GREEN + "Mute removido de " + ChatColor.YELLOW + finalPlayerName);
            } else {
                finalSender.sendMessage(ChatColor.RED + "Jogador " + finalPlayerName + " não está mutado ou já teve o mute removido.");
            }
        });

        return true;
    }
}

