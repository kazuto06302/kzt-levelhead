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

    private static final int API_WORKER_COUNT = 4;

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
    private final Set<UUID> inFlight = new HashSet<UUID>();
    private final Map<UUID, Request> requests = new HashMap<UUID, Request>();
    private final PriorityQueue<Request> queue = new PriorityQueue<Request>();
    private final Thread[] workers;

    private long worldGeneration = 0L;
    private volatile boolean running = true;
    private long rateLimitUntil = 0L;
    private long nextRequestAt = 0L;

    public PlayerStatsCache(ModConfig config) {
        this.config = config;
        this.workers = new Thread[API_WORKER_COUNT];

        for (int i = 0; i < API_WORKER_COUNT; i++) {
            final int workerId = i + 1;

            workers[i] = new Thread(
                    new Runnable() {
                        @Override
                        public void run() {
                            processQueue();
                        }
                    },
                    "LevelHead-API-Worker-" + workerId
            );

            workers[i].setDaemon(true);
            workers[i].start();
        }
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
        if (pending.contains(uuid) || inFlight.contains(uuid)) {
            Request current = requests.get(uuid);

            if (current == null) return;

            if (callback != null) {
                current.callbacks.add(callback);
            }

            if (priority.value < current.priority.value && pending.contains(uuid)) {
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
                inFlight.add(request.uuid);
            }

            boolean retry = false;

            try {
                awaitRequestPermit();

                if (!running) return;

                retry = executeRequest(request);
            } finally {
                if (!retry) {
                    synchronized (this) {
                        if (requests.get(request.uuid) == request) {
                            requests.remove(request.uuid);
                            inFlight.remove(request.uuid);
                        }
                    }
                }
            }
        }
    }

    private void awaitRequestPermit() {
        synchronized (this) {
            while (running) {
                long now = System.currentTimeMillis();
                long rateLimitRemaining = rateLimitUntil - now;
                long intervalRemaining = nextRequestAt - now;
                long waitTime = Math.max(rateLimitRemaining, intervalRemaining);

                if (waitTime <= 0L) {
                    nextRequestAt = now + Main.CONFIG.getRequestInterval();
                    return;
                }

                try {
                    wait(waitTime);
                } catch (InterruptedException e) {
                    if (!running) return;
                }
            }
        }
    }

    private boolean executeRequest(Request request) {
        final long requestGeneration;

        synchronized (this) {
            requestGeneration = worldGeneration;
        }

        try {
            ApiClient apiClient = Main.getApiClient();

            if (apiClient == null) return false;

            ApiClient.ApiResponse response = apiClient.fetchPlayer(request.uuid);

            synchronized (this) {
                if (requestGeneration != worldGeneration) return false;
                if (response == null || response.getStats() == null) return false;

                cache.put(
                        request.uuid,
                        new CachedPlayerData(
                                response.getStats(),
                                response.getRawJson()
                        )
                );

                trimCache();
            }

            Set<Callback> callbacks;
            synchronized (this) {
                callbacks = new HashSet<Callback>(request.callbacks);
            }

            for (Callback callback : callbacks) {
                callback.onSuccess(request.uuid, response.getStats());
            }

            return false;

        } catch (Exception e) {

            if (e instanceof HypixelApiException && ((HypixelApiException) e).isRateLimited()) {

                System.err.println("[LevelHead] Hypixel API rate limited. Pausing requests for 60 seconds.");

                synchronized (this) {
                    if (requestGeneration != worldGeneration) return false;

                    rateLimitUntil = Math.max(
                            rateLimitUntil,
                            System.currentTimeMillis() + 60000L
                    );

                    inFlight.remove(request.uuid);

                    if (!pending.contains(request.uuid)) {
                        pending.add(request.uuid);
                        requests.put(request.uuid, request);
                        queue.offer(request);
                    }

                    notifyAll();
                }

                return true;
            }

            System.err.println("[LevelHead] Failed to fetch " + request.uuid);
            e.printStackTrace();

            Set<Callback> callbacks;
            synchronized (this) {
                callbacks = new HashSet<Callback>(request.callbacks);
            }

            for (Callback callback : callbacks) {
                callback.onFailure(request.uuid, e);
            }

            return false;
        }
    }

    public synchronized void resetQueue() {

        worldGeneration++;

        queue.clear();
        pending.clear();
        requests.clear();
        inFlight.clear();

        rateLimitUntil = 0L;
        nextRequestAt = 0L;

        notifyAll();
    }

    public synchronized void clear() {

        cache.clear();

        queue.clear();
        pending.clear();
        requests.clear();
        inFlight.clear();

        worldGeneration++;

        rateLimitUntil = 0L;
        nextRequestAt = 0L;

        notifyAll();
    }

    public synchronized void remove(UUID uuid) {
        cache.remove(uuid);
        Request request = requests.remove(uuid);

        if (request != null) {
            queue.remove(request);
        }

        pending.remove(uuid);
        inFlight.remove(uuid);
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

        for (Thread worker : workers) {
            worker.interrupt();
        }
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
