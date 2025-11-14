package com.primeleague.chat.commands;

import com.primeleague.chat.ChatPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Comando /clearchat - Limpa o chat (admin)
 * Grug Brain: Comando simples, envia linhas em branco
 */
public class ClearChatCommand implements CommandExecutor {

    private final ChatPlugin plugin;

    public ClearChatCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("chat.admin")) {
            sender.sendMessage("§cVocê não tem permissão para usar este comando!");
            return true;
        }

        // Enviar 100 linhas em branco para "limpar" o chat
        for (int i = 0; i < 100; i++) {
            org.bukkit.Bukkit.broadcastMessage("");
        }

        String clearMessage = "§aChat limpo por " + sender.getName();
        org.bukkit.Bukkit.broadcastMessage(clearMessage);
        return true;
    }
}

