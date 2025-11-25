package com.primeleague.core.util;

import org.bukkit.ChatColor;

/**
 * Helper para padronizar mensagens em todos os plugins
 * Grug Brain: Métodos estáticos simples, retorna strings formatadas
 *
 * Formato padronizado:
 * - Erro: §c✗ §7mensagem
 * - Sucesso: §a✓ §7mensagem
 * - Info: §eℹ §7mensagem
 * - Aviso: §6⚠ §7mensagem
 */
public class MessageHelper {

    /**
     * Mensagem de erro
     */
    public static String error(String message) {
        return ChatColor.RED + "✗ " + ChatColor.GRAY + message;
    }

    /**
     * Mensagem de sucesso
     */
    public static String success(String message) {
        return ChatColor.GREEN + "✓ " + ChatColor.GRAY + message;
    }

    /**
     * Mensagem informativa
     */
    public static String info(String message) {
        return ChatColor.YELLOW + "ℹ " + ChatColor.GRAY + message;
    }

    /**
     * Mensagem de aviso
     */
    public static String warning(String message) {
        return ChatColor.GOLD + "⚠ " + ChatColor.GRAY + message;
    }

    /**
     * Formata valor destacado (para uso em mensagens)
     * CORREÇÃO: Não adiciona GRAY no final para permitir uso dentro de outras mensagens
     */
    public static String highlight(String value) {
        return ChatColor.YELLOW + value;
    }

    /**
     * Formata valor em negrito (para títulos/valores importantes)
     */
    public static String bold(String value) {
        return ChatColor.BOLD + value;
    }
}
