package com.primeleague.x1.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Validador para matches "anywhere"
 * Grug Brain: Validações simples e diretas, fail fast
 */
public class AnywhereMatchValidator {

    /**
     * Valida se dois players podem fazer x1 anywhere
     * @return null se válido, mensagem de erro se inválido
     */
    public static String validate(Player player1, Player player2, double maxDistance) {
        if (player1 == null || player2 == null) {
            return "§cUm dos jogadores não está online";
        }

        Location loc1 = player1.getLocation();
        Location loc2 = player2.getLocation();

        // 1. Mesmo mundo
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            return "§cVocês estão em mundos diferentes! Use uma arena.";
        }

        // 2. Distância máxima
        double distance = loc1.distance(loc2);
        if (distance > maxDistance) {
            int blocks = (int) distance;
            int maxBlocks = (int) maxDistance;
            return "§cMuito distante! (" + blocks + " blocos, máximo: " + maxBlocks + ")";
        }

        // 3. Localização segura (não em void)
        if (loc1.getY() < 0 || loc2.getY() < 0) {
            return "§cUm dos jogadores está no void!";
        }

        // 4. Verificar localização perigosa (lava, void próximo)
        String unsafe1 = checkUnsafeLocation(player1, loc1);
        if (unsafe1 != null) {
            return unsafe1;
        }
        
        String unsafe2 = checkUnsafeLocation(player2, loc2);
        if (unsafe2 != null) {
            return unsafe2;
        }

        // Válido
        return null;
    }

    /**
     * Verifica se localização é perigosa
     * Grug Brain: Apenas perigos reais (void, lava), não estruturas normais
     */
    private static String checkUnsafeLocation(Player player, Location loc) {
        // Verificar se está dentro de lava (perigo real)
        Block blockAt = loc.getBlock();
        if (blockAt.getType() == Material.LAVA || blockAt.getType() == Material.STATIONARY_LAVA) {
            return "§c" + player.getName() + " está na lava!";
        }

        // Grug Brain: Não verificar "sem chão" - players podem estar em pontes, escadas, etc
        // Apenas void real (Y < 0) já é verificado acima

        return null;
    }

    /**
     * Verifica distância entre dois players
     */
    public static double getDistance(Player player1, Player player2) {
        if (player1 == null || player2 == null) return Double.MAX_VALUE;
        if (!player1.getWorld().equals(player2.getWorld())) return Double.MAX_VALUE;
        return player1.getLocation().distance(player2.getLocation());
    }

    /**
     * Valida estado do player para match (game mode, flying, etc)
     * Grug Brain: Validações simples, fail fast
     * @return null se válido, mensagem de erro se inválido
     */
    public static String validatePlayerState(Player player) {
        if (player == null || !player.isOnline()) {
            return "§cJogador não está online";
        }

        // GameMode deve ser SURVIVAL ou ADVENTURE (não CREATIVE ou SPECTATOR)
        org.bukkit.GameMode gm = player.getGameMode();
        if (gm == org.bukkit.GameMode.CREATIVE || gm == org.bukkit.GameMode.SPECTATOR) {
            return "§c" + player.getName() + " está em modo " + gm.name() + "!";
        }

        // Não pode estar voando (unfair advantage)
        if (player.isFlying()) {
            return "§c" + player.getName() + " está voando!";
        }

        return null;
    }

    /**
     * Valida se players ainda estão em condições válidas para anywhere match
     * Usado durante countdown e durante o match
     */
    public static String validateDuringMatch(Player player1, Player player2, double maxDistance) {
        // Validar estados dos players
        String state1 = validatePlayerState(player1);
        if (state1 != null) return state1;
        
        String state2 = validatePlayerState(player2);
        if (state2 != null) return state2;

        // Validar distância e localização
        return validate(player1, player2, maxDistance);
    }
}

