package com.primeleague.economy.commands;

import com.primeleague.economy.EconomyAPI;
import com.primeleague.economy.EconomyPlugin;
import com.primeleague.economy.utils.DynamicPricer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * Comando /loja - Abre GUI de loja PvP
 * Grug Brain: GUI simples, 1 página, 9 slots principais
 */
public class LojaCommand implements CommandExecutor {

    private final EconomyPlugin plugin;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");
    private DynamicPricer dynamicPricer; // Injetar via setter

    public LojaCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Define DynamicPricer (injetado pelo EconomyPlugin)
     */
    public void setDynamicPricer(DynamicPricer dynamicPricer) {
        this.dynamicPricer = dynamicPricer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;
        openShopGUI(player);

        return true;
    }

    /**
     * Abre GUI da loja PvP
     * Grug Brain: Inventory simples, 27 slots (3 linhas)
     * Integrado com DynamicPricer quando habilitado
     */
    private void openShopGUI(Player player) {
        String currency = plugin.getConfig().getString("economy.simbolo", "¢");
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.BLUE + "Loja PvP");

        boolean dynamicEnabled = plugin.getConfig().getBoolean("economy.precos-dinamicos.habilitado", false);

        // Slot 0: Espada de Ferro (Sharp 2)
        ItemStack ironSword = createEnchantedItem(Material.IRON_SWORD, 1, Enchantment.DAMAGE_ALL, 2);
        double ironSwordPrice = dynamicEnabled && dynamicPricer != null
            ? dynamicPricer.getPrice("IRON_SWORD")
            : plugin.getConfig().getDouble("economy.shops.pvp.IRON_SWORD", 20.0);
        setShopItem(gui, 0, ironSword, ironSwordPrice, currency, "Espada de Ferro", "Sharp II");

        // Slot 1: Peitoral de Ferro (Prot 2)
        ItemStack ironChestplate = createEnchantedItem(Material.IRON_CHESTPLATE, 1, Enchantment.PROTECTION_ENVIRONMENTAL, 2);
        double ironChestPrice = dynamicEnabled && dynamicPricer != null
            ? dynamicPricer.getPrice("IRON_CHESTPLATE")
            : plugin.getConfig().getDouble("economy.shops.pvp.IRON_CHESTPLATE", 35.0);
        setShopItem(gui, 1, ironChestplate, ironChestPrice, currency, "Peitoral de Ferro", "Prot II");

        // Slot 2: Poção de Força II (1:30)
        ItemStack strengthPotion = createPotion(PotionType.STRENGTH, true, 1);
        double strengthPrice = dynamicEnabled && dynamicPricer != null
            ? dynamicPricer.getPrice("STRENGTH_POTION")
            : plugin.getConfig().getDouble("economy.shops.pvp.STRENGTH_POTION", 40.0);
        setShopItem(gui, 2, strengthPotion, strengthPrice, currency, "Poção de Força", "II (1:30)");

        // Slot 3: Poção de Velocidade II (1:30)
        ItemStack speedPotion = createPotion(PotionType.SPEED, true, 1);
        double speedPrice = dynamicEnabled && dynamicPricer != null
            ? dynamicPricer.getPrice("SPEED_POTION")
            : plugin.getConfig().getDouble("economy.shops.pvp.SPEED_POTION", 30.0);
        setShopItem(gui, 3, speedPotion, speedPrice, currency, "Poção de Velocidade", "II (1:30)");

        // Slot 4: Gapple
        ItemStack gapple = new ItemStack(Material.GOLDEN_APPLE, 1);
        double gapplePrice = dynamicEnabled && dynamicPricer != null
            ? dynamicPricer.getPrice("GOLDEN_APPLE")
            : plugin.getConfig().getDouble("economy.shops.pvp.GOLDEN_APPLE", 15.0);
        setShopItem(gui, 4, gapple, gapplePrice, currency, "Golden Apple", "");

        // Slot 5: Ender Pearl
        ItemStack enderPearl = new ItemStack(Material.ENDER_PEARL, 1);
        double pearlPrice = dynamicEnabled && dynamicPricer != null
            ? dynamicPricer.getPrice("ENDER_PEARL")
            : plugin.getConfig().getDouble("economy.shops.pvp.ENDER_PEARL", 8.0);
        setShopItem(gui, 5, enderPearl, pearlPrice, currency, "Ender Pearl", "");

        // Slot 6: XP Bottle (8 levels)
        ItemStack xpBottle = new ItemStack(Material.EXP_BOTTLE, 8);
        double xpPrice = dynamicEnabled && dynamicPricer != null
            ? dynamicPricer.getPrice("XP_BOTTLE")
            : plugin.getConfig().getDouble("economy.shops.pvp.XP_BOTTLE", 12.0);
        setShopItem(gui, 6, xpBottle, xpPrice, currency, "XP Bottle", "x8");

        // Slot 8: Fechar
        ItemStack closeItem = new ItemStack(Material.BARRIER, 1);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fechar");
        closeItem.setItemMeta(closeMeta);
        gui.setItem(8, closeItem);

        player.openInventory(gui);
    }

    /**
     * Cria item encantado
     * Grug Brain: Paper 1.8.8 compatível
     */
    private ItemStack createEnchantedItem(Material material, int amount, Enchantment enchant, int level) {
        ItemStack item = new ItemStack(material, amount);
        item.addUnsafeEnchantment(enchant, level);
        return item;
    }

    /**
     * Cria poção (Paper 1.8.8)
     * Grug Brain: API antiga de poções com duração customizada
     * Para Strength II 1:30 (1800 ticks), usar PotionMeta.addCustomEffect()
     */
    private ItemStack createPotion(PotionType type, boolean isSplash, int level) {
        Potion potion = new Potion(type, level);
        potion.setSplash(isSplash);

        ItemStack item = potion.toItemStack(1);

        // Ajustar duração para 1:30 (1800 ticks) usando PotionMeta
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta != null) {
            // Remover efeitos padrão e adicionar customizado
            meta.clearCustomEffects();

            // Adicionar efeito com duração customizada
            // level 1 = II (amplifier 1)
            org.bukkit.potion.PotionEffectType effectType = null;
            if (type == PotionType.STRENGTH) {
                effectType = org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE;
            } else if (type == PotionType.SPEED) {
                effectType = org.bukkit.potion.PotionEffectType.SPEED;
            }

            if (effectType != null) {
                // 1800 ticks = 90 segundos = 1:30
                meta.addCustomEffect(new org.bukkit.potion.PotionEffect(effectType, 1800, level - 1), true);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Define item na loja com preço
     */
    private void setShopItem(Inventory gui, int slot, ItemStack item, double price, String currency, String name, String extra) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + name + (extra.isEmpty() ? "" : " " + extra));

        String priceStr = balanceFormat.format(price) + currency;
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Preço: " + ChatColor.YELLOW + priceStr,
            ChatColor.GRAY + "Clique para comprar"
        ));

        item.setItemMeta(meta);
        gui.setItem(slot, item);
    }
}

