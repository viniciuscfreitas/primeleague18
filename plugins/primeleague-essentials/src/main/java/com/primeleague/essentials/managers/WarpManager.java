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

public class WarpManager {

    private static WarpManager instance;

    private WarpManager() {}

    public static WarpManager getInstance() {
        if (instance == null) {
            instance = new WarpManager();
        }
        return instance;
    }

    public boolean setWarp(String name, Location loc, String permission) {
        String sql = "INSERT INTO warps (name, world_name, x, y, z, yaw, pitch, permission) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (name) DO UPDATE SET " +
                "world_name = EXCLUDED.world_name, x = EXCLUDED.x, y = EXCLUDED.y, z = EXCLUDED.z, " +
                "yaw = EXCLUDED.yaw, pitch = EXCLUDED.pitch, permission = EXCLUDED.permission;";

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name.toLowerCase());
            stmt.setString(2, loc.getWorld().getName());
            stmt.setDouble(3, loc.getX());
            stmt.setDouble(4, loc.getY());
            stmt.setDouble(5, loc.getZ());
            stmt.setFloat(6, loc.getYaw());
            stmt.setFloat(7, loc.getPitch());
            stmt.setString(8, permission);
            
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            EssentialsPlugin.getInstance().getLogger().severe("Erro ao setar warp: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteWarp(String name) {
        String sql = "DELETE FROM warps WHERE name = ?";

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name.toLowerCase());
            
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            EssentialsPlugin.getInstance().getLogger().severe("Erro ao deletar warp: " + e.getMessage());
            return false;
        }
    }

    public Location getWarp(String name) {
        String sql = "SELECT world_name, x, y, z, yaw, pitch FROM warps WHERE name = ?";

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, name.toLowerCase());
            
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
            EssentialsPlugin.getInstance().getLogger().severe("Erro ao buscar warp: " + e.getMessage());
        }
        return null;
    }

    public List<String> getWarps() {
        List<String> warps = new ArrayList<>();
        String sql = "SELECT name FROM warps";

        try (Connection conn = CoreAPI.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    warps.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            EssentialsPlugin.getInstance().getLogger().severe("Erro ao listar warps: " + e.getMessage());
        }
        return warps;
    }
}
