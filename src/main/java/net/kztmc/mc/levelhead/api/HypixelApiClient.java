package net.kztmc.mc.levelhead.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kztmc.mc.levelhead.cache.PlayerStats;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class HypixelApiClient implements ApiClient {

    private static final String API_URL =
            "https://api.hypixel.net/v2/player?uuid=";

    private final String apiKey;

    public HypixelApiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public PlayerStats fetchPlayer(
            UUID uuid
    ) throws Exception {

        HttpURLConnection connection = null;

        try {

            URL url =
                    new URL(
                            API_URL
                                    + uuid.toString()
                    );

            connection =
                    (HttpURLConnection)
                            url.openConnection();

            /*
             * API Key
             */
            connection.setRequestProperty(
                    "Api-Key",
                    apiKey
            );

            /*
             * JSONを要求。
             */
            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            /*
             * タイムアウト。
             */
            connection.setConnectTimeout(
                    10000
            );

            connection.setReadTimeout(
                    10000
            );

            /*
             * GET
             */
            connection.setRequestMethod(
                    "GET"
            );

            int statusCode =
                    connection.getResponseCode();

            /*
             * HTTP 429
             *
             * Hypixel APIのレート制限。
             */
            if (statusCode == 429) {

                String body =
                        readErrorStream(
                                connection
                        );

                throw new HypixelApiException(
                        429,
                        "Hypixel API rate limited"
                                + (
                                body.isEmpty()
                                        ? ""
                                        : ": " + body
                        )
                );
            }

            /*
             * API Key不正など。
             */
            if (statusCode == 403) {

                String body =
                        readErrorStream(
                                connection
                        );

                throw new HypixelApiException(
                        403,
                        "Hypixel API access forbidden"
                                + (
                                body.isEmpty()
                                        ? ""
                                        : ": " + body
                        )
                );
            }

            /*
             * その他HTTPエラー。
             */
            if (statusCode < 200
                    || statusCode >= 300) {

                String body =
                        readErrorStream(
                                connection
                        );

                throw new HypixelApiException(
                        statusCode,
                        "Hypixel API returned HTTP "
                                + statusCode
                                + (
                                body.isEmpty()
                                        ? ""
                                        : ": " + body
                        )
                );
            }

            /*
             * 正常レスポンス。
             */
            String response =
                    readInputStream(
                            connection.getInputStream()
                    );

            if (response == null
                    || response.isEmpty()) {

                throw new Exception(
                        "Hypixel API returned empty response"
                );
            }

            /*
             * JSON解析。
             */
            JsonObject root =
                    new JsonParser()
                            .parse(response)
                            .getAsJsonObject();

            /*
             * success=false
             */
            JsonElement successElement =
                    root.get("success");

            if (
                    successElement != null
                            && !successElement
                            .getAsBoolean()
            ) {

                String cause =
                        getString(
                                root,
                                "cause"
                        );

                /*
                 * 念のため、JSON側でも
                 * throttleを429として扱う。
                 */
                if (
                        root.has("throttle")
                                && root.get("throttle")
                                .getAsBoolean()
                ) {

                    throw new HypixelApiException(
                            429,
                            "Hypixel API throttle"
                                    + (
                                    cause == null
                                            ? ""
                                            : ": " + cause
                            )
                    );
                }

                throw new HypixelApiException(
                        0,
                        "Hypixel API request failed"
                                + (
                                cause == null
                                        ? ""
                                        : ": " + cause
                        )
                );
            }

            /*
             * playerが存在しない場合。
             */
            JsonElement playerElement =
                    root.get("player");

            if (
                    playerElement == null
                            || playerElement.isJsonNull()
            ) {

                /*
                 * UUIDは存在するが
                 * Hypixelにデータがない場合など。
                 */
                return null;
            }

            JsonObject player =
                    playerElement
                            .getAsJsonObject();

            /*
             * 名前。
             */
            String name =
                    getString(
                            player,
                            "displayname"
                    );

            /*
             * Network Level
             */
            double networkExp =
                    getDouble(
                            player,
                            "networkExp",
                            0.0D
                    );

            int hypixelLevel =
                    getNetworkLevel(
                            networkExp
                    );

            /*
             * BedWars Level
             */
            JsonObject achievements =
                    getObject(
                            player,
                            "achievements"
                    );

            int bedwarsLevel = 0;

            if (achievements != null) {

                bedwarsLevel =
                        getInt(
                                achievements,
                                "bedwars_level",
                                0
                        );
            }

            return new PlayerStats(
                    name,
                    hypixelLevel,
                    bedwarsLevel
            );

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /*
     * InputStreamを文字列化。
     */
    private String readInputStream(
            InputStream input
    ) throws Exception {

        StringBuilder result =
                new StringBuilder();

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                input,
                                StandardCharsets.UTF_8
                        )
                );

        try {

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                result.append(line);
            }

        } finally {
            reader.close();
        }

        return result.toString();
    }

    /*
     * エラーレスポンスを読む。
     */
    private String readErrorStream(
            HttpURLConnection connection
    ) {

        InputStream input =
                connection.getErrorStream();

        if (input == null) {
            return "";
        }

        try {

            return readInputStream(input);

        } catch (Exception e) {

            return "";
        }
    }

    /*
     * JsonObjectからStringを取得。
     */
    private String getString(
            JsonObject object,
            String key
    ) {

        JsonElement element =
                object.get(key);

        if (
                element == null
                        || element.isJsonNull()
        ) {
            return null;
        }

        try {
            return element.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * JsonObjectからdoubleを取得。
     */
    private double getDouble(
            JsonObject object,
            String key,
            double defaultValue
    ) {

        JsonElement element =
                object.get(key);

        if (
                element == null
                        || element.isJsonNull()
        ) {
            return defaultValue;
        }

        try {
            return element.getAsDouble();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /*
     * JsonObjectからintを取得。
     */
    private int getInt(
            JsonObject object,
            String key,
            int defaultValue
    ) {

        JsonElement element =
                object.get(key);

        if (
                element == null
                        || element.isJsonNull()
        ) {
            return defaultValue;
        }

        try {
            return element.getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /*
     * JsonObjectを取得。
     */
    private JsonObject getObject(
            JsonObject object,
            String key
    ) {

        JsonElement element =
                object.get(key);

        if (
                element == null
                        || element.isJsonNull()
                        || !element.isJsonObject()
        ) {
            return null;
        }

        return element.getAsJsonObject();
    }

    /*
     * Hypixel Network XP → Level
     *
     * ここは後で正確なHypixelの
     * レベル計算式に置き換える。
     */
    private int getNetworkLevel(
            double networkExp
    ) {

        /*
         * 現在の実装を維持する場合はここ。
         *
         * XP / 10000 の単純計算ではなく、
         * Hypixelの実際の累積XPテーブルを
         * 後で実装する。
         */
        return (int)
                (networkExp / 10000.0D);
    }
}