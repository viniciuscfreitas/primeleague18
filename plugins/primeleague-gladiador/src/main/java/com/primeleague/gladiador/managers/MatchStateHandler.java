package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

/**
 * Handler consolidado para estados e finalização de match
 * Grug Brain: Consolida MatchStateManager + MatchEndHandler para reduzir complexidade
 */
public class MatchStateHandler {

    private final GladiadorPlugin plugin;
    private final BroadcastManager broadcastManager;
    private final BorderManager borderManager;
    private final MatchPlayerHandler playerHandler;
    private final MatchDatabaseManager databaseManager;
    private final MatchRankingManager rankingManager;

    public MatchStateHandler(GladiadorPlugin plugin, BroadcastManager broadcastManager,
                            BorderManager borderManager, MatchPlayerHandler playerHandler,
                            MatchDatabaseManager databaseManager, MatchRankingManager rankingManager) {
        this.plugin = plugin;
        this.broadcastManager = broadcastManager;
        this.borderManager = borderManager;
        this.playerHandler = playerHandler;
        this.databaseManager = databaseManager;
        this.rankingManager = rankingManager;
    }

    /**
     * Inicia preparação (fecha entrada, countdown)
     * Grug Brain: Valida mínimo de clans, inicia countdown
     */
    public void beginPreparation(GladiadorMatch match) {
        if (match == null) return;

        int minClans = plugin.getConfig().getInt("match.min-clans", 2);
        if (match.getClanEntries().size() < minClans) {
            broadcastManager.broadcastCancelled("Número mínimo de clans não atingido (" + minClans + ").");
            return;
        }

        if (match.getClanEntries().isEmpty()) {
            broadcastManager.broadcastCancelled("Falta de participantes.");
            return;
        }

        match.setState(GladiadorMatch.MatchState.PREPARATION);

        int prepTime = plugin.getConfig().getInt("arena.preparation-time", 30);
        broadcastManager.broadcastPreparation(match, prepTime);

        borderManager.setInitialBorder(match.getArena());

        int countdownTime = Math.max(10, prepTime / 3);
        startCountdown(match, countdownTime);
    }

    /**
     * Inicia countdown para PvP
     * Grug Brain: Countdown exato conforme especificação (10, 5, 4, 3, 2, 1)
     * Aguarda 1 minuto (60s) antes de iniciar countdown de 10 segundos
     */
    private void startCountdown(GladiadorMatch match, int countdownTime) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (match == null || match.getState() != GladiadorMatch.MatchState.PREPARATION) {
                    return;
                }

                // Avisar que countdown vai começar
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6&l[GLADIADOR] &eCountdown iniciando em 10 segundos..."));

                new BukkitRunnable() {
                    int count = 10;

                    @Override
                    public void run() {
                        if (match == null || match.getState() != GladiadorMatch.MatchState.PREPARATION) {
                            this.cancel();
                            return;
                        }

                        if (count == 10 || count == 5 || count == 4 || count == 3 || count == 2 || count == 1) {
                            String countColor = count <= 3 ? "&c" : (count <= 5 ? "&e" : "&7");
                            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                                "&6&l[GLADIADOR] &eCombate iniciando em " + countColor + count + " &esegundo(s)"));

                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (p.isOnline()) {
                                    if (count <= 5) {
                                        p.playSound(p.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
                                    } else {
                                        p.playSound(p.getLocation(), Sound.CLICK, 0.5f, 1f);
                                    }
                                }
                            }
                        }

