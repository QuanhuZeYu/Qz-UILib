package club.heiqi.uilib.ui.hud.api;

/**
 * 一次放置度量上报（{@link HudLayoutService#observe(String, HudLayoutMetrics)} 的输入）。
 *
 * <p>度量描述宿主<b>解析放置时的实参</b>：视口尺寸、内容物理盒、安全区，以及
 * 「已提交偏移 → 解析空间偏移」的系数 {@code offsetScale}（关闭态宿主 = HUD 全局倍率，
 * 其余宿主 = 1.0）。百分比分母 = 可用空间 − 内容物理盒（见
 * {@link HudLayoutResolver#travelSpan(int, int, int, int)}），因此度量必须与宿主交给
 * {@link HudLayoutResolver#resolve} 的参数逐项一致，否则还原位置会漂移。</p>
 *
 * <p>不可变值类型：不做坐标数学、不持有 UI 类型；{@code insets} 为空按 {@link HudInsets#NONE}，
 * {@code offsetScale} 非有限或 ≤ 0 收敛为 1.0。</p>
 */
public final class HudLayoutMetrics {

    private final int viewportWidth;
    private final int viewportHeight;
    private final int contentWidth;
    private final int contentHeight;
    private final HudInsets insets;
    private final float offsetScale;

    private HudLayoutMetrics(int viewportWidth, int viewportHeight, int contentWidth, int contentHeight,
            HudInsets insets, float offsetScale) {
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        this.insets = insets == null ? HudInsets.NONE : insets;
        this.offsetScale = (Float.isNaN(offsetScale) || Float.isInfinite(offsetScale) || offsetScale <= 0F)
                ? 1.0F : offsetScale;
    }

    /**
     * 以视口 / 内容物理盒 / 安全区创建度量（{@code offsetScale} 缺省 1.0）。
     *
     * @param viewportWidth  解析空间视口宽
     * @param viewportHeight 解析空间视口高
     * @param contentWidth   解析空间内容物理盒宽
     * @param contentHeight  解析空间内容物理盒高
     * @param insets         解析空间安全区（null = {@link HudInsets#NONE}）
     * @return 度量
     */
    public static HudLayoutMetrics of(int viewportWidth, int viewportHeight, int contentWidth,
            int contentHeight, HudInsets insets) {
        return new HudLayoutMetrics(viewportWidth, viewportHeight, contentWidth, contentHeight, insets, 1.0F);
    }

    /**
     * 设置「已提交偏移 × 系数 = 解析空间偏移」的换算系数。
     *
     * @param value 系数（非有限或 ≤ 0 收敛为 1.0）
     * @return 新度量
     */
    public HudLayoutMetrics offsetScale(float value) {
        return new HudLayoutMetrics(viewportWidth, viewportHeight, contentWidth, contentHeight, insets, value);
    }

    /** @return 解析空间视口宽 */
    public int getViewportWidth() { return viewportWidth; }

    /** @return 解析空间视口高 */
    public int getViewportHeight() { return viewportHeight; }

    /** @return 解析空间内容物理盒宽 */
    public int getContentWidth() { return contentWidth; }

    /** @return 解析空间内容物理盒高 */
    public int getContentHeight() { return contentHeight; }

    /** @return 解析空间安全区（恒非 null） */
    public HudInsets getInsets() { return insets; }

    /** @return 已提交偏移 → 解析空间偏移的换算系数（恒有限正数） */
    public float getOffsetScale() { return offsetScale; }

    /** @return 是否与另一度量描述同一个解析盒（视口/内容/安全区/系数全部相同） */
    public boolean sameBox(HudLayoutMetrics other) {
        if (other == null) {
            return false;
        }
        return viewportWidth == other.viewportWidth
                && viewportHeight == other.viewportHeight
                && contentWidth == other.contentWidth
                && contentHeight == other.contentHeight
                && insets.getLeft() == other.insets.getLeft()
                && insets.getTop() == other.insets.getTop()
                && insets.getRight() == other.insets.getRight()
                && insets.getBottom() == other.insets.getBottom()
                && Float.compare(offsetScale, other.offsetScale) == 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HudLayoutMetrics)) {
            return false;
        }
        return sameBox((HudLayoutMetrics) other);
    }

    @Override
    public int hashCode() {
        int result = viewportWidth;
        result = result * 31 + viewportHeight;
        result = result * 31 + contentWidth;
        result = result * 31 + contentHeight;
        result = result * 31 + insets.getLeft();
        result = result * 31 + insets.getTop();
        result = result * 31 + insets.getRight();
        result = result * 31 + insets.getBottom();
        return result * 31 + Float.floatToIntBits(offsetScale);
    }

    @Override
    public String toString() {
        return "HudLayoutMetrics{viewport=" + viewportWidth + "x" + viewportHeight
                + ", content=" + contentWidth + "x" + contentHeight
                + ", insets=" + insets.getLeft() + "," + insets.getTop() + ","
                + insets.getRight() + "," + insets.getBottom()
                + ", offsetScale=" + offsetScale + '}';
    }
}
