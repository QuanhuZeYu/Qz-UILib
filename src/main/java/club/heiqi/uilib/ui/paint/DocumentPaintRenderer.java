package club.heiqi.uilib.ui.paint;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentEffectType;
import club.heiqi.uilib.ui.layout.DocumentLayoutEdges;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.values.UiBorderColors;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.values.UiOutline;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.props.UiBorderCollapse;
import club.heiqi.uilib.ui.style.values.UiTextShadow;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;

/**
 * 将 HTML-like 绘制命令投影到现有 UI 渲染上下文。
 */
public final class DocumentPaintRenderer {

    private static final class OpenRenderState {

        private final DocumentPaintCommandType commandType;
        private final DocumentEffectType effectType;
        private final float previousFallbackOpacity;

        private OpenRenderState(DocumentPaintCommandType commandType, DocumentEffectType effectType,
                float previousFallbackOpacity) {
            this.commandType = commandType;
            this.effectType = effectType;
            this.previousFallbackOpacity = previousFallbackOpacity;
        }
    }

    private static final class RenderReplayState {

        private final Deque<OpenRenderState> openStates = new ArrayDeque<OpenRenderState>();
        private float fallbackOpacity = 1.0F;
        private int transformDepth;

        private void pushClip() {
            openStates.push(new OpenRenderState(DocumentPaintCommandType.CLIP_START,
                    DocumentEffectType.OVERFLOW_CLIP, fallbackOpacity));
        }

        private void pushPaintContext(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY) {
            float previousFallbackOpacity = fallbackOpacity;
            context.pushPaintContext(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY, command.getPaintContextOpacity());
            if (!context.isCurrentPaintContextLayerActive()) {
                fallbackOpacity *= command.getPaintContextOpacity();
            }
            openStates.push(new OpenRenderState(DocumentPaintCommandType.PAINT_CONTEXT_START,
                    DocumentEffectType.PAINT_CONTEXT, previousFallbackOpacity));
        }

        private void pushTransform(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY) {
            context.pushTransform(command.getTransform(), command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY);
            transformDepth++;
            openStates.push(new OpenRenderState(DocumentPaintCommandType.TRANSFORM_START, null, fallbackOpacity));
        }

        private void popTransform(UiRenderContext context) {
            context.popTransform();
            if (transformDepth > 0) {
                transformDepth--;
            }
        }

        private boolean isTransformActive() {
            return transformDepth > 0;
        }

        private boolean isEmpty() {
            return openStates.isEmpty();
        }

        private OpenRenderState peek() {
            return openStates.peek();
        }

        private OpenRenderState pop() {
            return openStates.pop();
        }
    }

    private DocumentPaintRenderer() {}

    /**
     * 解析元素的计算样式，并在单趟绘制重放内按元素实例备忘，避免逐命令递归到文档根重复级联。
     *
     * <p>绘制重放统一走 1 参样式入口（无 activeStates，交互伪类不参与），单趟内元素样式不变，
     * 因此按元素实例缓存是确定性的、安全的。普通元素经 {@link UiStyleResolver#computeWithParentStyle}
     * 自顶向下复用父级缓存，使每个元素每趟仅做一次单层级联；伪元素级联语义特殊，回退到原始
     * {@link UiStyleResolver#compute(ElementNode)} 入口，结果同样写入备忘表。</p>
     *
     * @param element 目标元素
     * @param styleMemo 单趟绘制的元素样式备忘表（每趟绘制新建，单趟内不失效）
     * @return 元素计算样式
     */
    private static ComputedStyle resolveStyle(ElementNode element, Map<ElementNode, ComputedStyle> styleMemo) {
        ComputedStyle cached = styleMemo.get(element);
        if (cached != null) {
            return cached;
        }
        ComputedStyle style;
        if (element.isPseudoElement()) {
            // 伪元素 origin/runtime 级联语义特殊，保持原始入口，避免破坏匹配结果
            style = UiStyleResolver.compute(element);
        } else {
            club.heiqi.uilib.ui.dom.DocumentNode parent = element.getParent();
            ComputedStyle parentStyle = parent instanceof ElementNode
                    ? resolveStyle((ElementNode) parent, styleMemo)
                    : null;
            style = UiStyleResolver.computeWithParentStyle(element, parentStyle);
        }
        styleMemo.put(element, style);
        return style;
    }

    /**
     * 解析命令对应元素的计算样式，优先使用构建期固化在命令上的样式快照。
     *
     * <p>绘制命令缓存与布局/绘制版本号绑定，命令存活期间元素样式不变，因此构建期固化的样式在回放期
     * 始终有效，可避免每帧对每条命令重算 computed style。固化样式缺失时（如旧构造路径或测试直接构造的
     * 命令）回退到按元素实例备忘的单趟级联。</p>
     *
     * @param command 绘制命令
     * @param styleMemo 单趟绘制的元素样式备忘表
     * @return 元素计算样式
     */
    private static ComputedStyle resolveCommandStyle(DocumentPaintCommand command,
            Map<ElementNode, ComputedStyle> styleMemo) {
        ComputedStyle bakedStyle = command.getElementStyle();
        if (bakedStyle != null) {
            return bakedStyle;
        }
        return resolveStyle(command.getElement(), styleMemo);
    }

