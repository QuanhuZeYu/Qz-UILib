package club.heiqi.uilib.host.client.font.context;

import java.util.Objects;
import club.heiqi.uilib.host.client.gl.GlApi;
import static club.heiqi.uilib.host.client.gl.GlTokens.*;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.MatrixState;

/** 捕捉调用线程当前LWJGL context的实际target/viewport/depth/scissor。
 * 固定管线矩阵必须由adapter声明来源；core profile不允许猜测driver legacy矩阵。
 * 此类不切FBO、不画文字，也不是可恢复所有GL状态的guard。延期回放另须保序和producer合同。 */
public final class LwjglFontContextCapture {
    /** 可由GLSM覆盖；只查询合法GL3光栅枚举，不碰legacy矩阵/alpha/light。 */
    public interface RasterQueries {
        GlApi gl();

        default int integer(int name) {
            return gl().glGetInteger(name);
        }

        default int[] integers(int name, int count) {
            int[] values = new int[count];
            gl().glGetIntegerv(name, values);
            return values;
        }

        default boolean enabled(int name) {
            return gl().glIsEnabled(name);
        }

        default boolean[] booleans(int name, int count) {
            int[] values = integers(name, count);
            boolean[] out = new boolean[count];
            for (int i = 0; i < count; i++) out[i] = values[i] != 0;
            return out;
        }

        default float[] floats(int name, int count) {
            float[] values = new float[count];
            gl().glGetFloatv(name, values);
            return values;
        }
    }

    public static FontRenderContext.RasterState driverCapture(final GlApi gl) {
        return captureRaster(
                new RasterQueries() {
                    public GlApi gl() {
                        return gl;
                    }
                });
    }

    static FontRenderContext.RasterState captureRaster(RasterQueries q) {
        return new FontRenderContext.RasterState(
                q.enabled(GL_BLEND),
                q.integer(GL_BLEND_SRC_RGB),
                q.integer(GL_BLEND_DST_RGB),
                q.integer(GL_BLEND_SRC_ALPHA),
                q.integer(GL_BLEND_DST_ALPHA),
                q.integer(GL_BLEND_EQUATION_RGB),
                q.integer(GL_BLEND_EQUATION_ALPHA),
                q.floats(GL_BLEND_COLOR, 4),
                q.enabled(GL_STENCIL_TEST),
                stencil(q, false),
                stencil(q, true),
                q.booleans(GL_COLOR_WRITEMASK, 4),
                q.floats(GL_DEPTH_RANGE, 2),
                q.enabled(GL_CULL_FACE),
                q.integer(GL_CULL_FACE_MODE),
                q.integer(GL_FRONT_FACE),
                q.enabled(GL_POLYGON_OFFSET_FILL),
                q.floats(GL_POLYGON_OFFSET_FACTOR, 1)[0],
                q.floats(GL_POLYGON_OFFSET_UNITS, 1)[0]);
    }

    private static FontRenderContext.StencilFace stencil(RasterQueries q, boolean back) {
        return new FontRenderContext.StencilFace(
                q.integer(back ? GL_STENCIL_BACK_FUNC : GL_STENCIL_FUNC),
                q.integer(back ? GL_STENCIL_BACK_REF : GL_STENCIL_REF),
                q.integer(back ? GL_STENCIL_BACK_VALUE_MASK : GL_STENCIL_VALUE_MASK),
                q.integer(back ? GL_STENCIL_BACK_WRITEMASK : GL_STENCIL_WRITEMASK),
                q.integer(back ? GL_STENCIL_BACK_FAIL : GL_STENCIL_FAIL),
                q.integer(back ? GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL_STENCIL_PASS_DEPTH_FAIL),
                q.integer(back ? GL_STENCIL_BACK_PASS_DEPTH_PASS : GL_STENCIL_PASS_DEPTH_PASS));
    }

    public interface MatrixStateProvider extends RasterQueries {
        default FontRenderContext.RasterState rasterState() {
            return captureRaster(this);
        }

        MatrixState capture();

