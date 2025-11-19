package com.primeleague.x1.integrations;

import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Match;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Integração com AnimatedScoreboard para scoreboards contextuais
 * Grug Brain: Usa reflexão para acessar API sem dependência Maven
 */
public class ScoreboardIntegration {

    private final X1Plugin plugin;
    private Object animatedScoreboardPlugin;
    private Method setScoreboardMethod;
    private boolean enabled;

    public ScoreboardIntegration(X1Plugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
        initialize();
    }

    /**
     * Inicializa integração com AnimatedScoreboard
     * Grug Brain: Usa reflexão para acessar API sem dependência Maven
     */
    private void initialize() {
        Plugin asPlugin = Bukkit.getPluginManager().getPlugin("AnimatedScoreboard");
        if (asPlugin == null || !asPlugin.isEnabled()) {
            plugin.getLogger().info("AnimatedScoreboard não encontrado - scoreboards contextuais desabilitados");
            return;
        }

        try {
            animatedScoreboardPlugin = asPlugin;
            Class<?> clazz = asPlugin.getClass();
            
            // Tentar diferentes métodos comuns do AnimatedScoreboard
            // Método 1: setScoreboard(Player, String)
            try {
                setScoreboardMethod = clazz.getMethod("setScoreboard", Player.class, String.class);
            } catch (NoSuchMethodException e1) {
                // Método 2: setPlayerScoreboard(Player, String)
                try {
                    setScoreboardMethod = clazz.getMethod("setPlayerScoreboard", Player.class, String.class);
                } catch (NoSuchMethodException e2) {
                    // Método 3: Via API manager
                    try {
                        Method getApiMethod = clazz.getMethod("getAPI");
                        Object api = getApiMethod.invoke(asPlugin);
                        if (api != null) {
                            try {
                                setScoreboardMethod = api.getClass().getMethod("setScoreboard", Player.class, String.class);
                                animatedScoreboardPlugin = api;
                            } catch (NoSuchMethodException e3) {
                                setScoreboardMethod = api.getClass().getMethod("setPlayerScoreboard", Player.class, String.class);
                                animatedScoreboardPlugin = api;
                            }
                        }
                    } catch (Exception e3) {
                        // Método 4: Tentar via ScoreboardManager interno
                        try {
                            Object manager = clazz.getMethod("getScoreboardManager").invoke(asPlugin);
                            setScoreboardMethod = manager.getClass().getMethod("setScoreboard", Player.class, String.class);
                            animatedScoreboardPlugin = manager;
                        } catch (Exception e4) {
                            plugin.getLogger().warning("Não foi possível encontrar método setScoreboard no AnimatedScoreboard. " +
                                "Scoreboards contextuais podem não funcionar. Verifique a versão do AnimatedScoreboard.");
                            return;
                        }
                    }
                }
            }

            this.enabled = true;
            plugin.getLogger().info("Integração com AnimatedScoreboard habilitada");
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao inicializar AnimatedScoreboard: " + e.getMessage());
            // Não imprimir stack trace completo - apenas logar
            if (plugin.getConfig().getBoolean("integrations.scoreboard.debug", false)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Verifica se integração está habilitada
     */
    public boolean isEnabled() {
        return enabled && animatedScoreboardPlugin != null && setScoreboardMethod != null;
    }

    /**
     * Atualiza scoreboard baseado no estado do player
     * Grug Brain: Lógica simples, detecta estado e aplica scoreboard apropriado
     */
    public void updateScoreboard(Player player) {
        if (!isEnabled() || player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String scoreboardName = null;

        // Verificar se está em match
        if (plugin.getMatchManager().isInMatch(uuid)) {
            Match match = plugin.getMatchManager().getMatch(uuid);
            if (match != null) {
                if (match.getStatus() == Match.MatchStatus.WAITING) {
                    scoreboardName = "x1-match-waiting";
                } else if (match.getStatus() == Match.MatchStatus.FIGHTING) {
                    scoreboardName = "x1-match-fighting";
                }
            }
        }
        // Verificar se está na queue
        else if (plugin.getQueueManager().isInQueue(uuid)) {
            scoreboardName = "x1-queue";
        }
        // Scoreboard padrão (já gerenciado pelo AnimatedScoreboard)
        // Não precisamos definir, o AnimatedScoreboard já gerencia

        if (scoreboardName != null) {
            setScoreboard(player, scoreboardName);
        }
    }

    /**
     * Define scoreboard para um player
     */
    private void setScoreboard(Player player, String scoreboardName) {
        if (!isEnabled()) {
            return;
        }

        try {
            setScoreboardMethod.invoke(animatedScoreboardPlugin, player, scoreboardName);
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao definir scoreboard para " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Remove scoreboard contextual (volta para padrão)
     */
    public void clearScoreboard(Player player) {
        if (!isEnabled() || player == null) {
            return;
        }

        // AnimatedScoreboard volta para scoreboard padrão automaticamente
        // ou podemos tentar definir como null/vazio se a API suportar
        try {
            // Tentar remover scoreboard contextual
            Method clearMethod = animatedScoreboardPlugin.getClass().getMethod("clearScoreboard", Player.class);
            clearMethod.invoke(animatedScoreboardPlugin, player);
        } catch (Exception e) {
            // Método não existe ou erro - ignorar, AnimatedScoreboard gerencia automaticamente
        }
    }
}

