package club.heiqi.uilib.ui.scene.integration;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.DisplayTestEnvironment;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.gl.Lwjgl2GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.GlImageResources;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.image.ItemStateScope;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImagePlacement;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 真实原 LWJGL2 compat：IMAGE 像素与独立 item state scope，不启动游戏、不冒称 Forge/Iris/glint 实测。 */
public final class ImageLwjgl2Scenario extends TestApplication {
    private static final int W = 240, H = 160;
    private final Lwjgl2GlApi gl = new Lwjgl2GlApi();
    private ClientFontServices fonts;
    private GlPaintPlanRenderer renderer;
    private int fbo;
    private int[] pixels;

    private static ImageContent solid(int rgba) {
        return new ImageContent(
                2,
                1,
                new byte[] {
                    (byte) (rgba >>> 24),
                    (byte) (rgba >>> 16),
                    (byte) (rgba >>> 8),
                    (byte) rgba,
                    (byte) (rgba >>> 24),
                    (byte) (rgba >>> 16),
                    (byte) (rgba >>> 8),
                    (byte) rgba
                });
    }

    private static FloatBuffer floats() {
        return ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static float[] value(int name) {
        FloatBuffer b = floats();
        glGetFloat(name, b);
        float[] result = new float[16];
        for (int i = 0; i < result.length; i++) result[i] = b.get(i);
        return result;
    }

    private PaintCommand image(
            ImageContent content, LayoutBox box, ImageOptions.Fit fit, LayoutBox clip) {
        return PaintCommand.image(
                box, clip, ImagePlacement.fit(content, box, ImageOptions.defaults().fit(fit)));
    }

    private void capture() {
        ByteBuffer b = ByteBuffer.allocateDirect(W * H * 4);
        glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, b);
        pixels = new int[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int i = ((H - 1 - y) * W + x) * 4;
                pixels[y * W + x] =
                        ((b.get(i + 3) & 255) << 24)
                                | ((b.get(i) & 255) << 16)
                                | ((b.get(i + 1) & 255) << 8)
                                | (b.get(i + 2) & 255);
            }
    }

    private void color(int x, int y, int expected) {
        int actual = pixels[y * W + x];
        for (int shift : new int[] {0, 8, 16, 24})
            require(
                    Math.abs(((actual >>> shift) & 255) - ((expected >>> shift) & 255)) <= 2,
                    "LWJGL2 pixel="
                            + x
                            + ","
                            + y
                            + " actual="
                            + Integer.toHexString(actual)
                            + " expected="
                            + Integer.toHexString(expected));
    }

    private boolean render(PaintPlan plan) {
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_LIGHTING);
        glDisable(GL_ALPHA_TEST);
        glClearColor(1, 1, 1, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        boolean ready;
        try (GlPaintPlanRenderer.Prepared p =
                fonts.prepare(renderer, plan, new LayoutBox(0, 0, W, H))) {
            p.render(FramebufferCoordinates.orthographic(W, H), H, false);
            ready = p.readyCount() == p.demandCount();
        }
        capture();
        return ready;
    }

    @Override
    protected void setup() throws Exception {
        own(new DisplayTestEnvironment(W, H, "UILib LWJGL2 image and scope"));
        fonts =
                own(
                        new ClientFontServices(
                                getClass().getClassLoader(),
                                1,
                                ClientFontServices.Settings.defaults(),
                                IsolatedFontEngine.shared(),
                                gl,
                                GlProgramProvider.NONE));
        GlImageResources images = own(new GlImageResources(gl, 16, 1024 * 1024));
        renderer = fonts.createPlanRenderer(images);
        onClose(() -> fonts.releasePlanRenderer(renderer));
        int texture = glGenTextures();
        fbo = glGenFramebuffers();
        onClose(
                () -> {
                    glBindFramebuffer(GL_FRAMEBUFFER, 0);
                    glDeleteFramebuffers(fbo);
                    glDeleteTextures(texture);
                });
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(
                GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "FBO");
        glViewport(0, 0, W, H);
        System.out.println(
                "LWJGL2 actual IMAGE vendor="
                        + glGetString(GL_VENDOR)
                        + " renderer="
                        + glGetString(GL_RENDERER)
                        + " version="
                        + glGetString(GL_VERSION));
    }

