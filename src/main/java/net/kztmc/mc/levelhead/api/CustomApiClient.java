package net.kztmc.mc.levelhead.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kztmc.mc.levelhead.cache.PlayerStats;
import net.kztmc.mc.levelhead.config.ModConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class CustomApiClient implements ApiClient {

    private final ModConfig config;

    public CustomApiClient(ModConfig config) {
        this.config = config;
    }

    @Override
    public PlayerStats fetchPlayer(UUID uuid) throws Exception {

        String template =
                config.getCustomApiUrl();

        if (template == null
                || template.trim().isEmpty()) {

            throw new IllegalStateException(
                    "Custom API URL is not configured"
            );
        }

        String urlString =
                template.replace(
                        "{uuid}",
                        uuid.toString()
                );

        URL url =
                new URL(urlString);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");

        connection.setRequestProperty(
                "User-Agent",
                "LevelHead/1.0"
        );

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        int responseCode =
                connection.getResponseCode();

        if (responseCode < 200
                || responseCode >= 300) {

            throw new Exception(
                    "HTTP " + responseCode
            );
        }

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                "UTF-8"
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

        if (root.has("success")
                && !root.get("success").getAsBoolean()) {

            return null;
        }

        String name = "";

        if (root.has("name")) {
            name = root
                    .get("name")
                    .getAsString();
        }

        int hypixelLevel = 0;

        if (root.has("hypixelLevel")) {
            hypixelLevel =
                    root.get("hypixelLevel")
                            .getAsInt();
        }

        int bedwarsLevel = 0;

        if (root.has("bedwarsLevel")) {
            bedwarsLevel =
                    root.get("bedwarsLevel")
                            .getAsInt();
        }

        return new PlayerStats(
                name,
                hypixelLevel,
                bedwarsLevel
        );
    }
}