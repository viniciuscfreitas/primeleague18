package com.primeleague.factions.manager;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.core.CoreAPI;
import com.primeleague.factions.PrimeFactions;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gerenciador de Upgrades de Factions
 * Grug Brain: Cache em memória, GUI simples, integração com clan bank
 */
public class UpgradeManager {

    private final PrimeFactions plugin;
    private final Map<Integer, UpgradeData> upgradeCache; // clanId -> UpgradeData

    public enum UpgradeType {
        SPAWNER_RATE("Taxa de Spawners", Material.MOB_SPAWNER, 5, 50000, "%d%%"),
        CROP_GROWTH("Crescimento de Plantas", Material.WHEAT, 5, 40000, "%d%%"),
        EXP_BOOST("EXP de Mobs", Material.EXP_BOTTLE, 5, 60000, "%d%%"),
        EXTRA_SHIELD("Shield Extra", Material.BEACON, 10, 100000, "%d hora(s)");

        private final String displayName;
        private final Material icon;
        private final int maxLevel;
        private final long baseCost; // em centavos
        private final String format;

        UpgradeType(String displayName, Material icon, int maxLevel, long baseCost, String format) {
            this.displayName = displayName;
            this.icon = icon;
            this.maxLevel = maxLevel;
            this.baseCost = baseCost;
            this.format = format;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getIcon() {
            return icon;
        }

        public int getMaxLevel() {
            return maxLevel;
        }

        public long getBaseCost() {
            return baseCost;
        }

        public String getFormat() {
            return format;
        }

        /**
         * Calcula custo para próximo nível
         * Grug Brain: Custo = baseCost * (currentLevel + 1)
         */
        public long getCostForLevel(int currentLevel) {
            if (currentLevel >= maxLevel) {
                return -1; // Já está no máximo
            }
            return baseCost * (currentLevel + 1);
        }
    }

    /**
     * Dados de upgrade de um clã
     */
    public static class UpgradeData {
        private final int clanId;
        private int spawnerRate;
        private int cropGrowth;
        private int expBoost;
        private int extraShieldHours;

        public UpgradeData(int clanId, int spawnerRate, int cropGrowth, int expBoost, int extraShieldHours) {
            this.clanId = clanId;
            this.spawnerRate = spawnerRate;
            this.cropGrowth = cropGrowth;
            this.expBoost = expBoost;
            this.extraShieldHours = extraShieldHours;
        }

        public int getClanId() {
            return clanId;
        }

        public int getSpawnerRate() {
            return spawnerRate;
        }

        public void setSpawnerRate(int spawnerRate) {
            this.spawnerRate = spawnerRate;
        }

        public int getCropGrowth() {
            return cropGrowth;
        }

        public void setCropGrowth(int cropGrowth) {
            this.cropGrowth = cropGrowth;
        }

        public int getExpBoost() {
            return expBoost;
        }

        public void setExpBoost(int expBoost) {
            this.expBoost = expBoost;
        }

        public int getExtraShieldHours() {
            return extraShieldHours;
        }

        public void setExtraShieldHours(int extraShieldHours) {
            this.extraShieldHours = extraShieldHours;
        }

        public int getLevel(UpgradeType type) {
            switch (type) {
                case SPAWNER_RATE:
                    return spawnerRate;
                case CROP_GROWTH:
                    return cropGrowth;
                case EXP_BOOST:
                    return expBoost;
                case EXTRA_SHIELD:
                    return extraShieldHours;
                default:
                    return 0;
            }
        }

        public void setLevel(UpgradeType type, int level) {
            switch (type) {
                case SPAWNER_RATE:
                    spawnerRate = level;
                    break;
                case CROP_GROWTH:
                    cropGrowth = level;
                    break;
                case EXP_BOOST:
                    expBoost = level;
                    break;
                case EXTRA_SHIELD:
                    extraShieldHours = level;
                    break;
            }
        }
    }

    public UpgradeManager(PrimeFactions plugin) {
        this.plugin = plugin;
        this.upgradeCache = new ConcurrentHashMap<>();
    }

