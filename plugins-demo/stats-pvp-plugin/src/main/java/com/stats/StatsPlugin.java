package com.stats;

import com.arena.ArenaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Plugin principal - Stats, scoreboard e chat formatado
 * Grug Brain: Config inline, sem abstrações desnecessárias
 */
public class StatsPlugin extends JavaPlugin {

    private StatsListener statsListener;
    private Map<UUID, PlayerStats> playerStatsMap;
    private ArenaPlugin arenaPlugin;

    @Override
    public void onEnable() {
        // Salvar config padrão se não existir
        saveDefaultConfig();

        // Verificar se ArenaPlugin está habilitado
        arenaPlugin = (ArenaPlugin) getServer().getPluginManager().getPlugin("ArenaPvP");
        if (arenaPlugin == null || !arenaPlugin.isEnabled()) {
            getLogger().warning("ArenaPvP não encontrado ou desabilitado! Stats podem não funcionar corretamente.");
        }

        // Inicializar estruturas de dados (thread-safe para AsyncPlayerChatEvent)
        playerStatsMap = new ConcurrentHashMap<>();

        // Registrar listener
        statsListener = new StatsListener(this);
        getServer().getPluginManager().registerEvents(statsListener, this);

        // Registrar comandos
        if (getCommand("stats") != null) {
            getCommand("stats").setExecutor(new StatsCommand(this));
        }
        if (getCommand("top") != null) {
            getCommand("top").setExecutor(new StatsCommand(this));
        }

        getLogger().info("StatsPvP plugin habilitado - Prime League");
    }

    @Override
    public void onDisable() {
        // Limpar dados
        if (playerStatsMap != null) {
            playerStatsMap.clear();
        }

        getLogger().info("StatsPvP plugin desabilitado");
    }

    /**
     * Obtém ou cria PlayerStats para um jogador
     */
    public PlayerStats getPlayerStats(UUID uuid) {
        return playerStatsMap.computeIfAbsent(uuid, k -> new PlayerStats());
    }

    /**
     * Remove stats de um jogador
     */
    public void removePlayerStats(UUID uuid) {
        playerStatsMap.remove(uuid);
    }

    /**
     * Obtém o listener de stats (para comandos)
     */
    public StatsListener getStatsListener() {
        return statsListener;
    }

    /**
     * Obtém referência ao ArenaPlugin
     */
    public ArenaPlugin getArenaPlugin() {
        return arenaPlugin;
    }

    /**
     * Atualiza scoreboard de todos os players online
     * Grug Brain: Lógica inline, sem abstrações
     */
    public void updateAllScoreboards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateScoreboard(player);
        }
    }

    /**
     * Cria ou atualiza scoreboard de um player
     * Grug Brain: Lógica inline, parse direto do config
     */
    public void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("stats", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Título do scoreboard (config)
        String titulo = getConfig().getString("scoreboard.titulo", "§b§lPRIME LEAGUE");
        objective.setDisplayName(titulo);

        // Obter top players primeiro
        int topMostrar = getConfig().getInt("scoreboard.top-mostrar", 10);
        String ordenarPor = getConfig().getString("scoreboard.ordenar-por", "kdr");
        String corNumero = getConfig().getString("scoreboard.cor-numero", "§b");
        String corNome = getConfig().getString("scoreboard.cor-nome", "§b");
        String corKdr = getConfig().getString("scoreboard.cor-kdr", "§7");

        List<Map.Entry<UUID, PlayerStats>> topPlayers = getTopPlayers(topMostrar, ordenarPor);

        // Site (linha fixa no topo)
        String site = getConfig().getString("scoreboard.site", "§7www.primeleague.com.br");
        objective.getScore(site).setScore(topPlayers.size() + 2);

        // Separador
        String separador = getConfig().getString("scoreboard.separador", "§b§l=== RANKING ===");
        objective.getScore(separador).setScore(topPlayers.size() + 1);

        // Adicionar entradas do ranking (de cima para baixo)
        // Scoreboard precisa de linhas únicas, usar códigos de cor invisíveis
        int score = topPlayers.size();
        for (Map.Entry<UUID, PlayerStats> entry : topPlayers) {
            UUID uuid = entry.getKey();
            PlayerStats stats = entry.getValue();

            Player topPlayer = Bukkit.getPlayer(uuid);
            if (topPlayer == null) {
                continue;
            }

            String playerName = topPlayer.getName();
            double kdr = stats.getKDR();

            // Formatar linha: #1 Nome (KDR)
            // Usar códigos de cor invisíveis para garantir linhas únicas
            String[] colorCodes = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9"};
            String colorCode = score <= 10 ? colorCodes[score - 1] : "";

            String linha = String.format("%s%s#%d %s%s %s(%.2f)",
                colorCode, corNumero, score, corNome, playerName, corKdr, kdr);

            // Scoreboard em 1.8.8 tem limite de 16 chars por linha (sem contar códigos de cor)
            // Códigos de cor não contam no limite, mas melhor truncar se muito longo
            String linhaSemCor = ChatColor.stripColor(linha);
            if (linhaSemCor.length() > 16) {
                // Truncar mantendo códigos de cor no início
                int maxChars = 16 - 3; // espaço para "..."
                linha = linha.substring(0, Math.min(linha.length(), maxChars + (linha.length() - linhaSemCor.length()))) + "...";
            }

            objective.getScore(linha).setScore(score);
            score--;
        }

        // Aplicar scoreboard ao player
        player.setScoreboard(scoreboard);
    }

    /**
     * Obtém top players ordenados por KDR ou kills
     * Grug Brain: Stream simples, sem abstrações
     */
    public List<Map.Entry<UUID, PlayerStats>> getTopPlayers(int limit, String ordenarPor) {
        List<Map.Entry<UUID, PlayerStats>> sorted = new ArrayList<>(playerStatsMap.entrySet());

        if ("kills".equals(ordenarPor)) {
            sorted.sort((a, b) -> Integer.compare(b.getValue().kills, a.getValue().kills));
        } else {
            // Ordenar por KDR (default)
            sorted.sort((a, b) -> Double.compare(b.getValue().getKDR(), a.getValue().getKDR()));
        }

        return sorted.stream().limit(limit).collect(Collectors.toList());
    }
}

