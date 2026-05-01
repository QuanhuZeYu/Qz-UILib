package club.heiqi.uilib.ui.animation;

/**
 * keyframe animation 的填充模式。
 */
public enum DocumentAnimationFillMode {
    /**
     * 动画有效区间外回到 computed style 基准值。
     */
    NONE,

    /**
     * delay 期间使用首帧值，结束后回到 computed style 基准值。
     */
    BACKWARDS,

    /**
     * delay 期间回到 computed style 基准值，结束后保留末帧值。
     */
    FORWARDS,

    /**
     * delay 期间使用首帧值，结束后保留末帧值。
     */
    BOTH
}
