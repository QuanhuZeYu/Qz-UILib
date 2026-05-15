package club.heiqi.uilib.ui.dom;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * HTML-like 文档作者入口。
 */
public final class UiDocument {

    private static final AtomicLong NEXT_ELEMENT_UID = new AtomicLong(1L);

    private final ElementNode rootElement;
    private final Map<String, DocumentKeyframes> keyframes = new LinkedHashMap<String, DocumentKeyframes>();
    private TextContentMode defaultTextContentMode = TextContentMode.UILIB_RAW;
    private int mutationVersion;
    private int layoutVersion;
    private int paintVersion;

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
     * 创建 img 元素。
     *
     * @return img 元素
     */
    public ElementNode img() {
        return element("img");
    }

    /**
     * 创建 table 元素。
     *
     * @return table 元素
     */
    public ElementNode table() {
        return element("table");
    }

    /**
     * 创建 thead 元素。
     *
     * @return thead 元素
     */
    public ElementNode thead() {
        return element("thead");
    }

    /**
     * 创建 tbody 元素。
     *
     * @return tbody 元素
     */
    public ElementNode tbody() {
        return element("tbody");
    }

    /**
     * 创建 tfoot 元素。
     *
     * @return tfoot 元素
     */
    public ElementNode tfoot() {
        return element("tfoot");
    }

    /**
     * 创建 tr 元素。
     *
     * @return tr 元素
     */
    public ElementNode tr() {
        return element("tr");
    }

    /**
     * 创建 th 元素。
     *
     * @return th 元素
     */
    public ElementNode th() {
        return element("th");
    }

    /**
     * 创建 td 元素。
     *
     * @return td 元素
     */
    public ElementNode td() {
        return element("td");
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
     * 创建原始文本节点。
     *
     * <p>该入口会显式按 UILib 原始文本处理字符串，不解析 Minecraft `§` 格式码。</p>
     *
     * @param text 文本内容
     * @return 文本节点
     */
    public TextNode rawText(String text) {
        return text(text, TextContentMode.UILIB_RAW);
    }

    /**
     * 创建 Minecraft 格式文本节点。
     *
     * <p>该入口会显式解析字符串中的 Minecraft `§` 颜色与样式码。</p>
     *
     * @param text 文本内容
     * @return 文本节点
     */
    public TextNode minecraftText(String text) {
        return text(text, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 创建指定文本模式的文本节点。
     *
     * @param text 文本内容
     * @param textContentMode 文本内容解析模式
     * @return 文本节点
     */
    public TextNode text(String text, TextContentMode textContentMode) {
        return new TextNode(this, text).setTextContentMode(textContentMode);
    }

    /**
     * 返回当前文档新建文本节点使用的默认解析模式。
     *
     * @return 默认文本内容解析模式
     */
    public TextContentMode getDefaultTextContentMode() {
        return defaultTextContentMode;
    }

    /**
     * 设置当前文档新建文本节点使用的默认解析模式。
     *
     * <p>该设置只影响后续新建的文本节点；已存在节点可通过 `TextNode#setTextContentMode(...)` 单独调整。</p>
     *
     * @param defaultTextContentMode 默认文本内容解析模式
     * @return 当前文档
     */
    public UiDocument setDefaultTextContentMode(TextContentMode defaultTextContentMode) {
        this.defaultTextContentMode = defaultTextContentMode == null ? TextContentMode.UILIB_RAW : defaultTextContentMode;
        return this;
    }

    /**
     * 把后续新建文本节点默认模式切为原始文本。
     *
     * @return 当前文档
     */
    public UiDocument useRawTextByDefault() {
        return setDefaultTextContentMode(TextContentMode.UILIB_RAW);
    }

    /**
     * 把后续新建文本节点默认模式切为 Minecraft 格式文本。
     *
     * @return 当前文档
     */
    public UiDocument useMinecraftTextByDefault() {
        return setDefaultTextContentMode(TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 注册命名 keyframes 定义。
     *
     * @param keyframes keyframes 定义
     * @return 当前文档
     */
    public UiDocument registerKeyframes(DocumentKeyframes keyframes) {
        DocumentKeyframes resolvedKeyframes = Objects.requireNonNull(keyframes, "keyframes");
        DocumentKeyframes previousKeyframes = this.keyframes.put(resolvedKeyframes.getName(), resolvedKeyframes);
        if (previousKeyframes != resolvedKeyframes) {
            recordPaintMutation();
        }
        return this;
    }

    /**
     * 移除命名 keyframes 定义。
     *
     * @param name keyframes 名称
     * @return 当前文档
     */
    public UiDocument unregisterKeyframes(String name) {
        if (keyframes.remove(Objects.requireNonNull(name, "name")) != null) {
            recordPaintMutation();
        }
        return this;
    }

    /**
     * 查找命名 keyframes 定义。
     *
     * @param name keyframes 名称
     * @return keyframes 定义；不存在时返回 null
     */
    public DocumentKeyframes getKeyframes(String name) {
        if (name == null) {
            return null;
        }
        return keyframes.get(name);
    }

    /**
     * 返回只读 keyframes 注册表。
     *
     * @return keyframes 注册表
     */
    public Map<String, DocumentKeyframes> getKeyframesRegistry() {
        return Collections.unmodifiableMap(keyframes);
    }

    /**
     * 返回文档变更版本。
     *
     * @return 变更版本
     */
    public int getMutationVersion() {
        return mutationVersion;
    }

    /**
     * 返回布局失效版本。
     *
     * @return 布局失效版本
     */
    public int getLayoutVersion() {
        return layoutVersion;
    }

    /**
     * 返回绘制失效版本。
     *
     * @return 绘制失效版本
     */
    public int getPaintVersion() {
        return paintVersion;
    }

    void recordMutation() {
        recordLayoutMutation();
    }

    void recordLayoutMutation() {
        mutationVersion++;
        layoutVersion++;
        paintVersion++;
    }

    void recordPaintMutation() {
        mutationVersion++;
        paintVersion++;
    }

    /**
     * 分配进程内唯一的内部元素身份。
     *
     * @return 内部元素身份
     */
    long __allocateElementUid() {
        return NEXT_ELEMENT_UID.getAndIncrement();
    }
}
