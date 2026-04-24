package club.heiqi.uilib.ui.paint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.ComputedStyle;

/**
 * HTML-like 绘制命令生成器。
 */
public final class DocumentPaintEngine {

    private DocumentPaintEngine() {}

    /**
     * 从布局盒树生成绘制命令。
     *
     * <p>当前初版按元素背景、元素边框、子树的顺序输出命令。文本、滚动条、裁剪与 stacking context
     * 会在后续阶段继续扩展。</p>
     *
     * @param rootBox 根布局盒
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox) {
        Objects.requireNonNull(rootBox, "rootBox");
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        appendBoxCommands(rootBox, commands);
        return commands;
    }

    private static void appendBoxCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands) {
        appendBackgroundCommand(box, commands);
        appendBorderCommand(box, commands);
        for (DocumentLayoutBox child : box.getChildren()) {
            appendBoxCommands(child, commands);
        }
    }

    private static void appendBackgroundCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands) {
        ComputedStyle style = box.getComputedStyle();
        int color = style.getBackgroundColor();
        if (color == 0 || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, box.getElement(), box.getLeft(),
                box.getTop(), box.getRight(), box.getBottom(), color, 0, resolveBorderRadius(box)));
    }

    private static void appendBorderCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands) {
        ComputedStyle style = box.getComputedStyle();
        int borderWidth = box.getBorder().getTop();
        int color = style.getBorderColor();
        if (color == 0 || borderWidth <= 0 || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BORDER, box.getElement(), box.getLeft(),
                box.getTop(), box.getRight(), box.getBottom(), color, borderWidth, resolveBorderRadius(box)));
    }

    private static int resolveBorderRadius(DocumentLayoutBox box) {
        int limit = Math.min(box.getWidth(), box.getHeight());
        int radius = box.getComputedStyle().getBorderRadius().resolve(limit, 0);
        return Math.max(0, Math.min(radius, limit / 2));
    }
}
