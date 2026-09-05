package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * L1 块身份行接缝（{@code toLayoutLines}，M7 方案乙）钉死测试——纯 JVM，零度量依赖。
 *
 * <p><b>核心不变量：接缝加身份、文本零改动。</b>每条反向断言按事故档
 * ERROR-20260905 第八节配「正对照 + 反空跑地板（命中数 >= N）」：</p>
 * <ul>
 *   <li>{@link #visibleTextIdenticalAcrossSeamsOnGateCorpus()}——门禁语料镜像逐条对拍：
 *       行路可见文本与段路一字不差、行界符数量一致（去界符字符序列 + 界符计数双保险）；</li>
 *   <li>{@link #quoteIndentMonotonicPerLevel()}——引用第 N 层左偏移随层数严格单调增；</li>
 *   <li>{@link #codeLinesShareBlockIdAndCarryBackdrop()} / {@link #thematicBreakLineAlwaysExists()}
 *       ——CODE 归组与横线恒成行；</li>
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
            {"N06", joinLF("上半句。", "---", "下半句。")},
            {"N07", "**粗** *斜* ~~删~~ ***粗斜*** 混排"},
            {"N08", "质能 $e=mc^2$ 行内混排 with 尾"},
            {"N09", "访问 [Qz 主页](https://example.com/qz) 详情"},
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

    /** 行路整串可见文本：行间以 LF 连接（空行即相邻两个 LF）。 */
    private static String linesVisible(List<MarkdownLayoutLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append(LF);
            }
            sb.append(flatVisible(lines.get(i).getSegments()));
        }
        return sb.toString();
    }

    // ==================== 不变量①：可见文本跨接缝逐字等值 ====================

    /**
     * 段流的「逻辑行序列化」：按 L2 splitLogicalLines 同规则切行（latex 原子随段整体入行；
     * 空文本段 = 行边界不携带字符；段内嵌 LF 逐处断行），行间以 LF 连接。
     * 用它对比行接缝产物，才能把「F6 占位段产空行」与「行接缝空行」对齐——纯字符拼接对比
     * 会把占位段的零字符差异漏掉，那是不合格的等值判据。
     */
    private static String segmentsSerializedByLines(List<TextSegment> segments) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (TextSegment segment : segments) {
            if (segment.isLatex()) {
                if (!first && sb.length() > 0 && sb.charAt(sb.length() - 1) == LF) {
                    // 新行起点已在换行时处理
                }
                sb.append("⟦").append(segment.getLatexSource()).append("⟧");
                continue;
            }
            String text = segment.getText();
            if (text.isEmpty()) {
                sb.append(LF); // 占位空段 = 空行（其自带一个行界符，与前一行分离）
                first = false;
                continue;
            }
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == LF) {
                    sb.append(text, start, i).append(LF);
                    start = i + 1;
                }
            }
            sb.append(text, start, text.length());
            first = false;
        }
        return sb.toString();
    }

    @Test
    public void visibleTextIdenticalAcrossSeamsOnGateCorpus() {
        String[][] corpus = corpus();
        int checked = 0;
        for (String[] entry : corpus) {
            MarkdownDocument doc = MarkdownDocument.parse(entry[1]);
            String viaSegments = segmentsSerializedByLines(
                    doc.toSegments(MarkdownStyleTable.defaults(), base()));
            String viaLines = linesVisible(
                    doc.toLayoutLines(MarkdownStyleTable.defaults(), base()));
            Assert.assertEquals(entry[0] + " 两接缝行序列化不等（可见文本或断行结构被改动）",
                    viaSegments, viaLines);
            checked++;
        }
        Assert.assertTrue("语料数地板（反空跑）：实测 " + checked + "，>=12", checked >= 12);
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

    @Test
    public void thematicBreakLineAlwaysExists() {
        MarkdownStyleTable noText = MarkdownStyleTable.defaults();
        noText.setThematicBreakText(""); // 既有旋钮，不新加
        String src = joinLF("上句", "---", "下句");
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
