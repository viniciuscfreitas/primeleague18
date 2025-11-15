package com.primeleague.stats.listeners;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.stats.StatsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener de combate - Rastreia kills, deaths e killstreak
 * Grug Brain: Lógica inline, sem abstrações, usa CoreAPI
 * Apenas mortes PvP são contadas (direto ou indireto)
 */
public class CombatListener implements Listener {

    private final StatsPlugin plugin;
    // Grug Brain: Map simples para rastrear último dano de player (UUID vítima -> UUID atacante + timestamp)
    // Thread-safe para uso em eventos async
    private final Map<UUID, LastDamageInfo> lastPlayerDamage = new ConcurrentHashMap<>();
    // Tempo máximo para considerar PvP indireto (5 segundos)
    private static final long PVP_INDIRECT_TIMEOUT_MS = 5000;

    public CombatListener(StatsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Classe simples para armazenar último dano de player
     * Grug Brain: POJO simples, sem abstrações
     */
    private static class LastDamageInfo {
        final UUID attackerUuid;
        final long timestamp;

        LastDamageInfo(UUID attackerUuid, long timestamp) {
            this.attackerUuid = attackerUuid;
            this.timestamp = timestamp;
        }
    }

    /**
     * Rastreia dano causado por players para detectar PvP indireto
     * Grug Brain: Evento síncrono, apenas salva no Map (rápido)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Só rastrear se ambos são players (PvP)
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        // Salvar último dano de player (para detectar PvP indireto)
        lastPlayerDamage.put(victim.getUniqueId(), new LastDamageInfo(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    /**
     * PlayerDeathEvent é assíncrono em Paper 1.8.8
     * Grug Brain: Query async recomendada, mas HikariCP é rápido o suficiente
     * UUID já está correto no PlayerLoginEvent (síncrono) - buscar diretamente por UUID
     * Apenas mortes PvP são contadas (direto ou indireto)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller(); // PvP direto

        // Verificar se foi PvP (direto ou indireto)
        Player actualKiller = getPvPKiller(victim, killer);
        if (actualKiller == null) {
            // Morte normal (queda, fogo, lava, etc.) - ignorar
            plugin.getLogger().info("Morte normal ignorada: " + victim.getName() + " (não foi PvP)");
            // Limpar último dano (não foi PvP)
            lastPlayerDamage.remove(victim.getUniqueId());
            return;
        }

        // PvP confirmado - processar stats
        final Player finalKiller = actualKiller;

        // Executar em thread assíncrona para não bloquear
        new BukkitRunnable() {
            @Override
            public void run() {
                // Grug Brain: Usar incremento atômico para evitar race condition
                // Incrementar deaths e resetar killstreak da vítima (PvP confirmado)
                PlayerData victimData = CoreAPI.incrementDeathsAndResetKillstreak(victim.getUniqueId());
                if (victimData == null) {
                    plugin.getLogger().warning("Player " + victim.getName() + " não encontrado no banco (UUID: " + victim.getUniqueId() + ")");
                    return;
                }

                // Atualizar stats do killer usando incremento atômico (PvP confirmado)
                PlayerData killerData = CoreAPI.incrementKillsAndKillstreak(finalKiller.getUniqueId());
                if (killerData != null) {
                    int newKillstreak = killerData.getKillstreak();
                    int bestKillstreak = killerData.getBestKillstreak();
                    
                    // Verificar se é novo best killstreak (best_killstreak já foi atualizado no SQL)
                    // Grug Brain: Se killstreak == best_killstreak, provavelmente é um novo recorde
                    // (best_killstreak só é atualizado quando killstreak atual >= best_killstreak)
                    if (newKillstreak == bestKillstreak && newKillstreak > 0) {
                        plugin.getLogger().info("NOVO BEST KILLSTREAK: " + finalKiller.getName() + " - " + newKillstreak + " kills!");
                    }

                    plugin.getLogger().info("Stats atualizadas: " + finalKiller.getName() +
                        " (Kills: " + killerData.getKills() + ", Killstreak: " + newKillstreak + ", Best: " + bestKillstreak + ")");

                    // Preparar mensagens (não enviar aqui - thread async)
                    final String killMsg = plugin.getConfig().getString("messages.kill", "")
                        .replace("{killstreak}", String.valueOf(newKillstreak));
                    final boolean hasKillMsg = !killMsg.isEmpty();

                    final String milestoneMsg;
                    final boolean hasMilestoneMsg;
                    if (newKillstreak > 0 && newKillstreak % 5 == 0) {
                        milestoneMsg = plugin.getConfig().getString("messages.killstreak-milestone", "")
                            .replace("{killstreak}", String.valueOf(newKillstreak));
                        hasMilestoneMsg = !milestoneMsg.isEmpty();
                    } else {
                        milestoneMsg = "";
                        hasMilestoneMsg = false;
                    }

                    // Voltar à thread principal para enviar mensagens (Bukkit API não é thread-safe)
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (hasKillMsg && finalKiller.isOnline()) {
                                finalKiller.sendMessage(killMsg);
                            }
                            if (hasMilestoneMsg && finalKiller.isOnline()) {
                                finalKiller.sendMessage(milestoneMsg);
                            }
                        }
                    }.runTask(plugin);
                }

                plugin.getLogger().info("Stats atualizadas: " + victim.getName() +
                    " (Deaths: " + victimData.getDeaths() + ", Killstreak resetado)");

                // Limpar último dano (já processado)
                lastPlayerDamage.remove(victim.getUniqueId());

                // Preparar mensagem de death
                final String deathMsg = plugin.getConfig().getString("messages.death", "");
                final boolean hasDeathMsg = !deathMsg.isEmpty();

                // Voltar à thread principal para enviar mensagem
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (hasDeathMsg && victim.isOnline()) {
                            victim.sendMessage(deathMsg);
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Verifica se morte foi PvP (direto ou indireto) e retorna o killer
     * Grug Brain: Lógica inline, sem abstrações
     * @param victim Vítima
     * @param directKiller Killer direto (pode ser null)
     * @return Killer (PvP direto ou indireto) ou null se não foi PvP
     */
    private Player getPvPKiller(Player victim, Player directKiller) {
        // PvP direto: killer não é null
        if (directKiller != null) {
            return directKiller;
        }

        // PvP indireto: verificar último dano de player (dentro de 5 segundos)
        LastDamageInfo lastDamage = lastPlayerDamage.get(victim.getUniqueId());
        if (lastDamage != null) {
            long timeSinceDamage = System.currentTimeMillis() - lastDamage.timestamp;
            if (timeSinceDamage <= PVP_INDIRECT_TIMEOUT_MS) {
                // Último dano foi de player há menos de 5 segundos - PvP indireto
                Player indirectKiller = plugin.getServer().getPlayer(lastDamage.attackerUuid);
                if (indirectKiller != null && indirectKiller.isOnline()) {
                    plugin.getLogger().info("PvP indireto detectado: " + victim.getName() + " morto por " + indirectKiller.getName() + " (hit há " + (timeSinceDamage / 1000.0) + "s)");
                    return indirectKiller;
                }
            }
        }

        // Não foi PvP
        return null;
    }

    /**
     * PlayerQuitEvent é síncrono
     * Grug Brain: Query async para não bloquear (quit pode ser raro, mas não queremos lag)
     * UUID já está correto no PlayerLoginEvent (síncrono) - buscar diretamente por UUID
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Limpar último dano ao sair (limpeza de memória)
        lastPlayerDamage.remove(player.getUniqueId());

        // Executar em thread assíncrona para não bloquear
        new BukkitRunnable() {
            @Override
            public void run() {
                // UUID já está correto no PlayerLoginEvent - buscar diretamente por UUID
                PlayerData data = CoreAPI.getPlayer(player.getUniqueId());
                if (data != null) {
                    data.setLastSeenAt(new Date());
                    CoreAPI.savePlayer(data);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}


