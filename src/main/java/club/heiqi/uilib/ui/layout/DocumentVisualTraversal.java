package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.layout.DocumentStickyPositioning.StickyContext;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * HTML-like 视觉遍历辅助层。
 *
 * <p>统一封装 stacking phase 收集、fixed/sticky 偏移、scroll offset 传播与 overflow clip 可达判定，
 * 让 paint、hit-test 与 scroll 共享同一套视觉树解释规则。</p>
 */
public final class DocumentVisualTraversal {

    private DocumentVisualTraversal() {}

    /**
     * 判断布局盒是否应作为局部 stacking context 递归边界。
     */
    public interface StackingContextResolver {

        /**
         * 判断当前盒是否建立局部 stacking context。
         *
         * @param box 布局盒
         * @return 是否建立局部 stacking context
         */
        boolean createsStackingContext(DocumentLayoutBox box);
    }

    /**
     * 构建根布局盒的视觉上下文。
     *
     * @param rootBox 根布局盒
     * @param scrollState 滚动状态
     * @return 根视觉上下文
     */
    public static BoxContext resolveRootBoxContext(DocumentLayoutBox rootBox, DocumentScrollState scrollState) {
        return resolveBoxContext(rootBox, scrollState, 0, 0, DocumentStickyPositioning.rootContext(),
                Collections.<ClipContext>emptyList());
    }

    /**
     * 基于父偏移和 sticky 上下文解析当前布局盒的视觉上下文。
     *
     * @param box 当前布局盒
     * @param scrollState 滚动状态
     * @param offsetX 父内容相对文档的 X 偏移
     * @param offsetY 父内容相对文档的 Y 偏移
     * @param stickyContext 父 sticky 上下文
     * @return 当前盒的视觉上下文
     */
    public static BoxContext resolveBoxContext(DocumentLayoutBox box, DocumentScrollState scrollState, int offsetX,
            int offsetY, StickyContext stickyContext) {
        return resolveBoxContext(box, scrollState, offsetX, offsetY, stickyContext,
                Collections.<ClipContext>emptyList());
    }

    private static BoxContext resolveBoxContext(DocumentLayoutBox box, DocumentScrollState scrollState, int offsetX,
            int offsetY, StickyContext stickyContext, List<ClipContext> clipChain) {
        StickyContext resolvedStickyContext = box.isFixedPositioned()
                ? DocumentStickyPositioning.rootContext()
                : stickyContext;
        List<ClipContext> resolvedClipChain = box.isFixedPositioned() ? Collections.<ClipContext>emptyList()
                : clipChain;
        int baseOffsetX = box.isFixedPositioned() ? 0 : offsetX;
        int baseOffsetY = box.isFixedPositioned() ? 0 : offsetY;
        int positionedOffsetX = baseOffsetX + box.getPositionOffsetX();
        int positionedOffsetY = baseOffsetY + box.getPositionOffsetY();
        int boxOffsetX = DocumentStickyPositioning.resolveOffsetX(box, positionedOffsetX, resolvedStickyContext);
        int boxOffsetY = DocumentStickyPositioning.resolveOffsetY(box, positionedOffsetY, resolvedStickyContext);
        DocumentEffectChain effectChain = DocumentEffectChain.resolve(box);
        StickyContext childStickyContext = DocumentStickyPositioning.createChildContext(box, boxOffsetX, boxOffsetY,
                resolvedStickyContext);
        List<ClipContext> childClipChain = effectChain.clipsChildren()
                ? appendClipContext(resolvedClipChain, new ClipContext(box, effectChain, boxOffsetX, boxOffsetY))
                : resolvedClipChain;
        int childOffsetX = boxOffsetX - getScrollLeft(scrollState, box);
        int childOffsetY = boxOffsetY - getScrollTop(scrollState, box);
        return new BoxContext(box, boxOffsetX, boxOffsetY, childOffsetX, childOffsetY, resolvedStickyContext,
                childStickyContext, effectChain, resolvedClipChain, childClipChain);
    }

    /**
     * 判断指定点是否可继续访问当前盒的子内容。
     *
     * @param context 当前盒视觉上下文
     * @param x 文档局部 X
     * @param y 文档局部 Y
     * @return 是否可进入子内容
     */
    public static boolean canReachChildren(BoxContext context, float x, float y) {
        return isPointInsideClipChain(context, x, y)
                && context.effectChain.canReachChildrenAt(x, y, context.boxOffsetX, context.boxOffsetY);
    }

