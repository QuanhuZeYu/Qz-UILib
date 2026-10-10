package club.heiqi.uilib.ui.scene.integration;

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
        scenario("伸缩定界符二项式和大根式动态Item同代投影", this::stretchStructures);
        scenario("数量32真实墨迹随公式盒缩放及提示独立字号", this::quantity);
        scenario("三像素小盒带装饰数量完整保留与有界缩小", this::quantityDecoration);
        scenario("被动hover祖先clip窄视口滚动", this::input);
        scenario("来源主题字号字体重载与整式错误恢复", this::dynamic);
        scenario("绑定唯一身份passive资格及卸载释放", this::lifecycle);
        scenario("装饰有序图元夹多个Item及phantom失去资格", this::decorations);
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
            page =
                    ScenePageSession.open(
                            domain,
                            host,
                            fonts,
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
                                                            ImageOptions.defaults()
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