    @Override
    protected void runTests() throws Exception {
        scenario(
                "actual IMAGE透明rect原序及后续实际glyph",
                () -> {
                    TextLayout text =
                            fonts.layout(
                                    "IMAGE 后的 Text", new TextStyle("default", 18, 0xff101010), W);
                    PaintPlanBuilder b = new PaintPlanBuilder();
                    b.add(PaintCommand.rect(new LayoutBox(10, 10, 80, 60), null, 0xff0000ff));
                    b.add(
                            image(
                                    solid(0xff000080),
                                    new LayoutBox(10, 10, 80, 60),
                                    ImageOptions.Fit.STRETCH,
                                    null));
                    b.add(PaintCommand.rect(new LayoutBox(30, 30, 20, 20), null, 0xff00ff00));
                    b.add(PaintCommand.text(new LayoutBox(10, 90, 220, 50), null, text));
                    for (int i = 0; i < 100; i++) {
                        if (render(b.build())) break;
                        Thread.sleep(5);
                    }
                    color(20, 20, 0xff80007f);
                    color(40, 40, 0xff00ff00);
                    int dark = 0;
                    for (int y = 90; y < 140; y++)
                        for (int x = 10; x < 230; x++)
                            if ((pixels[y * W + x] & 0xffffff) < 0x606060) dark++;
                    require(dark > 20, "IMAGE之后glyph缺失");
                });
        scenario(
                "actual IMAGE contain cover native有效ancestorclip",
                () -> {
                    ImageContent red = solid(0xff0000ff);
                    PaintPlanBuilder b = new PaintPlanBuilder();
                    for (int i = 0; i < 3; i++) {
                        LayoutBox box = new LayoutBox(10 + i * 70, 10, 60, 60);
                        b.add(
                                image(
                                        red,
                                        box,
                                        new ImageOptions.Fit[] {
                                                    ImageOptions.Fit.CONTAIN,
                                                    ImageOptions.Fit.COVER,
                                                    ImageOptions.Fit.NATIVE
                                                }
                                                [i],
                                        box));
                    }
                    render(b.build());
                    color(15, 15, 0xffffffff);
                    color(15, 30, 0xffff0000);
                    color(85, 15, 0xffff0000);
                    color(75, 15, 0xffffffff);
                    color(179, 40, 0xffff0000);
                    color(155, 15, 0xffffffff);
                });
        scenario("HOST_ITEM全局alpha经过真实compat委托与原版改色后合成", this::hostOpacity);
        scenario("字体shaderdelegate进入原版flat纹理路径实际像素", () -> legacyPixels(false));
        scenario("legacy纹理matrixenv多unit及alpha污染实际像素", () -> legacyPixels(true));
        scenario("compat scope normal状态逐项对照", () -> scope(false));
        scenario("compat scope异常漏matrix与attrib push状态逐项对照", () -> scope(true));
        require(glGetError() == GL_NO_ERROR, "LWJGL2 IMAGE/scope留下GLerror");
    }

