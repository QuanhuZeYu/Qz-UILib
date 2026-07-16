package club.heiqi.uilib.ui.scene.overlay;

/**
 * 锚定浮层的不可变横向尺寸策略。
 *
 * <p>{@link #DEFAULT} 保持触发器等宽且不留安全边距；显式策略优先使用 preferredWidth，
 * 可用宽度低于 preferredWidth 时逐步收窄，低于 minWidth 时退化为窄屏可用宽度。</p>
 */
public final class AnchoredPortalLayout {

    /** 保持旧行为的默认策略：触发器等宽、安全边距为零。 */
    public static final AnchoredPortalLayout DEFAULT = new AnchoredPortalLayout(0, 0, 0);

    private final int preferredWidth;
    private final int minWidth;
    private final int safeInset;

    /**
     * 创建锚定浮层尺寸策略。
     *
     * @param preferredWidth 宽屏首选宽度；0 表示跟随触发器宽度
     * @param minWidth 中屏最小目标宽度，不得大于首选宽度
     * @param safeInset 浮层距宿主左右边缘的安全边距
     */
    public AnchoredPortalLayout(int preferredWidth, int minWidth, int safeInset) {
        if (preferredWidth < 0 || minWidth < 0 || safeInset < 0) {
            throw new IllegalArgumentException("portal layout values must be non-negative");
        }
        if (minWidth > preferredWidth) {
            throw new IllegalArgumentException("minWidth must not exceed preferredWidth");
        }
        if (preferredWidth == 0 && minWidth != 0) {
            throw new IllegalArgumentException("trigger-width policy requires minWidth=0");
        }
        this.preferredWidth = preferredWidth;
        this.minWidth = minWidth;
        this.safeInset = safeInset;
    }

    /** @return 宽屏首选宽度；0 表示跟随触发器宽度 */
    public int getPreferredWidth() {
        return preferredWidth;
    }

    /** @return 中屏最小目标宽度 */
    public int getMinWidth() {
        return minWidth;
    }

    /** @return 左右安全边距 */
    public int getSafeInset() {
        return safeInset;
    }

    /** @return 是否为保持旧行为的触发器等宽策略 */
    public boolean isTriggerWidth() {
        return preferredWidth == 0;
    }
}
