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
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
            sender.sendMessage("§cApenas jogadores.");
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
            case "info":
                handleInfo(player);
                break;
            case "hud":
                handleHud(player);
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
        player.sendMessage("§6/f info §f- Ver informações do território atual.");
        player.sendMessage("§6/f hud §f- Ativar/Desativar HUD contextual.");
        player.sendMessage("§6/f confirm §f- Confirmar ação destrutiva pendente.");
    }

    private void handleUpgrade(Player player) {
        com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVocê precisa de um clã.");
            return;
        }

        // Verificar permissões (Leader ou Officer apenas)
        String role = plugin.getClansPlugin().getClansManager().getMemberRole(clan.getId(), player.getUniqueId());
        if (role == null || (!role.equals("LEADER") && !role.equals("OFFICER"))) {
            player.sendMessage("§cApenas líderes e oficiais podem gerenciar upgrades!");
            return;
        }

        // Abrir GUI de upgrades
        org.bukkit.inventory.Inventory gui = plugin.getUpgradeManager().createUpgradeGUI(player, clan.getId());
        player.openInventory(gui);
    }

    private void handleClaim(Player player, String[] args) {
        com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVocê precisa de um clã.");
            return;
        }

        // Verificar permissões (Leader ou Officer apenas)
        String role = plugin.getClansPlugin().getClansManager().getMemberRole(clan.getId(), player.getUniqueId());
        if (role == null || (!role.equals("LEADER") && !role.equals("OFFICER"))) {
            player.sendMessage("§cApenas líderes e oficiais podem claimar territórios!");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        String worldName = chunk.getWorld().getName();

        // Validar mundo permitido
        java.util.List<String> allowedWorlds = plugin.getConfig().getStringList("claims.allowed-worlds");
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(worldName)) {
            player.sendMessage("§cClaims desativados neste mundo!");
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
                    player.sendMessage("§cSeu clã não existe mais ou você foi removido.");
                    return;
                }

                // Verificar role novamente (pode ter mudado durante async)
                String currentRole = plugin.getClansPlugin().getClansManager().getMemberRole(finalClanId, player.getUniqueId());
                if (currentRole == null || (!currentRole.equals("LEADER") && !currentRole.equals("OFFICER"))) {
                    player.sendMessage("§cVocê não tem mais permissão para claimar territórios!");
                    return;
                }

                if (maxClaims > 0 && currentClaims >= maxClaims) {
                    player.sendMessage("§cClã sem power suficiente! Máximo: " + maxClaims + " claims (Power total: " + String.format("%.1f", totalPower) + ")");
                    return;
                }

                boolean success = plugin.getClaimManager().claimChunk(worldName, chunkX, chunkZ, finalClanId);

                if (success) {
                    player.sendMessage("§aTerritório conquistado!");
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
                    player.sendMessage("§cEste território já possui dono.");
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
            player.sendMessage("§cNenhuma confirmação pendente!");
            return;
        }

        if (pending.isExpired()) {
            player.sendMessage("§cConfirmação expirada! Execute o comando novamente.");
            return;
        }

        // Processar confirmação baseada no tipo
        if (pending.type == ActionType.UNCLAIM) {
            // CORREÇÃO: Validar novamente antes de executar
            com.primeleague.clans.models.ClanData clan =
                plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
            if (clan == null) {
                player.sendMessage("§cVocê não está mais em um clan.");
                return;
            }

            // CORREÇÃO: Verificar se chunk ainda existe e pertence ao clan
            org.bukkit.World world = plugin.getServer().getWorld(pending.worldName);
            if (world == null) {
                player.sendMessage("§cMundo não encontrado!");
                return;
            }

            Chunk chunk = world.getChunkAt(pending.chunkX, pending.chunkZ);
            int ownerId = plugin.getClaimManager().getClanAt(chunk);

            if (ownerId != clan.getId() && !player.hasPermission("factions.admin")) {
                player.sendMessage("§cEste território não pertence mais ao seu clan.");
                return;
            }

            executeUnclaim(player, chunk, clan);
        }
    }

    private void handleUnclaim(Player player, String[] args) {
        com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVocê precisa de um clã.");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        int ownerId = plugin.getClaimManager().getClanAt(chunk);

        if (ownerId != clan.getId() && !player.hasPermission("factions.admin")) {
            player.sendMessage("§cEste território não é seu.");
            return;
        }

        // Limpar confirmações expiradas
        cleanupExpiredConfirmations();

        // Verificar se há confirmação pendente
        UUID playerUuid = player.getUniqueId();
        PendingConfirmation pending = pendingConfirmations.get(playerUuid);

        // Se já tem confirmação pendente, avisar
        if (pending != null && pending.type == ActionType.UNCLAIM) {
            player.sendMessage("§eConfirmação pendente! Use §6/f confirm §epara confirmar.");
            player.sendMessage("§7Ou espere 30 segundos para a confirmação expirar.");
            return;
        }

        // CORREÇÃO: Armazenar coordenadas ao invés de Chunk (evita desatualização)
        pendingConfirmations.put(playerUuid, new PendingConfirmation(
            ActionType.UNCLAIM,
            chunk.getWorld().getName(),
            chunk.getX(),
            chunk.getZ()
        ));
        player.sendMessage("§c⚠ ATENÇÃO: Você está prestes a abandonar este território!");
        player.sendMessage("§eUse §6/f confirm §epara confirmar ou espere 30 segundos para cancelar.");
        player.sendMessage("§7Território: §f" + chunk.getWorld().getName() + " §7(" + chunk.getX() + ", " + chunk.getZ() + ")");
    }

    /**
     * Executa o unclaim após confirmação
     */
    private void executeUnclaim(Player player, Chunk chunk, com.primeleague.clans.models.ClanData clan) {
        boolean success = plugin.getClaimManager().unclaimChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (success) {
            player.sendMessage("§aTerritório abandonado.");

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
            player.sendMessage("§cEste território não estava conquistado.");
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
            player.sendMessage("§cVocê precisa de um clã.");
            return;
        }

        // Verificar permissões
        String role = plugin.getClansPlugin().getClansManager().getMemberRole(clan.getId(), player.getUniqueId());
        if (role == null || (!role.equals("LEADER") && !role.equals("OFFICER"))) {
            player.sendMessage("§cApenas líderes e oficiais!");
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
                player.sendMessage("§cHoras inválidas (1-168)");
                return;
            }

            long cost = hours * 50000L;
            long balance = plugin.getClansPlugin().getClansManager().getClanBalance(clan.getId());

            if (balance < cost) {
                player.sendMessage("§cSaldo insuficiente! Custo: $" + String.format("%.2f", cost/100.0) +
                    " | Saldo: $" + String.format("%.2f", balance/100.0));
                return;
            }

            // Ativar shield (async para não bloquear)
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean success = plugin.getShieldManager().activateShield(clan.getId(), hours);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage("§a🛡 Shield ativado por " + hours + "h!");

                        // Mostrar ActionBar uma vez após ativar (feedback imediato)
                        long newRemaining = plugin.getShieldManager().getRemainingMinutes(clan.getId());
                        String formatted = plugin.getShieldManager().formatRemaining(clan.getId());
                        String color = newRemaining < 720 ? "§e" : "§a";
                        com.primeleague.factions.util.ActionBarCompat.send(
                            player, color + "🛡 Shield: " + formatted
                        );
                    } else {
                        player.sendMessage("§cErro ao ativar shield!");
                    }
                });
            });
        } catch (NumberFormatException e) {
            player.sendMessage("§cUso: /f shield <horas>");
        }
    }

    /**
     * Handle /f info - Mostra informações do território atual
     */
    private void handleInfo(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        int ownerId = plugin.getClaimManager().getClanAt(chunk);

        // Se não claimado, mostrar mensagem
        if (ownerId == 0) {
            player.sendMessage(MessageHelper.info("Território não claimado."));
            return;
        }

        // Buscar dados do clan dono (async para não bloquear)
        final int finalOwnerId = ownerId;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ClanData ownerClan = plugin.getClansPlugin().getClansManager().getClan(finalOwnerId);
            if (ownerClan == null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(MessageHelper.error("Clan dono não encontrado."));
                });
                return;
            }

            // Buscar dados (async-safe)
            double totalPower = plugin.getPowerManager().getClanTotalPower(finalOwnerId);
            long shieldRemaining = plugin.getShieldManager().getRemainingMinutes(finalOwnerId);
            String shieldFormatted = plugin.getShieldManager().formatRemaining(finalOwnerId);
            com.primeleague.factions.manager.UpgradeManager.UpgradeData upgrades =
                plugin.getUpgradeManager().getUpgrades(finalOwnerId);

            // Buscar membros online (voltar para main thread para isso)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                List<String> onlineMembers = plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> {
                        ClanData playerClan = plugin.getClansPlugin().getClansManager()
                            .getClanByMember(p.getUniqueId());
                        return playerClan != null && playerClan.getId() == finalOwnerId;
                    })
                    .map(Player::getName)
                    .collect(Collectors.toList());

                // Formatar e exibir mensagens
                player.sendMessage("");
                player.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage(MessageHelper.info("Território: " + MessageHelper.highlight(chunk.getWorld().getName() +
                    " §7(" + chunk.getX() + ", " + chunk.getZ() + ")")));
                player.sendMessage("");
                player.sendMessage(MessageHelper.info("Clan Dono: " + MessageHelper.highlight(ownerClan.getName() +
                    " [" + ownerClan.getTag() + "]")));
                // Buscar total de membros do clan
                int totalMembers = plugin.getClansPlugin().getClansManager().getMembers(finalOwnerId).size();
                player.sendMessage(MessageHelper.info("Membros Online: " + MessageHelper.highlight(
                    onlineMembers.size() + "/" + totalMembers)));

                if (!onlineMembers.isEmpty()) {
                    String membersStr = String.join("§7, §e", onlineMembers);
                    player.sendMessage("§7  " + membersStr);
                }

                player.sendMessage("");
                player.sendMessage(MessageHelper.info("Power Total: " + MessageHelper.highlight(
                    String.format("%.1f", totalPower))));

                if (shieldRemaining > 0) {
                    player.sendMessage(MessageHelper.info("Shield: " + MessageHelper.highlight(shieldFormatted)));
                } else {
                    player.sendMessage(MessageHelper.info("Shield: §4ZERADO"));
                }

                // Upgrades ativos
                boolean hasUpgrades = upgrades.getSpawnerRate() > 0 || upgrades.getCropGrowth() > 0 ||
                    upgrades.getExpBoost() > 0 || upgrades.getExtraShieldHours() > 0;

                if (hasUpgrades) {
                    player.sendMessage("");
                    player.sendMessage(MessageHelper.info("Upgrades Ativos:"));

                    if (upgrades.getSpawnerRate() > 0) {
                        player.sendMessage("§7  • Taxa de Spawners: " + MessageHelper.highlight("+" +
                            (upgrades.getSpawnerRate() * 5) + "%"));
                    }
                    if (upgrades.getCropGrowth() > 0) {
                        player.sendMessage("§7  • Crescimento de Plantas: " + MessageHelper.highlight("+" +
                            (upgrades.getCropGrowth() * 5) + "%"));
                    }
                    if (upgrades.getExpBoost() > 0) {
                        player.sendMessage("§7  • EXP de Mobs: " + MessageHelper.highlight("+" +
                            (upgrades.getExpBoost() * 5) + "%"));
                    }
                    if (upgrades.getExtraShieldHours() > 0) {
                        player.sendMessage("§7  • Shield Extra: " + MessageHelper.highlight("+" +
                            upgrades.getExtraShieldHours() + "h"));
                    }
                }

                player.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("");
            });
        });
    }

    /**
     * Handle /f hud - Toggle HUD contextual (ActionBar)
     */
    private void handleHud(Player player) {
        boolean enabled = plugin.toggleHud(player.getUniqueId());
        if (enabled) {
            player.sendMessage(MessageHelper.success("HUD contextual ativado!"));
            // Mostrar HUD uma vez imediatamente (feedback)
            updateHudForPlayer(player);
        } else {
            player.sendMessage(MessageHelper.info("HUD contextual desativado."));
            // Limpar ActionBar
            com.primeleague.factions.util.ActionBarCompat.send(player, "");
        }
    }

    /**
     * Atualiza HUD para um player específico (chamado pela task periódica)
     */
    public void updateHudForPlayer(Player player) {
        if (!plugin.isHudEnabled(player.getUniqueId())) {
            return;
        }

        // Buscar dados (sync - já está na task)
        UUID uuid = player.getUniqueId();
        double power = plugin.getPowerManager().getPower(uuid);
        double maxPower = plugin.getPowerManager().getMaxPower(uuid);

        // Verificar se está em clan para mostrar shield e território
        com.primeleague.clans.models.ClanData clan =
            plugin.getClansPlugin().getClansManager().getClanByMember(uuid);

        StringBuilder hudText = new StringBuilder();

        // Power
        hudText.append("§e⚡ Power: §f").append(String.format("%.0f/%.0f", power, maxPower));

        if (clan != null) {
            // Shield (só mostra se > 0)
            long shieldRemaining = plugin.getShieldManager().getRemainingMinutes(clan.getId());
            if (shieldRemaining > 0) {
                String shieldFormatted = plugin.getShieldManager().formatRemaining(clan.getId());
                String shieldColor = shieldRemaining < 360 ? "§c" : (shieldRemaining < 720 ? "§e" : "§a");
                hudText.append(" §7| ").append(shieldColor).append("🛡 Shield: ").append(shieldFormatted);
            }

            // Território atual
            Chunk chunk = player.getLocation().getChunk();
            int ownerId = plugin.getClaimManager().getClanAt(chunk);

            hudText.append(" §7| ");
            if (ownerId == 0) {
                hudText.append("§7🏰 Sem dono");
            } else if (ownerId == clan.getId()) {
                hudText.append("§a🏰 ").append(clan.getName());
            } else {
                com.primeleague.clans.models.ClanData ownerClan =
                    plugin.getClansPlugin().getClansManager().getClan(ownerId);
                if (ownerClan != null) {
                    hudText.append("§c🏰 ").append(ownerClan.getName());
                } else {
                    hudText.append("§7🏰 Desconhecido");
                }
            }
        } else {
            // Sem clan, mostrar território como "Solo"
            Chunk chunk = player.getLocation().getChunk();
            int ownerId = plugin.getClaimManager().getClanAt(chunk);
            hudText.append(" §7| ");
            if (ownerId == 0) {
                hudText.append("§7🏰 Solo");
            } else {
                com.primeleague.clans.models.ClanData ownerClan =
                    plugin.getClansPlugin().getClansManager().getClan(ownerId);
                if (ownerClan != null) {
                    hudText.append("§c🏰 ").append(ownerClan.getName());
                } else {
                    hudText.append("§7🏰 Desconhecido");
                }
            }
        }

        // Enviar ActionBar
        com.primeleague.factions.util.ActionBarCompat.send(player, hudText.toString());
    }
}
