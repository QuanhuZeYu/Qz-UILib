package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.document.DocumentPageWidget;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 页面 authoring 所需的最小页面壳契约。
 *
 * <p>该接口只在 `ui.screen` 包内使用，用于收敛 controller
 * 对具体 `DocumentPageWidget` 的直接依赖，同时保留当前页面壳参数与运行时度量读取方式。</p>
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

    /**
     * 将真实页面壳包装为内部 authoring seam。
     *
     * @param documentPage 真实文档页面壳
     * @return 包装后的页面壳 contract
     */
    static DocumentPageAuthoringSurface adapt(DocumentPageWidget documentPage) {
        return new DocumentPageWidgetAuthoringAdapter(documentPage);
    }

    /**
     * `DocumentPageWidget` 的内部薄适配器。
     */
    final class DocumentPageWidgetAuthoringAdapter implements DocumentPageAuthoringSurface {

        private final DocumentPageWidget documentPage;

        private DocumentPageWidgetAuthoringAdapter(DocumentPageWidget documentPage) {
            this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        }

        @Override
        public DocumentPageAuthoringSurface addBlock(Widget child) {
            documentPage.addBlock(child);
            return this;
        }

        @Override
        public DocumentPageAuthoringSurface setContentWidthRange(int minContentWidth, int maxContentWidth) {
            documentPage.setContentWidthRange(minContentWidth, maxContentWidth);
            return this;
        }

        @Override
        public DocumentPageAuthoringSurface setMinContentHeight(int minContentHeight) {
            documentPage.setMinContentHeight(minContentHeight);
            return this;
        }

        @Override
        public DocumentPageAuthoringSurface setViewportFillRatio(float maxViewportFillWidth, float maxViewportFillHeight) {
            documentPage.setViewportFillRatio(maxViewportFillWidth, maxViewportFillHeight);
            return this;
        }

        @Override
        public int getWidth() {
            return documentPage.getWidth();
        }

        @Override
        public int getHeight() {
            return documentPage.getHeight();
        }

        @Override
        public int getScrollOffset() {
            return documentPage.getScrollOffset();
        }

        @Override
        public int getMaxScrollOffset() {
            return documentPage.getMaxScrollOffset();
        }

        @Override
        public int getVisibleContentWidth() {
            return documentPage.getVisibleContentWidth();
        }

        @Override
        public int getVisibleContentHeight() {
            return documentPage.getVisibleContentHeight();
        }

        @Override
        public int getContentWidth() {
            return documentPage.getContentWidth();
        }

        @Override
        public int getContentHeight() {
            return documentPage.getContentHeight();
        }
    }
}
