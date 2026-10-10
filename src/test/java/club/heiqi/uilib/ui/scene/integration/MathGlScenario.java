package club.heiqi.uilib.ui.scene.integration;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.host.client.image.ClientImageServices;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneMath;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

/** 隐藏真实GL上下文：正式ClientFontServices wrapper测量到同PaintPlan的nativeglyph/图像/rule像素。 */
public final class MathGlScenario extends TestApplication {
    private static final int W = 640, H = 320;
    private long window, serial;
    private GLFWErrorCallback errors;
    private ClientFontServices fonts;
    private ClientImageServices images;
    private GlPaintPlanRenderer renderer;
    private final ReactiveDomain domain = ReactiveDomain.direct();
    private ScenePageSession page;
    private Signal<MathContent> content;
    private Signal<SceneTheme> theme;
    private Signal<ImageOptions> imageOptions;
    private SceneNode math, image, healthy, hiddenMath, hiddenImage;
    private PaintPlan plan;
    private int ready, demand;
    private Path output;
    private BufferedImage baseline;
    private final StringBuilder evidence = new StringBuilder();

    private static String tex(String source) {
        return source.replace('@', (char) 92);
    }

    private static final String FORMULA = tex("@frac{x}{2}+@embed{ore}");

    public static void main(String[] args) throws Exception {
        new MathGlScenario().run(args);
    }

