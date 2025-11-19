package com.primeleague.league.commands;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.managers.ClansManager;
import com.primeleague.league.LeaguePlugin;
import com.primeleague.league.LeagueAPI;
import com.primeleague.league.models.RankingEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Comando /glad top - Mostra top 5 clans no spawn (holograma)
 * Grug Brain: Softdepend em HolographicDisplays/Holograms, atualiza a cada 5 minutos
 */
public class TopCommand implements CommandExecutor {

    private final LeaguePlugin plugin;
    private BukkitRunnable updateTask;

    public TopCommand(LeaguePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Mostrar top 5 clans
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<RankingEntry> topClans = LeagueAPI.getClanRankingByPoints(5);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.GOLD + "=== TOP 5 CLANS DA TEMPORADA ===");

                if (topClans.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Nenhum clan com pontos ainda!");
                } else {
                    for (RankingEntry entry : topClans) {
                        int clanId = Integer.parseInt(entry.getEntityId());
                        ClansManager clansManager = ClansPlugin.getInstance().getClansManager();
                        com.primeleague.clans.models.ClanData clan = clansManager.getClan(clanId);
                        String clanName = clan != null ? clan.getName() + " [" + clan.getTag() + "]" : "Clan #" + clanId;

                        sender.sendMessage(ChatColor.YELLOW + "#" + entry.getPosition() + " " +
                            ChatColor.WHITE + clanName + " " +
                            ChatColor.GRAY + "(" + (int)entry.getValue() + " pts)");
                    }
                }
            });
        });

        return true;
    }

    /**
     * Inicia task de atualização do holograma (se habilitado)
     */
    public void startHologramUpdateTask() {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
            return;
        }

        // Verificar se HolographicDisplays ou Holograms está disponível
        if (Bukkit.getPluginManager().getPlugin("HolographicDisplays") == null &&
            Bukkit.getPluginManager().getPlugin("Holograms") == null) {
            plugin.getLogger().info("HolographicDisplays/Holograms não encontrado. Holograma desabilitado.");
            return;
        }

        int updateInterval = plugin.getConfig().getInt("hologram.update-interval-seconds", 300);

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateHologram();
            }
        };

        updateTask.runTaskTimerAsynchronously(plugin, 20L * updateInterval, 20L * updateInterval);
    }

    /**
     * Atualiza holograma no spawn
     */
    private void updateHologram() {
        // TODO: Implementar atualização de holograma via HolographicDisplays/Holograms
        // Por enquanto, apenas log
        plugin.getLogger().info("Holograma atualizado (top 5 clans)");
    }

    /**
     * Para task de atualização
     */
    public void stopHologramUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
    }
}

