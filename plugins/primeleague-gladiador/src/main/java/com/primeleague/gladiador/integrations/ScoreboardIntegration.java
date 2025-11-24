package com.primeleague.gladiador.integrations;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Integração com AnimatedScoreboard para scoreboards contextuais
 * Grug Brain: Usa reflexão para acessar API sem dependência Maven
 */
public class ScoreboardIntegration {

    private final GladiadorPlugin plugin;
    private Object animatedScoreboardPlugin;
    private Method setScoreboardMethod;
    private boolean enabled;
    private BukkitTask updateTask;

    public ScoreboardIntegration(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
        initialize();
    }

    /**
     * Inicializa integração com AnimatedScoreboard
     * Grug Brain: Usa reflexão para acessar API sem dependência Maven
     * AnimatedScoreboard usa método setPlayerScoreboard(Player, String)
     */
    private void initialize() {
        Plugin asPlugin = Bukkit.getPluginManager().getPlugin("AnimatedScoreboard");
        if (asPlugin == null || !asPlugin.isEnabled()) {
            plugin.getLogger().info("AnimatedScoreboard não encontrado - scoreboards contextuais desabilitados");
            return;
        }

        try {
            // AnimatedScoreboard API: getInstance() -> setPlayerScoreboard(Player, String)
            Class<?> apiClass = Class.forName("com.animatedscoreboard.api.AnimatedScoreboardAPI");
            Method getInstanceMethod = apiClass.getMethod("getInstance");
            Object apiInstance = getInstanceMethod.invoke(null);
            
            if (apiInstance != null) {
                // Método: setPlayerScoreboard(Player, String)
                setScoreboardMethod = apiInstance.getClass().getMethod("setPlayerScoreboard", Player.class, String.class);
                animatedScoreboardPlugin = apiInstance;
                this.enabled = true;
                plugin.getLogger().info("Integração com AnimatedScoreboard habilitada (via API)");
                return;
            }
        } catch (Exception e) {
            // API não encontrada, tentar métodos alternativos
        }

        // Fallback: tentar métodos alternativos
        try {
            animatedScoreboardPlugin = asPlugin;
            Class<?> clazz = asPlugin.getClass();

            // Método 1: setPlayerScoreboard(Player, String) direto
            try {
                setScoreboardMethod = clazz.getMethod("setPlayerScoreboard", Player.class, String.class);
                this.enabled = true;
                plugin.getLogger().info("Integração com AnimatedScoreboard habilitada (método direto)");
                return;
            } catch (NoSuchMethodException e1) {
                // Método 2: setScoreboard(Player, String)
                try {
                    setScoreboardMethod = clazz.getMethod("setScoreboard", Player.class, String.class);
                    this.enabled = true;
                    plugin.getLogger().info("Integração com AnimatedScoreboard habilitada (método alternativo)");
                    return;
                } catch (NoSuchMethodException e2) {
                    plugin.getLogger().warning("Não foi possível encontrar método setPlayerScoreboard no AnimatedScoreboard. " +
                        "Scoreboards contextuais podem não funcionar. Verifique a versão do AnimatedScoreboard.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao inicializar AnimatedScoreboard: " + e.getMessage());
        }
    }

    /**
     * Verifica se integração está habilitada
     */
    public boolean isEnabled() {
        return enabled && animatedScoreboardPlugin != null && setScoreboardMethod != null;
    }

    /**
     * Define scoreboard para um player
     */
    public void setScoreboard(Player player, String scoreboardName) {
        if (!isEnabled() || player == null || !player.isOnline()) {
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

    /**
     * Inicia task periódica para atualizar scoreboard durante match
     * Grug Brain: Task simples, atualiza a cada 1 segundo
     */
    public void startUpdateTask(GladiadorMatch match) {
        if (!isEnabled() || match == null) {
            return;
        }

        // Cancelar task anterior se existir
        stopUpdateTask();

        updateTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (match == null || plugin.getMatchManager().getCurrentMatch() != match) {
                    this.cancel();
                    return;
                }

                // Atualizar scoreboard para todos os players no match
                for (UUID uuid : match.getAlivePlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        setScoreboard(p, "gladiador-match");
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // A cada 1 segundo
    }

    /**
     * Para task de atualização
     * Grug Brain: Paper 1.8.8 não tem isCancelled(), apenas cancelar
     */
    public void stopUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }
}

