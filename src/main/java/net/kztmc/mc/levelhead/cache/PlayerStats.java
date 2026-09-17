package net.kztmc.mc.levelhead.cache;

public class PlayerStats {

    private final String name;
    private final String rank;
    private final int hypixelLevel;
    private final int bedwarsLevel;
    private final int skywarsLevel;
    private final String skywarsLevelFormatted;
    private final int uhcLevel;

    public PlayerStats(
            String name,
            String rank,
            int hypixelLevel,
            int bedwarsLevel,
            int skywarsLevel,
            String skywarsLevelFormatted,
            int uhcLevel
    ) {
        this.name = name;
        this.rank = rank;
        this.hypixelLevel = hypixelLevel;
        this.bedwarsLevel = bedwarsLevel;
        this.skywarsLevel = skywarsLevel;
        this.skywarsLevelFormatted = skywarsLevelFormatted;
        this.uhcLevel = uhcLevel;
    }

    public String getName() {
        return name;
    }

    public String getRank() {
        return rank;
    }

    public int getHypixelLevel() {
        return hypixelLevel;
    }

    public int getBedwarsLevel() {
        return bedwarsLevel;
    }

    public int getSkywarsLevel() {
        return skywarsLevel;
    }

    public String getSkywarsLevelFormatted() {
        return skywarsLevelFormatted;
    }

    public int getUhcLevel() {
        return uhcLevel;
    }
}