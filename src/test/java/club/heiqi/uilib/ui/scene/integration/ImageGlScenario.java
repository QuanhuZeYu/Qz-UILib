package club.heiqi.uilib.ui.scene.integration;

import static org.lwjgl.opengl.GL32C.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.GlfwTestEnvironment;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.gl.GlImageResources;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImagePlacement;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 真GPU读取RGBA：图像/文字/rect有序批次、fit与有效clip、upload预算和状态恢复，不伪物品render。 */
public final class ImageGlScenario extends TestApplication {
    private static final int W = 380, H = 170;
    private ClientFontServices fonts;
    private GlImageResources images;
    private GlPaintPlanRenderer renderer;
    private int[] pixels;
    private File output;
    private boolean glyphsReady;

    private static ImageContent solid(int width, int height, int rgba) {
        byte[] values = new byte[width * height * 4];
        for (int i = 0; i < values.length; i += 4) {
            values[i] = (byte) (rgba >>> 24);
            values[i + 1] = (byte) (rgba >>> 16);
            values[i + 2] = (byte) (rgba >>> 8);
            values[i + 3] = (byte) rgba;
        }
        return new ImageContent(width, height, values);
    }

    private static ImageContent stripes() {
        byte[] rgba = new byte[120 * 60 * 4];
        for (int y = 0; y < 60; y++)
            for (int x = 0; x < 120; x++) {
                int i = (y * 120 + x) * 4;
                rgba[i + x / 40] = (byte) 255;
                rgba[i + 3] = (byte) 255;
            }
        return new ImageContent(120, 60, rgba);
    }

    private PaintCommand image(
            ImageContent content, LayoutBox box, ImageOptions.Fit fit, LayoutBox clip) {
        PaintPlan pending =
                new PaintPlanBuilder().add(PaintCommand.rect(box, clip, 0xFFFFCC00)).build();
        PaintPlan failed =
                new PaintPlanBuilder().add(PaintCommand.rect(box, clip, 0xFF5500AA)).build();
        return PaintCommand.image(
                box,
                clip,
                ImagePlacement.fit(content, box, ImageOptions.defaults().fit(fit)),
                pending,
                failed,
                null);
    }

