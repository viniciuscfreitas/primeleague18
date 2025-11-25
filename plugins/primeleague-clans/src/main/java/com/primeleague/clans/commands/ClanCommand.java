package com.primeleague.clans.commands;



import com.primeleague.clans.ClansPlugin;

import com.primeleague.clans.models.ClanData;

import com.primeleague.clans.models.ClanMember;

import com.primeleague.core.CoreAPI;

import com.primeleague.core.models.PlayerData;

// import com.primeleague.economy.EconomyAPI;

import org.bukkit.Bukkit;

import org.bukkit.ChatColor;

import org.bukkit.Location;

import org.bukkit.World;

import org.bukkit.command.Command;

import org.bukkit.command.CommandExecutor;

import org.bukkit.command.CommandSender;

import org.bukkit.entity.Player;



import java.sql.Connection;

import java.sql.PreparedStatement;

import java.sql.ResultSet;

import java.sql.SQLException;

import java.text.SimpleDateFormat;

import java.util.ArrayList;

import java.util.List;

import java.util.UUID;



/**

 * Comandos de clans - /clan

 * Grug Brain: Lógica inline, validações diretas

 */

public class ClanCommand implements CommandExecutor {



    private final ClansPlugin plugin;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private final ClanTerritoryHandler territoryHandler;


    public ClanCommand(ClansPlugin plugin) {

        this.plugin = plugin;

        this.territoryHandler = new ClanTerritoryHandler(plugin);
    }



    @Override

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {

            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por jogadores.");

            return true;

        }



        Player player = (Player) sender;



        if (args.length == 0) {

            sendHelp(player);

            return true;

        }



        String subCmd = args[0].toLowerCase();



