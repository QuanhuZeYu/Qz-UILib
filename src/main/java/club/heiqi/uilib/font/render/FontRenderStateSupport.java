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
     * <p>混合函数：RGB 与原版世界文字路径 {@code Render.func_147906_a}（:359）一致
     * （{@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}，straight over）；alpha 通道使用 over 累积
     * （{@code ONE, ONE_MINUS_SRC_ALPHA}）。片元着色器输出非预乘（straight）alpha
     * （{@code vec4(Color.rgb, tex.a * Color.a)}），RGB 因子不依赖 dst-alpha。</p>
     *
     * <p>alpha 通道必须 over 而非原版 {@code ONE, ZERO} 覆盖：离屏合成层（group opacity/
     * transform FBO）内字形画在不透明底板之上时，覆盖语义会把字形边缘像素的 dst alpha
     * 抹成字形自身的边缘 alpha（0~1 渐变），贴回主帧缓冲时 {@code ONE_MINUS_SRC_ALPHA}
     * 因子把底下的游戏画面从字形边缘漏进来，动画期（opacity&lt;1 恒走离屏层）文字边缘
     * 出现透底脏边——直画主帧缓冲时帧缓冲 alpha 无人读取故无感，只有离屏合成暴露。
     * over 累积下字形画在底板（α=1）上 dst alpha 保持 1，贴回不漏底；画在透明区
     * 时等价覆盖行为。2026-08-31 真机取证：动画期间仅字形边缘与底下混合异常、
     * 纯色底板正常，与覆盖语义缺陷严格吻合。</p>
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
