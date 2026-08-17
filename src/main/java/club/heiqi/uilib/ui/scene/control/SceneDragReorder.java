package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 拖拽重排行为工具。
 *
 * <p>本类收口列表拖拽排序的把手节点、输入 handler、落点判定与列表移动算法。
 * 控件仍以 signal 作为唯一状态写入入口；拖拽瞬态只保存在 handler 闭包 final 容器中，
 * 不在控件类或本工具类上增加实例状态（守 R1/I11）。</p>
 */
public final class SceneDragReorder {

    /** 拖拽把手固定宽度。 */
    private static final int HANDLE_WIDTH = 24;
    /** 拖拽把手图标。 */
    private static final String HANDLE_ICON = "\u2261";
    /** 拖拽把手 idle 背景色。 */
    private static final int HANDLE_BG_IDLE = 0x00000000;
    /** 拖拽把手 hover 背景色。 */
    private static final int HANDLE_BG_HOVER = SceneChromeTokens.BG_HOVER;
    /** 拖拽把手 pressed 背景色。 */
    private static final int HANDLE_BG_PRESSED = SceneChromeTokens.BG_PRESSED;
    /** 拖拽激活阈值，单位像素。 */
    private static final int DRAG_ACTIVATION_THRESHOLD_PX = 5;
    /** 自动滚动边缘触发区域，单位像素。 */
    private static final int AUTO_SCROLL_ZONE_PX = 32;
    /** 自动滚动单次 MOVE 最大速度，单位像素。 */
    private static final int AUTO_SCROLL_MAX_SPEED_PX = 20;
    /** 自动滚动单次 MOVE 最小速度，单位像素。 */
    private static final int AUTO_SCROLL_MIN_SPEED_PX = 4;

    /** 纯静态工具类，禁止实例化。 */
    private SceneDragReorder() {
    }

    /**
     * 构建拖拽把手并注册排序事件。
     *
     * @param rt             场景运行时
     * @param viewport       列表视口，子节点顺序与 orderSignal 一致
     * @param scrollSignal   视口滚动 signal，可为 null；非 null 时启用边缘自动滚动
     * @param dragId         当前被拖排行 id
     * @param orderSignal    当前顺序真值或预览顺序 signal
     * @param idExtractor    行 id 读取器
     * @param onPreviewOrder MOVE 期间产生新顺序后的回调
     * @param onDropCommit   UP 时提交当前顺序的回调
     * @param onCancel       CANCEL 时按拖拽起始顺序回滚/清理回调
     * @param <T>            行数据类型
     * @return 拖拽把手节点
     */
    public static <T> SceneNode buildHandle(SceneRuntime rt,
                                             SceneNode viewport,
                                             Signal<Integer> scrollSignal,
                                             long dragId,
                                             ReadableSignal<List<T>> orderSignal,
                                            ToLongFunction<T> idExtractor,
                                             Consumer<List<T>> onPreviewOrder,
                                             Consumer<List<T>> onDropCommit,
                                             Consumer<List<T>> onCancel) {
        return buildHandle(rt, viewport, scrollSignal, dragId, orderSignal, idExtractor,
                onPreviewOrder, onDropCommit, onCancel, null);
    }

    /**
     * 构建带拖拽开始通知的把手。
     *
     * <p>旧重载保持原语义；开始通知只在达到激活阈值后调用一次，供筛选列表冻结 visible
     * 投影。已激活拖拽的 UP 始终通知 drop，CANCEL 仅在已激活拖拽时通知回滚。</p>
     *
     * @param rt             场景运行时
     * @param viewport       列表视口
     * @param scrollSignal   滚动 signal
     * @param dragId         当前行 id
     * @param orderSignal    当前可见顺序 signal
     * @param idExtractor    行 id 读取器
     * @param onPreviewOrder MOVE 预览回调
     * @param onDropCommit   UP 提交回调
     * @param onCancel       CANCEL 回滚回调
     * @param onDragStart    达到激活阈值后的开始回调
     * @param <T>            行数据类型
     * @return 拖拽把手节点
     */
    public static <T> SceneNode buildHandle(SceneRuntime rt,
                                             SceneNode viewport,
                                             Signal<Integer> scrollSignal,
                                             long dragId,
                                             ReadableSignal<List<T>> orderSignal,
                                             ToLongFunction<T> idExtractor,
                                             Consumer<List<T>> onPreviewOrder,
                                             Consumer<List<T>> onDropCommit,
                                             Consumer<List<T>> onCancel,
                                             Runnable onDragStart) {
        return buildHandleInternal(rt, viewport, viewport, scrollSignal, dragId, orderSignal,
                idExtractor, onPreviewOrder, onDropCommit, onCancel, onDragStart);
    }

