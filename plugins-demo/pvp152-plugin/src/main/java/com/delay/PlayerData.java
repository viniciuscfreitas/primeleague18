package com.delay;

/**
 * Classe simples para armazenar dados do jogador relacionados ao PvP.
 * Grug Brain: Um Map com classe wrapper é melhor que múltiplos Maps separados.
 */
public class PlayerData {
    /** Tick do último dano recebido (para I-frames) - Grug Brain: usar ticks ao invés de ms */
    public long lastDamageTick = 0;
    
    /** Se o jogador está bloqueando com espada */
    public boolean isBlocking = false;
    
    /** Se o jogador estava em sprint no último movimento (para detectar reset) */
    public boolean wasSprinting = false;
    
    /** Tick do último reset de sprint (para cooldown) - Grug Brain: usar ticks ao invés de ms */
    public long lastSprintReset = 0;
    
    /** Tick de quando parou de correr (para detectar W-tapping) - Grug Brain: usar ticks ao invés de ms */
    public long sprintStopTime = 0;
    
    /** Tick do último hit dado enquanto em sprint (para sprint reset forçado) */
    public long lastSprintHitTick = 0;
}

