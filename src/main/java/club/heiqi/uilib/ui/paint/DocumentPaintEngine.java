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

    private static final int SCROLLBAR_TRACK_GAP = 2;
    private static final int SCROLLBAR_TRACK_THICKNESS = 6;
    private static final int SCROLLBAR_MIN_THUMB_SIZE = 24;
    private static final int SCROLLBAR_TRACK_COLOR = 0x663B4A66;
    private static final int SCROLLBAR_THUMB_COLOR = 0xDDBCD7FF;

    private DocumentPaintEngine() {}

    /**
     * 从布局盒树生成绘制命令。
     *
     * <p>当前初版按元素背景、元素边框、结构裁剪、滚动内容、子树与滚动条的顺序输出命令。
     * stacking context 会在后续阶段继续扩展。</p>
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
        Objects.requireNonNull(rootBox, "rootBox");
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        appendBoxCommands(rootBox, rootBox, commands, scrollState, 0, 0, currentTimeNanos);
        return commands;
    }

    private static void appendBoxCommands(DocumentLayoutBox rootBox, DocumentLayoutBox box,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState, int offsetX, int offsetY,
            long currentTimeNanos) {
        appendBackgroundCommand(box, commands, offsetX, offsetY);
        appendBorderCommand(box, commands, offsetX, offsetY);
        boolean clipChildren = shouldClipChildren(box);
        if (clipChildren) {
            appendClipStartCommand(box, commands, offsetX, offsetY);
        }
        int childOffsetX = offsetX - getScrollLeft(scrollState, box);
        int childOffsetY = offsetY - getScrollTop(scrollState, box);
        appendCustomCommand(box, commands, childOffsetX, childOffsetY);
        appendTextCommands(box, commands, childOffsetX, childOffsetY);
        for (DocumentLayoutBox child : box.getChildren()) {
            appendBoxCommands(rootBox, child, commands, scrollState, childOffsetX, childOffsetY,
                    currentTimeNanos);
        }
        if (clipChildren) {
            appendClipEndCommand(box, commands, offsetX, offsetY);
        }
        appendScrollbarCommands(rootBox, box, commands, scrollState, offsetX, offsetY, currentTimeNanos);
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

    private static void appendScrollbarCommands(DocumentLayoutBox rootBox, DocumentLayoutBox box,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState, int offsetX, int offsetY,
            long currentTimeNanos) {
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

        int contentLeft = box.getContentLeft() + offsetX;
        int contentTop = box.getContentTop() + offsetY;
        int contentRight = contentLeft + box.getContentWidth();
        int contentBottom = contentTop + box.getContentHeight();
        if (hasVerticalScrollbar) {
            appendVerticalScrollbarCommands(box, commands, scrollState, contentTop, contentRight,
                    contentBottom - (hasHorizontalScrollbar ? SCROLLBAR_TRACK_THICKNESS + SCROLLBAR_TRACK_GAP : 0));
        }
        if (hasHorizontalScrollbar) {
            appendHorizontalScrollbarCommands(box, commands, scrollState, contentLeft, contentRight
                    - (hasVerticalScrollbar ? SCROLLBAR_TRACK_THICKNESS + SCROLLBAR_TRACK_GAP : 0), contentBottom);
        }
    }

    private static void appendVerticalScrollbarCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentScrollState scrollState, int contentTop, int contentRight, int contentBottom) {
        int trackRight = contentRight - SCROLLBAR_TRACK_GAP;
        int trackLeft = trackRight - SCROLLBAR_TRACK_THICKNESS;
        int trackTop = contentTop + SCROLLBAR_TRACK_GAP;
        int trackBottom = contentBottom - SCROLLBAR_TRACK_GAP;
        if (trackRight <= trackLeft || trackBottom <= trackTop) {
            return;
        }

        int trackLength = trackBottom - trackTop;
        int viewportLength = Math.max(1, box.getContentHeight());
        int contentLength = viewportLength + scrollState.getMaxScrollTop(box.getElement());
        int thumbSize = Math.min(trackLength, Math.max(SCROLLBAR_MIN_THUMB_SIZE,
                Math.round(trackLength * (viewportLength / (float) Math.max(viewportLength, contentLength)))));
        int travel = Math.max(0, trackLength - thumbSize);
        int thumbTop = trackTop + Math.round(travel * (scrollState.getScrollTop(box.getElement())
                / (float) Math.max(1, scrollState.getMaxScrollTop(box.getElement()))));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLLBAR_TRACK, box.getElement(), trackLeft,
                trackTop, trackRight, trackBottom, SCROLLBAR_TRACK_COLOR, 0, SCROLLBAR_TRACK_THICKNESS / 2));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLLBAR_THUMB, box.getElement(), trackLeft,
                thumbTop, trackRight, thumbTop + thumbSize, SCROLLBAR_THUMB_COLOR, 0, SCROLLBAR_TRACK_THICKNESS / 2));
    }

    private static void appendHorizontalScrollbarCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentScrollState scrollState, int contentLeft, int contentRight, int contentBottom) {
        int trackLeft = contentLeft + SCROLLBAR_TRACK_GAP;
        int trackRight = contentRight - SCROLLBAR_TRACK_GAP;
        int trackBottom = contentBottom - SCROLLBAR_TRACK_GAP;
        int trackTop = trackBottom - SCROLLBAR_TRACK_THICKNESS;
        if (trackRight <= trackLeft || trackBottom <= trackTop) {
            return;
        }

        int trackLength = trackRight - trackLeft;
        int viewportLength = Math.max(1, box.getContentWidth());
        int contentLength = viewportLength + scrollState.getMaxScrollLeft(box.getElement());
        int thumbSize = Math.min(trackLength, Math.max(SCROLLBAR_MIN_THUMB_SIZE,
                Math.round(trackLength * (viewportLength / (float) Math.max(viewportLength, contentLength)))));
        int travel = Math.max(0, trackLength - thumbSize);
        int thumbLeft = trackLeft + Math.round(travel * (scrollState.getScrollLeft(box.getElement())
                / (float) Math.max(1, scrollState.getMaxScrollLeft(box.getElement()))));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLLBAR_TRACK, box.getElement(), trackLeft,
                trackTop, trackRight, trackBottom, SCROLLBAR_TRACK_COLOR, 0, SCROLLBAR_TRACK_THICKNESS / 2));
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLLBAR_THUMB, box.getElement(), thumbLeft,
                trackTop, thumbLeft + thumbSize, trackBottom, SCROLLBAR_THUMB_COLOR, 0, SCROLLBAR_TRACK_THICKNESS / 2));
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
