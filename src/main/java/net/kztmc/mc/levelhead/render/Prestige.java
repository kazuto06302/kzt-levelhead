package net.kztmc.mc.levelhead.render;

public class Prestige {

    public static String bedwarsPrestige(int level) {

        if (level >= 5000) return "§4%1§5%2§9%3§9%4§1✥";
        if (level >= 4900) return "§a%1§f%2§f%3§a%4§a✥";
        if (level >= 4800) return "§4%1§c%2§6%3§e%4§b✥";
        if (level >= 4700) return "§4%1§a%2§a%3§9%4§1✥";
        if (level >= 4600) return "§b%1§e%2§e%3§6%4§d✥";
        if (level >= 4500) return "§f%1§b%2§b%3§3%4§3✥";
        if (level >= 4400) return "§2%1§a%2§e%3§6%4§5✥";
        if (level >= 4300) return "§5%1§8%2§8%3§5%4§5✥";
        if (level >= 4200) return "§9%1§3%2§b%3§f%4§7✥";
        if (level >= 4100) return "§e%1§6%2§c%3§d%4§d✥";

        if (level >= 4000) return "§5%1§c%2§c%3§d%4§d✥";
        if (level >= 3900) return "§c%1§a%2§a%3§3%4§9✥";
        if (level >= 3800) return "§1%1§9%2§5%3§5%4§d✥";
        if (level >= 3700) return "§4%1§c%2§c%3§d%4§3✥";
        if (level >= 3600) return "§a%1§a%2§b%3§9%4§9✥";
        if (level >= 3500) return "§c%1§4%2§4%3§2%4§a✥";
        if (level >= 3400) return "§a%1§d%2§d%3§5%4§5✥";
        if (level >= 3300) return "§9%1§9%2§d%3§c%4§c✥";
        if (level >= 3200) return "§4%1§7%2§7%3§4%4§c✥";
        if (level >= 3100) return "§9%1§3%2§3%3§d%4§d✥";

        if (level >= 3000) return "§e%1§6%2§6%3§a%4§a⚝";
        if (level >= 2900) return "§b%1§3%2§3%3§9%4§9⚝";
        if (level >= 2800) return "§a%1§2%2§2%3§d%4§d⚝";
        if (level >= 2700) return "§e%1§f%2§f%3§8%4§8⚝";
        if (level >= 2600) return "§4%1§c%2§c%3§d%4§d⚝";
        if (level >= 2500) return "§f%1§a%2§a%3§2%4§2⚝";
        if (level >= 2400) return "§3%1§f%2§f%3§7%4§7⚝";
        if (level >= 2300) return "§5%1§d%2§d%3§6%4§e⚝";
        if (level >= 2200) return "§d%1§f%2§f%3§b%4§3⚝";
        if (level >= 2100) return "§f%1§e%2§e%3§6%4§6⚝";

        if (level >= 2000) return "§7%1§f%2§f%3§7%4§7✪";
        if (level >= 1900) return "§5%1§5%2§5%3§5%4§8✪";
        if (level >= 1800) return "§9%1§9%2§9%3§9%4§1✪";
        if (level >= 1700) return "§d%1§d%2§d%3§d%4§5✪";
        if (level >= 1600) return "§c%1§c%2§c%3§c%4§4✪";
        if (level >= 1500) return "§3%1§3%2§3%3§3%4§9✪";
        if (level >= 1400) return "§a%1§a%2§a%3§a%4§2✪";
        if (level >= 1300) return "§b%1§b%2§b%3§b%4§3✪";
        if (level >= 1200) return "§e%1§e%2§e%3§e%4§6✪";
        if (level >= 1100) return "§f%1§f%2§f%3§f%4§7✪";

        if (level >= 1000) return "§6%1§e%2§a%3§b%4§d✫";

        if (level >= 900) return "§5%1§5%2§5%3§5✫";
        if (level >= 800) return "§9%1§9%2§9%3§9✫";
        if (level >= 700) return "§d%1§d%2§d%3§d✫";
        if (level >= 600) return "§4%1§4%2§4%3§4✫";
        if (level >= 500) return "§3%1§3%2§3%3§3✫";
        if (level >= 400) return "§2%1§2%2§2%3§2✫";
        if (level >= 300) return "§b%1§b%2§b%3§b✫";
        if (level >= 200) return "§6%1§6%2§6%3§6✫";

        if (level >= 100) return "§f%1§f%2§f%3§f✫";
        if (level >= 10) return "§7%1§7%2§7✫";

        return "§7%1✫";
    }

    public static String uhcPrestige(int level) {

        if (level >= 10) return "§4§l" + level + "§r§4✫";
        if (level >= 9) return "§4" + level + "§r§4✫";
        if (level >= 8) return "§4" + level + "§r§4✫";
        if (level >= 7) return "§6" + level + "§r§6✫";
        if (level >= 6) return "§6" + level + "§r§6✫";
        if (level >= 5) return "§6" + level + "§r§6✫";
        if (level >= 4) return "§f" + level + "§r§f✫";
        if (level >= 3) return "§f" + level + "§r§f✫";
        if (level >= 2) return "§7" + level + "§r§7✫";

        return "§7" + level + "§r§7✫";
    }
}