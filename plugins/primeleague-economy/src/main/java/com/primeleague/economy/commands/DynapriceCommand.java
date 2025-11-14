package com.primeleague.economy.commands;

import com.primeleague.economy.EconomyPlugin;
import com.primeleague.economy.utils.DynamicPricer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * Comando /dynaprice - Info de dynamic pricing (admin)
 * CORREÇÃO #8: Mostra preços atuais (não só EMAs)
 * CORREÇÃO #10: disable salva config
 */
public class DynapriceCommand implements CommandExecutor {

    private final EconomyPlugin plugin;

    public DynapriceCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("economy.admin")) {
            sender.sendMessage(ChatColor.RED + "Sem permissão");
            return true;
        }

        DynamicPricer pricer = plugin.getDynamicPricer();
        if (pricer == null) {
            sender.sendMessage(ChatColor.RED + "DynamicPricer desabilitado");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            // CORREÇÃO #8: Mostrar preços atuais (não só EMAs)
            sender.sendMessage(ChatColor.GOLD + "=== Dynamic Pricing Info ===");

            double globalMult = pricer.getGlobalMultiplier();
            long lastUpdate = pricer.getLastUpdateTime();
            String lastUpdateStr = lastUpdate > 0
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(lastUpdate))
                : "Nunca";

            sender.sendMessage(ChatColor.YELLOW + "Multiplicador global (média): " + ChatColor.WHITE + String.format("%.3f", globalMult));
            sender.sendMessage(ChatColor.YELLOW + "Última atualização: " + ChatColor.WHITE + lastUpdateStr);
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GREEN + "Preços atuais:");

            Set<String> items = pricer.getItemNames();
            for (String item : items) {
                double base = pricer.getBasePrice(item);
                double current = pricer.getPrice(item);
                double ema = pricer.getEma(item);
                double mult = base > 0 ? current / base : 1.0;

                sender.sendMessage(String.format(
                    ChatColor.WHITE + "  %s: " + ChatColor.YELLOW + "$%.2f" +
                    ChatColor.GRAY + " (base: $%.2f, mult: %.2fx, EMA: %.3f)",
                    item, current, base, mult, ema
                ));
            }

        } else if (args.length > 0 && args[0].equalsIgnoreCase("disable")) {
            // CORREÇÃO #10: Comando para desabilitar e salvar config
            plugin.getConfig().set("economy.precos-dinamicos.habilitado", false);
            plugin.saveConfig();

            // Shutdown DynamicPricer (salva estado antes de desabilitar)
            if (pricer != null) {
                pricer.shutdown();
            }

            sender.sendMessage(ChatColor.GREEN + "DynamicPricer desabilitado e config salvo!");
            sender.sendMessage(ChatColor.YELLOW + "Use /reload ou reinicie o servidor para aplicar completamente.");

        } else {
            // Help
            sender.sendMessage(ChatColor.GOLD + "=== Dynamic Pricing ===");
            sender.sendMessage(ChatColor.YELLOW + "/dynaprice info" + ChatColor.WHITE + " - Mostra informações atuais");
            sender.sendMessage(ChatColor.YELLOW + "/dynaprice disable" + ChatColor.WHITE + " - Desabilita e salva config");
        }

        return true;
    }
}

