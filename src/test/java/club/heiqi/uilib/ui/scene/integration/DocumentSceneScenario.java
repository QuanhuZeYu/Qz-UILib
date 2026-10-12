package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.DocumentResources;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneMath;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ItemImageFacts;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.InlineContent;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathFont;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 真实FT/HB、同树文档与正式frame/input；位图不冒充Minecraft物品绘制。 */
public final class DocumentSceneScenario extends TestApplication {
    FontTextService fonts;

    public static void main(String[] args) throws Exception {
        new DocumentSceneScenario().run(args);
    }

    @Override
    protected void setup() throws Exception {
        fonts = own(new FontTextService(new LwjglFontEngine()));
    }

    @Override
    protected void runTests() throws Exception {
        scenario("DSL公式捕获图像声明与整段非法内容边界", this::syntax);
        scenario("上下标数学图像同树基线与主题更新", this::scriptedInline);
        scenario("正式同树文字公式图像换行基线与动态重排", this::flow);
        scenario("链接公式图像统一点击提示与声明替换取消", this::interaction);
        scenario("同帧滚轮提示裁剪与纯滚动复用度量", this::scroll);
        scenario("结构化图像来源更新保持节点并重新测量", this::imageUpdate);
        scenario("段落公式块与后续正文使用同树布局", this::blocks);
        scenario("同段落多宽度捕获返回缓存文字时排列仍一致", this::captureRequests);
        scenario("typed共同段落来源映射与同树对象链接动态呈现", this::typedFlow);
        scenario("显式小行高对象保留溢出呈现与链接几何", this::tightObjects);
    }

    private static String tex(String text) {
        return text.replace('@', (char) 92);
    }

    private void scriptedInline() throws Exception {
        try (Fixture f = new Fixture("A<mark><sup><u><latex>x+1</latex></u></sup>B<sub><img src=\"ore\" width=\"24\"/></sub></mark>C")) {
            f.ready();
            f.domain.execute(() -> {
                SceneGeometryView geometry = f.geometry();
                TextLayout layout = geometry.get(f.paragraph).text;
                verify(f, geometry);
                SceneNode math = f.paragraph.children().get(0);
                require(geometry.get(math).text.lines().stream().flatMap(line -> line.glyphs().stream())
                        .anyMatch(g -> g.logicalEm < 28), "脚本公式未消费共同字体解析后的字号");
                require(layout.lines().stream().flatMap(line -> line.clusters().stream())
                        .anyMatch(c -> c.style.baselineShift < 0), "数学脚本缺少父层基线");
                require(layout.lines().stream().flatMap(line -> line.clusters().stream())
                        .anyMatch(c -> c.style.baselineShift > 0), "图像脚本缺少父层基线");
                f.theme.set(SceneTheme.light().withTextStyle(new TextStyle("default", 38, -1)));
            });
            f.page.frame();
            f.domain.execute(() -> verify(f, f.geometry()));
        }
    }

    private void syntax() {
        TextStyle style = new TextStyle("default", 28, -1);
        String source =
                "前<a href=\"next\"><latex math-style=\"display\">"
                        + tex("a<b+@frac{1}{2}")
                        + "</latex><img src=\"ore\" width=\"24\" alt=\"矿物\"/></a>后";
        TextContent content = TextContent.richTags(source);
        TextContent.Resolved r = content.resolve(style);
        require(r.inlineContents().size() == 2, "没有声明两个原子项");
        require(r.inlineContents().get(0) == content.inlineContents().get(0), "主题派生重建声明身份");
        require(r.inlineContents().get(0).math.source.contains("a<b"), "公式正文误解释DSL");
        require(r.linkAt(r.inlineContents().get(1).offset) != null, "图像没有保留链接范围");
        require(
                TextContent.richTags(r.serializeRichTags()).resolve(style).inlineContents().size()
                        == 2,
                "对象序列化语义不能往返");
        TextContent.Resolved bad = TextContent.richTags(source + "<unknown>").resolve(style);
        require(
                bad.inlineContents().isEmpty()
                        && bad.links().isEmpty()
                        && !bad.text.diagnostics().isEmpty(),
                "非法DSL发布半解析对象");
        require(TextContent.plain(source).resolve(style).inlineContents().isEmpty(), "PLAIN猜测语法");
    }

