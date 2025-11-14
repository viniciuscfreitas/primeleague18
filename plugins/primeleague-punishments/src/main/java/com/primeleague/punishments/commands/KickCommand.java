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
 * Comando /kick
 * Grug Brain: Comando direto, sem DB, só kick
 */
public class KickCommand implements CommandExecutor {

    private final PunishPlugin plugin;

    public KickCommand(PunishPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 1. Validar permissão
        if (!sender.hasPermission("punish.kick")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        // 2. Validar args
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Uso: /kick <player> [reason]");
            return true;
        }

        // 3. Buscar player (deve estar online para kick)
        String playerName = args[0];
        Player targetPlayer = plugin.getServer().getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Jogador não está online: " + playerName);
            return true;
        }

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
        } else {
            reason = "Você foi expulso do servidor.";
        }

        // 5. Aplicar kick (sem salvar no DB conforme plano)
        UUID staffUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        plugin.getPunishManager().applyPunish(targetPlayer.getUniqueId(), null, "kick", reason, staffUuid, null);

        // 6. Mensagem de confirmação
        sender.sendMessage(ChatColor.GREEN + "Jogador " + ChatColor.YELLOW + playerName +
            ChatColor.GREEN + " foi expulso por: " + ChatColor.WHITE + reason);

        return true;
    }
}

