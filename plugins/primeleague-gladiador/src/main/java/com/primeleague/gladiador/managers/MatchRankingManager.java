package com.primeleague.gladiador.managers;

import com.primeleague.gladiador.models.ClanEntry;
import com.primeleague.gladiador.models.GladiadorMatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Gerenciador de ranking de clans no match
 * Grug Brain: Ordena clans por posição (vencedor primeiro, depois por kills)
 */
public class MatchRankingManager {

    /**
     * Obtém clans ordenados por posição
     * Grug Brain: Vencedor = posição 1, depois ordena por kills (mais kills = melhor)
     */
    public List<ClanEntry> getRankedClans(GladiadorMatch match) {
        if (match == null) {
            return new ArrayList<>();
        }

        List<ClanEntry> ranked = new ArrayList<>(match.getClanEntries());
        ClanEntry winner = findWinner(ranked);

        final ClanEntry finalWinner = winner;
        ranked.sort((a, b) -> {
            if (finalWinner != null) {
                if (a.getClanId() == finalWinner.getClanId()) return -1;
                if (b.getClanId() == finalWinner.getClanId()) return 1;
            }

            int killDiff = Integer.compare(b.getKills(), a.getKills());
            if (killDiff != 0) {
                return killDiff;
            }

            return Integer.compare(a.getDeaths(), b.getDeaths());
        });

        return ranked;
    }

    /**
     * Encontra vencedor (clan não eliminado)
     * Grug Brain: Busca simples, primeiro não eliminado
     */
    private ClanEntry findWinner(List<ClanEntry> clans) {
        for (ClanEntry entry : clans) {
            if (!entry.isEliminated()) {
                return entry;
            }
        }
        return null;
    }
}





