package com.primeleague.x1.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.elo.EloAPI;
import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Arena;
import com.primeleague.x1.models.Kit;
import com.primeleague.x1.models.Match;
import com.primeleague.x1.models.QueueEntry;
import com.primeleague.x1.utils.TitleCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Matches
 * Grug Brain: Thread-safe, integra com EloAPI e CoreAPI
 */
public class MatchManager {

    private final X1Plugin plugin;
    // Matches ativos: UUID do player -> Match
    private final Map<UUID, Match> activeMatches;
    private final int countdownSeconds;
	// Snapshot de inventário/armadura/efeitos por jogador para restaurar ao fim
	private final Map<UUID, PlayerSnapshot> snapshots;

    public MatchManager(X1Plugin plugin) {
        this.plugin = plugin;
        this.activeMatches = new ConcurrentHashMap<>();
		this.snapshots = new ConcurrentHashMap<>();
        int countdown = plugin.getConfig().getInt("match.countdown", 5);
        // Validar countdown (deve ser >= 0)
        if (countdown < 0) {
            plugin.getLogger().warning("Countdown inválido (" + countdown + "), usando 5 segundos");
            this.countdownSeconds = 5;
        } else {
            this.countdownSeconds = countdown;
        }
    }

    /**
     * Cria match a partir de queue entries
     */
    public void createMatchFromQueue(QueueEntry entry1, QueueEntry entry2) {
        // Buscar arena disponível (criar padrão se não existir)
        Arena arena = plugin.getArenaManager().getAvailableArena(entry1.getKit());
        if (arena == null) {
            plugin.getLogger().warning("Nenhuma arena disponível para kit: " + entry1.getKit());
            // Criar arena padrão automaticamente
            arena = createDefaultArena();
            if (arena == null) {
                // Notificar players
                org.bukkit.entity.Player p1 = org.bukkit.Bukkit.getPlayer(entry1.getPlayerUuid());
                org.bukkit.entity.Player p2 = org.bukkit.Bukkit.getPlayer(entry2.getPlayerUuid());
                String msg = plugin.getConfig().getString("messages.error.no-arena", 
                    "§cNenhuma arena disponível no momento. Tente novamente em instantes.");
                if (p1 != null) p1.sendMessage(msg);
                if (p2 != null) p2.sendMessage(msg);
                return;
            }
        }

        // Buscar ou criar kit
        Kit kit = plugin.getKitManager().getKit(entry1.getKit());
        if (kit == null) {
            plugin.getLogger().info("Kit não encontrado, criando kit padrão: " + entry1.getKit());
            kit = createDefaultKit(entry1.getKit());
            if (kit == null) {
                // Notificar players
                org.bukkit.entity.Player p1 = org.bukkit.Bukkit.getPlayer(entry1.getPlayerUuid());
                org.bukkit.entity.Player p2 = org.bukkit.Bukkit.getPlayer(entry2.getPlayerUuid());
                String msg = plugin.getConfig().getString("messages.error.invalid-kit", 
                    "§cErro ao criar kit: {kit}").replace("{kit}", entry1.getKit());
                if (p1 != null) p1.sendMessage(msg);
                if (p2 != null) p2.sendMessage(msg);
                return;
            }
        }

        // Criar match
        Match match = new Match(entry1.getPlayerUuid(), entry2.getPlayerUuid(), 
            entry1.getKit(), arena, entry1.isRanked());
        
        // Adicionar aos matches ativos
        activeMatches.put(entry1.getPlayerUuid(), match);
        activeMatches.put(entry2.getPlayerUuid(), match);

        // Marcar arena como em uso
        plugin.getArenaManager().markArenaInUse(arena);

        // Obter players uma vez e reutilizar
        Player p1 = Bukkit.getPlayer(entry1.getPlayerUuid());
        Player p2 = Bukkit.getPlayer(entry2.getPlayerUuid());
        
        // Atualizar TAB prefix (se disponível)
        if (plugin.getTabIntegration() != null && plugin.getTabIntegration().isEnabled()) {
            if (p1 != null) plugin.getTabIntegration().updateMatchPrefix(p1);
            if (p2 != null) plugin.getTabIntegration().updateMatchPrefix(p2);
        }

        // Feedback ao encontrar match
        boolean matchFoundSound = plugin.getConfig().getBoolean("ux.match-found-sound", true);
        String brandingNome = plugin.getConfig().getString("ux.branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("ux.branding.cor", "§b");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.branding.mostrar-em-titulos", true);
        
        if (p1 != null && p1.isOnline()) {
            String opponentName = p2 != null ? p2.getName() : "Oponente";
                try {
                String title = mostrarBranding ? 
                    brandingCor + "§l" + brandingNome : 
                    "§a§lPARTIDA ENCONTRADA!";
                String subtitle = "§7Oponente: §e" + opponentName;
                TitleCompat.send(p1, title, subtitle);
            } catch (Exception e) {
                // Fallback
                p1.sendMessage(plugin.getConfig().getString("messages.queue.match-found", 
                    "§aPartida encontrada! Preparando arena..."));
            }
            
            if (matchFoundSound) {
                try {
                    p1.playSound(p1.getLocation(), org.bukkit.Sound.LEVEL_UP, 1.0f, 1.2f);
                } catch (Exception e) {
                    // Fallback para som alternativo
                    try {
                        p1.playSound(p1.getLocation(), org.bukkit.Sound.NOTE_PLING, 1.0f, 1.2f);
                    } catch (Exception e2) {
                        // Ignorar se som não disponível
                    }
                }
            }
        }
        
        if (p2 != null && p2.isOnline()) {
            String opponentName = p1 != null ? p1.getName() : "Oponente";
                try {
                String title = mostrarBranding ? 
                    brandingCor + "§l" + brandingNome : 
                    "§a§lPARTIDA ENCONTRADA!";
                String subtitle = "§7Oponente: §e" + opponentName;
                TitleCompat.send(p2, title, subtitle);
            } catch (Exception e) {
                // Fallback
                p2.sendMessage(plugin.getConfig().getString("messages.queue.match-found", 
                    "§aPartida encontrada! Preparando arena..."));
            }
            
            if (matchFoundSound) {
                try {
                    p2.playSound(p2.getLocation(), org.bukkit.Sound.LEVEL_UP, 1.0f, 1.2f);
                } catch (Exception e) {
                    // Fallback para som alternativo
                    try {
                        p2.playSound(p2.getLocation(), org.bukkit.Sound.NOTE_PLING, 1.0f, 1.2f);
                    } catch (Exception e2) {
                        // Ignorar se som não disponível
                    }
                }
            }
        }

        // Iniciar countdown
        startMatch(match);
    }

