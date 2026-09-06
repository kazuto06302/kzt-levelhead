package net.kztmc.mc.levelhead.render;

import net.kztmc.mc.levelhead.Main;
import net.kztmc.mc.levelhead.cache.PlayerStats;
import net.kztmc.mc.levelhead.cache.PlayerStatsCache;
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
         * 自分自身には表示しない。
         */
        if (player == mc.thePlayer) {
            return;
        }

        /*
         * 距離を計算。
         */
        float distance =
                player.getDistanceToEntity(
                        mc.thePlayer
                );

        /*
         * 64ブロック以上は対象外。
         *
         * APIリクエストも発生させない。
         */
        if (distance > 64.0F) {
            return;
        }

        /*
         * 距離によってAPIリクエストの優先度を決定。
         *
         * 0～16   : HIGH
         * 16～48  : NORMAL
         * 48～64  : LOW
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
         *
         * キャッシュがなければ null が返り、
         * 裏でAPIリクエストがキューに追加される。
         *
         * キャッシュが期限切れの場合も、
         * 古いデータを表示しながら
         * 裏で更新される。
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

        StringBuilder text =
                new StringBuilder();

        /*
         * Hypixel Network Level
         */
        if (Main.CONFIG.isShowHypixelLevel()) {

            text.append("§b")
                    .append(stats.getHypixelLevel())
                    .append("§f");

            if (Main.CONFIG.isShowBedwarsLevel()) {
                text.append(" §7| ");
            }
        }

        /*
         * BedWars Level
         */
        if (Main.CONFIG.isShowBedwarsLevel()) {

            text.append("§a")
                    .append(stats.getBedwarsLevel())
                    .append("✫");
        }

        /*
         * 何も表示しない設定なら終了。
         */
        if (text.length() == 0) {
            return;
        }

        FontRenderer font =
                mc.fontRendererObj;

        float scale =
                0.016666668F;

        GL11.glPushMatrix();

        GL11.glTranslatef(
                (float) x,
                (float) y
                        + player.height
                        + 0.5F,
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
         * ライティングを無効化。
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
         * 半透明背景のためBlendを有効化。
         */
        GL11.glEnable(
                GL11.GL_BLEND
        );

        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        /*
         * テキスト幅を取得。
         */
        int width =
                font.getStringWidth(
                        text.toString()
                );

        float drawX =
                -width / 2.0F;

        /*
         * 黒い半透明背景。
         */
        drawBackground(
                drawX - 2,
                -2,
                width + 4,
                font.FONT_HEIGHT + 4
        );

        /*
         * テキスト描画。
         */
        font.drawStringWithShadow(
                text.toString(),
                drawX,
                0,
                0xFFFFFF
        );

        /*
         * GL状態を元に戻す。
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