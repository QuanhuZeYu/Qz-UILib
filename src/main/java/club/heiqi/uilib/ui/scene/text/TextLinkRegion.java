package club.heiqi.uilib.ui.scene.text;

/**
 * scene 侧链接区域：单行文本内一个 {@code <a>} 段的水平区间与目标 URL。

 * <p>与渲染层 {@code ui.text.TextLinkRegion} 语义一致，由装配层 adapter 映射转换；
 * scene 核心（layout/paint）只依赖本类，不接触渲染层类型（守 I10）。</p>
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