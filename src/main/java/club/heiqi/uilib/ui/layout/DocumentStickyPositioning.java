package club.heiqi.uilib.ui.layout;

import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * position:sticky 的滚动期视觉偏移解析。
 */
public final class DocumentStickyPositioning {

    private DocumentStickyPositioning() {}

    /**
     * 创建根节点 sticky 上下文。
     *
     * @return 根 sticky 上下文
     */
    public static StickyContext rootContext() {
        return StickyContext.ROOT;
    }

    /**
     * 基于当前盒的视觉位置创建其子节点 sticky 上下文。
     *
     * @param box 当前布局盒
     * @param boxOffsetX 当前盒视觉 X 偏移
     * @param boxOffsetY 当前盒视觉 Y 偏移
     * @param parentContext 父 sticky 上下文
     * @return 子节点 sticky 上下文
     */
    public static StickyContext createChildContext(DocumentLayoutBox box, int boxOffsetX, int boxOffsetY,
            StickyContext parentContext) {
        StickyScrollport scrollport = parentContext == null ? null : parentContext.scrollport;
        int contentLeft = box.getContentLeft() + boxOffsetX;
        int contentTop = box.getContentTop() + boxOffsetY;
        int contentRight = contentLeft + box.getContentWidth();
        int contentBottom = contentTop + box.getContentHeight();
        if (createsStickyScrollport(box.getComputedStyle())) {
            scrollport = new StickyScrollport(contentLeft, contentTop, contentRight, contentBottom);
        }
        return new StickyContext(scrollport, contentLeft, contentTop, contentRight, contentBottom, true);
    }

    /**
     * 解析当前盒在 X 轴上的 sticky 视觉偏移。
     *
     * @param box 当前布局盒
     * @param offsetX 已包含普通定位偏移的视觉 X 偏移
     * @param context sticky 上下文
     * @return 应用于绘制或命中的最终 X 偏移
     */
    public static int resolveOffsetX(DocumentLayoutBox box, int offsetX, StickyContext context) {
        if (!isSticky(box, context)) {
            return offsetX;
        }
        ComputedStyle style = box.getComputedStyle();
        int shift = 0;
        int visualLeft = box.getLeft() + offsetX;
        int visualRight = box.getRight() + offsetX;
        if (!isAuto(style.getLeft())) {
            int minLeft = context.scrollport.left + style.getLeft().resolve(context.scrollport.getWidth(), 0);
            if (visualLeft < minLeft) {
                shift += minLeft - visualLeft;
            }
        }
        if (!isAuto(style.getRight())) {
            int maxRight = context.scrollport.right - style.getRight().resolve(context.scrollport.getWidth(), 0);
            if (visualRight + shift > maxRight) {
                shift -= visualRight + shift - maxRight;
            }
        }
        if (context.hasContainingBlock) {
            if (visualLeft + shift < context.containingLeft) {
                shift += context.containingLeft - (visualLeft + shift);
            }
            if (visualRight + shift > context.containingRight) {
                shift -= visualRight + shift - context.containingRight;
            }
        }
        return offsetX + shift;
    }

    /**
     * 解析当前盒在 Y 轴上的 sticky 视觉偏移。
     *
     * @param box 当前布局盒
     * @param offsetY 已包含普通定位偏移的视觉 Y 偏移
     * @param context sticky 上下文
     * @return 应用于绘制或命中的最终 Y 偏移
     */
    public static int resolveOffsetY(DocumentLayoutBox box, int offsetY, StickyContext context) {
        if (!isSticky(box, context)) {
            return offsetY;
        }
        ComputedStyle style = box.getComputedStyle();
        int shift = 0;
        int visualTop = box.getTop() + offsetY;
        int visualBottom = box.getBottom() + offsetY;
        if (!isAuto(style.getTop())) {
            int minTop = context.scrollport.top + style.getTop().resolve(context.scrollport.getHeight(), 0);
            if (visualTop < minTop) {
                shift += minTop - visualTop;
            }
        }
        if (!isAuto(style.getBottom())) {
            int maxBottom = context.scrollport.bottom - style.getBottom().resolve(context.scrollport.getHeight(), 0);
            if (visualBottom + shift > maxBottom) {
                shift -= visualBottom + shift - maxBottom;
            }
        }
        if (context.hasContainingBlock) {
            if (visualTop + shift < context.containingTop) {
                shift += context.containingTop - (visualTop + shift);
            }
            if (visualBottom + shift > context.containingBottom) {
                shift -= visualBottom + shift - context.containingBottom;
            }
        }
        return offsetY + shift;
    }

    private static boolean isSticky(DocumentLayoutBox box, StickyContext context) {
        return box.getComputedStyle().getPosition() == UiPosition.STICKY
                && context != null && context.scrollport != null;
    }

    private static boolean createsStickyScrollport(ComputedStyle style) {
        return style.getOverflowX() != UiOverflow.VISIBLE || style.getOverflowY() != UiOverflow.VISIBLE;
    }

    private static boolean isAuto(UiStyleLength length) {
        return length.getType() == UiStyleLength.Type.AUTO;
    }

    /**
     * sticky 解析时需要携带的最近滚动视口与直接 containing block 边界。
     */
    public static final class StickyContext {
        private static final StickyContext ROOT = new StickyContext(null, 0, 0, 0, 0, false);

        private final StickyScrollport scrollport;
        private final int containingLeft;
        private final int containingTop;
        private final int containingRight;
        private final int containingBottom;
        private final boolean hasContainingBlock;

        private StickyContext(StickyScrollport scrollport, int containingLeft, int containingTop,
                int containingRight, int containingBottom, boolean hasContainingBlock) {
            this.scrollport = scrollport;
            this.containingLeft = containingLeft;
            this.containingTop = containingTop;
            this.containingRight = containingRight;
            this.containingBottom = containingBottom;
            this.hasContainingBlock = hasContainingBlock;
        }
    }

    private static final class StickyScrollport {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private StickyScrollport(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private int getWidth() {
            return Math.max(0, right - left);
        }

        private int getHeight() {
            return Math.max(0, bottom - top);
        }
    }
}
