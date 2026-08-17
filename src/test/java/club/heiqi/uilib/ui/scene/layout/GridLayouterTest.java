package club.heiqi.uilib.ui.scene.layout;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * {@link GridLayouter} / {@link GridLayouts} 网格布局单元测试。
 *
 * <p>验证与 FlexLayouter 同构的布局管线：固定列数与自动列数推算、换行、gap、cross/main 对齐、
 * 行高按内容、几何闸门引用稳定（零标脏帧零重写）、padding 坐标事实。流程：
 * {@code engine.layout → __bridgeLayoutEpoch → flush}（网格定位是 layoutDoneSignal 驱动的后置步）。</p>
 */
public class GridLayouterTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 引擎布局一帧并把布局纪元桥接给 runtime（驱动 GridLayouts.attach 的定位 effect）。 */
    private void layoutAndBridge() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        runtime.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        runtime.flush();
    }

    private static LayoutBox box(SceneNode node) {
        return (LayoutBox) node.getCachedLayout();
    }

    private static SceneNode childWithHeight(int height) {
        SceneNode child = new SceneNode();
        child.setPreferredHeight(height);
        return child;
    }

    /** 建 7 个等高于项（preferredHeight=50）的网格容器并接入布局。 */
    private SceneNode buildGrid(GridSpec spec) {
        SceneNode grid = GridLayouts.container(spec);
        sceneRoot.appendChild(grid);
        for (int i = 0; i < 7; i++) {
            grid.appendChild(childWithHeight(50));
        }
        GridLayouts.attach(runtime, grid, spec);
        layoutAndBridge();
        return grid;
    }

    @Test
    public void fixedColumnsWrapWithGap() {
        SceneNode grid = buildGrid(GridSpec.of(3, 100, 50, 10, 10));
        LayoutBox b0 = box(grid.__getChildren().get(0));
        Assert.assertEquals(new LayoutBox(0, 0, 100, 50), b0);
        Assert.assertEquals(new LayoutBox(110, 0, 100, 50), box(grid.__getChildren().get(1)));
        Assert.assertEquals(new LayoutBox(220, 0, 100, 50), box(grid.__getChildren().get(2)));
        Assert.assertEquals(new LayoutBox(0, 60, 100, 50), box(grid.__getChildren().get(3)));
        Assert.assertEquals(new LayoutBox(110, 60, 100, 50), box(grid.__getChildren().get(4)));
        Assert.assertEquals(new LayoutBox(220, 60, 100, 50), box(grid.__getChildren().get(5)));
        Assert.assertEquals(new LayoutBox(0, 120, 100, 50), box(grid.__getChildren().get(6)));
    }

    @Test
    public void autoColumnsDerivedFromInnerWidth() {
        // innerWidth=400, cellW=100, gapX=10 → floor(410/110)=3 列
        SceneNode grid = buildGrid(GridSpec.autoColumns(100, 50, 10, 10));
        Assert.assertEquals(new LayoutBox(0, 0, 100, 50), box(grid.__getChildren().get(0)));
        Assert.assertEquals(new LayoutBox(220, 0, 100, 50), box(grid.__getChildren().get(2)));
        Assert.assertEquals(new LayoutBox(0, 60, 100, 50), box(grid.__getChildren().get(3)));
    }

    @Test
    public void crossAndMainAlignment() {
        // crossStart=(400-320)/2=40；容器高=7*50+6*10=410，blockH=3*50+2*10=170 → mainStart=120
        GridSpec spec = new GridSpec(3, 100, 50, 10, 10, MainAxisAlign.CENTER, CrossAxisAlign.CENTER);
        SceneNode grid = buildGrid(spec);
        Assert.assertEquals(new LayoutBox(40, 120, 100, 50), box(grid.__getChildren().get(0)));
        Assert.assertEquals(new LayoutBox(150, 120, 100, 50), box(grid.__getChildren().get(1)));
        Assert.assertEquals(new LayoutBox(40, 180, 100, 50), box(grid.__getChildren().get(3)));
    }

    @Test
    public void contentRowsKeepNaturalHeights() {
        // cellHeight<=0：行高按内容；第 2 子高 60 → 行 0 高 60，行 1 从 70 起
        SceneNode grid = GridLayouts.container(GridSpec.of(3, 100, 0, 10, 10));
        sceneRoot.appendChild(grid);
        grid.appendChild(childWithHeight(50));
        grid.appendChild(childWithHeight(60));
        grid.appendChild(childWithHeight(50));
        GridLayouts.attach(runtime, grid, GridSpec.of(3, 100, 0, 10, 10));
        layoutAndBridge();
        Assert.assertEquals(new LayoutBox(0, 0, 100, 50), box(grid.__getChildren().get(0)));
        Assert.assertEquals(new LayoutBox(110, 0, 100, 60), box(grid.__getChildren().get(1)));
        Assert.assertEquals(new LayoutBox(220, 0, 100, 50), box(grid.__getChildren().get(2)));
    }

    @Test
    public void paddingRespectedInLogicalPx() {
        SceneNode grid = GridLayouts.container(GridSpec.of(3, 100, 50, 10, 10));
        grid.setPadding(10);
        sceneRoot.appendChild(grid);
        for (int i = 0; i < 3; i++) {
            grid.appendChild(childWithHeight(50));
        }
        GridLayouts.attach(runtime, grid, GridSpec.of(3, 100, 50, 10, 10));
        layoutAndBridge();
        Assert.assertEquals(new LayoutBox(10, 10, 100, 50), box(grid.__getChildren().get(0)));
        Assert.assertEquals(new LayoutBox(120, 10, 100, 50), box(grid.__getChildren().get(1)));
    }

    @Test
    public void geometryGateKeepsStableReferencesOnCleanFrames() {
        SceneNode grid = buildGrid(GridSpec.of(3, 100, 50, 10, 10));
        SceneNode firstChild = grid.__getChildren().get(0);
        LayoutBox firstBox = box(firstChild);
        LayoutBox gridBox = box(grid);
        // 模拟 paint 遍历消费完首轮几何脏位后，再跑一帧布局 + 桥接：
        // 网格定位走几何闸门 → 引用不变、零重写、不产生新脏位
        firstChild.clearGeometryDirty();
        layoutAndBridge();
        Assert.assertSame("干净帧子节点 LayoutBox 引用复用", firstBox, box(firstChild));
        Assert.assertSame("干净帧容器自身 LayoutBox 引用复用", gridBox, box(grid));
        Assert.assertFalse("干净帧不产生几何脏标记", firstChild.__isSelfGeometryDirty());
    }

    @Test
    public void directPositionChildrenPositionsAndIsStable() {
        // 直接调用定位协作者（与引擎时序解耦的纯函数路径）
        GridSpec spec = GridSpec.of(3, 100, 50, 10, 10);
        SceneNode grid = GridLayouts.container(spec);
        sceneRoot.appendChild(grid);
        for (int i = 0; i < 7; i++) {
            grid.appendChild(childWithHeight(50));
        }
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        LayoutBox gridBox = box(grid);
        GridLayouter.positionChildren(grid, spec, gridBox.getWidth(), gridBox.getHeight());
        Assert.assertEquals("首轮定位：子 0 落网格位", new LayoutBox(0, 0, 100, 50),
                box(grid.__getChildren().get(0)));
        LayoutBox stable = box(grid.__getChildren().get(0));
        FlexLayouter.SelfBubbleSignal second = GridLayouter.positionChildren(
                grid, spec, gridBox.getWidth(), gridBox.getHeight());
        Assert.assertFalse("二轮定位无变化 → 无 bubble", second.geometry());
        Assert.assertFalse(second.paint());
        Assert.assertSame("二轮定位几何闸门复用引用", stable, box(grid.__getChildren().get(0)));
    }

    @Test
    public void factoryReturnsColumnContainerWithClip() {
        SceneNode grid = GridLayouts.container(GridSpec.of(3, 100, 50, 10, 10));
        Assert.assertEquals(FlexDirection.COLUMN, grid.getFlexDirection());
        Assert.assertTrue(grid.isClipChildren());
        Assert.assertEquals(10, grid.getGap());
    }

    @Test
    public void emptyGridIsStable() {
        GridSpec spec = GridSpec.of(3, 100, 50, 10, 10);
        SceneNode grid = GridLayouts.container(spec);
        sceneRoot.appendChild(grid);
        GridLayouts.attach(runtime, grid, spec);
        layoutAndBridge();
        LayoutBox gridBox = box(grid);
        Assert.assertNotNull("空网格容器仍产出自身盒", gridBox);
        Assert.assertEquals(CANVAS_WIDTH, gridBox.getWidth());
        Assert.assertEquals(0, gridBox.getHeight());
    }
}
