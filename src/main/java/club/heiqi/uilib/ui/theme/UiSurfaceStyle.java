package club.heiqi.uilib.ui.theme;

/**
 * 通用表面样式。
 *
 * <p>用于承载容器或页面壳的背景与边框表面，而不是在结构容器上暴露分散的颜色入口。</p>
 */
public final class UiSurfaceStyle {

    private static final UiSurfaceStyle NONE = new UiSurfaceStyle(0, 0, 0);

    public final int fillColor;
    public final int borderColor;
    public final int cornerRadius;

    public UiSurfaceStyle(int fillColor, int borderColor) {
        this(fillColor, borderColor, 0);
    }

    public UiSurfaceStyle(int fillColor, int borderColor, int cornerRadius) {
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        this.cornerRadius = Math.max(0, cornerRadius);
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
