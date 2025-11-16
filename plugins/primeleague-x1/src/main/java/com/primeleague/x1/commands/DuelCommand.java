package com.primeleague.x1.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.x1.X1Plugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comando /duel - Desafiar players
 * Grug Brain: Sistema simples de desafios
 */
public class DuelCommand implements CommandExecutor {

    private final X1Plugin plugin;
    // Pendentes: UUID do desafiado -> UUID do desafiante + kit (thread-safe)
    private final Map<UUID, DuelChallenge> pendingChallenges;

    public DuelCommand(X1Plugin plugin) {
        this.plugin = plugin;
        this.pendingChallenges = new ConcurrentHashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

		if (args.length == 0) {
			player.sendMessage(ChatColor.RED + "Uso: /x1 desafiar <jogador> [kit] | /x1 aceitar | /x1 negar");
            return true;
        }

        String subCommand = args[0].toLowerCase();

		if (subCommand.equals("accept")) {
            return handleAccept(player);
		} else if (subCommand.equals("deny")) {
            return handleDeny(player);
        } else {
            // Desafiar player
            return handleChallenge(player, args);
        }
    }

    /**
     * /duel <player> [kit]
     */
    public boolean handleChallenge(Player challenger, String[] args) {
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

		if (target == null || !target.isOnline()) {
			challenger.sendMessage(plugin.getConfig().getString("messages.duel.not-found", 
				"§cJogador não encontrado ou offline"));
            return true;
        }

        if (target.equals(challenger)) {
            challenger.sendMessage(ChatColor.RED + "Você não pode se desafiar!");
            return true;
        }

        // Verificar se já está em match ou queue
        if (plugin.getMatchManager().isInMatch(challenger.getUniqueId()) || 
            plugin.getQueueManager().isInQueue(challenger.getUniqueId())) {
            challenger.sendMessage(plugin.getConfig().getString("messages.error.already-in-match", 
                "§cVocê já está em um match ou na queue"));
            return true;
        }

        if (plugin.getMatchManager().isInMatch(target.getUniqueId()) || 
            plugin.getQueueManager().isInQueue(target.getUniqueId())) {
            challenger.sendMessage(ChatColor.RED + target.getName() + " já está em um match ou na queue");
            return true;
        }

        String kit = args.length > 1 ? args[1] : plugin.getConfig().getString("kits.default", "nodebuff");

		// Criar/atualizar desafio
        DuelChallenge challenge = new DuelChallenge(challenger.getUniqueId(), kit);
        pendingChallenges.put(target.getUniqueId(), challenge);

        // Mensagens
        String msg1 = plugin.getConfig().getString("messages.duel.challenge-sent", 
            "§aDesafio enviado para {player}").replace("{player}", target.getName());
        challenger.sendMessage(msg1);

		String msg2 = plugin.getConfig().getString("messages.duel.challenge-received", 
			"§a{player} te desafiou! Use /x1 aceitar ou /x1 negar")
			.replace("{player}", challenger.getName())
			.replace("/duel accept", "/x1 aceitar")
			.replace("/duel deny", "/x1 negar");
        target.sendMessage(msg2);

        // Remover desafio após 30 segundos
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                pendingChallenges.remove(target.getUniqueId());
            }
        }, 600L); // 30 segundos

        return true;
    }

    /**
     * /duel accept
     */
    public boolean handleAccept(Player player) {
        DuelChallenge challenge = pendingChallenges.remove(player.getUniqueId());
        
        if (challenge == null) {
            player.sendMessage(ChatColor.RED + "Você não tem desafios pendentes.");
            return true;
        }

        Player challenger = Bukkit.getPlayer(challenge.getChallengerUuid());
        if (challenger == null || !challenger.isOnline()) {
            player.sendMessage(ChatColor.RED + "O desafiante não está mais online.");
            return true;
        }

        // Verificar se ainda estão disponíveis
        if (plugin.getMatchManager().isInMatch(challenger.getUniqueId()) || 
            plugin.getMatchManager().isInMatch(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Um dos players já está em um match.");
            return true;
        }

        // Criar match direto (sem queue)
        // Por enquanto, usar queue system com matchmaking imediato
        plugin.getQueueManager().addToQueue(challenger.getUniqueId(), challenge.getKit(), false);
        plugin.getQueueManager().addToQueue(player.getUniqueId(), challenge.getKit(), false);

        String msg = plugin.getConfig().getString("messages.duel.accepted", 
            "§a{player} aceitou seu desafio!").replace("{player}", player.getName());
        challenger.sendMessage(msg);

        return true;
    }

    /**
     * /duel deny
     */
    public boolean handleDeny(Player player) {
        DuelChallenge challenge = pendingChallenges.remove(player.getUniqueId());
        
        if (challenge == null) {
            player.sendMessage(ChatColor.RED + "Você não tem desafios pendentes.");
            return true;
        }

        Player challenger = Bukkit.getPlayer(challenge.getChallengerUuid());
        if (challenger != null && challenger.isOnline()) {
            String msg = plugin.getConfig().getString("messages.duel.denied", 
                "§c{player} negou seu desafio").replace("{player}", player.getName());
            challenger.sendMessage(msg);
        }

        player.sendMessage(ChatColor.YELLOW + "Desafio negado.");
        return true;
    }

    /**
     * Classe interna para desafios pendentes
     */
    private static class DuelChallenge {
        private final UUID challengerUuid;
        private final String kit;

        public DuelChallenge(UUID challengerUuid, String kit) {
            this.challengerUuid = challengerUuid;
            this.kit = kit;
        }

        public UUID getChallengerUuid() {
            return challengerUuid;
        }

        public String getKit() {
            return kit;
        }
    }
}

