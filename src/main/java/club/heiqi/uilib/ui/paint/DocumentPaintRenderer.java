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
        Objects.requireNonNull(context, "context");
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (DocumentPaintCommand command : commands) {
            renderCommand(context, command);
        }
    }

    private static void renderCommand(UiRenderContext context, DocumentPaintCommand command) {
        if (command == null || command.getWidth() <= 0 || command.getHeight() <= 0) {
            return;
        }
        if (command.getType() == DocumentPaintCommandType.BACKGROUND) {
            context.drawSurface(command.getLeft(), command.getTop(), command.getRight(), command.getBottom(),
                    new UiSurfaceStyle(command.getColor(), 0, command.getBorderRadius()));
            return;
        }
        if (command.getType() == DocumentPaintCommandType.BORDER) {
            renderBorder(context, command);
        }
    }

    private static void renderBorder(UiRenderContext context, DocumentPaintCommand command) {
        int maxBorderWidth = Math.min(command.getBorderWidth(), Math.min(command.getWidth(), command.getHeight()) / 2);
        for (int offset = 0; offset < maxBorderWidth; offset++) {
            int left = command.getLeft() + offset;
            int top = command.getTop() + offset;
            int right = command.getRight() - offset;
            int bottom = command.getBottom() - offset;
            int radius = Math.max(0, command.getBorderRadius() - offset);
            context.drawSurface(left, top, right, bottom, new UiSurfaceStyle(0, command.getColor(), radius));
        }
    }
}
