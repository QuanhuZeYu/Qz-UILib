package club.heiqi.uilib.ui.paint;

import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * 将 HTML-like 绘制命令投影到现有 UI 渲染上下文。
 */
public final class DocumentPaintRenderer {

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
        int clipDepth = 0;
        try {
            for (DocumentPaintCommand command : commands) {
                clipDepth = renderCommand(context, command, offsetX, offsetY, clipDepth);
            }
        } finally {
            while (clipDepth > 0) {
                context.popClip();
                clipDepth--;
            }
        }
    }

    private static int renderCommand(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY,
            int clipDepth) {
        if (command == null) {
            return clipDepth;
        }
        if (command.getType() == DocumentPaintCommandType.CLIP_END) {
            if (clipDepth > 0) {
                context.popClip();
                return clipDepth - 1;
            }
            return 0;
        }
        if (command.getWidth() <= 0 || command.getHeight() <= 0) {
            return clipDepth;
        }
        if (command.getType() == DocumentPaintCommandType.CLIP_START) {
            context.pushClip(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY, command.getBorderRadius());
            return clipDepth + 1;
        }
        if (command.getType() == DocumentPaintCommandType.BACKDROP_FILTER) {
            context.drawBackdropFilter(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    command.getBackdropBlurRadius(), command.getBackdropSaturation(), command.getBorderRadius());
            return clipDepth;
        }
        if (command.getType() == DocumentPaintCommandType.BACKGROUND) {
            context.drawSurface(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    new UiSurfaceStyle(command.getColor(), 0, command.getBorderRadius()));
            return clipDepth;
        }
        if (command.getType() == DocumentPaintCommandType.SCROLLBAR_TRACK
                || command.getType() == DocumentPaintCommandType.SCROLLBAR_THUMB) {
            context.drawSurface(command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getRight() + offsetX, command.getBottom() + offsetY,
                    new UiSurfaceStyle(command.getColor(), 0, command.getBorderRadius()));
            return clipDepth;
        }
        if (command.getType() == DocumentPaintCommandType.BORDER) {
            renderBorder(context, command, offsetX, offsetY);
            return clipDepth;
        }
        if (command.getType() == DocumentPaintCommandType.CUSTOM) {
            DocumentCustomRenderer customRenderer = command.getCustomRenderer();
            if (customRenderer != null) {
                customRenderer.render(context, command.getLeft() + offsetX, command.getTop() + offsetY,
                        command.getRight() + offsetX, command.getBottom() + offsetY);
            }
            return clipDepth;
        }
        if (command.getType() == DocumentPaintCommandType.TEXT) {
            context.drawText(command.getText(), command.getLeft() + offsetX, command.getTop() + offsetY,
                    command.getColor(), false);
        }
        return clipDepth;
    }

    private static void renderBorder(UiRenderContext context, DocumentPaintCommand command, int offsetX, int offsetY) {
        int maxBorderWidth = Math.min(command.getBorderWidth(), Math.min(command.getWidth(), command.getHeight()) / 2);
        for (int offset = 0; offset < maxBorderWidth; offset++) {
            int left = command.getLeft() + offset + offsetX;
            int top = command.getTop() + offset + offsetY;
            int right = command.getRight() - offset + offsetX;
            int bottom = command.getBottom() - offset + offsetY;
            int radius = Math.max(0, command.getBorderRadius() - offset);
            context.drawSurface(left, top, right, bottom, new UiSurfaceStyle(0, command.getColor(), radius));
        }
    }
}
