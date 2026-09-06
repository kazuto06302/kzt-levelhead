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
    public String getCommandUsage(
            ICommandSender sender
    ) {
        return "/levelhead <key|mode|api|clearcache|reload>";
    }

    @Override
    public List<String> addTabCompletionOptions(
            ICommandSender sender,
            String[] args,
            BlockPos pos
    ) {

        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                    args,
                    "key",
                    "mode",
                    "api",
                    "clearcache",
                    "reload"
            );
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("mode")) {

            return getListOfStringsMatchingLastWord(
                    args,
                    "hypixel",
                    "custom"
            );
        }

        return Collections.emptyList();
    }

    @Override
    public void processCommand(
            ICommandSender sender,
            String[] args
    ) {

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

            send(
                    sender,
                    "Cache cleared."
            );

            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {

            Main.CONFIG.load();

            Main.rebuildApiClient();

            Main.CACHE.clear();

            send(
                    sender,
                    "Configuration reloaded."
            );

            return;
        }

        help(sender);
    }

    private void setKey(
            ICommandSender sender,
            String[] args
    ) {

        if (args.length < 2) {
            send(
                    sender,
                    "Usage: /levelhead key <API-Key>"
            );

            return;
        }

        Main.CONFIG.setHypixelApiKey(
                args[1]
        );

        Main.CONFIG.setApiMode(
                ModConfig.ApiMode.HYPIXEL
        );

        Main.rebuildApiClient();

        send(
                sender,
                "Hypixel API key saved."
        );
    }

    private void setMode(
            ICommandSender sender,
            String[] args
    ) {

        if (args.length < 2) {
            send(
                    sender,
                    "Current mode: " +
                            Main.CONFIG
                                    .getApiMode()
                                    .name()
            );

            return;
        }

        if (args[1].equalsIgnoreCase("hypixel")) {

            Main.CONFIG.setApiMode(
                    ModConfig.ApiMode.HYPIXEL
            );

        } else if (
                args[1].equalsIgnoreCase("custom")
        ) {

            Main.CONFIG.setApiMode(
                    ModConfig.ApiMode.CUSTOM
            );

        } else {

            send(
                    sender,
                    "Mode must be hypixel or custom."
            );

            return;
        }

        Main.rebuildApiClient();

        Main.CACHE.clear();

        send(
                sender,
                "API mode changed to " +
                        Main.CONFIG
                                .getApiMode()
                                .name()
        );
    }

    private void setApi(
            ICommandSender sender,
            String[] args
    ) {

        if (args.length < 2) {
            send(
                    sender,
                    "Usage: /levelhead api <URL>"
            );

            return;
        }

        Main.CONFIG.setCustomApiUrl(
                args[1]
        );

        Main.CONFIG.setApiMode(
                ModConfig.ApiMode.CUSTOM
        );

        Main.rebuildApiClient();

        Main.CACHE.clear();

        send(
                sender,
                "Custom API URL saved."
        );
    }

    private void help(
            ICommandSender sender
    ) {

        send(
                sender,
                "§b/levelhead key <key> §7- Set Hypixel API key"
        );

        send(
                sender,
                "§b/levelhead mode <hypixel|custom> §7- Change API mode"
        );

        send(
                sender,
                "§b/levelhead api <url> §7- Set custom API URL"
        );

        send(
                sender,
                "§b/levelhead clearcache §7- Clear cache"
        );

        send(
                sender,
                "§b/levelhead reload §7- Reload configuration"
        );
    }

    private void send(
            ICommandSender sender,
            String message
    ) {

        sender.addChatMessage(
                new ChatComponentText(
                        EnumChatFormatting.AQUA +
                                "[LevelHead] " +
                                EnumChatFormatting.WHITE +
                                message
                )
        );
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}