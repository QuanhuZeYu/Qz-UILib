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
 * M10b/M10d「列表正文列」L2 锁（2026-09-05 裁定 2 + 同日「做全」追加裁定；规划 §二之七·续）。
 *
 * <p>判据核心：列表项名下<b>一切</b>视觉行的 leftInsetPx == 标记行 inset +
 * <b>独立量出</b>的推进宽——oracle 在测试内沿 {@code getListMarkerChain()} 逐元素走
 * {@code TextLayoutService.resolveAdvance} 求和取 ceil（与 M10c 聊天锁同款反自证纪律，
 * 不调 {@code MarkdownLineLayout} 的私有量法，不经 withLeftInsetPx 产物回推；
 * 事故档 ERROR-20260905 §八）。反向断言一律配正对照 + 反 ∅ 地板（断言到的行数/比较数下限，
 * 实测写进失败消息）。</p>
 *
 * <p><b>三级列硬值（锁⑤）</b>：派单假设的「上轮实测 13/25/38、真值 13/26/39」出自真机
 * 14px 档；headless 软件栅格器 @16px 逐码点实测 adv(「• 」)=13.789→14、adv(「  」)=14.222→15，
 * 故本档：代理列 14/29/43（旧 F2 前导文本代理，ceil 整串）vs 真值列 14/28/42（逐级
 * ceil(「• 」) 相加）。{@code adv('  ') != adv('• ')}——代理与度量确不相等，「每级少 1px」
 * 的模型缺陷在本档成立（方向：代理偏宽 1/1px 每级）。两档都写死进锁⑤，另以混级标记
 * （「• 」/「12. 」）杀「level × 固定步长」代理。</p>
 *
 * <p>装配纪律与 {@code MarkdownBlockGeometryTest} 同：复用 {@code LatexSoftwareRenderKit}
 * 共享装配，先喂字形再断度量，严禁另 new FontService。</p>
 */
public class MarkdownListContinuationLockTest {

    private static final char LF = (char) 0x0A;
    private static final int BASE = 16;
    private static final int WIDTH = 480;
    /** 16px 基准下「• 」实测推进宽（6.678+7.111=13.789 → ceil）。 */
    private static final int COL_BULLET = 14;
    /** 16px 基准下「12. 」实测推进宽（9.076+9.076+4.623+7.111=29.886 → ceil）。 */
    private static final int COL_ORDERED_12 = 30;

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
     * 独立 oracle（段）：段推进宽 = 逐码点 {@code resolveAdvance} 求和后 {@code ceil}。
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

