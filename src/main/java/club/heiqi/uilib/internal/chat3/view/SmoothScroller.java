package club.heiqi.uilib.internal.chat3.view;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * 聊天 3.0 显示行偏移平滑器(T5b):行粒度滚动目标的 120ms easeOutQuad 平滑插值。
 *
 * <p>纯函数 + 轻状态,无节点引用 / 无渲染依赖 / 无时钟抓取——时间全部由调用方
 * (帧时钟)注入,headless 可测:</p>
 * <ul>
 *   <li><b>目标行</b> {@code target}:自底部向上偏移的行数,与 {@code ChatHistory.getScroll()} 同单位
 *       (0 = 底部最新);</li>
 *   <li><b>平滑起点</b> {@code start}(float):目标变化时「以当前显示为起点」重启动画,无瞬跳;</li>
 *   <li><b>直通(拖动接管)</b>:{@link #snapTo} 置位后 {@link #setTarget} 直接到位、不启动动画,
 *       拖动手感即时;退出由非拖动来源显式 {@link #releaseDrag()} (滚轮/回底/跟随)。</li>
 * </ul>
 *
 * <p>曲线 = {@link Animator#easeOut(easeOutQuad)},时长 = {@link ChatMarkdownSettings#getSmoothScrollMillis()}
 * (默认 120ms,范围 0-500;0 或负 = 瞬移语义,不启动动画)。</p>
 */
public final class SmoothScroller {

    /** 目标行(自底部向上偏移;0 = 底部)。 */
    private int target;
    /** 平滑起点(显示行,float;动画期间的插值基准)。 */
    private float start;
    /** 起点时刻(wall millis,由调用方帧时钟注入)。 */
    private long startMillis;
    /** 最近一次时钟注入时刻(displayLines/setTarget 推进;isSettled 判定用)。 */
    private long lastNowMillis;
    /** 直通标志(snapTo 置位:拖动接管,setTarget 直接到位不启动动画)。 */
    private boolean direct;

    /** 初始态:目标 0(底部)、显示 0(底部)、无动画。 */
    public SmoothScroller() {
        this.target = 0;
        this.start = 0.0F;
        this.startMillis = Long.MIN_VALUE; // 首帧 elapsed 视为超时 → computeDisplay 直接取 target
        this.lastNowMillis = 0L;
        this.direct = false;
    }

    /**
     * 设置目标行:目标变化时以当前显示为起点重启 120ms 动画;目标未变不重启(动画按原起点推进)。
     * 直通(拖动接管)中直接到位;时长配置 ≤0 时按瞬移语义直接到位。
     *
     * @param targetLines 目标行(自底部向上偏移)
     * @param nowMillis   当前 wall millis(帧时钟)
     */
    public void setTarget(int targetLines, long nowMillis) {
        lastNowMillis = nowMillis;
        if (direct || durationMillis() <= 0L) {
            // 拖动接管直通 / 瞬移配置:直接到位,不启动平滑
            target = targetLines;
            start = targetLines;
            startMillis = nowMillis;
            return;
        }
        if (targetLines == target) {
            return; // 目标未变:不重启
        }
        start = displayLines(nowMillis); // 以当前显示为起点(动画中 = 插值现位,静止 = 当前目标)
        target = targetLines;
        startMillis = nowMillis;
    }

    /**
     * 直通到位并进入拖动接管:display 立即等于目标、取消进行中的平滑;
     * 此后 {@link #setTarget} 均直接到位(拖动手感即时),直到非拖动来源调用 {@link #releaseDrag()}。
     *
     * @param targetLines 接管显示行(拖动起点)
     */
    public void snapTo(int targetLines) {
        direct = true;
        target = targetLines;
        start = targetLines;
        startMillis = lastNowMillis;
    }

    /**
     * 退出拖动接管直通:恢复「目标变化 → 重启动画」语义。
     * 由非拖动来源调用(滚轮路径 / 回底 / 贴底跟随),保证拖动结束后的滚动恢复 120ms 平滑。
     */
    public void releaseDrag() {
        direct = false;
    }

    /** @return 当前目标行(自底部向上偏移) */
    public int getTarget() {
        return target;
    }

    /**
     * @param nowMillis 当前 wall millis(帧时钟)
     * @return 当前显示行(插值结果;直通/瞬移配置下恒等于目标)
     */
    public float displayLines(long nowMillis) {
        lastNowMillis = nowMillis;
        if (direct || durationMillis() <= 0L) {
            return target; // 直通/瞬移:显示恒等于目标
        }
        return computeDisplay(start, target, nowMillis - startMillis, durationMillis());
    }

    /** @return 是否已稳定(无进行中平滑;直通/瞬移配置恒稳) */
    public boolean isSettled() {
        if (direct) {
            return true;
        }
        long duration = durationMillis();
        if (duration <= 0L) {
            return true;
        }
        return lastNowMillis - startMillis >= duration || Math.abs(start - target) < 0.5F;
    }

    /** @return 平滑时长(ms,clamp 0..500;0 或负 = 瞬移语义) */
    private static long durationMillis() {
        return ChatMarkdownSettings.getSmoothScrollMillis();
    }

    /**
     * 插值纯函数(静态,测试入口):elapsed≤0 → start;elapsed≥duration(或 duration≤0)→ target;
     * 否则 start + (target-start) × easeOutQuad(elapsed/duration)。
     *
     * @param start    平滑起点(显示行)
     * @param target   目标行
     * @param elapsed  距起点时刻经过的毫秒数
     * @param duration 动画时长(ms;≤0 = 瞬移,直接取 target)
     * @return 插值显示行
     */
    public static float computeDisplay(float start, int target, long elapsed, long duration) {
        if (elapsed <= 0L) {
            return start;
        }
        if (duration <= 0L || elapsed >= duration) {
            return (float) target;
        }
        return start + ((float) target - start) * Animator.easeOut((float) elapsed / (float) duration);
    }
}
