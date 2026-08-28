package club.heiqi.uilib.internal.chat3.view;

import club.heiqi.uilib.ui.scene.node.Transform;

/**
 * 聊天输入屏容器开合动画状态机(纯 JVM,时间全部由调用方注入,headless 可测):
 *
 * <p>与 {@link ChatSceneController}/{@link DisplayStateMachine} 共用设计稿 §4.1 同源参数与
 * 曲线(注释级统一语义,不合并实现——输入屏容器动画独立于 HUD 窗口根动画,改动面受控):</p>
 * <ul>
 *   <li><b>OPEN(正向 pop)</b>:popMillis(240)easeOutBack translateY(+24→0) + scale(0.96→1)
 *       + opacity 0→1(clamp01),origin 容器左下角(0,1);</li>
 *   <li><b>CLOSING(反向收回)</b>:closingMillis(140)easeOutQuad opacity 1→0 +
 *       translateY 0→+12(scale 不参与)——与 {@code ChatSceneController.CLOSING} 同参数同曲线;</li>
 *   <li><b>重入(设计稿 §4.2)</b>:{@link #requestClose} 后再次请求 = 幂等——返回同一请求、
 *       不重置动画起点、完成回调只触发一次;OPEN 弹出中途请求关闭按 §4.2 折算:
 *       closing 从 1−p 起播(与 {@code DisplayStateMachine} POPPING→CLOSING 同规则,
 *       容器越接近弹出完成,关闭越接近从头播放);</li>
 *   <li><b>兜底</b>:关闭动画挂起(渲染停滞等)超 {@code closeTimeoutMillis}(生产
 *       {@value #DEFAULT_CLOSE_TIMEOUT_MILLIS}ms,远大于 closing 140)强制转入完成态并
 *       让回调可被取走触发,不放任屏幕卡死。</li>
 * </ul>
 *
 * <p>本类不持有时钟——nowMillis 由调用方(渲染帧 / 屏幕 updateScreen)注入,测试精确控制时间。</p>
 */
public final class ChatSurfaceAnimator {

    /** 生产关闭动画挂起兜底阈值下界(ms):设计定 500;实际装配按 closing 时长联动
     *  (见 {@link #closeTimeoutFor(long)}——动画必须完整播放,超时只兜底渲染挂起)。 */
    public static final long DEFAULT_CLOSE_TIMEOUT_MILLIS = 500L;

    /**
     * 生产装配关闭超时(ms) = max(500, closing + 500):保证关闭动画在任何配置时长下
     * 完整播放(closing 可配至秒级,2026-08-29 用户高层语义「关闭动画必须完整,
     * 超时只防渲染挂起卡死」),并保留 500ms 挂起检测缓冲。
     *
     * @param closingMillis 配置的关闭动画时长(ms;≤0 = 瞬完语义)
     * @return 装配用超时阈值
     */
    public static long closeTimeoutFor(long closingMillis) {
        return Math.max(DEFAULT_CLOSE_TIMEOUT_MILLIS, closingMillis + DEFAULT_CLOSE_TIMEOUT_MILLIS);
    }

    /** 开合阶段。 */
    public enum Phase {
        /** 容器显示态(含弹出动画,progress 归 1 后稳定)。 */
        OPEN,
        /** 关闭动画播放中(140ms easeOutQuad 淡出 + 下滑)。 */
        CLOSING,
        /** 关闭完成:透明不可见,完成回调已可被取走触发。 */
        CLOSED
    }

    /** 关闭请求令牌(重入幂等判定:同一请求同一实例)。 */
    public static final class CloseRequest {
        private CloseRequest() {
        }
    }

    private final long popMillis;
    private final long closingMillis;
    private final long closeTimeoutMillis;

