package club.heiqi.uilib.ui.diagnostic;

import java.util.Locale;

/**
 * UI 框架运行时性能统计快照。
 */
public class UiRuntimeStats {

    private static final UiRuntimeStats EMPTY = new UiRuntimeStats(
            "",
            0,
            0,
            0,
            0,
            0L,
            0L,
            0L,
            0.0D,
            0L,
            0L,
            0L,
            0,
            0,
            0,
            0L,
            0L,
            0,
            0,
            "",
            0L,
            "",
            0L,
            "",
            0,
            0);

    private final String screenName;
    private final int guiWidth;
    private final int guiHeight;
    private final int nativeWidth;
    private final int nativeHeight;
    private final long frameTimeNanos;
    private final long averageFrameTimeNanos;
    private final long maxFrameTimeNanos;
    private final double averageFps;
    private final long renderTimeNanos;
    private final long averageRenderTimeNanos;
    private final long presentTimeNanos;
    private final int mouseEventCount;
    private final int keyEventCount;
    private final int textEventCount;
    private final long inputRoutingTimeNanos;
    private final long hitTestVisitCount;
    private final int widgetRenderCount;
    private final int maxWidgetDepth;
    private final String slowestWidgetSelfClassName;
    private final long slowestWidgetSelfTimeNanos;
    private final String slowestWidgetTotalClassName;
    private final long slowestWidgetTotalTimeNanos;
    private final String phaseSummary;
    private final int slowFrameCount;
    private final int sampledFrameCount;

    /**
     * 创建运行时统计快照。
     *
     * @param screenName 当前界面名
     * @param guiWidth GUI 逻辑宽度
     * @param guiHeight GUI 逻辑高度
     * @param nativeWidth 原生渲染宽度
     * @param nativeHeight 原生渲染高度
     * @param frameTimeNanos 当前帧总耗时
     * @param averageFrameTimeNanos 滚动窗口平均帧耗时
     * @param maxFrameTimeNanos 滚动窗口最大帧耗时
     * @param averageFps 滚动窗口平均 FPS
     * @param renderTimeNanos 当前帧渲染耗时
     * @param averageRenderTimeNanos 滚动窗口平均渲染耗时
     * @param presentTimeNanos 当前帧贴屏耗时
     * @param mouseEventCount 当前帧鼠标事件数
     * @param keyEventCount 当前帧键盘事件数
     * @param textEventCount 当前帧文本输入事件数
     * @param inputRoutingTimeNanos 当前帧输入路由耗时
     * @param hitTestVisitCount 当前帧命中测试访问次数
     * @param widgetRenderCount 当前帧组件渲染次数
     * @param maxWidgetDepth 当前帧组件最大深度
     * @param slowestWidgetSelfClassName 当前帧最慢自身组件类型
     * @param slowestWidgetSelfTimeNanos 当前帧最慢自身组件耗时
     * @param slowestWidgetTotalClassName 当前帧最慢总计组件类型
     * @param slowestWidgetTotalTimeNanos 当前帧最慢总计组件耗时
     * @param phaseSummary 当前帧阶段摘要
     * @param slowFrameCount 滚动窗口慢帧数量
     * @param sampledFrameCount 滚动窗口采样帧数
     */
    public UiRuntimeStats(
            String screenName,
            int guiWidth,
            int guiHeight,
            int nativeWidth,
            int nativeHeight,
            long frameTimeNanos,
            long averageFrameTimeNanos,
            long maxFrameTimeNanos,
            double averageFps,
            long renderTimeNanos,
            long averageRenderTimeNanos,
            long presentTimeNanos,
            int mouseEventCount,
            int keyEventCount,
            int textEventCount,
            long inputRoutingTimeNanos,
            long hitTestVisitCount,
            int widgetRenderCount,
            int maxWidgetDepth,
            String slowestWidgetSelfClassName,
            long slowestWidgetSelfTimeNanos,
            String slowestWidgetTotalClassName,
            long slowestWidgetTotalTimeNanos,
            String phaseSummary,
            int slowFrameCount,
            int sampledFrameCount) {
        this.screenName = screenName;
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
        this.nativeWidth = nativeWidth;
        this.nativeHeight = nativeHeight;
        this.frameTimeNanos = frameTimeNanos;
        this.averageFrameTimeNanos = averageFrameTimeNanos;
        this.maxFrameTimeNanos = maxFrameTimeNanos;
        this.averageFps = averageFps;
        this.renderTimeNanos = renderTimeNanos;
        this.averageRenderTimeNanos = averageRenderTimeNanos;
        this.presentTimeNanos = presentTimeNanos;
        this.mouseEventCount = mouseEventCount;
        this.keyEventCount = keyEventCount;
        this.textEventCount = textEventCount;
        this.inputRoutingTimeNanos = inputRoutingTimeNanos;
        this.hitTestVisitCount = hitTestVisitCount;
        this.widgetRenderCount = widgetRenderCount;
        this.maxWidgetDepth = maxWidgetDepth;
        this.slowestWidgetSelfClassName = slowestWidgetSelfClassName;
        this.slowestWidgetSelfTimeNanos = slowestWidgetSelfTimeNanos;
        this.slowestWidgetTotalClassName = slowestWidgetTotalClassName;
        this.slowestWidgetTotalTimeNanos = slowestWidgetTotalTimeNanos;
        this.phaseSummary = phaseSummary;
        this.slowFrameCount = slowFrameCount;
        this.sampledFrameCount = sampledFrameCount;
    }

