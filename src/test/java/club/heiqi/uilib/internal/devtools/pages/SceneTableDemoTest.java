package club.heiqi.uilib.internal.devtools.pages;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneTableHostWidget 组装测试 —— 验证独立 Table demo 只包装页面壳，表格行为来自 SceneTable 本体。
 */
public class SceneTableDemoTest {

    private SceneTableHostWidget host;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private SceneNode root;
    private SceneNode tableRoot;
    private SceneNode viewport;
    private SceneNode content;

    /** 模拟 host 画布尺寸（足够容纳标题与表格）。 */
    private static final int CANVAS_WIDTH = 520;
    private static final int CANVAS_HEIGHT = 420;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new SceneTableHostWidget(null);
        runtime = host.__getRuntime();
        layoutEngine = host.__getLayoutEngine();
        root = host.__getRoot();
        tableRoot = host.__getTableRoot();
        viewport = host.__getViewport();
        content = host.__getContent();
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 跑一次布局（模拟 drawSelf 的 layout 阶段）。 */
    private void doLayout() {
        layoutEngine.layout(root, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 模拟 drawSelf pipeline：layout① → route(SCROLL) → flush → layout②。
     *
     * @param x          滚轮命中绝对 X
     * @param y          滚轮命中绝对 Y
     * @param wheelDelta 滚轮增量（向下滚 &lt; 0）
     */
    private void routeScrollAt(int x, int y, int wheelDelta) {
        doLayout();
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, x, y,
                SceneMouseButton.NONE, wheelDelta, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
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

    /** 视口高度应由 demo Props 钉死，内容高度应超过视口以触发纵向滚动。 */
    @Test
    public void viewportShouldPinHeightAndContentShouldOverflow() {
        doLayout();

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        LayoutBox contentBox = (LayoutBox) content.getCachedLayout();

        Assert.assertNotNull("viewport 应已布局", viewportBox);
        Assert.assertNotNull("content 应已布局", contentBox);
        Assert.assertEquals("viewport 高度应钉死为 demo 固定值",
                SceneTableHostWidget.__getViewportHeight(), viewportBox.getHeight());
        Assert.assertTrue("content 高度应溢出 viewport，保证纵向滚动可见",
                contentBox.getHeight() > viewportBox.getHeight());
    }

    /** 表头和每一条数据行都应保持 4 列，证明 SceneTable Props 组装稳定。 */
    @Test
    public void headerAndDataRowsShouldKeepStableColumnCount() {
        doLayout();

        Assert.assertSame("host 应持有 runtime.mount 返回的 Table 根节点",
                tableRoot, root.__getChildren().get(1));
        Assert.assertTrue("content 应包含表头和数据行", content.__getChildren().size() > 2);
        for (SceneNode row : content.__getChildren()) {
            Assert.assertEquals("表头/数据行列数应稳定为 4", 4, row.__getChildren().size());
        }
    }

    /** 滚轮命中表格数据行时应沿父链冒泡到 viewport handler，并更新 scrollOffsetY。 */
    @Test
    public void scrollOnTableRowShouldBubbleToViewportHandler() {
        doLayout();

        SceneNode firstDataRow = content.__getChildren().get(1);
        LayoutBox rowBox = (LayoutBox) firstDataRow.getCachedLayout();
        Assert.assertNotNull("数据行应已布局", rowBox);
        int[] rowOrigin = absOrigin(firstDataRow);
        int rowCenterX = rowOrigin[0] + rowBox.getWidth() / 2;
        int rowCenterY = rowOrigin[1] + rowBox.getHeight() / 2;

        Assert.assertEquals("滚动前 offset 应为 0", 0, viewport.getScrollOffsetY());
        routeScrollAt(rowCenterX, rowCenterY, -80);
        Assert.assertEquals("命中数据行的 SCROLL 应冒泡到 viewport handler，offset 更新为 80",
                80, viewport.getScrollOffsetY());
    }
}
