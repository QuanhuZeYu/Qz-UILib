package club.heiqi.uilib.ui.dom;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.style.UiStyleDeclaration;

/**
 * HTML-like 元素节点。
 */
public final class ElementNode extends DocumentNode {

    private final String tagName;
    private final Map<String, String> attributes = new LinkedHashMap<String, String>();
    private boolean focusable;
    private DocumentElementActiveHandler activeHandler;
    private DocumentElementClickHandler clickHandler;
    private DocumentElementFocusHandler focusHandler;
    private DocumentElementKeyHandler keyHandler;
    private DocumentElementTextInputHandler textInputHandler;
    private DocumentCustomRenderer customRenderer;
    private final UiStyleDeclaration style = new UiStyleDeclaration(new Runnable() {
        @Override
        public void run() {
            ElementNode.this.markMutated();
        }
    });

    ElementNode(UiDocument ownerDocument, String tagName) {
        super(ownerDocument);
        this.tagName = normalizeName(tagName, "tagName");
    }

    @Override
    public DocumentNodeType getNodeType() {
        return DocumentNodeType.ELEMENT;
    }

    /**
     * 返回规范化后的标签名。
     *
     * @return 标签名
     */
    public String getTagName() {
        return tagName;
    }

    /**
     * 返回元素 inline style 声明入口。
     *
     * @return 样式声明
     */
    public UiStyleDeclaration style() {
        return style;
    }

    /**
     * 返回元素 inline style 声明入口。
     *
     * @return 样式声明
     */
    public UiStyleDeclaration getInlineStyle() {
        return style;
    }

    /**
     * 设置属性值。
     *
     * @param name 属性名
     * @param value 属性值；为 null 时移除属性
     * @return 当前元素
     */
    public ElementNode setAttribute(String name, String value) {
        String resolvedName = normalizeName(name, "name");
        if (value == null) {
            removeAttribute(resolvedName);
            return this;
        }
        String previousValue = attributes.put(resolvedName, value);
        if (!Objects.equals(previousValue, value)) {
            markMutated();
        }
        return this;
    }

    /**
     * 读取属性值。
     *
     * @param name 属性名
     * @return 属性值；不存在时返回 null
     */
    public String getAttribute(String name) {
        return attributes.get(normalizeName(name, "name"));
    }

    /**
     * 判断属性是否存在。
     *
     * @param name 属性名
     * @return 是否存在
     */
    public boolean hasAttribute(String name) {
        return attributes.containsKey(normalizeName(name, "name"));
    }

    /**
     * 移除属性。
     *
     * @param name 属性名
     * @return 当前元素
     */
    public ElementNode removeAttribute(String name) {
        String resolvedName = normalizeName(name, "name");
        if (attributes.remove(resolvedName) != null) {
            markMutated();
        }
        return this;
    }

    /**
     * 返回只读属性表。
     *
     * @return 属性表
     */
    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * 设置元素是否允许获得 HTML-like 焦点。
     *
     * <p>焦点能力只影响输入分发，不影响布局和绘制缓存。</p>
     *
     * @param focusable 是否可聚焦
     * @return 当前元素
     */
    public ElementNode setFocusable(boolean focusable) {
        this.focusable = focusable;
        return this;
    }

    /**
     * 判断元素是否允许获得 HTML-like 焦点。
     *
     * @return 是否可聚焦
     */
    public boolean isFocusable() {
        return focusable;
    }

    /**
     * 设置元素 active 状态处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param activeHandler active 状态处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setActiveHandler(DocumentElementActiveHandler activeHandler) {
        this.activeHandler = activeHandler;
        return this;
    }

    /**
     * 返回元素 active 状态处理器。
     *
     * @return active 状态处理器；不存在时返回 null
     */
    public DocumentElementActiveHandler getActiveHandler() {
        return activeHandler;
    }

    /**
     * 设置元素点击处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param clickHandler 点击处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setClickHandler(DocumentElementClickHandler clickHandler) {
        this.clickHandler = clickHandler;
        return this;
    }

    /**
     * 返回元素点击处理器。
     *
     * @return 点击处理器；不存在时返回 null
     */
    public DocumentElementClickHandler getClickHandler() {
        return clickHandler;
    }

    /**
     * 设置元素焦点变化处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param focusHandler 焦点变化处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setFocusHandler(DocumentElementFocusHandler focusHandler) {
        this.focusHandler = focusHandler;
        return this;
    }

    /**
     * 返回元素焦点变化处理器。
     *
     * @return 焦点变化处理器；不存在时返回 null
     */
    public DocumentElementFocusHandler getFocusHandler() {
        return focusHandler;
    }

    /**
     * 设置元素键盘按键处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param keyHandler 键盘按键处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setKeyHandler(DocumentElementKeyHandler keyHandler) {
        this.keyHandler = keyHandler;
        return this;
    }

    /**
     * 返回元素键盘按键处理器。
     *
     * @return 键盘按键处理器；不存在时返回 null
     */
    public DocumentElementKeyHandler getKeyHandler() {
        return keyHandler;
    }

    /**
     * 设置元素文本输入处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param textInputHandler 文本输入处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setTextInputHandler(DocumentElementTextInputHandler textInputHandler) {
        this.textInputHandler = textInputHandler;
        return this;
    }

    /**
     * 返回元素文本输入处理器。
     *
     * @return 文本输入处理器；不存在时返回 null
     */
    public DocumentElementTextInputHandler getTextInputHandler() {
        return textInputHandler;
    }

    /**
     * 设置元素自定义绘制回调，供控件在背景/边框之后注入额外渲染。
     *
     * <p>回调会在 paint engine 的 appendBoxCommands 中被包装为 CUSTOM 命令，
     * 在元素背景和边框绘制之后、clip/子树之前执行。</p>
     *
     * @param customRenderer 自定义渲染回调
     */
    public void setCustomRenderer(DocumentCustomRenderer customRenderer) {
        this.customRenderer = customRenderer;
    }

    public DocumentCustomRenderer getCustomRenderer() {
        return customRenderer;
    }

    /**
     * 追加子节点并保持元素链式调用。
     *
     * @param child 子节点
     * @return 当前元素
     */
    public ElementNode append(DocumentNode child) {
        appendChild(child);
        return this;
    }

    /**
     * 追加文本子节点。
     *
     * @param text 文本内容
     * @return 新建文本节点
     */
    public TextNode appendText(String text) {
        TextNode textNode = getOwnerDocument().text(text);
        appendChild(textNode);
        return textNode;
    }

    private static String normalizeName(String value, String parameterName) {
        String normalized = Objects.requireNonNull(value, parameterName).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(parameterName + " cannot be empty");
        }
        return normalized;
    }
}
