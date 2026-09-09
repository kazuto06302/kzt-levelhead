package net.kztmc.mc.levelhead.api;

import net.kztmc.mc.levelhead.cache.PlayerStats;

import java.util.UUID;

public interface ApiClient {

    ApiResponse fetchPlayer(UUID uuid) throws Exception;

    class ApiResponse {

        private final PlayerStats stats;
        private final String rawJson;

        private final int rateLimitLimit;
        private final int rateLimitRemaining;
        private final long rateLimitReset;

        public ApiResponse(
                PlayerStats stats,
                String rawJson,
                int rateLimitLimit,
                int rateLimitRemaining,
                long rateLimitReset
        ) {
            this.stats = stats;
            this.rawJson = rawJson;
            this.rateLimitLimit = rateLimitLimit;
            this.rateLimitRemaining = rateLimitRemaining;
            this.rateLimitReset = rateLimitReset;
        }

        public PlayerStats getStats() {
            return stats;
        }

        public String getRawJson() {
            return rawJson;
        }

        public int getRateLimitLimit() {
            return rateLimitLimit;
        }

        public int getRateLimitRemaining() {
            return rateLimitRemaining;
        }

        public long getRateLimitReset() {
            return rateLimitReset;
        }
    }
}