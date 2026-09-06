package club.heiqi.uilib.font.layout.markdown;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * {@link MarkdownDocument} 公共面与扁平化测试矩阵（M2，D3 最小面验收依据）。
 * 三条踩坑语料在公共面的投影：① 硬换行扁平后不留行尾空格且与软换行在块模型位图中区分；
 * ② 围栏代码段内粗体/公式/引用记号在片段流中一律字面（无 BOLD/斜体/LaTeX 段）；
 * ③ 嵌套引用只进结构。<b>C1a（2026-09-06 对齐裁定）</b>：列表层级不再编码进段流文本
 * （旧 F2「每级 2 前导空格」退役，标记段恒裸体，见
 * {@code shouldFlattenNestedListItemAndContinuation} 注释）；缩进代码块与围栏同款 CODE 字面。
 */
public class MarkdownDocumentTest {

    private static final char TICK = (char) 0x60;

    private static TextStyle baseStyle() {
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        return style;
    }

    private static String nl(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append((char) 0x0A);
            }
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    private static String fence(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(TICK);
        }
        return builder.toString();
    }

    /** 拼接全部段的纯文本（latex 段以尖括号占位，同 M1 测试口径）。 */
    private static String plainText(List<TextSegment> segments) {
        StringBuilder builder = new StringBuilder();
        for (TextSegment segment : segments) {
            builder.append(segment.isLatex() ? "<latex>" + segment.getLatexSource() + "</latex>"
                    : segment.getText());
        }
        return builder.toString();
    }

    // ==================== 入口与空文档 ====================

    @Test
    public void shouldParseNullAndEmptyAsEmptyDocument() {
        // C6b：span 入口改名 parseSpans 后 parse(null) 恢复单义（C6a 的强转是重载二义的
        // 临时代价，随改名一并退还——「source 可为 null」承诺重新无摩擦）。
        MarkdownDocument nullDoc = MarkdownDocument.parse(null);
        Assert.assertTrue(nullDoc.isEmpty());
        Assert.assertEquals("", nullDoc.getSource());
        Assert.assertEquals(0, nullDoc.getBlockCount());
        Assert.assertTrue(nullDoc.toSegments(baseStyle()).isEmpty());
        Assert.assertTrue(MarkdownDocument.parse("").toSegments(baseStyle()).isEmpty());
    }

    @Test
    public void shouldRejectNullBaseStyle() {
        MarkdownDocument doc = MarkdownDocument.parse("段落");
        try {
            doc.toSegments(null);
            Assert.fail("null baseStyle 必须拒绝");
        } catch (IllegalArgumentException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
        try {
            doc.toSegments(MarkdownStyleTable.defaults(), null);
            Assert.fail("null baseStyle 必须拒绝");
        } catch (IllegalArgumentException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
    }

    // ==================== 标题 ====================

    @Test
    public void shouldFlattenHeadingBoldKeepingColor() {
        TextStyle base = baseStyle();
        base.setColor(0xFF336699);
        List<TextSegment> segments = MarkdownDocument.parse("### 小标").toSegments(base);
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("小标", segments.get(0).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(0).getStyle().getFontType());
        Assert.assertEquals(0xFF336699, segments.get(0).getStyle().getColor());
        Assert.assertFalse(segments.get(0).getStyle().isUnderline());
        // 基础样式不被修改（叠加语义照抄行内裁定）
        Assert.assertEquals(FontType.NORMAL, base.getFontType());
    }

    @Test
    public void shouldApplyHeadingUnderlineWhenTableConfigures() {
        MarkdownStyleTable table = MarkdownStyleTable.defaults();
        table.setHeadingUnderline(true);
        table.setHeadingBold(false);
        List<TextSegment> segments = MarkdownDocument.parse("## 标").toSegments(table, baseStyle());
        Assert.assertTrue(segments.get(0).getStyle().isUnderline());
        Assert.assertEquals(FontType.NORMAL, segments.get(0).getStyle().getFontType());
    }

    @Test
    public void shouldComputeHeadingSizeOnlyWhenAnchorResolvable() {
        MarkdownStyleTable table = MarkdownStyleTable.defaults();
        table.setHeadingFontSizeDeltaPx(2, 4);
        table.setDefaultFontSizePx(10);
        List<TextSegment> viaTable = MarkdownDocument.parse("## x").toSegments(table, baseStyle());
        Assert.assertEquals(14, viaTable.get(0).getStyle().getFontSizePx());
        TextStyle big = baseStyle();
        big.setFontSizePx(20);
        List<TextSegment> viaBase = MarkdownDocument.parse("## x").toSegments(table, big);
        Assert.assertEquals(24, viaBase.get(0).getStyle().getFontSizePx());
        // 基准不可解析（表与基础样式都未指定字号）时不写死字号：G4 度量同源
        List<TextSegment> inherit = MarkdownDocument.parse("## x").toSegments(baseStyle());
        Assert.assertEquals(0, inherit.get(0).getStyle().getFontSizePx());
    }

    @Test
    public void shouldParseInlineInsideHeading() {
        // 块级剥 #，正文照旧交行内解析：$x$ 走 LaTeX 段
        List<TextSegment> segments = MarkdownDocument.parse("# 式 $x^2$").toSegments(baseStyle());
        Assert.assertEquals(2, segments.size());
        Assert.assertEquals("式 ", segments.get(0).getText());
        Assert.assertTrue(segments.get(1).isLatex());
        Assert.assertEquals("x^2", segments.get(1).getLatexSource());
        Assert.assertEquals(FontType.BOLD, segments.get(1).getStyle().getFontType());
    }

    // ==================== 段落与换行（语料 ① 公共面） ====================

    @Test
    public void shouldKeepSoftAndHardBreakDistinctInModelAndCleanInFlow() {
        MarkdownDocument hard = MarkdownDocument.parse(nl("行一  ", "行二"));
        List<TextSegment> segments = hard.toSegments(baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals(nl("行一", "行二"), segments.get(0).getText());
        MarkdownBlock para = hard.blocks().get(0);
        Assert.assertTrue(para.hardBreaks[0]);
        MarkdownDocument soft = MarkdownDocument.parse(nl("行一", "行二"));
        Assert.assertFalse(soft.blocks().get(0).hardBreaks[0]);
        Assert.assertEquals(nl("行一", "行二"), plainText(soft.toSegments(baseStyle())));
    }

    @Test
    public void shouldSeparateBlocksByNewlineSegment() {
        List<TextSegment> segments = MarkdownDocument.parse(nl("# 标", "", "正文"))
                .toSegments(baseStyle());
        Assert.assertEquals(nl("标", "正文"), plainText(segments));
    }

    // ==================== 围栏代码（语料 ② 公共面） ====================

    @Test
    public void shouldFlattenFenceContentTotallyLiteral() {
        String src = nl(fence(3), "**粗** $x$ > q ~~删~~ [a](b)", fence(3));
        List<TextSegment> segments = MarkdownDocument.parse(src).toSegments(baseStyle());
        Assert.assertEquals(1, segments.size());
        TextSegment only = segments.get(0);
        Assert.assertEquals("**粗** $x$ > q ~~删~~ [a](b)", only.getText());
        Assert.assertFalse(only.isLatex());
        Assert.assertEquals(FontType.NORMAL, only.getStyle().getFontType());
        Assert.assertFalse(only.getStyle().isItalic());
        Assert.assertFalse(only.getStyle().isStrikethrough());
        Assert.assertNull(only.getStyle().getLink());
    }

    @Test
    public void shouldKeepMultiLineFenceInOneSegment() {
        String src = nl(fence(3), "a", "", "b", fence(3));
        List<TextSegment> segments = MarkdownDocument.parse(src).toSegments(baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals(nl("a", "", "b"), segments.get(0).getText());
    }

    // ==================== 引用（语料 ③ 公共面） ====================

    @Test
    public void shouldStripQuoteMarkersInFlowAndKeepNestingInModel() {
        MarkdownDocument doc = MarkdownDocument.parse("> > 深引用");
        Assert.assertEquals(1, doc.getBlockCount());
        Assert.assertEquals("深引用", plainText(doc.toSegments(baseStyle())));
        MarkdownBlock outer = doc.blocks().get(0);
        Assert.assertEquals(MarkdownBlock.Kind.QUOTE, outer.kind);
        Assert.assertEquals(MarkdownBlock.Kind.QUOTE, outer.children.get(0).kind);
    }

    @Test
    public void shouldApplyQuoteItalicOnlyWhenConfigured() {
        MarkdownStyleTable table = MarkdownStyleTable.defaults();
        table.setQuoteItalic(true);
        List<TextSegment> segments = MarkdownDocument.parse("> 语").toSegments(table, baseStyle());
        Assert.assertTrue(segments.get(0).getStyle().isItalic());
        Assert.assertFalse(MarkdownDocument.parse("> 语").toSegments(baseStyle())
                .get(0).getStyle().isItalic());
    }

    // ==================== 列表（语料 ③ 公共面） ====================

    /**
     * C3b2（2026-09-06 对齐裁定）重定：旧「有序保留源序号原文（含右括号定界）」作废，按 CommonMark
     * 续排——首项源数字 = 列表 start，其后按 start + 项下标 合成、定界符统一句点。本例 "3) 乙"
     * 与上一项定界符不同（")" vs 圆点）故另起一个有序列表，其 start=3 ⇒ 渲染序号仍为 "3. "。
     */
    @Test
    public void shouldFlattenBulletAsTableMarkerAndOrderedByStartPlusIndex() {
        List<TextSegment> segments = MarkdownDocument.parse(nl("- 甲", "", "3) 乙"))
                .toSegments(baseStyle());
        Assert.assertEquals(nl("• 甲", "3. 乙"), plainText(segments));
        // 同一列表内跨序号：start 只取首项，后续项源数字被忽略并续排
        List<TextSegment> cont = MarkdownDocument.parse(nl("3. 乙", "4) 丙")).toSegments(baseStyle());
        Assert.assertEquals("首项 = start 保留", "3. ", cont.get(0).getText());
        Assert.assertEquals(nl("3. 乙", "4. 丙"), plainText(cont));
    }

    @Test
    public void shouldFlattenNestedListItemAndContinuation() {
        List<TextSegment> segments = MarkdownDocument.parse(nl("- 甲", "  续行", "", "  - 乙"))
                .toSegments(baseStyle());
        // C1a（2026-09-06 对齐裁定）重定：旧 2026-09-04 裁 F2「嵌套每级 2 个前导空格写进 bullet
        // 段文本」作废——主流（CommonMark）不把层级编码进可见文本；标记段恒裸体「• 」，
        // 嵌套层级由行接缝 listMarkerChain 几何承载（M10d），两接缝文本同源等值。
        // 内容列嵌套判定本身不变（「  - 乙」= 父项「- 甲」内容列 2 的子列表）；块模型与
        // 缩进 px 仍未进公共面（裁 B 的「不外开块树」这一半不变）。
        Assert.assertEquals(nl("• 甲", "续行", "• 乙"), plainText(segments));
    }

    /** C1a：缩进代码块扁平 = 与围栏同款字面 CODE 段（不进段内解析、无 BOLD/链接段）。 */
    @Test
    public void shouldFlattenIndentedCodeAsLiteralSegmentLikeFence() {
        List<TextSegment> segments = MarkdownDocument.parse(nl("甲", "", "    **粗** [a](http://x.y)"))
                .toSegments(baseStyle());
        Assert.assertEquals(nl("甲", "**粗** [a](http://x.y)"), plainText(segments));
        for (TextSegment segment : segments) {
            Assert.assertFalse("代码内容不得成粗体",
                    segment.getStyle().getFontType() == FontType.BOLD);
            Assert.assertNull("代码内容不得链接化", segment.getStyle().getLink());
        }
    }

    /** C1a：块起点 1-3 空格 = 顶级列表，标记段无前导空格（baseLevel 深缩进口径作废）。 */
    @Test
    public void topLevelMarkerAtIndentUpToThreeHasBareMarkerSegment() {
        List<TextSegment> segments = MarkdownDocument.parse("   - x").toSegments(baseStyle());
        Assert.assertEquals("• ", segments.get(0).getText());
        Assert.assertEquals("x", segments.get(1).getText());
    }

    /** C1a：块起点 4 空格 + 列表标记 = 缩进代码字面段（不再是深缩进列表项）。 */
    @Test
    public void deepIndentedListMarkerFlattensToCodeLiteral() {
        List<TextSegment> segments = MarkdownDocument.parse("    - deep").toSegments(baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("- deep", segments.get(0).getText());
    }

    @Test
    public void shouldParseInlineInsideListItem() {
        List<TextSegment> segments = MarkdownDocument.parse("- **粗**常").toSegments(baseStyle());
        // 标记段 + 粗体段 + 尾部普通段（行内切段语义照抄既有裁定）
        Assert.assertEquals(3, segments.size());
        Assert.assertEquals("• ", segments.get(0).getText());
        Assert.assertEquals("粗", segments.get(1).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(1).getStyle().getFontType());
        Assert.assertEquals("常", segments.get(2).getText());
    }

    @Test
    public void shouldSupportCustomBulletAndSuppression() {
        MarkdownStyleTable table = MarkdownStyleTable.defaults();
        table.setBulletMarker("*");
        Assert.assertTrue(plainText(MarkdownDocument.parse("- 甲").toSegments(table, baseStyle()))
                .startsWith("* 甲"));
        table.setBulletMarker("");
        Assert.assertEquals("甲", plainText(MarkdownDocument.parse("- 甲").toSegments(table, baseStyle())));
    }

    // ==================== 分隔线 ====================

    @Test
    public void shouldFlattenThematicBreakAsTableText() {
        List<TextSegment> segments = MarkdownDocument.parse("---").toSegments(baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals(36, segments.get(0).getText().length());
        Assert.assertEquals("-", segments.get(0).getText().substring(0, 1));
        MarkdownStyleTable table = MarkdownStyleTable.defaults();
        table.setThematicBreakText("");
        Assert.assertTrue(MarkdownDocument.parse("***").toSegments(table, baseStyle()).isEmpty());
    }

    // ==================== 刻意不支持 = 字面输出（公共面钉死） ====================

    @Test
    public void shouldKeepImagePerExistingInlineRuling() {
        // 块层不生成图片节点；[alt](url) 依既有行内裁定成链接、! 字面——行内语义一行不改
        List<TextSegment> segments = MarkdownDocument.parse("![猫](http://a.b)").toSegments(baseStyle());
        Assert.assertEquals(2, segments.size());
        Assert.assertEquals("!", segments.get(0).getText());
        Assert.assertNull(segments.get(0).getStyle().getLink());
        Assert.assertEquals("猫", segments.get(1).getText());
        Assert.assertEquals("http://a.b", segments.get(1).getStyle().getLink());
        Assert.assertTrue(segments.get(1).getStyle().isUnderline());
    }

    @Test
    public void shouldKeepTableTaskListHtmlFootnoteLiteralInFlow() {
        String table = nl("| a | b |", "|-|-|");
        Assert.assertEquals(table, plainText(MarkdownDocument.parse(table).toSegments(baseStyle())));
        Assert.assertEquals("<b>粗</b> [^1]",
                plainText(MarkdownDocument.parse("<b>粗</b> [^1]").toSegments(baseStyle())));
        Assert.assertEquals("• [ ] 任务",
                plainText(MarkdownDocument.parse("- [ ] 任务").toSegments(baseStyle())));
    }

    // ==================== 混合文档端到端 ====================

    @Test
    public void shouldFlattenMixedDocumentInOrder() {
        String src = nl("# 标题", "", "段落 **粗** 与 [链](http://x.y)", "", fence(3), "code **", fence(3),
                "", "> 引用", "", "- 项一", "  续行", "- 项二", "", "---", "", "收尾段");
        MarkdownDocument doc = MarkdownDocument.parse(src);
        Assert.assertEquals(7, doc.getBlockCount());
        Assert.assertEquals(src, doc.getSource());
        String plain = plainText(doc.toSegments(baseStyle()));
        Assert.assertEquals(nl("标题", "段落 粗 与 链", "code **", "引用", "• 项一", "续行",
                "• 项二", "------------------------------------", "收尾段"), plain);
        // 顺序抽查
        Assert.assertTrue(plain.indexOf("标题") < plain.indexOf("code **"));
        Assert.assertTrue(plain.indexOf("引用") < plain.indexOf("• 项一"));
    }

    @Test
    public void shouldFallbackToDefaultsWhenTableNull() {
        List<TextSegment> segments = MarkdownDocument.parse("# 标").toSegments(null, baseStyle());
        Assert.assertEquals(FontType.BOLD, segments.get(0).getStyle().getFontType());
    }

    @Test
    public void shouldNotMutateBaseStyleThroughWholeDocument() {
        TextStyle base = baseStyle();
        MarkdownDocument.parse(nl("# 标", "", "- **粗**", "", "> 语", "", fence(3), "$x$", fence(3)))
                .toSegments(base);
        Assert.assertEquals(FontType.NORMAL, base.getFontType());
        Assert.assertFalse(base.isItalic());
        Assert.assertFalse(base.isUnderline());
        Assert.assertFalse(base.isStrikethrough());
        Assert.assertNull(base.getLink());
        Assert.assertEquals(0, base.getFontSizePx());
    }

}

