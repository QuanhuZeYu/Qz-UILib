package club.heiqi.uilib.internal.devtools.playground;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.ScenePaintCapture;

/**
 * 演示页注册表与页面构建契约测试。
 *
 * <p>资产：页面元信息（id/title/description）非空、id 唯一且小写；每页能在 headless
 * runtime 下构建出非空 scene 树（演示页构建逻辑可测）；默认清单不可变。
 * 纯视觉/交互细节（光标闪烁、soft wrap 视觉）不在此断言，由既有组件测试覆盖。</p>
 */
public class PlaygroundPageRegistryTest {

    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer());
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    @Test
    public void registryIsNotEmptyAndOrdered() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        Assert.assertFalse("至少一个页面", pages.isEmpty());
        Assert.assertEquals("默认页为首项（总览）", "home", pages.get(0).id());
        Assert.assertEquals("清单顺序稳定", pages.size(), PlaygroundPageRegistry.ids().size());
    }

    @Test
    public void pageIdsAreUniqueAndLowercase() {
        Set<String> seen = new HashSet<String>();
        for (PlaygroundPage page : PlaygroundPageRegistry.defaultPages()) {
            Assert.assertNotNull("id 非空", page.id());
            Assert.assertEquals("id 小写", page.id(), page.id().toLowerCase(java.util.Locale.ROOT));
            Assert.assertTrue("id 唯一: " + page.id(), seen.add(page.id()));
        }
    }

    @Test
    public void metadataIsPresentForAllPages() {
        for (PlaygroundPage page : PlaygroundPageRegistry.defaultPages()) {
            Assert.assertFalse("标题非空: " + page.id(), page.title().trim().isEmpty());
            Assert.assertFalse("说明非空: " + page.id(), page.description().trim().isEmpty());
        }
    }

    @Test
    public void lookupFindsRegisteredPagesAndMissesUnknown() {
        List<String> ids = PlaygroundPageRegistry.ids();
        for (String id : ids) {
            Assert.assertNotNull("lookup 命中: " + id, PlaygroundPageRegistry.lookup(id));
        }
        Assert.assertNull("未知 id 返回 null", PlaygroundPageRegistry.lookup("no-such-page"));
        Assert.assertNull("null id 返回 null", PlaygroundPageRegistry.lookup(null));
    }

    @Test
    public void defaultPagesIsImmutable() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        try {
            pages.add(PlaygroundPageRegistry.defaultPages().get(0));
            Assert.fail("默认清单不可变");
        } catch (UnsupportedOperationException expected) {
            // 预期不可变
        }
    }

    @Test
    public void everyPageBuildsNonNullTreeAndMountsCleanly() {
        for (PlaygroundPage page : PlaygroundPageRegistry.defaultPages()) {
            SceneNode parent = new SceneNode();
            SceneNode root = runtime.mount(parent, page.build(runtime)).getRoot();
            Assert.assertNotNull("页面构建非 null: " + page.id(), root);
            Assert.assertSame("根已挂入父节点: " + page.id(), root, parent.__getChildren().get(0));
            Assert.assertFalse("根有子节点: " + page.id(), root.__getChildren().isEmpty());
            runtime.flush();
        }
    }

    // ==================== M9 页面级像素锁：围栏底色必须是一整块连续像素 ====================

    private static final char LF = (char) 0x0A;
    private static final int BASE_FONT_PX = 14;
    /** 画布（足够容纳整页；{@code ScenePaintCapture} 同端口 FixedTextMeasurer）。 */
    private static final int CANVAS_W = 900;
    private static final int CANVAS_H = 1800;
    /** 反 ∅ 地板：参与判定的底色纵向游程数（页上有两个围栏块）。 */
    private static final int MIN_RUNS = 2;
    /** 反 ∅ 地板：至少一个游程要跨过的行数（证明「跨行无缝」真发生过）。 */
    private static final int MIN_MULTIROW_RUNS = 1;
    /** 反 ∅ 地板：底色像素总量。 */
    private static final int MIN_BACKDROP_PIXELS = 3000;

    /**
     * 页面级<b>纯像素</b>锁（M9；取代上一批那条 {@code getPreferredWidth()} 记账值断言，
     * 后者已删除——它不决定像素，在「有病」和「修好」两版代码上都会绿，属代理指标假绿）。
     *
     * <p><b>链路</b>：headless 构造 markdown 页 → {@code ScenePaintCapture}（layout → paint →
     * {@code ScenePaintReplayer} → 后端）→ 把后端收到的<b>实心面</b>调用（{@code fillRect} /
     * {@code drawSurface}）按调用顺序覆盖式栅格进 ARGB 缓冲 → 只在<b>像素</b>上断言。
     * 文本类调用忽略：取样刻意落在左内衬列（文字起点之左），不受字形墨影响。
     * 注：场景侧没有现成的 scene→{@code SoftwareRenderFrame} 桥（那条路只吃字形批），
     * 故像素由「后端收到的实心面指令流」重建——它正是真机 GL 收到的同一批几何。</p>
     *
     * <p><b>判据（全部形状无关，不看节点树长什么样）</b>：
     * ① <b>无缝</b>——在任意一列上，若两段底色游程之间的空档<b>小于一个行高</b>，那不是块间距而是
     * 块内裂缝 → 红（旧实现：三条 14px 带夹 8px 缝，行高 14 → 8 &lt; 14 命中）；
     * ② <b>跨行连续</b>——至少一个游程高度 &gt;= 2×行高（旧实现最高只有 14px → 红）；
     * ③ <b>横向统一</b>——每个游程内每一行的底色水平跨度必须完全相同（逐行自字宽 → 红）；
     * ④ <b>正对照</b>——无围栏的「标题」卡在该色上零像素；
     * ⑤ <b>反 ∅ 地板</b>——游程数 / 像素总量 / 跨行数各设下限。</p>
     */
    @Test
    public void markdownPageCodeBackdropIsOneContinuousPixelBlock() {
        PlaygroundPage page = PlaygroundPageRegistry.lookup("markdown");
        Assert.assertNotNull("注册表含 markdown 页", page);
        SceneNode shell = page.build(runtime).get();
        Assert.assertNotNull("markdown 页可 headless 构造", shell);

        RecordingRenderBackend backend = ScenePaintCapture.paintAndCapture(shell, CANVAS_W, CANVAS_H);
        int codeBg = probeCodeBackdropArgb();
        int rowHeightPx = probeCodeRowHeightPx();
        Assert.assertTrue("行高探针必须 > 0: " + rowHeightPx, rowHeightPx > 0);
        int[] canvas = rasterizeSolidFaces(backend);

        int backdropPixels = countColorAll(canvas, codeBg);
        Assert.assertTrue("反 ∅ 地板：底色像素总量 >= " + MIN_BACKDROP_PIXELS + "，实测 "
                + backdropPixels, backdropPixels >= MIN_BACKDROP_PIXELS);

        // 逐列扫游程：找「块内裂缝」（两段之间空档 < 行高）
        int seams = 0;
        int runs = 0;
        int multiRowRuns = 0;
        String seamEvidence = "";
        for (int x = 0; x < CANVAS_W; x++) {
            List<int[]> cols = runsInColumn(canvas, x, codeBg);
            if (cols.isEmpty()) {
                continue;
            }
            runs = Math.max(runs, cols.size());
            for (int r = 0; r + 1 < cols.size(); r++) {
                int gap = cols.get(r + 1)[0] - cols.get(r)[1] - 1;
                if (gap > 0 && gap < rowHeightPx) {
                    seams++;
                    if (seamEvidence.isEmpty()) {
                        seamEvidence = "x=" + x + " 第" + r + "段末 y=" + cols.get(r)[1]
                                + " 与下一段起 y=" + cols.get(r + 1)[0] + " 之间空档 " + gap
                                + "px < 行高 " + rowHeightPx + "px";
                    }
                }
            }
        }
        Assert.assertEquals("像素级：围栏底色不得存在「块内裂缝」（空档小于一个行高 = 同一块被切开）"
                + "——命中 " + seams + " 处" + (seamEvidence.isEmpty() ? "" : "，首例 " + seamEvidence),
                0, seams);

        // 跨行连续 + 横向统一：按连通块（同一游程且同宽）核
        List<int[]> bands = backdropBands(canvas, codeBg);
        Assert.assertTrue("反 ∅ 地板：底色横向带数 >= " + MIN_RUNS + "，实测 " + bands.size(),
                bands.size() >= MIN_RUNS);
        for (int b = 0; b < bands.size(); b++) {
            int[] band = bands.get(b);
            int top = band[0];
            int bottom = band[1];
            int height = bottom - top + 1;
            if (height >= 2 * rowHeightPx) {
                multiRowRuns++;
            }
            int left = band[2];
            int right = band[3];
            for (int y = top; y <= bottom; y++) {
                int[] extent = rowExtent(canvas, y, codeBg, left);
                Assert.assertEquals("像素级：横向带#" + b + " 在 y=" + y + " 的左缘必须与带首行一致"
                        + "（块内左缘不齐）: " + extent[0] + " vs " + left, left, extent[0]);
                Assert.assertEquals("像素级：横向带#" + b + " 在 y=" + y + " 的右缘必须与带首行一致"
                        + "（逐行自字宽口径在此会红）: " + extent[1] + " vs " + right, right, extent[1]);
            }
        }
        Assert.assertTrue("像素级：至少 " + MIN_MULTIROW_RUNS + " 个底色带必须纵向跨过 >= 2 行"
                + "（旧「每行一节点 + 卡片列 gap」最高只有 1 行 → 该地板即红），实测 " + multiRowRuns,
                multiRowRuns >= MIN_MULTIROW_RUNS);

        // 正对照：无围栏卡零底色像素（同一画布同一扫描器，证明「没有缝」不是「什么都没扫到」）
        int controlHits = countColor(canvas, titleCardBox(shell), codeBg);
        Assert.assertEquals("正对照：标题卡区域必须零围栏底色像素", 0, controlHits);
    }

    /** 底色带：按「连续若干行、每行水平跨度相同」聚合出的矩形（形状无关，只看像素）。 */
    private static List<int[]> backdropBands(int[] canvas, int codeBg) {
        List<int[]> bands = new ArrayList<int[]>();
        int y = 0;
        while (y < CANVAS_H) {
            int[] extent = firstExtent(canvas, y, codeBg);
            if (extent == null) {
                y++;
                continue;
            }
            int top = y;
            int bottom = y;
            while (bottom + 1 < CANVAS_H) {
                int[] next = firstExtent(canvas, bottom + 1, codeBg);
                if (next == null || next[0] != extent[0] || next[1] != extent[1]) {
                    break;
                }
                bottom++;
            }
            bands.add(new int[] {top, bottom, extent[0], extent[1]});
            y = bottom + 1;
        }
        return bands;
    }

    /** 第 y 行上第一段底色跨度 [left,right]；无则 null。 */
    private static int[] firstExtent(int[] canvas, int y, int codeBg) {
        int row = y * CANVAS_W;
        for (int x = 0; x < CANVAS_W; x++) {
            if (canvas[row + x] != codeBg) {
                continue;
            }
            int start = x;
            while (x < CANVAS_W && canvas[row + x] == codeBg) {
                x++;
            }
            return new int[] {start, x - 1};
        }
        return null;
    }

    /** 第 y 行上包含 fromX 的那段底色跨度。 */
    private static int[] rowExtent(int[] canvas, int y, int codeBg, int fromX) {
        int row = y * CANVAS_W;
        Assert.assertTrue("y=" + y + " 处 x=" + fromX + " 不是底色像素", canvas[row + fromX] == codeBg);
        int left = fromX;
        while (left > 0 && canvas[row + left - 1] == codeBg) {
            left--;
        }
        int right = fromX;
        while (right + 1 < CANVAS_W && canvas[row + right + 1] == codeBg) {
            right++;
        }
        return new int[] {left, right};
    }

    /** 第 x 列上所有「底色连续游程」[top,bottom]，按 y 升序。 */
    private static List<int[]> runsInColumn(int[] canvas, int x, int codeBg) {
        List<int[]> runs = new ArrayList<int[]>();
        int y = 0;
        while (y < CANVAS_H) {
            if (canvas[y * CANVAS_W + x] != codeBg) {
                y++;
                continue;
            }
            int top = y;
            while (y < CANVAS_H && canvas[y * CANVAS_W + x] == codeBg) {
                y++;
            }
            runs.add(new int[] {top, y - 1});
        }
        return runs;
    }

    private static int countColor(int[] canvas, AnchorRect area, int color) {
        int n = 0;
        int y1 = Math.min(CANVAS_H, area.getY() + area.getHeight());
        int x1 = Math.min(CANVAS_W, area.getX() + area.getWidth());
        for (int y = Math.max(0, area.getY()); y < y1; y++) {
            for (int x = Math.max(0, area.getX()); x < x1; x++) {
                if (canvas[y * CANVAS_W + x] == color) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int countColorAll(int[] canvas, int color) {
        int n = 0;
        for (int pixel : canvas) {
            if (pixel == color) {
                n++;
            }
        }
        return n;
    }

    /** 「标题」卡（第一张卡）的绝对盒——正对照取样区。 */
    private static AnchorRect titleCardBox(SceneNode shell) {
        for (SceneNode card : shell.__getChildren()) {
            if (!card.__getChildren().isEmpty()
                    && "标题（ATX 1..6 级）".equals(card.__getChildren().get(0).getText())) {
                return SceneGeometry.absoluteBox(card, 0, 0);
            }
        }
        throw new AssertionError("找不到标题卡（正对照取样区失效）");
    }

    /** 把后端收到的实心面按调用顺序覆盖式栅格进 ARGB 缓冲（文本类调用忽略）。 */
    private static int[] rasterizeSolidFaces(RecordingRenderBackend backend) {
        int[] canvas = new int[CANVAS_W * CANVAS_H];
        int solid = 0;
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
            String m = call.methodName();
            if (!"fillRect".equals(m) && !"drawSurface".equals(m)) {
                continue;
            }
            int left = Math.max(0, call.getInt(0));
            int top = Math.max(0, call.getInt(1));
            int right = Math.min(CANVAS_W, call.getInt(2));
            int bottom = Math.min(CANVAS_H, call.getInt(3));
            int color = call.getInt(4);
            if (color == 0 || right <= left || bottom <= top) {
                continue;
            }
            solid++;
            for (int y = top; y < bottom; y++) {
                int row = y * CANVAS_W;
                for (int x = left; x < right; x++) {
                    canvas[row + x] = color;
                }
            }
        }
        Assert.assertTrue("反空跑：画布必须收到实心面调用（实测 " + solid + " 条）", solid >= 4);
        return canvas;
    }


    // ==================== M10a 页面级像素锁：引用竖条=每组一根连续条 ====================

    /** 引用样本（与 MarkdownPage SAMPLES「嵌套引用」卡同源；层级序列实测 1/2/3/3/1/1/1）。 */
    private static final String QUOTE_SAMPLE =
            "> 一层引用" + LF + ">> 二层引用" + LF + ">>> 三层引用" + LF
            + "> 惰性续行（下一行不带 > 仍属本引用块）" + LF + LF
            + "> 引用里套列表：" + LF + "> - 子项甲" + LF + "> - 子项乙";

    /** 列表样本（与「列表与续行」卡同源）。 */
    private static final String LIST_SAMPLE =
            "- 第一项首行" + LF + "  第一项的缩进续行（懒延续）" + LF + "- 第二项" + LF
            + "  - 嵌套子项" + LF + "    - 更深层子项" + LF + "3. 有序三" + LF
            + "4) 有序四（右括号定界）" + LF + "   有序四项的续行";

    /** 反 ∅ 地板：竖条像素总量（实测 480：(2×146)+(2×58)+(2×36)）。 */
    private static final int MIN_BAR_PIXELS = 200;
    /** 反 ∅ 地板：竖条列数（一层/二层/三层 = 3 列）。 */
    private static final int MIN_BAR_COLUMNS = 3;

    /**
     * M10a：<b>纯像素</b>锁——引用竖条合并成连续条，且<b>合并不得越级拉长</b>。
     *
     * <p>链路同 {@link #markdownPageCodeBackdropIsOneContinuousPixelBlock()}：headless 造页 →
     * {@code ScenePaintCapture} → 实心面栅格 → 只在像素上断言。取样区限定为「嵌套引用」卡
     * （真横线与引用竖条共用装饰色，跨卡取样会互相污染；卡内无横线元素）。</p>
     *
     * <p>判据（层级由 L1 接缝现取，不硬编码）：对每个引用层级 L，其 2px 列上「accent 像素
     * 游程」必须与「接缝中 quoteLevel&ge;L 的行的极大连续段」<b>一一对应且逐端相等</b>——
     * 每个连续段恰一根条、内部零空档、顶/底恰为首行顶与末行底；二层列只覆盖它那几行、
     * 三层列只覆盖它那几行（越级拉长 → 高/顶底不等 → 红）。反 ∅ 地板：bar 像素 &ge; 200、
     * 列数 &ge; 3、至少一个多行组。正对照内置：各列 x 间距恒等于引用步长（列真在层槽位上，
     * 不是同一条被扫三遍）。</p>
     */
    @Test
    public void markdownPageQuoteBarIsOneContinuousColumn() {
        PlaygroundPage page = PlaygroundPageRegistry.lookup("markdown");
        Assert.assertNotNull(page);
        SceneNode shell = page.build(runtime).get();
        RecordingRenderBackend backend = ScenePaintCapture.paintAndCapture(shell, CANVAS_W, CANVAS_H);
        SceneNode quoteCard = cardByTitle(shell, "嵌套引用");
        AnchorRect box = SceneGeometry.absoluteBox(quoteCard, 0, 0);
        int gapPx = quoteCard.getGap();

        // 接缝侧真值：行层级序列 + 步长 + 竖条宽 + 行高（全现取，不硬编码）
        TextLayoutService svc = FontService.getInstance().getTextLayoutService();
        MarkdownStyleTable styles = MarkdownStyleTable.defaults();
        styles.setDefaultFontSizePx(BASE_FONT_PX);
        styles.setThematicBreakText("");
        TextStyle base = new TextStyle();
        base.setColor(0xFFFFFFFF);
        List<MarkdownLayoutLine> seam = MarkdownPainter.wrapLayoutLines(
                MarkdownDocument.parse(QUOTE_SAMPLE).toLayoutLines(styles, base),
                svc, 600, BASE_FONT_PX);
        int[] levels = new int[seam.size()];
        int maxLevel = 0;
        int accent = 0;
        int step = 0;
        int barWidth = 0;
        int rowH = 0;
        for (int i = 0; i < seam.size(); i++) {
            MarkdownLayoutLine line = seam.get(i);
            levels[i] = line.getQuoteLevel();
            maxLevel = Math.max(maxLevel, levels[i]);
            Assert.assertTrue("引用样本每行都必须是引用行（样本失效？）", levels[i] > 0);
            if (accent == 0) {
                accent = line.getAccentArgb();
                step = line.getIndentStepPx();
                barWidth = line.getBarWidthPx();
                rowH = MarkdownPainter.lineHeightPx(line.getSegments(), svc, BASE_FONT_PX);
            }
        }
        Assert.assertTrue("步长/条宽/行高探针必须 > 0", step > 0 && barWidth > 0 && rowH > 0);

        // 像素侧：卡内 accent 像素的 2px 列（竖条列）
        int[] canvas = rasterizeSolidFaces(backend);
        List<int[]> barColumns = new ArrayList<int[]>(); // [xLeft, xRight]
        int x = 0;
        while (x < CANVAS_W) {
            if (countColorInColumn(canvas, x, box, accent) > 0) {
                int left = x;
                while (x < CANVAS_W && countColorInColumn(canvas, x, box, accent) > 0) {
                    x++;
                }
                barColumns.add(new int[] {left, x - 1});
                continue;
            }
            x++;
        }
        int barPixels = countColor(canvas, box, accent);
        Assert.assertTrue("反 ∅ 地板：卡内竖条像素 >= " + MIN_BAR_PIXELS + "，实测 " + barPixels,
                barPixels >= MIN_BAR_PIXELS);
        Assert.assertTrue("反 ∅ 地板：竖条列数 >= " + MIN_BAR_COLUMNS + "，实测 "
                + barColumns.size(), barColumns.size() >= MIN_BAR_COLUMNS);
        Assert.assertEquals("竖条列数必须恰等于最大引用层级（每层一列）", maxLevel,
                barColumns.size());

        // 正对照：列宽恒 = barWidth，相邻列 x 差恒 = step（列真在各层槽位，非同一列重扫）
        for (int c = 0; c < barColumns.size(); c++) {
            Assert.assertEquals("第 " + c + " 层列宽 == 接缝 barWidthPx", barWidth,
                    barColumns.get(c)[1] - barColumns.get(c)[0] + 1);
            if (c > 0) {
                Assert.assertEquals("层间步距 == 接缝 indentStepPx", step,
                        barColumns.get(c)[0] - barColumns.get(c - 1)[0]);
            }
        }

        // 行 y（drawSegments 调用按 y 升序 = 行序），行数与接缝等值
        List<Integer> ys = drawSegmentsTopsIn(backend, box);
        Assert.assertEquals("绘制行数必须与接缝行数一一对应", seam.size(), ys.size());
        // 反自证陷阱（M10a「文字位置一字不动」）：行距必须恒 = 行高 + 卡片列 gap——
        // 这是分组前的平铺口径，独立于游程本身；若分组内列错用 gap（0 或硬编码 8 之外），
        // 行距先红，而不是被游程公式跟着一起挪掉。
        for (int i = 1; i < ys.size(); i++) {
            Assert.assertEquals("引用区行距必须恒 = 行高 + card.getGap()（文字位置一字不动）",
                    rowH + gapPx, ys.get(i).intValue() - ys.get(i - 1).intValue());
        }

        // 每个层级：接缝的极大连续段 ↔ 像素游程，逐端相等（内部空档恒 0 由「一段一游程」保证）
        int multiRowGroups = 0;
        for (int level = 1; level <= maxLevel; level++) {
            List<int[]> spans = maximalRuns(levels, level);
            if (spans.isEmpty()) {
                continue;
            }
            int colX = barColumns.get(level - 1)[0];
            for (int side = 0; side < barWidth; side++) {
                List<int[]> runs = clippedRunsInColumn(canvas, colX + side, box, accent);
                Assert.assertEquals("第 " + level + " 层列 x=" + (colX + side)
                        + " 的游程数必须等于该层连续段数（合并不得跨组连条；内部空档必须为 0）",
                        spans.size(), runs.size());
                for (int k = 0; k < spans.size(); k++) {
                    int[] span = spans.get(k);
                    if (span[1] - span[0] >= 1) {
                        multiRowGroups++;
                    }
                    int rows = span[1] - span[0] + 1;
                    int expectedTop = ys.get(span[0]).intValue();
                    int expectedBottom = ys.get(span[1]).intValue() + rowH - 1;
                    Assert.assertEquals("第 " + level + " 层第 " + k + " 组条顶必须贴首行顶（越级拉长/错位 → 红）",
                            expectedTop, runs.get(k)[0]);
                    Assert.assertEquals("第 " + level + " 层第 " + k + " 组条底必须贴末行底（合并不得越级拉长）",
                            expectedBottom, runs.get(k)[1]);
                    // 独立公式校验（不借 ys 反推）：条高 == rows×行高 + (rows-1)×card.gap
                    Assert.assertEquals("第 " + level + " 层第 " + k + " 组条高必须 == n×行高+(n-1)×card.gap"
                                    + "（内列 gap 被改数 → 此式与行距式互证，双保险）",
                            rows * rowH + (rows - 1) * gapPx,
                            runs.get(k)[1] - runs.get(k)[0] + 1);
                }
            }
        }
        Assert.assertTrue("反 ∅ 地板：至少一个多行组（连续段 >= 2 行），实测 " + multiRowGroups,
                multiRowGroups >= 1);
    }

    /** levels[i] &gt;= level 的极大连续 index 段 [first,last]。 */
    private static List<int[]> maximalRuns(int[] levels, int level) {
        List<int[]> out = new ArrayList<int[]>();
        int i = 0;
        while (i < levels.length) {
            if (levels[i] < level) {
                i++;
                continue;
            }
            int start = i;
            while (i < levels.length && levels[i] >= level) {
                i++;
            }
            out.add(new int[] {start, i - 1});
        }
        return out;
    }

    /** 卡内 accent 像素的非零计数（列发现扫描用）。 */
    private static int countColorInColumn(int[] canvas, int x, AnchorRect box, int color) {
        int n = 0;
        int y1 = Math.min(CANVAS_H, box.getY() + box.getHeight());
        for (int y = Math.max(0, box.getY()); y < y1; y++) {
            if (canvas[y * CANVAS_W + x] == color) {
                n++;
            }
        }
        return n;
    }

    /** 限定在卡盒范围内的纵向游程。 */
    private static List<int[]> clippedRunsInColumn(int[] canvas, int x, AnchorRect box, int color) {
        List<int[]> all = runsInColumn(canvas, x, color);
        List<int[]> out = new ArrayList<int[]>();
        for (int[] run : all) {
            if (run[0] >= box.getY() && run[1] < box.getY() + box.getHeight()) {
                out.add(run);
            }
        }
        return out;
    }

    /** 卡盒内 drawSegments 调用点的 y（按 y 升序 = 行序）。 */
    private static List<Integer> drawSegmentsTopsIn(RecordingRenderBackend backend, AnchorRect box) {
        List<Integer> ys = new ArrayList<Integer>();
        for (int[] pt : drawSegmentsPointsIn(backend, box)) {
            ys.add(Integer.valueOf(pt[1]));
        }
        return ys;
    }

    /** 卡盒内 drawSegments 调用点的 (x,y)（按 y 升序配对）。 */
    private static List<int[]> drawSegmentsPointsIn(RecordingRenderBackend backend, AnchorRect box) {
        List<int[]> pts = new ArrayList<int[]>();
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
            if (!"drawSegments".equals(call.methodName())) {
                continue;
            }
            int y = call.getInt(2);
            if (y >= box.getY() && y < box.getY() + box.getHeight()) {
                pts.add(new int[] {call.getInt(1), y});
            }
        }
        java.util.Collections.sort(pts, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.valueOf(a[1]).compareTo(Integer.valueOf(b[1]));
            }
        });
        return pts;
    }

    // ==================== M10b 页面级锁：列表续行对齐正文列 ====================

    /**
     * 「懒续行首墨 x == 同项标记行正文首墨 x（不是标记 x）」的像素级形态：
     * 断在<b>后端收到的 drawSegments.x</b> 上——它就是真机 GL 的字形起点几何。
     * 判据：以卡内最小 drawSegments.x 为列原点，每行 x 位移恒等于该行接缝
     * {@code leftInsetPx}；每个带懒延续的项，其延续行位移 == <b>独立量出</b>（逐码点
     * resolveAdvance，不经被测写入口）的标记段宽，且严格大于标记行位移。
     * 反 ∅ 地板：参与行 >= 8、对齐延续行（位移&gt;0）>= 2。
     */
    @Test
    public void markdownPageListContinuationAlignsToContentColumn() {
        PlaygroundPage page = PlaygroundPageRegistry.lookup("markdown");
        Assert.assertNotNull(page);
        SceneNode shell = page.build(runtime).get();
        RecordingRenderBackend backend = ScenePaintCapture.paintAndCapture(shell, CANVAS_W, CANVAS_H);
        SceneNode listCard = cardByTitle(shell, "列表与续行");
        AnchorRect box = SceneGeometry.absoluteBox(listCard, 0, 0);

        TextLayoutService svc = FontService.getInstance().getTextLayoutService();
        MarkdownStyleTable styles = MarkdownStyleTable.defaults();
        styles.setDefaultFontSizePx(BASE_FONT_PX);
        styles.setThematicBreakText("");
        TextStyle base = new TextStyle();
        base.setColor(0xFFFFFFFF);
        List<MarkdownLayoutLine> seam = MarkdownPainter.wrapLayoutLines(
                MarkdownDocument.parse(LIST_SAMPLE).toLayoutLines(styles, base),
                svc, 600, BASE_FONT_PX);
        List<int[]> pts = drawSegmentsPointsIn(backend, box);
        Assert.assertEquals("绘制行数与接缝一一对应", seam.size(), pts.size());

        int originX = Integer.MAX_VALUE;
        for (int[] pt : pts) {
            originX = Math.min(originX, pt[0]);
        }
        int shifted = 0;
        for (int i = 0; i < seam.size(); i++) {
            int offset = pts.get(i)[0] - originX;
            Assert.assertEquals("第 " + i + " 行 drawSegments.x 位移必须恒等于接缝 leftInsetPx"
                    + "（页面不得另算第二套真相）", seam.get(i).getLeftInsetPx(), offset);
            if (seam.get(i).getLeftInsetPx() > 0) {
                shifted++;
                // 独立 oracle：该块标记段逐码点推进宽（ceil）
                int blockId = seam.get(i).getBlockId();
                int markerWidth = 0;
                for (MarkdownLayoutLine line : seam) {
                    if (line.getBlockId() == blockId
                            && line.getKind() == MarkdownLayoutLine.Kind.LIST
                            && !line.getSegments().isEmpty()) {
                        markerWidth = oracleAdvance(svc, line.getSegments().get(0));
                        break;
                    }
                }
                Assert.assertTrue("对齐行位移必须 == 独立量出的标记宽（实测 offset="
                        + seam.get(i).getLeftInsetPx() + " oracle=" + markerWidth + "）",
                        seam.get(i).getLeftInsetPx() == markerWidth && markerWidth > 0);
            }
        }
        Assert.assertTrue("反 ∅ 地板：>=2 个对齐续行，实测 " + shifted, shifted >= 2);
        Assert.assertTrue("反 ∅ 地板：参与行 >= 8，实测 " + pts.size(), pts.size() >= 8);
        // 正对照：标记行本身恒在原点上（x 位移 0）——「对齐正文列」≠「整项平移」
        int markerRows = 0;
        for (int i = 0; i < seam.size(); i++) {
            MarkdownLayoutLine line = seam.get(i);
            if (line.getKind() == MarkdownLayoutLine.Kind.LIST && !line.getSegments().isEmpty()
                    && isMarkerSegment(line.getSegments().get(0).getText())) {
                Assert.assertEquals("标记行 x 位移必须为 0（首墨仍在标记列）", 0,
                        pts.get(i)[0] - originX);
                markerRows++;
            }
        }
        Assert.assertTrue("反 ∅ 地板：>=5 个标记行（2 无序 + 2 嵌套 + 2 有序），实测 "
                + markerRows, markerRows >= 5);
    }

    /** 独立标记宽 oracle（逐码点 resolveAdvance，与 L2 写入路径零共享实现）。 */
    private static int oracleAdvance(TextLayoutService svc, TextSegment segment) {
        double width = 0.0D;
        String text = segment.getText();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += svc.resolveAdvance(cp, segment.getStyle(), BASE_FONT_PX);
            i += Character.charCount(cp);
        }
        return (int) Math.ceil(width);
    }

    private static boolean isMarkerSegment(String text) {
        return text.matches(" *\u2022 ") || text.matches("[0-9]+[.)] ");
    }

    private static SceneNode cardByTitle(SceneNode shell, String title) {
        for (SceneNode card : shell.__getChildren()) {
            if (!card.__getChildren().isEmpty() && title.equals(card.__getChildren().get(0).getText())) {
                return card;
            }
        }
        throw new AssertionError("找不到卡: " + title);
    }

    /** 底色探针：从 L1 接缝取围栏底色（与页面同源，不硬编码常量）。 */
    private static int probeCodeBackdropArgb() {
        for (MarkdownLayoutLine line : probeFenceLines()) {
            if (line.getKind() == MarkdownLayoutLine.Kind.CODE && line.getBackgroundArgb() != 0) {
                return line.getBackgroundArgb();
            }
        }
        throw new AssertionError("L1 接缝未给出围栏底色（探针失效）");
    }

    /** 行高探针：同一段围栏文本经 L2 折行后的行高（与页面同一字号 14）。 */
    private static int probeCodeRowHeightPx() {
        TextLayoutService svc = FontService.getInstance().getTextLayoutService();
        for (MarkdownLayoutLine line : MarkdownPainter.wrapLayoutLines(probeFenceLines(), svc,
                CANVAS_W, BASE_FONT_PX)) {
            if (line.getKind() == MarkdownLayoutLine.Kind.CODE && !line.getSegments().isEmpty()) {
                return Math.max(1, MarkdownPainter.lineHeightPx(line.getSegments(), svc,
                        BASE_FONT_PX));
            }
        }
        throw new AssertionError("L2 接缝未给出行高（探针失效）");
    }

    private static List<MarkdownLayoutLine> probeFenceLines() {
        char tick = (char) 0x60;
        String src = String.valueOf(tick) + tick + tick + LF + "int a = 1;" + LF
                + "int bb = 2222;" + LF + tick + tick + tick;
        TextStyle base = new TextStyle();
        base.setColor(0xFFFFFFFF);
        return MarkdownDocument.parse(src).toLayoutLines(MarkdownStyleTable.defaults(), base);
    }
}
