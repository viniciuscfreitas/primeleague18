package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.Arena;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

/**
 * Gerenciador de WorldBorder do Gladiador
 * Grug Brain: Border shrink simples, reset automático
 */
public class BorderManager {

    private final GladiadorPlugin plugin;
    private boolean originalPvPState;

    public BorderManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Define border inicial da arena
     * Grug Brain: Border simples, centro na arena
     */
    public void setInitialBorder(Arena arena) {
        World world = Bukkit.getWorld(arena.getWorld());
        if (world == null) return;

        org.bukkit.WorldBorder border = world.getWorldBorder();
        Location center = new Location(world, arena.getCenterX(), arena.getCenterY(), arena.getCenterZ());
        border.setCenter(center);
        border.setSize(arena.getInitialBorderSize());
    }

    /**
     * Reduz border gradualmente
     * Grug Brain: Shrink simples, limpa itens na borda
     */
    public void shrinkBorder(Arena arena, int newSize, long timeSeconds) {
        World world = Bukkit.getWorld(arena.getWorld());
        if (world == null) return;

        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setSize(newSize, timeSeconds);
    }

    /**
     * Processa shrink periódico (chamado pela task)
     * Grug Brain: Reduz 100 blocos, limpa itens, partículas
     */
    public void processShrink(GladiadorMatch match) {
        if (match == null) return;

        // Usar [!] ao invés de ⚠ para compatibilidade 1.8.8
        Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
            "&c[!] &eA borda da arena está diminuindo! Corram para o centro!"));

        Arena arena = match.getArena();
        World world = Bukkit.getWorld(arena.getWorld());
        if (world == null) return;

        org.bukkit.WorldBorder wb = world.getWorldBorder();
        if (wb == null) return;

        double currentSize = wb.getSize();
        double newSize = Math.max(arena.getFinalBorderSize(), currentSize - 100);

        shrinkBorder(arena, (int) newSize, 60);

        // Efeito de partículas na borda (Paper 1.8.8)
        Location center = new Location(world, arena.getCenterX(), arena.getCenterY(), arena.getCenterZ());
        double radius = wb.getSize() / 2;

        // Spawnar partículas em círculo (8 pontos)
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI * i) / 8;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location particleLoc = new Location(world, x, center.getY(), z);
            world.playEffect(particleLoc, org.bukkit.Effect.SMOKE, 0);
        }

        // Limpar itens no chão dentro da arena
        double borderSize = wb.getSize();
        for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
            if (item.getLocation().distance(center) <= borderSize / 2) {
                item.remove();
            }
        }
    }

    /**
     * Inicia task de shrink periódico
     * Grug Brain: Task configurável, retorna para cancelar depois
     */
    public BukkitTask startShrinkTask(GladiadorMatch match) {
        // Grug Brain: Cancelar task anterior se existir (evitar duplicação)
        // Paper 1.8.8 não tem isCancelled(), apenas verificar se não é null
        if (match.getBorderTask() != null) {
            match.getBorderTask().cancel();
        }

        int shrinkInterval = plugin.getConfig().getInt("arena.border-shrink-interval", 60);

        return new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (match == null || match.getState() != GladiadorMatch.MatchState.ACTIVE) {
                    this.cancel();
                    return;
                }
                processShrink(match);
            }
        }.runTaskTimer(plugin, 20L * shrinkInterval, 20L * shrinkInterval);
    }

    /**
     * Reseta border para tamanho padrão
     * Grug Brain: Reset completo quando match termina
     */
    public void resetBorder(Arena arena) {
        if (arena == null) return;

        World world = Bukkit.getWorld(arena.getWorld());
        if (world == null) return;

        org.bukkit.WorldBorder border = world.getWorldBorder();
        // Resetar para tamanho padrão do servidor (29999984 blocos - padrão Minecraft)
        border.setSize(29999984);
        // Resetar centro para spawn do mundo
        border.setCenter(world.getSpawnLocation());
        plugin.getLogger().info("WorldBorder resetado para o mundo " + world.getName());
    }

    /**
     * Ativa PvP no mundo da arena
     * Grug Brain: Salva estado original, ativa PvP
     */
    public void enablePvP(Arena arena) {
        World world = Bukkit.getWorld(arena.getWorld());
        if (world == null) return;

        originalPvPState = world.getPVP();
        world.setPVP(true);
        plugin.getLogger().info("PvP ativado no mundo " + world.getName() + " (estado anterior: " + originalPvPState + ")");
    }

    /**
     * Desativa PvP no mundo da arena (restaura estado original)
     * Grug Brain: Restaura estado salvo
     */
    public void disablePvP(Arena arena) {
        World world = Bukkit.getWorld(arena.getWorld());
        if (world == null) return;

        world.setPVP(originalPvPState);
        plugin.getLogger().info("PvP restaurado para " + originalPvPState + " no mundo " + world.getName());
    }
}





