package com.primeleague.clans.commands;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.models.ClanData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Handler de comandos de territórios (Factions integrado ao Clans)
 * Grug Brain: Arquivo separado para não inflar ClanCommand.java
 * Factions é apenas sistema de claims - Clans é o sistema principal
 */
public class ClanTerritoryHandler {

    private final ClansPlugin plugin;

    public ClanTerritoryHandler(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Obtém instância do FactionsPlugin (soft dependency)
     */
    private com.primeleague.factions.PrimeFactions getFactionsPlugin() {
        org.bukkit.plugin.Plugin pf = plugin.getServer()
            .getPluginManager().getPlugin("PrimeleagueFactions");
        if (pf == null || !pf.isEnabled()) {
            return null;
        }
        if (pf instanceof com.primeleague.factions.PrimeFactions) {
            return (com.primeleague.factions.PrimeFactions) pf;
        }
        return null;
    }

    /**
     * Verifica se Factions está disponível
     */
    public boolean isFactionsEnabled() {
        return getFactionsPlugin() != null;
    }

    /**
     * /clan territorio - Mostra info completa de territórios do clan
     */
    public boolean handleTerritorio(Player player, String[] args) {
        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Você não está em um clan.");
            return true;
        }

        if (!isFactionsEnabled()) {
            player.sendMessage(ChatColor.RED + "Sistema de territórios não está disponível.");
            return true;
        }

        com.primeleague.factions.PrimeFactions pf = getFactionsPlugin();

        // Power total do clã
        double totalPower = pf.getPowerManager().getClanTotalPower(clan.getId());
        int currentClaims = pf.getClaimManager().getClaimCount(clan.getId());
        int maxClaims = (int) (totalPower / 10.0);

        // Shield
        long shieldMinutes = pf.getShieldManager().getRemainingMinutes(clan.getId());
        String shieldText = shieldMinutes > 0 ?
            pf.getShieldManager().formatRemaining(clan.getId()) : ChatColor.RED + "ZERADO";

        // Banco
        long balance = plugin.getClansManager().getClanBalance(clan.getId());

        // Mostrar TUDO em um comando
        player.sendMessage(ChatColor.GOLD + "=== Territórios de " + ChatColor.YELLOW + clan.getName() + ChatColor.GOLD + " ===");
        player.sendMessage(ChatColor.YELLOW + "Claims: " + ChatColor.WHITE + currentClaims +
            "/" + maxClaims + ChatColor.GRAY + " (" + String.format("%.1f", totalPower) + " power)");
        player.sendMessage(ChatColor.YELLOW + "Power Total: " + ChatColor.WHITE + String.format("%.1f", totalPower));
        player.sendMessage(ChatColor.YELLOW + "🛡 Shield: " + shieldText);
        player.sendMessage(ChatColor.YELLOW + "Banco: " + ChatColor.WHITE + String.format("$%.2f", balance / 100.0));
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/clan claim" +
            ChatColor.GRAY + " para conquistar territórios");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/f" +
            ChatColor.GRAY + " para comandos rápidos de territórios");

        return true;
    }

    /**
     * /clan power - Mostra power do clã (contexto completo)
     */
    public boolean handlePower(Player player, String[] args) {
        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Você não está em um clan.");
            return true;
        }

        if (!isFactionsEnabled()) {
            player.sendMessage(ChatColor.RED + "Sistema de territórios não está disponível.");
            return true;
        }

        com.primeleague.factions.PrimeFactions pf = getFactionsPlugin();

        // Power individual
        double playerPower = pf.getPowerManager().getPower(player.getUniqueId());

        // Power total do clã
        double totalPower = pf.getPowerManager().getClanTotalPower(clan.getId());

        // Claims
        int currentClaims = pf.getClaimManager().getClaimCount(clan.getId());
        int maxClaims = (int) (totalPower / 10.0);

        // Mostrar tudo
        player.sendMessage(ChatColor.GOLD + "=== Power do Clan " + ChatColor.YELLOW + clan.getName() + ChatColor.GOLD + " ===");
        player.sendMessage(ChatColor.YELLOW + "Seu Power: " + ChatColor.WHITE + String.format("%.2f", playerPower));
        player.sendMessage(ChatColor.YELLOW + "Power Total: " + ChatColor.WHITE + String.format("%.2f", totalPower));
        player.sendMessage(ChatColor.YELLOW + "Territórios: " + ChatColor.WHITE + currentClaims + "/" + maxClaims);

        if (maxClaims > 0 && currentClaims < maxClaims) {
            player.sendMessage(ChatColor.GREEN + "Pode claimar mais " +
                (maxClaims - currentClaims) + " territórios");
        } else if (maxClaims > 0) {
            player.sendMessage(ChatColor.RED + "Power insuficiente para mais territórios!");
        }

        return true;
    }

    /**
     * /clan claim - Delega para FactionsCommand
     * Grug Brain: args já contém "claim" no args[0], apenas passar direto
     */
    public boolean handleClaim(Player player, String[] args) {
        if (!isFactionsEnabled()) {
            player.sendMessage(ChatColor.RED + "Sistema de territórios não está disponível.");
            return true;
        }

        // Delegar para FactionsCommand (reutilizar código existente)
        com.primeleague.factions.PrimeFactions pf = getFactionsPlugin();
        com.primeleague.factions.command.FactionsCommand fc =
            new com.primeleague.factions.command.FactionsCommand(pf);

        // args já contém "claim" no args[0] (do switch do ClanCommand)
        // FactionsCommand.onCommand() espera args[0] = "claim", então está correto
        return fc.onCommand(player, null, "f", args);
    }

    /**
     * /clan unclaim - Delega para FactionsCommand
     */
    public boolean handleUnclaim(Player player, String[] args) {
        if (!isFactionsEnabled()) {
            player.sendMessage(ChatColor.RED + "Sistema de territórios não está disponível.");
            return true;
        }

        com.primeleague.factions.PrimeFactions pf = getFactionsPlugin();
        com.primeleague.factions.command.FactionsCommand fc =
            new com.primeleague.factions.command.FactionsCommand(pf);

        return fc.onCommand(player, null, "f", new String[]{"unclaim"});
    }

    /**
     * /clan mapa ou /clan map - Delega para FactionsCommand
     */
    public boolean handleMapa(Player player, String[] args) {
        if (!isFactionsEnabled()) {
            player.sendMessage(ChatColor.RED + "Sistema de territórios não está disponível.");
            return true;
        }

        com.primeleague.factions.PrimeFactions pf = getFactionsPlugin();
        com.primeleague.factions.command.FactionsCommand fc =
            new com.primeleague.factions.command.FactionsCommand(pf);

        return fc.onCommand(player, null, "f", new String[]{"map"});
    }

    /**
     * /clan shield - Delega para FactionsCommand
     * Grug Brain: args já contém "shield" no args[0], apenas passar direto
     */
    public boolean handleShield(Player player, String[] args) {
        if (!isFactionsEnabled()) {
            player.sendMessage(ChatColor.RED + "Sistema de territórios não está disponível.");
            return true;
        }

        com.primeleague.factions.PrimeFactions pf = getFactionsPlugin();
        com.primeleague.factions.command.FactionsCommand fc =
            new com.primeleague.factions.command.FactionsCommand(pf);

        // args já contém "shield" no args[0] (do switch do ClanCommand)
        // Se tiver args[1] = "24", FactionsCommand.handleShield() vai processar
        return fc.onCommand(player, null, "f", args);
    }

    /**
     * /clan upgrade - Delega para FactionsCommand
     */
    public boolean handleUpgrade(Player player, String[] args) {
        if (!isFactionsEnabled()) {
            player.sendMessage(ChatColor.RED + "Sistema de territórios não está disponível.");
            return true;
        }

        com.primeleague.factions.PrimeFactions pf = getFactionsPlugin();
        com.primeleague.factions.command.FactionsCommand fc =
            new com.primeleague.factions.command.FactionsCommand(pf);

        return fc.onCommand(player, null, "f", new String[]{"upgrade"});
    }

    /**
     * /clan fly - Delega para FactionsCommand
     */
    public boolean handleFly(Player player, String[] args) {
        if (!isFactionsEnabled()) {
            player.sendMessage(ChatColor.RED + "Sistema de territórios não está disponível.");
            return true;
        }

        com.primeleague.factions.PrimeFactions pf = getFactionsPlugin();
        com.primeleague.factions.command.FactionsCommand fc =
            new com.primeleague.factions.command.FactionsCommand(pf);

        return fc.onCommand(player, null, "f", new String[]{"fly"});
    }
}

