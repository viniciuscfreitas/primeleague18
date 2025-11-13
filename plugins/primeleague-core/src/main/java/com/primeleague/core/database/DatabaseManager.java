package com.primeleague.core.database;

import com.primeleague.core.CorePlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gerenciador de conexão PostgreSQL com HikariCP
 * Grug Brain: Pool simples, queries diretas, sem DAO/Repository
 */
public class DatabaseManager {

    private final CorePlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        FileConfiguration config = plugin.getConfig();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s",
            config.getString("database.host", "localhost"),
            config.getInt("database.port", 5432),
            config.getString("database.database", "primeleague")));
        hikariConfig.setUsername(config.getString("database.user", "postgres"));
        hikariConfig.setPassword(config.getString("database.password", "postgres"));
        hikariConfig.setMaximumPoolSize(config.getInt("database.pool-size", 10));
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);

        try {
            dataSource = new HikariDataSource(hikariConfig);

            // Testar conexão
            try (Connection conn = dataSource.getConnection()) {
                plugin.getLogger().info("Conexão PostgreSQL estabelecida com sucesso");

                // Criar schema se não existir
                createSchema(conn);

                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao conectar ao PostgreSQL: " + e.getMessage());
            return false;
        }
    }

    private void createSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Tabela users
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                "uuid UUID PRIMARY KEY, " +
                "name VARCHAR(16) UNIQUE NOT NULL, " +
                "ip_hash VARCHAR(64), " +
                "discord_id BIGINT, " +
                "access_code VARCHAR(32), " +
                "access_expires_at TIMESTAMP, " +
                "payment_status VARCHAR(20), " +
                "money BIGINT DEFAULT 0, " +
                "elo INT DEFAULT 1000, " +
                "created_at TIMESTAMP DEFAULT NOW()" +
                ")");

            // Migração: Alterar coluna ip_hash para permitir NULL (se já existir)
            try {
                stmt.execute("ALTER TABLE users ALTER COLUMN ip_hash DROP NOT NULL");
            } catch (SQLException e) {
                // Coluna já permite NULL ou não existe - ignorar
            }

            // Migração: Remover UNIQUE de discord_id para permitir múltiplas contas (alts)
            try {
                // PostgreSQL: tentar remover constraint UNIQUE
                stmt.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_discord_id_key");
            } catch (SQLException e) {
                // Constraint não existe ou já foi removida - ignorar
            }

            // Criar índice sem UNIQUE para performance (permite duplicatas)
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_discord_id ON users(discord_id)");
            } catch (SQLException e) {
                // Índice já existe - ignorar
            }

            // Migração: Stats de combate
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS kills INT DEFAULT 0");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS deaths INT DEFAULT 0");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS killstreak INT DEFAULT 0");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS best_killstreak INT DEFAULT 0");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_kill_at TIMESTAMP");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_death_at TIMESTAMP");
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP");
            } catch (SQLException e) {
                // Colunas já existem - ignorar
            }

            // Índices para rankings de stats
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_kills ON users(kills DESC)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_kdr ON users((kills::DECIMAL / NULLIF(deaths, 0)) DESC)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_best_killstreak ON users(best_killstreak DESC)");
            } catch (SQLException e) {
                // Índices já existem - ignorar
            }

            // Índice para ranking de ELO
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_elo ON users(elo DESC)");
            } catch (SQLException e) {
                // Índice já existe - ignorar
            }

            // Tabela pending_logins
            stmt.execute("CREATE TABLE IF NOT EXISTS pending_logins (" +
                "id SERIAL PRIMARY KEY, " +
                "player_uuid UUID NOT NULL REFERENCES users(uuid), " +
                "new_ip_hash VARCHAR(64) NOT NULL, " +
                "new_ip_address VARCHAR(45) NOT NULL, " +
                "created_at TIMESTAMP DEFAULT NOW(), " +
                "expires_at TIMESTAMP DEFAULT NOW() + INTERVAL '5 minutes'" +
                ")");

            // Tabela payment_webhooks
            stmt.execute("CREATE TABLE IF NOT EXISTS payment_webhooks (" +
                "id SERIAL PRIMARY KEY, " +
                "player_uuid UUID REFERENCES users(uuid), " +
                "payment_id VARCHAR(100) NOT NULL, " +
                "status VARCHAR(20) NOT NULL, " +
                "amount DECIMAL(10,2), " +
                "webhook_data JSONB, " +
                "created_at TIMESTAMP DEFAULT NOW()" +
                ")");

            // Índices
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_name ON users(name)");
            // idx_users_discord_id já criado na migração acima
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_access_code ON users(access_code)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pending_logins_player ON pending_logins(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pending_logins_expires ON pending_logins(expires_at)");

            plugin.getLogger().info("Schema PostgreSQL criado/verificado com sucesso");
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource não inicializado");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Pool de conexões PostgreSQL fechado");
        }
    }
}

