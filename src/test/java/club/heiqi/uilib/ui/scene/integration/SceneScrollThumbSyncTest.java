package club.heiqi.uilib.ui.scene.integration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneScrollContainer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * 滚轮 -> thumb 同步端到端集成测试 —— 验证 SceneScrollContainer.attach 建出的滚动列表，
 * 滚轮滚动后 scrollbar thumb 的 transform translateY 同步反映滚动进度。
 *
 * <p>doFrame 时序（参照 SceneScrollbarTest）：layout（产出 LayoutBox）-> 桥接 layoutEpoch
 * -> flush（驱动 scrollbar LAYOUT/COMPOSITE bind 读最新 LayoutBox 与 scrollSignal）
 * -> layout（清掉 effect 写入的 selfLayoutDirty）。</p>
 *
 * <p>thumb translateY 由 scrollbar 的 COMPOSITE bind（scrollSignal 驱动）物化，滚轮 handler 写
 * attach 内部 scrollSignal 后仅需 runtime.flush 即可让 translateY 更新。</p>
 *
 * <p>归类 L3 集成层：依赖 control（SceneScrollContainer/SceneScrollbar）+ runtime（bind/flush/route）
 * + input（滚轮 route）+ layout（maxScrollY/thumb 几何依赖 LayoutBox）多子系统协作。</p>
 *
 * <p>守 I1 signal-first（滚动位置经 attach 内部 scrollSignal 驱动）、I7 GEOMETRY 级滚动、
 * I4 transform 仅标 COMPOSITE（thumb translateY 变化不打 LAYOUT 重排）。</p>
 */
public class SceneScrollThumbSyncTest {

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int ITEM_COUNT = 40;
    private static final int ITEM_HEIGHT = 30;

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    /** 语义化交互注入 harness（route 根 + scroll 入口）；其 runtime 即上方 runtime 字段 */
    private SceneInteractionHarness harness;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        sceneRoot = new SceneNode();
        // 根填满 canvas 高，使 container(flexGrow=1) 撑满剩余高 -> viewport 收到确定高 -> scrollable 钉死
        sceneRoot.setFillParentHeight(true);
        // 挂载路由根并对齐 layout，供 harness.scroll 取中心 + route
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * 执行完整帧：layout -> 桥接 layoutEpoch -> flush -> layout，
     * 模拟宿主帧循环 + layoutDoneSignal 桥接，使 scrollbar LAYOUT/COMPOSITE bind 读到最新几何。
     */
    private void doFrame() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        runtime.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 构建 40 item 滚动列表（attach 默认带 bar），返回 container。
     * 结构：container(ROW) -> [viewport(scrollable), scrollbarColumn] -> thumb。
     *
     * @return container 节点
     */
    private SceneNode buildScrollList() {
        SceneNode container = SceneScrollContainer.attach(runtime, sceneRoot,
                content -> {
                    for (int i = 0; i < ITEM_COUNT; i++) {
                        SceneNode item = new SceneNode();
                        item.setPreferredHeight(ITEM_HEIGHT);
                        content.appendChild(item);
                    }
                });
        return container;
    }

    /**
     * 在 container 直接子中定位可滚动视口（isScrollable==true）。
     *
     * @param container 滚动容器根
     * @return viewport 节点
     */
    private static SceneNode findViewport(SceneNode container) {
        for (SceneNode child : container.__getChildren()) {
            if (child.isScrollable()) {
                return child;
            }
        }
        return null;
    }

    /**
     * 在 container 直接子中定位 scrollbar column（非 scrollable 的那个），
     * 再取其唯一直接子作为 thumb（SceneScrollbarTest 验证 column 含 1 个 thumb）。
     *
     * @param container 滚动容器根
     * @return thumb 节点
     */
    private static SceneNode findThumb(SceneNode container) {
        for (SceneNode child : container.__getChildren()) {
            if (!child.isScrollable()) {
                Assert.assertEquals("scrollbar column 应仅含 1 个 thumb",
                        1, child.__getChildren().size());
                return child.__getChildren().get(0);
            }
        }
        return null;
    }

    /**
     * 端到端：建 40 item 列表 -> 初始 thumb translateY=0 -> 滚轮向下滚 -> flush ->
     * thumb translateY > 0（滚动进度同步反映到 thumb 位置）。
     */
    @Test
    public void wheelScrollShouldSyncThumbTranslateY() {
        SceneNode container = buildScrollList();
        doFrame();

        SceneNode viewport = findViewport(container);
        Assert.assertNotNull("应建出可滚动 viewport", viewport);
        SceneNode thumb = findThumb(container);
        Assert.assertNotNull("应建出 scrollbar thumb", thumb);

        // 初始（未滚动）thumb translateY == 0
        Assert.assertNotNull("doFrame 后 thumb transform 应已初始化", thumb.getTransform());
        Assert.assertEquals("初始 thumb translateY=0（未滚动）",
                0f, thumb.getTransform().translateY, 0.5f);

        // 滚轮向下滚（wheelDelta 负值 -> offset 增大）
        harness.scroll(viewport, -500);
        runtime.flush(); // 物化 scrollbar COMPOSITE bind -> thumb translateY 更新

        float translateYAfter = thumb.getTransform().translateY;
        Assert.assertTrue("滚轮滚动后 thumb translateY 应 > 0（同步反映滚动进度），实际=" + translateYAfter,
                translateYAfter > 0f);
    }

    /**
     * 多次滚动 thumb translateY 单调递增 —— 验证持续滚动时 thumb 位置持续跟随。
     */
    @Test
    public void thumbTranslateYShouldIncreaseWithMoreScroll() {
        SceneNode container = buildScrollList();
        doFrame();

        SceneNode viewport = findViewport(container);
        SceneNode thumb = findThumb(container);

        // 第一段滚动
        harness.scroll(viewport, -300);
        runtime.flush();
        float translateY1 = thumb.getTransform().translateY;
        Assert.assertTrue("第一段滚动后 translateY > 0，实际=" + translateY1, translateY1 > 0f);

        // 第二段继续滚动（累计）
        harness.scroll(viewport, -300);
        runtime.flush();
        float translateY2 = thumb.getTransform().translateY;
        Assert.assertTrue("第二段滚动后 translateY 单调递增（translateY2 > translateY1），"
                        + "translateY1=" + translateY1 + " translateY2=" + translateY2,
                translateY2 > translateY1);
    }
}
