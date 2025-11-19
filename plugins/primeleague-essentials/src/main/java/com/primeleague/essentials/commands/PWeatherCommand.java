package com.primeleague.essentials.commands;

import org.bukkit.ChatColor;
import org.bukkit.WeatherType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PWeatherCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("essentials.pweather")) {
            player.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Uso: /pweather <sun|rain|reset>");
            return true;
        }

        String weather = args[0].toLowerCase();
        if (weather.equals("sun") || weather.equals("sol") || weather.equals("clear")) {
            player.setPlayerWeather(WeatherType.CLEAR);
            player.sendMessage(ChatColor.YELLOW + "Clima pessoal definido para sol.");
        } else if (weather.equals("rain") || weather.equals("chuva") || weather.equals("storm")) {
            player.setPlayerWeather(WeatherType.DOWNFALL);
            player.sendMessage(ChatColor.YELLOW + "Clima pessoal definido para chuva.");
        } else if (weather.equals("reset")) {
            player.resetPlayerWeather();
            player.sendMessage(ChatColor.YELLOW + "Clima pessoal resetado.");
        } else {
            player.sendMessage(ChatColor.RED + "Clima inválido.");
        }

        return true;
    }
}
