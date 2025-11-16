package com.primeleague.x1.utils;

/**
 * Utilitários gerais do X1
 * Grug Brain: Funções simples, reutilizáveis, sem dependências
 */
public class X1Utils {

    /**
     * Valida nome (kit/arena) usando regex
     * Grug Brain: Validação centralizada, reutilizável
     */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        // Permitir apenas letras, números e underscore, 1-50 caracteres
        return name.matches("^[a-zA-Z0-9_]{1,50}$");
    }

    /**
     * Normaliza nome para lowercase (usado como chave)
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        return name.toLowerCase();
    }
}

