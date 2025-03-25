package club.heiqi.skija;

import club.heiqi.skija.state.StateStack;
import io.github.humbleui.skija.*;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public class GLCanvas {
    public static Logger LOG = LogManager.getLogger();
    public final DirectContext context;
    public final Surface surface;
    public final BackendRenderTarget renderTarget;

    public StateStack stateStack = new StateStack();

    public GLCanvas() {
        context = DirectContext.makeGL();
        int fboID = Minecraft.getMinecraft().getFramebuffer().framebufferObject;
        renderTarget = BackendRenderTarget.makeGL(Display.getWidth(), Display.getHeight(),
            0,8,fboID,GL_RGBA8);
        surface = Surface.wrapBackendRenderTarget(context, renderTarget,
            SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888,ColorSpace.getSRGB());
        LOG.info("Skija GPU画布已创建");
    }

    public void preFlush() {
        stateStack.backup();
        glClearColor(0,0,0,0);
        if (context != null)
            context.resetGLAll();
        glDisable(GL_ALPHA_TEST);
    }

    public void flush() {
        if (context != null)
            context.flush();
        stateStack.restore();
    }

    public void render(Consumer<Canvas> consumer) {
        preFlush();
        consumer.accept(surface.getCanvas());
        flush();
    }

    public void dispose() {
        context.close();
        surface.close();
        renderTarget.close();
    }
}
