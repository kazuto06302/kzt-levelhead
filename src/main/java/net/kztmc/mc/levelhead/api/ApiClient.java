package net.kztmc.mc.levelhead.api;

import net.kztmc.mc.levelhead.cache.PlayerStats;

import java.util.UUID;

public interface ApiClient {

    ApiResponse fetchPlayer(UUID uuid) throws Exception;

    class ApiResponse {

        private final PlayerStats stats;
        private final String rawJson;

        public ApiResponse(
                PlayerStats stats,
                String rawJson
        ) {
            this.stats = stats;
            this.rawJson = rawJson;
        }

        public PlayerStats getStats() {
            return stats;
        }

        public String getRawJson() {
            return rawJson;
        }
    }
}