package club.heiqi.uilib.font.internal;

/** 内部数学字号量化：测量与展平绘制共用，不属于字体公共 API。 */
public final class LatexFontSize {

    private LatexFontSize() {}

    /**
     * 保留 max(1, truncate) 语义；仅消除缩放连乘及 size/base 往返在整数附近的浮点噪声。
     * 布局度量和绘制必须同时使用此口径，不能只在绘制端 round 或加 epsilon。
     */
    public static int effective(float sizePx) {
        float nearest = Math.round(sizePx);
        if (Math.abs(sizePx - nearest) <= 4.0F * Math.ulp(sizePx)) {
            sizePx = nearest;
        }
        return Math.max(1, (int) sizePx);
    }
}
