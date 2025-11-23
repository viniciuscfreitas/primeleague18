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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
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
            case "wand":
                giveWand(player);
                break;
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
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§e§lPrimeFactions §7- Comandos:");
        player.sendMessage("§6/f wand §f- Receber a Golden Hoe de claim.");
        player.sendMessage("§6/f claim §f- Conquistar o chunk atual.");
        player.sendMessage("§6/f unclaim §f- Abandonar o chunk atual.");
        player.sendMessage("§6/f map §f- Ver mapa de territórios.");
        player.sendMessage("§6/f power §f- Ver seu poder.");
        player.sendMessage("§6/f fly §f- Ativar/Desativar voo em território.");
    }

    private void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.GOLD_HOE);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName("§6§lCetro de Conquista");
        meta.setLore(Arrays.asList(
                "§7Use para conquistar terras.",
                "§eBotão Esq: §fPosição 1",
                "§eBotão Dir: §fPosição 2",
                "§eShift+Dir: §fConquistar Área"
        ));
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage("§aVocê recebeu o Cetro de Conquista!");
    }

    private void handleClaim(Player player, String[] args) {
        com.primeleague.clans.models.ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVocê precisa de um clã.");
            return;
        }

        Chunk chunk = player.getLocation().getChunk();
        boolean success = plugin.getClaimManager().claimChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), clan.getId());

        if (success) {
            player.sendMessage("§aTerritório conquistado!");
            ParticleBorder.showChunkBorder(player, chunk.getWorld(), chunk.getX(), chunk.getZ(), Effect.FLAME);
            
            // Notificar Discord
            if (plugin.getDiscordIntegration() != null && plugin.getDiscordIntegration().isEnabled()) {
                int totalClaims = plugin.getClaimManager().getClaimCount(clan.getId());
                plugin.getDiscordIntegration().sendTerritoryClaimed(
                    clan.getName(),
                    player.getName(),
                    chunk.getX(),
                    chunk.getZ(),
                    chunk.getWorld().getName(),
                    totalClaims
                );
            }
        } else {
            player.sendMessage("§cEste território já possui dono.");
        }
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
}
