package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.values.UiBorderRadius;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * HTML-like 布局盒命中测试引擎。
 */
public final class DocumentHitTestEngine {

    private DocumentHitTestEngine() {}

    /**
     * 在布局盒树中查找命中的最深元素。
     *
     * @param rootBox 根布局盒
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @return 命中的最深元素；未命中时返回 null
     */
    public static ElementNode hitTest(DocumentLayoutBox rootBox, DocumentScrollState scrollState, int documentX,
            int documentY) {
        return hitTest(rootBox, scrollState, documentX, documentY, 0L, null);
    }

    /**
     * 在布局盒树中查找命中的最深元素，并应用动画中的 paint-only transform。
     *
     * @param rootBox 根布局盒
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线；为 null 时只使用 computed style
     * @return 命中的最深元素；未命中时返回 null
     */
    public static ElementNode hitTest(DocumentLayoutBox rootBox, DocumentScrollState scrollState, int documentX,
            int documentY, long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        Objects.requireNonNull(rootBox, "rootBox");
        return hitTestBox(rootBox, scrollState, documentX, documentY, 0, 0, true,
                currentTimeNanos, animationTimeline,
                DocumentStickyPositioning.rootContext());
    }

    private static ElementNode hitTestBox(DocumentLayoutBox box, DocumentScrollState scrollState, int documentX,
            int documentY, int offsetX, int offsetY, boolean searchStackingContext, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline,
            DocumentStickyPositioning.StickyContext stickyContext) {
        return hitTestBox(box, scrollState, (float) documentX, (float) documentY, offsetX, offsetY,
                searchStackingContext, currentTimeNanos, animationTimeline, stickyContext);
    }

    private static ElementNode hitTestBox(DocumentLayoutBox box, DocumentScrollState scrollState, float documentX,
            float documentY, int offsetX, int offsetY, boolean searchStackingContext, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline,
            DocumentStickyPositioning.StickyContext stickyContext) {
        if (isHitTestHidden(box.getElement())) {
            return null;
        }
        int boxOffsetX = resolveBoxOffsetX(box, offsetX, stickyContext);
        int boxOffsetY = resolveBoxOffsetY(box, offsetY, stickyContext);
        UiTransform.Point inversePoint = inverseTransformPoint(box, boxOffsetX, boxOffsetY, documentX, documentY,
                currentTimeNanos, animationTimeline);
        if (inversePoint == null) {
            return null;
        }
        float hitX = inversePoint.getX();
        float hitY = inversePoint.getY();
        DocumentStickyPositioning.StickyContext childStickyContext = DocumentStickyPositioning.createChildContext(box,
                boxOffsetX, boxOffsetY, stickyContext);
        // #26 修复：border-radius 参与命中测试
        UiBorderRadiusResolver.ResolvedCornerRadii borderRadii = resolveBorderRadii(box);
        boolean insideBorderBox = containsInRoundedRect(hitX, hitY,
                box.getLeft() + boxOffsetX, box.getTop() + boxOffsetY,
                box.getRight() + boxOffsetX, box.getBottom() + boxOffsetY, borderRadii);
        if (canHitTestChildren(box, hitX, hitY, boxOffsetX, boxOffsetY)) {
            int childOffsetX = boxOffsetX - getScrollLeft(scrollState, box);
            int childOffsetY = boxOffsetY - getScrollTop(scrollState, box);
            ElementNode childHit = searchStackingContext
                    ? hitStackingContextChildren(box, scrollState, hitX, hitY, childOffsetX, childOffsetY,
                            currentTimeNanos, animationTimeline, childStickyContext)
                    : hitNormalFlowChildren(box, scrollState, hitX, hitY, childOffsetX, childOffsetY,
                            currentTimeNanos, animationTimeline, childStickyContext);
            if (childHit != null) {
                return childHit;
            }
            ElementNode inlineTextHit = hitTextRuns(box, hitX, hitY, childOffsetX, childOffsetY);
            if (inlineTextHit != null) {
                return inlineTextHit;
            }
            ElementNode inlineFragmentHit = hitInlineFragments(box, hitX, hitY, childOffsetX, childOffsetY);
            if (inlineFragmentHit != null) {
                return inlineFragmentHit;
            }
        }
        return insideBorderBox && isPointerEventsEnabled(box.getElement())
                ? resolveAuthorFacingElement(box.getElement()) : null;
    }

