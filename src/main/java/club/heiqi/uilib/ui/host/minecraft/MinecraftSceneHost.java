package club.heiqi.uilib.ui.host.minecraft;

import org.lwjgl.opengl.Display;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferSceneRenderer;
import club.heiqi.uilib.host.client.font.MinecraftFontServices;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 正式 MC 宿主与生产 client 字体服务共享度量、异步光栅及图集；坐标统一为 framebuffer px。
 * 本页只拥有 renderer；关闭不释放共享 ClientFontServices。 */
public final class MinecraftSceneHost implements SceneHost, SceneTextMeasurer, AutoCloseable {
    private final ClientFontServices fonts = MinecraftFontServices.shared();
    private final club.heiqi.uilib.host.client.image.ClientImageServices images =
            club.heiqi.uilib.host.client.image.MinecraftImageServices.shared();
    private final FramebufferSceneRenderer renderer = new FramebufferSceneRenderer(fonts, images);

    public club.heiqi.uilib.ui.scene.image.ImageService images() {
        return images.images();
    }

    @Override
    public void prepareResources() {
        images.prepareResources();
    }

    @Override
    public int width() {
        return Display.getWidth();
    }

    @Override
    public int height() {
        return Display.getHeight();
    }

    @Override
    public long metricsRevision() {
        return fonts.fonts().generation();
    }

    @Override
    public club.heiqi.uilib.ui.scene.text.math.MathFont mathFont(TextStyle style) {
        return fonts.mathFont(style);
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int availableWidth) {
        return fonts.layout(text, style, availableWidth);
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int availableWidth, TextPolicy policy) {
        return fonts.layout(text, style, availableWidth, policy);
    }

    @Override
    public TextLayout layout(
            club.heiqi.uilib.ui.scene.text.AttributedText text,
            float availableWidth,
            TextPolicy policy) {
        return fonts.layout(text, availableWidth, policy);
    }

    @Override
    public void replay(PaintPlan plan) {
        renderer.replay(plan, width(), height());
    }

    @Override
    public void close() {
        renderer.close();
    }
}
