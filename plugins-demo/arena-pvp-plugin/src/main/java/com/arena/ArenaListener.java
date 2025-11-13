package com.arena;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listener de eventos - Auto-teleport e kit
 * Grug Brain: Tudo inline, sem abstrações
 */
public class ArenaListener implements Listener {

    private final ArenaPlugin plugin;

    public ArenaListener(ArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Set estado SPAWNED ANTES de qualquer coisa (proteção imediata)
        plugin.setPlayerState(player, ArenaPlugin.ArenaState.SPAWNED);

        // Delay de 1 tick para evitar conflitos
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.teleportToArena(player);
                plugin.giveKit(player);

                // Feedback inline: títulos e som (UX padronizado)
                String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[ARENA]");
                String brandingNome = plugin.getConfig().getString("branding.nome", "PRIME LEAGUE");
                String brandingCor = plugin.getConfig().getString("branding.cor", "§b");
                boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-titulos", true);

                player.sendMessage(prefixo + " §aBem-vindo à arena! Você está protegido.");
                player.sendMessage(prefixo + " §7Movimente-se para iniciar o PvP em 5 segundos.");

                if (plugin.getConfig().getBoolean("ux.titulos", true)) {
                    try {
                        if (mostrarBranding) {
                            player.sendTitle(brandingCor + "§l" + brandingNome, "§eMovimente-se para começar");
                        } else {
                            player.sendTitle("§6§lARENA PVP", "§eMovimente-se para começar");
                        }
                    } catch (Exception e) {
                        // Fallback para versões sem sendTitle
                    }
                }

                if (plugin.getConfig().getBoolean("ux.sons", true)) {
                    player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0f, 1.0f);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Reset estado para SPAWNED ANTES de respawnar (proteção imediata)
        plugin.setPlayerState(player, ArenaPlugin.ArenaState.SPAWNED);

        // Set respawn location para arena (usa helper do plugin)
        event.setRespawnLocation(plugin.getArenaLocation());

        // Dar kit após respawn (delay de 1 tick)
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.giveKit(player);

                // Feedback de respawn (UX padronizado)
                String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[ARENA]");
                player.sendMessage(prefixo + " §aVocê respawnou na arena. Você está protegido.");
                player.sendMessage(prefixo + " §7Movimente-se para iniciar o PvP em 5 segundos.");
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * Detecta movimento do player e inicia countdown se estiver SPAWNED
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ArenaPlugin.ArenaState state = plugin.getPlayerState(player);

        // Só processar se estiver SPAWNED (ignorar se null ou outros estados)
        if (state != ArenaPlugin.ArenaState.SPAWNED) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        // Ignorar se não mudou posição (só olhou ao redor)
        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        // Calcular distância
        double distance = from.distance(to);

        // Ignorar micro-movimentos (threshold 0.1 blocos)
        if (distance < 0.1) {
            return;
        }

        // Ignorar teleportes (distância > 2 blocos)
        if (distance > 2.0) {
            return;
        }

        // Movimento real detectado - iniciar countdown
        plugin.startCountdown(player);
    }

    /**
     * Cancela dano se player não está READY
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Cancelar dano se está invulnerável (null = não inicializado = invulnerável)
        ArenaPlugin.ArenaState state = plugin.getPlayerState(player);
        if (state == null || state != ArenaPlugin.ArenaState.READY) {
            event.setCancelled(true);
        }
    }

    /**
     * Cancela PvP se player não está READY
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        // Cancelar PvP se qualquer um está invulnerável (null = não inicializado = invulnerável)
        ArenaPlugin.ArenaState victimState = plugin.getPlayerState(victim);
        ArenaPlugin.ArenaState attackerState = plugin.getPlayerState(attacker);
        if ((victimState == null || victimState != ArenaPlugin.ArenaState.READY) ||
            (attackerState == null || attackerState != ArenaPlugin.ArenaState.READY)) {
            event.setCancelled(true);
        }
    }

    /**
     * Cancela drops na morte se player está na arena
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ArenaPlugin.ArenaState state = plugin.getPlayerState(player);

        // Só cancelar drops se player está na arena (tem estado no map)
        if (state != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /**
     * Reset de kit do matador ao eliminar vítima (com delay seguro)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Verificar se killer existe e está na arena
        if (killer != null) {
            ArenaPlugin.ArenaState killerState = plugin.getPlayerState(killer);
            // Se matador está na arena, resetar kit com delay seguro
            if (killerState != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!killer.isOnline()) {
                            return;
                        }
                        // Verificar se ainda está na arena antes de dar kit
                        ArenaPlugin.ArenaState currentState = plugin.getPlayerState(killer);
                        if (currentState != null) {
                            plugin.giveKit(killer);
                            String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[ARENA]");
                            killer.sendMessage(prefixo + " §aKit resetado após eliminação!");
                        }
                    }
                }.runTaskLater(plugin, 1L);
            }
        }
    }

    /**
     * Previne drop manual de itens (tecla Q) se player está na arena
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ArenaPlugin.ArenaState state = plugin.getPlayerState(player);

        // Cancelar drop se player está na arena (tem estado no map)
        if (state != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Protege arena: cancela quebra de blocos no mundo da arena
     * Grug Brain: Sempre é "world", simplificado
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (loc.getWorld() != null && loc.getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }

    /**
     * Protege arena: cancela colocação de blocos no mundo da arena
     * Grug Brain: Sempre é "world", simplificado
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Location loc = event.getBlock().getLocation();
        if (loc.getWorld() != null && loc.getWorld().getName().equals("world")) {
            event.setCancelled(true);
        }
    }


    /**
     * Limpa estado do player ao sair (evitar memory leak)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.setPlayerState(event.getPlayer(), null);
    }
}

