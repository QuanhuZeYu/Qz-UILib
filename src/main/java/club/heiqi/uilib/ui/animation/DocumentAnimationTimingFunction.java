package club.heiqi.uilib.ui.animation;

/**
 * HTML-like transition timing function。
 */
public enum DocumentAnimationTimingFunction {
    /**
     * 线性插值。
     */
    LINEAR,

    /**
     * 简化版 ease。
     */
    EASE,

    /**
     * 简化版 ease-in。
     */
    EASE_IN,

    /**
     * 简化版 ease-out。
     */
    EASE_OUT,

    /**
     * 简化版 ease-in-out。
     */
    EASE_IN_OUT;

    /**
     * 计算缓动后的进度。
     *
     * @param progress 原始 0..1 进度
     * @return 缓动后 0..1 进度
     */
    public float apply(float progress) {
        float clampedProgress = Math.max(0.0F, Math.min(1.0F, progress));
        if (this == EASE_IN) {
            return clampedProgress * clampedProgress;
        }
        if (this == EASE_OUT) {
            float inverse = 1.0F - clampedProgress;
            return 1.0F - inverse * inverse;
        }
        if (this == EASE_IN_OUT || this == EASE) {
            return clampedProgress * clampedProgress * (3.0F - 2.0F * clampedProgress);
        }
        return clampedProgress;
    }
}