    private static void clear() {
        glDisable(GL_SCISSOR_TEST);
        glClearColor(1, 1, 1, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private void capture() throws Exception {
        ByteBuffer buffer = MemoryUtil.memAlloc(W * H * 4);
        try {
            glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            pixels = new int[W * H];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = ((H - y - 1) * W + x) * 4;
                    pixels[y * W + x] =
                            ((buffer.get(i + 3) & 255) << 24)
                                    | ((buffer.get(i) & 255) << 16)
                                    | ((buffer.get(i + 1) & 255) << 8)
                                    | (buffer.get(i + 2) & 255);
                }
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private void color(int x, int y, int expected) {
        int value = pixels[y * W + x];
        for (int shift : new int[] {0, 8, 16, 24})
            require(
                    Math.abs(((value >>> shift) & 255) - ((expected >>> shift) & 255)) <= 2,
                    "像素("
                            + x
                            + ","
                            + y
                            + ")实际="
                            + Integer.toHexString(value)
                            + "期望="
                            + Integer.toHexString(expected));
    }

    private void render(PaintPlan plan) throws Exception {
        try (GlPaintPlanRenderer.Prepared prepared =
                fonts.prepare(renderer, plan, new LayoutBox(0, 0, W, H))) {
            prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
            glyphsReady = prepared.readyCount() == prepared.demandCount();
        }
        capture();
    }

    private void save(String name) throws Exception {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, W, H, pixels, 0, W);
        ImageIO.write(image, "png", new File(output, name));
    }

    @Override
    protected void setup() throws Exception {
        output = new File(arguments().length == 0 ? "build/image-system/gpu" : arguments()[0]);
        require(output.isDirectory() || output.mkdirs(), "GPU输出目录失败");
        GlfwTestEnvironment environment = own(new GlfwTestEnvironment());
        environment.window(W, H, "UILib image GPU", GlfwTestEnvironment.Profile.CORE, 2);
        Lwjgl3GlApi gl = new Lwjgl3GlApi();
        fonts =
                own(
                        new ClientFontServices(
                                getClass().getClassLoader(),
                                1,
                                ClientFontServices.Settings.defaults(),
                                IsolatedFontEngine.shared(),
                                gl,
                                GlProgramProvider.NONE));
        images = own(new GlImageResources(gl, 16, 1024 * 1024));
        renderer = fonts.createPlanRenderer(images);
        onClose(() -> fonts.releasePlanRenderer(renderer));
        int texture = glGenTextures(), fbo = glGenFramebuffers();
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
        require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "FBO失败");
        glViewport(0, 0, W, H);
        System.out.println(
                "vendor="
                        + glGetString(GL_VENDOR)
                        + " renderer="
                        + glGetString(GL_RENDERER)
                        + " version="
                        + glGetString(GL_VERSION));
    }

    @Override
    protected void runTests() throws Exception {
        scenario(
                "真实contain cover stretch native及ancestorclip",
                () -> {
                    ImageContent red = stripes();
                    PaintPlanBuilder builder = new PaintPlanBuilder();
                    int index = 0;
                    for (ImageOptions.Fit fit : ImageOptions.Fit.values()) {
                        LayoutBox box = new LayoutBox(10 + index++ * 90, 20, 80, 80);
                        LayoutBox clip =
                                fit == ImageOptions.Fit.COVER || fit == ImageOptions.Fit.NATIVE
                                        ? box
                                        : null;
                        builder.add(image(red, box, fit, clip));
                    }
                    clear();
                    render(builder.build());
                    color(15, 25, 0xFFFFFFFF);
                    color(15, 50, 0xFFFF0000);
                    color(105, 25, 0xFFFF0000);
                    color(95, 25, 0xFFFFFFFF);
                    color(195, 25, 0xFFFF0000);
                    color(285, 25, 0xFFFFFFFF);
                    color(285, 50, 0xFFFF0000);
                    color(275, 50, 0xFFFFFFFF);
                    color(31, 60, 0xFFFF0000);
                    color(121, 60, 0xFF00FF00);
                    color(211, 60, 0xFFFF0000);
                    color(301, 60, 0xFF00FF00);
                    save("fits.png");
                });
        scenario(
                "straightRGBA透明与rect image text原序后续文字state",
                () -> {
                    ImageContent transparent = solid(2, 2, 0xFF000080);
                    TextLayout text =
                            fonts.layout(
                                    "Image 后的中文 Text",
                                    new TextStyle("default", 18, 0xFF101010),
                                    340);
                    PaintPlanBuilder builder = new PaintPlanBuilder();
                    builder.add(PaintCommand.rect(new LayoutBox(10, 10, 80, 80), null, 0xFF0000FF));
                    builder.add(
                            image(
                                    transparent,
                                    new LayoutBox(10, 10, 80, 80),
                                    ImageOptions.Fit.STRETCH,
                                    null));
                    builder.add(PaintCommand.rect(new LayoutBox(30, 30, 20, 20), null, 0xFF00FF00));
                    builder.add(PaintCommand.text(new LayoutBox(10, 110, 340, 40), null, text));
                    PaintPlan plan = builder.build();
                    for (int i = 0; i < 80; i++) {
                        clear();
                        render(plan);
                        if (glyphsReady && fonts.rasterizer().stats().started) break;
                        Thread.sleep(5);
                    }
                    color(20, 20, 0xFF80007F);
                    color(40, 40, 0xFF00FF00);
                    int dark = 0;
                    for (int y = 110; y < 155; y++)
                        for (int x = 10; x < 350; x++)
                            if ((pixels[y * W + x] & 0xFFFFFF) < 0x606060) dark++;
                    require(dark > 30, "图像后文字未产生真实glyph像素");
                    save("alpha-order-text.png");
                });
        scenario(
                "图像上传pixelstore PBO texture与后续quad恢复",
                () -> {
                    int oldUnit = glGetInteger(GL_ACTIVE_TEXTURE);
                    glActiveTexture(GL_TEXTURE0 + 3);
                    int texture = glGenTextures(), pbo = glGenBuffers();
                    onClose(
                            () -> {
                                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                                glDeleteBuffers(pbo);
                                glDeleteTextures(texture);
                                glActiveTexture(oldUnit);
                            });
                    glBindTexture(GL_TEXTURE_2D, texture);
                    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);
                    glBufferData(GL_PIXEL_UNPACK_BUFFER, 4096, GL_STATIC_DRAW);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 8);
                    glPixelStorei(GL_UNPACK_ROW_LENGTH, 7);
                    glPixelStorei(GL_UNPACK_SKIP_ROWS, 2);
                    glEnable(GL_SCISSOR_TEST);
                    glScissor(2, 3, 20, 21);
                    glEnable(GL_BLEND);
                    glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
                    int[] scissor = new int[4];
                    glGetIntegerv(GL_SCISSOR_BOX, scissor);
                    PaintPlanBuilder builder = new PaintPlanBuilder();
                    builder.add(
                            image(
                                    solid(3, 3, 0x00FF00FF),
                                    new LayoutBox(5, 5, 15, 15),
                                    ImageOptions.Fit.STRETCH,
                                    null));
                    render(builder.build());
                    require(
                            glGetInteger(GL_ACTIVE_TEXTURE) == GL_TEXTURE0 + 3
                                    && glGetInteger(GL_TEXTURE_BINDING_2D) == texture,
                            "图像破坏宿主纹理绑定");
                    require(
                            glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING) == pbo
                                    && glGetInteger(GL_UNPACK_ALIGNMENT) == 8
                                    && glGetInteger(GL_UNPACK_ROW_LENGTH) == 7
                                    && glGetInteger(GL_UNPACK_SKIP_ROWS) == 2,
                            "图像上传污染PBO/pixelstore");
                    int[] restored = new int[4];
                    glGetIntegerv(GL_SCISSOR_BOX, restored);
                    require(
                            Arrays.equals(scissor, restored)
                                    && glIsEnabled(GL_SCISSOR_TEST)
                                    && glGetInteger(GL_BLEND_SRC_RGB) == GL_SRC_ALPHA,
                            "图像quad污染scissor/blend");
                    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                    glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
                    glDisable(GL_SCISSOR_TEST);
                });
        scenario(
                "GPU局部失败与每次upload预算后续rect继续",
                () -> {
                    final int[] failure = {0}, pending = {0};
                    GlImageResources tiny =
                            own(
                                    new GlImageResources(
                                            new Lwjgl3GlApi(),
                                            4,
                                            64,
                                            4,
                                            1,
                                            (identity, waiting, message) -> {
                                                if (message != null) failure[0]++;
                                                if (waiting) pending[0]++;
                                            }));
                    GlPaintPlanRenderer consumer = fonts.createPlanRenderer(tiny);
                    onClose(() -> fonts.releasePlanRenderer(consumer));
                    ImageContent tooLarge = solid(2, 2, 0x00FF00FF),
                            a = solid(1, 1, 0xFF0000FF),
                            b = solid(1, 1, 0x0000FFFF);
                    PaintPlanBuilder builder = new PaintPlanBuilder();
                    builder.add(
                            image(
                                    tooLarge,
                                    new LayoutBox(10, 10, 20, 20),
                                    ImageOptions.Fit.STRETCH,
                                    null));
                    builder.add(
                            image(
                                    a,
                                    new LayoutBox(40, 10, 20, 20),
                                    ImageOptions.Fit.STRETCH,
                                    null));
                    builder.add(
                            image(
                                    b,
                                    new LayoutBox(70, 10, 20, 20),
                                    ImageOptions.Fit.STRETCH,
                                    null));
                    builder.add(
                            PaintCommand.rect(new LayoutBox(100, 10, 20, 20), null, 0xFF00FF00));
                    PaintPlan plan = builder.build();
                    clear();
                    try (GlPaintPlanRenderer.Prepared prepared =
                            fonts.prepare(consumer, plan, new LayoutBox(0, 0, W, H))) {
                        prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
                    }
                    capture();
                    color(15, 15, 0xFF5500AA);
                    color(45, 15, 0xFFFF0000);
                    color(75, 15, 0xFFFFCC00);
                    color(105, 15, 0xFF00FF00);
                    require(failure[0] == 1 && pending[0] == 1, "上传失败/限额缺局部反馈");
                    clear();
                    try (GlPaintPlanRenderer.Prepared prepared =
                            fonts.prepare(consumer, plan, new LayoutBox(0, 0, W, H))) {
                        prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
                    }
                    capture();
                    color(75, 15, 0xFF0000FF);
                    save("upload-budget-failure.png");
                });
        scenario(
                "备用先驻留时新主图优先取得纹理槽与字节预算",
                () -> {
                    for (boolean texturePressure : new boolean[] {true, false}) {
                        ImageContent backup = solid(1, 1, 0x2244AAFF),
                                primary = solid(1, 1, 0x22AA55FF);
                        LayoutBox box = new LayoutBox(10, 10, 40, 40);
                        java.util.List<String> failures = new java.util.ArrayList<>();
                        java.util.Set<Long> pending = new java.util.HashSet<>();
                        try (GlImageResources resources =
                                new GlImageResources(
                                        new Lwjgl3GlApi(),
                                        texturePressure ? 1 : 4,
                                        texturePressure ? 64 : primary.bytes(),
                                        8,
                                        2,
                                        (identity, waiting, error) -> {
                                            if (error != null) failures.add(identity + ":" + error);
                                            if (waiting) pending.add(identity);
                                        })) {
                            GlPaintPlanRenderer consumer = fonts.createPlanRenderer(resources);
                            try {
                                PaintPlan warm =
                                        new PaintPlanBuilder()
                                                .add(
                                                        image(
                                                                backup,
                                                                box,
                                                                ImageOptions.Fit.STRETCH,
                                                                null))
                                                .build();
                                clear();
                                try (GlPaintPlanRenderer.Prepared prepared =
                                        fonts.prepare(consumer, warm, new LayoutBox(0, 0, W, H))) {
                                    prepared.render(
                                            FramebufferCoordinates.orthographic(W, H), H, false);
                                }
                                capture();
                                color(15, 15, 0xFF2244AA);
                                PaintPlan plan =
                                        new PaintPlanBuilder()
                                                .add(
                                                        PaintCommand.image(
                                                                box,
                                                                null,
                                                                ImagePlacement.fit(
                                                                        primary,
                                                                        box,
                                                                        ImageOptions.defaults()
                                                                                .fit(
                                                                                        ImageOptions
                                                                                                .Fit
                                                                                                .STRETCH)),
                                                                warm,
                                                                warm,
                                                                null))
                                                .add(
                                                        PaintCommand.rect(
                                                                new LayoutBox(60, 10, 20, 20),
                                                                null,
                                                                0xFFFF8800))
                                                .build();
                                clear();
                                try (GlPaintPlanRenderer.Prepared prepared =
                                        fonts.prepare(consumer, plan, new LayoutBox(0, 0, W, H))) {
                                    prepared.render(
                                            FramebufferCoordinates.orthographic(W, H), H, false);
                                }
                                capture();
                                color(15, 15, 0xFF22AA55);
                                color(65, 15, 0xFFFF8800);
                                require(failures.isEmpty(), "备用驻留使主图失败或备用容量不足变成永久失败: " + failures);
                                require(
                                        pending.contains(backup.identity)
                                                && !pending.contains(primary.identity),
                                        "不足备用未按pending反馈或主图被备用阻挡");
                                require(
                                        resources.retainedBytes() == primary.bytes(),
                                        "备用阻挡主图或突破实际资源预算");
                            } finally {
                                fonts.releasePlanRenderer(consumer);
                            }
                        }
                    }
                });
        scenario(
                "HOST_ITEM缺adapterUnsupported与hostthrow局部failed后rect继续",
                () -> {
                    final String[] stage = {""};
                    final boolean[] unsupported = {false};
                    final int[] draws = {0};
                    try (GlImageResources resources =
                            new GlImageResources(
                                    new Lwjgl3GlApi(),
                                    2,
                                    1024,
                                    1024,
                                    2,
                                    new GlImageResources.Listener() {
                                        public void presentation(
                                                long identity, boolean pending, String failure) {}

                                        public void failure(
                                                long identity,
                                                String value,
                                                boolean missing,
                                                String diagnostic) {
                                            stage[0] = value;
                                            unsupported[0] = missing;
                                        }
                                    })) {
                        ImageContent host = new ImageContent(16, 16) {};
                        PaintPlanBuilder b = new PaintPlanBuilder();
                        b.add(
                                image(
                                        host,
                                        new LayoutBox(10, 10, 40, 40),
                                        ImageOptions.Fit.STRETCH,
                                        null));
                        b.add(PaintCommand.rect(new LayoutBox(60, 10, 30, 30), null, 0xff00ff00));
                        GlPaintPlanRenderer delegate = fonts.createPlanRenderer(resources);
                        try {
                            clear();
                            try (GlPaintPlanRenderer.Prepared prepared =
                                    fonts.prepare(delegate, b.build(), new LayoutBox(0, 0, W, H))) {
                                prepared.render(
                                        FramebufferCoordinates.orthographic(W, H), H, false);
                            }
                            capture();
                            color(15, 15, 0xff5500aa);
                            color(65, 15, 0xff00ff00);
                            require(
                                    unsupported[0] && stage[0].equals("item-adapter"),
                                    "缺host adapter未明确unsupported");
                            resources.hostItems(
                                    new GlImageResources.HostItems() {
                                        public boolean supports(ImageContent content) {
                                            return content == host;
                                        }

                                        public void render(
                                                club.heiqi.uilib.ui.scene.image.ImagePlacement p,
                                                LayoutBox c,
                                                int w,
                                                int h) {
                                            draws[0]++;
                                            throw new IllegalStateException("host render failed");
                                        }
                                    });
                            clear();
                            try (GlPaintPlanRenderer.Prepared prepared =
                                    fonts.prepare(delegate, b.build(), new LayoutBox(0, 0, W, H))) {
                                prepared.render(
                                        FramebufferCoordinates.orthographic(W, H), H, false);
                            }
                            capture();
                            color(15, 15, 0xff5500aa);
                            color(65, 15, 0xff00ff00);
                            require(
                                    !unsupported[0]
                                            && stage[0].equals("item-render")
                                            && draws[0] == 1,
                                    "host失败未局部failed stage或重复执行原图");
                        } finally {
                            fonts.releasePlanRenderer(delegate);
                        }
                    }
                });
        scenario(
                "不可用分支嵌套图像与文字在同次prepare固定资源",
                () -> {
                    ImageContent primary = solid(2, 2, 0xFF0000FF),
                            backup = solid(1, 1, 0x22AA55FF);
                    LayoutBox box = new LayoutBox(10, 10, 80, 40);
                    TextLayout label =
                            fonts.layout("备用文字", new TextStyle("default", 14, 0xFF101010), 100);
                    PaintPlan fallback =
                            new PaintPlanBuilder()
                                    .add(
                                            image(
                                                    backup,
                                                    box,
                                                    ImageOptions.Fit.STRETCH,
                                                    new LayoutBox(10, 10, 40, 40)))
                                    .add(
                                            PaintCommand.text(
                                                    new LayoutBox(100, 10, 100, 40), null, label))
                                    .build();
                    PaintPlan ready =
                            new PaintPlanBuilder()
                                    .add(
                                            PaintCommand.rect(
                                                    new LayoutBox(10, 10, 20, 20),
                                                    null,
                                                    0xFFFF0000))
                                    .build();
                    PaintPlan plan =
                            new PaintPlanBuilder()
                                    .add(
                                            PaintCommand.image(
                                                    box,
                                                    null,
                                                    ImagePlacement.fit(
                                                            primary,
                                                            box,
                                                            ImageOptions.defaults()
                                                                    .fit(ImageOptions.Fit.STRETCH)),
                                                    fallback,
                                                    fallback,
                                                    ready))
                                    .add(
                                            PaintCommand.rect(
                                                    new LayoutBox(210, 10, 20, 20),
                                                    null,
                                                    0xFF00FF00))
                                    .build();
                    GlImageResources tiny =
                            own(
                                    new GlImageResources(
                                            new Lwjgl3GlApi(),
                                            4,
                                            64,
                                            4,
                                            1,
                                            (identity, pending, error) -> {}));
                    GlPaintPlanRenderer consumer = fonts.createPlanRenderer(tiny);
                    onClose(() -> fonts.releasePlanRenderer(consumer));
                    boolean readyGlyph = false;
                    for (int i = 0; i < 100; i++) {
                        clear();
                        try (GlPaintPlanRenderer.Prepared prepared =
                                fonts.prepare(consumer, plan, new LayoutBox(0, 0, W, H))) {
                            require(prepared.demandCount() > 0, "备用文字未进入正式字形需求");
                            prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
                            readyGlyph = prepared.readyCount() == prepared.demandCount();
                        }
                        if (readyGlyph) break;
                        Thread.sleep(5);
                    }
                    capture();
                    color(15, 15, 0xFF22AA55);
                    color(55, 15, 0xFFFFFFFF);
                    color(215, 15, 0xFF00FF00);
                    require(readyGlyph, "备用文字资源未在同Frame完成");
                    int dark = 0;
                    for (int y = 10; y < 45; y++)
                        for (int x = 100; x < 200; x++)
                            if ((pixels[y * W + x] & 0xFFFFFF) < 0x606060) dark++;
                    require(dark > 10, "备用文字没有回放正式glyph");
                });
        scenario(
                "生产嵌套物品提示在全页内容后按父图像同帧分支投影",
                () -> {
                    club.heiqi.uilib.ui.reactive.ReactiveDomain domain =
                            club.heiqi.uilib.ui.reactive.ReactiveDomain.direct();
                    ImageContent marker = solid(1, 1, 0xFF0000FF),
                            primary = solid(1, 1, 0x0000FFFF);
                    club.heiqi.uilib.ui.scene.image.ItemImageFacts facts =
                            new club.heiqi.uilib.ui.scene.image.ItemImageFacts(
                                    "", "备用物品提示延伸到后续正文", false, 1, 0);
                    ImageContent item =
                            new ImageContent(16, 16) {
                                @Override
                                public club.heiqi.uilib.ui.scene.image.ItemImageFacts itemFacts() {
                                    return facts;
                                }
                            };
                    club.heiqi.uilib.ui.scene.image.ImageSource
                            markerSource =
                                    club.heiqi.uilib.ui.scene.image.ImageSource.resource(
                                            "test:overlay-marker"),
                            primarySource =
                                    club.heiqi.uilib.ui.scene.image.ImageSource.resource(
                                            "test:overlay-primary"),
                            itemSource =
                                    club.heiqi.uilib.ui.scene.image.ImageSource.resource(
                                            "test:overlay-item");
                    try (club.heiqi.uilib.ui.scene.image.SharedImageService service =
                            new club.heiqi.uilib.ui.scene.image.SharedImageService(
                                    source ->
                                            () ->
                                                    source == markerSource
                                                            ? marker
                                                            : source == primarySource
                                                                    ? primary
                                                                    : item,
                                    club.heiqi.uilib.ui.scene.image.SharedImageService.Budget
                                            .defaults())) {
                        SoftwareSceneScenario.SoftwareHost host =
                                new SoftwareSceneScenario.SoftwareHost();
                        host.width = W;
                        host.height = H;
                        club.heiqi.uilib.ui.scene.node.SceneNode[] parent = {null},
                                nested = {null},
                                body = {null},
                                markerNode = {null};
                        try (club.heiqi.uilib.ui.scene.host.ScenePageSession page =
                                club.heiqi.uilib.ui.scene.host.ScenePageSession.open(
                                        domain,
                                        host,
                                        fonts,
                                        c -> {
                                            markerNode[0] =
                                                    club.heiqi.uilib.ui.scene.control.SceneImage
                                                            .create(
                                                                    service,
                                                                    domain.signal(markerSource),
                                                                    domain.signal(
                                                                            ImageOptions.defaults()
                                                                                    .size(
                                                                                            club
                                                                                                    .heiqi
                                                                                                    .uilib
                                                                                                    .ui
                                                                                                    .scene
                                                                                                    .layout
                                                                                                    .Size
                                                                                                    .fixed(
                                                                                                            1),
                                                                                            club
                                                                                                    .heiqi
                                                                                                    .uilib
                                                                                                    .ui
                                                                                                    .scene
                                                                                                    .layout
                                                                                                    .Size
                                                                                                    .fixed(
                                                                                                            1))));
                                            c.mount(markerNode[0]);
                                            nested[0] =
                                                    club.heiqi.uilib.ui.scene.control.SceneImage
                                                            .themed(
                                                                    service,
                                                                    domain.signal(itemSource),
                                                                    domain.signal(
                                                                            ImageOptions.defaults()
                                                                                    .tooltip(true)
                                                                                    .size(
                                                                                            club
                                                                                                    .heiqi
                                                                                                    .uilib
                                                                                                    .ui
                                                                                                    .scene
                                                                                                    .layout
                                                                                                    .Size
                                                                                                    .fixed(
                                                                                                            40),
                                                                                            club
                                                                                                    .heiqi
                                                                                                    .uilib
                                                                                                    .ui
                                                                                                    .scene
                                                                                                    .layout
                                                                                                    .Size
                                                                                                    .fixed(
                                                                                                            20))),
                                                                    domain.signal(
                                                                            club.heiqi.uilib.ui
                                                                                    .scene.control
                                                                                    .SceneTheme
                                                                                    .dark()));
                                            parent[0] =
                                                    club.heiqi.uilib.ui.scene.control.SceneImage
                                                            .create(
                                                                    service,
                                                                    domain.signal(primarySource),
                                                                    domain.signal(
                                                                            ImageOptions.defaults()
                                                                                    .size(
                                                                                            club
                                                                                                    .heiqi
                                                                                                    .uilib
                                                                                                    .ui
                                                                                                    .scene
                                                                                                    .layout
                                                                                                    .Size
                                                                                                    .fixed(
                                                                                                            40),
                                                                                            club
                                                                                                    .heiqi
                                                                                                    .uilib
                                                                                                    .ui
                                                                                                    .scene
                                                                                                    .layout
                                                                                                    .Size
                                                                                                    .fixed(
                                                                                                            20))),
                                                                    nested[0]);
                                            c.mount(parent[0]);
                                            body[0] =
                                                    new club.heiqi.uilib.ui.scene.node.SceneNode()
                                                            .size(
                                                                    club.heiqi.uilib.ui.scene.layout
                                                                            .Size.fixed(120),
                                                                    club.heiqi.uilib.ui.scene.layout
                                                                            .Size.fixed(80))
                                                            .background(0xFFEE7700);
                                            c.mount(
                                                    new club.heiqi.uilib.ui.scene.node.SceneNode()
                                                            .padding(40)
                                                            .add(body[0]));
                                        })) {
                            boolean[] ready = {false};
                            for (int i = 0; i < 40; i++) {
                                service.drain();
                                page.frame();
                                domain.execute(
                                        () ->
                                                ready[0] =
                                                        club.heiqi.uilib.ui.scene.control.SceneImage
                                                                                .state(
                                                                                        markerNode[
                                                                                                0])
                                                                                .get()
                                                                                .content
                                                                        != null
                                                                && club.heiqi.uilib.ui.scene.control
                                                                                .SceneImage.state(
                                                                                        parent[0])
                                                                                .get()
                                                                                .content
                                                                        != null
                                                                && club.heiqi.uilib.ui.scene.control
                                                                                .SceneImage.state(
                                                                                        nested[0])
                                                                                .get()
                                                                                .content
                                                                        != null);
                                if (ready[0]) break;
                                Thread.sleep(5);
                            }
                            require(ready[0], "生产物品/主图CPU内容未ready");
                            service.presentation(primary.identity, true, null);
                            page.pointer(
                                    club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(10, 10));
                            page.frame();
                            PaintPlan plan = host.last;
                            PaintCommand owner =
                                    plan.commands().stream()
                                            .filter(
                                                    cmd ->
                                                            cmd.kind == PaintCommand.Kind.IMAGE
                                                                    && cmd.image.content == primary)
                                            .findFirst()
                                            .get();
                            PaintCommand projection =
                                    plan.commands().stream()
                                            .filter(
                                                    cmd ->
                                                            cmd.kind
                                                                            == PaintCommand.Kind
                                                                                    .IMAGE_BRANCH_OVERLAY
                                                                    && cmd.branchSource == owner)
                                            .findFirst()
                                            .get();
                            PaintCommand tip =
                                    projection.pendingPaint.commands().stream()
                                            .filter(
                                                    cmd ->
                                                            cmd.kind == PaintCommand.Kind.RECT
                                                                    && cmd.color
                                                                            == club.heiqi.uilib.ui
                                                                                    .scene.control
                                                                                    .SceneTheme
                                                                                    .dark()
                                                                                    .tooltip)
                                            .findFirst()
                                            .get();
                            require(
                                    owner.pendingPaint.commands().stream()
                                            .noneMatch(
                                                    cmd ->
                                                            cmd.kind == PaintCommand.Kind.RECT
                                                                    && cmd.color == tip.color),
                                    "物品tooltip仍封在父图像内容位置");
                            int bodyIndex = -1;
                            for (int i = 0; i < plan.commands().size(); i++)
                                if (plan.commands().get(i).color == 0xFFEE7700) bodyIndex = i;
                            require(
                                    bodyIndex >= 0
                                            && plan.commands().indexOf(projection) > bodyIndex,
                                    "备用overlay未保持全页内容后顺序");
                            LayoutBox[] bodyBox = {null};
                            domain.execute(
                                    () -> {
                                        bodyBox[0] =
                                                page.pipeline().current().geometry.get(body[0]).box;
                                        require(
                                                page.runtime()
                                                                .pointerRouter()
                                                                .passiveHovered(
                                                                        page.pipeline()
                                                                                .current()
                                                                                .geometry)
                                                        == nested[0],
                                                "正文遮挡范围误挡图标hover");
                                    });
                            LayoutBox overlap = tip.box.intersect(bodyBox[0]);
                            int sampleX = overlap.x + 1, sampleY = tip.box.y + tip.box.height - 2;
                            require(
                                    overlap.width > 2
                                            && sampleY >= overlap.y
                                            && sampleY < overlap.y + overlap.height,
                                    "提示没有实际延伸到后续正文像素");
                            int[] draws = {0};
                            try (GlImageResources resources =
                                    new GlImageResources(
                                            new Lwjgl3GlApi(),
                                            1,
                                            64,
                                            64,
                                            4,
                                            (identity, pending, error) -> {})) {
                                resources.hostItems(
                                        new GlImageResources.HostItems() {
                                            public boolean supports(ImageContent content) {
                                                return content == item;
                                            }

                                            public void render(
                                                    ImagePlacement image,
                                                    LayoutBox clip,
                                                    int width,
                                                    int height) {
                                                draws[0]++;
                                            }
                                        });
                                GlPaintPlanRenderer consumer = fonts.createPlanRenderer(resources);
                                try {
                                    clear();
                                    try (GlPaintPlanRenderer.Prepared prepared =
                                            fonts.prepare(
                                                    consumer, plan, new LayoutBox(0, 0, W, H))) {
                                        prepared.render(
                                                FramebufferCoordinates.orthographic(W, H),
                                                H,
                                                false);
                                    }
                                    capture();
                                    color(sampleX, sampleY, tip.color);
                                    require(draws[0] == 1, "overlay投影重复执行备用物品");
                                } finally {
                                    fonts.releasePlanRenderer(consumer);
                                }
                            }
                            // 同一冻结计划换成主图ready的Frame：后续正文应显露，不能按live uploading继续投影备用提示。
                            try (GlImageResources resources =
                                    new GlImageResources(
                                            new Lwjgl3GlApi(),
                                            4,
                                            64,
                                            64,
                                            4,
                                            (identity, pending, error) -> {})) {
                                resources.hostItems(
                                        new GlImageResources.HostItems() {
                                            public boolean supports(ImageContent content) {
                                                return content == item;
                                            }

                                            public void render(
                                                    ImagePlacement image,
                                                    LayoutBox clip,
                                                    int width,
                                                    int height) {
                                                draws[0]++;
                                            }
                                        });
                                GlPaintPlanRenderer consumer = fonts.createPlanRenderer(resources);
                                try {
                                    clear();
                                    try (GlPaintPlanRenderer.Prepared prepared =
                                            fonts.prepare(
                                                    consumer, plan, new LayoutBox(0, 0, W, H))) {
                                        prepared.render(
                                                FramebufferCoordinates.orthographic(W, H),
                                                H,
                                                false);
                                    }
                                    capture();
                                    color(sampleX, sampleY, 0xFFEE7700);
                                    require(draws[0] == 1, "ready仍执行备用原图或tooltip");
                                } finally {
                                    fonts.releasePlanRenderer(consumer);
                                }
                            }
                        }
                    }
                });
        scenario(
                "正式字体owner delegate连续consumer不污染冻结父Frame",
                () -> {
                    TextLayout text =
                            fonts.layout(
                                    "Parent W 后续", new TextStyle("default", 18, 0xff101010), W);
                    PaintPlanBuilder b = new PaintPlanBuilder();
                    b.add(PaintCommand.text(new LayoutBox(10, 100, 300, 50), null, text));
                    PaintPlan plan = b.build();
                    for (int i = 0; i < 100; i++) {
                        clear();
                        render(plan);
                        if (glyphsReady) break;
                        Thread.sleep(5);
                    }
                    GlPaintPlanRenderer nested = fonts.createPlanRenderer();
                    try {
                        clear();
                        try (GlPaintPlanRenderer.Prepared parent =
                                        fonts.prepare(renderer, plan, new LayoutBox(0, 0, W, H));
                                GlPaintPlanRenderer.Replay replay =
                                        parent.begin(
                                                FramebufferCoordinates.orthographic(W, H),
                                                H,
                                                false)) {
                            long resource =
                                    parent.resident(text.lines().get(0).glyphs().get(0)).resource;
                            replay.delegate(
                                    () ->
                                            fonts.withDelegatedConsumer(
                                                    () -> {
                                                        for (int i = 0; i < 2; i++)
                                                            try (GlPaintPlanRenderer.Prepared
                                                                    child =
                                                                            fonts.prepare(
                                                                                    nested,
                                                                                    plan,
                                                                                    new LayoutBox(
                                                                                            0, 0, W,
                                                                                            H))) {
                                                                child.render(
                                                                        FramebufferCoordinates
                                                                                .orthographic(W, H),
                                                                        H,
                                                                        false);
                                                            }
                                                    }));
                            require(
                                    parent.resident(text.lines().get(0).glyphs().get(0)).resource
                                            == resource,
                                    "nested consumer修改父resident");
                            for (PaintCommand command : plan.commands()) replay.draw(command);
                        }
                        capture();
                        int dark = 0;
                        for (int y = 100; y < 150; y++)
                            for (int x = 10; x < 300; x++)
                                if ((pixels[y * W + x] & 0xffffff) < 0x606060) dark++;
                        require(dark > 20, "委托恢复后父文字缺失");
                    } finally {
                        fonts.releasePlanRenderer(nested);
                    }
                });
        scenario("小字形需求预算优先保主正文和后续ready数量", () -> quantityGlyphPriority());
        scenario("备用字形先驻留时新主字形取得唯一图集容量", () -> cachedGlyphPriority());
        scenario("真实glyph父partial借用连续consumer容量与GPUpressure", () -> borrowPressure());
        require(glGetError() == GL_NO_ERROR, "图像GPU场景留下GLerror");
    }

    private void readyGlyphs(ClientFontServices owner, TextLayout... texts) throws Exception {
        java.util.Set<club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey> keys =
                new java.util.LinkedHashSet<>();
        for (TextLayout text : texts)
            for (TextLayout.Line line : text.lines())
                for (TextLayout.Glyph glyph : line.glyphs())
                    keys.add(club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey.of(glyph));
        require(!keys.isEmpty(), "真实文本没有字形");
        for (club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey key : keys)
            owner.rasterizer().request(key);
        for (int i = 0; i < 100; i++) {
            owner.pumpReady();
            boolean ready = true;
            for (club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey key : keys)
                ready &= owner.rasterizer().peek(key) != null;
            if (ready) return;
            Thread.sleep(5);
        }
        throw new AssertionError("真实字形worker未就绪");
    }

    private int darkPixels(LayoutBox box) {
        int dark = 0;
        for (int y = Math.max(0, box.y); y < Math.min(H, box.y + box.height); y++)
            for (int x = Math.max(0, box.x); x < Math.min(W, box.x + box.width); x++)
                if ((pixels[y * W + x] & 0xffffff) < 0x606060) dark++;
        return dark;
    }

    private void quantityGlyphPriority() throws Exception {
        ClientFontServices.Settings defaults = ClientFontServices.Settings.defaults();
        ClientFontServices.Settings bounded =
                new ClientFontServices.Settings(
                        defaults.cpu,
                        defaults.mode,
                        defaults.atlas,
                        new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.UploadBudget(
                                defaults.upload.bytes, 1000000000, defaults.upload.calls),
                        defaults.quadCapacity,
                        2);
        try (ClientFontServices owner =
                new ClientFontServices(
                        getClass().getClassLoader(),
                        1,
                        bounded,
                        IsolatedFontEngine.shared(),
                        new Lwjgl3GlApi(),
                        GlProgramProvider.NONE)) {
            club.heiqi.uilib.ui.reactive.ReactiveDomain domain =
                    club.heiqi.uilib.ui.reactive.ReactiveDomain.direct();
            ImageContent a = solid(1, 1, 0xFFFFFFFF),
                    b =
                            new ImageContent(1, 1, new byte[] {-1, -1, -1, -1}) {
                                @Override
                                public club.heiqi.uilib.ui.scene.image.ItemImageFacts itemFacts() {
                                    return new club.heiqi.uilib.ui.scene.image.ItemImageFacts(
                                            "7", "", false, 1, 0);
                                }
                            };
            club.heiqi.uilib.ui.scene.image.ImageSource
                    sa = club.heiqi.uilib.ui.scene.image.ImageSource.resource("test:glyph-a"),
                    sb = club.heiqi.uilib.ui.scene.image.ImageSource.resource("test:glyph-b");
            try (club.heiqi.uilib.ui.scene.image.SharedImageService service =
                            new club.heiqi.uilib.ui.scene.image.SharedImageService(
                                    source -> () -> source == sa ? a : b,
                                    club.heiqi.uilib.ui.scene.image.SharedImageService.Budget
                                            .defaults());
                    GlImageResources resources = new GlImageResources(new Lwjgl3GlApi(), 4, 64)) {
                SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
                host.width = W;
                host.height = H;
                club.heiqi.uilib.ui.scene.node.SceneNode[] an = {null},
                        bn = {null},
                        placeholder = {null};
                try (club.heiqi.uilib.ui.scene.host.ScenePageSession page =
                        club.heiqi.uilib.ui.scene.host.ScenePageSession.open(
                                domain,
                                host,
                                owner,
                                c -> {
                                    placeholder[0] =
                                            new club.heiqi.uilib.ui.scene.node.SceneNode()
                                                    .text("abcdefghijklmnopqrstu");
                                    an[0] =
                                            club.heiqi.uilib.ui.scene.control.SceneImage.create(
                                                    service,
                                                    domain.signal(sa),
                                                    domain.signal(
                                                            ImageOptions.defaults()
                                                                    .size(
                                                                            club.heiqi.uilib.ui
                                                                                    .scene.layout
                                                                                    .Size.fixed(80),
                                                                            club.heiqi.uilib.ui
                                                                                    .scene.layout
                                                                                    .Size.fixed(
                                                                                    40))),
                                                    placeholder[0]);
                                    bn[0] =
                                            club.heiqi.uilib.ui.scene.control.SceneImage.create(
                                                            service,
                                                            domain.signal(sb),
                                                            domain.signal(
                                                                    ImageOptions.defaults()
                                                                            .quantity(true)
                                                                            .size(
                                                                                    club.heiqi.uilib
                                                                                            .ui
                                                                                            .scene
                                                                                            .layout
                                                                                            .Size
                                                                                            .fixed(
                                                                                                    80),
                                                                                    club.heiqi.uilib
                                                                                            .ui
                                                                                            .scene
                                                                                            .layout
                                                                                            .Size
                                                                                            .fixed(
                                                                                                    40))))
                                                    .style(
                                                            new TextStyle(
                                                                    "default", 12, 0xff101010));
                                    c.mount(
                                            new club.heiqi.uilib.ui.scene.node.SceneNode()
                                                    .axis(
                                                            club.heiqi.uilib.ui.scene.node.SceneNode
                                                                    .Axis.ROW)
                                                    .add(
                                                            new club.heiqi.uilib.ui.scene.node
                                                                            .SceneNode()
                                                                    .text("W")
                                                                    .style(
                                                                            new TextStyle(
                                                                                    "default",
                                                                                    12,
                                                                                    0xff101010)))
                                                    .add(an[0])
                                                    .add(bn[0]));
                                })) {
                    boolean[] ready = {false};
                    for (int i = 0; i < 40; i++) {
                        service.drain();
                        page.frame();
                        domain.execute(
                                () ->
                                        ready[0] =
                                                club.heiqi.uilib.ui.scene.control.SceneImage.state(
                                                                                an[0])
                                                                        .get()
                                                                        .content
                                                                != null
                                                        && club.heiqi.uilib.ui.scene.control
                                                                        .SceneImage.state(bn[0])
                                                                        .get()
                                                                        .content
                                                                != null);
                        if (ready[0]) break;
                        Thread.sleep(5);
                    }
                    require(ready[0], "正式图像内容未ready");
                    domain.execute(
                            () ->
                                    require(
                                            page.pipeline().current().geometry.get(placeholder[0])
                                                    == null,
                                            "备用文字未隐藏"));
                    PaintPlan plan = host.last;
                    PaintCommand main =
                            plan.commands().stream()
                                    .filter(
                                            cmd ->
                                                    cmd.text != null
                                                            && cmd.text.logicalText.equals("W"))
                                    .findFirst()
                                    .get();
                    PaintCommand image =
                            plan.commands().stream()
                                    .filter(
                                            cmd ->
                                                    cmd.kind == PaintCommand.Kind.IMAGE
                                                            && cmd.image.content == b)
                                    .findFirst()
                                    .get();
                    PaintCommand quantity =
                            image.readyOverlay.commands().stream()
                                    .filter(
                                            cmd ->
                                                    cmd.text != null
                                                            && cmd.text.logicalText.equals("7"))
                                    .findFirst()
                                    .get();
                    require(!quantity.text.lines().get(0).glyphs().isEmpty(), "真实数量未产生glyph");
                    require(
                            main.text.style.color == 0xff101010
                                    && quantity.text.style.color == 0xff101010,
                            "像素反例必须声明与白背景有对比的实际字形颜色");
                    readyGlyphs(owner, main.text, quantity.text);
                    GlPaintPlanRenderer consumer = owner.createPlanRenderer(resources);
                    try {
                        // 先让成功数量成为resident，随后仍须在本Frame需求中保留，不能靠缓存侥幸画出。
                        PaintPlan warm = new PaintPlanBuilder().add(quantity).build();
                        for (int i = 0; i < 100; i++) {
                            boolean done;
                            try (GlPaintPlanRenderer.Prepared prepared =
                                    owner.prepare(consumer, warm, new LayoutBox(0, 0, W, H))) {
                                done = prepared.readyCount() == prepared.demandCount();
                            }
                            if (done) break;
                            Thread.sleep(5);
                        }
                        clear();
                        try (GlPaintPlanRenderer.Prepared prepared =
                                owner.prepare(consumer, plan, new LayoutBox(0, 0, W, H))) {
                            require(prepared.demandCount() == 2, "主正文与数量需求未尊重小预算");
                            for (TextLayout.Line line : quantity.text.lines())
                                for (TextLayout.Glyph glyph : line.glyphs())
                                    require(prepared.resident(glyph) != null, "隐藏备用文字挤掉已驻留的成功数量需求");
                            for (TextLayout.Line line : main.text.lines())
                                for (TextLayout.Glyph glyph : line.glyphs())
                                    require(prepared.resident(glyph) != null, "主正文被分支挤掉");
                            prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
                        }
                        capture();
                        // 白背景上分别验证显式深色字形；测量区域遵守该命令实际clip，不能白字白底误判留白。
                        LayoutBox viewport = new LayoutBox(0, 0, W, H),
                                mainRegion =
                                        main.box
                                                .intersect(main.clip == null ? viewport : main.clip)
                                                .intersect(viewport),
                                quantityRegion =
                                        quantity.box
                                                .intersect(
                                                        quantity.clip == null
                                                                ? viewport
                                                                : quantity.clip)
                                                .intersect(viewport);
                        int mainInk = darkPixels(mainRegion),
                                quantityInk = darkPixels(quantityRegion);
                        require(
                                mainRegion.width > 0 && mainRegion.height > 0 && mainInk > 0,
                                "主正文无真实墨迹: ink="
                                        + mainInk
                                        + " region="
                                        + mainRegion.x
                                        + ","
                                        + mainRegion.y
                                        + ","
                                        + mainRegion.width
                                        + ","
                                        + mainRegion.height);
                        require(
                                quantityRegion.width > 0
                                        && quantityRegion.height > 0
                                        && quantityInk > 0,
                                "成功数量无真实墨迹: ink="
                                        + quantityInk
                                        + " region="
                                        + quantityRegion.x
                                        + ","
                                        + quantityRegion.y
                                        + ","
                                        + quantityRegion.width
                                        + ","
                                        + quantityRegion.height);
                        require(
                                consumer.demandOverflow() > 0 && consumer.blankGlyphs() == 0,
                                "未制造备用crowd或主回放仍有留白glyph");
                    } finally {
                        owner.releasePlanRenderer(consumer);
                    }
                }
            }
        }
    }

    /** 主图集的单色为R8覆盖率、彩色为RGBA；预算按实际格式计字节，不能沿用CPU RGBA载荷。 */
    private static long premultRowBytes(
            club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap bitmap) {
        return (bitmap.color ? 4L : 1L) * (bitmap.width + 2);
    }

    private void cachedGlyphPriority() throws Exception {
        TextLayout primary = fonts.layout("Y", new TextStyle("default", 24, 0xff101010), 100),
                backup = fonts.layout("X", new TextStyle("default", 24, 0xff101010), 100);
        readyGlyphs(fonts, primary, backup);
        LayoutBox box = new LayoutBox(10, 10, 80, 40);
        PaintCommand y = PaintCommand.text(box, null, primary),
                x = PaintCommand.text(box, null, backup);
        ImageContent image = solid(1, 1, 0xFFFFFFFF);
        PaintPlan hidden = new PaintPlanBuilder().add(x).build();
        PaintPlan plan =
                new PaintPlanBuilder()
                        .add(PaintCommand.text(new LayoutBox(W + 10, 10, 80, 40), null, backup))
                        .add(y)
                        .add(
                                PaintCommand.image(
                                        new LayoutBox(120, 10, 40, 40),
                                        null,
                                        ImagePlacement.fit(
                                                image,
                                                new LayoutBox(120, 10, 40, 40),
                                                ImageOptions.defaults()),
                                        hidden,
                                        hidden,
                                        null))
                        .build();
        try (club.heiqi.uilib.host.client.gl.GlGlyphAtlas atlas =
                        new club.heiqi.uilib.host.client.gl.GlGlyphAtlas(
                                new Lwjgl3GlApi(),
                                club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Mode.TEXTURE_2D,
                                new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Budget(
                                        128, 1, 2, 1, 2, 128L * 128 * 4 * 2));
                GlImageResources resources = new GlImageResources(new Lwjgl3GlApi(), 2, 64);
                GlPaintPlanRenderer consumer =
                        new GlPaintPlanRenderer(
                                new Lwjgl3GlApi(),
                                fonts.rasterizer(),
                                atlas,
                                128,
                                2,
                                GlProgramProvider.NONE,
                                resources)) {
            club.heiqi.uilib.host.client.gl.GlGlyphAtlas.UploadBudget upload =
                    new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.UploadBudget(
                            65536, 1000000000, 8);
            clear();
            try (GlPaintPlanRenderer.Prepared prepared =
                    consumer.prepare(
                            new PaintPlanBuilder().add(x).build(),
                            new LayoutBox(0, 0, W, H),
                            upload)) {
                require(prepared.readyCount() == 1, "备用X未先驻留");
                prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
            }
            capture();
            require(darkPixels(box) > 0, "warm X未产生真实墨迹");
            clear();
            try (GlPaintPlanRenderer.Prepared prepared =
                    consumer.prepare(plan, new LayoutBox(0, 0, W, H), upload)) {
                require(prepared.demandCount() == 2, "未同时admit主Y和备用X");
                for (TextLayout.Line line : primary.lines())
                    for (TextLayout.Glyph glyph : line.glyphs())
                        require(prepared.resident(glyph) != null, "备用缓存先pin阻挡新主Y");
                for (TextLayout.Line line : backup.lines())
                    for (TextLayout.Glyph glyph : line.glyphs())
                        require(prepared.resident(glyph) == null, "唯一resident容量被突破或未让位主Y");
                prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
            }
            capture();
            require(darkPixels(box) > 0 && consumer.blankGlyphs() == 0, "主Y缺真实墨迹或仍blank");
            require(atlas.stats().residents == 1, "唯一字形resident预算被突破");
        }
        TextLayout largeBackup = fonts.layout("X", new TextStyle("default", 48, 0xff101010), 100),
                smallPrimary = fonts.layout(".", new TextStyle("default", 8, 0xff101010), 100);
        readyGlyphs(fonts, largeBackup, smallPrimary);
        club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey
                largeKey =
                        club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey.of(
                                largeBackup.lines().get(0).glyphs().get(0)),
                smallKey =
                        club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey.of(
                                smallPrimary.lines().get(0).glyphs().get(0));
        club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap
                largeBitmap = fonts.rasterizer().peek(largeKey),
                smallBitmap = fonts.rasterizer().peek(smallKey);
        long largeRow = premultRowBytes(largeBitmap),
                smallBytes = premultRowBytes(smallBitmap) * (smallBitmap.height + 2);
        require(smallBytes < largeRow, "未制造备用行无法完成但主字形可以完成的真实预算");
        try (club.heiqi.uilib.host.client.gl.GlGlyphAtlas atlas =
                        new club.heiqi.uilib.host.client.gl.GlGlyphAtlas(
                                new Lwjgl3GlApi(),
                                club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Mode.TEXTURE_2D,
                                new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Budget(
                                        128, 1, 2, 1, 2, 128L * 128 * 4 * 2));
                GlImageResources resources = new GlImageResources(new Lwjgl3GlApi(), 2, 64);
                GlPaintPlanRenderer consumer =
                        new GlPaintPlanRenderer(
                                new Lwjgl3GlApi(),
                                fonts.rasterizer(),
                                atlas,
                                128,
                                2,
                                GlProgramProvider.NONE,
                                resources)) {
            try (club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Frame warm =
                    atlas.beginFrame(java.util.Collections.singletonList(largeKey))) {
                warm.prepare(
                        fonts.rasterizer(),
                        new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.UploadBudget(
                                largeRow, 1000000000, 1));
                warm.freeze();
                require(
                        warm.lookup(largeKey) == null && atlas.stats().uploadedBytes == largeRow,
                        "备用大X未命中真实partial");
            }
            PaintPlan fallback =
                    new PaintPlanBuilder().add(PaintCommand.text(box, null, largeBackup)).build();
            PaintPlan pendingPlan =
                    new PaintPlanBuilder()
                            .add(PaintCommand.text(box, null, smallPrimary))
                            .add(
                                    PaintCommand.image(
                                            new LayoutBox(120, 10, 40, 40),
                                            null,
                                            ImagePlacement.fit(
                                                    image,
                                                    new LayoutBox(120, 10, 40, 40),
                                                    ImageOptions.defaults()),
                                            fallback,
                                            fallback,
                                            null))
                            .build();
            int alphaMax = 0;
            byte[] actual = smallBitmap.pixels();
            for (int i = 3; i < actual.length; i += 4)
                alphaMax = Math.max(alphaMax, actual[i] & 255);
            require(alphaMax > 0, "小主字形CPU位图没有实际coverage");
            clear();
            capture();
            int[] baseline = pixels.clone();
            LayoutBox inkRegion;
            try (GlPaintPlanRenderer.Prepared prepared =
                    consumer.prepare(
                            pendingPlan,
                            new LayoutBox(0, 0, W, H),
                            new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.UploadBudget(
                                    smallBytes, 1000000000, 1))) {
                TextLayout.Glyph glyph = smallPrimary.lines().get(0).glyphs().get(0);
                club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Resident resident =
                        prepared.resident(glyph);
                require(resident != null, "已降备用的旧partial长期阻挡新主字形");
                float ratio = glyph.rasterToLayout,
                        left = box.x + glyph.x + resident.bearingX * ratio,
                        top = box.y + glyph.y - resident.bearingY * ratio;
                int x0 = (int) Math.floor(left),
                        y0 = (int) Math.floor(top),
                        x1 = (int) Math.ceil(left + resident.width * resident.pixelScale * ratio),
                        y1 = (int) Math.ceil(top + resident.height * resident.pixelScale * ratio);
                inkRegion =
                        new LayoutBox(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0))
                                .intersect(new LayoutBox(0, 0, W, H));
                prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
            }
            capture();
            // 小字号句点的抗锯齿可仅产生浅灰像素；验证实际墨迹quad相对清屏基线的变化，不要求深于任意阈值。
            int changed = 0, minChannel = 255;
            for (int py = inkRegion.y; py < inkRegion.y + inkRegion.height; py++)
                for (int px = inkRegion.x; px < inkRegion.x + inkRegion.width; px++) {
                    int index = py * W + px, argb = pixels[index];
                    if ((argb & 0xffffff) != (baseline[index] & 0xffffff)) changed++;
                    minChannel =
                            Math.min(
                                    minChannel,
                                    Math.min(
                                            (argb >>> 16) & 255,
                                            Math.min((argb >>> 8) & 255, argb & 255)));
                }
            System.out.println(
                    "smallPrimary bitmap="
                            + smallBitmap.width
                            + "x"
                            + smallBitmap.height
                            + " alphaMax="
                            + alphaMax
                            + " bytes="
                            + smallBytes
                            + " backupRow="
                            + largeRow
                            + " region="
                            + inkRegion.x
                            + ","
                            + inkRegion.y
                            + ","
                            + inkRegion.width
                            + ","
                            + inkRegion.height
                            + " changed="
                            + changed
                            + " minChannel="
                            + minChannel
                            + " dark="
                            + darkPixels(box));
            require(
                    inkRegion.width > 0
                            && inkRegion.height > 0
                            && changed > 0
                            && consumer.blankGlyphs() == 0,
                    "小主字形未产生真实GPU覆盖: changed="
                            + changed
                            + " alphaMax="
                            + alphaMax
                            + " minChannel="
                            + minChannel);
        }
        // 多层prepare仍只花一次预算：主Y占满上传字节/调用额，备用X不能从下一层重新获取额度。
        club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey
                yKey =
                        club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey.of(
                                primary.lines().get(0).glyphs().get(0)),
                xKey =
                        club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey.of(
                                backup.lines().get(0).glyphs().get(0));
        club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap bitmap =
                fonts.rasterizer().peek(yKey);
        long firstBytes = premultRowBytes(bitmap) * (bitmap.height + 2);
        try (club.heiqi.uilib.host.client.gl.GlGlyphAtlas atlas =
                        new club.heiqi.uilib.host.client.gl.GlGlyphAtlas(
                                new Lwjgl3GlApi(),
                                club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Mode.TEXTURE_2D,
                                new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Budget(
                                        128, 1, 2, 2, 2, 128L * 128 * 4 * 2));
                club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Frame frame =
                        atlas.beginFrameLayers(
                                java.util.Arrays.asList(
                                        java.util.Collections.singletonList(yKey),
                                        java.util.Collections.singletonList(xKey)),
                                false,
                                club.heiqi.uilib.host.client.gl.GlGlyphAtlas.SamplingView
                                        .PREMULT_RGBA,
                                false)) {
            frame.prepare(
                    fonts.rasterizer(),
                    new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.UploadBudget(
                            firstBytes, 1000000000, 1));
            frame.freeze();
            System.out.println("LAYER_BUDGET expectedBytes=" + firstBytes
                    + " actualBytes=" + atlas.stats().uploadedBytes + " calls=" + atlas.stats().uploadCalls
                    + " primaryReady=" + (frame.lookup(yKey) != null)
                    + " backupReady=" + (frame.lookup(xKey) != null)
                    + " color=" + bitmap.color + " bitmap=" + bitmap.width + "x" + bitmap.height);
            require(
                    frame.lookup(yKey) != null
                            && frame.lookup(xKey) == null
                            && atlas.stats().uploadedBytes == firstBytes
                            && atlas.stats().uploadCalls == 1,
                    "备用层重置了单Frame上传预算");
        }
    }

    private void borrowPressure() throws Exception {
        TextLayout large = fonts.layout("WMQ", new TextStyle("default", 48, 0xff101010), W),
                small = fonts.layout(".i", new TextStyle("default", 8, 0xff101010), W);
        java.util.List<club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey>
                big = new java.util.ArrayList<>(),
                little = new java.util.ArrayList<>();
        for (TextLayout.Line line : large.lines())
            for (TextLayout.Glyph glyph : line.glyphs())
                big.add(club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey.of(glyph));
        for (TextLayout.Line line : small.lines())
            for (TextLayout.Glyph glyph : line.glyphs())
                little.add(club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey.of(glyph));
        java.util.List<club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey> all =
                new java.util.ArrayList<>(big);
        all.addAll(little);
        for (club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey key : all)
            fonts.rasterizer().request(key);
        for (int i = 0; i < 100; i++) {
            fonts.pumpReady();
            boolean ready = true;
            for (club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey key : all)
                ready &= fonts.rasterizer().peek(key) != null;
            if (ready) break;
            Thread.sleep(5);
        }
        int page = 4;
        for (club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey key : all) {
            club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap bitmap =
                    fonts.rasterizer().peek(key);
            require(bitmap != null, "真实压力glyph未完成");
            page = Math.max(page, Math.max(bitmap.width + 2, bitmap.height + 2));
        }
        club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap parentBitmap =
                fonts.rasterizer().peek(big.get(0));
        long oneRow = premultRowBytes(parentBitmap);
        for (boolean capacity : new boolean[] {true, false}) {
            java.util.List<club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey> children =
                    capacity ? little : big.subList(1, 3);
            try (club.heiqi.uilib.host.client.gl.GlGlyphAtlas tiny =
                    new club.heiqi.uilib.host.client.gl.GlGlyphAtlas(
                            new Lwjgl3GlApi(),
                            club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Mode.TEXTURE_2D,
                            new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Budget(
                                    page,
                                    1,
                                    capacity ? 2 : 1,
                                    capacity ? 1 : 3,
                                    8,
                                    2L * page * page * 4))) {
                try (club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Frame parent =
                        tiny.beginFrame(java.util.Collections.singletonList(big.get(0)))) {
                    parent.prepare(
                            fonts.rasterizer(),
                            new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.UploadBudget(
                                    oneRow, 1000000000, 1));
                    parent.freeze();
                    require(
                            parent.lookup(big.get(0)) == null
                                    && tiny.stats().uploadedBytes == oneRow,
                            "父partial场景未命中分行上传");
                    tiny.borrowFrozenFrame(
                            () -> {
                                for (club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey key :
                                        children)
                                    try (club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Frame child =
                                            tiny.beginFrame(
                                                    java.util.Collections.singletonList(key))) {
                                        child.prepare(
                                                fonts.rasterizer(),
                                                new club.heiqi.uilib.host.client.gl.GlGlyphAtlas
                                                        .UploadBudget(65536, 1000000000, 8));
                                        child.freeze();
                                        require(
                                                child.lookup(key) == null,
                                                "nested consumer突破父partial预算保护");
                                    }
                            });
                    require(
                            tiny.stats().evictedGroups == 0 && tiny.stats().residents == 0,
                            "借用期间父partial被淘汰/容量突破");
                }
                try (club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Frame resumed =
                        tiny.beginFrame(java.util.Collections.singletonList(big.get(0)))) {
                    resumed.prepare(
                            fonts.rasterizer(),
                            new club.heiqi.uilib.host.client.gl.GlGlyphAtlas.UploadBudget(
                                    65536, 1000000000, 8));
                    resumed.freeze();
                    club.heiqi.uilib.host.client.gl.GlGlyphAtlas.Resident resident =
                            resumed.lookup(big.get(0));
                    require(
                            resident != null
                                    && glIsTexture(
                                            tiny.resolve(resident.resource, resident.generation).id)
                                    && tiny.stats().residents == 1,
                            "借用后父partial未完成/悬空texture");
                }
                System.out.println(
                        "borrowPressure capacity="
                                + capacity
                                + " realParent="
                                + parentBitmap.width
                                + "x"
                                + parentBitmap.height
                                + " page="
                                + page
                                + " rowBytes="
                                + oneRow
                                + " evicted="
                                + tiny.stats().evictedGroups
                                + " residents="
                                + tiny.stats().residents
                                + " rejected="
                                + tiny.stats().rejected);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new ImageGlScenario().run(args);
    }
}
