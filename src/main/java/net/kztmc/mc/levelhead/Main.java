package net.kztmc.mc.levelhead;

import net.kztmc.mc.levelhead.api.ApiClient;
import net.kztmc.mc.levelhead.api.CustomApiClient;
import net.kztmc.mc.levelhead.api.HypixelApiClient;
import net.kztmc.mc.levelhead.api.LocalApiServer;
import net.kztmc.mc.levelhead.cache.PlayerStatsCache;
import net.kztmc.mc.levelhead.command.LevelHeadCommand;
import net.kztmc.mc.levelhead.config.ModConfig;
import net.kztmc.mc.levelhead.render.PlayerLevelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mod(
        modid = Main.MOD_ID,
        name = Main.MOD_NAME,
        version = Main.VERSION,
        clientSideOnly = true
)
public class Main {

    public static final String MOD_ID = "levelhead";
    public static final String MOD_NAME = "kzt-LevelHead";
    public static final String VERSION = "1.0.0";

    public static ModConfig CONFIG;
    public static PlayerStatsCache CACHE;

    private static ApiClient apiClient;
    private static LocalApiServer localApiServer;

    private final Minecraft mc = Minecraft.getMinecraft();

    private int tabCheckTimer = 0;

    private static volatile boolean queueAssignmentAllowed = false;

    public static boolean dev = false;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        CONFIG = new ModConfig(event.getModConfigurationDirectory());
        CONFIG.load();

        CACHE = new PlayerStatsCache(CONFIG);

        rebuildApiClient();

        localApiServer = new LocalApiServer();
        localApiServer.start();

        Runtime.getRuntime().addShutdownHook(
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                if (localApiServer != null) {
                                    localApiServer.stop();
                                }

                                if (CACHE != null) {
                                    CACHE.shutdown();
                                }
                            }
                        },
                        "LevelHead-Shutdown"
                )
        );
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new PlayerLevelRenderer());
        MinecraftForge.EVENT_BUS.register(new LevelHeadCommand());
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static void rebuildApiClient() {
        if (CONFIG == null) return;

        if (CONFIG.getApiMode() == ModConfig.ApiMode.HYPIXEL) {
            apiClient = new HypixelApiClient(CONFIG.getHypixelApiKey());
        } else {
            apiClient = new CustomApiClient(CONFIG);
        }
    }

    public static ApiClient getApiClient() {
        return apiClient;
    }

    public static boolean isQueueAssignmentAllowedCached() {
        return queueAssignmentAllowed;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (mc.theWorld == null || mc.thePlayer == null) {
            queueAssignmentAllowed = false;
            return;
        }

        tabCheckTimer++;

        if (tabCheckTimer < 20) return;

        tabCheckTimer = 0;
        queueAssignmentAllowed = false;

        if (mc.getNetHandler() == null) return;

        Collection<NetworkPlayerInfo> players =
                mc.getNetHandler().getPlayerInfoMap();

        int playerCount = players.size();

        if (playerCount >= 24) return;

        queueAssignmentAllowed = isQueueAssignmentAllowed();

        if (!queueAssignmentAllowed) return;

        for (NetworkPlayerInfo info : players) {
            if (info == null || info.getGameProfile() == null) continue;

            String displayName = info.getPlayerTeam() == null
                    ? info.getGameProfile().getName()
                    : ScorePlayerTeam.formatPlayerName(
                    info.getPlayerTeam(),
                    info.getGameProfile().getName()
            );

            if (displayName.contains("§k")) {
                continue;
            }

            if (Main.dev) {
                LevelHeadCommand.send(
                        mc.thePlayer,
                        "QUEUE CHECK: " + info.getGameProfile().getName()
                );
            }

            CACHE.get(
                    info.getGameProfile().getId(),
                    PlayerStatsCache.Priority.HIGH
            );
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null) return;
        CACHE.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (CACHE != null) {
            CACHE.resetQueue();
        }

        queueAssignmentAllowed = false;
        tabCheckTimer = 0;
    }

    private boolean isQueueAssignmentAllowed() {
        if (Main.dev) {
            LevelHeadCommand.send(
                    mc.thePlayer,
                    "QUEUE CHECKING"
            );
        }

        if (mc.theWorld == null) return false;

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) return false;

        ScoreObjective objective =
                scoreboard.getObjectiveInDisplaySlot(1);

        if (objective == null) return false;

        List<Score> scores = new ArrayList<Score>(
                scoreboard.getSortedScores(objective)
        );

        List<String> lines = new ArrayList<String>();

        for (Score score : scores) {
            if (score.getPlayerName().startsWith("#")) {
                continue;
            }

            String line = ScorePlayerTeam.formatPlayerName(
                    scoreboard.getPlayersTeam(score.getPlayerName()),
                    score.getPlayerName()
            );

            lines.add(line);
        }

        if (lines.isEmpty()) {
            return false;
        }

        if (Main.dev) {
            for (int i = 0; i < lines.size(); i++) {
                LevelHeadCommand.send(
                        mc.thePlayer,
                        i + ": " + lines.get(i)
                );
            }

            String topLine = lines.get(lines.size() - 1);

            LevelHeadCommand.send(
                    mc.thePlayer,
                    "TOP: " + topLine
            );

            LevelHeadCommand.send(
                    mc.thePlayer,
                    "DATE/M CHECK: " + isDateAndMLine(topLine)
            );
        }

        String firstLine = lines.get(lines.size() - 1);

        if (!isDateAndMLine(firstLine)) {
            return false;
        }

        String lastLine = lines.get(0);

        String cleanLastLine = removeFormattingCodes(lastLine);

        if (!"www.hypixel.net".equals(cleanLastLine)) {
            return false;
        }

        if (Main.dev) {
            LevelHeadCommand.send(
                    mc.thePlayer,
                    "QUEUE ALLOWED"
            );
        }

        return true;
    }

    private boolean isDateAndMLine(String text) {
        if (text == null) {
            return false;
        }

        String visible = removeFormattingCodes(text);

        return visible.matches(
                "^\\d{2}/\\d{2}/\\d{2}\\s+m.*"
        );
    }

    private String removeFormattingCodes(String text) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }

            result.append(c);
        }

        return result.toString();
    }

    private char getVisibleChar(String text, int index) {
        int visibleIndex = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }

            if (visibleIndex == index) {
                return c;
            }

            visibleIndex++;
        }

        return '\0';
    }
}