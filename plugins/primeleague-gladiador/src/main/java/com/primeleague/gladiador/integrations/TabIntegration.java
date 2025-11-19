package com.primeleague.gladiador.integrations;

import com.primeleague.gladiador.GladiadorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Integração opcional com TAB para tablist customizada
 * Grug Brain: Verificação simples, graceful degradation se não disponível
 * Mantém cores originais das tags dos clans (não altera identidade visual)
 */
public class TabIntegration {

    private final GladiadorPlugin plugin;
    private boolean enabled = false;
    private Object tabApi = null;
    private java.lang.reflect.Method getPlayerMethod = null;
    private java.lang.reflect.Method setTabListNameMethod = null;

    public TabIntegration(GladiadorPlugin plugin) {
        this.plugin = plugin;
        checkTabAvailability();
    }

    /**
     * Verifica se TAB está disponível e valida métodos
     */
    private void checkTabAvailability() {
        Plugin tabPlugin = Bukkit.getPluginManager().getPlugin("TAB");
        if (tabPlugin == null || !tabPlugin.isEnabled()) {
            plugin.getLogger().info("TAB não encontrado - integração tablist desabilitada");
            return;
        }

        // Tentar obter API do TAB via reflexão (compatibilidade 1.8.8)
        try {
            Class<?> tabClass = Class.forName("me.neznamy.tab.api.TabAPI");
            tabApi = tabClass.getMethod("getInstance").invoke(null);

            if (tabApi == null) {
                plugin.getLogger().info("TAB API retornou null - integração tablist desabilitada");
                return;
            }

            // Validar métodos antes de habilitar
            Class<?> tabApiClass = tabApi.getClass();
            getPlayerMethod = tabApiClass.getMethod("getPlayer", UUID.class);

            // Tentar obter classe do TabPlayer
            Class<?> tabPlayerClass = null;
            try {
                tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
            } catch (ClassNotFoundException e) {
                // Tentar obter do método getPlayer
                Object testPlayer = getPlayerMethod.invoke(tabApi, UUID.randomUUID());
                if (testPlayer != null) {
                    tabPlayerClass = testPlayer.getClass();
                }
            }

            if (tabPlayerClass == null) {
                plugin.getLogger().info("TAB: Não foi possível determinar classe TabPlayer - integração tablist desabilitada");
                return;
            }

            // Validar método setTabListName (para atualizar nome na tablist)
            try {
                setTabListNameMethod = tabPlayerClass.getMethod("setTabListName", String.class);
            } catch (NoSuchMethodException e) {
                // Tentar método alternativo
                try {
                    setTabListNameMethod = tabPlayerClass.getMethod("setCustomTabName", String.class);
                } catch (NoSuchMethodException e2) {
                    plugin.getLogger().info("TAB: Método setTabListName não encontrado - integração tablist desabilitada");
                    return;
                }
            }

            enabled = true;
            plugin.getLogger().info("TAB encontrado - integração tablist habilitada");
        } catch (Exception e) {
            // TAB pode não ter API pública ou versão diferente
            plugin.getLogger().info("TAB encontrado mas API não disponível: " + e.getMessage());
            enabled = false;
        }
    }

    /**
     * Atualiza tablist do player durante match
     * Grug Brain: Mantém cores originais das tags (não altera identidade visual)
     * Nota: TAB gerencia tablist automaticamente, apenas verificamos disponibilidade
     */
    public void updateTablist(Player player) {
        // TAB gerencia tablist automaticamente com cores originais das tags
        // Não precisamos fazer nada, apenas verificar se está habilitado
        // As cores das tags já vêm do ClansPlugin e são mantidas pelo TAB
        if (!enabled) {
            return;
        }
        // TAB já exibe as tags com cores originais automaticamente
    }

    /**
     * Verifica se integração está habilitada
     */
    public boolean isEnabled() {
        return enabled;
    }
}

