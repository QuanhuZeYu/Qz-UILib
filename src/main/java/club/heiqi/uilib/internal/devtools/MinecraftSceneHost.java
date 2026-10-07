package club.heiqi.uilib.internal.devtools;

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

/** scene与生产client字体owner共享度量、异步光栅及图集；布局/绘制/输入均原生framebufferpx。 */
final class MinecraftSceneHost implements SceneHost,SceneTextMeasurer,AutoCloseable {
    private final ClientFontServices fonts=MinecraftFontServices.shared();
    private final FramebufferSceneRenderer renderer=new FramebufferSceneRenderer(fonts);
    @Override public int width(){return Display.getWidth();}
    @Override public int height(){return Display.getHeight();}
    @Override public long metricsRevision(){return fonts.fonts().generation();}
    @Override public TextLayout layout(String text,TextStyle style,int availableWidth){return fonts.layout(text,style,availableWidth);}
    @Override public TextLayout layout(String text,TextStyle style,int availableWidth,TextPolicy policy){return fonts.layout(text,style,availableWidth,policy);}
    @Override public void replay(PaintPlan plan){renderer.replay(plan,width(),height());}
    @Override public void close(){renderer.close();}
}
