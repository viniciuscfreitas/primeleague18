package com.primeleague.x1.listeners;

import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Match;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Listener para monitorar movimento durante matches anywhere
 * Grug Brain: Validações simples, cancela match se necessário
 */
public class MatchMovementListener implements Listener {

    private final X1Plugin plugin;

    public MatchMovementListener(X1Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Monitora movimento durante match anywhere
     * Cancela match se players se afastarem muito ou mudarem de mundo
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Match match = plugin.getMatchManager().getMatch(player.getUniqueId());
        
        if (match == null || !match.isAnywhere()) {
            return; // Não está em match anywhere
        }

        // Só validar se match está em andamento (não durante countdown)
        if (match.getStatus() != Match.MatchStatus.FIGHTING) {
            return;
        }

        // Validar apenas se mudou de bloco (performance)
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        Player opponent = plugin.getServer().getPlayer(
            match.getPlayer1().equals(player.getUniqueId()) ? 
                match.getPlayer2() : match.getPlayer1());

        if (opponent == null || !opponent.isOnline()) {
            return;
        }

        // Validar apenas distância e mundo (pragmático)
        // Grug Brain: Não cancelar por GameMode/flying durante match (pode ser bug temporário)
        double maxDistance = plugin.getConfig().getDouble("match.anywhere.max-distance", 50.0);
        String validationError = com.primeleague.x1.utils.AnywhereMatchValidator.validate(
            player, opponent, maxDistance);

        if (validationError != null) {
            String msg = validationError + " §7Match cancelado.";
            player.sendMessage(msg);
            opponent.sendMessage(msg);
            plugin.getMatchManager().cancelMatch(match);
        }
    }

    /**
     * Monitora teleporte durante match anywhere
     * Cancela match se player teleportar (pode ser unfair)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Match match = plugin.getMatchManager().getMatch(player.getUniqueId());
        
        if (match == null || !match.isAnywhere()) {
            return; // Não está em match anywhere
        }

        // Só validar se match está em andamento
        if (match.getStatus() != Match.MatchStatus.FIGHTING) {
            return;
        }

        // Teleporte durante match anywhere = cancelar (unfair)
        Player opponent = plugin.getServer().getPlayer(
            match.getPlayer1().equals(player.getUniqueId()) ? 
                match.getPlayer2() : match.getPlayer1());

        if (opponent != null && opponent.isOnline()) {
            String msg = "§c" + player.getName() + " teleportou. Match cancelado.";
            player.sendMessage(msg);
            opponent.sendMessage(msg);
        }

        plugin.getMatchManager().cancelMatch(match);
    }
}

