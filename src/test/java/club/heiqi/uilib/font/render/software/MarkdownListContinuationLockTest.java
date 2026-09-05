package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/**
 * M10b「列表续行对齐正文列」L2 锁（2026-09-05 裁定 2；规划 §二之七·续 M10 条目）。
 *
 * <p>判据核心：续行视觉行的 {@code leftInsetPx} == 标记行 {@code leftInsetPx} +
 * <b>独立量出</b>的标记段推进宽——oracle 在测试内逐码点走
 * {@code TextLayoutService.resolveAdvance} 自行求和取 {@code ceil}，不调被测写入路径，
 * 不共用 {@code MarkdownLineLayout} 的任何私有量法（反向断言配正对照 + 反 ∅ 地板，
 * 事故档 ERROR-20260905 §八）。正对照双保险：
 * ①「偏移真被消费」——同一段长文本，带正文列的续行断点必须早于 inset=0 的同文；
 * ②「非列表行零沾染」——普通段落/无围栏文档的续行偏移一字不动（防泄漏）。</p>
 *
 * <p>装配纪律与 {@code MarkdownBlockGeometryTest} 同：复用 {@code LatexSoftwareRenderKit}
 * 共享装配，先喂字形再断度量，严禁另 new FontService。</p>
 */
public class MarkdownListContinuationLockTest {

    private static final char LF = (char) 0x0A;
    private static final int BASE = 16;
    private static final int WIDTH = 480;

    private int savedWidthMissBudget = -1;

    @Before
    public void liftWidthMissBudget() {
        savedWidthMissBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }

    @After
    public void restoreWidthMissBudget() {
        FontConfig.widthCacheMissBudgetPerWindow = savedWidthMissBudget;
    }

    @AfterClass
    public static void releaseShared() {
        LatexSoftwareRenderKit.resetShared();
    }

    private static TextStyle base() {
        TextStyle s = new TextStyle();
        s.setColor(0xFFFFFFFF);
        return s;
    }

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

    private static String repeat(char ch, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    /** 预装配语料全部码点（与出图/门禁同纪律）。 */
    private static TextLayoutService assemble(String... sources) {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        List<TextSegment> all = new ArrayList<TextSegment>();
        for (String src : sources) {
            all.addAll(MarkdownDocument.parse(src)
                    .toSegments(MarkdownStyleTable.defaults(), base()));
        }
        LatexSoftwareRenderKit.assembleGlyphs(shared, all);
        return shared.service;
    }

    /**
     * 独立 oracle：段推进宽 = 逐码点 {@code resolveAdvance} 求和后 {@code ceil}。
     * 与 L2 写入路径无共享实现（不读 MarkdownLineLayout，也不经 withLeftInsetPx 产物回推）。
     */
    private static int oracleAdvance(TextLayoutService service, TextSegment segment) {
        double width = 0.0D;
        String text = segment.getText();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += service.resolveAdvance(cp, segment.getStyle(), BASE);
            i += Character.charCount(cp);
        }
        return (int) Math.ceil(width);
    }

    private static String textOf(List<TextSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (TextSegment segment : segments) {
            if (!segment.isLatex()) {
                sb.append(segment.getText());
            }
        }
        return sb.toString();
    }

    /** 语料：4 个列表块（两软折标记行 + 懒延续 + 嵌套更深标记）+ 一个双行长段落（防泄漏对照）。 */
    private static final String LONG_MARK_A = "甲项首行" + repeat('甲', 30);      // "• "+34 字 → 必软折
    private static final String LAZY_A = "甲项缩进续行" + repeat('乙', 16);        // inset=0+13
    private static final String LONG_MARK_B = "乙项首行" + repeat('丙', 30);
    private static final String NESTED_C = "嵌套丙项" + repeat('丁', 30);          // "  • "+34 字 → 更宽标记且软折
    private static final String NESTED_LAZY = "嵌套丙的懒延续" + repeat('戊', 14);
    private static final String ORDERED_D = "有序一" + repeat('己', 30);           // "1. " 必软折
    private static final String ORDERED_LAZY = "有序一懒延续" + repeat('庚', 16);

