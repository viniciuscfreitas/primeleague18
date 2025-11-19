package com.primeleague.league;

import com.primeleague.core.CoreAPI;
import com.primeleague.league.managers.CacheManager;
import com.primeleague.league.managers.EventManager;
import com.primeleague.league.managers.LeagueManager;
import com.primeleague.league.managers.ResetManager;
import com.primeleague.league.managers.RewardsManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plugin League - Sistema de temporadas e estatísticas com Event Sourcing
 * Grug Brain: Plugin simples, depende do Core via CoreAPI
 */
public class LeaguePlugin extends JavaPlugin {

    private static LeaguePlugin instance;
    private LeagueManager leagueManager;
    private EventManager eventManager;
    private CacheManager cacheManager;
    private RewardsManager rewardsManager;
    private ResetManager resetManager;

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

        // Criar schema de banco de dados
        createSchema();

        // Inicializar managers
        leagueManager = new LeagueManager(this);
        eventManager = new EventManager(this);
        cacheManager = new CacheManager(this);
        rewardsManager = new RewardsManager(this);
        resetManager = new ResetManager(this);

        // Verificar e criar temporada ativa se necessário
        leagueManager.checkAndCreateNewSeason();

        // Registrar comandos
        if (getCommand("temporada") != null) {
            getCommand("temporada").setExecutor(new com.primeleague.league.commands.LeagueCommand(this));
        }
        if (getCommand("pontos") != null) {
            getCommand("pontos").setExecutor(new com.primeleague.league.commands.LeagueCommand(this));
        }
        if (getCommand("top") != null) {
            getCommand("top").setExecutor(new com.primeleague.league.commands.LeagueCommand(this));
        }

        // Registrar comando admin de reset
        // Nota: ResetCommand será chamado via subcomando de /temporada
        // Mas também podemos criar comando separado se necessário

