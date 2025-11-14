package com.primeleague.economy.listeners;

import com.primeleague.economy.EconomyAPI;
import com.primeleague.economy.EconomyPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;

/**
 * Listener da GUI da loja - Processa compras
 * Grug Brain: Lógica direta, valida saldo antes de comprar
 */
public class LojaGUIListener implements Listener {

    private final EconomyPlugin plugin;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    /**
     * CORREÇÃO #3: Enum ShopItem para mapeamento robusto (não quebra se GUI mudar)
     */
    private enum ShopItem {
        IRON_SWORD(0, "IRON_SWORD"),
        IRON_CHESTPLATE(1, "IRON_CHESTPLATE"),
        STRENGTH_POTION(2, "STRENGTH_POTION"),
        SPEED_POTION(3, "SPEED_POTION"),
        GOLDEN_APPLE(4, "GOLDEN_APPLE"),
        ENDER_PEARL(5, "ENDER_PEARL"),
        XP_BOTTLE(6, "XP_BOTTLE");

        private final int slot;
        private final String itemName;

        ShopItem(int slot, String itemName) {
            this.slot = slot;
            this.itemName = itemName;
        }

        public static ShopItem fromSlot(int slot) {
            for (ShopItem item : values()) {
                if (item.slot == slot) return item;
            }
            return null;
        }

        public String getItemName() {
            return itemName;
        }
    }

    public LojaGUIListener(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Verificar se é a GUI da loja
        if (event.getInventory().getTitle() == null ||
            !event.getInventory().getTitle().equals(ChatColor.BLUE + "Loja PvP")) {
            return;
        }

        // Cancelar clicks normais
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Slot 8 = Fechar
        if (event.getSlot() == 8) {
            player.closeInventory();
            return;
        }

        // Verificar se clicou em item válido
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        // Obter preço do item
        double price = getItemPrice(clicked);
        if (price <= 0) {
            return;
        }

        // Verificar saldo
        if (!EconomyAPI.hasBalance(player.getUniqueId(), price)) {
            double balance = EconomyAPI.getBalance(player.getUniqueId());
            String currency = plugin.getConfig().getString("economy.simbolo", "¢");
            player.sendMessage(ChatColor.RED + "Saldo insuficiente! Você tem " +
                balanceFormat.format(balance) + currency);
            return;
        }

        // CORREÇÃO #3: Obter item name do enum antes de comprar
        ShopItem shopItem = ShopItem.fromSlot(event.getSlot());
        String itemName = shopItem != null ? shopItem.getItemName() : null;

        // Comprar item
        EconomyAPI.removeMoney(player.getUniqueId(), price, "SHOP_BUY");

        // CORREÇÃO #3: Registrar transação com item name no reason (se disponível)
        if (itemName != null) {
            EconomyAPI.logTransactionPublic(player.getUniqueId(), null, price, "SHOP_BUY", itemName);
        }

        // Dar item ao player (clone para evitar problemas)
        ItemStack itemToGive = clicked.clone();
        ItemMeta meta = itemToGive.getItemMeta();
        // Remover lore da loja
        meta.setLore(null);
        itemToGive.setItemMeta(meta);

        // Dar item (verificar espaço no inventário)
        if (player.getInventory().firstEmpty() == -1) {
            // Inventário cheio - dropar no chão
            player.getWorld().dropItemNaturally(player.getLocation(), itemToGive);
            player.sendMessage(ChatColor.YELLOW + "Inventário cheio! Item foi dropado no chão.");
        } else {
            player.getInventory().addItem(itemToGive);
        }

        String currency = plugin.getConfig().getString("economy.simbolo", "¢");
        player.sendMessage(ChatColor.GREEN + "Você comprou " + clicked.getItemMeta().getDisplayName() +
            ChatColor.GREEN + " por " + ChatColor.YELLOW + balanceFormat.format(price) + currency);
    }

    /**
     * Obtém preço do item (da lore)
     * Grug Brain: Parse simples da lore
     */
    private double getItemPrice(ItemStack item) {
        if (item == null || item.getItemMeta() == null || item.getItemMeta().getLore() == null) {
            return 0;
        }

        for (String lore : item.getItemMeta().getLore()) {
            if (lore.contains("Preço:")) {
                // Parse: "§7Preço: §e20.00¢"
                String priceStr = ChatColor.stripColor(lore)
                    .replace("Preço:", "")
                    .replace("¢", "")
                    .trim();
                try {
                    return Double.parseDouble(priceStr);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }

        return 0;
    }
}

