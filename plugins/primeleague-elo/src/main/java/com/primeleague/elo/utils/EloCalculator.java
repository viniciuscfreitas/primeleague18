package com.primeleague.elo.utils;

/**
 * Calculadora de ELO
 * Grug Brain: Fórmula ELO padrão, inline, sem abstrações
 */
public class EloCalculator {

    /**
     * Calcula novo ELO após combate
     * @param playerElo ELO atual do player
     * @param opponentElo ELO atual do oponente
     * @param won true se player ganhou, false se perdeu
     * @param kFactor Fator K (padrão 32)
     * @return Novo ELO do player
     */
    public static int calculateElo(int playerElo, int opponentElo, boolean won, int kFactor) {
        // Expected score (probabilidade de vitória)
        double expected = 1.0 / (1.0 + Math.pow(10.0, (opponentElo - playerElo) / 400.0));

        // Result (1 = vitória, 0 = derrota)
        double result = won ? 1.0 : 0.0;

        // Novo ELO
        int newElo = (int) Math.round(playerElo + kFactor * (result - expected));

        // Retornar novo ELO (minElo será garantido no EloAPI)
        return newElo;
    }

    /**
     * Calcula novo ELO usando K Factor padrão (32)
     */
    public static int calculateElo(int playerElo, int opponentElo, boolean won) {
        return calculateElo(playerElo, opponentElo, won, 32);
    }
}

