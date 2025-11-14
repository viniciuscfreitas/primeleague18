package com.primeleague.clans.listeners;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.models.ClanData;
import com.primeleague.core.CoreAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Listener de stats de clan - Atualiza stats agregadas quando membros fazem PvP
 * Grug Brain: Query async para não bloquear, atualiza stats do clan em background
 * Nota: Stats são calculadas on-the-fly nas queries de ranking (tabela clan_stats é opcional)
 */
public class ClanStatsListener implements Listener {

    private final ClansPlugin plugin;

    public ClanStatsListener(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * PlayerDeathEvent é assíncrono em Paper 1.8.8
     * Grug Brain: Query async para não bloquear, apenas PvP direto
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller(); // PvP direto

        if (killer == null) {
            return; // Ignorar mortes não-PvP diretas
        }

        final UUID killerUuid = killer.getUniqueId();
        final UUID victimUuid = victim.getUniqueId();

        // Executar em thread assíncrona para não bloquear
        new BukkitRunnable() {
            @Override
            public void run() {
                ClanData killerClan = plugin.getClansManager().getClanByMember(killerUuid);
                ClanData victimClan = plugin.getClansManager().getClanByMember(victimUuid);

                // Atualizar kills do clan do killer
                if (killerClan != null) {
                    updateClanStats(killerClan.getId(), 1, 0); // +1 kill
                }

                // Atualizar deaths do clan da vítima
                if (victimClan != null) {
                    updateClanStats(victimClan.getId(), 0, 1); // +1 death
                }

                // Invalidar cache de ELO médio (recalculará na próxima consulta)
                if (killerClan != null) {
                    plugin.invalidateEloCache(killerClan.getId());
                }
                if (victimClan != null) {
                    plugin.invalidateEloCache(victimClan.getId());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Atualiza stats agregadas do clan (kills, deaths)
     * Grug Brain: Query direta PostgreSQL, try-with-resources
     * Nota: Tabela clan_stats é opcional - se não existir, stats são calculadas on-the-fly
     */
    private void updateClanStats(int clanId, int kills, int deaths) {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO clan_stats (clan_id, total_kills, total_deaths, updated_at) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (clan_id) DO UPDATE SET " +
                "total_kills = clan_stats.total_kills + ?, " +
                "total_deaths = clan_stats.total_deaths + ?, " +
                "updated_at = CURRENT_TIMESTAMP")) {
            stmt.setInt(1, clanId);
            stmt.setInt(2, kills);
            stmt.setInt(3, deaths);
            stmt.setInt(4, kills);
            stmt.setInt(5, deaths);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Tabela pode não existir (opcional) - ignorar erro silenciosamente
            // Stats serão calculadas on-the-fly nas queries de ranking
        }
    }
}

