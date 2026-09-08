package net.kztmc.mc.levelhead.cache;

import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.api.ApiClient;
import net.kztmc.mc.levelhead.api.HypixelApiException;
import net.kztmc.mc.levelhead.config.ModConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PlayerStatsCache {

    public enum Priority {

        HIGH(0),
        NORMAL(1),
        LOW(2);

        private final int value;

        Priority(int value) {
            this.value = value;
        }
    }

    public interface Callback {
        void onSuccess(UUID uuid, PlayerStats stats);
        void onFailure(UUID uuid, Exception exception);
    }

    private static class Request implements Comparable<Request> {

        private final UUID uuid;
        private Priority priority;
        private final Set<Callback> callbacks = new HashSet<Callback>();
        private final long createdAt;

        Request(UUID uuid, Priority priority, Callback callback) {
            this.uuid = uuid;
            this.priority = priority;

            if (callback != null) {
                callbacks.add(callback);
            }

            this.createdAt = System.nanoTime();
        }

        @Override
        public int compareTo(Request other) {
            int priorityCompare = Integer.compare(priority.value, other.priority.value);

            if (priorityCompare != 0) {
                return priorityCompare;
            }

            return Long.compare(createdAt, other.createdAt);
        }
    }

    private final ModConfig config;

    private final Map<UUID, CachedPlayerData> cache =
            new LinkedHashMap<UUID, CachedPlayerData>(128, 0.75f, true);

    private final Set<UUID> pending = new HashSet<UUID>();
    private final Map<UUID, Request> requests = new HashMap<UUID, Request>();
    private final PriorityQueue<Request> queue = new PriorityQueue<Request>();
    private final Thread worker;

    private long worldGeneration = 0L;
    private volatile boolean running = true;
    private long rateLimitUntil = 0L;

    public PlayerStatsCache(ModConfig config) {
        this.config = config;
        worker = new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                processQueue();
                            }
                        },
                        "LevelHead-API-Worker"
                );

        worker.setDaemon(true);
        worker.start();
    }

    public PlayerStats get(UUID uuid) {
        return get(uuid, Priority.NORMAL);
    }

    public synchronized PlayerStats get(UUID uuid, Priority priority) {
        CachedPlayerData cached = cache.get(uuid);

        if (cached == null) {
            enqueue(uuid, priority, null);
            return null;
        }

        long age = System.currentTimeMillis() - cached.getFetchedAt();

        if (age > config.getCacheDurationMillis()) {
            enqueue(uuid, priority, null);
        }

        return cached.getStats();
    }

    public synchronized CachedPlayerData getCachedData(UUID uuid) {
        return cache.get(uuid);
    }

    public CachedPlayerData getOrWait(final UUID uuid, Priority priority, long timeoutMillis) {
        CachedPlayerData cached;

        synchronized (this) {
            cached = cache.get(uuid);

            if (cached != null) {
                long age = System.currentTimeMillis() - cached.getFetchedAt();

                if (age > config.getCacheDurationMillis()) {
                    enqueue(uuid, priority, null);
                }

                return cached;
            }
        }

        final CountDownLatch latch = new CountDownLatch(1);

        Callback callback = new Callback() {
                    @Override
                    public void onSuccess(UUID uuid, PlayerStats stats) {
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(UUID uuid, Exception exception) {
                        latch.countDown();
                    }
                };

        enqueue(uuid, priority, callback);

        try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (this) {
            return cache.get(uuid);
        }
    }

    private synchronized void enqueue(UUID uuid, Priority priority, Callback callback) {
        if (pending.contains(uuid)) {
            Request current = requests.get(uuid);

            if (current == null) return;

            if (callback != null) {
                current.callbacks.add(callback);
            }

            if (priority.value < current.priority.value) {
                queue.remove(current);
                current.priority = priority;
                queue.offer(current);
            }

            return;
        }

        Request request = new Request(uuid, priority, callback);

        pending.add(uuid);
        requests.put(uuid, request);
        queue.offer(request);

        notifyAll();
    }

    private void processQueue() {
        while (running) {
            Request request;

            synchronized (this) {
                while (running && queue.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        if (!running) return;
                    }
                }

                if (!running) return;

                request = queue.poll();
                if (request == null) continue;

                pending.remove(request.uuid);
                requests.remove(request.uuid);
            }

            long remaining = rateLimitUntil - System.currentTimeMillis();

            if (remaining > 0L) {
                try {
                    Thread.sleep(remaining);
                } catch (InterruptedException e) {
                    if (!running) return;
                }
            }

            executeRequest(request);

            if (!running) return;

            // HIGHはrequestIntervalを待たず、次のリクエストへ進む
            if (request.priority == Priority.HIGH) {
                continue;
            }

            try {
                Thread.sleep(Main.CONFIG.getRequestInterval());
            } catch (InterruptedException e) {
                if (!running) return;
            }
        }
    }

    private void executeRequest(Request request) {
        final long requestGeneration;

        synchronized (this) {
            requestGeneration = worldGeneration;
        }

        try {
            ApiClient apiClient = Main.getApiClient();

            if (apiClient == null) return;

            ApiClient.ApiResponse response = apiClient.fetchPlayer(request.uuid);

            synchronized (this) {
                if (requestGeneration != worldGeneration) return;
                if (response == null || response.getStats() == null) return;

                cache.put(
                        request.uuid,
                        new CachedPlayerData(
                                response.getStats(),
                                response.getRawJson()
                        )
                );

                trimCache();
            }

            for (Callback callback : request.callbacks) {
                callback.onSuccess(request.uuid, response.getStats());
            }

        } catch (Exception e) {

            if (e instanceof HypixelApiException && ((HypixelApiException) e).isRateLimited()) {

                System.err.println("[LevelHead] Hypixel API rate limited. Pausing requests for 60 seconds.");

                synchronized (this) {
                    if (requestGeneration != worldGeneration) return;

                    rateLimitUntil = System.currentTimeMillis() + 60000L;

                    if (!pending.contains(request.uuid)) {

                        Request retry =
                                new Request(
                                        request.uuid,
                                        request.priority,
                                        null
                                );

                        retry.callbacks.addAll(request.callbacks);
                        pending.add(request.uuid);
                        requests.put(request.uuid, retry);
                        queue.offer(retry);

                        notifyAll();
                    }
                }
                return;
            }

            System.err.println("[LevelHead] Failed to fetch " + request.uuid);

            e.printStackTrace();

            for (Callback callback : request.callbacks) {

                callback.onFailure(request.uuid, e);
            }
        }
    }

    public synchronized void resetQueue() {

        worldGeneration++;

        queue.clear();
        pending.clear();
        requests.clear();

        rateLimitUntil = 0L;

        notifyAll();
    }

    public synchronized void clear() {

        cache.clear();

        queue.clear();
        pending.clear();
        requests.clear();

        worldGeneration++;

        rateLimitUntil = 0L;

        notifyAll();
    }

    public synchronized void remove(UUID uuid) {
        cache.remove(uuid);
        Request request = requests.remove(uuid);

        if (request != null) {
            queue.remove(request);
        }

        pending.remove(uuid);
    }

    public synchronized PlayerStats getCached(UUID uuid) {

        CachedPlayerData cached = cache.get(uuid);

        if (cached == null) return null;

        return cached.getStats();
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized int getQueueSize() {
        return queue.size();
    }

    public void shutdown() {

        running = false;

        synchronized (this) {
            notifyAll();
        }

        worker.interrupt();
    }

    private void trimCache() {

        while (cache.size() > config.getMaxCacheSize()) {

            Iterator<Map.Entry<UUID, CachedPlayerData>> iterator = cache.entrySet().iterator();

            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }
}