package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.widget.Widget;

/**
 * 页面 authoring 所需的最小挂载契约。
 *
 * <p>该接口只在 `ui.screen` 包内使用，用于收敛 controller 对具体宿主实现的直接依赖。
 * 当前 HTML-like 页面使用 direct surface，不再提供旧页面壳适配入口。</p>
 */
interface DocumentPageAuthoringSurface {

    /**
     * 追加文档块级内容。
     *
     * @param child 文档块
     * @return 当前页面壳 contract
     */
    DocumentPageAuthoringSurface addBlock(Widget child);

    /**
     * 设置内容宽度区间。
     *
     * @param minContentWidth 最小宽度
     * @param maxContentWidth 最大宽度
     * @return 当前页面壳 contract
     */
    DocumentPageAuthoringSurface setContentWidthRange(int minContentWidth, int maxContentWidth);

    /**
     * 设置最小内容高度。
     *
     * @param minContentHeight 最小内容高度
     * @return 当前页面壳 contract
     */
    DocumentPageAuthoringSurface setMinContentHeight(int minContentHeight);

    /**
     * 设置相对父视口的填充比例。
     *
     * @param maxViewportFillWidth 最大宽度占比
     * @param maxViewportFillHeight 最大高度占比
     * @return 当前页面壳 contract
     */
    DocumentPageAuthoringSurface setViewportFillRatio(float maxViewportFillWidth, float maxViewportFillHeight);

    /**
     * 返回页面壳当前宽度。
     *
     * @return 页面壳宽度
     */
    int getWidth();

    /**
     * 返回页面壳当前高度。
     *
     * @return 页面壳高度
     */
    int getHeight();

    /**
     * 返回当前滚动偏移。
     *
     * @return 当前滚动偏移
     */
    int getScrollOffset();

    /**
     * 返回最大滚动偏移。
     *
     * @return 最大滚动偏移
     */
    int getMaxScrollOffset();

    /**
     * 返回可见内容宽度。
     *
     * @return 可见内容宽度
     */
    int getVisibleContentWidth();

    /**
     * 返回可见内容高度。
     *
     * @return 可见内容高度
     */
    int getVisibleContentHeight();

    /**
     * 返回内容总宽度。
     *
     * @return 内容总宽度
     */
    int getContentWidth();

    /**
     * 返回内容总高度。
     *
     * @return 内容总高度
     */
    int getContentHeight();

}
