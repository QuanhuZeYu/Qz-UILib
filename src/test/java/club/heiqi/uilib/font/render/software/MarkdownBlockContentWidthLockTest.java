package club.heiqi.uilib.font.render.software;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * M8「块内统一内容宽」核心锁（规划《通用Markdown渲染器》§二之七·续 第 9 条）。
 *
 * <p><b>锁的是什么</b>：围栏代码块底色曾在三个表面上有三套口径——(a) L2 出图矩形（铺满容器右缘）、
 * (b) 聊天面板私有 {@code codeBlockWidthPx} 查表、(c) devtools 页逐行自字宽（右缘参差）。
 * 本批把「块内统一宽」上收到 L2（折行时按 blockId 聚合，唯一产地
 * {@code MarkdownLineLayout#unifyCodeBlockContentWidth}），于是这件事只有一个可机器形式：
 * <b>L2 命令流里那条合并 BACKGROUND 矩形的宽度 == {@code getBlockContentWidthPx()}</b>，
 * 对同 blockId 的全部 CODE 行成立，三侧消费者读同一个数。</p>
 *
 * <p><b>凭什么不是同义反复</b>（事故档 ERROR-20260905 第八节：判据必须能区分「是哪个数」）：
 * 每个块都另算一条<b>独立 oracle</b>——只用公共度量入口 {@link MarkdownPainter#lineWidthPx}
 * 逐行量出「本行自身文字宽」再取块内最大值，三方比对（矩形宽 == getter == oracle）。
 * 并且强制：① 至少 N 个块的成员行宽<b>互不相同</b>（ragged），否则「矩形宽 == 本行宽」这条
 * 被废弃的 (c) 口径也能通过；② 至少 M 个块的块宽<b>严格小于容器宽</b>，否则「铺满右缘」这条
 * 被废弃的 (a) 口径也能通过。两个方向各自钉成可失败项，等式才不是恒真。</p>
 *
 * <p><b>每条反向断言配正对照 + 反 ∅ 地板</b>：非围栏文档必须零 CODE 底色矩形、全行 getter
 * 取定义值 0，同时同一扫描器必须在围栏语料上报出真实命中；比较计数地板写死在常量里。</p>
 *
 * <p>装配纪律与 {@code MarkdownBlockGeometryTest} 同：复用 {@code LatexSoftwareRenderKit}
 * 共享装配（严禁另 new FontService），测量期解除宽度 miss 预算。</p>
 */
public class MarkdownBlockContentWidthLockTest {

    private static final char LF = (char) 0x0A;
    private static final int BASE = 16;
    private static final int WIDE = 480;
    private static final int NARROW = 200;

    /** 反 ∅ 地板：实际参与「矩形宽 == getter」比较的 CODE 视觉行数。 */
    private static final int MIN_COMPARED_CODE_ROWS = 10;
    /** 反 ∅ 地板：参与比较的合并 CODE 底色矩形数。 */
    private static final int MIN_COMPARED_RECTS = 6;
    /** 反同义反复地板：成员行宽互不相同（ragged）的块数——否则逐行自字宽口径也算对。 */
    private static final int MIN_RAGGED_BLOCKS = 3;
    /** 反同义反复地板：块宽严格小于容器宽的块数——否则铺满容器口径也算对。 */
    private static final int MIN_NARROWER_THAN_CONTAINER = 4;
    /** 装配侧锚点地板：块宽聚合 token 在 L2 的定义+调用合计次数。 */
    private static final int MIN_L2_AGGREGATION_HITS = 2;
    /** 装配侧锚点地板：三侧消费者在<b>代码行</b>里读 getter 的合计次数。 */
    private static final int MIN_CONSUMER_READS = 3;

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

    private static String fence() {
        char tick = (char) 0x60;
        return String.valueOf(tick) + tick + tick;
    }

    /** 一条待测语料：{标识, markdown 源, 容器宽}。 */
    private static final String[][] CORPUS = {
            {"单块三行宽窄悬殊", joinLF(fence() + "java", "class Qz {",
                    "    // 注释里 **星号** $公式$ 引号 全字面", "}"), String.valueOf(WIDE)},
            {"长token折行同块", joinLF(fence(), "ab",
                    "averyveryverylongunbreakstokenwhichcannotfit", "z"),
                    String.valueOf(NARROW)},
            {"引用内围栏", joinLF("> " + fence() + "java", "> int a = 1;",
                    "> int bbbbbbbbbbbbbbbbbbbbbbbbbbbb = 2222222222;", "> " + fence()),
                    String.valueOf(WIDE)},
            {"一块贴一段", joinLF("段落文字甲乙丙", "", fence(), "x = 1;",
                    "y = 2222222222;", fence()), String.valueOf(WIDE)},
            {"两块各自独立", joinLF(fence(), "aa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", fence(), "",
                    fence() + "sh", "echo $HOME", fence()), String.valueOf(WIDE)},
    };

    /** 正对照语料：无任何围栏（段落 + 引用 + 分隔线 + 列表）。 */
    private static final String CONTROL_NO_FENCE = joinLF("普通段落甲乙丙",
            "> 一层引用文本", "---", "- 列表项", "尾段");

    private static List<MarkdownLayoutLine> logical(String src) {
        return MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base());
    }

    /** 装配全部语料码点（与出图/门禁同纪律：先喂字形，再断度量）。 */
    private static TextLayoutService assembleAll() {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        List<TextSegment> all = new ArrayList<TextSegment>();
        for (int i = 0; i < CORPUS.length; i++) {
            all.addAll(MarkdownDocument.parse(CORPUS[i][1])
                    .toSegments(MarkdownStyleTable.defaults(), base()));
            all.addAll(flatten(logical(CORPUS[i][1])));
        }
        all.addAll(MarkdownDocument.parse(CONTROL_NO_FENCE)
                .toSegments(MarkdownStyleTable.defaults(), base()));
        LatexSoftwareRenderKit.assembleGlyphs(shared, all);
        return shared.service;
    }

    private static List<TextSegment> flatten(List<MarkdownLayoutLine> lines) {
        List<TextSegment> out = new ArrayList<TextSegment>();
        for (int i = 0; i < lines.size(); i++) {
            out.addAll(lines.get(i).getSegments());
        }
        return out;
    }

    // ==================== 核心锁 ====================

    /** 核心锁：合并 BACKGROUND 矩形宽 == getter（同块全部 CODE 行）== 独立 oracle。 */
    @Test
    public void mergedCodeRectWidthEqualsBlockContentWidthGetter() {
        TextLayoutService service = assembleAll();
        int comparedRows = 0;
        int comparedRects = 0;
        int raggedBlocks = 0;
        int narrowerThanContainer = 0;
        for (int c = 0; c < CORPUS.length; c++) {
            String tag = CORPUS[c][0];
            String src = CORPUS[c][1];
            int container = Integer.parseInt(CORPUS[c][2]);
            List<MarkdownLayoutLine> visual =
                    MarkdownPainter.wrapLayoutLines(logical(src), service, container, BASE);
            List<PaintCommand> commands =
                    MarkdownPainter.toLayoutPaintCommands(logical(src), service, container, BASE);
            int codeBg = codeBackdropArgb(visual, tag);
            Map<Integer, List<MarkdownLayoutLine>> blocks = codeBlocks(visual);
            List<PaintCommand> rects = codeRects(commands, codeBg);
            Assert.assertEquals(tag + "：合并底色矩形数必须等于 CODE 块数（每块恰一条）",
                    blocks.size(), rects.size());
            int rectIndex = 0;
            for (Map.Entry<Integer, List<MarkdownLayoutLine>> entry : blocks.entrySet()) {
                List<MarkdownLayoutLine> rows = entry.getValue();
                PaintCommand rect = rects.get(rectIndex++);
                int rectWidth = rect.getRight() - rect.getLeft();
                // 独立 oracle：只经公共度量入口量「每行自身宽」，再取块内最大
                int minOwn = Integer.MAX_VALUE;
                int maxOwn = 0;
                for (MarkdownLayoutLine row : rows) {
                    int own = MarkdownPainter.lineWidthPx(row.getSegments(), service, BASE);
                    if (own < minOwn) {
                        minOwn = own;
                    }
                    if (own > maxOwn) {
                        maxOwn = own;
                    }
                }
                Assert.assertTrue(tag + " 块 " + entry.getKey() + "：oracle 必须 > 0", maxOwn > 0);
                Assert.assertEquals(tag + " 块 " + entry.getKey() + "：合并矩形宽必须等于"
                        + "「块内最宽行自身实测宽」（既不是逐行自字宽，也不是铺满容器右缘）",
                        maxOwn, rectWidth);
                boolean blockFitsContainer = true;
                for (MarkdownLayoutLine row : rows) {
                    Assert.assertEquals(tag + " 块 " + entry.getKey() + "：矩形宽必须等于该行"
                            + " getBlockContentWidthPx()（同块行同值）", rectWidth,
                            row.getBlockContentWidthPx());
                    Assert.assertTrue(tag + " 块宽不得溢出容器: " + row.getBlockContentWidthPx()
                            + " vs " + container, row.getBlockContentWidthPx() <= container);
                    if (row.getBlockContentWidthPx() < container) {
                        blockFitsContainer = false;
                    }
                    comparedRows++;
                }
                if (!blockFitsContainer) {
                    narrowerThanContainer++;
                }
                if (maxOwn > minOwn) {
                    raggedBlocks++;
                }
                comparedRects++;
            }
        }
        Assert.assertTrue("反 ∅ 地板：实际比较的 CODE 视觉行数 >= " + MIN_COMPARED_CODE_ROWS
                + "，实测 " + comparedRows, comparedRows >= MIN_COMPARED_CODE_ROWS);
        Assert.assertTrue("反 ∅ 地板：实际比较的合并矩形数 >= " + MIN_COMPARED_RECTS
                + "，实测 " + comparedRects, comparedRects >= MIN_COMPARED_RECTS);
        Assert.assertTrue("反同义反复地板：成员行宽互不相同的 ragged 块 >= " + MIN_RAGGED_BLOCKS
                + "（否则 (c) 逐行自字宽口径也能通过等式），实测 " + raggedBlocks,
                raggedBlocks >= MIN_RAGGED_BLOCKS);
        Assert.assertTrue("反同义反复地板：块宽严格小于容器宽的块 >= " + MIN_NARROWER_THAN_CONTAINER
                + "（否则 (a) 铺满右缘口径也能通过等式），实测 " + narrowerThanContainer,
                narrowerThanContainer >= MIN_NARROWER_THAN_CONTAINER);
    }

    /** 同一 blockId 内所有 CODE 行的块宽彼此相等，且 >= 各行自身文字宽。 */
    @Test
    public void blockWidthIsUniformPerBlockAndNeverBelowOwnRowWidth() {
        TextLayoutService service = assembleAll();
        int comparisons = 0;
        int strictBlocks = 0;
        for (int c = 0; c < CORPUS.length; c++) {
            String tag = CORPUS[c][0];
            String src = CORPUS[c][1];
            int container = Integer.parseInt(CORPUS[c][2]);
            List<MarkdownLayoutLine> visual =
                    MarkdownPainter.wrapLayoutLines(logical(src), service, container, BASE);
            Map<Integer, List<MarkdownLayoutLine>> blocks = codeBlocks(visual);
            Assert.assertTrue(tag + " 必须含围栏块（语料空跑探测）", !blocks.isEmpty());
            for (Map.Entry<Integer, List<MarkdownLayoutLine>> entry : blocks.entrySet()) {
                List<MarkdownLayoutLine> rows = entry.getValue();
                int blockWidth = rows.get(0).getBlockContentWidthPx();
                Assert.assertTrue(tag + " 块 " + entry.getKey() + " 块宽必须 > 0: " + blockWidth,
                        blockWidth > 0);
                boolean strictlyWiderThanSomeRow = false;
                for (MarkdownLayoutLine row : rows) {
                    Assert.assertEquals(tag + " 同块行块宽必须彼此相等", blockWidth,
                            row.getBlockContentWidthPx());
                    int own = MarkdownPainter.lineWidthPx(row.getSegments(), service, BASE);
                    Assert.assertTrue(tag + " 块宽必须 >= 本行自身文字宽: 块宽=" + blockWidth
                            + " 本行=" + own + " 文本=<" + textOf(row) + '>', blockWidth >= own);
                    if (blockWidth > own) {
                        strictlyWiderThanSomeRow = true;
                    }
                    comparisons++;
                }
                if (strictlyWiderThanSomeRow) {
                    strictBlocks++;
                }
            }
        }
        Assert.assertTrue("反 ∅ 地板：等值/覆盖比较次数 >= " + MIN_COMPARED_CODE_ROWS
                + "，实测 " + comparisons, comparisons >= MIN_COMPARED_CODE_ROWS);
        Assert.assertTrue("反 ∅ 地板：块宽严格大于某行自身宽的块 >= 3（证明「取块内最大」真的在起作用，"
                + "不是每行恰好等宽），实测 " + strictBlocks, strictBlocks >= 3);
    }

    // ==================== 正对照 + 语义定义值 ====================

    /**
     * 正对照：非围栏文档零 CODE 底色矩形、全行 getter 恒取定义值 0；
     * 同一扫描器在围栏语料上必须报出真实命中（证明「没有」不是扫帚坏了）。
     */
    @Test
    public void nonFenceDocumentHasNoRectAndEveryLineCarriesTheDefinedZero() {
        TextLayoutService service = assembleAll();
        List<MarkdownLayoutLine> visual =
                MarkdownPainter.wrapLayoutLines(logical(CONTROL_NO_FENCE), service, WIDE, BASE);
        Assert.assertTrue("对照语料必须至少 3 行（扫描集非空前提）: " + visual.size(),
                visual.size() >= 3);
        int rows = 0;
        for (MarkdownLayoutLine line : visual) {
            Assert.assertTrue("对照语料不得含 CODE 行: " + line,
                    line.getKind() != MarkdownLayoutLine.Kind.CODE);
            Assert.assertEquals("非 CODE 行 getBlockContentWidthPx() 必须是定义值 0: " + line,
                    0, line.getBlockContentWidthPx());
            rows++;
        }
        Assert.assertTrue("对照语料参与比较行数地板 >= 3，实测 " + rows, rows >= 3);
        List<PaintCommand> commands =
                MarkdownPainter.toLayoutPaintCommands(logical(CONTROL_NO_FENCE), service, WIDE, BASE);
        Assert.assertTrue("对照语料必须至少产 1 条非 CODE 的 BACKGROUND（引用竖条/真横线），"
                + "否则「零 CODE 矩形」是空扫: " + backgrounds(commands).size(),
                !backgrounds(commands).isEmpty());
        List<MarkdownLayoutLine> fenceVisual =
                MarkdownPainter.wrapLayoutLines(logical(CORPUS[0][1]), service, WIDE, BASE);
        int codeBg = codeBackdropArgb(fenceVisual, CORPUS[0][0]);
        List<PaintCommand> fenceRects = codeRects(
                MarkdownPainter.toLayoutPaintCommands(logical(CORPUS[0][1]), service, WIDE, BASE),
                codeBg);
        Assert.assertEquals("正对照：同一扫描器在围栏语料必须命中恰 1 条 CODE 底色矩形，实测 "
                + fenceRects.size(), 1, fenceRects.size());
    }

    /**
     * 语义定义值与兼容性：L1 逻辑行未经 L2 折行 → 块宽恒 0（未算）；拷贝法只换该字段、
     * 身份与几何全量继承；{@code withSegments} 不丢块宽；负值归零；{@code blank()} 行取 0。
     */
    @Test
    public void definedZeroSemanticsAndCopyMethodPreserveIdentity() {
        TextLayoutService service = assembleAll();
        String src = joinLF(fence(), "int a = 1;", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        for (MarkdownLayoutLine line : logical(src)) {
            Assert.assertEquals("L1 逻辑行（未经 L2 度量）块宽必须恒 0: " + line,
                    0, line.getBlockContentWidthPx());
        }
        MarkdownLayoutLine code = null;
        for (MarkdownLayoutLine line : MarkdownPainter.wrapLayoutLines(
                logical(src), service, WIDE, BASE)) {
            if (line.getKind() == MarkdownLayoutLine.Kind.CODE) {
                code = line;
                break;
            }
        }
        Assert.assertNotNull("CODE 视觉行必须存在（反空跑）", code);
        Assert.assertTrue("CODE 视觉行块宽必须 > 0: " + code.getBlockContentWidthPx(),
                code.getBlockContentWidthPx() > 0);
        MarkdownLayoutLine stamped = code.withBlockContentWidthPx(777);
        Assert.assertEquals("拷贝法必须写入新值", 777, stamped.getBlockContentWidthPx());
        Assert.assertEquals("拷贝法不得改 kind", code.getKind(), stamped.getKind());
        Assert.assertEquals("拷贝法不得改 quoteLevel", code.getQuoteLevel(), stamped.getQuoteLevel());
        Assert.assertEquals("拷贝法不得改 blockId", code.getBlockId(), stamped.getBlockId());
        Assert.assertEquals("拷贝法不得改 leftInset", code.getLeftInsetPx(), stamped.getLeftInsetPx());
        Assert.assertEquals("拷贝法不得改 indentStep", code.getIndentStepPx(),
                stamped.getIndentStepPx());
        Assert.assertEquals("拷贝法不得改 barWidth", code.getBarWidthPx(), stamped.getBarWidthPx());
        Assert.assertEquals("拷贝法不得改 ruleThickness", code.getRuleThicknessPx(),
                stamped.getRuleThicknessPx());
        Assert.assertEquals("拷贝法不得改 accentArgb", code.getAccentArgb(), stamped.getAccentArgb());
        Assert.assertEquals("拷贝法不得改 backgroundArgb", code.getBackgroundArgb(),
                stamped.getBackgroundArgb());
        Assert.assertEquals("拷贝法不得改段流", code.getSegments(), stamped.getSegments());
        Assert.assertTrue("原行不得被改写（不可变性）: " + code.getBlockContentWidthPx(),
                code.getBlockContentWidthPx() != 777);
        Assert.assertEquals("withSegments 不得丢块宽", 777,
                stamped.withSegments(code.getSegments()).getBlockContentWidthPx());
        Assert.assertEquals("负值必须归零（非正值只有定义值 0 一种）", 0,
                code.withBlockContentWidthPx(-5).getBlockContentWidthPx());
        Assert.assertEquals("blank() 行块宽必须为定义值 0", 0,
                MarkdownLayoutLine.blank().getBlockContentWidthPx());
    }

    // ==================== 装配侧窄锚：块宽聚合只允许有一个产地 ====================

    /**
     * 装配侧窄锚（对 M6 复生锁作用域缺口的补法；判断见规划 §二之七·续 第 9 条）：
     * 「按 blockId 聚合块宽」在主源里只允许一个产地 = L2 的 {@code unifyCodeBlockContentWidth}；
     * 三个消费者只准读 getter，不准再各写一份（旧 {@code codeBlockWidthPx} 查表不得复活）。
     *
     * <p>锚点<b>刻意只锁这一件具体的事</b>，不扩成「禁止消费者做任何本地布局/钳宽」——
     * 后者是防写代码而不是防第二套真相。扫描一律走剥注释后的代码行（注释里提到被禁 token
     * 是给人看的，不算实现复活）。</p>
     */
    @Test
    public void blockWidthAggregationMayLiveOnlyInL2() throws IOException {
        Path l2 = Paths.get(
                "src/main/java/club/heiqi/uilib/ui/markdown/MarkdownLineLayout.java");
        Assert.assertTrue("L2 装配文件存在性探测（扫描器自校准）", Files.isRegularFile(l2));
        List<String> l2Code = codeLines(l2);
        int aggregationHits = countToken(l2Code, "unifyCodeBlockContentWidth");
        Assert.assertTrue("块宽聚合必须在 L2 定义且被调用，命中 " + aggregationHits
                + "（地板 " + MIN_L2_AGGREGATION_HITS + "；命中 0 = 扫帚坏了）",
                aggregationHits >= MIN_L2_AGGREGATION_HITS);
        String[] consumers = {
                "src/main/java/club/heiqi/uilib/internal/chat3/view/ChatMessageList.java",
                "src/main/java/club/heiqi/uilib/internal/chat3/view/ChatMarkdownPipeline.java",
                "src/main/java/club/heiqi/uilib/internal/devtools/playground/pages/MarkdownPage.java",
        };
        int consumerReads = 0;
        for (int i = 0; i < consumers.length; i++) {
            Path file = Paths.get(consumers[i]);
            Assert.assertTrue("消费者存在性探测: " + consumers[i], Files.isRegularFile(file));
            List<String> code = codeLines(file);
            Assert.assertEquals(consumers[i] + " 不得复活按 blockId 的块宽查表（旧私有机制已删）",
                    0, countToken(code, "codeBlockWidth"));
            Assert.assertEquals(consumers[i] + " 不得自带块宽聚合实现", 0,
                    countToken(code, "unifyCodeBlockContentWidth"));
            Assert.assertEquals(consumers[i] + " 不得自己按块聚合取最大行宽", 0,
                    countToken(code, "widestByBlock"));
            consumerReads += countToken(code, "lockContentWidthPx");
        }
        Assert.assertTrue("反空跑：三侧消费者代码行读 getter 的 token 命中合计 >= "
                + MIN_CONSUMER_READS + "，实测 " + consumerReads, consumerReads >= MIN_CONSUMER_READS);
    }

    // ==================== 工具 ====================

    private static List<PaintCommand> backgrounds(List<PaintCommand> commands) {
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        for (PaintCommand command : commands) {
            if (command.getType() == PaintCommandType.BACKGROUND) {
                out.add(command);
            }
        }
        return out;
    }

    private static int codeBackdropArgb(List<MarkdownLayoutLine> visual, String tag) {
        for (MarkdownLayoutLine line : visual) {
            if (line.getKind() == MarkdownLayoutLine.Kind.CODE && line.getBackgroundArgb() != 0) {
                return line.getBackgroundArgb();
            }
        }
        throw new AssertionError(tag + " 语料必须含 CODE 底色（否则该条语料空跑）");
    }

    /** 按 blockId 分组的 CODE 视觉行（保持块出现顺序）。 */
    private static Map<Integer, List<MarkdownLayoutLine>> codeBlocks(
            List<MarkdownLayoutLine> visual) {
        Map<Integer, List<MarkdownLayoutLine>> out =
                new LinkedHashMap<Integer, List<MarkdownLayoutLine>>();
        for (MarkdownLayoutLine line : visual) {
            if (line.getKind() != MarkdownLayoutLine.Kind.CODE) {
                continue;
            }
            Integer key = Integer.valueOf(line.getBlockId());
            List<MarkdownLayoutLine> rows = out.get(key);
            if (rows == null) {
                rows = new ArrayList<MarkdownLayoutLine>();
                out.put(key, rows);
            }
            rows.add(line);
        }
        return out;
    }

    private static List<PaintCommand> codeRects(List<PaintCommand> commands, int codeBg) {
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        for (PaintCommand command : commands) {
            if (command.getType() == PaintCommandType.BACKGROUND
                    && command.getColor() == codeBg) {
                out.add(command);
            }
        }
        return out;
    }

    private static String textOf(MarkdownLayoutLine line) {
        StringBuilder sb = new StringBuilder();
        for (TextSegment segment : line.getSegments()) {
            sb.append(segment.getText());
        }
        return sb.toString();
    }

    private static int countToken(List<String> lines, String token) {
        int n = 0;
        for (String line : lines) {
            int i = 0;
            while ((i = line.indexOf(token, i)) >= 0) {
                n++;
                i += token.length();
            }
        }
        return n;
    }

    /** 剥注释后的代码行（沿用 Chat3MarkdownResurrectionGuardTest 同一口径）。 */
    private static List<String> codeLines(Path file) throws IOException {
        List<String> out = new ArrayList<String>();
        boolean inBlock = false;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String s = raw.trim();
            if (inBlock) {
                int close = s.indexOf("*/");
                if (close >= 0) {
                    inBlock = false;
                    String tail = s.substring(close + 2).trim();
                    if (!tail.isEmpty()) {
                        out.add(tail);
                    }
                }
                continue;
            }
            if (s.startsWith("//")) {
                continue;
            }
            int blockOpen = s.indexOf("/*");
            if (blockOpen >= 0) {
                String head = s.substring(0, blockOpen).trim();
                if (!head.isEmpty()) {
                    out.add(head);
                }
                int close = s.indexOf("*/", blockOpen + 2);
                if (close < 0) {
                    inBlock = true;
                } else {
                    String tail = s.substring(close + 2).trim();
                    if (!tail.isEmpty()) {
                        out.add(tail);
                    }
                }
                continue;
            }
            if (s.startsWith("*")) {
                continue;
            }
            String line = raw;
            int lineComment = line.indexOf("//");
            if (lineComment >= 0) {
                line = line.substring(0, lineComment);
            }
            if (!line.trim().isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }
}
