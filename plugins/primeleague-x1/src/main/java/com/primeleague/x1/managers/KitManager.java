package com.primeleague.x1.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Kit;
import com.primeleague.x1.utils.KitSerializer;
import com.primeleague.x1.utils.X1Utils;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Kits
 * Grug Brain: Carrega/salva kits do PostgreSQL (JSONB), validação de items
 */
public class KitManager {

    private final X1Plugin plugin;
    private final Map<String, Kit> kits;

    public KitManager(X1Plugin plugin) {
        this.plugin = plugin;
        this.kits = new ConcurrentHashMap<>();
    }

    /**
     * Carrega kits do banco de dados
     */
    public void loadKits() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT name, items, armor, effects, enabled FROM x1_kits WHERE enabled = true");
                    
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        String name = rs.getString("name");
                        Kit kit = new Kit(name);
                        
                        // Carregar items do JSONB (PostgreSQL retorna JSONB como String)
                        Object itemsObj = rs.getObject("items");
                        if (itemsObj != null) {
                            String itemsJson = itemsObj.toString();
                            if (!itemsJson.isEmpty() && !itemsJson.equals("{}") && !itemsJson.equals("null")) {
                                ItemStack[] items = KitSerializer.deserializeItems(itemsJson, 36);
                                kit.setItems(items);
                            }
                        }
                        
                        // Carregar armor do JSONB
                        Object armorObj = rs.getObject("armor");
                        if (armorObj != null) {
                            String armorJson = armorObj.toString();
                            if (!armorJson.isEmpty() && !armorJson.equals("{}") && !armorJson.equals("null")) {
                                ItemStack[] armor = KitSerializer.deserializeItems(armorJson, 4);
                                kit.setArmor(armor);
                            }
                        }
                        
                        // Carregar effects do JSONB
                        Object effectsObj = rs.getObject("effects");
                        if (effectsObj != null) {
                            String effectsJson = effectsObj.toString();
                            if (!effectsJson.isEmpty() && !effectsJson.equals("{}") && !effectsJson.equals("null")) {
                                PotionEffect[] effects = KitSerializer.deserializeEffects(effectsJson);
                                kit.setEffects(effects);
                            }
                        }
                        
                        kit.setEnabled(rs.getBoolean("enabled"));
                        kits.put(X1Utils.normalizeName(name), kit);
                    }
                    
                    plugin.getLogger().info("Carregados " + kits.size() + " kits do banco de dados");
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao carregar kits: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Obtém kit por nome
     */
    public Kit getKit(String name) {
        if (name == null) {
            return null;
        }
        return kits.get(X1Utils.normalizeName(name));
    }

    /**
     * Salva kit no banco de dados (adiciona ao cache imediatamente)
     */
    public void saveKit(Kit kit) {
        // Validar nome
        if (!X1Utils.isValidName(kit.getName())) {
            throw new IllegalArgumentException("Nome de kit inválido: " + kit.getName());
        }

        // Adicionar ao cache imediatamente
        kits.put(X1Utils.normalizeName(kit.getName()), kit);

        // Salvar no banco async
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO x1_kits (name, items, armor, effects, enabled) VALUES (?, CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), ?) " +
                        "ON CONFLICT (name) DO UPDATE SET items = EXCLUDED.items, armor = EXCLUDED.armor, " +
                        "effects = EXCLUDED.effects, enabled = EXCLUDED.enabled");
                    
                    stmt.setString(1, kit.getName());
                    // Serializar items/armor/effects para JSONB
                    String itemsJson = kit.getItems() != null ? KitSerializer.serializeItems(kit.getItems()) : "[]";
                    String armorJson = kit.getArmor() != null ? KitSerializer.serializeItems(kit.getArmor()) : "[]";
                    String effectsJson = kit.getEffects() != null ? KitSerializer.serializeEffects(kit.getEffects()) : "[]";
                    stmt.setString(2, itemsJson);
                    stmt.setString(3, armorJson);
                    stmt.setString(4, effectsJson);
                    stmt.setBoolean(5, kit.isEnabled());
                    
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao salvar kit: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Deleta kit
     */
    public void deleteKit(String name) {
        if (!X1Utils.isValidName(name)) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM x1_kits WHERE name = ?");
                    stmt.setString(1, name);
                    stmt.executeUpdate();
                    
                    kits.remove(X1Utils.normalizeName(name));
                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao deletar kit: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Lista todos os kits
     */
    public Collection<Kit> getAllKits() {
        return kits.values();
    }
}

