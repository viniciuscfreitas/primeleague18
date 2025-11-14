package com.primeleague.economy.utils;

import com.primeleague.core.CoreAPI;
import com.primeleague.economy.EconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DynamicPricer - Sistema de precificação dinâmica Hyperbolic + EMA
 * Grug Brain: Simples, thread-safe, async, robusto
 *
 * Fórmula corrigida:
 * 1. total_inj = SUM(injection) / max(1, online_players)  // CORREÇÃO #1: Normalizar
 * 2. inj_per_player = total_inj / online_players
 * 3. expected_per_player = expected_injection / 10
 * 4. sales_rate_item = sinks_item / expected_sinks_per_item
 * 5. hyper_mult = 1.0 / (1.0 + k * (1.0 - sales_rate_item / base))
 * 6. new_ema = alpha * hyper_mult + (1 - alpha) * prev_ema
 * 7. price_delta = new_ema * base_price - prev_price
 * 8. clamped_delta = clamp(-max_change%, +max_change%, price_delta)
 * 9. final_price = clamp(base * min_mult, base * max_mult, prev_price + clamped_delta)
 */
public class DynamicPricer {

    private final EconomyPlugin plugin;
    private final FileConfiguration config;

    // Thread-safe caches
    private final ConcurrentHashMap<String, Double> priceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> emaCache = new ConcurrentHashMap<>();
    private final Map<String, Double> basePrices = new HashMap<>();

    // CORREÇÃO #5: prevPriceCache para max-change-cycle
    private final ConcurrentHashMap<String, Double> prevPriceCache = new ConcurrentHashMap<>();

    // Config values
    private final long intervalTicks;
    private final long windowMs;
    private final double expectedInjection;
    private final double alpha;
    private final double kHyper;
    private final double maxChange;
    private final double minMult;
    private final double maxMult;
    private final int minPlayers;

    // State
    private final AtomicLong lastUpdate = new AtomicLong(0);
    private BukkitRunnable updateTask;

    public DynamicPricer(EconomyPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;

        // Load config
        this.intervalTicks = config.getLong("economy.precos-dinamicos.intervalo", 600) * 20L; // Convert seconds to ticks
        this.windowMs = config.getLong("economy.precos-dinamicos.janela-min", 600000);
        this.expectedInjection = config.getDouble("economy.precos-dinamicos.expected-injection", 100.0);
        this.alpha = config.getDouble("economy.precos-dinamicos.alpha", 0.3);
        this.kHyper = config.getDouble("economy.precos-dinamicos.k-hyper", 0.5);
        this.maxChange = config.getDouble("economy.precos-dinamicos.max-change-cycle", 0.1);
        this.minMult = config.getDouble("economy.precos-dinamicos.min-mult", 0.5);
        this.maxMult = config.getDouble("economy.precos-dinamicos.max-mult", 1.5);
        this.minPlayers = config.getInt("economy.precos-dinamicos.players-min-update", 5);

        // Load base prices from config
        loadBasePrices();

        // CORREÇÃO #7: Load persisted state BEFORE cold-start
        loadPersistedState();

        // Start update task
        scheduleUpdates();

        plugin.getLogger().info("DynamicPricer inicializado com " + basePrices.size() + " items");
    }