    /** 与RenderItem.renderIcon相同顶点/UV/z及Tessellator的固定管线client数组提交，不运行MC。 */
    private void flatIcon(int texture) {
        glDisable(GL_LIGHTING);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        glBindTexture(GL_TEXTURE_2D, texture);
        glColor4f(1, 1, 1, 1);
        glEnable(GL_ALPHA_TEST);
        FloatBuffer vertices =
                ByteBuffer.allocateDirect(16 * 5 * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        vertices.put(
                        new float[] {
                            0, 16, 50, 0, 1, 16, 16, 50, 1, 1, 16, 0, 50, 1, 0, 0, 0, 50, 0, 0
                        })
                .flip();
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glVertexPointer(3, 20, vertices);
        vertices.position(3);
        glTexCoordPointer(2, 20, vertices);
        glDrawArrays(GL_QUADS, 0, 4);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
    }

    private void hostOpacity() throws Exception {
        int texture = glGenTextures();
        ImageContent item = new ImageContent(16, 16) {};
        int[] calls = {0};
        TextLayout text = fonts.layout("alpha 后续文字", new TextStyle("default", 18, 0xFF101010), W);
        try (GlImageResources resources = new GlImageResources(gl, 1, 64)) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            ByteBuffer rgba = ByteBuffer.allocateDirect(16);
            rgba.put(new byte[] {0, (byte) 255, (byte) 255, (byte) 255, 0, 0, 0, 0,
                    0, (byte) 255, (byte) 255, (byte) 255, 0, 0, 0, 0}).flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 2, 2, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            resources.hostItems(new GlImageResources.HostItems() {
                public boolean supports(ImageContent content) { return content == item; }
                public void render(ImagePlacement image, LayoutBox clip, int width, int height) {
                    calls[0]++;
                    try (ItemStateScope state = new ItemStateScope(gl)) {
                        state.place(image, clip, width, height);
                        // 此原版形态绘制会把glColor重新设为白；全局alpha必须在它之后合成。
                        flatIcon(texture);
                    }
                }
            });
            GlPaintPlanRenderer consumer = fonts.createPlanRenderer(resources);
            try {
                for (float opacity : new float[] {1, .5f, .25f, 0}) {
                    PaintPlan host = new PaintPlanBuilder().add(image(item, new LayoutBox(20, 20, 64, 64),
                            ImageOptions.Fit.STRETCH, new LayoutBox(20, 20, 48, 64)))
                            .build().presented(0, 0, opacity, null);
                    PaintPlanBuilder b = new PaintPlanBuilder();
                    for (PaintCommand command : host.commands()) b.add(command);
                    b.add(PaintCommand.text(new LayoutBox(5, 100, 220, 40), null, text));
                    for (int i = 0; i < 100; i++) {
                        glDisable(GL_SCISSOR_TEST);
                        glClearColor(1, 1, 1, 1);
                        glClear(GL_COLOR_BUFFER_BIT);
                        boolean ready;
                        try (GlPaintPlanRenderer.Prepared prepared = fonts.prepare(consumer, b.build(), new LayoutBox(0, 0, W, H))) {
                            prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
                            ready = prepared.readyCount() == prepared.demandCount();
                        }
                        capture();
                        if (ready) break;
                        Thread.sleep(5);
                    }
                    int a = Math.round(255 * opacity);
                    color(25, 25, 0xFF00FFFF | ((255 - a) << 16));
                    color(60, 25, 0xFFFFFFFF);
                    color(70, 25, 0xFFFFFFFF);
                    int dark = 0;
                    for (int y = 100; y < 140; y++) for (int x = 5; x < 230; x++)
                        if ((pixels[y * W + x] & 0xffffff) < 0x606060) dark++;
                    require(dark > 20, "透明物品后续文字缺失");
                    if (opacity == 1) require(resources.retainedHostCaptureBytes() == 0, "旧不透明委托分配了合成面");
                    else require(resources.retainedHostCaptureBytes() == 48L * 64 * 8, "旧宿主capture未按有效clip裁定预算");
                    require(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == fbo, "物品alpha未恢复入口FBO");
                    checkError("host alpha " + opacity);
                    System.out.println("LWJGL2_HOST_OPACITY alpha=" + opacity + " pixel="
                            + Integer.toHexString(pixels[25 * W + 25]) + " bytes=" + resources.retainedHostCaptureBytes());
                }
                require(calls[0] >= 4, "透明物品未实际重放宿主委托");
            } finally { fonts.releasePlanRenderer(consumer); }
        } finally { glDeleteTextures(texture); }
    }

