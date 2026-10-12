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

    public static void main(String[] args) throws Exception {
        new RichTextScenario().run(args);
    }

    @Override
    protected void setup() throws Exception {
        font = own(new FontTextService(new LwjglFontEngine()));
    }

    @Override
    protected void runTests() throws Exception {
        scenario(
                "正式富文本纵向闭环",
                () -> {
                    ReactiveDomain domain = ReactiveDomain.direct();
                    Host host = new Host();
                    Signal<TextContent>[] source = new Signal[1];
                    SceneNode[] node = new SceneNode[1];
                    List<String> activated = new ArrayList<String>();
                    ScenePageSession page =
                            own(
                                    ScenePageSession.open(
                                            domain,
                                            host,
                                            font,
                                            context -> {
                                                source[0] =
                                                        domain.signal(
                                                                TextContent.richTags(
                                                                        "<b>文字</b> <a href=\"../raw?a=1&amp;b=2\">链接 אבג 👩‍💻 多行</a>"));
                                                node[0] =
                                                        SceneRichText.create(
                                                                context,
                                                                source[0],
                                                                domain.signal(SceneTheme.dark()),
                                                                activated::add);
                                                context.mount(node[0]);
                                            }));
                    page.frame();
                    SceneGeometryView.Entry[] entry = new SceneGeometryView.Entry[1];
                    domain.execute(
                            () -> entry[0] = page.pipeline().current().geometry.get(node[0]));
                    TextLayout layout = entry[0].text;
                    require(layout.logicalText.equals("文字 链接 אבג 👩‍💻 多行"), "生产布局没有消费解析内容");
                    require(
                            host.plan.commands().stream()
                                    .anyMatch(
                                            c ->
                                                    c.kind == PaintCommand.Kind.TEXT
                                                            && c.text == layout),
                            "绘制没有使用命中同代布局");
                    TextLayout.Cluster link = null;
                    for (TextLayout.Line line : layout.lines())
                        for (TextLayout.Cluster cluster : line.clusters())
                            if (cluster.logicalStart == 3) link = cluster;
                    require(link != null, "链接缺真实字体簇");
                    int x = entry[0].box.x + (int) Math.ceil(link.x + link.advance / 2),
                            y = entry[0].box.y + 5;
                    page.pointer(ScenePointerEvent.down(0, x, y));
                    page.pointer(ScenePointerEvent.up(0, x, y));
                    page.frame();
                    require(
                            activated.size() == 1 && activated.get(0).equals("../raw?a=1&b=2"),
                            "链接没有仅发布原目标");
                    page.pointer(
                            ScenePointerEvent.syntheticActivation(host.width - 1, host.height - 1));
                    page.frame();
                    require(activated.size() == 1, "节点空白错误扩大链接命中");
                });
        scenario(
                "同批测量最终绘制共享属性布局且声明变化重新排版",
                () -> {
                    ReactiveDomain domain = ReactiveDomain.direct();
                    Host host = new Host();
                    int[] calls = {0};
                    SceneNode[] node = new SceneNode[1];
                    Signal<TextContent>[] source = new Signal[1];
                    club.heiqi.uilib.ui.scene.text.SceneTextMeasurer counted =
                            new club.heiqi.uilib.ui.scene.text.SceneTextMeasurer() {
                                public TextLayout layout(String text, TextStyle style, int width) {
                                    return font.layout(text, style, width);
                                }

                                public TextLayout layout(
                                        String text,
                                        TextStyle style,
                                        int width,
                                        club.heiqi.uilib.ui.scene.text.TextPolicy policy) {
                                    return font.layout(text, style, width, policy);
                                }

                                public TextLayout layout(
                                        club.heiqi.uilib.ui.scene.text.AttributedText text,
                                        float width,
                                        club.heiqi.uilib.ui.scene.text.TextPolicy policy) {
                                    calls[0]++;
                                    return font.layout(text, width, policy);
                                }
                            };
                    ScenePageSession page =
                            own(
                                    ScenePageSession.open(
                                            domain,
                                            host,
                                            counted,
                                            context -> {
                                                source[0] =
                                                        domain.signal(
                                                                TextContent.richTags(
                                                                        "<b>共享测量</b>"));
                                                node[0] =
                                                        SceneRichText.create(
                                                                        context,
                                                                        source[0],
                                                                        domain.signal(
                                                                                SceneTheme.dark()),
                                                                        href -> {})
                                                                .size(
                                                                        Size.fixed(host.width),
                                                                        Size.natural());
                                                context.mount(node[0]);
                                            }));
                    page.frame();
                    require(calls[0] == 1, "同宽测量与生产布局重复调用字体整形");
                    page.frame();
                    require(calls[0] == 1, "稳定呈现重复排版");
                    domain.execute(() -> source[0].set(TextContent.richTags("<i>新的属性声明</i>")));
                    page.frame();
                    require(calls[0] == 2, "新声明没有沿呈现批次重新排版");
                    require(
                            host.plan.commands().stream()
                                    .anyMatch(
                                            c ->
                                                    c.text != null
                                                            && c.text.logicalText.equals("新的属性声明")),
                            "绘制误用上一批内容");
                });
        scenario(
                "两模式节符字面沿正式页面呈现",
                () -> {
                    for (TextContent.Mode mode : TextContent.Mode.values()) {
                        ReactiveDomain domain = ReactiveDomain.direct();
                        Host host = new Host();
                        String raw = "§kA §lB §rC §§k 中文 👩‍💻";
                        SceneNode[] node = new SceneNode[1];
                        ScenePageSession page =
                                own(
                                        ScenePageSession.open(
                                                domain,
                                                host,
                                                font,
                                                context -> {
                                                    node[0] =
                                                            SceneRichText.create(
                                                                    context,
                                                                    domain.signal(
                                                                            TextContent.of(
                                                                                    mode,
                                                                                    TextInput
                                                                                            .string(
                                                                                                    raw))),
                                                                    domain.signal(
                                                                            SceneTheme.dark()),
                                                                    target -> {
                                                                        throw new AssertionError(
                                                                                "节符字面不应产生链接");
                                                                    });
                                                    context.mount(node[0]);
                                                }));
                        page.frame();
                        TextLayout layout =
                                read(
                                        domain,
                                        () -> page.pipeline().current().geometry.get(node[0]).text);
                        require(layout.logicalText.equals(raw), "正式模式解释了Minecraft节符");
                        require(
                                layout.lines().stream()
                                        .flatMap(line -> line.clusters().stream())
                                        .allMatch(cluster -> !cluster.style.effects.obfuscated),
                                "字面节符引入混淆样式");
                        require(
                                layout.diagnostics().stream()
                                        .noneMatch(d -> d.message.contains("混淆")),
                                "字面节符错误产生格式诊断");
                        require(
                                host.plan.commands().stream()
                                        .anyMatch(
                                                command ->
                                                        command.kind == PaintCommand.Kind.TEXT
                                                                && command.text == layout),
                                "字面模式未沿正式同代绘制计划");
                    }
                });
        scenario("扩展样式嵌套来源与往返", this::extendedDeclarations);
        scenario("真实字体上下标及完整簇字距", this::extendedGeometry);
        scenario("动态高亮重排与同一链接手势", this::extendedPage);
        scenario("来源映射与非法整段呈现", this::sourceAndFailure);
        scenario("正式字段使用最终RTL方向及声明行高", this::fieldDirectionPolicy);
        scenario("单行字段可见硬换行标记且Area与NoWrap标签保硬行", this::fieldHardBreaks);
        scenario("显式Unicode快照共用与局部重载归属", this::unicodeProfiles);
        scenario("同一DSL声明动态基础样式保源结构与链接身份", this::dynamicDeclaration);
        scenario("具体链接身份与物理尾部", this::gestureIdentity);
        scenario("真实多行Bidi簇与祖先裁剪滚动重排", this::geometry);
        scenario("handler失败先释放owner", this::handlerFailure);
        scenario(
                "字号内外装饰应一致",
                () -> {
                    TextStyle base = new TextStyle("default", 18, 0xffffffff);
                    TextLayout a =
                            font.layout(
                                    TextContent.richTags("<u><size value=\"32\">大字</size></u>")
                                            .resolve(base)
                                            .text,
                                    500,
                                    club.heiqi.uilib.ui.scene.text.TextPolicy.NORMAL);
                    TextLayout b =
                            font.layout(
                                    TextContent.richTags("<size value=\"32\"><u>大字</u></size>")
                                            .resolve(base)
                                            .text,
                                    500,
                                    club.heiqi.uilib.ui.scene.text.TextPolicy.NORMAL);
                    require(
                            a.lines().get(0).decorations().get(0).height
                                    == b.lines().get(0).decorations().get(0).height,
                            "同字号装饰因标签顺序使用旧字号厚度");
                    require(
                            a.lines().get(0).decorations().get(0).y
                                    == b.lines().get(0).decorations().get(0).y,
                            "同字号装饰偏移不同");
                });
        scenario(
                "显式模式与正常嵌套实体往返",
                () -> {
                    TextStyle base = new TextStyle("default", 18, 0x7fabcdef);
                    TextContent.Resolved result =
                            TextContent.richTags(
                                            "<b>A<b>B</b>C</b>D&lt;b&gt;&amp;<br/><color value=\"#112233\">X</color><size value=\"24\"><i>Y</i></size>")
                                    .resolve(base);
                    require(result.text.input.text.equals("ABCD<b>&\nXY"), "实体回扫或换行错误");
                    require(
                            result.text.styleAt(0).fontStyle.weight == 700
                                    && result.text.styleAt(2).fontStyle.weight == 700
                                    && result.text.styleAt(3).fontStyle.weight == 400,
                            "重复样式作用域提前退出");
                    require(
                            result.text.styleAt(result.text.input.text.indexOf('X')).color
                                    == 0xff112233,
                            "RGB应不透明");
                    require(
                            TextContent.richTags("<color value=\"#80112233\">X</color>")
                                            .resolve(base)
                                            .text
                                            .styleAt(0)
                                            .color
                                    == 0x80112233,
                            "ARGB未保留声明alpha");
                    TextContent.Resolved roundtrip =
                            TextContent.richTags(result.serializeRichTags()).resolve(base);
                    require(roundtrip.text.input.text.equals(result.text.input.text), "往返丢文字");
                    for (int i = 0; i < result.text.input.text.length(); i++)
                        require(result.text.styleAt(i).equals(roundtrip.text.styleAt(i)), "往返丢样式");
                    require(
                            TextContent.plain("§l<b>\n\t👩‍💻")
                                    .resolve(base)
                                    .text
                                    .input
                                    .text
                                    .equals("§l<b>\n\t👩‍💻"),
                            "PLAIN猜语法");
                });
    }

    private void extendedDeclarations() {
        TextStyle base = new TextStyle("default", 24, 0xffeeeeee).withDefaultHighlightColor(0xff336699);
        String source = "<mark>A<mark color=\"#00000000\">B</mark>C</mark>D<sup>E<sub>F</sub>G</sup><spacing value=\"-0.5\">H</spacing>";
        TextContent.Resolved r = TextContent.of(TextContent.Mode.RICH_TAGS, TextInput.utf8(source.getBytes(StandardCharsets.UTF_8))).resolve(base);
        require(r.text.input.text.equals("ABCDEFGH") && r.text.diagnostics().isEmpty(), "扩展标签未沿正常词法转换");
        require(r.text.styleAt(0).highlightColor == base.defaultHighlightColor
                && r.text.styleAt(1).highlightColor == 0
                && r.text.styleAt(2).highlightColor == base.defaultHighlightColor
                && r.text.styleAt(3).highlightColor == null, "背景嵌套、透明色或闭合恢复错误");
        require(r.text.styleAt(4).script.steps().size() == 1
                && r.text.styleAt(5).script.steps().size() == 2
                && r.text.styleAt(6).script.steps().size() == 1, "脚本嵌套未保留有序意图");
        require(r.text.styleAt(7).letterSpacing == -0.5f, "负字距丢失");
        TextContent.Resolved copy = TextContent.richTags(r.serializeRichTags()).resolve(base);
        for (int i = 0; i < r.text.input.text.length(); i++)
            require(r.text.styleAt(i).equals(copy.text.styleAt(i)), "扩展样式序列化丢失语义");
        for (String invalid : new String[] {"<spacing value=\"NaN\">A</spacing>", "<mark other=\"x\">A</mark>", "<sup value=\"2\">A</sup>"}) {
            TextContent.Resolved failure = TextContent.richTags(invalid).resolve(base);
            require(failure.text.input.text.equals(invalid) && !failure.text.diagnostics().isEmpty(), "非法扩展输入未整段字面呈现");
        }
    }

    private TextLayout extendedLayout(String source, TextStyle base, int width) {
        return font.layout(TextContent.richTags(source).resolve(base).text, width,
                club.heiqi.uilib.ui.scene.text.TextPolicy.NORMAL);
    }

    private void extendedGeometry() {
        TextStyle base = new TextStyle("default", 32, 0xffeeeeee);
        TextLayout scripts = extendedLayout("A<sup>2<sup>3</sup></sup>B<sub>4</sub>", base, 500);
        TextLayout.Line line = scripts.lines().get(0);
        TextLayout.Glyph sup = line.glyphs().stream().filter(g -> g.logicalStart == 1).findFirst().get();
        TextLayout.Glyph nested = line.glyphs().stream().filter(g -> g.logicalStart == 2).findFirst().get();
        TextLayout.Glyph sub = line.glyphs().stream().filter(g -> g.logicalStart == 4).findFirst().get();
        require(sup.logicalEm < base.logicalEm && sup.y < line.baseline, "上标没有依据字体缩小并上移");
        require(nested.logicalEm < sup.logicalEm && nested.y < sup.y, "嵌套上标字号/基线未继续协调");
        require(sub.logicalEm < base.logicalEm && sub.y > line.baseline, "下标没有缩小并下移");
        for (TextLayout.Glyph glyph : line.glyphs()) {
            require(glyph.y - glyph.resolvedStyle.logicalEm <= line.baseline + line.descent, "脚本不属于当前行几何");
        }
        TextLayout inside = extendedLayout("<sup><size value=\"28\">x</size></sup>", base, 500);
        require(inside.lines().get(0).glyphs().get(0).logicalEm == 28, "内层绝对字号未覆盖脚本字号");
        for (String text : new String[] {"office", "A👩‍💻éB", "אבג مرحبا ABC"}) {
            TextLayout original = font.layout(text, base, 500);
            for (float spacing : new float[] {2, -0.5f}) {
                TextLayout spaced = font.layout(text, base.withLetterSpacing(spacing), 500);
                List<TextLayout.Cluster> before = original.lines().get(0).clusters();
                List<TextLayout.Cluster> after = spaced.lines().get(0).clusters();
                require(before.size() == after.size(), "字距切开完整整形簇");
                float expected = original.advanceWidth;
                for (int i = 0; i < after.size(); i++) {
                    require(before.get(i).logicalStart == after.get(i).logicalStart
                            && before.get(i).logicalEnd == after.get(i).logicalEnd, "字距破坏Unicode/Bidi簇来源");
                    if (i + 1 < after.size() && before.get(i).advance > 0) expected += spacing;
                }
                require(Math.abs(spaced.advanceWidth - expected) < 0.02f, "字距被遗漏、重复或加到行尾");
            }
        }
        TextStyle emOnly = base.withScript(club.heiqi.uilib.ui.scene.text.TextScript.NONE.appendEm(20));
        require(font.layout("x", emOnly, 500).advanceWidth
                == extendedLayout("x", emOnly, 500).advanceWidth, "共同内容继承吞掉仅含字号的脚本意图");
        TextLayout decorated = extendedLayout("<sup><u>x</u></sup>", base, 500);
        TextLayout.Glyph scriptGlyph = decorated.lines().get(0).glyphs().get(0);
        TextLayout.Decoration underline = decorated.lines().get(0).decorations().get(0);
        require(Math.abs(underline.y - scriptGlyph.y - scriptGlyph.logicalEm / 12f) < 0.02f,
                "派生下划线未沿脚本最终字号及基线更新");
        TextLayout tabbed = font.layout("A\tB", base.withLetterSpacing(2), 500);
        TextLayout tabbedPlain = font.layout("A\tB", base, 500);
        require(Math.abs(tabbed.advanceWidth - tabbedPlain.advanceWidth) < 0.02f, "字距跨过Tab或破坏Tabstop");
        boolean rejected = false;
        try { font.layout("AB", base.withLetterSpacing(-1000), 500); }
        catch (IllegalArgumentException invalid) { rejected = invalid.getMessage().contains("source=["); }
        require(rejected, "无效负字距未明确指出来源");
        TextLayout wide = extendedLayout("<mark>多行高亮背景带有足够长的内容</mark>", base, 500);
        TextLayout narrow = extendedLayout("<mark>多行高亮背景带有足够长的内容</mark>", base, 90);
        require(narrow.lines().size() > wide.lines().size(), "窄宽未自动重排");
        for (TextLayout.Line row : narrow.lines())
            require(row.decorations().stream().anyMatch(d -> d.kind == TextLayout.Decoration.Kind.HIGHLIGHT
                    && d.height == row.ascent + row.descent), "跨行高亮没有消费本行几何");
    }

    private void extendedPage() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        Signal<SceneTheme>[] theme = new Signal[1];
        SceneNode[] node = new SceneNode[1];
        List<String> activated = new ArrayList<>();
        TextContent declaration = TextContent.richTags("<a href=\"report\"><mark><spacing value=\"1\">复核 CO<sub>2</sub> 数据</spacing></mark></a>");
        ScenePageSession page = own(ScenePageSession.open(domain, host, font, ctx -> {
            theme[0] = domain.signal(SceneTheme.light());
            node[0] = SceneRichText.create(ctx, () -> declaration, theme[0], activated::add);
            ctx.mount(node[0]);
        }));
        page.frame();
        SceneGeometryView.Entry initial = read(domain, () -> page.pipeline().current().geometry.get(node[0]));
        TextLayout.Cluster first = initial.text.lines().get(0).clusters().get(0);
        int x = initial.box.x + (int) (first.x + first.advance / 2), y = initial.box.y + 5;
        page.pointer(ScenePointerEvent.down(0, x, y));
        domain.execute(() -> theme[0].set(SceneTheme.dark()));
        page.frame();
        SceneGeometryView.Entry changed = read(domain, () -> page.pipeline().current().geometry.get(node[0]));
        require(changed.text.lines().get(0).decorations().stream()
                .filter(d -> d.kind == TextLayout.Decoration.Kind.HIGHLIGHT)
                .allMatch(d -> d.color == SceneTheme.dark().textStyle.defaultHighlightColor), "默认高亮色冻结旧主题");
        page.pointer(ScenePointerEvent.up(0, x, y));
        page.frame();
        require(activated.size() == 1 && activated.get(0).equals("report"), "样式更新破坏稳定链接手势");
        require(host.plan.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && c.text == changed.text), "画面与输入未共用当前扩展几何");
    }

    private void fieldHardBreaks() {
        for (String raw : new String[] {"A\u2028B", "A\u2029B"}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            Host host = new Host();
            SceneNode[] node = {null};
            club.heiqi.uilib.ui.scene.edit.SceneEditState[] state = {null};
            ScenePageSession page =
                    own(
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    font,
                                    context -> {
                                        state[0] =
                                                new club.heiqi.uilib.ui.scene.edit.SceneEditState(
                                                        domain, raw);
                                        node[0] =
                                                club.heiqi.uilib.ui.scene.control.SceneTextField
                                                        .create(
                                                                context,
                                                                state[0],
                                                                domain.signal(SceneTheme.dark()),
                                                                domain.signal(false),
                                                                s -> {})
                                                        .textPolicy(
                                                                new club.heiqi.uilib.ui.scene.text
                                                                        .TextPolicy(
                                                                        club.heiqi.uilib.ui.scene
                                                                                .text.TextPolicy
                                                                                .Direction.LTR,
                                                                        club.heiqi.uilib.ui.scene
                                                                                .text.TextPolicy
                                                                                .Wrap.NONE,
                                                                        "und",
                                                                        0,
                                                                        40))
                                                        .size(Size.fixed(140), Size.natural());
                                        context.mount(node[0]);
                                    }));
            page.frame();
            SceneGeometryView.Entry entry =
                    read(domain, () -> page.pipeline().current().geometry.get(node[0]));
            require(
                    entry.text.lines().size() == 1
                            && entry.text.lines().get(0).logicalEnd == raw.length(),
                    "LS/PS在单行字段形成隐藏硬行");
            require(
                    entry.text.logicalText.equals(raw)
                            && read(domain, () -> state[0].text().get()).equals(raw),
                    "硬换行投影改写源文");
            require(
                    entry.text.lines().get(0).clusters().stream()
                            .anyMatch(
                                    c ->
                                            c.logicalStart == 1
                                                    && c.logicalEnd == 2
                                                    && c.sourceStart == 1
                                                    && c.sourceEnd == 2),
                    "硬换行标记来源映射丢失");
            require(entry.box.height >= 40 + node[0].padding() * 2, "单行字段测量与最终几何不一致");
            TextLayout.Cluster last =
                    entry.text.lines().get(0).clusters().stream()
                            .filter(c -> c.logicalStart == 2)
                            .findFirst()
                            .get();
            int x = entry.box.x + node[0].padding() + (int) Math.floor(last.trailingX()),
                    y = entry.box.y + node[0].padding() + 2;
            page.pointer(ScenePointerEvent.down(0, x, y));
            page.pointer(ScenePointerEvent.up(0, x, y));
            page.frame();
            require(read(domain, () -> state[0].snapshot().get().caret) == 3, "点击正文末尾仍使用第一硬行几何");
            page.edit(
                    club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(
                            club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.LEFT,
                            false,
                            null));
            page.frame();
            require(read(domain, () -> state[0].snapshot().get().caret) == 2, "可见标记后的键盘光标错位");
            for (boolean area : new boolean[] {false, true}) {
                ReactiveDomain other = ReactiveDomain.direct();
                Host otherHost = new Host();
                SceneNode[] body = {null};
                ScenePageSession multi =
                        own(
                                ScenePageSession.open(
                                        other,
                                        otherHost,
                                        font,
                                        context -> {
                                            body[0] =
                                                    area
                                                            ? club.heiqi.uilib.ui.scene.control
                                                                    .SceneTextArea.create(
                                                                    context,
                                                                    new club.heiqi.uilib.ui.scene
                                                                            .edit.SceneEditState(
                                                                            other,
                                                                            raw,
                                                                            club.heiqi.uilib.ui
                                                                                    .scene.edit
                                                                                    .SceneEditState
                                                                                    .Mode
                                                                                    .MULTI_LINE),
                                                                    other.signal(SceneTheme.dark()),
                                                                    other.signal(false),
                                                                    s -> {})
                                                            : club.heiqi.uilib.ui.scene.control
                                                                    .SceneLabel.create(
                                                                    context,
                                                                    other.signal(raw),
                                                                    other.signal(SceneTheme.dark()),
                                                                    club.heiqi.uilib.ui.scene
                                                                            .control.SceneTextPolicy
                                                                            .OVERFLOW);
                                            // 使用正式入口默认策略，避免手动policy掩盖标签或Area的硬行回归。
                                            body[0].size(Size.fixed(140), Size.natural());
                                            context.mount(body[0]);
                                        }));
                multi.frame();
                TextLayout layout =
                        read(other, () -> multi.pipeline().current().geometry.get(body[0]).text);
                require(
                        layout.lines().size() == 2 && layout.logicalText.equals(raw),
                        "默认NoWrap/Area吞掉硬行或改变源文");
            }
        }
    }

    private void fieldDirectionPolicy() {
        for (String raw : new String[] {".", "A\u0085אבג"}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            Host host = new Host();
            SceneNode[] field = {null};
            club.heiqi.uilib.ui.scene.edit.SceneEditState[] state = {null};
            ScenePageSession page =
                    own(
                            ScenePageSession.open(
                                    domain,
                                    host,
                                    font,
                                    context -> {
                                        state[0] =
                                                new club.heiqi.uilib.ui.scene.edit.SceneEditState(
                                                        domain, raw);
                                        field[0] =
                                                club.heiqi.uilib.ui.scene.control.SceneTextField
                                                        .create(
                                                                context,
                                                                state[0],
                                                                domain.signal(SceneTheme.dark()),
                                                                domain.signal(false),
                                                                s -> {})
                                                        .textPolicy(
                                                                new club.heiqi.uilib.ui.scene.text
                                                                        .TextPolicy(
                                                                        club.heiqi.uilib.ui.scene
                                                                                .text.TextPolicy
                                                                                .Direction.RTL,
                                                                        club.heiqi.uilib.ui.scene
                                                                                .text.TextPolicy
                                                                                .Wrap.EMERGENCY,
                                                                        "he",
                                                                        15,
                                                                        60))
                                                        .size(Size.fixed(140), Size.natural());
                                        context.mount(field[0]);
                                    }));
            page.frame();
            SceneGeometryView.Entry entry =
                    read(domain, () -> page.pipeline().current().geometry.get(field[0]));
            require(
                    entry.text.lines().size() == 1 && entry.text.lines().get(0).lineHeight == 60,
                    "单行字段覆盖声明policy或错误分割控制标记");
            require(entry.box.height >= 60 + field[0].padding() * 2, "自然测量忽略声明行高");
            TextLayout.Cluster cluster =
                    entry.text.lines().get(0).clusters().stream()
                            .filter(c -> c.logicalStart == (raw.equals(".") ? 0 : 2))
                            .findFirst()
                            .get();
            require(cluster.rtl && cluster.leadingX() > cluster.trailingX(), "最终行方向事实未保留到字段呈现");
            int
                    x =
                            entry.box.x
                                    + field[0].padding()
                                    + (int) Math.floor(cluster.trailingX() + cluster.advance * .1f),
                    y = entry.box.y + field[0].padding() + 2;
            page.pointer(ScenePointerEvent.down(0, x, y));
            page.pointer(ScenePointerEvent.up(0, x, y));
            page.frame();
            require(
                    read(domain, () -> state[0].snapshot().get().caret) == cluster.sourceEnd,
                    "字段指针重新使用了不同Bidi/显示投影");
        }
    }

    private void unicodeProfiles() throws Exception {
        club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile bundled =
                club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.bundled();
        club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile[] current = {bundled};
        club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Source rules = () -> current[0];
        FontTextService local =
                own(
                        new FontTextService(
                                font.defaultCandidates(),
                                new LwjglFontEngine(),
                                4,
                                8,
                                4096,
                                rules));
        ReactiveDomain domain = ReactiveDomain.direct();
        club.heiqi.uilib.ui.scene.edit.SceneEditState[] state = {null};
        domain.execute(
                () ->
                        state[0] =
                                new club.heiqi.uilib.ui.scene.edit.SceneEditState(
                                        domain,
                                        "\n\u0301😀",
                                        club.heiqi.uilib.ui.scene.edit.SceneEditState.Mode
                                                .MULTI_LINE,
                                        rules));
        long before = local.generation();
        int[] invalidated = {0};
        own(local.onInvalidated(() -> invalidated[0]++));
        club.heiqi.uilib.ui.scene.text.unicode.EmojiData custom =
                club.heiqi.uilib.ui.scene.text.unicode.EmojiData.read(
                        "explicit-test",
                        new java.io.StringReader("1F600 ; Emoji\n1F600 ; Emoji_Presentation\n"),
                        new java.io.StringReader("1F600 ; Basic_Emoji\n"));
        domain.execute(
                () -> {
                    current[0] =
                            new club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile(
                                    custom);
                    local.refreshUnicodeRules();
                });
        require(invalidated[0] == 1, "显式规则刷新没有通知稳定页面或重复通知");
        local.refreshUnicodeRules();
        require(invalidated[0] == 1, "同快照刷新反复失效");
        require(
                local.generation() > before && local.emojiVersion().equals("explicit-test"),
                "共享规则快照变化未使字体代际失效");
        domain.execute(
                () -> {
                    state[0].deleteBackward();
                    state[0].deleteBackward();
                    require(state[0].text().get().equals("\n"), "注入规则下字素删除误吞换行");
                });
        local.reloadEmojiData(bundled.emoji);
        require(
                local.emojiVersion().equals(bundled.emoji.version)
                        && font.emojiVersion().equals(bundled.emoji.version),
                "局部emoji重载改变默认字体规则");
        require(
                rules.snapshot() == current[0] && rules.snapshot().emoji == custom,
                "局部字体重载反向修改编辑规则Source");
    }

    private void dynamicDeclaration() {
        TextContent content = TextContent.richTags("<a href=\"x\"><b><u>正文&amp;</u></b></a>");
        TextContent.Resolved first = content.resolve(new TextStyle("default", 18, 0x7fffffff));
        TextContent.Resolved next =
                content.resolve(
                        new TextStyle("minecraft", 32, 0x3f112233, font.defaultCandidates()));
        require(
                first.text.input == next.text.input
                        && first.links().get(0).link == next.links().get(0).link,
                "基础样式变化重新建立源结构或链接身份");
        require(
                next.text.styleAt(0).logicalEm == 32
                        && next.text.styleAt(0).color == 0x3f112233
                        && next.text.styleAt(0).fontStyle.weight == 700,
                "稳定声明未派生当前基础样式");
        require(
                next.text.styleAt(0).decoration.underlineThickness
                        > first.text.styleAt(0).decoration.underlineThickness,
                "装饰冻结旧字号");
        require(first.serializeRichTags().equals(next.serializeRichTags()), "样式派生改变规范源码");
    }

    private void sourceAndFailure() {
        TextStyle base = new TextStyle("default", 18, 0xffffffff);
        String raw = "<b>中😀&lt;</b><br/>&amp;";
        TextContent.Resolved content =
                TextContent.of(
                                TextContent.Mode.RICH_TAGS,
                                TextInput.utf8(raw.getBytes(StandardCharsets.UTF_8)))
                        .resolve(base);
        TextInput mapped = content.text.input;
        require(
                mapped.text.equals("中😀<\n&") && mapped.sourceUnit == TextInput.SourceUnit.BYTE,
                "UTF8实体和br转换错误");
        String[] tokens = {"中", "😀", "&lt;", "<br/>", "&amp;"};
        int[] logical = {0, 1, 3, 4, 5};
        for (int i = 0; i < tokens.length; i++) {
            int start = raw.indexOf(tokens[i]);
            int expectedStart = raw.substring(0, start).getBytes(StandardCharsets.UTF_8).length;
            int expectedEnd =
                    raw.substring(0, start + tokens[i].length())
                            .getBytes(StandardCharsets.UTF_8)
                            .length;
            require(
                    mapped.sourceStart(logical[i]) == expectedStart
                            && mapped.sourceEnd(logical[i]) == expectedEnd,
                    "byte来源未覆盖完整token/码点");
        }
        require(
                mapped.sourceStart(1) == mapped.sourceStart(2)
                        && mapped.sourceEnd(1) == mapped.sourceEnd(2),
                "代理对被切割");
        int consumed = 0;
        for (TextInput.SourceSegment segment : mapped.sourceSegments()) {
            require(segment.sourceStart == consumed, "来源片段缺口");
            consumed = segment.sourceEnd;
        }
        require(consumed == raw.getBytes(StandardCharsets.UTF_8).length, "尾部源码丢失");
        for (String invalid :
                new String[] {
                    "<b>中</i>",
                    "<a href='x'>中</a>",
                    "<a href=\"x\">中",
                    "<unknown>中</unknown>",
                    "<b color=\"#ffffff\">中</b>",
                    "&quot;",
                    "<B>中</B>",
                    "<color value=\"NaN\">中</color>",
                    "<size value=\"NaN\">中</size>"
                }) {
            TextContent.Resolved failed = TextContent.richTags(invalid).resolve(base);
            require(
                    failed.text.input.text.equals(invalid)
                            && failed.links().isEmpty()
                            && !failed.text.diagnostics().isEmpty(),
                    "非法内容被修复或半解析");
            TextLayout.Diagnostic diagnostic = failed.text.diagnostics().get(0);
            require(
                    diagnostic.sourceStart >= 0
                            && diagnostic.sourceEnd > diagnostic.sourceStart
                            && diagnostic.sourceEnd <= invalid.length(),
                    "诊断源位置无效");
            require(
                    TextContent.richTags(failed.serializeRichTags())
                            .resolve(base)
                            .text
                            .input
                            .text
                            .equals(invalid),
                    "失败序列化丢原文");
        }
        TextContent nested = TextContent.richTags("<a href=\"外&amp;值\">A<a href=\"内\">B</a>C</a>");
        TextContent.Resolved a = nested.resolve(base),
                b = nested.resolve(base.withColor(0xff112233));
        require(
                a.linkAt(0) == a.linkAt(2)
                        && a.linkAt(0) != a.linkAt(1)
                        && a.linkAt(0) == b.linkAt(0),
                "嵌套或同声明主题身份错误");
        TextContent.Resolved c = TextContent.richTags(a.serializeRichTags()).resolve(base);
        require(
                c.linkAt(0).target.equals("外&值")
                        && c.linkAt(1).target.equals("内")
                        && c.linkAt(2).target.equals("外&值"),
                "往返链接目标错误");
    }

    private void gestureIdentity() {
        Fixture f = own(new Fixture("<a href=\"A\">AAA</a> 空 <a href=\"B\">BBB</a>", false));
        int[] a = f.point(0), b = f.point(f.entry().text.logicalText.indexOf("BBB"));
        f.events(ScenePointerEvent.down(0, a[0], a[1]), ScenePointerEvent.up(0, b[0], b[1]));
        require(f.activated.isEmpty(), "同节点A按下B释放误触发");
        f.events(ScenePointerEvent.down(0, a[0], a[1]));
        f.domain.execute(() -> f.content.set(TextContent.richTags(f.content.get().source)));
        f.page.frame();
        f.events(ScenePointerEvent.up(0, a[0], a[1]));
        require(f.activated.isEmpty(), "新声明同源码同href复活旧手势");
        f.events(ScenePointerEvent.down(0, a[0], a[1]));
        f.domain.execute(() -> f.theme.set(SceneTheme.light()));
        f.page.frame();
        f.events(ScenePointerEvent.up(0, a[0], a[1]));
        require(f.activated.size() == 1, "仅主题变化无因丢声明身份");
        f.activated.clear();
        for (int action = 0; action < 4; action++) {
            f.events(ScenePointerEvent.down(0, a[0], a[1]));
            if (action == 0) {
                f.domain.execute(() -> f.enabled.set(false));
                f.page.frame();
                f.domain.execute(() -> f.enabled.set(true));
                f.page.frame();
            }
            if (action == 1)
                f.domain.execute(
                        () -> {
                            f.page.runtime().captureMount(f.node).detach();
                            f.page.runtime().attach(f.viewport, f.node);
                        });
            if (action == 2) f.events(ScenePointerEvent.cancel());
            if (action == 3) {
                f.domain.execute(() -> f.visible.set(false));
                f.page.frame();
                f.domain.execute(() -> f.visible.set(true));
                f.page.frame();
            }
            f.events(
                    ScenePointerEvent.syntheticActivation(a[0], a[1]),
                    ScenePointerEvent.up(0, a[0], a[1]));
            require(f.activated.isEmpty(), "失活/重挂载/cancel尾部复活");
        }
        f.events(
                ScenePointerEvent.down(1, a[0], a[1]),
                ScenePointerEvent.down(0, a[0], a[1]),
                ScenePointerEvent.up(1, a[0], a[1]),
                ScenePointerEvent.up(0, a[0], a[1]));
        require(f.activated.isEmpty(), "第二键尾部误激活");
        f.events(ScenePointerEvent.syntheticActivation(b[0], b[1]));
        require(f.activated.size() == 1 && f.activated.get(0).equals("B"), "synthetic未按内部目标");
    }

    private void geometry() {
        Fixture f =
                own(
                        new Fixture(
                                "普通 <a href=\"A\">多行链接 אבג 👩‍💻 中文文字 中文文字</a> 尾部<br/><a href=\"B\">第二链接 第二链接 第二链接 第二链接</a>",
                                true));
        SceneGeometryView.Entry first = f.entry();
        require(first.text.lines().size() > 1, "未经过真实多行布局");
        TextContent.Resolved resolved =
                read(f.domain, () -> f.content.get().resolve(f.theme.get().textStyle));
        boolean hidden = false, bidi = false, emoji = false;
        int visibleCount = 0;
        for (TextLayout.Line line : first.text.lines())
            for (TextLayout.Cluster cluster : line.clusters()) {
                if (cluster.logicalStart == cluster.logicalEnd || cluster.advance < 1) continue;
                if (first.text
                        .logicalText
                        .substring(cluster.logicalStart, cluster.logicalEnd)
                        .contains("א")) bidi = true;
                if (first.text
                        .logicalText
                        .substring(cluster.logicalStart, cluster.logicalEnd)
                        .contains("👩")) emoji = true;
                int x = first.box.x + (int) Math.floor(cluster.x + cluster.advance / 2),
                        y = first.box.y + (int) Math.floor(line.y + line.lineHeight / 2);
                boolean visible = first.clip == null || first.clip.contains(x, y);
                int before = f.activated.size();
                f.events(ScenePointerEvent.syntheticActivation(x, y));
                TextContent.Link link = resolved.linkAt(cluster.logicalStart);
                require(
                        f.activated.size() == before + (visible && link != null ? 1 : 0),
                        "可见完整簇/祖先clip链接资格错误");
                if (visible && link != null) visibleCount++;
                if (!visible && link != null) hidden = true;
            }
        require(visibleCount > 0 && hidden && bidi && emoji, "真实多行/Bidi/emoji/clip反例未触达");
        // 同一文字簇上下跨裁剪边界：交集内有目标，交集外没有目标。
        TextLayout.Line partial = first.text.lines().get(1);
        TextLayout.Cluster partialCluster = partial.clusters().get(0);
        int px = first.box.x + (int) Math.floor(partialCluster.x + partialCluster.advance / 2);
        int edge = first.clip.y + first.clip.height;
        int clippedBefore = f.activated.size();
        f.events(
                ScenePointerEvent.syntheticActivation(px, edge - 1),
                ScenePointerEvent.syntheticActivation(px, edge));
        require(
                f.activated.size() == clippedBefore + 1
                        && f.activated.get(clippedBefore).equals("A"),
                "部分裁剪簇未按可见交集命中");
        int[] old = f.point(0);
        int before = f.activated.size();
        f.events(ScenePointerEvent.scroll(3, 3, -1));
        SceneGeometryView.Entry scrolled = f.entry();
        require(scrolled.box.y < first.box.y, "正式滚动未变更坐标");
        f.events(ScenePointerEvent.syntheticActivation(old[0], old[1]));
        require(
                f.activated.size() == before + 1 && f.activated.get(before).equals("A"),
                "滚动后旧普通正文位置应对应当前第二行链接A");
        f.host.width = 90;
        f.domain.execute(
                () ->
                        f.theme.set(
                                SceneTheme.dark()
                                        .withTextStyle(new TextStyle("default", 26, 0xffffffff))));
        f.page.frame();
        SceneGeometryView.Entry changed = f.entry();
        require(
                changed.text != scrolled.text
                        && changed.text.lines().size() > scrolled.text.lines().size(),
                "宽度/字号未重排");
        require(
                f.host.plan.commands().stream()
                        .anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && c.text == changed.text),
                "重排后paint与hit不同代");
        Fixture cluster = own(new Fixture("<a href=\"A\">👩</a><a href=\"B\">‍💻</a>", false));
        TextLayout merged = cluster.entry().text;
        require(merged.lines().get(0).clusters().size() == 1, "emoji被链接边界拆簇");
        require(
                merged.diagnostics().stream()
                        .anyMatch(d -> d.kind == TextLayout.Diagnostic.Kind.STYLE_COALESCED),
                "跨链接簇缺来源诊断");
        int[] p = cluster.point(0);
        cluster.events(ScenePointerEvent.syntheticActivation(p[0], p[1]));
        require(
                cluster.activated.size() == 1 && cluster.activated.get(0).equals("A"),
                "完整簇未归leading链接");
    }

    private void handlerFailure() {
        Fixture f = own(new Fixture("<a href=\"A\">链接</a>", false));
        int[] a = f.point(0);
        f.fail = true;
        Throwable original = null;
        try {
            f.events(
                    ScenePointerEvent.down(0, a[0], a[1]),
                    ScenePointerEvent.up(0, a[0], a[1]),
                    ScenePointerEvent.down(1, a[0], a[1]));
        } catch (RuntimeException failure) {
            original = failure;
        }
        require(original == f.failure, "业务异常身份丢失");
        f.fail = false;
        f.events(
                ScenePointerEvent.syntheticActivation(a[0], a[1]),
                ScenePointerEvent.up(1, a[0], a[1]));
        require(f.activated.isEmpty(), "handler失败后物理尾部误激活");
        f.events(ScenePointerEvent.syntheticActivation(a[0], a[1]));
        require(f.activated.size() == 1, "结束尾部后新手势不能激活");
    }

    private final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final Host host = new Host();
        Signal<TextContent> content;
        Signal<SceneTheme> theme;
        Signal<Boolean> enabled, visible;
        final List<String> activated = new ArrayList<String>();
        SceneNode node, viewport;
        final ScenePageSession page;
        final RuntimeException failure = new RuntimeException("业务故障");
        boolean fail;

        Fixture(String raw, boolean clipped) {
            host.width = 140;
            host.height = 180;
            page =
                    ScenePageSession.open(
                            domain,
                            host,
                            font,
                            context -> {
                                content = domain.signal(TextContent.richTags(raw));
                                theme = domain.signal(SceneTheme.dark());
                                enabled = domain.signal(true);
                                visible = domain.signal(true);
                                node =
                                        SceneRichText.create(
                                                        context,
                                                        content,
                                                        theme,
                                                        target -> {
                                                            if (fail) throw failure;
                                                            activated.add(target);
                                                        })
                                                .size(Size.fill(1), Size.natural())
                                                .bind(
                                                        club.heiqi.uilib.ui.scene.runtime
                                                                .NodeBinding.value(
                                                                enabled, SceneNode::enabled))
                                                .bind(
                                                        club.heiqi.uilib.ui.scene.runtime
                                                                .NodeBinding.value(
                                                                visible, SceneNode::visible));
                                viewport =
                                        new SceneNode()
                                                .size(
                                                        Size.fill(1),
                                                        clipped ? Size.fixed(35) : Size.fill(1))
                                                .scrollable(clipped)
                                                .scrollStep(20)
                                                .add(node);
                                context.mount(viewport);
                            });
            page.frame();
        }

        SceneGeometryView.Entry entry() {
            return read(domain, () -> page.pipeline().current().geometry.get(node));
        }

        int[] point(int logical) {
            SceneGeometryView.Entry entry = entry();
            for (TextLayout.Line line : entry.text.lines())
                for (TextLayout.Cluster cluster : line.clusters())
                    if (cluster.logicalStart <= logical && cluster.logicalEnd > logical)
                        return new int[] {
                            entry.box.x + (int) Math.floor(cluster.x + cluster.advance / 2),
                            entry.box.y + (int) Math.floor(line.y + line.lineHeight / 2)
                        };
            throw new AssertionError("未找到目标完整簇");
        }

        void events(ScenePointerEvent... events) {
            for (ScenePointerEvent event : events) page.pointer(event);
            page.frame();
        }

        public void close() {
            page.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(ReactiveDomain domain, java.util.function.Supplier<T> value) {
        Object[] result = new Object[1];
        domain.execute(() -> result[0] = value.get());
        return (T) result[0];
    }

    private static final class Host implements SceneHost {
        int width = 180, height = 120;
        PaintPlan plan;

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public long metricsRevision() {
            return 0;
        }

        public void replay(PaintPlan plan) {
            this.plan = plan;
        }
    }
}
