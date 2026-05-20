package club.heiqi.uilib.ui.theme;

import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;

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
    public final UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii;

    public UiSurfaceStyle(int fillColor, int borderColor) {
        this(fillColor, borderColor, 0);
    }

    public UiSurfaceStyle(int fillColor, int borderColor, int cornerRadius) {
        this(fillColor, borderColor, UiBorderRadiusResolver.ResolvedCornerRadii.uniform(cornerRadius));
    }

    public UiSurfaceStyle(int fillColor, int borderColor, int cornerRadius, int cornerMask) {
        this(fillColor, borderColor, UiBorderRadiusResolver.ResolvedCornerRadii.uniform(cornerRadius), cornerMask);
    }

    /**
     * 创建支持分角圆角的表面样式。
     *
     * @param fillColor 填充颜色
     * @param borderColor 边框颜色
     * @param cornerRadii 四角圆角
     */
    public UiSurfaceStyle(int fillColor, int borderColor, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        this(fillColor, borderColor, cornerRadii, CORNER_ALL);
    }

    /**
     * 创建支持分角圆角和指定角位掩码的表面样式。
     *
     * @param fillColor 填充颜色
     * @param borderColor 边框颜色
     * @param cornerRadii 四角圆角
     * @param cornerMask 角位掩码
     */
    public UiSurfaceStyle(int fillColor, int borderColor, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii,
            int cornerMask) {
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        this.cornerRadii = cornerRadii == null ? UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0) : cornerRadii;
        this.cornerRadius = this.cornerRadii.isUniform() ? this.cornerRadii.getUniformRadius() : 0;
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
