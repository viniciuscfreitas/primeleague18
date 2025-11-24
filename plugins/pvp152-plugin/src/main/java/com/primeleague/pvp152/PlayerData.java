package com.primeleague.pvp152;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Classe simples para armazenar dados do jogador relacionados ao PvP.
 * Grug Brain: Thread-safe com AtomicLong/AtomicBoolean para evitar race conditions
 */
public class PlayerData {
    /** Tick do último dano recebido (para I-frames) - Thread-safe */
    private final AtomicLong lastDamageTick = new AtomicLong(0);
    
    /** Se o jogador está bloqueando com espada - Thread-safe */
    private final AtomicBoolean isBlocking = new AtomicBoolean(false);
    
    /** Se o jogador estava em sprint no último movimento (para detectar reset) */
    public boolean wasSprinting = false; // Apenas lido na thread principal (onPlayerMove)
    
    /** Tick do último reset de sprint (para cooldown) - Thread-safe */
    private final AtomicLong lastSprintReset = new AtomicLong(0);
    
    /** Tick de quando parou de correr (para detectar W-tapping) - Thread-safe */
    private final AtomicLong sprintStopTime = new AtomicLong(0);
    
    /** Tick do último hit dado enquanto em sprint (para sprint reset forçado) - Thread-safe */
    private final AtomicLong lastSprintHitTick = new AtomicLong(0);
    
    /** Tick do último hit dado pelo atacante (para limitar CPS) - Thread-safe */
    private final AtomicLong lastAttackTick = new AtomicLong(0);
    
    // Getters e Setters thread-safe
    public long getLastDamageTick() { return lastDamageTick.get(); }
    public void setLastDamageTick(long value) { lastDamageTick.set(value); }
    
    public boolean getIsBlocking() { return isBlocking.get(); }
    public void setIsBlocking(boolean value) { isBlocking.set(value); }
    
    public long getLastSprintReset() { return lastSprintReset.get(); }
    public void setLastSprintReset(long value) { lastSprintReset.set(value); }
    
    public long getSprintStopTime() { return sprintStopTime.get(); }
    public void setSprintStopTime(long value) { sprintStopTime.set(value); }
    
    public long getLastSprintHitTick() { return lastSprintHitTick.get(); }
    public void setLastSprintHitTick(long value) { lastSprintHitTick.set(value); }
    
    public long getLastAttackTick() { return lastAttackTick.get(); }
    public void setLastAttackTick(long value) { lastAttackTick.set(value); }
}

