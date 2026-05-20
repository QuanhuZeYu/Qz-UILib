package club.heiqi.uilib.ui.dom;

/**
 * DOM 文档片段节点。
 *
 * <p>文档片段本身不会进入最终文档树；作为 DOM 操作参数时会按浏览器语义展开其子节点。</p>
 */
public final class DocumentFragmentNode extends DocumentNode {

    DocumentFragmentNode(UiDocument ownerDocument) {
        super(ownerDocument);
    }

    @Override
    public DocumentNodeType getNodeType() {
        return DocumentNodeType.DOCUMENT_FRAGMENT;
    }

    @Override
    public DocumentNode cloneNode(boolean deep) {
        DocumentFragmentNode clone = getOwnerDocument().createDocumentFragment();
        if (deep) {
            for (DocumentNode child : getChildren()) {
                clone.__appendGeneratedChild(child.cloneNode(true));
            }
        }
        return clone;
    }
}
