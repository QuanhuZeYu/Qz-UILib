package club.heiqi.uilib.ui.diagnostic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.env.DiagnosticsEnvironment;
import club.heiqi.uilib.ui.input.UiInputFrame;

/**
 * UI 框架性能采样器。
 *
 * <h3>诊断环境从哪来（本类不持有开关）</h3>
 * <p>采样开关的唯一来源是 {@link #beginFrame} 的 {@link DiagnosticsEnvironment} 参数 —— <b>帧是采样的
 * 管辖单位</b>。会话持的是<b>域引用而非开关快照</b>，故 {@link DiagnosticsEnvironment#debugEnabled()}
 * 的每次读都取当前值：帧中途改开关即时生效，与旧的静态直读语义逐位一致。本类不读任何配置字段、
 * 也不缓存诊断域，故全仓只有一条投影路径（{@code ProcessUiEnvironment}），不存在第二个开关源。</p>
 *
 * <h3>成本（诊断关闭时零累计、零保留）</h3>
 * <p>关闭时 {@link #beginFrame} 不创建 {@link FrameSession}，帧内 {@link #recordPhase} /
 * {@link #recordCounter} 命中会话后按域判定即返回，不累计、不分配。帧外（无会话，即宿主渲染帧
 * 之外的面板构建、候选枚举）的样本落入有界待折叠桶，其取舍由「将要折叠进的那一帧」的诊断域决定：
 * 关闭的帧在 {@code beginFrame} 处直接作废该桶，故关闭态不会留下待折叠样本。关闭态的唯一残余成本
 * 是一次线程本地读；调用点若连这次读也要省掉，可自行按环境端口判定后再调用（仓库内控件埋点即如此），
 * 但那<b>不是正确性所必需</b> —— 漏判只会多一次入桶，不会污染统计。</p>
 *
 * <h3>帧入口与重入</h3>
 * <p>UILib 有两条互不嵌套、但可能在同一渲染帧内先后发生的宿主帧入口（屏幕/控件宿主与 HUD），
 * 且聊天输入面等控件宿主可能被 HUD 路径间接驱动。本类用<b>线程内重入深度</b>保证：
 * 最外层 {@code beginFrame} 拥有本帧会话，嵌套调用只递增深度、不新建会话；对应最外层
 * {@code finishFrame} 才结算。嵌套层传入的诊断域被忽略（本帧的诊断事实归最外层），
 * 故「帧中途关闭开关」不会让嵌套调用拆掉本帧会话。调用方仍需以 {@code try/finally} 保证配对
 * （见 {@code AbstractSceneHostWidget#render} 与 {@code UiHudRenderListener#renderHudFrame}）。</p>
 *
 * <h3>按界面分组的帧历史（有界）</h3>
 * <p>平均帧时间 / 最大帧时间 / 慢帧计数按 {@code screenName} 分组各自维护 120 帧滚动窗口。
 * 两条宿主路径交替调用时互不覆盖、也互不清空（历史清空只发生在显式
 * {@link #resetHistory(String)}）。分组数量有上限 {@value #MAX_SCREEN_HISTORIES} 个，
 * 超出按访问序淘汰最久未使用者，保证缓存有界。</p>
 *
 * <h3>帧外样本折叠</h3>
 * <p>选择器候选枚举、面板构建等操作发生在宿主渲染帧之外（此时无线程会话）。这类样本
 * （阶段耗时与计数）先落入有界的待折叠桶，在下一次 {@link #beginFrame} 时并入该帧快照，
 * 避免"最有价值的构建期数据恰好被丢弃"。帧外样本无自己的时刻归属，其采样与否由<b>折叠时的诊断域</b>
 * 判定：折叠进开启的帧则采纳，落进关闭的帧则与桶一同作废。折叠桶容量上限 {@value #MAX_PENDING_KEYS}
 * 个键，超限丢弃新键（不静默增长）。帧时间历史仍严格按 {@code screenName} 分组，不受此折叠影响。</p>
 */
public class UiPerformanceMonitor {

    private static final UiPerformanceMonitor INSTANCE = new UiPerformanceMonitor();
    private static final int HISTORY_SIZE = 120;

    /** 按界面分组的帧历史数量上限（超出按访问序淘汰最久未使用者，保证有界）。 */
    public static final int MAX_SCREEN_HISTORIES = 8;

    /** 单帧阶段名数量上限：即使调用方传入动态阶段名，帧内表也不会无界增长。 */
    private static final int MAX_PHASE_KEYS = 32;

