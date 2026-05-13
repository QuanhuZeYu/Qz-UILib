package club.heiqi.uilib.ui.host;

import java.util.Objects;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputRouter;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 文档宿主共享的交互会话。
 */
public final class DocumentHostInteractionSession {

    private final UiInputRouter inputRouter = new UiInputRouter();
    private int latestMouseX;
    private int latestMouseY;

    /**
     * 路由一帧输入并记录最近鼠标位置。
     *
     * @param runtimeName 运行时名称
     * @param root 根组件
     * @param frame 输入快照
     */
    public void route(String runtimeName, Widget root, UiInputFrame frame) {
        if (root == null || frame == null) {
            return;
        }
        latestMouseX = frame.getMouseX();
        latestMouseY = frame.getMouseY();
        UiPerformanceMonitor performanceMonitor = UiPerformanceMonitor.getInstance();
        performanceMonitor.beginInputRouting(Objects.requireNonNull(runtimeName, "runtimeName"), frame);
        try {
            inputRouter.route(root, frame);
        } finally {
            performanceMonitor.finishInputRouting();
        }
    }

    /**
     * 仅记录最近鼠标位置。
     *
     * @param frame 输入快照
     */
    public void recordPointer(UiInputFrame frame) {
        if (frame == null) {
            return;
        }
        latestMouseX = frame.getMouseX();
        latestMouseY = frame.getMouseY();
    }

    /**
     * 清理当前交互状态。
     */
    public void clearInteractionState() {
        inputRouter.clearInteractionState();
    }

    /**
     * 返回当前是否仍有有效焦点。
     *
     * @return 是否存在有效焦点
     */
    public boolean hasFocusedWidget() {
        return inputRouter.hasFocusedWidget();
    }

    /**
     * 返回最近一次输入记录的鼠标 X。
     *
     * @return 鼠标 X
     */
    public int getLatestMouseX() {
        return latestMouseX;
    }

    /**
     * 返回最近一次输入记录的鼠标 Y。
     *
     * @return 鼠标 Y
     */
    public int getLatestMouseY() {
        return latestMouseY;
    }
}
