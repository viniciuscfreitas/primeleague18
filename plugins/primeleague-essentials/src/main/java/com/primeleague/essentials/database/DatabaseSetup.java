package com.primeleague.essentials.database;

import com.primeleague.core.CoreAPI;
import com.primeleague.essentials.EssentialsPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Setup das tabelas do Essentials
 * Grug Brain: Cria tabelas se não existirem no startup.
 */
public class DatabaseSetup {

    public static boolean initTables() {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            
            // Tabela de Homes
            // UUID, Nome da Home, Mundo, X, Y, Z, Yaw, Pitch
            String homesTable = "CREATE TABLE IF NOT EXISTS user_homes (" +
                    "id SERIAL PRIMARY KEY, " +
                    "player_uuid UUID NOT NULL, " +
                    "home_name VARCHAR(32) NOT NULL, " +
                    "world_name VARCHAR(64) NOT NULL, " +
                    "x DOUBLE PRECISION NOT NULL, " +
                    "y DOUBLE PRECISION NOT NULL, " +
                    "z DOUBLE PRECISION NOT NULL, " +
                    "yaw FLOAT NOT NULL, " +
                    "pitch FLOAT NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE(player_uuid, home_name)" +
                    ");";
            
            try (PreparedStatement stmt = conn.prepareStatement(homesTable)) {
                stmt.execute();
            }

            // Tabela de Warps
            // Nome, Mundo, X, Y, Z, Yaw, Pitch, Permissão (opcional)
            String warpsTable = "CREATE TABLE IF NOT EXISTS warps (" +
                    "name VARCHAR(32) PRIMARY KEY, " +
                    "world_name VARCHAR(64) NOT NULL, " +
                    "x DOUBLE PRECISION NOT NULL, " +
                    "y DOUBLE PRECISION NOT NULL, " +
                    "z DOUBLE PRECISION NOT NULL, " +
                    "yaw FLOAT NOT NULL, " +
                    "pitch FLOAT NOT NULL, " +
                    "permission VARCHAR(64), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");";

            try (PreparedStatement stmt = conn.prepareStatement(warpsTable)) {
                stmt.execute();
            }

            // Tabela de Kits (opcional, se for salvar no banco)
            // Por enquanto kits podem ser config.yml ou JSON, mas vamos criar tabela de cooldowns
            String kitCooldownsTable = "CREATE TABLE IF NOT EXISTS user_kit_cooldowns (" +
                    "player_uuid UUID NOT NULL, " +
                    "kit_name VARCHAR(32) NOT NULL, " +
                    "last_used TIMESTAMP NOT NULL, " +
                    "PRIMARY KEY (player_uuid, kit_name)" +
                    ");";

            try (PreparedStatement stmt = conn.prepareStatement(kitCooldownsTable)) {
                stmt.execute();
            }

            return true;
        } catch (SQLException e) {
            EssentialsPlugin.getInstance().getLogger().severe("Erro ao inicializar tabelas: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
