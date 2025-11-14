package com.primeleague.chat.commands;

import com.primeleague.chat.ChatPlugin;
import com.primeleague.chat.managers.ChatManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

/**
 * Comando /msg - Mensagem privada
 * Grug Brain: Comando direto, validações simples
 */
public class MsgCommand implements CommandExecutor {

    private final ChatPlugin plugin;
    private final ChatManager chatManager;

    public MsgCommand(ChatPlugin plugin) {
        this.plugin = plugin;
        this.chatManager = plugin.getChatManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUso: /msg <player> <mensagem>");
            return true;
        }

        Player from = (Player) sender;
        Player to = Bukkit.getPlayer(args[0]);

        if (to == null) {
            sender.sendMessage("§cJogador não encontrado!");
            return true;
        }

        if (from.equals(to)) {
            sender.sendMessage("§cVocê não pode enviar mensagem para si mesmo!");
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        chatManager.sendPrivateMessage(from, to, message);
        return true;
    }
}

