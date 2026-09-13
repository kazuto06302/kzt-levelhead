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
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Collection;

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

        // Do not gate API requests behind Hypixel's scoreboard text.
        // The old implementation required a very specific scoreboard format
        // (date + "m" and "www.hypixel.net"), so when Hypixel changed the
        // scoreboard the cache was never populated and nothing was rendered.
        queueAssignmentAllowed = true;

        if (mc.getNetHandler() == null) return;

        Collection<NetworkPlayerInfo> players = mc.getNetHandler().getPlayerInfoMap();

        for (NetworkPlayerInfo info : players) {
            if (info == null || info.getGameProfile() == null) continue;

            String displayName = info.getPlayerTeam() == null
                    ? info.getGameProfile().getName()
                    : ScorePlayerTeam.formatPlayerName(
                    info.getPlayerTeam(),
                    info.getGameProfile().getName()
            );

            // Do not send obfuscated tab-list placeholders to the API.
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
}
