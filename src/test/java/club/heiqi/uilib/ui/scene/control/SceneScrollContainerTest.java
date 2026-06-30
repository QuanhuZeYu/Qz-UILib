package club.heiqi.uilib.ui.scene.control;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneScrollContainer 高阶工厂单元测试。
 *
 * <p>验证：建容器→挂内容超视口→layout→maxScrollY > 0；
 * scrollSignal set 后 scrollOffsetY 同步；无 scrollbarSpec 时不建 scrollbar。</p>
 */
public class SceneScrollContainerTest {

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
        // sceneRoot 设 fillParentHeight 使其从 Constraints 收到确定高并下传给 container（grow 子），
        // 否则 priorKnownInnerHeight(sceneRoot) 返回 UNCONSTRAINED → container 收不到确定高 → viewport 被内容撑大。
        sceneRoot.setFillParentHeight(true);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    private LayoutResult doLayout() {
        return layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 无 scrollbarSpec 时：container 只有 1 个子（viewport），content 溢出时 maxScrollY > 0。
     */
    @Test
    public void noScrollbarSpecShouldBuildViewportAndContentOnly() {
        SceneScrollContainer.Props props = new SceneScrollContainer.Props(
                0, 0, 0, 0, null);
        SceneScrollContainer.Result r = SceneScrollContainer.create(runtime, props);
        sceneRoot.appendChild(r.container());

        // 挂超视口内容：5 个固定高 100 的子，总高 500 > 300
        for (int i = 0; i < 5; i++) {
            SceneNode child = new SceneNode();
            child.setPreferredHeight(100);
            r.content().appendChild(child);
        }

        doLayout();
        runtime.flush();

        Assert.assertTrue("container 只有 viewport 一个子（无 scrollbar）",
                r.container().__getChildren().size() == 1);
        Assert.assertSame("container 第一个子是 viewport",
                r.viewport(), r.container().__getChildren().get(0));

        LayoutBox vpBox = (LayoutBox) r.viewport().getCachedLayout();
        Assert.assertNotNull("viewport 已布局", vpBox);
        Assert.assertTrue("viewport 高度受约束（未被内容撑大）",
                vpBox.getHeight() > 0 && vpBox.getHeight() <= CANVAS_HEIGHT);

        int maxScroll = SceneGeometry.maxScrollY(r.viewport());
        Assert.assertTrue("content 溢出时 maxScroll > 0", maxScroll > 0);
    }

    /**
     * scrollSignal set 后，经 bind 同步到 viewport.scrollOffsetY。
     */
    @Test
    public void scrollSignalSetShouldSyncToViewportOffset() {
        SceneScrollContainer.Props props = new SceneScrollContainer.Props(
                0, 0, 0, 0, null);
        SceneScrollContainer.Result r = SceneScrollContainer.create(runtime, props);
        sceneRoot.appendChild(r.container());

        for (int i = 0; i < 5; i++) {
            SceneNode child = new SceneNode();
            child.setPreferredHeight(100);
            r.content().appendChild(child);
        }

        doLayout();
        runtime.flush();

        int maxScroll = SceneGeometry.maxScrollY(r.viewport());
        Assert.assertTrue("前置：maxScroll > 0", maxScroll > 0);

        int target = Math.min(120, maxScroll);
        r.scrollSignal().set(Integer.valueOf(target));
        runtime.flush();

        Assert.assertEquals("scrollSignal set 后 viewport.scrollOffsetY 同步",
                target, r.viewport().getScrollOffsetY());
    }

    /**
     * 有 scrollbarSpec 时：container 有 2 个子（viewport + scrollbar column）。
     */
    @Test
    public void scrollbarSpecShouldBuildScrollbarColumn() {
        Signal<Object> bumpSignal = Signal.create(new Object());
        SceneScrollContainer.ScrollbarSpec sbSpec = new SceneScrollContainer.ScrollbarSpec(
                bumpSignal, 0x33FFFFFF, 0xFFFFFFFF, 8, 20);
        SceneScrollContainer.Props props = new SceneScrollContainer.Props(
                0, 0, 0, 0, sbSpec);
        SceneScrollContainer.Result r = SceneScrollContainer.create(runtime, props);
        sceneRoot.appendChild(r.container());

        for (int i = 0; i < 5; i++) {
            SceneNode child = new SceneNode();
            child.setPreferredHeight(100);
            r.content().appendChild(child);
        }

        doLayout();
        runtime.flush();

        Assert.assertEquals("有 scrollbarSpec 时 container 有 2 个子（viewport + scrollbar）",
                2, r.container().__getChildren().size());
        Assert.assertSame("container 第一个子是 viewport",
                r.viewport(), r.container().__getChildren().get(0));
        Assert.assertNotSame("container 第二个子是 scrollbar column（非 viewport）",
                r.viewport(), r.container().__getChildren().get(1));

        int maxScroll = SceneGeometry.maxScrollY(r.viewport());
        Assert.assertTrue("有 scrollbar 时 content 仍溢出 maxScroll > 0", maxScroll > 0);
    }

    /**
     * viewport 应已设 scrollable=true（attach 防呆不抛异常即证明）。
     */
    @Test
    public void viewportShouldBeScrollable() {
        SceneScrollContainer.Props props = new SceneScrollContainer.Props(
                0, 0, 0, 0, null);
        SceneScrollContainer.Result r = SceneScrollContainer.create(runtime, props);
        Assert.assertTrue("viewport 应已设 scrollable=true", r.viewport().isScrollable());
    }
}