        default boolean depthWrite() {
            return booleans(GL_DEPTH_WRITEMASK, 1)[0];
        }
    }

    private final GlApi gl;
    private final MatrixStateProvider provider;

    public LwjglFontContextCapture(GlApi gl, MatrixStateProvider provider) {
        this.gl = Objects.requireNonNull(gl, "gl");
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    public FontRenderContext capture(int sourceFramebufferHeight, long revision, int passKey) {
        if (sourceFramebufferHeight <= 0)
            throw new IllegalArgumentException("须提供当前target的framebuffer像素高度");
        Object identity = gl.capabilities().contextIdentity;
        MatrixState state = Objects.requireNonNull(provider.capture(), "provider返回null");
        int[] v = provider.integers(GL_VIEWPORT, 4), s = provider.integers(GL_SCISSOR_BOX, 4);
        FontRenderContext.RasterState raster =
                Objects.requireNonNull(provider.rasterState(), "provider rasterState");
        if (!gl.capabilities().openGL32)
            throw new IllegalStateException(
                    "capture要求OpenGL3实际draw framebuffer，旧context须显式target adapter");
        int fbo = provider.integer(GL_DRAW_FRAMEBUFFER_BINDING);
        return new FontRenderContext(
                new FontProjection(state.projection(), state.modelView(), v),
                fbo,
                sourceFramebufferHeight,
                provider.enabled(GL_DEPTH_TEST),
                provider.integer(GL_DEPTH_FUNC),
                provider.depthWrite(),
                provider.enabled(GL_SCISSOR_TEST),
                s,
                state,
                identity,
                revision,
                passKey,
                raster);
    }

    /** core/own scene由其根空间提供真实P/MV及状态，无legacy查询。 */
    public static MatrixStateProvider explicit(final GlApi gl, final MatrixState state) {
        Objects.requireNonNull(state, "state");
        return new MatrixStateProvider() {
            public GlApi gl() {
                return gl;
            }

            public MatrixState capture() {
                return state;
            }
        };
    }

    /** 只有合法compatibility context可调用；无宿主协议时不猜lightmap/blockEntity/FFP灯光。 */
    public static MatrixStateProvider compatibility(final GlApi gl) {
        return new MatrixStateProvider() {
            public GlApi gl() {
                return gl;
            }

            public MatrixState capture() {
                requireCompatibility(gl);
                if (gl.glIsEnabled(GL_LIGHTING))
                    throw new IllegalStateException(
                            "driver FFP lighting须由显式world provider提供，不可用UI默认lighting假代替");
                return new MatrixState(
                        read(gl, GL_PROJECTION_MATRIX, 16),
                        read(gl, GL_MODELVIEW_MATRIX, 16),
                        read(gl, GL_CURRENT_COLOR, 4),
                        gl.glIsEnabled(GL_ALPHA_TEST),
                        gl.glGetInteger(GL_ALPHA_TEST_FUNC),
                        gl.glGetFloat(GL_ALPHA_TEST_REF),
                        false,
                        0,
                        0,
                        0,
                        0,
                        read(gl, GL_CURRENT_NORMAL, 3),
                        new float[] {1, 1, 1},
                        -1);
            }
        };
    }

    private static void requireCompatibility(GlApi gl) {
        if (gl.capabilities().openGL32
                && (gl.glGetInteger(GL_CONTEXT_PROFILE_MASK) & GL_CONTEXT_CORE_PROFILE_BIT) != 0)
            throw new IllegalStateException(
                    "core context必须提供显式或宿主有效矩阵provider，禁止legacy glGetFloat");
        if (gl.capabilities().openGL32
                && (gl.glGetInteger(GL_CONTEXT_FLAGS) & GL_CONTEXT_FLAG_FORWARD_COMPATIBLE_BIT)
                        != 0)
            throw new IllegalStateException("forward-compatible context禁止固定管线矩阵读取");
    }

    private static float[] read(GlApi gl, int name, int size) {
        float[] values = new float[size];
        gl.glGetFloatv(name, values);
        return values;
    }
}
