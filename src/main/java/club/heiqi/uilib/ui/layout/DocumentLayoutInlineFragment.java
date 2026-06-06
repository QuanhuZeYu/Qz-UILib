package club.heiqi.uilib.ui.layout;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * inline 元素在单行内形成的 fragment 几何。
 */
public final class DocumentLayoutInlineFragment {

    private final ElementNode ownerElement;
    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private final boolean firstForElement;
    private final boolean lastForElement;

    DocumentLayoutInlineFragment(ElementNode ownerElement, int left, int top, int width, int height) {
        this(ownerElement, left, top, width, height, true, true);
    }

    DocumentLayoutInlineFragment(ElementNode ownerElement, int left, int top, int width, int height,
            boolean firstForElement, boolean lastForElement) {
        this.ownerElement = Objects.requireNonNull(ownerElement, "ownerElement");
        this.left = left;
        this.top = top;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.firstForElement = firstForElement;
        this.lastForElement = lastForElement;
    }

    /**
     * 返回拥有该 fragment 的 inline 元素。
     *
     * @return 所属元素
     */
    public ElementNode getOwnerElement() {
        return ownerElement;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getRight() {
        return left + width;
    }

    public int getBottom() {
        return top + height;
    }

    /**
     * 判断该 fragment 是否是所属 inline 元素的首个分片。
     *
     * @return 是否为首片
     */
    public boolean isFirstForElement() {
        return firstForElement;
    }

    /**
     * 判断该 fragment 是否是所属 inline 元素的末个分片。
     *
     * @return 是否为末片
     */
    public boolean isLastForElement() {
        return lastForElement;
    }

    DocumentLayoutInlineFragment translated(int deltaX, int deltaY) {
        if (deltaX == 0 && deltaY == 0) {
            return this;
        }
        return new DocumentLayoutInlineFragment(ownerElement, left + deltaX, top + deltaY, width, height,
                firstForElement, lastForElement);
    }
}
