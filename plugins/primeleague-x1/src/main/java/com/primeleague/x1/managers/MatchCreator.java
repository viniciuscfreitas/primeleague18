package com.primeleague.x1.managers;

import com.primeleague.x1.X1Plugin;
import com.primeleague.x1.models.Arena;
import com.primeleague.x1.models.Kit;
import com.primeleague.x1.models.Match;
import com.primeleague.x1.models.QueueEntry;
import com.primeleague.x1.utils.MatchFeedbackHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Criador de matches a partir de queue entries
 * Grug Brain: Lógica isolada, fail fast (sem criação automática mascarando erros)
 */
public class MatchCreator {

    private final X1Plugin plugin;
    private final MatchManager matchManager;

    public MatchCreator(X1Plugin plugin, MatchManager matchManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
    }

    /**
     * Cria match a partir de queue entries
     * Grug Brain: Falha explicitamente se arena/kit não existir (fail fast)
     */
    public void createFromQueue(QueueEntry entry1, QueueEntry entry2) {
        // Verificar se match seria suspeito (anti-farm)
        // Nota: Não bloqueia, apenas marca como suspeito para reduzir ELO depois
        boolean suspicious = plugin.getAntiFarmManager().isSuspiciousMatch(
            entry1.getPlayerUuid(), entry2.getPlayerUuid());
        
        if (suspicious) {
            // Log mas não bloqueia - apenas reduzirá ELO depois
            plugin.getLogger().info("Match potencialmente suspeito detectado (mesmos oponentes): " + 
                entry1.getPlayerUuid() + " vs " + entry2.getPlayerUuid());
        }
        
        // Buscar arena disponível (FAIL FAST se não existir)
        Arena arena = plugin.getArenaManager().getAvailableArena(entry1.getKit());
        if (arena == null) {
            plugin.getLogger().warning("Nenhuma arena disponível para kit: " + entry1.getKit());
            String msg = plugin.getConfig().getString("messages.error.no-arena",
                "§cNenhuma arena configurada. Use /x1 admin arena create");
            notifyPlayers(entry1, entry2, msg);
            return;
        }

        // Buscar kit (FAIL FAST se não existir)
        Kit kit = plugin.getKitManager().getKit(entry1.getKit());
        if (kit == null) {
            plugin.getLogger().warning("Kit não encontrado: " + entry1.getKit());
            String msg = plugin.getConfig().getString("messages.error.invalid-kit",
                "§cKit inválido: {kit}. Use /x1 admin kit create {kit}")
                .replace("{kit}", entry1.getKit());
            notifyPlayers(entry1, entry2, msg);
            return;
        }

        // Criar match
        Match match = new Match(entry1.getPlayerUuid(), entry2.getPlayerUuid(),
            entry1.getKit(), arena, entry1.isRanked());

        // Adicionar aos matches ativos
        matchManager.addActiveMatch(match);

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

        // Atualizar scoreboard contextual (se disponível)
        if (plugin.getScoreboardIntegration() != null && plugin.getScoreboardIntegration().isEnabled()) {
            if (p1 != null) plugin.getScoreboardIntegration().updateScoreboard(p1);
            if (p2 != null) plugin.getScoreboardIntegration().updateScoreboard(p2);
        }

        // Feedback ao encontrar match
        MatchFeedbackHandler.sendMatchFoundFeedback(plugin, p1, p2);

        // Iniciar match (teleport, kit, countdown)
        matchManager.startMatch(match);
    }

    /**
     * Cria match direto entre dois players (para duelos)
     * @param player1Uuid UUID do primeiro player
     * @param player2Uuid UUID do segundo player
     * @param kit Nome do kit (pode ser null se noKit=true)
     * @param ranked Se é ranked
     * @param anywhere Se true, não teleporta para arena (usa localização atual)
     * @param noKit Se true, não aplica kit (usa itens do jogador)
     */
    public void createDirectMatch(UUID player1Uuid, UUID player2Uuid, String kit, boolean ranked, boolean anywhere, boolean noKit) {
        Player p1 = Bukkit.getPlayer(player1Uuid);
        Player p2 = Bukkit.getPlayer(player2Uuid);

        if (p1 == null || p2 == null || !p1.isOnline() || !p2.isOnline()) {
            return;
        }

        // Validar condições para anywhere antes de criar match (fail fast)
        // Grug Brain: Apenas validar distância e mundo (pragmático)
        if (anywhere) {
            double maxDistance = plugin.getConfig().getDouble("match.anywhere.max-distance", 50.0);
            // Validar apenas distância e mundo (não GameMode/flying - players podem ajustar)
            String validationError = com.primeleague.x1.utils.AnywhereMatchValidator.validate(p1, p2, maxDistance);
            
            if (validationError != null) {
                String msg = validationError + " §7Match cancelado.";
                p1.sendMessage(msg);
                p2.sendMessage(msg);
                return;
            }
        }

        Arena arena = null;
        if (!anywhere) {
            // Buscar arena apenas se não for anywhere
            arena = plugin.getArenaManager().getAvailableArena(kit != null ? kit : "default");
            if (arena == null) {
                String msg = plugin.getConfig().getString("messages.error.no-arena",
                    "§cNenhuma arena disponível");
                p1.sendMessage(msg);
                p2.sendMessage(msg);
                return;
            }
        }

        // Se noKit, kit pode ser null ou "none"
        if (noKit && (kit == null || kit.isEmpty())) {
            kit = "none";
        }

        // Criar match
        Match match = new Match(player1Uuid, player2Uuid, kit, arena, ranked, anywhere, noKit);

        // Adicionar aos matches ativos
        matchManager.addActiveMatch(match);

        // Marcar arena como em uso (se houver)
        if (arena != null) {
            plugin.getArenaManager().markArenaInUse(arena);
        }

        // Atualizar TAB prefix (se disponível)
        if (plugin.getTabIntegration() != null && plugin.getTabIntegration().isEnabled()) {
            plugin.getTabIntegration().updateMatchPrefix(p1);
            plugin.getTabIntegration().updateMatchPrefix(p2);
        }

        // Atualizar scoreboard contextual (se disponível)
        if (plugin.getScoreboardIntegration() != null && plugin.getScoreboardIntegration().isEnabled()) {
            plugin.getScoreboardIntegration().updateScoreboard(p1);
            plugin.getScoreboardIntegration().updateScoreboard(p2);
        }

        // Feedback ao encontrar match
        MatchFeedbackHandler.sendMatchFoundFeedback(plugin, p1, p2);

        // Iniciar match (teleport, kit, countdown)
        matchManager.startMatch(match);
    }

    /**
     * Notifica ambos os players
     */
    private void notifyPlayers(QueueEntry entry1, QueueEntry entry2, String message) {
        Player p1 = Bukkit.getPlayer(entry1.getPlayerUuid());
        Player p2 = Bukkit.getPlayer(entry2.getPlayerUuid());
        if (p1 != null) p1.sendMessage(message);
        if (p2 != null) p2.sendMessage(message);
    }
}

