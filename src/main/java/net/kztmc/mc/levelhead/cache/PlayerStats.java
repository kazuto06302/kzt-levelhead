package net.kztmc.mc.levelhead.cache;

public class PlayerStats {

    private final String name;
    private final int hypixelLevel;
    private final int bedwarsLevel;

    public PlayerStats(
            String name,
            int hypixelLevel,
            int bedwarsLevel
    ) {
        this.name = name;
        this.hypixelLevel = hypixelLevel;
        this.bedwarsLevel = bedwarsLevel;
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
}