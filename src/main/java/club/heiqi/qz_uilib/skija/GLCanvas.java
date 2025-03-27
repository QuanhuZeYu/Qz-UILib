package club.heiqi.qz_uilib.skija;

import club.heiqi.qz_uilib.skija.state.GLPixelStore;
import club.heiqi.qz_uilib.skija.state.SkiaStore;
import io.github.humbleui.skija.*;
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
        context = DirectContext.makeGL();
        renderTarget = BackendRenderTarget.makeGL(width, height,
                0,8, frameBuffer.fboID, GL_RGBA8);
        surface = Surface.wrapBackendRenderTarget(context, renderTarget,
                SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888,ColorSpace.getSRGB());
        GLOBALS.add(this);
        LOG.info("画布已创建, 当前画布数量: {}, _ptr:{} {} {}", GLOBALS.size(), context._ptr, renderTarget._ptr, surface._ptr);
    }

    public void preFlush() {
        frameBuffer.bind(Display.getWidth(), Display.getHeight());
        skiaStore.backup();
        pixelStore.backup();
        // 提示安洁莉卡关闭过背面剔除
        glDisable(GL_CULL_FACE);
        // 提示安洁莉卡修改过颜色
        glColor4f(0.3485f,0.186f,0.7863f,0.915f);
        // 画布GL重置
        context.resetGLAll();
    }

    public void flush() {
        context.flush();
        pixelStore.restore();
        skiaStore.restore();
        frameBuffer.unbind();
    }

    public void render(Consumer<Canvas> consumer) {
        preFlush();
        consumer.accept(surface.getCanvas());
        flush();
        frameBuffer.renderToScreen();
        try {
            SkiaStore.glEnable.invoke(GL_CULL_FACE);
            SkiaStore.glBindSampler.invoke(0,0);
            SkiaStore.glEnable.invoke(GL_DEPTH_TEST);
            SkiaStore.glDepthMask.invoke(true);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void dispose() {
        context.close();
        surface.close();
        renderTarget.close();
        frameBuffer.dispose();
        GLOBALS.remove(this);
        LOG.info("画布已清理, 画布数量:{}", GLOBALS.size());
    }

    public void resize() {
        dispose();
        initContext();
    }
}