    /**
     * 判断指定点是否位于所有祖先 clip 边界内。
     *
     * @param context 当前盒视觉上下文
     * @param x 文档局部 X
     * @param y 文档局部 Y
     * @return 是否仍位于可见 clip 链内
     */
    public static boolean isPointInsideClipChain(BoxContext context, float x, float y) {
        for (ClipContext clipContext : context.clipChain) {
            if (!clipContext.contains(x, y)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 返回当前 stacking context 中直接 normal-flow 子盒的视觉访问项。
     *
     * @param contextRoot 当前 stacking context 根盒
     * @param rootContext 当前 stacking context 根的视觉上下文
     * @param scrollState 滚动状态
     * @param resolver stacking context 判断器
     * @param reverse 是否按反向顺序返回
     * @return 访问项列表
     */
    public static List<TraversalEntry> getNormalFlowEntries(DocumentLayoutBox contextRoot, BoxContext rootContext,
            DocumentScrollState scrollState, StackingContextResolver resolver, boolean reverse) {
        List<TraversalEntry> entries = new ArrayList<TraversalEntry>();
        List<DocumentLayoutBox> children = contextRoot.getChildren();
        if (reverse) {
            for (int index = children.size() - 1; index >= 0; index--) {
                appendNormalFlowEntry(children.get(index), entries, rootContext, scrollState, resolver);
            }
        } else {
            for (DocumentLayoutBox child : children) {
                appendNormalFlowEntry(child, entries, rootContext, scrollState, resolver);
            }
        }
        return Collections.unmodifiableList(entries);
    }

    /**
     * 收集当前 stacking context 中指定 phase 的可排序访问项。
     *
     * @param contextRoot 当前 stacking context 根盒
     * @param rootContext 当前 stacking context 根的视觉上下文
     * @param scrollState 滚动状态
     * @param resolver stacking context 判断器
     * @param phase stacking phase
     * @return 访问项列表；负 z 和正 z 会按 z-index 升序排列
     */
    public static List<TraversalEntry> collectStackingPhaseEntries(DocumentLayoutBox contextRoot,
            BoxContext rootContext, DocumentScrollState scrollState, StackingContextResolver resolver,
            DocumentStackingPhase phase) {
        List<TraversalEntry> entries = new ArrayList<TraversalEntry>();
        collectStackingPhaseEntries(contextRoot, rootContext, entries, scrollState, resolver, phase);
        if (phase == DocumentStackingPhase.NEGATIVE_POSITIONED
                || phase == DocumentStackingPhase.POSITIVE_POSITIONED) {
            Collections.sort(entries, new Comparator<TraversalEntry>() {
                @Override
                public int compare(TraversalEntry first, TraversalEntry second) {
                    return Integer.compare(first.boxContext.box.getStackingZIndex(),
                            second.boxContext.box.getStackingZIndex());
                }
            });
        }
        return Collections.unmodifiableList(entries);
    }

    /**
     * 基于父视觉上下文解析直接子盒的视觉上下文。
     *
     * @param parentContext 父视觉上下文
     * @param child 子布局盒
     * @param scrollState 滚动状态
     * @return 子视觉上下文
     */
    public static BoxContext resolveChildBoxContext(BoxContext parentContext, DocumentLayoutBox child,
            DocumentScrollState scrollState) {
        return resolveBoxContext(child, scrollState, parentContext.childOffsetX, parentContext.childOffsetY,
                parentContext.childStickyContext, parentContext.childClipChain);
    }

    /**
     * 判断当前盒在给定动画时刻是否建立运行态 stacking 边界。
     *
     * <p>该判定会把静态 effect boundary、动画 opacity 触发的 paint context 与动画 transform
     * 统一收敛为一套局部排序边界。</p>
     *
     * @param box 布局盒
     * @param currentTimeNanos 当前时间
     * @param animationTimeline 动画时间线
     * @return 是否建立运行态 stacking 边界
     */
    public static boolean createsRuntimeStackingContext(DocumentLayoutBox box, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        DocumentEffectChain effectChain = DocumentEffectChain.resolve(box);
        if (effectChain.createsStackingContext()) {
            return true;
        }
        if (animationTimeline == null) {
            return false;
        }
        float opacity = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.OPACITY,
                box.getComputedStyle().getOpacity(), currentTimeNanos);
        if (effectChain.createsPaintContext(false, opacity)) {
            return true;
        }
        UiTransform transform = resolveAnimatedTransform(box, currentTimeNanos, animationTimeline);
        return transform != null && !transform.isIdentity();
    }

    private static List<ClipContext> appendClipContext(List<ClipContext> clipChain, ClipContext clipContext) {
        List<ClipContext> nextChain = new ArrayList<ClipContext>(clipChain.size() + 1);
        nextChain.addAll(clipChain);
        nextChain.add(clipContext);
        return Collections.unmodifiableList(nextChain);
    }

    private static void appendNormalFlowEntry(DocumentLayoutBox child, List<TraversalEntry> entries,
            BoxContext rootContext, DocumentScrollState scrollState, StackingContextResolver resolver) {
        if (child.getStackingPhase() != DocumentStackingPhase.NORMAL_FLOW) {
            return;
        }
        entries.add(createEntry(child, rootContext, scrollState, resolver));
    }

    private static void collectStackingPhaseEntries(DocumentLayoutBox currentBox, BoxContext currentContext,
            List<TraversalEntry> entries, DocumentScrollState scrollState, StackingContextResolver resolver,
            DocumentStackingPhase phase) {
        for (DocumentLayoutBox child : currentBox.getChildren()) {
            TraversalEntry entry = createEntry(child, currentContext, scrollState, resolver);
            if (child.getStackingPhase() == phase) {
                entries.add(entry);
            }
            if (entry.stackingContext) {
                continue;
            }
            collectStackingPhaseEntries(child, entry.boxContext, entries, scrollState, resolver, phase);
        }
    }

    private static TraversalEntry createEntry(DocumentLayoutBox child, BoxContext parentContext,
            DocumentScrollState scrollState, StackingContextResolver resolver) {
        BoxContext childContext = resolveBoxContext(child, scrollState, parentContext.childOffsetX,
                parentContext.childOffsetY, parentContext.childStickyContext, parentContext.childClipChain);
        boolean stackingContext = resolver != null && resolver.createsStackingContext(child);
        return new TraversalEntry(childContext, stackingContext);
    }

    private static int getScrollLeft(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollLeft(box.getElement());
    }

    private static int getScrollTop(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollTop(box.getElement());
    }

    private static UiTransform resolveAnimatedTransform(DocumentLayoutBox box, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        UiTransform baseTransform = box.getComputedStyle().getTransform();
        if (baseTransform == null) {
            baseTransform = UiTransform.identity();
        }
        if (animationTimeline == null) {
            return baseTransform;
        }
        float translateX = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.TRANSLATE_X,
                baseTransform.getTranslateX(), currentTimeNanos);
        float translateY = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.TRANSLATE_Y,
                baseTransform.getTranslateY(), currentTimeNanos);
        float scaleX = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.SCALE_X,
                baseTransform.getScaleX(), currentTimeNanos);
        float scaleY = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.SCALE_Y,
                baseTransform.getScaleY(), currentTimeNanos);
        float rotate = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.ROTATE,
                baseTransform.getRotateDegrees(), currentTimeNanos);
        return UiTransform.of(translateX, translateY, scaleX, scaleY, rotate, baseTransform.getOriginX(),
                baseTransform.getOriginY());
    }

    /**
     * 单个布局盒的已解析视觉上下文。
     */
    public static final class BoxContext {

        private final DocumentLayoutBox box;
        private final int boxOffsetX;
        private final int boxOffsetY;
        private final int childOffsetX;
        private final int childOffsetY;
        private final StickyContext stickyContext;
        private final StickyContext childStickyContext;
        private final DocumentEffectChain effectChain;
        private final List<ClipContext> clipChain;
        private final List<ClipContext> childClipChain;

        private BoxContext(DocumentLayoutBox box, int boxOffsetX, int boxOffsetY, int childOffsetX, int childOffsetY,
                StickyContext stickyContext, StickyContext childStickyContext, DocumentEffectChain effectChain,
                List<ClipContext> clipChain, List<ClipContext> childClipChain) {
            this.box = box;
            this.boxOffsetX = boxOffsetX;
            this.boxOffsetY = boxOffsetY;
            this.childOffsetX = childOffsetX;
            this.childOffsetY = childOffsetY;
            this.stickyContext = stickyContext;
            this.childStickyContext = childStickyContext;
            this.effectChain = effectChain;
            this.clipChain = clipChain;
            this.childClipChain = childClipChain;
        }

        public DocumentLayoutBox getBox() {
            return box;
        }

        public int getBoxOffsetX() {
            return boxOffsetX;
        }

        public int getBoxOffsetY() {
            return boxOffsetY;
        }

        public int getChildOffsetX() {
            return childOffsetX;
        }

        public int getChildOffsetY() {
            return childOffsetY;
        }

        public StickyContext getStickyContext() {
            return stickyContext;
        }

        public StickyContext getChildStickyContext() {
            return childStickyContext;
        }

        public DocumentEffectChain getEffectChain() {
            return effectChain;
        }

        public List<ClipContext> getClipChain() {
            return clipChain;
        }

        public List<ClipContext> getChildClipChain() {
            return childClipChain;
        }
    }

    /**
     * 祖先 overflow clip 边界。
     */
    public static final class ClipContext {

        private final DocumentLayoutBox box;
        private final DocumentEffectChain effectChain;
        private final int boxOffsetX;
        private final int boxOffsetY;

        private ClipContext(DocumentLayoutBox box, DocumentEffectChain effectChain, int boxOffsetX, int boxOffsetY) {
            this.box = box;
            this.effectChain = effectChain;
            this.boxOffsetX = boxOffsetX;
            this.boxOffsetY = boxOffsetY;
        }

        public DocumentLayoutBox getBox() {
            return box;
        }

        public DocumentEffectChain getEffectChain() {
            return effectChain;
        }

        public int getBoxOffsetX() {
            return boxOffsetX;
        }

        public int getBoxOffsetY() {
            return boxOffsetY;
        }

        public boolean contains(float x, float y) {
            return effectChain.resolveChildClipBounds(boxOffsetX, boxOffsetY).contains(x, y);
        }
    }

    /**
     * stacking 遍历中单个待访问子项。
     */
    public static final class TraversalEntry {

        private final BoxContext boxContext;
        private final boolean stackingContext;

        private TraversalEntry(BoxContext boxContext, boolean stackingContext) {
            this.boxContext = boxContext;
            this.stackingContext = stackingContext;
        }

        public BoxContext getBoxContext() {
            return boxContext;
        }

        public boolean isStackingContext() {
            return stackingContext;
        }
    }
}
