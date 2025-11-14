package com.primeleague.clans.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Evento disparado quando um clan ganha um evento
 * Grug Brain: Evento customizado simples, HandlerList estático obrigatório para Bukkit 1.8.8
 */
public class ClanWinEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final int clanId;
    private final String eventName;
    private final int points;
    private final UUID awardedBy;

    public ClanWinEvent(int clanId, String eventName, int points, UUID awardedBy) {
        this.clanId = clanId;
        this.eventName = eventName;
        this.points = points;
        this.awardedBy = awardedBy;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public int getClanId() {
        return clanId;
    }

    public String getEventName() {
        return eventName;
    }

    public int getPoints() {
        return points;
    }

    public UUID getAwardedBy() {
        return awardedBy;
    }
}

