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
     *
     * <p>混合函数与原版世界文字路径 {@code Render.func_147906_a}（:359）保持一致：
     * {@code glBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO)}。FontShaderProgram 片元着色器
     * 输出的是非预乘（straight）alpha（{@code vec4(Color.rgb, tex.a * Color.a)} 与
     * {@code vec4(tex.rgb, tex.a * Color.a)}），颜色通道不依赖 dst-alpha 累积；dst-alpha 因子改为
     * {@code GL_ZERO} 后，帧缓冲 alpha 写入也与原版一致，不再遗留非原版的
     * {@code ONE_MINUS_SRC_ALPHA} 因子。RGB 因子未变，AA/阴影外观不受影响；帧缓冲 alpha 通道内容的差异
     * 属真机待验证项。</p>
     */
    public static void prepareTextRenderState() {
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
