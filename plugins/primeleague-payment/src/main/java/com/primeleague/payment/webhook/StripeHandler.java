package com.primeleague.payment.webhook;

import com.primeleague.core.CoreAPI;
import com.primeleague.core.models.PlayerData;
import com.primeleague.payment.PaymentPlugin;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Calendar;
import java.util.UUID;

/**
 * Handler de webhook do Stripe
 * Grug Brain: Handler simples, validação básica
 */
public class StripeHandler implements HttpHandler {

    private final PaymentPlugin plugin;
    private final JSONParser jsonParser;

    public StripeHandler(PaymentPlugin plugin) {
        this.plugin = plugin;
        this.jsonParser = new JSONParser();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            // Ler body do request
            InputStream is = exchange.getRequestBody();
            StringBuilder body = new StringBuilder();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                body.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
            }

            // Parse JSON (simplificado - MVP)
            JSONObject json = (JSONObject) jsonParser.parse(body.toString());

            // Validar webhook (simplificado - MVP)
            String webhookSecret = plugin.getConfig().getString("payment.stripe.webhook-secret");
            // TODO: Validar assinatura do webhook (MVP: skip)

            // Processar pagamento
            processPayment(json);

            sendResponse(exchange, 200, "OK");
        } catch (Exception e) {
            plugin.getLogger().severe("Erro ao processar webhook Stripe: " + e.getMessage());
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal server error");
        }
    }

    private void processPayment(JSONObject json) {
        // MVP: Assumir que o JSON contém player_uuid e status
        // Em produção, mapear campos do Stripe corretamente
        String paymentId = (String) json.get("id");
        String status = (String) json.get("status");
        String playerUuidStr = (String) json.get("player_uuid");

        if (playerUuidStr == null || paymentId == null || status == null) {
            plugin.getLogger().warning("Webhook Stripe com dados incompletos");
            return;
        }

        UUID playerUuid = UUID.fromString(playerUuidStr);

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Buscar player
                    PlayerData data = CoreAPI.getPlayer(playerUuid);
                    if (data == null) {
                        plugin.getLogger().warning("Player não encontrado para webhook: " + playerUuid);
                        return;
                    }

                    // Atualizar status de pagamento
                    if (status.equals("succeeded")) {
                        data.setPaymentStatus("approved");

                        // Adicionar 30 dias de acesso
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.DAY_OF_MONTH, 30);
                        data.setAccessExpiresAt(cal.getTime());

                        CoreAPI.savePlayer(data);
                        plugin.getLogger().info("Pagamento aprovado para " + data.getName() + " - Acesso até " + cal.getTime());
                    } else {
                        data.setPaymentStatus(status);
                        CoreAPI.savePlayer(data);
                    }

                    // Log webhook (try-with-resources)
                    try (Connection conn = CoreAPI.getDatabase().getConnection()) {
                        PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO payment_webhooks (player_uuid, payment_id, status, created_at) " +
                            "VALUES (?, ?, ?, NOW())");
                        stmt.setObject(1, playerUuid);
                        stmt.setString(2, paymentId);
                        stmt.setString(3, status);
                        stmt.executeUpdate();
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Erro ao processar pagamento: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        exchange.sendResponseHeaders(statusCode, message.length());
        OutputStream os = exchange.getResponseBody();
        os.write(message.getBytes(StandardCharsets.UTF_8));
        os.close();
    }
}