    /**
     * 渲染一组绘制命令。
     *
     * @param context 渲染上下文
     * @param commands 绘制命令列表
     */
    public static void render(UiRenderContext context, List<DocumentPaintCommand> commands) {
        render(context, commands, 0, 0);
    }

    /**
     * 以指定屏幕偏移渲染一组绘制命令。
     *
     * @param context 渲染上下文
     * @param commands 绘制命令列表
     * @param offsetX 绘制命令整体 X 偏移
     * @param offsetY 绘制命令整体 Y 偏移
     */
    public static void render(UiRenderContext context, List<DocumentPaintCommand> commands, int offsetX, int offsetY) {
        Objects.requireNonNull(context, "context");
        if (commands == null || commands.isEmpty()) {
            return;
        }
        RenderReplayState replayState = new RenderReplayState();
        Map<ElementNode, ComputedStyle> styleMemo = new IdentityHashMap<ElementNode, ComputedStyle>();
        try {
            int commandIndex = 0;
            while (commandIndex < commands.size()) {
                DocumentPaintCommand command = commands.get(commandIndex);
                if (isRenderableTextCommand(command, replayState)) {
                    if (isBatchableTextCommand(command, replayState)) {
                        commandIndex = renderTextBatch(context, commands, commandIndex, offsetX, offsetY,
                                replayState, styleMemo);
                    } else {
                        renderTextCommand(context, command, offsetX, offsetY, replayState, styleMemo);
                        commandIndex++;
                    }
                    continue;
                }
                renderCommand(context, command, offsetX, offsetY, replayState, styleMemo);
                commandIndex++;
            }
        } finally {
            while (!replayState.isEmpty()) {
                popOpenState(context, replayState, replayState.pop());
            }
        }
    }

