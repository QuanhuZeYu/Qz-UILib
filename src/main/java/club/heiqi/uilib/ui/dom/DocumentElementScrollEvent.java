package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素滚动事件。
 *
 * <p>当元素内部滚动位置变化时触发，类似浏览器的 {@code scroll} 事件。</p>
 */
public final class DocumentElementScrollEvent {

    private final ElementNode target;
    private final int scrollTop;
    private final int scrollLeft;
    private final int scrollHeight;
    private final int scrollWidth;
    private final long timeNanos;

    /**
     * 创建元素滚动事件。
     *
     * @param target 发生滚动的元素
     * @param scrollTop 垂直滚动偏移
     * @param scrollLeft 水平滚动偏移
     * @param scrollHeight 内容总高度
     * @param scrollWidth 内容总宽度
     * @param timeNanos 事件时间戳
     */
    public DocumentElementScrollEvent(ElementNode target, int scrollTop, int scrollLeft,
            int scrollHeight, int scrollWidth, long timeNanos) {
        this.target = Objects.requireNonNull(target, "target");
        this.scrollTop = scrollTop;
        this.scrollLeft = scrollLeft;
        this.scrollHeight = scrollHeight;
        this.scrollWidth = scrollWidth;
        this.timeNanos = timeNanos;
    }

    /**
     * 返回发生滚动的元素。
     *
     * @return 滚动元素
     */
    public ElementNode getTarget() {
        return target;
    }

    /**
     * 返回垂直滚动偏移。
     *
     * @return 垂直滚动位置（像素）
     */
    public int getScrollTop() {
        return scrollTop;
    }

    /**
     * 返回水平滚动偏移。
     *
     * @return 水平滚动位置（像素）
     */
    public int getScrollLeft() {
        return scrollLeft;
    }

    /**
     * 返回内容总高度。
     *
     * @return 内容总高度（像素）
     */
    public int getScrollHeight() {
        return scrollHeight;
    }

    /**
     * 返回内容总宽度。
     *
     * @return 内容总宽度（像素）
     */
    public int getScrollWidth() {
        return scrollWidth;
    }

    /**
     * 返回事件时间戳。
     *
     * @return 时间戳
     */
    public long getTimeNanos() {
        return timeNanos;
    }
}
