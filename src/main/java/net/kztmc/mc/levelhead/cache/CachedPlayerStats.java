package net.kztmc.mc.levelhead.cache;

public class CachedPlayerStats {

    private final PlayerStats stats;
    private final long fetchedAt;

    public CachedPlayerStats(PlayerStats stats) {
        this.stats = stats;
        this.fetchedAt = System.currentTimeMillis();
    }

    public PlayerStats getStats() {
        return stats;
    }

    public long getFetchedAt() {
        return fetchedAt;
    }
}