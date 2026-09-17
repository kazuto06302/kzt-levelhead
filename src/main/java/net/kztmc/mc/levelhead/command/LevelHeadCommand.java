package net.kztmc.mc.levelhead.command;

import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.config.ModConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Collections;
import java.util.List;

public class LevelHeadCommand extends CommandBase {

    private boolean registered = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {

        if (!registered) {
            ClientCommandHandler.instance.registerCommand(this);
            registered = true;
        }
    }

    @Override
    public String getCommandName() {
        return "levelhead";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/levelhead <key|mode|api|clearcache|reload|interval|game|changeprefix>";
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {

        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                    args,
                    "key",
                    "mode",
                    "api",
                    "clearcache",
                    "reload",
                    "interval",
                    "game",
                    "changeprefix"
            );
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return getListOfStringsMatchingLastWord(args, "hypixel", "custom");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("changeprefix")) {
            return getListOfStringsMatchingLastWord(
                    args,
                    "hypixel",
                    "bedwars",
                    "skywars",
                    "uhc"
            );
        }

        return Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (args.length == 0) {
            help(sender);
            return;
        }

        if (args[0].equalsIgnoreCase("key")) {
            setKey(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("mode")) {
            setMode(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("api")) {
            setApi(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("clearcache")) {
            Main.CACHE.clear();
            send(sender, "Cache cleared.");
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            Main.CONFIG.load();
            Main.rebuildApiClient();
            Main.CACHE.clear();

            send(sender, "Configuration reloaded.");

            return;
        }

        if (args[0].equalsIgnoreCase("interval")) {
            setInterval(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("game")) {
            setGame(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("changeprefix")) {
            changePrefix(sender, args);
            return;
        }

        if (args[0].equalsIgnoreCase("dev")) {
            Main.dev = !Main.dev;
            send(sender,"Developer Mode: " + Main.dev);
            return;
        }

        help(sender);
    }

    private void setKey(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Usage: /levelhead key <API-Key>");
            send(sender, "Current: " + maskapi(Main.CONFIG.getHypixelApiKey()));

            return;
        }

        Main.CONFIG.setHypixelApiKey(args[1]);
        Main.CONFIG.setApiMode(ModConfig.ApiMode.HYPIXEL);
        Main.rebuildApiClient();

        send(sender, "Hypixel API key saved.");
    }

    private void setMode(ICommandSender sender, String[] args) {

        if (args.length < 2) {
            send(sender, "Usage: /levelhead api <hypixel | custom>");
            send(sender, "Current: " + Main.CONFIG.getApiMode().name());
            return;
        }

        if (args[1].equalsIgnoreCase("hypixel")) {
            Main.CONFIG.setApiMode(ModConfig.ApiMode.HYPIXEL);

        } else if (args[1].equalsIgnoreCase("custom")) {
            Main.CONFIG.setApiMode(ModConfig.ApiMode.CUSTOM);

        } else {

            send(sender, "Mode must be hypixel or custom.");
            return;
        }

        Main.rebuildApiClient();
        Main.CACHE.clear();

        send(sender, "API mode changed to " + Main.CONFIG.getApiMode().name());
    }

    private void setApi(ICommandSender sender, String[] args) {

        if (args.length < 2) {
            send(sender, "Usage: /levelhead api <URL>");
            send(sender, "Current: " + Main.CONFIG.getCustomApiUrl());
            return;
        }

        Main.CONFIG.setCustomApiUrl(args[1]);
        Main.CONFIG.setApiMode(ModConfig.ApiMode.CUSTOM);
        Main.rebuildApiClient();
        Main.CACHE.clear();

        send(sender, "Custom API URL saved.");
    }

    private static final int MAX_PREFIX_LENGTH = 32;

    private void changePrefix(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Current[HYPIXEL]: " + Main.CONFIG.getHypixelPrefix());
            send(sender, "Current[BEDWARS]: " + Main.CONFIG.getBedwarsPrefix());
            send(sender, "Current[SKYWARS]: " + Main.CONFIG.getSkywarsPrefix());
            send(sender, "Current[UHC]: " + Main.CONFIG.getUhcPrefix());
            return;
        }

        String game = args[1].toLowerCase();

        // /levelhead changeprefix <game>
        if (args.length < 3) {

            switch (game) {
                case "hypixel":
                case "network":
                case "nw":
                    send(sender, "Current: " + Main.CONFIG.getHypixelPrefix());
                    return;

                case "bedwars":
                case "bw":
                    send(sender, "Current: " + Main.CONFIG.getBedwarsPrefix());
                    return;

                case "skywars":
                case "sw":
                    send(sender, "Current: " + Main.CONFIG.getSkywarsPrefix());
                    return;

                case "uhc":
                    send(sender, "Current: " + Main.CONFIG.getUhcPrefix());
                    return;

                default:
                    send(sender, "Game must be hypixel, bedwars, skywars, or uhc.");
                    return;
            }
        }

        // prefixを結合
        StringBuilder prefixBuilder = new StringBuilder();

        for (int i = 2; i < args.length; i++) {
            if (i > 2) {
                prefixBuilder.append(" ");
            }

            prefixBuilder.append(args[i]);
        }

        // 前後のスペースを削除
        String prefix = prefixBuilder.toString().trim();

        // !reset
        if (prefix.equalsIgnoreCase("!reset")) {

            switch (game) {
                case "hypixel":
                case "network":
                case "nw":
                    Main.CONFIG.setHypixelPrefix("§7NWLevel: §e");
                    break;

                case "bedwars":
                case "bw":
                    Main.CONFIG.setBedwarsPrefix("§7BWLevel: §f");
                    break;

                case "skywars":
                case "sw":
                    Main.CONFIG.setSkywarsPrefix("§7SWLevel: §f");
                    break;

                case "uhc":
                    Main.CONFIG.setUhcPrefix("§7UHCLevel: §f");
                    break;

                default:
                    send(sender, "Game must be hypixel, bedwars, skywars, or uhc.");
                    return;
            }

            send(sender, game.toUpperCase() + " prefix reset.");
            return;
        }

        // 空文字チェック
        if (prefix.isEmpty()) {
            send(sender, "Prefix cannot be empty.");
            return;
        }

        // 文字数制限
        if (prefix.length() > MAX_PREFIX_LENGTH) {
            send(sender, "Prefix is too long. Maximum length is "
                    + MAX_PREFIX_LENGTH + " characters.");
            return;
        }

        // ゲームごとの保存
        switch (game) {
            case "hypixel":
            case "network":
            case "nw":
                Main.CONFIG.setHypixelPrefix(prefix);
                break;

            case "bedwars":
            case "bw":
                Main.CONFIG.setBedwarsPrefix(prefix);
                break;

            case "skywars":
            case "sw":
                Main.CONFIG.setSkywarsPrefix(prefix);
                break;

            case "uhc":
                Main.CONFIG.setUhcPrefix(prefix);
                break;

            default:
                send(sender, "Game must be hypixel, bedwars, skywars, or uhc.");
                return;
        }

        send(sender, "Prefix changed to: " + prefix);
    }

    private void help(ICommandSender sender) {
        send(sender, "§b/levelhead key <key> §7- Set Hypixel API key");
        send(sender, "§b/levelhead mode <hypixel|custom> §7- Change API mode");
        send(sender, "§b/levelhead api <url> §7- Set custom API URL");
        send(sender, "§b/levelhead clearcache §7- Clear cache");
        send(sender, "§b/levelhead reload §7- Reload configuration");
        send(sender, "§b/levelhead interval §7- Set requestInterval");
        send(sender, "§b/levelhead game §7- Set Gamemode");
        send(sender, "§b/levelhead changeprefix <game> <prefix> §7- Change level prefix");
    }

    private void setInterval(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Usage: /levelhead interval <requestInterval(ms)>");
            send(sender, "Current: " + Main.CONFIG.getRequestInterval());
            return;
        }

        try {
            int interval = Integer.parseInt(args[1]);

            if (interval < 0) {
                send(sender, "Interval must be a positive integer.");
                return;
            }

            Main.CONFIG.setRequestInterval(interval);
            Main.rebuildApiClient();

            send(sender, "requestInterval saved.");
        } catch (NumberFormatException e) {
            send(sender, "Invalid number format. Please enter a valid integer.");
        }
    }

    private void setGame(ICommandSender sender, String[] args) {

        if (args.length < 2) {
            send(sender, "Usage: /levelhead game <hypixel | bedwars | skywars | uhc>");
            send(sender, "Current: " + Main.CONFIG.getLevelType().name());
            return;
        }

        String game = args[1].toLowerCase();

        switch (game) {
            case "hypixel":
            case "network":
            case "nw":
                Main.CONFIG.setLevelType(ModConfig.LevelType.HYPIXEL);
                break;

            case "bedwars":
            case "bw":
                Main.CONFIG.setLevelType(ModConfig.LevelType.BEDWARS);
                break;

            case "skywars":
            case "sw":
                Main.CONFIG.setLevelType(ModConfig.LevelType.SKYWARS);
                break;

            case "uhc":
                Main.CONFIG.setLevelType(ModConfig.LevelType.UHC);
                break;

            default:
                send(sender, "Game must be hypixel, bedwars, skywars, or uhc.");
                return;
        }

        Main.rebuildApiClient();
        Main.CACHE.clear();

        send(sender, "Game mode changed to " + Main.CONFIG.getLevelType().name());
    }

    public static void send(ICommandSender sender, String message) {

        sender.addChatMessage(
                new ChatComponentText(
                        EnumChatFormatting.AQUA + "[LevelHead] " +
                                EnumChatFormatting.WHITE +
                                message
                )
        );
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    public static String maskapi(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.charAt(0) + input.substring(1).replaceAll(".", "*");
    }
}