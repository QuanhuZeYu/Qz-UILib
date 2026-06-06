package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;

/**
 * HTML-like 元素布局盒。
 *
 * <p>当前初版表达元素级 block/flex layout、直接文本子节点的多行布局结果与 inline fragment 几何；
 * 完整 inline formatting 会在后续阶段扩展。</p>
 */
public final class DocumentLayoutBox {

    private final ElementNode element;
    private final ComputedStyle computedStyle;
    private final List<DocumentLayoutBox> children;
    private final List<DocumentLayoutTextRun> textRuns;
    private final List<DocumentLayoutInlineFragment> inlineFragments;
    private final DocumentLayoutEdges margin;
    private final DocumentLayoutEdges border;
    private final DocumentLayoutEdges padding;
    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private final int positionOffsetX;
    private final int positionOffsetY;
    private final int resolvedTopInset;
    private final int resolvedRightInset;
    private final int resolvedBottomInset;
    private final int resolvedLeftInset;
    private final int layoutMutationVersion;
    private final int subtreeLayoutMutationVersion;
    private final int layoutTextMeasureEpoch;
    private final int layoutContainingLeft;
    private final int layoutFlowTop;
    private final int layoutContainingWidth;
    private final int layoutContainingHeight;
    private final int layoutForcedContentWidth;
    private final int layoutForcedContentHeight;
    private int layoutPassReusedSubtreeCount;

    DocumentLayoutBox(ElementNode element, ComputedStyle computedStyle, List<DocumentLayoutBox> children,
            List<DocumentLayoutTextRun> textRuns, List<DocumentLayoutInlineFragment> inlineFragments,
            DocumentLayoutEdges margin, DocumentLayoutEdges border, DocumentLayoutEdges padding, int left, int top,
            int width, int height, int positionOffsetX, int positionOffsetY, int resolvedTopInset,
            int resolvedRightInset, int resolvedBottomInset, int resolvedLeftInset) {
        this(element, computedStyle, children, textRuns, inlineFragments, margin, border, padding, left, top, width,
                height, positionOffsetX, positionOffsetY, resolvedTopInset, resolvedRightInset, resolvedBottomInset,
                resolvedLeftInset, element.__getLayoutMutationVersion(), element.__getSubtreeLayoutMutationVersion(),
                -1, DocumentLayoutEngine.AUTO_SIZE, DocumentLayoutEngine.AUTO_SIZE, DocumentLayoutEngine.AUTO_SIZE,
                DocumentLayoutEngine.AUTO_SIZE, DocumentLayoutEngine.AUTO_SIZE, DocumentLayoutEngine.AUTO_SIZE);
    }

    DocumentLayoutBox(ElementNode element, ComputedStyle computedStyle, List<DocumentLayoutBox> children,
            List<DocumentLayoutTextRun> textRuns, List<DocumentLayoutInlineFragment> inlineFragments,
            DocumentLayoutEdges margin, DocumentLayoutEdges border, DocumentLayoutEdges padding, int left, int top,
            int width, int height, int positionOffsetX, int positionOffsetY, int resolvedTopInset,
            int resolvedRightInset, int resolvedBottomInset, int resolvedLeftInset, int layoutMutationVersion,
            int subtreeLayoutMutationVersion, int layoutTextMeasureEpoch, int layoutContainingLeft, int layoutFlowTop,
            int layoutContainingWidth, int layoutContainingHeight, int layoutForcedContentWidth,
            int layoutForcedContentHeight) {
        this.element = Objects.requireNonNull(element, "element");
        this.computedStyle = Objects.requireNonNull(computedStyle, "computedStyle");
        this.children = Collections.unmodifiableList(Objects.requireNonNull(children, "children"));
        this.textRuns = Collections.unmodifiableList(Objects.requireNonNull(textRuns, "textRuns"));
        this.inlineFragments = Collections.unmodifiableList(Objects.requireNonNull(inlineFragments,
                "inlineFragments"));
        this.margin = Objects.requireNonNull(margin, "margin");
        this.border = Objects.requireNonNull(border, "border");
        this.padding = Objects.requireNonNull(padding, "padding");
        this.left = left;
        this.top = top;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.positionOffsetX = positionOffsetX;
        this.positionOffsetY = positionOffsetY;
        this.resolvedTopInset = resolvedTopInset;
        this.resolvedRightInset = resolvedRightInset;
        this.resolvedBottomInset = resolvedBottomInset;
        this.resolvedLeftInset = resolvedLeftInset;
        this.layoutMutationVersion = layoutMutationVersion;
        this.subtreeLayoutMutationVersion = subtreeLayoutMutationVersion;
        this.layoutTextMeasureEpoch = layoutTextMeasureEpoch;
        this.layoutContainingLeft = layoutContainingLeft;
        this.layoutFlowTop = layoutFlowTop;
        this.layoutContainingWidth = layoutContainingWidth;
        this.layoutContainingHeight = layoutContainingHeight;
        this.layoutForcedContentWidth = layoutForcedContentWidth;
        this.layoutForcedContentHeight = layoutForcedContentHeight;
    }

