package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.InlineContent;
import club.heiqi.uilib.ui.scene.text.MarkdownDocument;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.MathTextFontSources;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 固定成熟解析器的阅读语义对照；期望只消费 oracle token，不调用生产 parser。 字符投影比较阅读结果而非 AST/属性片段的切分方式；真实字体场景另验声明身份与输入。
 * HTML/表格是后续切片，本应用只要求当前保留源文并提供来源诊断。
 */
public final class MarkdownReadingScenario extends TestApplication {
    private static final TextStyle BASE = new TextStyle("default", 22, 0xffabcdef);
    private List<Map<String, Object>> cases;

    public static void main(String[] args) throws Exception {
        new MarkdownReadingScenario().run(args);
    }

    @Override
    protected void setup() throws Exception {
        try (InputStream stream =
                getClass().getResourceAsStream("/markdown/reference/reading-corpus.json")) {
            require(stream != null, "classpath 缺少独立 Markdown oracle");
            Map<String, Object> corpus =
                    map(new Yaml().load(new InputStreamReader(stream, StandardCharsets.UTF_8)));
            Map<String, Object> reference = map(corpus.get("reference"));
            equal("14.2.0", reference.get("markdownIt"), "oracle Markdown 版本");
            equal("1.1.2", reference.get("mathPlugin"), "oracle 数学插件版本");
            cases = maps(corpus.get("cases"));
            require(!cases.isEmpty(), "oracle 语料为空");
        }
    }

