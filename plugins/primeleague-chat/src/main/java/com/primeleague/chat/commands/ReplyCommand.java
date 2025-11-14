package com.primeleague.chat.commands;

import com.primeleague.chat.ChatPlugin;
import com.primeleague.chat.managers.ChatManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

/**
 * Comando /reply - Responde última mensagem privada
 * Grug Brain: Comando direto, usa cache do ChatManager
 */
public class ReplyCommand implements CommandExecutor {

    private final ChatPlugin plugin;
    private final ChatManager chatManager;

    public ReplyCommand(ChatPlugin plugin) {
        this.plugin = plugin;
        this.chatManager = plugin.getChatManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUso: /reply <mensagem>");
            return true;
        }

        Player from = (Player) sender;
        UUID lastSenderUuid = chatManager.getLastSender(from.getUniqueId());

        if (lastSenderUuid == null) {
            sender.sendMessage("§cVocê não tem ninguém para responder!");
            return true;
        }

        Player to = Bukkit.getPlayer(lastSenderUuid);
        if (to == null || !to.isOnline()) {
            sender.sendMessage("§cJogador não está online!");
            return true;
        }

        String message = String.join(" ", args);
        chatManager.sendPrivateMessage(from, to, message);
        return true;
    }
}

