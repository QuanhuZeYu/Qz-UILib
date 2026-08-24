package club.heiqi.uilib.internal.chat3.view;

/**
 * 聊天 3.0 双形态状态机(纯 JVM,wall-clock):
 *
 * <pre>
 * 聊天关闭(目标 off)        聊天打开(目标 on)
 *      HUD  ──────────────→ COLLAPSING(收起 HUD 气泡)
 *       ↑                       ↓ 收起动画结束
 *       │                    POPPING(容器弹出)
 *   CLOSING ←────────────────── ↓ 弹出动画结束
 *       ↑                     CONTAINER(稳定)
 *   (关闭动画 = 容器收回)
 * </pre>
 *
 * <p>可重入:目标变化以最新为准(快速开关不叠动画);同目标重复调用幂等。</p>
 */
public final class DisplayStateMachine {

    /** 形态阶段。 */
    public enum Phase {
        /** 聊天关闭稳定态(HUD 堆叠气泡)。 */
        HUD,
        /** 打开中:先收起 HUD 气泡。 */
        COLLAPSING,
        /** 打开中:容器弹出。 */
        POPPING,
        /** 聊天打开稳定态(容器)。 */
        CONTAINER,
        /** 关闭中:容器收回。 */
        CLOSING
    }

    private Phase phase = Phase.HUD;
    private long phaseStartMillis = 0L;
    private boolean targetOpen = false;

    /**
     * 设置目标状态(聊天是否打开);目标变化时进入对应动画阶段并重置时钟。
     *
     * @param open       目标:聊天打开
     * @param nowMillis  当前时刻
     */
    public synchronized void setTarget(boolean open, long nowMillis) {
        boolean openStable = open && phase == Phase.CONTAINER;
        boolean closedStable = !open && phase == Phase.HUD;
        if (open == targetOpen && (openStable || closedStable)) {
            return; // 已稳定,幂等
        }
        targetOpen = open;
        phase = open ? Phase.COLLAPSING : Phase.CLOSING;
        phaseStartMillis = nowMillis;
    }

    /**
     * 推进阶段(幂等;动画时长耗尽自动进入下一阶段)。
     *
     * @param nowMillis     当前时刻
     * @param collapseMillis 收起动画时长
     * @param popMillis      弹出动画时长
     * @param closingMillis  关闭动画时长(设计稿 §4.1:closing 140,独立于 pop)
     * @return 当前阶段
     */
    public synchronized Phase tick(long nowMillis, long collapseMillis, long popMillis,
                                   long closingMillis) {
        switch (phase) {
            case COLLAPSING:
                if (nowMillis - phaseStartMillis >= collapseMillis) {
                    phase = Phase.POPPING;
                    phaseStartMillis = nowMillis;
                }
                break;
            case POPPING:
                if (nowMillis - phaseStartMillis >= popMillis) {
                    phase = Phase.CONTAINER;
                }
                break;
            case CLOSING:
                if (nowMillis - phaseStartMillis >= closingMillis) {
                    phase = Phase.HUD;
                }
                break;
            default:
                break;
        }
        return phase;
    }

    /**
     * @param nowMillis      当前时刻
     * @param durationMillis 当前阶段动画时长
     * @return 当前阶段进度 [0,1]
     */
    public synchronized float progress(long nowMillis, long durationMillis) {
        if (phase == Phase.HUD) {
            return 0.0F;
        }
        if (phase == Phase.CONTAINER) {
            return 1.0F;
        }
        if (durationMillis <= 0) {
            return 1.0F;
        }
        long elapsed = nowMillis - phaseStartMillis;
        if (elapsed <= 0) {
            return 0.0F;
        }
        if (elapsed >= durationMillis) {
            return 1.0F;
        }
        return (float) elapsed / (float) durationMillis;
    }

    /** @return 当前阶段 */
    public synchronized Phase getPhase() {
        return phase;
    }
}
