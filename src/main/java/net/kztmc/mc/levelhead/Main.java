package net.kztmc.mc.levelhead;

import net.kztmc.mc.levelhead.api.ApiClient;
import net.kztmc.mc.levelhead.api.CustomApiClient;
import net.kztmc.mc.levelhead.api.HypixelApiClient;
import net.kztmc.mc.levelhead.cache.PlayerStatsCache;
import net.kztmc.mc.levelhead.command.LevelHeadCommand;
import net.kztmc.mc.levelhead.config.ModConfig;
import net.kztmc.mc.levelhead.render.PlayerLevelRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

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

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        CONFIG = new ModConfig(event.getModConfigurationDirectory());
        CONFIG.load();

        CACHE = new PlayerStatsCache(CONFIG);

        rebuildApiClient();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new PlayerLevelRenderer());

        MinecraftForge.EVENT_BUS.register(new LevelHeadCommand());

        MinecraftForge.EVENT_BUS.register(this);
    }

    public static void rebuildApiClient() {
        if (CONFIG == null) {
            return;
        }

        if (CONFIG.getApiMode() == ModConfig.ApiMode.HYPIXEL) {
            apiClient = new HypixelApiClient(CONFIG.getHypixelApiKey());
        } else {
            apiClient = new CustomApiClient(CONFIG);
        }
    }

    public static ApiClient getApiClient() {
        return apiClient;
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null) {
            return;
        }

        CACHE.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onWorldUnload(
            WorldEvent.Unload event
    ) {

        if (CACHE != null) {
            CACHE.resetQueue();
        }
    }
}