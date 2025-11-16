package com.primeleague.x1.commands;

import com.primeleague.x1.X1Plugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando /arena - Gerenciar arenas (admin)
 * Grug Brain: Comandos simples, validação inline
 */
public class ArenaCommand implements CommandExecutor {

    private final X1Plugin plugin;

    public ArenaCommand(X1Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("x1.arena.admin")) {
            player.sendMessage(ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Uso: /arena [list|create|delete] <name>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                return handleList(player);
            case "create":
                return handleCreate(player, args);
            case "delete":
                return handleDelete(player, args);
            default:
                player.sendMessage(ChatColor.RED + "Uso: /arena [list|create|delete] <name>");
                return true;
        }
    }

    /**
     * /arena list
     */
    private boolean handleList(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Arenas" + ChatColor.GOLD + " ===");
        for (com.primeleague.x1.models.Arena arena : plugin.getArenaManager().getAllArenas()) {
            String status = arena.isInUse() ? ChatColor.RED + "Em uso" : ChatColor.GREEN + "Disponível";
            player.sendMessage(ChatColor.YELLOW + "- " + arena.getName() + " " + status);
        }
        return true;
    }

    /**
     * /arena create <name>
     * Cria arena na localização atual do player (spawn1 = player, spawn2 = player + 10 blocks, center = player)
     */
    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /arena create <name>");
            player.sendMessage(ChatColor.YELLOW + "Nota: Spawn1 = sua posição, Spawn2 = sua posição + 10 blocks à frente");
            return true;
        }

        String name = args[1];
        
        // Validar nome
        if (!name.matches("^[a-zA-Z0-9_]{1,50}$")) {
            player.sendMessage(ChatColor.RED + "Nome inválido. Use apenas letras, números e underscore (1-50 caracteres).");
            return true;
        }

        // Criar locações (simplificado - spawn2 será 10 blocks à frente)
        org.bukkit.Location spawn1 = player.getLocation();
        org.bukkit.Location spawn2 = spawn1.clone().add(spawn1.getDirection().multiply(10));
        org.bukkit.Location center = spawn1.clone();

        try {
            plugin.getArenaManager().createArena(name, spawn1, spawn2, center);
            player.sendMessage(ChatColor.GREEN + "Arena criada: " + name);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + e.getMessage());
        }
        
        return true;
    }

    /**
     * /arena delete <name>
     */
    private boolean handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /arena delete <name>");
            return true;
        }

        String name = args[1];
        plugin.getArenaManager().deleteArena(name);
        player.sendMessage(ChatColor.GREEN + "Arena deletada: " + name);
        
        return true;
    }
}

