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

    private static class Request
            implements Comparable<Request> {

        private final UUID uuid;
        private Priority priority;
        private final Callback callback;
        private final long createdAt;

        Request(UUID uuid, Priority priority, Callback callback) {

            this.uuid = uuid;
            this.priority = priority;
            this.callback = callback;

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

    private final Map<UUID, CachedPlayerStats> cache =
            new LinkedHashMap<UUID, CachedPlayerStats>(
                    128,
                    0.75f,
                    true
            );


    private final Set<UUID> pending = new HashSet<UUID>();
    private final Map<UUID, Request> requests = new HashMap<UUID, Request>();
    private final PriorityQueue<Request> queue = new PriorityQueue<Request>();


    private final Thread worker;
    private static final long REQUEST_INTERVAL = 500L;
    private long worldGeneration = 0L;
    private volatile boolean running = true;

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
        CachedPlayerStats cached = cache.get(uuid);

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


    private synchronized void enqueue(UUID uuid, Priority priority, Callback callback){
        if (pending.contains(uuid)) {
            Request current = requests.get(uuid);

            if (current == null) return;

            // 入れ替え
            if (priority.value < current.priority.value) {
                queue.remove(current);
                Request promoted = new Request(uuid, priority, callback);

                requests.put(uuid, promoted);
                queue.offer(promoted);
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

            executeRequest(request);

            if (!running) return;

            try {
                Thread.sleep(Main.CONFIG.getrequestInterval());
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

            PlayerStats stats = apiClient.fetchPlayer(request.uuid);

            synchronized (this) {

                if (requestGeneration != worldGeneration) return;
                if (stats == null) return;

                cache.put(request.uuid, new CachedPlayerStats(stats));

                trimCache();
            }

            if (request.callback != null) {
                request.callback.onSuccess(request.uuid, stats);
            }

        } catch (Exception e) {

            if (e instanceof HypixelApiException && ((HypixelApiException) e).isRateLimited()) {
                System.err.println("[LevelHead] Hypixel API " + "rate limited. " + "Request will be retried.");

                synchronized (this) {
                    if (requestGeneration != worldGeneration) return;

                    if (!pending.contains(request.uuid)) {
                        Request retry = new Request(request.uuid, request.priority, request.callback);

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

            if (request.callback != null) {
                request.callback.onFailure(request.uuid, e);
            }
        }
    }


    public synchronized void resetQueue() {
        worldGeneration++;

        queue.clear();
        pending.clear();
        requests.clear();

        notifyAll();
    }

    public synchronized void clear() {
        cache.clear();
        queue.clear();
        pending.clear();
        requests.clear();
        worldGeneration++;

        notifyAll();
    }

    public synchronized void remove(UUID uuid) {
        cache.remove(uuid);
        Request request = requests.remove(uuid);

        if (request != null) {queue.remove(request);}

        pending.remove(uuid);
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

            Iterator< Map.Entry<UUID, CachedPlayerStats> > iterator = cache.entrySet().iterator();

            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }
}