        getLogger().info("PrimeleagueLeague habilitado");
    }

    @Override
    public void onDisable() {
        if (cacheManager != null) {
            cacheManager.close();
        }

        getLogger().info("PrimeleagueLeague desabilitado");
    }

    /**
     * Cria tabelas PostgreSQL se não existirem
     * Grug Brain: Queries diretas, try-with-resources
     * Inclui partição, trigger e índices otimizados
     */
    private void createSchema() {
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Tabela de temporadas
            stmt.execute("CREATE TABLE IF NOT EXISTS seasons (" +
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(50) NOT NULL UNIQUE, " +
                "start_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "end_date TIMESTAMP NOT NULL, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")");

            // 2. Tabela de eventos atômicos (PARTITION BY RANGE season_id)
            // Nota: PostgreSQL 10+ suporta PARTITION BY RANGE
            // Para versões anteriores, usar tabela normal sem partição
            try {
                stmt.execute("CREATE TABLE IF NOT EXISTS league_events (" +
                    "id BIGSERIAL, " +
                    "season_id INTEGER NOT NULL REFERENCES seasons(id) ON DELETE CASCADE, " +
                    "entity_type VARCHAR(10) NOT NULL, " + // 'CLAN' ou 'PLAYER'
                    "entity_id VARCHAR(50) NOT NULL, " + // UUID (player) ou INT (clan) como string
                    "category VARCHAR(30) NOT NULL, " + // 'GLADIADOR', 'X1', 'PVP', 'KOTH', 'ECONOMY', 'PUNISH', 'ELO', etc
                    "action VARCHAR(50) NOT NULL, " + // 'WIN', 'KILL', 'DEATH', 'MVP', 'CAPTURE', 'BAN', 'PURCHASE', 'ELO_CHANGE', etc
                    "value NUMERIC(20,2) NOT NULL DEFAULT 0, " + // +50 pontos, +1 kill, -500 penalidade, 1000000 coins, etc
                    "reason VARCHAR(255), " + // "1º lugar", "Kill PvP", "Compra loja", etc
                    "metadata JSONB, " + // Dados extras (match_id, position, kills, old_elo, new_elo, etc)
                    "is_deleted BOOLEAN NOT NULL DEFAULT false, " + // Soft-delete
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "created_by UUID REFERENCES users(uuid), " + // Admin (se manual)
                    "PRIMARY KEY (id, season_id)" +
                    ") PARTITION BY RANGE (season_id)");

                // Criar partição inicial (season_id 1-1000)
                stmt.execute("CREATE TABLE IF NOT EXISTS league_events_1_1000 PARTITION OF league_events " +
                    "FOR VALUES FROM (1) TO (1001)");
            } catch (SQLException e) {
                // Se PARTITION BY não for suportado (PostgreSQL < 10), criar tabela normal
                if (e.getMessage().contains("PARTITION") || e.getMessage().contains("partition")) {
                    getLogger().warning("PostgreSQL não suporta PARTITION BY RANGE. Criando tabela normal.");
                    stmt.execute("CREATE TABLE IF NOT EXISTS league_events (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "season_id INTEGER NOT NULL REFERENCES seasons(id) ON DELETE CASCADE, " +
                        "entity_type VARCHAR(10) NOT NULL, " +
                        "entity_id VARCHAR(50) NOT NULL, " +
                        "category VARCHAR(30) NOT NULL, " +
                        "action VARCHAR(50) NOT NULL, " +
                        "value NUMERIC(20,2) NOT NULL DEFAULT 0, " +
                        "reason VARCHAR(255), " +
                        "metadata JSONB, " +
                        "is_deleted BOOLEAN NOT NULL DEFAULT false, " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "created_by UUID REFERENCES users(uuid)" +
                        ")");
                } else {
                    throw e;
                }
            }

            // 3. Tabela de cache agregado (league_summary)
            stmt.execute("CREATE TABLE IF NOT EXISTS league_summary (" +
                "season_id INTEGER NOT NULL REFERENCES seasons(id) ON DELETE CASCADE, " +
                "entity_type VARCHAR(10) NOT NULL, " + // 'CLAN' ou 'PLAYER'
                "entity_id VARCHAR(50) NOT NULL, " + // UUID (player) ou INT (clan) como string
                "points INTEGER NOT NULL DEFAULT 0, " + // Pontos totais da temporada
                "rank_cache INTEGER, " + // Posição no ranking (cache)
                "last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (season_id, entity_type, entity_id)" +
                ")");

            // 4. Função para atualizar league_summary automaticamente (trigger)
            // Atualiza summary apenas para eventos que dão pontos (value != 0)
            // Categories que dão pontos: GLADIADOR, KOTH, POINTS
            stmt.execute("CREATE OR REPLACE FUNCTION update_league_summary() RETURNS TRIGGER AS $$ " +
                "BEGIN " +
                "  IF NEW.is_deleted = false AND NEW.value != 0 AND " +
                "     (NEW.action = 'AWARD' OR NEW.action = 'POINTS' OR NEW.category IN ('GLADIADOR', 'KOTH', 'X1')) THEN " +
                "    INSERT INTO league_summary (season_id, entity_type, entity_id, points, last_updated) " +
                "    VALUES (NEW.season_id, NEW.entity_type, NEW.entity_id, NEW.value, CURRENT_TIMESTAMP) " +
                "    ON CONFLICT (season_id, entity_type, entity_id) " +
                "    DO UPDATE SET " +
                "      points = league_summary.points + NEW.value, " +
                "      last_updated = CURRENT_TIMESTAMP; " +
                "  END IF; " +
                "  RETURN NEW; " +
                "END; " +
                "$$ LANGUAGE plpgsql");

            // 5. Trigger para atualizar league_summary automaticamente
            try {
                stmt.execute("DROP TRIGGER IF EXISTS update_summary_trigger ON league_events");
            } catch (SQLException e) {
                // Ignorar se trigger não existir
            }
            stmt.execute("CREATE TRIGGER update_summary_trigger " +
                "AFTER INSERT ON league_events " +
                "FOR EACH ROW " +
                "WHEN (NEW.is_deleted = false) " +
                "EXECUTE FUNCTION update_league_summary()");

            // 6. Índices para performance (CRÍTICOS)
            // Nota: Índices em tabelas particionadas são criados automaticamente em cada partição
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_league_events_season " +
                    "ON league_events(season_id, created_at DESC)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_league_events_entity " +
                    "ON league_events(entity_type, entity_id, season_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_league_events_category " +
                    "ON league_events(category, action, season_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_league_events_composite " +
                    "ON league_events(season_id, category, action, entity_type, entity_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_league_events_deleted " +
                    "ON league_events(is_deleted) WHERE is_deleted = false");
            } catch (SQLException e) {
                // Ignorar se índices já existirem ou se tabela for particionada (índices criados automaticamente)
                getLogger().info("Índices de league_events: " + e.getMessage());
            }

            // Índices para league_summary
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_league_summary_season " +
                "ON league_summary(season_id, points DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_league_summary_entity " +
                "ON league_summary(entity_type, entity_id, season_id)");

            // Índice para seasons (status)
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_seasons_status " +
                "ON seasons(status)");

            getLogger().info("Schema League criado/verificado com sucesso");

        } catch (SQLException e) {
            getLogger().severe("Erro ao criar schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Getters
    public static LeaguePlugin getInstance() {
        return instance;
    }

    public LeagueManager getLeagueManager() {
        return leagueManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public RewardsManager getRewardsManager() {
        return rewardsManager;
    }

    public ResetManager getResetManager() {
        return resetManager;
    }
}

