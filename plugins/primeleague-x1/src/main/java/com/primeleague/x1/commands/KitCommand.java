package com.primeleague.x1.commands;

import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Kit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando /kit - Gerenciar kits
 * Grug Brain: Comandos simples, validação inline
 */
public class KitCommand implements CommandExecutor {

    private final X1Plugin plugin;

    public KitCommand(X1Plugin plugin) {
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
            // Listar kits
            return handleList(player);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                return handleList(player);
            case "create":
                if (!player.hasPermission("x1.kit.create")) {
                    player.sendMessage(ChatColor.RED + "Você não tem permissão para criar kits.");
                    return true;
                }
                return handleCreate(player, args);
            case "delete":
                if (!player.hasPermission("x1.kit.delete")) {
                    player.sendMessage(ChatColor.RED + "Você não tem permissão para deletar kits.");
                    return true;
                }
                return handleDelete(player, args);
            case "apply":
                if (!player.hasPermission("x1.kit.apply")) {
                    player.sendMessage(ChatColor.RED + "Você não tem permissão para aplicar kits.");
                    return true;
                }
                return handleApply(player, args);
            default:
                // Aplicar kit (comando curto: /kit <name>)
                return handleApply(player, args);
        }
    }

    /**
     * /kit list
     */
    private boolean handleList(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Kits Disponíveis" + ChatColor.GOLD + " ===");
        for (Kit kit : plugin.getKitManager().getAllKits()) {
            if (kit.isEnabled()) {
                player.sendMessage(ChatColor.YELLOW + "- " + kit.getName());
            }
        }
        return true;
    }

    /**
     * /kit create <name>
     */
    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /kit create <name>");
            return true;
        }

        String name = args[1];
        
        // Validar nome
        if (!name.matches("^[a-zA-Z0-9_]{1,50}$")) {
            player.sendMessage(ChatColor.RED + "Nome inválido. Use apenas letras, números e underscore (1-50 caracteres).");
            return true;
        }

        // Criar kit do inventário atual
        Kit kit = new Kit(name);
        kit.setItems(player.getInventory().getContents());
        kit.setArmor(player.getInventory().getArmorContents());
        // Effects serão adicionados via GUI editor (Fase 5)

        plugin.getKitManager().saveKit(kit);
        player.sendMessage(ChatColor.GREEN + "Kit criado: " + name);
        
        return true;
    }

    /**
     * /kit delete <name>
     */
    private boolean handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /kit delete <name>");
            return true;
        }

        String name = args[1];
        plugin.getKitManager().deleteKit(name);
        player.sendMessage(ChatColor.GREEN + "Kit deletado: " + name);
        
        return true;
    }

    /**
     * /kit apply <name> ou /kit <name>
     */
    private boolean handleApply(Player player, String[] args) {
        String name = args.length > 1 ? args[1] : args[0];
        
        Kit kit = plugin.getKitManager().getKit(name);
        if (kit == null) {
            player.sendMessage(plugin.getConfig().getString("messages.error.invalid-kit", 
                "§cKit inválido: {kit}").replace("{kit}", name));
            return true;
        }

        // Aplicar kit
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        
        // Remover effects
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        if (kit.getItems() != null) {
            player.getInventory().setContents(kit.getItems());
        }
        if (kit.getArmor() != null) {
            player.getInventory().setArmorContents(kit.getArmor());
        }
        if (kit.getEffects() != null) {
            for (org.bukkit.potion.PotionEffect effect : kit.getEffects()) {
                player.addPotionEffect(effect);
            }
        }

        player.updateInventory();
        player.sendMessage(ChatColor.GREEN + "Kit aplicado: " + name);
        
        return true;
    }
}

