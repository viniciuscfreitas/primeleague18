package com.primeleague.x1.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.X1Stats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comando principal /x1
 * Grug Brain: Queries diretas, sem abstrações
 */
public class X1Command implements CommandExecutor {

    private final X1Plugin plugin;
    // Rate limit: UUID -> timestamp da última vez que entrou na queue
    private final Map<UUID, Long> queueCooldowns;
    private static final long QUEUE_COOLDOWN_MS = 2000; // 2 segundos

    public X1Command(X1Plugin plugin) {
        this.plugin = plugin;
        this.queueCooldowns = new ConcurrentHashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Menu principal
            showMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

		switch (subCommand) {
			case "fila":
                return handleQueue(player, args);
            case "sair":
                return handleLeave(player);
            case "estatisticas":
                return handleStats(player, args);
            case "top":
                return handleTop(player, args);
            case "admin":
                return handleAdmin(player, args);
            case "espectar":
                return handleSpectate(player, args);
			case "desafiar":
                return handleDuelChallenge(player, args);
            case "aceitar":
                return handleDuelAccept(player);
            case "negar":
                return handleDuelDeny(player);
            default:
                player.sendMessage(ChatColor.RED + "Uso: /x1 [fila|sair|estatisticas|top|duelo|aceitar|negar]");
                return true;
        }
    }

