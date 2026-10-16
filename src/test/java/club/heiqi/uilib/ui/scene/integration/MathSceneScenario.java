package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
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
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathFont;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        scenario("真实native数量装饰固定字号探针", this::quantityDecorationProbe);
        // 旧实现可能在小盒收敛循环挂起；独立探针模式只测三个固定字号，父代理先取证再跑回归。
        if (java.util.Arrays.asList(arguments()).contains("--quantity-decoration-probe")) return;
        scenario("真实公式同代glyph浮点投影与分式脚本图像", this::geometry);
        scenario("数学em内容盒明确分配不自动求解节点Size", this::explicitEmbedBox);
        scenario("伸缩定界符二项式和大根式动态Item同代投影", this::stretchStructures);
        scenario("数量32真实墨迹随公式盒缩放及提示独立字号", this::quantity);
        scenario("三像素小盒带装饰数量完整保留与有界缩小", this::quantityDecoration);
        scenario("被动hover祖先clip窄视口滚动", this::input);
        scenario("数学只换颜色保留显式同色与真实定位和有序投影", this::colorRefresh);
        scenario("来源主题字号字体重载与整式错误恢复", this::dynamic);
        scenario("绑定唯一身份passive资格及卸载释放", this::lifecycle);
        scenario("装饰有序图元夹多个Item及phantom失去资格", this::decorations);
        scenario("纯滚动复用公式和超预算长文测量而输入变化重测", this::scrollMeasurement);
        scenario("尺寸与盒压深度公式滚动到底保留分母墨迹", this::smashScrollExtent);
        scenario("压高度与重叠公式动态包络及同帧图像输入", this::presentationExtent);
    }

    private void smashScrollExtent() throws Exception {
        String formula =
                tex(
                        "@phantom{XX}x+@hphantom{@embed{family}}y+@smash[b]{@frac{a}{b}}+@raisebox{.5em}{z}+@rule[.2em]{1em}{.1em}");
        for (float em : new float[] {28, 38}) {
            try (Fixture f = new Fixture(formula, MathStyle.DISPLAY, em, "", "family")) {
                f.width = 800;
                f.height = 24;
                f.frame();
                for (int i = 0; i < 10; i++) {
                    f.page.pointer(ScenePointerEvent.scroll(5, 5, -1));
                    f.frame();
                }
                f.domain.execute(
                        () -> {
                            healthy(f.layout());
                            SceneGeometryView.Entry view = f.geometry().get(f.viewport);
                            require(
                                    view.scrollMaximum > 0
                                            && view.scrollOffset == view.scrollMaximum,
                                    "反例没有到达实际滚动底部");
                            float bottom = Float.NEGATIVE_INFINITY;
                            for (PaintCommand cmd : f.paint.commands()) {
                                if (cmd.kind != PaintCommand.Kind.TEXT) continue;
                                for (TextLayout.Line line : cmd.text.lines())
                                    for (TextLayout.Glyph glyph : line.glyphs())
                                        bottom =
                                                Math.max(
                                                        bottom,
                                                        cmd.box.y
                                                                + glyph.y
                                                                + glyph.inkBounds.bottom);
                            }
                            float clipBottom = view.box.y + view.box.height;
                            System.out.println(
                                    "smash-scroll em="
                                            + em
                                            + " logicalHeight="
                                            + f.layout().height
                                            + " scroll="
                                            + view.scrollOffset
                                            + "/"
                                            + view.scrollMaximum
                                            + " inkBottom="
                                            + bottom
                                            + " clipBottom="
                                            + clipBottom
                                            + " missing="
                                            + Math.max(0, bottom - clipBottom));
                            require(bottom <= clipBottom, "滚动到底仍裁掉尺寸与盒分母：" + (bottom - clipBottom));
                            require(
                                    f.geometry().get(f.nodes.get("family")) == null,
                                    "hphantom仍有图像命中资格");
                        });
            }
        }
    }

    private void presentationExtent() throws Exception {
        String[] formulas = {
            tex("x+@smash[b]{@frac{a}{@embed{ore}}}"),
            tex("x+@smash[t]{@frac{@embed{ore}}{b}}"),
            tex(
                    "x+@smash{@raisebox{2em}{@embed{ore}}}+@smash{@rule[-2em]{1em}{.5em}}+@smash{@cancel{@frac{a}{b}}}"),
            tex("@mathllap{@embed{ore}}x+@mathrlap{@frac{a}{b}}")
        };
        try (Fixture f = new Fixture(formulas[0], MathStyle.DISPLAY, "ore")) {
            f.width = 800;
            f.height = 500;
            f.ready();
            SceneNode identity = f.nodes.get("ore");
            for (int i = 0; i < formulas.length; i++) {
                final int index = i;
                f.domain.execute(
                        () -> {
                            f.content.set(MathContent.parse(formulas[index]));
                            f.theme.set(
                                    (index % 2 == 0 ? SceneTheme.dark() : SceneTheme.light())
                                            .withTextStyle(
                                                    new TextStyle(
                                                            "default",
                                                            index % 2 == 0 ? 28 : 38,
                                                            -1)));
                        });
                f.page.pointer(ScenePointerEvent.move(-1, -1));
                f.ready();
                f.domain.execute(
                        () -> {
                            healthy(f.layout());
                            placement(f, "ore");
                            require(
                                    identity == f.nodes.get("ore") && f.leases == 1,
                                    "动态包络重建图像或泄漏lease");
                            LayoutBox box = f.geometry().get(f.math).box;
                            for (PaintCommand command : f.paint.commands()) {
                                if (command.kind == PaintCommand.Kind.TEXT) {
                                    for (TextLayout.Line line : command.text.lines())
                                        for (TextLayout.Glyph glyph : line.glyphs()) {
                                            if (glyph.inkBounds.right <= glyph.inkBounds.left
                                                    || glyph.inkBounds.bottom
                                                            <= glyph.inkBounds.top) continue;
                                            require(
                                                    command.box.x + glyph.x + glyph.inkBounds.left
                                                                    >= box.x
                                                            && command.box.y
                                                                            + glyph.y
                                                                            + glyph.inkBounds.top
                                                                    >= box.y
                                                            && command.box.x
                                                                            + glyph.x
                                                                            + glyph.inkBounds.right
                                                                    <= box.x + box.width
                                                            && command.box.y
                                                                            + glyph.y
                                                                            + glyph.inkBounds.bottom
                                                                    <= box.y + box.height,
                                                    "动态公式字形溢出自然尺寸");
                                        }
                                } else
                                    require(
                                            command.box.x >= box.x
                                                    && command.box.y >= box.y
                                                    && command.box.x + command.box.width
                                                            <= box.x + box.width
                                                    && command.box.y + command.box.height
                                                            <= box.y + box.height,
                                            "公式图元或嵌入溢出自然尺寸");
                            }
                        });
                LayoutBox[] observed = new LayoutBox[1];
                f.domain.execute(() -> observed[0] = f.geometry().get(identity).box);
                LayoutBox image = observed[0];
                f.page.pointer(
                        ScenePointerEvent.move(
                                image.x + image.width / 2, image.y + image.height / 2));
                f.frame();
                f.domain.execute(
                        () ->
                                require(
                                        f.page.runtime()
                                                        .pointerRouter()
                                                        .passiveHovered(f.geometry())
                                                == identity,
                                        "新包络图像悬停未使用当前几何"));
                f.height = 24;
                f.frame();
                f.domain.execute(
                        () -> {
                            SceneGeometryView.Entry entry = f.geometry().get(identity),
                                    view = f.geometry().get(f.viewport);
                            int delta = view.scrollMaximum - view.scrollOffset;
                            observed[0] =
                                    new LayoutBox(
                                                    entry.box.x,
                                                    entry.box.y - delta,
                                                    entry.box.width,
                                                    entry.box.height)
                                            .intersect(entry.clip);
                        });
                LayoutBox visible = observed[0];
                // 滚轮和move先同批采集，再只执行一次frame；每事件prepare应使用新排列。
                for (int step = 0; step < 10; step++)
                    f.page.pointer(ScenePointerEvent.scroll(5, 5, -1));
                if (visible.width > 0 && visible.height > 0)
                    f.page.pointer(
                            ScenePointerEvent.move(
                                    visible.x + visible.width / 2, visible.y + visible.height / 2));
                f.frame();
                f.domain.execute(
                        () -> {
                            SceneGeometryView.Entry view = f.geometry().get(f.viewport);
                            require(view.scrollOffset == view.scrollMaximum, "同帧滚轮批次没有滚动到底");
                            if (visible.width > 0 && visible.height > 0)
                                require(
                                        f.page.runtime()
                                                        .pointerRouter()
                                                        .passiveHovered(f.geometry())
                                                == identity,
                                        "同帧滚动后悬停仍使用旧原点");
                        });
                f.height = 500;
                f.frame();
            }
            fonts.reload(fonts.defaultCandidates());
            f.frame();
            f.domain.execute(
                    () -> {
                        healthy(f.layout());
                        placement(f, "ore");
                    });
        }
    }

    private static final class CountedText implements SceneTextMeasurer {
        private final FontTextService delegate;
        private int textCalls, mathCalls, nonemptyTextCalls;

        CountedText(FontTextService delegate) {
            this.delegate = delegate;
        }

        public TextLayout layout(String content, TextStyle style, int width) {
            return layout(content, style, width, TextPolicy.NORMAL);
        }

        public TextLayout layout(String content, TextStyle style, int width, TextPolicy policy) {
            textCalls++;
            if (!content.isEmpty()) nonemptyTextCalls++;
            return delegate.layout(content, style, width, policy);
        }

        public TextLayout layout(AttributedText content, float width, TextPolicy policy) {
            textCalls++;
            return delegate.layout(content, width, policy);
        }

        public TextStyle resolveStyle(TextStyle style) {
            return delegate.resolveStyle(style);
        }

        public MathFont mathFont(TextStyle style) {
            mathCalls++;
            return delegate.mathFont(style);
        }

        void reset() {
            textCalls = mathCalls = nonemptyTextCalls = 0;
        }
    }

    private void scrollMeasurement() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        CountedText counted = new CountedText(fonts);
        final int[] width = {440};
        final SceneNode[] formula = new SceneNode[1], viewport = new SceneNode[1];
        final Signal<MathContent>[] content = new Signal[1];
        final Signal<SceneTheme>[] theme = new Signal[1];
        StringBuilder longText = new StringBuilder();
        // 超过字体全局文本缓存预算；未改内容的滚动应复用节点当前测量事实。
        for (int i = 0; i < 14000; i++) longText.append(i % 2 == 0 ? 'x' : ' ');
        SceneHost host =
                new SceneHost() {
                    public int width() {
                        return width[0];
                    }

                    public int height() {
                        return 180;
                    }

                    public long metricsRevision() {
                        return fonts.generation();
                    }

                    public void replay(PaintPlan plan) {}
                };
        try (ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        counted,
                        ctx -> {
                            content[0] =
                                    domain.signal(
                                            MathContent.parse(
                                                    tex("@left(@frac{x}{1+x}@middle|y@right)")));
                            theme[0] =
                                    domain.signal(
                                            SceneTheme.dark()
                                                    .withTextStyle(
                                                            new TextStyle("default", 28, -1)));
                            formula[0] =
                                    SceneMath.create(
                                            ctx,
                                            content[0],
                                            theme[0],
                                            MathStyle.DISPLAY,
                                            Collections.emptyMap());
                            viewport[0] =
                                    new SceneNode()
                                            .size(Size.fill(1), Size.fill(1))
                                            .scrollable(true)
                                            .clip(true)
                                            .add(formula[0])
                                            .add(
                                                    SceneLabel.create(
                                                            ctx,
                                                            domain.signal(longText.toString()),
                                                            theme[0]));
                            ctx.mount(viewport[0]);
                        })) {
            page.frame();
            MathLayout first = SceneMath.layout(formula[0]);
            healthy(first);
            final int[] before = new int[1];
            domain.execute(
                    () -> before[0] = page.pipeline().current().geometry.get(formula[0]).box.y);
            counted.reset();
            for (int i = 0; i < 5; i++) page.pointer(ScenePointerEvent.scroll(10, 10, -1));
            page.frame();
            require(SceneMath.layout(formula[0]) == first && counted.mathCalls == 0, "纯滚动重算未变公式");
            require(counted.textCalls == 0, "纯滚动重新整形超预算长文");
            domain.execute(
                    () -> {
                        require(
                                page.pipeline().current().geometry.get(formula[0]).box.y
                                        < before[0],
                                "复用测量冻结了滚动几何");
                        require(
                                page.pipeline().current().geometry.get(formula[0]).clip != null,
                                "滚动丢失实际裁剪");
                    });
            domain.execute(() -> content[0].set(MathContent.parse("z^2")));
            page.frame();
            require(SceneMath.layout(formula[0]) != first && counted.mathCalls > 0, "内容变化没有重测");
            MathLayout changed = SceneMath.layout(formula[0]);
            domain.execute(
                    () ->
                            theme[0].set(
                                    SceneTheme.light()
                                            .withTextStyle(new TextStyle("default", 40, -1))));
            page.frame();
            require(SceneMath.layout(formula[0]) != changed, "字号变化没有重测");
            changed = SceneMath.layout(formula[0]);
            fonts.reload(fonts.defaultCandidates());
            page.frame();
            require(SceneMath.layout(formula[0]) != changed, "字体度量换代没有重测");
            for (MathLayout.TextPiece piece : SceneMath.layout(formula[0]).pieces)
                require(piece.text.generation == fonts.generation(), "字体重载混用旧代际");
            counted.reset();
            width[0] = 310;
            page.frame();
            require(counted.textCalls > 0, "视口变化没有重测长文约束");
            domain.execute(() -> page.runtime().captureMount(viewport[0]).detach());
            page.frame();
            domain.execute(
                    () ->
                            require(
                                    page.pipeline().current().geometry.get(formula[0]) == null,
                                    "卸载后仍有缓存几何"));
        }
    }

    private static ImageContent bitmap() {
        return bitmap("");
    }

    private static ImageContent bitmap(String quantity) {
        byte[] rgba = new byte[16 * 16 * 4];
        for (int i = 0; i < rgba.length; i += 4) {
            rgba[i] = (byte) 230;
            rgba[i + 1] = (byte) 120;
            rgba[i + 3] = (byte) 255;
        }
        return new ImageContent(16, 16, rgba) {
            @Override
            public ItemImageFacts itemFacts() {
                return new ItemImageFacts(quantity, "中性图像提示", false, 1, -1);
            }
        };
    }

    private final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final CountedText text = new CountedText(fonts);
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
            this(formula, mathStyle, EM, "", ids);
        }

        Fixture(String formula, MathStyle mathStyle, float em, String quantity, String... ids) {
            this(
                    formula,
                    mathStyle,
                    new TextStyle(
                            "default",
                            em,
                            -1,
                            null,
                            club.heiqi.uilib.ui.scene.text.TextEffects.NORMAL,
                            club.heiqi.uilib.ui.scene.text.TextDecoration.NONE),
                    quantity,
                    ids);
        }

        Fixture(
                String formula,
                MathStyle mathStyle,
                TextStyle textStyle,
                String quantity,
                String... ids) {
            this(formula, mathStyle, textStyle, quantity, ImageOptions.defaults(), ids);
        }

        Fixture(
                String formula,
                MathStyle mathStyle,
                TextStyle textStyle,
                String quantity,
                ImageOptions embedOptions,
                String... ids) {
            page =
                    ScenePageSession.open(
                            domain,
                            host,
                            text,
                            ctx -> {
                                content = domain.signal(MathContent.parse(formula));
                                theme = domain.signal(SceneTheme.dark().withTextStyle(textStyle));
                                source = domain.signal(ImageSource.bitmap(bitmap(quantity)));
                                Map<String, SceneMath.Embed> bindings = new LinkedHashMap<>();
                                for (String id : ids) {
                                    SceneNode image =
                                            SceneImage.themed(
                                                    counted,
                                                    source,
                                                    domain.signal(
                                                            embedOptions
                                                                    .quantity(!quantity.isEmpty())
                                                                    .tooltip(true)),
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
            StringBuilder failure = new StringBuilder("共享图像未及时READY");
            domain.execute(
                    () -> {
                        MathLayout value = layout();
                        if (value != null)
                            for (MathLayout.Diagnostic d : value.diagnostics)
                                failure.append(" [")
                                        .append(d.stage)
                                        .append(" ")
                                        .append(d.start)
                                        .append(":")
                                        .append(d.end)
                                        .append(" ")
                                        .append(d.message)
                                        .append("]");
                    });
            throw new AssertionError(failure.toString());
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

    private void stretchStructures() throws Exception {
        String[] formulas = {
            tex("@left@{@frac{1}{@frac{1}{@frac{@embed{ore}}{x}}}@middle|@binom{n}{k}@right@}^{2}"),
            tex("@sqrt{@frac{1}{@frac{1}{@frac{1}{@frac{@embed{ore}}{x}}}}}"),
            tex("@binom{@embed{ore}+n}{k}+@left.@frac{x}{1+x}@right|_{x=0}")
        };
        for (String formula : formulas) {
            try (Fixture f = new Fixture(formula, MathStyle.DISPLAY, 28, "32", "ore")) {
                f.ready();
                f.domain.execute(
                        () -> {
                            healthy(f.layout());
                            placement(f, "ore");
                            require(f.layout().embeds.size() == 1, "结构式丢失Item绑定");
                            if (formula.startsWith(tex("@sqrt")))
                                require(
                                        f.layout().pieces.stream()
                                                .anyMatch(
                                                        p ->
                                                                p.text.lines().stream()
                                                                        .anyMatch(
                                                                                line ->
                                                                                        line.glyphs()
                                                                                                                .size()
                                                                                                        > 1
                                                                                                && p
                                                                                                        .text
                                                                                                        .logicalText
                                                                                                        .equals(
                                                                                                                "√"))),
                                        "大型根式未用真实部件");
                        });
                f.domain.execute(() -> quantityFits(quantityObservations(f, "stretch-normal")));
                f.domain.execute(
                        () ->
                                f.theme.set(
                                        SceneTheme.light()
                                                .withTextStyle(new TextStyle("default", 38, -1))));
                f.frame();
                f.domain.execute(
                        () -> {
                            healthy(f.layout());
                            placement(f, "ore");
                        });
                f.domain.execute(() -> quantityFits(quantityObservations(f, "stretch-light38")));
                fonts.reload(fonts.defaultCandidates());
                f.frame();
                f.domain.execute(
                        () -> {
                            healthy(f.layout());
                            placement(f, "ore");
                            for (MathLayout.TextPiece piece : f.layout().pieces)
                                require(
                                        piece.text.generation == fonts.generation(),
                                        "伸缩结构重载数学facts混代");
                            require(
                                    f.geometry().get(f.math).text.generation == fonts.generation(),
                                    "伸缩结构重载投影混代");
                        });
                f.domain.execute(() -> f.content.set(MathContent.parse(tex("@left(@embed{ore}"))));
                f.frame();
                f.domain.execute(
                        () ->
                                require(
                                        f.layout().fallback
                                                && f.geometry().get(f.nodes.get("ore")) == null,
                                        "错配伸缩公式未整式诊断或残留Item"));
                f.domain.execute(() -> f.content.set(MathContent.parse(formula)));
                f.frame();
                f.domain.execute(
                        () -> {
                            healthy(f.layout());
                            placement(f, "ore");
                        });
            }
        }
    }

    private void explicitEmbedBox() throws Exception {
        // specs/content/uilib-math-content-spec.md 第 2 节：em 内联盒明确分配，节点声明不参与自然尺寸/min/max 求解。
        try (Fixture f = new Fixture(tex("x^{@embed{ore}}"), MathStyle.TEXT,
                SceneTheme.dark().textStyle.withEm(EM), "",
                ImageOptions.defaults().size(Size.fixed(120).bounds(80, 120), Size.fixed(120).bounds(80, 120)),
                "ore")) {
            f.ready();
            f.domain.execute(() -> {
                healthy(f.layout());
                placement(f, "ore");
                // 尺寸必须来自正式options绑定；直接node.size会被挂载时的绑定重写。
                SceneNode image = f.nodes.get("ore");
                for (Size size : new Size[] {image.width(), image.height()})
                    require(size.mode == Size.Mode.FIXED && size.value == 120
                                    && size.min == 80 && size.max == 120,
                            "挂载后的图像声明没有保留硬尺寸反例");
                LayoutBox before = f.geometry().get(image).box;
                require(before.width < 80 && before.height < 80,
                        "反例未触及与节点Size冲突的数学脚本em盒");
            });
            f.domain.execute(() -> f.theme.set(SceneTheme.light().withTextStyle(new TextStyle("default", 38, -1))));
            f.frame();
            f.domain.execute(() -> {
                healthy(f.layout());
                placement(f, "ore");
                require(f.geometry().get(f.nodes.get("ore")).box.width < 80,
                        "动态主题后数学em盒被节点min替代");
            });
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
                                entry.box.height == entry.text.height + f.math.padding() * 2,
                                "padding或公式extent重复计入");
                        require(
                                entry.text.height >= (int) Math.ceil(layout.height),
                                "普通font lineheight替代公式extent");
                        float[] shift = presentationShift(f);
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
                                    near(a.x, g.x + p.x + shift[0], "glyph浮点x");
                                    near(a.y, g.y + p.y + shift[1], "glyph负y/基线");
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

    private void quantity() throws Exception {
        // 同一来源供多个独立身份使用；数量必须实际进入 IMAGE.readyOverlay，不能只检查样式值。
        String formula =
                tex(
                        "@embed{body}+@frac{@embed{frac}}{2}+@sum@limits_{@embed{lower}}^{@embed{upper}}+x^{@embed{script}}");
        for (float em : new float[] {28, 38}) {
            try (Fixture f =
                    new Fixture(
                            formula,
                            MathStyle.TEXT,
                            em,
                            "32",
                            "body",
                            "frac",
                            "lower",
                            "upper",
                            "script")) {
                f.ready();
                Map<String, QuantityInk> normal = new LinkedHashMap<>();
                f.domain.execute(
                        () -> {
                            normal.putAll(quantityObservations(f, "normal"));
                            quantityFits(normal);
                            for (String id : normal.keySet()) {
                                if (!id.equals("body")) {
                                    require(
                                            normal.get(id).image.image.height
                                                    < normal.get("body").image.image.height,
                                            id + "未命中分式/limits/script缩小盒");
                                    require(
                                            normal.get(id).height() < normal.get("body").height(),
                                            id + "数量墨迹未随公式脚本盒缩小");
                                }
                            }
                        });
                quantityTooltip(f, "upper", em);
                f.domain.execute(
                        () -> f.content.set(MathContent.parse(tex("@scriptstyle ") + formula)));
                f.frame();
                f.domain.execute(
                        () -> {
                            Map<String, QuantityInk> small = quantityObservations(f, "scriptstyle");
                            quantityFits(small);
                            for (String id : normal.keySet()) {
                                require(
                                        small.get(id).image.image.height
                                                < normal.get(id).image.image.height,
                                        id + "公式缩小没有进入图像placement");
                                require(
                                        small.get(id).height() < normal.get(id).height(),
                                        id + "缩小后数量仍保留原墨迹大小");
                            }
                        });
                quantityTooltip(f, "upper", em);
                f.domain.execute(
                        () -> {
                            f.content.set(MathContent.parse(formula));
                            f.theme.set(
                                    SceneTheme.light()
                                            .withTextStyle(
                                                    new TextStyle(
                                                            "default",
                                                            em,
                                                            0xff123456,
                                                            null,
                                                            club.heiqi.uilib.ui.scene.text
                                                                    .TextEffects.NORMAL,
                                                            club.heiqi.uilib.ui.scene.text
                                                                    .TextDecoration.NONE)));
                        });
                f.frame();
                f.domain.execute(
                        () -> {
                            Map<String, QuantityInk> restored =
                                    quantityObservations(f, "normal-light");
                            quantityFits(restored);
                            for (String id : normal.keySet()) {
                                near(
                                        restored.get(id).height(),
                                        normal.get(id).height(),
                                        id + "normal恢复数量墨迹");
                                near(
                                        restored.get(id).image.image.height,
                                        normal.get(id).image.image.height,
                                        id + "normal恢复图像盒");
                            }
                        });
                quantityTooltip(f, "upper", em);
            }
        }
    }

    private void decorations() throws Exception {
        String formula =
                tex("@colorbox{yellow}{$@cancel{@embed{a}}$}x@fcolorbox{blue}{cyan}{$@embed{b}$}y");
        try (Fixture f = new Fixture(formula, MathStyle.TEXT, "a", "b", "hidden")) {
            f.ready();
            f.domain.execute(
                    () -> {
                        healthy(f.layout());
                        java.util.List<PaintCommand> commands = f.paint.commands();
                        int a = -1, b = -1, bg = -1, line = -1, between = -1;
                        for (int i = 0; i < commands.size(); i++) {
                            PaintCommand cmd = commands.get(i);
                            if (cmd.kind == PaintCommand.Kind.IMAGE) {
                                if (a < 0) a = i;
                                else b = i;
                            }
                            if (cmd.kind == PaintCommand.Kind.RECT && cmd.color == 0xffffff00)
                                bg = i;
                            if (cmd.kind == PaintCommand.Kind.LINE) line = i;
                            if (cmd.kind == PaintCommand.Kind.TEXT && a >= 0 && b < 0) between = i;
                        }
                        require(
                                bg < a && a < line && line < between && between < b,
                                "绘制不是背景→Item→cancel→邻text→下一Item");
                        require(
                                commands.stream()
                                                .filter(c -> c.kind == PaintCommand.Kind.IMAGE)
                                                .count()
                                        == 2,
                                "重复绘制Item");
                        require(
                                commands.stream()
                                                .filter(
                                                        c ->
                                                                c.kind == PaintCommand.Kind.RECT
                                                                        && c.color == 0xff0000ff)
                                                .count()
                                        == 4,
                                "边框颜色/4RECT丢失");
                        require(f.geometry().get(f.nodes.get("hidden")) == null, "非活动slot仍有资源几何");
                    });
            quantityTooltip(f, "a", EM);
            f.domain.execute(
                    () -> f.content.set(MathContent.parse(tex("@phantom{@embed{a}}+@embed{b}"))));
            f.frame();
            f.domain.execute(
                    () -> {
                        require(
                                f.geometry().get(f.nodes.get("a")) == null,
                                "phantom Item保留输入/资源资格");
                        require(
                                f.paint.commands().stream()
                                                .filter(c -> c.kind == PaintCommand.Kind.IMAGE)
                                                .count()
                                        == 1,
                                "phantom仍产生IMAGE");
                    });
            f.domain.execute(
                    () -> f.content.set(MathContent.parse(tex("@embed{b}+@cancel{@embed{a}}"))));
            f.frame();
            f.domain.execute(
                    () -> {
                        placement(f, "a");
                        placement(f, "b");
                        java.util.List<PaintCommand> commands = f.paint.commands();
                        int lastImage = -1, lastLine = -1;
                        for (int i = 0; i < commands.size(); i++) {
                            if (commands.get(i).kind == PaintCommand.Kind.IMAGE) lastImage = i;
                            if (commands.get(i).kind == PaintCommand.Kind.LINE) lastLine = i;
                        }
                        require(lastLine > lastImage, "最后suffix未在Item子树关闭后绘制");
                    });
            quantityTooltip(f, "a", EM);
        }
    }

    private static TextStyle underlinedQuantityStyle(float em) {
        return new TextStyle(
                "default",
                em,
                -1,
                null,
                new club.heiqi.uilib.ui.scene.text.TextEffects(
                        false, false, true, false, false, 0, 0),
                new club.heiqi.uilib.ui.scene.text.TextDecoration(1, 2, 0, 0, 0));
    }

    private void quantityDecorationProbe() {
        // 三个固定输入直接调用真实FT/HB；不进入SceneImage，也不执行任何收敛循环。
        for (float em : new float[] {1.5f, .1f, .001f}) {
            TextLayout text =
                    fonts.layout(
                            "32",
                            underlinedQuantityStyle(em),
                            Integer.MAX_VALUE,
                            club.heiqi.uilib.ui.scene.text.TextPolicy.SINGLE_LINE);
            QuantityInk ink =
                    new QuantityInk(
                            null,
                            PaintCommand.text(
                                    new LayoutBox(0, 0, text.width, text.height), null, text));
            int decorations = 0;
            for (TextLayout.Line line : text.lines())
                for (TextLayout.Decoration decoration : line.decorations()) {
                    decorations++;
                    require(decoration.height >= 2, "探针未命中绝对2px装饰");
                }
            require(decorations > 0, "真实probe未生成underline");
            System.out.println(
                    "quantity-decoration-probe em="
                            + em
                            + " glyphAndDecorationInk="
                            + ink.left
                            + ","
                            + ink.top
                            + ","
                            + ink.right
                            + ","
                            + ink.bottom
                            + " height="
                            + ink.height()
                            + " maxSmallBoxInkHeight="
                            + (3f * .5f)
                            + " decorationCount="
                            + decorations);
        }
    }

    private void quantityDecoration() throws Exception {
        String formula = tex("@embed{body}+@frac{@embed{frac}}{2}");
        for (String quantity : new String[] {"32"}) {
            try (Fixture f =
                    new Fixture(
                            formula,
                            MathStyle.TEXT,
                            underlinedQuantityStyle(3),
                            quantity,
                            "body",
                            "frac")) {
                f.ready();
                f.domain.execute(
                        () -> {
                            Map<String, QuantityInk> values =
                                    quantityObservations(f, "tiny-underlined-" + quantity);
                            quantityFits(values);
                            near(values.get("body").image.image.height, 3, "未命中3px宿主图像盒");
                            for (Map.Entry<String, QuantityInk> entry : values.entrySet()) {
                                QuantityInk ink = entry.getValue();
                                require(quantity.equals(ink.count.text.logicalText), "小盒截断数量");
                                near(
                                        f.nodes
                                                .get(entry.getKey())
                                                .style()
                                                .decoration
                                                .underlineThickness,
                                        2,
                                        "小盒缩放改写声明装饰");
                                float scale = ink.count.text.style.logicalEm / 3;
                                require(scale > 0 && scale < 1, "小盒数量没有选择有限正缩放");
                                int decorations = 0;
                                for (TextLayout.Line line : ink.count.text.lines())
                                    for (TextLayout.Decoration decoration : line.decorations()) {
                                        decorations++;
                                        require(
                                                decoration.kind
                                                        == TextLayout.Decoration.Kind.UNDERLINE,
                                                "声明underline丢失");
                                        near(decoration.height, 2 * scale, "数量装饰厚度未随数量缩小");
                                    }
                                require(decorations > 0, "小盒以删除装饰绕过收敛");
                            }
                        });
            }
        }
    }

    private void quantityTooltip(Fixture f, String id, float em) {
        LayoutBox[] box = {null};
        f.domain.execute(() -> box[0] = f.geometry().get(f.nodes.get(id)).box);
        f.page.pointer(
                ScenePointerEvent.move(box[0].x + box[0].width / 2, box[0].y + box[0].height / 2));
        f.frame();
        f.domain.execute(
                () -> {
                    PaintCommand tip =
                            f.paint.commands().stream()
                                    .filter(
                                            cmd ->
                                                    cmd.kind == PaintCommand.Kind.TEXT
                                                            && "中性图像提示"
                                                                    .equals(cmd.text.logicalText))
                                    .findFirst()
                                    .orElseThrow(() -> new AssertionError("数量场景提示没有沿正式hover出现"));
                    near(tip.text.style.logicalEm, em, "提示不应继承数量或公式script字号");
                    require(!tip.text.lines().isEmpty(), "提示没有真实字体行");
                    for (TextLayout.Line line : tip.text.lines())
                        for (TextLayout.Glyph glyph : line.glyphs())
                            near(glyph.logicalEm, em, "提示glyph沿独立主题字号");
                });
    }

    /** 观测正式计划的实际字形墨迹，不重写控件的字号选择、排版或锚定算法。 */
    private static final class QuantityInk {
        final PaintCommand image, count;
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;

        QuantityInk(PaintCommand image, PaintCommand count) {
            this.image = image;
            this.count = count;
            boolean[] sourceSeen = new boolean[count.text.logicalText.length()];
            int glyphs = 0;
            for (TextLayout.Line line : count.text.lines()) {
                for (TextLayout.Glyph glyph : line.glyphs()) {
                    require(glyph.inkBounds.known && !glyph.replacement, "数量没有真实字体墨迹");
                    require(
                            glyph.source != null
                                    && glyph.sourceStart >= 0
                                    && glyph.sourceEnd <= sourceSeen.length
                                    && glyph.sourceStart < glyph.sourceEnd,
                            "数量glyph源位置丢失");
                    for (int i = glyph.sourceStart; i < glyph.sourceEnd; i++) sourceSeen[i] = true;
                    left = Math.min(left, count.box.x + glyph.x + glyph.inkBounds.left);
                    top = Math.min(top, count.box.y + glyph.y + glyph.inkBounds.top);
                    right = Math.max(right, count.box.x + glyph.x + glyph.inkBounds.right);
                    bottom = Math.max(bottom, count.box.y + glyph.y + glyph.inkBounds.bottom);
                    glyphs++;
                    System.out.println(
                            "quantity32 glyph source=["
                                    + glyph.sourceStart
                                    + ","
                                    + glyph.sourceEnd
                                    + ") font="
                                    + glyph.source
                                    + " metadata="
                                    + glyph.source.metadata()
                                    + " em="
                                    + glyph.logicalEm
                                    + " baseline="
                                    + (count.box.y + glyph.y)
                                    + " lineHeight="
                                    + line.lineHeight);
                }
            }
            for (TextLayout.Line line : count.text.lines()) {
                for (TextLayout.Decoration decoration : line.decorations()) {
                    require(
                            decoration.sourceStart >= 0
                                    && decoration.sourceEnd <= sourceSeen.length,
                            "数量装饰来源位置丢失");
                    left = Math.min(left, count.box.x + decoration.x);
                    top = Math.min(top, count.box.y + decoration.y);
                    right = Math.max(right, count.box.x + decoration.x + decoration.width);
                    bottom = Math.max(bottom, count.box.y + decoration.y + decoration.height);
                    System.out.println(
                            "quantity decoration="
                                    + decoration.kind
                                    + " source=["
                                    + decoration.sourceStart
                                    + ","
                                    + decoration.sourceEnd
                                    + ")"
                                    + " width="
                                    + decoration.width
                                    + " height="
                                    + decoration.height);
                }
            }
            require(glyphs > 0, "数量未生成实际glyph");
            for (int i = 0; i < sourceSeen.length; i++) require(sourceSeen[i], "数量来源位置未完整保留: " + i);
            require(right > left && bottom > top, "数量墨迹为空");
        }

        float height() {
            return bottom - top;
        }
    }

    private static Map<String, QuantityInk> quantityObservations(Fixture f, String phase) {
        healthy(f.layout());
        Map<String, QuantityInk> result = new LinkedHashMap<>();
        int quantityCommands = 0;
        for (MathLayout.Embed embed : f.layout().embeds) {
            placement(f, embed.id);
            LayoutBox box = f.geometry().get(f.nodes.get(embed.id)).box;
            PaintCommand image =
                    f.paint.commands().stream()
                            .filter(
                                    cmd ->
                                            cmd.kind == PaintCommand.Kind.IMAGE
                                                    && cmd.box.x == box.x
                                                    && cmd.box.y == box.y
                                                    && cmd.box.width == box.width
                                                    && cmd.box.height == box.height)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError(embed.id + "缺少实际IMAGE"));
            require(image.readyOverlay != null, embed.id + "数量未进入成功分支");
            List<PaintCommand> counts =
                    image.readyOverlay.commands().stream()
                            .filter(
                                    cmd ->
                                            cmd.kind == PaintCommand.Kind.TEXT
                                                    && f.source
                                                            .get()
                                                            .bitmap
                                                            .itemFacts()
                                                            .quantity
                                                            .equals(cmd.text.logicalText))
                            .collect(java.util.stream.Collectors.toList());
            require(counts.size() == 1, embed.id + "成功分支数量TEXT计数=" + counts.size());
            quantityCommands += counts.size();
            QuantityInk ink = new QuantityInk(image, counts.get(0));
            result.put(embed.id, ink);
            System.out.println(
                    "quantity32 phase="
                            + phase
                            + " id="
                            + embed.id
                            + " themeEm="
                            + f.nodes.get(embed.id).style().logicalEm
                            + " formulaBox="
                            + embed.width
                            + "x"
                            + embed.height
                            + " image="
                            + image.image.x
                            + ","
                            + image.image.y
                            + ","
                            + image.image.width
                            + ","
                            + image.image.height
                            + " ink="
                            + ink.left
                            + ","
                            + ink.top
                            + ","
                            + ink.right
                            + ","
                            + ink.bottom);
        }
        require(
                result.size() == f.nodes.size() && quantityCommands == f.nodes.size(),
                "独立绑定与实际quantity TEXT数量不一致: " + quantityCommands);
        return result;
    }

    private static void quantityFits(Map<String, QuantityInk> values) {
        for (Map.Entry<String, QuantityInk> entry : values.entrySet()) {
            String id = entry.getKey();
            QuantityInk ink = entry.getValue();
            float right = ink.image.image.x + ink.image.image.width;
            float bottom = ink.image.image.y + ink.image.image.height;
            // 上限来自实机反馈；一像素余量只容纳场景整数原点量化，不能用font lineheight冒充墨迹。
            require(
                    ink.height() <= ink.image.image.height * .5f + .003f,
                    id
                            + "数量32盖住图像：inkHeight="
                            + ink.height()
                            + " imageHeight="
                            + ink.image.image.height);
            require(
                    ink.left >= ink.image.image.x - .003f
                            && ink.top >= ink.image.image.y - .003f
                            && ink.right <= right + .003f
                            && ink.bottom <= bottom + .003f,
                    id + "数量可见墨迹越过ImagePlacement");
            require(
                    right - ink.right <= 1.01f && bottom - ink.bottom <= 1.01f,
                    id
                            + "数量没有按可见墨迹锚定图像底右：rightGap="
                            + (right - ink.right)
                            + " bottomGap="
                            + (bottom - ink.bottom));
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

    private void colorRefresh() throws Exception {
        String source = tex("@sqrt{@frac{a}{@frac{b}{@frac{m}{n}}}}+@frac{a}{b}+@textcolor{white}{@frac{c}{d}}"
                + "+@textcolor{red}{@textcolor{currentColor}{e}}+@cancel{f}+@text{office}"
                + "+@embed{one}+@textcolor{white}{w}+@cancel{g}+@embed{two}"
                + "+@frac{h}{i}+@embed{three}+@overline{j}"
                + "+@fcolorbox{white}{white}{p}+@fcolorbox{white}{currentColor}{k}");
        try (Fixture f = new Fixture(source, MathStyle.DISPLAY, "one", "two", "three")) {
            f.width = 1800;
            f.height = 400;
            f.ready();
            MathLayout old = f.layout();
            PaintPlan oldPaint = f.paint;
            List<Integer> oldColors = paintColors(oldPaint);
            healthy(old);
            require(!old.rules.isEmpty() && !old.strokes.isEmpty(), "颜色反例没有真实线/路径");
            f.text.reset();
            // 全scene还会提交Item空文字及tooltip属性文字；数学入口是MathFont和非空普通文本。
            int color = 0xff123456;
            f.domain.execute(() -> f.theme.set(f.theme.get().withTextStyle(
                    f.theme.get().textStyle.withColor(color))));
            f.frame();
            require(f.text.mathCalls == 0 && f.text.nonemptyTextCalls == 0,
                    "纯颜色重新进入数学字体/文本整形入口");
            MathLayout current = f.layout();
            require(current != old, "颜色刷新没有发布新不可变数学载荷");
            sameMathGeometry(old, current);
            mathColors(current, source, color);
            mathColors(old, source, -1);
            require(paintColors(oldPaint).equals(oldColors), "旧PaintPlan被颜色刷新修改");
            List<Integer> expected = new java.util.ArrayList<>();
            for (MathLayout.Entry entry : current.entries) {
                if (entry.piece != null) {
                    for (TextLayout.Line line : entry.piece.text.lines())
                        for (TextLayout.Glyph glyph : line.glyphs()) expected.add(glyph.resolvedStyle.color);
                } else if (entry.rule != null) expected.add(entry.rule.color);
                else if (entry.stroke != null) expected.add(entry.stroke.color);
            }
            require(paintColors(f.paint).equals(expected), "多Item有序chunks仍持有旧色");
            f.domain.execute(() -> {
                List<Integer> projected = new java.util.ArrayList<>(), texts = new java.util.ArrayList<>();
                for (TextLayout.Line line : f.geometry().get(f.math).text.lines())
                    for (TextLayout.Glyph glyph : line.glyphs()) projected.add(glyph.resolvedStyle.color);
                for (MathLayout.TextPiece piece : current.pieces)
                    for (TextLayout.Line line : piece.text.lines())
                        for (TextLayout.Glyph glyph : line.glyphs()) texts.add(glyph.resolvedStyle.color);
                require(projected.equals(texts), "完整文字projection仍持有旧色");
            });
            f.domain.execute(() -> f.theme.set(f.theme.get().withTextStyle(f.theme.get().textStyle.withEm(40))));
            f.frame();
            require(f.text.mathCalls > 0, "字号变化没有重新布局");
            f.text.reset();
            fonts.reload(fonts.defaultCandidates());
            f.frame();
            require(f.text.mathCalls > 0, "字体资源换代没有重新布局");
            f.domain.execute(() -> f.content.set(MathContent.parse(tex("@unknown{x}"))));
            f.frame();
            require(f.layout().fallback, "整式原文反例没有进入正式fallback");
            MathLayout fallback = f.layout();
            f.text.reset();
            f.domain.execute(() -> f.theme.set(f.theme.get().withTextStyle(f.theme.get().textStyle.withColor(0xffabcdef))));
            f.frame();
            require(f.text.mathCalls == 0 && f.text.nonemptyTextCalls == 0, "fallback纯颜色重新整形源文");
            require(f.layout().sourceFallback.style.color == 0xffabcdef
                    && fallback.sourceFallback.style.color == color, "fallback当前载荷或旧快照颜色错误");
        }
        // 宏参数的两个可见使用拥有同一源区间，却分别继承与显式同色；区间/值替换无法判断。
        try (Fixture f = new Fixture(tex("@def@foo#1{#1+@textcolor{white}{#1}}@foo{x}"), MathStyle.DISPLAY)) {
            f.frame();
            MathLayout old = f.layout();
            healthy(old);
            require(old.pieces.size() == 3
                    && old.pieces.get(0).sourceStart == old.pieces.get(2).sourceStart,
                    "宏颜色反例没有共享实际来源区间");
            f.text.reset();
            f.domain.execute(() -> f.theme.set(f.theme.get().withTextStyle(f.theme.get().textStyle.withColor(0xff123456))));
            f.frame();
            require(f.text.mathCalls == 0 && f.text.nonemptyTextCalls == 0, "宏颜色刷新重新整形");
            sameMathGeometry(old, f.layout());
            require(f.layout().pieces.get(0).text.style.color == 0xff123456
                    && f.layout().pieces.get(2).text.style.color == -1, "同来源宏参数颜色绑定丢失");
        }
        String transparent = tex("a+@color{white}b@@c");
        try (Fixture f = new Fixture(transparent, MathStyle.DISPLAY)) {
            f.frame();
            MathLayout old = f.layout();
            healthy(old);
            f.text.reset();
            f.domain.execute(() -> f.theme.set(f.theme.get().withTextStyle(f.theme.get().textStyle.withColor(0xff123456))));
            f.frame();
            require(f.text.mathCalls == 0 && f.text.nonemptyTextCalls == 0, "顶层透明颜色刷新重新整形");
            sameMathGeometry(old, f.layout());
            for (MathLayout.TextPiece piece : f.layout().pieces) {
                String raw = transparent.substring(piece.sourceStart, piece.sourceEnd);
                require(piece.text.style.color == (raw.equals("b") || raw.equals("c") ? -1 : 0xff123456),
                        "顶层换行透明作用域丢失显式色");
            }
        }
    }

    private static List<Integer> paintColors(PaintPlan plan) {
        List<Integer> colors = new java.util.ArrayList<>();
        for (PaintCommand command : plan.commands()) {
            if (command.kind == PaintCommand.Kind.TEXT) {
                for (TextLayout.Line line : command.text.lines())
                    for (TextLayout.Glyph glyph : line.glyphs()) colors.add(glyph.resolvedStyle.color);
            } else if (command.kind == PaintCommand.Kind.RECT || command.kind == PaintCommand.Kind.LINE)
                colors.add(command.color);
        }
        return colors;
    }

    private static void mathColors(MathLayout layout, String source, int inherited) {
        int fixedFraction = source.indexOf(tex("@frac{c}{d}"));
        int fixedBox = source.indexOf(tex("@fcolorbox{white}{white}"));
        int mixedBox = source.indexOf(tex("@fcolorbox{white}{currentColor}")), mixedRules = 0;
        for (MathLayout.Entry entry : layout.entries) {
            if (entry.piece != null) {
                String literal = source.substring(entry.piece.sourceStart, entry.piece.sourceEnd);
                int expected = java.util.Arrays.asList("c", "d", "w").contains(literal)
                        ? -1 : literal.equals("e") ? 0xffff0000 : inherited;
                require(entry.piece.text.style.color == expected, "数学text颜色来源错误: " + literal);
                for (TextLayout.Line line : entry.piece.text.lines()) {
                    for (TextLayout.Glyph glyph : line.glyphs())
                        require(glyph.resolvedStyle.color == expected, "数学glyph颜色来源错误: " + literal);
                    for (TextLayout.Cluster cluster : line.clusters())
                        require(cluster.style.color == expected, "数学cluster颜色来源错误: " + literal);
                }
            } else if (entry.rule != null) {
                int start = entry.rule.sourceStart;
                int expected = start == fixedFraction || start == fixedBox ? -1
                        : start == mixedBox && mixedRules++ > 0 ? -1 : inherited;
                require(entry.rule.color == expected, "数学line/背景/边框颜色来源错误: " + start);
            } else if (entry.stroke != null)
                require(entry.stroke.color == inherited, "数学stroke没有跟随基础色");
        }
        require(mixedRules == 5, "混合背景与边框反例未触达五条真实图元");
    }

    private static void sameMathGeometry(MathLayout before, MathLayout after) {
        near(before.width, after.width, "只换色改变数学宽度");
        near(before.height, after.height, "只换色改变数学高度");
        near(before.baseline, after.baseline, "只换色改变数学基线");
        require(before.entries.size() == after.entries.size(), "只换色改变数学条目数");
        for (int i = 0; i < before.entries.size(); i++) {
            MathLayout.Entry a = before.entries.get(i), b = after.entries.get(i);
            require(a.kind == b.kind, "只换色改变图元顺序");
            if (a.piece != null) {
                near(a.piece.x, b.piece.x, "只换色移动文字X");
                near(a.piece.y, b.piece.y, "只换色移动文字Y");
                require(a.piece.text.generation == b.piece.text.generation, "只换色改变字体代际");
                for (int l = 0; l < a.piece.text.lines().size(); l++) {
                    List<TextLayout.Glyph> ga = a.piece.text.lines().get(l).glyphs(), gb = b.piece.text.lines().get(l).glyphs();
                    require(ga.size() == gb.size(), "只换色改变整形结果");
                    for (int g = 0; g < ga.size(); g++) {
                        TextLayout.Glyph x = ga.get(g), y = gb.get(g);
                        require(x.source == y.source && x.glyphId == y.glyphId
                                && x.inkBounds == y.inkBounds && x.paintClip == y.paintClip
                                && x.xExtension == y.xExtension, "只换色改变glyph资源或构建事实");
                        near(x.x, y.x, "只换色移动glyphX");
                        near(x.y, y.y, "只换色移动glyphY");
                        near(x.xAdvance, y.xAdvance, "只换色改变glyph advance");
                    }
                }
            } else if (a.rule != null) {
                near(a.rule.x, b.rule.x, "只换色移动线X");
                near(a.rule.y, b.rule.y, "只换色移动线Y");
                near(a.rule.width, b.rule.width, "只换色改变线宽");
                near(a.rule.height, b.rule.height, "只换色改变线高");
            } else if (a.stroke != null) {
                near(a.stroke.x1, b.stroke.x1, "只换色移动路径X1");
                near(a.stroke.y1, b.stroke.y1, "只换色移动路径Y1");
                near(a.stroke.x2, b.stroke.x2, "只换色移动路径X2");
                near(a.stroke.y2, b.stroke.y2, "只换色移动路径Y2");
                near(a.stroke.width, b.stroke.width, "只换色改变路径宽度");
            } else require(a.embed == b.embed, "只换色替换嵌入定位");
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

    /** 从正式文字投影观测外层原点，不镜像生产包络算法或读取私有缓存。 */
    private static float[] presentationShift(Fixture f) {
        int lineIndex = 0;
        TextLayout projected = f.geometry().get(f.math).text;
        for (MathLayout.TextPiece piece : f.layout().pieces) {
            for (TextLayout.Line line : piece.text.lines()) {
                TextLayout.Line actual = projected.lines().get(lineIndex++);
                if (!line.glyphs().isEmpty()) {
                    TextLayout.Glyph raw = line.glyphs().get(0), shifted = actual.glyphs().get(0);
                    return new float[] {shifted.x - raw.x - piece.x, shifted.y - raw.y - piece.y};
                }
            }
        }
        throw new AssertionError("原点观测场景缺少文字字形");
    }

    private static void placement(Fixture f, String id) {
        MathLayout.Embed e =
                f.layout().embeds.stream().filter(value -> value.id.equals(id)).findFirst().get();
        LayoutBox parent = f.geometry().get(f.math).box,
                box = f.geometry().get(f.nodes.get(id)).box;
        float[] shift = presentationShift(f);
        int x = (int) Math.floor(f.math.padding() + e.x + shift[0]),
                y = (int) Math.floor(f.math.padding() + e.y + shift[1]);
        require(
                box.x == parent.x + x
                        && box.y == parent.y + y
                        && box.width
                                == (int) Math.ceil(f.math.padding() + e.x + shift[0] + e.width) - x
                        && box.height
                                == (int) Math.ceil(f.math.padding() + e.y + shift[1] + e.height)
                                        - y,
                "slot未按同代float封闭边界排列");
    }
}
