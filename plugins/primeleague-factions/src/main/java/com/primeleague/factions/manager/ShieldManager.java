package com.primeleague.factions.manager;

import com.primeleague.core.CoreAPI;
import com.primeleague.factions.PrimeFactions;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Shield com timestamp absoluto
 * Grug Brain: Shield agora é tempo real, não horas do dia
 */
public class ShieldManager {

    private final PrimeFactions plugin;
    private final ConcurrentHashMap<Integer, Long> shieldCache = new ConcurrentHashMap<>(); // clanId -> expiresAt (timestamp)
    private final ConcurrentHashMap<Integer, Boolean> notifiedCache = new ConcurrentHashMap<>(); // clanId -> já notificou que acabou
    private final ConcurrentHashMap<Integer, Long> goldenHoursRemaining = new ConcurrentHashMap<>(); // clanId -> minutos restantes quando entrou em Golden Hours

    public ShieldManager(PrimeFactions plugin) {
        this.plugin = plugin;
        loadAllShields();
    }

    /**
     * Carrega todos os shields do banco na inicialização
     */
    private void loadAllShields() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, shield_expires_at FROM clans WHERE shield_expires_at IS NOT NULL")) {

                ResultSet rs = stmt.executeQuery();
                int count = 0;
                while (rs.next()) {
                    int clanId = rs.getInt("id");
                    Timestamp expiresAt = rs.getTimestamp("shield_expires_at");
                    if (expiresAt != null && expiresAt.getTime() > System.currentTimeMillis()) {
                        shieldCache.put(clanId, expiresAt.getTime());
                        count++;
                    }
                }
                plugin.getLogger().info("Carregados " + count + " shields ativos.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao carregar shields: " + e.getMessage());
            }
        });
    }

    /**
     * Ativa shield por X horas (compra com clan bank)
     * Grug Brain: Sincronizado para evitar race conditions
     */
    public synchronized boolean activateShield(int clanId, int hours) {
        long cost = hours * 50000L; // 50k por hora
        long balance = plugin.getClansPlugin().getClansManager().getClanBalance(clanId);

        if (balance < cost) {
            return false;
        }

        if (!plugin.getClansPlugin().getClansManager().removeClanBalance(clanId, cost)) {
            return false;
        }

        long currentExpires = shieldCache.getOrDefault(clanId, System.currentTimeMillis());
        long newExpires = Math.max(currentExpires, System.currentTimeMillis()) + (hours * 3600000L);

        // Atualizar cache
        shieldCache.put(clanId, newExpires);
        notifiedCache.remove(clanId); // Reset notificação

        // Salvar no banco async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = CoreAPI.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE clans SET shield_expires_at = ? WHERE id = ?")) {
                stmt.setTimestamp(1, new Timestamp(newExpires));
                stmt.setInt(2, clanId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao salvar shield no banco: " + e.getMessage());
            }
        });

        return true;
    }

    /**
     * Retorna minutos restantes do shield (0 se não tem ou acabou)
     * Grug Brain: Durante Golden Hours (00h-06h), shield pausa automaticamente
     */
    public long getRemainingMinutes(int clanId) {
        Long expiresAt = shieldCache.get(clanId);
        if (expiresAt == null || expiresAt <= System.currentTimeMillis()) {
            return 0;
        }

        // Verificar se está em Golden Hours (00h-06h)
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        boolean isGoldenHours = hour >= 0 && hour < 6;

        if (isGoldenHours) {
            // Durante Golden Hours, shield está pausado
            // Retornar o tempo que tinha quando entrou em Golden Hours
            Long savedRemaining = goldenHoursRemaining.get(clanId);
            if (savedRemaining == null) {
                // Primeira vez entrando em Golden Hours, salvar tempo atual
                long remaining = (expiresAt - System.currentTimeMillis()) / 60000L;
                goldenHoursRemaining.put(clanId, remaining);
                return remaining;
            }
            // Já está pausado, retornar tempo salvo
            return savedRemaining;
        } else {
            // Fora de Golden Hours, shield conta normalmente
            // Limpar pausa se estava pausado e ajustar expiresAt
            Long savedRemaining = goldenHoursRemaining.remove(clanId);
            if (savedRemaining != null) {
                // Ajustar expiresAt para compensar o tempo pausado
                // Novo expiresAt = agora + tempo restante salvo
                long newExpires = System.currentTimeMillis() + (savedRemaining * 60000L);
                shieldCache.put(clanId, newExpires);

                // Atualizar no banco async
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (Connection conn = CoreAPI.getDatabase().getConnection();
                         PreparedStatement stmt = conn.prepareStatement(
                             "UPDATE clans SET shield_expires_at = ? WHERE id = ?")) {
                        stmt.setTimestamp(1, new Timestamp(newExpires));
                        stmt.setInt(2, clanId);
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Erro ao atualizar shield após Golden Hours: " + e.getMessage());
                    }
                });

                return savedRemaining;
            }
            // Normal: calcular tempo restante
            return (expiresAt - System.currentTimeMillis()) / 60000L;
        }
    }

    /**
     * Verifica se shield está ativo (considerando Golden Hours)
     */
    public boolean isShieldActive(int clanId) {
        // Golden Hours: shield pausa automaticamente (00h-06h)
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (hour >= 0 && hour < 6) {
            // Durante Golden Hours, shield não conta (pausado)
            // Mas ainda retorna true se tem shield ativo (só não gasta tempo)
            return shieldCache.containsKey(clanId) && shieldCache.get(clanId) > System.currentTimeMillis();
        }

        Long expiresAt = shieldCache.get(clanId);
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    /**
     * Formata tempo restante (ex: "2d 5h" ou "18h 32min" ou "3h 12min")
     */
    public String formatRemaining(int clanId) {
        long minutes = getRemainingMinutes(clanId);
        if (minutes <= 0) {
            return "§4ZERADO";
        }

        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;

        if (days > 0) {
            return String.format("%dd %dh", days, hours);
        } else if (hours > 0) {
            return String.format("%dh %dmin", hours, mins);
        } else {
            return String.format("%dmin", mins);
        }
    }

    /**
     * Verifica se já notificou que shield acabou (para evitar spam)
     */
    public boolean wasNotified(int clanId) {
        return notifiedCache.getOrDefault(clanId, false);
    }

    /**
     * Marca que já notificou
     */
    public void markNotified(int clanId) {
        notifiedCache.put(clanId, true);
    }

    /**
     * Limpa notificação (quando shield é reativado)
     */
    public void clearNotification(int clanId) {
        notifiedCache.remove(clanId);
    }
}