    private final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SharedImageService images =
                new SharedImageService(
                        source -> () -> source.bitmap, SharedImageService.Budget.defaults());
        int width = 480, height = 200, attributedCalls, mathCalls;
        final SceneTextMeasurer counted =
                new SceneTextMeasurer() {
                    public TextLayout layout(String source, TextStyle style, int width) {
                        return fonts.layout(source, style, width);
                    }

                    public TextLayout layout(
                            String source, TextStyle style, int width, TextPolicy policy) {
                        return fonts.layout(source, style, width, policy);
                    }

                    public TextLayout layout(AttributedText text, float width, TextPolicy policy) {
                        if (!text.inlineBoxes().isEmpty()) attributedCalls++;
                        return fonts.layout(text, width, policy);
                    }

                    public TextStyle resolveStyle(TextStyle style) { return fonts.resolveStyle(style); }

                    public MathFont mathFont(TextStyle style) {
                        mathCalls++;
                        return fonts.mathFont(style);
                    }
                };
        Signal<TextContent> content;
        Signal<SceneTheme> theme;
        Signal<ImageSource> image;
        Signal<String> base;
        Signal<Boolean> enabled;
        Signal<TextPolicy> policy;
        SceneNode paragraph, viewport;
        PaintPlan paint;
        final List<String> clicks = new ArrayList<>();
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

        Fixture(String source) {
            page =
                    ScenePageSession.open(
                            domain,
                            host,
                            counted,
                            ctx -> {
                                content = domain.signal(TextContent.richTags(source));
                                theme =
                                        domain.signal(
                                                SceneTheme.dark()
                                                        .withTextStyle(
                                                                new TextStyle("default", 28, -1)));
                                image = domain.signal(ImageSource.bitmap(bitmap(24, 16)));
                                base = domain.signal("https://example.test/docs/");
                                enabled = domain.signal(true);
                                policy = domain.signal(TextPolicy.NORMAL);
                                Map<String, DocumentResources.ImageReference> refs =
                                        new LinkedHashMap<>();
                                refs.put(
                                        "ore",
                                        new DocumentResources.ImageReference(
                                                image,
                                                domain.signal(
                                                        ImageOptions.defaults().tooltip(true))));
                                refs.put("test:typed", refs.get("ore"));
                                DocumentResources resources =
                                        new DocumentResources(images, base, refs, null);
                                paragraph =
                                        SceneDocument.paragraph(
                                                        ctx, content, theme, resources, clicks::add)
                                                .size(Size.fill(1), Size.natural())
                                                .padding(7);
                                paragraph.bind(
                                        club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(
                                                enabled, (node, value) -> node.enabled(value)));
                                paragraph.bind(
                                        club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(
                                                policy, (node, value) -> node.textPolicy(value)));
                                viewport =
                                        new SceneNode()
                                                .size(Size.fill(1), Size.fill(1))
                                                .scrollable(true)
                                                .clip(true)
                                                .add(paragraph);
                                ctx.mount(viewport);
                            });
        }

        void frame() {
            page.frame();
        }

        SceneGeometryView geometry() {
            return page.pipeline().current().geometry;
        }

        void ready() throws Exception {
            long deadline = System.nanoTime() + 4_000_000_000L;
            do {
                frame();
                if (paint.commands().stream().anyMatch(cmd -> cmd.kind == PaintCommand.Kind.IMAGE))
                    return;
                Thread.sleep(5);
            } while (System.nanoTime() < deadline);
            throw new AssertionError("文档图像未就绪");
        }

