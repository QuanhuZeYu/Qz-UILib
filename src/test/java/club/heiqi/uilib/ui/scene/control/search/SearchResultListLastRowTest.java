package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneGridWindow;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * 「结果网格最后一排完全可见」派生 oracle 守卫（task-2；三条判据各自独立用例，便于变异检查逐条变红）。
 *
 * <h3>被钉死的缺陷：字号真值分叉 ⇒ 内容高 &gt; 模型内容高 ⇒ 末排不可达</h3>
 * <p>网格几何由 P5 度量快照派生：{@code PickerMetrics.fs = 档位基准字号 × 字号倍率}（标准档 12），
 * {@link GridMetrics} 用它算轨道高与 stride；而<b>单元标签实际按节点字号链渲染</b>
 * （宿主 portal 内容根 {@code fontScope}；宿主未声明字号时为节点层 4b 默认 16）。分叉时
 * 轨道高按 12 算出 65、标签真实行高按 16 算出 20 ⇒ 单元真实内容高 70 &gt; 65
 * ⇒ 行真实 pitch 76 &gt; 模型 stride 71 ⇒ 内容高 &gt; {@code totalRows*stride}
 * ⇒ {@code maxScrollPx} 比内容真底短一截：滚到底时末排尾部被裁（用户症状「最后一排显示不完整」）。</p>
 *
 * <h3>oracle（全部由可视高 / 行高 / 间距 / 内边距派生，无硬编码常量）</h3>
 * <ol>
 *   <li><b>(a) 内容高守恒 / 单一窗口数学</b>：{@code SceneGeometry.maxScrollY(viewport)}
 *       {@code == windowModel.maxScrollPx}；</li>
 *   <li><b>(b) 行 pitch == 模型 stride</b>：真实行距必须等于窗口数学使用的 stride
 *       （{@code (maxScrollPx + 视口高) / totalRows}）；</li>
 *   <li><b>(c) 末排完全可见</b>：滚到模型上限后，末排行盒完全落在 viewport 盒内
 *       （{@code rowTop ≥ vpTop ∧ rowBottom ≤ vpBottom}）且窗口贴住末行。</li>
 * </ol>
 *
 * <p>夹具自带<b>字体敏感度量替身</b>（行高随字号单调递增）：测试统一替身 {@code FixedTextMeasurer}
 * 的固定行高会让本缺陷完全隐身（δ=0），故本类不复用。</p>
 */
public class SearchResultListLastRowTest {

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    /** 包装节点高度 = viewport 可视高。 */
    private static final int VIEWPORT_HEIGHT = 200;
    /** 宿主字号真值（节点层 4b 默认，与 ScenePickerPanelTest.DEFAULT_FONT_SIZE 同源）。 */
    private static final int HOST_FONT_SIZE = 16;
    /** P5 标准档基准字号（度量派生输入）。 */
    private static final int DENSITY_FONT_SIZE = 12;
    /** 结果区内宽预算（列数派生输入）。 */
    private static final int INNER_WIDTH = 200;
    /** 60 项 / 3 列 = 20 行：内容高远超视口，末排进入场景。 */
    private static final int ITEM_COUNT = 60;

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FontSensitiveMeasurer measurer = new FontSensitiveMeasurer();
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 字体敏感度量替身：行高 = {@code round(fs × 1.25)}（真机 font 运行时「行高随字号递增」的最小复刻）。 */
    private static final class FontSensitiveMeasurer implements SceneTextMeasurer {

        private int epoch;

        @Override
        public int measureWidth(String text, int fontSizePx) {
            return (text == null ? 0 : text.length()) * 8;
        }

        @Override
        public int lineHeight(int fontSizePx) {
            return Math.max(1, (int) Math.rint(fontSizePx * 1.25));
        }

        @Override
        public int ascent(int fontSizePx) {
            return Math.max(1, (int) Math.rint(fontSizePx));
        }

