package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 文档作者入口。
 */
public final class UiDocument {

    private final ElementNode rootElement;
    private int mutationVersion;

    private UiDocument() {
        rootElement = new ElementNode(this, "document");
    }

    /**
     * 创建空文档。
     *
     * @return 文档实例
     */
    public static UiDocument create() {
        return new UiDocument();
    }

    /**
     * 返回文档根元素。
     *
     * @return 根元素
     */
    public ElementNode getRootElement() {
        return rootElement;
    }

    /**
     * 创建指定标签名的元素。
     *
     * @param tagName 标签名
     * @return 元素节点
     */
    public ElementNode element(String tagName) {
        return new ElementNode(this, tagName);
    }

    /**
     * 创建 div 元素。
     *
     * @return div 元素
     */
    public ElementNode div() {
        return element("div");
    }

    /**
     * 创建 span 元素。
     *
     * @return span 元素
     */
    public ElementNode span() {
        return element("span");
    }

    /**
     * 创建 button 元素。
     *
     * @return button 元素
     */
    public ElementNode button() {
        return element("button");
    }

    /**
     * 创建 input 元素。
     *
     * @return input 元素
     */
    public ElementNode input() {
        return element("input");
    }

    /**
     * 创建文本节点。
     *
     * @param text 文本内容
     * @return 文本节点
     */
    public TextNode text(String text) {
        return new TextNode(this, text);
    }

    /**
     * 返回文档变更版本。
     *
     * @return 变更版本
     */
    public int getMutationVersion() {
        return mutationVersion;
    }

    void recordMutation() {
        mutationVersion++;
    }
}
