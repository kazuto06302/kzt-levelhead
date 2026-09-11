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
        return "/levelhead <key|mode|api|clearcache|reload|interval|game>";
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
                    "game"
            );
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return getListOfStringsMatchingLastWord(args, "hypixel", "custom");
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

    private void help(ICommandSender sender) {
        send(sender, "§b/levelhead key <key> §7- Set Hypixel API key");
        send(sender, "§b/levelhead mode <hypixel|custom> §7- Change API mode");
        send(sender, "§b/levelhead api <url> §7- Set custom API URL");
        send(sender, "§b/levelhead clearcache §7- Clear cache");
        send(sender, "§b/levelhead reload §7- Reload configuration");
        send(sender, "§b/levelhead interval §7- Set requestInterval");
        send(sender, "§b/levelhead game §7- Set Gamemode");
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

        if (args[1].equalsIgnoreCase("hypixel") || args[1].equalsIgnoreCase("network")) {
            Main.CONFIG.setLevelType(ModConfig.LevelType.HYPIXEL);
        } else if (args[1].equalsIgnoreCase("bedwars") || args[1].equalsIgnoreCase("bw")) {
            Main.CONFIG.setLevelType(ModConfig.LevelType.BEDWARS);
        } else if (args[1].equalsIgnoreCase("skywars") || args[1].equalsIgnoreCase("sw")) {
            Main.CONFIG.setLevelType(ModConfig.LevelType.SKYWARS);
        } else if (args[1].equalsIgnoreCase("uhc")) {
            Main.CONFIG.setLevelType(ModConfig.LevelType.UHC);
        } else {
            send(sender, "Mode must be hypixel, bedwars, skywars, uhc");
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