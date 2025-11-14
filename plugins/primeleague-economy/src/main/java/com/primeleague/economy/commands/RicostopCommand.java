package com.primeleague.economy.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.economy.EconomyPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Comando /ricostop - Ranking dos mais ricos
 * Grug Brain: Query direta PostgreSQL, cache 5min
 */
public class RicostopCommand implements CommandExecutor {

    private final EconomyPlugin plugin;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    /**
     * Classe interna para cache de top
     */
    public static class TopCache {
        private final String data;
        private final long timestamp;

        public TopCache(String data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getData() {
            return data;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public RicostopCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        int page = 1;

        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) {
                    page = 1;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Página inválida: " + args[0]);
                return true;
            }
        }

        final int finalPage = page;
        final CommandSender finalSender = sender;

        // Verificar cache
        long cacheDuration = plugin.getConfig().getLong("cache.ricostop-duracao", 300) * 1000; // Converter para ms
        String cacheKey = "ricostop_" + finalPage;
        TopCache cache = plugin.getRicostopCache(cacheKey);

        if (cache != null && System.currentTimeMillis() - cache.getTimestamp() < cacheDuration) {
            sender.sendMessage(cache.getData());
            return true;
        }

        // Query async para não bloquear thread principal
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<TopEntry> topEntries = getTopRicos(finalPage, 10);

            // Voltar à thread principal para enviar mensagens
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (topEntries.isEmpty()) {
                    finalSender.sendMessage(ChatColor.RED + "Nenhum player com saldo ainda!");
                    return;
                }

                // Construir mensagem
                StringBuilder message = new StringBuilder();
                String currency = plugin.getConfig().getString("economy.simbolo", "¢");
                message.append(ChatColor.GOLD).append("=== TOP RICOS (Página ").append(finalPage).append(") ===\n");

                int startPosition = (finalPage - 1) * 10 + 1;
                for (int i = 0; i < topEntries.size(); i++) {
                    TopEntry entry = topEntries.get(i);
                    int position = startPosition + i;

                    // Cores por posição
                    ChatColor color = ChatColor.WHITE;
                    if (position == 1) color = ChatColor.GOLD;
                    else if (position == 2) color = ChatColor.GRAY;
                    else if (position == 3) color = ChatColor.YELLOW;

                    message.append(color).append("#").append(position).append(" ");
                    message.append(ChatColor.WHITE).append(entry.getName()).append(" ");
                    message.append(ChatColor.GREEN).append(balanceFormat.format(entry.getBalance())).append(currency).append("\n");
                }

                String result = message.toString();

                // Salvar no cache
                plugin.setRicostopCache(cacheKey, new TopCache(result));

                finalSender.sendMessage(result);
            });
        });

        return true;
    }

    /**
     * Query direta PostgreSQL para top ricos
     * Grug Brain: Try-with-resources para fechar recursos automaticamente
     */
    private List<TopEntry> getTopRicos(int page, int limit) {
        List<TopEntry> entries = new ArrayList<>();
        int offset = (page - 1) * limit;

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            String sql = "SELECT name, money FROM users " +
                        "WHERE money > 0 " +
                        "ORDER BY money DESC " +
                        "LIMIT ? OFFSET ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        TopEntry entry = new TopEntry();
                        entry.setName(rs.getString("name"));
                        // money está em centavos, converter para dólares
                        long cents = rs.getLong("money");
                        entry.setBalance(cents / 100.0);
                        entries.add(entry);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar top ricos (page: " + page + "): " + e.getMessage());
            e.printStackTrace();
        }

        return entries;
    }

    /**
     * Classe interna para entrada do top
     */
    private static class TopEntry {
        private String name;
        private double balance;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getBalance() {
            return balance;
        }

        public void setBalance(double balance) {
            this.balance = balance;
        }
    }
}