    @Override
    protected void setup() throws Exception {
        output =
                arguments().length == 0
                        ? Paths.get("build/math-embed/gpu")
                        : Paths.get(arguments()[0]);
        Files.createDirectories(output);
        errors = GLFWErrorCallback.createPrint(System.err);
        errors.set();
        own(
                () -> {
                    if (window != 0) {
                        glfwMakeContextCurrent(0);
                        glfwDestroyWindow(window);
                        window = 0;
                    }
                    GL.setCapabilities(null);
                    glfwTerminate();
                    glfwSetErrorCallback(null);
                    if (errors != null) errors.free();
                });
        require(glfwInit(), "GLFW初始化失败");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(W, H, "MathGlScenario", 0, 0);
        require(window != 0, "真实GL上下文创建失败");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glViewport(0, 0, W, H);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_SCISSOR_TEST);
        evidence.append("vendor=")
                .append(glGetString(GL_VENDOR))
                .append(" renderer=")
                .append(glGetString(GL_RENDERER))
                .append(" version=")
                .append(glGetString(GL_VERSION))
                .append((char) 10);
        GlApi gl = new Lwjgl3GlApi();
        fonts =
                own(
                        new ClientFontServices(
                                MathGlScenario.class.getClassLoader(),
                                1,
                                ClientFontServices.Settings.defaults(),
                                new LwjglFontEngine(),
                                gl,
                                GlProgramProvider.NONE));
        images =
                own(
                        new ClientImageServices(
                                gl,
                                source -> () -> source.bitmap,
                                SharedImageService.Budget.defaults()));
        renderer = fonts.createPlanRenderer(images.textures());
        own(() -> fonts.releasePlanRenderer(renderer));
        SceneHost host =
                new SceneHost() {
                    public int width() {
                        return W;
                    }

                    public int height() {
                        return H;
                    }

                    public long metricsRevision() {
                        return fonts.fonts().generation() ^ fonts.readyVersion();
                    }

                    public void prepareResources() {
                        images.prepareResources();
                    }

                    public void replay(PaintPlan paint) {
                        plan = paint;
                        fonts.beginPresentation(++serial);
                        try (GlPaintPlanRenderer.Prepared prepared =
                                fonts.prepare(renderer, paint, new LayoutBox(0, 0, W, H))) {
                            ready = prepared.readyCount();
                            demand = prepared.demandCount();
                            prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
                        } finally {
                            fonts.endPresentation();
                        }
                    }
                };
        // 将wrapper本身交给正式session/pipeline，验证宿主包装与BatchTextMeasurer的mathFont代理。
        page =
                own(
                        ScenePageSession.open(
                                domain,
                                host,
                                fonts,
                                ctx -> {
                                    content = domain.signal(MathContent.parse(FORMULA));
                                    theme =
                                            domain.signal(
                                                    SceneTheme.dark()
                                                            .withTextStyle(
                                                                    new TextStyle(
                                                                            "default",
                                                                            37.5f,
                                                                            0xffffffff,
                                                                            null,
                                                                            club.heiqi.uilib.ui
                                                                                    .scene.text
                                                                                    .TextEffects
                                                                                    .NORMAL,
                                                                            club.heiqi.uilib.ui
                                                                                    .scene.text
                                                                                    .TextDecoration
                                                                                    .NONE)));
                                    Signal<ImageSource> source =
                                            domain.signal(ImageSource.bitmap(red()));
                                    imageOptions = domain.signal(ImageOptions.defaults());
                                    image =
                                            SceneImage.themed(
                                                    images.images(), source, imageOptions, theme);
                                    math =
                                            SceneMath.create(
                                                    ctx,
                                                    content,
                                                    theme,
                                                    MathStyle.DISPLAY,
                                                    Collections.singletonMap(
                                                            "ore",
                                                            new SceneMath.Embed(
                                                                    image,
                                                                    new MathEmbedMetrics(
                                                                            1, 1, 0.8f))));
                                    healthy =
                                            SceneMath.create(
                                                    ctx,
                                                    domain.signal(
                                                            MathContent.parse(
                                                                    tex(
                                                                            "@sum@limits_{n=1}^{5}x_n+@sqrt{1+x^2}"))),
                                                    theme,
                                                    MathStyle.DISPLAY,
                                                    Collections.emptyMap());
                                    hiddenImage =
                                            SceneImage.themed(
                                                    images.images(),
                                                    source,
                                                    domain.signal(ImageOptions.defaults()),
                                                    theme);
                                    hiddenMath =
                                            SceneMath.create(
                                                    ctx,
                                                    domain.signal(
                                                            MathContent.parse(
                                                                    tex("@embed{hidden}"))),
                                                    theme,
                                                    MathStyle.DISPLAY,
                                                    Collections.singletonMap(
                                                            "hidden",
                                                            new SceneMath.Embed(
                                                                    hiddenImage,
                                                                    new MathEmbedMetrics(
                                                                            1, 1, 0.8f))));
                                    ctx.mount(
                                            new SceneNode()
                                                    .size(Size.fill(1), Size.fill(1))
                                                    .padding(14)
                                                    .gap(20)
                                                    .add(math)
                                                    .add(healthy)
                                                    .add(
                                                            new SceneNode()
                                                                    .size(
                                                                            Size.fixed(0),
                                                                            Size.fixed(42))
                                                                    .clip(true)
                                                                    .add(hiddenMath)));
                                }));
        clear();
        baseline = read();
        save("baseline.png", baseline);
    }

    private static final club.heiqi.uilib.ui.scene.image.ItemImageFacts FACTS =
            new club.heiqi.uilib.ui.scene.image.ItemImageFacts("32", "", false, 1, 0xffffffff);

    private static ImageContent red() {
        byte[] rgba = new byte[16 * 16 * 4];
        for (int i = 0; i < rgba.length; i += 4) {
            rgba[i] = (byte) 255;
            rgba[i + 1] = 30;
            rgba[i + 2] = 60;
            rgba[i + 3] = (byte) 255;
        }
        return new ImageContent(16, 16, rgba) {
            @Override
            public club.heiqi.uilib.ui.scene.image.ItemImageFacts itemFacts() {
                return FACTS;
            }
        };
    }

    @Override
    protected void runTests() throws Exception {
        scenario(
                "正式mathFontwrapper与真实nativeglyph image rule像素",
                () -> {
                    BufferedImage rendered = renderReady();
                    save("formula.png", rendered);
                    LayoutBox[] regions = new LayoutBox[4];
                    domain.execute(
                            () -> {
                                MathLayout layout = SceneMath.layout(math);
                                require(
                                        !layout.fallback,
                                        "生产ClientFontServices数学包装断路: "
                                                + layout.diagnostics.stream()
                                                        .map(d -> d.message)
                                                        .collect(
                                                                java.util.stream.Collectors.joining(
                                                                        ";")));
                                require(layout.diagnostics.isEmpty(), "有效公式有诊断");
                                require(
                                        !SceneMath.layout(healthy).fallback,
                                        "sumlimits/root真实GPU公式发生降级");
                                require(
                                        layout.pieces.stream().anyMatch(p -> p.y < 0),
                                        "没有覆盖font baseline负y");
                                LayoutBox box = page.pipeline().current().geometry.get(math).box;
                                MathLayout.TextPiece numerator =
                                        layout.pieces.stream()
                                                .filter(
                                                        p ->
                                                                "x"
                                                                        .equals(
                                                                                FORMULA.substring(
                                                                                        p.sourceStart,
                                                                                        p.sourceEnd)))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "未找到分子源范围: "
                                                                                + layout
                                                                                        .pieces
                                                                                        .stream()
                                                                                        .map(
                                                                                                p ->
                                                                                                        p.sourceStart
                                                                                                                + ":"
                                                                                                                + p.sourceEnd
                                                                                                                + "="
                                                                                                                + FORMULA
                                                                                                                        .substring(
                                                                                                                                p.sourceStart,
                                                                                                                                p.sourceEnd))
                                                                                        .collect(
                                                                                                java
                                                                                                        .util
                                                                                                        .stream
                                                                                                        .Collectors
                                                                                                        .joining(
                                                                                                                ";"))));
                                TextLayout.Glyph glyph =
                                        numerator.text.lines().get(0).glyphs().get(0);
                                require(
                                        glyph.source != null && glyph.inkBounds.known,
                                        "未取得真实nativeglyph墨迹facts");
                                regions[0] =
                                        bounds(
                                                box.x
                                                        + numerator.x
                                                        + glyph.x
                                                        + glyph.inkBounds.left,
                                                box.y + numerator.y + glyph.y + glyph.inkBounds.top,
                                                glyph.inkBounds.right - glyph.inkBounds.left,
                                                glyph.inkBounds.bottom - glyph.inkBounds.top);
                                LayoutBox target =
                                        page.pipeline().current().geometry.get(image).box;
                                regions[1] =
                                        new LayoutBox(
                                                target.x + 4,
                                                target.y + 4,
                                                target.width - 8,
                                                target.height - 8);
                                MathLayout.Rule rule = layout.rules.get(0);
                                regions[2] =
                                        bounds(
                                                box.x + rule.x,
                                                box.y + rule.y,
                                                rule.width,
                                                rule.height);
                                regions[3] =
                                        page.pipeline().current().geometry.get(hiddenImage).box;
                                LayoutBox hiddenClip =
                                        page.pipeline().current().geometry.get(hiddenImage).clip;
                                require(regions[3].intersect(hiddenClip).width == 0, "隐藏图像并未实际裁剪");
                                // 这些区域由同代几何给出且上下行分离，不能由其它glyph/图像冒充命中。
                                require(
                                        !overlap(regions[0], regions[1])
                                                && !overlap(regions[0], regions[2]),
                                        "glyph证据区被image/rule污染");
                                require(
                                        plan.commands().stream()
                                                        .anyMatch(
                                                                c ->
                                                                        c.kind
                                                                                == PaintCommand.Kind
                                                                                        .IMAGE)
                                                && plan.commands().stream()
                                                        .anyMatch(
                                                                c ->
                                                                        c.kind
                                                                                == PaintCommand.Kind
                                                                                        .RECT)
                                                && plan.commands().stream()
                                                        .anyMatch(
                                                                c ->
                                                                        c.kind
                                                                                == PaintCommand.Kind
                                                                                        .TEXT),
                                        "未共用同PaintPlan");
                            });
                    changed("nativeglyph", regions[0], rendered, true);
                    changed("rgba-image", regions[1], rendered, true);
                    changed("fraction-rule", regions[2], rendered, true);
                    changed("clipped-image", regions[3], rendered, false);
                    require(glGetError() == GL_NO_ERROR, "真实数学GL错误");
                });
        scenario(
                "数量32真实GPU像素随积分分子和脚本图像缩放",
                () -> {
                    domain.execute(
                            () ->
                                    theme.set(
                                            SceneTheme.dark()
                                                    .withTextStyle(
                                                            new TextStyle(
                                                                    "default", 28, 0xffffffff))));
                    for (String formula :
                            new String[] {
                                tex("@int_0^1 @frac{@embed{ore}}{1+x^2}@,@mathrm{d}x"),
                                tex("x^{@embed{ore}}")
                            }) {
                        domain.execute(
                                () -> {
                                    content.set(MathContent.parse(formula));
                                    imageOptions.set(ImageOptions.defaults());
                                });
                        BufferedImage without = renderReady();
                        String label = formula.startsWith(tex("@int")) ? "integral" : "script";
                        save("quantity-" + label + "-without.png", without);
                        domain.execute(
                                () -> imageOptions.set(ImageOptions.defaults().quantity(true)));
                        BufferedImage with = renderReady();
                        save("quantity-" + label + ".png", with);
                        final LayoutBox[] inkBox = {null};
                        domain.execute(
                                () -> {
                                    PaintCommand item =
                                            plan.commands().stream()
                                                    .filter(
                                                            c ->
                                                                    c.kind
                                                                                    == PaintCommand
                                                                                            .Kind
                                                                                            .IMAGE
                                                                            && c.image.content
                                                                                            .itemFacts()
                                                                                    != null
                                                                            && c.box.width > 0
                                                                            && (c.clip == null
                                                                                    || c.box
                                                                                                    .intersect(
                                                                                                            c.clip)
                                                                                                    .width
                                                                                            > 0))
                                                    .findFirst()
                                                    .get();
                                    PaintCommand count =
                                            item.readyOverlay.commands().stream()
                                                    .filter(c -> c.kind == PaintCommand.Kind.TEXT)
                                                    .findFirst()
                                                    .get();
                                    float left = Float.POSITIVE_INFINITY,
                                            top = Float.POSITIVE_INFINITY;
                                    float right = Float.NEGATIVE_INFINITY,
                                            bottom = Float.NEGATIVE_INFINITY;
                                    for (TextLayout.Line line : count.text.lines())
                                        for (TextLayout.Glyph glyph : line.glyphs()) {
                                            require(glyph.inkBounds.known, "数量缺少真实墨迹");
                                            left =
                                                    Math.min(
                                                            left,
                                                            count.box.x
                                                                    + glyph.x
                                                                    + glyph.inkBounds.left);
                                            top =
                                                    Math.min(
                                                            top,
                                                            count.box.y
                                                                    + glyph.y
                                                                    + glyph.inkBounds.top);
                                            right =
                                                    Math.max(
                                                            right,
                                                            count.box.x
                                                                    + glyph.x
                                                                    + glyph.inkBounds.right);
                                            bottom =
                                                    Math.max(
                                                            bottom,
                                                            count.box.y
                                                                    + glyph.y
                                                                    + glyph.inkBounds.bottom);
                                        }
                                    require(bottom - top <= item.image.height * .5f, "GPU数量盖住物品图像");
                                    inkBox[0] = bounds(left, top, right - left, bottom - top);
                                });
                        int changedInside = 0, changedOutside = 0;
                        LayoutBox region = inkBox[0];
                        for (int y = 0; y < H; y++)
                            for (int x = 0; x < W; x++)
                                if (with.getRGB(x, y) != without.getRGB(x, y)) {
                                    if (x >= region.x
                                            && y >= region.y
                                            && x < region.x + region.width
                                            && y < region.y + region.height) changedInside++;
                                    else changedOutside++;
                                }
                        require(changedInside > 0, "数量覆盖层未产生实际GPU像素");
                        require(changedOutside == 0, "数量切换污染公式或图像外区域: " + changedOutside);
                        evidence.append("quantity-")
                                .append(label)
                                .append(" ink=")
                                .append(region.x)
                                .append(',')
                                .append(region.y)
                                .append(',')
                                .append(region.width)
                                .append(',')
                                .append(region.height)
                                .append(" changedInside=")
                                .append(changedInside)
                                .append(" changedOutside=")
                                .append(changedOutside)
                                .append((char) 10);
                    }
                    require(glGetError() == GL_NO_ERROR, "数量覆盖层GL错误");
                });
        scenario(
                "字号主题更新及框架整式降级实际PNG",
                () -> {
                    domain.execute(
                            () ->
                                    theme.set(
                                            SceneTheme.light()
                                                    .withTextStyle(
                                                            new TextStyle(
                                                                    "default",
                                                                    45.25f,
                                                                    0xff4ad9c2,
                                                                    null,
                                                                    club.heiqi.uilib.ui.scene.text
                                                                            .TextEffects.NORMAL,
                                                                    club.heiqi.uilib.ui.scene.text
                                                                            .TextDecoration
                                                                            .NONE))));
                    save("dynamic.png", renderReady());
                    domain.execute(
                            () -> content.set(MathContent.parse(tex("@unknown{@embed{ore}}"))));
                    BufferedImage fallback = renderReady();
                    save("fallback.png", fallback);
                    domain.execute(
                            () -> {
                                MathLayout value = SceneMath.layout(math);
                                require(
                                        value.fallback
                                                && value.sourceFallback.originalText.equals(
                                                        tex("@unknown{@embed{ore}}")),
                                        "没有完整原文framework降级");
                                require(
                                        page.pipeline().current().geometry.get(image) == null,
                                        "错误公式残留图像几何");
                                // 另一图像slot属于零宽祖先，只能产生完全clipped图像command。
                                for (PaintCommand command : plan.commands())
                                    if (command.kind == PaintCommand.Kind.IMAGE)
                                        require(
                                                command.clip != null
                                                        && command.box.intersect(command.clip).width
                                                                == 0,
                                                "错误公式仍绘制可见IMAGE");
                            });
                    evidence.append("fallback=whole-source no-visible-image").append((char) 10);
                    saveEvidence();
                });
    }

    private BufferedImage renderReady() throws Exception {
        long deadline = System.nanoTime() + 8_000_000_000L;
        do {
            clear();
            page.frame();
            boolean[] imageReady = {false};
            domain.execute(() -> imageReady[0] = SceneImage.state(image).get().content != null);
            if (ready == demand && imageReady[0]) {
                glFinish();
                return read();
            }
            Thread.sleep(4);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("正式GPU绘制未ready: " + ready + "/" + demand);
    }

    private void clear() {
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0.09f, 0.13f, 0.19f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private BufferedImage read() {
        ByteBuffer bytes = MemoryUtil.memAlloc(W * H * 4);
        try {
            glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            BufferedImage result = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = ((H - 1 - y) * W + x) * 4;
                    result.setRGB(
                            x,
                            y,
                            (bytes.get(i + 3) & 255) << 24
                                    | (bytes.get(i) & 255) << 16
                                    | (bytes.get(i + 1) & 255) << 8
                                    | bytes.get(i + 2) & 255);
                }
            return result;
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    private void save(String name, BufferedImage image) throws Exception {
        require(ImageIO.write(image, "png", output.resolve(name).toFile()), "PNG编码器不可用");
    }

    private void changed(String name, LayoutBox region, BufferedImage image, boolean expected)
            throws Exception {
        LayoutBox area = region.intersect(new LayoutBox(0, 0, W, H));
        int changed = 0;
        for (int y = area.y; y < area.y + area.height; y++)
            for (int x = area.x; x < area.x + area.width; x++)
                if (image.getRGB(x, y) != baseline.getRGB(x, y)) changed++;
        evidence.append(name)
                .append(" region=")
                .append(area.x)
                .append(',')
                .append(area.y)
                .append(',')
                .append(area.width)
                .append(',')
                .append(area.height)
                .append(" changed=")
                .append(changed)
                .append((char) 10);
        saveEvidence();
        require(
                area.width > 0 && area.height > 0 && (expected ? changed > 0 : changed == 0),
                name + "像素区证据失败: " + changed);
    }

    private void saveEvidence() throws Exception {
        Files.write(
                output.resolve("regions.txt"),
                evidence.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.print(evidence);
    }

    private static boolean overlap(LayoutBox a, LayoutBox b) {
        LayoutBox i = a.intersect(b);
        return i.width > 0 && i.height > 0;
    }

    private static LayoutBox bounds(float x, float y, float width, float height) {
        int left = (int) Math.floor(x), top = (int) Math.floor(y);
        return new LayoutBox(
                left, top, (int) Math.ceil(x + width) - left, (int) Math.ceil(y + height) - top);
    }
}
