package com.primeleague.stats.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.stats.StatsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Comandos de stats - /stats e /top
 * Grug Brain: Queries diretas PostgreSQL, sem abstrações
 */
public class StatsCommand implements CommandExecutor {

    private final StatsPlugin plugin;
    private final DecimalFormat kdrFormat = new DecimalFormat("#.##");

    public StatsCommand(StatsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("stats")) {
            return handleStats(sender, args);
        } else if (cmdName.equals("top")) {
            return handleTop(sender, args);
        }

        return false;
    }

    /**
     * Comando /stats [player] - Mostra stats do player
     * Grug Brain: Query async para não bloquear thread principal
     */
    private boolean handleStats(CommandSender sender, String[] args) {
        Player targetPlayer = null;

        // Se tem argumento, buscar player por nome
        if (args.length > 0) {
            targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Player não encontrado: " + args[0]);
                return true;
            }
        } else if (sender instanceof Player) {
            targetPlayer = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Use: /stats <player>");
            return true;
        }

        // Tornar variáveis final para uso em lambdas (Paper 1.8.8 compatível)
        final Player finalTarget = targetPlayer;
        final String targetName = targetPlayer.getName();
        final CommandSender finalSender = sender;

        // Query async para não bloquear thread principal
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // UUID já está correto no PlayerLoginEvent (síncrono) - buscar diretamente por UUID
            PlayerData data = CoreAPI.getPlayer(finalTarget.getUniqueId());

            // Voltar à thread principal para enviar mensagens (Bukkit API não é thread-safe)
            final PlayerData finalData = data;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (finalData == null) {
                    finalSender.sendMessage(ChatColor.RED + "Player não encontrado no banco de dados.");
                    return;
                }

                // Calcular KDR
                double kdr = finalData.getDeaths() == 0 ? finalData.getKills() : (double) finalData.getKills() / finalData.getDeaths();

                // Mostrar stats
                finalSender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + targetName + ChatColor.GOLD + " STATS ===");
                finalSender.sendMessage(ChatColor.YELLOW + "Kills: " + ChatColor.WHITE + finalData.getKills());
                finalSender.sendMessage(ChatColor.YELLOW + "Deaths: " + ChatColor.WHITE + finalData.getDeaths());
                finalSender.sendMessage(ChatColor.YELLOW + "KDR: " + ChatColor.WHITE + kdrFormat.format(kdr));
                finalSender.sendMessage(ChatColor.YELLOW + "Killstreak: " + ChatColor.WHITE + finalData.getKillstreak());
                finalSender.sendMessage(ChatColor.YELLOW + "Best Killstreak: " + ChatColor.WHITE + finalData.getBestKillstreak());
            });
        });

        return true;
    }

    /**
     * Comando /top [kills|kdr|killstreak] [page] - Mostra top players
     */
    private boolean handleTop(CommandSender sender, String[] args) {
        String orderBy = "kills";
        int page = 1;

        // Parse argumentos
        if (args.length > 0) {
            String arg = args[0].toLowerCase();
            if (arg.equals("kills") || arg.equals("kill")) {
                orderBy = "kills";
            } else if (arg.equals("kdr") || arg.equals("ratio")) {
                orderBy = "kdr";
            } else if (arg.equals("killstreak") || arg.equals("ks")) {
                orderBy = "killstreak";
            }
        }

        if (args.length > 1) {
            try {
                int parsedPage = Integer.parseInt(args[1]);
                if (parsedPage < 1) {
                    parsedPage = 1;
                }
                page = parsedPage;
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Página inválida: " + args[1]);
                return true;
            }
        }

        // Tornar variáveis final para uso em lambdas
        final String finalOrderBy = orderBy;
        final int finalPage = page;

        // Verificar cache
        String cacheKey = finalOrderBy + "_" + finalPage;
        StatsPlugin.TopCache cache = plugin.getTopCache(cacheKey);
        if (cache != null) {
            sender.sendMessage(cache.getData());
            return true;
        }

        final String finalCacheKey = cacheKey;
        final CommandSender finalSender = sender;

        // Query async para não bloquear thread principal
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Query direta PostgreSQL
            List<TopEntry> topEntries = getTopPlayers(finalOrderBy, finalPage, 10);

            // Voltar à thread principal para enviar mensagens
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (topEntries.isEmpty()) {
                    finalSender.sendMessage(ChatColor.RED + "Nenhum player com stats ainda!");
                    return;
                }

                // Construir mensagem
                StringBuilder message = new StringBuilder();
                String orderByName = finalOrderBy.equals("kills") ? "KILLS" : (finalOrderBy.equals("kdr") ? "KDR" : "KILLSTREAK");
                message.append(ChatColor.GOLD).append("=== TOP ").append(orderByName).append(" (Página ").append(finalPage).append(") ===\n");

                int startPosition = (finalPage - 1) * 10 + 1;
                for (int i = 0; i < topEntries.size(); i++) {
                    TopEntry entry = topEntries.get(i);
                    int position = startPosition + i;
                    message.append(ChatColor.YELLOW).append("#").append(position).append(" ");
                    message.append(ChatColor.WHITE).append(entry.getName()).append(" ");
                    message.append(ChatColor.GRAY).append("(");
                    if (finalOrderBy.equals("kills")) {
                        message.append(entry.getKills()).append(" kills");
                    } else if (finalOrderBy.equals("kdr")) {
                        message.append(kdrFormat.format(entry.getKdr())).append(" KDR");
                    } else {
                        message.append(entry.getBestKillstreak()).append(" KS");
                    }
                    message.append(ChatColor.GRAY).append(")\n");
                }

                String result = message.toString();

                // Salvar no cache
                plugin.setTopCache(finalCacheKey, new StatsPlugin.TopCache(result));

                finalSender.sendMessage(result);
            });
        });

        return true;
    }

    /**
     * Query direta PostgreSQL para top players
     * Grug Brain: Try-with-resources para fechar recursos automaticamente
     */
    private List<TopEntry> getTopPlayers(String orderBy, int page, int limit) {
        List<TopEntry> entries = new ArrayList<>();
        int offset = (page - 1) * limit;

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            String sql;

            if (orderBy.equals("kdr")) {
                sql = "SELECT name, kills, deaths, " +
                      "(kills::DECIMAL / NULLIF(deaths, 0)) as kdr, best_killstreak " +
                      "FROM users " +
                      "WHERE kills > 0 " +
                      "ORDER BY kdr DESC, kills DESC " +
                      "LIMIT ? OFFSET ?";
            } else if (orderBy.equals("killstreak")) {
                sql = "SELECT name, kills, deaths, " +
                      "(kills::DECIMAL / NULLIF(deaths, 0)) as kdr, best_killstreak " +
                      "FROM users " +
                      "WHERE best_killstreak > 0 " +
                      "ORDER BY best_killstreak DESC, kills DESC " +
                      "LIMIT ? OFFSET ?";
            } else {
                // kills (default)
                sql = "SELECT name, kills, deaths, " +
                      "(kills::DECIMAL / NULLIF(deaths, 0)) as kdr, best_killstreak " +
                      "FROM users " +
                      "WHERE kills > 0 " +
                      "ORDER BY kills DESC, kdr DESC " +
                      "LIMIT ? OFFSET ?";
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        TopEntry entry = new TopEntry();
                        entry.setName(rs.getString("name"));
                        entry.setKills(rs.getInt("kills"));
                        entry.setDeaths(rs.getInt("deaths"));
                        entry.setKdr(rs.getDouble("kdr"));
                        entry.setBestKillstreak(rs.getInt("best_killstreak"));
                        entries.add(entry);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar top players (orderBy: " + orderBy + ", page: " + page + "): " + e.getMessage());
            e.printStackTrace();
        }

        return entries;
    }

    /**
     * Classe interna para entrada do top
     */
    private static class TopEntry {
        private String name;
        private int kills;
        private int deaths;
        private double kdr;
        private int bestKillstreak;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getKills() {
            return kills;
        }

        public void setKills(int kills) {
            this.kills = kills;
        }

        public int getDeaths() {
            return deaths;
        }

        public void setDeaths(int deaths) {
            this.deaths = deaths;
        }

        public double getKdr() {
            return kdr;
        }

        public void setKdr(double kdr) {
            this.kdr = kdr;
        }

        public int getBestKillstreak() {
            return bestKillstreak;
        }

        public void setBestKillstreak(int bestKillstreak) {
            this.bestKillstreak = bestKillstreak;
        }
    }
}


