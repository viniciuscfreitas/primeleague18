package com.primeleague.gladiador.commands;

import com.primeleague.gladiador.integrations.ClansAPI;
import com.primeleague.gladiador.GladiadorPlugin;
import com.primeleague.gladiador.managers.MatchManager;
import com.primeleague.gladiador.models.Arena;
import com.primeleague.gladiador.models.GladiadorMatch;
import com.primeleague.gladiador.models.GladiadorStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GladiadorCommand implements CommandExecutor, TabCompleter {

    private final GladiadorPlugin plugin;

    public GladiadorCommand(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores podem usar este comando.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            handleJoin(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "iniciar":
                handleStart(player);
                break;
            case "cancelar":
                handleCancel(player);
                break;
            case "setarena":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Uso: /gladiador setarena <nome>");
                    return true;
                }
                handleSetArena(player, args[1]);
                break;
            case "setspawn":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Uso: /gladiador setspawn <arena>");
                    return true;
                }
                handleSetSpawn(player, args[1]);
                break;
            case "sair":
                handleLeave(player);
                break;
            case "spectator":
                handleSpectator(player);
                break;
            case "setexitspawn":
                handleSetExitSpawn(player);
                break;
            case "top":
                handleTop(player);
                break;
            case "tabela":
                // Redirecionar para top (consolidado)
                handleTop(player);
                break;
            case "help":
            case "ajuda":
                handleHelp(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Comando desconhecido. Use /gladiador help");
                break;
        }

        return true;
    }

    private void handleJoin(Player player) {
        MatchManager matchManager = plugin.getMatchManager();
        GladiadorMatch match = matchManager.getCurrentMatch();

        if (match == null || match.getState() != GladiadorMatch.MatchState.WAITING) {
            player.sendMessage(ChatColor.RED + "O evento Gladiador não está aberto para entrada no momento.");
            return;
        }

        if (!ClansAPI.isEnabled()) {
            player.sendMessage(ChatColor.RED + "Erro: PrimeleagueClans não encontrado!");
            return;
        }
        Object clan = ClansAPI.getClanByMember(player.getUniqueId());

        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Você precisa estar em um clan para participar do Gladiador.");
            return;
        }

        // addPlayer já envia mensagem, não duplicar
        if (!matchManager.addPlayer(player)) {
            player.sendMessage(ChatColor.RED + "Não foi possível entrar no evento. Tente novamente.");
        }
    }

    private void handleLeave(Player player) {
        MatchManager matchManager = plugin.getMatchManager();
        GladiadorMatch match = matchManager.getCurrentMatch();

        if (match == null || !match.hasPlayer(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Você não está em um evento Gladiador.");
            return;
        }

        // Eliminar player do evento
        matchManager.handleDeath(player, null);
        player.sendMessage(ChatColor.YELLOW + "Você saiu do evento Gladiador.");
    }

    private void handleTop(Player player) {
        // Grug Brain: Query async para não bloquear thread principal
        // Consolidado: ordena por pontos de temporada, mostra tudo (pontos, vitórias, kills, win rate)
        plugin.getStatsManager().getTopBySeasonPointsAsync(20, top -> {
            // Verificar se player ainda está online
            if (!player.isOnline()) {
                return;
            }

            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.BOLD + "TABELA DE PONTOS - GLADIADOR" + ChatColor.RESET + ChatColor.GOLD + " ===");
            player.sendMessage("");

            if (top == null || top.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "Nenhum clan participou ainda.");
                return;
            }

            for (int i = 0; i < top.size(); i++) {
                GladiadorStats stats = top.get(i);
                if (stats == null) continue;

                String clanTag = "Clan #" + stats.getClanId();

                // Buscar tag via ClansAPI
                if (ClansAPI.isEnabled()) {
                    Object clanData = ClansAPI.getClan(stats.getClanId());
                    if (clanData != null) {
                        String tag = ClansAPI.getClanTag(clanData);
                        if (tag != null) {
                            clanTag = tag;
                        }
                    }
                }

                String medal = "";
                if (i == 0) medal = ChatColor.GOLD + "[1] ";
                else if (i == 1) medal = ChatColor.GRAY + "[2] ";
                else if (i == 2) medal = ChatColor.RED + "[3] ";

                player.sendMessage(String.format("%s%s%d. %s%s %s- %s%d pontos%s | %s%d vitórias%s | %s%d kills%s | %s%.1f%% WR",
                    medal,
                    ChatColor.YELLOW,
                    i + 1,
                    ChatColor.WHITE,
                    clanTag,
                    ChatColor.GRAY,
                    ChatColor.GREEN,
                    stats.getSeasonPoints(),
                    ChatColor.GRAY,
                    ChatColor.AQUA,
                    stats.getWins(),
                    ChatColor.GRAY,
                    ChatColor.LIGHT_PURPLE,
                    stats.getTotalKills(),
                    ChatColor.GRAY,
                    ChatColor.YELLOW,
                    stats.getWinRate()
                ));
            }
            player.sendMessage("");
        });
    }

    private void handleHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Comandos Gladiador ===");
        player.sendMessage(ChatColor.YELLOW + "/gladiador - Entra no evento (se estiver aberto)");
        player.sendMessage(ChatColor.YELLOW + "/gladiador sair - Sai do evento");
        player.sendMessage(ChatColor.YELLOW + "/gladiador top - Mostra tabela de pontos da temporada");

        if (player.hasPermission("primeleague.admin")) {
            player.sendMessage(ChatColor.RED + "=== Comandos Admin ===");
            player.sendMessage(ChatColor.YELLOW + "/gladiador iniciar - Inicia novo evento");
            player.sendMessage(ChatColor.YELLOW + "/gladiador cancelar - Cancela evento ativo");
            player.sendMessage(ChatColor.YELLOW + "/gladiador setarena <nome> - Cria arena");
            player.sendMessage(ChatColor.YELLOW + "/gladiador setspawn <arena> - Define spawn");
            player.sendMessage(ChatColor.YELLOW + "/gladiador setexitspawn - Define spawn de saída");
            player.sendMessage(ChatColor.YELLOW + "/gladiador spectator - Entra em modo spectator");
        }
    }

    private void handleStart(Player player) {
        if (!player.hasPermission("primeleague.admin")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return;
        }

        if (plugin.getMatchManager().startMatch()) {
            // Mensagem já é enviada via broadcast, não duplicar
        } else {
            player.sendMessage(ChatColor.RED + "Não foi possível iniciar o evento (já existe um ativo ou sem arena disponível).");
        }
    }

    private void handleCancel(Player player) {
        if (!player.hasPermission("primeleague.admin")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return;
        }

        plugin.getMatchManager().cancelMatch();
        player.sendMessage(ChatColor.GREEN + "Evento Gladiador cancelado.");
    }

    private void handleSetArena(Player player, String name) {
        if (!player.hasPermission("primeleague.admin")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return;
        }

        try {
            plugin.getArenaManager().createArena(name, player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Arena " + name + " criada com sucesso!");
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + e.getMessage());
        }
    }

    private void handleSetSpawn(Player player, String arenaName) {
        if (!player.hasPermission("primeleague.admin")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "Arena não encontrada.");
            return;
        }

        // Limpar spawns antigos e definir o novo (single spawn requirement)
        plugin.getArenaManager().setSpawnPoint(arena.getId(), player.getLocation());
        player.sendMessage(ChatColor.GREEN + "Spawn point adicionado à arena " + arenaName + "!");
    }

    private void handleSpectator(Player player) {
        if (!player.hasPermission("primeleague.admin")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return;
        }

        GladiadorMatch match = plugin.getMatchManager().getCurrentMatch();
        if (match == null) {
            player.sendMessage(ChatColor.RED + "Não há Gladiador ativo.");
            return;
        }

        Arena arena = match.getArena();
        Location spectatorLoc = new Location(
            Bukkit.getWorld(arena.getSpectatorWorld()),
            arena.getSpectatorX(), arena.getSpectatorY(), arena.getSpectatorZ(),
            arena.getSpectatorYaw(), arena.getSpectatorPitch()
        );

        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        // Paper 1.8.8: Spectator mode já é invisível por padrão, não precisa setInvisible()
        player.teleport(spectatorLoc);
        player.sendMessage(ChatColor.GREEN + "Modo spectator ativado!");
    }

    private void handleSetExitSpawn(Player player) {
        if (!player.hasPermission("primeleague.admin")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return;
        }

        Location loc = player.getLocation();
        plugin.getConfig().set("spawn.exit-world", loc.getWorld().getName());
        plugin.getConfig().set("spawn.exit-x", loc.getX());
        plugin.getConfig().set("spawn.exit-y", loc.getY());
        plugin.getConfig().set("spawn.exit-z", loc.getZ());
        plugin.getConfig().set("spawn.exit-yaw", (double) loc.getYaw());
        plugin.getConfig().set("spawn.exit-pitch", (double) loc.getPitch());
        plugin.saveConfig();

        player.sendMessage(ChatColor.GREEN + "Spawn de saída configurado!");
        player.sendMessage(ChatColor.GRAY + "Jogadores eliminados serão teleportados para esta localização.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("top");
            completions.add("tabela");
            if (sender.hasPermission("primeleague.admin")) {
                completions.addAll(Arrays.asList("iniciar", "cancelar", "setarena", "setspawn", "setexitspawn", "spectator"));
            }
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) {
            List<String> names = new ArrayList<>();
            for (Arena arena : plugin.getArenaManager().getAllArenas()) {
                names.add(arena.getName());
            }
            return names;
        }
        return Collections.emptyList();
    }
}
