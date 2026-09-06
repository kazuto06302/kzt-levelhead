package net.kztmc.mc.levelhead.cache;

import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.api.ApiClient;
import net.kztmc.mc.levelhead.config.ModConfig;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerStatsCache {

    private final ModConfig config;

    private final Map<UUID, CachedPlayerStats> cache =
            new LinkedHashMap<UUID, CachedPlayerStats>(
                    128,
                    0.75f,
                    true
            );

    private final Map<UUID, Boolean> loading =
            new LinkedHashMap<UUID, Boolean>();

    private final ExecutorService executor =
            Executors.newFixedThreadPool(2);

    public PlayerStatsCache(ModConfig config) {
        this.config = config;
    }

    public synchronized PlayerStats get(UUID uuid) {
        CachedPlayerStats cached = cache.get(uuid);

        if (cached == null) {
            request(uuid);
            return null;
        }

        long age = System.currentTimeMillis() - cached.getFetchedAt();

        if (age > config.getCacheDurationMillis()) {
            request(uuid);
        }

        return cached.getStats();
    }

    private synchronized void request(final UUID uuid) {
        if (loading.containsKey(uuid)) {
            return;
        }

        loading.put(uuid, true);

        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ApiClient apiClient = Main.getApiClient();

                    if (apiClient == null) {
                        return;
                    }

                    PlayerStats stats = apiClient.fetchPlayer(uuid);

                    if (stats == null) {
                        return;
                    }

                    synchronized (PlayerStatsCache.this) {
                        cache.put(
                                uuid,
                                new CachedPlayerStats(stats)
                        );

                        trimCache();
                    }

                } catch (Exception e) {
                    System.err.println(
                            "[LevelHead] Failed to fetch " + uuid
                    );

                    e.printStackTrace();

                } finally {
                    synchronized (PlayerStatsCache.this) {
                        loading.remove(uuid);
                    }
                }
            }
        });
    }

    private void trimCache() {
        while (cache.size() > config.getMaxCacheSize()) {
            Iterator<Map.Entry<UUID, CachedPlayerStats>> iterator =
                    cache.entrySet().iterator();

            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    public synchronized void remove(UUID uuid) {
        cache.remove(uuid);
        loading.remove(uuid);
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}