        @Override
        public int descent(int fontSizePx) {
            return Math.max(1, (int) Math.rint(fontSizePx * 0.25));
        }

        @Override
        public int lineGap(int fontSizePx) {
            return 0;
        }

        @Override
        public int epoch() {
            return epoch;
        }

        @Override
        public List<String> splitLines(String text, int fontSizePx, int wrapWidth, int textMode) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }

    private static List<Item> items(int count) {
        List<Item> list = new ArrayList<Item>();
        for (int i = 0; i < count; i++) {
            list.add(new Item(Integer.valueOf(i), null, "item" + i));
        }
        return list;
    }

    private void layout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    /** 夹具：度量通道（派生字号 12）+ 宿主字号真值 16（层 2 声明在包装节点上）。 */
    private final class Fixture {
        final SearchResultList.Result result;
        final MountHandle handle;
        final GridMetrics metrics;

        Fixture() {
            Signal<List<Item>> itemsSignal = Signal.create(items(ITEM_COUNT));
            Signal<Integer> highlight = Signal.create(Integer.valueOf(-1));
            Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
            // 度量快照只含派生字号 12 —— 真值分叉的来源（宿主字号不参与派生）。
            this.metrics = GridMetrics.deriveDensity(rt, DENSITY_FONT_SIZE, 40, 100, 0, INNER_WIDTH);
            Signal<GridMetrics> metricsSignal = Signal.create(metrics);
            SearchResultList.Props props = new SearchResultList.Props(
                    itemsSignal, 0, 64, 64, 8, 8, enabled, item -> { }, highlight, highlight::set,
                    null, null, SearchResultList.Props.UNSPECIFIED_TOTAL_ITEMS, 0, 3, null, null,
                    metricsSignal);
            SearchResultList.Result[] holder = new SearchResultList.Result[1];
            this.handle = rt.mount(sceneRoot, () -> {
                SceneNode wrapper = new SceneNode();
                wrapper.setPreferredHeight(VIEWPORT_HEIGHT);
                // 宿主字号真值（层 2）：16 ≠ 度量派生输入 12，复刻 ScenePickerPanel 的 portal fontScope。
                wrapper.setFontScope(HOST_FONT_SIZE);
                holder[0] = SearchResultList.create(rt, props);
                wrapper.appendChild(holder[0].root());
                return wrapper;
            });
            this.result = holder[0];
            rt.flush();
            layout();
            // 收敛拍：首拍 layoutDone 后 viewportHeightPx 更新、窗口收敛到实际视口行数。
            layout();
        }

        SceneNode vp() {
            return result.viewport();
        }

        SceneGridWindow.WindowModel windowModel() {
            return result.windowModel().get();
        }

        SceneNode content() {
            return vp().__getChildren().get(0);
        }

        SceneNode rowsContainer() {
            return content().__getChildren().get(1);
        }

        SceneNode cell(int rowIndex, int colIndex) {
            return rowsContainer().__getChildren().get(rowIndex).__getChildren().get(colIndex);
        }

        int viewportHeight() {
            Object cached = vp().getCachedLayout();
            return cached instanceof LayoutBox ? ((LayoutBox) cached).getHeight() : -1;
        }

