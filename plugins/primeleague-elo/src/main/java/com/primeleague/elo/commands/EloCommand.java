package com.primeleague.elo.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.elo.EloAPI;
import com.primeleague.elo.EloPlugin;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Comandos de ELO - /elo e /topelo
 * Grug Brain: Queries diretas PostgreSQL, sem abstrações
 */
public class EloCommand implements CommandExecutor {

    private final EloPlugin plugin;

    public EloCommand(EloPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("elo")) {
            return handleElo(sender, args);
        } else if (cmdName.equals("topelo")) {
            return handleTopElo(sender, args);
        }

        return false;
    }

    /**
     * Comando /elo [player] - Mostra ELO do player
     * Grug Brain: Query async para não bloquear thread principal
     */
    private boolean handleElo(CommandSender sender, String[] args) {
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
            sender.sendMessage(ChatColor.RED + "Use: /elo <player>");
            return true;
        }

        // Tornar variáveis final para uso em classes anônimas (Paper 1.8.8 compatível)
        final Player finalTarget = targetPlayer;
        final String targetName = targetPlayer.getName();
        final CommandSender finalSender = sender;

        // Query async para não bloquear thread principal
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                // UUID já está correto no PlayerLoginEvent (síncrono) - buscar diretamente por UUID
                int elo = EloAPI.getElo(finalTarget.getUniqueId());

                // Voltar à thread principal para enviar mensagens (Bukkit API não é thread-safe)
                final int finalElo = elo;
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        finalSender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + targetName + ChatColor.GOLD + " ELO ===");
                        finalSender.sendMessage(ChatColor.YELLOW + "ELO: " + ChatColor.WHITE + finalElo);
                    }
                });
            }
        });

        return true;
    }

    /**
     * Comando /topelo [page] - Mostra top ELO
     */
    private boolean handleTopElo(CommandSender sender, String[] args) {
        int page = 1;

        // Parse argumentos
        if (args.length > 0) {
            try {
                int parsedPage = Integer.parseInt(args[0]);
                if (parsedPage < 1) {
                    parsedPage = 1;
                }
                page = parsedPage;
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Página inválida: " + args[0]);
                return true;
            }
        }

        // Tornar variáveis final para uso em classes anônimas
        final int finalPage = page;

        // Verificar cache
        String cacheKey = "elo_" + finalPage;
        EloPlugin.TopCache cache = plugin.getTopCache(cacheKey);
        if (cache != null) {
            sender.sendMessage(cache.getData());
            return true;
        }

        final String finalCacheKey = cacheKey;
        final CommandSender finalSender = sender;

        // Query async para não bloquear thread principal
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                // Query direta PostgreSQL
                List<TopEloEntry> topEntries = getTopElo(finalPage, 10);

                // Voltar à thread principal para enviar mensagens
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (topEntries.isEmpty()) {
                            finalSender.sendMessage(ChatColor.RED + "Nenhum player com ELO ainda!");
                            return;
                        }

                        // Construir mensagem
                        StringBuilder message = new StringBuilder();
                        message.append(ChatColor.GOLD).append("=== TOP ELO (Página ").append(finalPage).append(") ===\n");

                        int startPosition = (finalPage - 1) * 10 + 1;
                        for (int i = 0; i < topEntries.size(); i++) {
                            TopEloEntry entry = topEntries.get(i);
                            int position = startPosition + i;
                            message.append(ChatColor.YELLOW).append("#").append(position).append(" ");
                            message.append(ChatColor.WHITE).append(entry.getName()).append(" ");
                            message.append(ChatColor.GRAY).append("(").append(entry.getElo()).append(" ELO").append(ChatColor.GRAY).append(")\n");
                        }

                        String result = message.toString();

                        // Salvar no cache
                        plugin.setTopCache(finalCacheKey, new EloPlugin.TopCache(result));

                        finalSender.sendMessage(result);
                    }
                });
            }
        });

        return true;
    }

    /**
     * Query direta PostgreSQL para top ELO
     * Grug Brain: Try-with-resources para fechar recursos automaticamente
     */
    private List<TopEloEntry> getTopElo(int page, int limit) {
        List<TopEloEntry> entries = new ArrayList<TopEloEntry>();
        int offset = (page - 1) * limit;

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            String sql = "SELECT name, elo " +
                      "FROM users " +
                      "WHERE elo > 0 " +
                      "ORDER BY elo DESC, name ASC " +
                      "LIMIT ? OFFSET ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        TopEloEntry entry = new TopEloEntry();
                        entry.setName(rs.getString("name"));
                        entry.setElo(rs.getInt("elo"));
                        entries.add(entry);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar top ELO (page: " + page + "): " + e.getMessage());
            e.printStackTrace();
        }

        return entries;
    }

    /**
     * Classe interna para entrada do top ELO
     */
    private static class TopEloEntry {
        private String name;
        private int elo;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getElo() {
            return elo;
        }

        public void setElo(int elo) {
            this.elo = elo;
        }
    }
}

