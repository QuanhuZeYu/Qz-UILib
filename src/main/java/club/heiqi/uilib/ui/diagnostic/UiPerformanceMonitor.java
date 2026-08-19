package club.heiqi.uilib.ui.diagnostic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.input.UiInputFrame;

/**
 * UI 框架性能采样器。
 */
public class UiPerformanceMonitor {

    private static final UiPerformanceMonitor INSTANCE = new UiPerformanceMonitor();
    private static final int HISTORY_SIZE = 120;
    private static final long SLOW_FRAME_THRESHOLD_NANOS = 16_666_667L;

    private final ThreadLocal<FrameSession> activeFrameSession = new ThreadLocal<FrameSession>();
    private final ThreadLocal<InputSession> activeInputSession = new ThreadLocal<InputSession>();
    private final Deque<Long> frameHistory = new ArrayDeque<Long>();
    private final Deque<Long> renderHistory = new ArrayDeque<Long>();

    private UiRuntimeStats latestStats = UiRuntimeStats.empty();
    private InputSession pendingInputSession;
    private long lastDebugLogAt;
    private String historyScreenName = "";

    private UiPerformanceMonitor() {}

    /**
     * 获取性能采样器单例。
     *
     * @return 采样器实例
     */
    public static UiPerformanceMonitor getInstance() {
        return INSTANCE;
    }

    /**
     * 获取最近一次完成帧的统计结果。
     *
     * @return 运行时统计快照
     */
    public synchronized UiRuntimeStats getRuntimeStats() {
        return latestStats;
    }

    /**
     * 记录当前帧中的阶段耗时。
     *
     * @param phaseName 阶段名
     * @param nanos 耗时
     */
    public void recordPhase(String phaseName, long nanos) {
        FrameSession session = activeFrameSession.get();
        if (session == null || phaseName == null || phaseName.isEmpty() || nanos <= 0L) {
            return;
        }
        session.recordPhase(phaseName, nanos);
    }

    /**
     * 开始记录当前 UI 帧。
     *
     * @param screenName 界面名
     * @param guiWidth GUI 逻辑宽度
     * @param guiHeight GUI 逻辑高度
     * @param nativeWidth 原生渲染宽度
     * @param nativeHeight 原生渲染高度
     */
    public void beginFrame(String screenName, int guiWidth, int guiHeight, int nativeWidth, int nativeHeight) {
        FrameSession session = new FrameSession(screenName, guiWidth, guiHeight, nativeWidth, nativeHeight);
        synchronized (this) {
            if (screenName != null && !screenName.equals(historyScreenName)) {
                clearHistories(screenName);
            }
            if (pendingInputSession != null) {
                if (screenName.equals(pendingInputSession.screenName)) {
                    session.applyInput(pendingInputSession);
                }
                pendingInputSession = null;
            }
        }
        activeFrameSession.set(session);
    }

    /**
     * 主动清空当前采样历史，供切页或重新进入诊断页后使用。
     *
     * @param screenName 当前界面名
     */
    public synchronized void resetHistory(String screenName) {
        clearHistories(screenName);
        latestStats = UiRuntimeStats.empty();
        pendingInputSession = null;
    }

    /**
     * 记录当前帧渲染阶段耗时。
     *
     * @param nanos 渲染耗时
     */
    public void recordRenderPhase(long nanos) {
        FrameSession session = activeFrameSession.get();
        if (session != null) {
            session.renderTimeNanos = Math.max(0L, nanos);
        }
    }

    /**
     * 记录当前帧贴屏阶段耗时。
     *
     * @param nanos 贴屏耗时
     */
    public void recordPresentPhase(long nanos) {
        FrameSession session = activeFrameSession.get();
        if (session != null) {
            session.presentTimeNanos = Math.max(0L, nanos);
        }
    }

