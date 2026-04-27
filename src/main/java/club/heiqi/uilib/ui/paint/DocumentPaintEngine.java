package club.heiqi.uilib.ui.paint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutTextRun;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
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
     * <p>当前初版按元素背景、元素边框、结构裁剪、滚动内容与子树的顺序输出命令。滚动条与 stacking context
     * 会在后续阶段继续扩展。</p>
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
        Objects.requireNonNull(rootBox, "rootBox");
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        appendBoxCommands(rootBox, commands, scrollState, 0, 0);
        return commands;
    }

    private static void appendBoxCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentScrollState scrollState, int offsetX, int offsetY) {
        appendBackgroundCommand(box, commands, offsetX, offsetY);
        appendBorderCommand(box, commands, offsetX, offsetY);
        boolean clipChildren = shouldClipChildren(box);
        if (clipChildren) {
            appendClipStartCommand(box, commands, offsetX, offsetY);
        }
        appendCustomCommand(box, commands, offsetX, offsetY);
        int childOffsetX = offsetX - getScrollLeft(scrollState, box);
        int childOffsetY = offsetY - getScrollTop(scrollState, box);
        appendTextCommands(box, commands, childOffsetX, childOffsetY);
        for (DocumentLayoutBox child : box.getChildren()) {
            appendBoxCommands(child, commands, scrollState, childOffsetX, childOffsetY);
        }
        if (clipChildren) {
            appendClipEndCommand(box, commands, offsetX, offsetY);
        }
    }

    private static void appendBackgroundCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            int offsetX, int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        int color = style.getBackgroundColor();
        if (color == 0 || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, color, 0, resolveBorderRadius(box)));
    }

    private static void appendBorderCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands, int offsetX,
            int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        int borderWidth = box.getBorder().getTop();
        int color = style.getBorderColor();
        if (color == 0 || borderWidth <= 0 || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BORDER, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, color, borderWidth, resolveBorderRadius(box)));
    }

    private static void appendCustomCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands, int offsetX,
            int offsetY) {
        DocumentCustomRenderer customRenderer = box.getElement().getCustomRenderer();
        if (customRenderer == null || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        int contentLeft = getPaddingBoxLeft(box) + offsetX;
        int contentTop = getPaddingBoxTop(box) + offsetY;
        int contentRight = getPaddingBoxRight(box) + offsetX;
        int contentBottom = getPaddingBoxBottom(box) + offsetY;
        if (contentRight <= contentLeft || contentBottom <= contentTop) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CUSTOM, box.getElement(),
                contentLeft, contentTop, contentRight, contentBottom,
                0, 0, 0, null, customRenderer));
    }

    private static void appendTextCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands, int offsetX,
            int offsetY) {
        int color = box.getComputedStyle().getTextColor();
        if (color == 0) {
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

    private static void appendClipStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands, int offsetX,
            int offsetY) {
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
                right, bottom, 0, 0, resolveBorderRadius(box)));
    }

    private static void appendClipEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands, int offsetX,
            int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CLIP_END, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, 0));
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

    private static int resolveBorderRadius(DocumentLayoutBox box) {
        int limit = Math.min(box.getWidth(), box.getHeight());
        int radius = box.getComputedStyle().getBorderRadius().resolve(limit, 0);
        return Math.max(0, Math.min(radius, limit / 2));
    }

    private static int getScrollLeft(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollLeft(box.getElement());
    }

    private static int getScrollTop(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollTop(box.getElement());
    }
}
