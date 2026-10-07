package club.heiqi.uilib.testing;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/** 独立LWJGL2宿主，保留默认Display.create()上下文参数，不依赖LWJGL3。 */
public final class DisplayTestEnvironment implements AutoCloseable {
    public DisplayTestEnvironment(int width, int height, String title) throws LWJGLException {
        try {
            Display.setDisplayMode(new DisplayMode(width, height));
            Display.setTitle(title);
            Display.create();
        } catch (LWJGLException | RuntimeException | Error failure) {
            try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @Override public void close() { if (Display.isCreated()) Display.destroy(); }
}