    public ElementNode getElement() {
        return element;
    }

    public ComputedStyle getComputedStyle() {
        return computedStyle;
    }

    /**
     * 复用当前布局几何，仅刷新盒树上的 computed style。
     *
     * <p>该方法只用于 paint-only style 变更后的绘制刷新，不会重新测量文本或重新计算盒几何。</p>
     *
     * @return 刷新样式后的布局盒树
     */
    public DocumentLayoutBox refreshComputedStyles() {
        List<DocumentLayoutBox> refreshedChildren = new ArrayList<DocumentLayoutBox>();
        for (DocumentLayoutBox child : children) {
            refreshedChildren.add(child.refreshComputedStyles());
        }
        ComputedStyle refreshedStyle = FlexLayoutHelper.ANONYMOUS_FLEX_ITEM_TAG.equals(element.getTagName())
                ? computedStyle : UiStyleResolver.compute(element);
        return new DocumentLayoutBox(element, refreshedStyle, refreshedChildren, textRuns, inlineFragments, margin,
                border, padding, left, top, width, height, positionOffsetX, positionOffsetY, resolvedTopInset,
                resolvedRightInset, resolvedBottomInset, resolvedLeftInset, layoutMutationVersion,
                subtreeLayoutMutationVersion, layoutTextMeasureEpoch, layoutContainingLeft, layoutFlowTop,
                layoutContainingWidth, layoutContainingHeight, layoutForcedContentWidth, layoutForcedContentHeight);
    }

    DocumentLayoutBox translatedTo(int nextLeft, int nextTop) {
        return translated(nextLeft - left, nextTop - top);
    }

    private DocumentLayoutBox translated(int deltaX, int deltaY) {
        if (deltaX == 0 && deltaY == 0) {
            return this;
        }
        List<DocumentLayoutBox> translatedChildren = new ArrayList<DocumentLayoutBox>(children.size());
        for (DocumentLayoutBox child : children) {
            translatedChildren.add(child.translated(deltaX, deltaY));
        }
        List<DocumentLayoutTextRun> translatedTextRuns = new ArrayList<DocumentLayoutTextRun>(textRuns.size());
        for (DocumentLayoutTextRun textRun : textRuns) {
            translatedTextRuns.add(textRun.translated(deltaX, deltaY));
        }
        List<DocumentLayoutInlineFragment> translatedInlineFragments =
                new ArrayList<DocumentLayoutInlineFragment>(inlineFragments.size());
        for (DocumentLayoutInlineFragment inlineFragment : inlineFragments) {
            translatedInlineFragments.add(inlineFragment.translated(deltaX, deltaY));
        }
        return new DocumentLayoutBox(element, computedStyle, translatedChildren, translatedTextRuns,
                translatedInlineFragments, margin, border, padding, left + deltaX, top + deltaY, width, height,
                positionOffsetX, positionOffsetY, resolvedTopInset, resolvedRightInset, resolvedBottomInset,
                resolvedLeftInset, layoutMutationVersion, subtreeLayoutMutationVersion, layoutTextMeasureEpoch,
                layoutContainingLeft + deltaX, layoutFlowTop + deltaY, layoutContainingWidth, layoutContainingHeight,
                layoutForcedContentWidth, layoutForcedContentHeight);
    }

