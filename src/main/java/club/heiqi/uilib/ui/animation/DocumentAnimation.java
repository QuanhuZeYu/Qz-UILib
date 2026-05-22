package club.heiqi.uilib.ui.animation;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 命令式 keyframe animation 句柄。
 */
public final class DocumentAnimation {

    private final ElementNode element;
    private final long animationId;
    private final String animationName;
    private final long startTimeNanos;
    private final long durationNanos;
    private final Controller controller;
    private boolean cancelled;

    DocumentAnimation(ElementNode element, long animationId, String animationName, long startTimeNanos,
            long durationNanos, Controller controller) {
        this.element = Objects.requireNonNull(element, "element");
        this.animationId = animationId;
        this.animationName = Objects.requireNonNull(animationName, "animationName");
        this.startTimeNanos = startTimeNanos;
        this.durationNanos = Math.max(0L, durationNanos);
        this.controller = controller;
    }

    /**
     * 创建未启动的动画句柄。
     *
     * @param element 目标元素
     * @param animationName 动画名称
     * @param options 动画选项
     * @return 未启动句柄
     */
    public static DocumentAnimation inactive(ElementNode element, String animationName,
            DocumentAnimationOptions options) {
        DocumentAnimationOptions resolvedOptions = options == null ? DocumentAnimationOptions.ofMillis(0L) : options;
        return new DocumentAnimation(element, 0L, animationName == null ? "inactive" : animationName, 0L,
                resolvedOptions.getDurationNanos(), null);
    }

    /**
     * 取消当前动画。
     *
     * @return 是否实际取消了仍在运行的动画
     */
    public boolean cancel() {
        if (cancelled) {
            return false;
        }
        cancelled = true;
        return controller != null && controller.cancel(this);
    }

    /**
     * 返回动画是否仍在运行。
     *
     * @return 是否运行中
     */
    public boolean isRunning() {
        return !cancelled && controller != null && controller.isRunning(this);
    }

    /**
     * 返回目标元素。
     *
     * @return 目标元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回内部动画身份。
     *
     * @return 动画身份
     */
    public long getAnimationId() {
        return animationId;
    }

    /**
     * 返回动画名称。
     *
     * @return 动画名称
     */
    public String getAnimationName() {
        return animationName;
    }

    /**
     * 返回启动时间。
     *
     * @return 启动时间，单位纳秒
     */
    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    /**
     * 返回单次迭代持续时间。
     *
     * @return 持续时间，单位纳秒
     */
    public long getDurationNanos() {
        return durationNanos;
    }

    /** 命令式动画运行控制器。 */
    interface Controller {
        boolean cancel(DocumentAnimation animation);

        boolean isRunning(DocumentAnimation animation);
    }
}
