package net.kztmc.mc.levelhead.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.cache.CachedPlayerData;
import net.kztmc.mc.levelhead.cache.PlayerStatsCache;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

public class LocalApiServer {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 3015;

    /*
     * Seraphからの問い合わせ時、
     * キャッシュになければAPI取得完了を最大この時間待つ。
     */
    private static final long CACHE_WAIT_TIMEOUT = 10000L;

    private HttpServer server;

    public void start() {
        try {
            server = HttpServer.create(
                    new InetSocketAddress(HOST, PORT),
                    0
            );

            server.createContext("/v2/player", new PlayerHandler());

            server.setExecutor(
                    Executors.newCachedThreadPool()
            );

            server.start();

            System.out.println(
                    "[LevelHead] Local API server started on "
                            + HOST + ":" + PORT
            );

        } catch (IOException e) {
            System.err.println(
                    "[LevelHead] Failed to start local API server"
            );
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;

            System.out.println(
                    "[LevelHead] Local API server stopped"
            );
        }
    }

    private static class PlayerHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            try {

                UUID uuid = getUuid(exchange);

                if (uuid == null) {
                    sendResponse(
                            exchange,
                            400,
                            createError("Invalid or missing UUID")
                    );
                    return;
                }

                /*
                 * まずキャッシュを確認。
                 */
                CachedPlayerData data =
                        Main.CACHE.getCachedData(uuid);

                /*
                 * キャッシュにない場合、
                 * 実際にAPI取得が完了するまで待つ。
                 */
                if (data == null || data.getRawJson() == null) {

                    data = Main.CACHE.getOrWait(
                            uuid,
                            PlayerStatsCache.Priority.HIGH,
                            CACHE_WAIT_TIMEOUT
                    );
                }

                /*
                 * それでも取得できなかった。
                 */
                if (data == null || data.getRawJson() == null) {

                    sendResponse(
                            exchange,
                            404,
                            createError("Player not available")
                    );

                    return;
                }

                /*
                 * キャッシュ済みJSONをそのままSeraphへ返す。
                 */
                sendResponse(
                        exchange,
                        200,
                        data.getRawJson()
                );

            } catch (Exception e) {

                System.err.println(
                        "[LevelHead] Local API request failed"
                );

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

        private UUID getUuid(HttpExchange exchange) throws UnsupportedEncodingException {

            /*
             * ① Seraph形式
             *
             * /v2/player?uuid=xxxxxxxx
             */
            String query =
                    exchange.getRequestURI().getRawQuery();

            if (query != null) {

                String[] parameters =
                        query.split("&");

                for (String parameter : parameters) {

                    String[] pair =
                            parameter.split("=", 2);

                    if (pair.length != 2) continue;

                    String key =
                            URLDecoder.decode(
                                    pair[0],
                                    "UTF-8"
                            );

                    if (!"uuid".equalsIgnoreCase(key)) {
                        continue;
                    }

                    String value =
                            URLDecoder.decode(
                                    pair[1],
                                    "UTF-8"
                            );

                    try {
                        return UUID.fromString(value);
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                }
            }

            /*
             * ② /v2/player/<uuid> 形式もサポート
             */
            String path =
                    exchange.getRequestURI().getPath();

            String prefix = "/v2/player/";

            if (path.startsWith(prefix)) {

                String uuidString =
                        path.substring(prefix.length());

                try {
                    return UUID.fromString(uuidString);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }

            return null;
        }
    }

    private static void sendResponse(
            HttpExchange exchange,
            int status,
            String response
    ) throws IOException {

        byte[] bytes =
                response.getBytes(StandardCharsets.UTF_8);

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

        try (OutputStream output =
                     exchange.getResponseBody()) {

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

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}