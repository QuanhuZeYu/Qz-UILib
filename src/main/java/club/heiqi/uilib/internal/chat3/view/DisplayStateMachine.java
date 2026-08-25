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
 * <p>可重入(设计稿 §4.2 重入规则,视觉侧定死):</p>
 * <ul>
 *   <li><b>COLLAPSING 中反悔(关)</b>:记录当前 progress p,从 p 反向播放(双向可逆,
 *       不硬切到 0 或 1),progress 归零后落 HUD;</li>
 *   <li><b>反向收起中再展开</b>:从当前 progress 正向续播(同样不硬切);</li>
 *   <li><b>CLOSING 不可被打断为 POPPING</b>:置 {@code pendingOpen},CLOSING 完成后
 *       自动进入 COLLAPSING(避免半透明残影叠层);</li>
 *   <li><b>POPPING 中关闭</b>:从半途折算反向起点——closing 从 progress 1−p 处开始
 *       (pop 完成度 p 越高,closing 越接近从头播放);</li>
 *   <li>POPPING 中新消息到达:消息入列表排队,动画不重置不跳变(本机不参与)。</li>
 * </ul>
 *
 * <p>非稳定态统一 anchor 模型:progress = anchorProgress + direction × (now − anchorMillis)
 * / duration(clamp01)。COLLAPSING 可反向(direction=−1),其余恒正向。</p>
 */
public final class DisplayStateMachine {

    /** 形态阶段。 */
    public enum Phase {
        /** 聊天关闭稳定态(HUD 堆叠气泡)。 */
        HUD,
        /** 打开中:先收起 HUD 气泡(可反向播放)。 */
        COLLAPSING,
        /** 打开中:容器弹出。 */
        POPPING,
        /** 聊天打开稳定态(容器)。 */
        CONTAINER,
        /** 关闭中:容器收回。 */
        CLOSING
    }

    private Phase phase = Phase.HUD;
    private boolean targetOpen = false;
    /** CLOSING 期间收到的打开请求(CLOSING 完成后自动进 COLLAPSING)。 */
    private boolean pendingOpen = false;
    /** 非稳定态 progress 锚点:progress = anchorProgress + direction × (now − anchorMillis) / duration。 */
    private long anchorMillis = 0L;
    private float anchorProgress = 0.0F;
    /** +1 正向播放 / −1 反向播放(仅 COLLAPSING 可反向)。 */
    private int direction = 1;

    /**
     * 设置目标状态(聊天是否打开);按 §4.2 重入规则从当前进度续播/反向/折算,不再硬切。
     *
     * @param open          目标:聊天打开
     * @param nowMillis     当前时刻
     * @param collapseMillis 收起动画时长
     * @param popMillis     弹出动画时长
     * @param closingMillis 关闭动画时长
     */
    public synchronized void setTarget(boolean open, long nowMillis,
            long collapseMillis, long popMillis, long closingMillis) {
        boolean openStable = open && phase == Phase.CONTAINER;
        boolean closedStable = !open && phase == Phase.HUD;
        if (open == targetOpen && (openStable || closedStable)) {
            return; // 已稳定,幂等
        }
        targetOpen = open;
        if (open) {
            switch (phase) {
                case CLOSING:
                    // §4.2:CLOSING 不可被打断为 POPPING,挂起待完成后进 COLLAPSING
                    pendingOpen = true;
                    break;
                case COLLAPSING:
                    if (direction < 0) {
                        // 反向收起中再展开:先取当前 progress 再移锚点(双向可逆,不硬切)
                        float current = collapseProgress(nowMillis, collapseMillis);
                        anchorMillis = nowMillis;
                        anchorProgress = current;
                        direction = 1;
                    }
                    break;
                case HUD:
                    enterCollapsing(nowMillis);
                    break;
                default:
                    break; // POPPING/CONTAINER:打开流程继续(幂等)
            }
        } else {
            switch (phase) {
                case CONTAINER:
                    startClosing(nowMillis, 0.0F);
                    pendingOpen = false;
                    break;
                case POPPING:
                    // §4.2:POPPING 中关闭 → 从半途折算反向起点(closing 从 1−p 开始)
                    float pop = popProgress(nowMillis, popMillis);
                    startClosing(nowMillis, 1.0F - pop);
                    pendingOpen = false;
                    break;
                case COLLAPSING:
                    if (direction > 0) {
                        // §4.2:COLLAPSING 中反悔 → 先取当前 progress p 再移锚点反向播放(不硬切)
                        float current = collapseProgress(nowMillis, collapseMillis);
                        anchorMillis = nowMillis;
                        anchorProgress = current;
                        direction = -1;
                    }
                    break;
                case CLOSING:
                    pendingOpen = false; // 关闭中再关闭:取消挂起的打开
                    break;
                default:
                    break; // HUD:幂等
            }
        }
    }