                        if (count <= 0) {
                            this.cancel();
                        }
                        count--;
                    }
                }.runTaskTimer(plugin, 0L, 20L);
            }
        }.runTaskLater(plugin, 20L * 60L); // Aguarda 1 minuto antes do countdown
    }

    /**
     * Ativa match (habilita PvP, inicia border shrink)
     * Grug Brain: Transição para ACTIVE, inicia sistemas
     */
    public BukkitTask activateMatch(GladiadorMatch match) {
        if (match == null) return null;

        match.setState(GladiadorMatch.MatchState.ACTIVE);
        match.setStartTime(System.currentTimeMillis());

        broadcastManager.broadcastPvPActivated(match);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOnline()) {
                p.playSound(p.getLocation(), Sound.ENDERDRAGON_GROWL, 1f, 0.8f);
                p.playSound(p.getLocation(), Sound.ANVIL_LAND, 1f, 0.5f);
            }
        }

        borderManager.enablePvP(match.getArena());
        BukkitTask borderTask = borderManager.startShrinkTask(match);
        match.setBorderTask(borderTask);

        broadcastManager.startStatusBroadcast(match);

        return borderTask;
    }

    /**
     * Finaliza match
     * Grug Brain: Transição para ENDING, processa finalização completa
     */
    public void endMatch(GladiadorMatch match) {
        if (match == null) return;

        if (match.getBorderTask() != null) {
            match.getBorderTask().cancel();
        }

        broadcastManager.stopStatusBroadcast();
        borderManager.resetBorder(match.getArena());
        borderManager.disablePvP(match.getArena());
    }

    /**
     * Processa finalização completa do match
     * Grug Brain: Teleporta vencedores, salva no banco, notifica Discord, registra LeagueAPI
     */
    public void handleMatchEnd(GladiadorMatch match, ClanEntry winner, MatchStatsCalculator.StatsResult stats,
                              double coinsReward) {
        // Celebrar vitória: fogos + mensagens, depois teleportar após delay
        for (UUID uuid : winner.getRemainingPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                // Mensagem mais informativa para vencedores
                int playerKills = match.getPlayerKills(uuid);
                p.sendMessage(ChatColor.GOLD + "=== VITÓRIA ===");
                p.sendMessage(ChatColor.YELLOW + "Parabéns! Seu clan venceu o Gladiador!");
                p.sendMessage(ChatColor.GRAY + "Você fez " + ChatColor.AQUA + playerKills + ChatColor.GRAY + " kills neste Gladiador.");
                p.sendMessage(ChatColor.GREEN + "Você tem 30 segundos para celebrar!");

                // Fogos de artifício (celebração)
                // Grug Brain: Fogos simples, configurados para explodir (1.8.8 compatível)
                Location loc = p.getLocation();
                for (int i = 0; i < 5; i++) {
                    Location fireworkLoc = loc.clone().add(
                        (Math.random() - 0.5) * 3,
                        Math.random() * 2 + 1,
                        (Math.random() - 0.5) * 3
                    );
                    org.bukkit.entity.Firework firework = p.getWorld().spawn(fireworkLoc, org.bukkit.entity.Firework.class);
                    FireworkEffect.Builder builder = FireworkEffect.builder();
                    builder.with(FireworkEffect.Type.BURST);
                    builder.withColor(org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE, org.bukkit.Color.RED);
                    builder.withFade(org.bukkit.Color.WHITE);
                    FireworkEffect effect = builder.build();
                    FireworkMeta meta = firework.getFireworkMeta();
                    meta.addEffect(effect);
                    meta.setPower(1); // Explode imediatamente
                    firework.setFireworkMeta(meta);
                }
            }
        }

        // Teleportar vencedores após delay de celebração (30 segundos - tempo para se reunir e tirar print)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : winner.getRemainingPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        playerHandler.teleportToExit(p);
                        playerHandler.restoreInventory(p);
                    }
                }
            }
        }.runTaskLater(plugin, 20L * 30); // 30 segundos de celebração

        // Calcular estatísticas finais
        int totalKills = match.getClanEntries().stream()
            .mapToInt(ClanEntry::getKills)
            .sum();
        long durationSeconds = (System.currentTimeMillis() - match.getStartTime()) / 1000;

        // Notificar via Discord
        plugin.getDiscordIntegration().sendMatchWon(winner.getClanTag(), totalKills, durationSeconds);

        // Salvar match no banco
        databaseManager.saveMatch(match, winner.getClanId());

        // Registrar vitórias via LeagueAPI (softdepend)
        registerLeagueAPI(match);

        // Remover scoreboard e tablist
        clearIntegrations(match);
    }

    /**
     * Registra vitórias via LeagueAPI
     * Grug Brain: Reflection para softdepend
     */
    private void registerLeagueAPI(GladiadorMatch match) {
        try {
            Class<?> leagueApiClass = Class.forName("com.primeleague.league.LeagueAPI");
            java.lang.reflect.Method isEnabledMethod = leagueApiClass.getMethod("isEnabled");
            Boolean isEnabled = (Boolean) isEnabledMethod.invoke(null);

            if (isEnabled != null && isEnabled) {
                List<ClanEntry> rankedClans = rankingManager.getRankedClans(match);
                java.lang.reflect.Method recordMethod = leagueApiClass.getMethod("recordGladiadorWin",
                    Integer.class, UUID.class, Integer.class, Integer.class, Integer.class);

                for (int i = 0; i < rankedClans.size(); i++) {
                    ClanEntry clan = rankedClans.get(i);
                    recordMethod.invoke(null, clan.getClanId(), match.getMatchId(), i + 1,
                        clan.getKills(), clan.getDeaths());
                }
            }
        } catch (Exception e) {
            // LeagueAPI não disponível - ignorar silenciosamente
        }
    }

    /**
     * Limpa integrações (scoreboard, tablist)
     * Grug Brain: Limpeza direta
     */
    private void clearIntegrations(GladiadorMatch match) {
        if (plugin.getScoreboardIntegration().isEnabled()) {
            plugin.getScoreboardIntegration().stopUpdateTask();
            for (UUID uuid : match.getAlivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    plugin.getScoreboardIntegration().clearScoreboard(p);
                }
            }
        }
    }
}

