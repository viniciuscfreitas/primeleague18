package com.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Sound;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin principal - Auto-kit e spawn na arena
 * Grug Brain: Tudo inline, parse direto do config
 */
public class ArenaPlugin extends JavaPlugin {

    /**
     * Estados do player na arena
     */
    public enum ArenaState {
        SPAWNED,    // Parado, invulnerável, aguardando movimento
        COUNTDOWN,  // Moveu, timer 5s rodando
        READY       // PvP liberado
    }

    // Map de estados dos players (thread-safe)
    private final Map<UUID, ArenaState> playerStates = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Salvar config padrão se não existir
        saveDefaultConfig();

        // Registrar listener
        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);

        // Registrar comando
        if (getCommand("arena") != null) {
            getCommand("arena").setExecutor(new ArenaCommand(this));
        }

        getLogger().info("ArenaPvP plugin habilitado");
    }

    @Override
    public void onDisable() {
        playerStates.clear();
        getLogger().info("ArenaPvP plugin desabilitado");
    }

    /**
     * Define o estado do player na arena
     */
    public void setPlayerState(Player player, ArenaState state) {
        if (state == null) {
            playerStates.remove(player.getUniqueId());
        } else {
            playerStates.put(player.getUniqueId(), state);
        }
    }

    /**
     * Obtém o estado do player
     * Retorna null se não estiver no map (não inicializado ainda)
     */
    public ArenaState getPlayerState(Player player) {
        return playerStates.get(player.getUniqueId());
    }

    /**
     * Verifica se a location está no mundo da arena
     * Grug Brain: Sempre é "world", simplificado
     */
    public boolean isArenaWorld(Location location) {
        return location != null && location.getWorld() != null && location.getWorld().getName().equals("world");
    }

    /**
     * Inicia countdown de 5 segundos após movimento
     * Action bar simplificado: 2-3 updates (início, meio, fim)
     */
    public void startCountdown(Player player) {
        setPlayerState(player, ArenaState.COUNTDOWN);

        // Mensagem inicial (UX padronizado)
        String prefixo = getConfig().getString("branding.prefixo", "§b[ARENA]");
        player.sendMessage(prefixo + " §eMovimento detectado. PvP será ativado em 5 segundos.");

        // Timer de 5 segundos (100 ticks)
        final ArenaPlugin plugin = this; // Referência para acessar config dentro do runnable
        new BukkitRunnable() {
            int secondsLeft = 5;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                ArenaState currentState = getPlayerState(player);
                if (currentState != ArenaState.COUNTDOWN) {
                    cancel();
                    return;
                }

                secondsLeft--;

                if (secondsLeft > 0) {
                    // Update no meio (3 segundos restantes)
                    if (secondsLeft == 3) {
                        String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[ARENA]");
                        player.sendMessage(prefixo + " §ePvP será ativado em 3 segundos. Prepare-se!");
                    }
                } else {
                    // Timer acabou - liberar PvP (UX padronizado)
                    setPlayerState(player, ArenaState.READY);
                    String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[ARENA]");
                    String brandingNome = plugin.getConfig().getString("branding.nome", "PRIME LEAGUE");
                    String brandingCor = plugin.getConfig().getString("branding.cor", "§b");
                    boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-titulos", true);

                    player.sendMessage(prefixo + " §aPvP ativado! Boa sorte na batalha!");

                    if (plugin.getConfig().getBoolean("ux.titulos", true)) {
                        try {
                            if (mostrarBranding) {
                                player.sendTitle(brandingCor + "§l" + brandingNome, "§a§lPvP ATIVADO §7Boa sorte!");
                            } else {
                                player.sendTitle("§a§lPvP ATIVADO", "§7Boa sorte!");
                            }
                        } catch (Exception e) {
                            player.sendMessage(prefixo + " §a§lPvP ATIVADO!");
                        }
                    }

                    if (plugin.getConfig().getBoolean("ux.sons", true)) {
                        // Sound.NOTE_BASS existe em 1.8.8
                        try {
                            player.playSound(player.getLocation(), Sound.NOTE_BASS, 1.0f, 1.0f);
                        } catch (Exception e) {
                            // Fallback para NOTE_PIANO se NOTE_BASS não existir
                            player.playSound(player.getLocation(), Sound.NOTE_PIANO, 1.0f, 1.0f);
                        }
                    }
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L); // A cada segundo (20 ticks)
    }

    /**
     * Obtém location da arena (helper para evitar duplicação)
     * Grug Brain: Método helper inline, sempre usa "world"
     */
    public Location getArenaLocation() {
        World world = Bukkit.getWorld("world");
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }
        double x = getConfig().getDouble("arena.x", 0.5);
        double y = getConfig().getDouble("arena.y", 64.0);
        double z = getConfig().getDouble("arena.z", 0.5);
        float yaw = (float) getConfig().getDouble("arena.yaw", 0.0);
        float pitch = (float) getConfig().getDouble("arena.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Teleporta player para arena (coordenadas do config)
     * Grug Brain: Usa helper para evitar duplicação
     */
    public void teleportToArena(Player player) {
        player.teleport(getArenaLocation());
    }

    /**
     * Dá kit completo ao player (limpa inventário e posiciona itens exatos)
     */
    public void giveKit(Player player) {
        // Limpar inventário e enderchest
        player.getInventory().clear();
        if (player.getEnderChest() != null) {
            player.getEnderChest().clear();
        }

        List<String> items = getConfig().getStringList("kit.items");

        for (String itemStr : items) {
            try {
                // Parse formato: MATERIAL:QUANTIDADE {ENCHANT:LEVEL,...} @SLOT
                // Exemplo: DIAMOND_SWORD:1 {DAMAGE_ALL:5,UNBREAKING:3} @2

                String trimmed = itemStr.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                // Separar slot (última parte após "@")
                int atIndex = trimmed.lastIndexOf("@");
                if (atIndex < 0) {
                    getLogger().warning("Formato inválido (sem @): " + itemStr);
                    continue;
                }

                int slot;
                try {
                    slot = Integer.parseInt(trimmed.substring(atIndex + 1).trim());
                } catch (NumberFormatException e) {
                    getLogger().warning("Slot inválido: " + itemStr);
                    continue;
                }

                // Parte antes do "@" (material:quantidade {enchants})
                String itemPart = trimmed.substring(0, atIndex).trim();

                // Separar material:quantidade dos enchants
                int braceStart = itemPart.indexOf("{");
                String materialPart;
                String enchantPart = "";

                if (braceStart >= 0) {
                    materialPart = itemPart.substring(0, braceStart).trim();
                    int braceEnd = itemPart.indexOf("}", braceStart);
                    if (braceEnd > braceStart) {
                        enchantPart = itemPart.substring(braceStart + 1, braceEnd).trim();
                    }
                } else {
                    materialPart = itemPart;
                }

                // Parse material:quantidade
                String[] materialParts = materialPart.split(":");
                if (materialParts.length != 2) {
                    getLogger().warning("Formato inválido (material:quantidade): " + itemStr);
                    continue;
                }

                Material material = Material.valueOf(materialParts[0].trim());
                int quantity = Integer.parseInt(materialParts[1].trim());

                // Pular AIR
                if (material == Material.AIR) {
                    continue;
                }

                ItemStack item = new ItemStack(material, quantity);

                // Aplicar enchants ou custom items
                if (enchantPart.contains("CUSTOM:")) {
                    // Poções customizadas ou enchanted golden apples
                    if (enchantPart.contains("STRENGTH_II_4MIN")) {
                        item = createStrengthPotion(quantity);
                    } else if (enchantPart.contains("SPEED_II_4MIN")) {
                        item = createSpeedPotion(quantity);
                    } else if (enchantPart.contains("ENCHANTED")) {
                        // Enchanted Golden Apple em 1.8.8
                        item = new ItemStack(Material.GOLDEN_APPLE, quantity, (short) 1);
                    }
                } else if (!enchantPart.isEmpty()) {
                    // Aplicar enchants normais - IMPORTANTE: obter meta ANTES de aplicar
                    ItemMeta meta = item.getItemMeta();
                    if (meta == null) {
                        getLogger().warning("ItemMeta é null para: " + material);
                        continue;
                    }

                    String[] enchants = enchantPart.split(",");
                    for (String enchantStr : enchants) {
                        String[] enchantParts = enchantStr.trim().split(":");
                        if (enchantParts.length == 2) {
                            try {
                                String enchantName = enchantParts[0].trim();
                                // Mapear nomes do config para nomes da API
                                Enchantment enchant = getEnchantmentByName(enchantName);
                                if (enchant == null) {
                                    getLogger().warning("Enchant não encontrado: " + enchantName);
                                    continue;
                                }
                                int level = Integer.parseInt(enchantParts[1].trim());
                                meta.addEnchant(enchant, level, true);
                            } catch (Exception e) {
                                getLogger().warning("Erro ao aplicar enchant: " + enchantStr + " - " + e.getMessage());
                            }
                        }
                    }
                    item.setItemMeta(meta);
                }

                // Posicionar item no slot exato
                if (slot >= 0 && slot < 40) {
                    if (slot >= 36 && slot <= 39) {
                        // Armor slots (36=boots, 37=legs, 38=chest, 39=helmet)
                        player.getInventory().setItem(slot, item);
                    } else {
                        // Inventory slots (0-35)
                        player.getInventory().setItem(slot, item);
                    }
                }

            } catch (Exception e) {
                getLogger().warning("Erro ao processar item do config: " + itemStr);
                getLogger().warning("Erro: " + e.getMessage());
            }
        }

        // Atualizar inventário
        player.updateInventory();
    }

    /**
     * Cria poção de Strength II com 4 minutos de duração (4800 ticks)
     * Em 1.8.8, usa PotionMeta.addCustomEffect() para duração customizada
     */
    private ItemStack createStrengthPotion(int quantity) {
        // Criar poção base
        Potion potion = new Potion(PotionType.STRENGTH);
        potion.setLevel(2); // Strength II
        potion.setSplash(false); // Poção bebível
        potion.setHasExtendedDuration(false);

        ItemStack item = potion.toItemStack(quantity);
        PotionMeta meta = (PotionMeta) item.getItemMeta();

        if (meta != null) {
            // Adicionar efeito customizado com 4 minutos (4800 ticks)
            // Strength II = INCREASE_DAMAGE level 1 (amplifier 1 = level II)
            meta.addCustomEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 4800, 1), true);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Cria poção de Speed II com 4 minutos de duração (4800 ticks)
     * Em 1.8.8, usa PotionMeta.addCustomEffect() para duração customizada
     */
    private ItemStack createSpeedPotion(int quantity) {
        // Criar poção base
        Potion potion = new Potion(PotionType.SPEED);
        potion.setLevel(2); // Speed II
        potion.setSplash(false); // Poção bebível
        potion.setHasExtendedDuration(false);

        ItemStack item = potion.toItemStack(quantity);
        PotionMeta meta = (PotionMeta) item.getItemMeta();

        if (meta != null) {
            // Adicionar efeito customizado com 4 minutos (4800 ticks)
            // Speed II = SPEED level 1 (amplifier 1 = level II)
            meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 4800, 1), true);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Obtém enchantment pelo nome (com mapeamento para compatibilidade)
     */
    private Enchantment getEnchantmentByName(String name) {
        // Tentar nome direto primeiro
        Enchantment enchant = Enchantment.getByName(name);
        if (enchant != null) {
            return enchant;
        }

        // Mapear nomes do config para nomes da API 1.8.8
        switch (name.toUpperCase()) {
            case "PROTECTION":
                return Enchantment.getByName("PROTECTION_ENVIRONMENTAL");
            case "UNBREAKING":
                return Enchantment.getByName("DURABILITY");
            case "DAMAGE_ALL":
                return Enchantment.getByName("DAMAGE_ALL");
            case "KNOCKBACK":
                return Enchantment.getByName("KNOCKBACK");
            default:
                // Tentar variações comuns
                if (name.toUpperCase().contains("PROTECTION")) {
                    return Enchantment.getByName("PROTECTION_ENVIRONMENTAL");
                }
                if (name.toUpperCase().contains("UNBREAKING") || name.toUpperCase().contains("DURABILITY")) {
                    return Enchantment.getByName("DURABILITY");
                }
                return null;
        }
    }
}

