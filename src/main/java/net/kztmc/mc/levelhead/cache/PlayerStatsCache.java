package net.kztmc.mc.levelhead.cache;

import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.api.ApiClient;
import net.kztmc.mc.levelhead.api.HypixelApiException;
import net.kztmc.mc.levelhead.command.LevelHeadCommand;
import net.kztmc.mc.levelhead.config.ModConfig;
import net.minecraft.client.Minecraft;

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

    /*
     * RateLimit-Remainingがこの値以下になったら
     * 通常の高速モードから安全側へ移行する。
     */
    private static final int LOW_RATE_LIMIT_REMAINING = 20;

    /*
     * RateLimitの残量が少ないときの最低リクエスト間隔。
     */
    private static final long LOW_RATE_LIMIT_INTERVAL = 250L;

    /*
     * RateLimit情報が取得できなかった場合の
     * フォールバック間隔。
     */
    private static final long FALLBACK_REQUEST_INTERVAL = 1000L;

    /*
     * 429を受け取った場合の安全な待機時間。
     *
     * 現在のHypixelApiExceptionにはRateLimit-Resetを
     * 保持する仕組みがないため、ここでは60秒待つ。
     */
    private static final long RATE_LIMIT_COOLDOWN = 60000L;


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
            new LinkedHashMap<UUID, CachedPlayerData>(
                    128,
                    0.75f,
                    true
            );


    private final Set<UUID> pending = new HashSet<UUID>();
    private final Map<UUID, Request> requests = new HashMap<UUID, Request>();
    private final PriorityQueue<Request> queue = new PriorityQueue<Request>();

    private final Thread[] workers;


    private long worldGeneration = 0L;

    private volatile boolean running = true;


    /*
     * RateLimit制御
     */
    private long rateLimitUntil = 0L;
    private long nextRequestAt = 0L;
    private int rateLimitRemaining = -1;
    private int rateLimitLimit = -1;
    private long rateLimitReset = -1L;


    /*
     * 現在APIリクエストを実行しているUUID。
     */
    private final Set<UUID> inFlight = new HashSet<UUID>();


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


            /*
             * Minecraft終了時にWorkerが
             * JVM終了を妨げないようにする。
             */
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


        Callback callback =
                new Callback() {

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


            if (priority.value < current.priority.value) {
                if (pending.contains(uuid)) {
                    queue.remove(current);
                    current.priority = priority;
                    queue.offer(current);
                } else {
                    // API実行中なのでqueueには戻さず、優先度だけ更新
                    current.priority = priority;
                }
            }

            return;
        }


        Request request = new Request(uuid, priority, callback);

        if (Main.dev) LevelHeadCommand.send(Minecraft.getMinecraft().thePlayer, "Request queued: " + uuid);

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

            /*
             * APIのRateLimitを確認。
             *
             * 4 Worker全員がここを通るため、
             * RateLimit制御はWorker間で共有される。
             */
            awaitRequestPermit();

            if (!running) {
                synchronized (this) {

                    if (requests.get(request.uuid) == request) {
                        inFlight.remove(request.uuid);
                    }
                }

                return;
            }

            executeRequest(request);
        }
    }


    private void executeRequest(Request request) {
        final long requestGeneration;

        synchronized (this) {
            requestGeneration = worldGeneration;
        }


        try {
            ApiClient apiClient = Main.getApiClient();

            if (apiClient == null) {
                synchronized (this) {
                    if (requests.get(request.uuid) == request) {
                        requests.remove(request.uuid);
                        inFlight.remove(request.uuid);
                    }
                }

                return;
            }


            ApiClient.ApiResponse response = apiClient.fetchPlayer(request.uuid);

            synchronized (this) {

                /*
                 * ワールド移動などによって
                 * 古いRequestになった場合は破棄。
                 */
                if (requestGeneration != worldGeneration) {
                    if (requests.get(request.uuid) == request) {
                        requests.remove(request.uuid);
                        inFlight.remove(request.uuid);
                    }

                    return;
                }


                if (response == null || response.getStats() == null) {
                    if (requests.get(request.uuid) == request) {
                        requests.remove(request.uuid);
                        inFlight.remove(request.uuid);
                    }

                    return;
                }


                /*
                 * ==========================================
                 * RateLimit情報更新
                 * ==========================================
                 */

                updateRateLimit(response);

                /*
                 * キャッシュへ保存。
                 */
                cache.put(request.uuid,
                        new CachedPlayerData(
                                response.getStats(),
                                response.getRawJson()
                        )
                );

                if (Main.dev) LevelHeadCommand.send(Minecraft.getMinecraft().thePlayer, "Cached: "+ request.uuid);


                trimCache();

                if (requests.get(request.uuid) == request) {
                    requests.remove(request.uuid);
                    inFlight.remove(request.uuid);
                }
            }


            /*
             * callbacksをコピーしてから実行。
             */
            Set<Callback> callbacks;


            synchronized (this) {
                callbacks = new HashSet<Callback>(request.callbacks);
            }


            for (Callback callback : callbacks) {

                try {
                    callback.onSuccess(request.uuid, response.getStats());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {


            /*
             * ==========================================
             * HTTP 429
             * ==========================================
             */

            if (e instanceof HypixelApiException && ((HypixelApiException) e).isRateLimited()) {
                if (Main.dev) LevelHeadCommand.send(Minecraft.getMinecraft().thePlayer, "rate limit");
                System.err.println("[LevelHead] Hypixel API rate limited. Pausing requests for 60 seconds.");


                synchronized (this) {

                    if (requestGeneration != worldGeneration) {
                        if (requests.get(request.uuid) == request) {
                            requests.remove(request.uuid);
                            inFlight.remove(request.uuid);
                        }

                        return;
                    }


                    /*
                     * 全Workerを一時停止。
                     */
                    rateLimitUntil = Math.max(rateLimitUntil,
                            System.currentTimeMillis() + RATE_LIMIT_COOLDOWN);


                    /*
                     * このRequestを再キュー。
                     */
                    inFlight.remove(request.uuid);

                    if (!pending.contains(request.uuid) && requests.get(request.uuid) == request) {
                        pending.add(request.uuid);
                        queue.offer(request);
                    }

                    notifyAll();
                }

                return;
            }


            /*
             * 通常のエラー。
             */
            synchronized (this) {

                if (requests.get(request.uuid) == request) {
                    requests.remove(request.uuid);
                    inFlight.remove(request.uuid);
                }
            }


            System.err.println("[LevelHead] Failed to fetch" + request.uuid);

            e.printStackTrace();

            Set<Callback> callbacks;

            synchronized (this) {
                callbacks = new HashSet<Callback>(request.callbacks);
            }


            for (Callback callback : callbacks) {

                try {
                    callback.onFailure(request.uuid, e);
                } catch (Exception callbackException) {
                    callbackException.printStackTrace();
                }
            }
        }
    }


    /*
     * ==========================================
     * RateLimit情報更新
     * ==========================================
     */
    private synchronized void updateRateLimit(ApiClient.ApiResponse response) {

        int limit = response.getRateLimitLimit();
        int remaining = response.getRateLimitRemaining();
        long reset = response.getRateLimitReset();


        if (limit >= 0) rateLimitLimit = limit;

        if (remaining >= 0) rateLimitRemaining = remaining;

        if (reset >= 0) rateLimitReset = reset;


        /*
         * Remainingが0なら、
         * Resetまで待機する。
         */
        if (rateLimitRemaining == 0 && rateLimitReset > 0) {

            /*
             * HypixelのRateLimit-Resetは
             * 「次のリセットまでの秒数」。
             */
            long resetMillis = rateLimitReset * 1000L;


            rateLimitUntil =
                    Math.max(
                            rateLimitUntil,
                            System.currentTimeMillis() + resetMillis
                    );
        }
    }


    /*
     * ==========================================
     * Request許可待ち
     * ==========================================
     */
    private void awaitRequestPermit() {

        synchronized (this) {

            while (running) {

                long now = System.currentTimeMillis();

                /*
                 * RateLimitによる停止。
                 */
                long rateLimitRemainingTime = rateLimitUntil - now;

                /*
                 * 通常のRequest間隔。
                 */
                long intervalRemaining = nextRequestAt - now;


                long waitTime =
                        Math.max(
                                rateLimitRemainingTime,
                                intervalRemaining
                        );

                /*
                 * RateLimit情報がある場合、
                 * Remainingに応じて速度を調整する。
                 */
                if (waitTime <= 0L && rateLimitRemaining >= 0) {
                    long dynamicInterval = getDynamicRequestInterval();
                    nextRequestAt = now + dynamicInterval;

                    return;
                }

                /*
                 * RateLimit情報がまだない場合。
                 */
                if (waitTime <= 0L && rateLimitRemaining < 0) {
                    long interval = getConfiguredRequestInterval();
                    nextRequestAt = now + interval;

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


    /*
     * ==========================================
     * 動的Request間隔
     * ==========================================
     */
    private long getDynamicRequestInterval() {

        /*
         * Limit情報がない。
         */
        if (rateLimitLimit <= 0) {
            return getConfiguredRequestInterval();
        }


        /*
         * 残量が十分にある場合。
         *
         * Workerが4つあるので、
         * ここでは最小限の間隔にする。
         */
        if (rateLimitRemaining > LOW_RATE_LIMIT_REMAINING) {
            return 50L;
        }


        /*
         * 残量が少ない。
         */
        if (rateLimitRemaining > 0) {
            return LOW_RATE_LIMIT_INTERVAL;
        }


        /*
         * Remaining = 0。
         *
         * 基本的にはupdateRateLimit()で
         * rateLimitUntilが設定される。
         */
        return getConfiguredRequestInterval();
    }


    /*
     * ==========================================
     * 設定値取得
     * ==========================================
     */
    private long getConfiguredRequestInterval() {

        long interval = Main.CONFIG.getRequestInterval();


        if (interval <= 0) {
            return FALLBACK_REQUEST_INTERVAL;
        }

        return interval;
    }


    /*
     * ==========================================
     * Queue Reset
     * ==========================================
     */
    public synchronized void resetQueue() {
        if (Main.dev) LevelHeadCommand.send(Minecraft.getMinecraft().thePlayer, "resetQueue");
        worldGeneration++;

        queue.clear();
        pending.clear();
        requests.clear();

        rateLimitUntil = 0L;
        nextRequestAt = 0L;

        rateLimitRemaining = -1;
        rateLimitLimit = -1;
        rateLimitReset = -1L;

        inFlight.clear();

        notifyAll();
    }


    /*
     * ==========================================
     * Cache Clear
     * ==========================================
     */
    public synchronized void clear() {

        cache.clear();
        queue.clear();
        pending.clear();
        requests.clear();

        worldGeneration++;

        rateLimitUntil = 0L;
        nextRequestAt = 0L;

        rateLimitRemaining = -1;
        rateLimitLimit = -1;
        rateLimitReset = -1L;

        inFlight.clear();

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