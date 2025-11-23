package com.primeleague.factions.manager;

import com.primeleague.core.CoreAPI;
import com.primeleague.factions.PrimeFactions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PowerManager implements Listener {

    private final PrimeFactions plugin;
    private final Map<UUID, Double> powerCache;
    private final Map<UUID, Double> maxPowerCache;
    private final double maxPowerDefault;
    private final double minPowerDefault;
    private final double powerRegen;
    private final double deathPenalty;

    public PowerManager(PrimeFactions plugin) {
        this.plugin = plugin;
        this.powerCache = new ConcurrentHashMap<>();
        this.maxPowerCache = new ConcurrentHashMap<>();
        
        this.maxPowerDefault = plugin.getConfig().getDouble("power.max-power", 50.0);
        this.minPowerDefault = plugin.getConfig().getDouble("power.min-power", -10.0);
        this.powerRegen = plugin.getConfig().getDouble("power.regen-per-minute", 0.33);
        this.deathPenalty = plugin.getConfig().getDouble("power.death-penalty", 4.0);

        // Start Regen Task
        startRegenTask();
    }

    private void startRegenTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (!powerCache.containsKey(uuid)) continue;

                double current = powerCache.get(uuid);
                double max = maxPowerCache.getOrDefault(uuid, maxPowerDefault);

                if (current < max) {
                    double newPower = Math.min(max, current + powerRegen);
                    powerCache.put(uuid, newPower);
                    // We don't save to DB every minute to avoid spam.
                    // We save on Quit or periodically.
                }
            }
        }, 1200L, 1200L); // Every minute (60 * 20)
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayerPower(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        savePlayerPower(event.getPlayer().getUniqueId());
        powerCache.remove(event.getPlayer().getUniqueId());
        maxPowerCache.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        
        if (powerCache.containsKey(uuid)) {
            double current = powerCache.get(uuid);
            double newPower = Math.max(minPowerDefault, current - deathPenalty);
            powerCache.put(uuid, newPower);
            player.sendMessage("§cVocê morreu e perdeu " + deathPenalty + " de poder! Atual: " + String.format("%.2f", newPower));
            
            // Notificar Discord se power ficou negativo ou crítico
            if (newPower < 0 && plugin.getDiscordIntegration() != null && plugin.getDiscordIntegration().isEnabled()) {
                // Obter clan do player
                String clanName = null;
                try {
                    com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(uuid);
                    if (clan != null) {
                        clanName = clan.getName();
                    }
                } catch (Exception e) {
                    // Ignorar erros ao buscar clan
                }
                
                plugin.getDiscordIntegration().sendPowerLost(player.getName(), current, newPower, deathPenalty, clanName);
                
                // Se power está crítico (muito negativo), notificar também
                if (newPower <= -5.0) {
                    plugin.getDiscordIntegration().sendPowerCritical(player.getName(), newPower, clanName);
                }
            }
            
            // Save immediately on death
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> savePlayerPower(uuid));
        }
    }

    public double getPower(UUID uuid) {
        return powerCache.getOrDefault(uuid, 0.0);
    }

    private void loadPlayerPower(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT power, max_power, last_power_regen FROM users WHERE uuid = ?")) {
                stmt.setObject(1, uuid);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    double power = rs.getDouble("power");
                    double maxPower = rs.getDouble("max_power");
                    Timestamp lastRegen = rs.getTimestamp("last_power_regen");
                    
                    // Calculate offline regen
                    if (lastRegen != null) {
                        long diffMillis = System.currentTimeMillis() - lastRegen.getTime();
                        double minutesOffline = diffMillis / 60000.0;
                        double regenAmount = minutesOffline * powerRegen;
                        power = Math.min(maxPower, power + regenAmount);
                    }

                    powerCache.put(uuid, power);
                    maxPowerCache.put(uuid, maxPower);
                } else {
                    // New player or not in DB yet (Core handles creation, but maybe delayed)
                    powerCache.put(uuid, 0.0); // Start with 0 or config default? User said "Start with 0"
                    maxPowerCache.put(uuid, maxPowerDefault);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao carregar power de " + uuid, e);
            }
        });
    }

    private void savePlayerPower(UUID uuid) {
        if (!powerCache.containsKey(uuid)) return;
        
        double power = powerCache.get(uuid);
        double maxPower = maxPowerCache.getOrDefault(uuid, maxPowerDefault);
        
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE users SET power = ?, max_power = ?, last_power_regen = CURRENT_TIMESTAMP WHERE uuid = ?")) {
            stmt.setDouble(1, power);
            stmt.setDouble(2, maxPower);
            stmt.setObject(3, uuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar power de " + uuid, e);
        }
    }
}
