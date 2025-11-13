package com.security;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Listener de segurança - Bloqueia ações não permitidas
 * Grug Brain: Tudo inline, sem abstrações
 */
public class SecurityListener implements Listener {

    private final SecurityPlugin plugin;

    public SecurityListener(SecurityPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Bloqueia interações com blocos do mundo (baús, portas, redstone, etc)
     * Grug Brain: Lógica inline, verificação direta
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Verificar se deve bloquear interações com blocos
        if (!plugin.getConfig().getBoolean("bloquear.interacoes-bloco", true)) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        Action action = event.getAction();

        // Bloquear apenas interações com blocos (não com itens no ar)
        if (clickedBlock != null && action != Action.PHYSICAL) {
            // Verificar se player está usando item (espada para bloquear, poção, etc)
            Player player = event.getPlayer();
            ItemStack item = player.getItemInHand();
            if (item != null && item.getType() != Material.AIR) {
                // Se tem item na mão, permitir interação (bloqueio com espada, uso de poções, etc)
                // Isso permite usar itens mesmo clicando em blocos
                return;
            } else {
                // Sem item na mão, bloquear interação com bloco (abrir baús, portas, etc)
                event.setCancelled(true);
            }
        }
        // Se action é RIGHT_CLICK_AIR ou LEFT_CLICK_AIR, não bloquear (uso de itens)
    }

    /**
     * Bloqueia pickup de itens do chão
     * Grug Brain: Lógica inline, bloqueio direto
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        // Verificar se deve bloquear pickup
        if (!plugin.getConfig().getBoolean("bloquear.pickup-itens", true)) {
            return;
        }

        // Bloquear pickup completamente (kit é fixo)
        event.setCancelled(true);
    }

    /**
     * Bloqueia abertura de inventários especiais (ender chest, crafting, etc)
     * Grug Brain: Lógica inline, verificação direta do tipo
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        // Verificar se deve bloquear inventários especiais
        if (!plugin.getConfig().getBoolean("bloquear.inventarios-especiais", true)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        InventoryType inventoryType = event.getInventory().getType();

        // Permitir apenas inventário normal do player
        if (inventoryType != InventoryType.PLAYER) {
            // Bloquear todos os outros tipos (CHEST, ENDER_CHEST, CRAFTING, FURNACE, etc)
            event.setCancelled(true);
        }
    }

    /**
     * Bloqueia comandos não permitidos
     * Grug Brain: Parse direto do config, lógica inline
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        // Verificar se deve bloquear comandos desconhecidos
        if (!plugin.getConfig().getBoolean("bloquear.comandos-desconhecidos", true)) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.length() < 2 || !message.startsWith("/")) {
            return;
        }

        // Extrair comando (remover "/" e pegar primeira palavra)
        String command = message.substring(1).split(" ")[0].toLowerCase();

        // Obter lista de comandos permitidos do config
        List<String> comandosPermitidos = plugin.getConfig().getStringList("comandos-permitidos");

        // Verificar se comando está na lista permitida
        boolean permitido = false;
        for (String cmdPermitido : comandosPermitidos) {
            if (cmdPermitido.equalsIgnoreCase(command)) {
                permitido = true;
                break;
            }
        }

        // Se está na lista permitida, não bloquear
        if (permitido) {
            return;
        }

        // Bloquear comandos sensíveis (sempre bloquear, mesmo se não estiver na lista)
        Player player = event.getPlayer();
        if (command.equals("plugins") || command.equals("pl") ||
            command.equals("version") || command.equals("ver") ||
            command.equals("about") || command.equals("bukkit") ||
            command.equals("spigot") || command.equals("paper")) {
            event.setCancelled(true);
            player.sendMessage("§cEste comando não está disponível.");
            return;
        }

        // Bloquear se não está na lista permitida
        event.setCancelled(true);
        player.sendMessage("§cEste comando não está disponível.");
    }
}

