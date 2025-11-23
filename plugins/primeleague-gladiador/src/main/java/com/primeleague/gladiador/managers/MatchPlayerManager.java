package com.primeleague.gladiador.managers;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.managers.ClansManager;
import com.primeleague.clans.models.ClanData;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gerenciador de players no Match
 * Grug Brain: Gerencia entrada, saída, morte e eliminação de players
 */
public class MatchPlayerManager {

    private final GladiadorPlugin plugin;
    private final MatchTeleportManager teleportManager;

    public MatchPlayerManager(GladiadorPlugin plugin, MatchTeleportManager teleportManager) {
        this.plugin = plugin;
        this.teleportManager = teleportManager;
    }

    /**
     * Adiciona player ao match
     * Grug Brain: Valida clan, cria ClanEntry se necessário, teleporta
     */
    public boolean addPlayer(Player player, GladiadorMatch match) {
        if (match == null || match.getState() != GladiadorMatch.MatchState.WAITING) {
            return false;
        }

        ClansPlugin clansPlugin = (ClansPlugin) Bukkit.getPluginManager().getPlugin("PrimeleagueClans");
        if (clansPlugin == null) return false;
        ClansManager clansManager = clansPlugin.getClansManager();
        ClanData clanData = clansManager.getClanByMember(player.getUniqueId());

        if (clanData == null) return false;

        if (match.hasPlayer(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Você já está no evento.");
            return false;
        }

        ClanEntry clanEntry = match.getClanEntry(clanData.getId());
        if (clanEntry == null) {
            clanEntry = new ClanEntry(clanData.getId(), clanData.getName(), clanData.getTag());
            match.addClanEntry(clanEntry);
        }

        teleportManager.saveInventorySnapshot(player);
        clanEntry.addPlayer(player.getUniqueId());
        match.getAlivePlayers().add(player.getUniqueId());
        teleportManager.teleportToArena(player, match, clanEntry);

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);

        player.sendMessage(ChatColor.GREEN + "Você entrou no Gladiador! Aguarde o início.");

        if (plugin.getTabIntegration().isEnabled()) {
            plugin.getTabIntegration().updateTablist(player);
        }

        return true;
    }

    /**
     * Processa morte de player
     * Grug Brain: Remove player, atualiza stats, verifica eliminação
     */
    public void handleDeath(Player victim, Player killer, GladiadorMatch match) {
        if (match == null || match.getState() == GladiadorMatch.MatchState.ENDING) return;
        if (!match.hasPlayer(victim.getUniqueId())) return;

        ClanEntry victimClanEntry = match.getClanEntry(victim.getUniqueId());
        if (victimClanEntry == null) return;

        victimClanEntry.removePlayer(victim.getUniqueId());
        match.getAlivePlayers().remove(victim.getUniqueId());
        victimClanEntry.incrementDeaths();

        if (victim.isOnline()) {
            teleportManager.teleportToExit(victim);
            teleportManager.restoreInventory(victim);
        }

        long survivalTime = (System.currentTimeMillis() - match.getStartTime()) / 1000;
        long minutes = survivalTime / 60;
        long seconds = survivalTime % 60;
        String survivalStr = (minutes > 0 ? minutes + "m " : "") + seconds + "s";

        String deathMessage = buildDeathMessage(victim, killer, victimClanEntry, survivalStr, match);
        Bukkit.broadcastMessage(deathMessage);

        if (killer != null) {
            ClanEntry killerClanEntry = match.getClanEntry(killer.getUniqueId());
            if (killerClanEntry != null) {
                killerClanEntry.incrementKills();
                plugin.getStatsManager().addKills(killerClanEntry.getClanId(), 1);
            }
        }

        plugin.getStatsManager().addDeaths(victimClanEntry.getClanId(), 1);

        // Verificar quantos clans estavam vivos ANTES de eliminar (Grug Brain: lógica correta)
        int clansBeforeElimination = match.getAliveClansCount();
        if (victimClanEntry.getRemainingPlayersCount() == 0) {
            eliminateClan(victimClanEntry, match, clansBeforeElimination);
        }
    }