    /**
     * 构建行容器与滚动视口分离的拖拽把手；行容器仍是 keyed 列表的唯一子节点容器。
     *
     * @param rt 场景运行时
     * @param rowViewport 行容器，子节点顺序与 orderSignal 一致
     * @param scrollViewport 实际裁剪/滚动视口
     * @param scrollSignal 滚动 signal
     * @param dragId 当前行 id
     * @param orderSignal 当前可见顺序
     * @param idExtractor 行 id 读取器
     * @param onPreviewOrder MOVE 预览回调
     * @param onDropCommit UP 提交回调
     * @param onCancel CANCEL 回滚回调
     * @param onDragStart 拖拽开始回调
     * @param <T> 行数据类型
     * @return 拖拽把手节点
     */
    public static <T> SceneNode buildHandle(SceneRuntime rt,
                                             SceneNode rowViewport,
                                             SceneNode scrollViewport,
                                             Signal<Integer> scrollSignal,
                                             long dragId,
                                             ReadableSignal<List<T>> orderSignal,
                                             ToLongFunction<T> idExtractor,
                                             Consumer<List<T>> onPreviewOrder,
                                             Consumer<List<T>> onDropCommit,
                                             Consumer<List<T>> onCancel,
                                             Runnable onDragStart) {
        return buildHandleInternal(rt, rowViewport, scrollViewport, scrollSignal, dragId, orderSignal,
                idExtractor, onPreviewOrder, onDropCommit, onCancel, onDragStart);
    }

    /**
     * 包级测试入口：观察手势 reset 后的瞬态快照，不构成公共 API。
     */
    static <T> SceneNode buildHandleForTest(SceneRuntime rt,
                                             SceneNode rowViewport,
                                             SceneNode scrollViewport,
                                             Signal<Integer> scrollSignal,
                                             long dragId,
                                             ReadableSignal<List<T>> orderSignal,
                                             ToLongFunction<T> idExtractor,
                                             Consumer<List<T>> onPreviewOrder,
                                             Consumer<List<T>> onDropCommit,
                                             Consumer<List<T>> onCancel,
                                             Runnable onDragStart,
                                             Consumer<GestureStateSnapshot> onReset) {
        return buildHandleInternal(rt, rowViewport, scrollViewport, scrollSignal, dragId, orderSignal,
                idExtractor, onPreviewOrder, onDropCommit, onCancel, onDragStart, onReset);
    }

    private static <T> SceneNode buildHandleInternal(SceneRuntime rt,
                                                     SceneNode viewport,
                                                     SceneNode scrollViewport,
                                                     Signal<Integer> scrollSignal,
                                                     long dragId,
                                                     ReadableSignal<List<T>> orderSignal,
                                                     ToLongFunction<T> idExtractor,
                                                     Consumer<List<T>> onPreviewOrder,
                                                     Consumer<List<T>> onDropCommit,
                                                     Consumer<List<T>> onCancel,
                                                     Runnable onDragStart) {
        return buildHandleInternal(rt, viewport, scrollViewport, scrollSignal, dragId, orderSignal,
                idExtractor, onPreviewOrder, onDropCommit, onCancel, onDragStart, null);
    }

