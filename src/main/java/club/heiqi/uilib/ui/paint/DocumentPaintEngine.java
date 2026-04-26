package club.heiqi.uilib.ui.paint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiOverflow;

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
        boolean clipChildren = shouldClipChildren(box);
        if (clipChildren) {
            appendClipStartCommand(box, commands);
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            appendBoxCommands(child, commands);
        }
        if (clipChildren) {
            appendClipEndCommand(box, commands);
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

    private static void appendClipStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands) {
        ComputedStyle style = box.getComputedStyle();
        int left = style.getOverflowX() == UiOverflow.VISIBLE ? Integer.MIN_VALUE / 4 : getPaddingBoxLeft(box);
        int right = style.getOverflowX() == UiOverflow.VISIBLE ? Integer.MAX_VALUE / 4 : getPaddingBoxRight(box);
        int top = style.getOverflowY() == UiOverflow.VISIBLE ? Integer.MIN_VALUE / 4 : getPaddingBoxTop(box);
        int bottom = style.getOverflowY() == UiOverflow.VISIBLE ? Integer.MAX_VALUE / 4 : getPaddingBoxBottom(box);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CLIP_START, box.getElement(), left, top,
                right, bottom, 0, 0, resolveBorderRadius(box)));
    }

    private static void appendClipEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CLIP_END, box.getElement(), box.getLeft(),
                box.getTop(), box.getRight(), box.getBottom(), 0, 0, 0));
    }

    private static boolean shouldClipChildren(DocumentLayoutBox box) {
        ComputedStyle style = box.getComputedStyle();
        return (style.getOverflowX() != UiOverflow.VISIBLE || style.getOverflowY() != UiOverflow.VISIBLE)
                && !box.getChildren().isEmpty();
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

    private static int resolveBorderRadius(DocumentLayoutBox box) {
        int limit = Math.min(box.getWidth(), box.getHeight());
        int radius = box.getComputedStyle().getBorderRadius().resolve(limit, 0);
        return Math.max(0, Math.min(radius, limit / 2));
    }
}
