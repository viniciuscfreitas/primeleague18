package com.primeleague.economy;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de cache de saldos em memória
 * Grug Brain: Cache simples, auto-save async a cada 30s
 */
public class EconomyManager {

    private final EconomyPlugin plugin;
    // Cache em memória: UUID -> centavos (long)
    private final Map<UUID, Long> balanceCache = new ConcurrentHashMap<>();
    private BukkitRunnable autoSaveTask;

    public EconomyManager(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Inicia auto-save async a cada 30s
     */
    public void startAutoSave() {
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                saveAllBalancesAsync();
            }
        };
        // 30s = 600 ticks (20 ticks/segundo * 30)
        autoSaveTask.runTaskTimerAsynchronously(plugin, 600L, 600L);
    }

    /**
     * Para auto-save e salva tudo antes de desabilitar
     */
    public void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        // Salvar tudo sincronamente antes de desabilitar
        saveAllBalancesSync();
    }

    /**
     * Carrega saldo do player do banco e coloca no cache
     * Grug Brain: Query direta via CoreAPI
     */
    public void loadBalance(UUID uuid) {
        PlayerData data = CoreAPI.getPlayer(uuid);
        if (data != null) {
            long cents = data.getMoney();
            balanceCache.put(uuid, cents);
        } else {
            // Player novo - saldo inicial
            long startingBalance = (long) (plugin.getConfig().getDouble("economy.saldo-inicial", 10.0) * 100);
            balanceCache.put(uuid, startingBalance);
        }
    }

    /**
     * Obtém saldo do cache (em centavos)
     */
    public long getBalanceCents(UUID uuid) {
        return balanceCache.getOrDefault(uuid, 0L);
    }

    /**
     * Obtém saldo do cache (em dólares)
     */
    public double getBalance(UUID uuid) {
        long cents = getBalanceCents(uuid);
        return cents / 100.0;
    }

    /**
     * Define saldo (em centavos)
     */
    public void setBalanceCents(UUID uuid, long cents) {
        balanceCache.put(uuid, cents);
    }

    /**
     * Adiciona dinheiro (em centavos)
     */
    public void addBalanceCents(UUID uuid, long cents) {
        long current = getBalanceCents(uuid);
        long maxBalance = (long) (plugin.getConfig().getDouble("economy.saldo-maximo", 1000000.0) * 100);
        long newBalance = Math.min(current + cents, maxBalance);
        balanceCache.put(uuid, newBalance);
    }

    /**
     * Remove dinheiro (em centavos)
     */
    public void removeBalanceCents(UUID uuid, long cents) {
        long current = getBalanceCents(uuid);
        long minBalance = (long) (plugin.getConfig().getDouble("economy.saldo-minimo", 0.0) * 100);
        long newBalance = Math.max(current - cents, minBalance);
        balanceCache.put(uuid, newBalance);
    }

    /**
     * Salva saldo do player no banco
     */
    private void saveBalance(UUID uuid) {
        long cents = getBalanceCents(uuid);
        PlayerData data = CoreAPI.getPlayer(uuid);
        if (data != null) {
            data.setMoney(cents);
            CoreAPI.savePlayer(data);
        }
    }

    /**
     * Salva todos os saldos no banco (async)
     */
    private void saveAllBalancesAsync() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : balanceCache.keySet()) {
                    saveBalance(uuid);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Salva todos os saldos no banco (sync - usado no disable)
     */
    private void saveAllBalancesSync() {
        for (UUID uuid : balanceCache.keySet()) {
            saveBalance(uuid);
        }
    }

    /**
     * Remove player do cache (on quit)
     */
    public void removePlayer(UUID uuid) {
        // Salvar antes de remover
        saveBalance(uuid);
        balanceCache.remove(uuid);
    }

    /**
     * Limpa cache (usado em testes ou reload)
     */
    public void clearCache() {
        balanceCache.clear();
    }
}

