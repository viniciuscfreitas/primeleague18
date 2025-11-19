package com.primeleague.league.commands;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.clans.managers.ClansManager;
import com.primeleague.core.CoreAPI;
import com.primeleague.league.LeaguePlugin;
import com.primeleague.league.LeagueAPI;
import com.primeleague.league.models.RankingEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Comandos de temporada e rankings
 * Grug Brain: Queries diretas, cache simples
 */
public class LeagueCommand implements CommandExecutor {

    private final LeaguePlugin plugin;

    public LeagueCommand(LeaguePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            return handleTemporada(sender);
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "pontos":
                return handlePontos(sender, args);
            case "top":
                return handleTop(sender, args);
            case "reset":
                return handleReset(sender, args);
            case "penalidade":
                return handlePenalidade(sender, args);
            case "softdelete":
                return handleSoftDelete(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "Uso: /temporada [pontos|top|reset|penalidade|softdelete]");
                return true;
        }
    }

    /**
     * Comando /temporada - Mostra ranking completo
     */
    private boolean handleTemporada(CommandSender sender) {
        final CommandSender finalSender = sender;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                // Top 10 clans por pontos
                List<RankingEntry> topClans = LeagueAPI.getClanRankingByPoints(10);

                // Top 20 players por kills
                List<RankingEntry> topPlayers = LeagueAPI.getPlayerRankingByKills(20);

                // Voltar à thread principal para enviar mensagens
                final List<RankingEntry> finalTopClans = topClans;
                final List<RankingEntry> finalTopPlayers = topPlayers;
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        finalSender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + ChatColor.BOLD + "RANKING TEMPORADA" + ChatColor.GOLD + " ===");

                        // Top Clans
                        finalSender.sendMessage(ChatColor.YELLOW + "--- Top Clans (Pontos) ---");
                        if (finalTopClans.isEmpty()) {
                            finalSender.sendMessage(ChatColor.GRAY + "Nenhum clan com pontos ainda!");
                        } else {
                            for (RankingEntry entry : finalTopClans) {
                                int clanId = Integer.parseInt(entry.getEntityId());
                                String clanName = getClanName(clanId);
                                finalSender.sendMessage(ChatColor.YELLOW + "#" + entry.getPosition() + " " +
                                    ChatColor.WHITE + clanName + " " +
                                    ChatColor.GRAY + "(" + (int)entry.getValue() + " pts)");
                            }
                        }

                        finalSender.sendMessage("");

                        // Top Players
                        finalSender.sendMessage(ChatColor.YELLOW + "--- Top Players (Kills) ---");
                        if (finalTopPlayers.isEmpty()) {
                            finalSender.sendMessage(ChatColor.GRAY + "Nenhum player com kills ainda!");
                        } else {
                            for (RankingEntry entry : finalTopPlayers) {
                                UUID playerUuid = UUID.fromString(entry.getEntityId());
                                String playerName = getPlayerName(playerUuid);
                                finalSender.sendMessage(ChatColor.YELLOW + "#" + entry.getPosition() + " " +
                                    ChatColor.WHITE + playerName + " " +
                                    ChatColor.GRAY + "(" + (int)entry.getValue() + " kills)");
                            }
                        }
                    }
                });
            }
        });

        return true;
    }

    /**
     * Comando /pontos <clan/player> <nome> - Mostra pontos específicos
     */
    private boolean handlePontos(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /pontos <clan/player> <nome>");
            return true;
        }

        String type = args[1].toLowerCase();
        String name = args[2];

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int points = 0;
            String entityName = name;

            if (type.equals("clan")) {
                // Buscar clan por tag
                ClansManager clansManager = ClansPlugin.getInstance().getClansManager();
                com.primeleague.clans.models.ClanData clan = clansManager.getClanByTag(name);
                if (clan != null) {
                    points = LeagueAPI.getPoints("CLAN", String.valueOf(clan.getId()));
                    entityName = clan.getName() + " [" + clan.getTag() + "]";
                } else {
                    entityName = null;
                }
            } else if (type.equals("player")) {
                // Buscar player por nome
                Player target = Bukkit.getPlayer(name);
                if (target != null) {
                    points = LeagueAPI.getPoints("PLAYER", target.getUniqueId().toString());
                    entityName = target.getName();
                } else {
                    // Tentar buscar por UUID via CoreAPI
                    com.primeleague.core.models.PlayerData data = CoreAPI.getPlayerByName(name);
                    if (data != null) {
                        points = LeagueAPI.getPoints("PLAYER", data.getUuid().toString());
                        entityName = data.getName();
                    } else {
                        entityName = null;
                    }
                }
            } else {
                entityName = null;
            }

            final int finalPoints = points;
            final String finalEntityName = entityName;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (finalEntityName == null) {
                    sender.sendMessage(ChatColor.RED + (type.equals("clan") ? "Clan" : "Player") + " não encontrado: " + name);
                    return;
                }

                sender.sendMessage(ChatColor.GOLD + "=== Pontos de " + finalEntityName + " ===");
                sender.sendMessage(ChatColor.YELLOW + "Pontos: " + ChatColor.WHITE + finalPoints);
            });
        });

        return true;
    }

    /**
     * Comando /top [kills|points|elo] - Ranking customizado
     */
    private boolean handleTop(CommandSender sender, String[] args) {
        String metric = "kills";
        if (args.length > 1) {
            metric = args[1].toLowerCase();
        }

        final String finalMetric = metric;
        final CommandSender finalSender = sender;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                List<RankingEntry> rankings;

                switch (finalMetric) {
                    case "kills":
                        rankings = LeagueAPI.getPlayerRankingByKills(10);
                        break;
                    case "points":
                        rankings = LeagueAPI.getClanRankingByPoints(10);
                        break;
                    default:
                        rankings = LeagueAPI.getPlayerRankingByKills(10);
                        break;
                }

                final List<RankingEntry> finalRankings = rankings;

                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        String metricName = finalMetric.equals("kills") ? "KILLS" :
                                           (finalMetric.equals("points") ? "PONTOS" : "KILLS");

                        finalSender.sendMessage(ChatColor.GOLD + "=== TOP " + metricName + " ===");

                        if (finalRankings.isEmpty()) {
                            finalSender.sendMessage(ChatColor.GRAY + "Nenhum resultado ainda!");
                        } else {
                            for (RankingEntry entry : finalRankings) {
                                String entityName;
                                if (entry.getEntityType().equals("CLAN")) {
                                    int clanId = Integer.parseInt(entry.getEntityId());
                                    entityName = getClanName(clanId);
                                } else {
                                    UUID playerUuid = UUID.fromString(entry.getEntityId());
                                    entityName = getPlayerName(playerUuid);
                                }

                                finalSender.sendMessage(ChatColor.YELLOW + "#" + entry.getPosition() + " " +
                                    ChatColor.WHITE + entityName + " " +
                                    ChatColor.GRAY + "(" + (int)entry.getValue() + ")");
                            }
                        }
                    }
                });
            }
        });

        return true;
    }

    /**
     * Obtém nome do clan (com cache)
     */
    private String getClanName(int clanId) {
        ClansManager clansManager = ClansPlugin.getInstance().getClansManager();
        com.primeleague.clans.models.ClanData clan = clansManager.getClan(clanId);
        if (clan != null) {
            return clan.getName() + " [" + clan.getTag() + "]";
        }
        return "Clan #" + clanId;
    }

    /**
     * Obtém nome do player
     */
    private String getPlayerName(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            return player.getName();
        }

        com.primeleague.core.models.PlayerData data = CoreAPI.getPlayer(playerUuid);
        if (data != null) {
            return data.getName();
        }

        return "Player " + playerUuid.toString().substring(0, 8);
    }

    /**
     * Comando /temporada reset <season_id> - Reseta temporada (admin)
     */
    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("league.admin.reset")) {
            sender.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /temporada reset <season_id>");
            return true;
        }

        try {
            int seasonId = Integer.parseInt(args[1]);
            LeagueAPI.resetSeason(seasonId);
            sender.sendMessage(ChatColor.GREEN + "Temporada " + seasonId + " resetada com sucesso!");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Season ID inválido: " + args[1]);
        }

        return true;
    }

    /**
     * Comando /temporada penalidade <clan/player> <nome> <pontos> <motivo> - Penaliza (admin)
     */
    private boolean handlePenalidade(CommandSender sender, String[] args) {
        if (!sender.hasPermission("league.admin.penalty")) {
            sender.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Uso: /temporada penalidade <clan/player> <nome> <pontos> <motivo>");
            return true;
        }

        String type = args[1].toLowerCase();
        String name = args[2];
        int points;
        try {
            points = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Pontos inválidos: " + args[3]);
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
        UUID adminUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = false;
            String entityName = name;

            if (type.equals("clan")) {
                ClansManager clansManager = ClansPlugin.getInstance().getClansManager();
                com.primeleague.clans.models.ClanData clan = clansManager.getClanByTag(name);
                if (clan != null) {
                    LeagueAPI.penalize("CLAN", String.valueOf(clan.getId()), points, reason, adminUuid);
                    success = true;
                    entityName = clan.getName() + " [" + clan.getTag() + "]";
                }
            } else if (type.equals("player")) {
                Player target = Bukkit.getPlayer(name);
                if (target != null) {
                    LeagueAPI.penalize("PLAYER", target.getUniqueId().toString(), points, reason, adminUuid);
                    success = true;
                    entityName = target.getName();
                } else {
                    com.primeleague.core.models.PlayerData data = CoreAPI.getPlayerByName(name);
                    if (data != null) {
                        LeagueAPI.penalize("PLAYER", data.getUuid().toString(), points, reason, adminUuid);
                        success = true;
                        entityName = data.getName();
                    }
                }
            }

            final boolean finalSuccess = success;
            final String finalEntityName = entityName;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (finalSuccess) {
                    sender.sendMessage(ChatColor.GREEN + "Penalidade aplicada: " + finalEntityName + " -" + points + " pontos (" + reason + ")");
                } else {
                    sender.sendMessage(ChatColor.RED + (type.equals("clan") ? "Clan" : "Player") + " não encontrado: " + name);
                }
            });
        });

        return true;
    }

    /**
     * Comando /temporada softdelete <event_id> - Soft-delete evento (admin)
     */
    private boolean handleSoftDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("league.admin.softdelete")) {
            sender.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /temporada softdelete <event_id>");
            return true;
        }

        try {
            long eventId = Long.parseLong(args[1]);
            UUID adminUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
            LeagueAPI.softDeleteEvent(eventId, adminUuid);
            sender.sendMessage(ChatColor.GREEN + "Evento " + eventId + " marcado como deletado.");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Event ID inválido: " + args[1]);
        }

        return true;
    }
}

