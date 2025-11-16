package com.primeleague.x1.utils;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializador de Kits para JSONB
 * Grug Brain: Serialização simples, formato customizado para 1.8.8
 */
public class KitSerializer {

    /**
     * Serializa ItemStack[] para JSON string
     */
    public static String serializeItems(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            if (!first) {
                sb.append(",");
            }
            first = false;

            sb.append("{");
            sb.append("\"slot\":").append(i).append(",");
            sb.append("\"type\":\"").append(item.getType().name()).append("\",");
            sb.append("\"amount\":").append(item.getAmount()).append(",");
            sb.append("\"durability\":").append(item.getDurability());

            // Enchants
            if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                sb.append(",\"enchants\":{");
                boolean firstEnchant = true;
                for (Map.Entry<Enchantment, Integer> enchant : item.getItemMeta().getEnchants().entrySet()) {
                    if (!firstEnchant) {
                        sb.append(",");
                    }
                    firstEnchant = false;
                    sb.append("\"").append(enchant.getKey().getName()).append("\":").append(enchant.getValue());
                }
                sb.append("}");
            }

            sb.append("}");
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Deserializa JSON string para ItemStack[]
     * Grug Brain: Parsing mais robusto, trata edge cases
     */
    public static ItemStack[] deserializeItems(String json, int size) {
        if (json == null || json.isEmpty() || json.equals("[]") || json.equals("{}") || json.equals("null")) {
            return new ItemStack[size];
        }

        ItemStack[] items = new ItemStack[size];
        
        // Parse simples (formato: [{"slot":0,"type":"DIAMOND_SWORD","amount":1,"durability":0,"enchants":{"DAMAGE_ALL":5}},...])
        try {
            // Remover [ e ]
            String content = json.trim();
            if (content.startsWith("[")) {
                content = content.substring(1);
            }
            if (content.endsWith("]")) {
                content = content.substring(0, content.length() - 1);
            }

            if (content.trim().isEmpty()) {
                return items;
            }

            // Dividir por },{ (items separados) - mais robusto
            // Usar regex para dividir corretamente, considerando nested objects
            List<String> itemStrings = new ArrayList<>();
            int depth = 0;
            StringBuilder current = new StringBuilder();
            
            for (char c : content.toCharArray()) {
                if (c == '{') {
                    depth++;
                    if (depth == 1 && current.length() > 0) {
                        // Novo item começando
                        String prev = current.toString().trim();
                        if (!prev.isEmpty()) {
                            itemStrings.add(prev);
                        }
                        current = new StringBuilder();
                    }
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        // Item completo
                        current.append(c);
                        itemStrings.add(current.toString().trim());
                        current = new StringBuilder();
                        continue;
                    }
                }
                if (depth > 0 || (depth == 0 && c != ',')) {
                    current.append(c);
                }
            }
            
            // Adicionar último item se houver
            if (current.length() > 0) {
                String last = current.toString().trim();
                if (!last.isEmpty()) {
                    itemStrings.add(last);
                }
            }
            
            for (String itemStr : itemStrings) {
                // Limpar chaves externas
                itemStr = itemStr.trim();
                if (itemStr.startsWith("{")) {
                    itemStr = itemStr.substring(1);
                }
                if (itemStr.endsWith("}")) {
                    itemStr = itemStr.substring(0, itemStr.length() - 1);
                }
                itemStr = itemStr.trim();
                
                if (itemStr.isEmpty()) {
                    continue;
                }

                int slot = -1;
                Material type = null;
                int amount = 1;
                short durability = 0;
                Map<Enchantment, Integer> enchants = new HashMap<>();

                // Parse campos - dividir por vírgula, mas respeitar nested objects
                List<String> fields = new ArrayList<>();
                int fieldDepth = 0;
                StringBuilder fieldBuilder = new StringBuilder();
                
                for (char c : itemStr.toCharArray()) {
                    if (c == '{') {
                        fieldDepth++;
                    } else if (c == '}') {
                        fieldDepth--;
                    } else if (c == ',' && fieldDepth == 0) {
                        fields.add(fieldBuilder.toString().trim());
                        fieldBuilder = new StringBuilder();
                        continue;
                    }
                    fieldBuilder.append(c);
                }
                if (fieldBuilder.length() > 0) {
                    fields.add(fieldBuilder.toString().trim());
                }
                
                for (String field : fields) {
                    field = field.trim();
                    if (field.isEmpty()) {
                        continue;
                    }

                    // Encontrar primeiro :
                    int colonIndex = field.indexOf(':');
                    if (colonIndex <= 0) {
                        continue;
                    }
                    
                    String key = field.substring(0, colonIndex).trim().replace("\"", "").replace("'", "");
                    String value = field.substring(colonIndex + 1).trim();

                    switch (key) {
                        case "slot":
                            try {
                            slot = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                // Ignorar
                            }
                            break;
                        case "type":
                            try {
                                value = value.replace("\"", "").replace("'", "");
                                type = Material.valueOf(value);
                            } catch (IllegalArgumentException e) {
                                // Material inválido - ignorar
                            }
                            break;
                        case "amount":
                            try {
                            amount = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                // Ignorar
                            }
                            break;
                        case "durability":
                            try {
                            durability = Short.parseShort(value);
                            } catch (NumberFormatException e) {
                                // Ignorar
                            }
                            break;
                        case "enchants":
                            // Parse enchants (formato: {"DAMAGE_ALL":5})
                            try {
                                value = value.trim();
                                if (value.startsWith("{")) {
                                    value = value.substring(1);
                                }
                                if (value.endsWith("}")) {
                                    value = value.substring(0, value.length() - 1);
                                }
                                
                                // Dividir por vírgula
                                String[] enchantPairs = value.split(",");
                                for (String pair : enchantPairs) {
                                    String[] parts = pair.split(":");
                                    if (parts.length == 2) {
                                        String enchantName = parts[0].trim().replace("\"", "").replace("'", "");
                                        int level = Integer.parseInt(parts[1].trim());
                                        
                                        Enchantment enchant = Enchantment.getByName(enchantName);
                                        if (enchant != null) {
                                            enchants.put(enchant, level);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Erro ao parsear enchants - ignorar
                            }
                            break;
                    }
                }

                if (type != null && slot >= 0 && slot < size) {
                    ItemStack item = new ItemStack(type, amount, durability);
                    
                    // Aplicar enchants
                    if (!enchants.isEmpty() && item.getItemMeta() != null) {
                        ItemMeta meta = item.getItemMeta();
                        for (Map.Entry<Enchantment, Integer> enchant : enchants.entrySet()) {
                            meta.addEnchant(enchant.getKey(), enchant.getValue(), true);
                        }
                        item.setItemMeta(meta);
                    }
                    
                    items[slot] = item;
                }
            }
        } catch (Exception e) {
            // Erro ao parsear - retornar array vazio
            e.printStackTrace();
        }

        return items;
    }

    /**
     * Serializa PotionEffect[] para JSON string
     */
    public static String serializeEffects(PotionEffect[] effects) {
        if (effects == null || effects.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (PotionEffect effect : effects) {
            if (effect == null) {
                continue;
            }

            if (!first) {
                sb.append(",");
            }
            first = false;

            sb.append("{");
            sb.append("\"type\":\"").append(effect.getType().getName()).append("\",");
            sb.append("\"duration\":").append(effect.getDuration()).append(",");
            sb.append("\"amplifier\":").append(effect.getAmplifier()).append(",");
            sb.append("\"ambient\":").append(effect.isAmbient());
            sb.append("}");
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Deserializa JSON string para PotionEffect[]
     * Grug Brain: Parsing mais robusto, trata edge cases
     */
    public static PotionEffect[] deserializeEffects(String json) {
        if (json == null || json.isEmpty() || json.equals("[]") || json.equals("{}") || json.equals("null")) {
            return new PotionEffect[0];
        }

        List<PotionEffect> effects = new ArrayList<>();

        try {
            // Remover [ e ]
            String content = json.trim();
            if (content.startsWith("[")) {
                content = content.substring(1);
            }
            if (content.endsWith("]")) {
                content = content.substring(0, content.length() - 1);
            }

            if (content.trim().isEmpty()) {
                return new PotionEffect[0];
            }

            // Dividir por },{ (effects separados) - mais robusto
            List<String> effectStrings = new ArrayList<>();
            int depth = 0;
            StringBuilder current = new StringBuilder();
            
            for (char c : content.toCharArray()) {
                if (c == '{') {
                    depth++;
                    if (depth == 1 && current.length() > 0) {
                        String prev = current.toString().trim();
                        if (!prev.isEmpty()) {
                            effectStrings.add(prev);
                        }
                        current = new StringBuilder();
                    }
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        current.append(c);
                        effectStrings.add(current.toString().trim());
                        current = new StringBuilder();
                        continue;
                    }
                }
                if (depth > 0 || (depth == 0 && c != ',')) {
                    current.append(c);
                }
            }
            
            if (current.length() > 0) {
                String last = current.toString().trim();
                if (!last.isEmpty()) {
                    effectStrings.add(last);
                }
            }

            for (String effectStr : effectStrings) {
                // Limpar chaves externas
                effectStr = effectStr.trim();
                if (effectStr.startsWith("{")) {
                    effectStr = effectStr.substring(1);
                }
                if (effectStr.endsWith("}")) {
                    effectStr = effectStr.substring(0, effectStr.length() - 1);
                }
                effectStr = effectStr.trim();
                
                if (effectStr.isEmpty()) {
                    continue;
                }

                PotionEffectType type = null;
                int duration = 0;
                int amplifier = 0;
                boolean ambient = false;

                // Parse campos
                String[] fields = effectStr.split(",");
                for (String field : fields) {
                    field = field.trim();
                    if (field.isEmpty()) {
                        continue;
                    }

                    int colonIndex = field.indexOf(':');
                    if (colonIndex <= 0) {
                        continue;
                    }
                    
                    String key = field.substring(0, colonIndex).trim().replace("\"", "").replace("'", "");
                    String value = field.substring(colonIndex + 1).trim().replace("\"", "").replace("'", "");

                    switch (key) {
                        case "type":
                            try {
                                type = PotionEffectType.getByName(value);
                            } catch (Exception e) {
                                // Tipo inválido - ignorar
                            }
                            break;
                        case "duration":
                            try {
                            duration = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                // Ignorar
                            }
                            break;
                        case "amplifier":
                            try {
                            amplifier = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                // Ignorar
                            }
                            break;
                        case "ambient":
                            ambient = Boolean.parseBoolean(value);
                            break;
                    }
                }

                if (type != null) {
                    effects.add(new PotionEffect(type, duration, amplifier, ambient));
                }
            }
        } catch (Exception e) {
            // Erro ao parsear - retornar array vazio
            e.printStackTrace();
        }

        return effects.toArray(new PotionEffect[0]);
    }
}

