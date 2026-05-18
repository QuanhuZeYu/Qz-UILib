package club.heiqi.uilib.ui.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.style.UiSelector;
import club.heiqi.uilib.ui.style.UiStyleRule;
import club.heiqi.uilib.ui.style.UiStyleSheet;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * HTML-like 文档作者入口。
 */
public final class UiDocument {

    private static final AtomicLong NEXT_ELEMENT_UID = new AtomicLong(1L);

    private final ElementNode rootElement;
    private final Map<String, DocumentKeyframes> keyframes = new LinkedHashMap<String, DocumentKeyframes>();
    private final List<UiStyleSheet> styleSheets = new ArrayList<UiStyleSheet>();
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
     * 挂载样式表到文档。
     *
     * <p>样式表按挂载顺序参与级联计算，后挂载的样式表在同特异性时优先级更高。
     * 挂载后会触发布局失效，使所有元素在下次布局时重新计算样式。</p>
     *
     * @param styleSheet 样式表
     * @return 当前文档
     */
    public UiDocument addStyleSheet(UiStyleSheet styleSheet) {
        Objects.requireNonNull(styleSheet, "styleSheet");
        if (!styleSheets.contains(styleSheet)) {
            styleSheets.add(styleSheet);
            recordLayoutMutation();
        }
        return this;
    }

    /**
     * 移除已挂载的样式表。
     *
     * @param styleSheet 样式表
     * @return 当前文档
     */
    public UiDocument removeStyleSheet(UiStyleSheet styleSheet) {
        if (styleSheets.remove(styleSheet)) {
            recordLayoutMutation();
        }
        return this;
    }

    /**
     * 返回已挂载样式表的只读列表。
     *
     * @return 样式表列表
     */
    public List<UiStyleSheet> getStyleSheets() {
        return Collections.unmodifiableList(styleSheets);
    }

    /**
     * 查找所有样式表中匹配指定元素的规则，按优先级升序排列。
     *
     * <p>跨样式表的规则按特异性排序；同特异性时，后挂载的样式表中的规则优先级更高。</p>
     *
     * @param element 目标元素
     * @return 匹配规则列表（按优先级升序）
     */
    public List<UiStyleRule> findMatchingRules(ElementNode element) {
        return findMatchingRules(element, null);
    }

    /**
     * 查找所有样式表中匹配指定元素的规则（考虑伪类状态），按优先级升序排列。
     *
     * @param element 目标元素
     * @param activeStates 元素当前激活的伪类状态集合；为 null 时伪类选择器不匹配
     * @return 匹配规则列表（按优先级升序）
     */
    public List<UiStyleRule> findMatchingRules(ElementNode element, java.util.Set<club.heiqi.uilib.ui.style.UiPseudoClass> activeStates) {
        if (element == null || styleSheets.isEmpty()) {
            return Collections.emptyList();
        }
        List<UiStyleRule> allMatched = new ArrayList<UiStyleRule>();
        for (UiStyleSheet sheet : styleSheets) {
            allMatched.addAll(sheet.findMatchingRules(element, activeStates));
        }
        if (allMatched.size() > 1) {
            Collections.sort(allMatched, new java.util.Comparator<UiStyleRule>() {
                @Override
                public int compare(UiStyleRule a, UiStyleRule b) {
                    return a.comparePriority(b);
                }
            });
        }
        return allMatched;
    }

    // ========== DOM 查询 ==========

    /**
     * 按 id 属性查找元素。
     *
     * <p>等价于浏览器的 {@code document.getElementById(id)}。
     * 遍历整棵文档树，返回第一个 id 属性匹配的元素。</p>
     *
     * @param id 目标 id 值
     * @return 匹配的元素；未找到时返回 null
     */
    public ElementNode getElementById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        return findElementById(rootElement, id);
    }

    /**
     * 按选择器查找第一个匹配的元素。
     *
     * <p>等价于浏览器的 {@code document.querySelector(selector)}。
     * 按深度优先顺序遍历文档树，返回第一个匹配的元素。</p>
     *
     * @param selectorText 选择器文本（如 ".my-class"、"div#id"、"button"）
     * @return 第一个匹配的元素；未找到时返回 null
     */
    public ElementNode querySelector(String selectorText) {
        if (selectorText == null || selectorText.isEmpty()) {
            return null;
        }
        UiSelector selector = UiSelector.parse(selectorText);
        return findFirstMatch(rootElement, selector);
    }

    /**
     * 按选择器查找所有匹配的元素。
     *
     * <p>等价于浏览器的 {@code document.querySelectorAll(selector)}。
     * 按深度优先顺序遍历文档树，返回所有匹配的元素。</p>
     *
     * @param selectorText 选择器文本
     * @return 匹配的元素列表（按文档顺序）；无匹配时返回空列表
     */
    public List<ElementNode> querySelectorAll(String selectorText) {
        if (selectorText == null || selectorText.isEmpty()) {
            return Collections.emptyList();
        }
        UiSelector selector = UiSelector.parse(selectorText);
        List<ElementNode> results = new ArrayList<ElementNode>();
        collectMatches(rootElement, selector, results);
        return results;
    }

    /**
     * 按标签名查找所有匹配的元素。
     *
     * <p>等价于浏览器的 {@code document.getElementsByTagName(tagName)}。</p>
     *
     * @param tagName 标签名
     * @return 匹配的元素列表（按文档顺序）
     */
    public List<ElementNode> getElementsByTagName(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedTag = tagName.trim().toLowerCase(java.util.Locale.ROOT);
        List<ElementNode> results = new ArrayList<ElementNode>();
        collectByTagName(rootElement, normalizedTag, results);
        return results;
    }

    /**
     * 按 class 名查找所有匹配的元素。
     *
     * <p>等价于浏览器的 {@code document.getElementsByClassName(className)}。</p>
     *
     * @param className 类名（单个类名，不含空格）
     * @return 匹配的元素列表（按文档顺序）
     */
    public List<ElementNode> getElementsByClassName(String className) {
        if (className == null || className.isEmpty()) {
            return Collections.emptyList();
        }
        List<ElementNode> results = new ArrayList<ElementNode>();
        collectByClassName(rootElement, className.trim(), results);
        return results;
    }

    private static ElementNode findElementById(DocumentNode node, String id) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (id.equals(element.getId())) {
                return element;
            }
        }
        for (DocumentNode child : node.getChildren()) {
            ElementNode found = findElementById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static ElementNode findFirstMatch(DocumentNode node, UiSelector selector) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (selector.matches(element)) {
                return element;
            }
        }
        for (DocumentNode child : node.getChildren()) {
            ElementNode found = findFirstMatch(child, selector);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void collectMatches(DocumentNode node, UiSelector selector, List<ElementNode> results) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (selector.matches(element)) {
                results.add(element);
            }
        }
        for (DocumentNode child : node.getChildren()) {
            collectMatches(child, selector, results);
        }
    }

    private static void collectByTagName(DocumentNode node, String tagName, List<ElementNode> results) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (tagName.equals(element.getTagName())) {
                results.add(element);
            }
        }
        for (DocumentNode child : node.getChildren()) {
            collectByTagName(child, tagName, results);
        }
    }

    private static void collectByClassName(DocumentNode node, String className, List<ElementNode> results) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (element.getClassList().contains(className)) {
                results.add(element);
            }
        }
        for (DocumentNode child : node.getChildren()) {
            collectByClassName(child, className, results);
        }
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
