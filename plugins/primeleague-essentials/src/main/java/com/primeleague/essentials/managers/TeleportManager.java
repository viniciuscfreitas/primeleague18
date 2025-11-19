package com.primeleague.essentials.managers;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Teleportes
 * Grug Brain: Mapas em memória para TPA e LastLocation.
 */
public class TeleportManager {

    private static TeleportManager instance;
    
    // Target UUID -> Requester UUID
    private final Map<UUID, UUID> tpaRequests = new ConcurrentHashMap<>();
    // Target UUID -> Timestamp
    private final Map<UUID, Long> tpaRequestTime = new ConcurrentHashMap<>();
    // Player UUID -> Last Location
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    
    private static final long TPA_TIMEOUT_MS = 120 * 1000; // 2 minutos

    private TeleportManager() {}

    public static TeleportManager getInstance() {
        if (instance == null) {
            instance = new TeleportManager();
        }
        return instance;
    }

    public void sendTpaRequest(Player requester, Player target) {
        tpaRequests.put(target.getUniqueId(), requester.getUniqueId());
        tpaRequestTime.put(target.getUniqueId(), System.currentTimeMillis());
    }

    public UUID getTpaRequester(Player target) {
        if (!tpaRequests.containsKey(target.getUniqueId())) {
            return null;
        }
        
        // Verificar timeout
        long timestamp = tpaRequestTime.get(target.getUniqueId());
        if (System.currentTimeMillis() - timestamp > TPA_TIMEOUT_MS) {
            tpaRequests.remove(target.getUniqueId());
            tpaRequestTime.remove(target.getUniqueId());
            return null;
        }
        
        return tpaRequests.get(target.getUniqueId());
    }

    public void removeTpaRequest(Player target) {
        tpaRequests.remove(target.getUniqueId());
        tpaRequestTime.remove(target.getUniqueId());
    }

    public void setLastLocation(Player player, Location location) {
        lastLocations.put(player.getUniqueId(), location);
    }

    public Location getLastLocation(Player player) {
        return lastLocations.get(player.getUniqueId());
    }
}
