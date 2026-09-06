package net.kztmc.mc.levelhead.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ModConfig {

    public enum ApiMode {
        HYPIXEL,
        CUSTOM
    }

    public enum LevelType {
        HYPIXEL,
        BEDWARS
    }

    private static class Data {

        String apiMode = "HYPIXEL";

        String hypixelApiKey = "";

        String customApiUrl =
                "https://example.com/player/{uuid}";

        long cacheDurationHours = 6;

        int maxCacheSize = 10000;

        /*
         * 表示・取得するレベル。
         *
         * HYPIXEL = Network Level
         * BEDWARS = BedWars Level
         */
        String levelType = "HYPIXEL";
    }

    private final File file;

    private final Gson gson =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private Data data = new Data();

    public ModConfig(File configDirectory) {

        File directory = new File(
                configDirectory,
                "levelhead"
        );

        if (!directory.exists()) {
            directory.mkdirs();
        }

        file = new File(
                directory,
                "config.json"
        );
    }

    public void load() {

        if (!file.exists()) {
            save();
            return;
        }

        try {

            FileReader reader =
                    new FileReader(file);

            Data loaded =
                    gson.fromJson(
                            reader,
                            Data.class
                    );

            reader.close();

            if (loaded != null) {
                data = loaded;
            }

        } catch (Exception e) {

            System.err.println(
                    "[LevelHead] Failed to load config"
            );

            e.printStackTrace();
        }
    }

    public void save() {

        try {

            FileWriter writer =
                    new FileWriter(file);

            gson.toJson(
                    data,
                    writer
            );

            writer.flush();
            writer.close();

        } catch (Exception e) {

            System.err.println(
                    "[LevelHead] Failed to save config"
            );

            e.printStackTrace();
        }
    }

    public ApiMode getApiMode() {

        try {

            return ApiMode.valueOf(
                    data.apiMode.toUpperCase()
            );

        } catch (Exception e) {

            return ApiMode.HYPIXEL;
        }
    }

    public void setApiMode(ApiMode mode) {

        data.apiMode =
                mode.name();

        save();
    }

    public String getHypixelApiKey() {

        return data.hypixelApiKey;
    }

    public void setHypixelApiKey(
            String key
    ) {

        data.hypixelApiKey = key;

        save();
    }

    public String getCustomApiUrl() {

        return data.customApiUrl;
    }

    public void setCustomApiUrl(
            String url
    ) {

        data.customApiUrl = url;

        save();
    }

    public long getCacheDurationMillis() {

        return data.cacheDurationHours
                * 60L
                * 60L
                * 1000L;
    }

    public long getCacheDurationHours() {

        return data.cacheDurationHours;
    }

    public void setCacheDurationHours(
            long hours
    ) {

        data.cacheDurationHours =
                Math.max(1, hours);

        save();
    }

    public int getMaxCacheSize() {

        return data.maxCacheSize;
    }

    public void setMaxCacheSize(
            int size
    ) {

        data.maxCacheSize =
                Math.max(100, size);

        save();
    }

    public LevelType getLevelType() {

        try {

            return LevelType.valueOf(
                    data.levelType.toUpperCase()
            );

        } catch (Exception e) {

            return LevelType.HYPIXEL;
        }
    }

    public void setLevelType(
            LevelType type
    ) {

        data.levelType =
                type.name();

        save();
    }

    public File getFile() {

        return file;
    }
}