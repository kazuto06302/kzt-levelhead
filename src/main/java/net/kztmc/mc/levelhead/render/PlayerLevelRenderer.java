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
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import static net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR;

public class PlayerLevelRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Post event) {
        EntityPlayer player = event.entityPlayer;

        float distance = player.getDistanceToEntity(mc.thePlayer);
        if (distance > 64.0F) {return;}

        PlayerStatsCache.Priority priority;

        if (distance <= 16.0F) {
            priority = PlayerStatsCache.Priority.HIGH;

        } else if (distance <= 48.0F) {
            priority = PlayerStatsCache.Priority.NORMAL;

        } else {
            priority = PlayerStatsCache.Priority.LOW;
        }

        PlayerStats stats = Main.CACHE.get(player.getUniqueID(), priority);

        if (stats == null) return;

        render(player, event.x, event.y, event.z, stats);
    }

    private void render(EntityPlayer player, double x, double y, double z, PlayerStats stats) {
        String text;
        ModConfig.LevelType levelType = Main.CONFIG.getLevelType();

        if (levelType == ModConfig.LevelType.HYPIXEL) {
            text = "§b" + stats.getHypixelLevel() + "§f";
        } else {
            text = "§a" + stats.getBedwarsLevel() + "✫";
        }

        FontRenderer font = mc.fontRendererObj;
        float scale = 0.016666668F;

        GL11.glPushMatrix();

        GL11.glTranslatef((float) x, (float) y + player.height + 0.75F, (float) z);
        GL11.glRotatef(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-scale, -scale, scale);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int width = font.getStringWidth(text);
        float drawX = -width / 2.0F;

        drawBackground(drawX - 2, -2, width + 4, font.FONT_HEIGHT + 4);
        font.drawStringWithShadow(text, drawX, 0, 0xFFFFFF);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);

        GL11.glPopMatrix();
    }

    private void drawBackground(float x, float y, float width, float height) {

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        GlStateManager.disableTexture2D();

        renderer.begin(GL11.GL_QUADS,POSITION_COLOR);

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