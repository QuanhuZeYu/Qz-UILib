package club.heiqi.uilib.ui.scene.input;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    /** 测试用滚动 signal。 */
    private Signal<Integer> scrollSignal;

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

    /** 已激活但原位释放也必须通知 drop，具体 no-op 由消费者判定。 */
    @Test
    public void activatedNoOpUpShouldNotifyDrop() {
        AtomicReference<List<Item>> committed = new AtomicReference<List<Item>>();
        mountDragListWithCallbacks(committed::set, ignored -> { });

        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);
        harness.pressAt(x, y);
        harness.moveAt(x, y + 6);
        harness.releaseAt(x, y + 6);

        Assert.assertNotNull("已激活拖拽即使原位释放也应调用 onDropCommit", committed.get());
        Assert.assertEquals("原位释放的 drop 顺序应保持不变",
                Arrays.asList("a", "b", "c"), values(committed.get()));
    }

    /** 阈值前取消只清 scene 指针状态，不应把空起始快照交给消费者。 */
    @Test
    public void cancelBeforeThresholdShouldNotNotifyConsumer() {
        AtomicReference<List<Item>> cancelled = new AtomicReference<List<Item>>();
        mountDragListWithCallbacks(ignored -> { }, cancelled::set);

        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);
        harness.pressAt(x, y);
        routePointer(ScenePointerAction.CANCEL, x, y);

        Assert.assertNull("阈值前 CANCEL 不应调用 onCancel", cancelled.get());
        Assert.assertNull("CANCEL 后应清空 pointer capture", runtime.getInputRouter().__getCapturedNode());
        Assert.assertEquals("阈值前 CANCEL 不应改顺序",
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

    /** 指针移动到 viewport 顶边缘触发区时，应按 MOVE 节奏向上滚动。 */
    @Test
    public void autoScrollNearTopShouldDecreaseScrollSignal() {
        mountScrollableDragList(Integer.valueOf(24));
        SceneNode handle = handleAt(1);
        int x = centerX(handle);
        int y = centerY(handle);
        int topY = topY(viewport) + 4;

        harness.pressAt(x, y);
        harness.moveAt(x, topY);

        Assert.assertTrue("顶边缘 MOVE 应减小 scrollSignal", scrollSignal.get().intValue() < 24);
        Assert.assertTrue("顶边缘 MOVE 不应小于 0", scrollSignal.get().intValue() >= 0);
    }

    /** 指针移动到 viewport 底边缘触发区时，应向下滚动并 clamp 到 maxScrollY。 */
    @Test
    public void autoScrollNearBottomShouldIncreaseAndClampScrollSignal() {
        mountScrollableDragList(Integer.valueOf(0));
        int maxScroll = SceneGeometry.maxScrollY(viewport);
        scrollSignal.set(Integer.valueOf(maxScroll - 1));
        runtime.flush();
        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);
        int bottomY = bottomY(viewport) - 4;

        harness.pressAt(x, y);
        harness.moveAt(x, bottomY);

        Assert.assertEquals("底边缘 MOVE 应 clamp 到 maxScrollY", maxScroll, scrollSignal.get().intValue());
    }

    /** scrollSignal 为 null 时，边缘 MOVE 不应抛错。 */
    @Test
    public void autoScrollNullSignalShouldNotThrow() {
        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);

        harness.pressAt(x, y);
        harness.moveAt(x, bottomY(viewport) - 4);

        Assert.assertNotNull("scrollSignal=null 时边缘 MOVE 不应抛错", orderSignal.get());
    }

    /** 拖拽完成与取消回调应收到对应的最终预览顺序与起始快照。 */
    @Test
    public void dropCommitAndCancelCallbacksShouldReceiveExpectedSnapshots() {
        AtomicReference<List<Item>> committed = new AtomicReference<List<Item>>();
        mountDragListWithCallbacks(committed::set, ignored -> { });

        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int centerOffset = centerY(rowAt(0)) - centerY(handle);
        int targetY = pointerYForDraggedCenter(bottomY(rowAt(2)) + 1, centerOffset);
        harness.pressAt(x, centerY(handle));
        harness.moveAt(x, targetY);
        Assert.assertEquals("MOVE 后形成提交前预览顺序", Arrays.asList("b", "c", "a"), values(orderSignal.get()));
        harness.releaseAt(x, targetY);

        Assert.assertNotNull("UP 后应调用 onDropCommit", committed.get());
        Assert.assertEquals("onDropCommit 内容应等于预览顺序",
                Arrays.asList("b", "c", "a"), values(committed.get()));

        AtomicReference<List<Item>> cancelled = new AtomicReference<List<Item>>();
        mountDragListWithCallbacks(ignored -> { }, cancelled::set);
        List<String> startSnapshot = values(orderSignal.get());
        handle = handleAt(0);
        x = centerX(handle);
        centerOffset = centerY(rowAt(0)) - centerY(handle);
        targetY = pointerYForDraggedCenter(bottomY(rowAt(2)) + 1, centerOffset);
        harness.pressAt(x, centerY(handle));
        harness.moveAt(x, targetY);
        Assert.assertEquals("CANCEL 前已有预览顺序", Arrays.asList("b", "c", "a"), values(orderSignal.get()));

        routePointer(ScenePointerAction.CANCEL, x, targetY);

        Assert.assertNotNull("CANCEL 后应调用 onCancel", cancelled.get());
        Assert.assertEquals("onCancel 内容应等于起始快照", startSnapshot, values(cancelled.get()));
    }

    /** 构造一行。 */
    private SceneNode row(Item item) {
        return row(item, null);
    }

    /** 构造一行，可注入滚动 signal。 */
    private SceneNode row(Item item, Signal<Integer> rowScrollSignal) {
        return row(item, rowScrollSignal, ignored -> { }, ignored -> { });
    }

    /** 构造一行，可注入滚动 signal 与拖拽回调。 */
    private SceneNode row(Item item, Signal<Integer> rowScrollSignal,
                          Consumer<List<Item>> onDropCommit, Consumer<List<Item>> onCancel) {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(36);
        row.appendChild(SceneDragReorder.buildHandle(runtime, viewport, rowScrollSignal, item.id, orderSignal,
                candidate -> candidate.id, next -> orderSignal.set(next), onDropCommit, onCancel));
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

    /** 挂载带滚动范围的拖拽列表。 */
    private void mountScrollableDragList(Integer initialScroll) {
        root = SceneNode.column();
        viewport = SceneNode.column();
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setPreferredHeight(80);
        viewport.setGap(4);
        root.appendChild(viewport);
        scrollSignal = Signal.create(initialScroll);
        orderSignal = Signal.create(Arrays.asList(
                new Item(1, "a"), new Item(2, "b"), new Item(3, "c"), new Item(4, "d"),
                new Item(5, "e"), new Item(6, "f"), new Item(7, "g"), new Item(8, "h")));
        for (Item item : orderSignal.get()) {
            viewport.appendChild(row(item, scrollSignal));
        }
        harness.mountRoot(root, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /** 挂载可捕获拖拽回调的默认列表。 */
    private void mountDragListWithCallbacks(Consumer<List<Item>> onDropCommit, Consumer<List<Item>> onCancel) {
        root = SceneNode.column();
        viewport = SceneNode.column();
        viewport.setGap(4);
        root.appendChild(viewport);
        orderSignal = Signal.create(Arrays.asList(new Item(1, "a"), new Item(2, "b"), new Item(3, "c")));
        for (Item item : orderSignal.get()) {
            viewport.appendChild(row(item, null, onDropCommit, onCancel));
        }
        harness.mountRoot(root, CANVAS_WIDTH, CANVAS_HEIGHT);
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

    /** @return 节点上边缘 Y。 */
    private int topY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY();
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

    // 白盒回退（精确 localX/坐标）：harness 无 CANCEL 投递入口，需裸建 InputFrameBuilder 直投
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(root, frame, 0, 0);
        runtime.flush();
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
