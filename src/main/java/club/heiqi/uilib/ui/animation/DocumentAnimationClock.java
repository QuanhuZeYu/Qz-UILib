package club.heiqi.uilib.ui.animation;

/**
 * HTML-like 动画时间源。
 */
public interface DocumentAnimationClock {

    /**
     * 返回当前动画时间戳。
     *
     * @return 当前时间，单位纳秒
     */
    long getCurrentTimeNanos();
}
