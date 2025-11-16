package com.primeleague.x1.listeners;

import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Match;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Listener de eventos de match
 * Grug Brain: Detecta vencedor, cancela match em disconnects, previne dano fora de match
 */
public class MatchListener implements Listener {

    private final X1Plugin plugin;

    public MatchListener(X1Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Detecta vencedor quando player morre
     * PlayerDeathEvent é assíncrono em Paper 1.8.8
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Verificar se vítima está em match
        Match match = plugin.getMatchManager().getMatch(victim.getUniqueId());
        if (match == null || match.getStatus() != Match.MatchStatus.FIGHTING) {
            return; // Não está em match ou match não está em andamento
        }

		// Não dropar itens do kit no chão durante X1
		try {
			event.getDrops().clear();
			event.setDroppedExp(0);
			// Opcional: ocultar death message global em X1
			// event.setDeathMessage(null);
		} catch (Exception ignored) {
		}

        // Verificar se killer é o oponente
        UUID opponentUuid = match.getPlayer1().equals(victim.getUniqueId()) ? 
            match.getPlayer2() : match.getPlayer1();
        
        if (killer == null || !killer.getUniqueId().equals(opponentUuid)) {
            // Morte não foi pelo oponente (queda, etc) - não finalizar match
            return;
        }

        // Vencedor confirmado
        UUID winnerUuid = killer.getUniqueId();
        
        // Finalizar match (PlayerDeathEvent é assíncrono - executar na thread principal)
        final Match finalMatch = match;
        final UUID finalWinnerUuid = winnerUuid;
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                plugin.getMatchManager().endMatch(finalMatch, finalWinnerUuid);
            }
        });
    }

	/**
	 * Bloqueia soltar itens enquanto estiver em X1 (evita espalhar kit)
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerDrop(PlayerDropItemEvent event) {
		Player player = event.getPlayer();
		Match match = plugin.getMatchManager().getMatch(player.getUniqueId());
		if (match == null) {
			return;
		}
		// Em qualquer status de match, não permitir drop
		event.setCancelled(true);
	}

    /**
     * Cancela match se player desconectar
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Match match = plugin.getMatchManager().getMatch(player.getUniqueId());

        if (match != null) {
            // Calcular UUID do oponente
            UUID opponentUuid = match.getPlayer1().equals(player.getUniqueId()) ? 
                match.getPlayer2() : match.getPlayer1();
            
            // Verificar se deve penalizar ELO
            boolean penalize = plugin.getConfig().getBoolean("match.penalize-disconnect", false);
            
            if (!penalize && match.isRanked()) {
                // Não penalizar ELO - apenas cancelar match
                plugin.getMatchManager().cancelMatch(match);
            } else {
                // Penalizar: considerar como derrota
                plugin.getMatchManager().endMatch(match, opponentUuid);
            }

            String msg = plugin.getConfig().getString("messages.match.disconnect", 
                "§c{player} desconectou. Match cancelado.")
                .replace("{player}", player.getName());
            Player opponent = plugin.getServer().getPlayer(opponentUuid);
            if (opponent != null) {
                opponent.sendMessage(msg);
            }
        }

        // Remover da queue (já removido por QueueListener, mas manter para segurança)
        plugin.getQueueManager().removeFromQueue(player.getUniqueId());
    }

    /**
     * Previne dano entre players que não estão em match
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        // Verificar se ambos estão no mesmo match
        Match victimMatch = plugin.getMatchManager().getMatch(victim.getUniqueId());
        Match attackerMatch = plugin.getMatchManager().getMatch(attacker.getUniqueId());

        if (victimMatch == null || attackerMatch == null || victimMatch != attackerMatch) {
            // Não estão no mesmo match - cancelar dano
            event.setCancelled(true);
            return;
        }

        // Verificar se match está em andamento
        if (victimMatch.getStatus() != Match.MatchStatus.FIGHTING) {
            event.setCancelled(true);
        }
    }
}

