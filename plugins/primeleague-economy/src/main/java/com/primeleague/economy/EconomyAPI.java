package com.primeleague.economy;

import com.primeleague.core.CoreAPI;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * API pública estática para outros plugins
 * Grug Brain: Métodos estáticos thread-safe, similar ao EloAPI
 */
public class EconomyAPI {

    private static EconomyPlugin getPlugin() {
        EconomyPlugin plugin = (EconomyPlugin) Bukkit.getPluginManager().getPlugin("PrimeleagueEconomy");
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("PrimeleagueEconomy não está habilitado");
        }
        return plugin;
    }

    public static boolean isEnabled() {
        EconomyPlugin plugin = (EconomyPlugin) Bukkit.getPluginManager().getPlugin("PrimeleagueEconomy");
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Adiciona dinheiro ao player
     * Thread-safe: synchronized para operação atômica
     * @param playerUuid UUID do player
     * @param amount Quantidade em dólares (ex: 10.50)
     * @param reason Motivo (para logs)
     * @return Quantidade adicionada (em dólares)
     */
    public static synchronized double addMoney(UUID playerUuid, double amount, String reason) {
        if (amount <= 0) {
            getPlugin().getLogger().warning("Tentativa de adicionar valor inválido: " + amount);
            return 0;
        }

        EconomyManager manager = getPlugin().getEconomyManager();

        // Converter dólares para centavos
        long cents = (long) (amount * 100);

        // Adicionar ao cache
        manager.addBalanceCents(playerUuid, cents);

        // Log transação async (não bloquear thread)
        logTransactionAsync(playerUuid, null, cents, reason != null ? reason : "ADD", "");

        // Logging reduzido (apenas se configurado)
        if (getPlugin().getConfig().getBoolean("economy.log-transacoes", false)) {
            double newBalance = manager.getBalance(playerUuid);
            getPlugin().getLogger().info("Dinheiro adicionado: " + playerUuid + " +$" + amount +
                " (" + reason + ") - Saldo: $" + newBalance);
        }

        return amount;
    }

    /**
     * Remove dinheiro do player
     * Thread-safe: synchronized para operação atômica
     * @param playerUuid UUID do player
     * @param amount Quantidade em dólares
     * @param reason Motivo (para logs)
     * @return Quantidade removida (em dólares)
     */
    public static synchronized double removeMoney(UUID playerUuid, double amount, String reason) {
        if (amount <= 0) {
            getPlugin().getLogger().warning("Tentativa de remover valor inválido: " + amount);
            return 0;
        }

        EconomyManager manager = getPlugin().getEconomyManager();

        // Verificar saldo
        if (!hasBalance(playerUuid, amount)) {
            getPlugin().getLogger().warning("Saldo insuficiente para remover: " + playerUuid + " - $" + amount);
            return 0;
        }

        // Converter dólares para centavos
        long cents = (long) (amount * 100);

        // Remover do cache
        manager.removeBalanceCents(playerUuid, cents);

        // Log transação async (não bloquear thread)
        logTransactionAsync(playerUuid, null, -cents, reason != null ? reason : "REMOVE", "");

        return amount;
    }

    /**
     * Obtém saldo do player
     * Thread-safe: apenas leitura
     * @param playerUuid UUID do player
     * @return Saldo em dólares
     */
    public static double getBalance(UUID playerUuid) {
        EconomyManager manager = getPlugin().getEconomyManager();
        return manager.getBalance(playerUuid);
    }

    /**
     * Define saldo do player
     * Thread-safe: synchronized para operação atômica
     * @param playerUuid UUID do player
     * @param amount Quantidade em dólares
     * @param reason Motivo (para logs)
     */
    public static synchronized void setBalance(UUID playerUuid, double amount, String reason) {
        if (amount < 0) {
            getPlugin().getLogger().warning("Tentativa de definir saldo negativo: " + amount);
            return;
        }

        EconomyManager manager = getPlugin().getEconomyManager();

        // Converter dólares para centavos
        long cents = (long) (amount * 100);

        // Definir no cache
        manager.setBalanceCents(playerUuid, cents);

        // Log transação async (não bloquear thread)
        logTransactionAsync(playerUuid, null, cents, reason != null ? reason : "SET", "");

        // Logging reduzido (apenas se configurado)
        if (getPlugin().getConfig().getBoolean("economy.log-transacoes", false)) {
            getPlugin().getLogger().info("Saldo definido: " + playerUuid + " = $" + amount + " (" + reason + ")");
        }
    }

    /**
     * Verifica se player tem saldo suficiente
     * Thread-safe: apenas leitura
     * @param playerUuid UUID do player
     * @param amount Quantidade em dólares
     * @return true se tem saldo suficiente
     */
    public static boolean hasBalance(UUID playerUuid, double amount) {
        double balance = getBalance(playerUuid);
        return balance >= amount;
    }

    /**
     * Transfere dinheiro entre players
     * Thread-safe: synchronized para operação atômica
     * @param fromUuid UUID do remetente
     * @param toUuid UUID do destinatário
     * @param amount Quantidade em dólares
     * @param reason Motivo (para logs)
     * @return true se transferência foi bem-sucedida
     */
    public static synchronized boolean transfer(UUID fromUuid, UUID toUuid, double amount, String reason) {
        if (amount <= 0) {
            return false;
        }

        // Verificar saldo
        if (!hasBalance(fromUuid, amount)) {
            return false;
        }

        EconomyManager manager = getPlugin().getEconomyManager();

        // Converter dólares para centavos
        long cents = (long) (amount * 100);

        // Remover do remetente
        manager.removeBalanceCents(fromUuid, cents);
        logTransactionAsync(fromUuid, toUuid, -cents, reason != null ? reason : "TRANSFER_OUT", "");

        // Adicionar ao destinatário
        manager.addBalanceCents(toUuid, cents);
        logTransactionAsync(toUuid, fromUuid, cents, reason != null ? reason : "TRANSFER_IN", "");

        return true;
    }

    /**
     * Registra transação no banco (async)
     * Grug Brain: Query direta PostgreSQL, executada async para não bloquear
     */
    private static void logTransactionAsync(UUID playerUuid, UUID otherUuid, long amountCents, String type, String reason) {
        final UUID finalPlayerUuid = playerUuid;
        final UUID finalOtherUuid = otherUuid;
        final long finalAmountCents = amountCents;
        final String finalType = type;
        final String finalReason = reason != null ? reason : "";

        new BukkitRunnable() {
            @Override
            public void run() {
                logTransaction(finalPlayerUuid, finalOtherUuid, finalAmountCents, finalType, finalReason);
            }
        }.runTaskAsynchronously(getPlugin());
    }

    /**
     * Registra transação no banco (sync - usado apenas internamente)
     * Grug Brain: Query direta PostgreSQL
     */
    private static void logTransaction(UUID playerUuid, UUID otherUuid, long amountCents, String type, String reason) {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO economy_transactions (player_uuid, from_uuid, to_uuid, amount, type, reason, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)");

            stmt.setObject(1, playerUuid);
            if (otherUuid != null) {
                // Se for transfer, from_uuid = from, to_uuid = to
                if (amountCents > 0) {
                    stmt.setObject(2, playerUuid); // from
                    stmt.setObject(3, otherUuid);  // to
                } else {
                    stmt.setObject(2, otherUuid);  // from
                    stmt.setObject(3, playerUuid); // to
                }
            } else {
                // Single player transaction
                stmt.setObject(2, playerUuid);
                stmt.setNull(3, java.sql.Types.OTHER);
            }
            stmt.setLong(4, Math.abs(amountCents));
            stmt.setString(5, type);
            stmt.setString(6, reason);
            stmt.setTimestamp(7, new Timestamp(System.currentTimeMillis()));

            stmt.executeUpdate();
        } catch (SQLException e) {
            getPlugin().getLogger().severe("Erro ao logar transação: " + e.getMessage());
        }
    }

    /**
     * Método público para logar transação (usado por outros plugins)
     */
    public static void logTransactionPublic(UUID playerUuid, UUID otherUuid, double amount, String type, String reason) {
        long cents = (long) (amount * 100);
        logTransactionAsync(playerUuid, otherUuid, cents, type, reason);
    }
}

