package net.kztmc.mc.levelhead.cache;

import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.api.ApiClient;
import net.kztmc.mc.levelhead.api.HypixelApiException;
import net.kztmc.mc.levelhead.config.ModConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

public class PlayerStatsCache {

    /*
     * リクエストの優先度。
     *
     * 数字が小さいほど高優先度。
     */
    public enum Priority {

        HIGH(0),
        NORMAL(1),
        LOW(2);

        private final int value;

        Priority(int value) {
            this.value = value;
        }
    }

    /*
     * APIリクエスト。
     */
    private static class Request
            implements Comparable<Request> {

        private final UUID uuid;
        private Priority priority;

        private final long createdAt;

        Request(
                UUID uuid,
                Priority priority
        ) {
            this.uuid = uuid;
            this.priority = priority;
            this.createdAt = System.nanoTime();
        }

        @Override
        public int compareTo(Request other) {

            int priorityCompare =
                    Integer.compare(
                            priority.value,
                            other.priority.value
                    );

            if (priorityCompare != 0) {
                return priorityCompare;
            }

            /*
             * 同じ優先度なら古いリクエストを先に処理。
             */
            return Long.compare(
                    createdAt,
                    other.createdAt
            );
        }
    }

    private final ModConfig config;

    /*
     * キャッシュ本体。
     *
     * accessOrder=true にして、
     * 最近使われたものを後ろにする。
     */
    private final Map<UUID, CachedPlayerStats> cache =
            new LinkedHashMap<UUID, CachedPlayerStats>(
                    128,
                    0.75f,
                    true
            );

    /*
     * 現在キューに入っているUUID。
     */
    private final Set<UUID> pending =
            new HashSet<UUID>();

    /*
     * 実際のRequestをUUIDごとに保持。
     *
     * LOW → HIGH の優先度変更時に
     * 古いRequestを削除するために使用する。
     */
    private final Map<UUID, Request> requests =
            new HashMap<UUID, Request>();

    /*
     * リクエストキュー。
     */
    private final PriorityQueue<Request> queue =
            new PriorityQueue<Request>();

    /*
     * API処理専用スレッド。
     *
     * 必ず1本だけ。
     */
    private final Thread worker;

    /*
     * 初期リクエスト間隔。
     *
     * 1200ms = 約1.2秒
     */
    private static final long INITIAL_INTERVAL = 1200L;

    /*
     * 通常時の最低間隔。
     */
    private static final long MIN_INTERVAL = 500L;

    /*
     * 通常時の最大間隔。
     */
    private static final long MAX_INTERVAL = 10000L;

    /*
     * 429発生時の初回待機時間。
     */
    private static final long INITIAL_BACKOFF = 5000L;

    /*
     * 最大バックオフ。
     */
    private static final long MAX_BACKOFF = 120000L;

    private volatile boolean running = true;

    /*
     * 現在のAPIリクエスト間隔。
     */
    private long requestInterval =
            INITIAL_INTERVAL;

    /*
     * 次回APIリクエストまでの時刻。
     */
    private long nextRequestTime = 0L;

    /*
     * 429連続回数。
     */
    private int rateLimitCount = 0;

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

        /*
         * Minecraft終了時にこのスレッドだけ残らないようにする。
         */
        worker.setDaemon(true);

