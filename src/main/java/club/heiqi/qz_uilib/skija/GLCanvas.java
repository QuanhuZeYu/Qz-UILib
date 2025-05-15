package club.heiqi.qz_uilib.skija;

import club.heiqi.qz_uilib.skija.state.AngelicaController;
import club.heiqi.qz_uilib.skija.state.GLPixelStore;
import club.heiqi.qz_uilib.skija.state.SkiaStore;
import io.github.humbleui.skija.*;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.*;

public class GLCanvas {
    public static Logger LOG = LogManager.getLogger();
    public static List<GLCanvas> GLOBALS = new ArrayList<>();
    public List<Consumer<Canvas>> consumers = new ArrayList<>();
    public DirectContext context;
    public Surface surface;
    public BackendRenderTarget renderTarget;
    public FrameBuffer frameBuffer;

    public GLPixelStore pixelStore = new GLPixelStore();
    public SkiaStore skiaStore = new SkiaStore();

    public GLCanvas() {
        initContext();
    }

    public void initContext() {
        int width = Display.getWidth(); int height = Display.getHeight();
        frameBuffer = new FrameBuffer(width, height);
        if (context == null) context = DirectContext.makeGL();
        renderTarget = BackendRenderTarget.makeGL(width, height,
                0,8, frameBuffer.fboID, GL_RGBA8);
        surface = Surface.wrapBackendRenderTarget(context, renderTarget,
                SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888,ColorSpace.getSRGB());
        GLOBALS.add(this);
        //LOG.info("画布已创建, 当前画布数量: {}, _ptr:{} {} {}", GLOBALS.size(), context._ptr, renderTarget._ptr, surface._ptr);
    }

    /**
     * skija进行绘制前的状态准备备份 并初始化skija的opengl状态
     */
    public void preFlush() {
        frameBuffer.bind(Display.getWidth(), Display.getHeight());
        AngelicaController.forceOffGLCache();
        skiaStore.backup();
        pixelStore.backup();
        context.resetGLAll();
    }

    /**
     * skija状态立即刷新 更新画布内容到opengl
     */
    public void flush() {
        context.flush();
        pixelStore.restore();
        skiaStore.restore();
        frameBuffer.unbind();
    }

    /**
     * 插队方法，每帧只有一次render方法的调用机会，如果想要在一张画布上额外添加内容，需要在render前使用该方法来附加
     * 且该方法会附加到render前进行绘制
     * @param consumer
     */
    public void addRender(Consumer<Canvas> consumer) {
        consumers.add(consumer);
    }

    public void render(Consumer<Canvas> consumer) {
        preFlush();
        Canvas canvas = surface.getCanvas();
        canvas.clear(0x00000000);
        for (Consumer<Canvas> c : consumers) {
            c.accept(canvas);
        }
        consumers.clear();
        consumer.accept(canvas);
        flush();
        try {
            SkiaStore.glBindSampler.invoke(0,0);
        } catch (Throwable ignored) {

        }
        // 将canvas绘制内容绘制到MC画布上
        int mcFBO = Minecraft.getMinecraft().getFramebuffer().framebufferObject;
        frameBuffer.renderToFBO(mcFBO);
    }

    public void dispose() {
        //context.close();
        surface.close();
        renderTarget.close();
        frameBuffer.dispose();
        GLOBALS.remove(this);
        /*LOG.info("画布已清理, 画布数量:{}", GLOBALS.size());*/
    }

    public void resize() {
        dispose();
        initContext();
    }
}
