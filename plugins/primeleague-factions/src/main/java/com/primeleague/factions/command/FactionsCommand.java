package com.primeleague.factions.command;

import com.primeleague.clans.models.ClanData;
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

public class FactionsCommand implements CommandExecutor {

    private final PrimeFactions plugin;

    public FactionsCommand(PrimeFactions plugin) {
        this.plugin = plugin;
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
                handleUnclaim(player);
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

    private void handleUnclaim(Player player) {
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
}
