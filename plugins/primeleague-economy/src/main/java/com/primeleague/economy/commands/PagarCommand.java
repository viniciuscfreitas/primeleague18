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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comando /pagar - Transfere dinheiro entre players
 * Grug Brain: Rate-limit 1/s, tax configurável, async
 */
public class PagarCommand implements CommandExecutor {

    private final EconomyPlugin plugin;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");
    // Rate-limit: UUID -> timestamp da última transação (thread-safe)
    private final Map<UUID, Long> lastPayTime = new ConcurrentHashMap<>();
    private static final long PAY_COOLDOWN_MS = 1000; // 1 segundo

    public PagarCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Use: /pagar <player> <valor>");
            return true;
        }

        Player player = (Player) sender;

        // Rate-limit: 1 pagamento por segundo
        if (!checkRateLimit(player.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Aguarde 1 segundo entre pagamentos.");
            return true;
        }

        String targetName = args[0];
        double amount;

        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Valor inválido: " + args[1]);
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Valor deve ser maior que zero.");
            return true;
        }

        // Validar saldo
        if (!EconomyAPI.hasBalance(player.getUniqueId(), amount)) {
            double balance = EconomyAPI.getBalance(player.getUniqueId());
            String currency = plugin.getConfig().getString("economy.simbolo", "¢");
            String message = plugin.getConfig().getString("mensagens.pagar-insuficiente", "§cSaldo insuficiente! Você tem {balance} {currency}")
                .replace("{balance}", balanceFormat.format(balance))
                .replace("{currency}", currency);
            sender.sendMessage(message);
            return true;
        }

        final Player finalPlayer = player;
        final String finalTargetName = targetName;
        final double finalAmount = amount;

        // Buscar target player (async)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            org.bukkit.entity.Player targetPlayer = Bukkit.getPlayer(finalTargetName);
            UUID targetUuid = null;

            if (targetPlayer != null) {
                targetUuid = targetPlayer.getUniqueId();
            } else {
                // Player offline - buscar no banco
                com.primeleague.core.models.PlayerData data = CoreAPI.getPlayerByName(finalTargetName);
                if (data == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        finalPlayer.sendMessage(ChatColor.RED + "Player não encontrado: " + finalTargetName);
                    });
                    return;
                }
                targetUuid = data.getUuid();
            }

            // Calcular tax se aplicável
            double taxPercent = 0;
            double taxAbove = plugin.getConfig().getDouble("economy.pagar-taxa-acima", 50.0);
            if (finalAmount > taxAbove) {
                taxPercent = plugin.getConfig().getDouble("economy.pagar-taxa-percentual", 0.03);
            }

            double totalAmount = finalAmount;
            double taxAmount = totalAmount * taxPercent;
            double netAmount = totalAmount - taxAmount;

            // Transferir
            final UUID finalTargetUuid = targetUuid;
            final double finalNetAmount = netAmount;
            final double finalTaxAmount = taxAmount;
            final org.bukkit.entity.Player finalTargetPlayer = targetPlayer;

            // Transferir valor líquido (sem tax)
            if (EconomyAPI.transfer(finalPlayer.getUniqueId(), finalTargetUuid, finalNetAmount, "PAY")) {
                // Tax se aplicável (remover do remetente)
                if (taxAmount > 0) {
                    EconomyAPI.removeMoney(finalPlayer.getUniqueId(), finalTaxAmount, "PAY_TAX");
                }

                // Mensagens
                String currency = plugin.getConfig().getString("economy.simbolo", "¢");
                String sentMsgBase = plugin.getConfig().getString("mensagens.pagar-enviado", "§aVocê enviou §e{amount} {currency} §apara {player}")
                    .replace("{amount}", balanceFormat.format(finalNetAmount))
                    .replace("{currency}", currency)
                    .replace("{player}", finalTargetName);
                final String sentMsg = finalTaxAmount > 0
                    ? sentMsgBase + ChatColor.GRAY + " (taxa: " + balanceFormat.format(finalTaxAmount) + currency + ")"
                    : sentMsgBase;

                final String receivedMsg = plugin.getConfig().getString("mensagens.pagar-recebido", "§aVocê recebeu §e{amount} {currency} §ade {sender}")
                    .replace("{amount}", balanceFormat.format(finalNetAmount))
                    .replace("{currency}", currency)
                    .replace("{sender}", finalPlayer.getName());

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    finalPlayer.sendMessage(sentMsg);
                    if (finalTargetPlayer != null && finalTargetPlayer.isOnline()) {
                        finalTargetPlayer.sendMessage(receivedMsg);
                    }
                });
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    finalPlayer.sendMessage(ChatColor.RED + "Erro ao transferir dinheiro.");
                });
            }
        });

        return true;
    }

    /**
     * Verifica rate-limit (1 pagamento por segundo)
     */
    private boolean checkRateLimit(UUID uuid) {
        long now = System.currentTimeMillis();
        Long lastTime = lastPayTime.get(uuid);

        if (lastTime != null && (now - lastTime) < PAY_COOLDOWN_MS) {
            return false;
        }

        lastPayTime.put(uuid, now);
        return true;
    }
}

