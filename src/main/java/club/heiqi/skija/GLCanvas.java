package club.heiqi.skija;

import club.heiqi.qz_blockinfo.hook.BeforeSwapBufferEvent;
import club.heiqi.skija.state.GLPixelStore;
import club.heiqi.skija.state.SkiaStore;
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
    public final DirectContext context;
    public Surface surface;
    public BackendRenderTarget renderTarget;
    public FrameBuffer frameBuffer;

    public GLPixelStore pixelStore = new GLPixelStore();
    public SkiaStore skiaStore = new SkiaStore();

    public GLCanvas() {
        int width = Display.getWidth(); int height = Display.getHeight();
        frameBuffer = new FrameBuffer(width, height);
        context = DirectContext.makeGL();
        renderTarget = BackendRenderTarget.makeGL(width, height,
            0,8, frameBuffer.fboID, GL_RGBA8);
        surface = Surface.wrapBackendRenderTarget(context, renderTarget,
            SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888,ColorSpace.getSRGB());
        LOG.info("Skija GPU画布已创建 fbo:{}", frameBuffer.fboID);
        GLOBALS.add(this);
    }

    public void preFlush() {
        frameBuffer.bind(Display.getWidth(), Display.getHeight());
        skiaStore.backup();
        pixelStore.backup();
        context.resetGLAll();
    }

    public void flush() {
        context.flush();
        pixelStore.restore();
        skiaStore.restore();
        frameBuffer.unbind();
    }

    public void render(Consumer<Canvas> consumer) {
        BeforeSwapBufferEvent.runnable.add(() -> renderAtLast(consumer));
    }

    public void renderAtLast(Consumer<Canvas> consumer) {
        preFlush();
        consumer.accept(surface.getCanvas());
        flush();
        frameBuffer.renderToScreen();
    }

    public void dispose() {
        context.close();
        surface.close();
        renderTarget.close();
        frameBuffer.dispose();
        GLOBALS.remove(this);
    }

    public void resize() {
        renderTarget.close(); surface.close();
        renderTarget = BackendRenderTarget.makeGL(Display.getWidth(), Display.getHeight(),
            0,8, frameBuffer.fboID, GL_RGBA8);
        surface = Surface.wrapBackendRenderTarget(context, renderTarget,
            SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888,ColorSpace.getSRGB());
        LOG.info("Skija GPU画布已重建 绑定fbo: {}", frameBuffer.fboID);
    }
}
