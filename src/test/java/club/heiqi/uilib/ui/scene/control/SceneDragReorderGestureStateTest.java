package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
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
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/** SceneDragReorder 手势瞬态复位测试。 */
public class SceneDragReorderGestureStateTest {

    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private SceneNode root;
    private SceneNode viewport;
    private Signal<List<Item>> orderSignal;
    private AtomicReference<SceneDragReorder.GestureStateSnapshot> resetState;

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
        resetState = new AtomicReference<SceneDragReorder.GestureStateSnapshot>();
        mountRows();
    }

    @After
    public void tearDown() {
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    @Test
    public void normalUpResetsEveryGestureTransientBeforeNotifyingConsumer() {
        final AtomicReference<SceneDragReorder.GestureStateSnapshot> stateSeenByConsumer =
                new AtomicReference<SceneDragReorder.GestureStateSnapshot>();
        final AtomicReference<List<Item>> committed = new AtomicReference<List<Item>>();
        remountRows(committed::set, ignored -> { }, stateSeenByConsumer);

        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);
        harness.pressAt(x, y);
        harness.moveAt(x, y + 6);
        harness.releaseAt(x, y + 6);

        assertResetState(resetState.get());
        Assert.assertSame("UP consumer 回调内应观察到已清理的逻辑状态",
                resetState.get(), stateSeenByConsumer.get());
        Assert.assertNotNull("UP 应通知提交消费者", committed.get());
        assertVisualResetAfterFlush(handle, resetState.get());
    }

    @Test
    public void cancelResetsEveryGestureTransientBeforeNotifyingConsumer() {
        final AtomicReference<SceneDragReorder.GestureStateSnapshot> stateSeenByConsumer =
                new AtomicReference<SceneDragReorder.GestureStateSnapshot>();
        final AtomicReference<List<Item>> cancelled = new AtomicReference<List<Item>>();
        remountRows(ignored -> { }, cancelled::set, stateSeenByConsumer);

        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);
        harness.pressAt(x, y);
        harness.moveAt(x, y + 6);
        routePointer(ScenePointerAction.CANCEL, x, y + 6);

        assertResetState(resetState.get());
        Assert.assertSame("CANCEL consumer 回调内应观察到已清理的逻辑状态",
                resetState.get(), stateSeenByConsumer.get());
        Assert.assertNotNull("已激活 CANCEL 应通知取消消费者", cancelled.get());
        assertVisualResetAfterFlush(handle, resetState.get());
    }

    @Test
    public void upThenSecondGestureUsesFreshStartOrderAndOffsetOnSameHandle() {
        final List<List<Item>> committed = new ArrayList<List<Item>>();
        final List<List<Item>> dragStarts = new ArrayList<List<Item>>();
        remountRows(committed::add, ignored -> { }, null,
                () -> dragStarts.add(new ArrayList<Item>(orderSignal.get())));

        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int firstStartY = centerY(handle);
        harness.pressAt(x, firstStartY);
        harness.moveAt(x, firstStartY + 60);
        harness.releaseAt(x, firstStartY + 60);
        SceneDragReorder.GestureStateSnapshot firstReset = resetState.get();
        assertVisualResetAfterFlush(handle, firstReset);
        Assert.assertEquals("第一手势应预览并提交新顺序", Arrays.asList("b", "a", "c"),
                values(committed.get(0)));

        int secondStartY = centerY(handle) + 5;
        harness.pressAt(x, secondStartY);
        harness.moveAt(x, secondStartY + 20);
        harness.releaseAt(x, secondStartY + 20);
        SceneDragReorder.GestureStateSnapshot secondReset = resetState.get();

        Assert.assertEquals("同一把手第二次 DOWN 应读取第一手势提交后的顺序",
                Arrays.asList("a", "b", "c"), values(dragStarts.get(0)));
        Assert.assertEquals("第二次 DOWN 应使用当前顺序而非旧快照",
                Arrays.asList("b", "a", "c"), values(dragStarts.get(1)));
        Assert.assertEquals("同一把手应连续通知两次 UP", 2, committed.size());
        Assert.assertTrue("第二次 MOVE 应产生新的非零偏移", secondReset.dragOffsetCurrent > 0);
        Assert.assertTrue("第二次偏移应按新起点计算而非复用第一手势偏移",
                secondReset.dragOffsetCurrent < firstReset.dragOffsetCurrent);
        assertVisualResetAfterFlush(handle, secondReset);
    }

    @Test
    public void cancelThenSecondGestureUsesFreshStartOrderAndOffsetOnSameHandle() {
        final List<List<Item>> cancelled = new ArrayList<List<Item>>();
        final List<List<Item>> committed = new ArrayList<List<Item>>();
        final List<List<Item>> dragStarts = new ArrayList<List<Item>>();
        remountRows(committed::add, start -> {
            cancelled.add(new ArrayList<Item>(start));
            orderSignal.set(start);
        }, null, () -> dragStarts.add(new ArrayList<Item>(orderSignal.get())));

        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int firstStartY = centerY(handle);
        harness.pressAt(x, firstStartY);
        harness.moveAt(x, firstStartY + 60);
        routePointer(ScenePointerAction.CANCEL, x, firstStartY + 60);
        SceneDragReorder.GestureStateSnapshot firstReset = resetState.get();
        assertVisualResetAfterFlush(handle, firstReset);
        Assert.assertEquals("CANCEL 应把起始顺序交给消费者", Arrays.asList("a", "b", "c"),
                values(cancelled.get(0)));
        Assert.assertEquals("CANCEL flush 后顺序应恢复", Arrays.asList("a", "b", "c"),
                values(orderSignal.get()));

        int secondStartY = centerY(handle) + 5;
        harness.pressAt(x, secondStartY);
        harness.moveAt(x, secondStartY + 20);
        harness.releaseAt(x, secondStartY + 20);
        SceneDragReorder.GestureStateSnapshot secondReset = resetState.get();

        Assert.assertEquals("CANCEL 后第二次 DOWN 应使用恢复后的新顺序",
                Arrays.asList("a", "b", "c"), values(dragStarts.get(1)));
        Assert.assertEquals("CANCEL 后同一把手第二次 UP 应正常提交", 1, committed.size());
        Assert.assertTrue("CANCEL 后第二次 MOVE 应产生新的非零偏移", secondReset.dragOffsetCurrent > 0);
        Assert.assertTrue("CANCEL 后第二次偏移不应复用第一手势值",
                secondReset.dragOffsetCurrent < firstReset.dragOffsetCurrent);
        assertVisualResetAfterFlush(handle, secondReset);
    }

    @Test
    public void thresholdUpThenSecondGestureShouldActivateNormallyOnSameHandle() {
        final List<List<Item>> committed = new ArrayList<List<Item>>();
        remountRows(committed::add, ignored -> { }, null);

        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);
        harness.pressAt(x, y);
        harness.releaseAt(x, y);
        assertResetState(resetState.get());
        assertVisualResetAfterFlush(handle, resetState.get());
        Assert.assertTrue("阈值前 UP 不应提交", committed.isEmpty());

        harness.pressAt(x, y);
        harness.moveAt(x, y + 20);
        harness.releaseAt(x, y + 20);
        Assert.assertEquals("阈值前结束后下一手势应正常激活", 1, committed.size());
        assertVisualResetAfterFlush(handle, resetState.get());
    }

    @Test
    public void thresholdCancelReleasesDownSnapshotWithoutNotifyingConsumer() {
        AtomicReference<List<Item>> cancelled = new AtomicReference<List<Item>>();
        remountRows(ignored -> { }, cancelled::set, null);

        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);
        harness.pressAt(x, y);
        routePointer(ScenePointerAction.CANCEL, x, y);

        assertResetState(resetState.get());
        Assert.assertNull("阈值前 CANCEL 不得通知 onCancel", cancelled.get());
        Assert.assertEquals("阈值前 CANCEL 不得改变顺序",
                Arrays.asList("a", "b", "c"), values(orderSignal.get()));
    }

    @Test
    public void thresholdUpReleasesDownSnapshotWithoutNotifyingConsumer() {
        AtomicReference<List<Item>> committed = new AtomicReference<List<Item>>();
        remountRows(committed::set, ignored -> { }, null);

        SceneNode handle = handleAt(0);
        int x = centerX(handle);
        int y = centerY(handle);
        harness.pressAt(x, y);
        harness.releaseAt(x, y);

        assertResetState(resetState.get());
        Assert.assertNull("阈值前 UP 不得通知 onDropCommit", committed.get());
        Assert.assertEquals("阈值前 UP 不得改变顺序",
                Arrays.asList("a", "b", "c"), values(orderSignal.get()));
    }

    private void mountRows() {
        for (Item item : orderSignal.get()) {
            viewport.appendChild(row(item, ignored -> { }, ignored -> { }, null, null));
        }
        harness.mountRoot(root, 240, 160);
    }

    private void remountRows(Consumer<List<Item>> onDropCommit,
                             Consumer<List<Item>> onCancel,
                             AtomicReference<SceneDragReorder.GestureStateSnapshot> stateSeenByConsumer) {
        remountRows(onDropCommit, onCancel, stateSeenByConsumer, null);
    }

    private void remountRows(Consumer<List<Item>> onDropCommit,
                             Consumer<List<Item>> onCancel,
                             AtomicReference<SceneDragReorder.GestureStateSnapshot> stateSeenByConsumer,
                             Runnable onDragStart) {
        root = SceneNode.column();
        viewport = SceneNode.column();
        viewport.setGap(4);
        root.appendChild(viewport);
        resetState.set(null);
        for (Item item : orderSignal.get()) {
            viewport.appendChild(row(item, onDropCommit, onCancel, stateSeenByConsumer, onDragStart));
        }
        harness.mountRoot(root, 240, 160);
    }

    private SceneNode row(Item item,
                          Consumer<List<Item>> onDropCommit,
                          Consumer<List<Item>> onCancel,
                          AtomicReference<SceneDragReorder.GestureStateSnapshot> stateSeenByConsumer,
                          Runnable onDragStart) {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(36);
        Consumer<SceneDragReorder.GestureStateSnapshot> onReset = snapshot -> {
            resetState.set(snapshot);
        };
        Consumer<List<Item>> dropWithState = values -> {
            assertLogicalResetInConsumer();
            if (stateSeenByConsumer != null) {
                stateSeenByConsumer.set(resetState.get());
            }
            onDropCommit.accept(values);
        };
        Consumer<List<Item>> cancelWithState = values -> {
            assertLogicalResetInConsumer();
            if (stateSeenByConsumer != null) {
                stateSeenByConsumer.set(resetState.get());
            }
            onCancel.accept(values);
        };
        SceneNode handle = SceneDragReorder.buildHandleForTest(runtime, viewport, viewport, null,
                 item.id, orderSignal, candidate -> candidate.id,
                 next -> orderSignal.set(next), dropWithState, cancelWithState, onDragStart, onReset);
        row.appendChild(handle);
        return row;
    }

    private SceneNode handleAt(int index) {
        return viewport.__getChildren().get(index).__getChildren().get(0);
    }

    private static SceneNode draggedRow(SceneNode handle) {
        SceneNode parent = handle.__getParent();
        return parent == null ? handle : parent;
    }

    private int centerX(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getX() + box.getWidth() / 2;
    }

    private int centerY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + box.getHeight() / 2;
    }

    // 白盒回退（精确 localX/坐标）：harness 无 CANCEL 投递入口，需裸建 InputFrameBuilder 直投
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = builder.drainFrame();
        runtime.route(root, frame, 0, 0);
        runtime.flush();
    }

    private void assertLogicalResetInConsumer() {
        assertResetState(resetState.get());
        Assert.assertEquals("consumer 回调前允许 signal 保持旧 flush 值",
                resetState.get().dragOffsetCurrent,
                resetState.get().dragOffsetSignal.get().intValue());
    }

    private static void assertResetState(SceneDragReorder.GestureStateSnapshot state) {
        Assert.assertNotNull("应产生 reset 快照", state);
        Assert.assertFalse("armed 必须归零", state.armed);
        Assert.assertFalse("dragging 必须归零", state.dragging);
        Assert.assertEquals("startX 必须归零", 0, state.startX);
        Assert.assertEquals("startY 必须归零", 0, state.startY);
        Assert.assertEquals("pointerToDraggedCenterY 必须归零", 0, state.pointerToDraggedCenterY);
        Assert.assertEquals("grabOffsetY 必须归零", 0, state.grabOffsetY);
        Assert.assertTrue("dragStartOrder 必须清空", state.dragStartOrder.isEmpty());
        Assert.assertEquals("dragOffset reset 逻辑目标必须为零", 0, state.dragOffsetTarget);
    }

    private static void assertVisualResetAfterFlush(SceneNode handle,
                                                     SceneDragReorder.GestureStateSnapshot state) {
        Assert.assertEquals("route/harness flush 后 dragOffset signal 必须归零", 0,
                state.dragOffsetSignal.get().intValue());
        Assert.assertEquals("route/harness flush 后视觉 offset 必须归零", 0.0f,
                draggedRow(handle).getTransform().translateY, 0.0f);
    }

    private static List<String> values(List<Item> items) {
        String[] result = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            result[i] = items.get(i).value;
        }
        return Arrays.asList(result);
    }

    private static final class Item {
        private final long id;
        private final String value;

        private Item(long id, String value) {
            this.id = id;
            this.value = value;
        }
    }
}
