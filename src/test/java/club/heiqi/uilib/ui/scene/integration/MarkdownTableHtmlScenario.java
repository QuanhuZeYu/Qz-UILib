package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.DocumentResources;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.InlineContent;
import club.heiqi.uilib.ui.scene.text.ParagraphContent;
import club.heiqi.uilib.ui.scene.text.StyledText;
import club.heiqi.uilib.ui.scene.text.TextSemanticSpan;
import club.heiqi.uilib.ui.scene.text.MarkdownDocument;
import club.heiqi.uilib.ui.scene.text.TableContent;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.MathTextFontSources;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * specs/content/uilib-markdown-spec.md 第 2、4 节 表格/HTML 的阅读和输入回归。期望来自固定 markdown-it14.2 与 jsoup1.19.1
 * 的实际研究证据及规格，手写语义断言，不把生产输出转成期望或结构快照。 真实字体场景使用共同 scene；不冒充宿主 GL 或双实例部署验收。
 */
public final class MarkdownTableHtmlScenario extends TestApplication {
    private static final TextStyle BASE = new TextStyle("default", 22, 0xffabcdef);

    public static void main(String[] args) throws Exception {
        new MarkdownTableHtmlScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        List<Throwable> failures = new ArrayList<>();
        runScenario("固定frontmatter默认表格语义与完整UTF16及BYTE来源", this::frontmatterOracle, failures);
        runScenario("frontmatter代理对节点别名及错误精确原位置", this::frontmatterSources, failures);
        runScenario("frontmatter真实字体主题重排与随后正文保持", this::frontmatterScene, failures);
        runScenario("YAML时间时区按词法单位转换且保留来源", this::frontmatterTimestamp, failures);
        runScenario("管道列数对齐补空裁剪及成熟块结束边界", this::pipeStructure, failures);
        runScenario("CRLF代理对转义管道代码及typed原子的原文来源", this::pipeSources, failures);
        runScenario("HTML全部内容类别及inline与block成熟Markdown边界", this::htmlSemantics, failures);
        runScenario("HTML与Markdown共同语义嵌套来源及主题身份", this::inlineMeaning, failures);
        runScenario("HTML实体有效attrs无效尺寸原value诊断不吞后文", this::htmlSources, failures);
        runScenario("同段密集HTML code保持精确容器与管道来源", this::htmlCodeRanges, failures);
        runScenario("真实字体表格列行主题动态重排与全投影对齐", this::tableScene, failures);
        runScenario("超宽公式独立横滚裁剪链接capture尾事件与释放", this::overflowScene, failures);
        runScenario("共享图像worker完成自动更新表格cell行高并释放", this::imageScene, failures);
        runScenario("居中表格半像素位置保留固定图像测量尺寸", this::centeredFixedImage, failures);
        if (!failures.isEmpty()) {
            AssertionError error = new AssertionError("Markdown表格HTML场景失败=" + failures.size());
            for (Throwable failure : failures) error.addSuppressed(failure);
            throw error;
        }
    }

