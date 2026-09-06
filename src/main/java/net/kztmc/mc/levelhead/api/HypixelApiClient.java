package net.kztmc.mc.levelhead.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kztmc.mc.levelhead.Main;
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

    private static final String API_URL = "https://api.hypixel.net/v2/player?uuid=";

    private final String apiKey;

    public HypixelApiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public PlayerStats fetchPlayer(UUID uuid) throws Exception {

        HttpURLConnection connection = null;

        try {
            URL url = new URL(API_URL + uuid.toString());
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestProperty("Api-Key", apiKey);
            connection.setRequestProperty("Accept", "application/json");

            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            connection.setRequestMethod("GET");

            int statusCode = connection.getResponseCode();

            if (statusCode == 429) {
                String body = readErrorStream(connection);
                throw new HypixelApiException(429, "Hypixel API rate limited" + (body.isEmpty() ? "" : ": " + body));
            }

            if (statusCode == 403) {
                String body = readErrorStream(connection);
                throw new HypixelApiException(403, "Hypixel API access forbidden" + (body.isEmpty() ? "" : ": " + body));
            }


            if (statusCode < 200 || statusCode >= 300) {
                String body = readErrorStream(connection);
                throw new HypixelApiException(statusCode, "Hypixel API returned HTTP " + statusCode + (body.isEmpty() ? "" : ": " + body));
            }


            String response = readInputStream(connection.getInputStream());
            if (response == null || response.isEmpty()) {
                throw new Exception("Hypixel API returned empty response");
            }

            JsonObject root = new JsonParser().parse(response).getAsJsonObject();
            JsonElement successElement = root.get("success");

            if (successElement != null && !successElement.getAsBoolean()) {
                String cause = getString(root, "cause");

                if (root.has("throttle") && root.get("throttle").getAsBoolean()) {
                    throw new HypixelApiException(429, "Hypixel API throttle" + (cause == null ? "" : ": " + cause));
                }

                throw new HypixelApiException(0, "Hypixel API request failed" + (cause == null ? "" : ": " + cause));
            }


            JsonElement playerElement = root.get("player");

            if (playerElement == null || playerElement.isJsonNull()) {
                return null;
            }

            JsonObject player = playerElement.getAsJsonObject();
            String name = getString(player, "displayname");

            int hypixelLevel = 0;
            int bedwarsLevel = 0;

            ModConfig.LevelType levelType = Main.CONFIG.getLevelType();

            if (levelType == ModConfig.LevelType.HYPIXEL) {
                double networkExp = getDouble(player, "networkExp", 0.0D);
                hypixelLevel = getNetworkLevel(networkExp);

            } else if (levelType == ModConfig.LevelType.BEDWARS) {
                JsonObject achievements = getObject(player, "achievements");

                if (achievements != null) {
                    bedwarsLevel = getInt(achievements, "bedwars_level", 0);
                }
            }

            return new PlayerStats(name, hypixelLevel, bedwarsLevel);

        } finally {
            if (connection != null) {connection.disconnect();}
        }
    }

    private String readInputStream(InputStream input) throws Exception {

        StringBuilder result = new StringBuilder();

        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));

        try {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);

        } finally {
            reader.close();
        }
        return result.toString();
    }

    private String readErrorStream(HttpURLConnection connection) {
        InputStream input = connection.getErrorStream();

        if (input == null) return "";

        try {
            return readInputStream(input);
        } catch (Exception e) {
            return "";
        }
    }

    private String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);

        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return element.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private double getDouble(JsonObject object, String key, double defaultValue) {
        JsonElement element = object.get(key);

        if (element == null || element.isJsonNull()) return defaultValue;

        try {
            return element.getAsDouble();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int getInt(JsonObject object, String key, int defaultValue) {
        JsonElement element = object.get(key);

        if (element == null || element.isJsonNull()) return defaultValue;

        try {
            return element.getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private JsonObject getObject(JsonObject object, String key) {
        JsonElement element = object.get(key);

        if (element == null || element.isJsonNull() || !element.isJsonObject()) return null;

        return element.getAsJsonObject();
    }

    private int getNetworkLevel(double networkExp) {
        return (int) (networkExp / 10000.0D);
    }
}