    private static int renderTextBatch(UiRenderContext context, List<DocumentPaintCommand> commands, int startIndex,
            int offsetX, int offsetY, RenderReplayState replayState, Map<ElementNode, ComputedStyle> styleMemo) {
        if (!context.supportsDeferredTextBatching()) {
            int commandIndex = startIndex;
            while (commandIndex < commands.size()) {
                DocumentPaintCommand command = commands.get(commandIndex);
                if (!isBatchableTextCommand(command, replayState)) {
                    break;
                }
                renderTextCommand(context, command, offsetX, offsetY, replayState, styleMemo);
                commandIndex++;
            }
            return commandIndex;
        }
        int commandIndex = startIndex;
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            context.beginDeferredTextBatch(context.getScreenWidth(), context.getScreenHeight());
            try {
                while (commandIndex < commands.size()) {
                    DocumentPaintCommand command = commands.get(commandIndex);
                    if (!isBatchableTextCommand(command, replayState)) {
                        break;
                    }
                    renderTextCommand(context, command, offsetX, offsetY, replayState, styleMemo);
                    commandIndex++;
                }
                return commandIndex;
            } finally {
                try {
                    context.flushDeferredTextBatch();
                } finally {
                    context.endDeferredTextBatch();
                }
            }
        }
    }

    private static void renderCommand(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY,
            RenderReplayState replayState, Map<ElementNode, ComputedStyle> styleMemo) {
        if (command == null) {
            return;
        }
        if (command.getType() == DocumentPaintCommandType.TRANSFORM_END) {
            popExpectedTransformState(context, replayState);
            return;
        }
        if (command.getType() == DocumentPaintCommandType.TRANSFORM_START) {
            replayState.pushTransform(context, command, offsetX, offsetY);
            return;
        }
        if (isEffectEndCommand(command)) {
            popExpectedRenderState(context, replayState, command.getEffectType());
            return;
        }
        if (isEffectStartCommand(command)) {
            pushEffectState(context, command, offsetX, offsetY, replayState, styleMemo);
            return;
        }
        if (command.getWidth() <= 0 || command.getHeight() <= 0) {
            return;
        }
        if (replayState.fallbackOpacity <= 0.001F) {
            return;
        }
        if (renderStatelessEffect(context, command, offsetX, offsetY, styleMemo)) {
            return;
        }
        if (command.getType() == DocumentPaintCommandType.BACKGROUND) {
            context.drawSurface(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    new UiSurfaceStyle(applyOpacity(command.getColor(), replayState.fallbackOpacity), 0,
                            resolveCommandCornerRadii(command, true, styleMemo), command.getCornerMask()));
            return;
        }
        if (command.getType() == DocumentPaintCommandType.BACKGROUND_IMAGE) {
            renderBackgroundImage(context, command, offsetX, offsetY, styleMemo);
            return;
        }
        if (command.getType() == DocumentPaintCommandType.SCROLLBAR_TRACK
                || command.getType() == DocumentPaintCommandType.SCROLLBAR_THUMB) {
            context.drawSurface(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    new UiSurfaceStyle(applyOpacity(command.getColor(), replayState.fallbackOpacity), 0,
                            resolveCommandCornerRadii(command, false, styleMemo), command.getCornerMask()));
            return;
        }
        if (command.getType() == DocumentPaintCommandType.BOX_SHADOW
                || command.getType() == DocumentPaintCommandType.BOX_SHADOW_INSET) {
            renderBoxShadow(context, command, offsetX, offsetY, replayState.fallbackOpacity, styleMemo);
            return;
        }
        if (command.getType() == DocumentPaintCommandType.BORDER) {
            renderBorder(context, command, offsetX, offsetY, replayState.fallbackOpacity, styleMemo);
            return;
        }
        if (command.getType() == DocumentPaintCommandType.OUTLINE) {
            renderOutline(context, command, offsetX, offsetY, replayState.fallbackOpacity, styleMemo);
            return;
        }
        if (command.getType() == DocumentPaintCommandType.TEXT_DECORATION) {
            context.drawSurface(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    new UiSurfaceStyle(applyOpacity(command.getColor(), replayState.fallbackOpacity), 0, 0));
            return;
        }
        if (command.getType() == DocumentPaintCommandType.CUSTOM) {
            DocumentCustomRenderer customRenderer = command.getCustomRenderer();
            if (customRenderer != null) {
                customRenderer.render(context, command.getLeft() + offsetX, command.getTop() + offsetY,
                        command.getRight() + offsetX, command.getBottom() + offsetY);
                context.notifyMainLayerContentChanged();
            }
            return;
        }
    }

    private static void renderTextCommand(UiRenderContext context, DocumentPaintCommand command, int offsetX,
            int offsetY, RenderReplayState replayState, Map<ElementNode, ComputedStyle> styleMemo) {
        renderTextShadow(context, command, offsetX, offsetY, replayState.fallbackOpacity, styleMemo);
        context.drawText(command.getText(), command.getLeft() + offsetX, command.getTop() + offsetY,
                applyOpacity(command.getColor(), replayState.fallbackOpacity), false, command.getTextMeasureStyle());
    }

    private static void renderBackgroundImage(UiRenderContext context, DocumentPaintCommand command, int offsetX,
            int offsetY, Map<ElementNode, ComputedStyle> styleMemo) {
        UiBackgroundImage backgroundImage = command.getBackgroundImage();
        if (backgroundImage == null) {
            return;
        }
        int left = command.getLeft() + offsetX;
        int top = command.getTop() + offsetY;
        int right = command.getRight() + offsetX;
        int bottom = command.getBottom() + offsetY;
        context.pushClip(left, top, right, bottom, resolveCommandCornerRadii(command, true, styleMemo));
        try {
            context.drawHostImage(backgroundImage.getSource(), left, top, right, bottom);
        } finally {
            context.popClip();
        }
    }

    private static void renderTextShadow(UiRenderContext context, DocumentPaintCommand command, int offsetX,
            int offsetY, float fallbackOpacity, Map<ElementNode, ComputedStyle> styleMemo) {
        UiTextShadow textShadow = resolveCommandStyle(command, styleMemo).getTextShadow();
        if (textShadow == null) {
            return;
        }
        int color = applyOpacity(textShadow.getColor(), fallbackOpacity);
        if (((color >>> 24) & 0xFF) == 0) {
            return;
        }
        int x = command.getLeft() + offsetX + textShadow.getOffsetX();
        int y = command.getTop() + offsetY + textShadow.getOffsetY();
        int blurRadius = Math.min(Math.max(0, textShadow.getBlurRadius()), 3);
        if (blurRadius <= 0) {
            context.drawText(command.getText(), x, y, color, false, command.getTextMeasureStyle());
            return;
        }
        int haloColor = fadeColor(color, 1, blurRadius + 2);
        for (int radius = blurRadius; radius >= 1; radius--) {
            context.drawText(command.getText(), x - radius, y, haloColor, false, command.getTextMeasureStyle());
            context.drawText(command.getText(), x + radius, y, haloColor, false, command.getTextMeasureStyle());
            context.drawText(command.getText(), x, y - radius, haloColor, false, command.getTextMeasureStyle());
            context.drawText(command.getText(), x, y + radius, haloColor, false, command.getTextMeasureStyle());
        }
        context.drawText(command.getText(), x, y, color, false, command.getTextMeasureStyle());
    }

    private static boolean isBatchableTextCommand(DocumentPaintCommand command, RenderReplayState replayState) {
        return isRenderableTextCommand(command, replayState) && !replayState.isTransformActive();
    }

    private static boolean isRenderableTextCommand(DocumentPaintCommand command, RenderReplayState replayState) {
        return command != null
                && command.getType() == DocumentPaintCommandType.TEXT
                && command.getWidth() > 0
                && command.getHeight() > 0
                && replayState.fallbackOpacity > 0.001F;
    }

    private static void popExpectedRenderState(UiRenderContext context, RenderReplayState replayState,
            DocumentEffectType expectedEffectType) {
        if (!replayState.isEmpty() && replayState.peek().effectType == expectedEffectType) {
            popOpenState(context, replayState, replayState.pop());
        }
    }

    private static void popExpectedTransformState(UiRenderContext context, RenderReplayState replayState) {
        if (!replayState.isEmpty() && replayState.peek().commandType == DocumentPaintCommandType.TRANSFORM_START) {
            popOpenState(context, replayState, replayState.pop());
        }
    }

    private static void popOpenState(UiRenderContext context, RenderReplayState replayState, OpenRenderState state) {
        if (state.commandType == DocumentPaintCommandType.TRANSFORM_START) {
            replayState.popTransform(context);
            return;
        }
        if (state.effectType == DocumentEffectType.OVERFLOW_CLIP) {
            context.popClip();
            return;
        }
        context.popPaintContext();
        replayState.fallbackOpacity = state.previousFallbackOpacity;
    }

    private static boolean isEffectStartCommand(DocumentPaintCommand command) {
        return command.getType() == DocumentPaintCommandType.PAINT_CONTEXT_START
                || command.getType() == DocumentPaintCommandType.CLIP_START;
    }

    private static boolean isEffectEndCommand(DocumentPaintCommand command) {
        return command.getType() == DocumentPaintCommandType.PAINT_CONTEXT_END
                || command.getType() == DocumentPaintCommandType.CLIP_END;
    }

    private static void pushEffectState(UiRenderContext context, DocumentPaintCommand command, int offsetX,
            int offsetY, RenderReplayState replayState, Map<ElementNode, ComputedStyle> styleMemo) {
        if (command.getEffectType() == DocumentEffectType.PAINT_CONTEXT) {
            replayState.pushPaintContext(context, command, offsetX, offsetY);
            return;
        }
        if (command.getEffectType() == DocumentEffectType.OVERFLOW_CLIP) {
            context.pushClip(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    resolveCommandCornerRadii(command, true, styleMemo));
            replayState.pushClip();
        }
    }

    private static boolean renderStatelessEffect(UiRenderContext context, DocumentPaintCommand command, int offsetX,
            int offsetY, Map<ElementNode, ComputedStyle> styleMemo) {
        if (command.getEffectType() != DocumentEffectType.BACKDROP_FILTER) {
            return false;
        }
        context.drawBackdropFilter(command.getLeft() + offsetX, command.getTop() + offsetY,
                command.getRight() + offsetX, command.getBottom() + offsetY,
                command.getBackdropBlurRadius(), command.getBackdropSaturation(),
                resolveCommandCornerRadii(command, true, styleMemo));
        return true;
    }

    private static void renderBorder(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY,
            float fallbackOpacity, Map<ElementNode, ComputedStyle> styleMemo) {
        ComputedStyle style = resolveCommandStyle(command, styleMemo);
        UiBorderStyle borderStyle = style.getBorderStyle();
        if (borderStyle == UiBorderStyle.HIDDEN || borderStyle == UiBorderStyle.NONE) {
            return;
        }
        DocumentLayoutEdges widths = resolveBorderWidths(command, style, styleMemo);
        int topColor = resolveBorderColor(style.getBorderColors(), command.getColor(), fallbackOpacity, 0);
        int rightColor = resolveBorderColor(style.getBorderColors(), command.getColor(), fallbackOpacity, 1);
        int bottomColor = resolveBorderColor(style.getBorderColors(), command.getColor(), fallbackOpacity, 2);
        int leftColor = resolveBorderColor(style.getBorderColors(), command.getColor(), fallbackOpacity, 3);
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveCommandCornerRadii(command, true, styleMemo);
        renderRing(context, command.getLeft() + offsetX, command.getTop() + offsetY, command.getRight() + offsetX,
                command.getBottom() + offsetY, widths, topColor, rightColor, bottomColor, leftColor, borderStyle,
                cornerRadii, command.getCornerMask());
    }

    private static void renderOutline(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY,
            float fallbackOpacity, Map<ElementNode, ComputedStyle> styleMemo) {
        ComputedStyle style = resolveCommandStyle(command, styleMemo);
        UiOutline outline = style.getOutline();
        if (outline == null || outline.isNone()) {
            return;
        }
        UiBorderStyle outlineStyle = outline.getStyle();
        if (outlineStyle == UiBorderStyle.NONE || outlineStyle == UiBorderStyle.HIDDEN) {
            return;
        }
        int width = Math.max(0, outline.getWidth());
        if (width <= 0) {
            return;
        }
        int offset = Math.max(0, outline.getOffset());
        DocumentLayoutEdges widths = DocumentLayoutEdges.of(width, width, width, width);
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveCommandCornerRadii(command, true, styleMemo)
                .outset(width + offset);
        int left = command.getLeft() + offsetX - width - offset;
        int top = command.getTop() + offsetY - width - offset;
        int right = command.getRight() + offsetX + width + offset;
        int bottom = command.getBottom() + offsetY + width + offset;
        int color = applyOpacity(command.getColor(), fallbackOpacity);
        renderRing(context, left, top, right, bottom, widths, color, color, color, color, outlineStyle, cornerRadii,
                command.getCornerMask());
    }

    private static void renderBoxShadow(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY,
            float fallbackOpacity, Map<ElementNode, ComputedStyle> styleMemo) {
        UiBoxShadow boxShadow = command.getBoxShadow();
        if (boxShadow == null) {
            ComputedStyle style = resolveCommandStyle(command, styleMemo);
            boxShadow = style.getBoxShadow();
        }
        if (boxShadow == null) {
            return;
        }
        int color = applyOpacity(command.getColor(), fallbackOpacity);
        int steps = Math.max(1, boxShadow.getBlurRadius());
        UiBorderRadiusResolver.ResolvedCornerRadii baseRadii = resolveCommandCornerRadii(command, true, styleMemo);
        if (!boxShadow.isInset()) {
            for (int index = steps; index >= 0; index--) {
                int expand = Math.max(0, boxShadow.getSpreadRadius()) + index;
                int layerColor = fadeColor(color, index + 1, steps + 1);
                context.drawSurface(command.getLeft() + offsetX + boxShadow.getOffsetX() - expand,
                        command.getTop() + offsetY + boxShadow.getOffsetY() - expand,
                        command.getRight() + offsetX + boxShadow.getOffsetX() + expand,
                        command.getBottom() + offsetY + boxShadow.getOffsetY() + expand, layerColor, 0,
                        baseRadii.outset(expand));
            }
            return;
        }
        context.pushClip(command.getLeft() + offsetX, command.getTop() + offsetY, command.getRight() + offsetX,
                command.getBottom() + offsetY, baseRadii);
        try {
            for (int index = 0; index <= steps; index++) {
                int inset = Math.max(0, boxShadow.getSpreadRadius()) + index;
                int layerColor = fadeColor(color, steps - index + 1, steps + 1);
                int left = command.getLeft() + offsetX + Math.max(0, -boxShadow.getOffsetX()) + inset;
                int top = command.getTop() + offsetY + Math.max(0, -boxShadow.getOffsetY()) + inset;
                int right = command.getRight() + offsetX - Math.max(0, boxShadow.getOffsetX()) - inset;
                int bottom = command.getBottom() + offsetY - Math.max(0, boxShadow.getOffsetY()) - inset;
                if (right <= left || bottom <= top) {
                    break;
                }
                context.drawSurface(left, top, right, bottom, new UiSurfaceStyle(0, layerColor,
                        insetCornerRadii(baseRadii, inset)));
            }
        } finally {
            context.popClip();
        }
    }

    private static void renderRing(UiRenderContext context, int left, int top, int right, int bottom,
            DocumentLayoutEdges widths, int topColor, int rightColor, int bottomColor, int leftColor,
            UiBorderStyle borderStyle, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int cornerMask) {
        if (canRenderLayeredRoundedSolidBorder(widths, topColor, rightColor, bottomColor, leftColor, borderStyle,
                cornerRadii, cornerMask)) {
            renderLayeredRoundedSolidBorder(context, left, top, right, bottom, widths.getTop(), topColor,
                    cornerRadii, cornerMask);
            return;
        }
        context.pushClip(left, top, right, bottom, applyCornerMask(cornerRadii, cornerMask));
        try {
            if (borderStyle == UiBorderStyle.DOUBLE) {
                renderDoubleBorder(context, left, top, right, bottom, widths, topColor, rightColor, bottomColor,
                        leftColor);
                return;
            }
            if (borderStyle == UiBorderStyle.DASHED || borderStyle == UiBorderStyle.DOTTED) {
                renderPatternBorder(context, left, top, right, bottom, widths, topColor, rightColor, bottomColor,
                        leftColor, borderStyle == UiBorderStyle.DOTTED);
                return;
            }
            renderSolidBorder(context, left, top, right, bottom, widths, topColor, rightColor, bottomColor,
                    leftColor);
        } finally {
            context.popClip();
        }
    }

    private static boolean canRenderLayeredRoundedSolidBorder(DocumentLayoutEdges widths, int topColor,
            int rightColor, int bottomColor, int leftColor, UiBorderStyle borderStyle,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int cornerMask) {
        return borderStyle == UiBorderStyle.SOLID
                && isUniformPositiveWidth(widths)
                && topColor == rightColor
                && rightColor == bottomColor
                && bottomColor == leftColor
                && ((topColor >>> 24) & 0xFF) > 0
                && hasAnyCornerRadius(cornerRadii, cornerMask);
    }

    private static void renderLayeredRoundedSolidBorder(UiRenderContext context, int left, int top, int right,
            int bottom, int width, int color, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii,
            int cornerMask) {
        for (int layer = 0; layer < width; layer++) {
            int layerLeft = left + layer;
            int layerTop = top + layer;
            int layerRight = right - layer;
            int layerBottom = bottom - layer;
            if (layerRight <= layerLeft || layerBottom <= layerTop) {
                return;
            }
            UiBorderRadiusResolver.ResolvedCornerRadii layerRadii = insetCornerRadii(cornerRadii, layer);
            context.drawSurface(layerLeft, layerTop, layerRight, layerBottom,
                    new UiSurfaceStyle(0, color, layerRadii, cornerMask));
        }
    }

    private static void renderSolidBorder(UiRenderContext context, int left, int top, int right, int bottom,
            DocumentLayoutEdges widths, int topColor, int rightColor, int bottomColor, int leftColor) {
        if (widths.getTop() > 0 && ((topColor >>> 24) & 0xFF) > 0) {
            context.drawSurface(left, top, right, top + widths.getTop(), new UiSurfaceStyle(topColor, 0, 0));
        }
        if (widths.getRight() > 0 && ((rightColor >>> 24) & 0xFF) > 0) {
            context.drawSurface(right - widths.getRight(), top, right, bottom, new UiSurfaceStyle(rightColor, 0, 0));
        }
        if (widths.getBottom() > 0 && ((bottomColor >>> 24) & 0xFF) > 0) {
            context.drawSurface(left, bottom - widths.getBottom(), right, bottom,
                    new UiSurfaceStyle(bottomColor, 0, 0));
        }
        if (widths.getLeft() > 0 && ((leftColor >>> 24) & 0xFF) > 0) {
            context.drawSurface(left, top, left + widths.getLeft(), bottom, new UiSurfaceStyle(leftColor, 0, 0));
        }
    }

    private static void renderDoubleBorder(UiRenderContext context, int left, int top, int right, int bottom,
            DocumentLayoutEdges widths, int topColor, int rightColor, int bottomColor, int leftColor) {
        int outerTop = Math.max(1, widths.getTop() / 3);
        int outerRight = Math.max(1, widths.getRight() / 3);
        int outerBottom = Math.max(1, widths.getBottom() / 3);
        int outerLeft = Math.max(1, widths.getLeft() / 3);
        DocumentLayoutEdges outer = DocumentLayoutEdges.of(outerTop, outerRight, outerBottom, outerLeft);
        renderSolidBorder(context, left, top, right, bottom, outer, topColor, rightColor, bottomColor, leftColor);
        int innerLeft = left + Math.max(0, widths.getLeft() - outerLeft);
        int innerTop = top + Math.max(0, widths.getTop() - outerTop);
        int innerRight = right - Math.max(0, widths.getRight() - outerRight);
        int innerBottom = bottom - Math.max(0, widths.getBottom() - outerBottom);
        renderSolidBorder(context, innerLeft, innerTop, innerRight, innerBottom, outer, topColor, rightColor,
                bottomColor, leftColor);
    }

    private static void renderPatternBorder(UiRenderContext context, int left, int top, int right, int bottom,
            DocumentLayoutEdges widths, int topColor, int rightColor, int bottomColor, int leftColor,
            boolean dotted) {
        renderHorizontalPattern(context, left, right, top, widths.getTop(), topColor, dotted, true);
        renderHorizontalPattern(context, left, right, bottom - widths.getBottom(), widths.getBottom(), bottomColor,
                dotted, false);
        renderVerticalPattern(context, top, bottom, left, widths.getLeft(), leftColor, dotted, true);
        renderVerticalPattern(context, top, bottom, right - widths.getRight(), widths.getRight(), rightColor, dotted,
                false);
    }

    private static void renderHorizontalPattern(UiRenderContext context, int left, int right, int top, int height,
            int color, boolean dotted, boolean isTop) {
        if (height <= 0 || ((color >>> 24) & 0xFF) <= 0) {
            return;
        }
        int unit = Math.max(1, dotted ? height : height * 3);
        int gap = Math.max(1, dotted ? height : height * 2);
        for (int cursor = left; cursor < right; cursor += unit + gap) {
            int segmentRight = Math.min(right, cursor + unit);
            context.drawSurface(cursor, top, segmentRight, top + height, new UiSurfaceStyle(color, 0, 0));
        }
    }

    private static void renderVerticalPattern(UiRenderContext context, int top, int bottom, int left, int width,
            int color, boolean dotted, boolean isLeft) {
        if (width <= 0 || ((color >>> 24) & 0xFF) <= 0) {
            return;
        }
        int unit = Math.max(1, dotted ? width : width * 3);
        int gap = Math.max(1, dotted ? width : width * 2);
        for (int cursor = top; cursor < bottom; cursor += unit + gap) {
            int segmentBottom = Math.min(bottom, cursor + unit);
            context.drawSurface(left, cursor, left + width, segmentBottom, new UiSurfaceStyle(color, 0, 0));
        }
    }

    private static DocumentLayoutEdges resolveBorderWidths(DocumentPaintCommand command, ComputedStyle style,
            Map<ElementNode, ComputedStyle> styleMemo) {
        UiStyleInsets borderWidthSides = style.getBorderWidthSides();
        DocumentLayoutEdges resolvedWidths;
        if (borderWidthSides != null) {
            resolvedWidths = DocumentLayoutEdges.of(Math.max(0, borderWidthSides.getTop().resolve(command.getWidth(), 0)),
                    Math.max(0, borderWidthSides.getRight().resolve(command.getWidth(), 0)),
                    Math.max(0, borderWidthSides.getBottom().resolve(command.getWidth(), 0)),
                    Math.max(0, borderWidthSides.getLeft().resolve(command.getWidth(), 0)));
        } else {
            int width = Math.max(0, command.getBorderWidth());
            resolvedWidths = DocumentLayoutEdges.of(width, width, width, width);
        }
        return applyCollapsedTableBorderOverride(command, style, resolvedWidths, styleMemo);
    }

    private static DocumentLayoutEdges applyCollapsedTableBorderOverride(DocumentPaintCommand command, ComputedStyle style,
            DocumentLayoutEdges widths, Map<ElementNode, ComputedStyle> styleMemo) {
        if (command == null || style == null || widths == null || style.getDisplay() != club.heiqi.uilib.ui.style.props.UiDisplay.TABLE_CELL) {
            return widths;
        }
        ElementNode element = command.getElement();
        if (element == null || !isCollapsedTableCell(element, styleMemo)) {
            return widths;
        }
        boolean lastColumn = isLastTableColumn(element);
        boolean lastRow = isLastTableRow(element, styleMemo);
        return DocumentLayoutEdges.of(widths.getTop(), lastColumn ? widths.getRight() : 0,
                lastRow ? widths.getBottom() : 0, widths.getLeft());
    }

    private static boolean isCollapsedTableCell(ElementNode cell, Map<ElementNode, ComputedStyle> styleMemo) {
        if (cell == null || !(cell.getParent() instanceof ElementNode)) {
            return false;
        }
        ElementNode row = (ElementNode) cell.getParent();
        if (!(row.getParent() instanceof ElementNode)) {
            return false;
        }
        ElementNode parent = (ElementNode) row.getParent();
        ElementNode table = null;
        if ("table".equals(parent.getTagName())) {
            table = parent;
        } else if (("thead".equals(parent.getTagName()) || "tbody".equals(parent.getTagName())
                || "tfoot".equals(parent.getTagName())) && parent.getParent() instanceof ElementNode) {
            table = (ElementNode) parent.getParent();
        }
        return table != null && resolveStyle(table, styleMemo).getBorderCollapse() == UiBorderCollapse.COLLAPSE;
    }

    private static boolean isLastTableColumn(ElementNode cell) {
        ElementNode row = (ElementNode) cell.getParent();
        if (row == null) {
            return true;
        }
        ElementNode lastCell = null;
        for (club.heiqi.uilib.ui.dom.DocumentNode child : row.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            if ("td".equals(childElement.getTagName()) || "th".equals(childElement.getTagName())) {
                lastCell = childElement;
            }
        }
        return lastCell == null || lastCell == cell;
    }

    private static boolean isLastTableRow(ElementNode cell, Map<ElementNode, ComputedStyle> styleMemo) {
        if (cell == null || !(cell.getParent() instanceof ElementNode)) {
            return true;
        }
        ElementNode row = (ElementNode) cell.getParent();
        ElementNode table = resolveTableAncestor(row);
        if (table == null) {
            return true;
        }
        ElementNode lastRow = findLastVisibleRowInTable(table, styleMemo);
        return lastRow == null || lastRow == row;
    }

    private static ElementNode resolveTableAncestor(ElementNode element) {
        for (club.heiqi.uilib.ui.dom.DocumentNode current = element; current instanceof ElementNode;
                current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            if ("table".equals(currentElement.getTagName())) {
                return currentElement;
            }
        }
        return null;
    }

    private static ElementNode findLastVisibleRowInSection(ElementNode section, Map<ElementNode, ComputedStyle> styleMemo) {
        ElementNode lastRow = null;
        for (club.heiqi.uilib.ui.dom.DocumentNode child : section.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            if (isVisibleTableRow(childElement, styleMemo)) {
                lastRow = childElement;
            }
        }
        return lastRow;
    }

    private static ElementNode findLastVisibleRowInTable(ElementNode table, Map<ElementNode, ComputedStyle> styleMemo) {
        ElementNode lastRow = null;
        for (club.heiqi.uilib.ui.dom.DocumentNode child : table.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            if (isVisibleTableRow(childElement, styleMemo)) {
                lastRow = childElement;
                continue;
            }
            if (isTableRowGroup(childElement, styleMemo)) {
                ElementNode sectionLastRow = findLastVisibleRowInSection(childElement, styleMemo);
                if (sectionLastRow != null) {
                    lastRow = sectionLastRow;
                }
            }
        }
        return lastRow;
    }

    private static boolean isVisibleTableRow(ElementNode element, Map<ElementNode, ComputedStyle> styleMemo) {
        if (element == null) {
            return false;
        }
        ComputedStyle style = resolveStyle(element, styleMemo);
        return "tr".equals(element.getTagName()) && style.getDisplay() == UiDisplay.TABLE_ROW
                && style.getVisibility() != UiVisibility.HIDDEN
                && style.getPosition() != UiPosition.ABSOLUTE
                && style.getPosition() != UiPosition.FIXED;
    }

    private static boolean isTableRowGroup(ElementNode element, Map<ElementNode, ComputedStyle> styleMemo) {
        if (element == null) {
            return false;
        }
        ComputedStyle style = resolveStyle(element, styleMemo);
        if (style.getVisibility() == UiVisibility.HIDDEN) {
            return false;
        }
        String tagName = element.getTagName();
        return ("thead".equals(tagName) && style.getDisplay() == UiDisplay.TABLE_HEADER_GROUP)
                || ("tbody".equals(tagName) && style.getDisplay() == UiDisplay.TABLE_ROW_GROUP)
                || ("tfoot".equals(tagName) && style.getDisplay() == UiDisplay.TABLE_FOOTER_GROUP);
    }

    private static boolean isUniformPositiveWidth(DocumentLayoutEdges widths) {
        if (widths == null || widths.getTop() <= 0) {
            return false;
        }
        return widths.getTop() == widths.getRight()
                && widths.getRight() == widths.getBottom()
                && widths.getBottom() == widths.getLeft();
    }

    private static boolean hasAnyCornerRadius(UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii,
            int cornerMask) {
        UiBorderRadiusResolver.ResolvedCornerRadii maskedRadii = applyCornerMask(cornerRadii, cornerMask);
        return maskedRadii.getTopLeft() > 0 || maskedRadii.getTopRight() > 0
                || maskedRadii.getBottomRight() > 0 || maskedRadii.getBottomLeft() > 0;
    }

    private static UiBorderRadiusResolver.ResolvedCornerRadii insetCornerRadii(
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int amount) {
        UiBorderRadiusResolver.ResolvedCornerRadii radii = cornerRadii == null
                ? UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0) : cornerRadii;
        int resolvedAmount = Math.max(0, amount);
        return UiBorderRadiusResolver.ResolvedCornerRadii.of(
                Math.max(0, radii.getTopLeft() - resolvedAmount),
                Math.max(0, radii.getTopRight() - resolvedAmount),
                Math.max(0, radii.getBottomRight() - resolvedAmount),
                Math.max(0, radii.getBottomLeft() - resolvedAmount));
    }

    private static UiBorderRadiusResolver.ResolvedCornerRadii applyCornerMask(
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int cornerMask) {
        UiBorderRadiusResolver.ResolvedCornerRadii radii = cornerRadii == null
                ? UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0) : cornerRadii;
        return UiBorderRadiusResolver.ResolvedCornerRadii.of(
                (cornerMask & UiSurfaceStyle.CORNER_TOP_LEFT) == 0 ? 0 : radii.getTopLeft(),
                (cornerMask & UiSurfaceStyle.CORNER_TOP_RIGHT) == 0 ? 0 : radii.getTopRight(),
                (cornerMask & UiSurfaceStyle.CORNER_BOTTOM_RIGHT) == 0 ? 0 : radii.getBottomRight(),
                (cornerMask & UiSurfaceStyle.CORNER_BOTTOM_LEFT) == 0 ? 0 : radii.getBottomLeft());
    }

    private static int resolveBorderColor(UiBorderColors borderColors, int fallbackColor, float fallbackOpacity,
            int sideIndex) {
        if (borderColors == null) {
            return applyOpacity(fallbackColor, fallbackOpacity);
        }
        int color;
        switch (sideIndex) {
            case 0:
                color = borderColors.getTop();
                break;
            case 1:
                color = borderColors.getRight();
                break;
            case 2:
                color = borderColors.getBottom();
                break;
            default:
                color = borderColors.getLeft();
                break;
        }
        return applyOpacity(color, fallbackOpacity);
    }

    private static UiBorderRadiusResolver.ResolvedCornerRadii resolveCommandCornerRadii(DocumentPaintCommand command,
            boolean useElementStyle, Map<ElementNode, ComputedStyle> styleMemo) {
        if (command.getCornerRadii() != null) {
            return command.getCornerRadii();
        }
        if (useElementStyle && command.getElement() != null) {
            return UiBorderRadiusResolver.resolve(resolveStyle(command.getElement(), styleMemo), command.getWidth(),
                    command.getHeight());
        }
        return UiBorderRadiusResolver.ResolvedCornerRadii.uniform(command.getBorderRadius());
    }

    private static int fadeColor(int color, int numerator, int denominator) {
        int alpha = (color >>> 24) & 0xFF;
        int scaledAlpha = denominator <= 0 ? alpha : Math.max(0, Math.min(255, alpha * numerator / denominator));
        return (scaledAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int applyOpacity(int color, float opacity) {
        float clampedOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
        int alpha = color >> 24 & 255;
        int resolvedAlpha = Math.max(0, Math.min(255, Math.round((float) alpha * clampedOpacity)));
        return color & 0x00FFFFFF | resolvedAlpha << 24;
    }
}
