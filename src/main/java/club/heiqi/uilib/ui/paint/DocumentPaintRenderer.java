package club.heiqi.uilib.ui.paint;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * 将 HTML-like 绘制命令投影到现有 UI 渲染上下文。
 */
public final class DocumentPaintRenderer {

    private enum OpenRenderStateType {
        PAINT_CONTEXT,
        CLIP
    }

    private static final class OpenRenderState {

        private final OpenRenderStateType type;
        private final float previousFallbackOpacity;

        private OpenRenderState(OpenRenderStateType type, float previousFallbackOpacity) {
            this.type = type;
            this.previousFallbackOpacity = previousFallbackOpacity;
        }
    }

    private static final class RenderReplayState {

        private final Deque<OpenRenderState> openStates = new ArrayDeque<OpenRenderState>();
        private float fallbackOpacity = 1.0F;

        private void pushClip() {
            openStates.push(new OpenRenderState(OpenRenderStateType.CLIP, fallbackOpacity));
        }

        private void pushPaintContext(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY) {
            float previousFallbackOpacity = fallbackOpacity;
            context.pushPaintContext(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY, command.getPaintContextOpacity());
            if (!context.isCurrentPaintContextLayerActive()) {
                fallbackOpacity *= command.getPaintContextOpacity();
            }
            openStates.push(new OpenRenderState(OpenRenderStateType.PAINT_CONTEXT, previousFallbackOpacity));
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
            for (DocumentPaintCommand command : commands) {
                renderCommand(context, command, offsetX, offsetY, replayState);
            }
        } finally {
            while (!replayState.isEmpty()) {
                popOpenState(context, replayState, replayState.pop());
            }
        }
    }

    private static void renderCommand(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY,
            RenderReplayState replayState) {
        if (command == null) {
            return;
        }
        if (command.getType() == DocumentPaintCommandType.PAINT_CONTEXT_END) {
            popExpectedRenderState(context, replayState, OpenRenderStateType.PAINT_CONTEXT);
            return;
        }
        if (command.getType() == DocumentPaintCommandType.CLIP_END) {
            popExpectedRenderState(context, replayState, OpenRenderStateType.CLIP);
            return;
        }
        if (command.getType() == DocumentPaintCommandType.PAINT_CONTEXT_START) {
            replayState.pushPaintContext(context, command, offsetX, offsetY);
            return;
        }
        if (command.getWidth() <= 0 || command.getHeight() <= 0) {
            return;
        }
        if (command.getType() == DocumentPaintCommandType.CLIP_START) {
            context.pushClip(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY, command.getBorderRadius());
            replayState.pushClip();
            return;
        }
        if (replayState.fallbackOpacity <= 0.001F) {
            return;
        }
        if (command.getType() == DocumentPaintCommandType.BACKDROP_FILTER) {
            context.drawBackdropFilter(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    command.getBackdropBlurRadius(), command.getBackdropSaturation(), command.getBorderRadius());
            return;
        }
        if (command.getType() == DocumentPaintCommandType.BACKGROUND) {
            context.drawSurface(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    new UiSurfaceStyle(applyOpacity(command.getColor(), replayState.fallbackOpacity), 0,
                            command.getBorderRadius()));
            return;
        }
        if (command.getType() == DocumentPaintCommandType.SCROLLBAR_TRACK
                || command.getType() == DocumentPaintCommandType.SCROLLBAR_THUMB) {
            context.drawSurface(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    new UiSurfaceStyle(applyOpacity(command.getColor(), replayState.fallbackOpacity), 0,
                            command.getBorderRadius()));
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
        if (command.getType() == DocumentPaintCommandType.TEXT) {
            context.drawText(command.getText(), command.getLeft() + offsetX, command.getTop() + offsetY,
                    applyOpacity(command.getColor(), replayState.fallbackOpacity), false);
        }
    }

    private static void popExpectedRenderState(UiRenderContext context, RenderReplayState replayState,
            OpenRenderStateType expectedState) {
        if (!replayState.isEmpty() && replayState.peek().type == expectedState) {
            popOpenState(context, replayState, replayState.pop());
        }
    }

    private static void popOpenState(UiRenderContext context, RenderReplayState replayState, OpenRenderState state) {
        if (state.type == OpenRenderStateType.CLIP) {
            context.popClip();
            return;
        }
        context.popPaintContext();
        replayState.fallbackOpacity = state.previousFallbackOpacity;
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
            context.drawSurface(left, top, right, bottom, new UiSurfaceStyle(0, borderColor, radius));
        }
    }

    private static int applyOpacity(int color, float opacity) {
        float clampedOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
        int alpha = color >> 24 & 255;
        int resolvedAlpha = Math.max(0, Math.min(255, Math.round((float) alpha * clampedOpacity)));
        return color & 0x00FFFFFF | resolvedAlpha << 24;
    }
}