    /** 单帧计数器数量上限：同上（计数器名应为 {@link UiPerfMarkers} 中的常量）。 */
    private static final int MAX_COUNTER_KEYS = 32;

    /** 帧外待折叠桶的键数量上限（阶段名 + 计数器名合计）。 */
    private static final int MAX_PENDING_KEYS = 64;

    private static final long SLOW_FRAME_THRESHOLD_NANOS = 16_666_667L;

    private final ThreadLocal<FrameSession> activeFrameSession = new ThreadLocal<FrameSession>();
    private final ThreadLocal<InputSession> activeInputSession = new ThreadLocal<InputSession>();

    /**
     * 线程内帧重入深度。深度 0 = 本线程无活跃帧；&gt;0 = 已有帧会话（嵌套调用只递增）。
     * 用可变槽避免每帧 {@code Integer} 装箱。
     */
    private final ThreadLocal<FrameScope> frameScope = new ThreadLocal<FrameScope>() {
        @Override
        protected FrameScope initialValue() {
            return new FrameScope();
        }
    };

    /**
     * 按界面名分组的帧历史（访问序 LinkedHashMap 即 LRU 顺序）。
     * 键数量受 {@link #MAX_SCREEN_HISTORIES} 约束。
     */
    private final LinkedHashMap<String, ScreenHistory> histories = new LinkedHashMap<String, ScreenHistory>(8, 0.75F, true);

    /** 帧外样本待折叠桶（有界，见 {@link #MAX_PENDING_KEYS}）。 */
    private PendingSamples pendingSamples;

    private UiRuntimeStats latestStats = UiRuntimeStats.empty();
    private InputSession pendingInputSession;
    private long lastDebugLogAt;

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
     * <p>无活跃帧会话时样本落入待折叠桶，随下一次 {@link #beginFrame} 并入该帧（覆盖宿主帧之外
     * 发生的候选枚举、面板构建等一次性成本）；该桶采纳与否由折叠时的诊断域决定。</p>
     *
     * @param phaseName 阶段名（应取 {@link UiPerfMarkers} 常量，禁止动态拼接）
     * @param nanos 耗时
     */
    public void recordPhase(String phaseName, long nanos) {
        if (nanos <= 0L || phaseName == null || phaseName.isEmpty()) {
            return;
        }
        FrameSession session = activeFrameSession.get();
        if (session == null) {
            accumulatePendingPhase(phaseName, nanos);
            return;
        }
        if (!session.diagnostics().debugEnabled()) {
            return;
        }
        session.recordPhase(phaseName, nanos);
    }

    /**
     * 累计当前帧的计数量。
     *
     * @param counterName 计数名（常量，见 {@link UiPerfMarkers}）
     * @param delta 增量
     */
    public void recordCounter(String counterName, long delta) {
        if (delta == 0L || counterName == null || counterName.isEmpty()) {
            return;
        }
        FrameSession session = activeFrameSession.get();
        if (session == null) {
            accumulatePendingCounter(counterName, delta);
            return;
        }
        if (!session.diagnostics().debugEnabled()) {
            return;
        }
        session.recordCounter(counterName, delta);
    }

    /**
     * 开始记录当前 UI 帧。
     *
     * <p><b>本方法是采样开关的唯一入口</b>：宿主在此显式表态本帧的诊断环境（缺席请传
     * {@link DiagnosticsEnvironment#EMPTY}，与「未安装」逐位等价）。会话只持有<b>域引用</b>而不缓存
     * 开关值，故帧中途改开关即时生效（关闭后帧内采样立刻停止，但已开始的本帧仍照常结算）。</p>
     *
     * <p>诊断关闭时立即返回：既不创建会话也不分配，并作废帧外待折叠样本 —— 关闭态不得留下待折叠
     * 样本，否则关闭期的记录会被后续开启的帧采纳。同一线程已有活跃帧时只递增重入深度（最外层拥有
     * 本帧，嵌套层传入的诊断域被忽略）；重复调用而不配对 {@code finishFrame} 时同样只递增深度，
     * 不会产生第二个会话。</p>
     *
     * @param screenName 界面名（null 视为空串）
     * @param guiWidth GUI 逻辑宽度
     * @param guiHeight GUI 逻辑高度
     * @param nativeWidth 原生渲染宽度
     * @param nativeHeight 原生渲染高度
     * @param diagnostics 本帧的诊断环境（不可为 null）
     */
    public void beginFrame(String screenName, int guiWidth, int guiHeight, int nativeWidth, int nativeHeight,
            DiagnosticsEnvironment diagnostics) {
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics 不可为 null；缺席请传 DiagnosticsEnvironment.EMPTY");
        }
        FrameScope scope = frameScope.get();
        if (scope.depth > 0) {
            // 嵌套帧沿用最外层会话（含其诊断域）：内层传什么域都不改变本帧管辖，也不会拆掉会话。
            scope.depth++;
            return;
        }
        if (!diagnostics.debugEnabled()) {
            synchronized (this) {
                pendingSamples = null;
            }
            return;
        }
        String resolvedScreenName = screenName == null ? "" : screenName;
        FrameSession session = new FrameSession(resolvedScreenName, guiWidth, guiHeight, nativeWidth, nativeHeight,
                diagnostics);
        synchronized (this) {
            historyFor(resolvedScreenName);
            if (pendingInputSession != null) {
                if (resolvedScreenName.equals(pendingInputSession.screenName)) {
                    session.applyInput(pendingInputSession);
                }
                pendingInputSession = null;
            }
            if (pendingSamples != null) {
                // 帧外样本（候选枚举、面板构建）发生在宿主帧之外，记录时无从得知界面名；
                // 统一折叠进下一个开始的帧快照，避免最有价值的构建期数据被丢弃。
                session.foldPending(pendingSamples);
                pendingSamples = null;
            }
        }
        activeFrameSession.set(session);
        scope.depth = 1;
    }

