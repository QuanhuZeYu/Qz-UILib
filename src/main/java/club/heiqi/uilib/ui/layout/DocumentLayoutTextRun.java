package club.heiqi.uilib.ui.layout;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;

/**
 * HTML-like 直接文本子节点的布局结果。
 */
public final class DocumentLayoutTextRun {

    private final TextNode textNode;
    private final ElementNode ownerElement;
    private final String text;
    private final int left;
    private final int top;
    private final int width;
    private final int height;

    DocumentLayoutTextRun(TextNode textNode, ElementNode ownerElement, String text, int left, int top, int width,
            int height) {
        this.textNode = Objects.requireNonNull(textNode, "textNode");
        this.ownerElement = Objects.requireNonNull(ownerElement, "ownerElement");
        this.text = text == null ? "" : text;
        this.left = left;
        this.top = top;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    /**
     * 返回对应文本节点。
     *
     * @return 文本节点
     */
    public TextNode getTextNode() {
        return textNode;
    }

    /**
     * 返回承载该文本行的元素。
     *
     * @return 所属元素
     */
    public ElementNode getOwnerElement() {
        return ownerElement;
    }

    /**
     * 返回布局时快照的文本内容。
     *
     * @return 文本内容
     */
    public String getText() {
        return text;
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