    private static String listCorpus() {
        return joinLF(
                "- " + LONG_MARK_A,
                "  " + LAZY_A,
                "- 乙项短",
                "  - " + NESTED_C,
                "    " + NESTED_LAZY,
                "1. " + ORDERED_D,
                "   " + ORDERED_LAZY,
                "", // 空行断开与列表的普通段落对照块
                "普通段落首行" + repeat('辛', 40),
                "普通段落次行" + repeat('壬', 24));
    }

    /** 标记段文本（"• " / "  • " / "1. "…）：LIST 块首行 seg0。 */
    private static TextSegment markerOfFirstListLine(List<MarkdownLayoutLine> logical, int blockId) {
        for (MarkdownLayoutLine line : logical) {
            if (line.getKind() == MarkdownLayoutLine.Kind.LIST
                    && line.getBlockId() == blockId && !line.getSegments().isEmpty()) {
                return line.getSegments().get(0);
            }
        }
        Assert.fail("块 " + blockId + " 找不到 LIST 首行");
        return null;
    }

    // ==================== 锁①：续行 inset == 标记行 inset + 独立量出的标记宽 ====================

    @Test
    public void continuationInsetEqualsIndependentlyMeasuredMarkerWidth() {
        String src = listCorpus();
        TextLayoutService service = assemble(src);
        List<MarkdownLayoutLine> logical =
                MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
        List<MarkdownLayoutLine> visual =
                MarkdownPainter.wrapLayoutLines(logical, service, WIDTH, BASE);

        Map<Integer, Integer> insetByBlock = new HashMap<Integer, Integer>();   // 块首视觉行 inset
        Map<Integer, Integer> compared = new HashMap<Integer, Integer>();       // 参与比较的视觉行数
        int listVisualLines = 0;
        for (MarkdownLayoutLine line : visual) {
            if (line.getKind() != MarkdownLayoutLine.Kind.LIST) {
                continue;
            }
            listVisualLines++;
            int blockId = line.getBlockId();
            Integer first = insetByBlock.get(Integer.valueOf(blockId));
            if (first == null) {
                insetByBlock.put(Integer.valueOf(blockId), Integer.valueOf(line.getLeftInsetPx()));
                continue; // 块首视觉行：保持原 inset（钉在锁③的差值里）
            }
            Integer seen = compared.get(Integer.valueOf(blockId));
            compared.put(Integer.valueOf(blockId),
                    Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
            int expected = first.intValue()
                    + oracleAdvance(service, markerOfFirstListLine(logical, blockId));
            Assert.assertEquals("块 " + blockId + " 的续行 leftInsetPx 必须 == 标记行 inset + "
                    + "独立量出的标记宽（expected=" + expected + "）",
                    expected, line.getLeftInsetPx());
        }
        Assert.assertTrue("LIST 视觉行地板 >= 6，实测 " + listVisualLines, listVisualLines >= 6);
        int totalCompared = 0;
        for (Integer v : compared.values()) {
            totalCompared += v.intValue();
        }
        Assert.assertTrue("参与比较的续行数地板 >= 6，实测 " + totalCompared,
                totalCompared >= 6);

        // 嵌套子项：正文列 > 父项，且恰等于自身标记宽（2 前导空格让「更宽」必然成立）
        int parentBlock = -1;
        int nestedBlock = -1;
        for (Integer id : insetByBlock.keySet()) {
            // 区分：标记文本以空格开头的块是嵌套项
            String marker = markerOfFirstListLine(logical, id.intValue()).getText();
            if (marker.startsWith("  ")) {
                nestedBlock = id.intValue();
            } else if (marker.startsWith("\u2022 ") && marker.length() == 2) {
                parentBlock = id.intValue();
            }
        }
        Assert.assertTrue("语料必须同时含父项与嵌套项块", parentBlock > 0 && nestedBlock > 0);
        int parentCol = oracleAdvance(service, markerOfFirstListLine(logical, parentBlock));
        int nestedCol = oracleAdvance(service, markerOfFirstListLine(logical, nestedBlock));
        Assert.assertTrue("嵌套正文列(" + nestedCol + ") 必须 > 父项正文列(" + parentCol + ")",
                nestedCol > parentCol);
        for (MarkdownLayoutLine line : visual) {
            if (line.getKind() == MarkdownLayoutLine.Kind.LIST && line.getBlockId() == nestedBlock
                    && line.getLeftInsetPx() != insetByBlock.get(Integer.valueOf(nestedBlock)).intValue()) {
                Assert.assertEquals("嵌套续行 inset 恰等于自身标记宽", nestedCol, line.getLeftInsetPx());
            }
        }

        // 防泄漏：非列表段落的续行不受影响（inset 恒 0，与改前逐位同）
        int plainVisual = 0;
        for (MarkdownLayoutLine line : visual) {
            String text = textOf(line.getSegments());
            if (line.getKind() == MarkdownLayoutLine.Kind.TEXT && text.startsWith("普通段落")) {
                Assert.assertEquals("非 LIST 续行偏移不得被沾染: <" + text + ">",
                        0, line.getLeftInsetPx());
                plainVisual++;
            }
        }
        Assert.assertTrue("非列表对照行地板 >= 2（软折两行），实测 " + plainVisual, plainVisual >= 2);
    }

    // ==================== 锁②：偏移真被消费（断点必早于 inset=0） ====================

    @Test
    public void listColumnIsActuallyConsumedByWrapping() {
        // 同一段长文本 T：(a) 作为 inset=0 的 TEXT 行折 → 首视觉行码点数 a；
        // (b) 作为 "- 短标记行" 的懒延续行折（inset=正文列）→ 首视觉行码点数 b。b 必须 < a。
        String t = "续行长文本" + repeat('癸', 40);
        String src = joinLF("- 短标记行", "  " + t);
        TextLayoutService service = assemble(src, t);
        List<MarkdownLayoutLine> logical =
                MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
        List<MarkdownLayoutLine> visual =
                MarkdownPainter.wrapLayoutLines(logical, service, WIDTH, BASE);
        MarkdownLayoutLine continuation = null;
        for (MarkdownLayoutLine line : visual) {
            if (textOf(line.getSegments()).startsWith("续行长文本")) {
                continuation = line;
                break;
            }
        }
        Assert.assertNotNull("懒延续行必须出现在视觉行里", continuation);
        Assert.assertTrue("懒延续行 inset 必须已被追加正文列: " + continuation.getLeftInsetPx(),
                continuation.getLeftInsetPx() > 0);
        // (a) 同文作 TEXT 零偏移行（公共 10 参构造器合成，不经 L1 列表路）
        MarkdownLayoutLine plain = new MarkdownLayoutLine(MarkdownLayoutLine.Kind.TEXT,
                0, 555, Collections.singletonList(new TextSegment(t, base())),
                0, 0, 0, 0, 0, 0);
        List<MarkdownLayoutLine> plainWrapped =
                MarkdownPainter.wrapLayoutLines(Collections.singletonList(plain), service, WIDTH, BASE);
        int a = plainWrapped.get(0).getSegments().isEmpty() ? 0
                : textOf(plainWrapped.get(0).getSegments()).codePointCount(
                        0, textOf(plainWrapped.get(0).getSegments()).length());
        int b = textOf(continuation.getSegments()).codePointCount(
                0, textOf(continuation.getSegments()).length());
        Assert.assertTrue("inset 生效时断点必须早于 inset=0（钉「偏移真被消费」）: b(" + b
                + ") < a(" + a + ")", b < a);
    }

    // ==================== 锁③：与引用竖条/横线/围栏底色正交（blockCommands 零改动） ====================

    @Test
    public void quoteBarsAndRulesStayOrthogonalToListColumn() {
        String src = joinLF("> - 甲项首行" + repeat('子', 30), ">   甲项缩进续行" + repeat('丑', 16));
        TextLayoutService service = assemble(src);
        List<MarkdownLayoutLine> logical =
                MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
        List<PaintCommand> commands =
                MarkdownPainter.toLayoutPaintCommands(logical, service, WIDTH, BASE);
        List<MarkdownLayoutLine> visual =
                MarkdownPainter.wrapLayoutLines(logical, service, WIDTH, BASE);
        int step = logical.get(0).getIndentStepPx();
        int bars = 0;
        int nonEmptyVisual = 0;
        for (MarkdownLayoutLine line : visual) {
            if (!line.getSegments().isEmpty()) {
                nonEmptyVisual++;
            }
        }
        // (1) 竖条正交：本语料恒一层引用 ⇒ 全部 BACKGROUND 竖条在 x=0 槽（l×step 口径），
        //     不随正文列右移——「列表偏移不改 blockCommands」的机器证据。
        List<PaintCommand> segmentCommands = new ArrayList<PaintCommand>();
        for (PaintCommand command : commands) {
            if (command.getType() == PaintCommandType.BACKGROUND) {
                Assert.assertEquals("引用竖条 x 恒取 l×step，不得吃列表正文列: left="
                        + command.getLeft(), 0, command.getLeft() % Math.max(1, step));
                Assert.assertEquals("一层引用竖条恒在 x=0 槽", 0, command.getLeft());
                bars++;
            } else if (command.getType() == PaintCommandType.SEGMENTS
                    && command.getSegments() != null && !command.getSegments().isEmpty()) {
                segmentCommands.add(command);
            }
        }
        Assert.assertTrue("竖条数地板 >= 2（每显示行一根，反 ∅），实测 " + bars, bars >= 2);
        // (2) 出图与接缝同数：逐视觉行 SEGMENTS.left == 该行 leftInsetPx（顺序配对，
        //     标记行软折的续行、懒延续行都在其中——含「块首视觉行只落一个 •」的折断形态）。
        Assert.assertEquals("SEGMENTS 命令数 == 非空视觉行数", nonEmptyVisual,
                segmentCommands.size());
        for (int i = 0, j = 0; i < visual.size(); i++) {
            if (visual.get(i).getSegments().isEmpty()) {
                continue;
            }
            Assert.assertEquals("视觉行#" + i + " 的 SEGMENTS.left 必须等于其 leftInsetPx",
                    visual.get(i).getLeftInsetPx(), segmentCommands.get(j++).getLeft());
        }
        // (3) 正文列差 = 独立量出的标记宽：首视觉行 inset 与其后最大 inset 之差。
        int firstInset = -1;
        int maxInset = -1;
        for (MarkdownLayoutLine line : visual) {
            if (line.getKind() != MarkdownLayoutLine.Kind.LIST) {
                continue;
            }
            if (firstInset < 0) {
                firstInset = line.getLeftInsetPx();
            }
            maxInset = Math.max(maxInset, line.getLeftInsetPx());
        }
        Assert.assertTrue("列差必须真的出现（max>first）: first=" + firstInset + " max=" + maxInset,
                maxInset > firstInset);
        Assert.assertEquals("引用内列表：续行左移量 == 独立量出的标记宽",
                oracleAdvance(service, markerOfFirstListLine(logical,
                        visual.get(0).getBlockId())),
                maxInset - firstInset);
        Assert.assertEquals("标记行首视觉行仍保持纯引用 inset（= quoteLevel×step）",
                logical.get(0).getQuoteLevel() * step, firstInset);
    }

    // ==================== 锁④：圆点空串 ⇒ 全链路零偏移（回退无副作用） ====================

    @Test
    public void emptyBulletMarkerProducesZeroInsetEverywhere() {
        MarkdownStyleTable empty = MarkdownStyleTable.defaults();
        empty.setBulletMarker("");
        String src = joinLF("- 甲项首行" + repeat('寅', 30), "  甲项缩进续行" + repeat('卯', 20));
        TextLayoutService service = assemble(src);
        List<MarkdownLayoutLine> visual = MarkdownPainter.wrapLayoutLines(
                MarkdownDocument.parse(src).toLayoutLines(empty, base()), service, WIDTH, BASE);
        for (MarkdownLayoutLine line : visual) {
            Assert.assertEquals("空圆点退 TEXT 后不得有任何悬挂偏移: " + line, 0,
                    line.getLeftInsetPx());
            Assert.assertEquals(MarkdownLayoutLine.Kind.TEXT, line.getKind());
        }
        // 正对照：同文默认表 ⇒ 至少一条续行 inset > 0（证明上面全 0 是旋钮的功劳不是恒真）
        List<MarkdownLayoutLine> control = MarkdownPainter.wrapLayoutLines(
                MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base()),
                service, WIDTH, BASE);
        boolean shifted = false;
        for (MarkdownLayoutLine line : control) {
            if (line.getLeftInsetPx() > 0) {
                shifted = true;
            }
        }
        Assert.assertTrue("正对照失效：默认表同文必须出现正偏移", shifted);
    }
}