    @Override
    protected void runTests() throws Exception {
        List<Throwable> failures = new ArrayList<>();
        runScenario("独立token的块结构显示文字typed样式链接与原子语义", this::oracle, failures);
        runScenario("多码点实体CRLF代理对的UTF16及BYTE原位置", this::sourceExamples, failures);
        runScenario("后续HTML表格保源定位及未闭合公式正文边界", this::boundaries, failures);
        runScenario("合法quote列表容器公式空行Tab精确来源不吞外部正文", this::containerMath, failures);
        runScenario("真实字体scene主题宽度变化保持链接身份并取消旧声明", this::scene, failures);
        runScenario("CODE独立滚动capture动态范围释放与嵌套列表正文", this::blockScene, failures);
        if (!failures.isEmpty()) {
            AssertionError result = new AssertionError("Markdown阅读场景失败=" + failures.size());
            for (Throwable failure : failures) result.addSuppressed(failure);
            throw result;
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

    private void oracle() {
        List<String> failures = new ArrayList<>();
        int compared = 0;
        for (Map<String, Object> item : cases) {
            String id = str(item, "id"), source = str(item, "source");
            // 正式规格明确接受此参照差异，另用正文存活场景验证，不能拿插件吞正文当期望。
            if (id.equals("unclosed-display")) continue;
            compared++;
            try {
                List<Map<String, Object>> tokens = maps(item.get("tokens"));
                List<Object> expected = oracleBlocks(tokens);
                DocumentContent utf = MarkdownDocument.parse(source);
                equal(source, utf.input().text, id + " 原文读取");
                equal(expected, document(utf), id + " UTF16 阅读语义");
                require(utf.diagnostics().isEmpty(), id + " 基础支持语法有诊断: " + utf.diagnostics());
                verifySources(utf, source.length(), TextInput.SourceUnit.UTF16);
                DocumentContent bytes =
                        MarkdownDocument.parse(
                                TextInput.utf8(source.getBytes(StandardCharsets.UTF_8)));
                equal(expected, document(bytes), id + " BYTE 阅读语义");
                verifySources(
                        bytes,
                        source.getBytes(StandardCharsets.UTF_8).length,
                        TextInput.SourceUnit.BYTE);
                compareSources(utf.blocks(), bytes.blocks(), source);
                System.out.println("PASS oracle " + id + " UTF16/BYTE");
            } catch (AssertionError | RuntimeException failure) {
                failures.add(id + ": " + failure);
            }
        }
        require(compared > 0, "没有比对独立语料");
        require(failures.isEmpty(), "独立oracle差异:\n" + String.join("\n", failures));
    }

    /** 将成熟 token 投影为块的开闭事件；列表 tight 来自直属段落的 hidden 事实。 */
    private static List<Object> oracleBlocks(List<Map<String, Object>> tokens) {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Map<String, Object> token = tokens.get(i);
            String type = str(token, "type"), content = str(token, "content");
            switch (type) {
                case "paragraph_open":
                    out.add("PARAGRAPH");
                    break;
                case "heading_open":
                    out.add(Arrays.asList("HEADING", str(token, "tag")));
                    break;
                case "blockquote_open":
                    out.add("QUOTE");
                    break;
                case "list_item_open":
                    out.add("LIST_ITEM");
                    break;
                case "ordered_list_open":
                case "bullet_list_open":
                    String start = attr(token, "start");
                    out.add(
                            Arrays.asList(
                                    "LIST",
                                    type.equals("ordered_list_open"),
                                    start == null ? 1 : Integer.parseInt(start),
                                    tight(tokens, i)));
                    break;
                case "paragraph_close":
                case "heading_close":
                case "blockquote_close":
                case "list_item_close":
                case "ordered_list_close":
                case "bullet_list_close":
                    out.add("END");
                    break;
                case "inline":
                    out.add(oracleInline(maps(token.get("children"))));
                    break;
                case "hr":
                    out.add("RULE");
                    out.add("END");
                    break;
                case "fence":
                case "code_block":
                    String info = str(token, "info").trim();
                    if (type.equals("fence") && info.equalsIgnoreCase("math")) {
                        out.add(Arrays.asList("MATH", content.trim(), "DISPLAY"));
                    } else {
                        out.add(Arrays.asList("CODE", info));
                        out.add(literal(content));
                    }
                    out.add("END");
                    break;
                case "math_block":
                    out.add(Arrays.asList("MATH", content.trim(), "DISPLAY"));
                    out.add("END");
                    break;
                default:
                    throw new AssertionError("oracle 出现本切片未声明 token: " + type);
            }
        }
        return out;
    }

    private static boolean tight(List<Map<String, Object>> tokens, int opening) {
        int depth = 0;
        for (int i = opening + 1; i < tokens.size(); i++) {
            Map<String, Object> token = tokens.get(i);
            String type = str(token, "type");
            if (type.equals("ordered_list_open") || type.equals("bullet_list_open")) depth++;
            if (type.equals("ordered_list_close") || type.equals("bullet_list_close")) {
                if (depth == 0) return true;
                depth--;
            }
            if (depth == 0
                    && type.equals("paragraph_open")
                    && !Boolean.TRUE.equals(token.get("hidden"))) return false;
        }
        throw new AssertionError("oracle 列表未闭合");
    }

    private static List<Object> oracleInline(List<Map<String, Object>> tokens) {
        List<Object> out = new ArrayList<>();
        int bold = 0, italic = 0, strike = 0;
        String target = null;
        for (Map<String, Object> token : tokens) {
            String type = str(token, "type"), content = str(token, "content");
            switch (type) {
                case "strong_open":
                    bold++;
                    continue;
                case "strong_close":
                    bold--;
                    continue;
                case "em_open":
                    italic++;
                    continue;
                case "em_close":
                    italic--;
                    continue;
                case "s_open":
                    strike++;
                    continue;
                case "s_close":
                    strike--;
                    continue;
                case "link_open":
                    target = attr(token, "href");
                    continue;
                case "link_close":
                    target = null;
                    continue;
                case "softbreak":
                    content = " ";
                    break;
                case "hardbreak":
                    content = "\n";
                    break;
                case "text":
                case "code_inline":
                    break;
                case "image":
                    append(
                            out,
                            "￼",
                            bold > 0,
                            italic > 0,
                            strike > 0,
                            target,
                            Arrays.asList("IMAGE", attr(token, "src"), imageAlt(token)));
                    continue;
                case "math_inline":
                case "math_block":
                    append(
                            out,
                            "￼",
                            bold > 0,
                            italic > 0,
                            strike > 0,
                            target,
                            Arrays.asList(
                                    "MATH",
                                    renderedMath(token),
                                    type.equals("math_block") ? "DISPLAY" : "TEXT"));
                    continue;
                default:
                    throw new AssertionError("oracle 未覆盖行内 token: " + type);
            }
            append(
                    out,
                    content,
                    bold > 0,
                    italic > 0,
                    strike > 0,
                    target,
                    null,
                    type.equals("code_inline"));
        }
        require(bold == 0 && italic == 0 && strike == 0 && target == null, "oracle 行内作用域未闭合");
        return out;
    }

    /** 固定math 1.1.2 inline renderer去掉成对首尾backtick，display renderer保留。 */
    private static String renderedMath(Map<String, Object> token) {
        String content = str(token, "content");
        if (str(token, "type").equals("math_inline")
                && content.length() > 2
                && content.charAt(0) == '`'
                && content.charAt(content.length() - 1) == '`')
            content = content.substring(1, content.length() - 1);
        return content.trim();
    }

    private static String imageAlt(Map<String, Object> token) {
        StringBuilder alt = new StringBuilder();
        for (Map<String, Object> child : maps(token.get("children"))) {
            String type = str(child, "type");
            if (type.equals("text") || type.equals("code_inline"))
                alt.append(str(child, "content"));
            else if (type.equals("softbreak") || type.equals("hardbreak")) alt.append('\n');
        }
        return alt.toString();
    }

    private static List<Object> literal(String text) {
        List<Object> out = new ArrayList<>();
        append(out, text, false, false, false, null, null, true);
        return out;
    }

    private static void append(
            List<Object> out,
            String text,
            boolean bold,
            boolean italic,
            boolean strike,
            String target,
            Object inline) {
        append(out, text, bold, italic, strike, target, inline, false);
    }

    private static void append(
            List<Object> out,
            String text,
            boolean bold,
            boolean italic,
            boolean strike,
            String target,
            Object inline,
            boolean mono) {
        for (int i = 0; i < text.length(); ) {
            int next = i + Character.charCount(text.codePointAt(i));
            out.add(
                    Arrays.asList(
                            text.substring(i, next),
                            bold,
                            italic,
                            strike,
                            uri(target),
                            inline,
                            mono));
            i = next;
        }
    }

    private static List<Object> document(DocumentContent doc) {
        List<Object> out = new ArrayList<>();
        for (DocumentContent.Block block : doc.blocks()) project(block, out);
        return out;
    }

    private static void project(DocumentContent.Block block, List<Object> out) {
        switch (block.kind) {
            case HEADING:
                out.add(Arrays.asList("HEADING", "h" + block.headingLevel));
                break;
            case LIST:
                out.add(Arrays.asList("LIST", block.ordered, block.orderedStart, block.tight));
                break;
            case CODE:
                out.add(Arrays.asList("CODE", block.codeInfo.trim()));
                break;
            case MATH:
                out.add(Arrays.asList("MATH", block.math.source.trim(), block.style.name()));
                break;
            default:
                out.add(block.kind.name());
        }
        if (block.paragraph != null) {
            TextContent.Resolved r = block.paragraph.resolve(BASE);
            List<Object> reading = new ArrayList<>();
            for (int i = 0; i < r.text.input.text.length(); ) {
                String text = r.text.input.text;
                int next = i + Character.charCount(text.codePointAt(i));
                TextStyle style = r.text.styleAt(i);
                TextContent.Link link = r.linkAt(i);
                Object atomic = null;
                for (InlineContent inline : r.inlineContents())
                    if (inline.offset == i) {
                        atomic =
                                inline.kind == InlineContent.Kind.IMAGE
                                        ? Arrays.asList("IMAGE", inline.source, inline.alt)
                                        : Arrays.asList(
                                                "MATH",
                                                inline.math.source.trim(),
                                                inline.mathStyle.name());
                    }
                append(
                        reading,
                        text.substring(i, next),
                        style.effects.bold,
                        style.effects.italic,
                        style.effects.strike,
                        link == null ? null : link.target,
                        atomic,
                        MathTextFontSources.MONO.equals(style.font));
                i = next;
            }
            out.add(reading);
            verifyStable(block.paragraph, r);
        }
        for (DocumentContent.Block child : block.children()) project(child, out);
        out.add("END");
    }

    private static void verifyStable(TextContent content, TextContent.Resolved original) {
        TextStyle changed = new TextStyle("changed-family", 35, 0xff123456);
        TextContent.Resolved after = content.resolve(changed);
        require(original.text.input == after.text.input, "主题变化重建来源声明");
        equal(original.links().size(), after.links().size(), "主题变化链接数");
        for (int i = 0; i < original.links().size(); i++)
            require(original.links().get(i).link == after.links().get(i).link, "主题变化重建链接身份");
        for (int i = 0; i < original.inlineContents().size(); i++)
            require(
                    original.inlineContents().get(i) == after.inlineContents().get(i),
                    "主题变化重建原子身份");
        for (int i = 0; i < after.text.input.text.length(); i++) {
            TextStyle style = after.text.styleAt(i);
            String expectedFont =
                    MathTextFontSources.MONO.equals(original.text.styleAt(i).font)
                            ? MathTextFontSources.MONO
                            : changed.font;
            require(
                    style.font.equals(expectedFont)
                            && style.color == changed.color
                            && style.logicalEm == changed.logicalEm,
                    "typed 样式冻结基础主题或丢失代码字体");
        }
    }

    private static void verifySources(DocumentContent doc, int length, TextInput.SourceUnit unit) {
        require(doc.input().sourceUnit == unit, "文档来源单位丢失");
        for (DocumentContent.Block block : doc.blocks()) verifyBlock(block, length, unit);
    }

    private static void verifyBlock(
            DocumentContent.Block block, int length, TextInput.SourceUnit unit) {
        require(
                block.sourceStart >= 0
                        && block.sourceEnd >= block.sourceStart
                        && block.sourceEnd <= length,
                "块来源越界");
        if (block.paragraph != null) {
            TextContent.Resolved r = block.paragraph.resolve(BASE);
            TextInput input = r.text.input;
            require(input.sourceUnit == unit, "段落来源单位丢失");
            int consumed = 0;
            for (TextInput.SourceSegment s : input.sourceSegments()) {
                require(
                        s.sourceStart == consumed && s.sourceEnd >= s.sourceStart,
                        "每个段落必须完整覆盖整篇原文，不能只保存局部来源");
                require(s.logicalStart >= 0 && s.logicalEnd <= input.text.length(), "来源片段逻辑越界");
                consumed = s.sourceEnd;
            }
            equal(length, consumed, "段落完整原文来源覆盖");
            for (int i = 0; i < input.text.length(); i++) {
                require(
                        input.sourceStart(i) >= block.sourceStart
                                && input.sourceEnd(i) <= block.sourceEnd
                                && input.sourceEnd(i) > input.sourceStart(i),
                        "逻辑字符来源未落在所属块 kind="
                                + block.kind
                                + " logical="
                                + i
                                + " range="
                                + input.sourceStart(i)
                                + ":"
                                + input.sourceEnd(i)
                                + " block="
                                + block.sourceStart
                                + ":"
                                + block.sourceEnd);
            }
            for (InlineContent inline : r.inlineContents()) {
                equal(input.sourceStart(inline.offset), inline.sourceStart, "对象来源起点");
                equal(input.sourceEnd(inline.offset), inline.sourceEnd, "对象来源终点");
            }
        }
        for (DocumentContent.Block child : block.children()) {
            require(
                    child.sourceStart >= block.sourceStart && child.sourceEnd <= block.sourceEnd,
                    "子块来源超出容器");
            verifyBlock(child, length, unit);
        }
    }

    private static void compareSources(
            List<DocumentContent.Block> utf, List<DocumentContent.Block> bytes, String source) {
        equal(utf.size(), bytes.size(), "来源单位改变块数");
        for (int b = 0; b < utf.size(); b++) {
            DocumentContent.Block a = utf.get(b), z = bytes.get(b);
            equal(byteOffset(source, a.sourceStart), z.sourceStart, "块BYTE起点");
            equal(byteOffset(source, a.sourceEnd), z.sourceEnd, "块BYTE终点");
            if (a.paragraph != null) {
                TextInput u = a.paragraph.resolve(BASE).text.input,
                        v = z.paragraph.resolve(BASE).text.input;
                equal(u.text, v.text, "来源单位改变正文");
                for (int i = 0; i < u.text.length(); i++) {
                    equal(byteOffset(source, u.sourceStart(i)), v.sourceStart(i), "字符BYTE起点");
                    equal(byteOffset(source, u.sourceEnd(i)), v.sourceEnd(i), "字符BYTE终点");
                }
            }
            compareSources(a.children(), z.children(), source);
        }
    }

    private void sourceExamples() {
        String source = "# 标题😀\r\n\r\n甲 &NotEqualTilde; &amp; 😀\r\n乙  \r\n丙\\*\r\n";
        for (boolean bytes : new boolean[] {false, true}) {
            DocumentContent doc = parse(source, bytes);
            equal(source, doc.input().text, "原始CRLF保留");
            TextInput heading = doc.blocks().get(0).paragraph.resolve(BASE).text.input;
            equal("标题😀", heading.text, "标题阅读文字");
            locate(heading, source, "标题", "标题", bytes);
            locate(heading, source, "😀", "😀", bytes);
            TextInput paragraph = doc.blocks().get(1).paragraph.resolve(BASE).text.input;
            equal("甲 ≂̸ & 😀 乙\n丙*", paragraph.text, "实体与软硬换行阅读文字");
            locate(paragraph, source, "≂̸", "&NotEqualTilde;", bytes);
            locate(paragraph, source, "&", "&amp;", bytes);
            int emojiSource = source.indexOf("😀", source.indexOf("甲"));
            range(
                    paragraph,
                    paragraph.text.indexOf("😀"),
                    2,
                    emojiSource,
                    emojiSource + 2,
                    source,
                    bytes);
            int soft = source.indexOf("\r\n乙");
            range(paragraph, paragraph.text.indexOf(" 乙"), 1, soft, soft + 2, source, bytes);
            locate(paragraph, source, "*", "\\*", bytes);
            verifySources(
                    doc,
                    bytes ? source.getBytes(StandardCharsets.UTF_8).length : source.length(),
                    bytes ? TextInput.SourceUnit.BYTE : TextInput.SourceUnit.UTF16);
        }
    }

    private static void locate(
            TextInput input, String source, String logical, String original, boolean bytes) {
        int l = input.text.indexOf(logical), s = source.indexOf(original);
        require(l >= 0 && s >= 0, "位置反例未命中");
        // 多码点实体共享完整源范围；普通多字符文本逐码点映射。
        if (original.startsWith("&")
                || original.startsWith("\\")
                || logical.codePointCount(0, logical.length()) == 1)
            range(input, l, logical.length(), s, s + original.length(), source, bytes);
        else
            for (int i = 0; i < logical.length(); i++)
                range(input, l + i, 1, s + i, s + i + 1, source, bytes);
    }

    private static void range(
            TextInput input,
            int logical,
            int count,
            int start,
            int end,
            String source,
            boolean bytes) {
        for (int i = logical; i < logical + count; i++) {
            equal(
                    bytes ? byteOffset(source, start) : start,
                    input.sourceStart(i),
                    "独立原文起点 logical=" + i);
            equal(bytes ? byteOffset(source, end) : end, input.sourceEnd(i), "独立原文终点 logical=" + i);
        }
    }

    private static int byteOffset(String source, int utf16) {
        require(utf16 >= 0 && utf16 <= source.length(), "来源UTF16越界");
        require(
                utf16 == source.length() || !Character.isLowSurrogate(source.charAt(utf16)),
                "来源切开代理对");
        return source.substring(0, utf16).getBytes(StandardCharsets.UTF_8).length;
    }

    private void boundaries() {
        for (String source :
                Arrays.asList("<section>保留正文</section>\n\n后续正文", "| 列 |\n| --- |\n| 值 |\n\n后续正文")) {
            for (boolean bytes : new boolean[] {false, true}) {
                DocumentContent doc = parse(source, bytes);
                equal(source, doc.input().text, "后续切片原始文档可读");
                StringBuilder readable = new StringBuilder();
                collectText(doc.blocks(), readable);
                for (String line : source.split("\n"))
                    require(
                            readable.toString().contains(line),
                            "未支持内容静默丢弃 line=" + line + " actual=" + readable);
                require(
                        !doc.diagnostics().isEmpty(),
                        "后续切片缺少定位诊断 bytes="
                                + bytes
                                + " source="
                                + source
                                + " readable="
                                + readable);
                int length =
                        bytes ? source.getBytes(StandardCharsets.UTF_8).length : source.length();
                for (TextLayout.Diagnostic d : doc.diagnostics())
                    require(
                            d.sourceStart >= 0
                                    && d.sourceEnd > d.sourceStart
                                    && d.sourceEnd <= length
                                    && d.sourceStart
                                            < (bytes
                                                    ? byteOffset(source, source.indexOf("\n\n"))
                                                    : source.indexOf("\n\n")),
                            "诊断没有关联未支持的原始内容");
                verifySources(
                        doc,
                        length,
                        bytes ? TextInput.SourceUnit.BYTE : TextInput.SourceUnit.UTF16);
            }
        }
        String unclosed = "$$\nx\n\n正文\n# next";
        DocumentContent doc = MarkdownDocument.parse(unclosed);
        StringBuilder readable = new StringBuilder();
        collectText(doc.blocks(), readable);
        require(
                readable.toString().contains("$$") && readable.toString().contains("正文"),
                "未闭合$$吞后续正文");
        require(
                doc.blocks().stream().noneMatch(b -> b.kind == DocumentContent.Block.Kind.MATH),
                "未配对$$变为公式块");
        require(
                doc.blocks().stream()
                        .anyMatch(
                                b ->
                                        b.kind == DocumentContent.Block.Kind.HEADING
                                                && b.paragraph
                                                        .resolve(BASE)
                                                        .text
                                                        .input
                                                        .text
                                                        .equals("next")),
                "未闭合$$吞后续标题");
    }

    private void containerMath() {
        containerUnclosed();
        containerAfterUnclosed();
        String outside = "容器外正文😀";
        String[] inputs = {
            "> $$\n>x\n>$$", "> $$\n> x\n>\n> y\n> $$", "- $$\n\tx\n\t$$", "- $$\n\tx\n\n\ty\n\t$$"
        };
        for (int index = 0; index < inputs.length; index++) {
            String source = inputs[index] + "\n\n" + outside;
            // 列表marker占两列；TAB到第四列，空行后的正文保留成熟容器剩余的两空格。
            String expectedFormula = index % 2 == 0 ? "x" : index == 3 ? "x\n\n  y" : "x\n\ny";
            for (boolean bytes : new boolean[] {false, true}) {
                DocumentContent doc = parse(source, bytes);
                equal(source, doc.input().text, "容器公式整篇原文保留");
                equal(2, doc.blocks().size(), "公式闭合后外部正文被错误吞入容器");
                DocumentContent.Block container = doc.blocks().get(0);
                equal(
                        index < 2
                                ? DocumentContent.Block.Kind.QUOTE
                                : DocumentContent.Block.Kind.LIST,
                        container.kind,
                        "公式未保留合法容器");
                List<DocumentContent.Block> nested = container.children();
                if (container.kind == DocumentContent.Block.Kind.LIST) {
                    equal(1, nested.size(), "列表公式拆成多个item");
                    equal(DocumentContent.Block.Kind.LIST_ITEM, nested.get(0).kind, "公式列表item丢失");
                    nested = nested.get(0).children();
                }
                equal(1, nested.size(), "完整$$被拆成空公式/孤立正文");
                DocumentContent.Block math = nested.get(0);
                equal(DocumentContent.Block.Kind.MATH, math.kind, "合法容器$$未形成块公式");
                // 固定插件在TAB容器中会吞x；正式合同保正文和完整pair，明确接受此差异。
                equal(expectedFormula, math.math.source.trim(), "容器公式正文或空行丢失");
                equal("DISPLAY", math.style.name(), "容器块公式显示样式");
                int open = source.indexOf("$$"), end = source.lastIndexOf("$$") + 2;
                equal(bytes ? byteOffset(source, open) : open, math.sourceStart, "公式开定界原位置");
                equal(bytes ? byteOffset(source, end) : end, math.sourceEnd, "公式闭定界原位置");
                DocumentContent.Block paragraph = doc.blocks().get(1);
                equal(DocumentContent.Block.Kind.PARAGRAPH, paragraph.kind, "公式吞容器外正文");
                TextInput text = paragraph.paragraph.resolve(BASE).text.input;
                equal(outside, text.text, "公式之后正文丢失");
                locate(text, source, "😀", "😀", bytes);
                verifySources(
                        doc,
                        bytes ? source.getBytes(StandardCharsets.UTF_8).length : source.length(),
                        bytes ? TextInput.SourceUnit.BYTE : TextInput.SourceUnit.UTF16);
            }
        }
    }

    private void containerAfterUnclosed() {
        String prefix = "> $$\n>未闭合正文\n\n";
        for (boolean quote : new boolean[] {false, true}) {
            String source = prefix + (quote ? "> $$y$$" : "$$x$$");
            for (boolean bytes : new boolean[] {false, true}) {
                DocumentContent doc = parse(source, bytes);
                equal(2, doc.blocks().size(), "未闭合引用遮蔽或吞入后续合法公式");
                equal(DocumentContent.Block.Kind.QUOTE, doc.blocks().get(0).kind, "前置引用容器丢失");
                require(!hasMath(doc.blocks().get(0).children()), "未闭合引用错误配对后续容器");
                StringBuilder readable = new StringBuilder();
                collectText(doc.blocks().get(0).children(), readable);
                require(
                        readable.toString().contains("$$") && readable.toString().contains("未闭合正文"),
                        "拒绝未闭合公式丢弃原文");
                DocumentContent.Block later = doc.blocks().get(1);
                if (quote) {
                    equal(DocumentContent.Block.Kind.QUOTE, later.kind, "后续公式独立引用容器丢失");
                    equal(1, later.children().size(), "后续引用合法公式拆块");
                    later = later.children().get(0);
                }
                equal(DocumentContent.Block.Kind.MATH, later.kind, "拒绝前置公式后没有识别后续合法pair");
                equal(quote ? "y" : "x", later.math.source.trim(), "后续公式正文错误");
                int start = source.lastIndexOf("$$" + (quote ? "y" : "x"));
                equal(bytes ? byteOffset(source, start) : start, later.sourceStart, "后续合法公式开定界位置");
                equal(
                        bytes ? source.getBytes(StandardCharsets.UTF_8).length : source.length(),
                        later.sourceEnd,
                        "后续合法公式闭定界位置");
                verifySources(
                        doc,
                        bytes ? source.getBytes(StandardCharsets.UTF_8).length : source.length(),
                        bytes ? TextInput.SourceUnit.BYTE : TextInput.SourceUnit.UTF16);
            }
        }
    }

    private void containerUnclosed() {
        String outside = "容器外正文😀";
        for (String prefix : Arrays.asList("> $$\n>引用正文", "- $$\n\t列表正文")) {
            String source = prefix + "\n\n$$\n\n" + outside;
            for (boolean bytes : new boolean[] {false, true}) {
                DocumentContent doc = parse(source, bytes);
                require(!hasMath(doc.blocks()), "容器外$$错误闭合容器内开定界");
                StringBuilder readable = new StringBuilder();
                collectText(doc.blocks(), readable);
                require(
                        readable.toString().contains(prefix.startsWith(">") ? "引用正文" : "列表正文")
                                && readable.toString().contains(outside)
                                && readable.toString().contains("$$"),
                        "不完整容器配对吞原文或外部正文");
                require(doc.blocks().get(doc.blocks().size() - 1).paragraph != null, "容器外正文被吞入其他块");
                equal(
                        outside,
                        doc.blocks()
                                .get(doc.blocks().size() - 1)
                                .paragraph
                                .resolve(BASE)
                                .text
                                .input
                                .text,
                        "未闭合公式必须保留独立外部段落");
                verifySources(
                        doc,
                        bytes ? source.getBytes(StandardCharsets.UTF_8).length : source.length(),
                        bytes ? TextInput.SourceUnit.BYTE : TextInput.SourceUnit.UTF16);
            }
        }
        String source = "> $$\n>引用正文\n\n- $$\n  列表正文\n\n" + outside;
        for (boolean bytes : new boolean[] {false, true}) {
            DocumentContent doc = parse(source, bytes);
            require(!hasMath(doc.blocks()), "不同容器中的$$错误形成完整配对");
            StringBuilder readable = new StringBuilder();
            collectText(doc.blocks(), readable);
            require(
                    readable.toString().contains("引用正文")
                            && readable.toString().contains("列表正文")
                            && readable.toString().contains(outside),
                    "跨容器误配对吞正文");
        }
    }

    private static boolean hasMath(List<DocumentContent.Block> blocks) {
        for (DocumentContent.Block block : blocks)
            if (block.kind == DocumentContent.Block.Kind.MATH || hasMath(block.children()))
                return true;
        return false;
    }

    private static void collectText(List<DocumentContent.Block> blocks, StringBuilder out) {
        for (DocumentContent.Block block : blocks) {
            if (block.paragraph != null)
                out.append(block.paragraph.resolve(BASE).text.input.text).append('\n');
            collectText(block.children(), out);
        }
    }

    private static DocumentContent parse(String source, boolean bytes) {
        return bytes
                ? MarkdownDocument.parse(TextInput.utf8(source.getBytes(StandardCharsets.UTF_8)))
                : MarkdownDocument.parse(source);
    }

    private void scene() throws Exception {
        String source = "[**稳定链接文字重复以便窄窗口换行稳定链接文字**](../next)";
        DocumentContent declaration = MarkdownDocument.parse(source);
        TextContent text = declaration.blocks().get(0).paragraph;
        TextContent.Link identity = text.resolve(BASE).links().get(0).link;
        ReactiveDomain domain = ReactiveDomain.direct();
        int[] width = {460};
        SceneNode[] root = new SceneNode[1];
        @SuppressWarnings("unchecked")
        Signal<SceneTheme>[] theme = new Signal[1];
        @SuppressWarnings("unchecked")
        Signal<DocumentContent>[] content = new Signal[1];
        List<String> clicks = new ArrayList<>();
        try (FontTextService fonts = new FontTextService(new LwjglFontEngine())) {
            SceneHost host =
                    new SceneHost() {
                        public int width() {
                            return width[0];
                        }

                        public int height() {
                            return 600;
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
                                content[0] = domain.signal(declaration);
                                theme[0] = domain.signal(SceneTheme.dark().withTextStyle(BASE));
                                root[0] =
                                        SceneDocument.create(
                                                        ctx,
                                                        content[0],
                                                        theme[0],
                                                        null,
                                                        clicks::add)
                                                .size(Size.fill(1), Size.natural());
                                ctx.mount(root[0]);
                            })) {
                page.frame();
                SceneNode[] paragraph = new SceneNode[1];
                int[] point = new int[2];
                domain.execute(
                        () -> {
                            paragraph[0] = textNode(root[0], page.pipeline().current().geometry);
                            targetPoint(
                                    paragraph[0],
                                    page.pipeline().current().geometry,
                                    identity,
                                    point);
                            equal(
                                    text.resolve(BASE).text.input.text,
                                    page.pipeline()
                                            .current()
                                            .geometry
                                            .get(paragraph[0])
                                            .text
                                            .logicalText,
                                    "原始数据不足以阅读");
                            theme[0].set(
                                    SceneTheme.light()
                                            .withTextStyle(
                                                    new TextStyle("default", 32, 0xff112233)));
                        });
                width[0] = 150;
                page.frame();
                domain.execute(
                        () -> {
                            require(
                                    textNode(root[0], page.pipeline().current().geometry)
                                            == paragraph[0],
                                    "主题重建段落节点");
                            require(
                                    page.pipeline()
                                                    .current()
                                                    .geometry
                                                    .get(paragraph[0])
                                                    .text
                                                    .lines()
                                                    .size()
                                            > 1,
                                    "窄宽度未重排");
                            targetPoint(
                                    paragraph[0],
                                    page.pipeline().current().geometry,
                                    identity,
                                    point);
                        });
                page.pointer(ScenePointerEvent.syntheticActivation(point[0], point[1]));
                page.frame();
                page.pointer(ScenePointerEvent.down(0, point[0], point[1]));
                page.frame();
                domain.execute(
                        () -> {
                            equal(Arrays.asList("../next"), clicks, "Markdown正式链接激活");
                            content[0].set(MarkdownDocument.parse(source));
                        });
                page.frame();
                domain.execute(
                        () -> {
                            SceneNode replacement =
                                    textNode(root[0], page.pipeline().current().geometry);
                            require(
                                    !replacement
                                            .control()
                                            .pointerTargetValid(replacement, identity),
                                    "替换内容复活旧声明资格");
                            TextContent.Link fresh =
                                    content[0]
                                            .get()
                                            .blocks()
                                            .get(0)
                                            .paragraph
                                            .resolve(BASE)
                                            .links()
                                            .get(0)
                                            .link;
                            require(fresh != identity, "相同源码的新声明复用旧链接身份");
                            targetPoint(
                                    replacement, page.pipeline().current().geometry, fresh, point);
                        });
                page.pointer(ScenePointerEvent.up(0, point[0], point[1]));
                page.frame();
                domain.execute(() -> equal(Arrays.asList("../next"), clicks, "旧声明按下不能在新声明释放激活"));
                page.pointer(ScenePointerEvent.syntheticActivation(point[0], point[1]));
                page.frame();
                domain.execute(
                        () -> equal(Arrays.asList("../next", "../next"), clicks, "新声明不能被旧手势永久取消"));
            }
        }
    }

