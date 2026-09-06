package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * C6a 能力锁：L1 块级文档 span 流入口（能力①）+ 行内跨 span 连续扫描（能力②）。
 *
 * <p>背景（用户裁定方案甲第一步）：宿主格式码只认样式不认 markdown 边界，样式转成
 * 锚点（{@link MarkdownSpan}）后文本恒纯、块检测由 L1 自己做（上下文它天然知道）。
 * 本锁钉死 L1 侧地基的八条行为（任务书测试要求 1..8，逐条见用例 javadoc）；
 * chat3 切换与 § 清洗拆除属 C6b，本批零消费者改动。</p>
 *
 * <p>注：本类源码刻意不用反斜杠字面（换行/反引号/反斜杠一律 char 拼接），避免
 * 定界语料在编辑器与扫描器之间的转义歧义——与 L1 主源 {@code (char) 0x60} 惯例同风。</p>
 */
public class MarkdownSpanStreamC6aLockTest {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;
    private static final int BLUE = 0xFF5555FF;

    private static final String NL = String.valueOf((char) 10);
    private static final String BS = String.valueOf((char) 92);
    private static final String T = String.valueOf((char) 0x60);
    private static final String FENCE = T + T + T;
    private static final String STAR = String.valueOf((char) 0x2A);
    private static final String BOLD = STAR + STAR;

    private static TextStyle style(int color) {
        TextStyle s = new TextStyle();
        s.setColor(color);
        return s;
    }

    private static MarkdownSpan span(String text, int color) {
        return new MarkdownSpan(text, style(color));
    }

    /** 段流逐位判等：文本 + latex 源 + 样式深比较（{@link StyleValues} 全字段尺）。 */
    private static void assertSegmentsEqual(String message, List<TextSegment> expected,
            List<TextSegment> actual) {
        Assert.assertEquals(message + "（段数）", expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            TextSegment e = expected.get(i);
            TextSegment a = actual.get(i);
            Assert.assertEquals(message + " 段" + i + " 文本", e.getText(), a.getText());
            Assert.assertEquals(message + " 段" + i + " latex 位", e.isLatex(), a.isLatex());
            Assert.assertEquals(message + " 段" + i + " latex 源", e.getLatexSource(),
                    a.getLatexSource());
            Assert.assertTrue(message + " 段" + i + " 样式值等值",
                    StyleValues.same(e.getStyle(), a.getStyle()));
        }
    }

    // ==================== 锁 1：span 入口 vs String 入口无语义样式逐位同 ====================