        worker.start();
    }

    /*
     * 通常取得。
     */
    public PlayerStats get(UUID uuid) {

        return get(
                uuid,
                Priority.NORMAL
        );
    }

    /*
     * 優先度付き取得。
     */
    public synchronized PlayerStats get(
            UUID uuid,
            Priority priority
    ) {

        CachedPlayerStats cached =
                cache.get(uuid);

        /*
         * キャッシュが存在しない。
         */
        if (cached == null) {

            enqueue(
                    uuid,
                    priority
            );

            return null;
        }

        long age =
                System.currentTimeMillis()
                        - cached.getFetchedAt();

        /*
         * キャッシュ期限切れ。
         *
         * 古いデータはそのまま返す。
         * 裏で更新する。
         */
        if (age >
                config.getCacheDurationMillis()) {

            enqueue(
                    uuid,
                    priority
            );
        }

        return cached.getStats();
    }

    /*
     * リクエストをキューに追加。
     */
    private synchronized void enqueue(
            UUID uuid,
            Priority priority
    ) {

        /*
         * すでにキューに存在する場合。
         */
        if (pending.contains(uuid)) {

            Request current =
                    requests.get(uuid);

            if (current == null) {
                return;
            }

            /*
             * 現在より高い優先度なら昇格。
             */
            if (priority.value <
                    current.priority.value) {

                /*
                 * PriorityQueueの要素を直接変更すると
                 * ヒープが壊れるので、
                 * 古いRequestを削除して新しいものを入れる。
                 */
                queue.remove(current);

                Request promoted =
                        new Request(
                                uuid,
                                priority
                        );

                requests.put(
                        uuid,
                        promoted
                );

                queue.offer(promoted);
            }

            return;
        }

        /*
         * 新規リクエスト。
         */
        Request request =
                new Request(
                        uuid,
                        priority
                );

        pending.add(uuid);

        requests.put(
                uuid,
                request
        );

        queue.offer(request);

        /*
         * workerが待機中なら起こす。
         */
        notifyAll();
    }

    /*
     * APIキュー処理。
     */
    private void processQueue() {

        while (running) {

            Request request = null;

            synchronized (this) {

                /*
                 * キューが空なら待機。
                 */
                while (
                        running
                                && queue.isEmpty()
                ) {

                    try {
                        wait();
                    } catch (InterruptedException e) {

                        if (!running) {
                            return;
                        }
                    }
                }

                if (!running) {
                    return;
                }

                /*
                 * APIリクエスト間隔を待つ。
                 */
                long now =
                        System.currentTimeMillis();

                long waitTime =
                        nextRequestTime - now;

                if (waitTime > 0) {

                    try {
                        wait(waitTime);
                    } catch (InterruptedException e) {

                        if (!running) {
                            return;
                        }
                    }

                    continue;
                }

                request =
                        queue.poll();

                if (request == null) {
                    continue;
                }

                /*
                 * pendingからはここで削除。
                 *
                 * API実行中は再リクエストされないように、
                 * requestsには残しておく。
                 */
                pending.remove(
                        request.uuid
                );

                requests.remove(
                        request.uuid
                );
            }

            /*
             * APIアクセス。
             */
            executeRequest(request);
        }
    }

    /*
     * 1件のAPIリクエストを実行。
     */
    private void executeRequest(
            Request request
    ) {

        try {

            ApiClient apiClient =
                    Main.getApiClient();

            if (apiClient == null) {
                return;
            }

            PlayerStats stats =
                    apiClient.fetchPlayer(
                            request.uuid
                    );

            if (stats == null) {
                return;
            }

            synchronized (this) {

                cache.put(
                        request.uuid,
                        new CachedPlayerStats(
                                stats
                        )
                );

                trimCache();
            }

            /*
             * 成功したので429カウンターをリセット。
             */
            synchronized (this) {

                rateLimitCount = 0;

                /*
                 * 徐々にリクエスト間隔を短くする。
                 */
                requestInterval =
                        Math.max(
                                MIN_INTERVAL,
                                requestInterval * 9L / 10L
                        );

                /*
                 * 次回リクエスト時刻。
                 */
                nextRequestTime =
                        System.currentTimeMillis()
                                + requestInterval;
            }

        } catch (Exception e) {

            if (
                    e instanceof HypixelApiException
                            && ((HypixelApiException) e)
                            .isRateLimited()
            ) {

                handleRateLimit();

                synchronized (this) {

                    if (!pending.contains(
                            request.uuid
                    )) {

                        Request retry =
                                new Request(
                                        request.uuid,
                                        request.priority
                                );

                        pending.add(
                                request.uuid
                        );

                        requests.put(
                                request.uuid,
                                retry
                        );

                        queue.offer(retry);

                        notifyAll();
                    }
                }

            } else {

                System.err.println(
                        "[LevelHead] Failed to fetch "
                                + request.uuid
                );

                e.printStackTrace();

                synchronized (this) {

                    nextRequestTime =
                            System.currentTimeMillis()
                                    + requestInterval;
                }
            }
        }
    }

    /*
     * 429処理。
     */
    private synchronized void handleRateLimit() {

        rateLimitCount++;

        long backoff =
                INITIAL_BACKOFF;

        /*
         * 5 → 10 → 20 → 40 → 80 → 120秒
         */
        for (
                int i = 1;
                i < rateLimitCount;
                i++
        ) {

            if (backoff >=
                    MAX_BACKOFF / 2) {

                backoff =
                        MAX_BACKOFF;

                break;
            }

            backoff *= 2L;
        }

        backoff =
                Math.min(
                        backoff,
                        MAX_BACKOFF
                );

        System.err.println(
                "[LevelHead] Hypixel API rate limited. "
                        + "Backing off for "
                        + backoff
                        + "ms."
        );

        nextRequestTime =
                System.currentTimeMillis()
                        + backoff;

        /*
         * 通常のリクエスト間隔も少し伸ばす。
         */
        requestInterval =
                Math.min(
                        MAX_INTERVAL,
                        requestInterval * 2L
                );
    }

    /*
     * キャッシュサイズ制限。
     */
    private void trimCache() {

        while (
                cache.size()
                        > config.getMaxCacheSize()
        ) {

            Iterator<Map.Entry<UUID, CachedPlayerStats>>
                    iterator =
                    cache.entrySet().iterator();

            if (iterator.hasNext()) {

                iterator.next();

                iterator.remove();
            }
        }
    }

    /*
     * UUIDのキャッシュ削除。
     */
    public synchronized void remove(
            UUID uuid
    ) {

        cache.remove(uuid);

        /*
         * キューに存在する場合も削除。
         */
        Request request =
                requests.remove(uuid);

        if (request != null) {
            queue.remove(request);
        }

        pending.remove(uuid);
    }

    /*
     * 全キャッシュ削除。
     */
    public synchronized void clear() {

        cache.clear();
    }

    /*
     * キャッシュ件数。
     */
    public synchronized int size() {

        return cache.size();
    }

    /*
     * 終了処理。
     */
    public void shutdown() {

        running = false;

        synchronized (this) {
            notifyAll();
        }

        worker.interrupt();
    }
}