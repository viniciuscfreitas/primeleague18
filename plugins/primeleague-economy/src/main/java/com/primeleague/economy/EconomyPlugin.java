package com.primeleague.economy;

import com.primeleague.core.CoreAPI;
import com.primeleague.economy.commands.DinheiroCommand;
import com.primeleague.economy.commands.EcoAdminCommand;
import com.primeleague.economy.commands.PagarCommand;
import com.primeleague.economy.commands.RicostopCommand;
import com.primeleague.economy.integrations.PlaceholderAPIExpansion;
import com.primeleague.economy.integrations.VaultEconomyProvider;
import com.primeleague.economy.listeners.FarmListener;
import com.primeleague.economy.listeners.LojaGUIListener;
import com.primeleague.economy.listeners.PvPRewardListener;
import com.primeleague.economy.utils.DynamicPricer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin de Economia - Primeleague
 * Grug Brain: Plugin simples, depende do Core via CoreAPI
 */
public class EconomyPlugin extends JavaPlugin implements Listener {

    private static EconomyPlugin instance;
    private EconomyManager economyManager;
    private VaultEconomyProvider vaultProvider;
    private PlaceholderAPIExpansion placeholderExpansion;
    private DynamicPricer dynamicPricer;
    private java.util.Map<String, RicostopCommand.TopCache> ricostopCache;
    private long ricostopCacheDuration;

    @Override
    public void onEnable() {
        instance = this;

        // Verificar se Core está habilitado
        if (!CoreAPI.isEnabled()) {
            getLogger().severe("PrimeleagueCore não encontrado! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Salvar config padrão se não existir
        saveDefaultConfig();

        // Inicializar economy manager
        economyManager = new EconomyManager(this);
        economyManager.startAutoSave();

        // Inicializar cache de ricostop (thread-safe)
        ricostopCache = new java.util.concurrent.ConcurrentHashMap<>();
        ricostopCacheDuration = getConfig().getLong("cache.ricostop-duracao", 300) * 1000; // Converter para ms

        // Criar tabela de transações se não existir
        createTransactionsTable();

        // Criar tabela de dynamic_prices se não existir
        createDynamicPricesTable();

        // Registrar listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PvPRewardListener(this), this);
        getServer().getPluginManager().registerEvents(new FarmListener(this), this);
        getServer().getPluginManager().registerEvents(new LojaGUIListener(this), this);

        // Registrar comandos
        if (getCommand("dinheiro") != null) {
            getCommand("dinheiro").setExecutor(new DinheiroCommand(this));
        }
        if (getCommand("saldo") != null) {
            getCommand("saldo").setExecutor(new DinheiroCommand(this));
        }
        if (getCommand("pagar") != null) {
            getCommand("pagar").setExecutor(new PagarCommand(this));
        }
        if (getCommand("ricostop") != null) {
            getCommand("ricostop").setExecutor(new RicostopCommand(this));
        }
        if (getCommand("eco") != null) {
            getCommand("eco").setExecutor(new EcoAdminCommand(this));
        }
        if (getCommand("loja") != null) {
            getCommand("loja").setExecutor(new com.primeleague.economy.commands.LojaCommand(this));
        }
        if (getCommand("vender") != null) {
            getCommand("vender").setExecutor(new com.primeleague.economy.commands.VenderCommand(this));
        }
        if (getCommand("recompensa") != null) {
            getCommand("recompensa").setExecutor(new com.primeleague.economy.commands.RecompensaCommand(this));
        }
        if (getCommand("leilao") != null) {
            getCommand("leilao").setExecutor(new com.primeleague.economy.commands.LeilaoCommand(this));
        }
        if (getCommand("dynaprice") != null) {
            getCommand("dynaprice").setExecutor(new com.primeleague.economy.commands.DynapriceCommand(this));
        }

        // Setup Vault (se disponível)
        setupVault();

        // Setup PlaceholderAPI (se disponível)
        setupPlaceholderAPI();

        // Inicializar DynamicPricer (se habilitado)
        if (getConfig().getBoolean("economy.precos-dinamicos.habilitado", false)) {
            try {
                dynamicPricer = new DynamicPricer(this, getConfig());

                // Injetar no LojaCommand
                com.primeleague.economy.commands.LojaCommand lojaCmd =
                    (com.primeleague.economy.commands.LojaCommand) getCommand("loja").getExecutor();
                if (lojaCmd != null) {
                    lojaCmd.setDynamicPricer(dynamicPricer);
                }

                getLogger().info("DynamicPricer habilitado");
            } catch (Exception e) {
                getLogger().warning("Erro ao inicializar DynamicPricer: " + e.getMessage());
                e.printStackTrace();
                // Graceful: sistema continua com preços fixos
            }
        }

        getLogger().info("PrimeleagueEconomy habilitado");
    }

    @Override
    public void onDisable() {
        // Desregistrar PlaceholderAPI expansion
        if (placeholderExpansion != null) {
            try {
                placeholderExpansion.unregister();
            } catch (Exception e) {
                // Ignorar erros ao desregistrar
            }
        }

        // Shutdown DynamicPricer (salva estado)
        if (dynamicPricer != null) {
            dynamicPricer.shutdown();
        }

        // Parar auto-save e salvar tudo
        if (economyManager != null) {
            economyManager.stopAutoSave();
        }

        getLogger().info("PrimeleagueEconomy desabilitado");
    }

    /**
     * Cria tabela de transações se não existir
     */
    private void createTransactionsTable() {
        try (java.sql.Connection conn = CoreAPI.getDatabase().getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS economy_transactions (" +
                "id SERIAL PRIMARY KEY, " +
                "player_uuid UUID, " +
                "from_uuid UUID, " +
                "to_uuid UUID, " +
                "amount BIGINT NOT NULL, " +
                "type VARCHAR(50) NOT NULL, " +
                "reason VARCHAR(255), " +
                "timestamp TIMESTAMP DEFAULT NOW()" +
                ")");

            // Criar índices
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_trans_type_time ON economy_transactions(type, timestamp)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_trans_player ON economy_transactions(player_uuid)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }

            // CORREÇÃO #2: Índices para DynamicPricer (queries otimizadas)
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_trans_timestamp_type ON economy_transactions(timestamp, type)");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_trans_timestamp_reason ON economy_transactions(timestamp, reason) WHERE type = 'SHOP_BUY'");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }
            // CORREÇÃO #2: Índice parcial para FARM_% (performance crítica)
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_trans_farm_type ON economy_transactions(type) WHERE type LIKE 'FARM_%'");
            } catch (java.sql.SQLException e) {
                // Índice já existe - ignorar
            }