    /**
     * 开始记录输入路由阶段。
     *
     * @param screenName 界面名
     * @param frame 输入快照
     */
    public void beginInputRouting(String screenName, UiInputFrame frame) {
        if (frame == null) {
            return;
        }
        activeInputSession.set(new InputSession(
                screenName,
                frame.getMouseEvents().size(),
                frame.getKeyEvents().size(),
                frame.getTextEvents().size()));
    }

    /**
     * 完成当前输入路由记录。
     */
    public void finishInputRouting() {
        InputSession session = activeInputSession.get();
        if (session == null) {
            return;
        }
        activeInputSession.remove();
        session.routingTimeNanos = System.nanoTime() - session.startNanos;
        synchronized (this) {
            pendingInputSession = session;
        }
    }

    /**
     * 记录一次命中测试访问。
     */
    public void recordHitTestVisit() {
        InputSession inputSession = activeInputSession.get();
        if (inputSession != null) {
            inputSession.hitTestVisitCount++;
            return;
        }

        FrameSession frameSession = activeFrameSession.get();
        if (frameSession != null) {
            frameSession.hitTestVisitCount++;
        }
    }

    /**
     * 完成当前 UI 帧统计。
     */
    public void finishFrame() {
        FrameSession session = activeFrameSession.get();
        if (session == null) {
            return;
        }
        activeFrameSession.remove();
        session.frameTimeNanos = System.nanoTime() - session.frameStartNanos;

        UiRuntimeStats stats;
        synchronized (this) {
            appendHistory(frameHistory, session.frameTimeNanos);
            appendHistory(renderHistory, session.renderTimeNanos);
            stats = buildStats(session);
            latestStats = stats;
        }
        debugLogStats(stats);
    }

    private synchronized UiRuntimeStats buildStats(FrameSession session) {
        long averageFrameTime = average(frameHistory);
        long maxFrameTime = max(frameHistory);
        long averageRenderTime = average(renderHistory);
        double averageFps = averageFrameTime <= 0L ? 0.0D : 1_000_000_000.0D / averageFrameTime;
        int slowFrameCount = countSlowFrames(frameHistory);
        return new UiRuntimeStats(
                session.screenName,
                session.guiWidth,
                session.guiHeight,
                session.nativeWidth,
                session.nativeHeight,
                session.frameTimeNanos,
                averageFrameTime,
                maxFrameTime,
                averageFps,
                session.renderTimeNanos,
                averageRenderTime,
                session.presentTimeNanos,
                session.mouseEventCount,
                session.keyEventCount,
                session.textEventCount,
                session.inputRoutingTimeNanos,
                session.hitTestVisitCount,
                session.widgetRenderCount,
                session.maxWidgetDepth,
                session.slowestWidgetSelfClassName,
                session.slowestWidgetSelfTimeNanos,
                session.slowestWidgetTotalClassName,
                session.slowestWidgetTotalTimeNanos,
                buildPhaseSummary(session),
                slowFrameCount,
                frameHistory.size());
    }

