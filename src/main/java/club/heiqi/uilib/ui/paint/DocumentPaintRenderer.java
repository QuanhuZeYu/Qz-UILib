package club.heiqi.uilib.ui.paint;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.ui.layout.DocumentEffectType;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * 将 HTML-like 绘制命令投影到现有 UI 渲染上下文。
 */
public final class DocumentPaintRenderer {

    private static final class OpenRenderState {

        private final DocumentEffectType effectType;
        private final float previousFallbackOpacity;

        private OpenRenderState(DocumentEffectType effectType, float previousFallbackOpacity) {
            this.effectType = effectType;
            this.previousFallbackOpacity = previousFallbackOpacity;
        }
    }

    private static final class RenderReplayState {

        private final Deque<OpenRenderState> openStates = new ArrayDeque<OpenRenderState>();
        private float fallbackOpacity = 1.0F;

        private void pushClip() {
            openStates.push(new OpenRenderState(DocumentEffectType.OVERFLOW_CLIP, fallbackOpacity));
        }

        private void pushPaintContext(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY) {
            float previousFallbackOpacity = fallbackOpacity;
            context.pushPaintContext(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY, command.getPaintContextOpacity());
            if (!context.isCurrentPaintContextLayerActive()) {
                fallbackOpacity *= command.getPaintContextOpacity();
            }
            openStates.push(new OpenRenderState(DocumentEffectType.PAINT_CONTEXT, previousFallbackOpacity));
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
        try {
            int commandIndex = 0;
            while (commandIndex < commands.size()) {
                DocumentPaintCommand command = commands.get(commandIndex);
                if (isBatchableTextCommand(command, replayState)) {
                    commandIndex = renderTextBatch(context, commands, commandIndex, offsetX, offsetY,
                            replayState);
                    continue;
                }
                renderCommand(context, command, offsetX, offsetY, replayState);
                commandIndex++;
            }
        } finally {
            while (!replayState.isEmpty()) {
                popOpenState(context, replayState, replayState.pop());
            }
        }
    }

    private static int renderTextBatch(UiRenderContext context, List<DocumentPaintCommand> commands, int startIndex,
            int offsetX, int offsetY, RenderReplayState replayState) {
        DefaultFontRendererAdapter fontRenderer = DefaultFontRendererAdapter.getInstance();
        FontService fontService = FontService.getInstance();
        int commandIndex = startIndex;
        synchronized (fontService) {
            fontRenderer.beginDeferredFlushScope(context.getScreenWidth(), context.getScreenHeight());
            try {
                while (commandIndex < commands.size()) {
                    DocumentPaintCommand command = commands.get(commandIndex);
                    if (!isBatchableTextCommand(command, replayState)) {
                        break;
                    }
                    renderTextCommand(context, command, offsetX, offsetY, replayState);
                    commandIndex++;
                }
                return commandIndex;
            } finally {
                try {
                    fontRenderer.flushDeferredFlushScope();
                } finally {
                    fontRenderer.endDeferredFlushScope();
                }
            }
        }
    }

    private static void renderCommand(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY,
            RenderReplayState replayState) {
        if (command == null) {
            return;
        }
        if (isEffectEndCommand(command)) {
            popExpectedRenderState(context, replayState, command.getEffectType());
            return;
        }
        if (isEffectStartCommand(command)) {
            pushEffectState(context, command, offsetX, offsetY, replayState);
            return;
        }
        if (command.getWidth() <= 0 || command.getHeight() <= 0) {
            return;
        }
        if (replayState.fallbackOpacity <= 0.001F) {
            return;
        }
        if (renderStatelessEffect(context, command, offsetX, offsetY)) {
            return;
        }
        if (command.getType() == DocumentPaintCommandType.BACKGROUND) {
            context.drawSurface(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    new UiSurfaceStyle(applyOpacity(command.getColor(), replayState.fallbackOpacity), 0,
                            command.getBorderRadius(), command.getCornerMask()));
            return;
        }
        if (command.getType() == DocumentPaintCommandType.SCROLLBAR_TRACK
                || command.getType() == DocumentPaintCommandType.SCROLLBAR_THUMB) {
            context.drawSurface(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    new UiSurfaceStyle(applyOpacity(command.getColor(), replayState.fallbackOpacity), 0,
                            command.getBorderRadius(), command.getCornerMask()));
            return;
        }
        if (command.getType() == DocumentPaintCommandType.BORDER) {
            renderBorder(context, command, offsetX, offsetY, replayState.fallbackOpacity);
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
            int offsetY, RenderReplayState replayState) {
        context.drawText(command.getText(), command.getLeft() + offsetX, command.getTop() + offsetY,
                applyOpacity(command.getColor(), replayState.fallbackOpacity), false,
                command.getTextContentMode());
    }

    private static boolean isBatchableTextCommand(DocumentPaintCommand command, RenderReplayState replayState) {
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

    private static void popOpenState(UiRenderContext context, RenderReplayState replayState, OpenRenderState state) {
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
            int offsetY, RenderReplayState replayState) {
        if (command.getEffectType() == DocumentEffectType.PAINT_CONTEXT) {
            replayState.pushPaintContext(context, command, offsetX, offsetY);
            return;
        }
        if (command.getEffectType() == DocumentEffectType.OVERFLOW_CLIP) {
            context.pushClip(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY, command.getBorderRadius());
            replayState.pushClip();
        }
    }

    private static boolean renderStatelessEffect(UiRenderContext context, DocumentPaintCommand command, int offsetX,
            int offsetY) {
        if (command.getEffectType() != DocumentEffectType.BACKDROP_FILTER) {
            return false;
        }
        context.drawBackdropFilter(command.getLeft() + offsetX, command.getTop() + offsetY,
                command.getRight() + offsetX, command.getBottom() + offsetY,
                command.getBackdropBlurRadius(), command.getBackdropSaturation(), command.getBorderRadius());
        return true;
    }

    private static void renderBorder(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY,
            float fallbackOpacity) {
        int maxBorderWidth = Math.min(command.getBorderWidth(), Math.min(command.getWidth(), command.getHeight()) / 2);
        int borderColor = applyOpacity(command.getColor(), fallbackOpacity);
        for (int offset = 0; offset < maxBorderWidth; offset++) {
            int left = command.getLeft() + offset + offsetX;
            int top = command.getTop() + offset + offsetY;
            int right = command.getRight() - offset + offsetX;
            int bottom = command.getBottom() - offset + offsetY;
            int radius = Math.max(0, command.getBorderRadius() - offset);
            context.drawSurface(left, top, right, bottom, new UiSurfaceStyle(0, borderColor, radius,
                    command.getCornerMask()));
        }
    }

    private static int applyOpacity(int color, float opacity) {
        float clampedOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
        int alpha = color >> 24 & 255;
        int resolvedAlpha = Math.max(0, Math.min(255, Math.round((float) alpha * clampedOpacity)));
        return color & 0x00FFFFFF | resolvedAlpha << 24;
    }
}
