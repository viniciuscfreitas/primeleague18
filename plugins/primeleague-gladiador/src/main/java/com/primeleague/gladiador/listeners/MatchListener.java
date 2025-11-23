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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Só processar se ambos são players (PvP)
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        MatchManager matchManager = plugin.getMatchManager();
        GladiadorMatch match = matchManager.getCurrentMatch();

        // Se não há match, não interferir (deixar outros plugins gerenciarem)
        if (match == null) {
            return;
        }

        // Verificar se AMBOS estão no match
        boolean victimInMatch = match.hasPlayer(victim.getUniqueId());
        boolean attackerInMatch = match.hasPlayer(attacker.getUniqueId());

        // Se apenas um está no match, não interferir (pode ser PvP normal fora do match)
        if (!victimInMatch && !attackerInMatch) {
            return;
        }

        // Se apenas um está no match, bloquear (proteção)
        if (victimInMatch != attackerInMatch) {
            event.setCancelled(true);
            return;
        }

        // Ambos estão no match - verificar estado
        // Bloquear dano antes do início
        if (match.getState() != GladiadorMatch.MatchState.ACTIVE) {
            event.setCancelled(true);
            if (match.getState() == GladiadorMatch.MatchState.PREPARATION) {
                attacker.sendMessage(ChatColor.YELLOW + "O PvP ainda não foi ativado! Aguarde o início do match.");
            }
            return;
        }

        // Match está ACTIVE - verificar Friendly Fire
        ClansPlugin clansPlugin = (ClansPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("PrimeleagueClans");
        if (clansPlugin == null) return;
        ClanData victimClan = clansPlugin.getClansManager().getClanByMember(victim.getUniqueId());
        ClanData attackerClan = clansPlugin.getClansManager().getClanByMember(attacker.getUniqueId());

        if (victimClan != null && attackerClan != null && victimClan.getId() == attackerClan.getId()) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "Você não pode atacar membros do seu clan!");
            return;
        }

        // PvP permitido - apenas descancelar se foi cancelado por outros plugins
        // Grug Brain: HIGHEST priority garante que processamos depois, mas só descancelamos se necessário
        // Isso evita conflitos desnecessários com outros plugins
        if (event.isCancelled()) {
            // Verificar se foi cancelado por plugins de delay/pvp152 (permitir PvP no Gladiador)
            event.setCancelled(false);
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