    private String buildPhaseSummary(FrameSession session) {
        if (session.phaseSamples.isEmpty()) {
            return "";
        }

        List<Map.Entry<String, PhaseSample>> entries = new ArrayList<Map.Entry<String, PhaseSample>>(session.phaseSamples.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, PhaseSample>>() {
            @Override
            public int compare(Map.Entry<String, PhaseSample> first, Map.Entry<String, PhaseSample> second) {
                long delta = second.getValue().totalNanos - first.getValue().totalNanos;
                if (delta > 0L) {
                    return 1;
                }
                if (delta < 0L) {
                    return -1;
                }
                return first.getKey().compareTo(second.getKey());
            }
        });

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(4, entries.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append("；");
            }
            Map.Entry<String, PhaseSample> entry = entries.get(index);
            builder.append(entry.getKey())
                    .append(' ')
                    .append(String.format(java.util.Locale.ROOT, "%.2fms x%d",
                            Double.valueOf(entry.getValue().totalNanos / 1_000_000.0D),
                            Integer.valueOf(entry.getValue().count)));
        }
        return builder.toString();
    }

    private void debugLogStats(UiRuntimeStats stats) {
        if (!Config.useDebug || stats.getSampledFrameCount() <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - lastDebugLogAt < 1000L) {
                return;
            }
            lastDebugLogAt = now;
        }
        MyMod.LOG.info("UI 运行统计[{}]: {}", stats.getScreenName(), stats);
    }

    private void appendHistory(Deque<Long> history, long value) {
        history.addLast(Long.valueOf(Math.max(0L, value)));
        while (history.size() > HISTORY_SIZE) {
            history.pollFirst();
        }
    }

    private void clearHistories(String screenName) {
        frameHistory.clear();
        renderHistory.clear();
        historyScreenName = screenName == null ? "" : screenName;
    }

    private long average(Deque<Long> history) {
        if (history.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Long value : history) {
            total += value.longValue();
        }
        return total / history.size();
    }

    private long max(Deque<Long> history) {
        long max = 0L;
        for (Long value : history) {
            max = Math.max(max, value.longValue());
        }
        return max;
    }

    private int countSlowFrames(Deque<Long> history) {
        int count = 0;
        for (Long value : history) {
            if (value.longValue() >= SLOW_FRAME_THRESHOLD_NANOS) {
                count++;
            }
        }
        return count;
    }

    /**
     * 一帧渲染中的临时统计状态。
     */
    private final class FrameSession {

        private final String screenName;
        private final int guiWidth;
        private final int guiHeight;
        private final int nativeWidth;
        private final int nativeHeight;
        private final long frameStartNanos = System.nanoTime();
        private final Map<String, PhaseSample> phaseSamples = new LinkedHashMap<String, PhaseSample>();

        private long frameTimeNanos;
        private long renderTimeNanos;
        private long presentTimeNanos;
        private int mouseEventCount;
        private int keyEventCount;
        private int textEventCount;
        private long inputRoutingTimeNanos;
        private long hitTestVisitCount;
        private int widgetRenderCount;
        private int maxWidgetDepth;
        private String slowestWidgetSelfClassName = "";
        private long slowestWidgetSelfTimeNanos;
        private String slowestWidgetTotalClassName = "";
        private long slowestWidgetTotalTimeNanos;

        private FrameSession(String screenName, int guiWidth, int guiHeight, int nativeWidth, int nativeHeight) {
            this.screenName = screenName;
            this.guiWidth = guiWidth;
            this.guiHeight = guiHeight;
            this.nativeWidth = nativeWidth;
            this.nativeHeight = nativeHeight;
        }

        private void applyInput(InputSession session) {
            mouseEventCount = session.mouseEventCount;
            keyEventCount = session.keyEventCount;
            textEventCount = session.textEventCount;
            inputRoutingTimeNanos = session.routingTimeNanos;
            hitTestVisitCount = session.hitTestVisitCount;
        }

        private void recordPhase(String phaseName, long nanos) {
            PhaseSample sample = phaseSamples.get(phaseName);
            if (sample == null) {
                sample = new PhaseSample();
                phaseSamples.put(phaseName, sample);
            }
            sample.totalNanos += Math.max(0L, nanos);
            sample.count++;
        }

    }

    /**
     * 一次输入路由阶段的临时统计状态。
     */
    private static final class InputSession {

        private final String screenName;
        private final int mouseEventCount;
        private final int keyEventCount;
        private final int textEventCount;
        private final long startNanos = System.nanoTime();

        private long routingTimeNanos;
        private long hitTestVisitCount;

        private InputSession(String screenName, int mouseEventCount, int keyEventCount, int textEventCount) {
            this.screenName = screenName;
            this.mouseEventCount = mouseEventCount;
            this.keyEventCount = keyEventCount;
            this.textEventCount = textEventCount;
        }
    }

    /**
     * 当前帧单个阶段的累计数据。
     */
    private static final class PhaseSample {

        private long totalNanos;
        private int count;
    }
}