    private static <T> SceneNode buildHandleInternal(SceneRuntime rt,
                                                     SceneNode viewport,
                                                     SceneNode scrollViewport,
                                                     Signal<Integer> scrollSignal,
                                                     long dragId,
                                                     ReadableSignal<List<T>> orderSignal,
                                                     ToLongFunction<T> idExtractor,
                                                     Consumer<List<T>> onPreviewOrder,
                                                     Consumer<List<T>> onDropCommit,
                                                     Consumer<List<T>> onCancel,
                                                     Runnable onDragStart,
                                                     Consumer<GestureStateSnapshot> onReset) {
        final boolean[] armed = {false};
        final boolean[] dragging = {false};
        final int[] startX = {0};
        final int[] startY = {0};
        final int[] pointerToDraggedCenterY = {0};
        final int[] grabOffsetY = {0};
        final int[] dragScrollY = {0};
        final int[] observedScrollY = {0};
        final int[] dragOffsetY = {0};
        final AtomicReference<List<T>> dragStartOrder = new AtomicReference<List<T>>(Collections.<T>emptyList());
        final AtomicReference<List<T>> dragOrder = new AtomicReference<List<T>>(Collections.<T>emptyList());
        final AtomicReference<List<T>> observedOrder = new AtomicReference<List<T>>(Collections.<T>emptyList());
        final Signal<Integer> dragOffsetSig = Signal.create(Integer.valueOf(0));

        SceneNode handle = SceneNode.row();
        handle.setMainAxisAlign(MainAxisAlign.CENTER);
        handle.setCrossAxisAlign(CrossAxisAlign.CENTER);
        handle.setPreferredWidth(HANDLE_WIDTH);
        handle.setPreferredHeight(SceneChromeTokens.INPUT_HEIGHT);
        handle.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        handle.setCursor(SceneCursor.GRAB);
        handle.setBackgroundColor(HANDLE_BG_IDLE);

        SceneNode icon = new SceneNode();
        icon.setHitTestable(false);
        icon.setText(HANDLE_ICON);
        icon.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
        handle.appendChild(icon);

        SceneInteractionState interaction = rt.interactionState(handle);
        rt.bindComputed(() -> {
            boolean hovered = Boolean.TRUE.equals(interaction.hovered().get());
            boolean pressed = Boolean.TRUE.equals(interaction.pressed().get());
            if (pressed) {
                return HANDLE_BG_PRESSED;
            }
            if (hovered) {
                return HANDLE_BG_HOVER;
            }
            return HANDLE_BG_IDLE;
        }, handle::setBackgroundColor);
        rt.bind(dragOffsetSig, dy -> draggedRow(handle).setTransform(Transform.translate(0.0f, dy.floatValue())));

        rt.on(handle, SceneEventType.POINTER_DOWN, (SceneEvent ev, SceneEventContext ctx) -> {
            armed[0] = true;
            dragging[0] = false;
            startX[0] = ctx.getRawPointerX();
            startY[0] = ctx.getRawPointerY();
            List<T> startOrder = immutableCopy(orderSignal.get());
            dragStartOrder.set(startOrder);
            dragOrder.set(startOrder);
            observedOrder.set(startOrder);
            int startScroll = scrollSignal == null || scrollSignal.get() == null
                    ? 0 : scrollSignal.get().intValue();
            dragScrollY[0] = startScroll;
            observedScrollY[0] = startScroll;
            dragOffsetY[0] = 0;
            ctx.stopPropagation();
        });
        rt.on(handle, SceneEventType.POINTER_MOVE, (SceneEvent ev, SceneEventContext ctx) -> {
            if (!armed[0]) {
                return;
            }
            if (!dragging[0]) {
                if (!exceedsActivationThreshold(startX[0], startY[0], ctx.getRawPointerX(), ctx.getRawPointerY())) {
                    ctx.stopPropagation();
                    return;
                }
                // DOWN 与实际激活之间可能先发生 blur/external controlled update；以激活帧真值开手势。
                List<T> startOrder = immutableCopy(orderSignal.get());
                if (!draggedGeometryMatchesOrder(viewport, handle, startOrder, idExtractor, dragId)) {
                    // keyed reconcile 尚未兑现同帧 authority；保留 armed，等下一次 MOVE 基于新几何重试。
                    ctx.stopPropagation();
                    return;
                }
                dragStartOrder.set(startOrder);
                dragOrder.set(startOrder);
                observedOrder.set(startOrder);
                synchronizeDragScroll(scrollSignal, dragScrollY, observedScrollY);
                int treeRootAbsY = treeRootAbsY(handle, ctx.getRawPointerY(), ctx.getLocalPointerY());
                AnchorRect draggedBox = SceneGeometry.absoluteBox(draggedRow(handle), 0, 0);
                // 以当前 keyed 几何吸收 DOWN 后的外部位移，但仍用原始 DOWN 坐标保留阈值前指针增量。
                grabOffsetY[0] = startY[0] - (draggedBox.getY() + treeRootAbsY);
                pointerToDraggedCenterY[0] = draggedCenterY(handle, ctx.getRawPointerY(), ctx.getLocalPointerY())
                        - startY[0];
                try {
                    if (onDragStart != null) {
                        onDragStart.run();
                    }
                } catch (RuntimeException | Error failure) {
                    resetGestureState(armed, dragging, startX, startY, pointerToDraggedCenterY, grabOffsetY,
                            dragScrollY, observedScrollY, dragOffsetY, dragStartOrder, dragOrder, observedOrder,
                            dragOffsetSig, onReset);
                    throw failure;
                }
                dragging[0] = true;
                ctx.requestPointerCapture();
            } else {
                if (synchronizeDragOrder(orderSignal, dragStartOrder, dragOrder, observedOrder)) {
                    int treeRootAbsY = treeRootAbsY(handle, ctx.getRawPointerY(), ctx.getLocalPointerY());
                    AnchorRect draggedBox = SceneGeometry.absoluteBox(draggedRow(handle), 0, 0);
                    grabOffsetY[0] = ctx.getRawPointerY() - (draggedBox.getY() + treeRootAbsY);
                    pointerToDraggedCenterY[0] = draggedCenterY(
                            handle, ctx.getRawPointerY(), ctx.getLocalPointerY()) - ctx.getRawPointerY();
                }
            }
            int draggedCenterY = ctx.getRawPointerY() + pointerToDraggedCenterY[0];
            autoScrollIfNeeded(scrollViewport, scrollSignal, handle, dragScrollY, observedScrollY,
                    ctx.getRawPointerY(), ctx.getLocalPointerY());
            int pendingScrollDelta = scrollSignal == null
                    ? 0 : dragScrollY[0] - scrollViewport.getScrollOffsetY();
            int targetIndex = pointerToRowIndex(viewport, handle, ctx.getRawPointerY(), ctx.getLocalPointerY(),
                    draggedCenterY + pendingScrollDelta);
            if (targetIndex < 0) {
                ctx.stopPropagation();
                return;
            }
            int targetLayoutTopY = predictedDraggedTopY(viewport, handle, targetIndex) - pendingScrollDelta;
            dragOffsetY[0] = clampedDragOffsetY(scrollViewport, draggedRow(handle), handle,
                    ctx.getRawPointerY(), ctx.getLocalPointerY(), grabOffsetY[0], targetLayoutTopY);
            dragOffsetSig.set(Integer.valueOf(dragOffsetY[0]));
            List<T> next = moveItem(dragOrder.get(), idExtractor, dragId, targetIndex);
            if (next != null) {
                dragOrder.set(next);
                onPreviewOrder.accept(next);
            }
            ctx.stopPropagation();
        });
        rt.on(handle, SceneEventType.SCROLL, (SceneEvent ev, SceneEventContext ctx) -> {
            if (!armed[0]) {
                return;
            }
            if (scrollSignal == null) {
                if (dragging[0]) {
                    ctx.stopPropagation();
                }
                return;
            }
            int current = synchronizeDragScroll(scrollSignal, dragScrollY, observedScrollY);
            int next = clamp(current - ev.getWheelDelta(), 0, SceneGeometry.maxScrollY(scrollViewport));
            if (next == current) {
                if (dragging[0]) {
                    ctx.stopPropagation();
                }
                return;
            }
            dragScrollY[0] = next;
            scrollSignal.set(Integer.valueOf(next));
            if (dragging[0]) {
                dragOffsetY[0] += next - current;
                dragOffsetSig.set(Integer.valueOf(dragOffsetY[0]));
            }
            ctx.stopPropagation();
        });
        rt.on(handle, SceneEventType.POINTER_UP, (SceneEvent ev, SceneEventContext ctx) -> {
            boolean wasDragging = dragging[0];
            List<T> finalOrder;
            try {
                if (wasDragging) {
                    boolean externalOrderChanged = synchronizeDragOrder(
                            orderSignal, dragStartOrder, dragOrder, observedOrder);
                    List<T> previewOrder = dragOrder.get();
                    if (!externalOrderChanged) {
                        int draggedCenterY = ctx.getRawPointerY() + pointerToDraggedCenterY[0];
                        synchronizeDragScroll(scrollSignal, dragScrollY, observedScrollY);
                        int pendingScrollDelta = scrollSignal == null
                                ? 0 : dragScrollY[0] - scrollViewport.getScrollOffsetY();
                        int targetIndex = pointerToRowIndex(viewport, handle, ctx.getRawPointerY(),
                                ctx.getLocalPointerY(), draggedCenterY + pendingScrollDelta);
                        List<T> next = moveItem(dragOrder.get(), idExtractor, dragId, targetIndex);
                        if (next != null) {
                            dragOrder.set(next);
                        }
                    }
                    if (!previewOrder.equals(dragOrder.get())) {
                        onPreviewOrder.accept(dragOrder.get());
                    }
                }
                finalOrder = immutableCopy(dragOrder.get());
            } finally {
                resetGestureState(armed, dragging, startX, startY, pointerToDraggedCenterY, grabOffsetY,
                        dragScrollY, observedScrollY, dragOffsetY, dragStartOrder, dragOrder, observedOrder,
                        dragOffsetSig, onReset);
            }
            if (wasDragging) {
                onDropCommit.accept(finalOrder);
            }
            ctx.stopPropagation();
        });
        rt.on(handle, SceneEventType.POINTER_CANCEL, (SceneEvent ev, SceneEventContext ctx) -> {
            boolean wasDragging = dragging[0];
            List<T> startOrder;
            try {
                if (wasDragging) {
                    synchronizeDragOrder(orderSignal, dragStartOrder, dragOrder, observedOrder);
                }
                startOrder = dragStartOrder.get();
            } finally {
                resetGestureState(armed, dragging, startX, startY, pointerToDraggedCenterY, grabOffsetY,
                        dragScrollY, observedScrollY, dragOffsetY, dragStartOrder, dragOrder, observedOrder,
                        dragOffsetSig, onReset);
            }
            if (wasDragging) {
                onCancel.accept(startOrder);
            }
        });
        return handle;
    }

