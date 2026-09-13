package net.kztmc.mc.levelhead.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.cache.CachedPlayerData;
import net.kztmc.mc.levelhead.cache.PlayerStatsCache;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpHandler;
import net.kztmc.mc.levelhead.command.LevelHeadCommand;
import net.minecraft.client.Minecraft;

public class LocalApiServer {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 3015;

    private HttpServer server;

    public void start() {
        try {
            server = HttpServer.create(
                    new InetSocketAddress(HOST, PORT),
                    0
            );

            server.createContext("/v2/player", new PlayerHandler());

            // Seraphから複数のリクエストが来ても
            // HTTPサーバー側で直列化されないようにする
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            System.out.println("[LevelHead] Local API server started on " + HOST + ":" + PORT);

        } catch (IOException e) {
            System.err.println("[LevelHead] Failed to start local API server");
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;

            System.out.println("[LevelHead] Local API server stopped");
        }
    }

    private static class PlayerHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            try {
                String path = exchange.getRequestURI().getPath();

                // /v2/player/<uuid>
                String prefix = "/v2/player/";

                if (!path.startsWith(prefix)) {
                    sendResponse(
                            exchange,
                            404,
                            createError("Player not available")
                    );
                    return;
                }

                String uuidString = path.substring(prefix.length());
                UUID uuid;

                try {
                    uuid = UUID.fromString(uuidString);
                } catch (IllegalArgumentException e) {
                    sendResponse(
                            exchange,
                            400,
                            createError("Invalid UUID")
                    );
                    return;
                }

                /*
                 * 重要:
                 *
                 * ここでは Hypixel API の完了を待たない。
                 *
                 * キャッシュがあれば即返す。
                 * キャッシュがなければバックグラウンド取得を開始して
                 * Seraphには即座に404を返す。
                 */

                CachedPlayerData data = Main.CACHE.getCachedData(uuid);

                if (data == null || data.getRawJson() == null) {

                    // API取得は非同期でキューに入れるだけ
                    Main.CACHE.get(uuid, PlayerStatsCache.Priority.HIGH);

                    sendResponse(
                            exchange,
                            404,
                            createError("Player not available")
                    );

                    if (Main.dev) LevelHeadCommand.send(Minecraft.getMinecraft().thePlayer,"LocalAPI Responded 404");

                    return;
                }

                // キャッシュ済みなら即座に返す
                sendResponse(
                        exchange,
                        200,
                        data.getRawJson()
                );

            } catch (Exception e) {

                System.err.println("[LevelHead] Local API request failed");
                e.printStackTrace();

                sendResponse(
                        exchange,
                        500,
                        createError("Internal server error")
                );

            } finally {
                exchange.close();
            }
        }
    }

    private static void sendResponse(HttpExchange exchange, int status, String response) throws IOException {

        byte[] bytes = response.getBytes("UTF-8");

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-cache"
        );

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String createError(String message) {

        return "{"
                + "\"success\":false,"
                + "\"cause\":\""
                + escapeJson(message)
                + "\""
                + "}";
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}