package club.heiqi.uilib.ui.paint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutTextRun;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.layout.DocumentScrollState.ScrollbarMetrics;
import club.heiqi.uilib.ui.layout.DocumentStackingPhase;
import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;

/**
 * HTML-like 绘制命令生成器。
 */
public final class DocumentPaintEngine {

    private static final int SCROLLBAR_TRACK_COLOR = 0x663B4A66;
    private static final int SCROLLBAR_THUMB_COLOR = 0xDDBCD7FF;
    private static final int MAX_BACKDROP_BLUR_RADIUS = 48;

    private DocumentPaintEngine() {}

    /**
     * 从布局盒树生成绘制命令。
     *
     * <p>当前初版按背后滤镜、元素背景、元素边框、结构裁剪、滚动内容、子树与滚动条的顺序输出命令。
     * 同级子元素会按 CSS-like stacking phase 做稳定排序，更完整 stacking context 会在后续阶段继续扩展。</p>
     *
     * @param rootBox 根布局盒
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox) {
        return buildPaintCommands(rootBox, null);
    }

    /**
     * 从布局盒树和滚动状态生成绘制命令。
     *
     * @param rootBox 根布局盒
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox,
            DocumentScrollState scrollState) {
        return buildPaintCommands(rootBox, scrollState, System.nanoTime());
    }

    /**
     * 从布局盒树和滚动状态生成绘制命令。
     *
     * <p>根元素滚动条保持可见；嵌套滚动条只在最近滚动后的短暂窗口内绘制，避免空闲时遮挡内容。</p>
     *
     * @param rootBox 根布局盒
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param currentTimeNanos 当前时间戳
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox,
            DocumentScrollState scrollState, long currentTimeNanos) {
        return buildPaintCommands(rootBox, scrollState, currentTimeNanos, null);
    }

    /**
     * 从布局盒树、滚动状态和动画时间线生成绘制命令。
     *
     * @param rootBox 根布局盒
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param currentTimeNanos 当前时间戳
     * @param animationTimeline 动画时间线；为 null 时不应用动画覆盖
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox,
            DocumentScrollState scrollState, long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        Objects.requireNonNull(rootBox, "rootBox");
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        appendBoxCommands(rootBox, rootBox, commands, scrollState, animationTimeline, 0, 0, currentTimeNanos, 1.0F,
                true);
        return commands;
    }

    private static void appendBoxCommands(DocumentLayoutBox rootBox, DocumentLayoutBox box,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState,
            DocumentAnimationTimeline animationTimeline, int offsetX, int offsetY, long currentTimeNanos,
            float inheritedOpacity, boolean paintStackingContext) {
        int boxOffsetX = offsetX + box.getPositionOffsetX();
        int boxOffsetY = offsetY + box.getPositionOffsetY();
        float localOpacity = resolveAnimatedOpacity(animationTimeline, box, currentTimeNanos);
        boolean paintContext = shouldCreatePaintContext(rootBox, box, localOpacity);
        boolean resolvedPaintStackingContext = paintStackingContext || paintContext || box == rootBox;
        float boxOpacity = paintContext ? inheritedOpacity : inheritedOpacity * localOpacity;
        if (paintContext) {
            appendPaintContextStartCommand(box, commands, localOpacity, boxOffsetX, boxOffsetY);
        }
        appendBackdropFilterCommand(box, commands, animationTimeline, currentTimeNanos, boxOffsetX, boxOffsetY);
        appendBackgroundCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                boxOffsetY);
        appendBorderCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX, boxOffsetY);
        boolean clipChildren = shouldClipChildren(box);
        if (clipChildren) {
            appendClipStartCommand(box, commands, animationTimeline, currentTimeNanos, boxOffsetX, boxOffsetY);
        }
        int childOffsetX = boxOffsetX - getScrollLeft(scrollState, box);
        int childOffsetY = boxOffsetY - getScrollTop(scrollState, box);
        if (resolvedPaintStackingContext) {
            appendStackingPhaseItems(rootBox, box, commands, scrollState, childOffsetX, childOffsetY,
                    animationTimeline, currentTimeNanos, boxOpacity, DocumentStackingPhase.NEGATIVE_POSITIONED);
            appendCustomCommand(box, commands, childOffsetX, childOffsetY);
            appendTextCommands(box, commands, animationTimeline, currentTimeNanos, boxOpacity, childOffsetX,
                    childOffsetY);
            appendNormalFlowChildren(rootBox, box, commands, scrollState, childOffsetX, childOffsetY,
                    animationTimeline, currentTimeNanos, boxOpacity);
            appendStackingPhaseItems(rootBox, box, commands, scrollState, childOffsetX, childOffsetY,
                    animationTimeline, currentTimeNanos, boxOpacity, DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO);
            appendStackingPhaseItems(rootBox, box, commands, scrollState, childOffsetX, childOffsetY,
                    animationTimeline, currentTimeNanos, boxOpacity, DocumentStackingPhase.POSITIVE_POSITIONED);
        } else {
            appendCustomCommand(box, commands, childOffsetX, childOffsetY);
            appendTextCommands(box, commands, animationTimeline, currentTimeNanos, boxOpacity, childOffsetX,
                    childOffsetY);
            appendNormalFlowChildren(rootBox, box, commands, scrollState, childOffsetX, childOffsetY,
                    animationTimeline, currentTimeNanos, boxOpacity);
        }
        if (clipChildren) {
            appendClipEndCommand(box, commands, boxOffsetX, boxOffsetY);
        }
        appendScrollbarCommands(rootBox, box, commands, scrollState, boxOffsetX, boxOffsetY, currentTimeNanos,
                boxOpacity);
        if (paintContext) {
            appendPaintContextEndCommand(box, commands, boxOffsetX, boxOffsetY);
        }
    }

    private static boolean shouldCreatePaintContext(DocumentLayoutBox rootBox, DocumentLayoutBox box,
            float localOpacity) {
        if (box == rootBox || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return false;
        }
        ComputedStyle style = box.getComputedStyle();
        if (localOpacity < 0.999F) {
            return true;
        }
        if (style.getPosition() != UiPosition.STATIC && style.getZIndex() != null) {
            return true;
        }
        int blurRadius = resolveBackdropBlurRadius(box);
        return blurRadius > 0 || Float.compare(style.getBackdropSaturation(), 1.0F) != 0;
    }

    private static void appendPaintContextStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            float localOpacity, int offsetX, int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.PAINT_CONTEXT_START, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, 0, null, null, 0, 1.0F, localOpacity));
    }

    private static void appendPaintContextEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            int offsetX, int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.PAINT_CONTEXT_END, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, 0));
    }

    private static void appendNormalFlowChildren(DocumentLayoutBox rootBox, DocumentLayoutBox box,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState, int childOffsetX, int childOffsetY,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float inheritedOpacity) {
        for (DocumentLayoutBox child : box.getChildren()) {
            if (child.getStackingPhase() != DocumentStackingPhase.NORMAL_FLOW) {
                continue;
            }
            float localOpacity = resolveAnimatedOpacity(animationTimeline, child, currentTimeNanos);
            boolean childPaintContext = shouldCreatePaintContext(rootBox, child, localOpacity);
            boolean childPaintStackingContext = childPaintContext || shouldClipChildren(child);
            appendBoxCommands(rootBox, child, commands, scrollState, animationTimeline, childOffsetX, childOffsetY,
                    currentTimeNanos, inheritedOpacity, childPaintStackingContext);
        }
    }

    private static void appendStackingPhaseItems(DocumentLayoutBox rootBox, DocumentLayoutBox contextRoot,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState, int childOffsetX, int childOffsetY,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float inheritedOpacity,
            DocumentStackingPhase phase) {
        List<StackingPaintItem> items = new ArrayList<StackingPaintItem>();
        collectStackingPhaseItems(rootBox, contextRoot, items, scrollState, childOffsetX, childOffsetY,
                animationTimeline, currentTimeNanos, phase);
        if (phase == DocumentStackingPhase.NEGATIVE_POSITIONED
                || phase == DocumentStackingPhase.POSITIVE_POSITIONED) {
            java.util.Collections.sort(items, new java.util.Comparator<StackingPaintItem>() {
                @Override
                public int compare(StackingPaintItem first, StackingPaintItem second) {
                    return Integer.compare(first.box.getStackingZIndex(), second.box.getStackingZIndex());
                }
            });
        }
        for (StackingPaintItem item : items) {
            appendBoxCommands(rootBox, item.box, commands, scrollState, animationTimeline, item.offsetX,
                    item.offsetY, currentTimeNanos, inheritedOpacity, item.paintStackingContext);
        }
    }

    private static void collectStackingPhaseItems(DocumentLayoutBox rootBox, DocumentLayoutBox currentBox,
            List<StackingPaintItem> items, DocumentScrollState scrollState, int childOffsetX, int childOffsetY,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, DocumentStackingPhase phase) {
        for (DocumentLayoutBox child : currentBox.getChildren()) {
            float localOpacity = resolveAnimatedOpacity(animationTimeline, child, currentTimeNanos);
            boolean childPaintContext = shouldCreatePaintContext(rootBox, child, localOpacity);
            boolean childPaintStackingContext = childPaintContext || shouldClipChildren(child);
            if (child.getStackingPhase() == phase) {
                items.add(new StackingPaintItem(child, childOffsetX, childOffsetY, childPaintStackingContext));
            }
            if (childPaintStackingContext) {
                continue;
            }
            int grandChildOffsetX = childOffsetX + child.getPositionOffsetX() - getScrollLeft(scrollState, child);
            int grandChildOffsetY = childOffsetY + child.getPositionOffsetY() - getScrollTop(scrollState, child);
            collectStackingPhaseItems(rootBox, child, items, scrollState, grandChildOffsetX, grandChildOffsetY,
                    animationTimeline, currentTimeNanos, phase);
        }
    }

    private static void appendBackgroundCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        int color = resolveAnimatedColor(animationTimeline, box, DocumentAnimationProperty.BACKGROUND_COLOR,
                style.getBackgroundColor(), currentTimeNanos);
        color = applyOpacity(color, opacity);
        if (isTransparent(color) || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, color, 0, resolveBorderRadius(box, animationTimeline,
                        currentTimeNanos)));
    }

    private static void appendBackdropFilterCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, int offsetX, int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        int blurRadius = resolveBackdropBlurRadius(box);
        float saturation = style.getBackdropSaturation();
        if ((blurRadius <= 0 && Float.compare(saturation, 1.0F) == 0) || box.getWidth() <= 0
                || box.getHeight() <= 0) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKDROP_FILTER, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, resolveBorderRadius(box, animationTimeline, currentTimeNanos),
                null, null, blurRadius, saturation));
    }

    private static void appendBorderCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        int borderWidth = box.getBorder().getTop();
        int color = resolveAnimatedColor(animationTimeline, box, DocumentAnimationProperty.BORDER_COLOR,
                style.getBorderColor(), currentTimeNanos);
        color = applyOpacity(color, opacity);
        if (isTransparent(color) || borderWidth <= 0 || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BORDER, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, color, borderWidth, resolveBorderRadius(box, animationTimeline,
                        currentTimeNanos)));
    }

    private static void appendCustomCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands, int offsetX,
            int offsetY) {
        DocumentCustomRenderer customRenderer = box.getElement().getCustomRenderer();
        if (customRenderer == null || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        int contentLeft = box.getContentLeft() + offsetX;
        int contentTop = box.getContentTop() + offsetY;
        int contentRight = contentLeft + box.getContentWidth();
        int contentBottom = contentTop + box.getContentHeight();
        if (contentRight <= contentLeft || contentBottom <= contentTop) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CUSTOM, box.getElement(),
                contentLeft, contentTop, contentRight, contentBottom,
                0, 0, 0, null, customRenderer));
    }

    private static void appendTextCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        int color = resolveAnimatedColor(animationTimeline, box, DocumentAnimationProperty.TEXT_COLOR,
                box.getComputedStyle().getTextColor(), currentTimeNanos);
        color = applyOpacity(color, opacity);
        if (isTransparent(color)) {
            return;
        }
        for (DocumentLayoutTextRun textRun : box.getTextRuns()) {
            if (textRun.getText().isEmpty() || textRun.getWidth() <= 0 || textRun.getHeight() <= 0) {
                continue;
            }
            commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, textRun.getOwnerElement(),
                    textRun.getLeft() + offsetX, textRun.getTop() + offsetY, textRun.getRight() + offsetX,
                    textRun.getBottom() + offsetY, color, 0, 0, textRun.getText()));
        }
    }

    private static int resolveAnimatedColor(DocumentAnimationTimeline animationTimeline, DocumentLayoutBox box,
            DocumentAnimationProperty property, int baseColor, long currentTimeNanos) {
        if (animationTimeline == null) {
            return baseColor;
        }
        return animationTimeline.resolveColor(box.getElement(), property, baseColor, currentTimeNanos);
    }

    private static float resolveAnimatedOpacity(DocumentAnimationTimeline animationTimeline, DocumentLayoutBox box,
            long currentTimeNanos) {
        float baseOpacity = box.getComputedStyle().getOpacity();
        if (animationTimeline == null) {
            return baseOpacity;
        }
        return animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.OPACITY, baseOpacity,
                currentTimeNanos);
    }

    private static int applyOpacity(int color, float opacity) {
        float clampedOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
        if (clampedOpacity >= 0.999F) {
            return color;
        }
        int alpha = Math.round(((color >>> 24) & 0xFF) * clampedOpacity);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static boolean isTransparent(int color) {
        return ((color >>> 24) & 0xFF) == 0;
    }

    private static void appendClipStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, int offsetX, int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        int left = style.getOverflowX() == UiOverflow.VISIBLE
                ? Integer.MIN_VALUE / 4
                : getPaddingBoxLeft(box) + offsetX;
        int right = style.getOverflowX() == UiOverflow.VISIBLE
                ? Integer.MAX_VALUE / 4
                : getPaddingBoxRight(box) + offsetX;
        int top = style.getOverflowY() == UiOverflow.VISIBLE
                ? Integer.MIN_VALUE / 4
                : getPaddingBoxTop(box) + offsetY;
        int bottom = style.getOverflowY() == UiOverflow.VISIBLE
                ? Integer.MAX_VALUE / 4
                : getPaddingBoxBottom(box) + offsetY;
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CLIP_START, box.getElement(), left, top,
                right, bottom, 0, 0, resolveBorderRadius(box, animationTimeline, currentTimeNanos)));
    }

    private static void appendClipEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands, int offsetX,
            int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CLIP_END, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, 0));
    }

    private static void appendScrollbarCommands(DocumentLayoutBox rootBox, DocumentLayoutBox box,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState, int offsetX, int offsetY,
            long currentTimeNanos, float opacity) {
        if (scrollState == null || box.getContentWidth() <= 0 || box.getContentHeight() <= 0) {
            return;
        }
        int maxScrollTop = scrollState.getMaxScrollTop(box.getElement());
        int maxScrollLeft = scrollState.getMaxScrollLeft(box.getElement());
        boolean hasVerticalScrollbar = maxScrollTop > 0 && box.getComputedStyle().getOverflowY() == UiOverflow.AUTO;
        boolean hasHorizontalScrollbar = maxScrollLeft > 0 && box.getComputedStyle().getOverflowX() == UiOverflow.AUTO;
        if (!hasVerticalScrollbar && !hasHorizontalScrollbar) {
            return;
        }
        if (box != rootBox && !scrollState.shouldShowTransientScrollbar(box.getElement(), currentTimeNanos)) {
            return;
        }

        if (hasVerticalScrollbar) {
            appendScrollbarCommands(box, commands, scrollState.getVerticalScrollbarMetrics(box, offsetX, offsetY,
                    hasHorizontalScrollbar), opacity);
        }
        if (hasHorizontalScrollbar) {
            appendScrollbarCommands(box, commands, scrollState.getHorizontalScrollbarMetrics(box, offsetX, offsetY,
                    hasVerticalScrollbar), opacity);
        }
    }

    private static void appendScrollbarCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            ScrollbarMetrics metrics, float opacity) {
        if (metrics == null) {
            return;
        }
        int radius = Math.max(0, Math.min(metrics.getTrackRight() - metrics.getTrackLeft(),
                metrics.getTrackBottom() - metrics.getTrackTop()) / 2);
        int trackColor = applyOpacity(SCROLLBAR_TRACK_COLOR, opacity);
        int thumbColor = applyOpacity(SCROLLBAR_THUMB_COLOR, opacity);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLLBAR_TRACK, box.getElement(),
                metrics.getTrackLeft(),
                metrics.getTrackTop(), metrics.getTrackRight(), metrics.getTrackBottom(), trackColor, 0,
                radius));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLLBAR_THUMB, box.getElement(),
                metrics.getThumbLeft(), metrics.getThumbTop(), metrics.getThumbRight(), metrics.getThumbBottom(),
                thumbColor, 0, radius));
    }

    private static boolean shouldClipChildren(DocumentLayoutBox box) {
        ComputedStyle style = box.getComputedStyle();
        boolean hasOverflow = style.getOverflowX() != UiOverflow.VISIBLE
                || style.getOverflowY() != UiOverflow.VISIBLE;
        if (!hasOverflow) {
            return false;
        }
        if (!box.getChildren().isEmpty() || !box.getTextRuns().isEmpty()) {
            return true;
        }
        return box.getElement().getCustomRenderer() != null;
    }

    private static int getPaddingBoxLeft(DocumentLayoutBox box) {
        return box.getLeft() + box.getBorder().getLeft();
    }

    private static int getPaddingBoxTop(DocumentLayoutBox box) {
        return box.getTop() + box.getBorder().getTop();
    }

    private static int getPaddingBoxRight(DocumentLayoutBox box) {
        return box.getRight() - box.getBorder().getRight();
    }

    private static int getPaddingBoxBottom(DocumentLayoutBox box) {
        return box.getBottom() - box.getBorder().getBottom();
    }

    private static int resolveBorderRadius(DocumentLayoutBox box, DocumentAnimationTimeline animationTimeline,
            long currentTimeNanos) {
        int radius = resolveStaticBorderRadius(box);
        if (animationTimeline != null) {
            radius = Math.round(animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.BORDER_RADIUS,
                    radius, currentTimeNanos));
        }
        int limit = Math.min(box.getWidth(), box.getHeight());
        return Math.max(0, Math.min(radius, limit / 2));
    }

    private static int resolveStaticBorderRadius(DocumentLayoutBox box) {
        int limit = Math.min(box.getWidth(), box.getHeight());
        int radius = box.getComputedStyle().getBorderRadius().resolve(limit, 0);
        return Math.max(0, Math.min(radius, limit / 2));
    }

    private static int resolveBackdropBlurRadius(DocumentLayoutBox box) {
        int availableSpace = Math.max(box.getWidth(), box.getHeight());
        int radius = box.getComputedStyle().getBackdropBlurRadius().resolve(availableSpace, 0);
        return Math.max(0, Math.min(radius, MAX_BACKDROP_BLUR_RADIUS));
    }

    private static int getScrollLeft(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollLeft(box.getElement());
    }

    private static int getScrollTop(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollTop(box.getElement());
    }

    /**
     * 最近 stacking context 中可被阶段排序的绘制项。
     */
    private static final class StackingPaintItem {

        private final DocumentLayoutBox box;
        private final int offsetX;
        private final int offsetY;
        private final boolean paintStackingContext;

        private StackingPaintItem(DocumentLayoutBox box, int offsetX, int offsetY, boolean paintStackingContext) {
            this.box = box;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.paintStackingContext = paintStackingContext;
        }
    }
}
