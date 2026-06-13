package club.heiqi.uilib.ui.paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentEffectChain;
import club.heiqi.uilib.ui.layout.DocumentEffectType;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEdges;
import club.heiqi.uilib.ui.layout.DocumentLayoutInlineFragment;
import club.heiqi.uilib.ui.layout.DocumentLayoutTextRun;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.layout.DocumentScrollState.ScrollbarMetrics;
import club.heiqi.uilib.ui.layout.DocumentStackingPhase;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.BoxContext;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.ClipContext;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.RootEntry;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.StackingContextResolver;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.TraversalEntry;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.VisualScene;
import club.heiqi.uilib.ui.render.BackdropBlurConfig;
import club.heiqi.uilib.ui.render.BackdropBlurPolicy;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiListStyleType;
import club.heiqi.uilib.ui.style.values.UiOutline;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiScrollbarColor;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.props.UiTextDecoration;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;
import club.heiqi.uilib.ui.style.values.UiTransform;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * HTML-like 绘制命令生成器。
 */
public final class DocumentPaintEngine {

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
        return buildPaintCommands(rootBox, Collections.<DocumentLayoutBox>emptyList(), scrollState, currentTimeNanos,
                animationTimeline);
    }

    /**
     * 从普通布局盒树、top-layer 根盒、滚动状态和动画时间线生成绘制命令。
     *
     * @param rootBox 普通文档根盒
     * @param topLayerBoxes top-layer 根盒；后面的盒位于更上层
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param currentTimeNanos 当前时间戳
     * @param animationTimeline 动画时间线；为 null 时不应用动画覆盖
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox,
            List<DocumentLayoutBox> topLayerBoxes, DocumentScrollState scrollState, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        return buildPaintCommands(rootBox, topLayerBoxes, scrollState, currentTimeNanos, animationTimeline, null);
    }

    /**
     * 从普通布局盒树、top-layer 根盒、滚动状态、动画时间线和文本测量服务生成绘制命令。
     *
     * <p>传入文本测量服务后，绘制阶段可对被 overflow clip 横向裁掉的长单行文本生成可见片段，
     * 避免每帧把完整长字符串提交给字体后端。</p>
     *
     * @param rootBox 普通文档根盒
     * @param topLayerBoxes top-layer 根盒；后面的盒位于更上层
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param currentTimeNanos 当前时间戳
     * @param animationTimeline 动画时间线；为 null 时不应用动画覆盖
     * @param textMeasureService 文本测量服务；为 null 时只做不依赖测量的可见性裁剪
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox,
            List<DocumentLayoutBox> topLayerBoxes, DocumentScrollState scrollState, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline, TextMeasureService textMeasureService) {
        Objects.requireNonNull(rootBox, "rootBox");
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        StackingContextResolver resolver = createPaintStackingContextResolver(currentTimeNanos, animationTimeline);
        VisualScene scene = DocumentVisualTraversal.resolveVisualScene(rootBox, topLayerBoxes, scrollState,
                currentTimeNanos, animationTimeline);
        for (RootEntry rootEntry : scene.getRootEntries()) {
            appendBoxCommands(rootEntry.getRootBox(), rootEntry.getRootContext(), commands, scrollState,
                    animationTimeline, currentTimeNanos, 1.0F, true, Collections.<ClipContext>emptyList(), resolver,
                    false, textMeasureService);
        }
        return commands;
    }

    private static void appendBoxCommands(DocumentLayoutBox rootBox, BoxContext boxContext,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float inheritedOpacity,
            boolean paintStackingContext, List<ClipContext> activeClipChain, StackingContextResolver resolver,
            boolean transformActive, TextMeasureService textMeasureService) {
        DocumentLayoutBox box = boxContext.getBox();
        // visibility:hidden 只隐藏当前元素自身，允许显式 visibility:visible 的后代恢复绘制。
        boolean visibilityHidden = isVisibilityHidden(box);
        int boxOffsetX = boxContext.getBoxOffsetX();
        int boxOffsetY = boxContext.getBoxOffsetY();
        UiTransform transform = resolveAnimatedTransform(animationTimeline, box, currentTimeNanos);
        boolean transformed = transform != null && !transform.isIdentity();
        boolean currentTransformActive = transformActive || transformed;
        if (transformed) {
            appendTransformStartCommand(box, commands, transform, boxOffsetX, boxOffsetY);
        }
        float localOpacity = resolveAnimatedOpacity(animationTimeline, box, currentTimeNanos);
        DocumentEffectChain effectChain = boxContext.getEffectChain();
        boolean paintContext = effectChain.createsPaintContext(box == rootBox, localOpacity);
        boolean resolvedPaintStackingContext = paintStackingContext || box == rootBox;
        float boxOpacity = paintContext ? inheritedOpacity : inheritedOpacity * localOpacity;
        List<ClipContext> currentClipChain = activeClipChain;
        if (paintContext) {
            appendPaintContextStartCommand(box, commands, localOpacity, boxOffsetX, boxOffsetY);
        }
        if (!visibilityHidden) {
            currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getClipChain(),
                    animationTimeline, currentTimeNanos);
            appendBackdropFilterCommand(box, commands, animationTimeline, currentTimeNanos, effectChain, boxOffsetX,
                    boxOffsetY);
            appendBoxShadowCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                    boxOffsetY, false);
            appendBackgroundCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                    boxOffsetY);
            appendBoxShadowCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                    boxOffsetY, true);
            appendBorderCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                    boxOffsetY);
        }
        int childOffsetX = boxContext.getChildOffsetX();
        int childOffsetY = boxContext.getChildOffsetY();
        if (resolvedPaintStackingContext) {
            appendStackingPhaseItems(rootBox, boxContext, commands, scrollState, animationTimeline,
                    currentTimeNanos, boxOpacity, DocumentStackingPhase.NEGATIVE_POSITIONED, currentClipChain,
                    resolver, currentTransformActive, textMeasureService);
            if (hasFlowContent(box, visibilityHidden)) {
                currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getChildClipChain(),
                        animationTimeline, currentTimeNanos);
            }
            if (!visibilityHidden) {
                appendCustomCommand(box, commands, childOffsetX, childOffsetY);
            }
            appendInlineFragmentSurfaceCommands(box, commands, animationTimeline, currentTimeNanos, boxOpacity,
                    childOffsetX, childOffsetY);
            appendListMarkerCommand(box, commands, boxOpacity, childOffsetX, childOffsetY);
            appendTextCommands(box, commands, animationTimeline, currentTimeNanos, boxOpacity, childOffsetX,
                    childOffsetY, currentClipChain, currentTransformActive, textMeasureService);
            appendNormalFlowChildren(rootBox, boxContext, commands, scrollState, animationTimeline,
                    currentTimeNanos, boxOpacity, currentClipChain, resolver, currentTransformActive,
                    textMeasureService);
            if (hasFlowContent(box, visibilityHidden)) {
                currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getClipChain(),
                        animationTimeline, currentTimeNanos);
            }
            appendStackingPhaseItems(rootBox, boxContext, commands, scrollState, animationTimeline,
                    currentTimeNanos, boxOpacity, DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO, currentClipChain,
                    resolver, currentTransformActive, textMeasureService);
            appendStackingPhaseItems(rootBox, boxContext, commands, scrollState, animationTimeline,
                    currentTimeNanos, boxOpacity, DocumentStackingPhase.POSITIVE_POSITIONED, currentClipChain,
                    resolver, currentTransformActive, textMeasureService);
        } else {
            if (hasFlowContent(box, visibilityHidden)) {
                currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getChildClipChain(),
                        animationTimeline, currentTimeNanos);
            }
            if (!visibilityHidden) {
                appendCustomCommand(box, commands, childOffsetX, childOffsetY);
            }
            appendInlineFragmentSurfaceCommands(box, commands, animationTimeline, currentTimeNanos, boxOpacity,
                    childOffsetX, childOffsetY);
            appendListMarkerCommand(box, commands, boxOpacity, childOffsetX, childOffsetY);
            appendTextCommands(box, commands, animationTimeline, currentTimeNanos, boxOpacity, childOffsetX,
                    childOffsetY, currentClipChain, currentTransformActive, textMeasureService);
            appendNormalFlowChildren(rootBox, boxContext, commands, scrollState, animationTimeline,
                    currentTimeNanos, boxOpacity, currentClipChain, resolver, currentTransformActive,
                    textMeasureService);
            if (hasFlowContent(box, visibilityHidden)) {
                currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getClipChain(),
                        animationTimeline, currentTimeNanos);
            }
        }
        currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getClipChain(),
                animationTimeline, currentTimeNanos);
        if (!visibilityHidden) {
            appendScrollbarCommands(rootBox, box, commands, scrollState, boxOffsetX, boxOffsetY, currentTimeNanos,
                    boxOpacity);
            appendOutlineCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                    boxOffsetY);
        }
        transitionClipChain(commands, currentClipChain, activeClipChain, animationTimeline, currentTimeNanos);
        if (paintContext) {
            appendPaintContextEndCommand(box, commands, boxOffsetX, boxOffsetY);
        }
        if (transformed) {
            appendTransformEndCommand(box, commands, transform, boxOffsetX, boxOffsetY);
        }
    }

    private static void appendTransformStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            UiTransform transform, int offsetX, int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TRANSFORM_START, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, transform));
    }

    private static void appendTransformEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            UiTransform transform, int offsetX, int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TRANSFORM_END, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, transform));
    }

    private static void appendPaintContextStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            float localOpacity, int offsetX, int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.PAINT_CONTEXT_START, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, 0, null, null, 0, 1.0F, localOpacity,
                DocumentEffectType.PAINT_CONTEXT));
    }

    private static void appendPaintContextEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            int offsetX, int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.PAINT_CONTEXT_END, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, 0, null, null, 0, 1.0F, 1.0F,
                DocumentEffectType.PAINT_CONTEXT));
    }

    private static void appendNormalFlowChildren(DocumentLayoutBox rootBox, BoxContext contextRootContext,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float inheritedOpacity,
            List<ClipContext> activeClipChain, StackingContextResolver resolver, boolean transformActive,
            TextMeasureService textMeasureService) {
        List<TraversalEntry> children = DocumentVisualTraversal.getNormalFlowEntries(contextRootContext.getBox(),
                contextRootContext, scrollState, resolver, false);
        for (TraversalEntry child : children) {
            appendBoxCommands(rootBox, child.getBoxContext(), commands, scrollState, animationTimeline,
                    currentTimeNanos, inheritedOpacity, child.isStackingContext(), activeClipChain, resolver,
                    transformActive, textMeasureService);
        }
    }

    private static void appendStackingPhaseItems(DocumentLayoutBox rootBox, BoxContext contextRootContext,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float inheritedOpacity,
            DocumentStackingPhase phase, List<ClipContext> activeClipChain, StackingContextResolver resolver,
            boolean transformActive, TextMeasureService textMeasureService) {
        List<TraversalEntry> items = DocumentVisualTraversal.collectStackingPhaseEntries(contextRootContext.getBox(),
                contextRootContext, scrollState, resolver, phase);
        for (TraversalEntry item : items) {
            appendBoxCommands(rootBox, item.getBoxContext(), commands, scrollState, animationTimeline,
                    currentTimeNanos, inheritedOpacity, item.isStackingContext(), activeClipChain, resolver,
                    transformActive, textMeasureService);
        }
    }

    private static boolean hasFlowContent(DocumentLayoutBox box, boolean visibilityHidden) {
        if (hasNormalFlowChildren(box)) {
            return true;
        }
        if (visibilityHidden) {
            return false;
        }
        return box.getElement().getCustomRenderer() != null || !box.getInlineFragments().isEmpty()
                || !box.getTextRuns().isEmpty() || isListItemWithMarker(box);
    }

    private static boolean hasNormalFlowChildren(DocumentLayoutBox box) {
        for (DocumentLayoutBox child : box.getChildren()) {
            if (child.getStackingPhase() == DocumentStackingPhase.NORMAL_FLOW) {
                return true;
            }
        }
        return false;
    }

    private static boolean isListItemWithMarker(DocumentLayoutBox box) {
        ElementNode element = box.getElement();
        if (!"li".equals(element.getTagName())) {
            return false;
        }
        ElementNode parent = resolveParentElement(element);
        if (parent == null) {
            return false;
        }
        String parentTagName = parent.getTagName();
        if (!"ul".equals(parentTagName) && !"ol".equals(parentTagName)) {
            return false;
        }
        return box.getComputedStyle().getListStyleType() != UiListStyleType.NONE;
    }

    private static void appendBackgroundCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        int color = resolveAnimatedColor(animationTimeline, box, DocumentAnimationProperty.BACKGROUND_COLOR,
                style.getBackgroundColor(), currentTimeNanos);
        color = applyOpacity(color, opacity);
        UiBackgroundImage backgroundImage = style.getBackgroundImage();
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        if (!isTransparent(color)) {
            commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, box.getElement(),
                    box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                    box.getBottom() + offsetY, color, 0, cornerRadii));
        }
        if (backgroundImage != null) {
            commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND_IMAGE, box.getElement(),
                    box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                    box.getBottom() + offsetY, cornerRadii, backgroundImage));
        }
    }

    private static void appendBackdropFilterCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, DocumentEffectChain effectChain,
            int offsetX, int offsetY) {
        BackdropBlurPolicy backdropBlurPolicy = resolveBackdropBlurPolicy(box);
        if (!backdropBlurPolicy.resolveEnabled(BackdropBlurConfig.getInstance())) {
            return;
        }
        int blurRadius = resolveAnimatedBackdropBlurRadius(animationTimeline, box, effectChain.getBackdropBlurRadius(),
                currentTimeNanos, backdropBlurPolicy);
        float saturation = effectChain.getBackdropSaturation();
        boolean runningBlurTransition = hasRunningTransition(animationTimeline, box,
                DocumentAnimationProperty.BACKDROP_BLUR_RADIUS);
        if ((!hasBackdropFilter(blurRadius, saturation) && !runningBlurTransition)
                || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKDROP_FILTER, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, cornerRadii, UiSurfaceStyle.CORNER_ALL, null, null, blurRadius,
                saturation, 1.0F,
                DocumentEffectType.BACKDROP_FILTER));
    }

    private static void appendBorderCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        if (style.getBorderStyle() == UiBorderStyle.NONE || style.getBorderStyle() == UiBorderStyle.HIDDEN
                || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        DocumentLayoutEdges borderWidths = box.getBorder();
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        int color = resolveAnimatedColor(animationTimeline, box, DocumentAnimationProperty.BORDER_COLOR,
                style.getBorderColor(), currentTimeNanos);
        color = applyOpacity(color, opacity);
        if (isTransparent(color) || borderWidths.getTop() <= 0 && borderWidths.getRight() <= 0
                && borderWidths.getBottom() <= 0 && borderWidths.getLeft() <= 0) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BORDER, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, color, borderWidths.getTop(), cornerRadii));
    }

    private static void appendBoxShadowCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY, boolean inset) {
        UiBoxShadow boxShadow = resolveAnimatedBoxShadow(box, animationTimeline, currentTimeNanos);
        if (boxShadow == null || boxShadow.getColor() == 0 || boxShadow.isInset() != inset) {
            return;
        }
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        commands.add(new DocumentPaintCommand(
                inset ? DocumentPaintCommandType.BOX_SHADOW_INSET : DocumentPaintCommandType.BOX_SHADOW,
                box.getElement(), box.getLeft() + offsetX, box.getTop() + offsetY,
                box.getRight() + offsetX, box.getBottom() + offsetY, applyOpacity(boxShadow.getColor(), opacity), 0,
                cornerRadii, boxShadow));
    }

    private static void appendOutlineCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        UiOutline outline = box.getComputedStyle().getOutline();
        if (outline == null || outline.isNone() || outline.getStyle() == UiBorderStyle.HIDDEN) {
            return;
        }
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.OUTLINE, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, applyOpacity(outline.getColor(), opacity), outline.getWidth(),
                cornerRadii));
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
            int offsetY, List<ClipContext> activeClipChain, boolean transformActive,
            TextMeasureService textMeasureService) {
        DocumentTextPaintClipper.ClipBounds clipBounds = transformActive ? null
                : DocumentTextPaintClipper.resolveClipBounds(activeClipChain);
        for (DocumentLayoutTextRun textRun : box.getTextRuns()) {
            if (textRun.getText().isEmpty() || textRun.getWidth() <= 0 || textRun.getHeight() <= 0) {
                continue;
            }
            if (isVisibilityHidden(textRun.getOwnerElement())) {
                continue;
            }
            int color = resolveTextRunColor(textRun, animationTimeline, currentTimeNanos, opacity);
            if (isTransparent(color)) {
                continue;
            }
            ComputedStyle ownerStyle = UiStyleResolver.compute(textRun.getOwnerElement());
            DocumentTextPaintClipper.PaintBounds paintBounds = DocumentTextPaintClipper.resolvePaintBounds(textRun,
                    ownerStyle, textMeasureService, offsetX, offsetY, clipBounds != null, false);
            int expansion = DocumentTextPaintClipper.resolveVisualExpansion(ownerStyle);
            if (clipBounds != null && !DocumentTextPaintClipper.intersectsExpanded(paintBounds, clipBounds,
                    expansion)) {
                continue;
            }
            if (clipBounds != null) {
                paintBounds = DocumentTextPaintClipper.resolvePaintBounds(textRun, ownerStyle, textMeasureService,
                        offsetX, offsetY, true, true);
                if (!DocumentTextPaintClipper.intersectsExpanded(paintBounds, clipBounds, expansion)) {
                    continue;
                }
            }
            DocumentTextPaintClipper.PaintSlice paintSlice = DocumentTextPaintClipper.resolveVisibleSlice(textRun,
                    paintBounds, ownerStyle, textMeasureService, clipBounds, expansion);
            if (paintSlice.getText().isEmpty() || paintSlice.getRight() <= paintSlice.getLeft()) {
                continue;
            }
            appendTextDecorationCommand(textRun, commands, color, paintSlice.getLeft(), paintSlice.getRight(),
                    offsetY);
            commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, textRun.getOwnerElement(),
                    paintSlice.getLeft(), textRun.getTop() + offsetY, paintSlice.getRight(),
                    textRun.getBottom() + offsetY, color, 0, 0, paintSlice.getText(), textRun.getTextContentMode(),
                    ownerStyle == null ? UiFontWeight.NORMAL : ownerStyle.getFontWeight(),
                    ownerStyle == null ? UiFontStyle.NORMAL : ownerStyle.getFontStyle(),
                    textRun.getTextMeasureStyle(), null, 0, 1.0F, 1.0F));
        }
    }

    private static void appendListMarkerCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            float opacity, int offsetX, int offsetY) {
        ElementNode element = box.getElement();
        if (!"li".equals(element.getTagName())) {
            return;
        }
        ElementNode parent = resolveParentElement(element);
        if (parent == null) {
            return;
        }
        String parentTagName = parent.getTagName();
        if (!"ul".equals(parentTagName) && !"ol".equals(parentTagName)) {
            return;
        }
        ComputedStyle style = box.getComputedStyle();
        if (style.getVisibility() == UiVisibility.HIDDEN) {
            return;
        }
        UiListStyleType listStyleType = style.getListStyleType();
        String markerText;
        if (listStyleType == UiListStyleType.NONE) {
            return;
        }
        if (listStyleType == UiListStyleType.DECIMAL) {
            markerText = String.valueOf(resolveListItemIndex(element)) + ".";
        } else if (listStyleType == UiListStyleType.CIRCLE) {
            markerText = "◦";
        } else if (listStyleType == UiListStyleType.SQUARE) {
            markerText = "▪";
        } else if (listStyleType == UiListStyleType.DISC) {
            markerText = "•";
        } else {
            return;
        }
        if (markerText.isEmpty() || box.getContentHeight() <= 0) {
            return;
        }
        int textColor = applyOpacity(style.getTextColor(), opacity);
        if (isTransparent(textColor)) {
            return;
        }
        int markerWidth = Math.max(4, markerText.length() * 8);
        int markerRight = box.getContentLeft() + offsetX - 6;
        int markerLeft = Math.max(box.getLeft() + offsetX, markerRight - markerWidth);
        int markerTop = box.getContentTop() + offsetY;
        int markerBottom = Math.min(box.getBottom() + offsetY, markerTop + Math.max(1, box.getContentHeight()));
        if (markerRight <= markerLeft || markerBottom <= markerTop) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, element, markerLeft, markerTop,
                markerRight, markerBottom, textColor, 0, 0, markerText, TextContentMode.UILIB_RAW,
                style.getFontWeight(), style.getFontStyle(),
                resolveTextMeasureStyle(style, TextContentMode.UILIB_RAW), null, 0, 1.0F, 1.0F));
    }

    private static TextMeasureStyle resolveTextMeasureStyle(ComputedStyle style, TextContentMode textContentMode) {
        int fontSizePx = style == null || style.getFontSize() == null
                ? TextMeasureStyle.DEFAULT_FONT_SIZE_PX
                : Math.max(1, style.getFontSize().resolve(TextMeasureStyle.DEFAULT_FONT_SIZE_PX,
                        TextMeasureStyle.DEFAULT_FONT_SIZE_PX));
        UiFontWeight fontWeight = style == null ? UiFontWeight.NORMAL : style.getFontWeight();
        UiFontStyle fontStyle = style == null ? UiFontStyle.NORMAL : style.getFontStyle();
        return new TextMeasureStyle(fontSizePx, textContentMode, fontWeight, fontStyle);
    }

    private static ElementNode resolveParentElement(ElementNode element) {
        if (element == null || !(element.getParent() instanceof ElementNode)) {
            return null;
        }
        return (ElementNode) element.getParent();
    }

    private static int resolveListItemIndex(ElementNode element) {
        ElementNode parent = resolveParentElement(element);
        if (parent == null) {
            return 1;
        }
        int index = 0;
        for (club.heiqi.uilib.ui.dom.DocumentNode child : parent.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            if (!"li".equals(childElement.getTagName())) {
                continue;
            }
            index++;
            if (childElement == element) {
                return index;
            }
        }
        return Math.max(1, index);
    }

    private static void appendTextDecorationCommand(DocumentLayoutTextRun textRun, List<DocumentPaintCommand> commands,
            int color, int commandLeft, int commandRight, int offsetY) {
        UiTextDecoration textDecoration = UiStyleResolver.compute(textRun.getOwnerElement()).getTextDecoration();
        if (textDecoration == UiTextDecoration.NONE || textRun.getWidth() <= 0 || textRun.getHeight() <= 0) {
            return;
        }
        int lineTop = resolveTextDecorationTop(textRun, textDecoration);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT_DECORATION, textRun.getOwnerElement(),
                commandLeft, lineTop + offsetY, commandRight,
                lineTop + 1 + offsetY, color, 0, 0));
    }

    private static int resolveTextDecorationTop(DocumentLayoutTextRun textRun, UiTextDecoration textDecoration) {
        if (textDecoration == UiTextDecoration.OVERLINE) {
            return textRun.getTop();
        }
        if (textDecoration == UiTextDecoration.LINE_THROUGH) {
            return textRun.getTop() + Math.max(0, textRun.getHeight() / 2);
        }
        return textRun.getBottom() - Math.max(1, textRun.getHeight() / 6);
    }

    private static void appendInlineFragmentSurfaceCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        for (DocumentLayoutInlineFragment inlineFragment : box.getInlineFragments()) {
            ElementNode ownerElement = inlineFragment.getOwnerElement();
            if (inlineFragment.getWidth() <= 0 || inlineFragment.getHeight() <= 0) {
                continue;
            }
            ComputedStyle ownerStyle = UiStyleResolver.compute(ownerElement);
            if (ownerStyle.getVisibility() == UiVisibility.HIDDEN) {
                continue;
            }
            int cornerMask = resolveInlineFragmentCornerMask(inlineFragment);
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveInlineFragmentBorderRadii(ownerStyle,
                    inlineFragment.getWidth(), inlineFragment.getHeight());
            int backgroundColor = resolveAnimatedColor(animationTimeline, ownerElement,
                    DocumentAnimationProperty.BACKGROUND_COLOR, ownerStyle.getBackgroundColor(), currentTimeNanos);
            backgroundColor = applyOpacity(backgroundColor, opacity);
            if (!isTransparent(backgroundColor)) {
                commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, ownerElement,
                        inlineFragment.getLeft() + offsetX, inlineFragment.getTop() + offsetY,
                        inlineFragment.getRight() + offsetX, inlineFragment.getBottom() + offsetY, backgroundColor, 0,
                        cornerRadii, cornerMask, null, null, 0, 1.0F, 1.0F, null));
            }
            int borderWidth = Math.max(0, ownerStyle.getBorderWidth().resolve(inlineFragment.getWidth(), 0));
            int borderColor = resolveAnimatedColor(animationTimeline, ownerElement, DocumentAnimationProperty.BORDER_COLOR,
                    ownerStyle.getBorderColor(), currentTimeNanos);
            borderColor = applyOpacity(borderColor, opacity);
            if (!isTransparent(borderColor) && borderWidth > 0
                    && ownerStyle.getBorderStyle() != UiBorderStyle.NONE
                    && ownerStyle.getBorderStyle() != UiBorderStyle.HIDDEN) {
                commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BORDER, ownerElement,
                        inlineFragment.getLeft() + offsetX, inlineFragment.getTop() + offsetY,
                        inlineFragment.getRight() + offsetX, inlineFragment.getBottom() + offsetY, borderColor,
                        borderWidth, cornerRadii, cornerMask, null, null, 0, 1.0F, 1.0F, null));
            }
        }
    }

    private static int resolveInlineFragmentCornerMask(DocumentLayoutInlineFragment inlineFragment) {
        boolean first = inlineFragment.isFirstForElement();
        boolean last = inlineFragment.isLastForElement();
        if (first && last) {
            return UiSurfaceStyle.CORNER_ALL;
        }
        int cornerMask = 0;
        if (first) {
            cornerMask |= UiSurfaceStyle.CORNER_TOP_LEFT | UiSurfaceStyle.CORNER_BOTTOM_LEFT;
        }
        if (last) {
            cornerMask |= UiSurfaceStyle.CORNER_TOP_RIGHT | UiSurfaceStyle.CORNER_BOTTOM_RIGHT;
        }
        return cornerMask;
    }

    private static UiBorderRadiusResolver.ResolvedCornerRadii resolveInlineFragmentBorderRadii(ComputedStyle style,
            int width, int height) {
        return UiBorderRadiusResolver.resolve(style, width, height);
    }

    private static int resolveTextRunColor(DocumentLayoutTextRun textRun, DocumentAnimationTimeline animationTimeline,
            long currentTimeNanos, float opacity) {
        ElementNode ownerElement = textRun.getOwnerElement();
        int baseColor = UiStyleResolver.compute(ownerElement).getTextColor();
        int color = resolveAnimatedColor(animationTimeline, ownerElement, DocumentAnimationProperty.TEXT_COLOR,
                baseColor, currentTimeNanos);
        return applyOpacity(color, opacity);
    }

    private static int resolveAnimatedColor(DocumentAnimationTimeline animationTimeline, DocumentLayoutBox box,
            DocumentAnimationProperty property, int baseColor, long currentTimeNanos) {
        return resolveAnimatedColor(animationTimeline, box.getElement(), property, baseColor, currentTimeNanos);
    }

    private static int resolveAnimatedColor(DocumentAnimationTimeline animationTimeline, ElementNode element,
            DocumentAnimationProperty property, int baseColor, long currentTimeNanos) {
        if (animationTimeline == null) {
            return baseColor;
        }
        return animationTimeline.resolveColor(element, property, baseColor, currentTimeNanos);
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

    private static UiTransform resolveAnimatedTransform(DocumentAnimationTimeline animationTimeline,
            DocumentLayoutBox box, long currentTimeNanos) {
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

    private static UiBoxShadow resolveAnimatedBoxShadow(DocumentLayoutBox box,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos) {
        UiBoxShadow baseShadow = box.getComputedStyle().getBoxShadow();
        int baseOffsetX = baseShadow == null ? 0 : baseShadow.getOffsetX();
        int baseOffsetY = baseShadow == null ? 0 : baseShadow.getOffsetY();
        int baseBlurRadius = baseShadow == null ? 0 : baseShadow.getBlurRadius();
        int baseSpreadRadius = baseShadow == null ? 0 : baseShadow.getSpreadRadius();
        int baseColor = baseShadow == null ? 0 : baseShadow.getColor();
        boolean inset = baseShadow != null && baseShadow.isInset();
        if (animationTimeline == null) {
            return baseShadow;
        }
        int offsetX = Math.round(animationTimeline.resolveFloat(box.getElement(),
                DocumentAnimationProperty.BOX_SHADOW_OFFSET_X, baseOffsetX, currentTimeNanos));
        int offsetY = Math.round(animationTimeline.resolveFloat(box.getElement(),
                DocumentAnimationProperty.BOX_SHADOW_OFFSET_Y, baseOffsetY, currentTimeNanos));
        int blurRadius = Math.round(animationTimeline.resolveFloat(box.getElement(),
                DocumentAnimationProperty.BOX_SHADOW_BLUR_RADIUS, baseBlurRadius, currentTimeNanos));
        int spreadRadius = Math.round(animationTimeline.resolveFloat(box.getElement(),
                DocumentAnimationProperty.BOX_SHADOW_SPREAD_RADIUS, baseSpreadRadius, currentTimeNanos));
        int color = animationTimeline.resolveColor(box.getElement(), DocumentAnimationProperty.BOX_SHADOW_COLOR,
                baseColor, currentTimeNanos);
        if (baseShadow == null && offsetX == 0 && offsetY == 0 && blurRadius == 0 && spreadRadius == 0
                && color == 0) {
            return null;
        }
        return inset ? UiBoxShadow.inset(offsetX, offsetY, blurRadius, spreadRadius, color)
                : UiBoxShadow.of(offsetX, offsetY, blurRadius, spreadRadius, color);
    }

    private static int resolveAnimatedBackdropBlurRadius(DocumentAnimationTimeline animationTimeline,
            DocumentLayoutBox box, int baseRadius, long currentTimeNanos, BackdropBlurPolicy backdropBlurPolicy) {
        if (animationTimeline == null) {
            return baseRadius;
        }
        float animatedRadius = animationTimeline.resolveFloat(box.getElement(),
                DocumentAnimationProperty.BACKDROP_BLUR_RADIUS, baseRadius, currentTimeNanos);
        int maxRadius = backdropBlurPolicy.resolveMaxBlurRadius(BackdropBlurConfig.getInstance());
        return Math.max(0, Math.min(Math.round(animatedRadius), maxRadius));
    }

    private static BackdropBlurPolicy resolveBackdropBlurPolicy(DocumentLayoutBox box) {
        if (box == null || box.getElement() == null || box.getElement().getOwnerDocument() == null) {
            return BackdropBlurPolicy.inheritGlobal();
        }
        return box.getElement().getOwnerDocument().getBackdropBlurController().getPolicy();
    }

    private static boolean hasRunningTransition(DocumentAnimationTimeline animationTimeline, DocumentLayoutBox box,
            DocumentAnimationProperty property) {
        return animationTimeline != null && animationTimeline.hasRunningTransition(box.getElement(), property);
    }

    private static boolean hasBackdropFilter(int blurRadius, float saturation) {
        return blurRadius > 0 || Float.compare(saturation, 1.0F) != 0;
    }

    private static StackingContextResolver createPaintStackingContextResolver(final long currentTimeNanos,
            final DocumentAnimationTimeline animationTimeline) {
        return new StackingContextResolver() {
            @Override
            public boolean createsStackingContext(DocumentLayoutBox box) {
                return DocumentVisualTraversal.createsRuntimeStackingContext(box, currentTimeNanos,
                        animationTimeline);
            }
        };
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

    private static void appendClipChainStart(List<DocumentPaintCommand> commands, List<ClipContext> clipChain,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos) {
        if (clipChain == null || clipChain.isEmpty()) {
            return;
        }
        for (ClipContext clipContext : clipChain) {
            appendClipStartCommand(clipContext.getBox(), commands, animationTimeline, currentTimeNanos,
                    clipContext.getEffectChain(), clipContext.getBoxOffsetX(), clipContext.getBoxOffsetY());
        }
    }

    private static void appendClipChainEnd(List<DocumentPaintCommand> commands, List<ClipContext> clipChain) {
        if (clipChain == null || clipChain.isEmpty()) {
            return;
        }
        for (int index = clipChain.size() - 1; index >= 0; index--) {
            ClipContext clipContext = clipChain.get(index);
            appendClipEndCommand(clipContext.getBox(), commands, clipContext.getBoxOffsetX(),
                    clipContext.getBoxOffsetY());
        }
    }

    private static List<ClipContext> transitionClipChain(List<DocumentPaintCommand> commands,
            List<ClipContext> currentClipChain, List<ClipContext> targetClipChain,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos) {
        List<ClipContext> current = currentClipChain == null ? Collections.<ClipContext>emptyList() : currentClipChain;
        List<ClipContext> target = targetClipChain == null ? Collections.<ClipContext>emptyList() : targetClipChain;
        int sharedPrefix = 0;
        int maxShared = Math.min(current.size(), target.size());
        while (sharedPrefix < maxShared && current.get(sharedPrefix) == target.get(sharedPrefix)) {
            sharedPrefix++;
        }
        for (int index = current.size() - 1; index >= sharedPrefix; index--) {
            ClipContext clipContext = current.get(index);
            appendClipEndCommand(clipContext.getBox(), commands, clipContext.getBoxOffsetX(), clipContext.getBoxOffsetY());
        }
        for (int index = sharedPrefix; index < target.size(); index++) {
            ClipContext clipContext = target.get(index);
            appendClipStartCommand(clipContext.getBox(), commands, animationTimeline, currentTimeNanos,
                    clipContext.getEffectChain(), clipContext.getBoxOffsetX(), clipContext.getBoxOffsetY());
        }
        return target;
    }

    private static void appendClipStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, DocumentEffectChain effectChain,
            int offsetX, int offsetY) {
        DocumentEffectChain.ClipBounds clipBounds = effectChain.resolveChildClipBounds(offsetX, offsetY);
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CLIP_START, box.getElement(),
                clipBounds.getLeft(), clipBounds.getTop(), clipBounds.getRight(), clipBounds.getBottom(), 0, 0,
                cornerRadii, UiSurfaceStyle.CORNER_ALL, null, null, 0, 1.0F, 1.0F,
                DocumentEffectType.OVERFLOW_CLIP));
    }

    private static void appendClipEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands, int offsetX,
            int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CLIP_END, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, 0, null, null, 0, 1.0F, 1.0F,
                DocumentEffectType.OVERFLOW_CLIP));
    }

    private static void appendScrollbarCommands(DocumentLayoutBox rootBox, DocumentLayoutBox box,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState, int offsetX, int offsetY,
            long currentTimeNanos, float opacity) {
        if (scrollState == null || box.getContentWidth() <= 0 || box.getContentHeight() <= 0) {
            return;
        }
        int maxScrollTop = scrollState.getMaxScrollTop(box.getElement());
        int maxScrollLeft = scrollState.getMaxScrollLeft(box.getElement());
        boolean hasVerticalScrollbar = maxScrollTop > 0
                && (box.getComputedStyle().getOverflowY() == UiOverflow.AUTO
                        || box.getComputedStyle().getOverflowY() == UiOverflow.SCROLL);
        boolean hasHorizontalScrollbar = maxScrollLeft > 0
                && (box.getComputedStyle().getOverflowX() == UiOverflow.AUTO
                        || box.getComputedStyle().getOverflowX() == UiOverflow.SCROLL);
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
        UiScrollbarColor scrollbarColor = box.getComputedStyle().getScrollbarColor();
        int trackColor = applyOpacity(scrollbarColor.getTrackColor(), opacity);
        int thumbColor = applyOpacity(scrollbarColor.getThumbColor(), opacity);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLLBAR_TRACK, box.getElement(),
                metrics.getTrackLeft(),
                metrics.getTrackTop(), metrics.getTrackRight(), metrics.getTrackBottom(), trackColor, 0,
                radius));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLLBAR_THUMB, box.getElement(),
                metrics.getThumbLeft(), metrics.getThumbTop(), metrics.getThumbRight(), metrics.getThumbBottom(),
                thumbColor, 0, radius));
    }

    private static UiBorderRadiusResolver.ResolvedCornerRadii resolveBorderRadii(DocumentLayoutBox box,
            DocumentAnimationTimeline animationTimeline,
            long currentTimeNanos) {
        UiBorderRadiusResolver.ResolvedCornerRadii radii = resolveStaticBorderRadii(box);
        if (hasRunningTransition(animationTimeline, box, DocumentAnimationProperty.BORDER_RADIUS)) {
            int radius = Math.round(animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.BORDER_RADIUS,
                    radii.getUniformRadius(), currentTimeNanos));
            radii = UiBorderRadiusResolver.resolve(box.getComputedStyle(), box.getWidth(), box.getHeight(),
                    Integer.valueOf(radius));
        }
        return radii;
    }

    private static UiBorderRadiusResolver.ResolvedCornerRadii resolveStaticBorderRadii(DocumentLayoutBox box) {
        return UiBorderRadiusResolver.resolve(box.getComputedStyle(), box.getWidth(), box.getHeight());
    }

    private static UiStyleInsets resolveBorderWidthSides(ComputedStyle style) {
        UiStyleInsets borderWidthSides = style.getBorderWidthSides();
        return borderWidthSides == null ? UiStyleInsets.all(style.getBorderWidth()) : borderWidthSides;
    }

    /**
     * 判断布局盒是否因 visibility:hidden 而不绘制。
     */
    private static boolean isVisibilityHidden(DocumentLayoutBox box) {
        return isVisibilityHidden(box.getElement());
    }

    private static boolean isVisibilityHidden(ElementNode element) {
        if (element == null) {
            return false;
        }
        return UiStyleResolver.compute(element).getVisibility() == UiVisibility.HIDDEN;
    }

}
