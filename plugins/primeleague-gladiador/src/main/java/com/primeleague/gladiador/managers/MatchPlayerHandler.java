package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.integrations.ClansAPI;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Handler consolidado para players no match (teleporte + gerenciamento)
 * Grug Brain: Consolida MatchTeleportManager + MatchPlayerManager para reduzir complexidade
 */
public class MatchPlayerHandler {

    private final GladiadorPlugin plugin;
    private final Map<UUID, ItemStack[]> inventorySnapshots;
    private final Map<UUID, ItemStack[]> armorSnapshots;
    private final ClanEliminationHandler eliminationHandler;

    public MatchPlayerHandler(GladiadorPlugin plugin, BroadcastManager broadcastManager) {
        this.plugin = plugin;
        this.inventorySnapshots = new ConcurrentHashMap<>();
        this.armorSnapshots = new ConcurrentHashMap<>();
        this.eliminationHandler = new ClanEliminationHandler(plugin, broadcastManager);
    }

    /**
     * Adiciona player ao match
     * Grug Brain: Valida clan via ClansAPI, cria ClanEntry se necessário, teleporta
     */
    public boolean addPlayer(Player player, GladiadorMatch match) {
        if (match == null || match.getState() != GladiadorMatch.MatchState.WAITING) {
            return false;
        }

        if (!ClansAPI.isEnabled()) {
            return false;
        }

        Object clanData = ClansAPI.getClanByMember(player.getUniqueId());
        if (clanData == null) return false;

        int clanId = ClansAPI.getClanId(clanData);
        String clanName = ClansAPI.getClanName(clanData);
        String clanTag = ClansAPI.getClanTag(clanData);

        if (clanId == -1 || clanName == null || clanTag == null) {
            return false;
        }

        if (match.hasPlayer(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Você já está no evento.");
            return false;
        }

        ClanEntry clanEntry = match.getClanEntry(clanId);
        if (clanEntry == null) {
            clanEntry = new ClanEntry(clanId, clanName, clanTag);
            match.addClanEntry(clanEntry);
        }

        saveInventorySnapshot(player);
        clanEntry.addPlayer(player.getUniqueId());
        match.getAlivePlayers().add(player.getUniqueId());
        teleportToArena(player, match, clanEntry);

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);

        int totalPlayers = match.getTotalPlayers();
        int totalClans = match.getClanEntries().size();
        // Consolidar mensagem de entrada (teleport + info em uma só)
        // Usar cores originais da tag (ClansPlugin armazena com cores)
        player.sendMessage(ChatColor.GREEN + "Você entrou no Gladiador como " + ChatColor.WHITE + "[" + clanEntry.getClanTag() + ChatColor.RESET + ChatColor.WHITE + "]" + ChatColor.GREEN + "! Teleportado para a arena. Aguarde o início (" + totalPlayers + " players, " + totalClans + " clans)");

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

        // Incrementar kills do killer ANTES de construir mensagem
        // Grug Brain: Ordem correta - incrementar primeiro para mensagem mostrar valor correto
        if (killer != null) {
            ClanEntry killerClanEntry = match.getClanEntry(killer.getUniqueId());
            if (killerClanEntry != null) {
                killerClanEntry.incrementKills();
                plugin.getStatsManager().addKills(killerClanEntry.getClanId(), 1);
                match.incrementPlayerKills(killer.getUniqueId());
            }
        }

        if (victim.isOnline()) {
            teleportToExit(victim);
            restoreInventory(victim);
        }

        // Construir mensagem DEPOIS do incremento para mostrar kills corretos
        String deathMessage = KillMessageBuilder.buildDeathMessage(victim, killer, victimClanEntry, match);
        Bukkit.broadcastMessage(deathMessage);

        plugin.getStatsManager().addDeaths(victimClanEntry.getClanId(), 1);

        int clansBeforeElimination = match.getAliveClansCount();
        if (victimClanEntry.getRemainingPlayersCount() == 0) {
            eliminateClan(victimClanEntry, match, clansBeforeElimination);
        }
    }