    /**
     * Inicia match com countdown
     */
    public void startMatch(Match match) {
        Player player1 = Bukkit.getPlayer(match.getPlayer1());
        Player player2 = Bukkit.getPlayer(match.getPlayer2());

        if (player1 == null || player2 == null || !player1.isOnline() || !player2.isOnline()) {
            cancelMatch(match);
            return;
        }

		// Snapshot antes de aplicar kit (evita duplicação/perda)
		captureSnapshot(player1);
		captureSnapshot(player2);

		// Teleportar players para arena
        player1.teleport(match.getArena().getSpawn1());
        player2.teleport(match.getArena().getSpawn2());

        // Aplicar kit
        Kit kit = plugin.getKitManager().getKit(match.getKit());
        if (kit != null) {
            applyKit(player1, kit);
            applyKit(player2, kit);
        }

        // Countdown
        final int[] countdown = {countdownSeconds};
        boolean countdownTitles = plugin.getConfig().getBoolean("ux.countdown-titles", true);
        String brandingNome = plugin.getConfig().getString("ux.branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("ux.branding.cor", "§b");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.branding.mostrar-em-titulos", true);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (match.getStatus() != Match.MatchStatus.WAITING) {
                    cancel();
                    return;
                }

                if (countdown[0] > 0) {
                    String msg = plugin.getConfig().getString("messages.match.starting", 
                        "§aPartida iniciando em {countdown} segundos!")
                        .replace("{countdown}", String.valueOf(countdown[0]));
                    player1.sendMessage(msg);
                    player2.sendMessage(msg);
                    
                    // Título visual durante countdown
                    if (countdownTitles) {
                        try {
                            String title = "§a§l" + countdown[0];
                            String subtitle = mostrarBranding ? 
                                brandingCor + "§l" + brandingNome + " §7Prepare-se!" : 
                                "§7Prepare-se!";
                            TitleCompat.send(player1, title, subtitle);
                            TitleCompat.send(player2, title, subtitle);
                        } catch (Exception e) {
                            // Fallback para versões sem sendTitle
                        }
                    }
                    
                    countdown[0]--;
                } else {
                    // Iniciar match
                    match.setStatus(Match.MatchStatus.FIGHTING);
                    match.setStartTime(new Date());
                    
                    String msg = plugin.getConfig().getString("messages.match.started", 
                        "§aPartida iniciada! Boa sorte!");
                    player1.sendMessage(msg);
                    player2.sendMessage(msg);
                    
                    // Título ao iniciar match
                    if (countdownTitles) {
                        try {
                            String title = mostrarBranding ? 
                                brandingCor + "§l" + brandingNome : 
                                "§a§lPARTIDA INICIADA";
                            String subtitle = "§a§lBOA SORTE!";
                            TitleCompat.send(player1, title, subtitle);
                            TitleCompat.send(player2, title, subtitle);
                        } catch (Exception e) {
                            // Fallback
                        }
                    }
                    
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // A cada segundo
    }

    /**
     * Finaliza match com vencedor
     */
    public void endMatch(Match match, UUID winnerUuid) {
        if (match.getStatus() == Match.MatchStatus.ENDED) {
            return; // Já finalizado
        }

        match.setStatus(Match.MatchStatus.ENDED);
        match.setEndTime(new Date());
        match.setWinner(winnerUuid);

        UUID loserUuid = winnerUuid.equals(match.getPlayer1()) ? match.getPlayer2() : match.getPlayer1();

        Player winner = Bukkit.getPlayer(winnerUuid);
        Player loser = Bukkit.getPlayer(loserUuid);

		// Restaurar inventários logo após definir vencedor
		restoreSnapshot(winnerUuid);
		restoreSnapshot(loserUuid);

        // Atualizar ELO se ranked
        int eloChange = 0;
        if (match.isRanked()) {
            try {
                if (EloAPI.isEnabled()) {
                    eloChange = EloAPI.updateEloAfterPvP(winnerUuid, loserUuid);
                    // Armazenar mudança de ELO para placeholders
                    plugin.setLastEloChange(winnerUuid, eloChange);
                    plugin.setLastEloChange(loserUuid, -eloChange);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erro ao atualizar ELO (EloAPI não disponível): " + e.getMessage());
            }
        }
        
        // Salvar match no banco (via async)
        saveMatchToDatabase(match, eloChange);

        // Atualizar stats globais (kills/deaths/killstreak)
        CoreAPI.incrementKillsAndKillstreak(winnerUuid);
        CoreAPI.incrementDeathsAndResetKillstreak(loserUuid);

        // Atualizar stats x1 (wins/losses/winstreak)
        plugin.getStatsManager().updateStats(winnerUuid, loserUuid);

        // Mensagens
        String winnerName = winner != null ? winner.getName() : "Desconhecido";
        String loserName = loser != null ? loser.getName() : "Desconhecido";
        String msg = plugin.getConfig().getString("messages.match.ended", 
            "§a{winner} venceu o match contra {loser}!")
            .replace("{winner}", winnerName)
            .replace("{loser}", loserName);
        
        // Evitar duplicação: se houver broadcast, não enviar mensagem individual
        boolean broadcastOnEnd = plugin.getConfig().getBoolean("match.broadcast-on-end", true);
        if (!broadcastOnEnd) {
        if (winner != null) winner.sendMessage(msg);
        if (loser != null) loser.sendMessage(msg);
        }
        
        // Títulos ao vencer/perder
        boolean victorySound = plugin.getConfig().getBoolean("ux.victory-sound", true);
        boolean defeatSound = plugin.getConfig().getBoolean("ux.defeat-sound", true);
        String brandingNome = plugin.getConfig().getString("ux.branding.nome", "PRIME LEAGUE");
        String brandingCor = plugin.getConfig().getString("ux.branding.cor", "§b");
        boolean mostrarBranding = plugin.getConfig().getBoolean("ux.branding.mostrar-em-titulos", true);
        
        // Título de vitória
        if (winner != null) {
                try {
                String title = mostrarBranding ? 
                    brandingCor + "§l" + brandingNome : 
                    "§a§lVITÓRIA!";
                String subtitle = "§7Você venceu " + loserName;
                if (match.isRanked() && eloChange != 0) {
                    String eloStr = eloChange > 0 ? "§a+" + eloChange : "§c" + eloChange;
                    subtitle += " §7| ELO: " + eloStr;
                }
                TitleCompat.send(winner, title, subtitle);
            } catch (Exception e) {
                // Fallback
            }
            
            if (victorySound) {
                try {
                    winner.playSound(winner.getLocation(), org.bukkit.Sound.LEVEL_UP, 1.0f, 1.5f);
                } catch (Exception e) {
                    // Fallback
                    try {
                        winner.playSound(winner.getLocation(), org.bukkit.Sound.NOTE_PLING, 1.0f, 1.5f);
                    } catch (Exception e2) {
                        // Ignorar
                    }
                }
            }
        }
        
        // Título de derrota
        if (loser != null) {
            try {
                String title = mostrarBranding ? 
                    brandingCor + "§l" + brandingNome : 
                    "§c§lDERROTA";
                String subtitle = "§7Você perdeu para " + winnerName;
                if (match.isRanked() && eloChange != 0) {
                    int loserEloChange = -eloChange;
                    String eloStr = loserEloChange > 0 ? "§a+" + loserEloChange : "§c" + loserEloChange;
                    subtitle += " §7| ELO: " + eloStr;
                }
                TitleCompat.send(loser, title, subtitle);
            } catch (Exception e) {
                // Fallback
            }
            
            if (defeatSound) {
                try {
                    loser.playSound(loser.getLocation(), org.bukkit.Sound.ANVIL_LAND, 0.5f, 0.8f);
                } catch (Exception e) {
                    // Fallback
                    try {
                        loser.playSound(loser.getLocation(), org.bukkit.Sound.ANVIL_BREAK, 0.5f, 0.8f);
                    } catch (Exception e2) {
                        // Ignorar
                    }
                }
            }
        }
        
        // Broadcast (opcional - pode ser desabilitado via config)
        if (broadcastOnEnd) {
        Bukkit.broadcastMessage(msg);
        }

        // Discord webhook (se disponível)
        if (plugin.getDiscordIntegration() != null) {
            plugin.getDiscordIntegration().sendMatchEndWebhook(match, winnerName, loserName, eloChange);
        }

        // Limpar match após delay
        new BukkitRunnable() {
            @Override
            public void run() {
                removeMatch(match);
                // Marcar arena como disponível
                plugin.getArenaManager().markArenaAvailable(match.getArena());
            }
        }.runTaskLater(plugin, 100L); // 5 segundos
    }

    /**
     * Cancela match (disconnect, etc)
     */
    public void cancelMatch(Match match) {
        if (match.getStatus() == Match.MatchStatus.ENDED) {
            return;
        }

        Player player1 = Bukkit.getPlayer(match.getPlayer1());
        Player player2 = Bukkit.getPlayer(match.getPlayer2());

        String msg = plugin.getConfig().getString("messages.match.cancelled", "§cMatch cancelado");
        if (player1 != null) player1.sendMessage(msg);
        if (player2 != null) player2.sendMessage(msg);

		// Restaurar snapshots em cancelamento
		restoreSnapshot(match.getPlayer1());
		restoreSnapshot(match.getPlayer2());

        removeMatch(match);
        plugin.getArenaManager().markArenaAvailable(match.getArena());
    }

    /**
     * Remove match dos ativos
     */
    private void removeMatch(Match match) {
        activeMatches.remove(match.getPlayer1());
        activeMatches.remove(match.getPlayer2());
		// Limpar snapshots armazenados
		snapshots.remove(match.getPlayer1());
		snapshots.remove(match.getPlayer2());
        
        // Limpar TAB prefix (se disponível)
        if (plugin.getTabIntegration() != null && plugin.getTabIntegration().isEnabled()) {
            Player p1 = Bukkit.getPlayer(match.getPlayer1());
            Player p2 = Bukkit.getPlayer(match.getPlayer2());
            if (p1 != null) plugin.getTabIntegration().clearPrefix(p1);
            if (p2 != null) plugin.getTabIntegration().clearPrefix(p2);
        }
    }

    /**
     * Obtém match de um player
     */
    public Match getMatch(UUID playerUuid) {
        return activeMatches.get(playerUuid);
    }

    /**
     * Verifica se player está em match
     */
    public boolean isInMatch(UUID playerUuid) {
        return activeMatches.containsKey(playerUuid);
    }

    /**
     * Aplica kit a um player
     */
    private void applyKit(Player player, Kit kit) {
        // Limpar inventário
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        
        // Remover effects
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Aplicar items
        if (kit.getItems() != null) {
            player.getInventory().setContents(kit.getItems());
        }

        // Aplicar armor
        if (kit.getArmor() != null) {
            player.getInventory().setArmorContents(kit.getArmor());
        }

        // Aplicar effects
        if (kit.getEffects() != null) {
            for (org.bukkit.potion.PotionEffect effect : kit.getEffects()) {
                player.addPotionEffect(effect);
            }
        }

        player.updateInventory();
    }

	/**
	 * Captura snapshot do jogador (inventário, armadura, efeitos)
	 */
	private void captureSnapshot(Player player) {
		if (player == null || !player.isOnline()) return;
		if (snapshots.containsKey(player.getUniqueId())) return;
		org.bukkit.inventory.ItemStack[] inv = player.getInventory().getContents();
		org.bukkit.inventory.ItemStack[] armor = player.getInventory().getArmorContents();
		java.util.Collection<org.bukkit.potion.PotionEffect> effects = new java.util.ArrayList<>(player.getActivePotionEffects());
		snapshots.put(player.getUniqueId(), new PlayerSnapshot(inv, armor, effects));
	}

	/**
	 * Restaura snapshot se existir e limpa o kit aplicado.
	 */
	private void restoreSnapshot(UUID playerId) {
		if (playerId == null) return;
		Player player = Bukkit.getPlayer(playerId);
		PlayerSnapshot snap = snapshots.get(playerId);
		if (player == null || snap == null) return;
		try {
			player.getInventory().clear();
			player.getInventory().setArmorContents(null);
			for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
				player.removePotionEffect(effect.getType());
			}
			if (snap.inventory != null) {
				player.getInventory().setContents(snap.inventory);
			}
			if (snap.armor != null) {
				player.getInventory().setArmorContents(snap.armor);
			}
			if (snap.effects != null) {
				for (org.bukkit.potion.PotionEffect e : snap.effects) {
					player.addPotionEffect(e);
				}
			}
			player.updateInventory();
		} catch (Exception ignored) {
		}
	}

	/**
	 * Estrutura simples de snapshot
	 */
	private static class PlayerSnapshot {
		private final org.bukkit.inventory.ItemStack[] inventory;
		private final org.bukkit.inventory.ItemStack[] armor;
		private final java.util.Collection<org.bukkit.potion.PotionEffect> effects;

		private PlayerSnapshot(org.bukkit.inventory.ItemStack[] inventory,
							   org.bukkit.inventory.ItemStack[] armor,
							   java.util.Collection<org.bukkit.potion.PotionEffect> effects) {
			this.inventory = inventory != null ? inventory.clone() : null;
			this.armor = armor != null ? armor.clone() : null;
			this.effects = effects;
		}
	}

    /**
     * Salva match no banco de dados (async)
     */
    private void saveMatchToDatabase(Match match, int eloChange) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (java.sql.Connection conn = CoreAPI.getDatabase().getConnection()) {
                    java.sql.PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO x1_matches (player1_uuid, player2_uuid, winner_uuid, kit_name, ranked, elo_change, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)");
                    
                    stmt.setObject(1, match.getPlayer1());
                    stmt.setObject(2, match.getPlayer2());
                    stmt.setObject(3, match.getWinner());
                    stmt.setString(4, match.getKit());
                    stmt.setBoolean(5, match.isRanked());
                    stmt.setInt(6, eloChange);
                    // Usar startTime se disponível, senão usar created_at (NOW())
                    java.util.Date startTime = match.getStartTime();
                    if (startTime != null) {
                        stmt.setTimestamp(7, new java.sql.Timestamp(startTime.getTime()));
                    } else {
                        stmt.setTimestamp(7, new java.sql.Timestamp(System.currentTimeMillis()));
                    }
                    
                    stmt.executeUpdate();
                } catch (java.sql.SQLException e) {
                    plugin.getLogger().severe("Erro ao salvar match no banco: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Cria arena padrão se não existir nenhuma
     */
    private Arena createDefaultArena() {
        // Buscar world padrão
        org.bukkit.World world = org.bukkit.Bukkit.getWorlds().get(0);
        if (world == null) {
            return null;
        }

        // Criar arena padrão no spawn do world
        org.bukkit.Location spawn = world.getSpawnLocation();
        org.bukkit.Location spawn1 = spawn.clone().add(10, 0, 0);
        org.bukkit.Location spawn2 = spawn.clone().add(-10, 0, 0);
        org.bukkit.Location center = spawn.clone();

        Arena arena = new Arena("default", world.getName(), spawn1, spawn2, center);
        plugin.getArenaManager().createArena("default", spawn1, spawn2, center);
        
        plugin.getLogger().info("Arena padrão criada automaticamente");
        return arena;
    }

    /**
     * Cria kit padrão se não existir
     */
    private Kit createDefaultKit(String kitName) {
        Kit kit = new Kit(kitName);
        
        // Kit básico: espada de diamante, maçã dourada
        org.bukkit.inventory.ItemStack sword = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SWORD, 1);
        org.bukkit.inventory.ItemStack apple = new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_APPLE, 64);
        
        org.bukkit.inventory.ItemStack[] items = new org.bukkit.inventory.ItemStack[36];
        items[0] = sword;
        items[1] = apple;
        kit.setItems(items);
        
        // Armor básico: diamante
        org.bukkit.inventory.ItemStack[] armor = new org.bukkit.inventory.ItemStack[4];
        armor[0] = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_BOOTS, 1);
        armor[1] = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_LEGGINGS, 1);
        armor[2] = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_CHESTPLATE, 1);
        armor[3] = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_HELMET, 1);
        kit.setArmor(armor);
        
        // Salvar kit
        plugin.getKitManager().saveKit(kit);
        plugin.getLogger().info("Kit padrão criado automaticamente: " + kitName);
        
        return kit;
    }
}