    /**
     * 统一释放一次手势捕获的全部瞬态；回调所需快照必须在调用方先读出。
     */
    private static <T> void resetGestureState(boolean[] armed,
                                               boolean[] dragging,
                                               int[] startX,
                                               int[] startY,
                                               int[] pointerToDraggedCenterY,
                                               int[] grabOffsetY,
                                               int[] dragScrollY,
                                               int[] observedScrollY,
                                               int[] dragOffsetY,
                                               AtomicReference<List<T>> dragStartOrder,
                                               AtomicReference<List<T>> dragOrder,
                                               AtomicReference<List<T>> observedOrder,
                                               Signal<Integer> dragOffsetSig,
                                               Consumer<GestureStateSnapshot> onReset) {
        armed[0] = false;
        dragging[0] = false;
        startX[0] = 0;
        startY[0] = 0;
        pointerToDraggedCenterY[0] = 0;
        grabOffsetY[0] = 0;
        dragScrollY[0] = 0;
        observedScrollY[0] = 0;
        dragOffsetY[0] = 0;
        dragStartOrder.set(Collections.<T>emptyList());
        dragOrder.set(Collections.<T>emptyList());
        observedOrder.set(Collections.<T>emptyList());
        dragOffsetSig.set(Integer.valueOf(0));
        if (onReset != null) {
            // set(0) 只登记同帧目标；observer 必须读取真实闭包和当前 signal，不能把 pending 写入伪装成已生效。
            onReset.accept(new GestureStateSnapshot(armed[0], dragging[0], startX[0], startY[0],
                    pointerToDraggedCenterY[0], grabOffsetY[0], dragScrollY[0], observedScrollY[0], dragOffsetY[0],
                    immutableCopy(dragStartOrder.get()),
                    immutableCopy(dragOrder.get()), immutableCopy(observedOrder.get()),
                    dragOffsetSig.get().intValue(), 0, dragOffsetSig));
        }
    }