    /**
     * Elimina clan completamente
     * Grug Brain: Delega para ClanEliminationHandler
     */
    private void eliminateClan(ClanEntry clanEntry, GladiadorMatch match, int clansBeforeElimination) {
        eliminationHandler.handleElimination(clanEntry, match, clansBeforeElimination);
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

    /**
     * Salva snapshot do inventário do player
     * Grug Brain: Thread-safe, usado antes de entrar no match
     */
    public void saveInventorySnapshot(Player player) {
        inventorySnapshots.put(player.getUniqueId(), player.getInventory().getContents());
        armorSnapshots.put(player.getUniqueId(), player.getInventory().getArmorContents());
    }

    /**
     * Restaura inventário do player
     * Grug Brain: Restaura snapshot salvo, remove do cache
     */
    public void restoreInventory(Player player) {
        if (inventorySnapshots.containsKey(player.getUniqueId())) {
            player.getInventory().setContents(inventorySnapshots.get(player.getUniqueId()));
            player.getInventory().setArmorContents(armorSnapshots.get(player.getUniqueId()));
            inventorySnapshots.remove(player.getUniqueId());
            armorSnapshots.remove(player.getUniqueId());
        }
    }

    /**
     * Teleporta player para arena com retry automático
     * Grug Brain: Retry com delay, valida spawn antes
     */
    public void teleportToArena(Player player, GladiadorMatch match, ClanEntry clanEntry) {
        teleportToArenaWithRetry(player, match, clanEntry, 3);
    }

    /**
     * Teleporta player para arena com retry automático
     * Grug Brain: Cria nova instância de BukkitRunnable para cada retry
     */
    private void teleportToArenaWithRetry(final Player player, final GladiadorMatch match,
                                          final ClanEntry clanEntry, final int retriesLeft) {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<Location> spawns = plugin.getArenaManager().getSpawnPoints(match.getArena());
                if (!spawns.isEmpty()) {
                    player.teleport(spawns.get(0));
                    // Mensagem de teleporte já está na mensagem de entrada consolidada
                } else {
                    if (retriesLeft > 0) {
                        teleportToArenaWithRetry(player, match, clanEntry, retriesLeft - 1);
                        return;
                    }

                    player.sendMessage(ChatColor.RED + "Erro: Arena sem spawn point configurado!");
                    player.sendMessage(ChatColor.YELLOW + "Use: /gladiador setspawn " + match.getArena().getName());
                    match.getAlivePlayers().remove(player.getUniqueId());
                    clanEntry.removePlayer(player.getUniqueId());
                }
            }
        }.runTaskLater(plugin, retriesLeft < 3 ? 10L : 0L);
    }

    /**
     * Teleporta player para spawn de saída
     * Grug Brain: Usa config ou world spawn como fallback
     */
    public void teleportToExit(Player player) {
        Location exitSpawn = getExitSpawn();
        if (exitSpawn != null) {
            player.teleport(exitSpawn);
        } else {
            player.teleport(player.getWorld().getSpawnLocation());
        }
    }

    /**
     * Obtém spawn de saída configurado
     * Grug Brain: Retorna spawn do config ou world spawn como fallback
     */
    private Location getExitSpawn() {
        String world = plugin.getConfig().getString("spawn.exit-world");
        if (world == null) return null;

        org.bukkit.World w = Bukkit.getWorld(world);
        if (w == null) return null;

        double x = plugin.getConfig().getDouble("spawn.exit-x");
        double y = plugin.getConfig().getDouble("spawn.exit-y");
        double z = plugin.getConfig().getDouble("spawn.exit-z");
        float yaw = (float) plugin.getConfig().getDouble("spawn.exit-yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("spawn.exit-pitch", 0);

        return new Location(w, x, y, z, yaw, pitch);
    }

    /**
     * Limpa todos os snapshots
     * Grug Brain: Chamado quando match termina
     */
    public void clearSnapshots() {
        inventorySnapshots.clear();
        armorSnapshots.clear();
    }
}

