package club.heiqi.uilib.internal.chat3.viewmodel;

/**
 * 聊天 3.0 HUD 可见时钟(L2 视图模型):只累计 HUD 形态帧推进的可见时间。
 *
 * <p>与 wall-clock 解耦:仅当 HUD 形态(isHudPhase)的帧推进时,帧间 delta
 * (夹取上限 1s)才累计进可见时钟;不可见帧只更新 lastWall 不累计。
 * 由此:聊天框打开(切离 HUD 形态)期间消息预算冻结;渲染停滞/切出无帧不累计;
 * 恢复可见时以最近壁钟定锚,不跳变。</p>
 *
 * <p>主线程使用。时间口径 = System.currentTimeMillis(与 ChatLineRecord 一致)。</p>
 */
public final class HudVisibleClock {

    /** 单帧 delta 累计上限(防切出后恢复的单帧大跳变;单位:ms)。 */
    private static final long MAX_FRAME_DELTA_MILLIS = 1000L;

    /** 上次 tickFrame 的 wall-clock;-1 = 未定锚(首帧只定锚不累计)。 */
    private long lastWallMillis = -1L;

    /** 累计可见毫秒(仅 HUD 形态帧推进)。 */
    private long visibleMillis = 0L;

    /**
     * 帧推进。
     *
     * @param nowWallMillis 当前 wall-clock(ms)
     * @param visible       是否 HUD 形态帧(可见):false 只更新壁钟不累计
     * @return 累计可见毫秒
     */
    public long tickFrame(long nowWallMillis, boolean visible) {
        if (!visible) {
            lastWallMillis = nowWallMillis;
            return visibleMillis;
        }
        if (lastWallMillis < 0L) {
            lastWallMillis = nowWallMillis;
            return visibleMillis;
        }
        long delta = nowWallMillis - lastWallMillis;
        if (delta < 0L) {
            delta = 0L;
        } else if (delta > MAX_FRAME_DELTA_MILLIS) {
            delta = MAX_FRAME_DELTA_MILLIS;
        }
        lastWallMillis = nowWallMillis;
        visibleMillis += delta;
        return visibleMillis;
    }

    /** @return 累计可见毫秒 */
    public long visibleMillis() {
        return visibleMillis;
    }

    /** 复位:累计清零并撤销定锚(复位后下一帧可见只定锚不累计)。 */
    public void reset() {
        lastWallMillis = -1L;
        visibleMillis = 0L;
    }
}
