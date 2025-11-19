package com.primeleague.essentials;

import com.primeleague.core.CoreAPI;
import com.primeleague.essentials.database.DatabaseSetup;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PrimeLeague Essentials
 * Grug Brain: O básico bem feito.
 */
public class EssentialsPlugin extends JavaPlugin {

    private static EssentialsPlugin instance;

    @Override
    public void onEnable() {
        instance = this;

        // Verificar dependência do Core
        if (!CoreAPI.isEnabled()) {
            getLogger().severe("PrimeleagueCore não encontrado! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        // Inicializar Database
        if (!DatabaseSetup.initTables()) {
            getLogger().severe("Erro ao criar tabelas! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Registrar Comandos
        getCommand("tpa").setExecutor(new com.primeleague.essentials.commands.TpaCommand());
        getCommand("tpaccept").setExecutor(new com.primeleague.essentials.commands.TpAcceptCommand());
        getCommand("tpdeny").setExecutor(new com.primeleague.essentials.commands.TpDenyCommand());
        getCommand("back").setExecutor(new com.primeleague.essentials.commands.BackCommand());
        getCommand("top").setExecutor(new com.primeleague.essentials.commands.TopCommand());
        getCommand("near").setExecutor(new com.primeleague.essentials.commands.NearCommand());
        
        getCommand("sethome").setExecutor(new com.primeleague.essentials.commands.SetHomeCommand());
        getCommand("home").setExecutor(new com.primeleague.essentials.commands.HomeCommand());
        getCommand("delhome").setExecutor(new com.primeleague.essentials.commands.DelHomeCommand());
        getCommand("homes").setExecutor(new com.primeleague.essentials.commands.HomesCommand());
        
        getCommand("setspawn").setExecutor(new com.primeleague.essentials.commands.SetSpawnCommand());
        getCommand("spawn").setExecutor(new com.primeleague.essentials.commands.SpawnCommand());
        
        getCommand("setwarp").setExecutor(new com.primeleague.essentials.commands.SetWarpCommand());
        getCommand("warp").setExecutor(new com.primeleague.essentials.commands.WarpCommand());
        getCommand("delwarp").setExecutor(new com.primeleague.essentials.commands.DelWarpCommand());
        getCommand("warps").setExecutor(new com.primeleague.essentials.commands.WarpsCommand());
        
        getCommand("fly").setExecutor(new com.primeleague.essentials.commands.FlyCommand());
        getCommand("heal").setExecutor(new com.primeleague.essentials.commands.HealCommand());
        getCommand("feed").setExecutor(new com.primeleague.essentials.commands.FeedCommand());
        getCommand("invsee").setExecutor(new com.primeleague.essentials.commands.InvSeeCommand());
        getCommand("enderchest").setExecutor(new com.primeleague.essentials.commands.EnderChestCommand());
        getCommand("hat").setExecutor(new com.primeleague.essentials.commands.HatCommand());
        getCommand("ptime").setExecutor(new com.primeleague.essentials.commands.PTimeCommand());
        getCommand("pweather").setExecutor(new com.primeleague.essentials.commands.PWeatherCommand());
        getCommand("workbench").setExecutor(new com.primeleague.essentials.commands.WorkbenchCommand());
        getCommand("anvil").setExecutor(new com.primeleague.essentials.commands.AnvilCommand());
        getCommand("sudo").setExecutor(new com.primeleague.essentials.commands.SudoCommand());
        getCommand("gamemode").setExecutor(new com.primeleague.essentials.commands.GamemodeCommand());
        
        getCommand("createkit").setExecutor(new com.primeleague.essentials.commands.CreateKitCommand());
        getCommand("kit").setExecutor(new com.primeleague.essentials.commands.KitCommand());

        // Registrar Listeners
        getServer().getPluginManager().registerEvents(new com.primeleague.essentials.listeners.SignListener(), this);
        getServer().getPluginManager().registerEvents(new com.primeleague.essentials.listeners.EssentialsListener(), this);

        getLogger().info("PrimeleagueEssentials habilitado com sucesso!");
    }

    @Override
    public void onDisable() {
        getLogger().info("PrimeleagueEssentials desabilitado.");
    }

    public static EssentialsPlugin getInstance() {
        return instance;
    }
}
