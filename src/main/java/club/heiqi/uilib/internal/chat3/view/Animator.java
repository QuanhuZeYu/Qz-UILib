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
}