    boolean containsOutOfFlowPositionedBox() {
        if (computedStyle.getPosition() == UiPosition.ABSOLUTE || computedStyle.getPosition() == UiPosition.FIXED) {
            return true;
        }
        for (DocumentLayoutBox child : children) {
            if (child.containsOutOfFlowPositionedBox()) {
                return true;
            }
        }
        return false;
    }

    void setLayoutPassReusedSubtreeCountForDiagnostics(int reusedSubtreeCount) {
        this.layoutPassReusedSubtreeCount = Math.max(0, reusedSubtreeCount);
    }

    /**
     * 返回生成当前根布局盒的布局 pass 中复用的子树数量。
     *
     * @return 复用子树数量
     */
    public int getLayoutPassReusedSubtreeCountForDiagnostics() {
        return layoutPassReusedSubtreeCount;
    }

    public List<DocumentLayoutBox> getChildren() {
        return children;
    }

    /**
     * 返回按同级 stacking 顺序排序后的子盒列表。
     *
     * <p>当前实现负 z-index、普通流、positioned auto/0、正 z-index 四个阶段；相同阶段与相同层级保留文档顺序。</p>
     *
     * @return 子盒 stacking 顺序列表
     */
    public List<DocumentLayoutBox> getChildrenInStackingOrder() {
        List<DocumentLayoutBox> orderedChildren = new ArrayList<DocumentLayoutBox>();
        appendChildrenInStackingPhase(orderedChildren, DocumentStackingPhase.NEGATIVE_POSITIONED);
        appendChildrenInStackingPhase(orderedChildren, DocumentStackingPhase.NORMAL_FLOW);
        appendChildrenInStackingPhase(orderedChildren, DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO);
        appendChildrenInStackingPhase(orderedChildren, DocumentStackingPhase.POSITIVE_POSITIONED);
        return Collections.unmodifiableList(orderedChildren);
    }

    /**
     * 返回指定 stacking 阶段内的子盒列表。
     *
     * @param phase stacking 阶段
     * @return 子盒列表
     */
    public List<DocumentLayoutBox> getChildrenInStackingPhase(DocumentStackingPhase phase) {
        List<DocumentLayoutBox> phaseChildren = new ArrayList<DocumentLayoutBox>();
        appendChildrenInStackingPhase(phaseChildren, Objects.requireNonNull(phase, "phase"));
        return Collections.unmodifiableList(phaseChildren);
    }

    /**
     * 返回当前布局盒在最近 stacking context 中所属的绘制阶段。
     *
     * @return stacking 阶段
     */
    public DocumentStackingPhase getStackingPhase() {
        return getStackingPhase(this);
    }

    /**
     * 返回当前布局盒在 stacking phase 内用于排序的 z-index 值。
     *
     * @return stacking z-index；auto 按 0 处理
     */
    public int getStackingZIndex() {
        return getStackingZIndex(this);
    }

    /**
     * 判断当前盒是否会建立独立 stacking context。
     *
     * <p>该判断只基于布局时的 computed style，动画中的临时 opacity context 由绘制阶段按当前时间另行判断。</p>
     *
     * @return 是否建立独立 stacking context
     */
    public boolean createsStackingContext() {
        return DocumentEffectChain.resolve(this).createsStackingContext();
    }

    /**
     * 返回当前元素直接文本子节点产生的布局文本行。
     *
     * @return 文本行列表
     */
    public List<DocumentLayoutTextRun> getTextRuns() {
        return textRuns;
    }

