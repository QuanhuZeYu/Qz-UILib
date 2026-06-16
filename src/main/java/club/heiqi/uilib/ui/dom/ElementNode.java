package club.heiqi.uilib.ui.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.animation.DocumentAnimation;
import club.heiqi.uilib.ui.animation.DocumentAnimationOptions;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.style.UiStyleChangeImpact;
import club.heiqi.uilib.ui.style.UiStyleChangeListener;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.selector.UiPseudoElement;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * HTML-like 元素节点。
 *
 * <p>本类聚焦于属性表、样式声明、子节点关系等节点本体职责。
 * 具名 interaction handler 与自定义渲染回调由 {@link ElementInteractionNode} 承载，
 * ARIA / tabindex / disabled 等纯函数语义计算交给 {@link ElementSemantics}。</p>
 */
public final class ElementNode extends ElementInteractionNode {

    private final String tagName;
    private final long __elementUid;
    private final ElementNode pseudoOriginElement;
    private final UiPseudoElement pseudoElement;
    private final Map<String, String> attributes = new LinkedHashMap<String, String>();
    private final Map<String, List<DocumentCustomEventHandler>> customEventHandlers =
            new LinkedHashMap<String, List<DocumentCustomEventHandler>>();
    private final Map<String, List<DocumentCustomEventHandler>> captureCustomEventHandlers =
            new LinkedHashMap<String, List<DocumentCustomEventHandler>>();
    private boolean focusable;
    private boolean focusableExplicitlySet;
    private int focusInvalidationVersion;
    private final DomTokenList classList = new DomTokenList(new Runnable() {
        @Override
        public void run() {
            ElementNode.this.markSubtreeMutated();
        }
    });
    private final UiStyleDeclaration style = new UiStyleDeclaration(new UiStyleChangeListener() {
        @Override
        public void onStyleChanged(UiStyleChangeImpact impact) {
            if (impact == UiStyleChangeImpact.COMPOSITE) {
                ElementNode.this.markCompositeMutated();
                return;
            }
            if (impact == UiStyleChangeImpact.PAINT) {
                ElementNode.this.markPaintMutated();
                return;
            }
            ElementNode.this.markSubtreeMutated();
        }
    });

    ElementNode(UiDocument ownerDocument, String tagName) {
        this(ownerDocument, tagName, null, null);
    }

    ElementNode(UiDocument ownerDocument, String tagName, ElementNode pseudoOriginElement,
            UiPseudoElement pseudoElement) {
        super(ownerDocument);
        this.__elementUid = ownerDocument.__allocateElementUid();
        this.tagName = normalizeName(tagName, "tagName");
        this.pseudoOriginElement = pseudoOriginElement;
        this.pseudoElement = pseudoElement;
        if (ElementSemantics.isNativeFocusableTag(this.tagName)) {
            this.focusable = true;
        }
        if ("input".equals(this.tagName)) {
            attributes.put("type", "text");
        }
        if ("textarea".equals(this.tagName)) {
            attributes.put("aria-multiline", "true");
        }
        if (DocumentImageElementSupport.isImageTag(this.tagName)) {
            DocumentImageElementSupport.attach(this);
        }
    }

    @Override
    public DocumentNodeType getNodeType() {
        return DocumentNodeType.ELEMENT;
    }

    @Override
    public DocumentNode cloneNode(boolean deep) {
        ElementNode clone = getOwnerDocument().element(tagName);
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            clone.attributes.put(entry.getKey(), entry.getValue());
        }
        clone.classList.copyFrom(classList);
        clone.style().__copyFromSilently(style);
        clone.focusable = focusable;
        clone.focusableExplicitlySet = focusableExplicitlySet;
        clone.focusInvalidationVersion = 0;
        if (deep) {
            for (DocumentNode child : getChildren()) {
                clone.__appendGeneratedChild(child.cloneNode(true));
            }
        }
        return clone;
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
     * 返回当前元素是否为 `::before` / `::after` 运行时伪元素。
     *
     * @return 是否为伪元素
     */
    public boolean isPseudoElement() {
        return pseudoElement != null;
    }

    /**
     * 返回当前伪元素类型。
     *
     * @return 伪元素类型；普通元素返回 null
     */
    public UiPseudoElement getPseudoElement() {
        return pseudoElement;
    }

    /**
     * 返回伪元素的来源元素。
     *
     * @return 来源元素；普通元素返回 null
     */
    public ElementNode getPseudoOriginElement() {
        return pseudoOriginElement;
    }

