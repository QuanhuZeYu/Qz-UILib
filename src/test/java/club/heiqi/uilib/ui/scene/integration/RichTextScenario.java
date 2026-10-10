package club.heiqi.uilib.ui.scene.integration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneRichText;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** 正式转换、字体、页面、计划与唯一 router 路径；不代表 MC 原生输入与 GPU 实机验收。 */
public final class RichTextScenario extends TestApplication {
    private FontTextService font;
    public static void main(String[] args) throws Exception { new RichTextScenario().run(args); }
    @Override protected void setup() throws Exception { font = own(new FontTextService(new LwjglFontEngine())); }
    @Override protected void runTests() throws Exception {
        scenario("正式富文本纵向闭环", () -> {
            ReactiveDomain domain = ReactiveDomain.direct(); Host host = new Host();
            Signal<TextContent>[] source = new Signal[1]; SceneNode[] node = new SceneNode[1];
            List<String> activated = new ArrayList<String>();
            ScenePageSession page = own(ScenePageSession.open(domain, host, font, context -> {
                source[0] = domain.signal(TextContent.richTags("<b>文字</b> <a href=\"../raw?a=1&amp;b=2\">链接 אבג 👩‍💻 多行</a>"));
                node[0] = SceneRichText.create(context, source[0], domain.signal(SceneTheme.dark()), activated::add);
                context.mount(node[0]);
            }));
            page.frame();
            SceneGeometryView.Entry[] entry = new SceneGeometryView.Entry[1];
            domain.execute(() -> entry[0] = page.pipeline().current().geometry.get(node[0]));
            TextLayout layout = entry[0].text;
            require(layout.logicalText.equals("文字 链接 אבג 👩‍💻 多行"), "生产布局没有消费解析内容");
            require(host.plan.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && c.text == layout), "绘制没有使用命中同代布局");
            TextLayout.Cluster link = null;
            for (TextLayout.Line line : layout.lines()) for (TextLayout.Cluster cluster : line.clusters()) if (cluster.logicalStart == 3) link = cluster;
            require(link != null, "链接缺真实字体簇");
            int x = entry[0].box.x + (int)Math.ceil(link.x + link.advance / 2), y = entry[0].box.y + 5;
            page.pointer(ScenePointerEvent.down(0, x, y)); page.pointer(ScenePointerEvent.up(0, x, y)); page.frame();
            require(activated.size() == 1 && activated.get(0).equals("../raw?a=1&b=2"), "链接没有仅发布原目标");
            page.pointer(ScenePointerEvent.syntheticActivation(host.width - 1, host.height - 1)); page.frame();
            require(activated.size() == 1, "节点空白错误扩大链接命中");
        });
        scenario("两模式节符字面沿正式页面呈现", () -> {
            for (TextContent.Mode mode : TextContent.Mode.values()) {
                ReactiveDomain domain = ReactiveDomain.direct(); Host host = new Host();
                String raw = "§kA §lB §rC §§k 中文 👩‍💻";
                SceneNode[] node = new SceneNode[1];
                ScenePageSession page = own(ScenePageSession.open(domain, host, font, context -> {
                    node[0] = SceneRichText.create(context, domain.signal(TextContent.of(mode, TextInput.string(raw))),
                        domain.signal(SceneTheme.dark()), target -> { throw new AssertionError("节符字面不应产生链接"); });
                    context.mount(node[0]);
                }));
                page.frame();
                TextLayout layout = read(domain, () -> page.pipeline().current().geometry.get(node[0]).text);
                require(layout.logicalText.equals(raw), "正式模式解释了Minecraft节符");
                require(layout.lines().stream().flatMap(line -> line.clusters().stream()).allMatch(cluster -> !cluster.style.effects.obfuscated), "字面节符引入混淆样式");
                require(layout.diagnostics().stream().noneMatch(d -> d.message.contains("混淆")), "字面节符错误产生格式诊断");
                require(host.plan.commands().stream().anyMatch(command -> command.kind == PaintCommand.Kind.TEXT && command.text == layout), "字面模式未沿正式同代绘制计划");
            }
        });
        scenario("来源映射与非法整段呈现", this::sourceAndFailure);
        scenario("具体链接身份与物理尾部", this::gestureIdentity);
        scenario("真实多行Bidi簇与祖先裁剪滚动重排", this::geometry);
        scenario("handler失败先释放owner", this::handlerFailure);
        scenario("字号内外装饰应一致", () -> {
            TextStyle base = new TextStyle("default", 18, 0xffffffff);
            TextLayout a = font.layout(TextContent.richTags("<u><size value=\"32\">大字</size></u>").resolve(base).text, 500, club.heiqi.uilib.ui.scene.text.TextPolicy.NORMAL);
            TextLayout b = font.layout(TextContent.richTags("<size value=\"32\"><u>大字</u></size>").resolve(base).text, 500, club.heiqi.uilib.ui.scene.text.TextPolicy.NORMAL);
            require(a.lines().get(0).decorations().get(0).height == b.lines().get(0).decorations().get(0).height,
                "同字号装饰因标签顺序使用旧字号厚度");
            require(a.lines().get(0).decorations().get(0).y == b.lines().get(0).decorations().get(0).y, "同字号装饰偏移不同");
        });
        scenario("显式模式与正常嵌套实体往返", () -> {
            TextStyle base = new TextStyle("default", 18, 0x7fabcdef);
            TextContent.Resolved result = TextContent.richTags("<b>A<b>B</b>C</b>D&lt;b&gt;&amp;<br/><color value=\"#112233\">X</color><size value=\"24\"><i>Y</i></size>").resolve(base);
            require(result.text.input.text.equals("ABCD<b>&\nXY"), "实体回扫或换行错误");
            require(result.text.styleAt(0).fontStyle.weight == 700 && result.text.styleAt(2).fontStyle.weight == 700 && result.text.styleAt(3).fontStyle.weight == 400, "重复样式作用域提前退出");
            require(result.text.styleAt(result.text.input.text.indexOf('X')).color == 0xff112233, "RGB应不透明");
            require(TextContent.richTags("<color value=\"#80112233\">X</color>").resolve(base).text.styleAt(0).color == 0x80112233, "ARGB未保留声明alpha");
            TextContent.Resolved roundtrip = TextContent.richTags(result.serializeRichTags()).resolve(base);
            require(roundtrip.text.input.text.equals(result.text.input.text), "往返丢文字");
            for (int i = 0; i < result.text.input.text.length(); i++) require(result.text.styleAt(i).equals(roundtrip.text.styleAt(i)), "往返丢样式");
            require(TextContent.plain("§l<b>\n\t👩‍💻").resolve(base).text.input.text.equals("§l<b>\n\t👩‍💻"), "PLAIN猜语法");
        });
    }
    private void sourceAndFailure() {
        TextStyle base = new TextStyle("default", 18, 0xffffffff);
        String raw = "<b>中😀&lt;</b><br/>&amp;";
        TextContent.Resolved content = TextContent.of(TextContent.Mode.RICH_TAGS, TextInput.utf8(raw.getBytes(StandardCharsets.UTF_8))).resolve(base);
        TextInput mapped = content.text.input;
        require(mapped.text.equals("中😀<\n&") && mapped.sourceUnit == TextInput.SourceUnit.BYTE, "UTF8实体和br转换错误");
        String[] tokens = {"中", "😀", "&lt;", "<br/>", "&amp;"};
        int[] logical = {0, 1, 3, 4, 5};
        for (int i = 0; i < tokens.length; i++) {
            int start = raw.indexOf(tokens[i]);
            int expectedStart = raw.substring(0, start).getBytes(StandardCharsets.UTF_8).length;
            int expectedEnd = raw.substring(0, start + tokens[i].length()).getBytes(StandardCharsets.UTF_8).length;
            require(mapped.sourceStart(logical[i]) == expectedStart && mapped.sourceEnd(logical[i]) == expectedEnd, "byte来源未覆盖完整token/码点");
        }
        require(mapped.sourceStart(1) == mapped.sourceStart(2) && mapped.sourceEnd(1) == mapped.sourceEnd(2), "代理对被切割");
        int consumed = 0;
        for (TextInput.SourceSegment segment : mapped.sourceSegments()) { require(segment.sourceStart == consumed, "来源片段缺口"); consumed = segment.sourceEnd; }
        require(consumed == raw.getBytes(StandardCharsets.UTF_8).length, "尾部源码丢失");
        for (String invalid : new String[] {"<b>中</i>", "<a href='x'>中</a>", "<a href=\"x\">中", "<mark>中</mark>", "<b color=\"#ffffff\">中</b>", "&quot;", "<B>中</B>", "<color value=\"NaN\">中</color>", "<size value=\"NaN\">中</size>"}) {
            TextContent.Resolved failed = TextContent.richTags(invalid).resolve(base);
            require(failed.text.input.text.equals(invalid) && failed.links().isEmpty() && !failed.text.diagnostics().isEmpty(), "非法内容被修复或半解析");
            TextLayout.Diagnostic diagnostic = failed.text.diagnostics().get(0);
            require(diagnostic.sourceStart >= 0 && diagnostic.sourceEnd > diagnostic.sourceStart && diagnostic.sourceEnd <= invalid.length(), "诊断源位置无效");
            require(TextContent.richTags(failed.serializeRichTags()).resolve(base).text.input.text.equals(invalid), "失败序列化丢原文");
        }
        TextContent nested = TextContent.richTags("<a href=\"外&amp;值\">A<a href=\"内\">B</a>C</a>");
        TextContent.Resolved a = nested.resolve(base), b = nested.resolve(base.withColor(0xff112233));
        require(a.linkAt(0) == a.linkAt(2) && a.linkAt(0) != a.linkAt(1) && a.linkAt(0) == b.linkAt(0), "嵌套或同声明主题身份错误");
        TextContent.Resolved c = TextContent.richTags(a.serializeRichTags()).resolve(base);
        require(c.linkAt(0).target.equals("外&值") && c.linkAt(1).target.equals("内") && c.linkAt(2).target.equals("外&值"), "往返链接目标错误");
    }
    private void gestureIdentity() {
        Fixture f = own(new Fixture("<a href=\"A\">AAA</a> 空 <a href=\"B\">BBB</a>", false));
        int[] a = f.point(0), b = f.point(f.entry().text.logicalText.indexOf("BBB"));
        f.events(ScenePointerEvent.down(0, a[0], a[1]), ScenePointerEvent.up(0, b[0], b[1]));
        require(f.activated.isEmpty(), "同节点A按下B释放误触发");
        f.events(ScenePointerEvent.down(0, a[0], a[1]));
        f.domain.execute(() -> f.content.set(TextContent.richTags(f.content.get().source))); f.page.frame();
        f.events(ScenePointerEvent.up(0, a[0], a[1])); require(f.activated.isEmpty(), "新声明同源码同href复活旧手势");
        f.events(ScenePointerEvent.down(0, a[0], a[1]));
        f.domain.execute(() -> f.theme.set(SceneTheme.light())); f.page.frame();
        f.events(ScenePointerEvent.up(0, a[0], a[1])); require(f.activated.size() == 1, "仅主题变化无因丢声明身份");
        f.activated.clear();
        for (int action = 0; action < 4; action++) {
            f.events(ScenePointerEvent.down(0, a[0], a[1]));
            if (action == 0) { f.domain.execute(() -> f.enabled.set(false)); f.page.frame(); f.domain.execute(() -> f.enabled.set(true)); f.page.frame(); }
            if (action == 1) f.domain.execute(() -> { f.page.runtime().captureMount(f.node).detach(); f.page.runtime().attach(f.viewport, f.node); });
            if (action == 2) f.events(ScenePointerEvent.cancel());
            if (action == 3) { f.domain.execute(() -> f.visible.set(false)); f.page.frame(); f.domain.execute(() -> f.visible.set(true)); f.page.frame(); }
            f.events(ScenePointerEvent.syntheticActivation(a[0], a[1]), ScenePointerEvent.up(0, a[0], a[1]));
            require(f.activated.isEmpty(), "失活/重挂载/cancel尾部复活");
        }
        f.events(ScenePointerEvent.down(1, a[0], a[1]), ScenePointerEvent.down(0, a[0], a[1]),
            ScenePointerEvent.up(1, a[0], a[1]), ScenePointerEvent.up(0, a[0], a[1]));
        require(f.activated.isEmpty(), "第二键尾部误激活");
        f.events(ScenePointerEvent.syntheticActivation(b[0], b[1])); require(f.activated.size() == 1 && f.activated.get(0).equals("B"), "synthetic未按内部目标");
    }
    private void geometry() {
        Fixture f = own(new Fixture("普通 <a href=\"A\">多行链接 אבג 👩‍💻 中文文字 中文文字</a> 尾部<br/><a href=\"B\">第二链接 第二链接 第二链接 第二链接</a>", true));
        SceneGeometryView.Entry first = f.entry();
        require(first.text.lines().size() > 1, "未经过真实多行布局");
        TextContent.Resolved resolved = read(f.domain, () -> f.content.get().resolve(f.theme.get().textStyle));
        boolean hidden = false, bidi = false, emoji = false; int visibleCount = 0;
        for (TextLayout.Line line : first.text.lines()) for (TextLayout.Cluster cluster : line.clusters()) {
            if (cluster.logicalStart == cluster.logicalEnd || cluster.advance < 1) continue;
            if (first.text.logicalText.substring(cluster.logicalStart, cluster.logicalEnd).contains("א")) bidi = true;
            if (first.text.logicalText.substring(cluster.logicalStart, cluster.logicalEnd).contains("👩")) emoji = true;
            int x = first.box.x + (int)Math.floor(cluster.x + cluster.advance / 2), y = first.box.y + (int)Math.floor(line.y + line.lineHeight / 2);
            boolean visible = first.clip == null || first.clip.contains(x, y);
            int before = f.activated.size(); f.events(ScenePointerEvent.syntheticActivation(x, y));
            TextContent.Link link = resolved.linkAt(cluster.logicalStart);
            require(f.activated.size() == before + (visible && link != null ? 1 : 0), "可见完整簇/祖先clip链接资格错误");
            if (visible && link != null) visibleCount++; if (!visible && link != null) hidden = true;
        }
        require(visibleCount > 0 && hidden && bidi && emoji, "真实多行/Bidi/emoji/clip反例未触达");
        // 同一文字簇上下跨裁剪边界：交集内有目标，交集外没有目标。
        TextLayout.Line partial = first.text.lines().get(1);
        TextLayout.Cluster partialCluster = partial.clusters().get(0);
        int px = first.box.x + (int)Math.floor(partialCluster.x + partialCluster.advance / 2);
        int edge = first.clip.y + first.clip.height;
        int clippedBefore = f.activated.size();
        f.events(ScenePointerEvent.syntheticActivation(px, edge - 1), ScenePointerEvent.syntheticActivation(px, edge));
        require(f.activated.size() == clippedBefore + 1 && f.activated.get(clippedBefore).equals("A"), "部分裁剪簇未按可见交集命中");
        int[] old = f.point(0); int before = f.activated.size();
        f.events(ScenePointerEvent.scroll(3, 3, -1));
        SceneGeometryView.Entry scrolled = f.entry(); require(scrolled.box.y < first.box.y, "正式滚动未变更坐标");
        f.events(ScenePointerEvent.syntheticActivation(old[0], old[1]));
        require(f.activated.size() == before + 1 && f.activated.get(before).equals("A"),
            "滚动后旧普通正文位置应对应当前第二行链接A");
        f.host.width = 90; f.domain.execute(() -> f.theme.set(SceneTheme.dark().withTextStyle(new TextStyle("default", 26, 0xffffffff)))); f.page.frame();
        SceneGeometryView.Entry changed = f.entry(); require(changed.text != scrolled.text && changed.text.lines().size() > scrolled.text.lines().size(), "宽度/字号未重排");
        require(f.host.plan.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && c.text == changed.text), "重排后paint与hit不同代");
        Fixture cluster = own(new Fixture("<a href=\"A\">👩</a><a href=\"B\">‍💻</a>", false));
        TextLayout merged = cluster.entry().text; require(merged.lines().get(0).clusters().size() == 1, "emoji被链接边界拆簇");
        require(merged.diagnostics().stream().anyMatch(d -> d.kind == TextLayout.Diagnostic.Kind.STYLE_COALESCED), "跨链接簇缺来源诊断");
        int[] p = cluster.point(0); cluster.events(ScenePointerEvent.syntheticActivation(p[0], p[1]));
        require(cluster.activated.size() == 1 && cluster.activated.get(0).equals("A"), "完整簇未归leading链接");
    }
    private void handlerFailure() {
        Fixture f = own(new Fixture("<a href=\"A\">链接</a>", false)); int[] a = f.point(0);
        f.fail = true;
        Throwable original = null;
        try { f.events(ScenePointerEvent.down(0, a[0], a[1]), ScenePointerEvent.up(0, a[0], a[1]), ScenePointerEvent.down(1, a[0], a[1])); }
        catch (RuntimeException failure) { original = failure; }
        require(original == f.failure, "业务异常身份丢失");
        f.fail = false; f.events(ScenePointerEvent.syntheticActivation(a[0], a[1]), ScenePointerEvent.up(1, a[0], a[1]));
        require(f.activated.isEmpty(), "handler失败后物理尾部误激活");
        f.events(ScenePointerEvent.syntheticActivation(a[0], a[1])); require(f.activated.size() == 1, "结束尾部后新手势不能激活");
    }
    private final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct(); final Host host = new Host();
        Signal<TextContent> content; Signal<SceneTheme> theme; Signal<Boolean> enabled, visible;
        final List<String> activated = new ArrayList<String>(); SceneNode node, viewport; final ScenePageSession page;
        final RuntimeException failure = new RuntimeException("业务故障"); boolean fail;
        Fixture(String raw, boolean clipped) {
            host.width = 140; host.height = 180;
            page = ScenePageSession.open(domain, host, font, context -> {
                content = domain.signal(TextContent.richTags(raw)); theme = domain.signal(SceneTheme.dark());
                enabled = domain.signal(true); visible = domain.signal(true);
                node = SceneRichText.create(context, content, theme, target -> { if (fail) throw failure; activated.add(target); })
                    .size(Size.fill(1), Size.natural()).bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(enabled, SceneNode::enabled))
                    .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(visible, SceneNode::visible));
                viewport = new SceneNode().size(Size.fill(1), clipped ? Size.fixed(35) : Size.fill(1)).scrollable(clipped).scrollStep(20).add(node);
                context.mount(viewport);
            }); page.frame();
        }
        SceneGeometryView.Entry entry() { return read(domain, () -> page.pipeline().current().geometry.get(node)); }
        int[] point(int logical) {
            SceneGeometryView.Entry entry = entry();
            for (TextLayout.Line line : entry.text.lines()) for (TextLayout.Cluster cluster : line.clusters())
                if (cluster.logicalStart <= logical && cluster.logicalEnd > logical)
                    return new int[] {entry.box.x + (int)Math.floor(cluster.x + cluster.advance / 2), entry.box.y + (int)Math.floor(line.y + line.lineHeight / 2)};
            throw new AssertionError("未找到目标完整簇");
        }
        void events(ScenePointerEvent... events) { for (ScenePointerEvent event : events) page.pointer(event); page.frame(); }
        public void close() { page.close(); }
    }
    @SuppressWarnings("unchecked")
    private static <T> T read(ReactiveDomain domain, java.util.function.Supplier<T> value) {
        Object[] result = new Object[1]; domain.execute(() -> result[0] = value.get()); return (T)result[0];
    }
    private static final class Host implements SceneHost {
        int width = 180, height = 120; PaintPlan plan;
        public int width() { return width; } public int height() { return height; }
        public long metricsRevision() { return 0; } public void replay(PaintPlan plan) { this.plan = plan; }
    }
}
