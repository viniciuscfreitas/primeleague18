package com.primeleague.factions.listener;

import com.primeleague.clans.models.ClanData;
import com.primeleague.factions.PrimeFactions;
import com.primeleague.factions.manager.UpgradeManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Listener para GUI de Upgrades
 * Grug Brain: Lógica inline, validações diretas
 */
public class UpgradeGUIListener implements Listener {

    private final PrimeFactions plugin;

    public UpgradeGUIListener(PrimeFactions plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // Verificar se é GUI de upgrades
        if (!title.equals(ChatColor.DARK_PURPLE + "Upgrades do Clã")) {
            return;
        }

        event.setCancelled(true); // Sempre cancelar (GUI read-only com compras)

        if (event.getClickedInventory() == null || event.getClickedInventory().getType() != InventoryType.CHEST) {
            return;
        }

        int slot = event.getSlot();

        // Slots dos upgrades: 10, 12, 14, 16
        UpgradeManager.UpgradeType type = null;
        if (slot == 10) {
            type = UpgradeManager.UpgradeType.SPAWNER_RATE;
        } else if (slot == 12) {
            type = UpgradeManager.UpgradeType.CROP_GROWTH;
        } else if (slot == 14) {
            type = UpgradeManager.UpgradeType.EXP_BOOST;
        } else if (slot == 16) {
            type = UpgradeManager.UpgradeType.EXTRA_SHIELD;
        }

        if (type == null) {
            return; // Slot não é upgrade
        }

        // Verificar se player tem clã
        ClanData clan = plugin.getClansPlugin().getClansManager().getClanByMember(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Você precisa de um clã.");
            player.closeInventory();
            return;
        }

        // Verificar permissões
        String role = plugin.getClansPlugin().getClansManager().getMemberRole(clan.getId(), player.getUniqueId());
        if (role == null || (!role.equals("LEADER") && !role.equals("OFFICER"))) {
            player.sendMessage(ChatColor.RED + "Apenas líderes e oficiais podem comprar upgrades!");
            return;
        }

        // Tentar comprar upgrade (async para não bloquear main thread)
        final int finalClanId = clan.getId();
        final UpgradeManager.UpgradeType finalType = type;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Verificar saldo e comprar (async)
            boolean success = plugin.getUpgradeManager().purchaseUpgrade(finalClanId, finalType);

            // Voltar para main thread para atualizar GUI
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(ChatColor.GREEN + "Upgrade comprado com sucesso!");
                    // Atualizar GUI
                    org.bukkit.inventory.Inventory newGUI = plugin.getUpgradeManager().createUpgradeGUI(player, finalClanId);
                    player.openInventory(newGUI);
                } else {
                    UpgradeManager.UpgradeData data = plugin.getUpgradeManager().getUpgrades(finalClanId);
                    int currentLevel = data.getLevel(finalType);

                    if (currentLevel >= finalType.getMaxLevel()) {
                        player.sendMessage(ChatColor.RED + "Este upgrade já está no nível máximo!");
                    } else {
                        long cost = finalType.getCostForLevel(currentLevel);
                        long balance = plugin.getClansPlugin().getClansManager().getClanBalance(finalClanId);
                        player.sendMessage(ChatColor.RED + "Saldo insuficiente! Custo: $" + String.format("%.2f", cost / 100.0) +
                            " | Saldo: $" + String.format("%.2f", balance / 100.0));
                    }
                }
            });
        });
    }
}