        /** 滚轮滚到底（handler 按 SceneGeometry.maxScrollY clamp → 回夹 effect 收敛到 maxScrollPx）。 */
        void scrollToBottom() {
            for (int i = 0; i < 3; i++) {
                AnchorRect box = SceneGeometry.absoluteBox(vp(), 0, 0);
                int cx = box.getX() + box.getWidth() / 2;
                int cy = box.getY() + box.getHeight() / 2;
                InputFrameBuilder fb = new InputFrameBuilder(cx, cy);
                fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, cx, cy,
                        SceneMouseButton.NONE, -100000, 0, 0,
                        false, false, false, false, 1000L));
                rt.route(sceneRoot, fb.drainFrame(), 0, 0);
                rt.flush();
                layout();
            }
        }
    }

    /** 单元标签节点（单元结构 = [icon, label]）。 */
    private static SceneNode labelOf(SceneNode cell) {
        List<SceneNode> children = cell.__getChildren();
        return children.get(children.size() - 1);
    }

    /** 前置：字号真值分叉场景成立（标签按宿主 16 渲染，度量按 12 派生），且内容高超过视口。 */
    private static void assertForkScenario(Fixture f) {
        SceneNode label = labelOf(f.cell(0, 0));
        Assert.assertEquals("前置：标签必须按宿主字号渲染", HOST_FONT_SIZE, label.effectiveFontSize());
        Assert.assertNotEquals("前置：派生字号与节点字号必须分叉（否则本用例不构成场景）",
                DENSITY_FONT_SIZE, label.effectiveFontSize());
        Assert.assertTrue("前置：内容高必须超过视口（否则末排不进场景）",
                f.windowModel().maxScrollPx() > 0);
    }

    /** oracle (a)：内容高守恒 —— 几何闭式与窗口闭式必须逐值相等（唯一窗口数学）。 */
    @Test
    public void contentHeightMatchesWindowScrollCeiling() {
        Fixture f = new Fixture();
        assertForkScenario(f);
        Assert.assertEquals("oracle(a): SceneGeometry.maxScrollY(viewport) 必须等于 windowModel.maxScrollPx",
                SceneGeometry.maxScrollY(f.vp()), f.windowModel().maxScrollPx());
    }

    /** oracle (b)：真实行 pitch 必须等于窗口数学使用的 stride。 */
    @Test
    public void rowPitchEqualsModelStride() {
        Fixture f = new Fixture();
        assertForkScenario(f);
        SceneGridWindow.WindowModel model = f.windowModel();
        int stride = (model.maxScrollPx() + f.viewportHeight()) / model.totalRows();
        Assert.assertTrue("前置：挂载至少 2 行才能测 pitch", f.rowsContainer().__getChildren().size() >= 2);
        int pitch = SceneGeometry.absoluteBox(f.rowsContainer().__getChildren().get(1), 0, 0).getY()
                - SceneGeometry.absoluteBox(f.rowsContainer().__getChildren().get(0), 0, 0).getY();
        Assert.assertEquals("oracle(b): 行 pitch 必须等于模型 stride", stride, pitch);
    }

    /** oracle (c)：滚到底后末排完全可见（用户症状判据）。 */
    @Test
    public void lastRowFullyVisibleAtMaxScroll() {
        Fixture f = new Fixture();
        assertForkScenario(f);
        f.scrollToBottom();
        SceneGridWindow.WindowModel bottom = f.windowModel();
        Assert.assertEquals("滚到底 = 模型上限（不为吸附牺牲可达性）",
                bottom.maxScrollPx(), f.vp().getScrollOffsetY());
        Assert.assertEquals("窗口贴住末行", bottom.totalRows(),
                bottom.windowStartRow() + bottom.mountedRows());
        SceneNode lastRow = f.rowsContainer().__getChildren().get(bottom.mountedRows() - 1);
        AnchorRect vpBox = SceneGeometry.absoluteBox(f.vp(), 0, 0);
        AnchorRect rowBox = SceneGeometry.absoluteBox(lastRow, 0, 0);
        Assert.assertTrue("oracle(c): 末排顶边应在视口内（rowTop=" + rowBox.getY()
                        + " vpTop=" + vpBox.getY() + "）",
                rowBox.getY() >= vpBox.getY());
        Assert.assertTrue("oracle(c): 末排必须完全可见（rowBottom=" + (rowBox.getY() + rowBox.getHeight())
                        + " vpBottom=" + (vpBox.getY() + vpBox.getHeight()) + "）",
                rowBox.getY() + rowBox.getHeight() <= vpBox.getY() + vpBox.getHeight());
    }
}
