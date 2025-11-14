package com.primeleague.economy.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.economy.EconomyAPI;
import com.primeleague.economy.EconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;

/**
 * Comando /dinheiro ou /saldo - Mostra saldo do player
 * Grug Brain: Query async, mensagens diretas
 */
public class DinheiroCommand implements CommandExecutor {

    private final EconomyPlugin plugin;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    public DinheiroCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Se tem argumento, mostrar saldo de outro player
        if (args.length > 0) {
            if (!sender.hasPermission("economy.balance")) {
                sender.sendMessage(ChatColor.RED + "Você não tem permissão para ver saldo de outros players.");
                return true;
            }
            return handleOtherPlayer(sender, args[0]);
        }

        // Mostrar próprio saldo
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;
        final CommandSender finalSender = sender;
        final Player finalPlayer = player;

        // Query async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            double balance = EconomyAPI.getBalance(finalPlayer.getUniqueId());
            String currency = plugin.getConfig().getString("economy.simbolo", "¢");
            String message = plugin.getConfig().getString("mensagens.saldo", "§bSeu saldo: §e{balance} {currency}")
                .replace("{balance}", balanceFormat.format(balance))
                .replace("{currency}", currency);

            // Voltar à thread principal para enviar mensagem
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                finalSender.sendMessage(message);

                // Actionbar não existe no 1.8.8 Player API
                // Se necessário, usar PacketWrapper ou reflection
            });
        });

        return true;
    }

    /**
     * Mostra saldo de outro player
     */
    private boolean handleOtherPlayer(CommandSender sender, String playerName) {
        final CommandSender finalSender = sender;
        final String targetName = playerName;

        // Query async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            org.bukkit.entity.Player targetPlayer = Bukkit.getPlayer(targetName);
            if (targetPlayer == null) {
                // Tentar buscar por nome no banco
                com.primeleague.core.models.PlayerData data = CoreAPI.getPlayerByName(targetName);
                if (data == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        finalSender.sendMessage(ChatColor.RED + "Player não encontrado: " + targetName);
                    });
                    return;
                }

                // Player offline - buscar saldo do banco
                double balance = EconomyAPI.getBalance(data.getUuid());
                String currency = plugin.getConfig().getString("economy.simbolo", "¢");
                String message = plugin.getConfig().getString("mensagens.saldo-outro", "§bSaldo de {player}: §e{balance} {currency}")
                    .replace("{player}", targetName)
                    .replace("{balance}", balanceFormat.format(balance))
                    .replace("{currency}", currency);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    finalSender.sendMessage(message);
                });
                return;
            }

            // Player online - usar cache
            double balance = EconomyAPI.getBalance(targetPlayer.getUniqueId());
            String currency = plugin.getConfig().getString("economy.simbolo", "¢");
            String message = plugin.getConfig().getString("mensagens.saldo-outro", "§bSaldo de {player}: §e{balance} {currency}")
                .replace("{player}", targetPlayer.getName())
                .replace("{balance}", balanceFormat.format(balance))
                .replace("{currency}", currency);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                finalSender.sendMessage(message);
            });
        });

        return true;
    }
}