    /** 包级测试 observer 快照，避免测试反射读取手势闭包。 */
    static final class GestureStateSnapshot {
        final boolean armed;
        final boolean dragging;
        final int startX;
        final int startY;
        final int pointerToDraggedCenterY;
        final int grabOffsetY;
        final int dragScrollY;
        final int observedScrollY;
        final int dragOffsetY;
        final List<?> dragStartOrder;
        final List<?> dragOrder;
        final List<?> observedOrder;
        /** reset 目标已为零，但同帧 flush 前仍可能是旧值。 */
        final int dragOffsetCurrent;
        final int dragOffsetTarget;
        /** 仅供包级测试在 route+harness flush 后读取当前 signal。 */
        final Signal<Integer> dragOffsetSignal;

        GestureStateSnapshot(boolean armed, boolean dragging, int startX, int startY,
                             int pointerToDraggedCenterY, int grabOffsetY, int dragScrollY,
                             int observedScrollY, int dragOffsetY,
                             List<?> dragStartOrder, List<?> dragOrder, List<?> observedOrder,
                             int dragOffsetCurrent, int dragOffsetTarget, Signal<Integer> dragOffsetSignal) {
            this.armed = armed;
            this.dragging = dragging;
            this.startX = startX;
            this.startY = startY;
            this.pointerToDraggedCenterY = pointerToDraggedCenterY;
            this.grabOffsetY = grabOffsetY;
            this.dragScrollY = dragScrollY;
            this.observedScrollY = observedScrollY;
            this.dragOffsetY = dragOffsetY;
            this.dragStartOrder = dragStartOrder;
            this.dragOrder = dragOrder;
            this.observedOrder = observedOrder;
            this.dragOffsetCurrent = dragOffsetCurrent;
            this.dragOffsetTarget = dragOffsetTarget;
            this.dragOffsetSignal = dragOffsetSignal;
        }
    }

