package club.heiqi.uilib.ui.diagnostic;

import java.util.Arrays;

/**
 * 轻量帧率探针，只负责采样帧间隔并计算 fps/帧耗时统计。
 *
 * <p>与 {@link UiPerformanceMonitor} 正交：UiPerformanceMonitor 走 Widget 树深度采集
 * （enterWidget/exitWidget、阶段耗时、输入路由统计），依赖 {@code Widget.render} 驱动；
 * 本探针服务 scene demo 等不走 {@code Widget.render} 的渲染壳层（如 McScreenBridge 壳），
 * 只在壳层 render 入口每帧调用 {@link #tick()} 即可获得窗口化帧率统计。</p>
 *
 * <p><b>线程不安全</b>：设计为单线程 render 调用，未做任何同步，禁止跨线程共享。</p>
 */
public class FrameRateProbe {

    /** 帧间隔环形缓冲容量（120 帧滚动窗口，与 UiPerformanceMonitor.HISTORY_SIZE 对齐）。 */
    private static final int HISTORY_SIZE = 120;
    /** 慢帧阈值（纳秒），对应 60fps 单帧预算 16.67ms。 */
    private static final long SLOW_THRESHOLD_NANOS = 16_666_667L;

    /** 上一帧 nanoTime 时间戳，0 表示尚未采到首帧。 */
    private long lastFrameNanos;
    /** 帧间隔环形缓冲（纳秒），容量 HISTORY_SIZE，按写入顺序循环覆盖。 */
    private final long[] frameTimeHistory;
    /** 环形缓冲下一个写入下标。 */
    private int historyIndex;
    /** 环形缓冲已填充样本数（未满前小于 HISTORY_SIZE，满后恒等于 HISTORY_SIZE）。 */
    private int historyCount;
    /** 当前窗口内慢帧累计数（滑出窗口时同步扣减，保持窗口语义）。 */
    private int slowFrameCountTotal;
    /** 自上次 {@link #reset()} 起累计采样帧数（不随窗口滑动回退）。 */
    private int sampledFrameCountTotal;

    /** 构造一个帧率探针，环形缓冲在构造期一次性分配。 */
    public FrameRateProbe() {
        this.frameTimeHistory = new long[HISTORY_SIZE];
    }

    /**
     * 每帧调用一次，采样当前帧间隔并写入环形缓冲。
     *
     * <p>首帧（lastFrameNanos==0）frameNanos 记为 0：不计慢帧（0 不大于阈值），
     * 也不计入窗口平均（{@link #getAverageFps()} 等访问器计算时跳过 ≤0 值）。</p>
     */
    public void tick() {
        long now = System.nanoTime();
        long frameNanos = lastFrameNanos == 0L ? 0L : now - lastFrameNanos;
        lastFrameNanos = now;

        // 入环形缓冲：未满时直接写入并递增 historyCount；满后覆盖最旧样本，
        // 覆盖前若旧值为慢帧则从 slowFrameCountTotal 扣减，保证窗口内慢帧计数语义正确。
        if (historyCount < frameTimeHistory.length) {
            frameTimeHistory[historyIndex] = frameNanos;
            historyCount++;
        } else {
            long old = frameTimeHistory[historyIndex];
            if (old > SLOW_THRESHOLD_NANOS) {
                slowFrameCountTotal--;
            }
            frameTimeHistory[historyIndex] = frameNanos;
        }
        // 当前帧若为慢帧（首帧 frameNanos=0 不算慢帧），窗口慢帧计数 +1。
        if (frameNanos > SLOW_THRESHOLD_NANOS) {
            slowFrameCountTotal++;
        }
        historyIndex = (historyIndex + 1) % frameTimeHistory.length;
        sampledFrameCountTotal++;
    }

    /**
     * 计算窗口内平均 fps。
     *
     * @return 平均帧耗时倒数换算的 fps；无有效样本时返回 0
     */
    public double getAverageFps() {
        long sumNanos = 0L;
        int validCount = 0;
        for (int i = 0; i < historyCount; i++) {
            long v = frameTimeHistory[i];
            if (v <= 0L) {
                continue;
            }
            sumNanos += v;
            validCount++;
        }
        if (validCount <= 0) {
            return 0.0;
        }
        double avgNanos = (double) sumNanos / validCount;
        return avgNanos > 0 ? 1_000_000_000.0 / avgNanos : 0.0;
    }

    /**
     * 返回最近一次采样的帧耗时（毫秒）。
     *
     * @return 最近一帧 frameNanos/1e6；首帧（frameNanos=0）返回 0
     */
    public double getCurrentFrameTimeMs() {
        if (historyCount <= 0) {
            return 0.0;
        }
        // 最近写入的槽位 = (historyIndex - 1 + HISTORY_SIZE) % HISTORY_SIZE
        int last = (historyIndex - 1 + frameTimeHistory.length) % frameTimeHistory.length;
        return frameTimeHistory[last] / 1_000_000.0;
    }

    /**
     * 返回窗口内平均帧耗时（毫秒）。
     *
     * @return 窗口平均 frameNanos/1e6；无有效样本时返回 0
     */
    public double getAverageFrameTimeMs() {
        long sumNanos = 0L;
        int validCount = 0;
        for (int i = 0; i < historyCount; i++) {
            long v = frameTimeHistory[i];
            if (v <= 0L) {
                continue;
            }
            sumNanos += v;
            validCount++;
        }
        if (validCount <= 0) {
            return 0.0;
        }
        return ((double) sumNanos / validCount) / 1_000_000.0;
    }

    /**
     * 返回窗口内最大帧耗时（毫秒）。
     *
     * @return 窗口最大 frameNanos/1e6；无有效样本时返回 0
     */
    public double getMaxFrameTimeMs() {
        long maxNanos = 0L;
        for (int i = 0; i < historyCount; i++) {
            long v = frameTimeHistory[i];
            if (v <= 0L) {
                continue;
            }
            if (v > maxNanos) {
                maxNanos = v;
            }
        }
        return maxNanos / 1_000_000.0;
    }

    /**
     * 返回当前窗口内慢帧累计数。
     *
     * @return 窗口慢帧数（滑出窗口时已同步扣减）
     */
    public int getSlowFrameCount() {
        return slowFrameCountTotal;
    }

    /**
     * 返回自上次 {@link #reset()} 起累计采样帧数。
     *
     * @return 总采样帧数（不随窗口滑动回退）
     */
    public int getSampledFrameCount() {
        return sampledFrameCountTotal;
    }

    /**
     * 清空环形缓冲并归零所有计数器，lastFrameNanos 也置 0（下一帧重新作为首帧）。
     */
    public void reset() {
        Arrays.fill(frameTimeHistory, 0L);
        historyIndex = 0;
        historyCount = 0;
        slowFrameCountTotal = 0;
        sampledFrameCountTotal = 0;
        lastFrameNanos = 0L;
    }
}
