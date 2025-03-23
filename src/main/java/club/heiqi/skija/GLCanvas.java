package club.heiqi.skija;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.impl.Stats;
import org.lwjgl.opengl.Display;

public class GLCanvas {
    public DirectContext context;
    public BackendRenderTarget target;
    public Surface surface;
    public Canvas canvas;

    public GLCanvas() {
        Stats.enabled = true;
        context = DirectContext.makeGL();
        int w = Display.getWidth(); int h = Display.getHeight();
        target = BackendRenderTarget.makeGL(w, h, 0, 8, 0, FramebufferFormat.GR_GL_RGBA8);

        surface = Surface.wrapBackendRenderTarget(
            context,
            target,
            SurfaceOrigin.BOTTOM_LEFT,
            SurfaceColorFormat.RGBA_8888,
            ColorSpace.getDisplayP3(),
            new SurfaceProps(PixelGeometry.RGB_H)
        );
        canvas = surface.getCanvas();
    }
}
