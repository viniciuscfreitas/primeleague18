package com.primeleague.pvp152;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Comando principal do PvP152 - Config in-game em tempo real
 * Grug Brain: Tudo em um arquivo, lógica inline, sem abstrações
 */
public class PvP152Command implements CommandExecutor {

    private final PvP152Plugin plugin;

    public PvP152Command(PvP152Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("pvp152.admin")) {
            String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[PvP]");
            boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);
            if (mostrarBranding) {
                sender.sendMessage(prefixo + " §cVocê não tem permissão!");
            } else {
                sender.sendMessage(ChatColor.RED + "✗ Você não tem permissão!");
            }
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        // Help
        if (subCmd.equals("help") || subCmd.equals("?")) {
            sendHelp(sender);
            return true;
        }

        // Ver tudo
        if (subCmd.equals("ver")) {
            sendVer(sender);
            return true;
        }

        // Reset
        if (subCmd.equals("reset")) {
            handleReset(sender, args);
            return true;
        }

        // Comandos de alteração
        if (args.length < 2) {
            String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[PvP]");
            boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);
            if (mostrarBranding) {
                sender.sendMessage(prefixo + " §cUso: /delay " + subCmd + " <valor>");
                sender.sendMessage(prefixo + " §eDigite /delay help para ver opções");
            } else {
                sender.sendMessage(ChatColor.RED + "✗ Uso: /delay " + subCmd + " <valor>");
                sender.sendMessage(ChatColor.YELLOW + "Digite /delay help para ver opções");
            }
            return true;
        }

        // Reach
        if (subCmd.equals("reach")) {
            return setValue(sender, "reach.max-distance", args[1], 2.5, 4.5, "Reach");
        }

        // Hitbox (tolerância)
        if (subCmd.equals("hitbox")) {
            return setValue(sender, "reach.hitbox-tolerance", args[1], 1.0, 1.5, "Tolerância de Hitbox");
        }

        // Espadas
        if (subCmd.equals("espada")) {
            return handleEspada(sender, args);
        }

        // Afiar (sharpness)
        if (subCmd.equals("afiar")) {
            return setValue(sender, "damage.enchantments.sharpness-per-level", args[1], 0.5, 2.0, "Afiação por Nível");
        }

        // Knockback
        if (subCmd.equals("knockback")) {
            return handleKnockback(sender, args);
        }

        // Bloqueio
        if (subCmd.equals("bloqueio")) {
            return handleBloqueio(sender, args);
        }

        // Crítico
        if (subCmd.equals("critico")) {
            return handleCritico(sender, args);
        }

        // I-Frame
        if (subCmd.equals("iframe")) {
            return setIntValue(sender, "combat.iframe-sweetspot", args[1], 0, 10, "I-Frames");
        }

        // Multiplicador Global de Dano
        if (subCmd.equals("multiplicador")) {
            return setValue(sender, "damage.global-multiplier", args[1], 0.5, 1.0, "Multiplicador Global de Dano");
        }

        // Debug
        if (subCmd.equals("debug")) {
            return handleDebug(sender, args);
        }

        String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[PvP]");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);
        if (mostrarBranding) {
            sender.sendMessage(prefixo + " §cComando não encontrado!");
            sender.sendMessage(prefixo + " §eDigite /delay help para ver opções");
        } else {
            sender.sendMessage(ChatColor.RED + "✗ Comando não encontrado!");
            sender.sendMessage(ChatColor.YELLOW + "Digite /delay help para ver opções");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        String brandingNome = plugin.getConfig().getString("branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("branding.cor", "§b");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);

        if (mostrarBranding) {
            sender.sendMessage(brandingCor + "§l=== " + brandingNome + " - COMANDOS ===");
        } else {
            sender.sendMessage(ChatColor.GOLD + "=== DELAY - COMANDOS ===");
        }
        sender.sendMessage(ChatColor.YELLOW + "Reach: " + ChatColor.WHITE + "/delay reach <valor>");
        sender.sendMessage(ChatColor.YELLOW + "Hitbox: " + ChatColor.WHITE + "/delay hitbox <valor>");
        sender.sendMessage(ChatColor.YELLOW + "Espada: " + ChatColor.WHITE + "/delay espada <tipo> <valor>");
        sender.sendMessage(ChatColor.GRAY + "  Tipos: madeira, pedra, ferro, ouro, diamante, todas");
        sender.sendMessage(ChatColor.YELLOW + "Afiar: " + ChatColor.WHITE + "/delay afiar <valor>");
        sender.sendMessage(ChatColor.YELLOW + "Multiplicador: " + ChatColor.WHITE + "/delay multiplicador <valor>");
        sender.sendMessage(ChatColor.YELLOW + "Knockback: " + ChatColor.WHITE + "/delay knockback <tipo> <valor>");
        sender.sendMessage(ChatColor.GRAY + "  Tipos: horizontal, vertical, encantamento1, encantamento2");
        sender.sendMessage(ChatColor.YELLOW + "Bloqueio: " + ChatColor.WHITE + "/delay bloqueio <tipo> <valor>");
        sender.sendMessage(ChatColor.GRAY + "  Tipos: reducao, angulo, alcance");
        sender.sendMessage(ChatColor.YELLOW + "Crítico: " + ChatColor.WHITE + "/delay critico <tipo> <valor>");
        sender.sendMessage(ChatColor.GRAY + "  Tipos: multiplicador, altura");
        sender.sendMessage(ChatColor.YELLOW + "I-Frame: " + ChatColor.WHITE + "/delay iframe <valor>");
        sender.sendMessage(ChatColor.YELLOW + "Debug: " + ChatColor.WHITE + "/delay debug <tipo> <on/off>");
        sender.sendMessage(ChatColor.YELLOW + "Ver tudo: " + ChatColor.WHITE + "/delay ver");
        sender.sendMessage(ChatColor.YELLOW + "Reset: " + ChatColor.WHITE + "/delay reset [grupo]");
    }

    private void sendVer(CommandSender sender) {
        FileConfiguration config = plugin.getConfig();
        String brandingNome = plugin.getConfig().getString("branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("branding.cor", "§b");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);

        if (mostrarBranding) {
            sender.sendMessage(brandingCor + "§l=== " + brandingNome + " - CONFIGURAÇÕES ===");
        } else {
            sender.sendMessage(ChatColor.GOLD + "=== CONFIGURAÇÕES DELAY ===");
        }

        // Reach
        sender.sendMessage(ChatColor.YELLOW + "Reach: " + ChatColor.WHITE + config.getDouble("reach.max-distance", 3.0));
        sender.sendMessage(ChatColor.YELLOW + "Tolerância Hitbox: " + ChatColor.WHITE + config.getDouble("reach.hitbox-tolerance", 1.05));

        // Dano Espadas
        sender.sendMessage(ChatColor.YELLOW + "Dano Madeira: " + ChatColor.WHITE + config.getDouble("damage.swords.wooden", 4.0));
        sender.sendMessage(ChatColor.YELLOW + "Dano Pedra: " + ChatColor.WHITE + config.getDouble("damage.swords.stone", 5.0));
        sender.sendMessage(ChatColor.YELLOW + "Dano Ferro: " + ChatColor.WHITE + config.getDouble("damage.swords.iron", 6.0));
        sender.sendMessage(ChatColor.YELLOW + "Dano Ouro: " + ChatColor.WHITE + config.getDouble("damage.swords.gold", 4.0));
        sender.sendMessage(ChatColor.YELLOW + "Dano Diamante: " + ChatColor.WHITE + config.getDouble("damage.swords.diamond", 7.0));
        sender.sendMessage(ChatColor.YELLOW + "Afiação por Nível: " + ChatColor.WHITE + config.getDouble("damage.enchantments.sharpness-per-level", 1.25));
        double globalMultiplier = config.getDouble("damage.global-multiplier", 0.75);
        sender.sendMessage(ChatColor.YELLOW + "Multiplicador Global: " + ChatColor.WHITE + globalMultiplier + " (" + String.format("%.0f%%", globalMultiplier * 100) + ")");

        // Knockback
        sender.sendMessage(ChatColor.YELLOW + "KB Horizontal: " + ChatColor.WHITE + config.getDouble("knockback.horizontal-boost", 1.2));
        sender.sendMessage(ChatColor.YELLOW + "KB Vertical: " + ChatColor.WHITE + config.getDouble("knockback.min-vertical", 0.4));
        sender.sendMessage(ChatColor.YELLOW + "KB Encantamento I: " + ChatColor.WHITE + config.getDouble("knockback.enchant-level-1-multiplier", 1.05));
        sender.sendMessage(ChatColor.YELLOW + "KB Encantamento II: " + ChatColor.WHITE + config.getDouble("knockback.enchant-level-2-multiplier", 1.90));

        // Bloqueio
        double blockReduction = config.getDouble("blocking.damage-reduction", 0.5);
        sender.sendMessage(ChatColor.YELLOW + "Bloqueio Redução: " + ChatColor.WHITE + String.format("%.0f%%", blockReduction * 100));
        sender.sendMessage(ChatColor.YELLOW + "Bloqueio Ângulo: " + ChatColor.WHITE + config.getDouble("blocking.angle-degrees", 75.0) + "°");
        sender.sendMessage(ChatColor.YELLOW + "Bloqueio Alcance: " + ChatColor.WHITE + config.getDouble("blocking.reach-reduction", 0.7));

        // Crítico
        sender.sendMessage(ChatColor.YELLOW + "Crítico Multiplicador: " + ChatColor.WHITE + config.getDouble("combat.critical-hits.damage-multiplier", 1.5));
        sender.sendMessage(ChatColor.YELLOW + "Crítico Altura: " + ChatColor.WHITE + config.getDouble("combat.critical-hits.min-height-above-ground", 0.01));

        // I-Frame
        sender.sendMessage(ChatColor.YELLOW + "I-Frames: " + ChatColor.WHITE + config.getInt("combat.iframe-sweetspot", 3));

        // Debug
        sender.sendMessage(ChatColor.YELLOW + "Debug Dano: " + ChatColor.WHITE + (config.getBoolean("debug.log-damage", false) ? "ON" : "OFF"));
        sender.sendMessage(ChatColor.YELLOW + "Debug Knockback: " + ChatColor.WHITE + (config.getBoolean("debug.log-knockback", false) ? "ON" : "OFF"));

        // Dica rápida
        sender.sendMessage(ChatColor.GRAY + "---");
        sender.sendMessage(ChatColor.GRAY + "Dica: Pra mais push no teto, use /delay knockback vertical 0.45");
    }

    private boolean handleEspada(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "✗ Uso: /delay espada <tipo> <valor>");
            sender.sendMessage(ChatColor.GRAY + "Tipos: madeira, pedra, ferro, ouro, diamante, todas");
            return true;
        }

        String tipo = args[1].toLowerCase();
        String valorStr = args[2];

        // Batch: todas
        if (tipo.equals("todas")) {
            double valor = parseDouble(sender, valorStr, 3.0, 8.0);
            if (valor < 0) return true;

            double oldWooden = plugin.getConfig().getDouble("damage.swords.wooden", 4.0);
            double oldStone = plugin.getConfig().getDouble("damage.swords.stone", 5.0);
            double oldIron = plugin.getConfig().getDouble("damage.swords.iron", 6.0);
            double oldGold = plugin.getConfig().getDouble("damage.swords.gold", 4.0);
            double oldDiamond = plugin.getConfig().getDouble("damage.swords.diamond", 7.0);

            plugin.getConfig().set("damage.swords.wooden", valor);
            plugin.getConfig().set("damage.swords.stone", valor);
            plugin.getConfig().set("damage.swords.iron", valor);
            plugin.getConfig().set("damage.swords.gold", valor);
            plugin.getConfig().set("damage.swords.diamond", valor);
            plugin.saveConfig();
            plugin.getCombatListener().reloadFromMemory(); // Recarregar da memória (já atualizado)

            String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[PvP]");
            boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);
            if (mostrarBranding) {
                sender.sendMessage(prefixo + " §aTodas as espadas alteradas: " + valor);
            } else {
                sender.sendMessage(ChatColor.GREEN + "✓ Todas as espadas alteradas: " + valor);
            }
            sender.sendMessage(ChatColor.GRAY + "  Madeira: " + oldWooden + " → " + valor);
            sender.sendMessage(ChatColor.GRAY + "  Pedra: " + oldStone + " → " + valor);
            sender.sendMessage(ChatColor.GRAY + "  Ferro: " + oldIron + " → " + valor);
            sender.sendMessage(ChatColor.GRAY + "  Ouro: " + oldGold + " → " + valor);
            sender.sendMessage(ChatColor.GRAY + "  Diamante: " + oldDiamond + " → " + valor);
            return true;
        }

        // Individual
        String path = getEspadaPath(tipo);
        if (path == null) {
            sender.sendMessage(ChatColor.RED + "✗ Tipo inválido! Use: madeira, pedra, ferro, ouro, diamante, todas");
            return true;
        }

        return setValue(sender, path, valorStr, 3.0, 8.0, "Dano " + capitalize(tipo));
    }

    private String getEspadaPath(String tipo) {
        switch (tipo) {
            case "madeira": return "damage.swords.wooden";
            case "pedra": return "damage.swords.stone";
            case "ferro": return "damage.swords.iron";
            case "ouro": return "damage.swords.gold";
            case "diamante": return "damage.swords.diamond";
            default: return null;
        }
    }

    private boolean handleKnockback(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "✗ Uso: /delay knockback <tipo> <valor>");
            sender.sendMessage(ChatColor.GRAY + "Tipos: horizontal, vertical, encantamento1, encantamento2");
            return true;
        }

        String tipo = args[1].toLowerCase();
        String valorStr = args[2];

        String path;
        double min, max;
        String nome;

        switch (tipo) {
            case "horizontal":
                path = "knockback.horizontal-boost";
                min = 0.5;
                max = 2.0;
                nome = "Knockback Horizontal";
                break;
            case "vertical":
                path = "knockback.min-vertical";
                min = 0.1;
                max = 1.0;
                nome = "Knockback Vertical";
                break;
            case "encantamento1":
                path = "knockback.enchant-level-1-multiplier";
                min = 1.0;
                max = 2.0;
                nome = "KB Encantamento I";
                break;
            case "encantamento2":
                path = "knockback.enchant-level-2-multiplier";
                min = 1.0;
                max = 3.0;
                nome = "KB Encantamento II";
                break;
            default:
                sender.sendMessage(ChatColor.RED + "✗ Tipo inválido! Use: horizontal, vertical, encantamento1, encantamento2");
                return true;
        }

        return setValue(sender, path, valorStr, min, max, nome);
    }

    private boolean handleBloqueio(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "✗ Uso: /delay bloqueio <tipo> <valor>");
            sender.sendMessage(ChatColor.GRAY + "Tipos: reducao, angulo, alcance");
            return true;
        }

        String tipo = args[1].toLowerCase();
        String valorStr = args[2];

        String path;
        double min, max;
        String nome;

        switch (tipo) {
            case "reducao":
                path = "blocking.damage-reduction";
                min = 0.0;
                max = 1.0;
                nome = "Bloqueio Redução";
                double valor = parseDouble(sender, valorStr, min, max);
                if (valor < 0) return true;
                double oldVal = plugin.getConfig().getDouble(path, 0.5);
                plugin.getConfig().set(path, valor);
                plugin.saveConfig();
                plugin.getCombatListener().reloadFromMemory(); // Recarregar da memória (já atualizado)
                String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[PvP]");
                boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);
                if (mostrarBranding) {
                    sender.sendMessage(prefixo + " §a" + nome + " alterado: " + String.format("%.0f%%", oldVal * 100) + " → " + String.format("%.0f%%", valor * 100));
                } else {
                    sender.sendMessage(ChatColor.GREEN + "✓ " + nome + " alterado: " + String.format("%.0f%%", oldVal * 100) + " → " + String.format("%.0f%%", valor * 100));
                }
                return true;
            case "angulo":
                path = "blocking.angle-degrees";
                min = 30.0;
                max = 180.0;
                nome = "Bloqueio Ângulo";
                break;
            case "alcance":
                path = "blocking.reach-reduction";
                min = 0.1;
                max = 1.0;
                nome = "Bloqueio Alcance";
                break;
            default:
                sender.sendMessage(ChatColor.RED + "✗ Tipo inválido! Use: reducao, angulo, alcance");
                return true;
        }

        return setValue(sender, path, valorStr, min, max, nome);
    }

    private boolean handleCritico(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "✗ Uso: /delay critico <tipo> <valor>");
            sender.sendMessage(ChatColor.GRAY + "Tipos: multiplicador, altura");
            return true;
        }

        String tipo = args[1].toLowerCase();
        String valorStr = args[2];

        String path;
        double min, max;
        String nome;

        switch (tipo) {
            case "multiplicador":
                path = "combat.critical-hits.damage-multiplier";
                min = 1.0;
                max = 3.0;
                nome = "Crítico Multiplicador";
                break;
            case "altura":
                path = "combat.critical-hits.min-height-above-ground";
                min = 0.0;
                max = 1.0;
                nome = "Crítico Altura";
                break;
            default:
                sender.sendMessage(ChatColor.RED + "✗ Tipo inválido! Use: multiplicador, altura");
                return true;
        }

        return setValue(sender, path, valorStr, min, max, nome);
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "✗ Uso: /delay debug <tipo> <on/off>");
            sender.sendMessage(ChatColor.GRAY + "Tipos: dano, knockback");
            return true;
        }

        String tipo = args[1].toLowerCase();
        String estado = args[2].toLowerCase();

        boolean valor;
        if (estado.equals("on") || estado.equals("true") || estado.equals("1")) {
            valor = true;
        } else if (estado.equals("off") || estado.equals("false") || estado.equals("0")) {
            valor = false;
        } else {
            sender.sendMessage(ChatColor.RED + "✗ Use: on ou off");
            return true;
        }

        String path;
        String nome;

        switch (tipo) {
            case "dano":
                path = "debug.log-damage";
                nome = "Debug Dano";
                break;
            case "knockback":
                path = "debug.log-knockback";
                nome = "Debug Knockback";
                break;
            default:
                sender.sendMessage(ChatColor.RED + "✗ Tipo inválido! Use: dano, knockback");
                return true;
        }

        boolean oldVal = plugin.getConfig().getBoolean(path, false);
        plugin.getConfig().set(path, valor);
        plugin.saveConfig();
        plugin.getCombatListener().reloadFromMemory(); // Recarregar da memória (já atualizado)

        String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[PvP]");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);
        if (mostrarBranding) {
            sender.sendMessage(prefixo + " §a" + nome + " alterado: " + (oldVal ? "ON" : "OFF") + " → " + (valor ? "ON" : "OFF"));
        } else {
            sender.sendMessage(ChatColor.GREEN + "✓ " + nome + " alterado: " + (oldVal ? "ON" : "OFF") + " → " + (valor ? "ON" : "OFF"));
        }
        return true;
    }

    private boolean setValue(CommandSender sender, String path, String valorStr, double min, double max, String nome) {
        double valor = parseDouble(sender, valorStr, min, max);
        if (valor < 0) return true;

        double oldVal = plugin.getConfig().getDouble(path);
        plugin.getConfig().set(path, valor);
        plugin.saveConfig();
        plugin.getCombatListener().reloadFromMemory(); // Recarregar da memória (já atualizado)

        String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[PvP]");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);

        String clampMsg = "";
        if (valor != Double.parseDouble(valorStr)) {
            clampMsg = ChatColor.YELLOW + " (valor clampado - range: " + min + "-" + max + ")";
        }

        if (mostrarBranding) {
            sender.sendMessage(prefixo + " §a" + nome + " alterado: " + oldVal + " → " + valor + clampMsg);
        } else {
            sender.sendMessage(ChatColor.GREEN + "✓ " + nome + " alterado: " + oldVal + " → " + valor + clampMsg);
        }
        return true;
    }

    private boolean setIntValue(CommandSender sender, String path, String valorStr, int min, int max, String nome) {
        int valor = parseInt(sender, valorStr, min, max);
        if (valor < 0) return true;

        int oldVal = plugin.getConfig().getInt(path);
        plugin.getConfig().set(path, valor);
        plugin.saveConfig();
        plugin.getCombatListener().reloadFromMemory(); // Recarregar da memória (já atualizado)

        String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[PvP]");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);

        String clampMsg = "";
        if (valor != Integer.parseInt(valorStr)) {
            clampMsg = ChatColor.YELLOW + " (valor clampado - range: " + min + "-" + max + ")";
        }

        if (mostrarBranding) {
            sender.sendMessage(prefixo + " §a" + nome + " alterado: " + oldVal + " → " + valor + clampMsg);
        } else {
            sender.sendMessage(ChatColor.GREEN + "✓ " + nome + " alterado: " + oldVal + " → " + valor + clampMsg);
        }
        return true;
    }

    private double parseDouble(CommandSender sender, String str, double min, double max) {
        try {
            double valor = Double.parseDouble(str);
            if (valor < min) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ Valor muito baixo! Ajustado para " + min);
                return min;
            }
            if (valor > max) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ Valor muito alto! Ajustado para " + max);
                return max;
            }
            return valor;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "✗ Valor inválido! Use um número (ex: 3.5)");
            return -1;
        }
    }

    private int parseInt(CommandSender sender, String str, int min, int max) {
        try {
            int valor = Integer.parseInt(str);
            if (valor < min) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ Valor muito baixo! Ajustado para " + min);
                return min;
            }
            if (valor > max) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ Valor muito alto! Ajustado para " + max);
                return max;
            }
            return valor;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "✗ Valor inválido! Use um número inteiro (ex: 3)");
            return -1;
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Reset tudo
            plugin.reloadConfig();
            plugin.getCombatListener().reloadConfig();
            String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[PvP]");
            boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);
            if (mostrarBranding) {
                sender.sendMessage(prefixo + " §aTodas as configurações resetadas ao padrão!");
            } else {
                sender.sendMessage(ChatColor.GREEN + "✓ Todas as configurações resetadas ao padrão!");
            }
            return;
        }

        // Reset seletivo
        String grupo = args[1].toLowerCase();

        switch (grupo) {
            case "reach":
                resetGroup(sender, new String[]{"reach.max-distance", "reach.hitbox-tolerance"}, "Reach");
                break;
            case "dano":
            case "espada":
                resetGroup(sender, new String[]{
                    "damage.swords.wooden", "damage.swords.stone", "damage.swords.iron",
                    "damage.swords.gold", "damage.swords.diamond", "damage.enchantments.sharpness-per-level",
                    "damage.global-multiplier"
                }, "Dano");
                break;
            case "knockback":
            case "kb":
                resetGroup(sender, new String[]{
                    "knockback.horizontal-boost", "knockback.min-vertical",
                    "knockback.enchant-level-1-multiplier", "knockback.enchant-level-2-multiplier"
                }, "Knockback");
                break;
            case "bloqueio":
                resetGroup(sender, new String[]{
                    "blocking.damage-reduction", "blocking.angle-degrees", "blocking.reach-reduction"
                }, "Bloqueio");
                break;
            case "critico":
                resetGroup(sender, new String[]{
                    "combat.critical-hits.damage-multiplier", "combat.critical-hits.min-height-above-ground"
                }, "Crítico");
                break;
            case "combat":
                resetGroup(sender, new String[]{"combat.iframe-sweetspot"}, "Combate");
                break;
            case "debug":
                resetGroup(sender, new String[]{"debug.log-damage", "debug.log-knockback"}, "Debug");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "✗ Grupo inválido! Use: reach, dano, knockback, bloqueio, critico, combat, debug");
                sender.sendMessage(ChatColor.YELLOW + "Ou use /delay reset (sem grupo) para resetar tudo");
                return;
        }
    }

    private void resetGroup(CommandSender sender, String[] paths, String nomeGrupo) {
        for (String path : paths) {
            plugin.getConfig().set(path, null);
        }
        plugin.saveConfig();
        plugin.getCombatListener().reloadConfig();
        String prefixo = plugin.getConfig().getString("branding.prefixo", "§b[PvP]");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.mostrar-branding-comandos", true);
        if (mostrarBranding) {
            sender.sendMessage(prefixo + " §a" + nomeGrupo + " resetado ao padrão!");
        } else {
            sender.sendMessage(ChatColor.GREEN + "✓ " + nomeGrupo + " resetado ao padrão!");
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

