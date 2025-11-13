package com.primeleague.payment;

import com.primeleague.payment.webhook.WebhookReceiver;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Calendar;
import java.util.Date;

/**
 * Plugin de pagamento - Primeleague
 * Grug Brain: Plugin simples, webhook receiver + task periódica
 */
public class PaymentPlugin extends JavaPlugin {

    private static PaymentPlugin instance;
    private WebhookReceiver webhookReceiver;

    @Override
    public void onEnable() {
        instance = this;

        // Verificar se Core está habilitado
        if (getServer().getPluginManager().getPlugin("PrimeleagueCore") == null) {
            getLogger().severe("PrimeleagueCore não encontrado! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Salvar config padrão se não existir
        saveDefaultConfig();

        // Inicializar webhook receiver
        int port = getConfig().getInt("payment.webhook-port", 8080);
        webhookReceiver = new WebhookReceiver(this, port);
        if (!webhookReceiver.start()) {
            getLogger().severe("Falha ao iniciar webhook receiver. Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Task periódica para verificar access_expires_at (a cada 1 hora)
        new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredAccess();
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L * 60L * 60L); // 1 hora = 72000 ticks

        getLogger().info("PrimeleaguePayment habilitado - Webhook receiver na porta " + port);
    }

    @Override
    public void onDisable() {
        if (webhookReceiver != null) {
            webhookReceiver.stop();
        }
        getLogger().info("PrimeleaguePayment desabilitado");
    }

    private void checkExpiredAccess() {
        try {
            com.primeleague.core.database.DatabaseManager db = com.primeleague.core.CoreAPI.getDatabase();

            // Buscar players com acesso expirado (try-with-resources)
            try (java.sql.Connection conn = db.getConnection()) {
                java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "SELECT uuid FROM users WHERE access_expires_at IS NOT NULL AND access_expires_at < NOW()");
                java.sql.ResultSet rs = stmt.executeQuery();

                int count = 0;
                while (rs.next()) {
                    // Atualizar payment_status para 'expired'
                    java.util.UUID uuid = (java.util.UUID) rs.getObject("uuid");
                    try (java.sql.PreparedStatement updateStmt = conn.prepareStatement(
                        "UPDATE users SET payment_status = 'expired' WHERE uuid = ?")) {
                        updateStmt.setObject(1, uuid);
                        updateStmt.executeUpdate();
                    }
                    count++;
                }

                if (count > 0) {
                    getLogger().info("Verificação de acesso: " + count + " players com acesso expirado");
                }
            }
        } catch (Exception e) {
            getLogger().severe("Erro ao verificar acesso expirado: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static PaymentPlugin getInstance() {
        return instance;
    }

    public WebhookReceiver getWebhookReceiver() {
        return webhookReceiver;
    }
}