    /**
     * Menu principal
     */
    private void showMenu(Player player) {
		player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "X1 Duelos" + ChatColor.GOLD + " ===");
        player.sendMessage(ChatColor.YELLOW + "/x1 fila <kit> [ranqueado] - Entrar na fila");
        player.sendMessage(ChatColor.YELLOW + "/x1 sair - Sair da fila");
        player.sendMessage(ChatColor.YELLOW + "/x1 estatisticas [jogador] - Ver estatísticas");
        player.sendMessage(ChatColor.YELLOW + "/x1 top [vitorias|elo|sequencia] - Rankings");
        player.sendMessage(ChatColor.YELLOW + "/x1 espectar <jogador> - Espectar partida");
		player.sendMessage(ChatColor.YELLOW + "/x1 admin kit|arena ... - Administração");
		player.sendMessage(ChatColor.YELLOW + "/x1 desafiar <jogador> [kit] - Desafiar jogador");
        player.sendMessage(ChatColor.GRAY + "  Padrão: qualquer lugar, seus itens. Use [kit] para arena.");
        player.sendMessage(ChatColor.YELLOW + "/x1 aceitar - Aceitar desafio");
        player.sendMessage(ChatColor.YELLOW + "/x1 negar - Negar desafio");
    }

    /**
     * /x1 queue <kit> [ranked]
     */
    private boolean handleQueue(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /x1 fila <kit> [ranqueado]");
            return true;
        }

        // Rate limit check
        Long lastUse = queueCooldowns.get(player.getUniqueId());
        if (lastUse != null && System.currentTimeMillis() - lastUse < QUEUE_COOLDOWN_MS) {
            long remaining = (QUEUE_COOLDOWN_MS - (System.currentTimeMillis() - lastUse)) / 1000;
            player.sendMessage("§cAguarde " + remaining + " segundo" + (remaining != 1 ? "s" : "") + " antes de entrar na fila novamente.");
            return true;
        }
        queueCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        String kit = args[1];
        boolean ranked = args.length > 2 && args[2].equalsIgnoreCase("ranqueado");

        // Verificar se já está na queue
        if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            player.sendMessage(plugin.getConfig().getString("messages.error.already-in-queue", 
                "§cVocê já está na queue"));
            return true;
        }

        // Verificar se já está em match
        if (plugin.getMatchManager().isInMatch(player.getUniqueId())) {
            player.sendMessage(plugin.getConfig().getString("messages.error.already-in-match", 
                "§cVocê já está em um match"));
            return true;
        }

        // Verificar se kit existe
        if (plugin.getKitManager().getKit(kit) == null) {
            player.sendMessage(plugin.getConfig().getString("messages.error.invalid-kit", 
                "§cKit inválido: {kit}").replace("{kit}", kit));
            return true;
        }

        // Adicionar à queue
        if (plugin.getQueueManager().addToQueue(player.getUniqueId(), kit, ranked)) {
            String mode = ranked ? "Ranqueado" : "Normal";
            
            // Feedback melhorado com posição e tamanho da queue
            boolean showPosition = plugin.getConfig().getBoolean("ux.show-queue-position", true);
            String msg;
            
            if (showPosition) {
                int position = plugin.getQueueManager().getQueuePosition(player.getUniqueId());
                String queueKey = plugin.getQueueManager().getPlayerQueueKey(player.getUniqueId());
                int queueSize = queueKey != null ? plugin.getQueueManager().getQueueSize(queueKey) : 0;
                
                msg = plugin.getConfig().getString("messages.queue.joined", 
                    "§aVocê entrou na fila para {kit} ({mode})")
                    .replace("{kit}", kit)
                    .replace("{mode}", mode);
                player.sendMessage(msg);
                
                if (queueSize > 0) {
                    player.sendMessage("§7Posição: §e#" + position + " §7de §e" + queueSize);
                }
            } else {
                msg = plugin.getConfig().getString("messages.queue.joined", 
                "§aVocê entrou na fila para {kit} ({mode})")
                .replace("{kit}", kit)
                .replace("{mode}", mode);
            player.sendMessage(msg);
            }
        } else {
            player.sendMessage(plugin.getConfig().getString("messages.error.invalid-kit", 
                "§cErro ao entrar na queue. Tente novamente."));
        }

        return true;
    }

    /**
     * /x1 leave
     */
    private boolean handleLeave(Player player) {
        if (plugin.getQueueManager().removeFromQueue(player.getUniqueId())) {
            player.sendMessage(plugin.getConfig().getString("messages.queue.left", 
                "§cVocê saiu da fila"));
        } else {
            player.sendMessage(ChatColor.RED + "Você não está na fila.");
        }
        return true;
    }

    /**
     * /x1 stats [player]
     */
    private boolean handleStats(Player player, String[] args) {
        Player target = player;
        
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
            player.sendMessage(ChatColor.RED + "Jogador não encontrado: " + args[1]);
                return true;
            }
        }

        final Player finalTarget = target;
        final String targetName = target.getName();
        final CommandSender finalSender = player;

        // Buscar stats async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new StatsRunnable(finalTarget, targetName, finalSender));

        return true;
    }

    /**
     * /x1 top [wins|elo|streak]
     */
    private boolean handleTop(Player player, String[] args) {
        String tipo = args.length > 1 ? args[1].toLowerCase() : "vitorias";
        if (!tipo.equals("vitorias") && !tipo.equals("elo") && !tipo.equals("sequencia")) {
            player.sendMessage(ChatColor.RED + "Tipo inválido. Use: vitorias, elo ou sequencia");
            return true;
        }

        final String finalType = tipo;
        final CommandSender finalSender = player;

        // Buscar top async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new TopRunnable(finalType, finalSender));

        return true;
    }

    /**
     * Busca top players do banco
     */
    private List<String> getTop(String tipo, int limit) {
        List<String> top = new ArrayList<>();
        
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            PreparedStatement stmt;
            
            switch (tipo) {
                case "vitorias":
                    stmt = conn.prepareStatement(
                        "SELECT u.name, s.wins FROM x1_stats s " +
                        "JOIN users u ON s.player_uuid = u.uuid " +
                        "ORDER BY s.wins DESC LIMIT ?");
                    break;
                case "elo":
                    stmt = conn.prepareStatement(
                        "SELECT name, elo FROM users " +
                        "ORDER BY elo DESC LIMIT ?");
                    break;
                case "sequencia":
                    stmt = conn.prepareStatement(
                        "SELECT u.name, s.best_winstreak FROM x1_stats s " +
                        "JOIN users u ON s.player_uuid = u.uuid " +
                        "ORDER BY s.best_winstreak DESC LIMIT ?");
                    break;
                default:
                    return top;
            }
            
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String name = rs.getString("name");
                int value = rs.getInt(2); // Segunda coluna (wins, elo ou best_winstreak)
                // Formato: "name|value" para parsing depois
                top.add(name + "|" + value);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar top: " + e.getMessage());
            e.printStackTrace();
        }
        
        return top;
    }

    /**
     * Runnable para buscar stats async
     * Grug Brain: Classe interna para evitar problemas com classes anônimas no Maven Shade
     */
    private class StatsRunnable implements Runnable {
        private final Player target;
        private final String targetName;
        private final CommandSender sender;

        public StatsRunnable(Player target, String targetName, CommandSender sender) {
            this.target = target;
            this.targetName = targetName;
            this.sender = sender;
        }

        @Override
        public void run() {
            X1Stats stats = plugin.getStatsManager().getStats(target.getUniqueId());
            
            // Voltar à thread principal
            plugin.getServer().getScheduler().runTask(plugin, new StatsDisplayRunnable(stats, targetName, sender));
        }
    }

    /**
     * Runnable para buscar top async
     * Grug Brain: Classe interna para evitar problemas com classes anônimas no Maven Shade
     */
    private class TopRunnable implements Runnable {
        private final String type;
        private final CommandSender sender;

        public TopRunnable(String type, CommandSender sender) {
            this.type = type;
            this.sender = sender;
        }

        @Override
        public void run() {
            List<String> top = getTop(type, 10);
            
            // Voltar à thread principal
            plugin.getServer().getScheduler().runTask(plugin, new TopDisplayRunnable(top, type, sender));
        }
    }

    /**
     * Runnable para exibir stats na thread principal
     * Grug Brain: Classe interna nomeada
     */
    private class StatsDisplayRunnable implements Runnable {
        private final X1Stats stats;
        private final String targetName;
        private final CommandSender sender;

        public StatsDisplayRunnable(X1Stats stats, String targetName, CommandSender sender) {
            this.stats = stats;
            this.targetName = targetName;
            this.sender = sender;
        }

        @Override
        public void run() {
            sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + targetName + 
                ChatColor.GOLD + " - Estatísticas X1 ===");
            
            // Cores baseadas em W/L ratio
            double wlr = stats.getWLR();
            ChatColor wlrColor;
            if (wlr >= 2.0) {
                wlrColor = ChatColor.RED; // Excelente
            } else if (wlr >= 1.0) {
                wlrColor = ChatColor.YELLOW; // Bom
            } else if (wlr >= 0.5) {
                wlrColor = ChatColor.GREEN; // Ok
            } else {
                wlrColor = ChatColor.GRAY; // Ruim
            }
            
            sender.sendMessage(ChatColor.YELLOW + "Vitórias: " + ChatColor.WHITE + stats.getWins());
            sender.sendMessage(ChatColor.YELLOW + "Derrotas: " + ChatColor.WHITE + stats.getLosses());
            sender.sendMessage(ChatColor.YELLOW + "Taxa W/L: " + wlrColor + 
                String.format("%.2f", wlr));
            sender.sendMessage(ChatColor.YELLOW + "Sequência: " + ChatColor.WHITE + stats.getWinstreak());
            sender.sendMessage(ChatColor.YELLOW + "Melhor sequência: " + ChatColor.WHITE + stats.getBestWinstreak());
        }
    }

    /**
     * Runnable para exibir top na thread principal
     * Grug Brain: Classe interna nomeada
     */
    private class TopDisplayRunnable implements Runnable {
        private final List<String> top;
        private final String type;
        private final CommandSender sender;

        public TopDisplayRunnable(List<String> top, String type, CommandSender sender) {
            this.top = top;
            this.type = type;
            this.sender = sender;
        }

        @Override
        public void run() {
            sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + 
                "TOP " + type.toUpperCase() + ChatColor.GOLD + " ===");
            
            for (int i = 0; i < top.size(); i++) {
                String entry = top.get(i);
                String[] parts = entry.split("\\|");
                if (parts.length != 2) {
                    // Formato antigo (compatibilidade)
                    sender.sendMessage(ChatColor.YELLOW + String.valueOf(i + 1) + ". " + ChatColor.WHITE + entry);
                    continue;
                }
                
                String name = parts[0];
                int value = Integer.parseInt(parts[1]);
                
                // Destaque top 3
                ChatColor positionColor;
                ChatColor nameColor;
                if (i == 0) {
                    positionColor = ChatColor.GOLD; // Ouro
                    nameColor = ChatColor.YELLOW;
                } else if (i == 1) {
                    positionColor = ChatColor.GRAY; // Prata
                    nameColor = ChatColor.WHITE;
                } else if (i == 2) {
                    positionColor = ChatColor.DARK_GRAY; // Bronze
                    nameColor = ChatColor.GRAY;
                } else {
                    positionColor = ChatColor.YELLOW;
                    nameColor = ChatColor.WHITE;
                }
                
                sender.sendMessage(positionColor + "#" + (i + 1) + ". " + 
                    nameColor + name + ChatColor.GRAY + " - " + ChatColor.AQUA + value);
            }
        }
    }

    // --- Duelo via /x1 ---
	private boolean handleDuelChallenge(Player player, String[] args) {
        if (args.length < 2) {
			player.sendMessage(ChatColor.RED + "Uso: /x1 desafiar <jogador> [kit]");
            player.sendMessage(ChatColor.GRAY + "Padrão: qualquer lugar, seus itens. Use [kit] para arena.");
            return true;
        }
		// Reaproveitar lógica do DuelCommand (instância única no plugin)
		com.primeleague.x1.commands.DuelCommand duel = plugin.getDuelCommand();
        // Remover o subcomando e repassar parâmetros esperados pelo DuelCommand
        String[] forward = java.util.Arrays.copyOfRange(args, 1, args.length);
        return duel.handleChallenge(player, forward);
    }

    private boolean handleDuelAccept(Player player) {
		com.primeleague.x1.commands.DuelCommand duel = plugin.getDuelCommand();
        return duel.handleAccept(player);
    }

    private boolean handleDuelDeny(Player player) {
		com.primeleague.x1.commands.DuelCommand duel = plugin.getDuelCommand();
        return duel.handleDeny(player);
    }

    // --- Administração via /x1 admin ---
    private boolean handleAdmin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /x1 admin [kit|arena] ...");
            return true;
        }
        String area = args[1].toLowerCase();
        String[] forward = java.util.Arrays.copyOfRange(args, 2, args.length);

        switch (area) {
            case "kit": {
                com.primeleague.x1.commands.KitCommand kit = new com.primeleague.x1.commands.KitCommand(plugin);
                return kit.onCommand(player, null, "x1", forward);
            }
            case "arena": {
                com.primeleague.x1.commands.ArenaCommand arena = new com.primeleague.x1.commands.ArenaCommand(plugin);
                return arena.onCommand(player, null, "x1", forward);
            }
            default:
                player.sendMessage(ChatColor.RED + "Uso: /x1 admin [kit|arena] ...");
                return true;
        }
    }

    // --- Spectate via /x1 spectate <jogador> ---
    private boolean handleSpectate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /x1 spectate <jogador>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "Jogador não encontrado: " + args[1]);
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "Você já está no local do jogador.");
            return true;
        }
        player.teleport(target.getLocation());
        player.sendMessage(ChatColor.YELLOW + "Você está espectando " + ChatColor.WHITE + target.getName() + ChatColor.YELLOW + ".");
        return true;
    }
}

