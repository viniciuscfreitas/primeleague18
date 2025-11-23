package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.Arena;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de teleporte e inventário do Match
 * Grug Brain: Teleporte com retry, snapshots de inventário
 */
public class MatchTeleportManager {

    private final GladiadorPlugin plugin;
    private final Map<UUID, ItemStack[]> inventorySnapshots;
    private final Map<UUID, ItemStack[]> armorSnapshots;

    public MatchTeleportManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.inventorySnapshots = new ConcurrentHashMap<>();
        this.armorSnapshots = new ConcurrentHashMap<>();
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
                    player.sendMessage(ChatColor.GREEN + "Teleportado para a arena!");
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

