package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiPosition;

/**
 * HTML-like 元素布局盒。
 *
 * <p>当前初版表达元素级 block/flex layout 与直接文本子节点的多行布局结果；完整 inline formatting 会在后续阶段扩展。</p>
 */
public final class DocumentLayoutBox {

    private final ElementNode element;
    private final ComputedStyle computedStyle;
    private final List<DocumentLayoutBox> children;
    private final List<DocumentLayoutTextRun> textRuns;
    private final DocumentLayoutEdges margin;
    private final DocumentLayoutEdges border;
    private final DocumentLayoutEdges padding;
    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private final int positionOffsetX;
    private final int positionOffsetY;

    DocumentLayoutBox(ElementNode element, ComputedStyle computedStyle, List<DocumentLayoutBox> children,
            List<DocumentLayoutTextRun> textRuns, DocumentLayoutEdges margin, DocumentLayoutEdges border,
            DocumentLayoutEdges padding, int left, int top, int width, int height, int positionOffsetX,
            int positionOffsetY) {
        this.element = Objects.requireNonNull(element, "element");
        this.computedStyle = Objects.requireNonNull(computedStyle, "computedStyle");
        this.children = Collections.unmodifiableList(Objects.requireNonNull(children, "children"));
        this.textRuns = Collections.unmodifiableList(Objects.requireNonNull(textRuns, "textRuns"));
        this.margin = Objects.requireNonNull(margin, "margin");
        this.border = Objects.requireNonNull(border, "border");
        this.padding = Objects.requireNonNull(padding, "padding");
        this.left = left;
        this.top = top;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.positionOffsetX = positionOffsetX;
        this.positionOffsetY = positionOffsetY;
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

    /**
     * 返回按同级 stacking 顺序排序后的子盒列表。
     *
     * <p>当前仅实现 positioned `z-index` 的稳定排序；相同层级保留文档顺序。</p>
     *
     * @return 子盒 stacking 顺序列表
     */
    public List<DocumentLayoutBox> getChildrenInStackingOrder() {
        List<DocumentLayoutBox> orderedChildren = new ArrayList<DocumentLayoutBox>(children);
        Collections.sort(orderedChildren, new Comparator<DocumentLayoutBox>() {
            @Override
            public int compare(DocumentLayoutBox first, DocumentLayoutBox second) {
                return Integer.compare(getStackingZIndex(first), getStackingZIndex(second));
            }
        });
        return Collections.unmodifiableList(orderedChildren);
    }

    /**
     * 返回当前元素直接文本子节点产生的布局文本行。
     *
     * @return 文本行列表
     */
    public List<DocumentLayoutTextRun> getTextRuns() {
        return textRuns;
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

    /**
     * 返回 relative 定位产生的视觉 X 偏移，普通流几何不受该值影响。
     *
     * @return 视觉 X 偏移
     */
    public int getPositionOffsetX() {
        return positionOffsetX;
    }

    /**
     * 返回 relative 定位产生的视觉 Y 偏移，普通流几何不受该值影响。
     *
     * @return 视觉 Y 偏移
     */
    public int getPositionOffsetY() {
        return positionOffsetY;
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

    private static int getStackingZIndex(DocumentLayoutBox box) {
        ComputedStyle style = box.getComputedStyle();
        if (style.getPosition() == UiPosition.STATIC || style.getZIndex() == null) {
            return 0;
        }
        return style.getZIndex().intValue();
    }
}
