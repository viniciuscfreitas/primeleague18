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

        // Grug Brain: Reutilizar scoreboard existente ou criar novo (1.8.8 API)
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null || scoreboard == Bukkit.getScoreboardManager().getMainScoreboard()) {
            scoreboard = manager.getNewScoreboard();
        } else {
            // Limpar objetivo antigo se existir (evitar memory leak em 1.8.8)
            Objective oldObjective = scoreboard.getObjective("stats");
            if (oldObjective != null) {
                oldObjective.unregister();
            }
        }
        
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

        // Tamanho mínimo do ranking (garantir scoreboard não fique muito pequeno)
        int tamanhoMinimo = getConfig().getInt("scoreboard.tamanho-minimo", 5);
        int rankingSize = Math.max(topPlayers.size(), tamanhoMinimo);

        // Grug Brain: Espaçamento visual - linhas vazias entre seções
        int currentScore = rankingSize + 4; // Começar com espaço extra no topo

        // Site (linha fixa no topo)
        String site = getConfig().getString("scoreboard.site", "§7www.primeleague.com.br");
        objective.getScore(site).setScore(currentScore);
        currentScore--;

        // Linha vazia para espaçamento (usar código de cor invisível para garantir unicidade)
        String[] colorCodes = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"};
        String espacoColorCode = currentScore <= colorCodes.length ? colorCodes[currentScore - 1] : "";
        objective.getScore(espacoColorCode + " ").setScore(currentScore);
        currentScore--;

        // Separador
        String separador = getConfig().getString("scoreboard.separador", "§b§l=== RANKING ===");
        objective.getScore(separador).setScore(currentScore);
        currentScore--;

        // Adicionar entradas do ranking (de cima para baixo)
        // Scoreboard precisa de linhas únicas, usar códigos de cor invisíveis
        // Grug Brain: Score maior = aparece primeiro (topo), número do ranking separado
        // Grug Brain: 1.8.8 tem limite de 28 chars por linha (não 16), então não precisa truncar na maioria dos casos
        int rankingNum = 1; // Número do ranking (1, 2, 3...)
        
        // Códigos de cor invisíveis para linhas únicas (reutilizar array já criado acima)
        
        for (Map.Entry<UUID, PlayerStats> entry : topPlayers) {
            UUID uuid = entry.getKey();
            PlayerStats stats = entry.getValue();

            Player topPlayer = Bukkit.getPlayer(uuid);
            if (topPlayer == null) {
                continue;
            }

            String playerName = topPlayer.getName();
            double kdr = stats.getKDR();

            // Formatar linha: #1 Nome (KDR) com espaçamento visual
            // Grug Brain: 1.8.8 limite é 28 chars, formato atual cabe facilmente
            // Usar códigos de cor invisíveis para garantir linhas únicas
            String colorCode = currentScore <= colorCodes.length ? colorCodes[currentScore - 1] : "";

            // Formato com espaçamento: #1  Nome  (KDR)
            String linha = String.format("%s%s#%d  %s%s  %s(%.2f)",
                colorCode, corNumero, rankingNum, corNome, playerName, corKdr, kdr);

            // Verificar apenas se excede 28 chars (limite real do 1.8.8)
            // Grug Brain: Truncar apenas se realmente necessário (nomes muito longos)
            String linhaSemCor = ChatColor.stripColor(linha);
            if (linhaSemCor != null && linhaSemCor.length() > 28) {
                // Truncar nome se necessário (mantendo formato)
                int maxNomeLength = 28 - String.format("#%d  ", rankingNum).length() - String.format("  (%.2f)", kdr).length();
                if (maxNomeLength > 0 && playerName.length() > maxNomeLength) {
                    String nomeTruncado = playerName.substring(0, maxNomeLength - 2) + "..";
                    linha = String.format("%s%s#%d  %s%s  %s(%.2f)",
                        colorCode, corNumero, rankingNum, corNome, nomeTruncado, corKdr, kdr);
                }
            }

            objective.getScore(linha).setScore(currentScore);
            currentScore--;
            rankingNum++;
        }

        // Preencher linhas vazias se ranking está vazio ou muito pequeno
        // Grug Brain: Linhas vazias com códigos de cor únicos para manter tamanho mínimo
        String mensagemVazio = getConfig().getString("scoreboard.mensagem-vazio", "§7Nenhum player ainda");
        while (currentScore > 0) {
            // Usar código de cor invisível único para cada linha vazia
            String colorCode = currentScore <= colorCodes.length ? colorCodes[currentScore - 1] : "";
            String linhaVazia = colorCode + mensagemVazio;
            
            // Verificar apenas se excede 28 chars (limite real do 1.8.8)
            String linhaVaziaSemCor = ChatColor.stripColor(linhaVazia);
            if (linhaVaziaSemCor != null && linhaVaziaSemCor.length() > 28) {
                int maxChars = 28 - 2; // espaço para ".."
                linhaVazia = linhaVazia.substring(0, Math.min(linhaVazia.length(), maxChars + (linhaVazia.length() - linhaVaziaSemCor.length()))) + "..";
            }
            
            objective.getScore(linhaVazia).setScore(currentScore);
            currentScore--;
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

