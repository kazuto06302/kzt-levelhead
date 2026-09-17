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
        BEDWARS,
        SKYWARS,
        UHC
    }

    private static class Data {

        String apiMode = "HYPIXEL";

        String hypixelApiKey = "";

        String customApiUrl = "https://example.com/player/{uuid}";

        long cacheDurationHours = 6;

        int maxCacheSize = 10000;

        String levelType = "BEDWARS";

        int requestInterval = 1000;

        String hypixelPrefix = "§7NWLevel: §e";
        String bedwarsPrefix = "§7BWLevel: §f";
        String skywarsPrefix = "§7SWLevel: §f";
        String uhcPrefix = "§7UHCLevel: §f";
    }

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Data data = new Data();

    public ModConfig(File configDirectory) {
        File directory = new File(configDirectory, "levelhead");
        if (!directory.exists()) directory.mkdirs();

        file = new File(directory, "config.json");
    }

    public void load() {

        if (!file.exists()) {
            save();
            return;
        }

        try {
            FileReader reader = new FileReader(file);
            Data loaded = gson.fromJson(reader, Data.class);
            reader.close();

            if (loaded != null) data = loaded;
        } catch (Exception e) {
            System.err.println("[LevelHead] Failed to load config");
            e.printStackTrace();
        }
    }

    public void save() {
        try {
            FileWriter writer = new FileWriter(file);

            gson.toJson(data, writer);

            writer.flush();
            writer.close();

        } catch (Exception e) {
            System.err.println("[LevelHead] Failed to save config");
            e.printStackTrace();
        }
    }

    public ApiMode getApiMode() {

        try {
            return ApiMode.valueOf(data.apiMode.toUpperCase());
        } catch (Exception e) {
            return ApiMode.HYPIXEL;
        }
    }

    public void setApiMode(ApiMode mode) {
        data.apiMode = mode.name();
        save();
    }

    public String getHypixelApiKey() {
        return data.hypixelApiKey;
    }

    public void setHypixelApiKey(String key) {
        data.hypixelApiKey = key;
        save();
    }

    public String getCustomApiUrl() {
        return data.customApiUrl;
    }

    public void setCustomApiUrl(String url) {
        data.customApiUrl = url;
        save();
    }

    public void setRequestInterval(int requestInterval) {
        data.requestInterval = requestInterval;
        save();
    }

    public long getCacheDurationMillis() {
        return data.cacheDurationHours * 60L * 60L * 1000L;
    }

    public int getMaxCacheSize() {
        return data.maxCacheSize;
    }

    public LevelType getLevelType() {
        try {
            return LevelType.valueOf(data.levelType.toUpperCase());

        } catch (Exception e) {
            return LevelType.HYPIXEL;

        }
    }

    public void setLevelType(LevelType type) {
        data.levelType = type.name();
        save();
    }

    public int getRequestInterval() {
        return data.requestInterval;
    }

    public String getHypixelPrefix() {
        return data.hypixelPrefix;
    }

    public void setHypixelPrefix(String prefix) {
        data.hypixelPrefix = prefix;
        save();
    }

    public String getBedwarsPrefix() {
        return data.bedwarsPrefix;
    }

    public void setBedwarsPrefix(String prefix) {
        data.bedwarsPrefix = prefix;
        save();
    }

    public String getSkywarsPrefix() {
        return data.skywarsPrefix;
    }

    public void setSkywarsPrefix(String prefix) {
        data.skywarsPrefix = prefix;
        save();
    }

    public String getUhcPrefix() {
        return data.uhcPrefix;
    }

    public void setUhcPrefix(String prefix) {
        data.uhcPrefix = prefix;
        save();
    }
}