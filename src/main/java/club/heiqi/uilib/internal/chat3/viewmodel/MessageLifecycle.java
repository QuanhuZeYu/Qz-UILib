package club.heiqi.uilib.internal.chat3.viewmodel;

/**
 * 聊天 3.0 消息生命周期(L2 视图模型,纯数据):每条消息的显示预算与 HUD 可见度状态。
 *
 * <p>预算语义(本批次改造的 HUD 显示时长机制):每条消息携带「需要显示的时间」预算,
 * 只在 HUD 真正可见(HUD 形态帧推进)时按可见时钟消耗;聊天框打开期间消息不进入
 * HUD 渲染,预算冻结,关闭聊天框后用尽剩余预算继续显示。由此修复原问题「聊天框打开
 * 期间预算照常消耗,关闭后看不到消息」。</p>
 *
 * <p>不可变:预算创建时固定;可变:可见起点与 done 标志。主线程使用(渲染/输入回调),
 * 无需加锁。{@code hudVisibleStartMillis = -1} 表示尚未进入 HUD 渲染。</p>
 */
public final class MessageLifecycle {

    /** 显示预算(创建时固定;单位:ms)。 */
    private final long budgetMillis;

    /** 首次进入 HUD 渲染时的可见时钟值;-1 = 未进入。 */
    private long hudVisibleStartMillis = -1L;

    /** 完成/脏标记:过期完成的组在 compose/裁剪/扫描路径 O(1) 跳过。 */
    private boolean done = false;

    /**
     * @param budgetMillis 显示预算(单位:ms;≥0,由调用方保证)
     */
    public MessageLifecycle(long budgetMillis) {
        this.budgetMillis = budgetMillis;
    }

    /** @return 显示预算(创建时固定,不可变) */
    public long getBudgetMillis() {
        return budgetMillis;
    }

    /** @return 是否已进入 HUD 渲染({@code hudVisibleStartMillis >= 0}) */
    public boolean isEntered() {
        return hudVisibleStartMillis >= 0L;
    }

    /**
     * 标记进入 HUD 渲染(幂等):首次调用记录可见起点,重复调用不重置
     * (重挂载/组增长重建不重置起点,预算不重计)。
     *
     * @param hudVisibleMillis 进入时刻的可见时钟值
     */
    public void markEntered(long hudVisibleMillis) {
        if (hudVisibleStartMillis < 0L) {
            hudVisibleStartMillis = hudVisibleMillis;
        }
    }

    /** @return 可见起点;-1 = 未进入 */
    public long getHudVisibleStartMillis() {
        return hudVisibleStartMillis;
    }

    /**
     * @param hudVisibleMillis 当前可见时钟值
     * @return 剩余显示时间:未进入 → 全额预算;已进入 → max(0, 预算 - 已显示时长)
     */
    public long remainingMillis(long hudVisibleMillis) {
        if (!isEntered()) {
            return budgetMillis;
        }
        long elapsed = hudVisibleMillis - hudVisibleStartMillis;
        return Math.max(0L, budgetMillis - elapsed);
    }

    /**
     * @param hudVisibleMillis 当前可见时钟值
     * @return 显示预算是否耗尽(剩余 ≤ 0)
     */
    public boolean isExpired(long hudVisibleMillis) {
        return remainingMillis(hudVisibleMillis) <= 0L;
    }

    /**
     * 淡出窗是否结束:预算耗尽且淡出窗(预算后的额外存活窗口,淡出动画期间)已走完;
     * {@code fadeMillis <= 0} 时预算耗尽即视为结束。未进入恒为 false。
     *
     * @param hudVisibleMillis 当前可见时钟值
     * @param fadeMillis       淡出窗时长(ms;≤ 0 表示无淡出窗)
     * @return true 当已进入且 已显示时长 ≥ 预算 + 淡出窗
     */
    public boolean isFadeElapsed(long hudVisibleMillis, long fadeMillis) {
        if (!isEntered()) {
            return false;
        }
        long elapsed = hudVisibleMillis - hudVisibleStartMillis;
        if (elapsed < budgetMillis) {
            return false;
        }
        return elapsed - budgetMillis >= fadeMillis;
    }

    /** 标记完成(脏标记;compose/裁剪/扫描路径 O(1) 跳过)。 */
    public void markDone() {
        done = true;
    }

    /** @return 是否已完成(脏标记) */
    public boolean isDone() {
        return done;
    }
}
