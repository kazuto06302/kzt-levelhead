package net.kztmc.mc.levelhead.cache;

public class PlayerStats {

    private final String name;

    private final int hypixelLevel;

    private final int bedwarsLevel;

    private final int skywarsLevel;

    private final int uhcLevel;

    public PlayerStats(
            String name,
            int hypixelLevel,
            int bedwarsLevel,
            int skywarsLevel,
            int uhcLevel
    ) {
        this.name = name;
        this.hypixelLevel = hypixelLevel;
        this.bedwarsLevel = bedwarsLevel;
        this.skywarsLevel = skywarsLevel;
        this.uhcLevel = uhcLevel;
    }

    public String getName() {
        return name;
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

    public int getUhcLevel() {
        return uhcLevel;
    }
}