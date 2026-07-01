package club.heiqi.uilib.internal.devtools.pages;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneLayoutHostWidget 组装测试。
 *
 * <p>重点覆盖 Layout demo 自身依赖的 P1-a 结构：root COLUMN 中固定标题兄弟 +
 * 唯一 fillParentHeight 视口吃剩余高度，并验证滚轮 handler 仍通过 signal-first
 * 链路更新视口滚动偏移。</p>
 */
public class SceneLayoutDemoTest {

    private static final int CANVAS_WIDTH = 520;
    private static final int CANVAS_HEIGHT = 360;

    private SceneLayoutHostWidget host;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private SceneNode root;
    private SceneNode viewport;
    private SceneNode content;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new SceneLayoutHostWidget(null);
        runtime = host.__getRuntime();
        layoutEngine = host.__getLayoutEngine();
        root = host.__getRoot();
        viewport = host.__getViewport();
        content = host.__getContent();
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 跑一次布局，模拟 drawSelf 中的 layout 阶段。 */
    private void doLayout() {
        layoutEngine.layout(root, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 模拟 drawSelf pipeline：layout① → route(SCROLL) → flush → layout②。
     *
     * @param x 滚轮命中绝对 X
     * @param y 滚轮命中绝对 Y
     * @param wheelDelta 滚轮增量（向下滚 &lt; 0）
     */
    private void routeScrollAt(int x, int y, int wheelDelta) {
        doLayout();
        InputFrameBuilder frameBuilder = new InputFrameBuilder(x, y);
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, x, y,
                SceneMouseButton.NONE, wheelDelta, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = frameBuilder.drainFrame();
        runtime.route(root, frame, 0, 0);
        runtime.flush();
        doLayout();
    }

    /**
     * 计算节点绝对坐标（沿父链累加 LayoutBox.x/y）。
     *
     * @param node 场景节点
     * @return 绝对坐标数组 [x, y]
     */
    private int[] absOrigin(SceneNode node) {
        int ax = 0;
        int ay = 0;
        SceneNode cur = node;
        while (cur != null) {
            LayoutBox box = (LayoutBox) cur.getCachedLayout();
            if (box != null) {
                ax += box.getX();
                ay += box.getY();
            }
            cur = cur.__getParent();
        }
        return new int[] { ax, ay };
    }

    /** 视口应通过 P1-a 吃 root 剩余高度，而不是被内容撑高。 */
    @Test
    public void viewportShouldFillRemainingHeightAndContentShouldOverflow() {
        doLayout();

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        LayoutBox contentBox = (LayoutBox) content.getCachedLayout();
        Assert.assertNotNull("viewport 应已布局", viewportBox);
        Assert.assertNotNull("content 应已布局", contentBox);

        int expectedViewportHeight = CANVAS_HEIGHT
                - root.getPaddingTop()
                - root.getPaddingBottom()
                - SceneLayoutHostWidget.__getTitleBarHeight()
                - root.getGap();
        Assert.assertEquals("viewport 应吃掉 root padding、标题条和 gap 后的剩余高度",
                expectedViewportHeight, viewportBox.getHeight());
        Assert.assertTrue("内容总高应超过 viewport，保证 Layout demo 可纵向滚动",
                contentBox.getHeight() > viewportBox.getHeight());
    }

    /** 滚轮应只经 scrollSignal 更新视口 scrollOffsetY，并按内容高度 clamp。 */
    @Test
    public void wheelShouldUpdateViewportScrollOffsetViaSignal() {
        doLayout();

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        int maxScroll = SceneGeometry.maxScrollY(viewport);
        Assert.assertTrue("Layout demo 内容应能产生正向 maxScroll", maxScroll > 0);

        int[] viewportOrigin = absOrigin(viewport);
        int viewportCenterX = viewportOrigin[0] + viewportBox.getWidth() / 2;
        int viewportCenterY = viewportOrigin[1] + viewportBox.getHeight() / 2;

        routeScrollAt(viewportCenterX, viewportCenterY, -120);
        Assert.assertEquals("向下滚后 scrollSignal 应更新为 120", 120,
                host.__getScrollSignal().get().intValue());
        Assert.assertEquals("bind 应把 scrollSignal 推给 viewport scrollOffsetY", 120,
                viewport.getScrollOffsetY());

        routeScrollAt(viewportCenterX, viewportCenterY, -9999);
        Assert.assertEquals("超界向下滚应 clamp 到 maxScroll", maxScroll, viewport.getScrollOffsetY());

        routeScrollAt(viewportCenterX, viewportCenterY, 9999);
        Assert.assertEquals("超界向上滚应 clamp 回 0", 0, viewport.getScrollOffsetY());
    }
}
