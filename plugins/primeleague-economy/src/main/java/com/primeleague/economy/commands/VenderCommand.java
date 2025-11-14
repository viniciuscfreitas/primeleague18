package com.primeleague.economy.commands;

import com.primeleague.economy.EconomyAPI;
import com.primeleague.economy.EconomyPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comando /vender - Vende items do inventário
 * Grug Brain: Apenas loot PvP (blacklist items minerados), multiplicador 0.65 (35% sink)
 */
public class VenderCommand implements CommandExecutor {

    private final EconomyPlugin plugin;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    // Rate-limit: /vender tudo 5s (thread-safe)
    private final Map<UUID, Long> lastSellTime = new ConcurrentHashMap<>();
    private static final long SELL_COOLDOWN_MS = 5000; // 5 segundos

    public VenderCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Use: /vender <tudo|mão> [quantidade]");
            return true;
        }

        String action = args[0].toLowerCase();

        if (action.equals("tudo")) {
            return handleSellAll(player);
        } else if (action.equals("mão") || action.equals("mao")) {
            int quantity = 1;
            if (args.length > 1) {
                try {
                    quantity = Integer.parseInt(args[1]);
                    if (quantity <= 0) {
                        sender.sendMessage(ChatColor.RED + "Quantidade deve ser maior que zero.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Quantidade inválida: " + args[1]);
                    return true;
                }
            }
            return handleSellHand(player, quantity);
        } else {
            sender.sendMessage(ChatColor.RED + "Use: /vender <tudo|mão> [quantidade]");
            return true;
        }
    }

    /**
     * /vender tudo - Vende todos os items vendáveis do inventário
     */
    private boolean handleSellAll(Player player) {
        UUID uuid = player.getUniqueId();

        // Rate-limit: 5 segundos
        if (!checkRateLimit(uuid)) {
            player.sendMessage(ChatColor.RED + "Aguarde 5 segundos entre vendas.");
            return true;
        }

        // Processar em async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            double totalValue = 0;
            int itemsSold = 0;

            // Verificar todos os slots do inventário
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }

                // Verificar se item é vendável (loot PvP apenas)
                if (!isSellable(item)) {
                    continue;
                }

                // Calcular valor
                double itemValue = getItemValue(item);
                if (itemValue <= 0) {
                    continue;
                }

                // Multiplicador (65% do valor = sink de 35%)
                double multiplier = plugin.getConfig().getDouble("economy.vender-multiplicador", 0.65);
                double sellValue = itemValue * multiplier * item.getAmount();

                totalValue += sellValue;
                itemsSold += item.getAmount();

                // Remover item do inventário
                player.getInventory().setItem(i, null);
            }

            final double finalTotalValue = totalValue;
            final int finalItemsSold = itemsSold;

            // Adicionar dinheiro e enviar mensagem (thread principal)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (finalTotalValue > 0) {
                    EconomyAPI.addMoney(uuid, finalTotalValue, "SELL_ALL");

                    String currency = plugin.getConfig().getString("economy.simbolo", "¢");
                    String msg = plugin.getConfig().getString("mensagens.vender-sucesso", "§aVocê vendeu items por §e{amount} {currency}")
                        .replace("{amount}", balanceFormat.format(finalTotalValue))
                        .replace("{currency}", currency);
                    player.sendMessage(msg);
                    player.sendMessage(ChatColor.GRAY + "Items vendidos: " + finalItemsSold);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Nenhum item vendável encontrado.");
                }
            });
        });

        return true;
    }

    /**
     * /vender mão [quantidade] - Vende item na mão
     */
    private boolean handleSellHand(Player player, int quantity) {
        ItemStack handItem = player.getItemInHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Você não está segurando nenhum item.");
            return true;
        }

        // Verificar se item é vendável
        if (!isSellable(handItem)) {
            String msg = plugin.getConfig().getString("mensagens.vender-item-blacklist",
                "§cEste item não pode ser vendido (já deu dinheiro direto)");
            player.sendMessage(msg);
            return true;
        }

        // Verificar quantidade
        int maxQuantity = handItem.getAmount();
        if (quantity > maxQuantity) {
            quantity = maxQuantity;
        }

        // Calcular valor
        double itemValue = getItemValue(handItem);
        if (itemValue <= 0) {
            player.sendMessage(ChatColor.YELLOW + "Este item não tem valor de venda.");
            return true;
        }

        // Multiplicador (65% do valor = sink de 35%)
        double multiplier = plugin.getConfig().getDouble("economy.vender-multiplicador", 0.65);
        double sellValue = itemValue * multiplier * quantity;

        // Remover item do inventário
        if (quantity >= maxQuantity) {
            player.setItemInHand(null);
        } else {
            handItem.setAmount(maxQuantity - quantity);
        }

        // Adicionar dinheiro
        EconomyAPI.addMoney(player.getUniqueId(), sellValue, "SELL_HAND");

        String currency = plugin.getConfig().getString("economy.simbolo", "¢");
        String msg = plugin.getConfig().getString("mensagens.vender-sucesso", "§aVocê vendeu items por §e{amount} {currency}")
            .replace("{amount}", balanceFormat.format(sellValue))
            .replace("{currency}", currency);
        player.sendMessage(msg);

        return true;
    }

    /**
     * Verifica se item é vendável (apenas loot PvP)
     * Grug Brain: Blacklist items minerados, whitelist loot PvP
     */
    private boolean isSellable(ItemStack item) {
        Material material = item.getType();

        // Blacklist: items minerados (nunca vendem)
        List<String> blacklist = plugin.getConfig().getStringList("economy.vender-blacklist");
        for (String blacklistItem : blacklist) {
            try {
                Material blacklistMaterial = Material.valueOf(blacklistItem.toUpperCase());
                if (material == blacklistMaterial) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                // Material inválido - ignorar
            }
        }

        // Whitelist: items de PvP (potions, gapples, enchanted items)
        List<String> whitelist = plugin.getConfig().getStringList("economy.vender-whitelist");
        for (String whitelistItem : whitelist) {
            try {
                Material whitelistMaterial = Material.valueOf(whitelistItem.toUpperCase());
                if (material == whitelistMaterial) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Material inválido - ignorar
            }
        }

        // Items encantados são loot PvP (swords, armor, etc.)
        if (item.getItemMeta() != null && item.getItemMeta().hasEnchants()) {
            return true;
        }

        // Outros items não vendem (para evitar farm)
        return false;
    }

    /**
     * Obtém valor base do item (config.yml ou padrão)
     * Grug Brain: Valores fixos iniciais, pode ser expandido
     */
    private double getItemValue(ItemStack item) {
        Material material = item.getType();

        // Valores padrão para loot PvP
        if (material == Material.GOLDEN_APPLE) {
            return 10.0;
        } else if (material == Material.POTION) {
            // No 1.8.8, POTION inclui tanto poções normais quanto splash
            return 5.0;
        } else if (material == Material.DIAMOND_SWORD) {
            return 30.0;
        } else if (material == Material.IRON_SWORD) {
            return 15.0;
        } else if (material == Material.DIAMOND_CHESTPLATE) {
            return 50.0;
        } else if (material == Material.IRON_CHESTPLATE) {
            return 25.0;
        } else {
            // Para items encantados, valor base + bônus de encantamentos
            if (item.getItemMeta() != null && item.getItemMeta().hasEnchants()) {
                return 20.0; // Valor base para items encantados
            }
            return 0;
        }
    }

    /**
     * Verifica rate-limit (5 segundos)
     */
    private boolean checkRateLimit(UUID uuid) {
        long now = System.currentTimeMillis();
        Long lastTime = lastSellTime.get(uuid);

        if (lastTime != null && (now - lastTime) < SELL_COOLDOWN_MS) {
            return false;
        }

        lastSellTime.put(uuid, now);
        return true;
    }
}

