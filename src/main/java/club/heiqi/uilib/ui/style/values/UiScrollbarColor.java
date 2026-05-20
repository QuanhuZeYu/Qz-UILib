package club.heiqi.uilib.ui.style.values;

/**
 * HTML-like 滚动条颜色值。
 *
 * <p>颜色使用 ARGB 整数，字段顺序保持 CSS {@code scrollbar-color} 的
 * thumb / track 心智。</p>
 */
public final class UiScrollbarColor {

    public static final int DEFAULT_THUMB_COLOR = 0xDDBCD7FF;
    public static final int DEFAULT_TRACK_COLOR = 0x663B4A66;

    private static final UiScrollbarColor AUTO = new UiScrollbarColor(DEFAULT_THUMB_COLOR, DEFAULT_TRACK_COLOR);

    private final int thumbColor;
    private final int trackColor;

    private UiScrollbarColor(int thumbColor, int trackColor) {
        this.thumbColor = thumbColor;
        this.trackColor = trackColor;
    }

    /**
     * 返回默认滚动条颜色。
     *
     * @return 默认滚动条颜色
     */
    public static UiScrollbarColor auto() {
        return AUTO;
    }

    /**
     * 创建滚动条颜色值。
     *
     * @param thumbColor 滑块颜色（ARGB）
     * @param trackColor 轨道颜色（ARGB）
     * @return 滚动条颜色值
     */
    public static UiScrollbarColor of(int thumbColor, int trackColor) {
        if (thumbColor == DEFAULT_THUMB_COLOR && trackColor == DEFAULT_TRACK_COLOR) {
            return AUTO;
        }
        return new UiScrollbarColor(thumbColor, trackColor);
    }

    /** 返回滑块颜色。 */
    public int getThumbColor() { return thumbColor; }

    /** 返回轨道颜色。 */
    public int getTrackColor() { return trackColor; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UiScrollbarColor)) {
            return false;
        }
        UiScrollbarColor other = (UiScrollbarColor) obj;
        return thumbColor == other.thumbColor && trackColor == other.trackColor;
    }

    @Override
    public int hashCode() {
        int result = thumbColor;
        result = 31 * result + trackColor;
        return result;
    }

    @Override
    public String toString() {
        return String.format("thumb=#%08X track=#%08X", thumbColor, trackColor);
    }
}
