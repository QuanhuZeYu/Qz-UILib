package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 文本节点。
 */
public final class TextNode extends DocumentNode {

    private String text;

    TextNode(UiDocument ownerDocument, String text) {
        super(ownerDocument);
        this.text = normalizeText(text);
    }

    @Override
    public DocumentNodeType getNodeType() {
        return DocumentNodeType.TEXT;
    }

    /**
     * 返回文本内容。
     *
     * @return 文本内容
     */
    public String getText() {
        return text;
    }

    /**
     * 更新文本内容。
     *
     * @param text 文本内容；为 null 时按空文本处理
     * @return 当前文本节点
     */
    public TextNode setText(String text) {
        String resolvedText = normalizeText(text);
        if (!this.text.equals(resolvedText)) {
            this.text = resolvedText;
            markMutated();
        }
        return this;
    }

    @Override
    protected boolean allowsChildren() {
        return false;
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text;
    }
}
