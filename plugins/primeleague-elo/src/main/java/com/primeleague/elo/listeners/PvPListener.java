package com.primeleague.elo.listeners;

import com.primeleague.elo.EloAPI;
import com.primeleague.elo.EloPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener de PvP - Atualiza ELO apenas para PvP direto
 * Grug Brain: Processa apenas PvP direto (killer != null), evita duplicação com stats
 * Stats plugin processa PvP indireto separadamente
 *
 * Fase 2: Armazena mudança de ELO em cache temporário para consolidação
 */
public class PvPListener implements Listener {

    private final EloPlugin plugin;

    // Cache temporário para mudanças de ELO (UUID -> EloChangeData)
    // TTL: 5 segundos (suficiente para consolidator processar)
    private static final ConcurrentHashMap<UUID, EloChangeData> eloChangeCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5000; // 5 segundos

    /**
     * Classe simples para armazenar mudança de ELO
     */
    private static class EloChangeData {
        final int eloChange;
        final long timestamp;

        EloChangeData(int eloChange) {
            this.eloChange = eloChange;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    public PvPListener(EloPlugin plugin) {
        this.plugin = plugin;

        // Task periódica para limpar cache expirado (a cada 30s)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanupExpiredCache();
        }, 600L, 600L); // A cada 30 segundos
    }

    /**
     * Limpa cache expirado
     */
    private static void cleanupExpiredCache() {
        eloChangeCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * API pública estática para consolidator acessar mudança de ELO
     */
    public static Integer getEloChange(UUID playerUuid) {
        EloChangeData data = eloChangeCache.get(playerUuid);
        if (data == null || data.isExpired()) {
            return null;
        }
        return data.eloChange;
    }

    /**
     * Limpa cache de ELO para um player
     */
    public static void clearEloChange(UUID playerUuid) {
        eloChangeCache.remove(playerUuid);
    }

    /**
     * PlayerDeathEvent é assíncrono em Paper 1.8.8
     * Grug Brain: Query async para não bloquear, apenas PvP direto
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();

        // APENAS PvP direto (killer != null)
        // PvP indireto é processado pelo Stats plugin separadamente
        if (killer == null) {
            return;
        }

        // PvP direto confirmado
        Player victim = event.getEntity();

        // Executar em thread assíncrona (PlayerDeathEvent é async)
        new BukkitRunnable() {
            @Override
            public void run() {
                // Buscar ELO antes de atualizar para calcular mudança
                int killerOldElo = EloAPI.getElo(killer.getUniqueId());
                int victimOldElo = EloAPI.getElo(victim.getUniqueId());

                // Atualizar ELO via API thread-safe
                int killerEloChange = EloAPI.updateEloAfterPvP(killer.getUniqueId(), victim.getUniqueId());

                // Buscar ELO atualizado para mensagens
                int killerElo = EloAPI.getElo(killer.getUniqueId());
                int victimElo = EloAPI.getElo(victim.getUniqueId());

                // Calcular mudança da vítima
                int victimEloChange = victimElo - victimOldElo;

                // Fase 2: Armazenar mudança de ELO em cache temporário para consolidação
                // Ao invés de enviar mensagem imediatamente, armazena no cache
                // O PvPRewardConsolidator no Core buscará e consolidará com outras recompensas
                if (killerEloChange != 0) {
                    eloChangeCache.put(killer.getUniqueId(), new EloChangeData(killerEloChange));
                }

                // Vítima ainda recebe mensagem separada (perde ELO, não é recompensa consolidada)
                final String victimMsg = formatEloMessage(victimEloChange, victimElo);
                final boolean hasVictimMsg = victimMsg != null && !victimMsg.isEmpty();
                final UUID victimUuid = victim.getUniqueId();

                // Enviar mensagem para vítima (ela perde ELO, não é consolidada)
                if (hasVictimMsg) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            Player victimPlayer = plugin.getServer().getPlayer(victimUuid);
                            if (victimPlayer != null && victimPlayer.isOnline()) {
                                victimPlayer.sendMessage(victimMsg);
                            }
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Formata mensagem de mudança de ELO
     * Grug Brain: Método utilitário simples, inline
     */
    private String formatEloMessage(int eloChange, int currentElo) {
        if (eloChange == 0) {
            return null;
        }

        String changeStr = eloChange > 0 ? "§a+" + eloChange : "§c" + eloChange;
        String msg = plugin.getConfig().getString("messages.elo-change", "");

        if (msg.isEmpty()) {
            return null;
        }

        return msg.replace("{change}", changeStr)
                  .replace("{elo}", String.valueOf(currentElo));
    }
}