    private Phase phase = Phase.CLOSED;
    /** 打开时刻(弹出动画基准)。 */
    private long openStartMillis = 0L;
    /** 关闭起点时刻。 */
    private long closeStartMillis = 0L;
    /** 关闭起点进度(OPEN 中途关闭按 §4.2 折算 = 1 − 弹出进度)。 */
    private float closeStartProgress = 0.0F;
    /** 已注册的关闭完成回调(取走后清空,同一请求只触发一次)。 */
    private Runnable onCloseComplete;
    /** 完成后待取走的回调是否仍在(消费式取走,takeCloseCallback 清零)。 */
    private boolean callbackPending = false;
    /** 当前关闭请求(重入幂等返回同一实例)。 */
    private CloseRequest closeRequest = new CloseRequest();

    /**
     * @param popMillis          弹出动画时长(ms,设计稿 §4.1 240;≤0 = 弹出瞬完)
     * @param closingMillis      关闭动画时长(ms,设计稿 §4.1 140;≤0 = 关闭瞬完)
     * @param closeTimeoutMillis 关闭动画挂起兜底阈值(ms;生产 {@value #DEFAULT_CLOSE_TIMEOUT_MILLIS})
     */
    public ChatSurfaceAnimator(long popMillis, long closingMillis, long closeTimeoutMillis) {
        this.popMillis = popMillis;
        this.closingMillis = closingMillis;
        this.closeTimeoutMillis = closeTimeoutMillis;
    }

    // ==================== 生命周期 ====================

    /** 打开(容器弹出):surface 打开时调用一次,重置关闭态。 */
    public void startOpen(long nowMillis) {
        phase = Phase.OPEN;
        openStartMillis = nowMillis;
        onCloseComplete = null;
        callbackPending = false;
    }

    /**
     * 请求关闭(播放 CLOSING 动画,完成后可视作屏幕可关)。
     *
     * <p>幂等:CLOSING/CLOSED 期间重复调用返回同一请求,不重置动画起点、不重复注册回调;
     * OPEN 弹出中途请求按 §4.2 折算起点 1−p(与 DisplayStateMachine POPPING→CLOSING 同规则)。</p>
     *
     * @param onCloseComplete 关闭完成回调(实际触发由调用方在 {@link #takeCloseCallback}
     *                        取走后执行——不能在 render 栈内触发,关屏会销毁 surface)
     * @param nowMillis       当前 wall millis
     * @return 本次关闭请求令牌(重入时 = 旧令牌)
     */
    public CloseRequest requestClose(Runnable onCloseComplete, long nowMillis) {
        if (phase == Phase.CLOSING || phase == Phase.CLOSED) {
            return closeRequest; // 重入:返回旧请求(动画起点/回调均不重置)
        }
        this.onCloseComplete = onCloseComplete;
        this.callbackPending = false;
        this.closeRequest = new CloseRequest();
        // §4.2 折算:弹出一半时关闭从 1−p 起播(p=1 稳定后 = 0 从头播;p=0 未弹出 = 1 直接完成,
        // 容器本就不可见,无可播内容)
        this.closeStartProgress = 1.0F - openProgress(nowMillis);
        this.closeStartMillis = nowMillis;
        this.phase = Phase.CLOSING;
        return closeRequest;
    }

    /**
     * 每帧推进(渲染帧推进动画 + 屏幕 updateScreen 兜底调用):关闭动画完成(进度 ≥1)或
     * 挂起超时(距起点 ≥ closeTimeoutMillis,兜底防卡死)时进入 CLOSED,完成回调转为待取。
     *
     * @param nowMillis 当前 wall millis
     * @return 推进后的阶段
     */
    public Phase tick(long nowMillis) {
        if (phase != Phase.CLOSING) {
            return phase; // OPEN/CLOSED:无完成事件
        }
        boolean done = progress(nowMillis) >= 1.0F
                || nowMillis - closeStartMillis >= closeTimeoutMillis;
        if (done) {
            phase = Phase.CLOSED;
            callbackPending = onCloseComplete != null;
        }
        return phase;
    }