    /**
     * Carrega upgrades de um clã (com cache)
     * Grug Brain: Thread-safe (ConcurrentHashMap), mas query pode ser async se necessário
     */
    public UpgradeData getUpgrades(int clanId) {
        // Verificar cache
        if (upgradeCache.containsKey(clanId)) {
            return upgradeCache.get(clanId);
        }

        // Carregar do banco (síncrono - cache evita queries frequentes)
        // Grug Brain: Query rápida, cache TTL implícito (invalidação manual)
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT spawner_rate, crop_growth, exp_boost, extra_shield_hours " +
                "FROM faction_upgrades WHERE clan_id = ?")) {
            stmt.setInt(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UpgradeData data = new UpgradeData(
                        clanId,
                        rs.getInt("spawner_rate"),
                        rs.getInt("crop_growth"),
                        rs.getInt("exp_boost"),
                        rs.getInt("extra_shield_hours")
                    );
                    upgradeCache.put(clanId, data);
                    return data;
                } else {
                    // Criar registro padrão
                    UpgradeData data = new UpgradeData(clanId, 0, 0, 0, 0);
                    createUpgradeRecord(clanId);
                    upgradeCache.put(clanId, data);
                    return data;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao carregar upgrades do clã " + clanId, e);
            return new UpgradeData(clanId, 0, 0, 0, 0);
        }
    }