    private void legacyPixels(boolean poison) throws Exception {
        int texture = glGenTextures();
        try {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            ByteBuffer bytes = ByteBuffer.allocateDirect(16);
            bytes.put(
                            new byte[] {
                                0,
                                (byte) 255,
                                (byte) 255,
                                (byte) 255,
                                0,
                                0,
                                0,
                                0,
                                0,
                                (byte) 255,
                                (byte) 255,
                                (byte) 255,
                                0,
                                0,
                                0,
                                0
                            })
                    .flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 2, 2, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            TextLayout text =
                    fonts.layout("after legacy 图标", new TextStyle("default", 18, 0xff101010), W);
            PaintPlanBuilder b = new PaintPlanBuilder();
            b.add(PaintCommand.rect(new LayoutBox(0, 0, W, H), null, 0xffffffff));
            b.add(PaintCommand.text(new LayoutBox(5, 100, 220, 40), null, text));
            // shader绘制不需要固定管线texture enable；主动保留此典型shader入口，验证scope自备旧路径。
            glDisable(GL_TEXTURE_2D);
            glAlphaFunc(GL_GREATER, .1f);
            if (poison) {
                glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_DECAL);
                glMatrixMode(GL_TEXTURE);
                glLoadIdentity();
                glTranslatef(.75f, .4f, 0);
                glActiveTexture(GL_TEXTURE1);
                glEnable(GL_TEXTURE_2D);
                glBindTexture(GL_TEXTURE_2D, texture);
                glClientActiveTexture(GL_TEXTURE1);
                glAlphaFunc(GL_NEVER, 1);
                glColor4f(0, 0, 0, 0);
                glMatrixMode(GL_MODELVIEW);
            }
            for (int i = 0; i < 100; i++) {
                glClearColor(1, 1, 1, 1);
                glClear(GL_COLOR_BUFFER_BIT);
                boolean ready;
                try (GlPaintPlanRenderer.Prepared prepared =
                        fonts.prepare(renderer, b.build(), new LayoutBox(0, 0, W, H))) {
                    try (GlPaintPlanRenderer.Replay replay =
                            prepared.begin(FramebufferCoordinates.orthographic(W, H), H, false)) {
                        replay.draw(b.build().commands().get(0));
                        replay.delegate(
                                () -> {
                                    try (ItemStateScope state = new ItemStateScope(gl)) {
                                        ImageContent host = new ImageContent(16, 16) {};
                                        state.place(
                                                ImagePlacement.fit(
                                                        host,
                                                        new LayoutBox(20, 20, 64, 64),
                                                        ImageOptions.defaults()),
                                                new LayoutBox(20, 20, 24, 64),
                                                W,
                                                H);
                                        flatIcon(texture);
                                        checkError("flat actual draw");
                                    }
                                });
                        replay.draw(b.build().commands().get(1));
                    }
                    ready = prepared.readyCount() == prepared.demandCount();
                }
                capture();
                if (ready) break;
                Thread.sleep(5);
            }
            color(25, 25, 0xff00ffff);
            color(60, 25, 0xffffffff);
            color(48, 25, 0xffffffff);
            int dark = 0;
            for (int y = 100; y < 140; y++)
                for (int x = 5; x < 230; x++) if ((pixels[y * W + x] & 0xffffff) < 0x606060) dark++;
            require(dark > 20, "legacy后实际文字缺失");
            System.out.println(
                    "LEGACY_FLAT_PIXELS_PASS actualShaderDelegate=true texturedCyan=true transparent=true ancestorClip=true afterGlyph=true");
        } finally {
            glActiveTexture(GL_TEXTURE1);
            glDisable(GL_TEXTURE_2D);
            glActiveTexture(GL_TEXTURE0);
            glClientActiveTexture(GL_TEXTURE0);
            glMatrixMode(GL_TEXTURE);
            glLoadIdentity();
            glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);
            glMatrixMode(GL_MODELVIEW);
            glAlphaFunc(GL_GREATER, .1f);
            glColor4f(1, 1, 1, 1);
            glDeleteTextures(texture);
        }
    }

    private static final int[] INTEGER = {
        GL_MATRIX_MODE,
        GL_PROJECTION_STACK_DEPTH,
        GL_MODELVIEW_STACK_DEPTH,
        GL_ATTRIB_STACK_DEPTH,
        GL_CLIENT_ATTRIB_STACK_DEPTH,
        GL_ACTIVE_TEXTURE,
        GL_CURRENT_PROGRAM,
        GL_VERTEX_ARRAY_BINDING,
        GL_ARRAY_BUFFER_BINDING,
        GL_ELEMENT_ARRAY_BUFFER_BINDING,
        GL_DRAW_FRAMEBUFFER_BINDING,
        GL_READ_FRAMEBUFFER_BINDING,
        GL_DEPTH_FUNC,
        GL_BLEND_SRC_RGB,
        GL_BLEND_DST_RGB,
        GL_ALPHA_TEST_FUNC
    };

    private int[] integers() {
        int[] out = new int[INTEGER.length];
        for (int i = 0; i < out.length; i++) out[i] = glGetInteger(INTEGER[i]);
        return out;
    }

    private void checkError(String stage) {
        int error = glGetError();
        require(error == GL_NO_ERROR, "scope " + stage + " GLerror=" + error);
    }

    private void scope(boolean fail) {
        checkError("entry");
        int vao = glGenVertexArrays(),
                hookVao = glGenVertexArrays(),
                vbo = glGenBuffers(),
                ebo = glGenBuffers(),
                tex0 = glGenTextures(),
                tex1 = glGenTextures(),
                sampler = glGenSamplers();
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glVertexPointer(2, GL_FLOAT, 8, 0L);
        glEnableClientState(GL_VERTEX_ARRAY);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glViewport(3, 4, W - 8, H - 9);
        glScissor(5, 6, 70, 80);
        glEnable(GL_SCISSOR_TEST);
        glEnable(GL_LIGHTING);
        glEnable(GL_LIGHT0);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, .4f);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        glColor4f(.2f, .3f, .4f, .5f);
        glNormal3f(.3f, .4f, .5f);
        glMaterialf(GL_FRONT, GL_SHININESS, 17);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glTranslatef(.1f, .2f, .3f);
        glPushMatrix();
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glScalef(2, 3, 4);
        glPushMatrix();
        for (int unit = 0; unit < 2; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, unit == 0 ? tex0 : tex1);
            glBindSampler(unit, sampler);
            glMatrixMode(GL_TEXTURE);
            glLoadIdentity();
            glTranslatef(unit + .1f, .2f, .3f);
            glPushMatrix();
        }
        glActiveTexture(GL_TEXTURE1);
        glMatrixMode(GL_TEXTURE);
        int[] before = integers();
        float[] projection = value(GL_PROJECTION_MATRIX),
                model = value(GL_MODELVIEW_MATRIX),
                normal = value(GL_CURRENT_NORMAL),
                color = value(GL_CURRENT_COLOR),
                textureMatrix = value(GL_TEXTURE_MATRIX);
        int[] viewport = new int[4], scissor = new int[4];
        gl.glGetIntegerv(GL_VIEWPORT, viewport);
        gl.glGetIntegerv(GL_SCISSOR_BOX, scissor);
        checkError("preflight");
        try {
            try (ItemStateScope state = new ItemStateScope(gl)) {
                checkError("capture");
                state.place(
                        ImagePlacement.fit(
                                solid(0xff0000ff),
                                new LayoutBox(10, 20, 80, 60),
                                ImageOptions.defaults()),
                        new LayoutBox(10, 20, 40, 30),
                        W,
                        H);
                checkError("place");
                glDisable(GL_LIGHTING);
                glDisable(GL_LIGHT0);
                glDisable(GL_ALPHA_TEST);
                glAlphaFunc(GL_ALWAYS, .1f);
                glDisable(GL_DEPTH_TEST);
                glDepthFunc(GL_ALWAYS);
                glDepthMask(true);
                glDisable(GL_BLEND);
                glBlendFunc(GL_ONE, GL_ZERO);
                glColor4f(1, 0, 0, 1);
                glNormal3f(0, 0, -1);
                glMaterialf(GL_FRONT, GL_SHININESS, 2);
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
                for (int unit = 0; unit < 2; unit++) {
                    glActiveTexture(GL_TEXTURE0 + unit);
                    glBindTexture(GL_TEXTURE_2D, 0);
                    glBindSampler(unit, 0);
                    glMatrixMode(GL_TEXTURE);
                    glLoadIdentity();
                    if (fail) glPushMatrix();
                }
                glMatrixMode(GL_PROJECTION);
                glLoadIdentity();
                if (fail) glPushMatrix();
                glMatrixMode(GL_MODELVIEW);
                glLoadIdentity();
                if (fail) {
                    glPushMatrix();
                    glPushAttrib(GL_ALL_ATTRIB_BITS);
                    glBindVertexArray(hookVao);
                    glPushClientAttrib(-1);
                    glEnableClientState(GL_VERTEX_ARRAY);
                    throw new IllegalStateException("probe");
                }
                checkError("body");
            }
        } catch (IllegalStateException error) {
            if (!fail || !"probe".equals(error.getMessage())) throw error;
        }
        checkError("restored");
        glBindVertexArray(0);
        require(
                glIsEnabled(GL_VERTEX_ARRAY)
                        && glGetInteger(GL_VERTEX_ARRAY_SIZE) == 2
                        && glGetInteger(GL_VERTEX_ARRAY_STRIDE) == 8
                        && glGetInteger(GL_VERTEX_ARRAY_BUFFER_BINDING) == vbo
                        && glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING) == ebo,
                "默认VAO客户端数组或EBO未归还");
        glBindVertexArray(vao);
        require(
                Arrays.equals(before, integers()),
                "scope integer bindings/depth/mode/alpha/blend不一致");
        require(
                Arrays.equals(projection, value(GL_PROJECTION_MATRIX))
                        && Arrays.equals(model, value(GL_MODELVIEW_MATRIX))
                        && Arrays.equals(textureMatrix, value(GL_TEXTURE_MATRIX)),
                "scope矩阵未恢复");
        require(
                Arrays.equals(normal, value(GL_CURRENT_NORMAL))
                        && Arrays.equals(color, value(GL_CURRENT_COLOR)),
                "scope normal/color未恢复");
        int[] after = new int[4];
        gl.glGetIntegerv(GL_VIEWPORT, after);
        require(Arrays.equals(viewport, after), "scope viewport");
        gl.glGetIntegerv(GL_SCISSOR_BOX, after);
        require(Arrays.equals(scissor, after) && glIsEnabled(GL_SCISSOR_TEST), "scope scissor");
        require(
                glIsEnabled(GL_LIGHTING)
                        && glIsEnabled(GL_LIGHT0)
                        && glIsEnabled(GL_ALPHA_TEST)
                        && glIsEnabled(GL_DEPTH_TEST)
                        && glIsEnabled(GL_BLEND)
                        && !glGetBoolean(GL_DEPTH_WRITEMASK),
                "scope enables/depthmask");
        require(glGetFloat(GL_ALPHA_TEST_REF) == .4f, "scope alpha ref");
        FloatBuffer mat = floats();
        glGetMaterial(GL_FRONT, GL_SHININESS, mat);
        require(mat.get(0) == 17, "scope material");
        for (int unit = 0; unit < 2; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            require(
                    glGetInteger(GL_TEXTURE_BINDING_2D) == (unit == 0 ? tex0 : tex1)
                            && glGetInteger(GL_SAMPLER_BINDING) == sampler,
                    "scope unit="
                            + unit
                            + " texture="
                            + glGetInteger(GL_TEXTURE_BINDING_2D)
                            + " expectedTexture="
                            + (unit == 0 ? tex0 : tex1)
                            + " sampler="
                            + glGetInteger(GL_SAMPLER_BINDING)
                            + " expectedSampler="
                            + sampler);
            glMatrixMode(GL_TEXTURE);
            glPopMatrix();
        }
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glActiveTexture(GL_TEXTURE0);
        glBindSampler(0, 0);
        glBindSampler(1, 0);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glDeleteVertexArrays(vao);
        glDeleteVertexArrays(hookVao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteTextures(tex0);
        glDeleteTextures(tex1);
        glDeleteSamplers(sampler);
        glDisable(GL_LIGHTING);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDepthMask(true);
        glViewport(0, 0, W, H);
        checkError("cleanup");
    }

    public static void main(String[] args) throws Exception {
        new ImageLwjgl2Scenario().run(args);
    }
}
