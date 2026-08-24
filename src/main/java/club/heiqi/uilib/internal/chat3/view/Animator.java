package club.heiqi.uilib.internal.chat3.view;

/**
 * 聊天 3.0 动画插值器(纯函数):wall-clock 补间进度与缓动。
 */
public final class Animator {

    private Animator() {
    }

    /** @return 进度夹取到 [0,1] */
    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** @return 线性缓动(恒等) */
    public static float linear(float progress) {
        return clamp01(progress);
    }

    /** @return ease-out 二次缓动(减速收尾) */
    public static float easeOut(float progress) {
        float p = clamp01(progress);
        return 1.0F - (1.0F - p) * (1.0F - p);
    }

    /** @return ease-in 二次缓动(p²,慢启动快收尾;HUD 过期淡出曲线) */
    public static float easeInQuad(float progress) {
        float p = clamp01(progress);
        return p * p;
    }

    /** @return ease-out 三次缓动(1-(1-p)³,减速更快更干脆;HUD 组出生 enter 曲线,设计稿 §4.1) */
    public static float easeOutCubic(float progress) {
        float p = clamp01(progress);
        float q = 1.0F - p;
        return 1.0F - q * q * q;
    }

    /**
     * @return ease-out back 缓动(先超调后回弹;容器 pop 弹出曲线,设计稿 §4.1,c 默认 1.04,
     *         峰值 ≈4% 克制回弹——编排裁决 2026-08-24:设计稿标注 c=1.4 与「约 4%」矛盾,
     *         取 1.04 贴合「克制不回弹过度」意图)。
     *         输入 p∈[0,1] 时输出可 &gt;1(overshoot 语义),调用方按通道决定是否 clamp
     *         (opacity 通道 clamp01,transform 通道保留超调)。
     */
    public static float easeOutBack(float progress) {
        return easeOutBack(progress, 1.04F);
    }

    /**
     * @param progress 当前进度(内部夹取到 [0,1])
     * @param c        回弹强度系数(默认 1.04;overshoot 幅度随 c 增大)
     * @return ease-out back 缓动:1 + (c+1)(p-1)³ + c(p-1)²;p=0 → 0、p=1 → 1,
     *         峰值 p*=1−2c/(3(c+1)) 处 f_max=1+4c³/(27(c+1)²)(c=1.04 → ≈1.0406)
     */
    public static float easeOutBack(float progress, float c) {
        float p = clamp01(progress);
        float q = p - 1.0F;
        return 1.0F + (c + 1.0F) * q * q * q + c * q * q;
    }
}