    /**
     * Cria registro de upgrades se não existir
     */
    private void createUpgradeRecord(int clanId) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO faction_upgrades (clan_id, spawner_rate, crop_growth, exp_boost, extra_shield_hours) " +
                "VALUES (?, 0, 0, 0, 0) ON CONFLICT (clan_id) DO NOTHING")) {
            stmt.setInt(1, clanId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao criar registro de upgrades", e);
        }
    }

    /**
     * Compra upgrade para um clã
     * Grug Brain: Thread-safe com synchronized (evita race conditions)
     * @return true se comprou com sucesso, false caso contrário
     */
    public synchronized boolean purchaseUpgrade(int clanId, UpgradeType type) {
        UpgradeData data = getUpgrades(clanId);
        int currentLevel = data.getLevel(type);

        // Verificar se já está no máximo
        if (currentLevel >= type.getMaxLevel()) {
            return false;
        }

        // Calcular custo
        long cost = type.getCostForLevel(currentLevel);
        if (cost < 0) {
            return false;
        }

        // Verificar saldo do clan bank (re-verificar após lock)
        long balance = plugin.getClansPlugin().getClansManager().getClanBalance(clanId);
        if (balance < cost) {
            return false; // Saldo insuficiente
        }

        // Remover dinheiro do bank
        if (!plugin.getClansPlugin().getClansManager().removeClanBalance(clanId, cost)) {
            return false; // Falha ao remover dinheiro
        }

        // Atualizar nível
        int newLevel = currentLevel + 1;
        data.setLevel(type, newLevel);

        // Atualizar cache imediatamente (otimismo - assume sucesso)
        upgradeCache.put(clanId, data);

        // Salvar no banco (async)
        saveUpgrade(clanId, type, newLevel);

        return true;
    }

    /**
     * Salva upgrade no banco
     * Grug Brain: Se falhar, faz rollback do cache e devolve dinheiro
     */
    private void saveUpgrade(int clanId, UpgradeType type, int level) {
        // Capturar valores antes de async (para rollback se necessário)
        final int oldLevel = level - 1;
        final long cost = type.getCostForLevel(oldLevel);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE faction_upgrades SET " + getColumnName(type) + " = ? WHERE clan_id = ?")) {
                stmt.setInt(1, level);
                stmt.setInt(2, clanId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao salvar upgrade, fazendo rollback", e);

                // Rollback: reverter cache e devolver dinheiro
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    UpgradeData data = getUpgrades(clanId);
                    data.setLevel(type, oldLevel);
                    upgradeCache.put(clanId, data);

                    // Devolver dinheiro ao clan bank
                    plugin.getClansPlugin().getClansManager().addClanBalance(clanId, cost);
                });
            }
        });
    }

    /**
     * Obtém nome da coluna no banco
     */
    private String getColumnName(UpgradeType type) {
        switch (type) {
            case SPAWNER_RATE:
                return "spawner_rate";
            case CROP_GROWTH:
                return "crop_growth";
            case EXP_BOOST:
                return "exp_boost";
            case EXTRA_SHIELD:
                return "extra_shield_hours";
            default:
                return "";
        }
    }

    /**
     * Cria GUI de upgrades
     */
    public Inventory createUpgradeGUI(Player player, int clanId) {
        Inventory inv = plugin.getServer().createInventory(null, 27, ChatColor.DARK_PURPLE + "Upgrades do Clã");

        UpgradeData data = getUpgrades(clanId);
        long balance = plugin.getClansPlugin().getClansManager().getClanBalance(clanId);

        // Slot 10: Spawner Rate
        inv.setItem(10, createUpgradeItem(UpgradeType.SPAWNER_RATE, data, balance, clanId));

        // Slot 12: Crop Growth
        inv.setItem(12, createUpgradeItem(UpgradeType.CROP_GROWTH, data, balance, clanId));

        // Slot 14: EXP Boost
        inv.setItem(14, createUpgradeItem(UpgradeType.EXP_BOOST, data, balance, clanId));

        // Slot 16: Extra Shield
        inv.setItem(16, createUpgradeItem(UpgradeType.EXTRA_SHIELD, data, balance, clanId));

        // Slot 22: Info do bank
        inv.setItem(22, createBankInfoItem(balance));

        return inv;
    }

    /**
     * Cria item de upgrade para GUI
     */
    private ItemStack createUpgradeItem(UpgradeType type, UpgradeData data, long balance, int clanId) {
        int currentLevel = data.getLevel(type);
        long cost = type.getCostForLevel(currentLevel);
        boolean canAfford = cost > 0 && balance >= cost;
        boolean isMax = currentLevel >= type.getMaxLevel();

        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + type.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Nível Atual: " + ChatColor.YELLOW + currentLevel + "/" + type.getMaxLevel());
        lore.add("");

        if (isMax) {
            lore.add(ChatColor.GREEN + "✓ Nível Máximo!");
        } else {
            String bonus = String.format(type.getFormat(), currentLevel + 1);
            lore.add(ChatColor.GRAY + "Próximo Nível: " + ChatColor.GREEN + "+" + bonus);
            lore.add("");
            lore.add(ChatColor.GRAY + "Custo: " + ChatColor.YELLOW + formatMoney(cost));

            if (canAfford) {
                lore.add(ChatColor.GREEN + "✓ Você pode comprar!");
            } else {
                lore.add(ChatColor.RED + "✗ Saldo insuficiente");
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Cria item de info do bank
     */
    private ItemStack createBankInfoItem(long balance) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Banco do Clã");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Saldo: " + ChatColor.YELLOW + formatMoney(balance));
        lore.add("");
        lore.add(ChatColor.GRAY + "Use /clan banco para depositar");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Formata dinheiro (centavos para reais)
     */
    private String formatMoney(long cents) {
        double reais = cents / 100.0;
        return String.format("$%.2f", reais);
    }

    /**
     * Obtém bônus de spawner rate (em percentual)
     */
    public double getSpawnerRateBonus(int clanId) {
        UpgradeData data = getUpgrades(clanId);
        return data.getSpawnerRate() * 5.0; // Cada nível = +5%
    }

    /**
     * Obtém bônus de crop growth (em percentual)
     */
    public double getCropGrowthBonus(int clanId) {
        UpgradeData data = getUpgrades(clanId);
        return data.getCropGrowth() * 5.0; // Cada nível = +5%
    }

    /**
     * Obtém bônus de EXP (em percentual)
     */
    public double getExpBoostBonus(int clanId) {
        UpgradeData data = getUpgrades(clanId);
        return data.getExpBoost() * 5.0; // Cada nível = +5%
    }

    /**
     * Obtém horas extras de shield
     */
    public int getExtraShieldHours(int clanId) {
        UpgradeData data = getUpgrades(clanId);
        return data.getExtraShieldHours();
    }

    /**
     * Invalida cache de upgrades (chamado quando upgrade é comprado)
     */
    public void invalidateCache(int clanId) {
        upgradeCache.remove(clanId);
    }
}

