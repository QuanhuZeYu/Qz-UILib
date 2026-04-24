package club.heiqi.uilib.ui.layout;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.ComputedStyle;

/**
 * HTML-like 元素布局盒。
 *
 * <p>当前初版只表达元素级 block layout 结果；文本 inline box 与 paint order 会在后续阶段扩展。</p>
 */
public final class DocumentLayoutBox {

    private final ElementNode element;
    private final ComputedStyle computedStyle;
    private final List<DocumentLayoutBox> children;
    private final DocumentLayoutEdges margin;
    private final DocumentLayoutEdges border;
    private final DocumentLayoutEdges padding;
    private final int left;
    private final int top;
    private final int width;
    private final int height;

    DocumentLayoutBox(ElementNode element, ComputedStyle computedStyle, List<DocumentLayoutBox> children,
            DocumentLayoutEdges margin, DocumentLayoutEdges border, DocumentLayoutEdges padding, int left, int top,
            int width, int height) {
        this.element = Objects.requireNonNull(element, "element");
        this.computedStyle = Objects.requireNonNull(computedStyle, "computedStyle");
        this.children = Collections.unmodifiableList(Objects.requireNonNull(children, "children"));
        this.margin = Objects.requireNonNull(margin, "margin");
        this.border = Objects.requireNonNull(border, "border");
        this.padding = Objects.requireNonNull(padding, "padding");
        this.left = left;
        this.top = top;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public ElementNode getElement() {
        return element;
    }

    public ComputedStyle getComputedStyle() {
        return computedStyle;
    }

    public List<DocumentLayoutBox> getChildren() {
        return children;
    }

    public DocumentLayoutEdges getMargin() {
        return margin;
    }

    public DocumentLayoutEdges getBorder() {
        return border;
    }

    public DocumentLayoutEdges getPadding() {
        return padding;
    }

    /**
     * 返回 border box 左侧坐标。
     *
     * @return border box 左侧
     */
    public int getLeft() {
        return left;
    }

    /**
     * 返回 border box 顶部坐标。
     *
     * @return border box 顶部
     */
    public int getTop() {
        return top;
    }

    /**
     * 返回 border box 宽度。
     *
     * @return border box 宽度
     */
    public int getWidth() {
        return width;
    }

    /**
     * 返回 border box 高度。
     *
     * @return border box 高度
     */
    public int getHeight() {
        return height;
    }

    public int getRight() {
        return left + width;
    }

    public int getBottom() {
        return top + height;
    }

    public int getContentLeft() {
        return left + border.getLeft() + padding.getLeft();
    }

    public int getContentTop() {
        return top + border.getTop() + padding.getTop();
    }

    public int getContentWidth() {
        return Math.max(0, width - border.getHorizontal() - padding.getHorizontal());
    }

    public int getContentHeight() {
        return Math.max(0, height - border.getVertical() - padding.getVertical());
    }

    public int getMarginBoxLeft() {
        return left - margin.getLeft();
    }

    public int getMarginBoxTop() {
        return top - margin.getTop();
    }

    public int getMarginBoxRight() {
        return getRight() + margin.getRight();
    }

    public int getMarginBoxBottom() {
        return getBottom() + margin.getBottom();
    }
}
