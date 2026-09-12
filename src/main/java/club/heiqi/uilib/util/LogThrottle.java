package club.heiqi.uilib.util;

/**
 * 诊断日志限流器 —— 真机「平台文本输入通道」一次性诊断的通用频率闸门。
 *
 * <h3>语义（每个实例 = 一条独立日志通道）</h3>
 * <ol>
 *   <li>每个时间窗内<b>前 {@code burstPerWindow} 次</b>调用放行，其余一律抑制；</li>
 *   <li>窗口按调用方传入的单调纳秒滑动：{@code now - windowStart >= windowMillis} 即开新窗口
 *       （非固定对齐窗口，避免"刚过边界就连打两轮"）；</li>
 *   <li>每次放行时把「自上次放行以来被抑制的次数」快照给调用方（{@link #suppressedSinceLastLog()}），
 *       调用点应把它与 {@link #total()} 一起写进日志 —— 这样每条日志自身说明有多少事件被折叠，
 *       限流不会制造信息黑洞。</li>
 * </ol>
 *
 * <p>由此得到与事件速率无关的稳态上限：<b>{@code burstPerWindow} 条 /
 * {@code windowMillis} 毫秒</b>。调用点用它在"正常路径不刷屏"和"故障现场留证据"之间取平衡。</p>
 *
 * <h3>边界与使用约束</h3>
 * <ul>
 *   <li><b>纯观测</b>：{@link #allow(long)} 无任何副作用，绝不影响被诊断的输入/渲染行为；</li>
 *   <li><b>时间基准由调用方传入</b>（{@code System.nanoTime()} 或事件自带时间戳），
 *       便于纯 JVM 测试注入假时钟；时间戳不单调或恒为 0 时只会更保守（少打日志）；</li>
 *   <li><b>非线程安全</b>：使用点全在客户端主线程（MC 输入回调 / 场景帧管线），
 *       与那些调用点本身的主线程约束一致，故不做同步；</li>
 *   <li>抑制只对"重复发生"生效：窗口内前几次仍然可见，故障现场不会因为限流而完全静默。</li>
 * </ul>
 */
public final class LogThrottle {

    /** 每窗口放行条数（至少 1）。 */
    private final int burstPerWindow;
    /** 窗口长度（毫秒，至少 1）。 */
    private final long windowNanos;

    /** 是否已开启过第一个窗口（避免用哨兵值做减法溢出）。 */
    private boolean windowOpen;
    /** 当前窗口起点（调用方时间基准）。 */
    private long windowStartNanos;
    /** 当前窗口已放行条数。 */
    private int allowedInWindow;

    /** 累计调用次数。 */
    private long total;
    /** 自上次放行以来被抑制的次数（累计值）。 */
    private long suppressedSinceLastLog;
    /** 最近一次放行时上报的抑制次数快照。 */
    private long lastSuppressed;

    /**
     * 创建一条限流通道。
     *
     * @param burstPerWindow 每窗口放行条数，必须 ≥ 1
     * @param windowMillis   窗口长度（毫秒），必须 ≥ 1
     */
    public LogThrottle(int burstPerWindow, long windowMillis) {
        if (burstPerWindow < 1) {
            throw new IllegalArgumentException("burstPerWindow 必须 ≥ 1: " + burstPerWindow);
        }
        if (windowMillis < 1L) {
            throw new IllegalArgumentException("windowMillis 必须 ≥ 1: " + windowMillis);
        }
        this.burstPerWindow = burstPerWindow;
        this.windowNanos = windowMillis * 1_000_000L;
    }

    /**
     * 判定本次日志是否放行（同时记账）。
     *
     * @param nowNanos 当前时间（单调纳秒，通常 {@code System.nanoTime()} 或事件时间戳）
     * @return true = 本条日志应输出；false = 被限流抑制
     */
    public boolean allow(long nowNanos) {
        total++;
        if (!windowOpen || nowNanos - windowStartNanos >= windowNanos) {
            windowOpen = true;
            windowStartNanos = nowNanos;
            allowedInWindow = 0;
        }
        if (allowedInWindow < burstPerWindow) {
            allowedInWindow++;
            lastSuppressed = suppressedSinceLastLog;
            suppressedSinceLastLog = 0L;
            return true;
        }
        suppressedSinceLastLog++;
        return false;
    }

    /** @return 累计调用次数（含被抑制的），供日志写"累计 N 次" */
    public long total() {
        return total;
    }

    /** @return 自上次放行以来被抑制的次数（随最近一次放行快照），供日志写"已折叠 M 次" */
    public long suppressedSinceLastLog() {
        return lastSuppressed;
    }
}
