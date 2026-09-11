package net.kztmc.mc.levelhead.render;

import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.cache.PlayerStats;
import net.kztmc.mc.levelhead.cache.PlayerStatsCache;
import net.kztmc.mc.levelhead.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import static net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR;

public class PlayerLevelRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {

        if (mc.theWorld == null || mc.thePlayer == null) return;

        for (EntityPlayer player : mc.theWorld.playerEntities) {

            if (player == null) continue;
            if (player.isInvisible()) continue;

            float distance = player.getDistanceToEntity(mc.thePlayer);

            if (distance > 64.0F) continue;

            PlayerStats stats;

            if (Main.isQueueAssignmentAllowedCached()) {
                //ingame

                PlayerStatsCache.Priority priority;

                if (distance <= 16.0F) {
                    priority = PlayerStatsCache.Priority.HIGH;

                } else if (distance <= 48.0F) {
                    priority = PlayerStatsCache.Priority.NORMAL;

                } else {
                    priority = PlayerStatsCache.Priority.LOW;
                }

                stats = Main.CACHE.get(
                        player.getUniqueID(),
                        priority
                );

            } else {
                //outgame
                stats = Main.CACHE.getCached(
                        player.getUniqueID()
                );
            }

            if (stats == null) continue;

            double x = player.lastTickPosX
                    + (player.posX - player.lastTickPosX) * event.partialTicks
                    - mc.getRenderManager().viewerPosX;

            double y = player.lastTickPosY
                    + (player.posY - player.lastTickPosY) * event.partialTicks
                    - mc.getRenderManager().viewerPosY;

            double z = player.lastTickPosZ
                    + (player.posZ - player.lastTickPosZ) * event.partialTicks
                    - mc.getRenderManager().viewerPosZ;

            render(player, x, y, z, stats);
        }
    }

    private void render(EntityPlayer player, double x, double y, double z, PlayerStats stats) {
        String text = "";

        ModConfig.LevelType levelType = Main.CONFIG.getLevelType();

        if (levelType == ModConfig.LevelType.HYPIXEL) {
            text = "§f" + "NetworkLevel: " + "§b" + stats.getHypixelLevel() + "§f";
        } else if (levelType == ModConfig.LevelType.BEDWARS){
            text = "§f" + "BWLevel: " + "§a" + stats.getBedwarsLevel() + "✫";
        } else if (levelType == ModConfig.LevelType.SKYWARS) {
            text = "§f" + "SWLevel: " + "§b" + stats.getSkywarsLevel() + "✫";
        } else if (levelType == ModConfig.LevelType.UHC) {
            text = "§f" + "UHCLevel: " + "§c" + stats.getUhcLevel() + "✫";
        }

        FontRenderer font = mc.fontRendererObj;

        float scale = 0.025F;

        boolean hasBelowName = hasBelowNameObjective(player);
        float yOffset;

        if (hasBelowName) {
            yOffset = player.height + 1.0F;

        } else {
            yOffset = player.height + 0.75F;
        }

        if (player.isSneaking()) {
            yOffset -= 0.3F;
        }

        GL11.glPushMatrix();

        GL11.glTranslatef((float) x, (float) y + yOffset, (float) z);

        GL11.glRotatef(
                -mc.getRenderManager().playerViewY,
                0.0F,
                1.0F,
                0.0F
        );

        GL11.glRotatef(
                mc.getRenderManager().playerViewX,
                1.0F,
                0.0F,
                0.0F
        );

        GL11.glScalef(-scale, -scale, scale);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        int width = font.getStringWidth(text);

        float drawX = -width / 2.0F;

        drawBackground(
                drawX - 2,
                -2,
                width + 4,
                font.FONT_HEIGHT + 4
        );

        font.drawStringWithShadow(
                text,
                drawX,
                0,
                0xFFFFFF
        );

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);

        GL11.glPopMatrix();
    }

    private boolean hasBelowNameObjective(EntityPlayer player) {
        if (mc.theWorld == null) return false;

        ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(2);

        if (objective == null) return false;

        String playerName = player.getName();
        if (playerName == null) return false;

        return mc.theWorld.getScoreboard().getValueFromObjective(playerName, objective) != null;
    }

    private void drawBackground(float x, float y, float width, float height) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();

        GlStateManager.disableTexture2D();

        renderer.begin(
                GL11.GL_QUADS,
                POSITION_COLOR
        );

        renderer.pos(x, y, 0)
                .color(0, 0, 0, 120)
                .endVertex();

        renderer.pos(x + width, y, 0)
                .color(0, 0, 0, 120)
                .endVertex();

        renderer.pos(x + width, y + height, 0)
                .color(0, 0, 0, 120)
                .endVertex();

        renderer.pos(x, y + height, 0)
                .color(0, 0, 0, 120)
                .endVertex();

        tessellator.draw();

        GlStateManager.enableTexture2D();
    }
}