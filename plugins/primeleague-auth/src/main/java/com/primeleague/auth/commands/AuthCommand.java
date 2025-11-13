package com.primeleague.auth.commands;

import com.primeleague.auth.AuthPlugin;
import com.primeleague.auth.listeners.AuthListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando para processar código de acesso
 * Grug Brain: Comando simples, direto
 */
public class AuthCommand implements CommandExecutor {

    private final AuthPlugin plugin;

    public AuthCommand(AuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 1) {
            player.sendMessage("§cUso: /auth <código>");
            return true;
        }

        String code = args[0].trim();
        if (code.isEmpty() || code.length() > 32) {
            player.sendMessage("§cCódigo inválido.");
            return true;
        }

        String ip = player.getAddress().getAddress().getHostAddress();
        if (ip == null || ip.isEmpty()) {
            player.sendMessage("§cErro ao obter endereço IP.");
            return true;
        }

        // Processar código de acesso
        AuthListener authListener = plugin.getAuthListener();
        if (authListener.processAccessCode(player.getName(), ip, code)) {
            player.sendMessage("§aCódigo válido! Conta criada com sucesso.");
            player.sendMessage("§eVocê precisa vincular sua conta Discord para segurança.");
            player.sendMessage("§eUse /link <seu_nome_minecraft> no Discord.");
        } else {
            player.sendMessage("§cCódigo inválido. Verifique e tente novamente.");
        }

        return true;
    }
}

