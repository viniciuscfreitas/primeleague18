package com.primeleague.gladiador.listeners;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.models.ClanData;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.managers.MatchManager;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MatchListener implements Listener {

    private final GladiadorPlugin plugin;

    public MatchListener(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        MatchManager matchManager = plugin.getMatchManager();
        GladiadorMatch match = matchManager.getCurrentMatch();

        if (match == null || match.getState() != GladiadorMatch.MatchState.ACTIVE) return;
        if (!match.hasPlayer(victim.getUniqueId())) return;

        event.setDeathMessage(null); // Gerenciado pelo MatchManager
        event.getDrops().clear(); // Limpar drops para não sujar a arena

        matchManager.handleDeath(victim, victim.getKiller());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        MatchManager matchManager = plugin.getMatchManager();
        GladiadorMatch match = matchManager.getCurrentMatch();

        if (match == null) return;
        if (!match.hasPlayer(player.getUniqueId())) return;

        // Tratar quit como morte/eliminação (síncrono - player ainda está disponível)
        // Grug Brain: Executar antes do player ser removido do servidor
        // handleDeath já chama checkWinCondition internamente
        matchManager.handleDeath(player, null);
        plugin.getLogger().info("Player " + player.getName() + " desconectou durante Gladiador - eliminado");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        MatchManager matchManager = plugin.getMatchManager();
        GladiadorMatch match = matchManager.getCurrentMatch();

        if (match == null) return;
        if (!match.hasPlayer(victim.getUniqueId())) return;

        // Bloquear dano antes do início
        if (match.getState() != GladiadorMatch.MatchState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        // Verificar Friendly Fire
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            if (match.hasPlayer(attacker.getUniqueId())) {
                ClanData victimClan = ClansPlugin.getInstance().getClansManager().getClanByMember(victim.getUniqueId());
                ClanData attackerClan = ClansPlugin.getInstance().getClansManager().getClanByMember(attacker.getUniqueId());

                if (victimClan != null && attackerClan != null && victimClan.getId() == attackerClan.getId()) {
                    event.setCancelled(true);
                    attacker.sendMessage(ChatColor.RED + "Você não pode atacar membros do seu clan!");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        MatchManager matchManager = plugin.getMatchManager();
        GladiadorMatch match = matchManager.getCurrentMatch();

        if (match == null) return;
        if (!match.hasPlayer(player.getUniqueId())) return;

        String message = event.getMessage().toLowerCase();

        // Bloquear comandos de clan durante match (anti-exploit)
        if (message.startsWith("/clan sair") ||
            message.startsWith("/clan kick") ||
            message.startsWith("/clan expulsar") ||
            message.startsWith("/clan convidar") ||
            message.startsWith("/clan aceitar")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Você não pode usar comandos de clan durante o Gladiador!");
            return;
        }

        // Permitir comandos essenciais
        if (message.startsWith("/g ") || message.equals("/g") ||
            message.startsWith("/tell ") ||
            message.startsWith("/gladiador sair")) { // Permitir sair
            return;
        }

        // Bloquear outros comandos
        if (!player.hasPermission("primeleague.admin")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Comandos bloqueados durante o Gladiador.");
        }
    }
}
