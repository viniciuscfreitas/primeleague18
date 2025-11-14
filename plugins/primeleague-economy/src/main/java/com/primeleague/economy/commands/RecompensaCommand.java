package com.primeleague.economy.commands;

import com.primeleague.core.CoreAPI;
import com.primeleague.economy.EconomyAPI;
import com.primeleague.economy.EconomyPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;

/**
 * Comando /recompensa - Recompensa diária
 * Grug Brain: $50 base + $1 por dia consecutivo, cooldown 24h
 */
public class RecompensaCommand implements CommandExecutor {

    private final EconomyPlugin plugin;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    public RecompensaCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;
        processDailyReward(player);

        return true;
    }

    /**
     * Processa recompensa diária
     */
    private void processDailyReward(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            java.util.UUID uuid = player.getUniqueId();

            // Verificar cooldown e obter streak
            DailyRewardData data = getDailyRewardData(uuid);

            // Verificar se pode coletar
            long now = System.currentTimeMillis();
            long lastClaim = data.getLastClaim();
            long cooldownMs = 24 * 60 * 60 * 1000; // 24 horas

            if (lastClaim > 0 && (now - lastClaim) < cooldownMs) {
                long remainingMs = cooldownMs - (now - lastClaim);
                long remainingHours = remainingMs / (60 * 60 * 1000);
                long remainingMinutes = (remainingMs % (60 * 60 * 1000)) / (60 * 1000);

                final String msg = ChatColor.RED + "Aguarde " + remainingHours + "h " + remainingMinutes + "m para coletar novamente.";
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(msg);
                });
                return;
            }

            // Calcular streak (dias consecutivos)
            int streak = data.getStreak();
            if (lastClaim > 0) {
                // Verificar se passou mais de 24h mas menos de 48h (streak continua)
                long daysSinceLastClaim = (now - lastClaim) / (24 * 60 * 60 * 1000);
                if (daysSinceLastClaim == 1) {
                    // Streak continua
                    streak++;
                } else if (daysSinceLastClaim > 1) {
                    // Streak resetado
                    streak = 1;
                }
            } else {
                // Primeira vez
                streak = 1;
            }

            // Calcular recompensa
            double baseReward = 50.0; // $50 base
            double bonusReward = streak; // $1 por dia consecutivo
            double totalReward = baseReward + bonusReward;

            // Salvar cooldown e streak
            saveDailyRewardData(uuid, streak, now);

            // Adicionar dinheiro
            EconomyAPI.addMoney(uuid, totalReward, "DAILY_REWARD");

            // Mensagem
            final String currency = plugin.getConfig().getString("economy.simbolo", "¢");
            final String msg = ChatColor.GREEN + "Você coletou sua recompensa diária!" +
                ChatColor.YELLOW + " +" + balanceFormat.format(totalReward) + currency +
                ChatColor.GRAY + " (Base: " + balanceFormat.format(baseReward) +
                currency + ", Bonus: " + balanceFormat.format(bonusReward) + currency +
                " | Streak: " + streak + " dias)";

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(msg);
            });
        });
    }

    /**
     * Obtém dados de daily reward
     */
    private DailyRewardData getDailyRewardData(java.util.UUID uuid) {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            // Criar tabela se não existir
            try (java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_daily_rewards (" +
                    "player_uuid UUID PRIMARY KEY, " +
                    "streak INT DEFAULT 1, " +
                    "last_claim TIMESTAMP" +
                    ")");
            }

            PreparedStatement stmt = conn.prepareStatement(
                "SELECT streak, last_claim FROM economy_daily_rewards WHERE player_uuid = ?");
            stmt.setObject(1, uuid);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    DailyRewardData data = new DailyRewardData();
                    data.setStreak(rs.getInt("streak"));
                    Timestamp lastClaim = rs.getTimestamp("last_claim");
                    data.setLastClaim(lastClaim != null ? lastClaim.getTime() : 0);
                    return data;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao buscar daily reward: " + e.getMessage());
        }

        // Retornar dados padrão
        DailyRewardData data = new DailyRewardData();
        data.setStreak(0);
        data.setLastClaim(0);
        return data;
    }

    /**
     * Salva dados de daily reward
     */
    private void saveDailyRewardData(java.util.UUID uuid, int streak, long lastClaim) {
        try (Connection conn = CoreAPI.getDatabase().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO economy_daily_rewards (player_uuid, streak, last_claim) " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT (player_uuid) DO UPDATE SET streak = ?, last_claim = ?");

            Timestamp timestamp = new Timestamp(lastClaim);
            stmt.setObject(1, uuid);
            stmt.setInt(2, streak);
            stmt.setTimestamp(3, timestamp);
            stmt.setInt(4, streak);
            stmt.setTimestamp(5, timestamp);

            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao salvar daily reward: " + e.getMessage());
        }
    }

    /**
     * Classe interna para dados de daily reward
     */
    private static class DailyRewardData {
        private int streak;
        private long lastClaim;

        public int getStreak() {
            return streak;
        }

        public void setStreak(int streak) {
            this.streak = streak;
        }

        public long getLastClaim() {
            return lastClaim;
        }

        public void setLastClaim(long lastClaim) {
            this.lastClaim = lastClaim;
        }
    }
}

