package club.heiqi.uilib.ui.scene.integration;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
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

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import javax.imageio.ImageIO;

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
    private Signal<MathContent> inlineContent;
    private Signal<ImageOptions> imageOptions;
    private SceneNode math, image, image2, healthy, hiddenMath, hiddenImage;
    private Signal<Integer> viewportWidth;
    private PaintPlan plan;
    private int ready, demand;
    private Path output;
    private BufferedImage baseline;
    private final StringBuilder evidence = new StringBuilder();

    private static String tex(String source) {
        return source.replace('@', (char) 92)
                .replace("html!mathml", "html@mathml")
                .replace("!not", "@not");
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
                                    viewportWidth = domain.signal(W);
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
                                    image2 =
                                            SceneImage.themed(
                                                    images.images(), source, imageOptions, theme);
                                    Map<String, SceneMath.Embed> mathSlots =
                                            new java.util.LinkedHashMap<>();
                                    mathSlots.put(
                                            "ore",
                                            new SceneMath.Embed(
                                                    image, new MathEmbedMetrics(1, 1, .8f)));
                                    mathSlots.put(
                                            "ore2",
                                            new SceneMath.Embed(
                                                    image2, new MathEmbedMetrics(1, 1, .8f)));
                                    math =
                                            SceneMath.create(
                                                    ctx,
                                                    content,
                                                    theme,
                                                    MathStyle.DISPLAY,
                                                    mathSlots);
                                    inlineContent =
                                            domain.signal(
                                                    MathContent.parse(
                                                            tex(
                                                                    "@displaystyle"
                                                                        + " @sum@limits_{n=1}^{5}x_n+@sqrt{1+x^2}")));
                                    SceneNode inlineItem =
                                            SceneImage.themed(
                                                    images.images(), source, imageOptions, theme);
                                    healthy =
                                            SceneMath.create(
                                                    ctx,
                                                    inlineContent,
                                                    theme,
                                                    MathStyle.TEXT,
                                                    Collections.singletonMap(
                                                            "ore",
                                                            new SceneMath.Embed(
                                                                    inlineItem,
                                                                    new MathEmbedMetrics(
                                                                            1, 1, .8f))));
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
                                                    .clip(true)
                                                    .bind(
                                                            club.heiqi.uilib.ui.scene.runtime
                                                                    .NodeBinding.value(
                                                                    viewportWidth,
                                                                    (node, width) ->
                                                                            node.size(
                                                                                    Size.fixed(
                                                                                            width),
                                                                                    Size.fill(1))))
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
            new club.heiqi.uilib.ui.scene.image.ItemImageFacts(
                    "32", "装饰Item提示", false, 1, 0xffffffff);

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
                                // 真实像素区域消费同代文字投影；smash/lap的raw原点不一定是Scene原点。
                                int projectedLine = 0;
                                for (MathLayout.TextPiece piece : layout.pieces) {
                                    if (piece == numerator) break;
                                    projectedLine += piece.text.lines().size();
                                }
                                TextLayout.Glyph projected =
                                        page.pipeline()
                                                .current()
                                                .geometry
                                                .get(math)
                                                .text
                                                .lines()
                                                .get(projectedLine)
                                                .glyphs()
                                                .get(0);
                                require(
                                        projected.source.equals(glyph.source)
                                                && projected.glyphId == glyph.glyphId
                                                && projected.sourceStart == glyph.sourceStart
                                                && projected.sourceEnd == glyph.sourceEnd,
                                        "分子正式文字投影未保留字形事实");
                                float shiftX = projected.x - numerator.x - glyph.x,
                                        shiftY = projected.y - numerator.y - glyph.y;
                                regions[0] =
                                        bounds(
                                                box.x + projected.x + glyph.inkBounds.left,
                                                box.y + projected.y + glyph.inkBounds.top,
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
                                                box.x + rule.x + shiftX,
                                                box.y + rule.y + shiftY,
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
                "真实MATH部件连续像素与定界符二项式Item",
                () -> {
                    String[] formulas = {
                        tex(
                                "@left@{@dfrac{1}{@dfrac{1}{@dfrac{@embed{ore}}{1+x}}}@middle|@binom{n}{k}@right@}"),
                        tex("@sqrt{@dfrac{1}{@dfrac{1}{@dfrac{@embed{ore}}{1+x}}}}"),
                        tex("@binom{@embed{ore}+n}{k}+@left.@frac{x}{1+x}@right|_{x=0}")
                    };
                    for (int f = 0; f < formulas.length; f++) {
                        final String formula = formulas[f];
                        for (float em : new float[] {18, 28}) {
                            domain.execute(
                                    () -> {
                                        theme.set(
                                                SceneTheme.dark()
                                                        .withTextStyle(
                                                                new TextStyle(
                                                                        "default",
                                                                        em,
                                                                        -1,
                                                                        null,
                                                                        club.heiqi.uilib.ui.scene
                                                                                .text.TextEffects
                                                                                .NORMAL,
                                                                        club.heiqi.uilib.ui.scene
                                                                                .text.TextDecoration
                                                                                .NONE)));
                                        content.set(MathContent.parse(formula));
                                        imageOptions.set(ImageOptions.defaults().quantity(true));
                                    });
                            BufferedImage rendered = renderReady();
                            save("stretch-" + f + "-" + (int) em + ".png", rendered);
                            final int[] assemblies = {0};
                            domain.execute(
                                    () -> {
                                        MathLayout value = SceneMath.layout(math);
                                        require(
                                                !value.fallback,
                                                "伸缩结构实际GPU降为原文: " + value.diagnostics);
                                        require(value.embeds.size() == 1, "伸缩结构丢失Item");
                                        LayoutBox origin =
                                                page.pipeline().current().geometry.get(math).box;
                                        for (MathLayout.TextPiece piece : value.pieces) {
                                            String symbol = piece.text.logicalText;
                                            if (!(symbol.equals("{")
                                                    || symbol.equals("}")
                                                    || symbol.equals("√")
                                                    || symbol.equals("|"))) continue;
                                            int glyphCount =
                                                    piece.text.lines().stream()
                                                            .mapToInt(line -> line.glyphs().size())
                                                            .sum();
                                            if (glyphCount <= 1) continue;
                                            assemblies[0]++;
                                            assemblyPixels(piece, origin, rendered, em);
                                        }
                                    });
                            if (f < 2) require(assemblies[0] > 0, "代表性高内容没有进入真实部件构建");
                            require(glGetError() == GL_NO_ERROR, "伸缩部件GL错误");
                        }
                    }
                    fonts.reload(MathGlScenario.class.getClassLoader(), System.nanoTime());
                    save("stretch-reloaded.png", renderReady());
                    domain.execute(() -> require(!SceneMath.layout(math).fallback, "重载后伸缩公式失效"));
                });
        scenario("求值线恒定覆盖与根号顶部连续实际像素", this::lineSeamPixels);
        scenario("所有装饰family多em真实GL图像", this::decorationPixels);
        scenario("多个Item背景斜线邻text及全页tooltip真实叠压", this::orderedDecorationPixels);
        scenario("顶层换行双入口实际GL可选间距Item", this::lineBreakPixels);
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

    private void decorationPixels() throws Exception {
        String[] names = {
            "narrow-accent",
            "wide-hat",
            "wide-check",
            "wide-tilde",
            "arrow-accent",
            "under-accent",
            "over-under",
            "stack-labels",
            "xarrow",
            "paired-arrow",
            "double-arrow",
            "brace",
            "color",
            "size-class",
            "phantom",
            "boxes",
            "raise-rule",
            "choice",
            "vcenter-lap",
            "smash",
            "enclose",
            "pmb",
            "htmlmathml",
            "verb",
            "tag",
            "linebreak",
            "text-accent",
            "rejected-command",
            "negation-symbol",
            "negation-overlay",
            "alignedat"
        };
        String[] formulas = {
            "@hat{x}+@acute{y}",
            "@widehat{xyz}",
            "@widecheck{xyz}",
            "@widetilde{xyz}",
            "@overleftrightarrow{xyz}+@vec{x}",
            "@utilde{xyz}+@underleftarrow{xyz}",
            "@overline{xy}+@underline{xy}",
            "@overset{a}{x}+@underset{b}{y}+@stackrel{!}{=}",
            "@xrightarrow[b]{abc}",
            "@xrightleftharpoons[b]{abc}",
            "@xLeftrightarrow[b]{abc}",
            "@overbrace{xyz}^{n}+@underbrace{xyz}_{m}",
            "@textcolor{red}{x}+@textcolor{blue}{y}+@textcolor{currentColor}{z}",
            "{@Huge x}+@mathrel{y}",
            "x+@phantom{yyyy}+@hphantom{x}+@vphantom{y}",
            "@boxed{x}+@colorbox{yellow}{x}+@fcolorbox{blue}{cyan}{y}",
            "@raisebox{.2em}{x}+@rule[.1em]{.5em}{.08em}",
            "@mathchoice{D}{T}{S}{s}",
            "@vcenter{x}+@mathllap{x}+@mathrlap{y}+@mathclap{z}",
            "@smash[t]{x}+@smash[b]{y}",
            "@cancel{x}+@bcancel{x}+@xcancel{x}+@angl{x}+@sout{x}+@phase{x}",
            "@pmb{x+y}",
            "@html!mathml{x}{y}",
            "@verb|a b|",
            "x@tag{label}",
            "x@@y",
            "@c{x}+@text{@^{x}@.{x}@c{x}@H{x}}",
            "x+@href{https://example.org}{hidden}",
            "@!not x+@not=+@not@subset",
            "@colorbox{yellow}{$@textcolor{lime}{@not=+@not@subset}$}",
            "@begin{alignedat}{2}@embed{ore}+a&=b&c&=d@@x&=y&z&=w@end{alignedat}"
        };
        for (float em : new float[] {18, 31.5f})
            for (int i = 0; i < formulas.length; i++) {
                final boolean alignedat = names[i].equals("alignedat");
                final String formula = tex(formulas[i] + (alignedat ? "" : "+@embed{ore}"));
                domain.execute(
                        () -> {
                            theme.set(
                                    SceneTheme.dark()
                                            .withTextStyle(
                                                    new TextStyle(
                                                            "default",
                                                            em,
                                                            0xffeaeaea,
                                                            null,
                                                            club.heiqi.uilib.ui.scene.text
                                                                    .TextEffects.NORMAL,
                                                            club.heiqi.uilib.ui.scene.text
                                                                    .TextDecoration.NONE)));
                            imageOptions.set(ImageOptions.defaults());
                            content.set(MathContent.parse(formula));
                        });
                BufferedImage result = renderReady();
                save("decoration-" + names[i] + "-" + em + ".png", result);
                final LayoutBox[] area = {null};
                domain.execute(
                        () -> {
                            MathLayout value = SceneMath.layout(math);
                            require(
                                    !value.fallback,
                                    "真实GL装饰整式降级: " + formula + " " + value.diagnostics);
                            area[0] = page.pipeline().current().geometry.get(math).box;
                            require(value.embeds.size() == 1, "family fixture重复Item");
                            if (alignedat) {
                                require(value.diagnostics.isEmpty(), "合法alignedat有数学诊断");
                                require(
                                        Float.isFinite(value.width + value.height + value.baseline),
                                        "alignedat几何非有限");
                            }
                        });
                changed("decoration-" + names[i] + "-" + em, area[0], result, true);
                require(glGetError() == GL_NO_ERROR, "装饰GL错误");
            }
    }

    private void lineBreakPixels() throws Exception {
        String[] formulas = {"x@@[1pt]y", "@displaystyle x@@[-1pt]y+@embed{ore}"};
        for (float em : new float[] {18, 31.5f})
            for (int i = 0; i < formulas.length; i++) {
                String formula = tex(formulas[i]);
                domain.execute(
                        () -> {
                            viewportWidth.set(W);
                            theme.set(
                                    SceneTheme.dark()
                                            .withTextStyle(
                                                    new TextStyle(
                                                            "default",
                                                            em,
                                                            0xffeaeaea,
                                                            null,
                                                            club.heiqi.uilib.ui.scene.text
                                                                    .TextEffects.NORMAL,
                                                            club.heiqi.uilib.ui.scene.text
                                                                    .TextDecoration.NONE)));
                            imageOptions.set(ImageOptions.defaults().tooltip(true));
                            content.set(MathContent.parse(formula));
                            inlineContent.set(MathContent.parse(formula));
                        });
                BufferedImage pixels = renderReady();
                save("linebreak-" + i + "-" + em + ".png", pixels);
                domain.execute(
                        () -> {
                            for (SceneNode node : new SceneNode[] {math, healthy}) {
                                MathLayout layout = SceneMath.layout(node);
                                require(!layout.fallback, "双入口换行降级");
                                float first = Float.NaN, second = Float.NaN;
                                for (MathLayout.TextPiece piece : layout.pieces) {
                                    String original =
                                            formula.substring(piece.sourceStart, piece.sourceEnd);
                                    if (original.equals("x"))
                                        first = piece.y + piece.text.lines().get(0).baseline;
                                    if (original.equals("y"))
                                        second = piece.y + piece.text.lines().get(0).baseline;
                                }
                                require(second > first, "实际GPU换行未生成两个基线");
                                require(
                                        layout.embeds.size() == (formula.contains("embed") ? 1 : 0),
                                        "多行Item资格错误");
                            }
                        });
                final LayoutBox[] area = {null};
                domain.execute(() -> area[0] = page.pipeline().current().geometry.get(math).box);
                changed("linebreak-" + i + "-" + em, area[0], pixels, true);
                require(glGetError() == GL_NO_ERROR, "换行GL错误");
            }
        domain.execute(
                () ->
                        inlineContent.set(
                                MathContent.parse(
                                        tex(
                                                "@displaystyle"
                                                    + " @sum@limits_{n=1}^{5}x_n+@sqrt{1+x^2}"))));
        renderReady();
    }

    private void orderedDecorationPixels() throws Exception {
        String formula =
                tex(
                        "@colorbox{yellow}{$@textcolor{lime}{@cancel{@embed{ore}}}$}x@fcolorbox{blue}{cyan}{$@embed{ore2}$}y");
        domain.execute(
                () -> {
                    theme.set(
                            SceneTheme.dark()
                                    .withTextStyle(
                                            new TextStyle(
                                                    "default",
                                                    31.5f,
                                                    0xffeeeeee,
                                                    null,
                                                    club.heiqi.uilib.ui.scene.text.TextEffects
                                                            .NORMAL,
                                                    club.heiqi.uilib.ui.scene.text.TextDecoration
                                                            .NONE)));
                    content.set(MathContent.parse(formula));
                    imageOptions.set(ImageOptions.defaults().quantity(true).tooltip(true));
                });
        BufferedImage pixels = renderReady();
        save("decoration-ordered.png", pixels);
        final LayoutBox[] item = {null};
        final long[] generation = {0};
        domain.execute(
                () -> {
                    require(!SceneMath.layout(math).fallback, "有序装饰降级");
                    java.util.List<PaintCommand> commands = plan.commands();
                    int bg = -1, a = -1, line = -1, text = -1, b = -1;
                    for (int i = 0; i < commands.size(); i++) {
                        PaintCommand c = commands.get(i);
                        if (c.kind == PaintCommand.Kind.RECT && c.color == 0xffffff00) bg = i;
                        if (c.kind == PaintCommand.Kind.IMAGE
                                && c.clip != null
                                && c.box.intersect(c.clip).width == 0) continue;
                        if (c.kind == PaintCommand.Kind.IMAGE) {
                            if (a < 0) a = i;
                            else b = i;
                        }
                        if (c.kind == PaintCommand.Kind.LINE && c.color == 0xff00ff00) line = i;
                        if (c.kind == PaintCommand.Kind.TEXT && a >= 0 && b < 0) text = i;
                    }
                    require(bg < a && a < line && line < text && text < b, "GPU计划未保持Item之间有序绘制");
                    item[0] = page.pipeline().current().geometry.get(image).box;
                    generation[0] = SceneMath.layout(math).pieces.get(0).text.generation;
                });
        LayoutBox box = item[0];
        int green = 0;
        for (int y = box.y; y < box.y + box.height; y++)
            for (int x = box.x; x < box.x + box.width; x++) {
                int rgb = pixels.getRGB(x, y),
                        r = (rgb >>> 16) & 255,
                        g = (rgb >>> 8) & 255,
                        b = rgb & 255;
                if (g > 100 && g > r * 1.5 && g > b * 1.5) green++;
            }
        require(green > 0, "cancel实际LINE像素被Item覆盖");
        evidence.append("ordered-cancel-green=").append(green).append((char) 10);
        page.pointer(
                club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(
                        box.x + box.width / 2, box.y + box.height / 2));
        BufferedImage tip = renderReady();
        save("decoration-ordered-tooltip.png", tip);
        domain.execute(
                () -> {
                    require(
                            page.runtime()
                                            .pointerRouter()
                                            .passiveHovered(page.pipeline().current().geometry)
                                    == image,
                            "绘制段窃取passivehover");
                    int lastLine = -1, tooltip = -1;
                    for (int i = 0; i < plan.commands().size(); i++) {
                        PaintCommand c = plan.commands().get(i);
                        if (c.kind == PaintCommand.Kind.LINE) lastLine = i;
                        if (c.kind == PaintCommand.Kind.TEXT
                                && c.text.logicalText.equals("装饰Item提示")) tooltip = i;
                    }
                    require(tooltip > lastLine, "公式suffix盖过全页tooltip");
                });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(W - 1, H - 1));
        domain.execute(() -> viewportWidth.set(95));
        save("decoration-width-signal.png", renderReady());
        domain.execute(
                () -> {
                    viewportWidth.set(W);
                    theme.set(
                            SceneTheme.light()
                                    .withTextStyle(new TextStyle("default", 24, 0xff663399)));
                });
        save("decoration-theme-signal.png", renderReady());
        fonts.reload(MathGlScenario.class.getClassLoader(), System.nanoTime());
        save("decoration-generation.png", renderReady());
        domain.execute(
                () ->
                        require(
                                SceneMath.layout(math).pieces.get(0).text.generation
                                        != generation[0],
                                "重载保留旧字形generation"));
        saveEvidence();
    }

    /** 真实回归：直线中段亮度应恒定，根号短横到长横不能有背景列或上下台阶。 */
    private void lineSeamPixels() throws Exception {
        String[] formulas = {
            tex("@sqrt{@dfrac{1}{@dfrac{1}{@dfrac{@embed{ore}}{1+x}}}}"),
            tex("@binom{@embed{ore}+n}{k}+@left.@frac{x}{1+x}@right|_{x=0}")
        };
        for (float em : new float[] {18, 28})
            for (int alpha : new int[] {255, 128}) {
                for (int f = 0; f < formulas.length; f++) {
                    final String formula = formulas[f];
                    domain.execute(
                            () -> {
                                theme.set(
                                        SceneTheme.dark()
                                                .withTextStyle(
                                                        new TextStyle(
                                                                "default",
                                                                em,
                                                                (alpha << 24) | 0xffffff,
                                                                null,
                                                                club.heiqi.uilib.ui.scene.text
                                                                        .TextEffects.NORMAL,
                                                                club.heiqi.uilib.ui.scene.text
                                                                        .TextDecoration.NONE)));
                                content.set(MathContent.parse(formula));
                            });
                    BufferedImage rendered = renderReady();
                    save("line-seam-" + f + "-" + (int) em + "-" + alpha + ".png", rendered);
                    final int formulaIndex = f;
                    domain.execute(
                            () -> {
                                MathLayout layout = SceneMath.layout(math);
                                require(!layout.fallback, "连接回归降为原文");
                                LayoutBox origin = page.pipeline().current().geometry.get(math).box;
                                for (MathLayout.TextPiece piece : layout.pieces) {
                                    if (!piece.text.logicalText.equals(
                                            formulaIndex == 0 ? "√" : "|")) continue;
                                    float left = Float.POSITIVE_INFINITY,
                                            top = Float.POSITIVE_INFINITY;
                                    float right = Float.NEGATIVE_INFINITY,
                                            bottom = Float.NEGATIVE_INFINITY;
                                    for (TextLayout.Line line : piece.text.lines())
                                        for (TextLayout.Glyph glyph : line.glyphs()) {
                                            left =
                                                    Math.min(
                                                            left,
                                                            origin.x
                                                                    + piece.x
                                                                    + glyph.x
                                                                    + glyph.inkBounds.left);
                                            top =
                                                    Math.min(
                                                            top,
                                                            origin.y
                                                                    + piece.y
                                                                    + glyph.y
                                                                    + glyph.inkBounds.top);
                                            right =
                                                    Math.max(
                                                            right,
                                                            origin.x
                                                                    + piece.x
                                                                    + glyph.x
                                                                    + glyph.inkBounds.right);
                                            bottom =
                                                    Math.max(
                                                            bottom,
                                                            origin.y
                                                                    + piece.y
                                                                    + glyph.y
                                                                    + glyph.inkBounds.bottom);
                                        }
                                    int background = rendered.getRGB(0, 0) & 255;
                                    if (formulaIndex == 1) {
                                        int first = -1, last = -1;
                                        int[] peaks = new int[H];
                                        int center = -1, centerPeak = 0;
                                        int sampleRow = (int) Math.ceil(top + em * .2f);
                                        for (int x = (int) Math.floor(left);
                                                x < Math.ceil(right);
                                                x++) {
                                            int pixel = rendered.getRGB(x, sampleRow) & 255;
                                            if (pixel > centerPeak) {
                                                centerPeak = pixel;
                                                center = x;
                                            }
                                        }
                                        require(center >= 0, "求值线没有中心墨迹");
                                        for (int y = (int) Math.floor(top);
                                                y < Math.ceil(bottom);
                                                y++) {
                                            // 只采真实竖线中心，保守包络可能含紧邻的分数横线。
                                            peaks[y] = rendered.getRGB(center, y) & 255;
                                            if (peaks[y] > background + 10) {
                                                if (first < 0) first = y;
                                                last = y;
                                            }
                                        }
                                        require(first >= 0 && last - first > em, "求值线未产生可验证像素");
                                        int low = 255, high = 0;
                                        // 两端的正常滤波衰减不计入恒定中段；覆盖所有部件接点。
                                        for (int y = first + 2; y <= last - 2; y++) {
                                            low = Math.min(low, peaks[y]);
                                            high = Math.max(high, peaks[y]);
                                        }
                                        require(
                                                high - low <= 1,
                                                "求值线部件重复覆盖产生明暗分段: " + low + ".." + high);
                                        evidence.append("line-seam bar em=")
                                                .append(em)
                                                .append(" alpha=")
                                                .append(alpha)
                                                .append(" peakRange=")
                                                .append(high - low)
                                                .append((char) 10);
                                    } else {
                                        int rowLow = H, rowHigh = 0;
                                        for (int x = (int) Math.floor(right - em * .3f);
                                                x < Math.ceil(right + em * .3f);
                                                x++) {
                                            int peak = 0, peakRow = -1;
                                            for (int y = (int) Math.floor(top);
                                                    y < Math.ceil(top + em * .2f);
                                                    y++) {
                                                int pixel = rendered.getRGB(x, y) & 255;
                                                if (pixel > peak) {
                                                    peak = pixel;
                                                    peakRow = y;
                                                }
                                            }
                                            require(
                                                    peak > background + 10,
                                                    "根号顶部短横与长横之间存在背景列: " + x);
                                            rowLow = Math.min(rowLow, peakRow);
                                            rowHigh = Math.max(rowHigh, peakRow);
                                        }
                                        require(
                                                rowHigh - rowLow <= 1,
                                                "根号顶部短横与长横出现上下台阶: " + rowLow + ".." + rowHigh);
                                        evidence.append("line-seam radical em=")
                                                .append(em)
                                                .append(" alpha=")
                                                .append(alpha)
                                                .append(" peakRowRange=")
                                                .append(rowHigh - rowLow)
                                                .append((char) 10);
                                    }
                                }
                            });
                }
            }
        saveEvidence();
    }

    private void assemblyPixels(
            MathLayout.TextPiece piece, LayoutBox origin, BufferedImage rendered, float em) {
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        int glyphs = 0;
        for (TextLayout.Line line : piece.text.lines())
            for (TextLayout.Glyph glyph : line.glyphs()) {
                require(glyph.inkBounds.known && glyph.source != null, "拼接部件没有真实native墨迹");
                left = Math.min(left, origin.x + piece.x + glyph.x + glyph.inkBounds.left);
                top = Math.min(top, origin.y + piece.y + glyph.y + glyph.inkBounds.top);
                right = Math.max(right, origin.x + piece.x + glyph.x + glyph.inkBounds.right);
                bottom = Math.max(bottom, origin.y + piece.y + glyph.y + glyph.inkBounds.bottom);
                glyphs++;
            }
        LayoutBox area = bounds(left, top, right - left, bottom - top);
        require(
                area.x >= 0 && area.y >= 0 && area.x + area.width <= W && area.y + area.height <= H,
                "拼接像素证据被视口裁断");
        int first = -1, last = -1, empty = 0, pixels = 0;
        boolean[] rows = new boolean[area.height];
        for (int y = 0; y < area.height; y++)
            for (int x = area.x; x < area.x + area.width; x++) {
                int pixel = rendered.getRGB(x, area.y + y);
                // 白色真实数学字形，不把背景或中性红色Item算作连接像素。
                if (((pixel >> 16) & 255) > 80 && ((pixel >> 8) & 255) > 80 && (pixel & 255) > 80) {
                    rows[y] = true;
                    pixels++;
                }
            }
        for (int y = 0; y < rows.length; y++)
            if (rows[y]) {
                if (first < 0) first = y;
                last = y;
            }
        for (int y = first; y <= last && y >= 0; y++) if (!rows[y]) empty++;
        require(
                first >= 0 && empty == 0,
                "真实拼接存在整行裂缝: " + piece.text.logicalText + " emptyRows=" + empty);
        evidence.append("assembly symbol=")
                .append(piece.text.logicalText)
                .append(" em=")
                .append(em)
                .append(" glyphs=")
                .append(glyphs)
                .append(" ink=")
                .append(area.x)
                .append(',')
                .append(area.y)
                .append(',')
                .append(area.width)
                .append(',')
                .append(area.height)
                .append(" pixels=")
                .append(pixels)
                .append(" emptyRows=")
                .append(empty)
                .append((char) 10);
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