    /**
     * Constrói mensagem de morte
     * Grug Brain: Formata mensagem baseada no tipo de morte
     */
    private String buildDeathMessage(Player victim, Player killer, ClanEntry victimClanEntry, 
                                     String survivalStr, GladiadorMatch match) {
        if (killer != null) {
            double distance = victim.getLocation().distance(killer.getLocation());
            ClanEntry killerClanEntry = match.getClanEntry(killer.getUniqueId());
            String killerClanTag = killerClanEntry != null ? " [" + killerClanEntry.getClanTag() + "]" : "";
            
            return ChatColor.RED + "☠ " + ChatColor.WHITE + victim.getName() +
                   ChatColor.GRAY + " [" + victimClanEntry.getClanTag() + "]" +
                   ChatColor.RED + " foi eliminado por " +
                   ChatColor.WHITE + killer.getName() + killerClanTag +
                   ChatColor.GRAY + " (" + String.format("%.1f", distance) + "m)" +
                   ChatColor.DARK_GRAY + " | Sobreviveu: " + survivalStr;
        } else if (!victim.isOnline()) {
            return ChatColor.RED + "☠ " + ChatColor.WHITE + victim.getName() +
                   ChatColor.GRAY + " [" + victimClanEntry.getClanTag() + "]" +
                   ChatColor.RED + " desconectou e foi eliminado!" +
                   ChatColor.DARK_GRAY + " | Sobreviveu: " + survivalStr;
        } else {
            return ChatColor.RED + "☠ " + ChatColor.WHITE + victim.getName() +
                   ChatColor.GRAY + " [" + victimClanEntry.getClanTag() + "]" +
                   ChatColor.RED + " foi eliminado por PvE" +
                   ChatColor.DARK_GRAY + " | Sobreviveu: " + survivalStr;
        }
    }

    /**
     * Elimina clan completamente
     * Grug Brain: Broadcast, stats, Discord notifications
     * @param clansBeforeElimination Quantos clans estavam vivos ANTES de eliminar este
     */
    private void eliminateClan(ClanEntry clanEntry, GladiadorMatch match, int clansBeforeElimination) {
        int remainingClans = match.getAliveClansCount();

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.RED + "❌ O clan " + ChatColor.BOLD + clanEntry.getClanTag() + ChatColor.RED + " foi eliminado!");

        if (remainingClans > 1) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Restam " + remainingClans + " clans na arena.");
        } else if (remainingClans == 1) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "Apenas 1 clan resta na arena!");
        }
        Bukkit.broadcastMessage("");

        plugin.getStatsManager().incrementParticipation(clanEntry.getClanId());

        // Notificar Discord via GladiadorIntegration
        plugin.getDiscordIntegration().sendClanEliminated(clanEntry.getClanTag(), match.getAliveClansCount());

        // Partículas e som quando clan é eliminado
        // Grug Brain: Usa Effect API (compatível com Paper 1.8.8)
        for (UUID uuid : clanEntry.getRemainingPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                org.bukkit.Location loc = p.getLocation();
                // Efeitos de fogueira (compatível com 1.8.8 - usando efeitos garantidos)
                for (int i = 0; i < 10; i++) {
                    double offsetX = (Math.random() - 0.5) * 2.0;
                    double offsetY = Math.random() * 2.0;
                    double offsetZ = (Math.random() - 0.5) * 2.0;
                    Location effectLoc = loc.clone().add(offsetX, offsetY, offsetZ);
                    // Efeitos garantidos em Paper 1.8.8
                    p.getWorld().playEffect(effectLoc, org.bukkit.Effect.MOBSPAWNER_FLAMES, 0);
                    p.getWorld().playEffect(effectLoc, org.bukkit.Effect.SMOKE, 0);
                }
            }
        }

        // Som de eliminação
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOnline()) {
                p.playSound(p.getLocation(), org.bukkit.Sound.AMBIENCE_THUNDER, 1f, 1f);
            }
        }
        
        // Anúncio quando restam 2 clans
        // Grug Brain: Verifica se havia 3+ clans ANTES e agora restam 2
        if (clansBeforeElimination > 2 && remainingClans == 2) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ChatColor.GOLD + ChatColor.BOLD.toString() + "⚔ APENAS 2 CLANS RESTAM! ⚔");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "A batalha final está chegando!");
            Bukkit.broadcastMessage("");
            
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOnline()) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.WITHER_SPAWN, 1f, 1f);
                }
            }
        }
    }

    /**
     * Verifica condição de vitória
     * Grug Brain: Retorna vencedor ou null se não há vencedor ainda
     */
    public ClanEntry checkWinCondition(GladiadorMatch match) {
        if (match == null) return null;

        List<ClanEntry> aliveClans = match.getClanEntries().stream()
                .filter(c -> c.getRemainingPlayersCount() > 0)
                .collect(Collectors.toList());

        if (aliveClans.size() == 1) {
            return aliveClans.get(0);
        }

        return null;
    }

    /**
     * Verifica se match deve ser cancelado
     * Grug Brain: Retorna motivo do cancelamento ou null
     */
    public String shouldCancelMatch(GladiadorMatch match) {
        if (match == null) return null;

        List<ClanEntry> aliveClans = match.getClanEntries().stream()
                .filter(c -> c.getRemainingPlayersCount() > 0)
                .collect(Collectors.toList());

        if (aliveClans.isEmpty()) {
            return "Todos os clans foram eliminados.";
        }

        if (match.getAlivePlayers().isEmpty()) {
            return "Todos os jogadores desconectaram.";
        }

        return null;
    }
}

