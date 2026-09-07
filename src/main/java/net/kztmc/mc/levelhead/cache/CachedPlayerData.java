package net.kztmc.mc.levelhead.cache;

public class CachedPlayerData {

    private final PlayerStats stats;
    private final String rawJson;
    private final long fetchedAt;

    public CachedPlayerData(
            PlayerStats stats,
            String rawJson
    ) {
        this.stats = stats;
        this.rawJson = rawJson;
        this.fetchedAt = System.currentTimeMillis();
    }

    public PlayerStats getStats() {
        return stats;
    }

    public String getRawJson() {
        return rawJson;
    }

    public long getFetchedAt() {
        return fetchedAt;
    }
}