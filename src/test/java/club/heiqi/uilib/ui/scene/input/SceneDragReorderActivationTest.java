package club.heiqi.uilib.ui.scene.input;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneDragReorder;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * 拖拽排序激活阈值测试。
 *
 * <p>使用 input 包内测试探针核对显式 pointer capture，覆盖 DOWN 后微动不激活、超过阈值才激活。</p>
 */
public class SceneDragReorderActivationTest {

    /** 测试画布宽度。 */
    private static final int CANVAS_WIDTH = 240;
    /** 测试画布高度。 */
    private static final int CANVAS_HEIGHT = 160;

    /** 交互注入 harness。 */
    private SceneInteractionHarness harness;
    /** 场景运行时。 */
    private SceneRuntime runtime;
    /** 场景根。 */
    private SceneNode root;
    /** 列表视口。 */
    private SceneNode viewport;
    /** 顺序 signal。 */
    private Signal<List<Item>> orderSignal;

    /** 初始化测试场景。 */
    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        root = SceneNode.column();
        viewport = SceneNode.column();
        viewport.setGap(4);
        root.appendChild(viewport);
        orderSignal = Signal.create(Arrays.asList(new Item(1, "a"), new Item(2, "b"), new Item(3, "c")));

        for (Item item : orderSignal.get()) {
            viewport.appendChild(row(item));
        }
        harness.mountRoot(root, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /** 清理运行时。 */
    @After
    public void tearDown() {
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    /** DOWN 后微动小于 5px 不应重排，也不应请求显式 capture。 */
    @Test
    public void moveBelowThresholdShouldNotReorderOrCapture() {
        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);

        harness.pressAt(x, y);
        Assert.assertNull("DOWN 后不应立即 capture", runtime.getInputRouter().__getCapturedNode());

        harness.moveAt(x, y + 4);

        Assert.assertEquals("微动未超过阈值时顺序不变", Arrays.asList("a", "b", "c"), values(orderSignal.get()));
        Assert.assertNull("微动未超过阈值时不应 capture", runtime.getInputRouter().__getCapturedNode());
    }

    /** MOVE 累计位移超过 5px 后才进入拖拽，触发显式 capture 与预览重排。 */
    @Test
    public void moveBeyondThresholdShouldStartDragAndCapture() {
        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);

        harness.pressAt(x, y);
        harness.moveAt(x, y + 6);

        Assert.assertSame("超过阈值后应 capture 当前把手", handle, runtime.getInputRouter().__getCapturedNode());
        Assert.assertEquals("超过阈值但未跨过静止行边缘时不重排",
                Arrays.asList("a", "b", "c"), values(orderSignal.get()));
    }

    /** 相邻边界附近抖动时，只有被拖行中心跨过静止行边缘才改变预览顺序。 */
    @Test
    public void adjacentBoundaryJitterShouldNotFlipPreviewBackAndForth() {
        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);
        int rowOneBottom = bottomY(rowAt(1));
        int centerOffset = centerY(rowAt(0)) - centerY(handle);

        harness.pressAt(x, y);
        harness.moveAt(x, pointerYForDraggedCenter(rowOneBottom - 1, centerOffset));
        Assert.assertEquals("被拖行中心未跨过 row1 下边缘时不重排",
                Arrays.asList("a", "b", "c"), values(orderSignal.get()));

        harness.moveAt(x, pointerYForDraggedCenter(rowOneBottom + 1, centerOffset));
        Assert.assertEquals("被拖行中心跨过 row1 下边缘后移到 row1 后",
                Arrays.asList("b", "a", "c"), values(orderSignal.get()));

    }

    /** 构造一行。 */
    private SceneNode row(Item item) {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(36);
        row.appendChild(SceneDragReorder.buildHandle(runtime, viewport, null, item.id, orderSignal,
                candidate -> candidate.id, next -> orderSignal.set(next), ignored -> { }, () -> { }));
        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setText(item.value);
        row.appendChild(label);
        return row;
    }

    /** @return 指定行把手。 */
    private SceneNode handleAt(int index) {
        return rowAt(index).__getChildren().get(0);
    }

    /** @return 指定行。 */
    private SceneNode rowAt(int index) {
        return viewport.__getChildren().get(index);
    }

    /** @return 节点中心 X。 */
    private int centerX(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getX() + box.getWidth() / 2;
    }

    /** @return 节点中心 Y。 */
    private int centerY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + box.getHeight() / 2;
    }

    /** @return 节点下边缘 Y。 */
    private int bottomY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + box.getHeight();
    }

    /**
     * 将目标拖拽中心 Y 换算为指针 Y。
     */
    private int pointerYForDraggedCenter(int draggedCenterY, int centerOffset) {
        return draggedCenterY - centerOffset;
    }

    /** @return 文本顺序。 */
    private List<String> values(List<Item> items) {
        String[] out = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            out[i] = items.get(i).value;
        }
        return Arrays.asList(out);
    }

    /** 测试行数据。 */
    private static final class Item {
        /** 行 id。 */
        private final long id;
        /** 行文本。 */
        private final String value;

        /** 创建测试行。 */
        private Item(long id, String value) {
            this.id = id;
            this.value = value;
        }
    }
}