    private static ElementNode hitStackingContextChildren(DocumentLayoutBox contextRoot,
            DocumentScrollState scrollState, float documentX, float documentY, int childOffsetX, int childOffsetY,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline,
            DocumentStickyPositioning.StickyContext stickyContext) {
        ElementNode hit = hitStackingPhaseItems(contextRoot, scrollState, documentX, documentY, childOffsetX,
                childOffsetY, DocumentStackingPhase.POSITIVE_POSITIONED, currentTimeNanos, animationTimeline,
                stickyContext);
        if (hit != null) {
            return hit;
        }
        hit = hitStackingPhaseItems(contextRoot, scrollState, documentX, documentY, childOffsetX, childOffsetY,
                DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO, currentTimeNanos, animationTimeline, stickyContext);
        if (hit != null) {
            return hit;
        }
        hit = hitNormalFlowChildren(contextRoot, scrollState, documentX, documentY, childOffsetX, childOffsetY,
                currentTimeNanos, animationTimeline, stickyContext);
        if (hit != null) {
            return hit;
        }
        return hitStackingPhaseItems(contextRoot, scrollState, documentX, documentY, childOffsetX, childOffsetY,
                DocumentStackingPhase.NEGATIVE_POSITIONED, currentTimeNanos, animationTimeline, stickyContext);
    }

