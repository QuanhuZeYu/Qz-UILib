package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import club.heiqi.uilib.ui.reactive.Signal;
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

    /** 纯静态工具类，禁止实例化。 */
    private SceneDragReorder() {
    }

    /**
     * 构建拖拽把手并注册排序事件。
     *
     * @param rt             场景运行时
     * @param viewport       列表视口，子节点顺序与 orderSignal 一致
     * @param scrollSignal   视口滚动 signal，当前阶段可为 null，预留给后续 auto-scroll
     * @param dragId         当前被拖排行 id
     * @param orderSignal    当前顺序真值或预览顺序 signal
     * @param idExtractor    行 id 读取器
     * @param onPreviewOrder MOVE 期间产生新顺序后的回调
     * @param onDropCommit   UP 时提交当前顺序的回调
     * @param onCancel       CANCEL 时回滚/清理回调
     * @param <T>            行数据类型
     * @return 拖拽把手节点
     */
    public static <T> SceneNode buildHandle(SceneRuntime rt,
                                            SceneNode viewport,
                                            Signal<Integer> scrollSignal,
                                            long dragId,
                                            Signal<List<T>> orderSignal,
                                            ToLongFunction<T> idExtractor,
                                            Consumer<List<T>> onPreviewOrder,
                                            Consumer<List<T>> onDropCommit,
                                            Runnable onCancel) {
        final boolean[] armed = {false};
        final boolean[] dragging = {false};
        final int[] startX = {0};
        final int[] startY = {0};

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

        rt.on(handle, SceneEventType.POINTER_DOWN, (SceneEvent ev, SceneEventContext ctx) -> {
            armed[0] = true;
            dragging[0] = false;
            startX[0] = ctx.getRawPointerX();
            startY[0] = ctx.getRawPointerY();
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
                dragging[0] = true;
                ctx.requestPointerCapture();
            }
            int targetIndex = pointerToRowIndex(viewport, handle, ctx.getRawPointerY(), ctx.getLocalPointerY());
            if (targetIndex < 0) {
                ctx.stopPropagation();
                return;
            }
            List<T> next = moveItem(orderSignal.get(), idExtractor, dragId, targetIndex);
            if (next != null) {
                onPreviewOrder.accept(next);
            }
            ctx.stopPropagation();
        });
        rt.on(handle, SceneEventType.POINTER_UP, (SceneEvent ev, SceneEventContext ctx) -> {
            boolean shouldCommit = dragging[0];
            armed[0] = false;
            dragging[0] = false;
            if (shouldCommit) {
                onDropCommit.accept(safeList(orderSignal.get()));
            }
            ctx.stopPropagation();
        });
        rt.on(handle, SceneEventType.POINTER_CANCEL, (SceneEvent ev, SceneEventContext ctx) -> {
            armed[0] = false;
            dragging[0] = false;
            onCancel.run();
        });
        return handle;
    }

    /**
     * 判断本次移动是否超过拖拽激活阈值。
     */
    private static boolean exceedsActivationThreshold(int startX, int startY, int currentX, int currentY) {
        int dx = currentX - startX;
        int dy = currentY - startY;
        return dx * dx + dy * dy > DRAG_ACTIVATION_THRESHOLD_PX * DRAG_ACTIVATION_THRESHOLD_PX;
    }

    /**
     * 按指针 Y 计算拖拽目标行 index。
     *
     * @param viewport     列表视口
     * @param handle       当前 capture 节点
     * @param rawPointerY  指针屏幕绝对 Y
     * @param handleLocalY handle 局部 Y
     * @return 目标行 index；viewport 为空返回 -1
     */
    static int pointerToRowIndex(SceneNode viewport, SceneNode handle, int rawPointerY, int handleLocalY) {
        List<SceneNode> children = viewport.__getChildren();
        if (children.isEmpty()) {
            return -1;
        }
        int handleLayoutY = SceneGeometry.absoluteBox(handle, 0, 0).getY();
        int treeRootAbsY = (rawPointerY - handleLocalY) - handleLayoutY;
        int lastIndex = children.size() - 1;
        for (int i = 0; i <= lastIndex; i++) {
            AnchorRect box = SceneGeometry.absoluteBox(children.get(i), 0, 0);
            int screenTop = box.getY() + treeRootAbsY;
            int center = screenTop + box.getHeight() / 2;
            if (rawPointerY < center) {
                return i;
            }
        }
        return lastIndex;
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
        int fromIndex = -1;
        for (int i = 0; i < current.size(); i++) {
            if (idExtractor.applyAsLong(current.get(i)) == fromId) {
                fromIndex = i;
                break;
            }
        }
        if (fromIndex < 0 || toIndex < 0 || toIndex >= current.size() || fromIndex == toIndex) {
            return null;
        }
        List<T> next = new ArrayList<T>(current);
        T moved = next.remove(fromIndex);
        next.add(toIndex, moved);
        return Collections.unmodifiableList(next);
    }

    /**
     * null 安全读取列表。
     */
    private static <T> List<T> safeList(List<T> items) {
        return items == null ? Collections.<T>emptyList() : items;
    }
}