    /**
     * 任务书要求 1：无语义样式（单 span 白底）下，块级 span 流入口与 String 入口产出
     * 逐位相同的行接缝（kind/quoteLevel/headingLevel/blockId/标记链/逐段文本与样式）
     * 与段接缝（toSegments）。语料覆盖全部块形态：ATX 标题、段落+强调、无序/有序列表、
     * 引用（含空行多段与硬换行）、围栏（含空行）、缩进代码、分隔线、setext、块间空行。
     */
    @Test
    public void spanEntryMustMatchStringEntryBitForBitWithoutSemanticStyles() {
        String doc = "# 标题一" + NL + NL + "正文 **强调** 尾" + NL + NL
                + "- 项甲" + NL + "- 项乙" + NL + NL
                + "1. 序一" + NL + "2. 序二" + NL + NL
                + "> 引用段甲" + NL + ">" + NL + "> 引用段乙 硬换行  " + NL + "> 次行" + NL + NL
                + FENCE + "java" + NL + "围栏 **粗** dollar x dollar 字面" + NL + NL + FENCE + NL + NL
                + "    缩进代码行" + NL + NL
                + "歧义上句" + NL + "---" + NL + NL
                + "---" + NL + "尾段 *斜* 结束";
        MarkdownDocument viaString = MarkdownDocument.parse(doc);
        MarkdownDocument viaSpan = MarkdownDocument.parse(
                Collections.singletonList(span(doc, WHITE)));
        TextStyle base = style(WHITE);
        MarkdownStyleTable table = MarkdownStyleTable.defaults();
        List<MarkdownLayoutLine> a = viaString.toLayoutLines(table, base);
        List<MarkdownLayoutLine> b = viaSpan.toLayoutLines(table, base);
        Assert.assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            MarkdownLayoutLine la = a.get(i);
            MarkdownLayoutLine lb = b.get(i);
            Assert.assertEquals("第" + i + "行 kind", la.getKind(), lb.getKind());
            Assert.assertEquals("第" + i + "行 quoteLevel", la.getQuoteLevel(), lb.getQuoteLevel());
            Assert.assertEquals("第" + i + "行 headingLevel", la.getHeadingLevel(),
                    lb.getHeadingLevel());
            Assert.assertEquals("第" + i + "行 blockId", la.getBlockId(), lb.getBlockId());
            Assert.assertEquals("第" + i + "行 链长", la.getListMarkerChain().size(),
                    lb.getListMarkerChain().size());
            assertSegmentsEqual("第" + i + "行", la.getSegments(), lb.getSegments());
        }
        assertSegmentsEqual("toSegments 全流", viaString.toSegments(table, base),
                viaSpan.toSegments(table, base));
        Assert.assertEquals(viaString.getBlockCount(), viaSpan.getBlockCount());
        Assert.assertEquals(viaString.getSource(), viaSpan.getSource());
    }

    // ==================== 锁 2：跨 span 定界闭合 ====================

    /**
     * 任务书要求 2（能力②实证后果）：span[(BOLD,A),("粗",B),(BOLD,A)] 必须产粗体「粗」
     * 且颜色取 B 段基础样式——旧逐 span 独立解析产字面形态（与单文本输入语义分叉），
     * 方案甲切换后此形态海量出现，分叉即回归。
     */
    @Test
    public void emphasisDelimitersMustCloseAcrossSpanBoundaries() {
        List<TextSegment> out = MarkdownInlineParser.parse(Arrays.asList(
                new MarkdownSpan(BOLD, style(RED)),
                new MarkdownSpan("粗", style(GREEN)),
                new MarkdownSpan(BOLD, style(RED))));
        Assert.assertEquals("跨 span 配对 → 单语义段", 1, out.size());
        Assert.assertEquals("粗", out.get(0).getText());
        Assert.assertEquals("颜色取 B 段基础样式", GREEN, out.get(0).getStyle().getColor());
        Assert.assertEquals("bold 位叠加", FontType.BOLD, out.get(0).getStyle().getFontType());
        List<TextSegment> plain = MarkdownInlineParser.parse(BOLD + "粗" + BOLD, style(RED));
        Assert.assertEquals(1, plain.size());
        Assert.assertEquals(plain.get(0).getText(), out.get(0).getText());
        Assert.assertEquals(plain.get(0).getStyle().getFontType(),
                out.get(0).getStyle().getFontType());
    }

    // ==================== 锁 3：一个 markdown 段跨多样式区间按边界切段 ====================

    /**
     * 任务书要求 3：bold 内容跨红蓝两个样式区间 → 按样式边界切成两段，
     * 每段 = 该区间基础样式 + bold 叠加位（粗位不丢、颜色不串）。
     */
    @Test
    public void oneEmphasisRunMustSplitAtStyleBoundaries() {
        List<TextSegment> out = MarkdownInlineParser.parse(Arrays.asList(
                new MarkdownSpan(BOLD + "粗红", style(RED)),
                new MarkdownSpan("蓝" + BOLD, style(BLUE))));
        Assert.assertEquals(2, out.size());
        Assert.assertEquals("粗红", out.get(0).getText());
        Assert.assertEquals("蓝", out.get(1).getText());
        Assert.assertEquals(RED, out.get(0).getStyle().getColor());
        Assert.assertEquals(BLUE, out.get(1).getStyle().getColor());
        Assert.assertEquals("前段 bold 不丢", FontType.BOLD, out.get(0).getStyle().getFontType());
        Assert.assertEquals("后段 bold 不丢", FontType.BOLD, out.get(1).getStyle().getFontType());
    }

    // ==================== 锁 4：行首样式锚点不影响块检测 ====================

    /**
     * 任务书要求 4（方案甲核心目的）：span[("- ",红),("项",蓝)]——样式在、文本纯 ⇒
     * 块标记命中：识别为列表项（行接缝 kind=LIST），正文段色 = span 蓝；
     * 标记段是块层合成文本、恒 caller 基样式。
     */
    @Test
    public void styledLineStartMustNotBlockListDetection() {
        MarkdownDocument doc = MarkdownDocument.parse(Arrays.asList(
                span("- ", RED), span("项", BLUE)));
        List<MarkdownLayoutLine> lines = doc.toLayoutLines(MarkdownStyleTable.defaults(),
                style(WHITE));
        Assert.assertEquals(1, lines.size());
        MarkdownLayoutLine line = lines.get(0);
        Assert.assertEquals("块标记命中 → LIST", MarkdownLayoutLine.Kind.LIST, line.getKind());
        Assert.assertEquals("标记段 + 正文段", 2, line.getSegments().size());
        TextSegment marker = line.getSegments().get(0);
        TextSegment body = line.getSegments().get(1);
        String bullet = MarkdownStyleTable.defaults().getBulletMarker() + " ";
        Assert.assertEquals("标记段文本 = 样式表符号+空格", bullet, marker.getText());
        Assert.assertEquals("标记段 = 合成文本，取 caller 基样式", WHITE,
                marker.getStyle().getColor());
        Assert.assertEquals("项", body.getText());
        Assert.assertEquals("正文色 = span 基础样式（蓝）", BLUE, body.getStyle().getColor());
    }

    // ==================== 锁 5：围栏内 span 样式保留且内容字面（对照缺陷 (a)） ====================

    /**
     * 任务书要求 5：走 span 入口时围栏内容不被改写——旧「输入侧预清洗」看不到块上下文，
     * 会把围栏内行当消息行处理（吞字级）。本锁钉死：围栏行内容字面（星号原样）、
     * 样式锚点保留（段色 = span 红）、行接缝 kind=CODE、内容绝不进强调解析。
     */
    @Test
    public void fenceContentMustStayLiteralWithStylesPreserved() {
        String body = "代码 **粗** 全字面";
        List<MarkdownSpan> spans = Arrays.asList(
                span(FENCE + NL, WHITE),
                span(body, RED),
                span(NL + FENCE + NL, WHITE));
        MarkdownDocument doc = MarkdownDocument.parse(spans);
        List<MarkdownLayoutLine> lines = doc.toLayoutLines(MarkdownStyleTable.defaults(),
                style(WHITE));
        Assert.assertEquals("围栏内容 1 显示行", 1, lines.size());
        MarkdownLayoutLine codeLine = lines.get(0);
        Assert.assertEquals(MarkdownLayoutLine.Kind.CODE, codeLine.getKind());
        Assert.assertEquals("内容字面：定界符一字不改", body, text(codeLine.getSegments()));
        Assert.assertEquals("样式锚点保留：内容段色 = span 红", RED,
                codeLine.getSegments().get(0).getStyle().getColor());
        Assert.assertFalse("code 内容不进强调解析", anyBold(codeLine.getSegments()));
    }

    // ==================== 锁 6：引用内带样式列表行升格（对照缺陷 (b)） ====================

    /**
     * 任务书要求 6：引用标记为普通文本、列表标记行带样式锚点（= 方案甲下容器标记后
     * 带码列表行的形态）必须升格为引用内列表——旧预清洗只认物理行首、容器标记后的
     * 标记不处理。用 quoteTextColor=0 的表（颜色由 span 决定的口径）验色轴，
     * 默认表验结构轴。
     */
    @Test
    public void styledListLineInsideContainerMustUpgrade() {
        MarkdownStyleTable table = new MarkdownStyleTable();
        table.setQuoteTextColor(0);
        MarkdownDocument doc = MarkdownDocument.parse(Arrays.asList(
                span("> ", WHITE), span("- ", RED), span("x", BLUE)));
        List<MarkdownLayoutLine> lines = doc.toLayoutLines(table, style(WHITE));
        Assert.assertEquals(1, lines.size());
        MarkdownLayoutLine line = lines.get(0);
        Assert.assertEquals("引用层级升格", 1, line.getQuoteLevel());
        Assert.assertEquals("引用内列表升格", MarkdownLayoutLine.Kind.LIST, line.getKind());
        String bullet = MarkdownStyleTable.defaults().getBulletMarker() + " ";
        Assert.assertEquals(bullet, line.getSegments().get(0).getText());
        Assert.assertEquals("x", line.getSegments().get(1).getText());
        Assert.assertEquals("关闭 F3 旋钮后正文色 = span（蓝）", BLUE,
                line.getSegments().get(1).getStyle().getColor());
        MarkdownLayoutLine defaulted = doc.toLayoutLines(MarkdownStyleTable.defaults(),
                style(WHITE)).get(0);
        Assert.assertEquals(line.getKind(), defaulted.getKind());
        Assert.assertEquals(line.getQuoteLevel(), defaulted.getQuoteLevel());
    }

    // ==================== 锁 7：单 span 等价性（能力②回归防线） ====================

    /**
     * 任务书要求 7：parse(String, style) 与 parse(singletonList(span)) 逐位等价。
     * 语料取行内全语法面 + 防误伤族 + 未闭合族（门禁 48 条语料全绿是全量消费者的
     * 机器证明，本锁是行内层自身的直接锁）。
     */
    @Test
    public void singleSpanMustStayBitIdenticalToStringPath() {
        String[] corpus = {
            "前 **粗** 后", "hello_world", "a_b", "x_1", "a*b", "2*3=6", "x * y",
            "中文_夹_中文", "这是**重要**消息", "a***b***", STAR + "both" + STAR,
            "__粗__ 与 _斜_", "~~删~~ 混 **粗 *内斜* 尾**", "反引号 " + T + "code x" + T + " 字面",
            "价格 $5.99 与 $E=mc^2$", "[链接](https://a.b) 与 [**粗链**](https://c.d(e))",
            "转义 " + BS + "*" + BS + " 与 x", "未闭合 **粗 与 ~~删 与 $公 与 [标](x",
            "第一行" + NL + "第二行", "![图](url) 字面叹号", "尾随标记**", BOLD + "头",
            "x" + T + "y" + T + "z" + T,
        };
        for (String src : corpus) {
            TextStyle base = style(RED);
            List<TextSegment> viaString = MarkdownInlineParser.parse(src, base);
            List<TextSegment> viaSpan = MarkdownInlineParser.parse(
                    Collections.singletonList(new MarkdownSpan(src, base)));
            assertSegmentsEqual("语料[" + src + "]", viaString, viaSpan);
            MarkdownStyleTable table = MarkdownStyleTable.defaults();
            assertSegmentsEqual("带表语料[" + src + "]",
                    MarkdownInlineParser.parse(src, base, table),
                    MarkdownInlineParser.parse(Collections.singletonList(
                            new MarkdownSpan(src, base)), table));
        }
    }

    /** 跨 span 连续扫描不得新造配对：拼接后仍未闭合的定界符恒字面。 */
    @Test
    public void unclosedDelimitersMustStayLiteralAcrossSpans() {
        List<TextSegment> out = MarkdownInlineParser.parse(Arrays.asList(
                span(BOLD + "未闭合", RED), span("继续", BLUE)));
        Assert.assertEquals(BOLD + "未闭合继续", text(out));
        Assert.assertFalse(anyBold(out));
        List<TextSegment> lone = MarkdownInlineParser.parse(Arrays.asList(
                span("[标", RED), span("签]", BLUE)));
        Assert.assertEquals("[标签]", text(lone));
    }

    /**
     * 跨 span 交叠边界取样锁（C6a 交付报告第⑧条点名「嵌套强调/链接/code span 交叠」，
     * 探针实证后升格为常驻锁）：嵌套强调、链接、code span 三种复合形态在任意 span
     * 边界切断都必须与单文本产形同语义（文本/位一致，颜色按区间回溯）。
     */
    @Test
    public void compositeFormsMustSurviveArbitrarySpanCuts() {
        // 嵌套强调：加粗体内跨斜体界，span 切点在词内任意处
        List<TextSegment> nested = MarkdownInlineParser.parse(Arrays.asList(
                span("**外A *内", RED), span("B* 尾", GREEN), span("C**", BLUE)));
        Assert.assertEquals("外A ", nested.get(0).getText());
        Assert.assertEquals(FontType.BOLD, nested.get(0).getStyle().getFontType());
        Assert.assertTrue("内段斜+粗", nested.get(1).getStyle().isItalic());
        Assert.assertEquals(FontType.BOLD, nested.get(1).getStyle().getFontType());
        Assert.assertEquals(RED, nested.get(1).getStyle().getColor());
        Assert.assertEquals(FontType.BOLD, nested.get(3).getStyle().getFontType());
        Assert.assertEquals(GREEN, nested.get(3).getStyle().getColor());
        Assert.assertEquals("C", nested.get(4).getText());
        Assert.assertEquals(BLUE, nested.get(4).getStyle().getColor());
        // 与单文本产形逐段等值（同 base 下）
        String whole = "**外A *内" + "B* 尾" + "C**";
        Assert.assertEquals(text(MarkdownInlineParser.parse(whole, style(WHITE))),
                text(nested));
        // 链接：方括号/圆括号/URL 三处切断
        List<TextSegment> link = MarkdownInlineParser.parse(Arrays.asList(
                span("[tk", RED), span("](http", GREEN), span(".a)", BLUE)));
        Assert.assertEquals(1, link.size());
        Assert.assertEquals("tk", link.get(0).getText());
        Assert.assertEquals("http.a", link.get(0).getStyle().getLink());
        Assert.assertTrue(link.get(0).getStyle().isUnderline());
        Assert.assertEquals("URL 内跨 span 不被切段（定界吃入 link 位后整段）", RED,
                link.get(0).getStyle().getColor());
        // code span：反引号对跨 span 闭合、内容按样式界切段、每段 code 位全套
        List<TextSegment> code = MarkdownInlineParser.parse(Arrays.asList(
                span("x" + T + "co", RED), span("de" + T + "y", GREEN)));
        Assert.assertEquals(4, code.size());
        Assert.assertEquals("x", code.get(0).getText());
        Assert.assertFalse(code.get(0).getStyle().isCodeSpan());
        Assert.assertEquals("co", code.get(1).getText());
        Assert.assertTrue("前 code 段带全套位", code.get(1).getStyle().isCodeSpan());
        Assert.assertEquals(RED, code.get(1).getStyle().getColor());
        Assert.assertEquals("de", code.get(2).getText());
        Assert.assertTrue("后 code 段带全套位", code.get(2).getStyle().isCodeSpan());
        Assert.assertEquals(GREEN, code.get(2).getStyle().getColor());
        Assert.assertEquals("y", code.get(3).getText());
        // code 内容内星号恒字面（跨 span 也不新造配对）
        List<TextSegment> codeStars = MarkdownInlineParser.parse(Arrays.asList(
                span(T + "**a", RED), span("b**" + T, GREEN)));
        Assert.assertEquals(2, codeStars.size());
        Assert.assertEquals("**a", codeStars.get(0).getText());
        Assert.assertEquals("b**", codeStars.get(1).getText());
        Assert.assertTrue(codeStars.get(0).getStyle().isCodeSpan());
        Assert.assertFalse(anyBold(codeStars));
        // flanking 防误伤跨边界维持："a**" + "b**c" 恒字面（与单文本同）
        List<TextSegment> flank = MarkdownInlineParser.parse(Arrays.asList(
                span("a**", RED), span("b**c", GREEN)));
        Assert.assertEquals("a**b**c", text(flank));
        Assert.assertFalse(anyBold(flank));
    }

    // ==================== 锁 8：span 含换行符的切行正确性 ====================

    /** 一个 span 跨多行：样式继承到每一行；行接缝按行拆分。 */
    @Test
    public void spanCrossingNewlinesMustSplitIntoStyledSourceLines() {
        MarkdownDocument doc = MarkdownDocument.parse(
                Collections.singletonList(span("甲" + NL + "乙" + NL + "丙", RED)));
        List<MarkdownLayoutLine> lines = doc.toLayoutLines(MarkdownStyleTable.defaults(),
                style(WHITE));
        Assert.assertEquals("软换行 3 显示行", 3, lines.size());
        Assert.assertEquals("甲", text(lines.get(0).getSegments()));
        Assert.assertEquals("乙", text(lines.get(1).getSegments()));
        Assert.assertEquals("丙", text(lines.get(2).getSegments()));
        for (MarkdownLayoutLine line : lines) {
            Assert.assertEquals("样式继承：每行都吃 span 红", RED,
                    line.getSegments().get(0).getStyle().getColor());
        }
    }

    /** 换行归属：跨样式边界的换行后新样式段正确起新行；行中段流按样式边界切。 */
    @Test
    public void newlineAttributionMustKeepAdjacentStylePiecesOnTheirLines() {
        MarkdownDocument doc = MarkdownDocument.parse(Arrays.asList(
                span("甲" + NL + "乙", RED), span("丙", BLUE)));
        List<MarkdownLayoutLine> lines = doc.toLayoutLines(MarkdownStyleTable.defaults(),
                style(WHITE));
        Assert.assertEquals(2, lines.size());
        Assert.assertEquals("甲", text(lines.get(0).getSegments()));
        Assert.assertEquals("行1 = 红「乙」+ 蓝「丙」两段", 2, lines.get(1).getSegments().size());
        Assert.assertEquals("乙", lines.get(1).getSegments().get(0).getText());
        Assert.assertEquals(RED, lines.get(1).getSegments().get(0).getStyle().getColor());
        Assert.assertEquals("丙", lines.get(1).getSegments().get(1).getText());
        Assert.assertEquals(BLUE, lines.get(1).getSegments().get(1).getStyle().getColor());
    }

    /** 空行归属：单 span 含空行 ⇒ 正常切块（两段落 + F6 占位），样式继承两段。 */
    @Test
    public void blankLineInsideSpanMustBelongToNoLine() {
        MarkdownDocument doc = MarkdownDocument.parse(
                Collections.singletonList(span("a" + NL + NL + "b", RED)));
        Assert.assertEquals("空行切出两个段落", 2, doc.getBlockCount());
        // 两路对比口径 = 同值 base（span 语义样式下色由 span 定，caller 传红 → 与 String 路同形）
        List<TextSegment> segs = doc.toSegments(MarkdownStyleTable.defaults(), style(RED));
        assertSegmentsEqual("与 String 路逐位同",
                MarkdownDocument.parse("a" + NL + NL + "b")
                        .toSegments(MarkdownStyleTable.defaults(), style(RED)),
                segs);
        for (TextSegment s : segs) {
            Assert.assertEquals("样式继承不丢色", RED, s.getStyle().getColor());
        }
    }

    // ==================== 辅助 ====================

    private static String text(List<TextSegment> segments) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            builder.append(segments.get(i).getText());
        }
        return builder.toString();
    }

    private static boolean anyBold(List<TextSegment> segments) {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).getStyle().getFontType() == FontType.BOLD) {
                return true;
            }
        }
        return false;
    }
}