    /**
     * 判断本次移动是否超过拖拽激活阈值。
     */
    private static boolean exceedsActivationThreshold(int startX, int startY, int currentX, int currentY) {
        int dx = currentX - startX;
        int dy = currentY - startY;
        return dx * dx + dy * dy >= DRAG_ACTIVATION_THRESHOLD_PX * DRAG_ACTIVATION_THRESHOLD_PX;
    }

    /** authority 已换位但 keyed layout 尚未兑现时，不得把旧几何 index 套到新顺序。 */
    private static <T> boolean draggedGeometryMatchesOrder(SceneNode viewport,
                                                            SceneNode handle,
                                                            List<T> order,
                                                            ToLongFunction<T> idExtractor,
                                                            long dragId) {
        int geometryIndex = viewport.__getChildren().indexOf(draggedRow(handle));
        int orderIndex = indexOfId(order, idExtractor, dragId);
        return geometryIndex >= 0 && geometryIndex == orderIndex;
    }

    /**
     * 按被拖行中心计算插入槽位；越过相邻行中线即换位。
     *
     * @param viewport     列表视口
     * @param handle       当前 capture 节点
     * @param rawPointerY  指针屏幕绝对 Y
     * @param handleLocalY handle 局部 Y
     * @param draggedCenterY 被拖行中心的屏幕绝对 Y
     * @return 目标行 index；viewport 为空返回 -1
     */
    static int pointerToRowIndex(SceneNode viewport, SceneNode handle, int rawPointerY, int handleLocalY,
                                 int draggedCenterY) {
        List<SceneNode> children = viewport.__getChildren();
        if (children.isEmpty()) {
            return -1;
        }
        SceneNode draggedRow = draggedRow(handle);
        int fromIndex = children.indexOf(draggedRow);
        if (fromIndex < 0) {
            return -1;
        }
        int treeRootAbsY = treeRootAbsY(handle, rawPointerY, handleLocalY);
        int targetIndex = 0;
        for (SceneNode rowNode : children) {
            if (rowNode == draggedRow) {
                continue;
            }
            if (draggedCenterY > centerY(rowNode, treeRootAbsY)) {
                targetIndex++;
            }
        }
        return targetIndex;
    }