    /**
     * Carrega preços base do config.yml
     */
    private void loadBasePrices() {
        basePrices.clear();

        // Load from config: economy.shops.pvp.{ITEM}
        basePrices.put("IRON_SWORD", config.getDouble("economy.shops.pvp.IRON_SWORD", 20.0));
        basePrices.put("IRON_CHESTPLATE", config.getDouble("economy.shops.pvp.IRON_CHESTPLATE", 35.0));
        basePrices.put("STRENGTH_POTION", config.getDouble("economy.shops.pvp.STRENGTH_POTION", 40.0));
        basePrices.put("SPEED_POTION", config.getDouble("economy.shops.pvp.SPEED_POTION", 30.0));
        basePrices.put("GOLDEN_APPLE", config.getDouble("economy.shops.pvp.GOLDEN_APPLE", 15.0));
        basePrices.put("ENDER_PEARL", config.getDouble("economy.shops.pvp.ENDER_PEARL", 8.0));
        basePrices.put("XP_BOTTLE", config.getDouble("economy.shops.pvp.XP_BOTTLE", 12.0));

        // Initialize price cache with base prices
        for (Map.Entry<String, Double> entry : basePrices.entrySet()) {
            priceCache.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * CORREÇÃO #7: Load persisted state BEFORE cold-start
     * Carrega EMAs persistidas do banco (se existirem)
     */
    private void loadPersistedState() {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            String sql = "SELECT item_name, ema_mult FROM dynamic_prices";

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                boolean loaded = false;
                while (rs.next()) {
                    String item = rs.getString("item_name");
                    double ema = rs.getDouble("ema_mult");
                    if (basePrices.containsKey(item)) {
                        emaCache.put(item, ema);
                        // Recalcular price cache
                        double base = basePrices.get(item);
                        priceCache.put(item, Math.max(base * minMult, Math.min(base * maxMult, ema * base)));
                        loaded = true;
                    }
                }

                // CORREÇÃO #7: Só fazer cold-start se não há dados persistidos
                if (!loaded) {
                    plugin.getLogger().info("DynamicPricer: Cold start - inicializando EMAs em 1.0");
                    basePrices.forEach((item, base) -> {
                        emaCache.put(item, 1.0);
                        priceCache.put(item, base);
                        prevPriceCache.put(item, base);
                    });
                } else {
                    plugin.getLogger().info("DynamicPricer: Carregado estado persistido (" + emaCache.size() + " items)");
                    // Initialize prevPriceCache with current prices
                    for (Map.Entry<String, Double> entry : priceCache.entrySet()) {
                        prevPriceCache.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Erro ao carregar dynamic prices: " + e.getMessage());
            // Fallback: EMAs = 1.0 (preços base)
            basePrices.forEach((item, base) -> {
                emaCache.put(item, 1.0);
                priceCache.put(item, base);
                prevPriceCache.put(item, base);
            });
        }
    }

    /**
     * CORREÇÃO #4: persistState() batch correto usando EXCLUDED
     * Salva EMAs no banco
     * CORREÇÃO: Fallback para PostgreSQL < 9.5 (que não suporta ON CONFLICT)
     */
    private void persistState() {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Verificar se PostgreSQL suporta ON CONFLICT (9.5+)
            // Tenta usar ON CONFLICT primeiro, fallback para DELETE + INSERT se falhar
            String sql = "INSERT INTO dynamic_prices (item_name, ema_mult, updated) " +
                        "VALUES (?, ?, NOW()) " +
                        "ON CONFLICT (item_name) DO UPDATE " +
                        "SET ema_mult = EXCLUDED.ema_mult, updated = NOW()";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Map.Entry<String, Double> entry : emaCache.entrySet()) {
                    stmt.setString(1, entry.getKey());
                    stmt.setDouble(2, entry.getValue());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (SQLException e) {
                // Fallback: PostgreSQL < 9.5 ou erro de sintaxe
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (errorMsg.contains("on conflict") || errorMsg.contains("syntax error") ||
                    errorMsg.contains("feature not supported")) {
                    plugin.getLogger().warning("PostgreSQL < 9.5 detectado ou ON CONFLICT não suportado - usando fallback (DELETE + INSERT)");
                    persistStateFallback(conn);
                } else {
                    throw e; // Re-throw outros erros
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Erro ao persistir dynamic prices: " + e.getMessage());
        }
    }

    /**
     * Fallback para PostgreSQL < 9.5 (DELETE + INSERT)
     */
    private void persistStateFallback(Connection conn) throws SQLException {
        String deleteSql = "DELETE FROM dynamic_prices WHERE item_name = ?";
        String insertSql = "INSERT INTO dynamic_prices (item_name, ema_mult, updated) VALUES (?, ?, NOW())";

        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            for (Map.Entry<String, Double> entry : emaCache.entrySet()) {
                String item = entry.getKey();
                double ema = entry.getValue();

                // Delete primeiro
                deleteStmt.setString(1, item);
                deleteStmt.addBatch();

                // Insert depois
                insertStmt.setString(1, item);
                insertStmt.setDouble(2, ema);
                insertStmt.addBatch();
            }

            deleteStmt.executeBatch();
            insertStmt.executeBatch();
        }
    }

    /**
     * Obtém preço atual do item (thread-safe)
     */
    public double getPrice(String item) {
        return priceCache.getOrDefault(item, basePrices.getOrDefault(item, 0.0));
    }

    /**
     * Agenda atualizações periódicas (async)
     * CORREÇÃO: Obter online players na thread principal antes de executar async
     */
    private void scheduleUpdates() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Obter online players count na thread principal (thread-safe)
                final int onlinePlayers = Bukkit.getOnlinePlayers().size();

                // Executar lógica pesada (queries DB) em thread async
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        updatePrices(onlinePlayers);
                    }
                }.runTaskAsynchronously(plugin);
            }
        };
        updateTask.runTaskTimer(plugin, 0L, intervalTicks);
    }

    /**
     * Para o sistema de atualizações
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        // Salvar estado final
        persistState();
    }

    /**
     * Atualiza preços baseado em injection e sinks (async)
     * CORREÇÃO #1: Normalização por player
     * CORREÇÃO #5: prevPriceCache snapshot antes do loop
     * CORREÇÃO #6: Log com null check
     * CORREÇÃO: Recebe onlinePlayers como parâmetro (obtido na thread principal)
     */
    private void updatePrices(int onlinePlayers) {
        try {
            // Edge case: Servidor vazio
            if (onlinePlayers < minPlayers) {
                plugin.getLogger().info("DynamicPricer: Servidor vazio (<" + minPlayers + " players) - skip update");
                return;
            }

            long now = System.currentTimeMillis();
            long since = now - windowMs;

            // Query total injection
            double totalInj = queryInjection(since);

            // CORREÇÃO #1: Normalizar por players
            double online = Math.max(1, onlinePlayers);
            double injPerPlayer = totalInj / online;
            double expectedPerPlayer = expectedInjection / 10.0; // Média histórica (ajustar depois)

            // Outlier detection (anti-manipulação)
            if (totalInj > expectedInjection * 3.0) {
                plugin.getLogger().warning("Injection outlier detectado: " + totalInj +
                    " (expected: " + expectedInjection + ") - capping");
                totalInj = expectedInjection * 3.0;
                injPerPlayer = totalInj / online;
            }

            // Query sinks por item
            Map<String, Double> itemSinks = queryItemSinks(since);

            // CORREÇÃO #5: Snapshot prevPriceCache antes do loop
            Map<String, Double> prevSnapshot = new HashMap<>(prevPriceCache);

            // Calcular novos preços para cada item
            for (Map.Entry<String, Double> entry : basePrices.entrySet()) {
                String item = entry.getKey();
                double basePrice = entry.getValue();

                // Obter sinks do item (0 se não comprado)
                double sinksItem = itemSinks.getOrDefault(item, 0.0);

                // Expected sinks per item (tunar depois - por enquanto usar base price como proxy)
                double expectedSinksPerItem = basePrice * 0.5; // Aproximação simples

                // Sales rate
                double salesRate = expectedSinksPerItem > 0
                    ? sinksItem / expectedSinksPerItem
                    : 0.0;

                // Hyperbolic multiplier (baseado em sales rate)
                double hyperMult = 1.0 / (1.0 + kHyper * (1.0 - salesRate));

                // EMA update
                double prevEma = emaCache.getOrDefault(item, 1.0);
                double newEma = alpha * hyperMult + (1.0 - alpha) * prevEma;
                emaCache.put(item, newEma);

                // Calcular novo preço
                double newPrice = newEma * basePrice;

                // CORREÇÃO #5: Obter preço anterior do snapshot
                double oldPrice = prevSnapshot.getOrDefault(item, basePrice);

                // Max change per cycle (anti-oscilação)
                double delta = newPrice - oldPrice;
                double deltaPercent = oldPrice > 0 ? Math.abs(delta) / oldPrice : 0.0;

                if (deltaPercent > maxChange) {
                    double direction = delta > 0 ? 1.0 : -1.0;
                    newPrice = oldPrice * (1.0 + direction * maxChange);
                }

                // Aplicar clamps finais
                double finalPrice = Math.max(basePrice * minMult, Math.min(basePrice * maxMult, newPrice));
                priceCache.put(item, finalPrice);

                // CORREÇÃO #5: Atualizar prevPriceCache ao final
                prevPriceCache.put(item, finalPrice);
            }

            // Log update
            double totalSinks = itemSinks.values().stream().mapToDouble(Double::doubleValue).sum();
            plugin.getLogger().info(String.format(
                "DynamicPricer: Updated prices (inj=%.2f, sinks=%.2f, players=%d, inj_per_player=%.2f)",
                totalInj, totalSinks, onlinePlayers, injPerPlayer
            ));

            // CORREÇÃO #6: Log mudanças significativas (>10%) com null check
            for (Map.Entry<String, Double> entry : basePrices.entrySet()) {
                String item = entry.getKey();
                double oldPrice = prevSnapshot.getOrDefault(item, basePrices.get(item)); // CORREÇÃO #6
                double newPrice = priceCache.get(item);
                double base = basePrices.get(item);

                if (oldPrice > 0 && Math.abs(newPrice - oldPrice) / oldPrice > 0.1) {
                    double percentChange = ((newPrice - oldPrice) / oldPrice) * 100;
                    plugin.getLogger().info(String.format(
                        "DynamicPricer: %s mudou de $%.2f para $%.2f (%.1f%%, base: $%.2f)",
                        item, oldPrice, newPrice, percentChange, base
                    ));
                }
            }

            // Persistir estado
            persistState();

            lastUpdate.set(now);

        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao atualizar dynamic prices: " + e.getMessage());
            // Graceful degradation: manter preços atuais
        } catch (Exception e) {
            plugin.getLogger().severe("Erro inesperado no DynamicPricer: " + e.getMessage());
            e.printStackTrace();
            // Graceful degradation: sistema continua funcionando com preços atuais
        }
    }

    /**
     * Query total injection (KILL, KILLSTREAK, FARM_%)
     * CORREÇÃO #2: Duas queries separadas para aproveitar índices (mais eficiente que UNION)
     */
    private double queryInjection(long since) throws SQLException {
        double total = 0.0;
        java.sql.Timestamp timestamp = new java.sql.Timestamp(since);

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Query 1: KILL, KILLSTREAK (usa índice idx_trans_timestamp_type)
            String sql1 = "SELECT COALESCE(SUM(amount)/100.0, 0) as total " +
                         "FROM economy_transactions " +
                         "WHERE timestamp > ? AND type IN ('KILL', 'KILLSTREAK_3', 'KILLSTREAK_5')";

            try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                stmt.setTimestamp(1, timestamp);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        total += rs.getDouble("total");
                    }
                }
            }

            // Query 2: FARM_% (usa índice parcial idx_trans_farm_type)
            String sql2 = "SELECT COALESCE(SUM(amount)/100.0, 0) as total " +
                         "FROM economy_transactions " +
                         "WHERE timestamp > ? AND type LIKE 'FARM_%'";

            try (PreparedStatement stmt = conn.prepareStatement(sql2)) {
                stmt.setTimestamp(1, timestamp);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        total += rs.getDouble("total");
                    }
                }
            }
        }
        return total;
    }

    /**
     * Query sinks por item (SHOP_BUY grouped by reason)
     */
    private Map<String, Double> queryItemSinks(long since) throws SQLException {
        Map<String, Double> sinks = new HashMap<>();

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            String sql = "SELECT reason as item, COALESCE(SUM(amount)/100.0, 0) as sinks " +
                        "FROM economy_transactions " +
                        "WHERE timestamp > ? AND type = 'SHOP_BUY' AND reason IS NOT NULL " +
                        "GROUP BY reason";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, new java.sql.Timestamp(since));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String item = rs.getString("item");
                        double sink = rs.getDouble("sinks");
                        sinks.put(item, sink);
                    }
                }
            }
        }

        return sinks;
    }

    // Getters para comando admin (CORREÇÃO #8)
    public double getGlobalMultiplier() {
        if (emaCache.isEmpty()) return 1.0;
        return emaCache.values().stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
    }

    public long getLastUpdateTime() {
        return lastUpdate.get();
    }

    public Set<String> getItemNames() {
        return basePrices.keySet();
    }

    public double getBasePrice(String item) {
        return basePrices.getOrDefault(item, 0.0);
    }

    public double getEma(String item) {
        return emaCache.getOrDefault(item, 1.0);
    }
}

