package com.primeleague.pvp152;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin principal - Replica PvP da versão 1.5.2 para Paper 1.8.8
 * Grug Brain: Apenas mecânicas de PvP, sem lógica de negócio (stats são responsabilidade de outros plugins)
 */
public class PvP152Plugin extends JavaPlugin {
    
    private CombatListener combatListener;
    private Map<UUID, PlayerData> playerDataMap;
    
    @Override
    public void onEnable() {
        // Salvar config padrão se não existir
        saveDefaultConfig();
        
        // Inicializar estruturas de dados (thread-safe)
        playerDataMap = new ConcurrentHashMap<>();
        
        // Registrar listener de combate (toda lógica consolidada aqui)
        combatListener = new CombatListener(this);
        getServer().getPluginManager().registerEvents(combatListener, this);
        
        // Registrar comando
        if (getCommand("delay") != null) {
            getCommand("delay").setExecutor(new PvP152Command(this));
        }
        
        // PlayerQuitEvent já limpa dados, não precisa de task periódica
        
        getLogger().info("PrimeleaguePvP152 habilitado - Replicando PvP da versão 1.5.2");
    }
    
    @Override
    public void onDisable() {
        // Limpar dados
        if (playerDataMap != null) {
            playerDataMap.clear();
        }
        
        getLogger().info("PrimeleaguePvP152 desabilitado");
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







