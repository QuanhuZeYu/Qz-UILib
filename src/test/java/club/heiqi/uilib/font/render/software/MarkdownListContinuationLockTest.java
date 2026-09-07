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
 * <p><b>三级列（锁⑤）——C9 起为「同 JVM 独立测量」形，不再写死 Windows 读数</b>：原硬值
 * 14/28/42 与代理 29/43 出自 Windows Dialog 一次实测（@16px adv(「• 」)=13.789→14）；
 * Linux CI（DejaVu）同式实测 17/34/51、代理 25，Windows 字面量即 8 例误红之一。现全部
 * 经 {@link #measuredColumn} 当场量：真值列 = 逐级 ceil(本级标记宽) 之和；代理列 =
 * ceil(整串前导空格+标记)（旧 F2 文本代理口径，floor/ceil 各自复现）。两式在 Windows、
 * Verdana、SimSun、DejaVu 四组度量下都互不相等（temp 探针 2026-09-07：Win 29≠28、
 * Verdana 31≠34、SimSun 30≠32），「每级差 1px」的模型缺陷在四档都成立；混级标记
 * （「• 」/「1. 」）继续杀「level × 固定步长」代理。</p>
 *
 * <p><b>C9 输入侧自适应</b>：一切「必须软折出第二条视觉行」的锁（②⑤⑥与①的计数地板），
 * 语料字数不再按 Windows 度量写死（Linux 下 CJK advance 收窄 → 不折行 → 反 ∅ 地板与
 * 断点前移判据整批空转/误红），改为本 JVM 实测单字 advance 后按 {@link #wrapCountChars}
 * 推导「必然折行 + 第二行 ≥2 字」的字数；折行容器宽按 {@link #narrowWrapWidth} 由
 * 3 字宽 + 实测列构造，b&lt;a 由构造必然成立。地板数值保留（= 构造推导值），语义不变。</p>
 *
 * <p>装配纪律与 {@code MarkdownBlockGeometryTest} 同：复用 {@code LatexSoftwareRenderKit}
 * 共享装配，先喂字形再断度量，严禁另 new FontService。</p>
 */
public class MarkdownListContinuationLockTest {

    private static final char LF = (char) 0x0A;
    private static final int BASE = 16;
    private static final int WIDTH = 480;
    /** 语料用 CJK 码点集（自适应字数前必须先把这些字形装配进度量表）。 */
    private static final String CORPUS_CHARS =
            "甲乙丙丁戊己庚辛壬癸寅卯续行首项缩进嵌懒序短标记段落普通文长文本字";

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

    /** C9：独立量出某标记文本在 BASE 档的整列推进宽（逐码点 resolveAdvance 求和取 ceil）。 */
    private static int measuredColumn(TextLayoutService svc, String markerText) {
        return oracleAdvance(svc, new TextSegment(markerText, base()));
    }

    /** C9：单码点推进宽（双精度，构造自适应语料用）。 */
    private static double charAdvance(TextLayoutService svc, char ch) {
        return svc.resolveAdvance(ch, base(), BASE);
    }

    /**
     * C9 自适应字数：返回使「prefixChars + n 个单宽字符 + inset 偏移」必然超过 width 的 n，
     * 且折出第二行 ≥2 字（推导：第一行最多容纳 floor((width-inset)/cw) 字，取该值 −prefix+2
     * 即总字数比容量多 2 ⇒ 第二行 ≥2 字；cw 为本 JVM 实测，任何字体下成立）。
     */
    private static int wrapCountChars(TextLayoutService svc, char ch, int prefixChars, int inset,
            int width) {
        double cw = charAdvance(svc, ch);
        Assert.assertTrue("单字推进宽必须 > 0，实测 " + cw, cw > 0.0D);
        int capacity = (int) Math.floor((width - inset) / cw);
        return Math.max(1, capacity - prefixChars + 2);
    }

    /**
     * C9 窄折行容器宽（锁②构造）：{@code round(k×cw) + inset − 1}（k=3）。
     * 推导：零偏移行首行容量 a = floor(W/cw) ≥ k = 3；带 inset 偏移行首行容量
     * b = floor((round(3cw) − 1)/cw) = 2（round(3cw) − 1 ∈ [3cw − 1.5, 3cw − 0.5]，
     * cw ≥ 2 时严格落在 [2cw, 3cw) 内）⇒ b &lt; a 由构造必然，与字体无关。
     */
    private static int narrowWrapWidth(double cw, int inset) {
        return (int) Math.round(3.0D * cw) + inset - 1;
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

    /**
     * 语料：4 个列表块（两软折标记行 + 懒延续 + 嵌套更深标记）+ 一个双行长段落（防泄漏
     * 对照）。C9：长行字数按本 JVM 实测 advance 推导（每行必然软折且第二行 ≥2 字），
     * 不再按 Windows 度量写死；行首文本（startsWith 认人用）保持不变。
     */
    private static String longRow(TextLayoutService svc, String prefix, char ch, int inset) {
        return prefix + repeat(ch, wrapCountChars(svc, ch, prefix.codePointCount(0, prefix.length()),
                inset, WIDTH));
    }

    private static String listCorpus(TextLayoutService svc) {
        int col = measuredColumn(svc, "• ");          // 一级列（懒延续/标记行 inset 来源）
        int col2 = 2 * col;                                 // 二级列（嵌套行 inset 来源）
        return joinLF(
                "- " + longRow(svc, "甲项首行", '甲', col),
                "  " + longRow(svc, "甲项缩进续行", '乙', col),
                "- 乙项短",
                "  - " + longRow(svc, "嵌套丙项", '丁', col2),
                "    " + longRow(svc, "嵌套丙的懒延续", '戊', col2),
                "1. " + longRow(svc, "有序一", '己', col),
                "   " + longRow(svc, "有序一懒延续", '庚', col),
                "", // 空行断开与列表的普通段落对照块
                longRow(svc, "普通段落首行", '辛', 0),
                longRow(svc, "普通段落次行", '壬', 0));
    }

    // ==================== 锁①：同块续行 inset == 标记行 inset + 独立量出的本级标记宽 ====================

    @Test
    public void continuationInsetEqualsIndependentlyMeasuredMarkerWidth() {
        TextLayoutService service = assemble(CORPUS_CHARS);
        String src = listCorpus(service);
        service = assemble(src);
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
        // 地板为构造推导值（C9）：三个长块各含 2 条自适应长行，每行必软折 ≥2 视觉行
        // ⇒ LIST 视觉行 ≥ 3×4+1 = 13 ≥ 6；每块续行 ≥3 ⇒ compared ≥ 9 ≥ 6。
        // 若度量突变到不折行（样本失效），地板先红，不静默空跑。
        Assert.assertTrue("LIST 视觉行地板 >= 6（构造推导 ≥13），实测 " + listVisualLines,
                listVisualLines >= 6);
        int totalCompared = 0;
        for (Integer v : compared.values()) {
            totalCompared += v.intValue();
        }
        Assert.assertTrue("参与比较的续行数地板 >= 6（构造推导 ≥9），实测 " + totalCompared,
                totalCompared >= 6);

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
        // 自适应构造 ⇒ 首/次行各 ≥2 视觉行，对照行地板 ≥2 由构造必然（推导见 listCorpus）。
        Assert.assertTrue("非列表对照行地板 >= 2（构造推导 ≥4），实测 " + plainVisual,
                plainVisual >= 2);
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
        // C9：容器宽与文本长度按本 JVM 实测单字 advance 构造（narrowWrapWidth 推导
        // b=2、a≥3，任何字体下 b<a 必然）——原 480 容器 + 45 字在 Linux（CJK 收窄到
        // ~10px）两行都不折，b=a=45 误红（CI 944175267）。
        TextLayoutService svc0 = assemble(CORPUS_CHARS, "续行长文本");
        int inset = measuredColumn(svc0, "• ");
        double cw = charAdvance(svc0, '癸');
        int wrapWidth = narrowWrapWidth(cw, inset);
        int totalChars = (int) Math.floor(wrapWidth / cw) + 3;
        String prefix = "续行长文本";
        String t = prefix + repeat('癸', totalChars - prefix.length());
        String src = joinLF("- 短标记行", "  " + t);
        TextLayoutService service = assemble(src, t);
        List<MarkdownLayoutLine> logical = logicalOf(src);
        List<MarkdownLayoutLine> visual =
                MarkdownPainter.wrapLayoutLines(logical, service, wrapWidth, BASE);
        MarkdownLayoutLine continuation = null;
        // 查找前缀取「续行」二字（窄容器下懒延续首行可能只装得下前缀残段；标记行
        // 「- 短标记行」的折残段不含「续行」开头，认人仍唯一）
        for (MarkdownLayoutLine line : visual) {
            if (textOf(line.getSegments()).startsWith("续行")) {
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
                MarkdownPainter.wrapLayoutLines(Collections.singletonList(plain), service,
                        wrapWidth, BASE);
        String aText = textOf(plainWrapped.get(0).getSegments());
        int a = aText.codePointCount(0, aText.length());
        String bText = textOf(continuation.getSegments());
        int b = bText.codePointCount(0, bText.length());
        // 构造反证地板：a≥3、b≥1 不成立即样本失效先红（防「b<a 因两边都空转成立」）
        Assert.assertTrue("构造地板：零偏移行首行 a≥3，实测 " + a, a >= 3);
        Assert.assertTrue("构造地板：带偏移行首行 b≥1，实测 " + b, b >= 1);
        Assert.assertTrue("inset 生效时断点必须早于 inset=0（钉「偏移真被消费」）: b(" + b
                + ") < a(" + a + ")，wrapWidth=" + wrapWidth + " cw=" + cw + " inset=" + inset,
                b < a);
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
     * 三级列，两档语料（C9：全部数值同 JVM 独立量出，Windows 读数只作示例注记）：
     * ① 均匀圆点三层：真值 = k×ceil(「• 」)（Win 14/28/42）；旧 F2 文本代理档
     *    （整串 ceil）= ceil(「  • 」)/ceil(「    • 」)（Win 14/29/43）——二、三级都
     *    钉死「不得等于实测代理」；
     * ② 混级标记（圆点 →「1. 」→圆点）：真值 = col / col+colOrdered / …（Win 14/35/28）
     *    ——均匀档三级恰与「level×固定步长」同值，正因如此必须配混级档杀该代理。
     * 长行字数按实测 advance 推导（每块必软折出第二条视觉行，反 ∅ 地板由构造成立）。
     */
    @Test
    public void threeLevelColumnsAreChainSumsNotFixedStepProxies() {
        TextLayoutService svc0 = assemble(CORPUS_CHARS);
        int col = measuredColumn(svc0, "• ");
        int proxy2 = measuredColumn(svc0, "  • ");
        int proxy3 = measuredColumn(svc0, "    • ");
        // 反证前置：本档代理与真值必须可区分（若某平台二者重合，样本失去判别力，
        // 如实红——Windows 29/43 vs 28/42、Verdana 31/45 vs 34/51、SimSun 30/44 vs
        // 32/48 三组实测都可区分，temp 探针 2026-09-07）。
        Assert.assertTrue("代理必须与真值可区分（样本失效先红）: proxy2=" + proxy2
                + " 2col=" + 2 * col, proxy2 != 2 * col);
        Assert.assertTrue("代理必须与真值可区分（样本失效先红）: proxy3=" + proxy3
                + " 3col=" + 3 * col, proxy3 != 3 * col);
        String uniform = joinLF(
                "- 甲" + repeat('甲', wrapCountChars(svc0, '甲', 1, col, WIDTH)),
                "  - 乙" + repeat('乙', wrapCountChars(svc0, '乙', 1, 2 * col, WIDTH)),
                "    - 丙" + repeat('丙', wrapCountChars(svc0, '丙', 1, 3 * col, WIDTH)));
        TextLayoutService service = assemble(uniform);
        List<MarkdownLayoutLine> uVisual =
                MarkdownPainter.wrapLayoutLines(logicalOf(uniform), service, WIDTH, BASE);
        int[] uCols = secondVisualInsetByBlockOrder(uVisual);
        Assert.assertEquals("均匀档必须恰 3 个列表块", 3, uCols.length);
        Assert.assertEquals("一级正文列（独立量出）", col, uCols[0]);
        Assert.assertEquals("二级正文列（独立量出=父列+本级）", 2 * col, uCols[1]);
        Assert.assertEquals("三级正文列（独立量出=父列+本级）", 3 * col, uCols[2]);
        Assert.assertTrue("二级不得 = 旧文本代理实测 ceil(「  • 」)=" + proxy2, uCols[1] != proxy2);
        Assert.assertTrue("三级不得 = 旧文本代理实测 ceil(「    • 」)=" + proxy3, uCols[2] != proxy3);

        int colOrdered = measuredColumn(svc0, "1. ");
        int proxyOrdered2 = measuredColumn(svc0, "  1. ");
        String mixed = joinLF(
                "- 甲" + repeat('甲', wrapCountChars(svc0, '甲', 1, col, WIDTH)),
                "  1. 乙" + repeat('乙', wrapCountChars(svc0, '乙', 1, col + colOrdered, WIDTH)),
                "    - 丙" + repeat('丙', wrapCountChars(svc0, '丙', 1, 2 * col, WIDTH)));
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
        Assert.assertEquals("混级一级（独立量出）", col, c1);
        Assert.assertEquals("混级「1. 」二级（独立量出 " + col + "+" + colOrdered + "）",
                col + colOrdered, cOrdered);
        Assert.assertEquals("混级圆点二级（独立量出 " + col + "+" + col + "）", 2 * col, cBullet);
        Assert.assertTrue("「1. 」二级不得 = 2×固定步长 " + 2 * col + "（level×step 代理）: " + cOrdered,
                cOrdered != 2 * col);
        Assert.assertTrue("「1. 」二级不得 = 旧文本代理实测 ceil(「  1. 」)=" + proxyOrdered2,
                cOrdered != proxyOrdered2);
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
            Assert.assertTrue("每块必须软折出第二条视觉行（反 ∅，自适应构造下必然成立；"
                    + "不成立=语料/度量样本失效，先红）", pair[1] >= 0);
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
        TextLayoutService svc0 = assemble(CORPUS_CHARS);
        int col = measuredColumn(svc0, "• ");
        int colOrdered = measuredColumn(svc0, "1. ");
        // 长段字数按实测 advance 推导（首段/第二段/子项首段各必软折 ≥2 视觉行，
        // 断言行数地板 ≥8 由构造成立：head≥2+2+1、heading≥1、quote≥1、ordered≥2+1）
        String src = joinLF(
                "- 甲项首段" + repeat('甲', wrapCountChars(svc0, '甲', 4, col, WIDTH)),
                "",
                "  第二段正文" + repeat('乙', wrapCountChars(svc0, '乙', 5, col, WIDTH)),
                "",
                "  第三段正文",
                "",
                "  #### 项内标题",
                "",
                "  > 项内引用行",
                "",
                "  1. 有序子项首段" + repeat('丙',
                        wrapCountChars(svc0, '丙', 6, col + colOrdered, WIDTH)),
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
        Assert.assertEquals("首段所在链（[「• 」]）沿列 = 同 JVM 独立量出的标记宽（C9：期望值"
                + "不得是字面量——Linux 实测 17 vs Windows 14，语义零平台差）col=" + col,
                col, col1);
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
        // 分项数值点名（防「两边同错互相抵消」：列、份额、层级各钉各的数——C9 起
        // 「列」全部取同 JVM 独立量出值，引用步长 8 是样式表登记常量非字体度量，保留字面）
        // 标记行身份按「该块首条视觉行」判；seg0 会被折断成「1.」残片
        // （行尾空白丢弃，K3 既有行为，探针实测），不可据其文本认人。
        boolean orderedFirstSeen = false;
        for (MarkdownLayoutLine line : visual) {
            if (line.getBlockId() == headingBlock) {
                Assert.assertEquals("项内标题（另起块）inset == 独立量出列 " + col, col,
                        line.getLeftInsetPx());
            } else if (line.getBlockId() == quoteBlock) {
                Assert.assertEquals("项内引用（另起块）inset == 引用 8 + 独立量出列 " + col,
                        8 + col, line.getLeftInsetPx());
                Assert.assertEquals("引用步长仍取样式表 8", 8, line.getIndentStepPx());
                Assert.assertEquals("引用层级不被列表改动", 1, line.getQuoteLevel());
            } else if (line.getBlockId() == orderedBlock) {
                boolean markerRow = !orderedFirstSeen;
                orderedFirstSeen = true;
                Assert.assertEquals("有序子项" + (markerRow ? "标记行吃祖先列 " + col
                        : "续行吃全额列 " + (col + colOrdered)),
                        markerRow ? col : col + colOrdered, line.getLeftInsetPx());
            }
        }
        // 地板 = 构造推导（见语料处注释：≥10），保留 8 作样本失效先红线。
        Assert.assertTrue("断言行数地板 >= 8（构造推导 ≥10），实测 " + assertedRows,
                assertedRows >= 8);
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