    /**
     * 取走关闭完成回调(消费式,仅一次;null = 无待触发回调)。
     *
     * <p>回调必须在渲染栈外触发:关屏 displayGuiScreen(null) → onGuiClosed → surface/容器
     * 销毁,render 栈内执行会打断本帧渲染管线;生产由屏幕 updateScreen 每 tick 取走触发。</p>
     */
    public Runnable takeCloseCallback() {
        if (!callbackPending) {
            return null;
        }
        callbackPending = false;
        return onCloseComplete;
    }

    // ==================== 查询 ====================

    /** @return 当前阶段 */
    public Phase phase() {
        return phase;
    }

    /** @return 是否正在播放关闭动画 */
    public boolean isClosing() {
        return phase == Phase.CLOSING;
    }

    /** @return 关闭是否已完成(回调已可取/已取) */
    public boolean isClosed() {
        return phase == Phase.CLOSED;
    }

    /**
     * @param nowMillis 当前 wall millis
     * @return 当前阶段进度 [0,1]:OPEN = 弹出进度;CLOSING = 关闭进度(起点含 §4.2 折算);CLOSED = 1
     */
    public float progress(long nowMillis) {
        switch (phase) {
            case OPEN:
                return openProgress(nowMillis);
            case CLOSING:
                return closeProgress(nowMillis);
            default:
                return 1.0F;
        }
    }

    /** 弹出进度(设计稿 §4.1 pop 240)。 */
    private float openProgress(long nowMillis) {
        return clamp01(elapsedRatio(popMillis, nowMillis - openStartMillis));
    }

    /** 关闭进度(起点含 §4.2 折算)。 */
    private float closeProgress(long nowMillis) {
        return clamp01(closeStartProgress + elapsedRatio(closingMillis, nowMillis - closeStartMillis));
    }

    /** elapsed/duration,时长 ≤0 = 瞬完语义(返回 1)。 */
    private static float elapsedRatio(long durationMillis, long elapsedMillis) {
        return durationMillis <= 0L ? 1.0F : (float) elapsedMillis / (float) durationMillis;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    // ==================== 渲染输出(设计稿 §4.1,与 ChatSceneController 同参数同曲线) ====================

    /** 根 transform:OPEN = easeOutBack pop;CLOSING = easeOutQuad 下滑;CLOSED = 关闭终点。origin 左下角(0,1)。 */
    public Transform transform(long nowMillis) {
        switch (phase) {
            case OPEN: {
                float eased = Animator.easeOutBack(openProgress(nowMillis));
                // 弹出:translateY +24→0、scale 0.96→1(transform 通道保留 overshoot)
                return new Transform(0.0F, 24.0F * (1.0F - eased), 0.0F,
                        0.96F + 0.04F * eased, 0.96F + 0.04F * eased, 0.0F, 1.0F);
            }
            case CLOSING: {
                float eased = Animator.easeOut(closeProgress(nowMillis));
                // 关闭:translateY 0→+12(下滑消失),scale 不参与——与 ChatSceneController.CLOSING 同参数同曲线
                return new Transform(0.0F, 12.0F * eased, 0.0F,
                        1.0F, 1.0F, 0.0F, 1.0F);
            }
            default:
                // CLOSED:关闭终点(淡出完成,下滑到底;容器透明,视觉不可见)
                return new Transform(0.0F, 12.0F, 0.0F,
                        1.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    /** 根 opacity:OPEN = clamp01(easeOutBack)(不超 1);CLOSING = 1 − easeOut(1→0);CLOSED = 0。 */
    public float opacity(long nowMillis) {
        switch (phase) {
            case OPEN:
                return Animator.clamp01(Animator.easeOutBack(openProgress(nowMillis)));
            case CLOSING:
                return 1.0F - Animator.easeOut(closeProgress(nowMillis));
            default:
                return 0.0F;
        }
    }
}
