package club.heiqi.uilib.ui.dom.control;

/**
 * 页面级 tooltip 的指针避让定位计算器。
 */
final class DocumentTooltipLayoutResolver {

    private static final int SCREEN_MARGIN = 4;
    private static final int POINTER_RADIUS = 32;
    private static final int POINTER_DIAGONAL_COMPONENT = Math.round((float) (POINTER_RADIUS / Math.sqrt(2.0D)));

    private DocumentTooltipLayoutResolver() {}

    /**
     * tooltip 高度估算器。
     */
    interface TooltipHeightEstimator {

        /**
         * 基于当前宽度估算 tooltip 高度。
         *
         * @param tooltipWidth tooltip 宽度
         * @return 估算高度
         */
        int estimate(int tooltipWidth);
    }

    /**
     * 解析 tooltip 布局结果。
     *
     * @param hostWidth 宿主宽度
     * @param hostHeight 宿主高度
     * @param mouseX 指针 X
     * @param mouseY 指针 Y
     * @param preferredWidth 偏好宽度
     * @param minWidth 最小宽度
     * @param heightEstimator 高度估算器
     * @return 定位结果
     */
    static TooltipPlacement resolve(int hostWidth, int hostHeight, int mouseX, int mouseY,
            int preferredWidth, int minWidth, TooltipHeightEstimator heightEstimator) {
        TooltipPlacement bestPlacement = place(hostWidth, hostHeight, mouseX, mouseY, preferredWidth,
                minWidth, heightEstimator, HorizontalSide.RIGHT, VerticalSide.BOTTOM);
        int bestScore = bestPlacement.score();

        TooltipPlacement[] candidates = new TooltipPlacement[] {
                place(hostWidth, hostHeight, mouseX, mouseY, preferredWidth, minWidth, heightEstimator,
                        HorizontalSide.RIGHT, VerticalSide.TOP),
                place(hostWidth, hostHeight, mouseX, mouseY, preferredWidth, minWidth, heightEstimator,
                        HorizontalSide.LEFT, VerticalSide.BOTTOM),
                place(hostWidth, hostHeight, mouseX, mouseY, preferredWidth, minWidth, heightEstimator,
                        HorizontalSide.LEFT, VerticalSide.TOP) };
        for (TooltipPlacement candidate : candidates) {
            int candidateScore = candidate.score();
            if (candidateScore > bestScore) {
                bestPlacement = candidate;
                bestScore = candidateScore;
            }
        }
        return bestPlacement;
    }

    private static TooltipPlacement place(int hostWidth, int hostHeight, int mouseX, int mouseY,
            int preferredWidth, int minWidth, TooltipHeightEstimator heightEstimator,
            HorizontalSide horizontalSide, VerticalSide verticalSide) {
        int availableWidth = resolveAvailableWidth(hostWidth, mouseX, horizontalSide);
        int tooltipWidth = resolveTooltipWidth(preferredWidth, minWidth, availableWidth);
        int tooltipHeight = Math.max(1, heightEstimator == null ? 1 : heightEstimator.estimate(tooltipWidth));
        int preferredLeft = horizontalSide == HorizontalSide.RIGHT
                ? mouseX + POINTER_DIAGONAL_COMPONENT : mouseX - POINTER_DIAGONAL_COMPONENT - tooltipWidth;
        int preferredTop = verticalSide == VerticalSide.BOTTOM
                ? mouseY + POINTER_DIAGONAL_COMPONENT : mouseY - POINTER_DIAGONAL_COMPONENT - tooltipHeight;

        int left = clamp(preferredLeft, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, hostWidth - tooltipWidth - SCREEN_MARGIN));
        int top = clamp(preferredTop, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, hostHeight - tooltipHeight - SCREEN_MARGIN));

        int hiddenWidth = overflow(preferredLeft, tooltipWidth, hostWidth);
        int hiddenHeight = overflow(preferredTop, tooltipHeight, hostHeight);
        boolean coversPointer = mouseX >= left && mouseX <= left + tooltipWidth
                && mouseY >= top && mouseY <= top + tooltipHeight;
        int availableHeight = resolveAvailableHeight(hostHeight, mouseY, verticalSide);
        int freeSpace = Math.max(0, availableWidth) * Math.max(0, availableHeight);
        return new TooltipPlacement(left, top, tooltipWidth, tooltipHeight, hiddenWidth + hiddenHeight,
                coversPointer, freeSpace, horizontalSide, verticalSide);
    }

    // 这里按 32px 鼠标清空带预留宽高，锚点仍使用对角分量保持右下默认落点。
    private static int resolveAvailableWidth(int hostWidth, int mouseX, HorizontalSide horizontalSide) {
        return horizontalSide == HorizontalSide.RIGHT
                ? Math.max(0, hostWidth - mouseX - POINTER_RADIUS - SCREEN_MARGIN)
                : Math.max(0, mouseX - POINTER_RADIUS - SCREEN_MARGIN);
    }

    // 这里按 32px 鼠标清空带预留宽高，避免 tooltip 贴住指针热区。
    private static int resolveAvailableHeight(int hostHeight, int mouseY, VerticalSide verticalSide) {
        return verticalSide == VerticalSide.BOTTOM
                ? Math.max(0, hostHeight - mouseY - POINTER_RADIUS - SCREEN_MARGIN)
                : Math.max(0, mouseY - POINTER_RADIUS - SCREEN_MARGIN);
    }

    private static int resolveTooltipWidth(int preferredWidth, int minWidth, int availableWidth) {
        int resolvedPreferredWidth = Math.max(1, preferredWidth);
        int resolvedMinWidth = Math.max(1, minWidth);
        if (availableWidth >= resolvedMinWidth) {
            return Math.min(resolvedPreferredWidth, availableWidth);
        }
        if (availableWidth > 0) {
            return availableWidth;
        }
        return Math.min(resolvedPreferredWidth, resolvedMinWidth);
    }

    private static int overflow(int preferredStart, int size, int boundary) {
        int minOverflow = Math.max(0, SCREEN_MARGIN - preferredStart);
        int maxOverflow = Math.max(0, preferredStart + size - (boundary - SCREEN_MARGIN));
        return minOverflow + maxOverflow;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum HorizontalSide {
        RIGHT,
        LEFT
    }

    private enum VerticalSide {
        BOTTOM,
        TOP
    }

    /**
     * tooltip 页面级定位结果。
     */
    static final class TooltipPlacement {

        private final int left;
        private final int top;
        private final int width;
        private final int height;
        private final int overflow;
        private final boolean coversPointer;
        private final int freeSpace;
        private final HorizontalSide horizontalSide;
        private final VerticalSide verticalSide;

        TooltipPlacement(int left, int top, int width, int height, int overflow, boolean coversPointer,
                int freeSpace, HorizontalSide horizontalSide, VerticalSide verticalSide) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.overflow = overflow;
            this.coversPointer = coversPointer;
            this.freeSpace = freeSpace;
            this.horizontalSide = horizontalSide;
            this.verticalSide = verticalSide;
        }

        int getLeft() {
            return left;
        }

        int getTop() {
            return top;
        }

        int getWidth() {
            return width;
        }

        int getHeight() {
            return height;
        }

        int score() {
            int directionBias = horizontalSide == HorizontalSide.RIGHT && verticalSide == VerticalSide.BOTTOM ? 1 : 0;
            return -overflow * 1_000_000 - (coversPointer ? 100_000 : 0) + freeSpace + directionBias;
        }
    }
}