            getLogger().info("Tabela economy_transactions criada/verificada");
        } catch (java.sql.SQLException e) {
            getLogger().severe("Erro ao criar tabela economy_transactions: " + e.getMessage());
        }
    }

    /**
     * Cria tabela dynamic_prices se não existir
     */
    private void createDynamicPricesTable() {
        try (java.sql.Connection conn = CoreAPI.getDatabase().getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS dynamic_prices (" +
                "item_name VARCHAR(50) PRIMARY KEY, " +
                "ema_mult DOUBLE PRECISION NOT NULL DEFAULT 1.0, " +
                "updated TIMESTAMP DEFAULT NOW()" +
                ")");

            getLogger().info("Tabela dynamic_prices criada/verificada");
        } catch (java.sql.SQLException e) {
            getLogger().severe("Erro ao criar tabela dynamic_prices: " + e.getMessage());
        }
    }

    /**
     * Setup Vault integration
     */
    private void setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault não encontrado - integração desabilitada");
            return;
        }

        vaultProvider = new VaultEconomyProvider(this);
        getServer().getServicesManager().register(
            net.milkbowl.vault.economy.Economy.class,
            vaultProvider,
            this,
            ServicePriority.Normal
        );

        getLogger().info("Vault integration habilitada");
    }

    /**
     * Setup PlaceholderAPI integration
     */
    private void setupPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI não encontrado - integração desabilitada");
            return;
        }

        try {
            placeholderExpansion = new PlaceholderAPIExpansion(this);
            if (placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI integration habilitada");
            } else {
                getLogger().warning("Falha ao registrar PlaceholderAPI expansion");
            }
        } catch (Exception e) {
            getLogger().warning("Erro ao configurar PlaceholderAPI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Carregar saldo do player no cache
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                economyManager.loadBalance(event.getPlayer().getUniqueId());
            }
        }.runTaskAsynchronously(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Salvar e remover do cache
        economyManager.removePlayer(event.getPlayer().getUniqueId());
    }

    public static EconomyPlugin getInstance() {
        return instance;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    /**
     * Obtém cache de ricostop ou null se expirado
     */
    public RicostopCommand.TopCache getRicostopCache(String type) {
        RicostopCommand.TopCache cache = ricostopCache.get(type);
        if (cache != null && System.currentTimeMillis() - cache.getTimestamp() < ricostopCacheDuration) {
            return cache;
        }
        return null;
    }

    /**
     * Define cache de ricostop
     */
    public void setRicostopCache(String type, RicostopCommand.TopCache cache) {
        ricostopCache.put(type, cache);
    }

    /**
     * Obtém DynamicPricer (se habilitado)
     */
    public DynamicPricer getDynamicPricer() {
        return dynamicPricer;
    }
}

