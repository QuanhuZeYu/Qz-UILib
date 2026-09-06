package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * L1 块身份行接缝（{@code toLayoutLines}，M7 方案乙）钉死测试——纯 JVM，零度量依赖。
 *
 * <p><b>核心不变量：接缝加身份、文本改动只许一处。</b>C1a（2026-09-06 对齐裁定，CommonMark）
 * 把 F2「  」前导空格编码从段流里退役——两接缝标记段统一取 bareListMarker 单源，M10d 曾许可的
 * 「唯一文本差」不复存在：等值锁就地收紧为「两接缝可见文本严格逐字等 + LIST 行两侧均无前导空格」，
 * 旧四判据里「剥净前导后逐字等 / 差异必为偶数个前导空格 / 剥前导只许落 LIST 标记行」三条款随
 * 前导在源头的消亡作废（不再有「被许可的差」需要甄别，任何差都是坏差）。层级只由行接缝
 * {@code listMarkerChain} 沿链求和承载几何。每条反向断言按事故档
 * ERROR-20260905 第八节配「正对照 + 反空跑地板（命中数 >= N）」：</p>
 * <ul>
 *   <li>{@link #visibleTextIdenticalAcrossSeamsOnGateCorpus()}——门禁语料镜像逐条对拍：
 *       行路可见文本与段路逐行零差异等值，行数量一致；</li>
 *   <li>{@link #quoteIndentMonotonicPerLevel()}——引用第 N 层左偏移随层数严格单调增；</li>
 *   <li>{@link #codeLinesShareBlockIdAndCarryBackdrop()} / {@link #thematicBreakLineAlwaysExists()}
 *       ——CODE 归组与横线恒成行；</li>
 *   <li>C3b3（2026-09-06 拆除批）标题身份锁——{@link #headingLinesCarryKindAndLevel1To6()} /
 *       {@link #setextHeadingsCarryKindAndLevelInSeam()} /
 *       {@link #headingInsideQuoteKeepsHeadingKindAndQuoteGeometry()} /
 *       {@link #headingInsideListItemKeepsHeadingKindAndNonEmptyChain()} /
 *       {@link #withMethodsPreserveHeadingKindAndLevel()}：块模型的标题身份与级别不再在
 *       接缝丢弃，{@code Kind.HEADING} + {@code getHeadingLevel()} 随行；</li>
 *   <li>反向对照：无引用/无围栏/无分隔线文档对应字段恒零（证明字段不是常量假象）。</li>
 * </ul>
 */
public class MarkdownLayoutLinesTest {

    /** 行界符 LF（源码内避免字面换行歧义，与仓内 0x60 惯例同哲学）。 */
    private static final char LF = (char) 0x0A;

    private static String joinLF(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(LF);
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** 门禁语料镜像（文本只读复用；围栏反引号以 (char) 0x60 拼装）。 */
    private static String fence3() {
        char tick = (char) 0x60;
        return String.valueOf(tick) + tick + tick;
    }

    private static String[][] corpus() {
        return new String[][] {
            {"P01", "详见 http://qz.club/download?id=3&v=2 与 WWW.MINECRAFT.NET/download 资料"},
            {"P08", joinLF("- 甲", "  - 乙", "    - 丙")},
            {"P12", "> 引用的文字"},
            {"P16", joinLF("甲", "", "乙")},
            {"N01", joinLF("# 一级标题", "##### 五级标题")},
            {"N02", joinLF(fence3() + "java", "int x = 1; // **粗** $y$ > 引号 全字面", fence3())},
            {"N03", joinLF("> 甲", ">> 乙", ">>> 丙")},
            {"N05", joinLF("第一行  ", "第二行" + String.valueOf((char) 0x5C), "第三行")},
            {"N06", joinLF("上半句。", "", "---", "下半句。")},
            {"N07", "**粗** *斜* ~~删~~ ***粗斜*** 混排"},
            {"N08", "质能 $e=mc^2$ 行内混排 with 尾"},
            {"N09", "访问 [Qz 主页](https://example.com/qz) 详情"},
            // M10b 语料：LIST 身份行 + 懒延续行——两接缝可见文本必须仍逐字等值（几何走 px 不走文本）
            {"L01", joinLF("- 首行", "  缩进续行（懒延续）", "1. 有序项", "   有序项续行", "2) 右括号项")},
        };
    }

    private static TextStyle base() {
        TextStyle s = new TextStyle();
        s.setColor(0xFFFFFFFF);
        return s;
    }

    /** 段路整串可见文本（latex 段以方括号源占位，与门禁口径同）。 */
    private static String flatVisible(List<TextSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (TextSegment segment : segments) {
            sb.append(segment.isLatex()
                    ? "⟦" + segment.getLatexSource() + "⟧" : segment.getText());
        }
        return sb.toString();
    }


    // ==================== 不变量①：可见文本跨接缝逐字等值 ====================


    @Test
    public void visibleTextIdenticalAcrossSeamsOnGateCorpus() {
        String[][] corpus = corpus();
        int checked = 0;
        int nestedListLines = 0;
        for (String[] entry : corpus) {
            MarkdownDocument doc = MarkdownDocument.parse(entry[1]);
            List<String> viaSegments = segmentsByLineList(
                    doc.toSegments(MarkdownStyleTable.defaults(), base()));
            List<MarkdownLayoutLine> lines =
                    doc.toLayoutLines(MarkdownStyleTable.defaults(), base());
            Assert.assertEquals(entry[0] + " 两接缝行数不等（断行结构被改动）",
                    viaSegments.size(), lines.size());
            for (int i = 0; i < lines.size(); i++) {
                String segLine = viaSegments.get(i);
                MarkdownLayoutLine line = lines.get(i);
                String lineText = flatVisible(line.getSegments());
                // C1a（2026-09-06 对齐裁定）：M10d 曾许可的唯一文本差（段路 LIST 标记行带
                // F2「  」前导、行路剥净）随前导机制退役——段路不再产前导空格，两接缝可见
                // 文本必须严格逐字等，任何差（含前导差）当场红。
                Assert.assertEquals(entry[0] + " 行#" + i
                        + " 两接缝可见文本必须逐字相等（C1a 裸标记口径）", segLine, lineText);
                if (line.getKind() == MarkdownLayoutLine.Kind.LIST) {
                    // 「均无前导空格」钉：旧「成双空格/只落 LIST 标记行」条款的替代形——
                    // 两侧同钉裸体，段路与行路一律不许把层级写进文本（几何进文本=违仓规）。
                    Assert.assertFalse(entry[0] + " 行#" + i
                            + " 段路 LIST 行不得带前导空格（F2 前导已退役）: <" + segLine + ">",
                            segLine.startsWith(" "));
                    Assert.assertFalse(entry[0] + " 行#" + i
                            + " 行路 LIST 行不得带前导空格（几何进文本=违仓规）: <" + lineText + ">",
                            lineText.startsWith(" "));
                    // 层级走链不走文本：嵌套项（链 >=2 级）在场即是「等值锁真跑过旧 F2 行」的
                    // 正对照素材，计数进地板。
                    if (line.getListMarkerChain().size() >= 2) {
                        nestedListLines++;
                    }
                }
            }
            checked++;
        }
        Assert.assertTrue("语料数地板（反空跑）：实测 " + checked + "，>=13", checked >= 13);
        Assert.assertTrue("正对照地板（P08 二级+三级嵌套 LIST 行必须各命中 1 行，"
                + "证等值锁真跑过曾带 F2 前导的行）：实测 " + nestedListLines + "，>=2",
                nestedListLines >= 2);
    }

    /**
     * 段流 → 行文本列表：按 L2 splitLogicalLines 同规则切行（latex 原子整体入行；空文本段
     * = F6 占位空行；段内嵌 LF 逐处断行）。用它逐行对比行接缝产物，才能把「F6 占位段产空行」
     * 与「行接缝空行」对齐——纯字符拼接对比会把占位段的零字符差异漏掉，那是不合格的等值判据。
     */
    private static List<String> segmentsByLineList(List<TextSegment> segments) {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        StringBuilder current = new StringBuilder();
        for (TextSegment segment : segments) {
            if (segment.isLatex()) {
                current.append("\u27e6").append(segment.getLatexSource()).append("\u27e7");
                continue;
            }
            String text = segment.getText();
            if (text.isEmpty()) {
                out.add(current.toString()); // F6 占位空段 = 空行
                current.setLength(0);
                continue;
            }
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == LF) {
                    current.append(text, start, i);
                    out.add(current.toString());
                    current.setLength(0);
                    start = i + 1;
                }
            }
            current.append(text, start, text.length());
        }
        out.add(current.toString());
        return out;
    }

    // ==================== M10b 不变量⑥：LIST 身份与「同块首行才带标记段」 ====================

    /**
     * LIST 身份三钉（2026-09-05 裁定 2 的地基，L2 悬挂列全赖这两条）：
     * ① 带标记的列表行 kind=LIST（正对照：同源的普通段落行仍 TEXT）；
     * ② LIST 块的<b>首行 segments.get(0) 恰为独立标记段</b>（"• " / "3. "；C3b2 起有序恒为
     * 「续排序号 + 句点」，源右括号定界不进可见文本）；
     * ③ 同一 blockId 内<b>只有第一行</b>带标记段——懒延续/软折续行的 seg0 是正文，
     *    这正是 L2「按块首条 LIST 行的 seg0 量正文列」不会量错的依据。
     * 反 ∅ 地板：LIST 行命中 >= 4、比较块数 >= 3。
     */
    @Test
    public void listIdentityMarksOnlyFirstLineOfEachBlock() {
        // 末块与列表之间隔一个空行——不加空行时「普通段落」会被 CommonMark 懒延续吸进
        // 有序项段落（那也是 LIST 身份），正对照就失效了；这本身是 L1 行身份的正确行为。
        String src = joinLF("- 甲项首行", "  甲项缩进续行（懒延续）", "- 乙项", "  - 嵌套丙",
                "1. 有序一", "   有序一续行", "", "普通段落");
        List<MarkdownLayoutLine> lines =
                MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
        int listLines = 0;
        int blocks = 0;
        java.util.Set<Integer> seenBlocks = new java.util.HashSet<Integer>();
        java.util.Map<Integer, List<TextSegment>> chainByBlock =
                new java.util.HashMap<Integer, List<TextSegment>>();
        int chainedContinuations = 0;
        for (int i = 0; i < lines.size(); i++) {
            MarkdownLayoutLine line = lines.get(i);
            if (line.getKind() != MarkdownLayoutLine.Kind.LIST) {
                continue;
            }
            listLines++;
            boolean firstOfBlock = seenBlocks.add(Integer.valueOf(line.getBlockId()));
            // M10d：LIST 行必带非空链；链尾 = 本级标记段（同文同款，单源 bareListMarker）
            List<TextSegment> chain = line.getListMarkerChain();
            Assert.assertTrue("LIST 行必须携带非空标记链: " + line, !chain.isEmpty());
            if (firstOfBlock) {
                blocks++;
                // ② 块首 LIST 行的 seg0 = 裸标记段（圆点+空格 或 源序号+定界+空格），
                //    **无** F2 前导空格——几何不编码进可见文本（M10d 仓规钉）
                String seg0 = line.getSegments().get(0).getText();
                Assert.assertTrue("块首 LIST 行 seg0 应为裸标记段（无空格前导），实测 <" + seg0 + ">",
                        seg0.equals("• ") || seg0.matches("[0-9]+[.)] "));
                Assert.assertEquals("链尾元素文本 == seg0", seg0,
                        chain.get(chain.size() - 1).getText());
                Assert.assertEquals("链尾元素颜色 == seg0 颜色",
                        line.getSegments().get(0).getStyle().getColor(),
                        chain.get(chain.size() - 1).getStyle().getColor());
                chainByBlock.put(Integer.valueOf(line.getBlockId()), chain);
            } else {
                // ③ 同块后续 LIST 行不再带标记段，但必须继承同一条链（懒延续吃列的地基）
                String seg0 = line.getSegments().get(0).getText();
                Assert.assertFalse("同 blockId 非首行不得再带标记段: <" + seg0 + ">",
                        seg0.equals("• ") || seg0.matches("[0-9]+[.)] ")
                                || seg0.startsWith("• "));
                Assert.assertEquals("同块续行的链必须与块首逐元素等值",
                        chainByBlock.get(Integer.valueOf(line.getBlockId())), chain);
                chainedContinuations++;
            }
        }
        // 嵌套深度钉：「  - 嵌套丙」链长 2；「- 甲项首行 / - 乙项 / 1. 有序一」链长 1
        int twoLevel = 0;
        int oneLevel = 0;
        for (List<TextSegment> chain : chainByBlock.values()) {
            if (chain.size() == 2) {
                twoLevel++;
            } else if (chain.size() == 1) {
                oneLevel++;
            }
        }
        Assert.assertEquals("恰一条 2 级链（嵌套丙）", 1, twoLevel);
        Assert.assertTrue("1 级链 >= 3（甲/乙/有序一），实测 " + oneLevel, oneLevel >= 3);
        Assert.assertTrue("带链懒延续行地板 >= 2，实测 " + chainedContinuations,
                chainedContinuations >= 2);
        // ① 正对照：普通段落行必须仍 TEXT（不是全员 LIST 的假象）
        boolean plainIsText = false;
        for (MarkdownLayoutLine line : lines) {
            if (!line.getSegments().isEmpty()
                    && "普通段落".equals(line.getSegments().get(0).getText())) {
                Assert.assertEquals("普通段落行恒 TEXT", MarkdownLayoutLine.Kind.TEXT, line.getKind());
                plainIsText = true;
            }
        }
        Assert.assertTrue("正对照缺普通段落行（语料失效）", plainIsText);
        Assert.assertTrue("LIST 行地板 >= 4（4 项 + 2 续行，实测 " + listLines + "）", listLines >= 4);
        Assert.assertTrue("参与块数地板 >= 3（实测 " + blocks + "）", blocks >= 3);
    }

    /**
     * 圆点被样式表配成空串 ⇒ 列表行退回 TEXT 身份（没有标记段可量，悬挂列无从谈起）。
     * 反 ∅：同一语料在默认表下 LIST 行 >= 2（证明退 TEXT 不是恒真）。
     */
    @Test
    public void emptyBulletMarkerFallsBackToTextKind() {
        MarkdownStyleTable empty = MarkdownStyleTable.defaults();
        empty.setBulletMarker("");
        String src = joinLF("- 甲", "- 乙");
        List<MarkdownLayoutLine> lines =
                MarkdownDocument.parse(src).toLayoutLines(empty, base());
        for (MarkdownLayoutLine line : lines) {
            Assert.assertEquals("空圆点下无 LIST 身份可用，恒退 TEXT",
                    MarkdownLayoutLine.Kind.TEXT, line.getKind());
            Assert.assertEquals("退 TEXT 行不携带任何引用几何", 0, line.getLeftInsetPx());
            // M10d：空串圆点无可渲染标记 ⇒ 该级零宽、不进链（链不注水，几何恒等）
            Assert.assertTrue("空圆点级不得进链: " + line,
                    line.getListMarkerChain().isEmpty());
        }
        List<MarkdownLayoutLine> control = MarkdownDocument.parse(src)
                .toLayoutLines(MarkdownStyleTable.defaults(), base());
        int listLines = countKind(control, MarkdownLayoutLine.Kind.LIST);
        Assert.assertTrue("正对照：默认表下同一语料 LIST 行 >= 2，实测 " + listLines, listLines >= 2);
    }

    // ==================== 不变量②：引用第 N 层左偏移严格单调增 ====================

    @Test
    public void quoteIndentMonotonicPerLevel() {
        // 注意（L1 既有裁定，两路一致）：空行后再来 ">" 行会被 readQuote 合并为同一多段引用块
        // （CommonMark 语义），故 "> a\n>> b\n>>> c\n\n> d" 产 4 行、层级 1/2/3/1——
        // 恰好覆盖「升层严格单调 + 回层偏移还原」。
        String src = joinLF("> 一层", ">> 二层", ">>> 三层", "", "> 回一层");
        List<MarkdownLayoutLine> lines =
                MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
        Assert.assertEquals(4, lines.size());
        int[] levels = { lines.get(0).getQuoteLevel(), lines.get(1).getQuoteLevel(),
                lines.get(2).getQuoteLevel(), lines.get(3).getQuoteLevel() };
        Assert.assertArrayEquals(new int[] { 1, 2, 3, 1 }, levels);
        int[] insets = { lines.get(0).getLeftInsetPx(), lines.get(1).getLeftInsetPx(),
                lines.get(2).getLeftInsetPx(), lines.get(3).getLeftInsetPx() };
        Assert.assertTrue("随层数严格单调增: " + insets[0] + "<" + insets[1] + "<" + insets[2],
                insets[0] < insets[1] && insets[1] < insets[2]);
        Assert.assertEquals("回层后偏移还原", insets[0], insets[3]);
        Assert.assertEquals("inset = level * step",
                3 * lines.get(2).getIndentStepPx(), insets[2]);
        Assert.assertTrue("步长 > 0: " + lines.get(0).getIndentStepPx(),
                lines.get(0).getIndentStepPx() > 0);
        Assert.assertTrue("竖条宽 > 0: " + lines.get(0).getBarWidthPx(),
                lines.get(0).getBarWidthPx() > 0);
        int quoteLines = 0;
        for (MarkdownLayoutLine line : lines) {
            if (line.getQuoteLevel() > 0) {
                quoteLines++;
                Assert.assertNotEquals("引用行必带竖条色", 0, line.getAccentArgb());
            }
        }
        Assert.assertTrue("引用行数地板（>=4）: " + quoteLines, quoteLines >= 4);
    }

    @Test
    public void nonQuoteLinesCarryZeroQuoteGeometry() {
        List<MarkdownLayoutLine> lines = MarkdownDocument.parse(joinLF("普通段落", "- 项"))
                .toLayoutLines(MarkdownStyleTable.defaults(), base());
        Assert.assertTrue(lines.size() >= 2);
        for (MarkdownLayoutLine line : lines) {
            Assert.assertEquals(0, line.getQuoteLevel());
            Assert.assertEquals(0, line.getLeftInsetPx());
            Assert.assertEquals(0, line.getAccentArgb());
            Assert.assertEquals("非 CODE 行不得带底色", 0, line.getBackgroundArgb());
        }
    }

    // ==================== 不变量③：CODE 同块同 id + 底色 ====================

    @Test
    public void codeLinesShareBlockIdAndCarryBackdrop() {
        String src = joinLF(fence3() + "java", "alpha", "", "beta", fence3());
        List<MarkdownLayoutLine> lines =
                MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
        List<MarkdownLayoutLine> codeLines = new ArrayList<MarkdownLayoutLine>();
        for (MarkdownLayoutLine line : lines) {
            if (line.getKind() == MarkdownLayoutLine.Kind.CODE) {
                codeLines.add(line);
            }
        }
        Assert.assertEquals("围栏 3 源行（含空行）= 3 条 CODE 行: " + codeLines.size(),
                3, codeLines.size());
        int id = codeLines.get(0).getBlockId();
        Assert.assertTrue("blockId 有效: " + id, id > 0);
        for (MarkdownLayoutLine line : codeLines) {
            Assert.assertEquals("同围栏同 blockId", id, line.getBlockId());
            Assert.assertNotEquals("CODE 行必带块底色（样式表包内登记）", 0, line.getBackgroundArgb());
        }
        Assert.assertEquals("alpha", flatVisible(codeLines.get(0).getSegments()));
        Assert.assertEquals("空源行零段（不注水）", 0, codeLines.get(1).getSegments().size());
        Assert.assertEquals("beta", flatVisible(codeLines.get(2).getSegments()));
    }

    // ==================== 不变量④：分隔线恒成行 ====================

    /**
     * C3b2（2026-09-06 对齐裁定）后源文本改成空行隔开式：段落<b>紧邻</b>的 {@code ---} 按 CommonMark
     * 判 setext 下划线（不再是分隔线；该形态由 {@code MarkdownBlockParserTest} 的 setext 三例钉死），
     * 本例钉的不变量不变——「分隔线恒成行」，只把源里的 --- 移到块起点位。
     */
    @Test
    public void thematicBreakLineAlwaysExists() {
        MarkdownStyleTable noText = MarkdownStyleTable.defaults();
        noText.setThematicBreakText(""); // 既有旋钮，不新加
        String src = joinLF("上句", "", "---", "下句");
        Assert.assertEquals("无分隔线文档 0 横线行（反向对照）",
                0, countKind(MarkdownDocument.parse("纯文本").toLayoutLines(noText, base()),
                        MarkdownLayoutLine.Kind.THEMATIC_BREAK));
        List<MarkdownLayoutLine> withRule =
                MarkdownDocument.parse(src).toLayoutLines(noText, base());
        Assert.assertEquals("空文本旋钮下横线仍必须成行（几何由 kind 承载）",
                1, countKind(withRule, MarkdownLayoutLine.Kind.THEMATIC_BREAK));
        MarkdownLayoutLine rule = firstOfKind(withRule, MarkdownLayoutLine.Kind.THEMATIC_BREAK);
        Assert.assertEquals("恒零段（不产新可见文本）", 0, rule.getSegments().size());
        Assert.assertTrue("横线厚度登记 > 0: " + rule.getRuleThicknessPx(),
                rule.getRuleThicknessPx() > 0);
        Assert.assertNotEquals("横线色登记", 0, rule.getAccentArgb());
        List<MarkdownLayoutLine> dashed =
                MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
        MarkdownLayoutLine dashRule =
                firstOfKind(dashed, MarkdownLayoutLine.Kind.THEMATIC_BREAK);
        Assert.assertEquals("默认表下横线行的文本 = 既有 dash 旋钮值",
                36, flatVisible(dashRule.getSegments()).length());
        Assert.assertTrue("dash 全部是 '-'", flatVisible(dashRule.getSegments())
                .matches("-{36}"));
    }

    // ==================== C3b3 不变量⑦：标题块身份 + 级别进接缝 ====================

    /** 取指定文本前缀的首行（找不到即红，防空跑）。 */
    private static MarkdownLayoutLine firstLineStartingWith(List<MarkdownLayoutLine> lines,
            String prefix, String what) {
        for (MarkdownLayoutLine line : lines) {
            if (!line.getSegments().isEmpty()
                    && flatVisible(line.getSegments()).startsWith(prefix)) {
                return line;
            }
        }
        Assert.fail("缺 " + what + "（文本前缀 <" + prefix + ">）: " + lines);
        return null;
    }

    /**
     * ATX 1..6 级逐档钉：块模型级别原样进接缝（{@code Kind.HEADING} +
     * {@code getHeadingLevel()}=1..6），可见文本不变；正对照=纯段落行仍 TEXT 且级别 0。
     * 反向锁旧口径「标题/普通段恒 TEXT」（本批作废的类头注记）。
     */
    @Test
    public void headingLinesCarryKindAndLevel1To6() {
        StringBuilder sb = new StringBuilder();
        for (int level = 1; level <= 6; level++) {
            if (level > 1) {
                sb.append(LF).append(LF); // 空行隔开，保证是 6 个独立标题块
            }
            for (int h = 0; h < level; h++) {
                sb.append('#');
            }
            sb.append(" 标题").append(level);
        }
        List<MarkdownLayoutLine> lines =
                MarkdownDocument.parse(sb.toString()).toLayoutLines(MarkdownStyleTable.defaults(), base());
        int locked = 0;
        for (int level = 1; level <= 6; level++) {
            MarkdownLayoutLine line = firstLineStartingWith(lines, "标题" + level,
                    "ATX h" + level);
            Assert.assertEquals("ATX h" + level + " 行 kind 必须 HEADING",
                    MarkdownLayoutLine.Kind.HEADING, line.getKind());
            Assert.assertEquals("ATX h" + level + " 级别随行进接缝", level,
                    line.getHeadingLevel());
            Assert.assertTrue("标题行 blockId 有效", line.getBlockId() > 0);
            Assert.assertEquals("标题不进列表链", 0, line.getListMarkerChain().size());
            Assert.assertEquals("顶层标题不在引用内", 0, line.getQuoteLevel());
            locked++;
        }
        Assert.assertEquals("六档级别全配齐（反空跑）", 6, locked);
        // 正对照：段落行 TEXT、级别 0（级别不是全员常量）
        List<MarkdownLayoutLine> para = MarkdownDocument.parse(joinLF("普通段落", "# 二级", "#### 四级"))
                .toLayoutLines(MarkdownStyleTable.defaults(), base());
        MarkdownLayoutLine text = firstLineStartingWith(para, "普通段落", "段落行");
        Assert.assertEquals(MarkdownLayoutLine.Kind.TEXT, text.getKind());
        Assert.assertEquals("非标题行级别恒 0", 0, text.getHeadingLevel());
        Assert.assertEquals("ATX 闭序列（## 尾）级别仍按井号数", 2,
                firstLineStartingWith(MarkdownDocument.parse("## 尾 ##")
                        .toLayoutLines(MarkdownStyleTable.defaults(), base()), "尾",
                        "闭序列标题").getHeadingLevel());
    }

    /** setext 定级进接缝：{@code ===}→H1、{@code ---}→H2（与块模型/门禁 R 路同尺）。 */
    @Test
    public void setextHeadingsCarryKindAndLevelInSeam() {
        List<MarkdownLayoutLine> h1 = MarkdownDocument.parse(joinLF("甲行", "==="))
                .toLayoutLines(MarkdownStyleTable.defaults(), base());
        Assert.assertEquals("setext === 产 HEADING", MarkdownLayoutLine.Kind.HEADING,
                h1.get(0).getKind());
        Assert.assertEquals("=== → 1 级", 1, h1.get(0).getHeadingLevel());
        Assert.assertEquals("下划线行不产内容行", 1, h1.size());
        List<MarkdownLayoutLine> h2 = MarkdownDocument.parse(joinLF("乙行", "---"))
                .toLayoutLines(MarkdownStyleTable.defaults(), base());
        Assert.assertEquals("setext --- 产 HEADING", MarkdownLayoutLine.Kind.HEADING,
                h2.get(0).getKind());
        Assert.assertEquals("--- → 2 级", 2, h2.get(0).getHeadingLevel());
        // 反空跑：同宽度 --- 若被误判分隔线（旧歧义口径），这里就不是 HEADING ——两形态都钉
        List<MarkdownLayoutLine> mixed = MarkdownDocument.parse(joinLF("丙行", "==="))
                .toLayoutLines(MarkdownStyleTable.defaults(), base());
        Assert.assertNotEquals("setext 行不得退 CODE/TB", MarkdownLayoutLine.Kind.THEMATIC_BREAK,
                mixed.get(0).getKind());
    }

    /** 引用内标题：kind=HEADING 与 quoteLevel 正交并存，引用几何照常。 */
    @Test
    public void headingInsideQuoteKeepsHeadingKindAndQuoteGeometry() {
        List<MarkdownLayoutLine> lines = MarkdownDocument.parse(joinLF("> ### 引用内标题"))
                .toLayoutLines(MarkdownStyleTable.defaults(), base());
        MarkdownLayoutLine line = firstLineStartingWith(lines, "引用内标题", "引用内标题");
        Assert.assertEquals("引用不遮蔽标题身份（R 路同构）", MarkdownLayoutLine.Kind.HEADING,
                line.getKind());
        Assert.assertEquals(3, line.getHeadingLevel());
        Assert.assertEquals("引用层级独立正交", 1, line.getQuoteLevel());
        Assert.assertTrue("引用几何照给（步长/竖条/色）", line.getIndentStepPx() > 0
                && line.getBarWidthPx() > 0 && line.getAccentArgb() != 0);
        Assert.assertEquals("inset = quoteLevel × step",
                line.getQuoteLevel() * line.getIndentStepPx(), line.getLeftInsetPx());
    }

    /**
     * 列表项内标题（M10d「做全」的 kind 侧补钉）：kind=HEADING 与 listMarkerChain 非空
     * <b>正交并存</b>——链仍是正文列触发器（与 kind 无关），级别不因让位链而丢。
     */
    @Test
    public void headingInsideListItemKeepsHeadingKindAndNonEmptyChain() {
        String src = joinLF("- 首段", "", "  ## 项内标题");
        List<MarkdownLayoutLine> lines =
                MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
        MarkdownLayoutLine heading = firstLineStartingWith(lines, "项内标题", "项内标题");
        Assert.assertEquals(MarkdownLayoutLine.Kind.HEADING, heading.getKind());
        Assert.assertEquals(2, heading.getHeadingLevel());
        List<TextSegment> chain = heading.getListMarkerChain();
        Assert.assertTrue("项内标题必须带非空链（链与 kind 正交）: " + heading,
                !chain.isEmpty());
        Assert.assertEquals("链尾 = 本级圆点标记", "• ", chain.get(chain.size() - 1).getText());
        // 嵌套子项内标题：链长 2（父 + 子），级别照带
        String nested = joinLF("- 父段", "  - 子段", "", "    ### 深标题");
        List<MarkdownLayoutLine> nlines =
                MarkdownDocument.parse(nested).toLayoutLines(MarkdownStyleTable.defaults(), base());
        MarkdownLayoutLine deep = firstLineStartingWith(nlines, "深标题", "深链标题");
        Assert.assertEquals(MarkdownLayoutLine.Kind.HEADING, deep.getKind());
        Assert.assertEquals(3, deep.getHeadingLevel());
        Assert.assertEquals("子项内标题链 = 父级 + 子级", 2, deep.getListMarkerChain().size());
    }

    /**
     * 身份经三个 {@code with*} 拷贝法原样继承（D 约束：换段流/追加正文列/写块宽后标题行
     * 不得退化成无级别 TEXT）；{@code blank()} 与公共 10 参构造器恒 level=0；
     * 非 HEADING kind 经全参构造器传级别被归一 0（级别是标题专属属性，不外溢）。
     */
    @Test
    public void withMethodsPreserveHeadingKindAndLevel() {
        List<TextSegment> body =
                Collections.singletonList(new TextSegment("标题正文", base()));
        List<TextSegment> chain =
                Collections.singletonList(new TextSegment("• ", base()));
        // 包内 13 参全字段构造器（同包测试可用；公共 10 参签名不许动——A1 守卫钉）
        MarkdownLayoutLine heading = new MarkdownLayoutLine(MarkdownLayoutLine.Kind.HEADING,
                1, 7, body, 8, 8, 2, 0, 0x40FFFFFF, 0, 0, 4, chain);
        Assert.assertEquals(4, heading.getHeadingLevel());
        List<TextSegment> swapped =
                Collections.singletonList(new TextSegment("换了段流", base()));
        Assert.assertEquals("withSegments 保 kind", MarkdownLayoutLine.Kind.HEADING,
                heading.withSegments(swapped).getKind());
        Assert.assertEquals("withSegments 保级别", 4,
                heading.withSegments(swapped).getHeadingLevel());
        Assert.assertEquals("withLeftInsetPx 保级别（L2 折行追加正文列后身份不丢）", 4,
                heading.withLeftInsetPx(22).getHeadingLevel());
        Assert.assertEquals("withBlockContentWidthPx 保级别", 4,
                heading.withBlockContentWidthPx(120).getHeadingLevel());
        Assert.assertEquals("with* 链式连打仍保级别", 4, heading.withSegments(swapped)
                .withLeftInsetPx(22).withBlockContentWidthPx(120).getHeadingLevel());
        // 公共 10 参构造器与 blank()：恒 level 0
        Assert.assertEquals("公共 10 参构造器恒产级别 0", 0,
                new MarkdownLayoutLine(MarkdownLayoutLine.Kind.HEADING, 0, 1, body, 0, 0, 0, 0,
                        0, 0).getHeadingLevel());
        Assert.assertEquals("blank() kind=TEXT", MarkdownLayoutLine.Kind.TEXT,
                MarkdownLayoutLine.blank().getKind());
        Assert.assertEquals("blank() 级别恒 0", 0, MarkdownLayoutLine.blank().getHeadingLevel());
        // 非标题 kind 传级别 ⇒ 构造器归一 0（级别不外溢）
        Assert.assertEquals("TEXT 行传级别必须归零", 0,
                new MarkdownLayoutLine(MarkdownLayoutLine.Kind.TEXT, 0, 1, body, 0, 0, 0, 0,
                        0, 0, 0, 6, null).getHeadingLevel());
        Assert.assertEquals("CODE 行传级别必须归零", 0,
                new MarkdownLayoutLine(MarkdownLayoutLine.Kind.CODE, 0, 1, body, 0, 0, 0, 0,
                        0, 0, 0, 6, null).getHeadingLevel());
    }

    // ==================== 不变量⑤：F6 空行 = 零段零身份行 ====================

    @Test
    public void blankLineBetweenBlocksBecomesGeometryFreeLine() {
        String src = joinLF("甲", "", "乙");
        List<MarkdownLayoutLine> lines =
                MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
        Assert.assertEquals("甲/空行/乙 = 3 行（与段路 F6 显示行等值）", 3, lines.size());
        MarkdownLayoutLine blank = lines.get(1);
        Assert.assertEquals(0, blank.getSegments().size());
        Assert.assertEquals(MarkdownLayoutLine.NO_BLOCK, blank.getBlockId());
        Assert.assertEquals(0, blank.getQuoteLevel());
        Assert.assertTrue("前后两块 blockId 不同（块归属真实）",
                lines.get(0).getBlockId() != lines.get(2).getBlockId());
    }

    // ==================== 工具 ====================

    private static int countKind(List<MarkdownLayoutLine> lines, MarkdownLayoutLine.Kind kind) {
        int n = 0;
        for (MarkdownLayoutLine line : lines) {
            if (line.getKind() == kind) {
                n++;
            }
        }
        return n;
    }

    private static MarkdownLayoutLine firstOfKind(List<MarkdownLayoutLine> lines,
            MarkdownLayoutLine.Kind kind) {
        for (MarkdownLayoutLine line : lines) {
            if (line.getKind() == kind) {
                return line;
            }
        }
        Assert.fail("缺 kind=" + kind + ": " + lines);
        return null;
    }
}
