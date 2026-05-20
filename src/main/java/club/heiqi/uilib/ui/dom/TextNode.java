package club.heiqi.uilib.ui.dom;

import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * HTML-like 文本节点。
 */
public final class TextNode extends DocumentNode {

    private String text;
    private TextContentMode textContentMode = TextContentMode.UILIB_RAW;

    TextNode(UiDocument ownerDocument, String text) {
        super(ownerDocument);
        this.text = normalizeText(text);
        this.textContentMode = ownerDocument.getDefaultTextContentMode();
    }

    @Override
    public DocumentNodeType getNodeType() {
        return DocumentNodeType.TEXT;
    }

    @Override
    public DocumentNode cloneNode(boolean deep) {
        return getOwnerDocument().text(text, textContentMode);
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

    /**
     * 返回当前文本节点的内容解析模式。
     *
     * @return 文本内容解析模式
     */
    public TextContentMode getTextContentMode() {
        return textContentMode;
    }

    /**
     * 设置当前文本节点的内容解析模式。
     *
     * @param textContentMode 文本内容解析模式
     * @return 当前文本节点
     */
    public TextNode setTextContentMode(TextContentMode textContentMode) {
        TextContentMode resolvedMode = textContentMode == null ? TextContentMode.UILIB_RAW : textContentMode;
        if (this.textContentMode != resolvedMode) {
            this.textContentMode = resolvedMode;
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