    private static ElementNode hitNormalFlowChildren(DocumentLayoutBox box, DocumentScrollState scrollState,
            float documentX, float documentY, int childOffsetX, int childOffsetY,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline,
            DocumentStickyPositioning.StickyContext stickyContext) {
        List<DocumentLayoutBox> children = box.getChildren();
        for (int index = children.size() - 1; index >= 0; index--) {
            DocumentLayoutBox child = children.get(index);
            if (child.getStackingPhase() != DocumentStackingPhase.NORMAL_FLOW) {
                continue;
            }
            boolean childStackingContext = shouldSearchAsStackingContext(child, currentTimeNanos, animationTimeline);
            ElementNode hit = hitTestBox(child, scrollState, documentX, documentY, childOffsetX, childOffsetY,
                    childStackingContext, currentTimeNanos, animationTimeline, stickyContext);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static ElementNode hitStackingPhaseItems(DocumentLayoutBox contextRoot,
            DocumentScrollState scrollState, float documentX, float documentY, int childOffsetX, int childOffsetY,
            DocumentStackingPhase phase, long currentTimeNanos, DocumentAnimationTimeline animationTimeline,
            DocumentStickyPositioning.StickyContext stickyContext) {
        List<StackingHitItem> items = new ArrayList<StackingHitItem>();
        collectStackingPhaseItems(contextRoot, items, scrollState, childOffsetX, childOffsetY, phase,
                currentTimeNanos, animationTimeline, stickyContext);
        if (phase == DocumentStackingPhase.NEGATIVE_POSITIONED
                || phase == DocumentStackingPhase.POSITIVE_POSITIONED) {
            Collections.sort(items, new Comparator<StackingHitItem>() {
                @Override
                public int compare(StackingHitItem first, StackingHitItem second) {
                    return Integer.compare(first.box.getStackingZIndex(), second.box.getStackingZIndex());
                }
            });
        }
        for (int index = items.size() - 1; index >= 0; index--) {
            StackingHitItem item = items.get(index);
            ElementNode hit = hitTestBox(item.box, scrollState, documentX, documentY, item.offsetX,
                    item.offsetY, item.searchStackingContext, currentTimeNanos, animationTimeline,
                    item.stickyContext);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static void collectStackingPhaseItems(DocumentLayoutBox currentBox, List<StackingHitItem> items,
            DocumentScrollState scrollState, int childOffsetX, int childOffsetY, DocumentStackingPhase phase,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline,
            DocumentStickyPositioning.StickyContext stickyContext) {
        for (DocumentLayoutBox child : currentBox.getChildren()) {
            boolean childStackingContext = shouldSearchAsStackingContext(child, currentTimeNanos, animationTimeline);
            if (child.getStackingPhase() == phase) {
                items.add(new StackingHitItem(child, childOffsetX, childOffsetY, childStackingContext,
                        stickyContext));
            }
            if (childStackingContext) {
                continue;
            }
            int childBoxOffsetX = resolveBoxOffsetX(child, childOffsetX, stickyContext);
            int childBoxOffsetY = resolveBoxOffsetY(child, childOffsetY, stickyContext);
            DocumentStickyPositioning.StickyContext childStickyContext = DocumentStickyPositioning.createChildContext(
                    child, childBoxOffsetX, childBoxOffsetY, stickyContext);
            int grandChildOffsetX = childBoxOffsetX - getScrollLeft(scrollState, child);
            int grandChildOffsetY = childBoxOffsetY - getScrollTop(scrollState, child);
            collectStackingPhaseItems(child, items, scrollState, grandChildOffsetX, grandChildOffsetY, phase,
                    currentTimeNanos, animationTimeline, childStickyContext);
        }
    }

    private static boolean canHitTestChildren(DocumentLayoutBox box, float documentX, float documentY, int offsetX,
            int offsetY) {
        return DocumentEffectChain.resolve(box).canReachChildrenAt(documentX, documentY, offsetX, offsetY);
    }

    private static ElementNode hitTextRuns(DocumentLayoutBox box, float documentX, float documentY, int offsetX,
            int offsetY) {
        if (isHitTestHidden(box.getElement())) {
            return null;
        }
        if (!isPointerEventsEnabled(box.getElement())) {
            return null;
        }
        List<DocumentLayoutTextRun> textRuns = box.getTextRuns();
        for (int index = textRuns.size() - 1; index >= 0; index--) {
            DocumentLayoutTextRun textRun = textRuns.get(index);
            if (containsInRect(documentX, documentY, textRun.getLeft() + offsetX, textRun.getTop() + offsetY,
                    textRun.getRight() + offsetX, textRun.getBottom() + offsetY)) {
                return resolveAuthorFacingElement(textRun.getOwnerElement());
            }
        }
        return null;
    }

    private static ElementNode hitInlineFragments(DocumentLayoutBox box, float documentX, float documentY, int offsetX,
            int offsetY) {
        if (isHitTestHidden(box.getElement())) {
            return null;
        }
        List<DocumentLayoutInlineFragment> inlineFragments = box.getInlineFragments();
        for (int index = inlineFragments.size() - 1; index >= 0; index--) {
            DocumentLayoutInlineFragment inlineFragment = inlineFragments.get(index);
            if (!isPointerEventsEnabled(inlineFragment.getOwnerElement())) {
                continue;
            }
            if (containsInRect(documentX, documentY, inlineFragment.getLeft() + offsetX,
                    inlineFragment.getTop() + offsetY, inlineFragment.getRight() + offsetX,
                    inlineFragment.getBottom() + offsetY)) {
                return resolveAuthorFacingElement(inlineFragment.getOwnerElement());
            }
        }
        return null;
    }

    private static int getScrollLeft(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollLeft(box.getElement());
    }

    private static int getScrollTop(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollTop(box.getElement());
    }

    private static int resolveBoxOffsetX(DocumentLayoutBox box, int offsetX,
            DocumentStickyPositioning.StickyContext stickyContext) {
        int baseOffsetX = box.isFixedPositioned() ? 0 : offsetX;
        int positionedOffsetX = baseOffsetX + box.getPositionOffsetX();
        return DocumentStickyPositioning.resolveOffsetX(box, positionedOffsetX, stickyContext);
    }

    private static int resolveBoxOffsetY(DocumentLayoutBox box, int offsetY,
            DocumentStickyPositioning.StickyContext stickyContext) {
        int baseOffsetY = box.isFixedPositioned() ? 0 : offsetY;
        int positionedOffsetY = baseOffsetY + box.getPositionOffsetY();
        return DocumentStickyPositioning.resolveOffsetY(box, positionedOffsetY, stickyContext);
    }

    private static boolean shouldSearchAsStackingContext(DocumentLayoutBox box, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        DocumentEffectChain effectChain = DocumentEffectChain.resolve(box);
        if (effectChain.isStackingBoundary()) {
            return true;
        }
        if (animationTimeline == null) {
            return false;
        }
        float opacity = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.OPACITY,
                box.getComputedStyle().getOpacity(), currentTimeNanos);
        return effectChain.createsPaintContext(false, opacity)
                || createsTransformStackingContext(box, currentTimeNanos, animationTimeline);
    }

    private static boolean isHitTestHidden(ElementNode element) {
        if (element == null) {
            return false;
        }
        if ("true".equals(element.getAttribute("data-hit-test-hidden"))) {
            return true;
        }
        // visibility:hidden 的元素不响应命中测试
        ComputedStyle style = UiStyleResolver.compute(element);
        return style.getVisibility() == UiVisibility.HIDDEN;
    }

    private static boolean isPointerEventsEnabled(ElementNode element) {
        if (element == null) {
            return true;
        }
        return UiStyleResolver.compute(element).getPointerEvents() != UiPointerEvents.NONE;
    }

    private static ElementNode resolveAuthorFacingElement(ElementNode element) {
        if (element == null) {
            return null;
        }
        return element.isPseudoElement() && element.getPseudoOriginElement() != null
                ? element.getPseudoOriginElement() : element;
    }

    private static boolean containsInRect(int x, int y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private static boolean containsInRect(float x, float y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    /**
     * 圆角感知命中测试。
     *
     * <p>当 borderRadius > 0 时，对四个角落进行圆弧判断；其余区域仍按矩形处理。</p>
     */
    private static boolean containsInRoundedRect(float x, float y, int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii borderRadii) {
        if (!containsInRect(x, y, left, top, right, bottom)) {
            return false;
        }
        if (borderRadii == null) {
            return true;
        }
        int topLeftRadius = Math.max(0, borderRadii.getTopLeft());
        int topRightRadius = Math.max(0, borderRadii.getTopRight());
        int bottomRightRadius = Math.max(0, borderRadii.getBottomRight());
        int bottomLeftRadius = Math.max(0, borderRadii.getBottomLeft());
        if (topLeftRadius <= 0 && topRightRadius <= 0 && bottomRightRadius <= 0 && bottomLeftRadius <= 0) {
            return true;
        }
        int cx;
        int cy;
        cx = left + topLeftRadius;
        cy = top + topLeftRadius;
        if (topLeftRadius > 0 && x < cx && y < cy) {
            return isInsideCircle(x, y, cx, cy, topLeftRadius);
        }
        cx = right - topRightRadius;
        cy = top + topRightRadius;
        if (topRightRadius > 0 && x >= cx && y < cy) {
            return isInsideCircle(x, y, cx, cy, topRightRadius);
        }
        cx = right - bottomRightRadius;
        cy = bottom - bottomRightRadius;
        if (bottomRightRadius > 0 && x >= cx && y >= cy) {
            return isInsideCircle(x, y, cx, cy, bottomRightRadius);
        }
        cx = left + bottomLeftRadius;
        cy = bottom - bottomLeftRadius;
        if (bottomLeftRadius > 0 && x < cx && y >= cy) {
            return isInsideCircle(x, y, cx, cy, bottomLeftRadius);
        }
        return true;
    }

    private static boolean isInsideCircle(float x, float y, int cx, int cy, int r) {
        double dx = x - cx;
        double dy = y - cy;
        return dx * dx + dy * dy < (double) r * r;
    }

    private static UiTransform.Point inverseTransformPoint(DocumentLayoutBox box, int boxOffsetX, int boxOffsetY,
            float documentX, float documentY, long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        UiTransform transform = resolveTransform(box, currentTimeNanos, animationTimeline);
        if (transform == null || transform.isIdentity()) {
            return new UiTransform.Point(documentX, documentY);
        }
        return transform.inverseTransformPoint(documentX, documentY, box.getLeft() + boxOffsetX,
                box.getTop() + boxOffsetY, box.getWidth(), box.getHeight());
    }

    private static UiTransform resolveTransform(DocumentLayoutBox box, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        UiTransform baseTransform = box.getComputedStyle().getTransform();
        if (baseTransform == null) {
            baseTransform = UiTransform.identity();
        }
        if (animationTimeline == null) {
            return baseTransform;
        }
        ElementNode element = box.getElement();
        float translateX = animationTimeline.resolveFloat(element, DocumentAnimationProperty.TRANSLATE_X,
                baseTransform.getTranslateX(), currentTimeNanos);
        float translateY = animationTimeline.resolveFloat(element, DocumentAnimationProperty.TRANSLATE_Y,
                baseTransform.getTranslateY(), currentTimeNanos);
        float scaleX = animationTimeline.resolveFloat(element, DocumentAnimationProperty.SCALE_X,
                baseTransform.getScaleX(), currentTimeNanos);
        float scaleY = animationTimeline.resolveFloat(element, DocumentAnimationProperty.SCALE_Y,
                baseTransform.getScaleY(), currentTimeNanos);
        float rotate = animationTimeline.resolveFloat(element, DocumentAnimationProperty.ROTATE,
                baseTransform.getRotateDegrees(), currentTimeNanos);
        return UiTransform.of(translateX, translateY, scaleX, scaleY, rotate,
                baseTransform.getOriginX(), baseTransform.getOriginY());
    }

    private static boolean createsTransformStackingContext(DocumentLayoutBox box, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        UiTransform transform = resolveTransform(box, currentTimeNanos, animationTimeline);
        return transform != null && !transform.isIdentity();
    }

    /**
     * 从布局盒的 computed style 解析 border-radius 像素值（已限制上限）。
     */
    private static UiBorderRadiusResolver.ResolvedCornerRadii resolveBorderRadii(DocumentLayoutBox box) {
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            return UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0);
        }
        return UiBorderRadiusResolver.resolve(box.getComputedStyle(), box.getWidth(), box.getHeight());
    }

    /**
     * 最近 stacking context 中可被阶段排序的命中项。
     */
    private static final class StackingHitItem {

        private final DocumentLayoutBox box;
        private final int offsetX;
        private final int offsetY;
        private final boolean searchStackingContext;
        private final DocumentStickyPositioning.StickyContext stickyContext;

        private StackingHitItem(DocumentLayoutBox box, int offsetX, int offsetY, boolean searchStackingContext,
                DocumentStickyPositioning.StickyContext stickyContext) {
            this.box = box;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.searchStackingContext = searchStackingContext;
            this.stickyContext = stickyContext;
        }
    }
}
