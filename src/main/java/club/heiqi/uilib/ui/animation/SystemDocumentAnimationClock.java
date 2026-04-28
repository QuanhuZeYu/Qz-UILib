package club.heiqi.uilib.ui.animation;

/**
 * 使用系统单调时钟的 HTML-like 动画时间源。
 */
public final class SystemDocumentAnimationClock implements DocumentAnimationClock {

    private static final SystemDocumentAnimationClock INSTANCE = new SystemDocumentAnimationClock();

    private SystemDocumentAnimationClock() {}

    /**
     * 返回共享系统动画时钟。
     *
     * @return 系统动画时钟
     */
    public static SystemDocumentAnimationClock getInstance() {
        return INSTANCE;
    }

    @Override
    public long getCurrentTimeNanos() {
        return System.nanoTime();
    }
}
