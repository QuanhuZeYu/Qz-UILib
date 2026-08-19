package club.heiqi.uilib.ui.text;

/**
 * 渲染层链接区域：单行文本内一个 {@code <a>} 段的水平区间与目标 URL。

 * <p>由 {@link TextMeasureService#getLinkRegions} 产出（富文本感知：标签不占宽，
 * 字号/上下标/字距均计入偏移），供 scene 装配层换算为命中区域。</p>
 */
public final class TextLinkRegion {

    /** 行内起始 X（UI 像素，相对行左缘）。 */
    private final int startX;
    /** 区域宽度（UI 像素）。 */
    private final int width;
    /** 链接 URL。 */
    private final String url;

    public TextLinkRegion(int startX, int width, String url) {
        this.startX = startX;
        this.width = width;
        this.url = url;
    }

    /** @return 行内起始 X（UI 像素） */
    public int getStartX() {
        return startX;
    }

    /** @return 区域宽度（UI 像素） */
    public int getWidth() {
        return width;
    }

    /** @return 链接 URL */
    public String getUrl() {
        return url;
    }
}