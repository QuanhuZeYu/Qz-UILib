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

    DocumentLayoutInlineFragment(ElementNode ownerElement, int left, int top, int width, int height) {
        this.ownerElement = Objects.requireNonNull(ownerElement, "ownerElement");
        this.left = left;
        this.top = top;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
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
}
