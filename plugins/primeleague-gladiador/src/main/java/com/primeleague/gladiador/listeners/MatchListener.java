package com.primeleague.gladiador.listeners;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.integrations.ClansAPI;
import com.primeleague.gladiador.managers.MatchManager;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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

        // Processar morte apenas se estiver no match (qualquer estado, mas principalmente ACTIVE)
        if (match == null) return;
        if (!match.hasPlayer(victim.getUniqueId())) return;

        // Só processar mortes durante ACTIVE (mortes em WAITING/PREPARATION são raras, mas podem acontecer)
        if (match.getState() != GladiadorMatch.MatchState.ACTIVE) return;

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

    /**
     * Proteção PvE: Bloqueia dano de queda, fogo, mobs, etc antes do match começar
     * Grug Brain: Proteção crítica, simples
     * Nota: EntityDamageByEntityEvent (PvP) é gerenciado separadamente com HIGHEST priority
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamagePvE(EntityDamageEvent event) {
        // Ignorar se for PvP (EntityDamageByEntityEvent) - será gerenciado por onEntityDamageByEntity
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        MatchManager matchManager = plugin.getMatchManager();
        GladiadorMatch match = matchManager.getCurrentMatch();

        if (match == null) return;
        if (!match.hasPlayer(player.getUniqueId())) return;

        // Bloquear todo dano PvE antes do match começar (WAITING/PREPARATION)
        if (match.getState() != GladiadorMatch.MatchState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        // Durante ACTIVE, permitir dano PvE (queda, fogo, etc podem matar)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
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
                attacker.sendMessage(ChatColor.YELLOW + "O PvP ainda não foi ativado! Aguarde o início do Gladiador.");
            }
            return;
        }

        // Match está ACTIVE - verificar Friendly Fire
        if (!ClansAPI.isEnabled()) {
            return;
        }

        Object victimClan = ClansAPI.getClanByMember(victim.getUniqueId());
        Object attackerClan = ClansAPI.getClanByMember(attacker.getUniqueId());

        if (victimClan != null && attackerClan != null) {
            int victimClanId = ClansAPI.getClanId(victimClan);
            int attackerClanId = ClansAPI.getClanId(attackerClan);
            if (victimClanId != -1 && attackerClanId != -1 && victimClanId == attackerClanId) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "Você não pode atacar membros do seu clan!");
                return;
            }
        }

        // PvP permitido - apenas descancelar se foi cancelado por outros plugins
        // Grug Brain: HIGHEST priority garante que processamos depois, mas só descancelamos se necessário
        // Isso evita conflitos desnecessários com outros plugins
        if (event.isCancelled()) {
            // Verificar se foi cancelado por plugins de delay/pvp152 (permitir PvP no Gladiador)
            event.setCancelled(false);
        }

        // Tracking de dano por player (para DAMAGE)
        // Grug Brain: Usar merge atômico para thread safety
        double damage = event.getFinalDamage();
        match.getPlayerDamage().merge(attacker.getUniqueId(), damage, Double::sum);
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
            player.sendMessage(ChatColor.RED + "Comandos de clan bloqueados durante o Gladiador!");
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
