package net.kztmc.mc.levelhead.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.cache.PlayerStats;
import net.kztmc.mc.levelhead.command.LevelHeadCommand;
import net.kztmc.mc.levelhead.config.ModConfig;
import net.minecraft.client.Minecraft;

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
    public ApiResponse fetchPlayer(UUID uuid) throws Exception {

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

                LevelHeadCommand.send(Minecraft.getMinecraft().thePlayer,
                        "§6[I] HTTP " + statusCode +
                                " | Remaining: " +
                                connection.getHeaderField("RateLimit-Remaining") +
                                " | Reset: " +
                                connection.getHeaderField("RateLimit-Reset")
                );

                LevelHeadCommand.send(Minecraft.getMinecraft().thePlayer,
                        "§6[L] LevelHead API UUID: " + uuid
                );

                throw new HypixelApiException(
                        429,
                        "Hypixel API rate limited" +
                                (body.isEmpty() ? "" : ": " + body)
                );
            }

            if (statusCode == 403) {
                String body = readErrorStream(connection);

                throw new HypixelApiException(
                        403,
                        "Hypixel API access forbidden" +
                                (body.isEmpty() ? "" : ": " + body)
                );
            }

            if (statusCode < 200 || statusCode >= 300) {
                String body = readErrorStream(connection);

                throw new HypixelApiException(
                        statusCode,
                        "Hypixel API returned HTTP " + statusCode +
                                (body.isEmpty() ? "" : ": " + body)
                );
            }

            String response = readInputStream(
                    connection.getInputStream()
            );

            if (response == null || response.isEmpty()) {
                throw new Exception(
                        "Hypixel API returned empty response"
                );
            }

            JsonObject root =
                    new JsonParser()
                            .parse(response)
                            .getAsJsonObject();

            JsonElement successElement = root.get("success");

            if (successElement != null &&
                    !successElement.getAsBoolean()) {

                String cause = getString(root, "cause");

                if (root.has("throttle") &&
                        root.get("throttle").getAsBoolean()) {

                    throw new HypixelApiException(
                            429,
                            "Hypixel API throttle" +
                                    (cause == null ? "" : ": " + cause)
                    );
                }

                throw new HypixelApiException(
                        0,
                        "Hypixel API request failed" +
                                (cause == null ? "" : ": " + cause)
                );
            }

            JsonElement playerElement = root.get("player");

            if (playerElement == null ||
                    playerElement.isJsonNull()) {

                return new ApiResponse(null, response);
            }

            JsonObject player =
                    playerElement.getAsJsonObject();

            String name =
                    getString(player, "displayname");

            int hypixelLevel = 0;
            int bedwarsLevel = 0;
            int skywarsLevel = 0;
            int uhcLevel = 0;

            ModConfig.LevelType levelType =
                    Main.CONFIG.getLevelType();

            if (levelType == ModConfig.LevelType.HYPIXEL) {

                double networkExp =
                        getDouble(
                                player,
                                "networkExp",
                                0.0D
                        );

                hypixelLevel =
                        getNetworkLevel(networkExp);

            } else if (levelType == ModConfig.LevelType.BEDWARS) {

                JsonObject achievements =
                        getObject(
                                player,
                                "achievements"
                        );

                if (achievements != null) {
                    bedwarsLevel =
                            getInt(
                                    achievements,
                                    "bedwars_level",
                                    0
                            );
                }

            } else if (levelType == ModConfig.LevelType.SKYWARS) {

                JsonObject stats =
                        getObject(
                                player,
                                "stats"
                        );

                JsonObject skywars =
                        getObject(
                                stats,
                                "SkyWars"
                        );

                if (skywars != null) {

                    double experience =
                            getDouble(
                                    skywars,
                                    "skywars_experience",
                                    0.0D
                            );

                    skywarsLevel =
                            getSkyWarsLevel(experience);
                }

            } else if (levelType == ModConfig.LevelType.UHC) {

                JsonObject stats =
                        getObject(
                                player,
                                "stats"
                        );

                JsonObject uhc =
                        getObject(
                                stats,
                                "UHC"
                        );

                if (uhc != null) {

                    int score =
                            getInt(
                                    uhc,
                                    "score",
                                    0
                            );

                    uhcLevel =
                            getUhcLevel(score);
                }
            }

            PlayerStats stats =
                    new PlayerStats(
                            name,
                            hypixelLevel,
                            bedwarsLevel,
                            skywarsLevel,
                            uhcLevel
                    );

            return new ApiResponse(
                    stats,
                    response
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

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

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

        } finally {
            reader.close();
        }

        return result.toString();
    }

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

    private String getString(
            JsonObject object,
            String key
    ) {

        JsonElement element =
                object.get(key);

        if (element == null ||
                element.isJsonNull()) {

            return null;
        }

        try {
            return element.getAsString();

        } catch (Exception e) {
            return null;
        }
    }

    private double getDouble(
            JsonObject object,
            String key,
            double defaultValue
    ) {

        JsonElement element =
                object.get(key);

        if (element == null ||
                element.isJsonNull()) {

            return defaultValue;
        }

        try {
            return element.getAsDouble();

        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int getInt(
            JsonObject object,
            String key,
            int defaultValue
    ) {

        JsonElement element =
                object.get(key);

        if (element == null ||
                element.isJsonNull()) {

            return defaultValue;
        }

        try {
            return element.getAsInt();

        } catch (Exception e) {
            return defaultValue;
        }
    }

    private JsonObject getObject(
            JsonObject object,
            String key
    ) {

        JsonElement element =
                object.get(key);

        if (element == null ||
                element.isJsonNull() ||
                !element.isJsonObject()) {

            return null;
        }

        return element.getAsJsonObject();
    }

    private int getNetworkLevel(double networkExp) {
        if (networkExp < 0.0D) return 1;
        return (int) Math.floor(Math.sqrt(networkExp / 1250.0D + 12.25D) - 3.5D) + 1;
    }

    private int getSkyWarsLevel(double experience) {
        if (experience < 20) return 1;
        if (experience < 70) return 2;
        if (experience < 150) return 3;
        if (experience < 250) return 4;
        if (experience < 500) return 5;
        if (experience < 1000) return 6;
        if (experience < 2000) return 7;
        if (experience < 3500) return 8;
        if (experience < 6000) return 9;
        if (experience < 10000) return 10;
        if (experience < 15000) return 11;

        return 12 + (int) Math.floor((experience - 15000) / 10000.0D);
    }

    private int getUhcLevel(int score) {
        if (score < 10) return 1;
        if (score < 60) return 2;
        if (score < 210) return 3;
        if (score < 460) return 4;
        if (score < 960) return 5;
        if (score < 1710) return 6;
        if (score < 2710) return 7;
        if (score < 5210) return 8;
        if (score < 10210) return 9;
        if (score < 13210) return 10;
        if (score < 16210) return 11;
        if (score < 19210) return 12;
        if (score < 22210) return 13;
        if (score < 25210) return 14;

        return 15;
    }
}