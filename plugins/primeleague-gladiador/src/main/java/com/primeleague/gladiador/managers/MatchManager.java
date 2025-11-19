package com.primeleague.gladiador.managers;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.managers.ClansManager;
import com.primeleague.clans.models.ClanData;
import com.primeleague.core.CoreAPI;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.models.Arena;
import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gerenciador de partidas do Gladiador
 * Grug Brain: Match logic direto, sem abstrações desnecessárias
 */
public class MatchManager {

    private final GladiadorPlugin plugin;
    private GladiadorMatch currentMatch;
    private final Map<UUID, ItemStack[]> inventorySnapshots = new ConcurrentHashMap<>(); // Thread-safe
    private final Map<UUID, ItemStack[]> armorSnapshots = new ConcurrentHashMap<>(); // Thread-safe

    public MatchManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    public GladiadorMatch getCurrentMatch() {
        return currentMatch;
    }

    /**
     * Inicia novo match
     */
    public boolean startMatch() {
        if (currentMatch != null) {
            return false; // Já existe match ativo
        }

        Arena arena = plugin.getArenaManager().getAvailableArena();
        if (arena == null) {
            return false;
        }

        currentMatch = new GladiadorMatch(arena);
        currentMatch.setState(GladiadorMatch.MatchState.WAITING);

        // Broadcast
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "⚔ " + ChatColor.YELLOW + ChatColor.BOLD + "EVENTO GLADIADOR" + ChatColor.GOLD + " ⚔");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "O evento foi iniciado! Digite " + ChatColor.GREEN + ChatColor.BOLD + "/gladiador" + ChatColor.YELLOW + " para participar");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Requisito: Estar em um clan");
        Bukkit.broadcastMessage("");

        // Iniciar task de preparação automática após X segundos (configurável)
        int waitTime = plugin.getConfig().getInt("arena.preparation-time", 30);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentMatch != null && currentMatch.getState() == GladiadorMatch.MatchState.WAITING) {
                    beginPreparation();
                }
            }
        }.runTaskLater(plugin, 20L * waitTime); // Tempo configurável (padrão 30s para teste)

        return true;
    }

    /**
     * Adiciona player ao match
     */
    public boolean addPlayer(Player player) {
        if (currentMatch == null || currentMatch.getState() != GladiadorMatch.MatchState.WAITING) {
            return false;
        }

        ClansManager clansManager = ClansPlugin.getInstance().getClansManager();
        ClanData clanData = clansManager.getClanByMember(player.getUniqueId());

        if (clanData == null) return false;

        // Verificar se player já está no match
        if (currentMatch.hasPlayer(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Você já está no evento.");
            return false;
        }

        // Obter ou criar ClanEntry
        ClanEntry clanEntry = currentMatch.getClanEntry(clanData.getId());
        if (clanEntry == null) {
            clanEntry = new ClanEntry(clanData.getId(), clanData.getName(), clanData.getTag());
            currentMatch.addClanEntry(clanEntry);
        }

        // Salvar inventário
        inventorySnapshots.put(player.getUniqueId(), player.getInventory().getContents());
        armorSnapshots.put(player.getUniqueId(), player.getInventory().getArmorContents());

        // Adicionar player
        clanEntry.addPlayer(player.getUniqueId());
        currentMatch.getAlivePlayers().add(player.getUniqueId());

        // Teleportar para spawn da arena (busca direta do banco com retry)
        final ClanEntry finalClanEntry = clanEntry;
        final GladiadorMatch finalMatch = currentMatch;
        teleportToArenaWithRetry(player, finalMatch, finalClanEntry, 3);

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);

        // Mensagem única (não duplicar)
        player.sendMessage(ChatColor.GREEN + "Você entrou no Gladiador! Aguarde o início.");

        // Atualizar tablist quando player entra
        if (plugin.getTabIntegration().isEnabled()) {
            plugin.getTabIntegration().updateTablist(player);
        }

        return true;
    }

    /**
     * Começa preparação (fecha entrada, countdown)
     */
    public void beginPreparation() {
        if (currentMatch == null) return;

        if (currentMatch.getClanEntries().isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "Gladiador cancelado por falta de participantes.");
            cancelMatch();
            return;
        }

        currentMatch.setState(GladiadorMatch.MatchState.PREPARATION);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "⚔ " + ChatColor.YELLOW + ChatColor.BOLD + "GLADIADOR" + ChatColor.GOLD + " ⚔");
        Bukkit.broadcastMessage(ChatColor.RED + "As entradas foram fechadas!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Clans: " + ChatColor.WHITE + ChatColor.BOLD + currentMatch.getClanEntries().size() +
                                ChatColor.YELLOW + " | Jogadores: " + ChatColor.WHITE + ChatColor.BOLD + currentMatch.getTotalPlayers());

        int prepTime = plugin.getConfig().getInt("arena.preparation-time", 30);
        int minutes = prepTime / 60;
        int seconds = prepTime % 60;
        String timeStr = minutes > 0 ? minutes + " minuto" + (minutes > 1 ? "s" : "") + (seconds > 0 ? " e " + seconds + "s" : "") : seconds + " segundos";
        Bukkit.broadcastMessage(ChatColor.YELLOW + "O PvP será ativado em " + ChatColor.WHITE + ChatColor.BOLD + timeStr);
        Bukkit.broadcastMessage("");

        // Setar world border inicial
        plugin.getArenaManager().setWorldBorder(currentMatch.getArena(), currentMatch.getArena().getInitialBorderSize());

        // Countdown para PvP (configurável, padrão 10s para teste)
        int countdownTime = Math.max(10, prepTime / 3); // 1/3 do tempo de preparação, mínimo 10s

        new BukkitRunnable() {
            int count = countdownTime;

            @Override
            public void run() {
                if (currentMatch == null || currentMatch.getState() != GladiadorMatch.MatchState.PREPARATION) {
                    this.cancel();
                    return;
                }

                if (count == countdownTime / 2 || count == 10 || count <= 5) {
                    if (count <= 5) {
                        Bukkit.broadcastMessage(ChatColor.RED.toString() + ChatColor.BOLD.toString() + "⚔ " + count + " segundos!");
                    } else {
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "O Gladiador começa em " + count + " segundos!");
                    }
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.CLICK, 1f, 1f);
                    }
                }

                if (count <= 0) {
                    beginMatch();
                    this.cancel();
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Começa match (habilita PvP, inicia border shrink)
     */
    public void beginMatch() {
        if (currentMatch == null) return;

        currentMatch.setState(GladiadorMatch.MatchState.ACTIVE);
        currentMatch.setStartTime(System.currentTimeMillis());

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.RED.toString() + ChatColor.BOLD.toString() + "⚔ " + ChatColor.DARK_RED.toString() + ChatColor.BOLD.toString() + "VALENDO!" + ChatColor.RED.toString() + ChatColor.BOLD.toString() + " ⚔");
        Bukkit.broadcastMessage(ChatColor.RED + "O PvP foi ativado! " + ChatColor.YELLOW + "Boa sorte aos clans!");
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENDERDRAGON_GROWL, 1f, 1f);
        }

        // Notificar Discord de todos os clans participantes (via ClansPlugin)
        ClansManager clansManager = ClansPlugin.getInstance().getClansManager();
        if (ClansPlugin.getInstance().getDiscordIntegration() != null) {
            for (ClanEntry entry : currentMatch.getClanEntries()) {
                ClanData clan = clansManager.getClan(entry.getClanId());
                if (clan != null) {
                    ClansPlugin.getInstance().getDiscordIntegration().notifyDiscord(clan,
                        "⚔ Gladiador Iniciado!",
                        "O evento Gladiador começou! Boa sorte aos " + entry.getRemainingPlayersCount() + " guerreiros do clan!");
                }
            }
        }

        // Notificar via Discord (se habilitado)
        plugin.getDiscordIntegration().sendMatchStarted(
            currentMatch.getClanEntries().size(),
            currentMatch.getTotalPlayers(),
            currentMatch.getArena().getName()
        );

        // Aplicar scoreboard para todos os players no match
        if (plugin.getScoreboardIntegration().isEnabled()) {
            for (UUID uuid : currentMatch.getAlivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    plugin.getScoreboardIntegration().setScoreboard(p, "gladiador-match");
                }
            }
            plugin.getScoreboardIntegration().startUpdateTask(currentMatch);
        }

        // Atualizar tablist para todos os players no match
        if (plugin.getTabIntegration().isEnabled()) {
            for (UUID uuid : currentMatch.getAlivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    plugin.getTabIntegration().updateTablist(p);
                }
            }
        }

        // Iniciar task de border shrink (configurável)
        int shrinkInterval = plugin.getConfig().getInt("arena.border-shrink-interval", 10);
        BukkitTask borderTask = new BukkitRunnable() {
            @Override
            public void run() {
                shrinkBorder();
            }
        }.runTaskTimer(plugin, 20L * shrinkInterval, 20L * shrinkInterval); // Intervalo configurável

        currentMatch.setBorderTask(borderTask);
    }

    /**
     * Reduz world border
     * Grug Brain: Lógica simples, verificação de cancelamento feita em checkWinCondition()
     */
    private void shrinkBorder() {
        if (currentMatch == null) return;

        // Lógica simples: Reduzir 100 blocos a cada execução até o limite final
        Bukkit.broadcastMessage(ChatColor.RED + "⚠ A borda da arena está diminuindo! Corram para o centro!");

        Arena arena = currentMatch.getArena();
        org.bukkit.WorldBorder wb = Bukkit.getWorld(arena.getWorld()).getWorldBorder();
        if (wb == null) return; // World não carregado

        double currentSize = wb.getSize();
        double newSize = Math.max(arena.getFinalBorderSize(), currentSize - 100);

        plugin.getArenaManager().shrinkBorder(arena, (int) newSize, 60);

        // Efeito de partículas na borda e limpar itens (Paper 1.8.8 - usar Effect)
        org.bukkit.World world = Bukkit.getWorld(arena.getWorld());
        if (world != null) {
            Location center = new Location(world, arena.getCenterX(), arena.getCenterY(), arena.getCenterZ());
            double radius = wb.getSize() / 2;
            double borderSize = wb.getSize();

            // Spawnar partículas em círculo (8 pontos)
            for (int i = 0; i < 8; i++) {
                double angle = (2 * Math.PI * i) / 8;
                double x = center.getX() + radius * Math.cos(angle);
                double z = center.getZ() + radius * Math.sin(angle);
                Location particleLoc = new Location(world, x, center.getY(), z);
                world.playEffect(particleLoc, org.bukkit.Effect.SMOKE, 0);
            }

            // Limpar itens no chão dentro da arena
            for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                if (item.getLocation().distance(center) <= borderSize / 2) {
                    item.remove();
                }
            }
        }
    }

    /**
     * Processa morte de player
     * Grug Brain: Funciona em qualquer estado (ACTIVE, PREPARATION, WAITING)
     */
    public void handleDeath(Player victim, Player killer) {
        if (currentMatch == null) return;

        // Permitir eliminação em qualquer estado (desconexão durante preparação também elimina)
        if (currentMatch.getState() == GladiadorMatch.MatchState.ENDING) return;

        if (!currentMatch.hasPlayer(victim.getUniqueId())) return;

        ClanEntry victimClanEntry = currentMatch.getClanEntry(victim.getUniqueId());
        if (victimClanEntry == null) return;

        // Remover player
        victimClanEntry.removePlayer(victim.getUniqueId());
        currentMatch.getAlivePlayers().remove(victim.getUniqueId());
        victimClanEntry.incrementDeaths();

        // Teleportar para spawn (fora da arena) - apenas se player estiver online
        if (victim.isOnline()) {
            victim.teleport(victim.getWorld().getSpawnLocation()); // TODO: Configurar spawn de saída
            restoreInventory(victim);
        }

        // Calcular tempo de sobrevivência
        long survivalTime = (System.currentTimeMillis() - currentMatch.getStartTime()) / 1000;
        long minutes = survivalTime / 60;
        long seconds = survivalTime % 60;
        String survivalStr = (minutes > 0 ? minutes + "m " : "") + seconds + "s";

        // Mensagem de morte customizada
        String deathMessage;
        String killerName = (killer != null) ? killer.getName() : "PvE";
        String killerClanTag = "";

        if (killer != null) {
            ClanEntry killerClanEntry = currentMatch.getClanEntry(killer.getUniqueId());
            if (killerClanEntry != null) {
                killerClanEntry.incrementKills();
                killerClanTag = " [" + killerClanEntry.getClanTag() + "]";

                // Adicionar kills ao stats global do clan
                plugin.getStatsManager().addKills(killerClanEntry.getClanId(), 1);
            }

            // PvP - incluir distância e tempo de sobrevivência
            double distance = victim.getLocation().distance(killer.getLocation());
            deathMessage = ChatColor.RED + "☠ " + ChatColor.WHITE + victim.getName() +
                          ChatColor.GRAY + " [" + victimClanEntry.getClanTag() + "]" +
                          ChatColor.RED + " foi eliminado por " +
                          ChatColor.WHITE + killerName + killerClanTag +
                          ChatColor.GRAY + " (" + String.format("%.1f", distance) + "m)" +
                          ChatColor.DARK_GRAY + " | Sobreviveu: " + survivalStr;
        } else if (!victim.isOnline()) {
            // Desconexão - incluir tempo de sobrevivência
            deathMessage = ChatColor.RED + "☠ " + ChatColor.WHITE + victim.getName() +
                          ChatColor.GRAY + " [" + victimClanEntry.getClanTag() + "]" +
                          ChatColor.RED + " desconectou e foi eliminado!" +
                          ChatColor.DARK_GRAY + " | Sobreviveu: " + survivalStr;
        } else {
            // PvE - incluir tempo de sobrevivência
            deathMessage = ChatColor.RED + "☠ " + ChatColor.WHITE + victim.getName() +
                          ChatColor.GRAY + " [" + victimClanEntry.getClanTag() + "]" +
                          ChatColor.RED + " foi eliminado por PvE" +
                          ChatColor.DARK_GRAY + " | Sobreviveu: " + survivalStr;
        }

        // Adicionar death ao stats global do clan
        plugin.getStatsManager().addDeaths(victimClanEntry.getClanId(), 1);

        Bukkit.broadcastMessage(deathMessage);

        // Verificar eliminação do clan
        if (victimClanEntry.getRemainingPlayersCount() == 0) {
            eliminateClan(victimClanEntry);
        }

        // Verificar vitória
        checkWinCondition();
    }

    /**
     * Elimina clan completamente
     */
    private void eliminateClan(ClanEntry clanEntry) {
        if (currentMatch == null) return;

        int remainingClans = currentMatch.getAliveClansCount();

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.RED + "❌ O clan " + ChatColor.BOLD + clanEntry.getClanTag() + ChatColor.RED + " foi eliminado!");

        // Só mostrar "Restam X clans" se houver mais de 1
        if (remainingClans > 1) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Restam " + remainingClans + " clans na arena.");
        } else if (remainingClans == 1) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "Apenas 1 clan resta na arena!");
        }
        Bukkit.broadcastMessage("");

        // Atualizar participação no stats
        plugin.getStatsManager().incrementParticipation(clanEntry.getClanId());

        // Notificar Discord (via ClansPlugin)
        if (ClansPlugin.getInstance().getDiscordIntegration() != null) {
            ClanData clan = ClansPlugin.getInstance().getClansManager().getClan(clanEntry.getClanId());
            if (clan != null) {
                ClansPlugin.getInstance().getDiscordIntegration().notifyDiscord(clan,
                    "❌ Clan Eliminado",
                    "O clan foi eliminado do Gladiador! Mais sorte na próxima vez.");
            }
        }

        // Notificar via Discord (se habilitado)
        plugin.getDiscordIntegration().sendClanEliminated(clanEntry.getClanTag(), currentMatch.getAliveClansCount());

        // Som de eliminação (global) - Paper 1.8.8: usar AMBIENCE_THUNDER
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.AMBIENCE_THUNDER, 1f, 1f);
        }
    }

    /**
     * Verifica condição de vitória
     * Grug Brain: Verifica vitória ANTES de verificar cancelamento (ordem importante!)
     */
    public void checkWinCondition() {
        if (currentMatch == null) return;

        // Filtrar clans com jogadores vivos
        List<ClanEntry> aliveClans = currentMatch.getClanEntries().stream()
                .filter(c -> c.getRemainingPlayersCount() > 0)
                .collect(Collectors.toList());

        // Verificar vitória PRIMEIRO (se sobra 1 clan, vence mesmo se jogadores desconectarem depois)
        if (aliveClans.size() == 1) {
            // Cancelar border task antes de finalizar
            if (currentMatch.getBorderTask() != null) {
                currentMatch.getBorderTask().cancel();
            }
            endMatch(aliveClans.get(0));
            return;
        }

        // Se não há clans vivos OU não há jogadores vivos → cancelar
        if (aliveClans.isEmpty() || currentMatch.getAlivePlayers().isEmpty()) {
            // Cancelar border task antes de cancelar match
            if (currentMatch.getBorderTask() != null) {
                currentMatch.getBorderTask().cancel();
            }

            if (aliveClans.isEmpty()) {
                Bukkit.broadcastMessage(ChatColor.RED + "Gladiador cancelado: Todos os clans foram eliminados.");
            } else {
                Bukkit.broadcastMessage(ChatColor.RED + "Gladiador cancelado: Todos os jogadores desconectaram.");
            }
            cancelMatch();
        }
        // Se aliveClans.size() > 1 → continua normalmente (não faz nada)
    }

    /**
     * Finaliza match
     */
    private void endMatch(ClanEntry winner) {
        if (currentMatch == null) return;

        // Border task já foi cancelado em checkWinCondition()
        // Garantir cancelamento aqui também (defensive programming - Paper 1.8.8 não tem isCancelled())
        if (currentMatch.getBorderTask() != null) {
            currentMatch.getBorderTask().cancel();
        }

        // Anúncio
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "🏆 GLADIADOR FINALIZADO 🏆");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Clan vencedor: " + ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "[" + winner.getClanTag() + "]");
        Bukkit.broadcastMessage(ChatColor.GREEN + "Parabéns pela vitória!");
        Bukkit.broadcastMessage("");

        // Prêmios (Exemplo: 1kk para o banco do clan)
        ClansManager clansManager = ClansPlugin.getInstance().getClansManager();
        clansManager.addClanBalance(winner.getClanId(), 100000000); // 1kk em centavos
        clansManager.addEventWin(winner.getClanId(), "Gladiador", 100, null);

        // Stats do vencedor
        plugin.getStatsManager().incrementWins(winner.getClanId());
        plugin.getStatsManager().incrementParticipation(winner.getClanId());

        // Teleportar vencedores e restaurar inv
        for (UUID uuid : winner.getRemainingPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.teleport(p.getWorld().getSpawnLocation());
                restoreInventory(p);
                p.sendMessage(ChatColor.GOLD + "Parabéns pela vitória!");
            }
        }

        // Calcular estatísticas finais
        int totalKills = currentMatch.getClanEntries().stream()
            .mapToInt(ClanEntry::getKills)
            .sum();
        long durationSeconds = (System.currentTimeMillis() - currentMatch.getStartTime()) / 1000;

        // Notificar via Discord (se habilitado)
        plugin.getDiscordIntegration().sendMatchWon(winner.getClanTag(), totalKills, durationSeconds);

        // Salvar match no banco
        saveMatchToDatabase(currentMatch, winner.getClanId());

        // Remover scoreboard e tablist customizados
        if (plugin.getScoreboardIntegration().isEnabled()) {
            plugin.getScoreboardIntegration().stopUpdateTask();
            for (UUID uuid : currentMatch.getAlivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    plugin.getScoreboardIntegration().clearScoreboard(p);
                }
            }
        }

        currentMatch = null;
    }

    /**
     * Cancela match
     * Grug Brain: Cancela tasks e restaura jogadores online
     */
    public void cancelMatch() {
        if (currentMatch == null) return;

        // Parar border shrink
        if (currentMatch.getBorderTask() != null) {
            currentMatch.getBorderTask().cancel();
        }

        // Restaurar todos os jogadores que ainda estão online
        for (ClanEntry entry : currentMatch.getClanEntries()) {
            for (UUID uuid : entry.getMembers()) { // Todos os membros, não apenas vivos
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.teleport(p.getWorld().getSpawnLocation());
                    restoreInventory(p);
                }
            }
        }

        // Limpar snapshots de inventário
        inventorySnapshots.clear();
        armorSnapshots.clear();

        // Remover scoreboard e tablist customizados
        if (plugin.getScoreboardIntegration().isEnabled()) {
            plugin.getScoreboardIntegration().stopUpdateTask();
            for (ClanEntry entry : currentMatch.getClanEntries()) {
                for (UUID uuid : entry.getMembers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        plugin.getScoreboardIntegration().clearScoreboard(p);
                    }
                }
            }
        }

        plugin.getLogger().info("Match Gladiador cancelado");
        currentMatch = null;
    }

    private void restoreInventory(Player player) {
        if (inventorySnapshots.containsKey(player.getUniqueId())) {
            player.getInventory().setContents(inventorySnapshots.get(player.getUniqueId()));
            player.getInventory().setArmorContents(armorSnapshots.get(player.getUniqueId()));
            inventorySnapshots.remove(player.getUniqueId());
            armorSnapshots.remove(player.getUniqueId());
        }
    }

    /**
     * Teleporta player para arena com retry automático
     * Grug Brain: Cria nova instância de BukkitRunnable para cada retry (evita "Already scheduled")
     */
    private void teleportToArenaWithRetry(final Player player, final GladiadorMatch match,
                                          final ClanEntry clanEntry, final int retriesLeft) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Buscar spawn points diretamente do banco (sempre atualizado)
                List<Location> spawns = plugin.getArenaManager().getSpawnPoints(match.getArena());
                if (!spawns.isEmpty()) {
                    // Usa o primeiro spawn point (single spawn requirement)
                    player.teleport(spawns.get(0));
                    player.sendMessage(ChatColor.GREEN + "Teleportado para a arena!");
                } else {
                    // Retry se não encontrou (pode estar salvando ainda)
                    if (retriesLeft > 0) {
                        // Aguardar 10 ticks (0.5s) antes de retry
                        teleportToArenaWithRetry(player, match, clanEntry, retriesLeft - 1);
                        return;
                    }

                    // Sem spawn após retries
                    player.sendMessage(ChatColor.RED + "Erro: Arena sem spawn point configurado!");
                    player.sendMessage(ChatColor.YELLOW + "Use: /gladiador setspawn " + match.getArena().getName());
                    // Remover player do match se não tem spawn
                    match.getAlivePlayers().remove(player.getUniqueId());
                    clanEntry.removePlayer(player.getUniqueId());
                }
            }
        }.runTaskLater(plugin, retriesLeft < 3 ? 10L : 0L); // Delay de 10 ticks em retries
    }

    /**
     * Salva match no banco de dados
     * Grug Brain: Query direta, async para não bloquear
     */
    private void saveMatchToDatabase(GladiadorMatch match, Integer winnerClanId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = CoreAPI.getDatabase().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO gladiador_matches (arena_id, winner_clan_id, participant_clans, " +
                         "total_kills, duration_seconds, started_at, ended_at) " +
                         "VALUES (?, ?, ?::jsonb, ?, ?, ?, CURRENT_TIMESTAMP)")) {

                    // Arena ID
                    stmt.setInt(1, match.getArena().getId());

                    // Winner clan ID (pode ser null se cancelado)
                    if (winnerClanId != null) {
                        stmt.setInt(2, winnerClanId);
                    } else {
                        stmt.setNull(2, Types.INTEGER);
                    }

                    // Participant clans como JSONB
                    JSONArray participantClans = new JSONArray();
                    for (ClanEntry entry : match.getClanEntries()) {
                        JSONObject clanJson = new JSONObject();
                        clanJson.put("clan_id", entry.getClanId());
                        clanJson.put("tag", entry.getClanTag());
                        clanJson.put("kills", entry.getKills());
                        clanJson.put("deaths", entry.getDeaths());
                        participantClans.add(clanJson);
                    }
                    stmt.setString(3, participantClans.toJSONString());

                    // Total kills (soma de todos os clans)
                    int totalKills = match.getClanEntries().stream()
                        .mapToInt(ClanEntry::getKills)
                        .sum();
                    stmt.setInt(4, totalKills);

                    // Duration em segundos
                    long durationSeconds = (System.currentTimeMillis() - match.getStartTime()) / 1000;
                    stmt.setInt(5, (int) durationSeconds);

                    // Started at
                    stmt.setTimestamp(6, new Timestamp(match.getStartTime()));

                    stmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Erro ao salvar match no banco: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}
