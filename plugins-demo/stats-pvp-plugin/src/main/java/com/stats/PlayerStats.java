package com.stats;

/**
 * Classe simples para armazenar stats do jogador.
 * Grug Brain: Campos públicos, sem getters/setters desnecessários
 */
public class PlayerStats {
    /** Número de kills */
    public int kills = 0;

    /** Número de deaths */
    public int deaths = 0;

    /**
     * Calcula KDR (Kill/Death Ratio)
     * @return KDR (se deaths == 0, retorna kills)
     */
    public double getKDR() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }
}

