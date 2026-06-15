package club.heiqi.uilib.ui.layout;

import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.BoxContext;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.RootEntry;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.StackingContextResolver;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.TraversalEntry;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.VisualScene;
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
        return hitTest(rootBox, java.util.Collections.<DocumentLayoutBox>emptyList(), scrollState, documentX,
                documentY, currentTimeNanos, animationTimeline);
    }

    /**
     * 在普通文档树与 top-layer 场景中查找命中的最深元素。
     *
     * @param rootBox 普通文档根盒
     * @param topLayerBoxes top-layer 根盒；后面的盒位于更上层
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线；为 null 时只使用 computed style
     * @return 命中的最深元素；未命中时返回 null
     */
    public static ElementNode hitTest(DocumentLayoutBox rootBox, List<DocumentLayoutBox> topLayerBoxes,
            DocumentScrollState scrollState, int documentX, int documentY, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        Objects.requireNonNull(rootBox, "rootBox");
        StackingContextResolver resolver = createStackingContextResolver(currentTimeNanos, animationTimeline);
        VisualScene scene = DocumentVisualTraversal.resolveVisualScene(rootBox, topLayerBoxes, scrollState,
                currentTimeNanos, animationTimeline);
        List<RootEntry> rootEntries = scene.getRootEntries();
        for (int index = rootEntries.size() - 1; index >= 0; index--) {
            ElementNode hit = hitTestBox(rootEntries.get(index).getRootContext(), scrollState, documentX, documentY,
                    true, currentTimeNanos, animationTimeline, resolver);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * 在指定偏移下对某棵子树布局盒执行命中测试。
     *
     * @param rootBox 子树根布局盒
     * @param scrollState 滚动状态
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @param offsetX 根布局盒相对文档原点的 X 偏移
     * @param offsetY 根布局盒相对文档原点的 Y 偏移
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线
     * @return 命中的最深元素；未命中时返回 null
     */
    public static ElementNode hitTest(DocumentLayoutBox rootBox, DocumentScrollState scrollState, int documentX,
            int documentY, int offsetX, int offsetY, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        Objects.requireNonNull(rootBox, "rootBox");
        StackingContextResolver resolver = createStackingContextResolver(currentTimeNanos, animationTimeline);
        return hitTestBox(DocumentVisualTraversal.resolveBoxContext(rootBox, scrollState, offsetX, offsetY,
                DocumentStickyPositioning.rootContext(), currentTimeNanos, animationTimeline), scrollState, documentX, documentY, true,
                currentTimeNanos, animationTimeline, resolver);
    }

    private static ElementNode hitTestBox(BoxContext boxContext, DocumentScrollState scrollState, int documentX,
            int documentY, boolean searchStackingContext, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline, StackingContextResolver resolver) {
        return hitTestBox(boxContext, scrollState, (float) documentX, (float) documentY, searchStackingContext,
                currentTimeNanos, animationTimeline, resolver);
    }

    private static ElementNode hitTestBox(BoxContext boxContext, DocumentScrollState scrollState, float documentX,
            float documentY, boolean searchStackingContext, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline, StackingContextResolver resolver) {
        DocumentLayoutBox box = boxContext.getBox();
        if (isHitTestSubtreeSuppressed(box.getElement())) {
            return null;
        }
        int boxOffsetX = boxContext.getBoxOffsetX();
        int boxOffsetY = boxContext.getBoxOffsetY();
        UiTransform.Point inversePoint = DocumentVisualHitTransforms.inverseTransformPoint(box, boxOffsetX,
                boxOffsetY, documentX, documentY, currentTimeNanos, animationTimeline);
        if (inversePoint == null) {
            return null;
        }
        float hitX = inversePoint.getX();
        float hitY = inversePoint.getY();
        if (!DocumentVisualTraversal.isPointInsideClipChain(boxContext, hitX, hitY)) {
            return null;
        }
        // #26 修复：border-radius 参与命中测试
        UiBorderRadiusResolver.ResolvedCornerRadii borderRadii = resolveBorderRadii(box);
        boolean insideBorderBox = containsInRoundedRect(hitX, hitY,
                box.getLeft() + boxOffsetX, box.getTop() + boxOffsetY,
                box.getRight() + boxOffsetX, box.getBottom() + boxOffsetY, borderRadii);
        if (DocumentVisualTraversal.canReachChildren(boxContext, hitX, hitY)) {
            ElementNode childHit = searchStackingContext
                    ? hitStackingContextChildren(boxContext, scrollState, hitX, hitY, currentTimeNanos,
                            animationTimeline, resolver)
                    : hitNormalFlowChildren(boxContext, scrollState, hitX, hitY, currentTimeNanos,
                            animationTimeline, resolver);
            if (childHit != null) {
                return childHit;
            }
            ElementNode inlineTextHit = hitTextRuns(box, hitX, hitY, boxContext.getChildOffsetX(),
                    boxContext.getChildOffsetY());
            if (inlineTextHit != null) {
                return inlineTextHit;
            }
            ElementNode inlineFragmentHit = hitInlineFragments(box, hitX, hitY, boxContext.getChildOffsetX(),
                    boxContext.getChildOffsetY());
            if (inlineFragmentHit != null) {
                return inlineFragmentHit;
            }
        }
        ComputedStyle boxStyle = box.getComputedStyle();
        return insideBorderBox && isSelfHitTestVisible(box.getElement(), boxStyle)
                && isPointerEventsEnabled(box.getElement(), boxStyle)
                ? resolveAuthorFacingElement(box.getElement()) : null;
    }

    private static ElementNode hitStackingContextChildren(BoxContext contextRootContext,
            DocumentScrollState scrollState, float documentX, float documentY, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline, StackingContextResolver resolver) {
        ElementNode hit = hitStackingPhaseItems(contextRootContext, scrollState, documentX, documentY,
                DocumentStackingPhase.POSITIVE_POSITIONED, currentTimeNanos, animationTimeline, resolver);
        if (hit != null) {
            return hit;
        }
        hit = hitStackingPhaseItems(contextRootContext, scrollState, documentX, documentY,
                DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO, currentTimeNanos, animationTimeline, resolver);
        if (hit != null) {
            return hit;
        }
        hit = hitNormalFlowChildren(contextRootContext, scrollState, documentX, documentY, currentTimeNanos,
                animationTimeline, resolver);
        if (hit != null) {
            return hit;
        }
        return hitStackingPhaseItems(contextRootContext, scrollState, documentX, documentY,
                DocumentStackingPhase.NEGATIVE_POSITIONED, currentTimeNanos, animationTimeline, resolver);
    }

    private static ElementNode hitNormalFlowChildren(BoxContext contextRootContext, DocumentScrollState scrollState,
            float documentX, float documentY, long currentTimeNanos, DocumentAnimationTimeline animationTimeline,
            StackingContextResolver resolver) {
        List<TraversalEntry> entries = DocumentVisualTraversal.getNormalFlowEntries(contextRootContext.getBox(),
                contextRootContext, scrollState, resolver, true);
        for (TraversalEntry entry : entries) {
            ElementNode hit = hitTestBox(entry.getBoxContext(), scrollState, documentX, documentY,
                    entry.isStackingContext(), currentTimeNanos, animationTimeline, resolver);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static ElementNode hitStackingPhaseItems(BoxContext contextRootContext,
            DocumentScrollState scrollState, float documentX, float documentY, DocumentStackingPhase phase,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline, StackingContextResolver resolver) {
        List<TraversalEntry> items = DocumentVisualTraversal.collectStackingPhaseEntries(contextRootContext.getBox(),
                contextRootContext, scrollState, resolver, phase);
        for (int index = items.size() - 1; index >= 0; index--) {
            TraversalEntry item = items.get(index);
            ElementNode hit = hitTestBox(item.getBoxContext(), scrollState, documentX, documentY,
                    item.isStackingContext(), currentTimeNanos, animationTimeline, resolver);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static ElementNode hitTextRuns(DocumentLayoutBox box, float documentX, float documentY, int offsetX,
            int offsetY) {
        List<DocumentLayoutTextRun> textRuns = box.getTextRuns();
        ElementNode boxElement = box.getElement();
        ComputedStyle boxStyle = box.getComputedStyle();
        for (int index = textRuns.size() - 1; index >= 0; index--) {
            DocumentLayoutTextRun textRun = textRuns.get(index);
            ElementNode ownerElement = textRun.getOwnerElement();
            // 直接文本子节点的 ownerElement 即当前盒元素，复用布局期缓存样式避免重复级联计算。
            if (ownerElement == boxElement) {
                if (isSelfHitTestSuppressed(ownerElement, boxStyle) || !isPointerEventsEnabled(ownerElement, boxStyle)) {
                    continue;
                }
            } else if (isSelfHitTestSuppressed(ownerElement) || !isPointerEventsEnabled(ownerElement)) {
                continue;
            }
            if (containsInRect(documentX, documentY, textRun.getLeft() + offsetX, textRun.getTop() + offsetY,
                    textRun.getRight() + offsetX, textRun.getBottom() + offsetY)) {
                return resolveAuthorFacingElement(ownerElement);
            }
        }
        return null;
    }

    private static ElementNode hitInlineFragments(DocumentLayoutBox box, float documentX, float documentY, int offsetX,
            int offsetY) {
        List<DocumentLayoutInlineFragment> inlineFragments = box.getInlineFragments();
        ElementNode boxElement = box.getElement();
        ComputedStyle boxStyle = box.getComputedStyle();
        for (int index = inlineFragments.size() - 1; index >= 0; index--) {
            DocumentLayoutInlineFragment inlineFragment = inlineFragments.get(index);
            ElementNode ownerElement = inlineFragment.getOwnerElement();
            // 内联片段 ownerElement 与当前盒元素一致时复用布局期缓存样式避免重复级联计算。
            if (ownerElement == boxElement) {
                if (isSelfHitTestSuppressed(ownerElement, boxStyle) || !isPointerEventsEnabled(ownerElement, boxStyle)) {
                    continue;
                }
            } else if (isSelfHitTestSuppressed(ownerElement) || !isPointerEventsEnabled(ownerElement)) {
                continue;
            }
            if (containsInRect(documentX, documentY, inlineFragment.getLeft() + offsetX,
                    inlineFragment.getTop() + offsetY, inlineFragment.getRight() + offsetX,
                    inlineFragment.getBottom() + offsetY)) {
                return resolveAuthorFacingElement(ownerElement);
            }
        }
        return null;
    }


    private static StackingContextResolver createStackingContextResolver(final long currentTimeNanos,
            final DocumentAnimationTimeline animationTimeline) {
        return new StackingContextResolver() {
            @Override
            public boolean createsStackingContext(DocumentLayoutBox box) {
                return DocumentVisualTraversal.createsRuntimeStackingContext(box, currentTimeNanos,
                        animationTimeline);
            }
        };
    }

    /**
     * 判断元素整棵命中子树是否被显式抑制。
     *
     * @param element 元素
     * @return 是否抑制整棵子树命中
     */
    public static boolean isHitTestSubtreeSuppressed(ElementNode element) {
        if (element == null) {
            return false;
        }
        if ("true".equals(element.getAttribute("data-hit-test-hidden"))) {
            return true;
        }
        // 显式穿透区域按整棵子树透明处理，允许同一宿主内继续命中视觉下方内容。
        if ("true".equals(element.getAttribute("data-hit-test-passthrough"))) {
            return true;
        }
        return false;
    }

    /**
     * 判断元素自身是否不应成为命中目标。
     *
     * @param element 元素
     * @return 元素自身是否被抑制
     */
    public static boolean isSelfHitTestSuppressed(ElementNode element) {
        return isHitTestSubtreeSuppressed(element) || isVisibilityHidden(element);
    }

    /**
     * 使用布局期缓存样式判断元素自身是否不应成为命中目标。
     *
     * <p>命中遍历热路径上调用方已持有该元素布局期解析的 {@link ComputedStyle}（{@code box.getComputedStyle()}），
     * 复用它可避免在每个盒上重复执行无缓存的 {@link UiStyleResolver#compute(ElementNode)} 全量级联。
     * {@code visibility} 标记为 PAINT 影响，变化时会触发盒树 {@code refreshComputedStyles}，故缓存样式与
     * 即时计算语义一致。</p>
     *
     * @param element 元素
     * @param computedStyle 该元素布局期缓存样式
     * @return 元素自身是否被抑制
     */
    public static boolean isSelfHitTestSuppressed(ElementNode element, ComputedStyle computedStyle) {
        return isHitTestSubtreeSuppressed(element) || isVisibilityHidden(computedStyle);
    }

    /**
     * 判断元素自身是否可成为命中目标。
     *
     * @param element 元素
     * @return 元素自身是否可命中
     */
    public static boolean isSelfHitTestVisible(ElementNode element) {
        return !isSelfHitTestSuppressed(element);
    }

    /**
     * 使用布局期缓存样式判断元素自身是否可成为命中目标。
     *
     * @param element 元素
     * @param computedStyle 该元素布局期缓存样式
     * @return 元素自身是否可命中
     */
    public static boolean isSelfHitTestVisible(ElementNode element, ComputedStyle computedStyle) {
        return !isSelfHitTestSuppressed(element, computedStyle);
    }

    private static boolean isVisibilityHidden(ElementNode element) {
        if (element == null) {
            return false;
        }
        return isVisibilityHidden(UiStyleResolver.compute(element));
    }

    private static boolean isVisibilityHidden(ComputedStyle style) {
        return style != null && style.getVisibility() == UiVisibility.HIDDEN;
    }

    /**
     * 判断元素当前 computed pointer-events 是否允许命中。
     *
     * @param element 元素
     * @return 是否允许命中
     */
    public static boolean isPointerEventsEnabled(ElementNode element) {
        if (element == null) {
            return true;
        }
        return UiStyleResolver.compute(element).getPointerEvents() != UiPointerEvents.NONE;
    }

    /**
     * 使用布局期缓存样式判断元素当前 computed pointer-events 是否允许命中。
     *
     * <p>{@code pointer-events} 标记为 PAINT 影响，变化时会触发盒树 {@code refreshComputedStyles}，故复用
     * 布局期缓存样式与即时计算语义一致，避免命中遍历热路径上的重复级联。</p>
     *
     * @param element 元素
     * @param computedStyle 该元素布局期缓存样式
     * @return 是否允许命中
     */
    public static boolean isPointerEventsEnabled(ElementNode element, ComputedStyle computedStyle) {
        if (element == null) {
            return true;
        }
        if (computedStyle == null) {
            return isPointerEventsEnabled(element);
        }
        return computedStyle.getPointerEvents() != UiPointerEvents.NONE;
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

    /**
     * 从布局盒的 computed style 解析 border-radius 像素值（已限制上限）。
     */
    private static UiBorderRadiusResolver.ResolvedCornerRadii resolveBorderRadii(DocumentLayoutBox box) {
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            return UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0);
        }
        return UiBorderRadiusResolver.resolve(box.getComputedStyle(), box.getWidth(), box.getHeight());
    }

}
