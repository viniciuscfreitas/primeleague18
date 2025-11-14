package com.primeleague.economy.integrations;

import com.primeleague.economy.EconomyAPI;
import com.primeleague.economy.EconomyPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

/**
 * Vault Economy Provider
 * Grug Brain: Implementação simples, métodos diretos via EconomyAPI
 */
public class VaultEconomyProvider implements Economy {

    private final EconomyPlugin plugin;
    private final DecimalFormat format = new DecimalFormat("#,##0.00");

    public VaultEconomyProvider(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return EconomyAPI.isEnabled();
    }

    @Override
    public String getName() {
        return "PrimeleagueEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false; // Sem suporte a bancos
    }

    @Override
    public int fractionalDigits() {
        return 2; // 2 casas decimais
    }

    @Override
    public String format(double amount) {
        String currency = plugin.getConfig().getString("economy.simbolo", "¢");
        return format.format(amount) + currency;
    }

    @Override
    public String currencyNamePlural() {
        return plugin.getConfig().getString("economy.simbolo", "¢");
    }

    @Override
    public String currencyNameSingular() {
        return plugin.getConfig().getString("economy.simbolo", "¢");
    }

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(getUUID(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return hasAccount(player.getUniqueId());
    }

    private boolean hasAccount(UUID uuid) {
        return EconomyAPI.getBalance(uuid) >= 0; // Sempre tem conta (mesmo se saldo 0)
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName); // Sem suporte a múltiplos mundos
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(getUUID(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    private double getBalance(UUID uuid) {
        return EconomyAPI.getBalance(uuid);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return has(getUUID(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return has(player.getUniqueId(), amount);
    }

    private boolean has(UUID uuid, double amount) {
        return EconomyAPI.hasBalance(uuid, amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(getUUID(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdrawPlayer(player.getUniqueId(), amount);
    }

    private EconomyResponse withdrawPlayer(UUID uuid, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Valor não pode ser negativo");
        }

        if (!EconomyAPI.hasBalance(uuid, amount)) {
            return new EconomyResponse(0, getBalance(uuid), EconomyResponse.ResponseType.FAILURE, "Saldo insuficiente");
        }

        EconomyAPI.removeMoney(uuid, amount, "VAULT_WITHDRAW");
        double newBalance = getBalance(uuid);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(getUUID(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return depositPlayer(player.getUniqueId(), amount);
    }

    private EconomyResponse depositPlayer(UUID uuid, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Valor não pode ser negativo");
        }

        EconomyAPI.addMoney(uuid, amount, "VAULT_DEPOSIT");
        double newBalance = getBalance(uuid);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bancos não suportados");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return createBank(name, player.getName());
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bancos não suportados");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bancos não suportados");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bancos não suportados");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bancos não suportados");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bancos não suportados");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bancos não suportados");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return isBankOwner(name, player.getName());
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bancos não suportados");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return isBankMember(name, player.getName());
    }

    @Override
    public List<String> getBanks() {
        return null; // Sem bancos
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        // Conta é criada automaticamente no primeiro acesso
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return createPlayerAccount(player.getName());
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    /**
     * Obtém UUID do player por nome (offline)
     * Grug Brain: Busca direta via CoreAPI
     */
    private UUID getUUID(String playerName) {
        // Tentar player online primeiro
        org.bukkit.entity.Player onlinePlayer = org.bukkit.Bukkit.getPlayer(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        // Buscar no banco
        com.primeleague.core.models.PlayerData data = com.primeleague.core.CoreAPI.getPlayerByName(playerName);
        if (data != null) {
            return data.getUuid();
        }

        // Fallback: gerar UUID offline (UUID.nameUUIDFromBytes)
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
    }
}

