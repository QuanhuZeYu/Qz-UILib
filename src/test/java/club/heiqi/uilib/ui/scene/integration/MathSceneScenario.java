package club.heiqi.uilib.ui.scene.integration;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ItemImageFacts;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathFont;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

/** 真 FT/HB + 正式页面/帧/input/共享CPU图像；位图提示事实不冒充 Minecraft 物品绘制。 */
public final class MathSceneScenario extends TestApplication {
    private FontTextService fonts;
    private static final float EM = 32.3f;
    private static final String INTEGRAL = tex("@int_0^1 @frac{@embed{ore}}{1+x^2}@,@mathrm{d}x");

    private static String tex(String source) {
        return source.replace('@', (char) 92);
    }

    public static void main(String[] args) throws Exception {
        new MathSceneScenario().run(args);
    }

    @Override
    protected void setup() throws Exception {
        fonts = own(new FontTextService(new LwjglFontEngine()));
    }

    @Override
    protected void runTests() throws Exception {
        scenario("真实公式同代glyph浮点投影与分式脚本图像", this::geometry);
        scenario("被动hover祖先clip窄视口滚动", this::input);
        scenario("来源主题字号字体重载与整式错误恢复", this::dynamic);
        scenario("绑定唯一身份passive资格及卸载释放", this::lifecycle);
    }

    private static ImageContent bitmap() {
        byte[] rgba = new byte[16 * 16 * 4];
        for (int i = 0; i < rgba.length; i += 4) {
            rgba[i] = (byte) 230;
            rgba[i + 1] = (byte) 120;
            rgba[i + 3] = (byte) 255;
        }
        return new ImageContent(16, 16, rgba) {
            @Override
            public ItemImageFacts itemFacts() {
                return new ItemImageFacts("", "中性图像提示", false, 1, -1);
            }
        };
    }

    private final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SharedImageService images =
                new SharedImageService(
                        source -> () -> source.bitmap, SharedImageService.Budget.defaults());
        final ImageService counted =
                new ImageService() {
                    public Lease acquire(ImageSource source, ReactiveDomain domain) {
                        Lease lease = images.acquire(source, domain);
                        leases++;
                        return new Lease() {
                            boolean closed;

                            public club.heiqi.uilib.ui.reactive.ReadableSignal<
                                            club.heiqi.uilib.ui.scene.image.ImageState>
                                    state() {
                                return lease.state();
                            }

                            public void close() {
                                if (!closed) {
                                    closed = true;
                                    lease.close();
                                    leases--;
                                }
                            }
                        };
                    }

                    public void reload(ImageSource source) {
                        images.reload(source);
                    }
                };
        int leases, width = 440, height = 180;
        PaintPlan paint;
        Signal<MathContent> content;
        Signal<SceneTheme> theme;
        Signal<ImageSource> source;
        SceneNode math, viewport;
        final Map<String, SceneNode> nodes = new LinkedHashMap<>();
        final SceneHost host =
                new SceneHost() {
                    public int width() {
                        return width;
                    }

                    public int height() {
                        return height;
                    }

                    public long metricsRevision() {
                        return fonts.generation();
                    }

                    public void prepareResources() {
                        images.drain();
                    }

                    public void replay(PaintPlan plan) {
                        paint = plan;
                    }
                };
        final ScenePageSession page;