    private void blockScene() throws Exception {
        String line =
                "  $x$ https://example.test <b> **code** "
                        + String.join("", Collections.nCopies(12, "abcdefgh  "));
        String source = "~~~java\n" + line + "\nsecond\tline\n~~~\n\n3. 外层正文\n   - 内层正文\n\n末尾正文";
        ReactiveDomain domain = ReactiveDomain.direct();
        int[] width = {240};
        SceneNode[] root = new SceneNode[1], code = new SceneNode[1], viewport = new SceneNode[1];
        MountHandle[] mount = new MountHandle[1];
        @SuppressWarnings("unchecked")
        Signal<DocumentContent>[] content = new Signal[1];
        @SuppressWarnings("unchecked")
        Signal<SceneTheme>[] theme = new Signal[1];
        int[] wheel = new int[2];
        try (FontTextService fonts = new FontTextService(new LwjglFontEngine())) {
            SceneHost host =
                    new SceneHost() {
                        public int width() {
                            return width[0];
                        }

                        public int height() {
                            return 900;
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
                                content[0] = domain.signal(MarkdownDocument.parse(source));
                                theme[0] = domain.signal(SceneTheme.dark().withTextStyle(BASE));
                                root[0] =
                                        SceneDocument.create(
                                                        ctx,
                                                        content[0],
                                                        theme[0],
                                                        null,
                                                        target -> {
                                                            throw new AssertionError(
                                                                    "代码符号错误成为链接动作");
                                                        })
                                                .size(Size.fill(1), Size.natural());
                                ctx.mount(root[0]);
                            })) {
                page.frame();
                int[] origin = new int[1];
                domain.execute(
                        () -> {
                            SceneGeometryView g = page.pipeline().current().geometry;
                            code[0] = findText(root[0], g, line + "\nsecond\tline\n");
                            require(code[0] != null, "代码空白/原始行被改写");
                            viewport[0] = code[0].parent();
                            mount[0] = page.runtime().captureMount(viewport[0]);
                            verifyCode(g, viewport[0], code[0]);
                            origin[0] = g.get(code[0]).box.x;
                            LayoutBox box = g.get(viewport[0]).box;
                            wheel[0] = box.x + box.width / 2;
                            wheel[1] = box.y + Math.max(1, viewport[0].padding());
                            verifyList(root[0], g, BASE.logicalEm);
                        });
                page.pointer(ScenePointerEvent.scroll(wheel[0], wheel[1], -2));
                page.frame();
                domain.execute(
                        () -> {
                            SceneGeometryView g = page.pipeline().current().geometry;
                            require(g.get(code[0]).box.x < origin[0], "代码横向滚轮未移动正式子几何");
                            verifyCode(g, viewport[0], code[0]);
                            // 真正复用公开capture：宽→窄→宽，不读取control私有offset或另造几何。
                            SceneLayoutEngine engine = new SceneLayoutEngine(fonts);
                            engine.layout(
                                    Collections.singletonList(root[0]),
                                    width[0],
                                    900,
                                    fonts.generation(),
                                    page.runtime().measureRevision());
                            for (int available : new int[] {width[0], 120, width[0]}) {
                                SceneGeometryView capture =
                                        engine.captureSubtree(
                                                root[0],
                                                new LayoutBox(0, 0, available, 900),
                                                null,
                                                new LayoutBox(0, 0, available, 900));
                                verifyCode(capture, viewport[0], code[0]);
                                verifyList(root[0], capture, BASE.logicalEm);
                            }
                            theme[0].set(
                                    SceneTheme.light()
                                            .withTextStyle(
                                                    new TextStyle("default", 31, 0xff223344)));
                        });
                width[0] = 180;
                page.frame();
                domain.execute(
                        () -> {
                            SceneGeometryView g = page.pipeline().current().geometry;
                            require(
                                    findText(root[0], g, line + "\nsecond\tline\n") == code[0],
                                    "主题改变重建代码声明节点");
                            verifyCode(g, viewport[0], code[0]);
                            verifyList(root[0], g, 31);
                        });
                page.pointer(ScenePointerEvent.scroll(wheel[0], wheel[1], Integer.MIN_VALUE));
                page.frame();
                domain.execute(
                        () -> {
                            SceneGeometryView g = page.pipeline().current().geometry;
                            LayoutBox view = g.get(viewport[0]).box, body = g.get(code[0]).box;
                            equal(
                                    view.x + view.width - viewport[0].padding(),
                                    body.x + body.width,
                                    "代码最大横向滚动未按右padding边界对齐");
                        });
                // 变宽后范围归零，不能保持已经失效的旧横向偏移。
                width[0] = 10000;
                page.frame();
                domain.execute(
                        () -> {
                            SceneGeometryView g = page.pipeline().current().geometry;
                            equal(
                                    g.get(viewport[0]).box.x + viewport[0].padding(),
                                    g.get(code[0]).box.x,
                                    "宽视口没有将代码横向范围归零");
                            content[0].set(MarkdownDocument.parse("新的正文"));
                        });
                page.frame();
                domain.execute(
                        () -> {
                            require(
                                    !mount[0].active()
                                            && viewport[0].runtime() == null
                                            && code[0].runtime() == null,
                                    "替换文档未释放代码视口/子段落挂载");
                            require(
                                    findText(root[0], page.pipeline().current().geometry, "新的正文")
                                            != null,
                                    "替换后的正文不可读");
                        });
                page.pointer(ScenePointerEvent.scroll(wheel[0], wheel[1], -1));
                page.frame();
            }
            domain.execute(() -> require(!mount[0].active(), "页面关闭后旧挂载复活"));
        }
    }

    private static void verifyCode(SceneGeometryView g, SceneNode viewport, SceneNode code) {
        SceneGeometryView.Entry view = g.get(viewport), body = g.get(code);
        require(view != null && body != null && body.text != null, "代码capture缺少共同文字几何");
        require(body.text.lines().size() <= 3, "代码长行被窄视口折行");
        equal(body.box.height + viewport.padding() * 2, view.box.height, "代码视口重复计入padding高度");
        for (TextLayout.Line line : body.text.lines())
            for (TextLayout.Cluster cluster : line.clusters())
                equal(MathTextFontSources.MONO, cluster.style.font, "代码未使用正式等宽字体名称");
        require(
                view.box.width > 0 && body.box.width >= view.box.width - viewport.padding() * 2,
                "代码测量撑开文档或遗漏内容宽度");
        require(
                body.clip != null
                        && body.clip.x >= view.box.x
                        && body.clip.x + body.clip.width <= view.box.x + view.box.width,
                "代码子几何未遵守独立视口裁剪");
        require(
                body.box.x <= view.box.x + viewport.padding()
                        && body.box.x + body.box.width
                                >= view.box.x + view.box.width - viewport.padding(),
                "代码横向偏移超出本次测量范围");
    }

    private static void verifyList(SceneNode root, SceneGeometryView g, float em) {
        SceneNode outer = findText(root, g, "外层正文"),
                inner = findText(root, g, "内层正文"),
                tail = findText(root, g, "末尾正文");
        require(outer != null && inner != null && tail != null, "嵌套列表丢失正文");
        SceneGeometryView.Entry a = g.get(outer), b = g.get(inner), c = g.get(tail);
        require(b.box.x > a.box.x, "嵌套列表未用同树几何表达层级");
        require(a.box.width > 0 && b.box.width > 0, "列表标记挤掉正文宽度");
        require(a.text.style.logicalEm == em && b.text.style.logicalEm == em, "列表缩小正文代替结构布局");
        require(
                a.box.y + a.box.height <= b.box.y && b.box.y + b.box.height <= c.box.y,
                "列表层级或末尾正文几何交叠");
        require(
                findText(root, g, "3.") != null && findText(root, g, "•") != null,
                "列表独立起始编号/无序标记未呈现");
    }

    private static SceneNode findText(SceneNode node, SceneGeometryView geometry, String text) {
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry != null && entry.text != null && text.equals(entry.text.logicalText)) return node;
        for (SceneNode child : node.children()) {
            SceneNode found = findText(child, geometry, text);
            if (found != null) return found;
        }
        return null;
    }

    private static SceneNode textNode(SceneNode node, SceneGeometryView geometry) {
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry != null && entry.text != null && !entry.text.logicalText.isEmpty()) return node;
        for (SceneNode child : node.children()) {
            SceneNode found = textNode(child, geometry);
            if (found != null) return found;
        }
        return null;
    }

    private static void targetPoint(
            SceneNode node, SceneGeometryView geometry, Object identity, int[] point) {
        require(node != null, "文档没有可读段落几何");
        SceneGeometryView.Entry entry = geometry.get(node);
        for (TextLayout.Line line : entry.text.lines())
            for (TextLayout.Cluster cluster : line.clusters()) {
                if (cluster.advance <= 0) continue;
                int x = entry.box.x + node.padding() + (int) (cluster.x + cluster.advance / 2);
                int y = entry.box.y + node.padding() + (int) (line.y + line.lineHeight / 2);
                if (node.control().pointerTarget(node, geometry, x, y) == identity) {
                    point[0] = x;
                    point[1] = y;
                    return;
                }
            }
        throw new AssertionError("正式几何未找到同一链接声明");
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual))
            throw new AssertionError(label + " " + difference(expected, actual, "root"));
    }

    /** URL百分号编码与等价Unicode地址表示相同目标；不以表示差异要求重写声明。 */
    private static String uri(String target) {
        if (target == null) return null;
        try {
            return URI.create(target).toASCIIString();
        } catch (IllegalArgumentException invalid) {
            return target;
        }
    }

    private static String difference(Object expected, Object actual, String path) {
        if (expected instanceof List && actual instanceof List) {
            List<?> a = (List<?>) expected, b = (List<?>) actual;
            for (int i = 0; i < Math.min(a.size(), b.size()); i++)
                if (!Objects.equals(a.get(i), b.get(i)))
                    return difference(a.get(i), b.get(i), path + "[" + i + "]");
            return path + " size expected=" + a.size() + " actual=" + b.size();
        }
        return path + " expected=" + expected + " actual=" + actual;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static String str(Map<String, Object> token, String key) {
        return String.valueOf(token.get(key));
    }

    private static String attr(Map<String, Object> token, String name) {
        for (Object value : (List<?>) token.get("attrs")) {
            List<?> pair = (List<?>) value;
            if (name.equals(pair.get(0))) return String.valueOf(pair.get(1));
        }
        return null;
    }
}
