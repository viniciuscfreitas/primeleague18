package com.primeleague.auth.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Gerador de hash SHA-256 para IP
 * Grug Brain: Função simples, direta
 */
public class IpHashGenerator {

    private static final String SALT = "primeleague-salt-2025";

    public static String generate(String name, String ip) {
        try {
            String input = name + ":" + ip + ":" + SALT;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));

            // Converter para hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar hash de IP", e);
        }
    }
}