        Fixture(String formula, MathStyle mathStyle, String... ids) {
            page =
                    ScenePageSession.open(
                            domain,
                            host,
                            fonts,
                            ctx -> {
                                content = domain.signal(MathContent.parse(formula));
                                theme =
                                        domain.signal(
                                                SceneTheme.dark()
                                                        .withTextStyle(
                                                                new TextStyle(
                                                                        "default",
                                                                        EM,
                                                                        -1,
                                                                        null,
                                                                        club.heiqi.uilib.ui.scene
                                                                                .text.TextEffects
                                                                                .NORMAL,
                                                                        club.heiqi.uilib.ui.scene
                                                                                .text.TextDecoration
                                                                                .NONE)));
                                source = domain.signal(ImageSource.bitmap(bitmap()));
                                Map<String, SceneMath.Embed> bindings = new LinkedHashMap<>();
                                for (String id : ids) {
                                    SceneNode image =
                                            SceneImage.themed(
                                                    counted,
                                                    source,
                                                    domain.signal(
                                                            ImageOptions.defaults().tooltip(true)),
                                                    theme);
                                    nodes.put(id, image);
                                    bindings.put(
                                            id,
                                            new SceneMath.Embed(
                                                    image, new MathEmbedMetrics(1, 1, 0.8f)));
                                }
                                math =
                                        SceneMath.create(ctx, content, theme, mathStyle, bindings)
                                                .padding(7);
                                viewport =
                                        new SceneNode()
                                                .size(Size.fill(1), Size.fill(1))
                                                .scrollable(true)
                                                .clip(true)
                                                .add(math);
                                ctx.mount(viewport);
                            });
        }

        void frame() {
            page.frame();
        }

        void ready() throws Exception {
            long deadline = System.nanoTime() + 4_000_000_000L;
            do {
                frame();
                if (paint.commands().stream().anyMatch(cmd -> cmd.kind == PaintCommand.Kind.IMAGE))
                    return;
                Thread.sleep(5);
            } while (System.nanoTime() < deadline);
            throw new AssertionError("共享图像未及时READY");
        }

        SceneGeometryView geometry() {
            return page.pipeline().current().geometry;
        }

        MathLayout layout() {
            return SceneMath.layout(math);
        }