        switch (subCmd) {

            case "criar":

                return handleCriar(player, args);

            case "sair":

                return handleSair(player);

            case "info":

                return handleInfo(player, args);

            case "membros":

                return handleMembros(player, args);

            case "convidar":

                return handleConvidar(player, args);

            case "aceitar":

                return handleAceitar(player, args);

            case "top":

                return handleTop(player, args);

            case "banco":

                return handleBanco(player);

            case "depositar":

                return handleDepositar(player, args);

            case "sacar":

                return handleSacar(player, args);

            case "expulsar":

                return handleExpulsar(player, args);

            case "promover":

                return handlePromover(player, args);

            case "rebaixar":

                return handleRebaixar(player, args);

            case "transferir":

                return handleTransferir(player, args);

            case "home":

                return handleHome(player, args);

            case "stats":

                return handleStats(player, args);

            case "alertas":

                return handleAlertas(player, args);

            case "tag":

                return handleTag(player, args);

            case "admin":

                return handleAdmin(player, args);

            // Comandos de territórios (Factions integrado)
            case "territorio":
            case "territorios":
            case "claims":
                return territoryHandler.handleTerritorio(player, args);
            case "claim":
                return territoryHandler.handleClaim(player, args);
            case "unclaim":
                return territoryHandler.handleUnclaim(player, args);
            case "mapa":
            case "map":
                return territoryHandler.handleMapa(player, args);
            case "power":
                return territoryHandler.handlePower(player, args);
            case "shield":
                return territoryHandler.handleShield(player, args);
            case "upgrade":
            case "upgrades":
                return territoryHandler.handleUpgrade(player, args);
            case "fly":
                return territoryHandler.handleFly(player, args);
            default:

                sendHelp(player);

                return true;

        }

    }



    /**

     * /clan criar <nome> <tag>

     */

    private boolean handleCriar(Player player, String[] args) {

        if (args.length < 3) {

            player.sendMessage(ChatColor.RED + "Uso: /clan criar <nome> <tag>");

            return true;

        }



        // Juntar nome (pode ter espaços)

        StringBuilder nomeBuilder = new StringBuilder();

        for (int i = 1; i < args.length - 1; i++) {

            if (i > 1) nomeBuilder.append(" ");

            nomeBuilder.append(args[i]);

        }

        String nome = nomeBuilder.toString().trim();

        String tag = args[args.length - 1];



        // Validar nome

        if (nome.length() < 1 || nome.length() > 50) {

            player.sendMessage(ChatColor.RED + "Nome do clan deve ter entre 1 e 50 caracteres.");

            return true;

        }



        // Validar tag

        String tagClean = ChatColor.stripColor(tag);

        if (tagClean.length() != 3) {

            player.sendMessage(ChatColor.RED + "Tag do clan deve ter exatamente 3 caracteres (sem contar cores).");

            return true;

        }

        if (tag.length() > 20) {

            player.sendMessage(ChatColor.RED + "Tag completa (com cores) não pode ter mais de 20 caracteres.");

            return true;

        }



        // Verificar se já está em clan

        ClanData existingClan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (existingClan != null) {

            player.sendMessage(ChatColor.RED + "Você já está em um clan! Use /clan sair primeiro.");

            return true;

        }



        // Criar clan

        ClanData clan = plugin.getClansManager().createClan(nome, tag, player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Erro ao criar clan. Verifique se a tag já existe.");

            return true;

        }



        player.sendMessage(ChatColor.GREEN + "Clan " + ChatColor.YELLOW + nome + ChatColor.GREEN + " criado com sucesso! Tag: " + ChatColor.YELLOW + tag);

        return true;

    }



    /**

     * /clan sair

     */

    private boolean handleSair(Player player) {

        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        // Verificar se é leader

        String role = plugin.getClansManager().getMemberRole(clan.getId(), player.getUniqueId());

        if ("LEADER".equals(role)) {

            player.sendMessage(ChatColor.RED + "Você é o líder do clan! Use /clan transferir para transferir a liderança antes de sair.");

            return true;

        }



        // Remover do clan

        if (plugin.getClansManager().removeMember(clan.getId(), player.getUniqueId())) {

            player.sendMessage(ChatColor.GREEN + "Você saiu do clan " + ChatColor.YELLOW + clan.getName() + ChatColor.GREEN + ".");

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao sair do clan.");

        }

        return true;

    }



    /**

     * /clan info [TAG]

     * CORREÇÃO: Mostra informações do clan + status unificado do player (se for o próprio clan)
     */

    private boolean handleInfo(Player player, String[] args) {

        ClanData clan = null;

        boolean isOwnClan = false; // Se está vendo o próprio clan


        if (args.length > 1) {

            // Buscar por TAG (clan de outro player)
            String tagClean = ChatColor.stripColor(args[1]).toUpperCase();

            clan = plugin.getClansManager().getClanByTag(tagClean);

            if (clan == null) {

                player.sendMessage(ChatColor.RED + "Clan não encontrado com a tag: " + args[1]);

                return true;

            }

        } else {

            // Mostrar clan do player (seu próprio clan)
            clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

            if (clan == null) {

                player.sendMessage(ChatColor.RED + "Você não está em um clan.");

                return true;

            }

            isOwnClan = true; // É o próprio clan
        }

        // CORREÇÃO: Se for o próprio clan, fazer tudo async (status + info do clan)
        // Se for outro clan, mostrar info síncrona (padrão atual)
        if (isOwnClan) {
            showClanInfoWithStatusAsync(player, clan);
            return true; // Retornar imediatamente - async vai mostrar tudo
        }

        // Para outro clan, mostrar info normalmente (síncrono - padrão existente)
        showClanInfoSync(player, clan);
        return true;
    }

    /**
     * Mostra informações do clan de forma síncrona (padrão existente - para outros clans)
     */
    private void showClanInfoSync(Player player, ClanData clan) {
        // Buscar membros

        List<ClanMember> members = plugin.getClansManager().getMembers(clan.getId());



        // Mostrar info

        player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + clan.getName() + ChatColor.GOLD + " ===");

        player.sendMessage(ChatColor.YELLOW + "Tag: " + ChatColor.WHITE + clan.getTag());

        player.sendMessage(ChatColor.YELLOW + "Membros: " + ChatColor.WHITE + members.size());



        // ELO médio do clan (com cache)

        double avgElo = plugin.getClansManager().getClanAverageElo(clan.getId());

        player.sendMessage(ChatColor.YELLOW + "ELO Médio: " + ChatColor.WHITE + String.format("%.0f", avgElo));



        // Buscar nome do leader

        PlayerData leaderData = CoreAPI.getPlayer(clan.getLeaderUuid());

        String leaderName = leaderData != null ? leaderData.getName() : "Desconhecido";

        player.sendMessage(ChatColor.YELLOW + "Líder: " + ChatColor.WHITE + leaderName);



        if (clan.getCreatedAt() != null) {

            player.sendMessage(ChatColor.YELLOW + "Criado em: " + ChatColor.GRAY + dateFormat.format(clan.getCreatedAt()));

        }



        if (clan.getDescription() != null && !clan.getDescription().isEmpty()) {

            player.sendMessage(ChatColor.YELLOW + "Descrição: " + ChatColor.GRAY + clan.getDescription());

        }



        // Pontos e eventos ganhos

        int points = plugin.getClansManager().getClanPoints(clan.getId());

        player.sendMessage(ChatColor.YELLOW + "Pontos: " + ChatColor.WHITE + points);

        Integer eventWinsCount = clan.getEventWinsCount();

        if (eventWinsCount != null && eventWinsCount > 0) {

            player.sendMessage(ChatColor.YELLOW + "Vitórias em Eventos: " + ChatColor.WHITE + eventWinsCount);

        }



        // Eventos ganhos por tipo

        java.util.Map<String, Integer> winsByEvent = plugin.getClansManager().getClanEventWinsByEvent(clan.getId());

        if (!winsByEvent.isEmpty()) {

            StringBuilder eventsStr = new StringBuilder();

            eventsStr.append(ChatColor.YELLOW).append("Eventos Ganhos: ").append(ChatColor.GRAY);

            int count = 0;

            for (java.util.Map.Entry<String, Integer> entry : winsByEvent.entrySet()) {

                if (count > 0) eventsStr.append(", ");

                eventsStr.append(entry.getKey()).append(" (x").append(entry.getValue()).append(")");

                count++;

                if (count >= 10) break; // Limitar a 10 eventos

            }

            player.sendMessage(eventsStr.toString());

        }



        // Status de bloqueio

        if (plugin.getClansManager().isClanBlockedFromEvents(clan.getId())) {

            player.sendMessage(ChatColor.RED + "⚠ Clan bloqueado de participar de eventos!");

        }



        // Info de Factions (territórios) - se disponível
        org.bukkit.plugin.Plugin factionsPlugin =
            plugin.getServer().getPluginManager().getPlugin("PrimeleagueFactions");
        if (factionsPlugin != null && factionsPlugin.isEnabled()) {
            com.primeleague.factions.PrimeFactions pf =
                (com.primeleague.factions.PrimeFactions) factionsPlugin;

            // Territórios
            int claims = pf.getClaimManager().getClaimCount(clan.getId());
            double totalPower = pf.getPowerManager().getClanTotalPower(clan.getId());
            int maxClaims = (int) (totalPower / 10.0);
            player.sendMessage(ChatColor.YELLOW + "Territórios: " + ChatColor.WHITE + claims + "/" + maxClaims +
                ChatColor.GRAY + " (" + String.format("%.1f", totalPower) + " power)");

            // Shield
            long shieldMinutes = pf.getShieldManager().getRemainingMinutes(clan.getId());
            if (shieldMinutes > 0) {
                String shieldText = pf.getShieldManager().formatRemaining(clan.getId());
                player.sendMessage(ChatColor.YELLOW + "🛡 Shield: " + ChatColor.WHITE + shieldText);
            } else {
                player.sendMessage(ChatColor.YELLOW + "🛡 Shield: " + ChatColor.RED + "ZERADO");
            }
        }
    }

    /**
     * DEPRECATED: Substituído por showClanInfoWithStatusAsync() que mostra tudo junto
     * Mantido apenas para compatibilidade - não usar mais
     */
    @Deprecated
    private void showPlayerStatusAsync(Player player) {
        UUID playerUuid = player.getUniqueId();
        final Player finalPlayer = player;

        // Query async para não bloquear thread principal
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                // Buscar dados do player (query async)
                PlayerData playerData = CoreAPI.getPlayer(playerUuid);
                if (playerData == null) {
                    return; // Player não encontrado (não deveria acontecer)
                }

                // Obter power (usar cache - thread-safe, não precisa async)
                // CORREÇÃO: PowerManager usa ConcurrentHashMap cache, então é seguro acessar de qualquer thread
                org.bukkit.plugin.Plugin factionsPlugin =
                    plugin.getServer().getPluginManager().getPlugin("PrimeleagueFactions");
                Double powerValue = null;
                Double maxPowerValue = null;
                if (factionsPlugin != null && factionsPlugin.isEnabled()) {
                    com.primeleague.factions.PrimeFactions pf =
                        (com.primeleague.factions.PrimeFactions) factionsPlugin;
                    powerValue = pf.getPowerManager().getPower(playerUuid);
                    maxPowerValue = pf.getPowerManager().getMaxPower(playerUuid);
                }

                // Obter role no clan (usar cache do ClansManager - thread-safe)
                ClanData clan = plugin.getClansManager().getClanByMember(playerUuid);
                String role = null;
                if (clan != null) {
                    role = plugin.getClansManager().getMemberRole(clan.getId(), playerUuid);
                }

                // Preparar dados para enviar (tornar final para usar na lambda)
                final PlayerData finalPlayerData = playerData;
                final Double finalPower = powerValue;
                final Double finalMaxPower = maxPowerValue;
                final String finalRole = role;

                // Voltar à thread principal para enviar mensagens (Bukkit API não é thread-safe)
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        // Verificar se player ainda está online
                        if (!finalPlayer.isOnline()) {
                            return;
                        }

                        // Header
                        finalPlayer.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Seu Status" + ChatColor.GOLD + " ===");

                        // ELO
                        int elo = finalPlayerData.getElo();
                        finalPlayer.sendMessage(ChatColor.YELLOW + "ELO: " + ChatColor.WHITE + elo);

                        // Dinheiro
                        long moneyCents = finalPlayerData.getMoney();
                        double money = moneyCents / 100.0;
                        finalPlayer.sendMessage(ChatColor.YELLOW + "Dinheiro: " + ChatColor.WHITE + String.format("$%.2f", money));

                        // Power (Factions - se disponível)
                        if (finalPower != null && finalMaxPower != null) {
                            finalPlayer.sendMessage(ChatColor.YELLOW + "Power: " + ChatColor.WHITE +
                                String.format("%.1f", finalPower) + "/" + String.format("%.1f", finalMaxPower));
                        }

                        // Stats de combate (K/D)
                        int kills = finalPlayerData.getKills();
                        int deaths = finalPlayerData.getDeaths();
                        double kdr = deaths > 0 ? (double) kills / deaths : kills;
                        finalPlayer.sendMessage(ChatColor.YELLOW + "K/D: " + ChatColor.WHITE +
                            kills + "/" + deaths + ChatColor.GRAY + " (" + String.format("%.2f", kdr) + ")");

                        // Killstreak atual e melhor
                        int killstreak = finalPlayerData.getKillstreak();
                        int bestKillstreak = finalPlayerData.getBestKillstreak();
                        if (killstreak > 0 || bestKillstreak > 0) {
                            finalPlayer.sendMessage(ChatColor.YELLOW + "Killstreak: " + ChatColor.WHITE + killstreak +
                                ChatColor.GRAY + " (Melhor: " + bestKillstreak + ")");
                        }

                        // Role no clan (se estiver em um)
                        if (finalRole != null) {
                            String roleDisplay = getRoleDisplay(finalRole);
                            finalPlayer.sendMessage(ChatColor.YELLOW + "Cargo: " + roleDisplay);
                        }

                        // Espaço visual antes das informações do clan
                        finalPlayer.sendMessage("");
                    }
                });
            }
        });
    }

    /**
     * Mostra informações do clan + status do player de forma async (para próprio clan)
     * CORREÇÃO: Tudo async para não bloquear thread, ordem garantida (status primeiro, depois clan info)
     */
    private void showClanInfoWithStatusAsync(final Player player, final ClanData clan) {
        UUID playerUuid = player.getUniqueId();
        final int clanId = clan.getId();

        // Query async para não bloquear thread principal
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                // Buscar dados do player (query async)
                PlayerData playerData = CoreAPI.getPlayer(playerUuid);
                if (playerData == null) {
                    // Se não encontrou player, ainda mostrar info do clan
                    plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (!player.isOnline()) return;
                            showClanInfoComplete(player, clan);
                        }
                    });
                    return;
                }

                // Obter power (cache - thread-safe)
                org.bukkit.plugin.Plugin factionsPlugin =
                    plugin.getServer().getPluginManager().getPlugin("PrimeleagueFactions");
                Double powerValue = null;
                Double maxPowerValue = null;
                if (factionsPlugin != null && factionsPlugin.isEnabled()) {
                    com.primeleague.factions.PrimeFactions pf =
                        (com.primeleague.factions.PrimeFactions) factionsPlugin;
                    powerValue = pf.getPowerManager().getPower(playerUuid);
                    maxPowerValue = pf.getPowerManager().getMaxPower(playerUuid);
                }

                // Obter role no clan (cache - thread-safe)
                String role = plugin.getClansManager().getMemberRole(clanId, playerUuid);

                // Buscar nome do leader (query async)
                PlayerData leaderData = CoreAPI.getPlayer(clan.getLeaderUuid());
                String leaderName = leaderData != null ? leaderData.getName() : "Desconhecido";

                // Preparar dados para enviar
                final PlayerData finalPlayerData = playerData;
                final Double finalPower = powerValue;
                final Double finalMaxPower = maxPowerValue;
                final String finalRole = role;
                final String finalLeaderName = leaderName;

                // Voltar à thread principal para enviar mensagens
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) return;

                        // 1. Mostrar status do player primeiro
                        showPlayerStatusSync(player, finalPlayerData, finalPower, finalMaxPower, finalRole);

                        // Espaço visual
                        player.sendMessage("");

                        // 2. Mostrar informações do clan depois
                        showClanInfoComplete(player, clan, finalLeaderName);
                    }
                });
            }
        });
    }

    /**
     * Mostra informações completas do clan (header + restante) com nome do leader já obtido
     */
    private void showClanInfoComplete(Player player, ClanData clan, String leaderName) {
        // Buscar membros (cache - rápido)
        List<ClanMember> members = plugin.getClansManager().getMembers(clan.getId());

        // Header
        player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + clan.getName() + ChatColor.GOLD + " ===");
        player.sendMessage(ChatColor.YELLOW + "Tag: " + ChatColor.WHITE + clan.getTag());
        player.sendMessage(ChatColor.YELLOW + "Membros: " + ChatColor.WHITE + members.size());

        // ELO médio do clan (com cache)
        double avgElo = plugin.getClansManager().getClanAverageElo(clan.getId());
        player.sendMessage(ChatColor.YELLOW + "ELO Médio: " + ChatColor.WHITE + String.format("%.0f", avgElo));
        player.sendMessage(ChatColor.YELLOW + "Líder: " + ChatColor.WHITE + leaderName);

        // Resto das informações
        showClanInfoRemaining(player, clan);
    }

    /**
     * Mostra informações completas do clan (header + restante) - busca leader síncrono
     */
    private void showClanInfoComplete(Player player, ClanData clan) {
        PlayerData leaderData = CoreAPI.getPlayer(clan.getLeaderUuid());
        String leaderName = leaderData != null ? leaderData.getName() : "Desconhecido";
        showClanInfoComplete(player, clan, leaderName);
    }

    /**
     * Mostra informações restantes do clan (compartilhado entre sync e async)
     */
    private void showClanInfoRemaining(Player player, ClanData clan) {
        if (clan.getCreatedAt() != null) {
            player.sendMessage(ChatColor.YELLOW + "Criado em: " + ChatColor.GRAY + dateFormat.format(clan.getCreatedAt()));
        }

        if (clan.getDescription() != null && !clan.getDescription().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Descrição: " + ChatColor.GRAY + clan.getDescription());
        }

        // Pontos e eventos ganhos
        int points = plugin.getClansManager().getClanPoints(clan.getId());
        player.sendMessage(ChatColor.YELLOW + "Pontos: " + ChatColor.WHITE + points);
        Integer eventWinsCount = clan.getEventWinsCount();
        if (eventWinsCount != null && eventWinsCount > 0) {
            player.sendMessage(ChatColor.YELLOW + "Vitórias em Eventos: " + ChatColor.WHITE + eventWinsCount);
        }

        // Eventos ganhos por tipo
        java.util.Map<String, Integer> winsByEvent = plugin.getClansManager().getClanEventWinsByEvent(clan.getId());
        if (!winsByEvent.isEmpty()) {
            StringBuilder eventsStr = new StringBuilder();
            eventsStr.append(ChatColor.YELLOW).append("Eventos Ganhos: ").append(ChatColor.GRAY);
            int count = 0;
            for (java.util.Map.Entry<String, Integer> entry : winsByEvent.entrySet()) {
                if (count > 0) eventsStr.append(", ");
                eventsStr.append(entry.getKey()).append(" (x").append(entry.getValue()).append(")");
                count++;
                if (count >= 10) break; // Limitar a 10 eventos
            }
            player.sendMessage(eventsStr.toString());
        }

        // Status de bloqueio
        if (plugin.getClansManager().isClanBlockedFromEvents(clan.getId())) {
            player.sendMessage(ChatColor.RED + "⚠ Clan bloqueado de participar de eventos!");
        }

        // Info de Factions (territórios) - se disponível
        org.bukkit.plugin.Plugin factionsPlugin =
            plugin.getServer().getPluginManager().getPlugin("PrimeleagueFactions");
        if (factionsPlugin != null && factionsPlugin.isEnabled()) {
            com.primeleague.factions.PrimeFactions pf =
                (com.primeleague.factions.PrimeFactions) factionsPlugin;

            // Territórios
            int claims = pf.getClaimManager().getClaimCount(clan.getId());
            double totalPower = pf.getPowerManager().getClanTotalPower(clan.getId());
            int maxClaims = (int) (totalPower / 10.0);
            player.sendMessage(ChatColor.YELLOW + "Territórios: " + ChatColor.WHITE + claims + "/" + maxClaims +
                ChatColor.GRAY + " (" + String.format("%.1f", totalPower) + " power)");

            // Shield
            long shieldMinutes = pf.getShieldManager().getRemainingMinutes(clan.getId());
            if (shieldMinutes > 0) {
                String shieldText = pf.getShieldManager().formatRemaining(clan.getId());
                player.sendMessage(ChatColor.YELLOW + "🛡 Shield: " + ChatColor.WHITE + shieldText);
            } else {
                player.sendMessage(ChatColor.YELLOW + "🛡 Shield: " + ChatColor.RED + "ZERADO");
            }
        }
    }

    /**
     * Mostra status do player de forma síncrona (já com dados carregados)
     */
    private void showPlayerStatusSync(Player player, PlayerData playerData, Double power, Double maxPower, String role) {
        // Header
        player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Seu Status" + ChatColor.GOLD + " ===");

        // ELO
        int elo = playerData.getElo();
        player.sendMessage(ChatColor.YELLOW + "ELO: " + ChatColor.WHITE + elo);

        // Dinheiro
        long moneyCents = playerData.getMoney();
        double money = moneyCents / 100.0;
        player.sendMessage(ChatColor.YELLOW + "Dinheiro: " + ChatColor.WHITE + String.format("$%.2f", money));

        // Power (Factions - se disponível)
        if (power != null && maxPower != null) {
            player.sendMessage(ChatColor.YELLOW + "Power: " + ChatColor.WHITE +
                String.format("%.1f", power) + "/" + String.format("%.1f", maxPower));
        }

        // Stats de combate (K/D)
        int kills = playerData.getKills();
        int deaths = playerData.getDeaths();
        double kdr = deaths > 0 ? (double) kills / deaths : kills;
        player.sendMessage(ChatColor.YELLOW + "K/D: " + ChatColor.WHITE +
            kills + "/" + deaths + ChatColor.GRAY + " (" + String.format("%.2f", kdr) + ")");

        // Killstreak atual e melhor
        int killstreak = playerData.getKillstreak();
        int bestKillstreak = playerData.getBestKillstreak();
        if (killstreak > 0 || bestKillstreak > 0) {
            player.sendMessage(ChatColor.YELLOW + "Killstreak: " + ChatColor.WHITE + killstreak +
                ChatColor.GRAY + " (Melhor: " + bestKillstreak + ")");
        }

        // Role no clan (se estiver em um)
        if (role != null) {
            String roleDisplay = getRoleDisplay(role);
            player.sendMessage(ChatColor.YELLOW + "Cargo: " + roleDisplay);
        }
    }



    /**

     * /clan membros [clan]

     */

    private boolean handleMembros(Player player, String[] args) {

        ClanData clan = null;



        if (args.length > 1) {

            // Buscar por nome (simplificado)

            player.sendMessage(ChatColor.RED + "Busca por nome ainda não implementada. Use sem argumentos para ver membros do seu clan.");

            return true;

        } else {

            clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

            if (clan == null) {

                player.sendMessage(ChatColor.RED + "Você não está em um clan.");

                return true;

            }

        }



        List<ClanMember> members = plugin.getClansManager().getMembers(clan.getId());

        if (members.isEmpty()) {

            player.sendMessage(ChatColor.RED + "Nenhum membro encontrado.");

            return true;

        }



        player.sendMessage(ChatColor.GOLD + "=== Membros de " + ChatColor.YELLOW + clan.getName() + ChatColor.GOLD + " ===");



        // Separar por role

        for (ClanMember member : members) {

            PlayerData memberData = CoreAPI.getPlayer(member.getPlayerUuid());

            String memberName = memberData != null ? memberData.getName() : "Desconhecido";

            String roleDisplay = getRoleDisplay(member.getRole());

            player.sendMessage(roleDisplay + ChatColor.WHITE + ": " + memberName);

        }



        return true;

    }



    /**

     * /clan convidar <player>

     */

    private boolean handleConvidar(Player player, String[] args) {

        if (args.length < 2) {

            player.sendMessage(ChatColor.RED + "Uso: /clan convidar <player>");

            return true;

        }



        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        // Verificar permissões (Leader ou Officer)

        String role = plugin.getClansManager().getMemberRole(clan.getId(), player.getUniqueId());

        if (!"LEADER".equals(role) && !"OFFICER".equals(role)) {

            player.sendMessage(ChatColor.RED + "Apenas líderes e oficiais podem convidar jogadores.");

            return true;

        }



        // Buscar player

        Player target = Bukkit.getPlayer(args[1]);

        if (target == null) {

            player.sendMessage(ChatColor.RED + "Player não encontrado: " + args[1]);

            return true;

        }



        // Verificar se já está em clan

        ClanData targetClan = plugin.getClansManager().getClanByMember(target.getUniqueId());

        if (targetClan != null) {

            player.sendMessage(ChatColor.RED + target.getName() + " já está em um clan.");

            return true;

        }



        // Criar invite

        if (plugin.getClansManager().invitePlayer(clan.getId(), target.getUniqueId(), player.getUniqueId())) {

            player.sendMessage(ChatColor.GREEN + "Convite enviado para " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + "!");

            target.sendMessage(ChatColor.YELLOW + "Você recebeu um convite do clan " + ChatColor.GOLD + clan.getName() + ChatColor.YELLOW + " (" + clan.getTag() + ChatColor.YELLOW + ")!");

            target.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/clan aceitar " + clan.getTag() + ChatColor.GRAY + " para aceitar.");

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao enviar convite.");

        }



        return true;

    }



    /**

     * /clan aceitar [clan]

     */

    private boolean handleAceitar(Player player, String[] args) {

        // Verificar se já está em clan

        ClanData existingClan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (existingClan != null) {

            player.sendMessage(ChatColor.RED + "Você já está em um clan!");

            return true;

        }



        List<Integer> invites = plugin.getClansManager().getPendingInvites(player.getUniqueId());

        if (invites.isEmpty()) {

            player.sendMessage(ChatColor.RED + "Você não tem convites pendentes.");

            return true;

        }



        // Se não especificou clan, listar invites

        if (args.length < 2) {

            player.sendMessage(ChatColor.GOLD + "=== Convites Pendentes ===");

            for (Integer clanId : invites) {

                ClanData clan = plugin.getClansManager().getClan(clanId);

                if (clan != null) {

                    player.sendMessage(ChatColor.YELLOW + clan.getName() + ChatColor.GRAY + " (" + clan.getTag() + ChatColor.GRAY + ")");

                }

            }

            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/clan aceitar <TAG>" + ChatColor.GRAY + " para aceitar um convite.");

            return true;

        }



        // Buscar clan por TAG na lista de invites

        String tagClean = ChatColor.stripColor(args[1]).toUpperCase();

        int clanId = -1;

        for (Integer inviteClanId : invites) {

            ClanData inviteClan = plugin.getClansManager().getClan(inviteClanId);

            if (inviteClan != null) {

                String inviteTagClean = ChatColor.stripColor(inviteClan.getTag()).toUpperCase();

                if (inviteTagClean.equals(tagClean)) {

                    clanId = inviteClanId;

                    break;

                }

            }

        }



        if (clanId == -1) {

            player.sendMessage(ChatColor.RED + "Você não tem convite do clan com a tag: " + args[1]);

            return true;

        }



        ClanData clan = plugin.getClansManager().getClan(clanId);

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Clan não encontrado.");

            return true;

        }



        // Aceitar invite

        if (plugin.getClansManager().acceptInvite(clanId, player.getUniqueId())) {

            player.sendMessage(ChatColor.GREEN + "Você entrou no clan " + ChatColor.YELLOW + clan.getName() + ChatColor.GREEN + " (" + clan.getTag() + ChatColor.GREEN + ")!");

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao aceitar convite. O convite pode ter expirado.");

        }



        return true;

    }



    /**

     * /clan top [elo|kills] [página]

     */

    private boolean handleTop(Player player, String[] args) {

        String type = "elo"; // Default: elo

        int page = 1;



        // Parse tipo (elo, kills ou points)

        if (args.length > 1) {

            String typeArg = args[1].toLowerCase();

            if (typeArg.equals("kills") || typeArg.equals("kill")) {

                type = "kills";

            } else if (typeArg.equals("elo")) {

                type = "elo";

            } else if (typeArg.equals("points") || typeArg.equals("point")) {

                type = "points";

            } else {

                // Pode ser página

                try {

                    page = Integer.parseInt(typeArg);

                    if (page < 1) page = 1;

                } catch (NumberFormatException e) {

                    player.sendMessage(ChatColor.RED + "Tipo inválido. Use: elo, kills ou points");

                    return true;

                }

            }

        }



        // Parse página

        if (args.length > 2) {

            try {

                page = Integer.parseInt(args[2]);

                if (page < 1) page = 1;

            } catch (NumberFormatException e) {

                player.sendMessage(ChatColor.RED + "Página inválida: " + args[2]);

                return true;

            }

        }



        final int finalPage = page;

        final String finalType = type;



        // Verificar cache

        String cacheKey = "clan_top_" + finalType + "_" + finalPage;

        ClansPlugin.TopCache cache = plugin.getTopCache(cacheKey);

        if (cache != null) {

            player.sendMessage(cache.getData());

            return true;

        }



        final String finalCacheKey = cacheKey;



        // Query async para não bloquear thread principal

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {

            @Override

            public void run() {

                List<TopClanEntry> topEntries = getTopClans(finalType, finalPage, 10);



                // Voltar à thread principal para enviar mensagens

                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {

                    @Override

                    public void run() {

                        if (topEntries.isEmpty()) {

                            player.sendMessage(ChatColor.RED + "Nenhum clan encontrado!");

                            return;

                        }



                        // Construir mensagem

                        StringBuilder message = new StringBuilder();

                        String typeDisplay;

                        if (finalType.equals("kills")) {

                            typeDisplay = "Kills";

                        } else if (finalType.equals("points")) {

                            typeDisplay = "Pontos";

                        } else {

                            typeDisplay = "ELO";

                        }

                        message.append(ChatColor.GOLD).append("=== TOP CLANS ").append(typeDisplay.toUpperCase())

                                .append(" (Página ").append(finalPage).append(") ===\n");



                        int startPosition = (finalPage - 1) * 10 + 1;

                        for (int i = 0; i < topEntries.size(); i++) {

                            TopClanEntry entry = topEntries.get(i);

                            int position = startPosition + i;

                            message.append(ChatColor.YELLOW).append("#").append(position).append(" ");

                            message.append(ChatColor.WHITE).append(entry.getName()).append(" ");

                            message.append(ChatColor.GRAY).append("(");

                            if (finalType.equals("kills")) {

                                message.append(entry.getValue()).append(" kills");

                            } else if (finalType.equals("points")) {

                                message.append(entry.getValue()).append(" pontos");

                            } else {

                                message.append(entry.getValue()).append(" ELO");

                            }

                            message.append(ChatColor.GRAY).append(")\n");

                        }



                        String result = message.toString();



                        // Salvar no cache

                        plugin.setTopCache(finalCacheKey, new ClansPlugin.TopCache(result));



                        player.sendMessage(result);

                    }

                });

            }

        });



        return true;

    }



    /**

     * Query direta PostgreSQL para top clans

     * Grug Brain: Try-with-resources para fechar recursos automaticamente

     */

    private List<TopClanEntry> getTopClans(String type, int page, int limit) {

        List<TopClanEntry> entries = new ArrayList<>();

        int offset = (page - 1) * limit;



        try (Connection conn = CoreAPI.getDatabase().getConnection()) {

            String sql;

            if (type.equals("kills")) {

                // Top por kills totais

                sql = "SELECT c.name, COALESCE(SUM(u.kills), 0) as total_kills " +

                      "FROM clans c " +

                      "LEFT JOIN clan_members cm ON c.id = cm.clan_id " +

                      "LEFT JOIN users u ON cm.player_uuid = u.uuid " +

                      "GROUP BY c.id, c.name " +

                      "HAVING COALESCE(SUM(u.kills), 0) > 0 " +

                      "ORDER BY total_kills DESC, c.name ASC " +

                      "LIMIT ? OFFSET ?";

            } else if (type.equals("points")) {

                // Top por pontos

                sql = "SELECT name, COALESCE(points, 0) as total_points, COALESCE(event_wins_count, 0) as wins " +

                      "FROM clans " +

                      "WHERE points IS NOT NULL AND points > 0 " +

                      "ORDER BY points DESC, event_wins_count DESC, name ASC " +

                      "LIMIT ? OFFSET ?";

            } else {

                // Top por ELO médio

                sql = "SELECT c.name, COALESCE(AVG(u.elo), 0) as avg_elo " +

                      "FROM clans c " +

                      "LEFT JOIN clan_members cm ON c.id = cm.clan_id " +

                      "LEFT JOIN users u ON cm.player_uuid = u.uuid " +

                      "GROUP BY c.id, c.name " +

                      "HAVING COUNT(cm.player_uuid) > 0 " +

                      "ORDER BY avg_elo DESC, c.name ASC " +

                      "LIMIT ? OFFSET ?";

            }



            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);

                stmt.setInt(2, offset);



                try (ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {

                        TopClanEntry entry = new TopClanEntry();

                        entry.setName(rs.getString("name"));

                        if (type.equals("kills")) {

                            entry.setValue(rs.getInt("total_kills"));

                        } else if (type.equals("points")) {

                            entry.setValue(rs.getInt("total_points"));

                        } else {

                            entry.setValue((int) rs.getDouble("avg_elo"));

                        }

                        entries.add(entry);

                    }

                }

            }

        } catch (SQLException e) {

            plugin.getLogger().severe("Erro ao buscar top clans (type: " + type + ", page: " + page + "): " + e.getMessage());

            e.printStackTrace();

        }



        return entries;

    }



    /**

     * Classe interna para entrada do top clans

     */

    private static class TopClanEntry {

        private String name;

        private int value;



        public String getName() {

            return name;

        }



        public void setName(String name) {

            this.name = name;

        }



        public int getValue() {

            return value;

        }



        public void setValue(int value) {

            this.value = value;

        }

    }



    /**

     * /clan banco

     */

    private boolean handleBanco(Player player) {

        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        // Verificar se Economy está habilitado

        /*

        if (!EconomyAPI.isEnabled()) {

            player.sendMessage(ChatColor.RED + "Sistema de economia não está disponível.");

            return true;

        }

        */



        // Buscar saldo do clan

        long balanceCents = plugin.getClansManager().getClanBalance(clan.getId());

        double balance = balanceCents / 100.0;



        player.sendMessage(ChatColor.GOLD + "=== Banco do Clan " + ChatColor.YELLOW + clan.getName() + ChatColor.GOLD + " ===");

        player.sendMessage(ChatColor.YELLOW + "Saldo: " + ChatColor.WHITE + String.format("%.2f", balance) + ChatColor.GRAY + " ¢");

        return true;

    }



    /**

     * /clan depositar <valor>

     */

    /**

     * /clan depositar <valor>

     */

    private boolean handleDepositar(Player player, String[] args) {

        if (args.length < 2) {

            player.sendMessage(ChatColor.RED + "Uso: /clan depositar <valor>");

            return true;

        }



        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        // Verificar se Economy está habilitado (soft dependency)

        if (!isEconomyEnabled()) {

            player.sendMessage(ChatColor.RED + "Sistema de economia não está disponível.");

            return true;

        }



        // Parse valor

        double amount;

        try {

            amount = Double.parseDouble(args[1]);

            if (amount <= 0) {

                player.sendMessage(ChatColor.RED + "Valor deve ser maior que zero.");

                return true;

            }

        } catch (NumberFormatException e) {

            player.sendMessage(ChatColor.RED + "Valor inválido: " + args[1]);

            return true;

        }



        // Verificar saldo do player

        if (!economyHasMoney(player.getUniqueId(), amount)) {

            player.sendMessage(ChatColor.RED + "Você não tem dinheiro suficiente.");

            return true;

        }



        // Depositar

        economyRemoveMoney(player.getUniqueId(), amount, "Depósito Clan");

        long cents = (long) (amount * 100);



        if (plugin.getClansManager().addClanBalance(clan.getId(), cents)) {

            player.sendMessage(ChatColor.GREEN + "Você depositou " + ChatColor.YELLOW + String.format("%.2f", amount) +

                ChatColor.GRAY + " ¢ " + ChatColor.GREEN + "no banco do clan!");

        } else {

            // Reverter se falhar

            economyAddMoney(player.getUniqueId(), amount, "Depósito Clan Revertido");

            player.sendMessage(ChatColor.RED + "Erro ao depositar. Seu dinheiro foi devolvido.");

        }



        return true;

    }



    /**

     * /clan sacar <valor>

     */

    private boolean handleSacar(Player player, String[] args) {

        if (args.length < 2) {

            player.sendMessage(ChatColor.RED + "Uso: /clan sacar <valor>");

            return true;

        }



        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        // Verificar permissões (Leader ou Officer)

        String role = plugin.getClansManager().getMemberRole(clan.getId(), player.getUniqueId());

        if (!"LEADER".equals(role) && !"OFFICER".equals(role)) {

            player.sendMessage(ChatColor.RED + "Apenas líderes e oficiais podem sacar do banco do clan.");

            return true;

        }



        // Verificar se Economy está habilitado (soft dependency)

        if (!isEconomyEnabled()) {

            player.sendMessage(ChatColor.RED + "Sistema de economia não está disponível.");

            return true;

        }



        // Parse valor

        double amount;

        try {

            amount = Double.parseDouble(args[1]);

            if (amount <= 0) {

                player.sendMessage(ChatColor.RED + "Valor deve ser maior que zero.");

                return true;

            }

        } catch (NumberFormatException e) {

            player.sendMessage(ChatColor.RED + "Valor inválido: " + args[1]);

            return true;

        }



        // Verificar saldo do clan

        long balanceCents = plugin.getClansManager().getClanBalance(clan.getId());

        long amountCents = (long) (amount * 100);

        if (balanceCents < amountCents) {

            player.sendMessage(ChatColor.RED + "O clan não tem saldo suficiente.");

            return true;

        }



        // Remover do clan e adicionar ao player

        if (plugin.getClansManager().removeClanBalance(clan.getId(), amountCents)) {

            economyAddMoney(player.getUniqueId(), amount, "Saque Clan");

            player.sendMessage(ChatColor.GREEN + "Você sacou " + ChatColor.YELLOW + String.format("%.2f", amount) +

                ChatColor.GRAY + " ¢ " + ChatColor.GREEN + "do banco do clan!");

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao sacar do banco do clan.");

        }



        return true;

    }



    /**

     * /clan expulsar <player>

     */

    private boolean handleExpulsar(Player player, String[] args) {

        if (args.length < 2) {

            player.sendMessage(ChatColor.RED + "Uso: /clan expulsar <player>");

            return true;

        }



        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        // Verificar permissões (Leader ou Officer)

        String role = plugin.getClansManager().getMemberRole(clan.getId(), player.getUniqueId());

        if (!"LEADER".equals(role) && !"OFFICER".equals(role)) {

            player.sendMessage(ChatColor.RED + "Apenas líderes e oficiais podem expulsar membros.");

            return true;

        }



        // Buscar player por nome (pode estar offline)

        Player target = Bukkit.getPlayer(args[1]);

        UUID targetUuid = null;

        String targetName = args[1];



        if (target != null) {

            targetUuid = target.getUniqueId();

            targetName = target.getName();

        } else {

            // Buscar por nome no banco

            PlayerData targetData = CoreAPI.getPlayerByName(args[1]);

            if (targetData != null) {

                targetUuid = targetData.getUuid();

            } else {

                player.sendMessage(ChatColor.RED + "Player não encontrado: " + args[1]);

                return true;

            }

        }



        // Verificar se target é membro do clan

        ClanData targetClan = plugin.getClansManager().getClanByMember(targetUuid);

        if (targetClan == null || targetClan.getId() != clan.getId()) {

            player.sendMessage(ChatColor.RED + targetName + " não é membro do seu clan.");

            return true;

        }



        // Verificar se target é leader (não pode expulsar leader)

        String targetRole = plugin.getClansManager().getMemberRole(clan.getId(), targetUuid);

        if ("LEADER".equals(targetRole)) {

            player.sendMessage(ChatColor.RED + "Você não pode expulsar o líder do clan!");

            return true;

        }



        // Verificar se player está tentando expulsar a si mesmo

        if (targetUuid.equals(player.getUniqueId())) {

            player.sendMessage(ChatColor.RED + "Use /clan sair para sair do clan.");

            return true;

        }



        // Expulsar membro

        if (plugin.getClansManager().removeMember(clan.getId(), targetUuid)) {

            player.sendMessage(ChatColor.GREEN + targetName + " foi expulso do clan.");

            if (target != null) {

                target.sendMessage(ChatColor.RED + "Você foi expulso do clan " + ChatColor.YELLOW + clan.getName() + ChatColor.RED + ".");

            }

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao expulsar membro.");

        }



        return true;

    }



    /**

     * /clan admin set <TAG> <campo> <valor>

     */

    private boolean handleAdminSet(Player player, String[] args) {

        if (args.length < 5) {

            player.sendMessage(ChatColor.RED + "Uso: /clan admin set <TAG> <campo> <valor>");

            player.sendMessage(ChatColor.GRAY + "Campos: " + ChatColor.WHITE + "name, tag, leader");

            player.sendMessage(ChatColor.GRAY + "Nota: Use a TAG do clan (ex: ABC) ao invés do nome");

            return true;

        }



        // Extrair campo e valor

        String campo = args[args.length - 2].toLowerCase();



        // Para campo "name", valor pode ter espaços; para "tag" e "leader", valor é palavra única

        String valor;

        StringBuilder nomeBuilder = new StringBuilder();

        if (campo.equals("name")) {

            // Valor é o último arg (pode ter espaços se juntado manualmente, mas vamos assumir que é o último)

            valor = args[args.length - 1];

            // Nome do clan está entre args[2] e args[args.length - 3]

            for (int i = 3; i < args.length - 2; i++) {

                if (i > 3) nomeBuilder.append(" ");

                nomeBuilder.append(args[i]);

            }

        } else {

            // Para tag e leader, valor é palavra única

            valor = args[args.length - 1];

            // Nome do clan está entre args[2] e args[args.length - 3]

            for (int i = 3; i < args.length - 2; i++) {

                if (i > 3) nomeBuilder.append(" ");

                nomeBuilder.append(args[i]);

            }

        }

        String clanName = nomeBuilder.toString().trim();



        // Buscar clan por tag (primeiro tenta por tag, depois por nome)

        ClanData clan = null;

        String tagCleanSearch = ChatColor.stripColor(args[3]).toUpperCase();

        clan = plugin.getClansManager().getClanByTag(tagCleanSearch);



        // Se não encontrou por tag, tenta por nome

        if (clan == null) {

            try (Connection conn = CoreAPI.getDatabase().getConnection();

                 PreparedStatement stmt = conn.prepareStatement(

                    "SELECT id FROM clans WHERE name = ?")) {

                stmt.setString(1, clanName);

                try (ResultSet rs = stmt.executeQuery()) {

                    if (rs.next()) {

                        int clanId = rs.getInt("id");

                        clan = plugin.getClansManager().getClan(clanId);

                    }

                }

            } catch (SQLException e) {

                plugin.getLogger().severe("Erro ao buscar clan: " + e.getMessage());

                player.sendMessage(ChatColor.RED + "Erro ao buscar clan.");

                return true;

            }

        }



        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Clan não encontrado: " + clanName);

            return true;

        }



        // Atualizar campo

        try (Connection conn = CoreAPI.getDatabase().getConnection()) {

            if (campo.equals("name")) {

                try (PreparedStatement stmt = conn.prepareStatement(

                    "UPDATE clans SET name = ? WHERE id = ?")) {

                    stmt.setString(1, valor);

                    stmt.setInt(2, clan.getId());

                    stmt.executeUpdate();

                    player.sendMessage(ChatColor.GREEN + "Nome do clan atualizado para: " + ChatColor.YELLOW + valor);

                }

            } else if (campo.equals("tag")) {

                // Validar tag

                String tagClean = ChatColor.stripColor(valor);

                if (tagClean.length() != 3 || valor.length() > 20) {

                    player.sendMessage(ChatColor.RED + "Tag inválida. Deve ter exatamente 3 caracteres (sem cores) e no máximo 20 com cores.");

                    return true;

                }

                tagClean = tagClean.toUpperCase();



                // Verificar se tag já existe

                ClanData existingClan = plugin.getClansManager().getClanByTag(tagClean);

                if (existingClan != null && existingClan.getId() != clan.getId()) {

                    player.sendMessage(ChatColor.RED + "Tag já existe: " + tagClean);

                    return true;

                }



                try (PreparedStatement stmt = conn.prepareStatement(

                    "UPDATE clans SET tag = ?, tag_clean = ? WHERE id = ?")) {

                    stmt.setString(1, valor);

                    stmt.setString(2, tagClean);

                    stmt.setInt(3, clan.getId());

                    stmt.executeUpdate();

                    player.sendMessage(ChatColor.GREEN + "Tag do clan atualizada para: " + ChatColor.YELLOW + valor);

                }

            } else if (campo.equals("leader")) {

                // Buscar UUID do novo leader

                PlayerData leaderData = CoreAPI.getPlayerByName(valor);

                if (leaderData == null) {

                    player.sendMessage(ChatColor.RED + "Player não encontrado: " + valor);

                    return true;

                }



                UUID newLeaderUuid = leaderData.getUuid();



                // Verificar se novo leader é membro do clan

                String memberRole = plugin.getClansManager().getMemberRole(clan.getId(), newLeaderUuid);

                if (memberRole == null) {

                    player.sendMessage(ChatColor.RED + "Player não é membro do clan. Adicione-o primeiro.");

                    return true;

                }



                // Transferir liderança

                if (plugin.getClansManager().transferLeadership(clan.getId(), newLeaderUuid)) {

                    player.sendMessage(ChatColor.GREEN + "Líder do clan atualizado para: " + ChatColor.YELLOW + valor);

                } else {

                    player.sendMessage(ChatColor.RED + "Erro ao atualizar líder do clan.");

                }

            } else {

                player.sendMessage(ChatColor.RED + "Campo inválido. Campos disponíveis: " + ChatColor.WHITE + "name, tag, leader");

            }

        } catch (SQLException e) {

            plugin.getLogger().severe("Erro ao atualizar clan: " + e.getMessage());

            player.sendMessage(ChatColor.RED + "Erro ao atualizar clan.");

        }



        return true;

    }



    /**

     * /clan tag cor <cor1> [cor2] [cor3]

     * Permite líder alterar cor da tag mantendo o texto

     * Até 3 cores (1 por letra)

     */

    private boolean handleTag(Player player, String[] args) {

        if (args.length < 2) {

            player.sendMessage(ChatColor.RED + "Uso: /clan tag cor <cor1> [cor2] [cor3]");

            player.sendMessage(ChatColor.GRAY + "Exemplo: /clan tag cor &a (aplica verde nas 3 letras)");

            player.sendMessage(ChatColor.GRAY + "Exemplo: /clan tag cor &a &b (1ª letra verde, outras 2 azul claro)");

            player.sendMessage(ChatColor.GRAY + "Exemplo: /clan tag cor &a &b &c (cada letra com sua cor)");

            return true;

        }



        if (!args[1].equalsIgnoreCase("cor")) {

            player.sendMessage(ChatColor.RED + "Uso: /clan tag cor <cor1> [cor2] [cor3]");

            return true;

        }



        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        // Verificar se é líder

        String role = plugin.getClansManager().getMemberRole(clan.getId(), player.getUniqueId());

        if (!"LEADER".equals(role)) {

            player.sendMessage(ChatColor.RED + "Apenas o líder pode alterar a cor da tag.");

            return true;

        }



        // Pegar tag atual sem cores

        String tagClean = clan.getTagClean();

        if (tagClean == null || tagClean.length() != 3) {

            player.sendMessage(ChatColor.RED + "Tag do clan inválida.");

            return true;

        }



        // Coletar cores (até 3)

        String[] cores = new String[3];

        int coresCount = 0;

        for (int i = 2; i < args.length && coresCount < 3; i++) {

            String cor = args[i];

            // Validar formato de cor (&x ou §x)

            if (cor.length() == 2 && (cor.charAt(0) == '&' || cor.charAt(0) == '§')) {

                // Validar se segundo caractere é válido (0-9, a-f, k-o, r)

                char code = cor.charAt(1);

                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') ||

                    (code >= 'k' && code <= 'o') || code == 'r') {

                    cores[coresCount] = cor;

                    coresCount++;

                } else {

                    player.sendMessage(ChatColor.RED + "Código de cor inválido: " + cor + ChatColor.GRAY + " (use &0-9, &a-f, &k-o, &r)");

                    return true;

                }

            } else {

                player.sendMessage(ChatColor.RED + "Cor inválida: " + cor + ChatColor.GRAY + " (use &x ou §x)");

                return true;

            }

        }



        if (coresCount == 0) {

            player.sendMessage(ChatColor.RED + "Você deve fornecer pelo menos uma cor.");

            return true;

        }



        // Construir nova tag com cores aplicadas

        // Lógica: última cor sempre altera as próximas letras

        // 1 cor: aplica nas 3 letras

        // 2 cores: primeira cor na 1ª letra, segunda cor nas outras 2

        // 3 cores: cada letra com sua cor

        StringBuilder novaTag = new StringBuilder();

        String ultimaCor = null;



        for (int i = 0; i < 3; i++) {

            // Determinar qual cor usar para esta letra

            String corParaLetra = null;

            if (i < coresCount && cores[i] != null) {

                // Tem cor específica para esta posição

                corParaLetra = cores[i].replace('&', '§');

                ultimaCor = corParaLetra; // Atualiza última cor

            } else if (ultimaCor != null) {

                // Usa última cor definida (propaga)

                corParaLetra = ultimaCor;

            }



            // Aplicar cor se houver

            if (corParaLetra != null) {

                novaTag.append(corParaLetra);

            }



            // Adicionar letra

            novaTag.append(tagClean.charAt(i));

        }



        String novaTagStr = novaTag.toString();



        // Validar tamanho

        if (novaTagStr.length() > 20) {

            player.sendMessage(ChatColor.RED + "Tag com cores muito longa (máximo 20 caracteres).");

            return true;

        }



        // Atualizar no banco

        try (Connection conn = CoreAPI.getDatabase().getConnection();

             PreparedStatement stmt = conn.prepareStatement(

                "UPDATE clans SET tag = ? WHERE id = ?")) {

            stmt.setString(1, novaTagStr);

            stmt.setInt(2, clan.getId());

            stmt.executeUpdate();



            // Atualizar cache

            clan.setTag(novaTagStr);



            player.sendMessage(ChatColor.GREEN + "Cor da tag atualizada para: " + ChatColor.YELLOW + novaTagStr);

        } catch (SQLException e) {

            plugin.getLogger().severe("Erro ao atualizar cor da tag: " + e.getMessage());

            player.sendMessage(ChatColor.RED + "Erro ao atualizar cor da tag.");

        }



        return true;

    }



    /**

     * /clan admin reload

     */

    private boolean handleAdminReload(Player player) {

        plugin.reloadConfig();

        player.sendMessage(ChatColor.GREEN + "Config do PrimeleagueClans recarregado!");

        return true;

    }



    /**

     * /clan alertas [página]

     */

    private boolean handleAlertas(Player player, String[] args) {

        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        String role = plugin.getClansManager().getMemberRole(clan.getId(), player.getUniqueId());

        if (!role.equals("LEADER") && !role.equals("OFFICER")) {

            player.sendMessage(ChatColor.RED + "Apenas líderes e oficiais podem ver alertas.");

            return true;

        }



        int page = 1;

        if (args.length > 1) {

            try {

                page = Integer.parseInt(args[1]);

                if (page < 1) page = 1;

            } catch (NumberFormatException e) {

                player.sendMessage(ChatColor.RED + "Página inválida: " + args[1]);

                return true;

            }

        }



        final int finalPage = page;

        final int finalClanId = clan.getId();



        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {

            List<com.primeleague.clans.models.ClanAlert> alerts = plugin.getClansManager().getClanAlerts(finalClanId, false);

            int alertCount = plugin.getClansManager().getClanAlertCount(finalClanId);

            boolean blocked = plugin.getClansManager().isClanBlockedFromEvents(finalClanId);



            plugin.getServer().getScheduler().runTask(plugin, () -> {

                player.sendMessage(ChatColor.GOLD + "=== Alertas do Clan ===");

                player.sendMessage(ChatColor.YELLOW + "Total: " + ChatColor.WHITE + alertCount + ChatColor.GRAY + " | " +

                        ChatColor.RED + "Bloqueado: " + ChatColor.WHITE + (blocked ? "Sim" : "Não"));



                if (alerts.isEmpty()) {

                    player.sendMessage(ChatColor.GRAY + "Nenhum alerta encontrado.");

                    return;

                }



                int perPage = 10;

                int start = (finalPage - 1) * perPage;

                int end = Math.min(start + perPage, alerts.size());



                for (int i = start; i < end; i++) {

                    com.primeleague.clans.models.ClanAlert alert = alerts.get(i);

                    String playerName = "Clan";

                    if (alert.getPlayerUuid() != null) {

                        PlayerData playerData = CoreAPI.getPlayer(alert.getPlayerUuid());

                        playerName = playerData != null ? playerData.getName() : "Desconhecido";

                    }

                    String createdBy = "Sistema";

                    if (alert.getCreatedBy() != null) {

                        PlayerData creatorData = CoreAPI.getPlayer(alert.getCreatedBy());

                        createdBy = creatorData != null ? creatorData.getName() : "Desconhecido";

                    }

                    String dateStr = alert.getCreatedAt() != null ? dateFormat.format(alert.getCreatedAt()) : "Desconhecido";

                    player.sendMessage(ChatColor.RED + "[ALERTA] " + ChatColor.GRAY + alert.getAlertType() +

                            ChatColor.WHITE + " - " + ChatColor.YELLOW + alert.getMessage() +

                            ChatColor.GRAY + " (por " + createdBy + " em " + dateStr + ")");

                }



                if (alerts.size() > end) {

                    player.sendMessage(ChatColor.GRAY + "Use /clan alertas " + (finalPage + 1) + " para ver mais.");

                }

            });

        });



        return true;

    }



    /**

     * /clan admin alertar <tag> [player] <tipo> <mensagem>

     */

    private boolean handleAdminAlertar(Player player, String[] args) {

        if (args.length < 5) {

            player.sendMessage(ChatColor.RED + "Uso: /clan admin alertar <tag> [player] <tipo> <mensagem>");

            return true;

        }



        // Buscar clan por tag

        String tagClean = ChatColor.stripColor(args[2]).toUpperCase();

        ClanData clan = plugin.getClansManager().getClanByTag(tagClean);



        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Clan não encontrado com a tag: " + args[2]);

            return true;

        }



        // Parse args: pode ter player ou não

        UUID playerUuid = null;

        String alertType;

        String message;



        // Verificar se terceiro arg é player ou tipo

        PlayerData testPlayer = CoreAPI.getPlayerByName(args[3]);

        if (testPlayer != null) {

            // É player

            playerUuid = testPlayer.getUuid();

            alertType = args[4];

            // Mensagem é o resto

            StringBuilder msgBuilder = new StringBuilder();

            for (int i = 5; i < args.length; i++) {

                if (i > 5) msgBuilder.append(" ");

                msgBuilder.append(args[i]);

            }

            message = msgBuilder.toString();

        } else {

            // Não é player, é tipo

            alertType = args[3];

            // Mensagem é o resto

            StringBuilder msgBuilder = new StringBuilder();

            for (int i = 4; i < args.length; i++) {

                if (i > 4) msgBuilder.append(" ");

                msgBuilder.append(args[i]);

            }

            message = msgBuilder.toString();

        }



        // Validar tipo

        if (!alertType.equals("WARNING") && !alertType.equals("PUNISHMENT") &&

                !alertType.equals("BAN") && !alertType.equals("CHEAT") && !alertType.equals("INFO")) {

            player.sendMessage(ChatColor.RED + "Tipo inválido. Use: WARNING, PUNISHMENT, BAN, CHEAT, INFO");

            return true;

        }



        if (plugin.getClansManager().addAlert(clan.getId(), playerUuid, alertType, message, player.getUniqueId(), null)) {

            player.sendMessage(ChatColor.GREEN + "Alerta adicionado ao clan " + clan.getName() + "!");

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao adicionar alerta.");

        }



        return true;

    }



    /**

     * /clan admin removealert <alerta_id>

     */

    private boolean handleAdminRemovealert(Player player, String[] args) {

        if (args.length < 3) {

            player.sendMessage(ChatColor.RED + "Uso: /clan admin removealert <alerta_id>");

            return true;

        }



        int alertId;

        try {

            alertId = Integer.parseInt(args[2]);

        } catch (NumberFormatException e) {

            player.sendMessage(ChatColor.RED + "ID de alerta inválido: " + args[2]);

            return true;

        }



        if (plugin.getClansManager().removeAlert(alertId, player.getUniqueId())) {

            player.sendMessage(ChatColor.GREEN + "Alerta #" + alertId + " removido! Penalidades recalculadas.");

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao remover alerta. Verifique se o ID existe.");

        }



        return true;

    }



    /**

     * /clan admin addwin <tag> <evento> [pontos]

     */

    private boolean handleAdminAddwin(Player player, String[] args) {

        if (args.length < 4) {

            player.sendMessage(ChatColor.RED + "Uso: /clan admin addwin <tag> <evento> [pontos]");

            return true;

        }



        // Buscar clan por tag

        String tagClean = ChatColor.stripColor(args[2]).toUpperCase();

        ClanData clan = plugin.getClansManager().getClanByTag(tagClean);



        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Clan não encontrado com a tag: " + args[2]);

            return true;

        }



        String eventName = args[3];

        int points;

        if (args.length > 4) {

            try {

                points = Integer.parseInt(args[4]);

            } catch (NumberFormatException e) {

                player.sendMessage(ChatColor.RED + "Pontos inválidos: " + args[4]);

                return true;

            }

        } else {

            points = plugin.getPointsForEvent(eventName);

        }



        // Disparar evento

        com.primeleague.clans.events.ClanWinEvent event = new com.primeleague.clans.events.ClanWinEvent(

                clan.getId(), eventName, points, player.getUniqueId());

        plugin.getServer().getPluginManager().callEvent(event);



        player.sendMessage(ChatColor.GREEN + "Vitória adicionada ao clan " + clan.getName() + "!");

        return true;

    }



    /**

     * /clan admin addpoints <tag> <pontos>

     */

    private boolean handleAdminAddpoints(Player player, String[] args) {

        if (args.length < 4) {

            player.sendMessage(ChatColor.RED + "Uso: /clan admin addpoints <tag> <pontos>");

            return true;

        }



        // Buscar clan por tag

        String tagClean = ChatColor.stripColor(args[2]).toUpperCase();

        ClanData clan = plugin.getClansManager().getClanByTag(tagClean);



        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Clan não encontrado com a tag: " + args[2]);

            return true;

        }



        int points;

        try {

            points = Integer.parseInt(args[3]);

        } catch (NumberFormatException e) {

            player.sendMessage(ChatColor.RED + "Pontos inválidos: " + args[3]);

            return true;

        }



        if (plugin.getClansManager().addPoints(clan.getId(), points, player.getUniqueId())) {

            player.sendMessage(ChatColor.GREEN + "Pontos adicionados ao clan " + clan.getName() + "!");

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao adicionar pontos.");

        }



        return true;

    }



    /**

     * /clan admin removepoints <tag> <pontos>

     */

    private boolean handleAdminRemovepoints(Player player, String[] args) {

        if (args.length < 4) {

            player.sendMessage(ChatColor.RED + "Uso: /clan admin removepoints <tag> <pontos>");

            return true;

        }



        // Buscar clan por tag

        String tagClean = ChatColor.stripColor(args[2]).toUpperCase();

        ClanData clan = plugin.getClansManager().getClanByTag(tagClean);



        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Clan não encontrado com a tag: " + args[2]);

            return true;

        }



        int points;

        try {

            points = Integer.parseInt(args[3]);

        } catch (NumberFormatException e) {

            player.sendMessage(ChatColor.RED + "Pontos inválidos: " + args[3]);

            return true;

        }



        if (plugin.getClansManager().removePoints(clan.getId(), points, player.getUniqueId())) {

            player.sendMessage(ChatColor.GREEN + "Pontos removidos do clan " + clan.getName() + "!");

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao remover pontos.");

        }



        return true;

    }



    /**

     * Mostra ajuda dos comandos admin

     */

    private void sendAdminHelp(Player player) {

        player.sendMessage(ChatColor.GOLD + "=== Comandos Admin de Clans ===");

        player.sendMessage(ChatColor.YELLOW + "/clan admin criar <nome> <tag> <leader>" + ChatColor.WHITE + " - Cria um clan");

        player.sendMessage(ChatColor.YELLOW + "/clan admin deletar <clan>" + ChatColor.WHITE + " - Deleta um clan");

        player.sendMessage(ChatColor.YELLOW + "/clan admin set <clan> <campo> <valor>" + ChatColor.WHITE + " - Atualiza campo do clan");

        player.sendMessage(ChatColor.GRAY + "  Campos: " + ChatColor.WHITE + "name, tag, leader");

        player.sendMessage(ChatColor.YELLOW + "/clan admin reload" + ChatColor.WHITE + " - Recarrega config");

        player.sendMessage(ChatColor.YELLOW + "/clan admin alertar <tag> [player] <tipo> <mensagem>" + ChatColor.WHITE + " - Adiciona alerta");

        player.sendMessage(ChatColor.YELLOW + "/clan admin removealert <alerta_id>" + ChatColor.WHITE + " - Remove alerta");

        player.sendMessage(ChatColor.YELLOW + "/clan admin addwin <tag> <evento> [pontos]" + ChatColor.WHITE + " - Adiciona vitória");

        player.sendMessage(ChatColor.YELLOW + "/clan admin addpoints <tag> <pontos>" + ChatColor.WHITE + " - Adiciona pontos");

        player.sendMessage(ChatColor.YELLOW + "/clan admin removepoints <tag> <pontos>" + ChatColor.WHITE + " - Remove pontos");

    }



    /**

     * Mostra ajuda do comando /clan

     */

    private void sendHelp(Player player) {

        player.sendMessage(ChatColor.GOLD + "=== Comandos de Clan ===");

        player.sendMessage(ChatColor.YELLOW + "/clan criar <nome> <tag>" + ChatColor.WHITE + " - Cria um clan");

        player.sendMessage(ChatColor.YELLOW + "/clan info [tag]" + ChatColor.WHITE + " - Informações do clan");

        player.sendMessage(ChatColor.YELLOW + "/clan membros" + ChatColor.WHITE + " - Lista membros");

        player.sendMessage(ChatColor.YELLOW + "/clan convidar <player>" + ChatColor.WHITE + " - Convida player");

        player.sendMessage(ChatColor.YELLOW + "/clan aceitar [tag]" + ChatColor.WHITE + " - Aceita convite");

        player.sendMessage(ChatColor.YELLOW + "/clan sair" + ChatColor.WHITE + " - Sai do clan");

        player.sendMessage(ChatColor.YELLOW + "/clan banco" + ChatColor.WHITE + " - Ver saldo do banco");

        player.sendMessage(ChatColor.YELLOW + "/clan depositar <valor>" + ChatColor.WHITE + " - Deposita dinheiro");

        player.sendMessage(ChatColor.YELLOW + "/clan sacar <valor>" + ChatColor.WHITE + " - Saca dinheiro");


        // Comandos de territórios (se Factions estiver disponível)
        if (territoryHandler.isFactionsEnabled()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "=== Territórios ===");
            player.sendMessage(ChatColor.YELLOW + "/clan territorio" + ChatColor.WHITE + " - Info completa de territórios");
            player.sendMessage(ChatColor.YELLOW + "/clan claim" + ChatColor.WHITE + " - Conquistar território");
            player.sendMessage(ChatColor.YELLOW + "/clan unclaim" + ChatColor.WHITE + " - Abandonar território");
            player.sendMessage(ChatColor.YELLOW + "/clan mapa" + ChatColor.WHITE + " - Ver mapa de territórios");
            player.sendMessage(ChatColor.YELLOW + "/clan power" + ChatColor.WHITE + " - Ver power do clan");
            player.sendMessage(ChatColor.YELLOW + "/clan shield [horas]" + ChatColor.WHITE + " - Ver/ativar shield");
            player.sendMessage(ChatColor.YELLOW + "/clan upgrade" + ChatColor.WHITE + " - Menu de upgrades");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/f" + ChatColor.GRAY + " para comandos rápidos");
        }
    }



    /**

     * /clan promover <player>

     */

    private boolean handlePromover(Player player, String[] args) {

        if (args.length < 2) {

            player.sendMessage(ChatColor.RED + "Uso: /clan promover <player>");

            return true;

        }



        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        String role = plugin.getClansManager().getMemberRole(clan.getId(), player.getUniqueId());

        if (!role.equals("LEADER") && !role.equals("OFFICER")) {

            player.sendMessage(ChatColor.RED + "Apenas líderes e oficiais podem promover membros.");

            return true;

        }



        PlayerData targetData = CoreAPI.getPlayerByName(args[1]);

        if (targetData == null) {

            player.sendMessage(ChatColor.RED + "Player não encontrado: " + args[1]);

            return true;

        }



        String currentRole = plugin.getClansManager().getMemberRole(clan.getId(), targetData.getUuid());

        if (currentRole == null) {

            player.sendMessage(ChatColor.RED + "Este player não está no seu clan.");

            return true;

        }



        String newRole;

        if (currentRole.equals("RECRUIT")) {

            newRole = "MEMBER";

        } else if (currentRole.equals("MEMBER")) {

            newRole = "OFFICER";

        } else {

            player.sendMessage(ChatColor.RED + "Este player já está no rank máximo.");

            return true;

        }



        if (plugin.getClansManager().updateMemberRole(clan.getId(), targetData.getUuid(), newRole)) {

            player.sendMessage(ChatColor.GREEN + targetData.getName() + " foi promovido para " + newRole + "!");

            Player targetPlayer = Bukkit.getPlayer(targetData.getUuid());

            if (targetPlayer != null) {

                targetPlayer.sendMessage(ChatColor.GREEN + "Você foi promovido para " + newRole + "!");

            }

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao promover player.");

        }



        return true;

    }



    /**

     * /clan rebaixar <player>

     */

    private boolean handleRebaixar(Player player, String[] args) {

        if (args.length < 2) {

            player.sendMessage(ChatColor.RED + "Uso: /clan rebaixar <player>");

            return true;

        }



        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        String role = plugin.getClansManager().getMemberRole(clan.getId(), player.getUniqueId());

        if (!role.equals("LEADER") && !role.equals("OFFICER")) {

            player.sendMessage(ChatColor.RED + "Apenas líderes e oficiais podem rebaixar membros.");

            return true;

        }



        PlayerData targetData = CoreAPI.getPlayerByName(args[1]);

        if (targetData == null) {

            player.sendMessage(ChatColor.RED + "Player não encontrado: " + args[1]);

            return true;

        }



        String currentRole = plugin.getClansManager().getMemberRole(clan.getId(), targetData.getUuid());

        if (currentRole == null) {

            player.sendMessage(ChatColor.RED + "Este player não está no seu clan.");

            return true;

        }



        String newRole;

        if (currentRole.equals("OFFICER")) {

            newRole = "MEMBER";

        } else if (currentRole.equals("MEMBER")) {

            newRole = "RECRUIT";

        } else {

            player.sendMessage(ChatColor.RED + "Este player já está no rank mínimo.");

            return true;

        }



        if (plugin.getClansManager().updateMemberRole(clan.getId(), targetData.getUuid(), newRole)) {

            player.sendMessage(ChatColor.GREEN + targetData.getName() + " foi rebaixado para " + newRole + "!");

            Player targetPlayer = Bukkit.getPlayer(targetData.getUuid());

            if (targetPlayer != null) {

                targetPlayer.sendMessage(ChatColor.YELLOW + "Você foi rebaixado para " + newRole + ".");

            }

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao rebaixar player.");

        }



        return true;

    }



    /**

     * /clan transferir <player>

     */

    private boolean handleTransferir(Player player, String[] args) {

        if (args.length < 2) {

            player.sendMessage(ChatColor.RED + "Uso: /clan transferir <player>");

            return true;

        }



        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        String role = plugin.getClansManager().getMemberRole(clan.getId(), player.getUniqueId());

        if (!role.equals("LEADER")) {

            player.sendMessage(ChatColor.RED + "Apenas o líder pode transferir liderança.");

            return true;

        }



        PlayerData targetData = CoreAPI.getPlayerByName(args[1]);

        if (targetData == null) {

            player.sendMessage(ChatColor.RED + "Player não encontrado: " + args[1]);

            return true;

        }



        if (plugin.getClansManager().transferLeadership(clan.getId(), targetData.getUuid())) {

            player.sendMessage(ChatColor.GREEN + "Liderança transferida para " + targetData.getName() + "!");

            Player targetPlayer = Bukkit.getPlayer(targetData.getUuid());

            if (targetPlayer != null) {

                targetPlayer.sendMessage(ChatColor.GREEN + "Você é o novo líder do clan " + clan.getName() + "!");

            }

        } else {

            player.sendMessage(ChatColor.RED + "Erro ao transferir liderança.");

        }



        return true;

    }



    /**

     * /clan home

     */

    private boolean handleHome(Player player, String[] args) {

        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        if (clan.getHomeWorld() == null || clan.getHomeX() == null || clan.getHomeY() == null || clan.getHomeZ() == null) {

            player.sendMessage(ChatColor.RED + "O clan não tem home definida.");

            return true;

        }



        World world = Bukkit.getWorld(clan.getHomeWorld());

        if (world == null) {

            player.sendMessage(ChatColor.RED + "Mundo do home não encontrado.");

            return true;

        }



        Location home = new Location(world, clan.getHomeX(), clan.getHomeY(), clan.getHomeZ());

        player.teleport(home);

        player.sendMessage(ChatColor.GREEN + "Teleportado para o home do clan!");



        return true;

    }



    /**

     * /clan stats

     */

    private boolean handleStats(Player player, String[] args) {

        ClanData clan = plugin.getClansManager().getClanByMember(player.getUniqueId());

        if (clan == null) {

            player.sendMessage(ChatColor.RED + "Você não está em um clan.");

            return true;

        }



        player.sendMessage(ChatColor.GOLD + "=== Estatísticas de " + ChatColor.YELLOW + clan.getName() + ChatColor.GOLD + " ===");

        player.sendMessage(ChatColor.YELLOW + "Pontos: " + ChatColor.WHITE + clan.getPoints());

        player.sendMessage(ChatColor.YELLOW + "Vitórias em Eventos: " + ChatColor.WHITE + (clan.getEventWinsCount() != null ? clan.getEventWinsCount() : 0));



        try {

            double avgElo = plugin.getClansManager().getClanAverageElo(clan.getId());

            player.sendMessage(ChatColor.YELLOW + "ELO Médio: " + ChatColor.WHITE + String.format("%.0f", avgElo));

        } catch (Exception e) {

            // Ignorar erro

        }



        return true;

    }



    /**

     * /clan admin <subcomando>

     */

    private boolean handleAdmin(Player player, String[] args) {

        if (!player.hasPermission("primeleague.clans.admin")) {

            player.sendMessage(ChatColor.RED + "Você não tem permissão para usar este comando.");

            return true;

        }



        if (args.length < 2) {

            sendAdminHelp(player);

            return true;

        }



        String subCmd = args[1].toLowerCase();

        switch (subCmd) {

            case "set":

                return handleAdminSet(player, args);

            case "reload":

                return handleAdminReload(player);

            case "alertar":

                return handleAdminAlertar(player, args);

            case "removealert":

                return handleAdminRemovealert(player, args);

            case "addwin":

                return handleAdminAddwin(player, args);

            case "addpoints":

                return handleAdminAddpoints(player, args);

            case "removepoints":

                return handleAdminRemovepoints(player, args);

            default:

                sendAdminHelp(player);

                return true;

        }

    }



    /**

     * Obtém display do role (com cor)

     */

    private String getRoleDisplay(String role) {

        if (role == null) return ChatColor.GRAY + "[?]";

        switch (role.toUpperCase()) {

            case "LEADER":

                return ChatColor.RED + "[Líder]";

            case "OFFICER":

                return ChatColor.GOLD + "[Oficial]";

            case "MEMBER":

                return ChatColor.GREEN + "[Membro]";

            case "RECRUIT":

                return ChatColor.GRAY + "[Recruta]";

            default:

                return ChatColor.GRAY + "[" + role + "]";

        }

    }



    /**

     * Helper methods para EconomyAPI (soft dependency via reflection)

     */

    private boolean isEconomyEnabled() {

        try {

            org.bukkit.plugin.Plugin economyPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueEconomy");

            if (economyPlugin == null || !economyPlugin.isEnabled()) {

                return false;

            }

            Class<?> economyAPIClass = Class.forName("com.primeleague.economy.EconomyAPI");

            java.lang.reflect.Method isEnabledMethod = economyAPIClass.getMethod("isEnabled");

            return (Boolean) isEnabledMethod.invoke(null);

        } catch (Exception e) {

            return false;

        }

    }



    private boolean economyHasMoney(UUID uuid, double amount) {

        try {

            Class<?> economyAPIClass = Class.forName("com.primeleague.economy.EconomyAPI");

            java.lang.reflect.Method hasMoneyMethod = economyAPIClass.getMethod("hasMoney", UUID.class, double.class);

            return (Boolean) hasMoneyMethod.invoke(null, uuid, amount);

        } catch (Exception e) {

            return false;

        }

    }



    private void economyRemoveMoney(UUID uuid, double amount, String reason) {

        try {

            Class<?> economyAPIClass = Class.forName("com.primeleague.economy.EconomyAPI");

            java.lang.reflect.Method removeMoneyMethod = economyAPIClass.getMethod("removeMoney", UUID.class, double.class, String.class);

            removeMoneyMethod.invoke(null, uuid, amount, reason);

        } catch (Exception e) {

            // Ignorar erro

        }

    }



    private void economyAddMoney(UUID uuid, double amount, String reason) {

        try {

            Class<?> economyAPIClass = Class.forName("com.primeleague.economy.EconomyAPI");

            java.lang.reflect.Method addMoneyMethod = economyAPIClass.getMethod("addMoney", UUID.class, double.class, String.class);

            addMoneyMethod.invoke(null, uuid, amount, reason);

        } catch (Exception e) {

            // Ignorar erro

        }

    }

}