    /**
     * 主动清空指定界面的采样历史，供切页或重新进入诊断页后使用。
     *
     * @param screenName 当前界面名
     */
    public synchronized void resetHistory(String screenName) {
        histories.remove(screenName == null ? "" : screenName);
        latestStats = UiRuntimeStats.empty();
        pendingInputSession = null;
        pendingSamples = null;
    }

    /**
     * 记录当前帧渲染阶段耗时。
     *
     * @param nanos 渲染耗时
     */
    public void recordRenderPhase(long nanos) {
        FrameSession session = activeFrameSession.get();
        if (session == null || !session.diagnostics().debugEnabled()) {
            return;
        }
        session.renderTimeNanos = Math.max(0L, nanos);
    }

    /**
     * 记录当前帧贴屏阶段耗时。
     *
     * @param nanos 贴屏耗时
     */
    public void recordPresentPhase(long nanos) {
        FrameSession session = activeFrameSession.get();
        if (session == null || !session.diagnostics().debugEnabled()) {
            return;
        }
        session.presentTimeNanos = Math.max(0L, nanos);
    }

    /**
     * 开始记录输入路由阶段。
     *
     * <p>与 {@link #beginFrame} 同一口径：输入路由有自己的诊断环境入口，诊断关闭时不建会话
     * （后续 {@link #recordHitTestVisit} 因此不累计）。</p>
     *
     * @param screenName 界面名
     * @param frame 输入快照
     * @param diagnostics 本段输入路由的诊断环境（不可为 null）
     */
    public void beginInputRouting(String screenName, UiInputFrame frame, DiagnosticsEnvironment diagnostics) {
        if (frame == null || diagnostics == null || !diagnostics.debugEnabled()) {
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
        // 命中测试是单帧内调用最密的采样点，故不设前置开关读：两段会话各自持有诊断域，
        // 关闭态下两次查询都落空，代价是两次线程本地读（今日为一次静态读，差异在纳秒级）。
        InputSession inputSession = activeInputSession.get();
        if (inputSession != null) {
            inputSession.hitTestVisitCount++;
            return;
        }

        FrameSession frameSession = activeFrameSession.get();
        if (frameSession != null && frameSession.diagnostics().debugEnabled()) {
            frameSession.hitTestVisitCount++;
        }
    }

    /**
     * 完成当前 UI 帧统计。
     *
     * <p>重入深度 &gt;1 时只递减深度（嵌套帧不结算）。无活跃会话时安全返回
     * （"只 finish 不 begin"不产生任何副作用）。调用方必须置于 {@code finally}，
     * 使渲染异常路径同样完成结算，不把会话泄漏在 ThreadLocal。</p>
     */
    public void finishFrame() {
        FrameScope scope = frameScope.get();
        if (scope.depth > 1) {
            scope.depth--;
            return;
        }
        if (scope.depth == 1) {
            scope.depth = 0;
        }
        FrameSession session = activeFrameSession.get();
        if (session == null) {
            return;
        }
        activeFrameSession.remove();
        session.frameTimeNanos = System.nanoTime() - session.frameStartNanos;

        UiRuntimeStats stats;
        synchronized (this) {
            appendHistory(frameHistoryFor(session.screenName), session.frameTimeNanos);
            appendHistory(renderHistoryFor(session.screenName), session.renderTimeNanos);
            stats = buildStats(session);
            latestStats = stats;
        }
        debugLogStats(stats, session.diagnostics());
    }

    private synchronized UiRuntimeStats buildStats(FrameSession session) {
        Deque<Long> frames = frameHistoryFor(session.screenName);
        Deque<Long> renders = renderHistoryFor(session.screenName);
        long averageFrameTime = average(frames);
        long maxFrameTime = max(frames);
        long averageRenderTime = average(renders);
        double averageFps = averageFrameTime <= 0L ? 0.0D : 1_000_000_000.0D / averageFrameTime;
        int slowFrameCount = countSlowFrames(frames);
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
                frames.size(),
                buildCounterSummary(session));
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
                    .append(String.format(Locale.ROOT, "%.2fms x%d",
                            Double.valueOf(entry.getValue().totalNanos / 1_000_000.0D),
                            Integer.valueOf(entry.getValue().count)));
        }
        return builder.toString();
    }

    /**
     * 计数摘要：按名排序输出 {@code name=total/max×samples}，便于脚本按名解析。
     */
    private String buildCounterSummary(FrameSession session) {
        if (session.counterSamples.isEmpty()) {
            return "";
        }
        List<Map.Entry<String, CounterSample>> entries =
                new ArrayList<Map.Entry<String, CounterSample>>(session.counterSamples.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, CounterSample>>() {
            @Override
            public int compare(Map.Entry<String, CounterSample> first, Map.Entry<String, CounterSample> second) {
                return first.getKey().compareTo(second.getKey());
            }
        });
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                builder.append('；');
            }
            Map.Entry<String, CounterSample> entry = entries.get(index);
            CounterSample sample = entry.getValue();
            builder.append(entry.getKey())
                    .append('=')
                    .append(sample.total)
                    .append('/')
                    .append(sample.max)
                    .append("x")
                    .append(sample.samples);
        }
        return builder.toString();
    }

    /**
     * 采样日志：开关与被记录的那一帧同源（读本帧诊断域），不另设第二处开关判定。
     */
    private void debugLogStats(UiRuntimeStats stats, DiagnosticsEnvironment diagnostics) {
        if (!diagnostics.debugEnabled() || stats.getSampledFrameCount() <= 0) {
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

    /** 取（必要时创建）指定界面的历史分组，并按 LRU 淘汰超额分组。 */
    private ScreenHistory historyFor(String screenName) {
        ScreenHistory history = histories.get(screenName);
        if (history == null) {
            history = new ScreenHistory();
            histories.put(screenName, history);
            evictExcessHistories();
        }
        return history;
    }

    private Deque<Long> frameHistoryFor(String screenName) {
        return historyFor(screenName).frameTimes;
    }

    private Deque<Long> renderHistoryFor(String screenName) {
        return historyFor(screenName).renderTimes;
    }

    private void evictExcessHistories() {
        if (histories.size() <= MAX_SCREEN_HISTORIES) {
            return;
        }
        Iterator<Map.Entry<String, ScreenHistory>> iterator = histories.entrySet().iterator();
        while (histories.size() > MAX_SCREEN_HISTORIES && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
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

    private synchronized void accumulatePendingPhase(String phaseName, long nanos) {
        if (pendingSamples == null) {
            pendingSamples = new PendingSamples();
        }
        pendingSamples.recordPhase(phaseName, nanos);
    }

    private synchronized void accumulatePendingCounter(String counterName, long delta) {
        if (pendingSamples == null) {
            pendingSamples = new PendingSamples();
        }
        pendingSamples.recordCounter(counterName, delta);
    }

    /**
     * 线程内帧重入深度槽（可变，避免每帧装箱）。
     */
    private static final class FrameScope {

        private int depth;
    }

    /**
     * 单个界面的 120 帧滚动窗口历史。
     */
    private static final class ScreenHistory {

        private final Deque<Long> frameTimes = new ArrayDeque<Long>();
        private final Deque<Long> renderTimes = new ArrayDeque<Long>();
    }

    /**
     * 帧外样本待折叠桶（有界；键数量达上限后丢弃新键）。
     */
    private static final class PendingSamples {

        private final Map<String, PhaseSample> phases = new LinkedHashMap<String, PhaseSample>();
        private final Map<String, CounterSample> counters = new LinkedHashMap<String, CounterSample>();

        private void recordPhase(String phaseName, long nanos) {
            PhaseSample sample = phases.get(phaseName);
            if (sample == null) {
                if (phases.size() >= MAX_PENDING_KEYS) {
                    return;
                }
                sample = new PhaseSample();
                phases.put(phaseName, sample);
            }
            sample.totalNanos += Math.max(0L, nanos);
            sample.count++;
        }

        private void recordCounter(String counterName, long delta) {
            CounterSample sample = counters.get(counterName);
            if (sample == null) {
                if (counters.size() >= MAX_PENDING_KEYS) {
                    return;
                }
                sample = new CounterSample();
                counters.put(counterName, sample);
            }
            sample.add(delta);
        }
    }

    /**
     * 一帧渲染中的临时统计状态（按帧创建，内部表有界）。
     */
    private final class FrameSession {

        private final String screenName;
        private final int guiWidth;
        private final int guiHeight;
        private final int nativeWidth;
        private final int nativeHeight;
        /** 本帧的诊断域（域引用而非开关快照：每次读都取当前值）。 */
        private final DiagnosticsEnvironment diagnostics;
        private final long frameStartNanos = System.nanoTime();
        private final Map<String, PhaseSample> phaseSamples = new LinkedHashMap<String, PhaseSample>();
        private final Map<String, CounterSample> counterSamples = new LinkedHashMap<String, CounterSample>();

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

        private FrameSession(String screenName, int guiWidth, int guiHeight, int nativeWidth, int nativeHeight,
                DiagnosticsEnvironment diagnostics) {
            this.screenName = screenName;
            this.guiWidth = guiWidth;
            this.guiHeight = guiHeight;
            this.nativeWidth = nativeWidth;
            this.nativeHeight = nativeHeight;
            this.diagnostics = diagnostics;
        }

        private DiagnosticsEnvironment diagnostics() {
            return diagnostics;
        }

        private void applyInput(InputSession session) {
            mouseEventCount = session.mouseEventCount;
            keyEventCount = session.keyEventCount;
            textEventCount = session.textEventCount;
            inputRoutingTimeNanos = session.routingTimeNanos;
            hitTestVisitCount = session.hitTestVisitCount;
        }

        /** 并入帧外样本（阶段与计数各自有界）。 */
        private void foldPending(PendingSamples pending) {
            for (Map.Entry<String, PhaseSample> entry : pending.phases.entrySet()) {
                PhaseSample sample = phaseSamples.get(entry.getKey());
                if (sample == null) {
                    if (phaseSamples.size() >= MAX_PHASE_KEYS) {
                        continue;
                    }
                    sample = new PhaseSample();
                    phaseSamples.put(entry.getKey(), sample);
                }
                sample.totalNanos += entry.getValue().totalNanos;
                sample.count += entry.getValue().count;
            }
            for (Map.Entry<String, CounterSample> entry : pending.counters.entrySet()) {
                CounterSample sample = counterSamples.get(entry.getKey());
                if (sample == null) {
                    if (counterSamples.size() >= MAX_COUNTER_KEYS) {
                        continue;
                    }
                    sample = new CounterSample();
                    counterSamples.put(entry.getKey(), sample);
                }
                sample.merge(entry.getValue());
            }
        }

        private void recordPhase(String phaseName, long nanos) {
            PhaseSample sample = phaseSamples.get(phaseName);
            if (sample == null) {
                if (phaseSamples.size() >= MAX_PHASE_KEYS) {
                    return;
                }
                sample = new PhaseSample();
                phaseSamples.put(phaseName, sample);
            }
            sample.totalNanos += Math.max(0L, nanos);
            sample.count++;
        }

        private void recordCounter(String counterName, long delta) {
            CounterSample sample = counterSamples.get(counterName);
            if (sample == null) {
                if (counterSamples.size() >= MAX_COUNTER_KEYS) {
                    return;
                }
                sample = new CounterSample();
                counterSamples.put(counterName, sample);
            }
            sample.add(delta);
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

    /**
     * 当前帧单个计数器的累计数据：total = 累加值，max = 单次峰值，samples = 记录次数。
     */
    private static final class CounterSample {

        private long total;
        private long max;
        private int samples;

        private void add(long delta) {
            total += delta;
            if (delta > max) {
                max = delta;
            }
            samples++;
        }

        private void merge(CounterSample other) {
            total += other.total;
            if (other.max > max) {
                max = other.max;
            }
            samples += other.samples;
        }
    }
}
