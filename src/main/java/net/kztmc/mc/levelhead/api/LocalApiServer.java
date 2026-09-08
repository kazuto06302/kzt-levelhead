package net.kztmc.mc.levelhead.api;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.cache.CachedPlayerData;
import net.kztmc.mc.levelhead.cache.PlayerStatsCache;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class LocalApiServer {

    private static final String HOST = "127.0.0.1";

    private static final int PORT = 3015;

    private static final long WAIT_TIMEOUT = 30000L;

    private HttpServer server;

    public void start() {

        if (server != null) return;

        try {

            server = HttpServer.create(new InetSocketAddress(HOST, PORT), 0);
            server.createContext("/v2/player", new PlayerHandler());
            server.setExecutor(null);
            server.start();

            System.out.println("[LevelHead] Local API started at http://" + HOST + ":" + PORT);

        } catch (Exception e) {
            System.err.println("[LevelHead] Failed to start local API");

            e.printStackTrace();
        }
    }

    public void stop() {

        if (server == null) return;

        server.stop(0);
        server = null;

        System.out.println("[LevelHead] Local API stopped");
    }

    private static class PlayerHandler
            implements com.sun.net.httpserver.HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, createError("Method Not Allowed"));
                return;
            }

            try {

                URI uri = exchange.getRequestURI();
                String query = uri.getRawQuery();
                String uuidString = getQueryParameter(query, "uuid");

                if (uuidString == null || uuidString.isEmpty()) {
                    sendResponse(exchange, 400, createError("Missing uuid"));
                    return;
                }

                UUID uuid;

                try {
                    uuid = parseUuid(uuidString);

                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, createError("Invalid uuid"));
                    return;
                }

                CachedPlayerData data = Main.CACHE.getOrWait(uuid, PlayerStatsCache.Priority.HIGH, WAIT_TIMEOUT);

                if (data == null || data.getRawJson() == null) {
                    sendResponse(exchange, 404, createError("Player not available"));
                    return;
                }

                sendResponse(exchange, 200, data.getRawJson());

            } catch (Exception e) {
                e.printStackTrace();

                sendResponse(exchange, 500, createError("Internal Server Error"));
            }
        }
    }

    private static UUID parseUuid(String value) {

        try {
            return UUID.fromString(value);

        } catch (IllegalArgumentException e) {

            if (value.length() != 32) {
                throw e;
            }

            String normalized =
                    value.substring(0, 8) +
                            "-" +
                            value.substring(8, 12) +
                            "-" +
                            value.substring(12, 16) +
                            "-" +
                            value.substring(16, 20) +
                            "-" +
                            value.substring(20);

            return UUID.fromString(normalized);
        }
    }

    private static String getQueryParameter(String query, String name) {

        if (query == null || query.isEmpty()) return null;

        String[] parameters = query.split("&");

        for (String parameter : parameters) {
            String[] parts = parameter.split("=", 2);

            if (parts.length != 2) continue;
            if (parts[0].equals(name)) return parts[1];
        }
        return null;
    }

    private static String createError(String message) {
        JsonObject root = new JsonObject();

        root.addProperty("success", false);
        root.addProperty("cause", message);

        return root.toString();
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {

        byte[] data = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, data.length);

        OutputStream output = exchange.getResponseBody();

        try {
            output.write(data);
        } finally {
            output.close();
        }
    }
}