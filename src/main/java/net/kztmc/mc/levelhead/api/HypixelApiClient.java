package net.kztmc.mc.levelhead.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kztmc.mc.levelhead.cache.PlayerStats;
import net.kztmc.mc.levelhead.config.ModConfig;

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

    private final ModConfig config;

    public HypixelApiClient(ModConfig config) {
        this.config = config;
    }

    @Override
    public PlayerStats fetchPlayer(UUID uuid) throws Exception {

        String apiKey = config.getHypixelApiKey();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Hypixel API key is not configured"
            );
        }

        URL url = new URL(
                API_URL + uuid.toString()
        );

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");

        connection.setRequestProperty(
                "Api-Key",
                apiKey
        );

        connection.setRequestProperty(
                "User-Agent",
                "LevelHead/1.0"
        );

        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        int responseCode =
                connection.getResponseCode();

        InputStream inputStream;

        if (responseCode >= 200 && responseCode < 300) {
            inputStream = connection.getInputStream();
        } else {
            inputStream = connection.getErrorStream();
        }

        if (inputStream == null) {
            throw new Exception(
                    "HTTP " + responseCode
            );
        }

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                inputStream,
                                StandardCharsets.UTF_8
                        )
                );

        StringBuilder response =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();

        connection.disconnect();

        if (responseCode < 200 || responseCode >= 300) {
            throw new Exception(
                    "HTTP " + responseCode +
                            ": " + response
            );
        }

        return parseResponse(
                response.toString()
        );
    }

    private PlayerStats parseResponse(
            String response
    ) {

        JsonObject root =
                new JsonParser()
                        .parse(response)
                        .getAsJsonObject();

        if (!root.has("success")
                || !root.get("success").getAsBoolean()) {

            return null;
        }

        if (!root.has("player")
                || root.get("player").isJsonNull()) {

            return null;
        }

        JsonObject player =
                root.getAsJsonObject("player");

        String name = "";

        if (player.has("displayname")) {
            name = player
                    .get("displayname")
                    .getAsString();
        }

        int hypixelLevel = 0;

        if (player.has("networkExp")) {
            double xp =
                    player.get("networkExp")
                            .getAsDouble();

            hypixelLevel =
                    calculateNetworkLevel(xp);
        }

        int bedwarsLevel = 0;

        if (player.has("achievements")) {

            JsonObject achievements =
                    player.getAsJsonObject(
                            "achievements"
                    );

            if (achievements.has("bedwars_level")) {
                bedwarsLevel =
                        achievements
                                .get("bedwars_level")
                                .getAsInt();
            }
        }

        return new PlayerStats(
                name,
                hypixelLevel,
                bedwarsLevel
        );
    }

    private int calculateNetworkLevel(double xp) {
        if (xp < 0) {
            return 0;
        }

        /*
         * Hypixel Network Level XP calculation.
         *
         * The first levels require different XP amounts.
         * This uses the standard cumulative XP thresholds.
         */

        double[] required = {
                0,
                10000,
                15000,
                20000,
                25000,
                30000,
                35000,
                40000,
                45000,
                50000
        };

        if (xp < 10000) {
            return 0;
        }

        double remaining =
                xp - 10000;

        int level = 1;

        double requirement = 15000;

        while (remaining >= requirement) {
            remaining -= requirement;
            level++;

            requirement =
                    5000;
        }

        return level;
    }
}