        @Override
        public void close() {
            page.close();
            images.close();
        }
    }

    private void geometry() throws Exception {
        try (Fixture f = new Fixture(INTEGRAL, MathStyle.DISPLAY, "ore")) {
            f.ready();
            f.domain.execute(
                    () -> {
                        MathLayout layout = f.layout();
                        healthy(layout);
                        near(layout.embeds.get(0).width, EM, "display分子应使用根em");
                        placement(f, "ore");
                        SceneGeometryView.Entry entry = f.geometry().get(f.math);
                        require(
                                entry.box.height == (int) Math.ceil(layout.height) + 14,
                                "padding或公式extent重复计入");
                        require(
                                entry.text.height == (int) Math.ceil(layout.height),
                                "普通font lineheight替代公式extent");
                        int lineIndex = 0;
                        boolean negative = false, fractional = false;
                        for (MathLayout.TextPiece p : layout.pieces) {
                            negative |= p.y < 0;
                            fractional |= p.x != Math.floor(p.x) || p.y != Math.floor(p.y);
                            for (TextLayout.Line line : p.text.lines()) {
                                TextLayout.Line actual = entry.text.lines().get(lineIndex++);
                                require(actual.paragraphRtl == line.paragraphRtl, "投影丢Bidi事实");
                                for (int i = 0; i < line.glyphs().size(); i++) {
                                    TextLayout.Glyph g = line.glyphs().get(i),
                                            a = actual.glyphs().get(i);
                                    near(a.x, g.x + p.x, "glyph浮点x");
                                    near(a.y, g.y + p.y, "glyph负y/基线");
                                    require(
                                            a.source == g.source
                                                    && a.resolvedStyle == g.resolvedStyle
                                                    && a.clusterIndex == g.clusterIndex
                                                    && a.sourceStart == g.sourceStart
                                                    && a.sourceEnd == g.sourceEnd,
                                            "投影丢字体/style/cluster/source事实");
                                }
                            }
                        }
                        require(negative && fractional, "真实积分未命中负y与浮点回归路径");
                        require(
                                f.paint.commands().stream()
                                                .filter(cmd -> cmd.kind == PaintCommand.Kind.IMAGE)
                                                .count()
                                        == 1,
                                "同一SceneImage重复绘制");
                    });
        }
        try (Fixture f =
                new Fixture(
                        tex("x^{@embed{up}}_{@embed{down}}+@frac{@embed{ore}}{2}"),
                        MathStyle.TEXT,
                        "up",
                        "down",
                        "ore")) {
            f.ready();
            f.domain.execute(
                    () -> {
                        healthy(f.layout());
                        float ratio =
                                fonts.mathFont(f.math.style())
                                                .constant(
                                                        MathFont.Constant.SCRIPT_PERCENT_SCALE_DOWN)
                                        / 100f;
                        for (MathLayout.Embed e : f.layout().embeds) {
                            near(e.width, EM * ratio, "script与text分式em");
                            placement(f, e.id);
                        }
                    });
        }
        try (Fixture f =
                new Fixture(
                        tex("@sum@limits_{1}^{@embed{up}}+@sqrt[3]{1+@frac{@embed{ore}}{2}}"),
                        MathStyle.DISPLAY,
                        "up",
                        "ore")) {
            f.ready();
            f.domain.execute(() -> healthy(f.layout()));
        }
    }

    private void input() throws Exception {
        try (Fixture f = new Fixture(INTEGRAL, MathStyle.DISPLAY, "ore")) {
            f.ready();
            LayoutBox[] box = {null};
            f.domain.execute(() -> box[0] = f.geometry().get(f.nodes.get("ore")).box);
            f.page.pointer(
                    ScenePointerEvent.move(
                            box[0].x + box[0].width / 2, box[0].y + box[0].height / 2));
            f.frame();
            f.domain.execute(
                    () ->
                            require(
                                    f.page.runtime().pointerRouter().passiveHovered(f.geometry())
                                            == f.nodes.get("ore"),
                                    "可见图像没有沿唯一router悬停"));
            require(
                    f.paint.commands().stream()
                            .anyMatch(
                                    cmd ->
                                            cmd.kind == PaintCommand.Kind.TEXT
                                                    && "中性图像提示".equals(cmd.text.logicalText)),
                    "已有SceneImage提示未绘制");
            f.width = Math.max(1, box[0].x - 1);
            f.frame();
            f.domain.execute(
                    () -> {
                        require(
                                f.geometry().get(f.nodes.get("ore")).clip.width == f.width,
                                "slot没有继承祖先clip");
                        require(
                                f.page.runtime().pointerRouter().passiveHovered(f.geometry())
                                        == null,
                                "clip外图像仍hover");
                    });
            f.width = 440;
            f.height = 20;
            f.frame();
            f.domain.execute(
                    () ->
                            require(
                                    f.geometry().get(f.viewport).scrollMaximum > 0,
                                    "公式extent没有进入滚动范围"));
            f.page.pointer(ScenePointerEvent.scroll(5, 5, -1));
            f.frame();
            f.domain.execute(
                    () -> require(f.geometry().get(f.viewport).scrollOffset > 0, "滚轮没有沿scene几何滚动"));
        }
    }

    private void dynamic() throws Exception {
        try (Fixture f = new Fixture(INTEGRAL, MathStyle.DISPLAY, "ore")) {
            f.ready();
            SceneNode identity = f.nodes.get("ore");
            f.domain.execute(
                    () ->
                            f.theme.set(
                                    SceneTheme.light()
                                            .withTextStyle(
                                                    new TextStyle(
                                                            "default",
                                                            EM * 1.5f,
                                                            0xff123456,
                                                            null,
                                                            club.heiqi.uilib.ui.scene.text
                                                                    .TextEffects.NORMAL,
                                                            club.heiqi.uilib.ui.scene.text
                                                                    .TextDecoration.NONE))));
            f.frame();
            f.domain.execute(
                    () -> {
                        near(f.layout().embeds.get(0).width, EM * 1.5f, "字号变化不更新slot");
                        require(
                                f.math.children().get(0) == identity
                                        && identity.style().color == 0xff123456,
                                "字号或主题重建图像/没有明确绑定");
                        require(
                                f.geometry().get(f.math).text.style.color == 0xff123456,
                                "文本仍使用旧主题");
                    });
            fonts.reload(fonts.defaultCandidates());
            f.frame();
            f.domain.execute(
                    () -> {
                        for (MathLayout.TextPiece p : f.layout().pieces)
                            require(p.text.generation == fonts.generation(), "重载数学facts混代");
                        require(
                                f.geometry().get(f.math).text.generation == fonts.generation(),
                                "重载投影混代");
                    });
            f.domain.execute(() -> f.content.set(MathContent.parse(tex("1+@embed{missing}"))));
            f.frame();
            f.domain.execute(
                    () -> {
                        require(
                                f.layout().fallback
                                        && f.layout()
                                                .sourceFallback
                                                .originalText
                                                .equals(tex("1+@embed{missing}")),
                                "missing绑定未完整源文降级");
                        require(
                                f.geometry().get(identity) == null
                                        && !f.geometry().get(f.math).text.diagnostics().isEmpty(),
                                "降级残留几何或丢framework诊断");
                        require(
                                f.page.runtime().pointerRouter().passiveHovered(f.geometry())
                                        == null,
                                "无placement图像仍hover");
                    });
            require(
                    f.paint.commands().stream()
                            .noneMatch(cmd -> cmd.kind == PaintCommand.Kind.IMAGE),
                    "降级仍绘制IMAGE");
            f.domain.execute(() -> f.content.set(MathContent.parse(tex("@unknown{a}"))));
            f.frame();
            f.domain.execute(
                    () ->
                            require(
                                    f.layout().fallback
                                            && f.layout().diagnostics.get(0).stage.equals("parse"),
                                    "未知命令未framework降级"));
            f.domain.execute(
                    () -> {
                        f.content.set(MathContent.parse(INTEGRAL));
                        f.source.set(ImageSource.bitmap(bitmap()));
                    });
            f.ready();
            f.domain.execute(
                    () -> {
                        healthy(f.layout());
                        require(
                                f.nodes.get("ore") == identity && f.leases == 1,
                                "恢复/来源更新泄漏lease或重建slot");
                    });
        }
    }

    private void lifecycle() throws Exception {
        Fixture f = new Fixture(INTEGRAL, MathStyle.DISPLAY, "ore");
        try {
            f.ready();
            require(f.leases == 1, "真实image lease未获取");
            require(
                    dependents(f.content) > 0
                            && dependents(f.theme) > 0
                            && dependents(f.source) > 0,
                    "场景未命中真实订阅");
        } finally {
            f.close();
        }
        require(f.leases == 0 && f.images.entryCount() == 0, "卸载没有释放图像lease/CPU内容");
        require(
                dependents(f.content) == 0 && dependents(f.theme) == 0 && dependents(f.source) == 0,
                "卸载没有释放来源/主题/图像订阅");
        // 正式构造拒绝复用节点和focusable图像，避免没有placement的逻辑树获得焦点。
        ReactiveDomain domain = ReactiveDomain.direct();
        SceneHost host =
                new SceneHost() {
                    public int width() {
                        return 100;
                    }

                    public int height() {
                        return 100;
                    }

                    public long metricsRevision() {
                        return fonts.generation();
                    }

                    public void replay(PaintPlan plan) {}
                };
        try (SharedImageService images =
                        new SharedImageService(
                                source -> () -> source.bitmap,
                                SharedImageService.Budget.defaults());
                ScenePageSession page =
                        ScenePageSession.open(
                                domain,
                                host,
                                fonts,
                                ctx -> {
                                    SceneNode image =
                                            SceneImage.create(
                                                    images,
                                                    domain.signal(ImageSource.bitmap(bitmap())),
                                                    domain.signal(ImageOptions.defaults()));
                                    Map<String, SceneMath.Embed> duplicate = new LinkedHashMap<>();
                                    duplicate.put(
                                            "a",
                                            new SceneMath.Embed(
                                                    image, new MathEmbedMetrics(1, 1, 0.8f)));
                                    duplicate.put("b", duplicate.get("a"));
                                    reject(
                                            () ->
                                                    SceneMath.create(
                                                            ctx,
                                                            domain.signal(MathContent.parse("")),
                                                            domain.signal(SceneTheme.dark()),
                                                            MathStyle.TEXT,
                                                            duplicate));
                                    require(image.parent() == null, "校验失败留下半树");
                                    image.focusable(true);
                                    reject(
                                            () ->
                                                    SceneMath.create(
                                                            ctx,
                                                            domain.signal(MathContent.parse("")),
                                                            domain.signal(SceneTheme.dark()),
                                                            MathStyle.TEXT,
                                                            Collections.singletonMap(
                                                                    "a", duplicate.get("a"))));
                                })) {
            page.frame();
        }
        final Signal<Boolean>[] focus = new Signal[1];
        final SceneNode[] formula = new SceneNode[1], icon = new SceneNode[1];
        try (SharedImageService images =
                        new SharedImageService(
                                source -> () -> source.bitmap,
                                SharedImageService.Budget.defaults());
                ScenePageSession page =
                        ScenePageSession.open(
                                domain,
                                host,
                                fonts,
                                ctx -> {
                                    focus[0] = domain.signal(false);
                                    icon[0] =
                                            SceneImage.create(
                                                            images,
                                                            domain.signal(
                                                                    ImageSource.bitmap(bitmap())),
                                                            domain.signal(ImageOptions.defaults()))
                                                    .bind(
                                                            NodeBinding.value(
                                                                    focus[0],
                                                                    (node, value) ->
                                                                            node.focusable(value)));
                                    formula[0] =
                                            SceneMath.create(
                                                    ctx,
                                                    domain.signal(
                                                            MathContent.parse(tex("@unknown{a}"))),
                                                    domain.signal(SceneTheme.dark()),
                                                    MathStyle.TEXT,
                                                    Collections.singletonMap(
                                                            "unused",
                                                            new SceneMath.Embed(
                                                                    icon[0],
                                                                    new MathEmbedMetrics(
                                                                            1, 1, 0.8f))));
                                    ctx.mount(formula[0]);
                                })) {
            page.frame();
            domain.execute(
                    () ->
                            require(
                                    SceneMath.layout(formula[0]).fallback
                                            && page.pipeline().current().geometry.get(icon[0])
                                                    == null,
                                    "反例未命中fallback无placement"));
            domain.execute(() -> focus[0].set(true));
            boolean failed = false;
            try {
                page.frame();
            } catch (IllegalStateException expected) {
                failed = "数学图像绑定不能获得主动交互资格".equals(expected.getMessage());
            }
            require(failed, "fallback无placement的图像通过binding获得focus资格而未failfast");
        }
    }

    private static void reject(Runnable action) {
        boolean failed = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            failed = true;
        }
        require(failed, "非法slot未拒绝");
    }

    private static int dependents(Object signal) throws Exception {
        Field field = signal.getClass().getSuperclass().getDeclaredField("dependents");
        field.setAccessible(true);
        return ((Set<?>) field.get(signal)).size();
    }

    private static void healthy(MathLayout value) {
        require(
                value != null && !value.fallback,
                "有效公式降级: "
                        + (value == null
                                ? "null"
                                : value.diagnostics.stream()
                                        .map(d -> d.message)
                                        .collect(java.util.stream.Collectors.joining(";"))));
    }

    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.003f, message + ": " + actual + " != " + expected);
    }

    private static void placement(Fixture f, String id) {
        MathLayout.Embed e =
                f.layout().embeds.stream().filter(value -> value.id.equals(id)).findFirst().get();
        LayoutBox parent = f.geometry().get(f.math).box,
                box = f.geometry().get(f.nodes.get(id)).box;
        int x = (int) Math.floor(f.math.padding() + e.x),
                y = (int) Math.floor(f.math.padding() + e.y);
        require(
                box.x == parent.x + x
                        && box.y == parent.y + y
                        && box.width == (int) Math.ceil(f.math.padding() + e.x + e.width) - x
                        && box.height == (int) Math.ceil(f.math.padding() + e.y + e.height) - y,
                "slot未按同代float封闭边界排列");
    }
}
