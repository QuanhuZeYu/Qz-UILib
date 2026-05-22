package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * HTML-like 标签页内容构建器。
 */
@FunctionalInterface
public interface DocumentTabContentBuilder {

    /**
     * 构建标签页内容。
     *
     * @param panel 当前标签页内容容器
     * @param document 所属 HTML-like 文档
     */
    void build(ElementNode panel, UiDocument document);
}
