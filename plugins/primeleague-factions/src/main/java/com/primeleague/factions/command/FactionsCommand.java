package com.primeleague.factions.command;

import com.primeleague.clans.models.ClanData;
import com.primeleague.core.util.MessageHelper;
import com.primeleague.factions.PrimeFactions;
import com.primeleague.factions.util.ChunkKey;
import com.primeleague.factions.util.ParticleBorder;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionsCommand implements CommandExecutor {

    private final PrimeFactions plugin;

    /**
     * Confirmações pendentes: UUID do player -> Ação pendente
     * Grug Brain: Sistema simples com timeout de 30 segundos
     */
    private final Map<UUID, PendingConfirmation> pendingConfirmations;
    private static final long CONFIRMATION_TIMEOUT_MS = 30000; // 30 segundos

    public FactionsCommand(PrimeFactions plugin) {
        this.plugin = plugin;
        this.pendingConfirmations = new ConcurrentHashMap<>();

        // CORREÇÃO: Task periódica para limpar confirmações expiradas (a cada 30s)
        // Segue padrão do projeto (PunishManager, QueueManager)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanupExpiredConfirmations();
        }, 600L, 600L); // A cada 30 segundos (600 ticks)
    }

    /**
     * Ação de confirmação pendente
     * CORREÇÃO: Armazenar coordenadas ao invés de Chunk (evita desatualização)
     */
    private static class PendingConfirmation {
        final ActionType type;
        final long timestamp;
        final String worldName;
        final int chunkX;
        final int chunkZ;

        PendingConfirmation(ActionType type, String worldName, int chunkX, int chunkZ) {
            this.type = type;
            this.worldName = worldName;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONFIRMATION_TIMEOUT_MS;
        }
    }

    private enum ActionType {
        UNCLAIM,
        SHIELD_REMOVE
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageHelper.error("Apenas jogadores podem usar este comando."));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "claim":
                handleClaim(player, args);
                break;
            case "unclaim":
                handleUnclaim(player, args);
                break;
            case "map":
                handleMap(player);
                break;
            case "power":
                handlePower(player);
                break;
            case "fly":
                plugin.getFlyManager().toggleFly(player);
                break;
            case "upgrade":
                handleUpgrade(player);
                break;
            case "shield":
                handleShield(player, args);
                break;
            case "confirm":
                handleConfirm(player);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§e§lPrimeFactions §7- Comandos:");
        player.sendMessage("§6/f claim §f- Conquistar o chunk atual.");
        player.sendMessage("§6/f unclaim §f- Abandonar o chunk atual.");
        player.sendMessage("§6/f map §f- Ver mapa de territórios.");
        player.sendMessage("§6/f power §f- Ver seu poder.");
        player.sendMessage("§6/f fly §f- Ativar/Desativar voo em território.");
        player.sendMessage("§6/f upgrade §f- Abrir menu de upgrades.");
        player.sendMessage("§6/f shield [horas] §f- Ver/Ativar shield do clã.");
        player.sendMessage("§6/f confirm §f- Confirmar ação destrutiva pendente.");
    }

    private void handleUpgrade(Player player) {
        com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(MessageHelper.error("Você precisa estar em um clã para usar este comando."));
            return;
        }

        // Verificar permissões (Leader ou Officer apenas)
        String role = plugin.getClansPlugin().getClansManager().getMemberRole(clan.getId(), player.getUniqueId());
        if (role == null || (!role.equals("LEADER") && !role.equals("OFFICER"))) {
            player.sendMessage(MessageHelper.error("Apenas líderes e oficiais podem gerenciar upgrades."));
            return;
        }

        // Abrir GUI de upgrades
        org.bukkit.inventory.Inventory gui = plugin.getUpgradeManager().createUpgradeGUI(player, clan.getId());
        player.openInventory(gui);
    }

    private void handleClaim(Player player, String[] args) {
        com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(MessageHelper.error("Você precisa estar em um clã para usar este comando."));
            return;
        }

        // Verificar permissões (Leader ou Officer apenas)
        String role = plugin.getClansPlugin().getClansManager().getMemberRole(clan.getId(), player.getUniqueId());
        if (role == null || (!role.equals("LEADER") && !role.equals("OFFICER"))) {
            player.sendMessage(MessageHelper.error("Apenas líderes e oficiais podem claimar territórios."));
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        String worldName = chunk.getWorld().getName();

        // Validar mundo permitido
        java.util.List<String> allowedWorlds = plugin.getConfig().getStringList("claims.allowed-worlds");
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(worldName)) {
            player.sendMessage(MessageHelper.error("Claims desativados neste mundo."));
            return;
        }

        // Validar power máximo (async para não bloquear main thread)
        final int finalClanId = clan.getId();
        final int chunkX = chunk.getX();
        final int chunkZ = chunk.getZ();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            double totalPower = plugin.getPowerManager().getClanTotalPower(finalClanId);
            int currentClaims = plugin.getClaimManager().getClaimCount(finalClanId);
            int maxClaims = (int) (totalPower / 10.0); // 1 claim = 10 power

            // Voltar para main thread para claimar e enviar mensagens
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Verificar se player ainda está online (pode ter saído durante async)
                if (player == null || !player.isOnline()) {
                    return;
                }

                // Verificar se clã ainda existe (pode ter sido deletado durante async)
                com.primeleague.clans.models.ClanData currentClan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
                if (currentClan == null || currentClan.getId() != finalClanId) {
                    player.sendMessage(MessageHelper.error("Seu clã não existe mais ou você foi removido."));
                    return;
                }

                // Verificar role novamente (pode ter mudado durante async)
                String currentRole = plugin.getClansPlugin().getClansManager().getMemberRole(finalClanId, player.getUniqueId());
                if (currentRole == null || (!currentRole.equals("LEADER") && !currentRole.equals("OFFICER"))) {
                    player.sendMessage(MessageHelper.error("Você não tem mais permissão para claimar territórios."));
                    return;
                }

                if (maxClaims > 0 && currentClaims >= maxClaims) {
                    player.sendMessage(MessageHelper.error("Clã sem power suficiente! Máximo: " + MessageHelper.highlight(String.valueOf(maxClaims)) + " claims (Power total: " + String.format("%.1f", totalPower) + ")"));
                    return;
                }

                boolean success = plugin.getClaimManager().claimChunk(worldName, chunkX, chunkZ, finalClanId);

                if (success) {
                    player.sendMessage(MessageHelper.success("Território conquistado!"));
                    ParticleBorder.showChunkBorder(player, chunk.getWorld(), chunkX, chunkZ, Effect.FLAME);

                    // Notificar Discord
                    if (plugin.getDiscordIntegration() != null && plugin.getDiscordIntegration().isEnabled()) {
                        int totalClaims = plugin.getClaimManager().getClaimCount(finalClanId);
                        plugin.getDiscordIntegration().sendTerritoryClaimed(
                            clan.getName(),
                            player.getName(),
                            chunkX,
                            chunkZ,
                            worldName,
                            totalClaims
                        );
                    }
                } else {
                    player.sendMessage(MessageHelper.error("Este território já possui dono."));
                }
            });
        });
    }

    /**
     * Handle /f confirm - Confirma ação destrutiva pendente
     */
    private void handleConfirm(Player player) {
        // Limpar confirmações expiradas
        cleanupExpiredConfirmations();

        UUID playerUuid = player.getUniqueId();
        PendingConfirmation pending = pendingConfirmations.remove(playerUuid);

        if (pending == null) {
            player.sendMessage(MessageHelper.error("Nenhuma confirmação pendente."));
            return;
        }

        if (pending.isExpired()) {
            player.sendMessage(MessageHelper.error("Confirmação expirada! Execute o comando novamente."));
            return;
        }

        // Processar confirmação baseada no tipo
        if (pending.type == ActionType.UNCLAIM) {
            // CORREÇÃO: Validar novamente antes de executar
            com.primeleague.clans.models.ClanData clan =
                plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
            if (clan == null) {
                player.sendMessage(MessageHelper.error("Você não está mais em um clan."));
                return;
            }

            // CORREÇÃO: Verificar se chunk ainda existe e pertence ao clan
            org.bukkit.World world = plugin.getServer().getWorld(pending.worldName);
            if (world == null) {
                player.sendMessage(MessageHelper.error("Mundo não encontrado."));
                return;
            }

            Chunk chunk = world.getChunkAt(pending.chunkX, pending.chunkZ);
            int ownerId = plugin.getClaimManager().getClanAt(chunk);

            if (ownerId != clan.getId() && !player.hasPermission("factions.admin")) {
                player.sendMessage(MessageHelper.error("Este território não pertence mais ao seu clan."));
                return;
            }

            executeUnclaim(player, chunk, clan);
        }
    }

    private void handleUnclaim(Player player, String[] args) {
        com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(MessageHelper.error("Você precisa estar em um clã para usar este comando."));
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        int ownerId = plugin.getClaimManager().getClanAt(chunk);

        if (ownerId != clan.getId() && !player.hasPermission("factions.admin")) {
            player.sendMessage(MessageHelper.error("Este território não pertence ao seu clã."));
            return;
        }

        // Limpar confirmações expiradas
        cleanupExpiredConfirmations();

        // Verificar se há confirmação pendente
        UUID playerUuid = player.getUniqueId();
        PendingConfirmation pending = pendingConfirmations.get(playerUuid);

        // Se já tem confirmação pendente, avisar
        if (pending != null && pending.type == ActionType.UNCLAIM) {
            player.sendMessage(MessageHelper.warning("Confirmação pendente! Use " + MessageHelper.highlight("/f confirm") + " para confirmar."));
            player.sendMessage(ChatColor.GRAY + "Ou espere 30 segundos para a confirmação expirar.");
            return;
        }

        // CORREÇÃO: Armazenar coordenadas ao invés de Chunk (evita desatualização)
        pendingConfirmations.put(playerUuid, new PendingConfirmation(
            ActionType.UNCLAIM,
            chunk.getWorld().getName(),
            chunk.getX(),
            chunk.getZ()
        ));
        player.sendMessage(MessageHelper.warning("ATENÇÃO: Você está prestes a abandonar este território!"));
        player.sendMessage(MessageHelper.info("Use " + MessageHelper.highlight("/f confirm") + " para confirmar ou espere 30 segundos para cancelar."));
        player.sendMessage(ChatColor.GRAY + "Território: " + ChatColor.WHITE + chunk.getWorld().getName() + ChatColor.GRAY + " (" + chunk.getX() + ", " + chunk.getZ() + ")");
    }

    /**
     * Executa o unclaim após confirmação
     */
    private void executeUnclaim(Player player, Chunk chunk, com.primeleague.clans.models.ClanData clan) {
        boolean success = plugin.getClaimManager().unclaimChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (success) {
            player.sendMessage(MessageHelper.success("Território abandonado."));

            // Notificar Discord
            if (plugin.getDiscordIntegration() != null && plugin.getDiscordIntegration().isEnabled()) {
                int totalClaims = plugin.getClaimManager().getClaimCount(clan.getId());
                plugin.getDiscordIntegration().sendTerritoryUnclaimed(
                    clan.getName(),
                    player.getName(),
                    chunk.getX(),
                    chunk.getZ(),
                    chunk.getWorld().getName(),
                    totalClaims
                );
            }
        } else {
            player.sendMessage(MessageHelper.error("Este território não estava conquistado."));
        }
    }

    /**
     * Limpa confirmações expiradas
     */
    private void cleanupExpiredConfirmations() {
        pendingConfirmations.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Limpa confirmação pendente de um player específico
     * CORREÇÃO: Método público para listener de PlayerQuitEvent
     */
    public void clearPendingConfirmation(java.util.UUID playerUuid) {
        pendingConfirmations.remove(playerUuid);
    }

    private void handleMap(Player player) {
        player.sendMessage("§e§lMapa de Territórios (Raio 3):");
        Set<ChunkKey> claims = plugin.getClaimManager().getClaimsInRadius(player.getLocation(), 3);

        // Visual feedback using particles for all nearby claims
        for (ChunkKey key : claims) {
            ParticleBorder.showChunkBorder(player, player.getWorld(), key.getX(), key.getZ(), Effect.HAPPY_VILLAGER);
        }
        player.sendMessage("§aBordas visíveis por 5 segundos.");
    }

    private void handlePower(Player player) {
        double power = plugin.getPowerManager().getPower(player.getUniqueId());
        player.sendMessage("§eSeu Poder: §f" + String.format("%.2f", power));
    }

    private void handleShield(Player player, String[] args) {
        com.primeleague.clans.models.ClanData clan =
            plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(MessageHelper.error("Você precisa estar em um clã para usar este comando."));
            return;
        }

        // Verificar permissões
        String role = plugin.getClansPlugin().getClansManager().getMemberRole(clan.getId(), player.getUniqueId());
        if (role == null || (!role.equals("LEADER") && !role.equals("OFFICER"))) {
            player.sendMessage(MessageHelper.error("Apenas líderes e oficiais podem usar este comando."));
            return;
        }

        if (args.length < 2) {
            // Mostrar status atual
            long remaining = plugin.getShieldManager().getRemainingMinutes(clan.getId());
            if (remaining == 0) {
                player.sendMessage("§c🛡 Shield: §4ZERADO");
                player.sendMessage("§7Use: §6/f shield <horas> §7para ativar (ex: /f shield 72)");
            } else {
                String formatted = plugin.getShieldManager().formatRemaining(clan.getId());
                player.sendMessage("§e🛡 Shield: " + formatted);
                player.sendMessage("§7Use: §6/f shield <horas> §7para adicionar tempo");
                player.sendMessage("§7Custo: §650k por hora");
            }
            return;
        }

        // Comprar shield: /f shield 24
        try {
            int hours = Integer.parseInt(args[1]);
            if (hours < 1 || hours > 168) {
                player.sendMessage(MessageHelper.error("Horas inválidas. Use um valor entre 1 e 168 horas."));
                return;
            }

            long cost = hours * 50000L;
            long balance = plugin.getClansPlugin().getClansManager().getClanBalance(clan.getId());

            if (balance < cost) {
                player.sendMessage(MessageHelper.error("Saldo insuficiente! Custo: " + MessageHelper.highlight("$" + String.format("%.2f", cost/100.0)) + 
                    " | Saldo: $" + String.format("%.2f", balance/100.0)));
                return;
            }

            // Ativar shield (async para não bloquear)
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean success = plugin.getShieldManager().activateShield(clan.getId(), hours);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage(MessageHelper.success("🛡 Shield ativado por " + MessageHelper.highlight(hours + "h") + "!"));

                        // Mostrar ActionBar uma vez após ativar (feedback imediato)
                        long newRemaining = plugin.getShieldManager().getRemainingMinutes(clan.getId());
                        String formatted = plugin.getShieldManager().formatRemaining(clan.getId());
                        String color = newRemaining < 720 ? "§e" : "§a";
                        com.primeleague.factions.util.ActionBarCompat.send(
                            player, color + "🛡 Shield: " + formatted
                        );
                    } else {
                        player.sendMessage(MessageHelper.error("Erro ao ativar shield."));
                    }
                });
            });
        } catch (NumberFormatException e) {
            player.sendMessage(MessageHelper.error("Uso: /f shield <horas>"));
        }
    }
}