        public void close() {
            page.close();
            images.close();
        }
    }

    private void flow() throws Exception {
        String source =
                "中文前段<a href=\"next\">链接<latex>"
                        + tex("@frac{a}{b}")
                        + "</latex>后缀</a><img src=\"ore\" width=\"36\"/>中文尾段与足够长的换行内容";
        try (Fixture f = new Fixture(source)) {
            f.ready();
            final SceneNode[] identities = new SceneNode[2];
            f.domain.execute(
                    () -> {
                        require(f.paragraph.children().size() == 2, "原子项未在唯一scene树挂载");
                        identities[0] = f.paragraph.children().get(0);
                        identities[1] = f.paragraph.children().get(1);
                        verify(f);
                    });
            f.width = 150;
            f.frame();
            f.domain.execute(
                    () -> {
                        verify(f);
                        require(f.geometry().get(f.paragraph).text.lines().size() > 1, "窄视口没有共同换行");
                        require(
                                f.paragraph.children().get(0) == identities[0]
                                        && f.paragraph.children().get(1) == identities[1],
                                "宽度变化重建子项");
                        f.theme.set(
                                SceneTheme.light().withTextStyle(new TextStyle("default", 38, -1)));
                    });
            f.frame();
            f.domain.execute(() -> verify(f));
            fonts.reload(fonts.defaultCandidates());
            f.frame();
            f.domain.execute(
                    () -> {
                        verify(f);
                        require(
                                f.geometry().get(f.paragraph).text.generation == fonts.generation(),
                                "段落字体混代");
                        require(
                                f.geometry().get(identities[0]).text.generation
                                        == fonts.generation(),
                                "公式字体混代");
                        f.content.set(
                                TextContent.richTags(
                                        "前<latex>" + tex("@notacommand{x}") + "</latex>后续正文"));
                    });
            f.frame();
            f.domain.execute(
                    () -> {
                        require(
                                f.paragraph.children().size() == 1
                                        && SceneMath.layout(f.paragraph.children().get(0)).fallback,
                                "错误公式未完整降级");
                        require(
                                f.geometry().get(f.paragraph).text.logicalText.endsWith("后续正文"),
                                "错误公式丢掉后文");
                        require(f.geometry().get(identities[1]) == null, "替换后旧图像残留几何");
                    });
        }
    }

    private static ImageContent bitmap(int width, int height) {
        byte[] rgba = new byte[width * height * 4];
        Arrays.fill(rgba, (byte) 255);
        return new ImageContent(width, height, rgba) {
            @Override
            public ItemImageFacts itemFacts() {
                return new ItemImageFacts("32", "文档图像提示", false, 1, -1);
            }
        };
    }

    private int[] center(Fixture f, SceneNode child) {
        final int[] point = new int[2];
        f.domain.execute(
                () -> {
                    LayoutBox box = f.geometry().get(child).box;
                    point[0] = box.x + box.width / 2;
                    point[1] = box.y + box.height / 2;
                });
        return point;
    }

    private void interaction() throws Exception {
        String source =
                "前<a href=\"next\"><latex>"
                        + tex("@frac{a}{b}")
                        + "</latex><img src=\"ore\" width=\"36\"/></a>后";
        try (Fixture f = new Fixture(source)) {
            f.ready();
            final SceneNode[] child = new SceneNode[2];
            f.domain.execute(
                    () -> {
                        child[0] = f.paragraph.children().get(0);
                        child[1] = f.paragraph.children().get(1);
                    });
            for (SceneNode node : child) {
                int[] point = center(f, node);
                f.page.pointer(ScenePointerEvent.syntheticActivation(point[0], point[1]));
                f.frame();
            }
            f.domain.execute(
                    () ->
                            require(
                                    f.clicks.equals(
                                            Arrays.asList(
                                                    "https://example.test/docs/next",
                                                    "https://example.test/docs/next")),
                                    "公式/图像链接未走统一目标解析"));
            int[] point = center(f, child[1]);
            f.page.pointer(ScenePointerEvent.move(point[0], point[1]));
            f.frame();
            f.domain.execute(
                    () ->
                            require(
                                    f.page.runtime().pointerRouter().passiveHovered(f.geometry())
                                            == child[1],
                                    "链接图像丢失正式被动提示资格"));
            f.page.pointer(ScenePointerEvent.down(0, point[0], point[1]));
            f.frame();
            f.domain.execute(() -> f.content.set(TextContent.richTags(source)));
            f.frame();
            f.page.pointer(ScenePointerEvent.up(0, point[0], point[1]));
            f.frame();
            f.domain.execute(() -> require(f.clicks.size() == 2, "同源码新声明复活旧链接手势"));
            f.domain.execute(() -> f.enabled.set(false));
            f.frame();
            f.page.pointer(ScenePointerEvent.syntheticActivation(point[0], point[1]));
            f.frame();
            f.domain.execute(
                    () ->
                            require(
                                    f.clicks.size() == 2
                                            && f.page.runtime()
                                                            .pointerRouter()
                                                            .passiveHovered(f.geometry())
                                                    == null,
                                    "禁用仍触发链接/提示"));
        }
    }

    private void scroll() throws Exception {
        try (Fixture f =
                new Fixture(
                        "前段<br/>第二段<br/>第三段<br/><a href=\"next\"><img src=\"ore\""
                                + " width=\"36\"/></a><latex>"
                                + tex("@smash[b]{@frac{a}{b}}")
                                + "</latex>")) {
            f.height = 60;
            f.ready();
            final SceneNode[] image = new SceneNode[1];
            final int[] before = new int[2];
            f.domain.execute(
                    () -> {
                        image[0] = f.paragraph.children().get(0);
                        before[0] = f.attributedCalls;
                        before[1] = f.mathCalls;
                        require(f.geometry().get(f.viewport).scrollMaximum > 0, "没有滚动范围");
                    });
            for (int i = 0; i < 10; i++) {
                f.page.pointer(ScenePointerEvent.scroll(5, 5, -1));
                f.frame();
            }
            int[] point = center(f, image[0]);
            f.page.pointer(ScenePointerEvent.scroll(5, 5, -1));
            f.page.pointer(ScenePointerEvent.move(point[0], point[1]));
            f.frame();
            f.domain.execute(
                    () -> {
                        require(
                                f.page.runtime().pointerRouter().passiveHovered(f.geometry())
                                        == image[0],
                                "同帧wheel/move没有使用当前几何");
                        System.out.println(
                                "document-scroll attributed="
                                        + before[0]
                                        + "->"
                                        + f.attributedCalls
                                        + " math="
                                        + before[1]
                                        + "->"
                                        + f.mathCalls);
                        require(
                                f.attributedCalls == before[0] && f.mathCalls == before[1],
                                "纯滚动重新整形段落或数学内容");
                    });
            f.page.pointer(ScenePointerEvent.move(point[0], f.height + 1));
            f.frame();
            f.domain.execute(
                    () ->
                            require(
                                    f.page.runtime().pointerRouter().passiveHovered(f.geometry())
                                            == null,
                                    "裁剪区外残留图像提示"));
            f.page.close();
            require(f.images.entryCount() == 0, "卸载仍保留文档图像资源");
        }
    }

    private void imageUpdate() throws Exception {
        try (Fixture f = new Fixture("前<img src=\"ore\" width=\"36\"/>后")) {
            f.ready();
            final SceneNode[] node = new SceneNode[1];
            f.domain.execute(
                    () -> {
                        node[0] = f.paragraph.children().get(0);
                        require(f.geometry().get(node[0]).box.height == 24, "单边图像尺寸未按来源比例");
                        f.image.set(ImageSource.bitmap(bitmap(48, 16)));
                    });
            f.ready();
            f.domain.execute(
                    () -> {
                        require(f.paragraph.children().get(0) == node[0], "来源更新重建图像节点");
                        require(f.geometry().get(node[0]).box.height == 12, "来源比例更新没有重新测量文档行");
                        verify(f);
                    });
        }
    }

    private void blocks() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        final SceneNode[] root = new SceneNode[1];
        SceneHost host =
                new SceneHost() {
                    public int width() {
                        return 180;
                    }

                    public int height() {
                        return 300;
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
                        fonts,
                        ctx -> {
                            List<DocumentContent.Block> blocks =
                                    Arrays.asList(
                                            DocumentContent.Block.paragraph(
                                                    TextContent.richTags("首段文字")),
                                            DocumentContent.Block.formula(
                                                    MathContent.parse(tex("@frac{a}{b}")),
                                                    MathStyle.DISPLAY),
                                            DocumentContent.Block.paragraph(
                                                    TextContent.plain("后续正文")));
                            root[0] =
                                    SceneDocument.create(
                                                    ctx,
                                                    domain.signal(new DocumentContent(blocks)),
                                                    domain.signal(SceneTheme.dark()),
                                                    null,
                                                    target -> {})
                                            .size(Size.fill(1), Size.natural());
                            ctx.mount(root[0]);
                        })) {
            page.frame();
            domain.execute(
                    () -> {
                        require(root[0].children().size() == 3, "块未生成同树内容");
                        SceneGeometryView geometry = page.pipeline().current().geometry;
                        LayoutBox before = geometry.get(root[0].children().get(0)).box,
                                math = geometry.get(root[0].children().get(1)).box,
                                after = geometry.get(root[0].children().get(2)).box;
                        require(
                                before.y + before.height <= math.y
                                        && math.y + math.height <= after.y,
                                "公式块覆盖正文");
                        require(
                                geometry.get(root[0].children().get(2))
                                        .text
                                        .logicalText
                                        .equals("后续正文"),
                                "块公式截断正文");
                    });
        }
    }

    private void tightObjects() throws Exception {
        try (Fixture f =
                new Fixture(
                        "<a href=\"next\"><latex>"
                                + tex("@frac{a}{b}")
                                + "</latex><img src=\"ore\" width=\"40\" height=\"75\"/></a>")) {
            f.domain.execute(
                    () ->
                            f.policy.set(
                                    new TextPolicy(
                                            TextPolicy.Direction.AUTO,
                                            TextPolicy.Wrap.EMERGENCY,
                                            "und",
                                            0,
                                            3)));
            f.ready();
            SceneNode[] children = new SceneNode[2];
            f.domain.execute(
                    () -> {
                        children[0] = f.paragraph.children().get(0);
                        children[1] = f.paragraph.children().get(1);
                        TextLayout.Line line = f.geometry().get(f.paragraph).text.lines().get(0);
                        require(line.lineHeight == 3, "段落重新覆盖显式行高");
                        require(f.geometry().get(children[1]).box.height == 75, "显式行高削掉图像真实盒");
                    });
            for (SceneNode child : children) {
                int[] point = center(f, child);
                f.domain.execute(
                        () -> {
                            SceneGeometryView.Entry entry = f.geometry().get(f.paragraph);
                            TextLayout.Line line = entry.text.lines().get(0);
                            require(
                                    point[1] - entry.box.y - f.paragraph.padding()
                                            >= line.y + line.lineHeight,
                                    "没有命中显式行高外的对象区域");
                        });
                f.page.pointer(ScenePointerEvent.syntheticActivation(point[0], point[1]));
                f.frame();
            }
            f.domain.execute(
                    () ->
                            require(
                                    f.clicks.equals(
                                            Arrays.asList(
                                                    "https://example.test/docs/next",
                                                    "https://example.test/docs/next")),
                                    "可见溢出对象链接未使用正式子几何"));
        }
    }

    private void typedFlow() throws Exception {
        for (boolean bytes : new boolean[] {false, true}) {
            try (Fixture f = new Fixture("初始正文")) {
                f.domain.execute(() -> f.content.set(TypedParagraphScenario.typedContent(bytes)));
                f.ready();
                final SceneNode[] children = new SceneNode[2];
                f.domain.execute(
                        () -> {
                            verify(f);
                            children[0] = f.paragraph.children().get(0);
                            children[1] = f.paragraph.children().get(1);
                            TextLayout text = f.geometry().get(f.paragraph).text;
                            require(text.logicalText.equals("甲\ufffc\ufffc链"), "typed正文被送回DSL解析");
                            require(
                                    text.input.sourceUnit
                                            == (bytes
                                                    ? club.heiqi.uilib.ui.scene.text.TextInput
                                                            .SourceUnit.BYTE
                                                    : club.heiqi.uilib.ui.scene.text.TextInput
                                                            .SourceUnit.UTF16),
                                    "typed来源单位丢失");
                            f.theme.set(
                                    SceneTheme.light()
                                            .withTextStyle(new TextStyle("default", 38, -1)));
                            f.image.set(ImageSource.bitmap(bitmap(48, 24)));
                        });
                f.ready();
                f.domain.execute(
                        () -> {
                            verify(f);
                            require(
                                    f.paragraph.children().get(0) == children[0]
                                            && f.paragraph.children().get(1) == children[1],
                                    "typed动态输入重建对象身份");
                        });
                for (SceneNode child : children) {
                    int[] point = center(f, child);
                    f.page.pointer(ScenePointerEvent.syntheticActivation(point[0], point[1]));
                    f.frame();
                }
                f.domain.execute(
                        () ->
                                require(
                                        f.clicks.equals(
                                                Arrays.asList(
                                                        "https://example.test/docs/target",
                                                        "https://example.test/docs/target")),
                                        "typed公式/图像没有复用正式链接路由"));
            }
        }
    }

    private void captureRequests() throws Exception {
        try (Fixture f =
                new Fixture(
                        "前置正文足够长，观察自动换行<latex>"
                                + tex("@frac{a}{b}")
                                + "</latex><img src=\"ore\"/>尾段文字")) {
            f.ready();
            f.domain.execute(
                    () -> {
                        SceneLayoutEngine engine = new SceneLayoutEngine(f.counted);
                        SceneGeometryView wide =
                                engine.layout(
                                        java.util.Collections.singletonList(f.paragraph),
                                        480,
                                        1000,
                                        fonts.generation(),
                                        f.page.runtime().measureRevision());
                        verify(f, wide);
                        SceneGeometryView narrow =
                                engine.captureSubtree(
                                        f.paragraph,
                                        new LayoutBox(0, 0, 120, 1000),
                                        null,
                                        new LayoutBox(0, 0, 120, 1000));
                        verify(f, narrow);
                        require(
                                narrow.get(f.paragraph).text.lines().size()
                                        > wide.get(f.paragraph).text.lines().size(),
                                "多请求反例没有触发不同换行");
                        SceneGeometryView again =
                                engine.captureSubtree(
                                        f.paragraph,
                                        new LayoutBox(0, 0, 480, 1000),
                                        null,
                                        new LayoutBox(0, 0, 480, 1000));
                        require(
                                again.get(f.paragraph).text == wide.get(f.paragraph).text,
                                "没有命中原宽度文字缓存");
                        verify(f, again);
                    });
        }
    }

    private void verify(Fixture f) {
        verify(f, f.geometry());
    }

    private void verify(Fixture f, SceneGeometryView geometry) {
        TextLayout text = geometry.get(f.paragraph).text;
        List<InlineContent> content = f.content.get().inlineContents();
        for (int i = 0; i < content.size(); i++) {
            InlineContent item = content.get(i);
            SceneNode child = f.paragraph.children().get(i);
            SceneGeometryView.Entry actual = geometry.get(child),
                    parent = geometry.get(f.paragraph);
            require(actual != null, "有效原子项没有同代几何");
            boolean found = false;
            for (TextLayout.Line line : text.lines())
                for (TextLayout.Cluster cluster : line.clusters())
                    if (cluster.logicalStart == item.offset
                            && cluster.logicalEnd == item.offset + 1) {
                        found = true;
                        require(cluster.glyphIndexStart == cluster.glyphIndexEnd, "原子项被绘制成替代字符");
                        require(
                                Math.abs(
                                                actual.box.x
                                                        - parent.box.x
                                                        - f.paragraph.padding()
                                                        - cluster.x)
                                        < 1.01f,
                                "原子项x未从同代簇读取");
                        float baseline =
                                item.kind == InlineContent.Kind.MATH
                                        ? SceneMath.presentationBaseline(child)
                                        : actual.box.height;
                        require(
                                Math.abs(
                                                actual.box.y
                                                        - parent.box.y
                                                        - f.paragraph.padding()
                                                        + baseline
                                                        - line.baseline
                                                        - cluster.style.baselineShift)
                                        < 1.01f,
                                "原子项基线错误");
                        require(
                                actual.box.y >= parent.box.y + f.paragraph.padding() + line.y - 1
                                        && actual.box.y + actual.box.height
                                                <= parent.box.y
                                                        + f.paragraph.padding()
                                                        + line.y
                                                        + line.lineHeight
                                                        + 1,
                                "原子项超出行高");
                    }
            require(found, "原子项没有完整文字簇");
        }
    }
}
