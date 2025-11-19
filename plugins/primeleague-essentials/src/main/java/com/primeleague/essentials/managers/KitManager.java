package com.primeleague.essentials.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.essentials.EssentialsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KitManager {

    private static KitManager instance;
    private File kitsFile;
    private FileConfiguration kitsConfig;

    private KitManager() {
        kitsFile = new File(EssentialsPlugin.getInstance().getDataFolder(), "kits.yml");
        if (!kitsFile.exists()) {
            try {
                kitsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
    }

    public static KitManager getInstance() {
        if (instance == null) {
            instance = new KitManager();
        }
        return instance;
    }

    public void createKit(String name, Player player, long delaySeconds) {
        String path = "kits." + name.toLowerCase();
        kitsConfig.set(path + ".delay", delaySeconds);
        
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }
        kitsConfig.set(path + ".items", items);
        
        // Armor
        List<ItemStack> armor = new ArrayList<>();
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) {
                armor.add(item);
            }
        }
        kitsConfig.set(path + ".armor", armor);

        saveConfig();
    }

    public boolean giveKit(Player player, String kitName) {
        String path = "kits." + kitName.toLowerCase();
        if (!kitsConfig.contains(path)) return false;

        // Verificar cooldown
        long delay = kitsConfig.getLong(path + ".delay");
        if (delay > 0) {
            long lastUsed = getLastUsed(player.getUniqueId(), kitName);
            long nextUse = lastUsed + (delay * 1000);
            if (System.currentTimeMillis() < nextUse) {
                long remaining = (nextUse - System.currentTimeMillis()) / 1000;
                player.sendMessage(ChatColor.RED + "Aguarde " + remaining + "s para usar este kit novamente.");
                return true; // Kit existe, mas está em cooldown
            }
        }

        // Dar itens
        List<?> items = kitsConfig.getList(path + ".items");
        if (items != null) {
            for (Object obj : items) {
                if (obj instanceof ItemStack) {
                    player.getInventory().addItem((ItemStack) obj);
                }
            }
        }

        // Dar armadura (se estiver vazio)
        List<?> armor = kitsConfig.getList(path + ".armor");
        if (armor != null) {
            ItemStack[] currentArmor = player.getInventory().getArmorContents();
            int i = 0;
            for (Object obj : armor) {
                if (obj instanceof ItemStack && i < currentArmor.length) {
                    if (currentArmor[i] == null || currentArmor[i].getType() == Material.AIR) {
                        // TODO: Lógica melhor para armadura, mas Grug Brain diz: só dá se tiver vazio ou add no inv
                        // Simplificação: Adiciona no inventário se não tiver espaço na armadura
                        player.getInventory().addItem((ItemStack) obj);
                    }
                    i++;
                }
            }
        }

        // Atualizar cooldown
        if (delay > 0) {
            setLastUsed(player.getUniqueId(), kitName);
        }

        return true;
    }

    public List<String> getKits() {
        if (kitsConfig.getConfigurationSection("kits") == null) return new ArrayList<>();
        return new ArrayList<>(kitsConfig.getConfigurationSection("kits").getKeys(false));
    }

    private void saveConfig() {
        try {
            kitsConfig.save(kitsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Database Cooldowns
    private long getLastUsed(UUID playerUuid, String kitName) {
        String sql = "SELECT last_used FROM user_kit_cooldowns WHERE player_uuid = ? AND kit_name = ?";
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, playerUuid);
            stmt.setString(2, kitName.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("last_used").getTime();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void setLastUsed(UUID playerUuid, String kitName) {
        String sql = "INSERT INTO user_kit_cooldowns (player_uuid, kit_name, last_used) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (player_uuid, kit_name) DO UPDATE SET last_used = CURRENT_TIMESTAMP";
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, playerUuid);
            stmt.setString(2, kitName.toLowerCase());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
