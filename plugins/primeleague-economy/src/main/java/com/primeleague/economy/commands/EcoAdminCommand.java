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
 * Comando /eco - Admin commands para gerenciar economia
 * Grug Brain: Comandos diretos, validações básicas
 */
public class EcoAdminCommand implements CommandExecutor {

    private final EconomyPlugin plugin;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    public EcoAdminCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("economy.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Use: /eco <dar|remover|definir|reset> <player> <valor>");
            sender.sendMessage(ChatColor.GRAY + "Exemplos:");
            sender.sendMessage(ChatColor.GRAY + "  /eco dar PlayerName 100");
            sender.sendMessage(ChatColor.GRAY + "  /eco remover PlayerName 50");
            sender.sendMessage(ChatColor.GRAY + "  /eco definir PlayerName 500");
            sender.sendMessage(ChatColor.GRAY + "  /eco reset PlayerName");
            return true;
        }

        String action = args[0].toLowerCase();
        String playerName = args[1];
        double amount = 0;

        if (!action.equals("reset")) {
            try {
                amount = Double.parseDouble(args[2]);
                if (amount < 0) {
                    sender.sendMessage(ChatColor.RED + "Valor deve ser positivo.");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Valor inválido: " + args[2]);
                return true;
            }
        }

        final String finalAction = action;
        final String finalPlayerName = playerName;
        final double finalAmount = amount;
        final CommandSender finalSender = sender;

        // Buscar player (async)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            org.bukkit.entity.Player targetPlayer = Bukkit.getPlayer(finalPlayerName);
            java.util.UUID targetUuid = null;

            if (targetPlayer != null) {
                targetUuid = targetPlayer.getUniqueId();
            } else {
                // Player offline - buscar no banco
                com.primeleague.core.models.PlayerData data = CoreAPI.getPlayerByName(finalPlayerName);
                if (data == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        finalSender.sendMessage(ChatColor.RED + "Player não encontrado: " + finalPlayerName);
                    });
                    return;
                }
                targetUuid = data.getUuid();
            }

            final java.util.UUID finalTargetUuid = targetUuid;
            String currency = plugin.getConfig().getString("economy.simbolo", "¢");

            // Executar ação
            switch (finalAction) {
                case "dar":
                    EconomyAPI.addMoney(finalTargetUuid, finalAmount, "ADMIN_GIVE");
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        finalSender.sendMessage(ChatColor.GREEN + "Adicionado $" + balanceFormat.format(finalAmount) +
                            currency + " para " + finalPlayerName);
                    });
                    break;

                case "remover":
                    if (EconomyAPI.hasBalance(finalTargetUuid, finalAmount)) {
                        EconomyAPI.removeMoney(finalTargetUuid, finalAmount, "ADMIN_REMOVE");
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            finalSender.sendMessage(ChatColor.GREEN + "Removido $" + balanceFormat.format(finalAmount) +
                                currency + " de " + finalPlayerName);
                        });
                    } else {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            finalSender.sendMessage(ChatColor.RED + "Player não tem saldo suficiente.");
                        });
                    }
                    break;

                case "definir":
                    EconomyAPI.setBalance(finalTargetUuid, finalAmount, "ADMIN_SET");
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        finalSender.sendMessage(ChatColor.GREEN + "Saldo de " + finalPlayerName + " definido para $" +
                            balanceFormat.format(finalAmount) + currency);
                    });
                    break;

                case "reset":
                    double startingBalance = plugin.getConfig().getDouble("economy.saldo-inicial", 10.0);
                    EconomyAPI.setBalance(finalTargetUuid, startingBalance, "ADMIN_RESET");
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        finalSender.sendMessage(ChatColor.GREEN + "Saldo de " + finalPlayerName + " resetado para $" +
                            balanceFormat.format(startingBalance) + currency);
                    });
                    break;

                default:
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        finalSender.sendMessage(ChatColor.RED + "Ação inválida: " + finalAction);
                    });
                    break;
            }
        });

        return true;
    }
}

