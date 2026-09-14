package net.kztmc.mc.levelhead.render;

public class Prestige {

    public static String bedwarsPrestige(int level) {

        String prestige;

        if (level >= 5000) prestige = "§4%1§5%2§9%3§9%4§1✥";
        else if (level >= 4900) prestige = "§a%1§f%2§f%3§a%4§a✥";
        else if (level >= 4800) prestige = "§4%1§c%2§6%3§e%4§b✥";
        else if (level >= 4700) prestige = "§4%1§a%2§a%3§9%4§1✥";
        else if (level >= 4600) prestige = "§b%1§e%2§e%3§6%4§d✥";
        else if (level >= 4500) prestige = "§f%1§b%2§b%3§3%4§3✥";
        else if (level >= 4400) prestige = "§2%1§a%2§e%3§6%4§5✥";
        else if (level >= 4300) prestige = "§5%1§8%2§8%3§5%4§5✥";
        else if (level >= 4200) prestige = "§9%1§3%2§b%3§f%4§7✥";
        else if (level >= 4100) prestige = "§e%1§6%2§c%3§d%4§d✥";

        else if (level >= 4000) prestige = "§5%1§c%2§c%3§d%4§d✥";
        else if (level >= 3900) prestige = "§c%1§a%2§a%3§3%4§9✥";
        else if (level >= 3800) prestige = "§1%1§9%2§5%3§5%4§d✥";
        else if (level >= 3700) prestige = "§4%1§c%2§c%3§d%4§3✥";
        else if (level >= 3600) prestige = "§a%1§a%2§b%3§9%4§9✥";
        else if (level >= 3500) prestige = "§c%1§4%2§4%3§2%4§a✥";
        else if (level >= 3400) prestige = "§a%1§d%2§d%3§5%4§5✥";
        else if (level >= 3300) prestige = "§9%1§9%2§d%3§c%4§c✥";
        else if (level >= 3200) prestige = "§4%1§7%2§7%3§4%4§c✥";
        else if (level >= 3100) prestige = "§9%1§3%2§3%3§d%4§d✥";

        else if (level >= 3000) prestige = "§e%1§6%2§6%3§a%4§a⚝";
        else if (level >= 2900) prestige = "§b%1§3%2§3%3§9%4§9⚝";
        else if (level >= 2800) prestige = "§a%1§2%2§2%3§d%4§d⚝";
        else if (level >= 2700) prestige = "§e%1§f%2§f%3§8%4§8⚝";
        else if (level >= 2600) prestige = "§4%1§c%2§c%3§d%4§d⚝";
        else if (level >= 2500) prestige = "§f%1§a%2§a%3§2%4§2⚝";
        else if (level >= 2400) prestige = "§3%1§f%2§f%3§7%4§7⚝";
        else if (level >= 2300) prestige = "§5%1§d%2§d%3§6%4§e⚝";
        else if (level >= 2200) prestige = "§d%1§f%2§f%3§b%4§3⚝";
        else if (level >= 2100) prestige = "§f%1§e%2§e%3§6%4§6⚝";

        else if (level >= 2000) prestige = "§7%1§f%2§f%3§7%4§7✪";
        else if (level >= 1900) prestige = "§5%1§5%2§5%3§5%4§8✪";
        else if (level >= 1800) prestige = "§9%1§9%2§9%3§9%4§1✪";
        else if (level >= 1700) prestige = "§d%1§d%2§d%3§d%4§5✪";
        else if (level >= 1600) prestige = "§c%1§c%2§c%3§c%4§4✪";
        else if (level >= 1500) prestige = "§3%1§3%2§3%3§3%4§9✪";
        else if (level >= 1400) prestige = "§a%1§a%2§a%3§a%4§2✪";
        else if (level >= 1300) prestige = "§b%1§b%2§b%3§b%4§3✪";
        else if (level >= 1200) prestige = "§e%1§e%2§e%3§e%4§6✪";
        else if (level >= 1100) prestige = "§f%1§f%2§f%3§f%4§7✪";

        else if (level >= 1000) prestige = "§6%1§e%2§a%3§b%4§d✫";

        else if (level >= 900) prestige = "§5%1§5%2§5%3§5✫";
        else if (level >= 800) prestige = "§9%1§9%2§9%3§9✫";
        else if (level >= 700) prestige = "§d%1§d%2§d%3§d✫";
        else if (level >= 600) prestige = "§4%1§4%2§4%3§4✫";
        else if (level >= 500) prestige = "§3%1§3%2§3%3§3✫";
        else if (level >= 400) prestige = "§2%1§2%2§2%3§2✫";
        else if (level >= 300) prestige = "§b%1§b%2§b%3§b✫";
        else if (level >= 200) prestige = "§6%1§6%2§6%3§6✫";

        else if (level >= 100) prestige = "§f%1§f%2§f%3§f✫";
        else if (level >= 10) prestige = "§7%1§7%2§7✫";
        else prestige = "§7%1✫";

        String levelString = String.valueOf(level);

        for (int i = 0; i < levelString.length(); i++) {
            prestige = prestige.replace(
                    "%" + (i + 1),
                    String.valueOf(levelString.charAt(i))
            );
        }

        prestige = prestige
                .replace("%1", "")
                .replace("%2", "")
                .replace("%3", "")
                .replace("%4", "");

        return prestige;
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