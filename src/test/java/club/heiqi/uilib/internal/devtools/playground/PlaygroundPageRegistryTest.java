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
