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
    // 自分自身の表示専用: footer(www.hypixel.net)のみの判定。
    // date/M行の判定でズレる(ロビー切替直後など)ことがあるため、
    // 自分自身はこちらだけで許可する。
    private static volatile boolean onHypixelNetwork = false;

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

    // 自分自身の表示可否用(footerのみで判定、date/M行は見ない)
    public static boolean isOnHypixelNetworkCached() {
        return onHypixelNetwork;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (mc.theWorld == null || mc.thePlayer == null) {
            queueAssignmentAllowed = false;
            onHypixelNetwork = false;
            return;
        }

        tabCheckTimer++;

        if (tabCheckTimer < 20) return;

        tabCheckTimer = 0;
        queueAssignmentAllowed = false;

        if (mc.getNetHandler() == null) {
            onHypixelNetwork = false;
            return;
        }

        // Hypixel判定はTabList人数に関係なく毎回行う
        // (ロビー等24人以上いる場面で判定自体がスキップされ、
        //  QUEUE ALLOWEDが出ない/自分のスタッツが表示されない原因になっていた)
        queueAssignmentAllowed = isQueueAssignmentAllowed();

        // 自分自身はfooter(www.hypixel.net)判定のみで許可する。
        // queueAssignmentAllowedはfooterに加えてdate/M行も要求するが、
        // 自分自身の表示はそこまで厳密にせず、TabList人数にも関わらず
        // 常に取得を試みる(ロビーでも表示させるため)。
        if (onHypixelNetwork && mc.thePlayer.getUniqueID() != null) {
            CACHE.get(mc.thePlayer.getUniqueID(), PlayerStatsCache.Priority.HIGH);
        }

        if (!queueAssignmentAllowed) return;

        Collection<NetworkPlayerInfo> players = mc.getNetHandler().getPlayerInfoMap();

        int playerCount = players.size();
        // 大人数(ロビー等)ではTabList全員分の先読みのみAPI保護のため抑制する
        if (playerCount >= 24) return;

        for (NetworkPlayerInfo info : players) {
            if (info == null || info.getGameProfile() == null) continue;

            String displayName = info.getPlayerTeam() == null
                    ? info.getGameProfile().getName()
                    : ScorePlayerTeam.formatPlayerName(
                    info.getPlayerTeam(),
                    info.getGameProfile().getName()
            );

            // 表示名が難読化(§k)されている間はAPIリクエストを出さない
            // (ニック非表示中/読込中などで、実際の名前・意味のある問い合わせにならないため)
            if (displayName.contains("§k")) {
                continue;
            }

            //if (Main.dev) LevelHeadCommand.send(mc.thePlayer, "QUEUE CHECK: " + info.getGameProfile().getName());

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
        onHypixelNetwork = false;
        tabCheckTimer = 0;
    }

    private boolean isQueueAssignmentAllowed() {
        if (Main.dev) LevelHeadCommand.send(mc.thePlayer, "QUEUE CHECKING");

        onHypixelNetwork = false;

        if (mc.theWorld == null) return false;

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) return false;

        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) return false;

        List<Score> scores = new ArrayList<Score>(scoreboard.getSortedScores(objective));
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

        if (lines.isEmpty()) return false;

        // footer(www.hypixel.net)判定はdate/M行の判定より先に、
        // かつ独立して行う。自分自身の表示可否(onHypixelNetwork)は
        // こちらのみで決まる。
        String lastLine = lines.get(0);
        String cleanLastLine = removeFormattingCodes(lastLine).trim();

        if (!isHypixelFooterLine(cleanLastLine)) {
            if (Main.dev) {
                LevelHeadCommand.send(mc.thePlayer,
                        "QUEUE DENY (footer mismatch): \"" + cleanLastLine + "\"");
            }
            return false;
        }

        onHypixelNetwork = true;

        String firstLine = lines.get(lines.size() - 1);
        if (!isDateAndMLine(firstLine)) {
            if (Main.dev) {
                LevelHeadCommand.send(mc.thePlayer,
                        "QUEUE DENY (date line mismatch): \"" + removeFormattingCodes(firstLine) + "\"");
            }
            return false;
        }

        if (Main.dev) LevelHeadCommand.send(mc.thePlayer, "QUEUE ALLOWED");

        return true;
    }

    private boolean isDateAndMLine(String text) {
        if (text == null) return false;

        String visible = removeFormattingCodes(text).trim();
        // Hypixelは日付を"9/16/26"のようにゼロ埋めせず表示することがあるため
        // 桁数を1〜2桁どちらでも許容する
        return visible.matches("^\\d{1,2}/\\d{1,2}/\\d{2}\\s+m.*");
    }

    /*
     * サイドバー最終行がHypixelのURLかどうかを判定する。
     * Hypixelはイベント装飾として絵文字(🎂等)をURL文字列の
     * 途中に挿入してくることがある(例: "www.hypixel.ne🎂t")ため、
     * 英数字とドット以外の文字(絵文字・記号・空白など)は
     * すべて除去してから比較する。
     */
    private boolean isHypixelFooterLine(String cleanLastLine) {
        if (cleanLastLine == null || cleanLastLine.isEmpty()) return false;

        String strippedDecorations = cleanLastLine.replaceAll("[^a-zA-Z0-9.]", "");
        String normalized = strippedDecorations.toLowerCase();

        return normalized.contains("www.hypixel.net")
                || normalized.contains("www.hypixel.com")
                || normalized.contains("hypixel.net")
                || normalized.contains("hypixel.com");
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
}