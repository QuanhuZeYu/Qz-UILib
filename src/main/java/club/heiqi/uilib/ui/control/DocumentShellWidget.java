package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.widget.Widget;

/**
 * 文档内容壳，作为网页语义包装层暴露页面内容区配置。
 */
public class DocumentShellWidget extends ScrollViewportWidget {

    public DocumentShellWidget() {
        setParentViewportAlignment(FrameAlign.CENTER, FrameAlign.START);
        getContent().setDocumentColumn();
    }

    /**
     * 获取文档主内容容器。
     *
     * @return 文档根容器
     */
    public DivWidget getDocument() {
        return getContent();
    }

    /**
     * 向文档根容器追加子节点。
     *
     * @param child 子组件
     * @return 当前页面壳
     */
    public DocumentShellWidget addDocumentChild(Widget child) {
        getDocument().addChild(child);
        return this;
    }

    @Override
    public DocumentShellWidget setPadding(int padding) {
        super.setPadding(padding);
        return this;
    }

    @Override
    public DocumentShellWidget setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        return this;
    }

    @Override
    public DocumentShellWidget setFillColor(int fillColor) {
        super.setFillColor(fillColor);
        return this;
    }

    @Override
    public DocumentShellWidget setBorderColor(int borderColor) {
        super.setBorderColor(borderColor);
        return this;
    }

    /**
     * 设置文档内容壳的宽度区间，语义接近网页容器的 min-width 与 max-width。
     *
     * @param minContentWidth 最小内容宽度
     * @param maxContentWidth 最大内容宽度
     * @return 当前页面
     */
    public DocumentShellWidget setContentWidthRange(int minContentWidth, int maxContentWidth) {
        super.setParentViewportWidthRange(minContentWidth, maxContentWidth);
        return this;
    }

    /**
     * 设置内容壳的最小高度保护。
     *
     * @param minContentHeight 最小内容高度
     * @return 当前页面
     */
    public DocumentShellWidget setMinContentHeight(int minContentHeight) {
        super.setParentViewportMinHeight(minContentHeight);
        return this;
    }

    /**
     * 设置内容壳相对视口的最大填充占比。
     *
     * @param maxViewportFillWidth 最大宽度占比
     * @param maxViewportFillHeight 最大高度占比
     * @return 当前页面
     */
    public DocumentShellWidget setViewportFillRatio(float maxViewportFillWidth, float maxViewportFillHeight) {
        super.setParentViewportFillRatio(maxViewportFillWidth, maxViewportFillHeight);
        return this;
    }
}