    /**
     * 获取空统计快照。
     *
     * @return 空快照
     */
    public static UiRuntimeStats empty() {
        return EMPTY;
    }

    public String getScreenName() {
        return screenName;
    }

    public int getGuiWidth() {
        return guiWidth;
    }

    public int getGuiHeight() {
        return guiHeight;
    }

    public int getNativeWidth() {
        return nativeWidth;
    }

    public int getNativeHeight() {
        return nativeHeight;
    }

    public long getFrameTimeNanos() {
        return frameTimeNanos;
    }

    public long getAverageFrameTimeNanos() {
        return averageFrameTimeNanos;
    }

    public long getMaxFrameTimeNanos() {
        return maxFrameTimeNanos;
    }

    public double getAverageFps() {
        return averageFps;
    }

    public long getRenderTimeNanos() {
        return renderTimeNanos;
    }

    public long getAverageRenderTimeNanos() {
        return averageRenderTimeNanos;
    }

    public long getPresentTimeNanos() {
        return presentTimeNanos;
    }

    public int getMouseEventCount() {
        return mouseEventCount;
    }

    public int getKeyEventCount() {
        return keyEventCount;
    }

    public int getTextEventCount() {
        return textEventCount;
    }

    public long getInputRoutingTimeNanos() {
        return inputRoutingTimeNanos;
    }

    public long getHitTestVisitCount() {
        return hitTestVisitCount;
    }

    public int getWidgetRenderCount() {
        return widgetRenderCount;
    }

    public int getMaxWidgetDepth() {
        return maxWidgetDepth;
    }

    public String getSlowestWidgetSelfClassName() {
        return slowestWidgetSelfClassName;
    }

    public long getSlowestWidgetSelfTimeNanos() {
        return slowestWidgetSelfTimeNanos;
    }

    public String getSlowestWidgetTotalClassName() {
        return slowestWidgetTotalClassName;
    }

    public long getSlowestWidgetTotalTimeNanos() {
        return slowestWidgetTotalTimeNanos;
    }

    public String getPhaseSummary() {
        return phaseSummary;
    }

    public int getSlowFrameCount() {
        return slowFrameCount;
    }

    public int getSampledFrameCount() {
        return sampledFrameCount;
    }

    public double getFrameTimeMs() {
        return nanosToMs(frameTimeNanos);
    }

    public double getAverageFrameTimeMs() {
        return nanosToMs(averageFrameTimeNanos);
    }

    public double getMaxFrameTimeMs() {
        return nanosToMs(maxFrameTimeNanos);
    }

    public double getRenderTimeMs() {
        return nanosToMs(renderTimeNanos);
    }

    public double getAverageRenderTimeMs() {
        return nanosToMs(averageRenderTimeNanos);
    }

    public double getPresentTimeMs() {
        return nanosToMs(presentTimeNanos);
    }

    public double getInputRoutingTimeMs() {
        return nanosToMs(inputRoutingTimeNanos);
    }

    public double getSlowestWidgetSelfTimeMs() {
        return nanosToMs(slowestWidgetSelfTimeNanos);
    }

    public double getSlowestWidgetTotalTimeMs() {
        return nanosToMs(slowestWidgetTotalTimeNanos);
    }

    private double nanosToMs(long nanos) {
        return nanos / 1_000_000.0D;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT,
                "frame=%.2fms(avg=%.2fms,max=%.2fms,fps=%.1f), render=%.2fms(avg=%.2fms), present=%.2fms, input=%.2fms, events=%d/%d/%d, hitTests=%d, widgets=%d, depth=%d, slowWidgetSelf=%s %.2fms, slowWidgetTotal=%s %.2fms, phases=%s, slowFrames=%d/%d, viewport=%dx%d(gui)/%dx%d(native)",
                Double.valueOf(getFrameTimeMs()),
                Double.valueOf(getAverageFrameTimeMs()),
                Double.valueOf(getMaxFrameTimeMs()),
                Double.valueOf(averageFps),
                Double.valueOf(getRenderTimeMs()),
                Double.valueOf(getAverageRenderTimeMs()),
                Double.valueOf(getPresentTimeMs()),
                Double.valueOf(getInputRoutingTimeMs()),
                Integer.valueOf(mouseEventCount),
                Integer.valueOf(keyEventCount),
                Integer.valueOf(textEventCount),
                Long.valueOf(hitTestVisitCount),
                Integer.valueOf(widgetRenderCount),
                Integer.valueOf(maxWidgetDepth),
                slowestWidgetSelfClassName == null || slowestWidgetSelfClassName.isEmpty() ? "<none>" : slowestWidgetSelfClassName,
                Double.valueOf(getSlowestWidgetSelfTimeMs()),
                slowestWidgetTotalClassName == null || slowestWidgetTotalClassName.isEmpty() ? "<none>" : slowestWidgetTotalClassName,
                Double.valueOf(getSlowestWidgetTotalTimeMs()),
                phaseSummary == null || phaseSummary.isEmpty() ? "<none>" : phaseSummary,
                Integer.valueOf(slowFrameCount),
                Integer.valueOf(sampledFrameCount),
                Integer.valueOf(guiWidth),
                Integer.valueOf(guiHeight),
                Integer.valueOf(nativeWidth),
                Integer.valueOf(nativeHeight));
    }
}
