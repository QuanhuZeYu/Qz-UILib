package club.heiqi.uilib.font.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * 字体渲染共享状态准备工具。
 */
public final class FontRenderStateSupport {

    private FontRenderStateSupport() {}

    /**
     * 准备字体自身拥有的二维文本绘制状态。
     *
     * <p>字体层不修改深度测试、深度写入掩码或深度比较函数。世界文字继承调用阶段已有的深度状态；
     * UILib screen/HUD 的二维深度状态由 UI host 阶段边界建立。</p>
     */
    public static void prepareTextRenderState() {
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