    /**
     * 返回框架内部元素唯一身份。
     *
     * <p>该值只供测试、调试、缓存和内部追踪使用，不等同于 HTML `id` 属性，也不会进入属性表或样式选择器。</p>
     *
     * @return 进程内唯一元素身份
     * @apiNote 框架内部 API，仅供测试、调试与运行时缓存使用，业务代码不应依赖此值。
     *          LTS 不承诺此方法的兼容性，未来可能迁移至 {@code dom.internal} 子包或私有化。
     */
    public long __getElementUid() {
        return __elementUid;
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
     * 返回元素的 classList 管理器。
     *
     * <p>提供 add/remove/toggle/contains 等标准操作，变更会触发样式重算。</p>
     *
     * @return classList 管理器
     */
    public DomTokenList getClassList() {
        return classList;
    }

    /**
     * 返回元素的 className（空格分隔的 class 字符串）。
     *
     * @return className 字符串；无 class 时返回空字符串
     */
    public String getClassName() {
        return classList.value();
    }

    /**
     * 设置元素的 className（空格分隔的 class 字符串）。
     *
     * <p>会清除现有 class 并重新解析。</p>
     *
     * @param className 空格分隔的 class 字符串；为 null 或空时清空
     * @return 当前元素
     */
    public ElementNode setClassName(String className) {
        classList.setValue(className);
        return this;
    }

    /**
     * 返回元素的 id 属性。
     *
     * <p>等价于 getAttribute("id")，提供便捷访问。</p>
     *
     * @return id 值；未设置时返回 null
     */
    public String getId() {
        return getAttribute("id");
    }

    /**
     * 设置元素的 id 属性。
     *
     * <p>等价于 setAttribute("id", id)，提供便捷访问。</p>
     *
     * @param id id 值；为 null 时移除 id 属性
     * @return 当前元素
     */
    public ElementNode setId(String id) {
        setAttribute("id", id);
        return this;
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
        boolean changed = !Objects.equals(previousValue, value);
        if (ElementSemantics.isAnchorElement(tagName) && "href".equals(resolvedName)) {
            changed |= updateAnchorFocusableFromHref();
        }
        if (changed) {
            markSubtreeMutated();
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
     * 在框架内部为运行时生成的临时元素静默写入属性，不触发文档布局失效。
     *
     * @param name 属性名
     * @param value 属性值
     * @return 当前元素
     * @apiNote 框架内部 API，仅供布局引擎为临时匿名盒（如 flex 匿名文本项）这类从不挂入文档树的
     *          生成元素设置属性时调用。普通业务代码请使用 {@link #setAttribute}。LTS 不承诺此方法的
     *          兼容性，未来可能迁移至 {@code dom.internal} 子包或私有化。
     */
    public ElementNode __putGeneratedAttribute(String name, String value) {
        attributes.put(normalizeName(name, "name"), value);
        return this;
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
        String previousValue = attributes.remove(resolvedName);
        if (previousValue != null) {
            if (ElementSemantics.isAnchorElement(tagName) && "href".equals(resolvedName)) {
                updateAnchorFocusableFromHref();
            }
            markSubtreeMutated();
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
     * 注册自定义事件 listener。
     *
     * @param type 事件类型
     * @param handler 事件处理器
     * @return 当前元素
     */
    public ElementNode addEventListener(String type, DocumentCustomEventHandler handler) {
        return addEventListener(type, handler, false);
    }

    /**
     * 注册自定义事件 listener。
     *
     * @param type 事件类型
     * @param handler 事件处理器
     * @param capture 是否注册到捕获阶段
     * @return 当前元素
     */
    public ElementNode addEventListener(String type, DocumentCustomEventHandler handler, boolean capture) {
        String resolvedType = normalizeEventType(type);
        DocumentCustomEventHandler resolvedHandler = Objects.requireNonNull(handler, "handler");
        Map<String, List<DocumentCustomEventHandler>> targetMap = capture
                ? captureCustomEventHandlers : customEventHandlers;
        List<DocumentCustomEventHandler> listeners = targetMap.get(resolvedType);
        if (listeners == null) {
            listeners = new ArrayList<DocumentCustomEventHandler>();
            targetMap.put(resolvedType, listeners);
        }
        if (!listeners.contains(resolvedHandler)) {
            listeners.add(resolvedHandler);
        }
        return this;
    }

    /**
     * 移除自定义事件 listener。
     *
     * @param type 事件类型
     * @param handler 事件处理器
     * @return 当前元素
     */
    public ElementNode removeEventListener(String type, DocumentCustomEventHandler handler) {
        return removeEventListener(type, handler, false);
    }

    /**
     * 移除自定义事件 listener。
     *
     * @param type 事件类型
     * @param handler 事件处理器
     * @param capture 是否从捕获阶段移除
     * @return 当前元素
     */
    public ElementNode removeEventListener(String type, DocumentCustomEventHandler handler, boolean capture) {
        if (type == null || handler == null) {
            return this;
        }
        Map<String, List<DocumentCustomEventHandler>> targetMap = capture
                ? captureCustomEventHandlers : customEventHandlers;
        List<DocumentCustomEventHandler> listeners = targetMap.get(normalizeEventType(type));
        if (listeners == null) {
            return this;
        }
        listeners.remove(handler);
        if (listeners.isEmpty()) {
            targetMap.remove(normalizeEventType(type));
        }
        return this;
    }

    /**
     * 派发自定义事件。
     *
     * @param event 自定义事件对象
     * @return 默认行为是否未被阻止
     */
    public boolean dispatchEvent(DocumentCustomEvent event) {
        return getOwnerDocument().__dispatchCustomEvent(this, event);
    }

    List<DocumentCustomEventHandler> __getCustomEventHandlers(String type, boolean capture) {
        List<DocumentCustomEventHandler> handlers = (capture ? captureCustomEventHandlers : customEventHandlers)
                .get(type);
        if (handlers == null || handlers.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<DocumentCustomEventHandler>(handlers));
    }

    Set<String> __getRegisteredCustomEventTypes() {
        Set<String> types = new LinkedHashSet<String>();
        types.addAll(customEventHandlers.keySet());
        types.addAll(captureCustomEventHandlers.keySet());
        return Collections.unmodifiableSet(types);
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
        boolean previousEffectiveFocusable = this.focusable;
        if (this.focusable && !focusable) {
            focusInvalidationVersion++;
        }
        this.focusableExplicitlySet = true;
        this.focusable = focusable;
        boolean nextEffectiveFocusable = this.focusable;
        if (previousEffectiveFocusable != nextEffectiveFocusable) {
            markSubtreeMutated();
        }
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
     * 返回焦点失效版本，用于宿主在元素禁用后清理旧焦点。
     *
     * @return 焦点失效版本
     */
    public int getFocusInvalidationVersion() {
        return focusInvalidationVersion;
    }

    /**
     * 程序化聚焦当前元素。
     *
     * <p>只有已挂载到当前 HTML-like 运行时、允许聚焦、未 disabled 且存在可见布局盒的元素会成功；
     * 其他情况保持无副作用并返回 false。</p>
     *
     * @return 是否成功聚焦当前元素
     */
    public boolean focus() {
        return getOwnerDocument().__focusElement(this);
    }

    /**
     * 程序化清除当前元素的焦点。
     *
     * @return 当前元素原本持有焦点并已失焦时返回 true，否则返回 false
     */
    public boolean blur() {
        return getOwnerDocument().__blurElement(this);
    }

    /**
     * 程序化设置当前元素的滚动偏移。
     *
     * <p>偏移会被限制在当前可滚范围内；节点未挂载、不可见或没有可滚范围时返回 false。</p>
     *
     * @param scrollLeft 横向滚动偏移
     * @param scrollTop 纵向滚动偏移
     * @return 是否存在可滚动运行态并完成调用
     */
    public boolean scrollTo(int scrollLeft, int scrollTop) {
        return getOwnerDocument().__scrollElementTo(this, scrollLeft, scrollTop);
    }

    /**
     * 返回当前元素横向滚动偏移。
     *
     * @return 横向滚动偏移；未挂载或无运行态时返回 0
     */
    public int getScrollLeft() {
        return getOwnerDocument().__getScrollLeft(this);
    }

    /**
     * 返回当前元素纵向滚动偏移。
     *
     * @return 纵向滚动偏移；未挂载或无运行态时返回 0
     */
    public int getScrollTop() {
        return getOwnerDocument().__getScrollTop(this);
    }

    /**
     * 返回当前元素最大横向滚动偏移。
     *
     * @return 最大横向滚动偏移；未挂载或不可横向滚动时返回 0
     */
    public int getMaxScrollLeft() {
        return getOwnerDocument().__getMaxScrollLeft(this);
    }

    /**
     * 返回当前元素最大纵向滚动偏移。
     *
     * @return 最大纵向滚动偏移；未挂载或不可纵向滚动时返回 0
     */
    public int getMaxScrollTop() {
        return getOwnerDocument().__getMaxScrollTop(this);
    }

    /**
     * 程序化滚动最近可滚祖先，使当前元素尽量进入可见区域。
     *
     * <p>节点未挂载、不可见或没有布局盒时返回 false；目标已在可见区域内时返回 true 但不改变滚动偏移。</p>
     *
     * @return 是否找到有效布局目标并完成调用
     */
    public boolean scrollIntoView() {
        return getOwnerDocument().__scrollElementIntoView(this);
    }

    /**
     * 以命令式方式启动 keyframe animation。
     *
     * <p>只有元素已挂载到当前 HTML-like 运行时时才会实际启动；未挂载时返回未运行句柄。</p>
     *
     * @param keyframes keyframes 定义
     * @param options 播放选项
     * @return 动画句柄
     */
    public DocumentAnimation animate(DocumentKeyframes keyframes, DocumentAnimationOptions options) {
        return getOwnerDocument().__animateElement(this, keyframes, options);
    }

    /**
     * 以命令式方式启动 keyframe animation。
     *
     * @param keyframes keyframes 定义
     * @param durationMillis 持续时间，单位毫秒
     * @return 动画句柄
     */
    public DocumentAnimation animate(DocumentKeyframes keyframes, long durationMillis) {
        return animate(keyframes, DocumentAnimationOptions.ofMillis(durationMillis));
    }

    /**
     * 返回 tabindex 属性解析值。
     *
     * @return tabindex；未声明或无法解析时返回 null
     */
    public Integer getTabIndex() {
        return ElementSemantics.resolveTabIndex(this);
    }

    /**
     * 判断元素是否被 aria-hidden 从语义树隐藏。
     *
     * @return 是否语义隐藏
     */
    public boolean isAriaHidden() {
        return ElementSemantics.isAriaHidden(this);
    }

    /**
     * 判断原生表单控件是否处于 disabled 状态。
     *
     * <p>仅对 button/input 等原生表单标签有效；存在 disabled 属性（无论值为 true 还是空串）均视为 disabled。</p>
     *
     * @return 是否处于 disabled 状态
     */
    public boolean isDisabled() {
        return ElementSemantics.isDisabled(this);
    }

    /**
     * 返回元素面向辅助语义的角色。
     *
     * @return 语义角色；无角色时返回 null
     */
    public String getSemanticRole() {
        return ElementSemantics.resolveSemanticRole(this);
    }

    /**
     * 返回元素可访问名称的最小解析结果。
     *
     * @return 可访问名称；无可用名称或 aria-hidden 时返回空字符串
     */
    public String getAccessibleLabel() {
        return ElementSemantics.resolveAccessibleLabel(this);
    }

    /**
     * 返回元素在文档局部坐标系下的当前布局边界。
     *
     * <p>文档尚未挂载到运行时或元素不在当前布局树中时返回不可用边界。</p>
     *
     * @return 当前布局边界
     */
    public DocumentElementBounds getDocumentBounds() {
        return getOwnerDocument().__getElementBounds(this);
    }

    /**
     * 返回元素当前视觉边界，包含 paint-only transform 与动画运行态。
     *
     * @return 当前视觉边界
     * @apiNote 框架内部 API，供内置弹层按视觉位置锚定。业务代码优先使用 {@link #getDocumentBounds()}。
     */
    public DocumentElementBounds __getVisualDocumentBounds() {
        return getOwnerDocument().__getElementVisualBounds(this);
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

    /**
     * 追加原始文本子节点。
     *
     * <p>该入口会显式按 UILib 原始文本处理字符串，不解析 Minecraft `§` 格式码。</p>
     *
     * @param text 文本内容
     * @return 新建文本节点
     */
    public TextNode appendRawText(String text) {
        TextNode textNode = getOwnerDocument().rawText(text);
        appendChild(textNode);
        return textNode;
    }

    /**
     * 追加 Minecraft 格式文本子节点。
     *
     * <p>该入口会显式解析字符串中的 Minecraft `§` 颜色与样式码。</p>
     *
     * @param text 文本内容
     * @return 新建文本节点
     */
    public TextNode appendMinecraftText(String text) {
        TextNode textNode = getOwnerDocument().minecraftText(text);
        appendChild(textNode);
        return textNode;
    }

    /**
     * 追加指定文本模式的文本子节点。
     *
     * @param text 文本内容
     * @param textContentMode 文本内容解析模式
     * @return 新建文本节点
     */
    public TextNode appendText(String text, TextContentMode textContentMode) {
        TextNode textNode = getOwnerDocument().text(text, textContentMode);
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

    private static String normalizeEventType(String type) {
        String normalized = Objects.requireNonNull(type, "type").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("type cannot be empty");
        }
        return normalized;
    }

    private boolean updateAnchorFocusableFromHref() {
        if (!ElementSemantics.isAnchorElement(tagName) || focusableExplicitlySet) {
            return false;
        }
        boolean nextFocusable = ElementSemantics.hasLinkHref(getAttribute("href"));
        if (focusable == nextFocusable) {
            return false;
        }
        if (focusable && !nextFocusable) {
            focusInvalidationVersion++;
        }
        focusable = nextFocusable;
        return true;
    }
}
