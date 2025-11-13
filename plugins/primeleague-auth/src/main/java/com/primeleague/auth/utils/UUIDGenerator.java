package com.primeleague.auth.utils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Gerador de UUID determinístico compatível com Paper 1.8.8
 * Grug Brain: Sempre usa lógica do Paper (apenas nome) - IP não afeta UUID
 * Paper 1.8.8 (modo offline) usa: UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes())
 */
public class UUIDGenerator {

    /**
     * Gera UUID determinístico compatível com Paper 1.8.8
     * Grug Brain: Sempre usa apenas nome (como Paper faz) - IP não importa para UUID
     *
     * Paper 1.8.8 (modo offline) sempre usa: "OfflinePlayer:" + name
     * UUID deve ser baseado apenas no nome, não no IP
     * IP é usado apenas para validação (ip_hash), não para UUID
     */
    public static UUID generate(String name, String ip) {
        // Grug Brain: Sempre usar lógica do Paper (apenas nome) - ignorar IP
        // Paper 1.8.8 sempre gera UUID baseado apenas no nome
        // IP não deve afetar UUID (IP é para validação, não para UUID)
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}