    private void enterCollapsing(long nowMillis) {
        phase = Phase.COLLAPSING;
        anchorMillis = nowMillis;
        anchorProgress = 0.0F;
        direction = 1;
        pendingOpen = false;
    }

    /** 进入 CLOSING(progress 从 {@code startProgress} 起播;CONTAINER 路径 = 0,POPPING 折算路径 = 1−p)。 */
    private void startClosing(long nowMillis, float startProgress) {
        phase = Phase.CLOSING;
        anchorMillis = nowMillis;
        anchorProgress = Math.max(0.0F, Math.min(1.0F, startProgress));
        direction = 1;
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
            case COLLAPSING: {
                float p = collapseProgress(nowMillis, collapseMillis);
                if (direction > 0 && p >= 1.0F) {
                    phase = Phase.POPPING;
                    anchorMillis = nowMillis;
                    anchorProgress = 0.0F;
                    direction = 1;
                } else if (direction < 0 && p <= 0.0F) {
                    // 反向收起播放完毕:回到 HUD
                    phase = Phase.HUD;
                    anchorMillis = nowMillis;
                    anchorProgress = 0.0F;
                    direction = 1;
                }
                break;
            }
            case POPPING:
                if (popProgress(nowMillis, popMillis) >= 1.0F) {
                    phase = Phase.CONTAINER;
                }
                break;
            case CLOSING:
                if (closingProgress(nowMillis, closingMillis) >= 1.0F) {
                    if (pendingOpen) {
                        // §4.2:CLOSING 完成后兑现挂起的打开
                        enterCollapsing(nowMillis);
                    } else {
                        phase = Phase.HUD;
                        anchorMillis = nowMillis;
                        anchorProgress = 0.0F;
                        direction = 1;
                    }
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
     * @return 当前阶段进度 [0,1](COLLAPSING 反向播放时从锚点递减)
     */
    public synchronized float progress(long nowMillis, long durationMillis) {
        switch (phase) {
            case HUD:
                return 0.0F;
            case CONTAINER:
                return 1.0F;
            default:
                break;
        }
        if (durationMillis <= 0) {
            return direction > 0 ? 1.0F : 0.0F;
        }
        float raw = anchorProgress + direction * (float) (nowMillis - anchorMillis) / (float) durationMillis;
        return Math.max(0.0F, Math.min(1.0F, raw));
    }

    /** COLLAPSING 进度(受 direction 控制,可反向)。 */
    private float collapseProgress(long nowMillis, long collapseMillis) {
        return progress(nowMillis, collapseMillis);
    }

    /** POPPING 进度(恒正向,anchorProgress=0)。 */
    private float popProgress(long nowMillis, long popMillis) {
        return progress(nowMillis, popMillis);
    }

    /** CLOSING 进度(恒正向,起点可为折算值)。 */
    private float closingProgress(long nowMillis, long closingMillis) {
        return progress(nowMillis, closingMillis);
    }

    /** @return 当前阶段 */
    public synchronized Phase getPhase() {
        return phase;
    }
}
