package com.primeleague.x1.integrations;

import com.primeleague.x1.X1Plugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Integração opcional com TAB para prefix/suffix
 * Grug Brain: Verificação simples, graceful degradation se não disponível
 */
public class TabIntegration {

    private final X1Plugin plugin;
    private boolean enabled = false;
    private Object tabApi = null;
    private java.lang.reflect.Method getPlayerMethod = null;
    private java.lang.reflect.Method setNameTagPrefixMethod = null;

    public TabIntegration(X1Plugin plugin) {
        this.plugin = plugin;
        checkTabAvailability();
    }

    /**
     * Verifica se TAB está disponível e valida métodos
     */
    private void checkTabAvailability() {
        Plugin tabPlugin = Bukkit.getPluginManager().getPlugin("TAB");
        if (tabPlugin == null || !tabPlugin.isEnabled()) {
            plugin.getLogger().info("TAB não encontrado - integração desabilitada");
            return;
        }

        // Tentar obter API do TAB via reflexão (compatibilidade 1.8.8)
        try {
            Class<?> tabClass = Class.forName("me.neznamy.tab.api.TabAPI");
            tabApi = tabClass.getMethod("getInstance").invoke(null);
            
            if (tabApi == null) {
                plugin.getLogger().info("TAB API retornou null - integração desabilitada");
                return;
            }
            
            // Validar métodos antes de habilitar
            Class<?> tabApiClass = tabApi.getClass();
            getPlayerMethod = tabApiClass.getMethod("getPlayer", UUID.class);
            
            // Tentar obter classe do TabPlayer (pode retornar null se player não existe)
            // Usar um UUID aleatório para testar, mas não confiar no resultado
            Class<?> tabPlayerClass = null;
            try {
                // Tentar obter classe diretamente
                tabPlayerClass = Class.forName("me.neznamy.tab.api.tablist.TabList");
            } catch (ClassNotFoundException e1) {
                try {
                    // Tentar alternativa
                    tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
                } catch (ClassNotFoundException e2) {
                    // Tentar obter do método getPlayer
                    Object testPlayer = getPlayerMethod.invoke(tabApi, java.util.UUID.randomUUID());
                    if (testPlayer != null) {
                        tabPlayerClass = testPlayer.getClass();
                    }
                }
            }
            
            if (tabPlayerClass == null) {
                plugin.getLogger().info("TAB: Não foi possível determinar classe TabPlayer - integração desabilitada");
                return;
            }
            
            // Validar método setNameTagPrefix
            try {
                setNameTagPrefixMethod = tabPlayerClass.getMethod("setNameTagPrefix", String.class);
            } catch (NoSuchMethodException e) {
                // Tentar método alternativo
                try {
                    setNameTagPrefixMethod = tabPlayerClass.getMethod("setPrefix", String.class);
                } catch (NoSuchMethodException e2) {
                    plugin.getLogger().info("TAB: Método setNameTagPrefix/setPrefix não encontrado - integração desabilitada");
                    return;
                }
            }
            
            enabled = true;
            plugin.getLogger().info("TAB encontrado - integração habilitada (API validada)");
        } catch (Exception e) {
            // TAB pode não ter API pública ou versão diferente
            plugin.getLogger().info("TAB encontrado mas API não disponível: " + e.getMessage());
            enabled = false;
        }
    }

    /**
     * Atualiza prefix/suffix do player durante match
     */
    public void updateMatchPrefix(Player player) {
        if (!enabled || tabApi == null || getPlayerMethod == null || setNameTagPrefixMethod == null) {
            return;
        }

        try {
            Object tabPlayer = getPlayerMethod.invoke(tabApi, player.getUniqueId());
            if (tabPlayer != null) {
                setNameTagPrefixMethod.invoke(tabPlayer, "§c[IN MATCH] ");
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Erro ao atualizar prefix TAB (match): " + e.getMessage());
        }
    }

    /**
     * Atualiza prefix/suffix do player na queue
     */
    public void updateQueuePrefix(Player player) {
        if (!enabled || tabApi == null || getPlayerMethod == null || setNameTagPrefixMethod == null) {
            return;
        }

        try {
            Object tabPlayer = getPlayerMethod.invoke(tabApi, player.getUniqueId());
            if (tabPlayer != null) {
                setNameTagPrefixMethod.invoke(tabPlayer, "§e[QUEUE] ");
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Erro ao atualizar prefix TAB (queue): " + e.getMessage());
        }
    }

    /**
     * Remove prefix/suffix customizado (restaurar padrão)
     */
    public void clearPrefix(Player player) {
        if (!enabled || tabApi == null || getPlayerMethod == null || setNameTagPrefixMethod == null) {
            return;
        }

        try {
            Object tabPlayer = getPlayerMethod.invoke(tabApi, player.getUniqueId());
            if (tabPlayer != null) {
                setNameTagPrefixMethod.invoke(tabPlayer, "");
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Erro ao limpar prefix TAB: " + e.getMessage());
        }
    }

    /**
     * Verifica se integração está habilitada
     */
    public boolean isEnabled() {
        return enabled;
    }
}

