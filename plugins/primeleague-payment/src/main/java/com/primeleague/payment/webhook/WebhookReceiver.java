package com.primeleague.payment.webhook;

import com.primeleague.payment.PaymentPlugin;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * HTTP Server para receber webhooks de pagamento
 * Grug Brain: Server nativo Java 8, handlers simples
 */
public class WebhookReceiver {

    private final PaymentPlugin plugin;
    private final int port;
    private HttpServer server;
    private MercadoPagoHandler mercadoPagoHandler;
    private StripeHandler stripeHandler;

    public WebhookReceiver(PaymentPlugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
        this.mercadoPagoHandler = new MercadoPagoHandler(plugin);
        this.stripeHandler = new StripeHandler(plugin);
    }

    public boolean start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // Endpoint Mercado Pago
            server.createContext("/webhook/mercadopago", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    mercadoPagoHandler.handle(exchange);
                }
            });

            // Endpoint Stripe
            server.createContext("/webhook/stripe", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    stripeHandler.handle(exchange);
                }
            });

            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("Webhook receiver iniciado na porta " + port);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao iniciar webhook receiver: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("Webhook receiver parado");
        }
    }
}

