package com.delay;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plugin principal - Replica PvP da versão 1.5.2 para Paper 1.8.8
 * Grug Brain: Config inline, sem ConfigManager separado desnecessário
 */
public class DelayPlugin extends JavaPlugin {
    
    private CombatListener combatListener;
    private Map<UUID, PlayerData> playerDataMap;
    
    @Override
    public void onEnable() {
        // Salvar config padrão se não existir
        saveDefaultConfig();
        
        // Inicializar estruturas de dados
        playerDataMap = new HashMap<>();
        
        // Registrar listener de combate (toda lógica consolidada aqui)
        combatListener = new CombatListener(this);
        getServer().getPluginManager().registerEvents(combatListener, this);
        
        // Registrar comando
        if (getCommand("delay") != null) {
            getCommand("delay").setExecutor(new DelayCommand(this));
        }
        
        // PlayerQuitEvent já limpa dados, não precisa de task periódica
        
        getLogger().info("Delay plugin habilitado - Replicando PvP da versão 1.5.2");
    }
    
    @Override
    public void onDisable() {
        // Limpar dados
        if (playerDataMap != null) {
            playerDataMap.clear();
        }
        
        getLogger().info("Delay plugin desabilitado");
    }
    
    /**
     * Obtém ou cria PlayerData para um jogador
     */
    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, k -> new PlayerData());
    }
    
    /**
     * Remove dados de um jogador
     */
    public void removePlayerData(UUID uuid) {
        playerDataMap.remove(uuid);
    }
    
    /**
     * Obtém o listener de combate (para comandos)
     */
    public CombatListener getCombatListener() {
        return combatListener;
    }
}

