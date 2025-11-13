package com.stats;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comandos de stats - /stats e /top
 * Grug Brain: Tudo inline, sem abstrações
 */
public class StatsCommand implements CommandExecutor {

    private final StatsPlugin plugin;

    public StatsCommand(StatsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("stats")) {
            return handleStats(sender);
        } else if (cmdName.equals("top")) {
            return handleTop(sender, args);
        }

        return false;
    }

    /**
     * Comando /stats - Mostra stats do player
     */
    private boolean handleStats(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas players podem usar este comando!");
            return true;
        }

        Player player = (Player) sender;
        PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());

        // Header com branding Prime League
        String brandingNome = plugin.getConfig().getString("branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("branding.cor", "§b");

        player.sendMessage(brandingCor + "§l=== " + brandingNome + " STATS ===");
        player.sendMessage(ChatColor.YELLOW + "Kills: " + ChatColor.WHITE + stats.kills);
        player.sendMessage(ChatColor.YELLOW + "Deaths: " + ChatColor.WHITE + stats.deaths);
        player.sendMessage(ChatColor.YELLOW + "KDR: " + ChatColor.WHITE + String.format("%.2f", stats.getKDR()));

        return true;
    }

    /**
     * Comando /top [kills|kdr] - Mostra top players
     */
    private boolean handleTop(CommandSender sender, String[] args) {
        String ordenarPor = "kdr";
        if (args.length > 0) {
            String arg = args[0].toLowerCase();
            if (arg.equals("kills") || arg.equals("kill")) {
                ordenarPor = "kills";
            } else if (arg.equals("kdr") || arg.equals("ratio")) {
                ordenarPor = "kdr";
            }
        }

        // Obter top players
        int topMostrar = plugin.getConfig().getInt("scoreboard.top-mostrar", 10);
        List<Map.Entry<UUID, PlayerStats>> topPlayers = getTopPlayers(topMostrar, ordenarPor);

        if (topPlayers.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Nenhum player com stats ainda!");
            return true;
        }

        // Header com branding Prime League
        String brandingNome = plugin.getConfig().getString("branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("branding.cor", "§b");
        String ordenarPorNome = ordenarPor.equals("kills") ? "KILLS" : "KDR";

        sender.sendMessage(brandingCor + "§l=== " + brandingNome + " RANKING (" + ordenarPorNome + ") ===");

        // Mostrar top players
        int posicao = 1;
        for (Map.Entry<UUID, PlayerStats> entry : topPlayers) {
            UUID uuid = entry.getKey();
            PlayerStats stats = entry.getValue();

            Player topPlayer = Bukkit.getPlayer(uuid);
            if (topPlayer == null) {
                continue;
            }

            String corNumero = plugin.getConfig().getString("scoreboard.cor-numero", "§b");
            String corNome = plugin.getConfig().getString("scoreboard.cor-nome", "§b");
            String corKdr = plugin.getConfig().getString("scoreboard.cor-kdr", "§7");

            if (ordenarPor.equals("kills")) {
                sender.sendMessage(String.format("%s#%d %s%s %s(%d kills)",
                    corNumero, posicao, corNome, topPlayer.getName(), corKdr, stats.kills));
            } else {
                sender.sendMessage(String.format("%s#%d %s%s %s(%.2f KDR)",
                    corNumero, posicao, corNome, topPlayer.getName(), corKdr, stats.getKDR()));
            }

            posicao++;
        }

        return true;
    }

    /**
     * Obtém top players ordenados (usa método do plugin)
     */
    private List<Map.Entry<UUID, PlayerStats>> getTopPlayers(int limit, String ordenarPor) {
        return plugin.getTopPlayers(limit, ordenarPor);
    }
}

