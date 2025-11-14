package com.primeleague.clans.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Evento disparado quando um membro de um clan recebe uma punição
 * Grug Brain: Evento customizado simples, HandlerList estático obrigatório para Bukkit 1.8.8
 */
public class ClanPunishmentEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final int clanId;
    private final UUID playerUuid;
    private final String alertType;
    private final String message;
    private final String punishmentId;

    public ClanPunishmentEvent(int clanId, UUID playerUuid, String alertType, String message, String punishmentId) {
        this.clanId = clanId;
        this.playerUuid = playerUuid;
        this.alertType = alertType;
        this.message = message;
        this.punishmentId = punishmentId;
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

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getAlertType() {
        return alertType;
    }

    public String getMessage() {
        return message;
    }

    public String getPunishmentId() {
        return punishmentId;
    }
}

