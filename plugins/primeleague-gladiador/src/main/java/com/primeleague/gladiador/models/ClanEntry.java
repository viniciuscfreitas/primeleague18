package com.primeleague.gladiador.models;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entrada de um clan em uma partida de Gladiador
 * Grug Brain: POJO simples para rastrear status do clan
 */
public class ClanEntry {

    private int clanId;
    private String clanName;
    private String clanTag;
    private Set<UUID> members;
    private Set<UUID> aliveMembers;
    private int kills;
    private int deaths;

    public ClanEntry(int clanId, String clanName, String clanTag) {
        this.clanId = clanId;
        this.clanName = clanName;
        this.clanTag = clanTag;
        this.members = new HashSet<>();
        this.aliveMembers = new HashSet<>();
        this.kills = 0;
        this.deaths = 0;
    }

    /**
     * Verifica se clan foi eliminado (ninguém vivo)
     */
    public boolean isEliminated() {
        return aliveMembers.isEmpty();
    }

    /**
     * Adiciona membro ao clan
     */
    public void addMember(UUID playerUuid) {
        members.add(playerUuid);
        aliveMembers.add(playerUuid);
    }

    /**
     * Elimina jogador do clan
     */
    public void eliminatePlayer(UUID playerUuid) {
        aliveMembers.remove(playerUuid);
    }

    /**
     * Adiciona player (alias para addMember)
     */
    public void addPlayer(UUID playerUuid) {
        addMember(playerUuid);
    }

    /**
     * Remove player (alias para eliminatePlayer)
     */
    public void removePlayer(UUID playerUuid) {
        eliminatePlayer(playerUuid);
    }

    /**
     * Obtém jogadores restantes (vivos)
     */
    public Set<UUID> getRemainingPlayers() {
        return new HashSet<>(aliveMembers);
    }

    /**
     * Conta jogadores restantes (vivos)
     */
    public int getRemainingPlayersCount() {
        return aliveMembers.size();
    }

    // Getters e Setters
    public int getClanId() {
        return clanId;
    }

    public void setClanId(int clanId) {
        this.clanId = clanId;
    }

    public String getClanName() {
        return clanName;
    }

    public void setClanName(String clanName) {
        this.clanName = clanName;
    }

    public String getClanTag() {
        return clanTag;
    }

    public void setClanTag(String clanTag) {
        this.clanTag = clanTag;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void setMembers(Set<UUID> members) {
        this.members = members;
    }

    public Set<UUID> getAliveMembers() {
        return aliveMembers;
    }

    public void setAliveMembers(Set<UUID> aliveMembers) {
        this.aliveMembers = aliveMembers;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public void incrementKills() {
        this.kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public void incrementDeaths() {
        this.deaths++;
    }
}
