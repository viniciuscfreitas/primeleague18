package com.primeleague.gladiador.managers;

/**
 * Formatador de valores para mensagens
 * Grug Brain: Utilitário simples, sem dependências
 */
public class MessageFormatter {

    /**
     * Formata valor de coins (ex: 2500000 -> "2,5M")
     * Grug Brain: Lógica direta, sem overengineering
     */
    public static String formatCoins(double coins) {
        if (coins >= 1000000) {
            double millions = coins / 1000000.0;
            if (millions == (int) millions) {
                return (int) millions + "M";
            }
            return String.format("%.1fM", millions).replace(".", ",");
        } else if (coins >= 1000) {
            double thousands = coins / 1000.0;
            if (thousands == (int) thousands) {
                return (int) thousands + "k";
            }
            return String.format("%.1fk", thousands).replace(".", ",");
        }
        return String.format("%.0f", coins);
    }

    /**
     * Formata valor de dano (ex: 86000 -> "86k")
     * Grug Brain: Lógica direta, sem overengineering
     */
    public static String formatDamage(double damage) {
        if (damage >= 1000) {
            double thousands = damage / 1000.0;
            if (thousands == (int) thousands) {
                return (int) thousands + "k";
            }
            return String.format("%.0fk", thousands);
        }
        return String.format("%.0f", damage);
    }
}


