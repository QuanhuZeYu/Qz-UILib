package club.heiqi.uilib.font.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * 字体渲染共享状态准备工具。
 */
public final class FontRenderStateSupport {

    private FontRenderStateSupport() {}

    /**
     * 准备稳定的二维文本绘制状态。
     */
    public static void prepareTextRenderState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
