package club.heiqi.uilib.font.layout.markdown;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.markdown.MarkdownBlock.Kind;

/**
 * 块级扫描器测试矩阵（M2）。每个块级构造至少一条用例，含规划外三条既有踩坑语料：
 * ① 行尾两空格硬换行 vs 普通换行不得互相误判；② 围栏代码块内粗体星号/美元符/尖角号一律
 * 字面（块模型钉死：CODE 正文原样、children 为空，永不进行内解析）；③ 嵌套引用两连 &gt; 与
 * 列表项内的续行缩进。刻意不支持语法（表格/任务列表/HTML 内联/脚注/图片）的字面输出同测。
 */
public class MarkdownBlockParserTest {

    /** 反引号 0x60（同 MarkdownInlineParser 书写裁定，避免 Unicode 转义陷阱）。 */
    private static final char TICK = (char) 0x60;

    /** 反斜杠 0x5C。 */
    private static final char BS = (char) 0x5C;

    private static String fence(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(TICK);
        }
        return builder.toString();
    }

    private static String repeat(char ch, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }

    /** 拼接行集为含 \n 的全文（软换行原样保留可见于断言）。 */
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

    /** 断言单块文档并返回该块。 */
    private static MarkdownBlock single(String source, Kind kind) {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(source);
        Assert.assertEquals(source, 1, blocks.size());
        Assert.assertEquals(source, kind, blocks.get(0).kind);
        return blocks.get(0);
    }

    // ==================== ATX 标题 ====================

    @Test
    public void shouldParseAtxHeadingsAllLevels() {
        for (int level = 1; level <= 6; level++) {
            List<MarkdownBlock> blocks = MarkdownBlockParser.parse(repeat((char) 35, level) + " 标题" + level);
            Assert.assertEquals(1, blocks.size());
            Assert.assertEquals(Kind.HEADING, blocks.get(0).kind);
            Assert.assertEquals(level, blocks.get(0).level);
            Assert.assertEquals("标题" + level, blocks.get(0).text);
        }
    }

    @Test
    public void shouldRequireSpaceAfterHashes() {
        MarkdownBlock block = single("#hashtag 未加空格", Kind.PARAGRAPH);
        Assert.assertEquals("#hashtag 未加空格", block.joinedLines());
    }

    @Test
    public void shouldStripClosingHashSequence() {
        MarkdownBlock block = single("## 标题 ##  ", Kind.HEADING);
        Assert.assertEquals("标题", block.text);
        Assert.assertEquals("x", MarkdownBlockParser.parse("### x #").get(0).text);
        Assert.assertEquals("a#b", MarkdownBlockParser.parse("# a#b").get(0).text);
    }

    @Test
    public void shouldTreatSevenHashesAsParagraph() {
        MarkdownBlock block = single("####### 七级", Kind.PARAGRAPH);
        Assert.assertEquals("####### 七级", block.joinedLines());
    }

    /** 钉死已裁简化：行首反斜杠不是块转义——整行按字面段落，反斜杠保留（# 不可转义）。 */
    @Test
    public void backslashBeforeHashIsLiteralParagraph() {
        MarkdownBlock block = single(BS + "# 屏蔽尝试", Kind.PARAGRAPH);
        Assert.assertEquals(BS + "# 屏蔽尝试", block.joinedLines());
    }

    // ==================== 围栏代码 ====================

    /** 踩坑语料 ②：围栏内粗体星号、美元符、尖角号与波浪线一律字面。 */
    @Test
    public void shouldKeepFenceContentLiteral() {
        String src = nl(fence(3), "**粗** $x$ > 非引用 ~~~ 未闭波浪线", fence(3));
        MarkdownBlock block = single(src, Kind.CODE);
        Assert.assertEquals(1, block.lines.size());
        Assert.assertEquals("**粗** $x$ > 非引用 ~~~ 未闭波浪线", block.lines.get(0));
        Assert.assertEquals("", block.info);
        Assert.assertTrue(block.children.isEmpty());
    }

    @Test
    public void shouldCloseTildeFenceOnlyWithTilde() {
        String src = nl("~~~", fence(3) + " 不闭合", "~~~");
        MarkdownBlock block = single(src, Kind.CODE);
        Assert.assertEquals(1, block.lines.size());
        Assert.assertEquals(fence(3) + " 不闭合", block.lines.get(0));
    }

    @Test
    public void shouldRequireClosingFenceAtLeastAsLong() {
        String src = nl(fence(4), fence(3), "内容", fence(4));
        MarkdownBlock block = single(src, Kind.CODE);
        Assert.assertEquals(2, block.lines.size());
        Assert.assertEquals(fence(3), block.lines.get(0));
        Assert.assertEquals("内容", block.lines.get(1));
    }

    @Test
    public void shouldConsumeUnclosedFenceToEof() {
        MarkdownBlock block = single(nl(fence(3), "甲", "乙"), Kind.CODE);
        Assert.assertEquals(2, block.lines.size());
        Assert.assertEquals("甲", block.lines.get(0));
        Assert.assertEquals("乙", block.lines.get(1));
    }

    @Test
    public void shouldStripFenceIndentAndRecordInfo() {
        String src = nl("  " + fence(3) + "java", "  code1", "    code2", "   ", "  " + fence(3));
        MarkdownBlock block = single(src, Kind.CODE);
        Assert.assertEquals("java", block.info);
        Assert.assertEquals(3, block.lines.size());
        Assert.assertEquals("code1", block.lines.get(0));
        Assert.assertEquals("  code2", block.lines.get(1));
        Assert.assertEquals("", block.lines.get(2));
    }

    @Test
    public void shouldLetFenceInterruptParagraph() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("正文", fence(3), "code", fence(3)));
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(0).kind);
        Assert.assertEquals(Kind.CODE, blocks.get(1).kind);
        Assert.assertEquals("正文", blocks.get(0).joinedLines());
        Assert.assertEquals("code", blocks.get(1).joinedLines());
    }

    // ==================== 引用块 ====================

    @Test
    public void shouldParseSimpleQuote() {
        MarkdownBlock quote = single("> 引用文本", Kind.QUOTE);
        Assert.assertEquals(1, quote.children.size());
        Assert.assertEquals(Kind.PARAGRAPH, quote.children.get(0).kind);
        Assert.assertEquals("引用文本", quote.children.get(0).joinedLines());
    }

    /** 踩坑语料 ③（其一）：嵌套引用（两连 &gt;）→ 两层 QUOTE 树。 */
    @Test
    public void shouldParseNestedQuote() {
        MarkdownBlock outer = single("> > 深引用", Kind.QUOTE);
        Assert.assertEquals(1, outer.children.size());
        MarkdownBlock inner = outer.children.get(0);
        Assert.assertEquals(Kind.QUOTE, inner.kind);
        Assert.assertEquals(Kind.PARAGRAPH, inner.children.get(0).kind);
        Assert.assertEquals("深引用", inner.children.get(0).joinedLines());
    }

    @Test
    public void shouldLazyContinueQuote() {
        MarkdownBlock quote = single(nl("> 甲", "乙(惰性)"), Kind.QUOTE);
        Assert.assertEquals(1, quote.children.size());
        MarkdownBlock para = quote.children.get(0);
        Assert.assertEquals(2, para.lines.size());
        Assert.assertEquals("乙(惰性)", para.lines.get(1));
    }

    @Test
    public void shouldEndQuoteBeforeInterruptingBlock() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("> 甲", "# 标题"));
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals(Kind.QUOTE, blocks.get(0).kind);
        Assert.assertEquals(Kind.HEADING, blocks.get(1).kind);
    }

    @Test
    public void shouldMergeQuoteParagraphsAcrossBlankLine() {
        MarkdownBlock quote = single(nl("> 甲", "", "> 乙"), Kind.QUOTE);
        Assert.assertEquals(2, quote.children.size());
        Assert.assertEquals("甲", quote.children.get(0).joinedLines());
        Assert.assertEquals("乙", quote.children.get(1).joinedLines());
    }

    @Test
    public void shouldQuoteInterruptParagraph() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("正文", "> 引用"));
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(0).kind);
        Assert.assertEquals(Kind.QUOTE, blocks.get(1).kind);
    }

    @Test
    public void shouldAcceptQuoteWithoutSpaceAfterMarker() {
        MarkdownBlock quote = single(">紧挨", Kind.QUOTE);
        Assert.assertEquals("紧挨", quote.children.get(0).joinedLines());
    }

    // ==================== 列表 ====================

    @Test
    public void shouldParseUnorderedMarkersAsSeparateLists() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("- 一", "* 二", "+ 三"));
        Assert.assertEquals(3, blocks.size());
        for (MarkdownBlock list : blocks) {
            Assert.assertEquals(Kind.LIST, list.kind);
            Assert.assertFalse(list.ordered);
            Assert.assertEquals(1, list.children.size());
        }
        Assert.assertEquals("-", blocks.get(0).children.get(0).marker);
        Assert.assertEquals("*", blocks.get(1).children.get(0).marker);
        Assert.assertEquals("+", blocks.get(2).children.get(0).marker);
    }

    @Test
    public void shouldParseOrderedDotAndParen() {
        MarkdownBlock list = single(nl("3. 三", "4. 四"), Kind.LIST);
        Assert.assertTrue(list.ordered);
        Assert.assertEquals(2, list.children.size());
        Assert.assertEquals("3.", list.children.get(0).marker);
        Assert.assertEquals("4.", list.children.get(1).marker);
        MarkdownBlock paren = single("1) 一", Kind.LIST);
        Assert.assertEquals("1)", paren.children.get(0).marker);
    }

    @Test
    public void shouldRequireSpaceAfterListMarker() {
        Assert.assertEquals("-不加空格", single("-不加空格", Kind.PARAGRAPH).joinedLines());
        Assert.assertEquals("1.不是列表", single("1.不是列表", Kind.PARAGRAPH).joinedLines());
    }

    /** 踩坑语料 ③（其二）：列表项内的续行缩进 → 剥列后并入项体段落。 */
    @Test
    public void shouldCollectIndentedContinuationInItem() {
        MarkdownBlock list = single(nl("- 项目甲", "  续行乙"), Kind.LIST);
        MarkdownBlock item = list.children.get(0);
        Assert.assertEquals(Kind.LIST_ITEM, item.kind);
        Assert.assertEquals(1, item.children.size());
        MarkdownBlock para = item.children.get(0);
        Assert.assertEquals(Kind.PARAGRAPH, para.kind);
        Assert.assertEquals(2, para.lines.size());
        Assert.assertEquals("项目甲", para.lines.get(0));
        Assert.assertEquals("续行乙", para.lines.get(1));
    }

    @Test
    public void shouldSupportNestedLists() {
        MarkdownBlock list = single(nl("- 甲", "  - 乙", "    - 丙"), Kind.LIST);
        MarkdownBlock outerItem = list.children.get(0);
        Assert.assertEquals(2, outerItem.children.size());
        Assert.assertEquals("甲", outerItem.children.get(0).joinedLines());
        MarkdownBlock midList = outerItem.children.get(1);
        Assert.assertEquals(Kind.LIST, midList.kind);
        MarkdownBlock midItem = midList.children.get(0);
        Assert.assertEquals(2, midItem.children.size());
        Assert.assertEquals("乙", midItem.children.get(0).joinedLines());
        MarkdownBlock innerList = midItem.children.get(1);
        Assert.assertEquals(Kind.LIST, innerList.kind);
        Assert.assertEquals("丙", innerList.children.get(0).children.get(0).joinedLines());
    }

    @Test
    public void shouldKeepSameIndentSiblingItemsInOneList() {
        MarkdownBlock list = single(nl("- 甲", "- 乙", "- 丙"), Kind.LIST);
        Assert.assertEquals(3, list.children.size());
    }

    @Test
    public void shouldEndListAtUnrelatedParagraph() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("- 甲", "", "乙段"));
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals(Kind.LIST, blocks.get(0).kind);
        Assert.assertEquals(1, blocks.get(0).children.size());
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(1).kind);
        Assert.assertEquals("乙段", blocks.get(1).joinedLines());
    }

    @Test
    public void shouldContinueLooseListAfterIndentedLine() {
        MarkdownBlock list = single(nl("- 甲", "", "  续", "", "- 乙"), Kind.LIST);
        Assert.assertEquals(2, list.children.size());
        MarkdownBlock first = list.children.get(0);
        Assert.assertEquals(1, first.children.size());
        Assert.assertEquals(2, first.children.get(0).lines.size());
        Assert.assertEquals("续", first.children.get(0).lines.get(1));
    }

    @Test
    public void shouldAcceptTabAfterMarker() {
        MarkdownBlock list = single("-" + (char) 0x09 + "甲", Kind.LIST);
        Assert.assertEquals("甲", list.children.get(0).children.get(0).joinedLines());
    }

    /** 钉死范围裁定：4 空格缩进不是缩进代码块，是段落续行（行首空格字面保留）。 */
    @Test
    public void shouldNotParseIndentedCode() {
        MarkdownBlock para = single(nl("正文", "    缩进行"), Kind.PARAGRAPH);
        Assert.assertEquals(2, para.lines.size());
        Assert.assertEquals("    缩进行", para.lines.get(1));
    }

    /** 钉死已裁简化：正文中序数不为 1 的有序起始不打断段落。 */
    @Test
    public void shouldNotInterruptParagraphWithOrderedOtherThanOne() {
        MarkdownBlock para = single(nl("正文", "2. 像是列表"), Kind.PARAGRAPH);
        Assert.assertEquals(2, para.lines.size());
        Assert.assertEquals("2. 像是列表", para.lines.get(1));
    }

    @Test
    public void shouldKeepEmptyListItem() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("-", "", "甲"));
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals(Kind.LIST, blocks.get(0).kind);
        Assert.assertTrue(blocks.get(0).children.get(0).children.isEmpty());
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(1).kind);
    }

    // ==================== 分隔线 ====================

    @Test
    public void shouldParseThematicBreakVariants() {
        String[] sources = {"---", "----", "***", "___", "- - -", "  ***  "};
        for (String source : sources) {
            single(source, Kind.THEMATIC_BREAK);
        }
    }

    @Test
    public void shouldNotConfuseBreakWithListOrEmphasis() {
        Assert.assertEquals("--", single("--", Kind.PARAGRAPH).joinedLines());
        Assert.assertEquals("***x", single("***x", Kind.PARAGRAPH).joinedLines());
        Assert.assertEquals(Kind.LIST, MarkdownBlockParser.parse("* x").get(0).kind);
    }

    @Test
    public void shouldBreakParagraphAroundThematicBreak() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("前文", "---", "后文"));
        Assert.assertEquals(3, blocks.size());
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(0).kind);
        Assert.assertEquals(Kind.THEMATIC_BREAK, blocks.get(1).kind);
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(2).kind);
    }

    // ==================== 段落与换行 ====================

    @Test
    public void shouldSplitParagraphsOnBlankLines() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("甲", "乙", "", "丙"));
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals(2, blocks.get(0).lines.size());
        Assert.assertEquals("丙", blocks.get(1).joinedLines());
    }

    /** 踩坑语料 ①：行尾两空格 = 硬换行；普通换行 = 软换行；互不误判。 */
    @Test
    public void shouldDetectHardBreakByTwoTrailingSpaces() {
        MarkdownBlock para = single(nl("行一  ", "行二", "行三"), Kind.PARAGRAPH);
        Assert.assertEquals(3, para.lines.size());
        Assert.assertEquals("行一", para.lines.get(0));
        Assert.assertEquals(2, para.hardBreaks.length);
        Assert.assertTrue(para.hardBreaks[0]);
        Assert.assertFalse(para.hardBreaks[1]);
    }

    /** 踩坑语料 ①（反斜杠变体 + 转义反斜杠不算）。 */
    @Test
    public void shouldDetectHardBreakByBackslashAndRejectEscaped() {
        MarkdownBlock back = single(nl("a" + BS, "b"), Kind.PARAGRAPH);
        Assert.assertTrue(back.hardBreaks[0]);
        Assert.assertEquals("a", back.lines.get(0));
        MarkdownBlock escaped = single(nl("a" + BS + BS, "b"), Kind.PARAGRAPH);
        Assert.assertFalse(escaped.hardBreaks[0]);
        Assert.assertEquals("a" + BS + BS, escaped.lines.get(0)); // 偶数反斜杠=转义反斜杠字面，非硬换行
        MarkdownBlock soft = single(nl("a", "b"), Kind.PARAGRAPH);
        Assert.assertFalse(soft.hardBreaks[0]);
    }

    @Test
    public void shouldNotConfuseHardAndSoftInMixedParagraph() {
        MarkdownBlock a = single(nl("甲  ", "乙", "丙"), Kind.PARAGRAPH);
        Assert.assertTrue(a.hardBreaks[0]);
        Assert.assertFalse(a.hardBreaks[1]);
        MarkdownBlock b = single(nl("甲", "乙  ", "丙"), Kind.PARAGRAPH);
        Assert.assertFalse(b.hardBreaks[0]);
        Assert.assertTrue(b.hardBreaks[1]);
    }

    @Test
    public void shouldDropTrailingSpacesFromStoredLines() {
        MarkdownBlock para = single("结尾空两格  ", Kind.PARAGRAPH);
        Assert.assertEquals("结尾空两格", para.lines.get(0));
        Assert.assertEquals(0, para.hardBreaks.length);
    }

    // ==================== 刻意不支持语法 = 字面输出 ====================

    @Test
    public void shouldKeepTableRowsLiteral() {
        MarkdownBlock para = single(nl("| a | b |", "|-|-|"), Kind.PARAGRAPH);
        Assert.assertEquals(2, para.lines.size());
        Assert.assertEquals("| a | b |", para.lines.get(0));
        Assert.assertEquals("|-|-|", para.lines.get(1));
    }

    @Test
    public void shouldKeepHtmlAndFootnoteLiteral() {
        MarkdownBlock para = single("<b>粗</b> [^1] 注", Kind.PARAGRAPH);
        Assert.assertEquals("<b>粗</b> [^1] 注", para.joinedLines());
    }

    /** 任务列表刻意不做：块层仍按普通列表处理，方括号文字字面（不生成勾选节点）。 */
    @Test
    public void shouldKeepTaskListMarkerLiteral() {
        MarkdownBlock list = single("- [ ] 任务", Kind.LIST);
        Assert.assertEquals("[ ] 任务", list.children.get(0).children.get(0).joinedLines());
    }

    // ==================== 输入归一与防御 ====================

    @Test
    public void shouldNormalizeCrlf() {
        MarkdownBlock para = single("a" + (char) 0x0D + (char) 0x0A + "b", Kind.PARAGRAPH);
        Assert.assertEquals(2, para.lines.size());
        Assert.assertEquals("a", para.lines.get(0));
    }

    @Test
    public void shouldReturnEmptyForBlankInput() {
        Assert.assertTrue(MarkdownBlockParser.parse(null).isEmpty());
        Assert.assertTrue(MarkdownBlockParser.parse("").isEmpty());
        String blanks = "  " + (char) 0x0A + (char) 0x0A + " " + (char) 0x09 + " ";
        Assert.assertTrue(MarkdownBlockParser.parse(blanks).isEmpty());
    }

    /** 防御：超深嵌套引用不炸栈，超限层按段落字面收拢。 */
    @Test
    public void shouldCapDeepNesting() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            builder.append("> ");
        }
        builder.append("深处");
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(builder.toString());
        Assert.assertEquals(1, blocks.size());
        Assert.assertEquals(Kind.QUOTE, blocks.get(0).kind);
    }

}
