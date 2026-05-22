package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素在文档局部坐标系下的布局边界。
 */
public final class DocumentElementBounds {

    private static final DocumentElementBounds UNAVAILABLE = new DocumentElementBounds(false, 0, 0, 0, 0, 0, 0, 0,
            0);

    private final boolean available;
    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private final int contentLeft;
    private final int contentTop;
    private final int contentWidth;
    private final int contentHeight;

    private DocumentElementBounds(boolean available, int left, int top, int width, int height, int contentLeft,
            int contentTop, int contentWidth, int contentHeight) {
        this.available = available;
        this.left = left;
        this.top = top;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.contentLeft = contentLeft;
        this.contentTop = contentTop;
        this.contentWidth = Math.max(0, contentWidth);
        this.contentHeight = Math.max(0, contentHeight);
    }

    /**
     * 创建可用布局边界。
     *
     * @param left 左侧文档坐标
     * @param top 顶部文档坐标
     * @param width 宽度
     * @param height 高度
     * @return 布局边界
     */
    public static DocumentElementBounds of(int left, int top, int width, int height) {
        return of(left, top, width, height, left, top, width, height);
    }

    /**
     * 创建可用布局边界。
     *
     * @param left 左侧文档坐标
     * @param top 顶部文档坐标
     * @param width 宽度
     * @param height 高度
     * @param contentLeft 内容区左侧文档坐标
     * @param contentTop 内容区顶部文档坐标
     * @param contentWidth 内容区宽度
     * @param contentHeight 内容区高度
     * @return 布局边界
     */
    public static DocumentElementBounds of(int left, int top, int width, int height, int contentLeft, int contentTop,
            int contentWidth, int contentHeight) {
        return new DocumentElementBounds(true, left, top, width, height, contentLeft, contentTop, contentWidth,
                contentHeight);
    }

    /**
     * 返回不可用布局边界。
     *
     * @return 不可用布局边界
     */
    public static DocumentElementBounds unavailable() {
        return UNAVAILABLE;
    }

    /**
     * 判断布局边界是否来自已挂载运行时。
     *
     * @return 是否可用
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 返回左侧文档坐标。
     *
     * @return 左侧文档坐标
     */
    public int getLeft() {
        return left;
    }

    /**
     * 返回顶部文档坐标。
     *
     * @return 顶部文档坐标
     */
    public int getTop() {
        return top;
    }

    /**
     * 返回宽度。
     *
     * @return 宽度
     */
    public int getWidth() {
        return width;
    }

    /**
     * 返回高度。
     *
     * @return 高度
     */
    public int getHeight() {
        return height;
    }

    /**
     * 返回内容区左侧文档坐标。
     *
     * @return 内容区左侧文档坐标
     */
    public int getContentLeft() {
        return contentLeft;
    }

    /**
     * 返回内容区顶部文档坐标。
     *
     * @return 内容区顶部文档坐标
     */
    public int getContentTop() {
        return contentTop;
    }

    /**
     * 返回内容区宽度。
     *
     * @return 内容区宽度
     */
    public int getContentWidth() {
        return contentWidth;
    }

    /**
     * 返回内容区高度。
     *
     * @return 内容区高度
     */
    public int getContentHeight() {
        return contentHeight;
    }
}
