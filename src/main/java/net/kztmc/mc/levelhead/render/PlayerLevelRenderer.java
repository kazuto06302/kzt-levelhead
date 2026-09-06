package net.kztmc.mc.levelhead.render;

import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.cache.PlayerStats;
import net.kztmc.mc.levelhead.cache.PlayerStatsCache;
import net.kztmc.mc.levelhead.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public class PlayerLevelRenderer {

    private final Minecraft mc =
            Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderPlayer(
            RenderPlayerEvent.Post event
    ) {

        EntityPlayer player =
                event.entityPlayer;

        /*
         * 距離。
         */
        float distance =
                player.getDistanceToEntity(
                        mc.thePlayer
                );

        /*
         * 64ブロック以上は対象外。
         */
        if (distance > 64.0F) {
            return;
        }

        /*
         * 距離によって優先度を決定。
         */
        PlayerStatsCache.Priority priority;

        if (distance <= 16.0F) {

            priority =
                    PlayerStatsCache.Priority.HIGH;

        } else if (distance <= 48.0F) {

            priority =
                    PlayerStatsCache.Priority.NORMAL;

        } else {

            priority =
                    PlayerStatsCache.Priority.LOW;
        }

        /*
         * キャッシュから取得。
         */
        PlayerStats stats =
                Main.CACHE.get(
                        player.getUniqueID(),
                        priority
                );

        if (stats == null) {
            return;
        }

        render(
                player,
                event.x,
                event.y,
                event.z,
                stats
        );
    }

    private void render(
            EntityPlayer player,
            double x,
            double y,
            double z,
            PlayerStats stats
    ) {

        /*
         * 表示するレベル。
         */
        String text;

        ModConfig.LevelType levelType =
                Main.CONFIG.getLevelType();

        if (levelType
                == ModConfig.LevelType.HYPIXEL) {

            /*
             * Network Level
             */
            text =
                    "§b"
                            + stats.getHypixelLevel()
                            + "§f";

        } else {

            /*
             * BedWars Level
             */
            text =
                    "§a"
                            + stats.getBedwarsLevel()
                            + "✫";
        }

        /*
         * FontRenderer
         */
        FontRenderer font =
                mc.fontRendererObj;

        /*
         * Minecraftの名前表示と同じサイズ。
         */
        float scale =
                0.016666668F;

        GL11.glPushMatrix();

        /*
         * 名前の上に表示。
         *
         * Vanillaの名前表示より
         * 少し上にずらす。
         */
        GL11.glTranslatef(
                (float) x,
                (float) y
                        + player.height
                        + 0.75F,
                (float) z
        );

        /*
         * プレイヤーの方向を向く。
         */
        GL11.glRotatef(
                -mc.getRenderManager()
                        .playerViewY,
                0.0F,
                1.0F,
                0.0F
        );

        GL11.glRotatef(
                mc.getRenderManager()
                        .playerViewX,
                1.0F,
                0.0F,
                0.0F
        );

        /*
         * テキストサイズ。
         */
        GL11.glScalef(
                -scale,
                -scale,
                scale
        );

        /*
         * Lighting OFF
         */
        GL11.glDisable(
                GL11.GL_LIGHTING
        );

        /*
         * 壁越しでも表示。
         */
        GL11.glDisable(
                GL11.GL_DEPTH_TEST
        );

        /*
         * 背景の透明処理。
         */
        GL11.glEnable(
                GL11.GL_BLEND
        );

        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        /*
         * テキスト幅。
         */
        int width =
                font.getStringWidth(
                        text
                );

        float drawX =
                -width / 2.0F;

        /*
         * 背景。
         */
        drawBackground(
                drawX - 2,
                -2,
                width + 4,
                font.FONT_HEIGHT + 4
        );

        /*
         * テキスト。
         */
        font.drawStringWithShadow(
                text,
                drawX,
                0,
                0xFFFFFF
        );

        /*
         * GL状態を戻す。
         */
        GL11.glDisable(
                GL11.GL_BLEND
        );

        GL11.glEnable(
                GL11.GL_DEPTH_TEST
        );

        GL11.glEnable(
                GL11.GL_LIGHTING
        );

        GL11.glPopMatrix();
    }

    private void drawBackground(
            float x,
            float y,
            float width,
            float height
    ) {

        net.minecraft.client.renderer.Tessellator
                tessellator =
                net.minecraft.client.renderer.Tessellator
                        .getInstance();

        net.minecraft.client.renderer.WorldRenderer
                renderer =
                tessellator.getWorldRenderer();

        net.minecraft.client.renderer.GlStateManager
                .disableTexture2D();

        renderer.begin(
                GL11.GL_QUADS,
                net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR
        );

        renderer.pos(
                        x,
                        y,
                        0
                )
                .color(
                        0,
                        0,
                        0,
                        120
                )
                .endVertex();

        renderer.pos(
                        x + width,
                        y,
                        0
                )
                .color(
                        0,
                        0,
                        0,
                        120
                )
                .endVertex();

        renderer.pos(
                        x + width,
                        y + height,
                        0
                )
                .color(
                        0,
                        0,
                        0,
                        120
                )
                .endVertex();

        renderer.pos(
                        x,
                        y + height,
                        0
                )
                .color(
                        0,
                        0,
                        0,
                        120
                )
                .endVertex();

        tessellator.draw();

        net.minecraft.client.renderer.GlStateManager
                .enableTexture2D();
    }
}