    private void runScenario(String name, CheckedRunnable action, List<Throwable> failures)
            throws Exception {
        try {
            scenario(name, action);
        } catch (AssertionError | RuntimeException failure) {
            failures.add(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private void frontmatterOracle() throws Exception {
        Map<String, Object> corpus;
        try (java.io.InputStream stream =
                getClass().getResourceAsStream("/markdown/reference/frontmatter-corpus.json")) {
            require(stream != null, "缺少固定frontmatter独立语料");
            corpus =
                    (Map<String, Object>)
                            new org.yaml.snakeyaml.Yaml()
                                    .load(
                                            new java.io.InputStreamReader(
                                                    stream, StandardCharsets.UTF_8));
        }
        List<String> failures = new ArrayList<>();
        for (Map<String, Object> item : (List<Map<String, Object>>) corpus.get("cases")) {
            String id = (String) item.get("id"), source = (String) item.get("source");
            // 固定扩展把首行quote内部也误作frontmatter并报YAML错；不以吞容器正文作为验收。
            if (id.equals("quote_front")) continue;
            try {
                org.jsoup.nodes.Document expected =
                        org.jsoup.Jsoup.parseBodyFragment((String) item.get("html"));
                org.jsoup.nodes.Element table = expected.selectFirst("table.frontmatter");
                for (boolean bytes : new boolean[] {false, true}) {
                    DocumentContent doc = checked(source, bytes);
                    List<TableContent> tables = new ArrayList<>();
                    for (DocumentContent.Block block : doc.blocks())
                        if (block.table != null) tables.add(block.table);
                    if (table != null) {
                        require(!tables.isEmpty(), id + " frontmatter未转共同表格");
                        List<org.jsoup.nodes.Element> rows = table.select("tbody > tr");
                        equal(rows.size(), tables.get(0).rows().size(), id + " 元数据行数量");
                        for (int i = 0; i < rows.size(); i++) {
                            TableContent.Row row = tables.get(0).rows().get(i);
                            equal(2, row.cells().size(), id + " 行头和值两列");
                            require(
                                    row.cells().get(0).header && !row.cells().get(1).header,
                                    id + " th行头身份");
                            equal(
                                    frontmatterHtml(rows.get(i).child(0)),
                                    frontmatterBlocks(row.cells().get(0).content()),
                                    id + " key");
                            equal(
                                    frontmatterHtml(rows.get(i).child(1)),
                                    frontmatterBlocks(row.cells().get(1).content()),
                                    id + " value");
                        }
                    } else require(tables.isEmpty(), id + " 无表格参照却产生表格");
                    if (item.get("yamlError") != null) {
                        require(!doc.diagnostics().isEmpty(), id + " YAML错误无定位诊断");
                        String tail = source.substring(source.lastIndexOf("---") + 3).trim();
                        if (!tail.isEmpty())
                            require(
                                    doc.blocks().stream()
                                            .anyMatch(
                                                    block ->
                                                            block.paragraph != null
                                                                    && block.paragraph
                                                                            .resolve(BASE)
                                                                            .text
                                                                            .input
                                                                            .text
                                                                            .equals(tail)),
                                    id + " YAML错误吞掉后续正文");
                    } else if (item.get("frontMatter") != null)
                        for (TextLayout.Diagnostic diagnostic : doc.diagnostics()) {
                            // CommonMark输入NUL已有INVALID_INPUT合同，frontmatter消费不撤销原文诊断。
                            require(
                                    source.indexOf(0) >= 0
                                            && diagnostic.kind
                                                    == TextLayout.Diagnostic.Kind.INVALID_INPUT,
                                    id + " 正常frontmatter产生额外诊断");
                        }
                }
            } catch (AssertionError | RuntimeException error) {
                failures.add(id + ": " + error);
            }
        }
        require(failures.isEmpty(), "固定frontmatter语义差异:\n" + String.join("\n", failures));
    }

    private static List<Object> frontmatterHtml(org.jsoup.nodes.Element element) {
        List<Object> result = new ArrayList<>();
        for (org.jsoup.nodes.Node child : element.childNodes()) {
            if (child instanceof org.jsoup.nodes.TextNode) {
                String text = ((org.jsoup.nodes.TextNode) child).getWholeText();
                if (!text.isEmpty()) result.add(Arrays.asList("text", text));
            } else if (child instanceof org.jsoup.nodes.Element) {
                org.jsoup.nodes.Element nested = (org.jsoup.nodes.Element) child;
                if (nested.normalName().equals("code"))
                    result.add(Arrays.asList("code", nested.wholeText()));
                else result.add(Arrays.asList(nested.normalName(), frontmatterHtml(nested)));
            }
        }
        return result;
    }

    private static List<Object> frontmatterBlocks(List<DocumentContent.Block> blocks) {
        List<Object> result = new ArrayList<>();
        for (DocumentContent.Block block : blocks) {
            if (block.paragraph != null) {
                TextContent.Resolved resolved = block.paragraph.resolve(BASE);
                require(
                        resolved.links().isEmpty() && block.paragraph.inlineContents().isEmpty(),
                        "元数据不得解释Markdown链接数学或图片");
                boolean mono =
                        !resolved.text.spans().isEmpty()
                                && resolved.text
                                        .spans()
                                        .get(0)
                                        .style
                                        .font
                                        .equals(
                                                club.heiqi.uilib.ui.scene.text.font
                                                        .MathTextFontSources.MONO);
                if (!resolved.text.input.text.isEmpty())
                    result.add(Arrays.asList(mono ? "code" : "text", resolved.text.input.text));
            } else if (block.kind == DocumentContent.Block.Kind.LIST)
                result.add(Arrays.asList("ul", frontmatterBlocks(block.children())));
            else if (block.kind == DocumentContent.Block.Kind.LIST_ITEM)
                result.add(Arrays.asList("li", frontmatterBlocks(block.children())));
        }
        return result;
    }

    private void frontmatterTimestamp() {
        for (String zone : new String[] {"+00:15", "-00:15", "+02", "-02", "+01:30", "-01:30", "Z"}) {
            String value = "2026-10-13T00:00:00" + zone;
            String source = "---\n!!timestamp " + value + "\n---\n正文";
            // Java8 ISO_OFFSET_DATE_TIME要求分钟；YAML原输入仍保留合法短小时偏移。
            String oracle = !zone.equals("Z") && zone.indexOf(':') < 0 ? value + ":00" : value;
            String expected = java.time.format.DateTimeFormatter
                    .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(java.time.ZoneOffset.UTC)
                    .format(java.time.OffsetDateTime.parse(oracle).toInstant());
            for (boolean bytes : new boolean[] {false, true}) {
                DocumentContent document = checked(source, bytes);
                require(document.diagnostics().isEmpty(), "合法timestamp不能产生诊断");
                TextInput presented = document.blocks().get(0).table.rows().get(0).cells().get(1)
                        .content().get(0).paragraph.resolve(BASE).text.input;
                equal(expected, presented.text, "timestamp时区不能按数值大小猜小时或分钟: " + zone);
                int start = source.indexOf("!!timestamp");
                exactRange(presented, 0, source, start, start + "!!timestamp ".length() + value.length(), bytes);
                require(document.blocks().size() == 2, "timestamp转换不得吞掉随后正文");
            }
        }
    }

    private void frontmatterSources() {
        String invalid = "---\r\n😀: [1\r\n---\r\n正文";
        String control = "---\r\n😀: \"x\u0001y\"\r\n---\r\n正文";
        for (boolean bytes : new boolean[] {false, true}) {
            DocumentContent failed = checked(control, bytes);
            require(!failed.diagnostics().isEmpty(), "成熟YAML字符限制应定位诊断并保留源码");
            TextLayout.Diagnostic diagnostic = failed.diagnostics().get(0);
            int bad = control.indexOf(1);
            equal(
                    offset(control, bad, bytes),
                    diagnostic.sourceStart,
                    "ReaderException代理对后的错误start");
            equal(offset(control, bad + 1, bytes), diagnostic.sourceEnd, "ReaderException错误end");
            require(
                    failed.blocks().stream()
                            .anyMatch(
                                    block ->
                                            block.paragraph != null
                                                    && block.paragraph
                                                            .resolve(BASE)
                                                            .text
                                                            .input
                                                            .text
                                                            .equals("正文")),
                    "成熟YAML ReaderException吞掉后文");
            DocumentContent syntax = checked(invalid, bytes);
            require(!syntax.diagnostics().isEmpty(), "未闭合YAML应诊断");
            require(
                    syntax.blocks().stream()
                            .anyMatch(
                                    block ->
                                            block.paragraph != null
                                                    && block.paragraph
                                                            .resolve(BASE)
                                                            .text
                                                            .input
                                                            .text
                                                            .equals("正文")),
                    "YAML错误吞掉后文");
        }
        String source = "---\r\n😀: \"a\\nb\"\r\nseq: &x [1, 2]\r\nalias: *x\r\n---\r\n# 正文";
        for (boolean bytes : new boolean[] {false, true}) {
            DocumentContent doc = checked(source, bytes);
            require(doc.diagnostics().isEmpty(), "合法YAML代理对与alias产生诊断");
            TableContent table = doc.blocks().get(0).table;
            TextInput key =
                    table.rows()
                            .get(0)
                            .cells()
                            .get(0)
                            .content()
                            .get(0)
                            .paragraph
                            .resolve(BASE)
                            .text
                            .input;
            int keyStart = source.indexOf("😀");
            exactRange(key, 0, source, keyStart, keyStart + "😀".length(), bytes);
            TextInput value =
                    table.rows()
                            .get(0)
                            .cells()
                            .get(1)
                            .content()
                            .get(0)
                            .paragraph
                            .resolve(BASE)
                            .text
                            .input;
            equal("a\nb", value.text, "块外原文中的YAMLquoted escape解码");
            int scalarStart = source.indexOf("\"a\\nb\"");
            exactRange(value, 0, source, scalarStart, scalarStart + "\"a\\nb\"".length(), bytes);
            TableContent.Cell alias = table.rows().get(2).cells().get(1);
            int aliasStart = source.indexOf("*x");
            equal(offset(source, aliasStart, bytes), alias.sourceStart, "alias cell必须定位引用自身");
            equal(offset(source, aliasStart + 2, bytes), alias.sourceEnd, "alias cell引用范围");
            DocumentContent.Block list = alias.content().get(0);
            for (DocumentContent.Block item : list.children()) {
                TextInput mapped = item.children().get(0).paragraph.resolve(BASE).text.input;
                exactRange(mapped, 0, source, aliasStart, aliasStart + 2, bytes);
            }
            equal(
                    offset(source, source.indexOf("# 正文"), bytes),
                    doc.blocks().get(1).sourceStart,
                    "消费前缀后正文source不可平移");
        }
    }

    private void frontmatterScene() throws Exception {
        String source =
                "---\ntitle: 工作台\nnotes: 很长的一段元数据，窗口变窄时应自动换行并保持正文位置。\n---\n\n# 正文\n[go](next) $x$";
        try (Fixture f = new Fixture(source, 800)) {
            f.page.frame();
            SceneNode[] table = new SceneNode[1];
            int[] height = new int[1];
            f.domain.execute(
                    () -> {
                        table[0] = f.root[0].children().get(0);
                        equal(4, table[0].children().size(), "frontmatter真实cell数量");
                        grid(f.geometry(), f.root[0], table[0], 2);
                        height[0] = f.geometry().get(table[0]).box.height;
                        f.theme[0].set(
                                SceneTheme.light()
                                        .withTextStyle(new TextStyle("default", 31, 0xff112233)));
                    });
            f.width[0] = 210;
            f.page.frame();
            f.domain.execute(
                    () -> {
                        require(f.root[0].children().get(0) == table[0], "主题窗口变化重建frontmatter声明");
                        grid(f.geometry(), f.root[0], table[0], 2);
                        require(
                                f.geometry().get(table[0]).box.height > height[0],
                                "frontmatter窄宽主题未自动更新行高");
                        require(f.root[0].children().size() > 1, "frontmatter吞掉后文");
                    });
        }
    }

    private void pipeStructure() {
        String source =
                "| D | L | C | R |\n| --- | :--- | :---: | ---: |\n"
                        + "| one | two | three | four | ignored |\n| missing |\n| | | | |\n\ntail";
        for (boolean bytes : new boolean[] {false, true}) {
            DocumentContent doc = checked(source, bytes);
            TableContent table = table(doc);
            equal(4, table.columnCount(), "额外正文列不能扩展列数");
            equal(4, table.rows().size(), "表头与正文行数");
            TableContent.Alignment[] alignment = {
                TableContent.Alignment.DEFAULT,
                TableContent.Alignment.LEFT,
                TableContent.Alignment.CENTER,
                TableContent.Alignment.RIGHT
            };
            for (int row = 0; row < table.rows().size(); row++) {
                equal(4, table.rows().get(row).cells().size(), "每行保持表头列数");
                for (int col = 0; col < 4; col++) {
                    TableContent.Cell cell = table.rows().get(row).cells().get(col);
                    equal(alignment[col], cell.alignment, "列对齐继承到正文");
                    equal(row == 0, cell.header, "表头语义");
                }
            }
            equal(Arrays.asList("one", "two", "three", "four"), rowText(table, 1), "超列只忽略显示");
            equal(Arrays.asList("missing", "", "", ""), rowText(table, 2), "缺列补空");
            equal(Arrays.asList("", "", "", ""), rowText(table, 3), "显式空单元格");
            require(!allText(doc).contains("ignored"), "额外列进入显示内容");
            require(allText(doc).endsWith("tail"), "表格吞后续段落");
        }
        for (String invalid :
                Arrays.asList(
                        "a|b\n---|---|---\nvalue|value",
                        "a|b|c\n---|---\nvalue|value",
                        "a|b\n---|wrong\nvalue|value")) {
            DocumentContent doc = checked(invalid, false);
            require(
                    find(doc.blocks(), DocumentContent.Block.Kind.TABLE).isEmpty(),
                    "不匹配表头/分隔列被识别为表格");
            require(allText(doc).contains("value|value"), "不成立的表格丢失普通正文");
        }
        // 固定参照把单独管道行识别为空正文行；有管道的标题仍终止表格。
        DocumentContent empty = checked("a|b\n-| -\n|\n\ntail", false);
        equal(Arrays.asList("", ""), rowText(table(empty), 1), "单独管道的空正文行");
        DocumentContent heading = checked("a|b\n-| -\n# x|y\nend", false);
        equal(1, table(heading).rows().size(), "标题不得进入表格正文");
        List<DocumentContent.Block> headings =
                find(heading.blocks(), DocumentContent.Block.Kind.HEADING);
        equal(1, headings.size(), "表格后标题保留");
        equal("x|y", text(headings.get(0)), "标题中的管道保持正文");
        require(allText(heading).endsWith("end"), "块边界吞末尾正文");
        DocumentContent lazy = checked("a|b\n-| -\nonly\n\nend", false);
        equal(Arrays.asList("only", ""), rowText(table(lazy), 1), "无管道的合法缺列正文");
    }

    private void pipeSources() {
        String source =
                "头|次\r\n-| -\r\n中😀x\\|y *z*|`q\\|r`\r\n"
                        + "[go](next) ![alt](asset.png) $x$|last\r\n\r\n尾";
        for (boolean bytes : new boolean[] {false, true}) {
            DocumentContent doc = checked(source, bytes);
            TableContent t = table(doc);
            TextContent.Resolved a = paragraph(t.rows().get(1).cells().get(0)).resolve(BASE);
            TextContent.Resolved code = paragraph(t.rows().get(1).cells().get(1)).resolve(BASE);
            equal("中😀x|y z", a.text.input.text, "解除表格转义后统一inline解析");
            equal("q|r", code.text.input.text, "代码内转义管道先由表格解除");
            mapped(a.text.input, "z", source, "z", bytes);
            mapped(a.text.input, "😀", source, "😀", bytes);
            mapped(a.text.input, "|", source, "\\|", bytes);
            mapped(code.text.input, "q", source, "q", bytes);
            mapped(code.text.input, "r", source, "r", bytes);
            require(
                    a.text.styleAt(a.text.input.text.indexOf('z')).fontStyle.posture
                            == FontStyle.Posture.ITALIC,
                    "转义管道后强调未解析");
            equal(MathTextFontSources.MONO, code.text.styleAt(0).font, "表格内代码字体");
            TextContent.Resolved atoms = paragraph(t.rows().get(2).cells().get(0)).resolve(BASE);
            equal(1, atoms.links().size(), "表格内统一typed链接");
            equal("next", atoms.links().get(0).link.target, "链接目标");
            equal(2, atoms.inlineContents().size(), "表格内图片公式不是字面或DSL");
            InlineContent image = atoms.inlineContents().get(0),
                    math = atoms.inlineContents().get(1);
            equal(InlineContent.Kind.IMAGE, image.kind, "图片typed对象");
            equal("asset.png", image.source, "图片来源");
            equal("alt", image.alt, "图片替代正文");
            equal(InlineContent.Kind.MATH, math.kind, "数学typed对象");
            equal("x", math.math.source, "公式正文");
            atomRange(image, source, "![alt](asset.png)", bytes);
            atomRange(math, source, "$x$", bytes);
            TextContent.Resolved themed =
                    paragraph(t.rows().get(2).cells().get(0))
                            .resolve(new TextStyle("default", 31, 0xff112233));
            require(
                    atoms.links().get(0).link == themed.links().get(0).link
                            && image == themed.inlineContents().get(0)
                            && math == themed.inlineContents().get(1),
                    "主题变化重建链接或原子声明");
            require(allText(doc).endsWith("尾"), "CRLF表格吞后续原文");
        }
    }

    private void inlineMeaning() {
        String source = "lead <strong>S</strong><b>B</b><em>E</em><i>I</i><u>U</u><s>X</s><del>D</del> "
                + "<b><b>中😀&amp;</b></b> <a href='next'><i>L</i></a> "
                + "<strong>A<code>&lt;b&gt;</code>Z</strong> "
                + "<code>&lt;b&gt;literal&lt;/b&gt;</code> **M** *N* ~~O~~ tail";
        String expected = "lead SBEIUXD 中😀& L A<b>Z <b>literal</b> M N O tail";
        TextSemanticSpan.Kind[] kinds = {
            TextSemanticSpan.Kind.IMPORTANCE, TextSemanticSpan.Kind.ATTENTION,
            TextSemanticSpan.Kind.EMPHASIS, TextSemanticSpan.Kind.ALTERNATE_VOICE,
            TextSemanticSpan.Kind.ANNOTATION, TextSemanticSpan.Kind.NO_LONGER_ACCURATE,
            TextSemanticSpan.Kind.DELETION, TextSemanticSpan.Kind.ATTENTION,
            TextSemanticSpan.Kind.ATTENTION, TextSemanticSpan.Kind.ALTERNATE_VOICE,
            TextSemanticSpan.Kind.IMPORTANCE, TextSemanticSpan.Kind.IMPORTANCE,
            TextSemanticSpan.Kind.EMPHASIS, TextSemanticSpan.Kind.NO_LONGER_ACCURATE
        };
        String[] fragments = {"S", "B", "E", "I", "U", "X", "D", "中😀&", "中😀&", "L", "A<b>Z", "M", "N", "O"};
        for (boolean bytes : new boolean[] {false, true}) {
            TextContent content = checked(source, bytes).blocks().get(0).paragraph;
            TextContent.Resolved first = content.resolve(BASE);
            TextContent.Resolved themed = content.resolve(new TextStyle("default", 31, 0xff112233));
            equal(expected, first.text.input.text, "语义不能改正文或代码字面");
            equal(kinds.length, first.semantics().size(), "同样视觉与重复嵌套不可吞语义");
            for (int i = 0; i < kinds.length; i++) {
                TextSemanticSpan span = first.semantics().get(i);
                int start = expected.indexOf(fragments[i]);
                equal(kinds[i], span.kind, "中性含义/顺序 " + i);
                equal(start, span.start, "语义UTF16开始 " + i);
                equal(start + fragments[i].length(), span.end, "语义UTF16结束 " + i);
                require(span == content.semantics().get(i) && span == themed.semantics().get(i),
                        "主题派生重建语义声明 " + i);
            }
            TextSemanticSpan inner = first.semantics().get(8);
            equal(bytes ? TextInput.SourceUnit.BYTE : TextInput.SourceUnit.UTF16,
                    first.text.input.sourceUnit, "语义来源单位");
            int originalStart = source.indexOf("中😀&amp;");
            int originalEnd = originalStart + "中😀&amp;".length();
            equal(bytes ? source.substring(0, originalStart).getBytes(StandardCharsets.UTF_8).length : originalStart,
                    first.text.input.sourceStart(inner.start), "嵌套语义真实来源开始");
            equal(bytes ? source.substring(0, originalEnd).getBytes(StandardCharsets.UTF_8).length : originalEnd,
                    first.text.input.sourceEnd(inner.end - 1), "实体语义真实来源结束");
            equal("next", first.links().get(0).link.target, "语义不代替链接身份");
            require(first.links().get(0).link == themed.links().get(0).link, "语义派生更换链接");
            require(first.text.styleAt(expected.indexOf('S')).fontStyle.weight == 700
                            && first.text.styleAt(expected.indexOf('B')).fontStyle.weight == 700
                            && first.text.styleAt(expected.indexOf('I')).fontStyle.posture == FontStyle.Posture.ITALIC,
                    "语义补全改变既有视觉意图");
        }
        String[] captures = {"*<code>x*</code>", "**<code>x**</code>", "~~<code>x~~</code>", "*a<code>b*</code>c"};
        String[] capturedText = {"x*", "x**", "x~~", "ab*c"};
        int[] capturedEnd = {2, 3, 3, 3};
        TextSemanticSpan.Kind[] capturedKind = {TextSemanticSpan.Kind.EMPHASIS,
                TextSemanticSpan.Kind.IMPORTANCE, TextSemanticSpan.Kind.NO_LONGER_ACCURATE,
                TextSemanticSpan.Kind.EMPHASIS};
        for (int i = 0; i < captures.length; i++) {
            for (boolean bytes : new boolean[] {false, true}) {
                TextContent.Resolved captured = checked(captures[i], bytes).blocks().get(0).paragraph.resolve(BASE);
                equal(capturedText[i], captured.text.input.text, "延后代码捕获保持原字面合同");
                equal(1, captured.semantics().size(), "代码延后投影不能丢失进入时语义 " + captures[i]);
                TextSemanticSpan span = captured.semantics().get(0);
                equal(capturedKind[i], span.kind, "代码捕获的中性含义");
                require(span.start == 0 && span.end == capturedEnd[i], "语义随实际延迟正文结束而非源码遍历结束");
            }
        }
        TextContent.Resolved block = checked("<p><strong>A<b>B</b></strong><i>C</i></p>", false)
                .blocks().get(0).paragraph.resolve(BASE);
        equal("ABC", block.text.input.text, "HTML块共同正文");
        equal(3, block.semantics().size(), "HTML块递归须保留相同语义");
        equal(TextSemanticSpan.Kind.IMPORTANCE, block.semantics().get(0).kind, "HTML块strong");
        equal(TextSemanticSpan.Kind.ATTENTION, block.semantics().get(1).kind, "HTML块b");
        require(block.semantics().get(0).start == 0 && block.semantics().get(0).end == 2
                        && block.semantics().get(1).start == 1 && block.semantics().get(1).end == 2,
                "HTML块语义范围");
        TextContent.Resolved escaped = checked("&lt;b&gt;x&lt;/b&gt;", false).blocks().get(0).paragraph.resolve(BASE);
        require(escaped.semantics().isEmpty(), "转义文字不能重解释HTML语义");
        require(TextContent.plain("<b>x</b>").resolve(BASE).semantics().isEmpty()
                        && TextContent.richTags("<b>x</b>").resolve(BASE).semantics().isEmpty(),
                "PLAIN与DSL入口不隐式获得HTML含义");
        ParagraphContent legacy = new ParagraphContent(
                new StyledText(TextInput.string("old"), null, Collections.singletonList(
                        new club.heiqi.uilib.ui.scene.text.TextStyleOverrideSpan(0, 3,
                                club.heiqi.uilib.ui.scene.text.TextStyleOverride.INHERIT)),
                        club.heiqi.uilib.ui.scene.text.TextStyleOverride.INHERIT,
                        Collections.<TextLayout.Diagnostic>emptyList()),
                Collections.<TextContent.LinkSpan>emptyList(), Collections.<InlineContent>emptyList());
        require(legacy.semantics().isEmpty(), "旧共同段落构造器必须兼容");
    }

    private void htmlSemantics() {
        String inline =
                "lead <strong>S</strong><b>B</b><em>E</em><i>I</i><u>U</u><s>X</s><del>D</del><br>line"
                        + " <code>*literal* $x$</code> <em>*x*</em>"
                        + " <foo>*kept*</foo><script>*hidden*</script><style>hidden-css</style> tail";
        for (boolean bytes : new boolean[] {false, true}) {
            DocumentContent doc = checked(inline, bytes);
            TextContent.Resolved r = doc.blocks().get(0).paragraph.resolve(BASE);
            equal(
                    "lead SBEIUXD\nline *literal* $x$ x kept tail",
                    r.text.input.text,
                    "inline HTML正文与成熟Markdown边界");
            for (String bold : Arrays.asList("S", "B"))
                equal(
                        700,
                        r.text.styleAt(r.text.input.text.indexOf(bold)).fontStyle.weight,
                        "strong/b内容语义");
            for (String italic : Arrays.asList("E", "I", "x", "kept"))
                equal(
                        FontStyle.Posture.ITALIC,
                        r.text.styleAt(r.text.input.text.lastIndexOf(italic)).fontStyle.posture,
                        "em/i与Markdown共同语义");
            require(r.text.styleAt(r.text.input.text.indexOf('U')).effects.underline, "u丢失下划线语义");
            require(
                    r.text.styleAt(r.text.input.text.indexOf('X')).effects.strike
                            && r.text.styleAt(r.text.input.text.indexOf('D')).effects.strike,
                    "s/del丢失删除语义");
            equal(
                    MathTextFontSources.MONO,
                    r.text.styleAt(r.text.input.text.indexOf("*literal*")).font,
                    "HTML code字体");
            require(r.inlineContents().isEmpty(), "HTML code内美元被解释为公式");
        }
        String blocks =
                "<section><h1>one</h1><h2>two</h2><h3>three</h3><h4>four</h4><h5>five</h5><h6>six</h6><p>*block*<br>line</p><hr><blockquote><p>quote</p><ol"
                        + " start='7'><li>outer<ul><li>inner</li></ul></li></ol></blockquote><pre><code>"
                        + "  *literal* $x$ &lt;b&gt;\n"
                        + "\tend</code></pre><table><thead><tr><th align='left'>H</th><th"
                        + " align='center'>J</th><th"
                        + " align='right'>K</th></tr></thead><tbody><tr><td>*cell*</td><td><b>bold</b></td><td>body</td></tr></tbody><tfoot><tr><td>foot</td><td></td><td>end</td></tr></tfoot></table></section>\n\n"
                        + "tail";
        for (boolean bytes : new boolean[] {false, true}) {
            DocumentContent doc = checked(blocks, bytes);
            List<DocumentContent.Block> headings =
                    find(doc.blocks(), DocumentContent.Block.Kind.HEADING);
            equal(6, headings.size(), "h1至h6全部转换");
            for (int i = 0; i < 6; i++) equal(i + 1, headings.get(i).headingLevel, "标题层级");
            equal(1, find(doc.blocks(), DocumentContent.Block.Kind.RULE).size(), "hr结构");
            equal(1, find(doc.blocks(), DocumentContent.Block.Kind.QUOTE).size(), "blockquote结构");
            List<DocumentContent.Block> lists = find(doc.blocks(), DocumentContent.Block.Kind.LIST);
            equal(2, lists.size(), "嵌套HTML列表结构");
            require(
                    lists.get(0).ordered && lists.get(0).orderedStart == 7 && !lists.get(1).ordered,
                    "ol start或嵌套ul语义");
            DocumentContent.Block code = find(doc.blocks(), DocumentContent.Block.Kind.CODE).get(0);
            equal("  *literal* $x$ <b>\n\tend", text(code), "pre/code字面空白与实体");
            require(code.paragraph.inlineContents().isEmpty(), "HTML pre再次解析Markdown或公式");
            TableContent table = table(doc);
            equal(3, table.rows().size(), "thead tbody tfoot共同表格行");
            equal(Arrays.asList("*cell*", "bold", "body"), rowText(table, 1), "HTML block表格不重解析星号");
            equal(
                    TableContent.Alignment.DEFAULT,
                    table.rows().get(0).cells().get(0).alignment,
                    "未列入支持属性的HTML align采用默认对齐");
            equal(
                    TableContent.Alignment.DEFAULT,
                    table.rows().get(0).cells().get(1).alignment,
                    "未列入支持属性的HTML align采用默认对齐");
            equal(
                    TableContent.Alignment.DEFAULT,
                    table.rows().get(0).cells().get(2).alignment,
                    "未列入支持属性的HTML align采用默认对齐");
            int align = blocks.indexOf("align");
            boolean ignoredAlignment = false;
            for (TextLayout.Diagnostic diagnostic : doc.diagnostics())
                if (diagnostic.sourceStart <= offset(blocks, align, bytes)
                        && diagnostic.sourceEnd >= offset(blocks, align + "align".length(), bytes))
                    ignoredAlignment = true;
            require(ignoredAlignment, "HTML未支持align属性须关联原属性诊断");
            equal(
                    700,
                    paragraph(table.rows().get(1).cells().get(1))
                            .resolve(BASE)
                            .text
                            .styleAt(0)
                            .fontStyle
                            .weight,
                    "HTML表格中b语义");
            require(
                    allText(doc).contains("*block*\nline") && allText(doc).endsWith("tail"),
                    "HTML block段落/后文存活");
        }
    }

    private void htmlSources() {
        for (String bad : Arrays.asList(String.valueOf((char) 0), "&#0;", "&#xD800;")) {
            String source = "<p>a" + bad + "b</p>\n\ntail";
            for (boolean bytes : new boolean[] {false, true}) {
                DocumentContent doc = checked(source, bytes);
                TextInput input = doc.blocks().get(0).paragraph.resolve(BASE).text.input;
                equal("a\ufffdb", input.text, "HTML非法Unicode替代且保留两侧正文");
                exactRange(
                        input,
                        1,
                        source,
                        source.indexOf(bad),
                        source.indexOf(bad) + bad.length(),
                        bytes);
                boolean diagnosed = false;
                for (TextLayout.Diagnostic diagnostic : doc.diagnostics())
                    if (diagnostic.sourceStart == offset(source, source.indexOf(bad), bytes)
                            && diagnostic.sourceEnd
                                    == offset(source, source.indexOf(bad) + bad.length(), bytes))
                        diagnosed = true;
                require(diagnosed, "非法HTML字符/实体替代须有原范围诊断");
                require(allText(doc).endsWith("tail"), "非法HTML字符不得截断后文");
            }
        }
        // 原串重读会把quote容器前缀、table split已经删除的反斜杠重新带回code正文。
        for (boolean bytes : new boolean[] {false, true}) {
            String quote = "> before <code>a\n> b</code>";
            DocumentContent quoted = checked(quote, bytes);
            DocumentContent.Block paragraph =
                    find(quoted.blocks(), DocumentContent.Block.Kind.PARAGRAPH).get(0);
            equal("before a\nb", text(paragraph), "HTML code不能恢复quote容器前缀");
            TextInput quotedInput = paragraph.paragraph.resolve(BASE).text.input;
            int b = quote.indexOf("b</code>");
            exactRange(quotedInput, quotedInput.text.lastIndexOf('b'), quote, b, b + 1, bytes);
            String pipe = "head|next\n---|---\n<code>a\\|b</code>|tail";
            DocumentContent piped = checked(pipe, bytes);
            TextInput code =
                    paragraph(table(piped).rows().get(1).cells().get(0)).resolve(BASE).text.input;
            equal("a|b", code.text, "HTML code不能恢复成熟table已解除的转义反斜杠");
            mapped(code, "|", pipe, "\\|", bytes);
        }
        String source =
                "<p>中😀 A&amp;B &#x1F600; <a href='../next?a=1&amp;b=2' title='go'>link</a><img"
                        + " src='asset.png?a=1&amp;b=2' alt='A&amp;B' title='pic' width='37'"
                        + " height='19'><img src='other.png' width='bad' height='-2'"
                        + " data-ignored='v'><unknown>readable</unknown><script>hidden-js</script><style>hidden-css</style></p>\r\n\r\n"
                        + "tail";
        for (boolean bytes : new boolean[] {false, true}) {
            DocumentContent doc = checked(source, bytes);
            TextContent.Resolved r = doc.blocks().get(0).paragraph.resolve(BASE);
            require(
                    r.text.input.text.contains("中😀 A&B 😀 link")
                            && r.text.input.text.contains("readable"),
                    "HTML实体/未知容器正文");
            require(
                    !allText(doc).contains("hidden") && allText(doc).endsWith("tail"),
                    "非正文抑制或后文丢失");
            mapped(r.text.input, "&", source, "&amp;", bytes);
            int emoji = r.text.input.text.lastIndexOf("😀"), original = source.indexOf("&#x1F600;");
            exactRange(
                    r.text.input, emoji, source, original, original + "&#x1F600;".length(), bytes);
            exactRange(
                    r.text.input,
                    emoji + 1,
                    source,
                    original,
                    original + "&#x1F600;".length(),
                    bytes);
            equal("../next?a=1&b=2", r.links().get(0).link.target, "HTML a href实体及相对来源");
            equal(2, r.inlineContents().size(), "HTML图片统一typed对象");
            InlineContent valid = r.inlineContents().get(0), invalid = r.inlineContents().get(1);
            equal("asset.png?a=1&b=2", valid.source, "img src实体");
            equal("A&B", valid.alt, "img alt实体");
            equal(Integer.valueOf(37), valid.width, "有效logical px宽度");
            equal(Integer.valueOf(19), valid.height, "有效logical px高度");
            require(invalid.width == null && invalid.height == null, "无效尺寸进入布局");
            for (String value : Arrays.asList("bad", "-2")) {
                int start = source.indexOf(value), end = start + value.length();
                boolean located = false;
                for (TextLayout.Diagnostic diagnostic : doc.diagnostics())
                    if (diagnostic.sourceStart == offset(source, start, bytes)
                            && diagnostic.sourceEnd == offset(source, end, bytes)) located = true;
                require(located, "无效尺寸诊断必须定位原属性value=" + value);
            }
            String literal = "<em $bad>read</em>\n\ntail";
            DocumentContent malformed = checked(literal, bytes);
            require(
                    allText(malformed).contains("<em $bad>read")
                            && allText(malformed).endsWith("tail"),
                    "Markdown已判定的字面坏标签不能被宽松HTML吞掉");
            DocumentContent escaped =
                    checked("\\<b>literal</b>\n\n~~~html\n<b>*code*</b>\n~~~", bytes);
            require(
                    allText(escaped).contains("<b>literal")
                            && allText(escaped).contains("<b>*code*</b>"),
                    "转义与围栏HTML应为字面");
        }
    }

    private void htmlCodeRanges() {
        for (boolean tableCell : new boolean[] {false, true}) {
            StringBuilder raw = new StringBuilder(tableCell ? "head|next\r\n---|---\r\n" : "> head ");
            StringBuilder expected = new StringBuilder(tableCell ? "" : "head ");
            List<Integer> original = new ArrayList<>(), logical = new ArrayList<>();
            for (int i = 0; i < 128; i++) {
                if (i % 16 == 0) raw.append("<code></code>");
                raw.append("<code>");
                original.add(raw.length());
                logical.add(expected.length());
                raw.append(tableCell ? "x\\|😀" : "x😀").append("</code>");
                expected.append(tableCell ? "x|😀" : "x😀");
            }
            raw.append(tableCell ? "|tail" : " tail");
            if (!tableCell) expected.append(" tail");
            String source = raw.toString();
            for (boolean bytes : new boolean[] {false, true}) {
                DocumentContent doc = checked(source, bytes);
                TextInput input = tableCell
                        ? paragraph(table(doc).rows().get(1).cells().get(0)).resolve(BASE).text.input
                        : find(doc.blocks(), DocumentContent.Block.Kind.PARAGRAPH).get(0)
                                .paragraph.resolve(BASE).text.input;
                equal(expected.toString(), input.text, "密集code内容、空片段与后文保持");
                for (int i = 0; i < original.size(); i++) {
                    int start = original.get(i), at = logical.get(i);
                    exactRange(input, at, source, start, start + 1, bytes);
                    if (tableCell) exactRange(input, at + 1, source, start + 1, start + 3, bytes);
                }
            }
        }
    }

    private void tableScene() throws Exception {
        String source =
                "wide heading words|wide heading words|wide heading words\n:---|:---:|---:\n"
                        + "[go](next) $x$|[go](next) $x$|[go](next) $x$\n"
                        + "many words that wrap in a narrow window|short|short\n\ntail";
        try (Fixture f = new Fixture(source, 1000)) {
            f.page.frame();
            SceneNode[] table = new SceneNode[1], cells = new SceneNode[9];
            int[] oldHeight = new int[1];
            f.domain.execute(
                    () -> {
                        SceneGeometryView g = f.geometry();
                        table[0] = f.root[0].children().get(0);
                        equal(9, table[0].children().size(), "共同表格扁平cell子节点");
                        for (int i = 0; i < cells.length; i++)
                            cells[i] = table[0].children().get(i);
                        grid(g, f.root[0], table[0], 3);
                        TextLayout header = g.get(textNode(cells[0], g)).text;
                        for (TextLayout.Line line : header.lines())
                            for (TextLayout.Cluster cluster : line.clusters())
                                equal(700, cluster.style.fontStyle.weight, "表头主题粗体");
                        SceneNode left = textNode(cells[3], g),
                                center = textNode(cells[4], g),
                                right = textNode(cells[5], g);
                        projection(
                                g.get(left).text,
                                g.get(center).text,
                                g.get(center).box.width,
                                0.5f);
                        projection(g.get(left).text, g.get(right).text, g.get(right).box.width, 1f);
                        inlinePlacement(left, g);
                        inlinePlacement(center, g);
                        inlinePlacement(right, g);
                        oldHeight[0] = g.get(cells[6]).box.height;
                        f.theme[0].set(
                                SceneTheme.light()
                                        .withTextStyle(new TextStyle("default", 31, 0xff112233)));
                    });
            f.width[0] = 210;
            f.page.frame();
            f.domain.execute(
                    () -> {
                        SceneGeometryView g = f.geometry();
                        require(
                                f.root[0].children().get(0) == table[0]
                                        && table[0].children().get(6) == cells[6],
                                "窗口/主题变化重建表格内容声明");
                        grid(g, f.root[0], table[0], 3);
                        require(g.get(cells[6]).box.height > oldHeight[0], "窄窗口和字号变化未更新rowHeight");
                        require(g.get(textNode(cells[6], g)).text.lines().size() > 1, "可拆文本未自动换行");
                        SceneNode body = textNode(cells[3], g);
                        equal(31f, g.get(body).text.style.logicalEm, "字号变化未进单元格布局");
                        inlinePlacement(body, g);
                    });
        }
    }

    private void overflowScene() throws Exception {
        String formula = String.join("+", Collections.nCopies(40, "x"));
        String source = "head|second\n---|---\n[go](next) $" + formula + "$|short\n\ntail";
        try (Fixture f = new Fixture(source, 220)) {
            f.page.frame();
            SceneNode[] table = new SceneNode[1], first = new SceneNode[1], link = new SceneNode[1];
            MountHandle[] mount = new MountHandle[1];
            int[] point = new int[2], wheel = new int[2], initial = new int[1];
            f.domain.execute(
                    () -> {
                        SceneGeometryView g = f.geometry();
                        table[0] = f.root[0].children().get(0);
                        first[0] = table[0].children().get(0);
                        link[0] = textNode(table[0].children().get(2), g);
                        mount[0] = f.page.runtime().captureMount(table[0]);
                        grid(g, f.root[0], table[0], 2);
                        SceneNode atomic = link[0].children().get(0);
                        require(
                                g.get(atomic).box.width > g.get(table[0]).box.width,
                                "输入必须命中真实超宽公式");
                        inlinePlacement(link[0], g);
                        require(
                                g.get(atomic).clip != null
                                        && g.get(atomic).clip.width <= g.get(table[0]).box.width,
                                "公式未进入表格共同裁剪");
                        initial[0] = g.get(first[0]).box.x;
                        LayoutBox box = g.get(table[0]).box;
                        wheel[0] = box.x + box.width / 2;
                        wheel[1] = box.y + box.height / 2;
                        targetPoint(link[0], g, point);
                    });
            // 使用该页面实际声明，而不是另一次parse的同值链接作为输入身份。
            long[] measureBefore = new long[1];
            f.domain.execute(() -> measureBefore[0] = f.page.runtime().measureRevision());
            f.page.pointer(ScenePointerEvent.down(0, point[0], point[1]));
            f.page.pointer(ScenePointerEvent.scroll(wheel[0], wheel[1], Integer.MIN_VALUE));
            f.page.frame();
            f.domain.execute(
                    () ->
                            equal(
                                    measureBefore[0],
                                    f.page.runtime().measureRevision(),
                                    "纯表格横滚不改变文字和内容测量代际"));
            f.domain.execute(
                    () -> {
                        SceneGeometryView g = f.geometry();
                        require(g.get(first[0]).box.x < initial[0], "横滚未移动正式cell几何");
                        grid(g, f.root[0], table[0], 2);
                        SceneNode last = table[0].children().get(3);
                        LayoutBox view = g.get(table[0]).box, end = g.get(last).box;
                        equal(view.x + view.width, end.x + end.width, "最大横滚对齐共同视口右边界");
                        LayoutBox body = g.get(link[0]).box;
                        require(body.x < view.x, "最大横滚未将原链接移出可见范围");
                    });
            f.page.pointer(ScenePointerEvent.up(0, point[0], point[1]));
            f.page.pointer(ScenePointerEvent.syntheticActivation(-1, point[1]));
            f.page.frame();
            require(f.clicks.isEmpty(), "滚动后捕获尾事件或裁剪外激活了旧链接");
            f.page.pointer(ScenePointerEvent.scroll(wheel[0], wheel[1], Integer.MAX_VALUE));
            f.page.frame();
            f.domain.execute(() -> targetPoint(link[0], f.geometry(), point));
            f.page.pointer(ScenePointerEvent.syntheticActivation(point[0], point[1]));
            f.page.frame();
            equal(Collections.singletonList("next"), f.clicks, "同代正式router激活链接");
            f.domain.execute(
                    () -> {
                        SceneLayoutEngine engine = new SceneLayoutEngine(f.fonts);
                        engine.layout(
                                Collections.singletonList(f.root[0]),
                                f.width[0],
                                900,
                                f.fonts.generation(),
                                f.page.runtime().measureRevision());
                        for (int width : new int[] {220, 130, 220}) {
                            SceneGeometryView capture =
                                    engine.captureSubtree(
                                            f.root[0],
                                            new LayoutBox(0, 0, width, 900),
                                            null,
                                            new LayoutBox(0, 0, width, 900));
                            grid(capture, f.root[0], table[0], 2);
                            inlinePlacement(link[0], capture);
                        }
                    });
            f.page.pointer(ScenePointerEvent.down(0, point[0], point[1]));
            f.domain.execute(() -> f.content[0].set(MarkdownDocument.parse("replacement tail")));
            f.page.frame();
            f.page.pointer(ScenePointerEvent.up(0, point[0], point[1]));
            f.page.frame();
            f.domain.execute(
                    () -> {
                        require(
                                !mount[0].active()
                                        && table[0].runtime() == null
                                        && link[0].runtime() == null,
                                "源替换未释放表格/公式及输入挂载");
                        require(textNode(f.root[0], f.geometry()) != null, "替换后的正文不可读");
                    });
            equal(Collections.singletonList("next"), f.clicks, "源替换后旧capture尾事件激活新声明");
        }
    }

    private void centeredFixedImage() throws Exception {
        try (SharedImageService images = new SharedImageService(
                source -> () -> ImageServiceScenario.pixels(16, 16), SharedImageService.Budget.defaults())) {
            DocumentResources resources = new DocumentResources(images, () -> "", Collections.emptyMap(), null);
            try (Fixture f = new Fixture("| <img src=\"resource:test:ore\" width=\"16\" height=\"16\"/> |\n| :---: |", 17, resources)) {
                f.domain.execute(() -> {
                    SceneTheme theme = SceneTheme.dark().withTextStyle(BASE);
                    f.theme[0].set(new SceneTheme(theme.textStyle, theme.panel, theme.button, theme.hover,
                            theme.pressed, theme.focus, theme.disabled, theme.disabledText,
                            theme.tooltip, theme.tooltipText, 0, 0));
                });
                f.page.frame();
                // 首帧挂载声明，后续drain派发worker并发布完成；IMAGE观测必须消费ready资源。
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (images.retainedBytes() == 0 && System.nanoTime() < deadline) {
                    f.page.frame();
                    Thread.yield();
                }
                require(images.retainedBytes() > 0, "固定图像未完成正式资源发布");
                f.page.frame();
                f.domain.execute(() -> {
                    SceneGeometryView geometry = f.geometry();
                    SceneNode table = f.root[0].children().get(0);
                    SceneNode paragraph = textNode(table.children().get(0), geometry);
                    SceneNode image = paragraph.children().get(0);
                    equal(17, geometry.get(paragraph).box.width, "反例段落可用宽度");
                    require(image.width().mode == Size.Mode.FIXED && image.width().value == 16
                                    && image.height().mode == Size.Mode.FIXED && image.height().value == 16,
                            "正式图像绑定没有保留硬尺寸");
                    TextLayout.Cluster cluster = geometry.get(paragraph).text.lines().get(0).clusters().get(0);
                    require(Math.abs(cluster.x - 0.5f) < 0.001f, "反例未触达半像素居中位置");
                    equal(16, geometry.get(image).box.width, "居中位置不应放大图像固定宽度");
                    equal(16, geometry.get(image).box.height, "基线小数不应放大图像固定高度");
                    PaintCommand command = null;
                    for (PaintCommand item : f.paint.commands())
                        if (item.kind == PaintCommand.Kind.IMAGE) command = item;
                    LayoutBox box = geometry.get(image).box;
                    require(command != null, "完成发布的固定图像没有IMAGE命令");
                    require(command.box.x == box.x && command.box.y == box.y
                                    && command.box.width == box.width && command.box.height == box.height,
                            "图像绘制没有消费同树固定几何");
                });
            }
        }
    }

    private void imageScene() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        ImageContent pixels = ImageServiceScenario.pixels(280, 140);
        try (SharedImageService images =
                new SharedImageService(
                        source ->
                                () -> {
                                    started.countDown();
                                    require(release.await(2, TimeUnit.SECONDS), "图像worker没有收到完成触发");
                                    return pixels;
                                },
                        SharedImageService.Budget.defaults())) {
            DocumentResources resources =
                    new DocumentResources(images, () -> "", Collections.emptyMap(), null);
            try (Fixture f =
                    new Fixture(
                            "head|next\n---|---\n![loaded](resource:test:table)|short\n\ntail",
                            1000,
                            resources)) {
                try {
                    f.page.frame();
                    // 首帧prepareResources先于挂载；下一宿主帧才派发首帧新声明的pending图像。
                    f.page.frame();
                    require(started.await(1, TimeUnit.SECONDS), "表格图片没有进入共同图像worker");
                    SceneNode[] table = new SceneNode[1],
                            cell = new SceneNode[1],
                            atom = new SceneNode[1];
                    int[] before = new int[1];
                    f.domain.execute(
                            () -> {
                                table[0] = f.root[0].children().get(0);
                                cell[0] = table[0].children().get(2);
                                atom[0] = textNode(cell[0], f.geometry()).children().get(0);
                                before[0] = f.geometry().get(cell[0]).box.height;
                            });
                    release.countDown();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (images.retainedBytes() == 0 && System.nanoTime() < deadline) {
                        f.page.frame();
                        Thread.yield();
                    }
                    require(images.retainedBytes() == pixels.bytes(), "共享服务未发布实际图像完成");
                    f.page.frame();
                    f.domain.execute(
                            () -> {
                                SceneGeometryView g = f.geometry();
                                require(
                                        table[0].children().get(2) == cell[0]
                                                && atom[0].runtime() != null,
                                        "图像完成重建或释放了原声明");
                                require(
                                        g.get(cell[0]).box.height > before[0],
                                        "图像完成未自动更新cell rowHeight");
                                equal(pixels.width, g.get(atom[0]).box.width, "图像正式自然宽度");
                                equal(pixels.height, g.get(atom[0]).box.height, "图像正式自然高度");
                                grid(g, f.root[0], table[0], 2);
                                inlinePlacement(textNode(cell[0], g), g);
                                f.content[0].set(MarkdownDocument.parse("replacement tail"));
                            });
                    f.page.frame();
                    require(atom[0].runtime() == null, "源替换未释放表格图像挂载");
                    // 正式资源缓存可保留READY内容；reload无消费者分支证明lease确已释放。
                    images.reload(ImageSource.resource("test:table"));
                    require(
                            images.entryCount() == 0 && images.retainedBytes() == 0,
                            "源替换未释放表格图像共享lease");
                } finally {
                    release.countDown();
                }
            }
        }
    }

    /** 比较同内容的左/中/右列，偏移按当前正式可用宽度计算，字形、簇、装饰全部同代平移。 */
    private static void projection(TextLayout left, TextLayout aligned, int width, float factor) {
        equal(left.logicalText, aligned.logicalText, "对齐不能改变正文");
        equal(left.lines().size(), aligned.lines().size(), "同宽列换行一致");
        for (int i = 0; i < left.lines().size(); i++) {
            TextLayout.Line a = left.lines().get(i), b = aligned.lines().get(i);
            float shift = Math.max(0, width - a.advance) * factor;
            require(shift > 0, "输入没有留下可观测对齐空间");
            equal(a.clusters().size(), b.clusters().size(), "对齐簇数");
            equal(a.glyphs().size(), b.glyphs().size(), "对齐glyph数");
            equal(a.decorations().size(), b.decorations().size(), "对齐装饰数");
            for (int j = 0; j < a.clusters().size(); j++)
                near(a.clusters().get(j).x + shift, b.clusters().get(j).x, "cluster对齐投影");
            for (int j = 0; j < a.glyphs().size(); j++)
                near(a.glyphs().get(j).x + shift, b.glyphs().get(j).x, "glyph对齐投影");
            for (int j = 0; j < a.decorations().size(); j++)
                near(a.decorations().get(j).x + shift, b.decorations().get(j).x, "decoration对齐投影");
        }
    }

    private static void grid(SceneGeometryView g, SceneNode root, SceneNode table, int columns) {
        LayoutBox view = g.get(table).box;
        require(
                g.get(root).box.width <= g.viewport().width && view.width <= g.get(root).box.width,
                "表格内容撑宽整篇文档");
        for (int row = 0; row < table.children().size() / columns; row++) {
            LayoutBox first = g.get(table.children().get(row * columns)).box;
            int required = 0;
            for (int col = 0; col < columns; col++) {
                SceneNode cell = table.children().get(row * columns + col);
                LayoutBox box = g.get(cell).box;
                equal(first.y, box.y, "同一row共同y");
                equal(first.height, box.height, "rowHeight取所有cell最大高度");
                int content = cell.padding() * 2;
                for (SceneNode child : cell.children()) content += g.get(child).box.height;
                content += Math.max(0, cell.children().size() - 1) * cell.gap();
                required = Math.max(required, content);
                if (row > 0) equal(g.get(table.children().get(col)).box.width, box.width, "列宽全行一致");
                SceneGeometryView.Entry entry = g.get(cell);
                require(
                        entry.clip != null
                                && entry.clip.x >= view.x
                                && entry.clip.x + entry.clip.width <= view.x + view.width,
                        "cell共同视口裁剪范围");
            }
            equal(required, first.height, "行高不得采用最短cell或重复padding");
        }
    }

    private static void inlinePlacement(SceneNode paragraph, SceneGeometryView g) {
        SceneGeometryView.Entry entry = g.get(paragraph);
        for (SceneNode child : paragraph.children()) {
            LayoutBox box = g.get(child).box;
            boolean matched = false;
            for (TextLayout.Line line : entry.text.lines())
                for (TextLayout.Cluster c : line.clusters())
                    if (entry.text.logicalText.charAt(c.logicalStart) == '￼') {
                        int expected = entry.box.x + paragraph.padding() + (int) Math.floor(c.x);
                        if (expected == box.x) matched = true;
                    }
            require(matched, "原子子节点未跟随同代cluster对齐/滚动");
        }
    }

    private static void targetPoint(SceneNode node, SceneGeometryView g, int[] point) {
        SceneGeometryView.Entry entry = g.get(node);
        for (TextLayout.Line line : entry.text.lines())
            for (TextLayout.Cluster c : line.clusters()) {
                if (c.advance <= 0) continue;
                int x = entry.box.x + node.padding() + (int) (c.x + c.advance / 2);
                int y = entry.box.y + node.padding() + (int) (line.y + line.lineHeight / 2);
                if (node.control().pointerTarget(node, g, x, y) instanceof TextContent.Link) {
                    point[0] = x;
                    point[1] = y;
                    return;
                }
            }
        throw new AssertionError("正式几何未找到链接命中");
    }

    private static SceneNode textNode(SceneNode node, SceneGeometryView g) {
        SceneGeometryView.Entry entry = g.get(node);
        if (entry != null && entry.text != null && !entry.text.logicalText.isEmpty()) return node;
        for (SceneNode child : node.children()) {
            SceneNode found = textNode(child, g);
            if (found != null) return found;
        }
        return null;
    }

    private static final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final int[] width;
        final FontTextService fonts = new FontTextService(new LwjglFontEngine());
        final SceneNode[] root = new SceneNode[1];
        final List<String> clicks = new ArrayList<>();
        final Signal<DocumentContent>[] content;
        final Signal<SceneTheme>[] theme;
        final ScenePageSession page;
        PaintPlan paint;

        @SuppressWarnings("unchecked")
        Fixture(String source, int width) throws Exception {
            this(source, width, null);
        }

        @SuppressWarnings("unchecked")
        Fixture(String source, int width, DocumentResources resources) throws Exception {
            this.width = new int[] {width};
            content = new Signal[1];
            theme = new Signal[1];
            SceneHost host =
                    new SceneHost() {
                        public int width() {
                            return Fixture.this.width[0];
                        }

                        public int height() {
                            return 900;
                        }

                        public long metricsRevision() {
                            return fonts.generation();
                        }

                        public void prepareResources() {
                            if (resources != null && resources.images instanceof SharedImageService)
                                ((SharedImageService) resources.images).drain();
                        }

                        public void replay(PaintPlan plan) { paint = plan; }
                    };
            page =
                    ScenePageSession.open(
                            domain,
                            host,
                            fonts,
                            ctx -> {
                                content[0] = domain.signal(checked(source, false));
                                theme[0] = domain.signal(SceneTheme.dark().withTextStyle(BASE));
                                root[0] =
                                        SceneDocument.create(
                                                        ctx,
                                                        content[0],
                                                        theme[0],
                                                        resources,
                                                        clicks::add)
                                                .size(Size.fill(1), Size.natural());
                                ctx.mount(root[0]);
                            });
        }

        SceneGeometryView geometry() {
            return page.pipeline().current().geometry;
        }

        public void close() {
            page.close();
            fonts.close();
        }
    }

    private static DocumentContent checked(String source, boolean bytes) {
        TextInput original =
                bytes
                        ? TextInput.utf8(source.getBytes(StandardCharsets.UTF_8))
                        : TextInput.string(source);
        DocumentContent doc = MarkdownDocument.parse(original);
        require(doc.input() == original, "入口originalInput声明应保留");
        equal(source, doc.input().text, "源文不得吞字或重写");
        int length = offset(source, source.length(), bytes);
        for (DocumentContent.Block block : doc.blocks())
            sourceBlock(block, original.sourceUnit, length);
        for (TextLayout.Diagnostic d : doc.diagnostics())
            range(d.sourceStart, d.sourceEnd, length, "diagnostic");
        return doc;
    }

    private static void sourceBlock(
            DocumentContent.Block block, TextInput.SourceUnit unit, int length) {
        range(block.sourceStart, block.sourceEnd, length, "block");
        if (block.paragraph != null) {
            TextInput input = block.paragraph.resolve(BASE).text.input;
            equal(unit, input.sourceUnit, "段落来源单位");
            int consumed = 0;
            for (TextInput.SourceSegment segment : input.sourceSegments()) {
                equal(consumed, segment.sourceStart, "段落必须完整覆盖整篇原文，不仅保留cell局部");
                range(segment.sourceStart, segment.sourceEnd, length, "segment");
                require(
                        segment.logicalStart >= 0
                                && segment.logicalEnd >= segment.logicalStart
                                && segment.logicalEnd <= input.text.length(),
                        "来源逻辑范围");
                consumed = segment.sourceEnd;
            }
            equal(length, consumed, "整篇原文source覆盖");
            for (int i = 0; i < input.text.length(); i++) {
                range(input.sourceStart(i), input.sourceEnd(i), length, "character");
                require(input.sourceEnd(i) > input.sourceStart(i), "可见字符不能伪造零宽来源");
            }
            for (InlineContent atom : block.paragraph.inlineContents())
                range(atom.sourceStart, atom.sourceEnd, length, "atomic");
        }
        for (DocumentContent.Block child : block.children()) sourceBlock(child, unit, length);
        if (block.table != null)
            for (TableContent.Row row : block.table.rows()) {
                range(row.sourceStart, row.sourceEnd, length, "row");
                for (TableContent.Cell cell : row.cells()) {
                    range(cell.sourceStart, cell.sourceEnd, length, "cell");
                    for (DocumentContent.Block child : cell.content())
                        sourceBlock(child, unit, length);
                }
            }
    }

    private static void range(int start, int end, int length, String label) {
        require(start >= 0 && end >= start && end <= length, label + "来源越界=" + start + ":" + end);
    }

    private static int offset(String source, int index, boolean bytes) {
        return bytes ? source.substring(0, index).getBytes(StandardCharsets.UTF_8).length : index;
    }

    private static void exactRange(
            TextInput input, int logical, String source, int start, int end, boolean bytes) {
        equal(offset(source, start, bytes), input.sourceStart(logical), "字符原始start");
        equal(offset(source, end, bytes), input.sourceEnd(logical), "字符原始end");
    }

    private static void mapped(
            TextInput input, String visible, String source, String raw, boolean bytes) {
        int logical = input.text.indexOf(visible), start = source.indexOf(raw);
        require(logical >= 0 && start >= 0, "场景输入缺少手写来源目标");
        exactRange(input, logical, source, start, start + raw.length(), bytes);
    }

    private static void atomRange(InlineContent atom, String source, String raw, boolean bytes) {
        int start = source.indexOf(raw);
        equal(offset(source, start, bytes), atom.sourceStart, "typed原子原始start");
        equal(offset(source, start + raw.length(), bytes), atom.sourceEnd, "typed原子原始end");
    }

    private static TextContent paragraph(TableContent.Cell cell) {
        require(
                cell.content().size() == 1 && cell.content().get(0).paragraph != null,
                "场景要求单段cell");
        return cell.content().get(0).paragraph;
    }

    private static String text(DocumentContent.Block block) {
        return block.paragraph == null ? "" : block.paragraph.resolve(BASE).text.input.text;
    }

    private static List<String> rowText(TableContent table, int row) {
        List<String> result = new ArrayList<>();
        for (TableContent.Cell cell : table.rows().get(row).cells())
            result.add(textOf(cell.content()));
        return result;
    }

    private static String allText(DocumentContent doc) {
        return textOf(doc.blocks());
    }

    private static String textOf(List<DocumentContent.Block> blocks) {
        StringBuilder result = new StringBuilder();
        for (DocumentContent.Block block : blocks) {
            result.append(text(block)).append(textOf(block.children()));
            if (block.table != null)
                for (TableContent.Row row : block.table.rows())
                    for (TableContent.Cell cell : row.cells())
                        result.append(textOf(cell.content()));
        }
        return result.toString();
    }

    private static TableContent table(DocumentContent doc) {
        List<DocumentContent.Block> tables = find(doc.blocks(), DocumentContent.Block.Kind.TABLE);
        equal(1, tables.size(), "场景应有一个正式TABLE块");
        return tables.get(0).table;
    }

    private static List<DocumentContent.Block> find(
            List<DocumentContent.Block> blocks, DocumentContent.Block.Kind kind) {
        List<DocumentContent.Block> result = new ArrayList<>();
        for (DocumentContent.Block block : blocks) {
            if (block.kind == kind) result.add(block);
            result.addAll(find(block.children(), kind));
            if (block.table != null)
                for (TableContent.Row row : block.table.rows())
                    for (TableContent.Cell cell : row.cells())
                        result.addAll(find(cell.content(), kind));
        }
        return result;
    }

    private static void near(float expected, float actual, String label) {
        require(
                Math.abs(expected - actual) < 0.02f,
                label + " expected=" + expected + " actual=" + actual);
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual))
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
    }
}
