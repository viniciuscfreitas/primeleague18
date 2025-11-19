package com.primeleague.essentials.managers;

import com.primeleague.core.CoreAPI;
import com.primeleague.essentials.EssentialsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gerenciador de Homes
 * Grug Brain: CRUD direto no banco via CoreAPI.
 */
public class HomeManager {

    private static HomeManager instance;

    private HomeManager() {}

    public static HomeManager getInstance() {
        if (instance == null) {
            instance = new HomeManager();
        }
        return instance;
    }

    public boolean setHome(UUID playerUuid, String homeName, Location loc) {
        String sql = "INSERT INTO user_homes (player_uuid, home_name, world_name, x, y, z, yaw, pitch) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (player_uuid, home_name) DO UPDATE SET " +
                "world_name = EXCLUDED.world_name, x = EXCLUDED.x, y = EXCLUDED.y, z = EXCLUDED.z, " +
                "yaw = EXCLUDED.yaw, pitch = EXCLUDED.pitch;";

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, playerUuid);
            stmt.setString(2, homeName.toLowerCase());
            stmt.setString(3, loc.getWorld().getName());
            stmt.setDouble(4, loc.getX());
            stmt.setDouble(5, loc.getY());
            stmt.setDouble(6, loc.getZ());
            stmt.setFloat(7, loc.getYaw());
            stmt.setFloat(8, loc.getPitch());
            
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            EssentialsPlugin.getInstance().getLogger().severe("Erro ao setar home: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteHome(UUID playerUuid, String homeName) {
        String sql = "DELETE FROM user_homes WHERE player_uuid = ? AND home_name = ?";

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, playerUuid);
            stmt.setString(2, homeName.toLowerCase());
            
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            EssentialsPlugin.getInstance().getLogger().severe("Erro ao deletar home: " + e.getMessage());
            return false;
        }
    }

    public Location getHome(UUID playerUuid, String homeName) {
        String sql = "SELECT world_name, x, y, z, yaw, pitch FROM user_homes WHERE player_uuid = ? AND home_name = ?";

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, playerUuid);
            stmt.setString(2, homeName.toLowerCase());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String worldName = rs.getString("world_name");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) return null;

                    return new Location(
                            world,
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                    );
                }
            }
        } catch (SQLException e) {
            EssentialsPlugin.getInstance().getLogger().severe("Erro ao buscar home: " + e.getMessage());
        }
        return null;
    }

    public List<String> getHomes(UUID playerUuid) {
        List<String> homes = new ArrayList<>();
        String sql = "SELECT home_name FROM user_homes WHERE player_uuid = ?";

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, playerUuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    homes.add(rs.getString("home_name"));
                }
            }
        } catch (SQLException e) {
            EssentialsPlugin.getInstance().getLogger().severe("Erro ao listar homes: " + e.getMessage());
        }
        return homes;
    }
    
    public int getHomeCount(UUID playerUuid) {
        String sql = "SELECT COUNT(*) as count FROM user_homes WHERE player_uuid = ?";
        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, playerUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
