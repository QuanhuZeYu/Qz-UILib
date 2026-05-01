package club.heiqi.uilib.ui.theme;

/**
 * 通用表面样式。
 *
 * <p>用于承载容器或页面壳的背景与边框表面，而不是在结构容器上暴露分散的颜色入口。</p>
 */
public final class UiSurfaceStyle {

    public static final int CORNER_TOP_LEFT = 1;
    public static final int CORNER_TOP_RIGHT = 1 << 1;
    public static final int CORNER_BOTTOM_RIGHT = 1 << 2;
    public static final int CORNER_BOTTOM_LEFT = 1 << 3;
    public static final int CORNER_ALL = CORNER_TOP_LEFT | CORNER_TOP_RIGHT | CORNER_BOTTOM_RIGHT
            | CORNER_BOTTOM_LEFT;

    private static final UiSurfaceStyle NONE = new UiSurfaceStyle(0, 0, 0);

    public final int fillColor;
    public final int borderColor;
    public final int cornerRadius;
    public final int cornerMask;

    public UiSurfaceStyle(int fillColor, int borderColor) {
        this(fillColor, borderColor, 0);
    }

    public UiSurfaceStyle(int fillColor, int borderColor, int cornerRadius) {
        this(fillColor, borderColor, cornerRadius, CORNER_ALL);
    }

    public UiSurfaceStyle(int fillColor, int borderColor, int cornerRadius, int cornerMask) {
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        this.cornerRadius = Math.max(0, cornerRadius);
        this.cornerMask = cornerMask & CORNER_ALL;
    }

    /**
     * 获取空表面样式。
     *
     * @return 空表面样式
     */
    public static UiSurfaceStyle none() {
        return NONE;
    }
}
