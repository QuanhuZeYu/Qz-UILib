package club.heiqi.uilib.ui.paint;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 滚动条滑块（thumb）回放期实时重算描述。
 *
 * <p>方案2 下不含 sticky 后代的滚动容器滚动时不重建绘制命令，但 thumb 在轨道内的位置由当前滚动偏移决定，
 * 必须随滚动跟手。track（轨道）位置基于视口框、不随滚动移动，故 thumb 命令落在滚动偏移作用域之外、坐标
 * 不会被回放期偏移栈平移。本描述固化构建期算出的轨道几何与可滚范围，回放期按 {@link DocumentPaintRenderer
 * .ScrollOffsetProvider} 查询实时滚动偏移，仅重算 thumb 在主轴上的起点，避免为了移动 thumb 而整树重建。</p>
 *
 * <p>maxScrollOffset / trackStart / travel / thumbSize 都只在布局变化时改变，而布局变化必然触发命令重建，
 * 因此构建期固化值在命令存活期内有效；只有滚动偏移在存活期内变化，恰由回放期实时查询补齐。</p>
 */
final class DocumentScrollbarThumbReplay {

    private final ElementNode element;
    private final boolean vertical;
    private final int trackStart;
    private final int travel;
    private final int thumbSize;
    private final int maxScrollOffset;

    DocumentScrollbarThumbReplay(ElementNode element, boolean vertical, int trackStart, int travel, int thumbSize,
            int maxScrollOffset) {
        this.element = element;
        this.vertical = vertical;
        this.trackStart = trackStart;
        this.travel = Math.max(0, travel);
        this.thumbSize = Math.max(0, thumbSize);
        this.maxScrollOffset = Math.max(0, maxScrollOffset);
    }

    /**
     * 按当前滚动偏移重算 thumb 在主轴上的起点（命令坐标系，未加屏幕偏移）。
     *
     * @param scrollOffsetProvider 回放期滚动偏移源
     * @return thumb 主轴起点
     */
    int resolveThumbStart(DocumentPaintRenderer.ScrollOffsetProvider scrollOffsetProvider) {
        if (maxScrollOffset <= 0 || travel <= 0) {
            return trackStart;
        }
        int scrollOffset = vertical ? scrollOffsetProvider.getScrollTop(element)
                : scrollOffsetProvider.getScrollLeft(element);
        int clamped = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return trackStart + Math.round(travel * (clamped / (float) maxScrollOffset));
    }

    boolean isVertical() {
        return vertical;
    }

    int getThumbSize() {
        return thumbSize;
    }
}
