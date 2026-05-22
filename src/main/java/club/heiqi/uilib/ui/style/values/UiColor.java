package club.heiqi.uilib.ui.style.values;

/**
 * 颜色工具类，提供便捷的 ARGB 颜色构造方法。
 *
 * <p>所有方法返回 int 类型的 ARGB 颜色值（0xAARRGGBB），可直接传入
 * {@code UiStyleDeclaration#setBackgroundColor(int)} 等样式方法。</p>
 */
public final class UiColor {

    private UiColor() {
    }

    /**
     * 从 RGB 分量创建不透明颜色。
     *
     * @param r 红色分量（0-255）
     * @param g 绿色分量（0-255）
     * @param b 蓝色分量（0-255）
     * @return ARGB 颜色值（alpha = 255）
     */
    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /**
     * 从 RGBA 分量创建颜色。
     *
     * @param r 红色分量（0-255）
     * @param g 绿色分量（0-255）
     * @param b 蓝色分量（0-255）
     * @param a 透明度分量（0-255，0=完全透明，255=完全不透明）
     * @return ARGB 颜色值
     */
    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /**
     * 从浮点 RGB 分量创建不透明颜色。
     *
     * @param r 红色分量（0.0-1.0）
     * @param g 绿色分量（0.0-1.0）
     * @param b 蓝色分量（0.0-1.0）
     * @return ARGB 颜色值（alpha = 255）
     */
    public static int rgb(float r, float g, float b) {
        return rgb(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    /**
     * 从浮点 RGBA 分量创建颜色。
     *
     * @param r 红色分量（0.0-1.0）
     * @param g 绿色分量（0.0-1.0）
     * @param b 蓝色分量（0.0-1.0）
     * @param a 透明度分量（0.0-1.0）
     * @return ARGB 颜色值
     */
    public static int rgba(float r, float g, float b, float a) {
        return rgba(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255), Math.round(a * 255));
    }

    /**
     * 修改已有颜色的透明度。
     *
     * @param color 原始 ARGB 颜色
     * @param alpha 新的透明度（0-255）
     * @return 修改后的 ARGB 颜色值
     */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * 修改已有颜色的透明度（浮点）。
     *
     * @param color 原始 ARGB 颜色
     * @param alpha 新的透明度（0.0-1.0）
     * @return 修改后的 ARGB 颜色值
     */
    public static int withAlpha(int color, float alpha) {
        return withAlpha(color, Math.round(alpha * 255));
    }

    /**
     * 提取颜色的 alpha 分量。
     *
     * @param color ARGB 颜色值
     * @return alpha 分量（0-255）
     */
    public static int alpha(int color) {
        return (color >> 24) & 0xFF;
    }

    /**
     * 提取颜色的红色分量。
     *
     * @param color ARGB 颜色值
     * @return 红色分量（0-255）
     */
    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    /**
     * 提取颜色的绿色分量。
     *
     * @param color ARGB 颜色值
     * @return 绿色分量（0-255）
     */
    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    /**
     * 提取颜色的蓝色分量。
     *
     * @param color ARGB 颜色值
     * @return 蓝色分量（0-255）
     */
    public static int blue(int color) {
        return color & 0xFF;
    }
}
