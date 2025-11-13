package com.stats;

import com.arena.ArenaPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;

/**
 * Listener de eventos - Stats tracking
 * Grug Brain: Tudo inline, sem abstrações
 */
public class StatsListener implements Listener {

    private final StatsPlugin plugin;

    public StatsListener(StatsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Incrementa kills e deaths quando player morre
     * Só conta se ambos players estão na arena
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Verificar se ArenaPlugin está disponível
        ArenaPlugin arenaPlugin = plugin.getArenaPlugin();
        if (arenaPlugin == null) {
            return;
        }

        // Só contar stats se vítima está na arena
        if (arenaPlugin.getPlayerState(victim) == null) {
            return;
        }

        // Incrementar deaths da vítima
        PlayerStats victimStats = plugin.getPlayerStats(victim.getUniqueId());
        victimStats.deaths++;

        // Incrementar kills do killer (se existe e está na arena)
        if (killer != null && arenaPlugin.getPlayerState(killer) != null) {
            PlayerStats killerStats = plugin.getPlayerStats(killer.getUniqueId());
            killerStats.kills++;

            // UX: Títulos e sons para killer
            handleKillUX(killer, victim);

            // UX: Milestones para killer
            checkMilestones(killer, killerStats.kills);
        }

        // UX: Títulos e sons para vítima
        handleDeathUX(victim, killer);

        // Atualizar scoreboards de todos os players após kill/death
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.updateAllScoreboards();
        });
    }

    /**
     * UX: Títulos e sons quando player mata alguém
     */
    private void handleKillUX(Player killer, Player victim) {
        boolean titulosEnabled = plugin.getConfig().getBoolean("ux.titulos-kill", true);
        boolean sonsEnabled = plugin.getConfig().getBoolean("ux.sons", true);
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-titulos", true);

        String brandingNome = plugin.getConfig().getString("branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("branding.cor", "§b");

        if (titulosEnabled) {
            try {
                if (mostrarBranding) {
                    // sendTitle em 1.8.8 só aceita 2 parâmetros (title, subtitle)
                    killer.sendTitle(brandingCor + "§l" + brandingNome,
                        "§c§lELIMINADO §7" + victim.getName());
                } else {
                    killer.sendTitle("§c§lELIMINADO", "§7" + victim.getName());
                }
            } catch (Exception e) {
                // Fallback para versões sem sendTitle
                killer.sendMessage(brandingCor + "§l[" + brandingNome + "] §c§lELIMINADO §7" + victim.getName());
            }
        }

        if (sonsEnabled) {
            killer.playSound(killer.getLocation(), Sound.LEVEL_UP, 1.0f, 1.2f);
        }
    }

    /**
     * UX: Títulos e sons quando player morre
     */
    private void handleDeathUX(Player victim, Player killer) {
        boolean titulosEnabled = plugin.getConfig().getBoolean("ux.titulos-death", true);
        boolean sonsEnabled = plugin.getConfig().getBoolean("ux.sons", true);
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-titulos", true);

        String brandingNome = plugin.getConfig().getString("branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("branding.cor", "§b");

        if (titulosEnabled) {
            try {
                String killerName = killer != null ? killer.getName() : "Algo";
                if (mostrarBranding) {
                    // sendTitle em 1.8.8 só aceita 2 parâmetros (title, subtitle)
                    victim.sendTitle(brandingCor + "§l" + brandingNome,
                        "§c§lVOCÊ MORREU §7" + killerName);
                } else {
                    victim.sendTitle("§c§lVOCÊ MORREU", "§7" + killerName);
                }
            } catch (Exception e) {
                // Fallback para versões sem sendTitle
                String killerName = killer != null ? killer.getName() : "Algo";
                victim.sendMessage(brandingCor + "§l[" + brandingNome + "] §c§lVOCÊ MORREU §7" + killerName);
            }
        }

        if (sonsEnabled) {
            // Sound.ANVIL_BREAK pode não existir em 1.8.8, usar ANVIL_LAND
            try {
                victim.playSound(victim.getLocation(), Sound.valueOf("ANVIL_BREAK"), 0.5f, 0.8f);
            } catch (IllegalArgumentException e) {
                // Fallback para ANVIL_LAND se ANVIL_BREAK não existir
                victim.playSound(victim.getLocation(), Sound.valueOf("ANVIL_LAND"), 0.5f, 0.8f);
            }
        }
    }

    /**
     * UX: Verifica e aplica milestones (5 kills, 10 kills, etc)
     * Grug Brain: Parse direto da lista do config
     */
    private void checkMilestones(Player player, int kills) {
        // Ler lista de milestones do config
        List<Map<?, ?>> milestonesList = plugin.getConfig().getMapList("ux.milestones");
        if (milestonesList == null || milestonesList.isEmpty()) {
            return;
        }

        for (Map<?, ?> milestoneMap : milestonesList) {
            Object killsObj = milestoneMap.get("kills");
            Object tituloObj = milestoneMap.get("titulo");

            if (killsObj == null || tituloObj == null) {
                continue;
            }

            int milestoneKills;
            if (killsObj instanceof Integer) {
                milestoneKills = (Integer) killsObj;
            } else if (killsObj instanceof Number) {
                milestoneKills = ((Number) killsObj).intValue();
            } else {
                continue;
            }

            String titulo = tituloObj.toString();

            if (kills == milestoneKills && !titulo.isEmpty()) {
                // Milestone alcançado!
                try {
                    // sendTitle em 1.8.8 só aceita 2 parâmetros (title, subtitle)
                    player.sendTitle(titulo, "§7Parabéns!");
                } catch (Exception e) {
                    player.sendMessage(titulo + " §7Parabéns!");
                }

                // Som especial (ENDERDRAGON_DEATH pode não existir em 1.8.8)
                try {
                    player.playSound(player.getLocation(), Sound.valueOf("ENDERDRAGON_DEATH"), 0.3f, 1.0f);
                } catch (IllegalArgumentException e) {
                    // Fallback para som alternativo
                    player.playSound(player.getLocation(), Sound.LEVEL_UP, 0.5f, 0.5f);
                }

                // Mensagem no chat
                String brandingNome = plugin.getConfig().getString("branding.nome", "PRIME LEAGUE");
                String brandingCor = plugin.getConfig().getString("branding.cor", "§b");
                plugin.getServer().broadcastMessage(brandingCor + "§l[" + brandingNome + "] " +
                    player.getName() + " alcançou " + milestoneKills + " kills!");

                break; // Só mostrar um milestone por kill
            }
        }
    }

    /**
     * Cria scoreboard quando player entra
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Delay de 1 tick para garantir que player está totalmente carregado
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.updateScoreboard(player);

            // Mensagem de boas-vindas com branding
            String brandingNome = plugin.getConfig().getString("branding.nome", "PRIME LEAGUE");
            String brandingCor = plugin.getConfig().getString("branding.cor", "§b");

            player.sendMessage("");
            player.sendMessage(brandingCor + "§l=== " + brandingNome + " ===");
            player.sendMessage("§7Use §b/stats §7para ver suas estatísticas");
            player.sendMessage("§7Use §b/top §7para ver o ranking");
            player.sendMessage("");
        }, 1L);
    }

    /**
     * Remove stats quando player sai
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.removePlayerStats(event.getPlayer().getUniqueId());
    }

    /**
     * Intercepta e formata chat com KDR
     * Grug Brain: Parse direto do config, lógica inline
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Obter stats do player
        PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
        double kdr = stats.getKDR();

        // Obter formato do config
        String formato = plugin.getConfig().getString("chat.formato", "§b[PRIME] §8[§7{0}§8] §b{1}: §7{2}");

        // Determinar cor do KDR baseado em thresholds
        String corKdr = getKDRColor(kdr);
        String kdrFormatado = String.format("%s%.2f", corKdr, kdr);

        // Substituir placeholders
        String mensagemFormatada = formato
            .replace("{0}", kdrFormatado)
            .replace("{1}", player.getName())
            .replace("{2}", message);

        // Cancelar evento original
        event.setCancelled(true);

        // Enviar mensagem formatada para todos online (thread-safe: usar scheduler para sync)
        // Grug Brain: Lógica inline, sem abstrações
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                onlinePlayer.sendMessage(mensagemFormatada);
            }
        });

        // Log no console também
        plugin.getLogger().info(String.format("[CHAT] %s: %s", player.getName(), message));
    }

    /**
     * Obtém cor do KDR baseado em thresholds
     * Grug Brain: Lógica inline, sem abstrações
     */
    private String getKDRColor(double kdr) {
        if (kdr >= 2.0) {
            return plugin.getConfig().getString("chat.cores.kdr-alto", "§c");
        } else if (kdr >= 1.0) {
            return plugin.getConfig().getString("chat.cores.kdr-medio", "§e");
        } else if (kdr >= 0.5) {
            return plugin.getConfig().getString("chat.cores.kdr-baixo", "§a");
        } else {
            return plugin.getConfig().getString("chat.cores.kdr-ruim", "§7");
        }
    }
}