    /**
     * 计算被拖行当前中心 Y。
     */
    private static int draggedCenterY(SceneNode handle, int rawPointerY, int handleLocalY) {
        return centerY(draggedRow(handle), treeRootAbsY(handle, rawPointerY, handleLocalY));
    }

    /**
     * @return 把手所属行节点。
     */
    private static SceneNode draggedRow(SceneNode handle) {
        SceneNode parent = handle.__getParent();
        return parent == null ? handle : parent;
    }

    /**
     * 反推当前派发树根的屏幕绝对 Y。
     */
    private static int treeRootAbsY(SceneNode handle, int rawPointerY, int handleLocalY) {
        int handleLayoutY = SceneGeometry.absoluteBox(handle, 0, 0).getY();
        return (rawPointerY - handleLocalY) - handleLayoutY;
    }

    /**
     * 计算节点在屏幕坐标系下的中心 Y。
     */
    private static int centerY(SceneNode node, int treeRootAbsY) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + treeRootAbsY + box.getHeight() / 2;
    }

    /**
     * 计算被拖行的 viewport 内 clamp 后浮起偏移。
     */
    private static int clampedDragOffsetY(SceneNode viewport, SceneNode draggedRow, SceneNode handle,
                                           int rawPointerY, int handleLocalY, int grabOffsetY,
                                           int targetLayoutTopY) {
        int treeRootAbsY = treeRootAbsY(handle, rawPointerY, handleLocalY);
        AnchorRect draggedBox = SceneGeometry.absoluteBox(draggedRow, 0, 0);
        AnchorRect viewportBox = SceneGeometry.absoluteBox(viewport, 0, 0);
        int desiredTopY = rawPointerY - treeRootAbsY - grabOffsetY;
        int minTopY = viewportBox.getY();
        int maxTopY = viewportBox.getY() + viewportBox.getHeight() - draggedBox.getHeight();
        return clamp(desiredTopY, minTopY, maxTopY) - targetLayoutTopY;
    }

    /**
     * 预测 keyed 列表完成本次换位后，被拖行 LayoutBox 的 top。
     */
    private static int predictedDraggedTopY(SceneNode viewport, SceneNode handle, int targetIndex) {
        List<SceneNode> children = viewport.__getChildren();
        SceneNode draggedRow = draggedRow(handle);
        int fromIndex = children.indexOf(draggedRow);
        AnchorRect draggedBox = SceneGeometry.absoluteBox(draggedRow, 0, 0);
        if (fromIndex < 0 || targetIndex < 0 || targetIndex >= children.size() || targetIndex == fromIndex) {
            return draggedBox.getY();
        }
        AnchorRect targetBox = SceneGeometry.absoluteBox(children.get(targetIndex), 0, 0);
        if (targetIndex < fromIndex) {
            return targetBox.getY();
        }
        return targetBox.getY() + targetBox.getHeight() - draggedBox.getHeight();
    }

    /**
     * 将值夹到闭区间。
     */
    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 指针处于视口边缘区时按 MOVE 节奏滚动。
     */
    private static void autoScrollIfNeeded(SceneNode viewport, Signal<Integer> scrollSignal, SceneNode handle,
                                           int[] dragScrollY, int[] observedScrollY,
                                           int rawPointerY, int handleLocalY) {
        if (scrollSignal == null) {
            return;
        }
        int treeRootAbsY = treeRootAbsY(handle, rawPointerY, handleLocalY);
        AnchorRect viewportBox = SceneGeometry.absoluteBox(viewport, 0, 0);
        int vpAbsTop = viewportBox.getY() + treeRootAbsY;
        int vpAbsBottom = vpAbsTop + viewportBox.getHeight();
        int zone = Math.min(AUTO_SCROLL_ZONE_PX, Math.max(1, viewportBox.getHeight() / 3));
        int curScroll = synchronizeDragScroll(scrollSignal, dragScrollY, observedScrollY);
        int maxScroll = SceneGeometry.maxScrollY(viewport);
        int nextScroll = curScroll;
        if (rawPointerY < vpAbsTop + zone) {
            int dist = rawPointerY - vpAbsTop;
            int speed = autoScrollSpeed(dist, zone);
            nextScroll = Math.max(0, curScroll - speed);
        } else if (rawPointerY > vpAbsBottom - zone) {
            int dist = vpAbsBottom - rawPointerY;
            int speed = autoScrollSpeed(dist, zone);
            nextScroll = Math.min(maxScroll, curScroll + speed);
        }
        if (nextScroll != curScroll) {
            dragScrollY[0] = nextScroll;
            scrollSignal.set(Integer.valueOf(nextScroll));
        }
    }

    /** 将已 flush 的外部滚动值并入手势本地目标，同时保留本帧尚未 flush 的自身累计写入。 */
    private static int synchronizeDragScroll(Signal<Integer> scrollSignal,
                                             int[] dragScrollY,
                                             int[] observedScrollY) {
        if (scrollSignal == null) {
            return 0;
        }
        Integer value = scrollSignal.get();
        int current = value == null ? 0 : value.intValue();
        if (current != observedScrollY[0]) {
            observedScrollY[0] = current;
            dragScrollY[0] = current;
        }
        return dragScrollY[0];
    }

    /** 合入已 flush 的受控顺序；自身 preview 兑现只推进观察点，外部版本则成为新的取消基线。 */
    private static <T> boolean synchronizeDragOrder(ReadableSignal<List<T>> orderSignal,
                                                    AtomicReference<List<T>> dragStartOrder,
                                                    AtomicReference<List<T>> dragOrder,
                                                    AtomicReference<List<T>> observedOrder) {
        List<T> current = immutableCopy(orderSignal.get());
        if (current.equals(observedOrder.get())) {
            return false;
        }
        observedOrder.set(current);
        if (!current.equals(dragOrder.get())) {
            dragStartOrder.set(current);
            dragOrder.set(current);
            return true;
        }
        return false;
    }

    /**
     * 根据距边缘距离计算滚动速度，越靠近边缘越快。
     */
    private static int autoScrollSpeed(int distanceFromEdge, int zone) {
        int clampedDistance = Math.max(0, Math.min(zone, distanceFromEdge));
        int speed = (zone - clampedDistance) * AUTO_SCROLL_MAX_SPEED_PX / zone;
        return Math.max(AUTO_SCROLL_MIN_SPEED_PX, speed);
    }

    /**
     * 移动指定 id 的行。
     *
     * @param items       当前顺序
     * @param idExtractor 行 id 读取器
     * @param fromId      被拖拽行 id
     * @param toIndex     目标 index
     * @param <T>         行数据类型
     * @return 新的不可变顺序；无变化返回 null
     */
    static <T> List<T> moveItem(List<T> items, ToLongFunction<T> idExtractor, long fromId, int toIndex) {
        List<T> current = safeList(items);
        int fromIndex = indexOfId(current, idExtractor, fromId);
        if (fromIndex < 0 || toIndex < 0 || toIndex >= current.size() || fromIndex == toIndex) {
            return null;
        }
        List<T> next = new ArrayList<T>(current);
        T moved = next.remove(fromIndex);
        next.add(toIndex, moved);
        return Collections.unmodifiableList(next);
    }

    private static <T> int indexOfId(List<T> items, ToLongFunction<T> idExtractor, long id) {
        for (int i = 0; i < items.size(); i++) {
            if (idExtractor.applyAsLong(items.get(i)) == id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * null 安全读取列表。
     */
    private static <T> List<T> safeList(List<T> items) {
        return items == null ? Collections.<T>emptyList() : items;
    }

    /**
     * 返回列表的不可变快照。
     */
    private static <T> List<T> immutableCopy(List<T> items) {
        return Collections.unmodifiableList(new ArrayList<T>(safeList(items)));
    }
}
