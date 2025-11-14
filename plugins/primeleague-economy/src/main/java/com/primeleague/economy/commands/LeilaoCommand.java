package com.primeleague.economy.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.economy.EconomyAPI;
import com.primeleague.economy.EconomyPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comando /leilao - Sistema de leilões básico
 * Grug Brain: Leilões simples, expiração 48h, NBT serializado
 */
public class LeilaoCommand implements CommandExecutor {

    private final EconomyPlugin plugin;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    public LeilaoCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
        createAuctionsTable();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            return handleList(player);
        }

        String action = args[0].toLowerCase();

        if (action.equals("vender")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Use: /leilao vender <preço>");
                return true;
            }
            double minPrice;
            try {
                minPrice = Double.parseDouble(args[1]);
                if (minPrice <= 0) {
                    player.sendMessage(ChatColor.RED + "Preço deve ser maior que zero.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Preço inválido: " + args[1]);
                return true;
            }
            return handleCreate(player, minPrice);
        } else if (action.equals("dar")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Use: /leilao dar <id> <valor>");
                return true;
            }
            int auctionId;
            double bidAmount;
            try {
                auctionId = Integer.parseInt(args[1]);
                bidAmount = Double.parseDouble(args[2]);
                if (bidAmount <= 0) {
                    player.sendMessage(ChatColor.RED + "Valor deve ser maior que zero.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "ID ou valor inválido.");
                return true;
            }
            return handleBid(player, auctionId, bidAmount);
        } else {
            return handleList(player);
        }
    }

    /**
     * Cria tabela de leilões
     */
    private void createAuctionsTable() {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS economy_auctions (" +
                "id SERIAL PRIMARY KEY, " +
                "seller_uuid UUID NOT NULL, " +
                "item_serial TEXT NOT NULL, " +
                "min_price BIGINT NOT NULL, " +
                "current_bid BIGINT, " +
                "bidder_uuid UUID, " +
                "expires TIMESTAMP NOT NULL" +
                ")");

            plugin.getLogger().info("Tabela economy_auctions criada/verificada");
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao criar tabela economy_auctions: " + e.getMessage());
        }
    }

    /**
     * /leilao - Lista leilões ativos
     */
    private boolean handleList(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<AuctionEntry> auctions = getActiveAuctions();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (auctions.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Nenhum leilão ativo no momento.");
                    return;
                }

                player.sendMessage(ChatColor.GOLD + "=== LEILÕES ATIVOS ===");
                String currency = plugin.getConfig().getString("economy.simbolo", "¢");

                for (AuctionEntry entry : auctions) {
                    String line = ChatColor.YELLOW + "#" + entry.getId() + " " +
                        ChatColor.WHITE + entry.getItemName() + " " +
                        ChatColor.GREEN + "Min: " + balanceFormat.format(entry.getMinPrice()) + currency;

                    if (entry.getCurrentBid() > 0) {
                        line += ChatColor.GRAY + " | Atual: " +
                            ChatColor.YELLOW + balanceFormat.format(entry.getCurrentBid()) + currency;
                    }

                    line += ChatColor.GRAY + " | Expira em: " + formatTimeRemaining(entry.getExpires());
                    player.sendMessage(line);
                }
                player.sendMessage(ChatColor.GRAY + "Use /leilao dar <id> <valor> para dar lance");
            });
        });

        return true;
    }

    /**
     * /leilao vender <preço> - Cria leilão
     */
    private boolean handleCreate(Player player, double minPrice) {
        ItemStack handItem = player.getItemInHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Você deve estar segurando um item.");
            return true;
        }

        // Serializar item (NBT)
        String serialized = serializeItem(handItem);
        if (serialized == null) {
            player.sendMessage(ChatColor.RED + "Erro ao serializar item.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        long minPriceCents = (long) (minPrice * 100);
        long expiresMs = System.currentTimeMillis() + (48 * 60 * 60 * 1000); // 48 horas

        // Criar leilão (async)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO economy_auctions (seller_uuid, item_serial, min_price, current_bid, bidder_uuid, expires) " +
                    "VALUES (?, ?, ?, NULL, NULL, ?)");

                stmt.setObject(1, uuid);
                stmt.setString(2, serialized);
                stmt.setLong(3, minPriceCents);
                stmt.setTimestamp(4, new Timestamp(expiresMs));

                stmt.executeUpdate();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // Remover item do inventário
                    player.setItemInHand(null);

                    String currency = plugin.getConfig().getString("economy.simbolo", "¢");
                    player.sendMessage(ChatColor.GREEN + "Leilão criado! Item: " +
                        handItem.getItemMeta().getDisplayName() +
                        ChatColor.GREEN + " | Preço mínimo: " +
                        ChatColor.YELLOW + balanceFormat.format(minPrice) + currency);
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao criar leilão: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "Erro ao criar leilão.");
                });
            }
        });

        return true;
    }

    /**
     * /leilao dar <id> <valor> - Dar lance
     */
    private boolean handleBid(Player player, int auctionId, double bidAmount) {
        UUID uuid = player.getUniqueId();
        long bidCents = (long) (bidAmount * 100);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                // Buscar leilão
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT seller_uuid, min_price, current_bid, bidder_uuid, expires " +
                    "FROM economy_auctions WHERE id = ? AND expires > NOW()");
                stmt.setInt(1, auctionId);

                AuctionEntry auction = null;
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        auction = new AuctionEntry();
                        auction.setId(auctionId);
                        auction.setSellerUuid((UUID) rs.getObject("seller_uuid"));
                        auction.setMinPrice(rs.getLong("min_price") / 100.0);
                        long currentBid = rs.getLong("current_bid");
                        auction.setCurrentBid(currentBid > 0 ? currentBid / 100.0 : 0);
                        auction.setBidderUuid((UUID) rs.getObject("bidder_uuid"));
                        auction.setExpires(rs.getTimestamp("expires").getTime());
                    }
                }

                if (auction == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.RED + "Leilão não encontrado ou expirado.");
                    });
                    return;
                }

                // Verificar se não é o seller
                if (auction.getSellerUuid().equals(uuid)) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.RED + "Você não pode dar lance no próprio leilão.");
                    });
                    return;
                }

                // Verificar lance mínimo
                double minBid = auction.getCurrentBid() > 0 ? auction.getCurrentBid() + 1.0 : auction.getMinPrice();
                if (bidAmount < minBid) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.RED + "Lance mínimo: " + balanceFormat.format(minBid) +
                            plugin.getConfig().getString("economy.simbolo", "¢"));
                    });
                    return;
                }

                // Verificar saldo
                if (!EconomyAPI.hasBalance(uuid, bidAmount)) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.RED + "Saldo insuficiente.");
                    });
                    return;
                }

                // Retornar dinheiro do bidder anterior (se houver)
                if (auction.getBidderUuid() != null && auction.getCurrentBid() > 0) {
                    EconomyAPI.addMoney(auction.getBidderUuid(), auction.getCurrentBid(), "AUCTION_BID_REFUND");
                }

                // Remover dinheiro do novo bidder
                EconomyAPI.removeMoney(uuid, bidAmount, "AUCTION_BID");

                // Atualizar leilão
                PreparedStatement updateStmt = conn.prepareStatement(
                    "UPDATE economy_auctions SET current_bid = ?, bidder_uuid = ? WHERE id = ?");
                updateStmt.setLong(1, bidCents);
                updateStmt.setObject(2, uuid);
                updateStmt.setInt(3, auctionId);
                updateStmt.executeUpdate();

                String currency = plugin.getConfig().getString("economy.simbolo", "¢");
                final String msg = ChatColor.GREEN + "Lance dado! Valor: " +
                    ChatColor.YELLOW + balanceFormat.format(bidAmount) + currency;

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(msg);
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao dar lance: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "Erro ao dar lance.");
                });
            }
        });

        return true;
    }

    /**
     * Obtém leilões ativos
     */
    private List<AuctionEntry> getActiveAuctions() {
        List<AuctionEntry> auctions = new ArrayList<>();

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, seller_uuid, item_serial, min_price, current_bid, bidder_uuid, expires " +
                "FROM economy_auctions WHERE expires > NOW() ORDER BY id DESC LIMIT 20");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AuctionEntry entry = new AuctionEntry();
                    entry.setId(rs.getInt("id"));
                    entry.setSellerUuid((UUID) rs.getObject("seller_uuid"));
                    entry.setItemName(getItemNameFromSerial(rs.getString("item_serial")));
                    entry.setMinPrice(rs.getLong("min_price") / 100.0);
                    long currentBid = rs.getLong("current_bid");
                    entry.setCurrentBid(currentBid > 0 ? currentBid / 100.0 : 0);
                    entry.setBidderUuid((UUID) rs.getObject("bidder_uuid"));
                    entry.setExpires(rs.getTimestamp("expires").getTime());
                    auctions.add(entry);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar leilões: " + e.getMessage());
        }

        return auctions;
    }

    /**
     * Serializa item (Paper 1.8.8)
     * Grug Brain: Serialização simples com encantamentos
     * Formato: MATERIAL:DURABILITY:AMOUNT:ENCHANTS
     * ENCHANTS: enchant1:level1,enchant2:level2 (vazio se sem encantamentos)
     */
    private String serializeItem(ItemStack item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getType().name()).append(":")
          .append(item.getDurability()).append(":")
          .append(item.getAmount()).append(":");

        // Serializar encantamentos
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            boolean first = true;
            for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry :
                 item.getEnchantments().entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(entry.getKey().getName()).append(":").append(entry.getValue());
                first = false;
            }
        } else {
            sb.append("none");
        }

        return sb.toString();
    }

    /**
     * Obtém nome do item da serialização
     */
    private String getItemNameFromSerial(String serial) {
        try {
            String[] parts = serial.split(":");
            Material material = Material.valueOf(parts[0]);
            return material.name();
        } catch (Exception e) {
            return "Item";
        }
    }

    /**
     * Formata tempo restante
     */
    private String formatTimeRemaining(long expiresMs) {
        long remaining = expiresMs - System.currentTimeMillis();
        long hours = remaining / (60 * 60 * 1000);
        return hours + "h";
    }

    /**
     * Classe interna para entrada de leilão
     */
    private static class AuctionEntry {
        private int id;
        private UUID sellerUuid;
        private String itemName;
        private double minPrice;
        private double currentBid;
        private UUID bidderUuid;
        private long expires;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public UUID getSellerUuid() { return sellerUuid; }
        public void setSellerUuid(UUID sellerUuid) { this.sellerUuid = sellerUuid; }
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public double getMinPrice() { return minPrice; }
        public void setMinPrice(double minPrice) { this.minPrice = minPrice; }
        public double getCurrentBid() { return currentBid; }
        public void setCurrentBid(double currentBid) { this.currentBid = currentBid; }
        public UUID getBidderUuid() { return bidderUuid; }
        public void setBidderUuid(UUID bidderUuid) { this.bidderUuid = bidderUuid; }
        public long getExpires() { return expires; }
        public void setExpires(long expires) { this.expires = expires; }
    }
}

