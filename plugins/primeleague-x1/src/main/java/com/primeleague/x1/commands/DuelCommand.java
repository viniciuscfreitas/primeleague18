package com.primeleague.x1.commands;

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
            player.sendMessage(ChatColor.GRAY + "Padrão: qualquer lugar, seus itens. Use [kit] para arena com kit.");
            return true;
        }

        String subCommand = args[0].toLowerCase();

		if (subCommand.equals("accept") || subCommand.equals("aceitar")) {
            return handleAccept(player);
		} else if (subCommand.equals("deny") || subCommand.equals("negar")) {
            return handleDeny(player);
        } else if (subCommand.equals("desafiar") || subCommand.equals("duel") || subCommand.equals("challenge")) {
            // Desafiar player (remover primeiro arg que é o comando)
            String[] challengeArgs = new String[args.length - 1];
            System.arraycopy(args, 1, challengeArgs, 0, args.length - 1);
            return handleChallenge(player, challengeArgs);
        } else {
            // Assumir que primeiro arg é o nome do jogador (compatibilidade)
            return handleChallenge(player, args);
        }
    }

    /**
     * /x1 desafiar <player> [kit]
     * Grug Brain: Padrão = anywhere + noKit (zero fricção)
     * Se especificar kit, usa arena (anywhere=false) mas ainda noKit
     * Para usar kit, precisa especificar explicitamente
     */
    public boolean handleChallenge(Player challenger, String[] args) {
        if (args.length == 0) {
            challenger.sendMessage(ChatColor.RED + "Uso: /x1 desafiar <jogador> [kit]");
            challenger.sendMessage(ChatColor.GRAY + "Padrão: qualquer lugar, seus itens. Use [kit] para arena com kit.");
            return true;
        }

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

        // Grug Brain: Padrão = anywhere + noKit (zero fricção)
        // Se especificar kit, usa arena mas ainda pode ser noKit
        String kit = null;
        boolean anywhere = true;  // PADRÃO: anywhere
        boolean noKit = true;      // PADRÃO: noKit

        // Se especificou kit, usa arena (anywhere=false) e aplica kit (noKit=false)
        if (args.length > 1) {
            kit = args[1];
            anywhere = false;  // Kit = usa arena
            noKit = false;     // Kit = aplica kit
        } else {
            kit = "none";  // Sem kit quando noKit=true
            
            // Validar condições para anywhere (fail fast)
            // Grug Brain: Apenas validar coisas críticas (distância, mundo, void, lava)
            // Não validar GameMode/flying no desafio (players podem ajustar depois)
            double maxDistance = plugin.getConfig().getDouble("match.anywhere.max-distance", 50.0);
            
            // Validar apenas distância e localização (pragmático)
            String validationError = com.primeleague.x1.utils.AnywhereMatchValidator.validate(
                challenger, target, maxDistance);
            
            if (validationError != null) {
                challenger.sendMessage(validationError);
                challenger.sendMessage(ChatColor.GRAY + "Dica: Use /x1 desafiar " + target.getName() + " <kit> para usar uma arena");
                return true;
            }
            
            // Avisar se está longe mas ainda permitir
            double warnDistance = plugin.getConfig().getDouble("match.anywhere.warn-distance", 30.0);
            double distance = com.primeleague.x1.utils.AnywhereMatchValidator.getDistance(challenger, target);
            if (distance > warnDistance) {
                int blocks = (int) distance;
                challenger.sendMessage(ChatColor.YELLOW + "⚠ Aviso: " + target.getName() + 
                    " está a " + blocks + " blocos de distância");
            }
        }

		// Criar/atualizar desafio
        DuelChallenge challenge = new DuelChallenge(challenger.getUniqueId(), kit, anywhere, noKit);
        pendingChallenges.put(target.getUniqueId(), challenge);

        // Mensagens simplificadas (não mostra flags se for padrão)
        String msg1 = plugin.getConfig().getString("messages.duel.challenge-sent", 
            "§aDesafio enviado para {player}").replace("{player}", target.getName());
        if (!anywhere) {
            msg1 += " §7(arena: " + kit + ")";
        }
        challenger.sendMessage(msg1);

		String msg2 = plugin.getConfig().getString("messages.duel.challenge-received", 
			"§a{player} te desafiou! Use /x1 aceitar ou /x1 negar")
			.replace("{player}", challenger.getName());
        if (!anywhere) {
            msg2 += " §7(arena: " + kit + ")";
        }
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
            player.sendMessage(ChatColor.RED + "✗ Você não tem desafios pendentes.");
            return true;
        }

        Player challenger = Bukkit.getPlayer(challenge.getChallengerUuid());
        if (challenger == null || !challenger.isOnline()) {
            player.sendMessage(ChatColor.RED + "✗ O desafiante não está mais online.");
            return true;
        }

        // Verificar se ainda estão disponíveis
        if (plugin.getMatchManager().isInMatch(challenger.getUniqueId()) || 
            plugin.getMatchManager().isInMatch(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "✗ Um dos jogadores já está em uma partida.");
            return true;
        }

        // Criar match direto usando MatchCreator (suporta anywhere e noKit)
        plugin.getMatchManager().getMatchCreator().createDirectMatch(
            challenge.getChallengerUuid(),
            player.getUniqueId(),
            challenge.getKit(),
            false, // não ranked por padrão
            challenge.isAnywhere(),
            challenge.isNoKit()
        );

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
            player.sendMessage(ChatColor.RED + "✗ Você não tem desafios pendentes.");
            return true;
        }

        Player challenger = Bukkit.getPlayer(challenge.getChallengerUuid());
        if (challenger != null && challenger.isOnline()) {
            String msg = plugin.getConfig().getString("messages.duel.denied", 
                "§c✗ {player} negou seu desafio").replace("{player}", player.getName());
            challenger.sendMessage(msg);
        }

        player.sendMessage(ChatColor.YELLOW + "✗ Desafio negado.");
        return true;
    }

    /**
     * Classe interna para desafios pendentes
     */
    private static class DuelChallenge {
        private final UUID challengerUuid;
        private final String kit;
        private final boolean anywhere;
        private final boolean noKit;

        public DuelChallenge(UUID challengerUuid, String kit, boolean anywhere, boolean noKit) {
            this.challengerUuid = challengerUuid;
            this.kit = kit;
            this.anywhere = anywhere;
            this.noKit = noKit;
        }

        public UUID getChallengerUuid() {
            return challengerUuid;
        }

        public String getKit() {
            return kit;
        }

        public boolean isAnywhere() {
            return anywhere;
        }

        public boolean isNoKit() {
            return noKit;
        }
    }
}