    /**
     * 独立 oracle（链）：正文列 = 沿该行标记链<b>逐级</b> ceil(元素推进宽) 之和。
     * 「逐级」而非「双精度求和后一次取整」——父正文列是已渲染落定的整 px 几何，本级标记
     * 从父列起笔（@16 档两式同值，@14 档差 1px+；页面锁与 {@code @14 硬值} 见
     * {@code PlaygroundPageRegistryTest}）。
     */
    private static int oracleChainColumn(TextLayoutService service, MarkdownLayoutLine line) {
        int column = 0;
        for (TextSegment element : line.getListMarkerChain()) {
            column += oracleAdvance(service, element);
        }
        return column;
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

    private static List<MarkdownLayoutLine> logicalOf(String src) {
        return MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
    }

    /** 标记段文本（圆点+空格 / 源序号+定界+空格）：LIST 块首行 seg0（M10d 起裸标记）。 */
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

    /** 语料：4 个列表块（两软折标记行 + 懒延续 + 嵌套更深标记）+ 一个双行长段落（防泄漏对照）。 */
    private static final String LONG_MARK_A = "甲项首行" + repeat('甲', 30);      // 裸标记+34 字 → 必软折
    private static final String LAZY_A = "甲项缩进续行" + repeat('乙', 16);        // inset=0+14
    private static final String LONG_MARK_B = "乙项首行" + repeat('丙', 30);
    private static final String NESTED_C = "嵌套丙项" + repeat('丁', 30);          // 链长 2 → 列 28 且必软折
    private static final String NESTED_LAZY = "嵌套丙的懒延续" + repeat('戊', 14);
    private static final String ORDERED_D = "有序一" + repeat('己', 30);           // 必软折
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

    // ==================== 锁①：同块续行 inset == 标记行 inset + 独立量出的本级标记宽 ====================

    @Test
    public void continuationInsetEqualsIndependentlyMeasuredMarkerWidth() {
        String src = listCorpus();
        TextLayoutService service = assemble(src);
        List<MarkdownLayoutLine> logical = logicalOf(src);
        List<MarkdownLayoutLine> visual =
                MarkdownPainter.wrapLayoutLines(logical, service, WIDTH, BASE);

        Map<Integer, Integer> insetByBlock = new HashMap<Integer, Integer>();   // 块首视觉行 inset
        Map<Integer, Integer> chainLenByBlock = new HashMap<Integer, Integer>();
        Map<Integer, Integer> compared = new HashMap<Integer, Integer>();       // 参与比较的续行数
        int listVisualLines = 0;
        for (MarkdownLayoutLine line : visual) {
            if (line.getKind() != MarkdownLayoutLine.Kind.LIST) {
                continue;
            }
            listVisualLines++;
            int blockId = line.getBlockId();
            if (!chainLenByBlock.containsKey(Integer.valueOf(blockId))) {
                chainLenByBlock.put(Integer.valueOf(blockId),
                        Integer.valueOf(line.getListMarkerChain().size()));
            }
            Integer first = insetByBlock.get(Integer.valueOf(blockId));
            if (first == null) {
                insetByBlock.put(Integer.valueOf(blockId), Integer.valueOf(line.getLeftInsetPx()));
                continue; // 块首视觉行：保持祖先 inset（差值钉在每一条后续行上）
            }
            Integer seen = compared.get(Integer.valueOf(blockId));
            compared.put(Integer.valueOf(blockId),
                    Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
            int expected = first.intValue()
                    + oracleAdvance(service, markerOfFirstListLine(logical, blockId));
            Assert.assertEquals("块 " + blockId + " 的续行 leftInsetPx 必须 == 标记行 inset + "
                    + "独立量出的本级标记宽（expected=" + expected + "）",
                    expected, line.getLeftInsetPx());
        }
        Assert.assertTrue("LIST 视觉行地板 >= 6，实测 " + listVisualLines, listVisualLines >= 6);
        int totalCompared = 0;
        for (Integer v : compared.values()) {
            totalCompared += v.intValue();
        }
        Assert.assertTrue("参与比较的续行数地板 >= 6，实测 " + totalCompared, totalCompared >= 6);

        // M10d 新事实：嵌套项「标记行自身」落在父项正文列（旧代理世界它落在 0 列、靠 2 空格撑文本）。
        int parentBlock = -1;
        int nestedBlock = -1;
        for (Map.Entry<Integer, Integer> entry : chainLenByBlock.entrySet()) {
            if (entry.getValue().intValue() == 1 && markerOfFirstListLine(logical,
                    entry.getKey().intValue()).getText().equals("\u2022 ")) {
                parentBlock = entry.getKey().intValue();
            } else if (entry.getValue().intValue() == 2) {
                nestedBlock = entry.getKey().intValue();
            }
        }
        Assert.assertTrue("语料必须同时含 1 级圆点块与 2 级块（parent=" + parentBlock
                + " nested=" + nestedBlock + "）", parentBlock > 0 && nestedBlock > 0);
        int parentCol = oracleAdvance(service, markerOfFirstListLine(logical, parentBlock));
        Assert.assertEquals("嵌套项标记行的 inset == 父项正文列（祖先份额，沿链独立量出）",
                parentCol, insetByBlock.get(Integer.valueOf(nestedBlock)).intValue());
        Assert.assertEquals("嵌套项续行 inset == 父列 + 本级标记宽（= 自身沿链全额列）",
                parentCol + oracleAdvance(service, markerOfFirstListLine(logical, nestedBlock)),
                visualInsetOfLaterRowsThanFirst(visual, nestedBlock));

        // 防泄漏：非列表段落的续行不受影响（inset 恒 0，与改前逐位同）
        int plainVisual = 0;
        for (MarkdownLayoutLine line : visual) {
            String text = textOf(line.getSegments());
            if (line.getKind() == MarkdownLayoutLine.Kind.TEXT && text.startsWith("普通段落")) {
                Assert.assertEquals("无链段落的续行偏移不得被沾染: <" + text + ">",
                        0, line.getLeftInsetPx());
                Assert.assertTrue("无链 ⇒ 段落行链恒空", line.getListMarkerChain().isEmpty());
                plainVisual++;
            }
        }
        Assert.assertTrue("非列表对照行地板 >= 2（软折两行），实测 " + plainVisual, plainVisual >= 2);
    }

    private static int visualInsetOfLaterRowsThanFirst(List<MarkdownLayoutLine> visual, int blockId) {
        boolean seenFirst = false;
        for (MarkdownLayoutLine line : visual) {
            if (line.getBlockId() != blockId) {
                continue;
            }
            if (!seenFirst) {
                seenFirst = true;
                continue;
            }
            return line.getLeftInsetPx();
        }
        Assert.fail("块 " + blockId + " 没有第二条视觉行（样本失效，不许静默放行）");
        return -1;
    }

    // ==================== 锁②：偏移真被消费（断点必早于 inset=0） ====================

    @Test
    public void listColumnIsActuallyConsumedByWrapping() {
        // 同一段长文本 T：(a) 作为 inset=0 的 TEXT 行折 → 首视觉行码点数 a；
        // (b) 作为 "- 短标记行" 的懒延续行折（inset=正文列）→ 首视觉行码点数 b。b 必须 < a。
        String t = "续行长文本" + repeat('癸', 40);
        String src = joinLF("- 短标记行", "  " + t);
        TextLayoutService service = assemble(src, t);
        List<MarkdownLayoutLine> logical = logicalOf(src);
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
        // (a) 同文作 TEXT 零偏移行（公共 10 参构造器合成，无链 ⇒ 不经列表路）
        MarkdownLayoutLine plain = new MarkdownLayoutLine(MarkdownLayoutLine.Kind.TEXT,
                0, 555, Collections.singletonList(new TextSegment(t, base())),
                0, 0, 0, 0, 0, 0);
        List<MarkdownLayoutLine> plainWrapped =
                MarkdownPainter.wrapLayoutLines(Collections.singletonList(plain), service, WIDTH, BASE);
        String aText = textOf(plainWrapped.get(0).getSegments());
        int a = aText.codePointCount(0, aText.length());
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
        List<MarkdownLayoutLine> logical = logicalOf(src);
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
        // (2) 出图与接缝同数：逐视觉行 SEGMENTS.left == 该行 leftInsetPx（顺序配对）。
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
        Assert.assertEquals("标记行首视觉行仍保持纯引用 inset（= quoteLevel×step；1 级项祖先份额恒 0）",
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
            Assert.assertTrue("空圆点级不进链（链注水=第二套口径温床）: " + line,
                    line.getListMarkerChain().isEmpty());
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

    // ==================== 锁⑤（M10d 硬值）：三级列 = 沿链求和，非文本代理、非固定步长 ====================

    /**
     * 三级列硬值，两档语料：
     * ① 均匀圆点三层：真值 <b>14 / 28 / 42</b>（逐级 ceil(「• 」)=14 相加）；旧 F2
     *    文本代理档（整串 ceil）为 14/29/43——二、三级都钉死「不得等于」；
     * ② 混级标记（圆点 →「1. 」→圆点）：真值 <b>14 / 35 / 28</b>——均匀档 42 恰与
     *    「level×固定步长」同值，正因如此必须配混级档杀该代理（2×14=28≠35）。
     * 数字出处：headless 软件栅格器 @BASE=16 逐码点 resolveAdvance 实测（「1. 」=20.81→21）。
     */
    @Test
    public void threeLevelColumnsAreChainSumsNotFixedStepProxies() {
        String uniform = joinLF(
                "- 甲" + repeat('甲', 40),
                "  - 乙" + repeat('乙', 40),
                "    - 丙" + repeat('丙', 40));
        TextLayoutService service = assemble(uniform);
        List<MarkdownLayoutLine> uVisual =
                MarkdownPainter.wrapLayoutLines(logicalOf(uniform), service, WIDTH, BASE);
        int[] uCols = secondVisualInsetByBlockOrder(uVisual);
        Assert.assertEquals("均匀档必须恰 3 个列表块", 3, uCols.length);
        Assert.assertEquals("一级正文列（硬值）", COL_BULLET, uCols[0]);
        Assert.assertEquals("二级正文列（硬值=父列+本级）", 2 * COL_BULLET, uCols[1]);
        Assert.assertEquals("三级正文列（硬值=父列+本级）", 3 * COL_BULLET, uCols[2]);
        Assert.assertTrue("二级不得 = 旧文本代理 ceil(「  • 」)=29", uCols[1] != 29);
        Assert.assertTrue("三级不得 = 旧文本代理 ceil(「    • 」)=43", uCols[2] != 43);

        String mixed = joinLF(
                "- 甲" + repeat('甲', 40),
                "  1. 乙" + repeat('乙', 40),
                "    - 丙" + repeat('丙', 40));
        TextLayoutService mService = assemble(mixed);
        List<MarkdownLayoutLine> mLogical = logicalOf(mixed);
        List<MarkdownLayoutLine> mVisual =
                MarkdownPainter.wrapLayoutLines(mLogical, mService, WIDTH, BASE);
        Map<Integer, List<TextSegment>> chains = new HashMap<Integer, List<TextSegment>>();
        for (MarkdownLayoutLine line : mLogical) {
            if (line.getKind() == MarkdownLayoutLine.Kind.LIST
                    && !line.getListMarkerChain().isEmpty()) {
                chains.put(Integer.valueOf(line.getBlockId()), line.getListMarkerChain());
            }
        }
        Assert.assertEquals("混级档必须恰 3 个列表块，实测 " + chains.size(), 3, chains.size());
        // 结构前置（块树探针实测形态）：链长 1/2/2，二级链分别以「1. 」「• 」收尾
        int n1 = 0;
        int orderedL2 = 0;
        int bulletL2 = 0;
        for (List<TextSegment> chain : chains.values()) {
            if (chain.size() == 1) {
                n1++;
            } else if (chain.size() == 2 && chain.get(1).getText().equals("1. ")) {
                orderedL2++;
            } else if (chain.size() == 2) {
                bulletL2++;
            }
        }
        Assert.assertTrue("链形态前置失效（样本被解析成别的形态）: "
                + n1 + "/" + orderedL2 + "/" + bulletL2, n1 == 1 && orderedL2 == 1 && bulletL2 == 1);
        int c1 = 0, cOrdered = 0, cBullet = 0;
        for (Map.Entry<Integer, List<TextSegment>> entry : chains.entrySet()) {
            int blockId = entry.getKey().intValue();
            int laterInset = visualInsetOfLaterRowsThanFirst(mVisual, blockId);
            int chainOracle = 0;
            for (TextSegment element : entry.getValue()) {
                chainOracle += oracleAdvance(mService, element);
            }
            Assert.assertEquals("块 " + blockId + " 续行 inset == 独立沿链求和 " + chainOracle,
                    chainOracle, laterInset);
            // M10d 新事实：标记行落在父列（祖先份额 = 全额列 − 本级标记实测宽）
            Assert.assertEquals("块 " + blockId + " 标记行 inset == 祖先列",
                    chainOracle - oracleAdvance(mService,
                            markerOfFirstListLine(mLogical, blockId)),
                    firstVisualInset(mVisual, blockId));
            if (entry.getValue().size() == 1) {
                c1 = laterInset;
            } else if (entry.getValue().get(1).getText().equals("1. ")) {
                cOrdered = laterInset;
            } else {
                cBullet = laterInset;
            }
        }
        Assert.assertEquals("混级一级硬值", COL_BULLET, c1);
        Assert.assertEquals("混级「1. 」二级硬值（14+21）", COL_BULLET + 21, cOrdered);
        Assert.assertEquals("混级圆点二级硬值（14+14）", 2 * COL_BULLET, cBullet);
        Assert.assertTrue("「1. 」二级不得 = 2×固定步长 28（level×step 代理）: " + cOrdered,
                cOrdered != 2 * COL_BULLET);
        Assert.assertTrue("「1. 」二级不得 = 旧文本代理 ceil(「  1. 」)=36", cOrdered != 36);
    }

    /** 按 LIST 块首现序返回各块「第二条视觉行 inset」（= 全额正文列）；缺第二条即红（反 ∅）。 */
    private static int[] secondVisualInsetByBlockOrder(List<MarkdownLayoutLine> visual) {
        java.util.LinkedHashMap<Integer, int[]> pairs =
                new java.util.LinkedHashMap<Integer, int[]>();
        for (MarkdownLayoutLine line : visual) {
            if (line.getKind() != MarkdownLayoutLine.Kind.LIST
                    || line.getBlockId() == MarkdownLayoutLine.NO_BLOCK) {
                continue;
            }
            int[] pair = pairs.get(Integer.valueOf(line.getBlockId()));
            if (pair == null) {
                pairs.put(Integer.valueOf(line.getBlockId()), new int[] {line.getLeftInsetPx(), -1});
            } else if (pair[1] < 0) {
                pair[1] = line.getLeftInsetPx();
            }
        }
        int[] out = new int[pairs.size()];
        int i = 0;
        for (int[] pair : pairs.values()) {
            Assert.assertTrue("每块必须软折出第二条视觉行（反 ∅，样本失效先红）",
                    pair[1] >= 0);
            out[i++] = pair[1];
        }
        return out;
    }

    private static int firstVisualInset(List<MarkdownLayoutLine> visual, int blockId) {
        for (MarkdownLayoutLine line : visual) {
            if (line.getBlockId() == blockId) {
                return line.getLeftInsetPx();
            }
        }
        Assert.fail("块 " + blockId + " 无视觉行");
        return -1;
    }

    // ==================== 锁⑥（M10d「做全」）：项名下每个块都落在同一正文列 ====================

    /**
     * 松散项正身锁（块树探针实测形态钉死，不凭想象）：本仓解析器把「- a / 空行 / b / 空行 / c」
     * 并进<b>同一</b> blockId 的段落（b/c 是段内行——派单/规划文档说的「另起 blockId」不成立，
     * 冲突已报），真正另起 blockId 的是项内标题/项内引用/项内嵌套子项。本锁对两类都断言：
     * 含目标文本的视觉行 inset 恒 == 独立沿链求出的所属项正文列（引用行另叠引用份额、
     * 嵌套子项标记行吃祖先列）。地板：断言行数 >= 8、覆盖形态 >= 4。
     */
    @Test
    public void looseItemFollowUpBlocksAllGetTheirItemsContentColumn() {
        String src = joinLF(
                "- 甲项首段" + repeat('甲', 30),
                "",
                "  第二段正文" + repeat('乙', 30),
                "",
                "  第三段正文",
                "",
                "  #### 项内标题",
                "",
                "  > 项内引用行",
                "",
                "  1. 有序子项首段" + repeat('丙', 30),
                "     子项懒延续");
        TextLayoutService service = assemble(src);
        List<MarkdownLayoutLine> logical = logicalOf(src);
        List<MarkdownLayoutLine> visual =
                MarkdownPainter.wrapLayoutLines(logical, service, WIDTH, BASE);

        // ---- 结构前置（探针实测形态，失效先红）----
        int headBlock = -1;
        int headingBlock = -1;
        int quoteBlock = -1;
        int orderedBlock = -1;
        java.util.Set<Integer> mergedParaBlocks = new java.util.HashSet<Integer>();
        for (MarkdownLayoutLine line : logical) {
            String text = textOf(line.getSegments());
            if (text.startsWith("• 甲项首段")) {
                headBlock = line.getBlockId();
            } else if (text.startsWith("第二段正文") || text.startsWith("第三段正文")) {
                Assert.assertEquals("探针实测：空行后的同段行块身份仍是 LIST（段内行继承）",
                        MarkdownLayoutLine.Kind.LIST, line.getKind());
                mergedParaBlocks.add(Integer.valueOf(line.getBlockId()));
            } else if (text.startsWith("项内标题")) {
                headingBlock = line.getBlockId();
            } else if (text.startsWith("项内引用行")) {
                quoteBlock = line.getBlockId();
            } else if (text.startsWith("1. 有序子项首段") || text.startsWith("子项懒延续")) {
                orderedBlock = line.getBlockId();
            }
        }
        Assert.assertTrue("首段/标题/引用/子项块必须齐备 (b" + headBlock + "/b" + headingBlock
                + "/b" + quoteBlock + "/b" + orderedBlock + ")",
                headBlock > 0 && headingBlock > 0 && quoteBlock > 0 && orderedBlock > 0);
        Assert.assertEquals("探针实测：二/三段与首段同 blockId（解析器事实=派单前提的反例）",
                true, mergedParaBlocks.size() == 1
                        && mergedParaBlocks.contains(Integer.valueOf(headBlock)));
        Assert.assertTrue("标题/引用/子项必须另起 blockId（真缺口本体）",
                headingBlock != headBlock && quoteBlock != headBlock && orderedBlock != headBlock);

        // ---- 主断言：一切项内行 inset == 引用份额 + 独立沿链列（块首标记行再扣本级）----
        int col1 = 0;
        for (MarkdownLayoutLine line : visual) {
            if (line.getBlockId() == headBlock) {
                col1 = oracleChainColumn(service, line);
                break;
            }
        }
        Assert.assertEquals("首段所在链（[「• 」]）沿列硬值", COL_BULLET, col1);
        int assertedRows = 0;
        java.util.Set<Integer> blockHeadSeen = new java.util.HashSet<Integer>();
        for (MarkdownLayoutLine line : visual) {
            int blockId = line.getBlockId();
            if (blockId != headBlock && blockId != headingBlock && blockId != quoteBlock
                    && blockId != orderedBlock) {
                continue;
            }
            boolean firstOfBlock = blockHeadSeen.add(Integer.valueOf(blockId));
            int expected = oracleChainColumn(service, line);
            // 链列（未扣本级前）必须 > 0，否则正交断言空转；标记行首视觉行
            // 扣后正当地可以为 0（一级项），不得把它当空转抓。
            Assert.assertTrue("链列必须 > 0", expected > 0);
            if (firstOfBlock && line.getKind() == MarkdownLayoutLine.Kind.LIST
                    && !line.getListMarkerChain().isEmpty()) {
                // 本级宽恒取链尾元素（= 块首标记逻辑行 seg0 原文）——视觉行
                // seg0 可能已被折断成「•」残片（行尾空白丢弃，K3 既有行为），据其子
                // 宽反扣会得到 7 而非 14。L2 写入端量的是逻辑行 seg0，oracle 必须同源。
                expected -= oracleAdvance(service, line.getListMarkerChain()
                        .get(line.getListMarkerChain().size() - 1));
            }
            Assert.assertEquals("行「" + brief(textOf(line.getSegments())) + "」 inset 必须 == 引用份额+沿链列",
                    line.getQuoteLevel() * line.getIndentStepPx() + expected,
                    line.getLeftInsetPx());
            assertedRows++;
        }
        // 分项硬值点名（防「两边同错互相抵消」：列、份额、层级各钉各的数）
        // 标记行身份按「该块首条视觉行」判；seg0 会被折断成「1.」残片
        // （行尾空白丢弃，K3 既有行为，探针实测），不可据其文本认人。
        boolean orderedFirstSeen = false;
        for (MarkdownLayoutLine line : visual) {
            if (line.getBlockId() == headingBlock) {
                Assert.assertEquals("项内标题（另起块）inset == 列 14", COL_BULLET,
                        line.getLeftInsetPx());
            } else if (line.getBlockId() == quoteBlock) {
                Assert.assertEquals("项内引用（另起块）inset == 引用 8 + 列 14",
                        8 + COL_BULLET, line.getLeftInsetPx());
                Assert.assertEquals("引用步长仍取样式表 8", 8, line.getIndentStepPx());
                Assert.assertEquals("引用层级不被列表改动", 1, line.getQuoteLevel());
            } else if (line.getBlockId() == orderedBlock) {
                boolean markerRow = !orderedFirstSeen;
                orderedFirstSeen = true;
                Assert.assertEquals("有序子项" + (markerRow ? "标记行吃祖先列 14" : "续行吃全额列 35"),
                        markerRow ? COL_BULLET : COL_BULLET + 21, line.getLeftInsetPx());
            }
        }
        Assert.assertTrue("断言行数地板 >= 8，实测 " + assertedRows, assertedRows >= 8);
        // 覆盖形态计数：同块段行 / 另起标题 / 另起引用 / 嵌套子项（标记行+懒延续行）
        int kinds = 0;
        if (mergedParaBlocks.size() == 1) {
            kinds++;
        }
        if (headingBlock > 0 && headingBlock != headBlock) {
            kinds++;
        }
        if (quoteBlock > 0 && quoteBlock != headBlock) {
            kinds++;
        }
        if (orderedBlock > 0 && orderedBlock != headBlock) {
            kinds++;
        }
        Assert.assertTrue("覆盖形态地板 >= 4，实测 " + kinds, kinds >= 4);
    }

    private static String brief(String s) {
        return s.length() > 12 ? s.substring(0, 12) + "…" : s;
    }
}
