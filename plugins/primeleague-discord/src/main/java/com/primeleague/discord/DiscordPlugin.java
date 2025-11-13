package com.primeleague.discord;

import com.primeleague.discord.bot.DiscordBot;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin Discord Bot - Primeleague
 * Grug Brain: Plugin simples, depende do Core
 */
public class DiscordPlugin extends JavaPlugin {

    private static DiscordPlugin instance;
    private DiscordBot discordBot;

    @Override
    public void onEnable() {
        instance = this;

        // Verificar se Core está habilitado
        if (getServer().getPluginManager().getPlugin("PrimeleagueCore") == null) {
            getLogger().severe("PrimeleagueCore não encontrado! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Salvar config padrão se não existir
        saveDefaultConfig();

        // Inicializar Discord Bot
        String token = getConfig().getString("discord.bot-token");
        if (token == null || token.equals("YOUR_BOT_TOKEN_HERE")) {
            getLogger().warning("Token do Discord Bot não configurado! Configure em config.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        discordBot = new DiscordBot(this, token);
        if (!discordBot.initialize()) {
            getLogger().severe("Falha ao inicializar Discord Bot. Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("PrimeleagueDiscord habilitado");
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.shutdown();
        }
        getLogger().info("PrimeleagueDiscord desabilitado");
    }

    public static DiscordPlugin getInstance() {
        return instance;
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }
}

