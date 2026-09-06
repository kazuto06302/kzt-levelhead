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

    /*
     * APIリクエストの優先度。
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
     * API取得完了時のCallback。
     *
     * 現在はCache内部で使用するが、
     * 後からUI更新などにも利用できる。
     */
    public interface Callback {

        void onSuccess(
                UUID uuid,
                PlayerStats stats
        );

        void onFailure(
                UUID uuid,
                Exception exception
        );
    }

    /*
     * APIリクエスト。
     */
    private static class Request
            implements Comparable<Request> {

        private final UUID uuid;

        private Priority priority;

        private final Callback callback;

        private final long createdAt;

        Request(
                UUID uuid,
                Priority priority,
                Callback callback
        ) {

            this.uuid = uuid;
            this.priority = priority;
            this.callback = callback;

            this.createdAt =
                    System.nanoTime();
        }

        @Override
        public int compareTo(
                Request other
        ) {

            int priorityCompare =
                    Integer.compare(
                            priority.value,
                            other.priority.value
                    );

            if (priorityCompare != 0) {
                return priorityCompare;
            }

            return Long.compare(
                    createdAt,
                    other.createdAt
            );
        }
    }

    private final ModConfig config;

    /*
     * キャッシュ。
     */
    private final Map<UUID, CachedPlayerStats> cache =
            new LinkedHashMap<UUID, CachedPlayerStats>(
                    128,
                    0.75f,
                    true
            );

    /*
     * 現在queueに入っているUUID。
     */
    private final Set<UUID> pending =
            new HashSet<UUID>();

    /*
     * UUID → Request。
     *
     * 優先度昇格に使用。
     */
    private final Map<UUID, Request> requests =
            new HashMap<UUID, Request>();

    /*
     * API Request Queue。
     */
    private final PriorityQueue<Request> queue =
            new PriorityQueue<Request>();

    /*
     * API worker。
     *
     * APIアクセスはこのスレッドだけが行う。
     */
    private final Thread worker;

    /*
     * 1秒間に最大2リクエスト。
     *
     * 500ms = 2 requests/sec
     */
    private static final long REQUEST_INTERVAL = 500L;

    /*
     * ワールド世代。
     *
     * ワールドが変わるたびに増やす。
     *
     * 古いワールドからのAPIレスポンスを
     * 新しいワールドに混ぜないために使う。
     */
    private long worldGeneration = 0L;

    private volatile boolean running = true;

    public PlayerStatsCache(
            ModConfig config
    ) {

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

    /*
     * 通常取得。
     */
    public PlayerStats get(
            UUID uuid
    ) {

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
         * キャッシュなし。
         */
        if (cached == null) {

            enqueue(
                    uuid,
                    priority,
                    null
            );

            return null;
        }

        long age =
                System.currentTimeMillis()
                        - cached.getFetchedAt();

        /*
         * キャッシュ期限切れ。
         *
         * 古いデータを表示しながら
         * バックグラウンドで更新する。
         */
        if (
                age >
                        config.getCacheDurationMillis()
        ) {

            enqueue(
                    uuid,
                    priority,
                    null
            );
        }

        return cached.getStats();
    }

    /*
     * Requestをqueueへ追加。
     */
    private synchronized void enqueue(
            UUID uuid,
            Priority priority,
            Callback callback
    ) {

        /*
         * すでにqueueにある。
         */
        if (pending.contains(uuid)) {

            Request current =
                    requests.get(uuid);

            if (current == null) {
                return;
            }

            /*
             * より高い優先度になった場合、
             * Requestを入れ替える。
             */
            if (
                    priority.value
                            < current.priority.value
            ) {

                queue.remove(current);

                Request promoted =
                        new Request(
                                uuid,
                                priority,
                                callback
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
         * 新しいRequest。
         */
        Request request =
                new Request(
                        uuid,
                        priority,
                        callback
                );

        pending.add(uuid);

        requests.put(
                uuid,
                request
        );

        queue.offer(request);

        /*
         * Workerを起こす。
         */
        notifyAll();
    }

    /*
     * Queue処理。
     */
    private void processQueue() {

        while (running) {

            Request request;

            synchronized (this) {

                /*
                 * Queueが空なら待機。
                 */
                while (
                        running
                                && queue.isEmpty()
                ) {

                    try {
                        wait();

                    } catch (
                            InterruptedException e
                    ) {

                        if (!running) {
                            return;
                        }
                    }
                }

                if (!running) {
                    return;
                }

                /*
                 * 最優先Requestを取得。
                 */
                request =
                        queue.poll();

                if (request == null) {
                    continue;
                }

                pending.remove(
                        request.uuid
                );

                requests.remove(
                        request.uuid
                );
            }

            /*
             * APIリクエスト。
             */
            executeRequest(request);

            /*
             * 1リクエストにつき500ms待つ。
             *
             * 2 requests/sec。
             */
            if (!running) {
                return;
            }

            try {

                Thread.sleep(
                        REQUEST_INTERVAL
                );

            } catch (
                    InterruptedException e
            ) {

                if (!running) {
                    return;
                }
            }
        }
    }

    /*
     * APIリクエスト実行。
     */
    private void executeRequest(
            Request request
    ) {

        /*
         * リクエスト開始時のWorld世代。
         */
        final long requestGeneration;

        synchronized (this) {
            requestGeneration =
                    worldGeneration;
        }

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

            /*
             * API取得中にワールドが変わった。
             *
             * 古い結果は破棄。
             */
            synchronized (this) {

                if (
                        requestGeneration
                                != worldGeneration
                ) {

                    return;
                }

                if (stats == null) {
                    return;
                }

                cache.put(
                        request.uuid,
                        new CachedPlayerStats(
                                stats
                        )
                );

                trimCache();
            }

            /*
             * Callback。
             */
            if (
                    request.callback != null
            ) {

                request.callback.onSuccess(
                        request.uuid,
                        stats
                );
            }

        } catch (
                Exception e
        ) {

            /*
             * 429の場合。
             *
             * ここでは指数バックオフをしない。
             *
             * 次のRequestまで500ms待つ。
             */
            if (
                    e instanceof HypixelApiException
                            && ((HypixelApiException) e)
                            .isRateLimited()
            ) {

                System.err.println(
                        "[LevelHead] Hypixel API "
                                + "rate limited. "
                                + "Request will be retried."
                );

                /*
                 * 429なら再度queueへ。
                 */
                synchronized (this) {

                    /*
                     * ワールドが変わっていたら
                     * 再キューしない。
                     */
                    if (
                            requestGeneration
                                    != worldGeneration
                    ) {
                        return;
                    }

                    if (
                            !pending.contains(
                                    request.uuid
                            )
                    ) {

                        Request retry =
                                new Request(
                                        request.uuid,
                                        request.priority,
                                        request.callback
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

                return;
            }

            System.err.println(
                    "[LevelHead] Failed to fetch "
                            + request.uuid
            );

            e.printStackTrace();

            if (
                    request.callback != null
            ) {

                request.callback.onFailure(
                        request.uuid,
                        e
                );
            }
        }
    }

    /*
     * ワールド変更時に呼ぶ。
     *
     * 重要：
     *
     * ・Queueを全消去
     * ・pendingを全消去
     * ・古いRequestを無効化
     *
     * キャッシュは消さない。
     */
    public synchronized void resetQueue() {

        worldGeneration++;

        queue.clear();

        pending.clear();

        requests.clear();

        System.out.println(
                "[LevelHead] API request queue reset."
                        + " World generation: "
                        + worldGeneration
        );

        notifyAll();
    }

    /*
     * キャッシュも含めて全部消す。
     */
    public synchronized void clear() {

        cache.clear();

        queue.clear();

        pending.clear();

        requests.clear();

        worldGeneration++;

        notifyAll();
    }

    /*
     * 特定UUIDのキャッシュ削除。
     */
    public synchronized void remove(
            UUID uuid
    ) {

        cache.remove(uuid);

        Request request =
                requests.remove(uuid);

        if (request != null) {
            queue.remove(request);
        }

        pending.remove(uuid);
    }

    /*
     * キャッシュサイズ。
     */
    public synchronized int size() {

        return cache.size();
    }

    /*
     * Queueサイズ。
     *
     * デバッグ用。
     */
    public synchronized int getQueueSize() {

        return queue.size();
    }

    /*
     * Worker終了。
     */
    public void shutdown() {

        running = false;

        synchronized (this) {
            notifyAll();
        }

        worker.interrupt();
    }

    /*
     * キャッシュサイズ制限。
     */
    private void trimCache() {

        while (
                cache.size()
                        > config.getMaxCacheSize()
        ) {

            Iterator<
                    Map.Entry<
                            UUID,
                            CachedPlayerStats
                            >
                    >
                    iterator =
                    cache.entrySet()
                            .iterator();

            if (iterator.hasNext()) {

                iterator.next();

                iterator.remove();
            }
        }
    }
}