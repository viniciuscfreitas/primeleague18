package com.primeleague.factions;

import com.primeleague.clans.ClansPlugin;
import com.primeleague.core.CoreAPI;
import com.primeleague.factions.command.FactionsCommand;
import com.primeleague.factions.integrations.DiscordIntegration;
import com.primeleague.factions.integrations.DynmapIntegration;
import com.primeleague.factions.listener.FlyListener;
import com.primeleague.factions.listener.GenBucketListener;
import com.primeleague.factions.listener.ProtectionListener;
import com.primeleague.factions.manager.ClaimManager;
import com.primeleague.factions.manager.FlyManager;
import com.primeleague.factions.manager.PowerManager;
import com.primeleague.factions.manager.UpgradeManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class PrimeFactions extends JavaPlugin {

    private static PrimeFactions instance;
    private ClaimManager claimManager;
    private PowerManager powerManager;
    private DiscordIntegration discordIntegration;
    private DynmapIntegration dynmapIntegration;
    private FlyManager flyManager;
    private UpgradeManager upgradeManager;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Verify Dependencies
        if (!CoreAPI.isEnabled()) {
            getLogger().severe("PrimeleagueCore não encontrado! Desabilitando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().getPlugin("PrimeleagueClans") == null) {
            getLogger().severe("PrimeleagueClans não encontrado! Desabilitando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Load Config
        saveDefaultConfig();

        // 3. Setup Database
        setupDatabase();

        // 4. Initialize Managers
        this.claimManager = new ClaimManager(this);
        this.powerManager = new PowerManager(this);
        this.discordIntegration = new DiscordIntegration(this);
        this.dynmapIntegration = new DynmapIntegration(this);
        this.flyManager = new FlyManager(this);
        this.upgradeManager = new UpgradeManager(this);

        // 4.1. Setup Dynmap Integration (soft dependency)
        this.dynmapIntegration.setup();

        // 5. Register Listeners
        getServer().getPluginManager().registerEvents(this.powerManager, this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GenBucketListener(this), this);
        getServer().getPluginManager().registerEvents(new FlyListener(this), this);
        getServer().getPluginManager().registerEvents(new com.primeleague.factions.listener.UpgradeGUIListener(this), this);

        // 6. Register Commands
        getCommand("f").setExecutor(new FactionsCommand(this));

        // 7. Clean Discord rate limit cache periodically (every 5 minutes)
        if (discordIntegration != null && discordIntegration.isDiscordEnabled()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    discordIntegration.cleanRateLimitCache();
                }
            }.runTaskTimerAsynchronously(this, 6000L, 6000L); // 5 minutes = 6000 ticks
        }

        getLogger().info("PrimeleagueFactions (Legendary Edition) habilitado!");
    }

    @Override
    public void onDisable() {
        // Cleanup Dynmap
        if (dynmapIntegration != null) {
            dynmapIntegration.cleanup();
        }

        // Salvar solo builds no banco
        if (claimManager != null) {
            claimManager.saveSoloBuilds();
        }

        getLogger().info("PrimeleagueFactions desabilitado.");
    }

    public static PrimeFactions getInstance() {
        return instance;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public PowerManager getPowerManager() {
        return powerManager;
    }

    public ClansPlugin getClansPlugin() {
        return ClansPlugin.getInstance();
    }

    public DiscordIntegration getDiscordIntegration() {
        return discordIntegration;
    }

    public FlyManager getFlyManager() {
        return flyManager;
    }

    public DynmapIntegration getDynmapIntegration() {
        return dynmapIntegration;
    }

    public UpgradeManager getUpgradeManager() {
        return upgradeManager;
    }

    private void setupDatabase() {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Power Columns (in users table)
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN power DOUBLE PRECISION DEFAULT 50.0");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE users ADD COLUMN max_power DOUBLE PRECISION DEFAULT 50.0");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE users ADD COLUMN last_power_regen TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            } catch (SQLException ignored) {}

            // 2. Faction Claims Table
            stmt.execute("CREATE TABLE IF NOT EXISTS faction_claims (" +
                    "world VARCHAR(50) NOT NULL, " +
                    "x INT NOT NULL, " +
                    "z INT NOT NULL, " +
                    "clan_id INT NOT NULL, " +
                    "PRIMARY KEY (world, x, z), " +
                    "FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE" +
                    ")");

            // 3. Index for fast lookups
            try {
                stmt.execute("CREATE INDEX idx_clan_claims ON faction_claims(clan_id)");
            } catch (SQLException ignored) {}

            // 4. Upgrades Table
            stmt.execute("CREATE TABLE IF NOT EXISTS faction_upgrades (" +
                    "clan_id INT PRIMARY KEY, " +
                    "spawner_rate INT DEFAULT 0, " +
                    "crop_growth INT DEFAULT 0, " +
                    "exp_boost INT DEFAULT 0, " +
                    "extra_shield_hours INT DEFAULT 0, " +
                    "FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE" +
                    ")");

            // 5. Solo Builds Table
            stmt.execute("CREATE TABLE IF NOT EXISTS solo_builds (" +
                    "world VARCHAR(50) NOT NULL, " +
                    "x INT NOT NULL, " +
                    "z INT NOT NULL, " +
                    "owner_uuid VARCHAR(36) NOT NULL, " +
                    "PRIMARY KEY (world, x, z)" +
                    ")");

            // 6. Index for solo builds lookups
            try {
                stmt.execute("CREATE INDEX idx_solo_builds_owner ON solo_builds(owner_uuid)");
            } catch (SQLException ignored) {}

            getLogger().info("Banco de dados configurado com sucesso.");

        } catch (SQLException e) {
            getLogger().severe("Erro ao configurar banco de dados: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
