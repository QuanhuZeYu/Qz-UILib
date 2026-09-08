package club.heiqi.uilib.font.layout.markdown;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownBlock.Kind;

/**
 * 块级扫描器测试矩阵（M2）。每个块级构造至少一条用例，含规划外三条既有踩坑语料：
 * ① 行尾两空格硬换行 vs 普通换行不得互相误判；② 围栏代码块内粗体星号/美元符/尖角号一律
 * 字面（块模型钉死：CODE 正文原样、children 为空，永不进行内解析）；③ 嵌套引用两连 &gt; 与
 * 列表项内的续行缩进。TABLE 自 T1 起识别，旧出口暂态字面降级仍在本测试保留；
 * 刻意不支持语法（任务列表/HTML 内联/脚注/图片）的字面输出同测。
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

    /** C1a：块起点前导 1-3 空格 = 顶级列表项，前导缩进被剥除、不进项体文本
     *（旧「层级 = 前导空格 / 2 + baseLevel」口径作废）。 */
    @Test
    public void topLevelListMarkerAtOneToThreeSpacesStripsIndent() {
        MarkdownBlock list = single("  - x", Kind.LIST);
        Assert.assertFalse(list.ordered);
        MarkdownBlock item = list.children.get(0);
        Assert.assertEquals("-", item.marker);
        Assert.assertEquals("x", item.children.get(0).joinedLines());
        MarkdownBlock three = single("   - y", Kind.LIST);
        Assert.assertEquals("y", three.children.get(0).children.get(0).joinedLines());
        // 有序同理：「  12. z」= 顶级项，内容列自绝对列起算
        MarkdownBlock ord = single(nl("  12. z", "      续"), Kind.LIST);
        MarkdownBlock zItem = ord.children.get(0);
        Assert.assertEquals("12.", zItem.marker);
        Assert.assertEquals(nl("z", "续"), zItem.children.get(0).joinedLines());
        Assert.assertEquals(1, ord.children.size());
    }

    /** 内容列口径（CommonMark 例 296/297）：宽标记「10) 」内容列 = 4，嵌套要 4 空格；
     * 3 空格不属内容、又命中列表标记（打断段落）→ 本列表结束、另起一个列表。 */
    @Test
    public void nestingFollowsParentMarkerActualWidth() {
        MarkdownBlock list = single(nl("10) 甲", "    - 乙"), Kind.LIST);
        MarkdownBlock item = list.children.get(0);
        Assert.assertEquals(2, item.children.size());
        Assert.assertEquals(Kind.LIST, item.children.get(1).kind);
        Assert.assertEquals("乙",
                item.children.get(1).children.get(0).children.get(0).joinedLines());
        List<MarkdownBlock> two = MarkdownBlockParser.parse(nl("10) 甲", "   - 乙"));
        Assert.assertEquals(2, two.size());
        Assert.assertEquals(Kind.LIST, two.get(0).kind);
        Assert.assertTrue(two.get(0).ordered);
        Assert.assertEquals(1, two.get(0).children.size());
        Assert.assertEquals(Kind.LIST, two.get(1).kind);
        Assert.assertFalse(two.get(1).ordered);
        Assert.assertEquals(1, two.get(1).children.size());
    }

    /** 续行 < 内容列且非块标记 = CommonMark 惰性续行（例 291）：仍属该项段落、行首空白
     * 折叠；「空行 + < 内容列」才是真项结束（列表断块、行落顶层段落）。 */
    @Test
    public void continuationBelowContentColumnIsLazyThenEndsAfterBlank() {
        MarkdownBlock list = single(nl("12. 甲", "   续行"), Kind.LIST);
        Assert.assertEquals(1, list.children.size());
        MarkdownBlock para = list.children.get(0).children.get(0);
        Assert.assertEquals(2, para.lines.size());
        Assert.assertEquals("续行", para.lines.get(1)); // 折叠剥除 3 前导空格
        List<MarkdownBlock> split = MarkdownBlockParser.parse(nl("12. 甲", "", "   续行"));
        Assert.assertEquals(2, split.size());
        Assert.assertEquals(Kind.LIST, split.get(0).kind);
        Assert.assertEquals(1, split.get(0).children.get(0).children.get(0).lines.size());
        Assert.assertEquals(Kind.PARAGRAPH, split.get(1).kind);
        Assert.assertEquals("续行", split.get(1).joinedLines());
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

    // —— 缩进代码块（C1a，2026-09-06 CommonMark 对齐裁定；旧「4 空格缩进不是缩进代码块」
    //    裁定作废，本节按 CommonMark 0.30 §4.4 例 110/111/113/114/116/117 重钉）——

    /** 块起点 ≥4 前导空格 = 缩进代码块；剥 4 后为内容，info 恒空。 */
    @Test
    public void indentedCodeAtBlockStartBecomesCodeBlock() {
        MarkdownBlock code = single("    foo", Kind.CODE);
        Assert.assertEquals(1, code.lines.size());
        Assert.assertEquals("foo", code.lines.get(0));
        Assert.assertEquals("", code.info);
        Assert.assertTrue(code.children.isEmpty());
    }

    /** 6 空格行剥 4 留 2（多余缩进属于代码内容，例 116）。 */
    @Test
    public void indentedCodeStripsOnlyFourColumns() {
        MarkdownBlock code = single(nl("      foo", "    bar"), Kind.CODE);
        Assert.assertEquals(2, code.lines.size());
        Assert.assertEquals("  foo", code.lines.get(0));
        Assert.assertEquals("bar", code.lines.get(1));
    }

    /** 块内行内 markdown 不解析：与围栏 CODE 同一字面口径。 */
    @Test
    public void indentedCodeContentStaysLiteral() {
        MarkdownBlock code = single(nl("    **粗** [a](http://x.y) $x$"), Kind.CODE);
        Assert.assertEquals("**粗** [a](http://x.y) $x$", code.joinedLines());
    }

    /** 连续 ≥4 空格行并入同块；块间空行属于块；尾随空行不入块（例 111/117）。 */
    @Test
    public void indentedCodeChunksMergeAcrossInteriorBlanks() {
        MarkdownBlock one = single(nl("    a", "    b"), Kind.CODE);
        Assert.assertEquals(nl("a", "b"), one.joinedLines());
        MarkdownBlock merged = single(nl("    a", "", "    b"), Kind.CODE);
        Assert.assertEquals(nl("a", "", "b"), merged.joinedLines());
        // 空行后被 <4 非空行中断：尾随空行不入块，归下一块的块前空行（F6）
        List<MarkdownBlock> split = MarkdownBlockParser.parse(nl("    a", "", "bar"));
        Assert.assertEquals(2, split.size());
        Assert.assertEquals("a", split.get(0).joinedLines());
        Assert.assertEquals(Kind.PARAGRAPH, split.get(1).kind);
        Assert.assertEquals(1, split.get(1).blanksBefore);
    }

    /** 首个前导 <4 的非空行即刻结束缩进代码块（例 114）。 */
    @Test
    public void lessIndentedNonBlankLineEndsIndentedCode() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("    foo", "bar"));
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals(Kind.CODE, blocks.get(0).kind);
        Assert.assertEquals("foo", blocks.get(0).joinedLines());
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(1).kind);
        Assert.assertEquals("bar", blocks.get(1).joinedLines());
    }

    /** 缩进代码不得中断段落：段落已开始后 4+ 空格行仍是续行，且行首空白折叠剥除
     *（旧「字面保留」裁定作废，例 113）。 */
    @Test
    public void indentedLineAfterParagraphIsFoldedContinuation() {
        MarkdownBlock para = single(nl("正文", "    缩进行"), Kind.PARAGRAPH);
        Assert.assertEquals(2, para.lines.size());
        Assert.assertEquals("缩进行", para.lines.get(1));
    }

    /** 段落经空行结束后，4+ 空格行才成为缩进代码块（块上下文边界）。 */
    @Test
    public void blankThenIndentedLineBecomesCodeBlock() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("甲", "", "    x"));
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(0).kind);
        Assert.assertEquals(Kind.CODE, blocks.get(1).kind);
        Assert.assertEquals("x", blocks.get(1).joinedLines());
        Assert.assertEquals(1, blocks.get(1).blanksBefore);
    }

    /** 块起点 4 空格 + 列表标记 = 缩进代码字面（M5 F2「深缩进独立列表」裁定作废）；
     * 5 空格剥 4 留 1。 */
    @Test
    public void deepIndentedListMarkerIsCodeLiteral() {
        MarkdownBlock code = single("    - deep", Kind.CODE);
        Assert.assertEquals("- deep", code.joinedLines());
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse("     - deep");
        Assert.assertEquals(Kind.CODE, blocks.get(0).kind);
        Assert.assertEquals(" - deep", blocks.get(0).joinedLines());
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

    /**
     * C3b2（2026-09-06 对齐裁定）重定：旧「段落紧邻的 {@code ---} 恒为分隔线」是 MC 小众口径，
     * 按 CommonMark 拆除——段落<b>紧邻</b>的下划线行判 setext（见下面 setext 三例），要被
     * 分隔线切开必须空行隔开（实证 commonmark-java 0.21）。
     */
    @Test
    public void shouldBreakParagraphAroundThematicBreak() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("前文", "", "---", "后文"));
        Assert.assertEquals(3, blocks.size());
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(0).kind);
        Assert.assertEquals(Kind.THEMATIC_BREAK, blocks.get(1).kind);
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(2).kind);
    }

    // ==================== setext 标题（C3b2 新增，2026-09-06 对齐裁定） ====================

    /** {@code ===} 下划线 → H1；{@code ---} 下划线 → H2；下划线行本身不产内容行。 */
    @Test
    public void setextUnderlinePromotesParagraphToHeading() {
        List<MarkdownBlock> blocks = MarkdownBlockParser.parse(nl("上半句。", "---", "下半句。"));
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals(Kind.HEADING, blocks.get(0).kind);
        Assert.assertEquals(2, blocks.get(0).level);
        Assert.assertEquals("上半句。", blocks.get(0).text);
        Assert.assertEquals(Kind.PARAGRAPH, blocks.get(1).kind);
        MarkdownBlock h1 = single(nl("甲行", "======"), Kind.HEADING);
        Assert.assertEquals(1, h1.level);
        Assert.assertEquals("甲行", h1.text);
        // 单个字符也是下划线（主流实证："a\n=" → H1、"a\n-" → H2）
        Assert.assertEquals(1, single(nl("甲", "="), Kind.HEADING).level);
        Assert.assertEquals(2, single(nl("甲", "-"), Kind.HEADING).level);
    }

    /** 多行段落整体升格：正文按软换行以 '\n' 连接（与 ATX 同一标题通道）。 */
    @Test
    public void setextHeadingTakesWholeMultiLineParagraph() {
        MarkdownBlock h = single(nl("甲", "乙", "---"), Kind.HEADING);
        Assert.assertEquals(2, h.level);
        Assert.assertEquals(nl("甲", "乙"), h.text);
    }

    /** 块起点/空行后的 {@code ===}/{@code ---} 仍是分隔线或段落，不升格；夹空白不是下划线。 */
    @Test
    public void setextNeedsAdjacentParagraphAndPureUnderlineRun() {
        List<MarkdownBlock> head = MarkdownBlockParser.parse(nl("---", "下半句。"));
        Assert.assertEquals(Kind.THEMATIC_BREAK, head.get(0).kind);
        Assert.assertEquals(Kind.PARAGRAPH, head.get(1).kind);
        // 空行隔开 → 不升格（下划线属下一块起点）
        List<MarkdownBlock> afterBlank = MarkdownBlockParser.parse(nl("甲", "", "---", "乙"));
        Assert.assertEquals(Kind.PARAGRAPH, afterBlank.get(0).kind);
        Assert.assertEquals(Kind.THEMATIC_BREAK, afterBlank.get(1).kind);
        Assert.assertEquals(Kind.PARAGRAPH, afterBlank.get(2).kind);
        // 文档以 === 开头 = 段落字面；"= =" 夹空白按段落续行（主流实证同形）
        Assert.assertEquals("===", single("===", Kind.PARAGRAPH).joinedLines());
        MarkdownBlock spaced = single(nl("甲", "= ="), Kind.PARAGRAPH);
        Assert.assertEquals(nl("甲", "= ="), spaced.joinedLines());
        // 标题（非段落）紧邻 --- 不升格：ATX 后跟下划线行 = 分隔线
        List<MarkdownBlock> afterHeading = MarkdownBlockParser.parse(nl("## 标题", "---", "正文"));
        Assert.assertEquals(Kind.HEADING, afterHeading.get(0).kind);
        Assert.assertEquals(Kind.THEMATIC_BREAK, afterHeading.get(1).kind);
        // ≥4 前导空格的 --- 不作下划线（段落续行折叠，实证同形）
        MarkdownBlock deep = single(nl("甲", "    ---"), Kind.PARAGRAPH);
        Assert.assertEquals(nl("甲", "---"), deep.joinedLines());
    }

    /** setext 标题走与 ATX 同一条样式通道：级别决定 heading 旋钮档位（H1=delta[0]/H2=delta[1]）。 */
    @Test
    public void setextAndAtxShareTheSameHeadingChannel() {
        MarkdownStyleTable table = MarkdownStyleTable.defaults();
        table.setDefaultFontSizePx(16);
        table.setHeadingFontSizeDeltaPx(1, 10);
        table.setHeadingFontSizeDeltaPx(2, 4);
        TextStyle base = new TextStyle();
        base.setColor(0xFFFFFFFF);
        int setSize = MarkdownDocument.parse(nl("甲", "===")).toSegments(table, base).get(0)
                .getStyle().getFontSizePx();
        int atxSize = MarkdownDocument.parse("# 甲").toSegments(table, base).get(0)
                .getStyle().getFontSizePx();
        Assert.assertEquals("setext H1 与 ATX H1 字号同档", atxSize, setSize);
        int h2Size = MarkdownDocument.parse(nl("甲", "---")).toSegments(table, base).get(0)
                .getStyle().getFontSizePx();
        Assert.assertTrue("setext H2 走 delta[1] 档（实测 " + h2Size + "）", h2Size > 0
                && h2Size != atxSize);
    }

    /**
     * C4 N2 收紧锁（2026-09-06）：setext 下划线行不得是惰性续行（CommonMark 同款规则，
     * 原「类头已裁简化表」登记的偏离已删除）。{@code > 甲} + 惰性 {@code ===} 与
     * {@code - 甲} + 惰性 {@code ===} 必须保持段内字面；正对照钉「字面化」不是恒字面——
     * 自带 '>' 标记的下划线行与顶层段落紧邻下划线照常升格（X11 / setext 三例同形）。
     */
    @Test
    public void setextUnderlineMustNotBeLazyContinuation() {
        // 引用侧：惰性 === 留在段内字面
        MarkdownBlock quote = single(nl("> 甲", "==="), Kind.QUOTE);
        Assert.assertEquals(1, quote.children.size());
        MarkdownBlock para = quote.children.get(0);
        Assert.assertEquals(Kind.PARAGRAPH, para.kind);
        Assert.assertEquals(nl("甲", "==="), para.joinedLines());
        // 惰性行后继续有普通续行，仍不升格
        Assert.assertEquals(nl("甲", "===", "乙"),
                single(nl("> 甲", "===", "乙"), Kind.QUOTE).children.get(0).joinedLines());
        // 列表侧：低于内容列的惰性 === 同样字面
        MarkdownBlock list = single(nl("- 甲", "==="), Kind.LIST);
        MarkdownBlock itemPara = list.children.get(0).children.get(0);
        Assert.assertEquals(Kind.PARAGRAPH, itemPara.kind);
        Assert.assertEquals(nl("甲", "==="), itemPara.joinedLines());
        // 惰性 === 不得「吞掉」下划线后的正常行继续聚段（行为 = 普通段落续行）
        MarkdownBlock mixed = single(nl("> 甲", "===", "> 乙"), Kind.QUOTE);
        Assert.assertEquals(1, mixed.children.size());
        Assert.assertEquals(nl("甲", "===", "乙"), mixed.children.get(0).joinedLines());
        // 正对照 1：自带 '>' 的下划线行（非惰性）照常升格
        MarkdownBlock marked = single(nl("> 甲", "> ==="), Kind.QUOTE);
        Assert.assertEquals(Kind.HEADING, marked.children.get(0).kind);
        Assert.assertEquals(1, marked.children.get(0).level);
        // 正对照 2：顶层段落紧邻下划线照常升格（非惰性路径未受影响）
        MarkdownBlock top = single(nl("甲", "==="), Kind.HEADING);
        Assert.assertEquals(1, top.level);
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
        String source = nl("| a | b |", "|-|-|");
        single(source, Kind.TABLE);
        // T1 识别已升级；旧出口使用的同源降级树必须仍保持原两行段落。
        List<MarkdownBlock> literal = MarkdownBlockParser.parseLiteral(source, null);
        Assert.assertEquals(1, literal.size());
        MarkdownBlock para = literal.get(0);
        Assert.assertEquals(Kind.PARAGRAPH, para.kind);
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