    /**
     * 返回 inline 元素按行形成的 fragment 几何。
     *
     * @return inline fragment 列表
     */
    public List<DocumentLayoutInlineFragment> getInlineFragments() {
        return inlineFragments;
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

    /**
     * 返回布局阶段解析后的 `top` 值。
     *
     * @return 已解析的 `top` 像素值；auto 时返回 0
     */
    public int getResolvedTopInset() {
        return resolvedTopInset;
    }

    /**
     * 返回布局阶段解析后的 `right` 值。
     *
     * @return 已解析的 `right` 像素值；auto 时返回 0
     */
    public int getResolvedRightInset() {
        return resolvedRightInset;
    }

    /**
     * 返回布局阶段解析后的 `bottom` 值。
     *
     * @return 已解析的 `bottom` 像素值；auto 时返回 0
     */
    public int getResolvedBottomInset() {
        return resolvedBottomInset;
    }

    /**
     * 返回布局阶段解析后的 `left` 值。
     *
     * @return 已解析的 `left` 像素值；auto 时返回 0
     */
    public int getResolvedLeftInset() {
        return resolvedLeftInset;
    }

    int getLayoutMutationVersion() {
        return layoutMutationVersion;
    }

    int getSubtreeLayoutMutationVersion() {
        return subtreeLayoutMutationVersion;
    }

    int getLayoutTextMeasureEpoch() {
        return layoutTextMeasureEpoch;
    }

    int getLayoutContainingLeft() {
        return layoutContainingLeft;
    }

    int getLayoutFlowTop() {
        return layoutFlowTop;
    }

    int getLayoutContainingWidth() {
        return layoutContainingWidth;
    }

    int getLayoutContainingHeight() {
        return layoutContainingHeight;
    }

    int getLayoutForcedContentWidth() {
        return layoutForcedContentWidth;
    }

    int getLayoutForcedContentHeight() {
        return layoutForcedContentHeight;
    }

    /**
     * 判断当前布局盒是否是 fixed 定位元素。
     *
     * @return 是否为 fixed 定位盒
     */
    public boolean isFixedPositioned() {
        return computedStyle.getPosition() == UiPosition.FIXED;
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

    private void appendChildrenInStackingPhase(List<DocumentLayoutBox> target, DocumentStackingPhase phase) {
        List<DocumentLayoutBox> phaseChildren = new ArrayList<DocumentLayoutBox>();
        for (DocumentLayoutBox child : children) {
            if (getStackingPhase(child) == phase) {
                phaseChildren.add(child);
            }
        }
        if (phase == DocumentStackingPhase.NEGATIVE_POSITIONED
                || phase == DocumentStackingPhase.POSITIVE_POSITIONED) {
            Collections.sort(phaseChildren, new Comparator<DocumentLayoutBox>() {
                @Override
                public int compare(DocumentLayoutBox first, DocumentLayoutBox second) {
                    return Integer.compare(getStackingZIndex(first), getStackingZIndex(second));
                }
            });
        }
        target.addAll(phaseChildren);
    }

    private static DocumentStackingPhase getStackingPhase(DocumentLayoutBox box) {
        ComputedStyle style = box.getComputedStyle();
        if (style.getPosition() == UiPosition.STATIC) {
            return DocumentStackingPhase.NORMAL_FLOW;
        }
        Integer zIndex = style.getZIndex();
        if (zIndex == null || zIndex.intValue() == 0) {
            return DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO;
        }
        return zIndex.intValue() < 0 ? DocumentStackingPhase.NEGATIVE_POSITIONED
                : DocumentStackingPhase.POSITIVE_POSITIONED;
    }

    private static int getStackingZIndex(DocumentLayoutBox box) {
        ComputedStyle style = box.getComputedStyle();
        if (style.getZIndex() == null) {
            return 0;
        }
        return style.getZIndex().intValue();
    }
}
