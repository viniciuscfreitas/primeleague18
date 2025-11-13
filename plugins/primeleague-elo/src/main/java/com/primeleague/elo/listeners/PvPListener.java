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

/**
 * Listener de PvP - Atualiza ELO apenas para PvP direto
 * Grug Brain: Processa apenas PvP direto (killer != null), evita duplicação com stats
 * Stats plugin processa PvP indireto separadamente
 */
public class PvPListener implements Listener {

    private final EloPlugin plugin;

    public PvPListener(EloPlugin plugin) {
        this.plugin = plugin;
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

                // Preparar mensagens (não enviar aqui - thread async)
                final String killerMsg = formatEloMessage(killerEloChange, killerElo);
                final String victimMsg = formatEloMessage(victimEloChange, victimElo);
                final boolean hasKillerMsg = killerMsg != null && !killerMsg.isEmpty();
                final boolean hasVictimMsg = victimMsg != null && !victimMsg.isEmpty();

                // Salvar UUIDs para re-buscar players na thread principal (mais seguro)
                final UUID killerUuid = killer.getUniqueId();
                final UUID victimUuid = victim.getUniqueId();

                // Voltar à thread principal para enviar mensagens (Bukkit API não é thread-safe)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Re-buscar players na thread principal (Player references podem ficar stale)
                        Player killerPlayer = plugin.getServer().getPlayer(killerUuid);
                        Player victimPlayer = plugin.getServer().getPlayer(victimUuid);

                        if (hasKillerMsg && killerPlayer != null && killerPlayer.isOnline()) {
                            killerPlayer.sendMessage(killerMsg);
                        }
                        if (hasVictimMsg && victimPlayer != null && victimPlayer.isOnline()) {
                            victimPlayer.sendMessage(victimMsg);
                        }
                    }
                }.runTask(plugin);
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

