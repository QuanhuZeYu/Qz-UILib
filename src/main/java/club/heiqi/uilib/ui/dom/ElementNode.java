package club.heiqi.uilib.ui.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.style.UiStyleChangeImpact;
import club.heiqi.uilib.ui.style.UiStyleChangeListener;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.selector.UiPseudoElement;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * HTML-like 元素节点。
 *
 * <p>本类聚焦于属性表、样式声明、子节点关系等"节点本体"职责。
 * 14+4 组具名 interaction handler 与自定义渲染回调通过 {@link ElementInteractionHandlers}
 * 容器持有，ARIA / tabindex / disabled 等纯函数语义计算交给 {@link ElementSemantics}。</p>
 */
public final class ElementNode extends DocumentNode {

    private final String tagName;
    private final long __elementUid;
    private final ElementNode pseudoOriginElement;
    private final UiPseudoElement pseudoElement;
    private final Map<String, String> attributes = new LinkedHashMap<String, String>();
    private final Map<String, List<DocumentCustomEventHandler>> customEventHandlers =
            new LinkedHashMap<String, List<DocumentCustomEventHandler>>();
    private final Map<String, List<DocumentCustomEventHandler>> captureCustomEventHandlers =
            new LinkedHashMap<String, List<DocumentCustomEventHandler>>();
    private final ElementInteractionHandlers handlers = new ElementInteractionHandlers();
    private boolean focusable;
    private boolean focusableExplicitlySet;
    private int focusInvalidationVersion;
    private final DomTokenList classList = new DomTokenList(new Runnable() {
        @Override
        public void run() {
            ElementNode.this.markMutated();
        }
    });
    private final UiStyleDeclaration style = new UiStyleDeclaration(new UiStyleChangeListener() {
        @Override
        public void onStyleChanged(UiStyleChangeImpact impact) {
            if (impact == UiStyleChangeImpact.PAINT) {
                ElementNode.this.markPaintMutated();
                return;
            }
            ElementNode.this.markMutated();
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
        clone.style().copyFrom(style);
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
        String previousValue = attributes.remove(resolvedName);
        if (previousValue != null) {
            if (ElementSemantics.isAnchorElement(tagName) && "href".equals(resolvedName)) {
                updateAnchorFocusableFromHref();
            }
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
            markMutated();
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
     * 设置元素 active 状态处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param activeHandler active 状态处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setActiveHandler(DocumentElementActiveHandler activeHandler) {
        handlers.activeHandler = activeHandler;
        return this;
    }

    /**
     * 返回元素 active 状态处理器。
     *
     * @return active 状态处理器；不存在时返回 null
     */
    public DocumentElementActiveHandler getActiveHandler() {
        return handlers.activeHandler;
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
        handlers.clickHandler = clickHandler;
        return this;
    }

    /**
     * 返回元素点击处理器。
     *
     * @return 点击处理器；不存在时返回 null
     */
    public DocumentElementClickHandler getClickHandler() {
        return handlers.clickHandler;
    }

    /**
     * 设置元素双击处理器。
     *
     * @param doubleClickHandler 双击处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setDoubleClickHandler(DocumentElementDoubleClickHandler doubleClickHandler) {
        handlers.doubleClickHandler = doubleClickHandler;
        return this;
    }

    /**
     * 返回元素双击处理器。
     *
     * @return 双击处理器；不存在时返回 null
     */
    public DocumentElementDoubleClickHandler getDoubleClickHandler() {
        return handlers.doubleClickHandler;
    }

    /**
     * 设置元素右键菜单处理器。
     *
     * @param contextMenuHandler 右键菜单处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setContextMenuHandler(DocumentElementContextMenuHandler contextMenuHandler) {
        handlers.contextMenuHandler = contextMenuHandler;
        return this;
    }

    /**
     * 返回元素右键菜单处理器。
     *
     * @return 右键菜单处理器；不存在时返回 null
     */
    public DocumentElementContextMenuHandler getContextMenuHandler() {
        return handlers.contextMenuHandler;
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
        handlers.focusHandler = focusHandler;
        return this;
    }

    /**
     * 返回元素焦点变化处理器。
     *
     * @return 焦点变化处理器；不存在时返回 null
     */
    public DocumentElementFocusHandler getFocusHandler() {
        return handlers.focusHandler;
    }

    /**
     * 设置元素悬停状态处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param hoverHandler 悬停状态处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setHoverHandler(DocumentElementHoverHandler hoverHandler) {
        handlers.hoverHandler = hoverHandler;
        return this;
    }

    /**
     * 返回元素悬停状态处理器。
     *
     * @return 悬停状态处理器；不存在时返回 null
     */
    public DocumentElementHoverHandler getHoverHandler() {
        return handlers.hoverHandler;
    }

    /**
     * 设置元素拖拽处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param dragHandler 拖拽处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setDragHandler(DocumentElementDragHandler dragHandler) {
        handlers.dragHandler = dragHandler;
        return this;
    }

    /**
     * 返回元素拖拽处理器。
     *
     * @return 拖拽处理器；不存在时返回 null
     */
    public DocumentElementDragHandler getDragHandler() {
        return handlers.dragHandler;
    }

    /**
     * 设置元素 dragstart 处理器。
     *
     * @param dragStartHandler dragstart 处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setDragStartHandler(DocumentElementDragStartHandler dragStartHandler) {
        handlers.dragStartHandler = dragStartHandler;
        return this;
    }

    /**
     * 返回元素 dragstart 处理器。
     *
     * @return dragstart 处理器；不存在时返回 null
     */
    public DocumentElementDragStartHandler getDragStartHandler() {
        return handlers.dragStartHandler;
    }

    /**
     * 设置元素 dragover 处理器。
     *
     * @param dragOverHandler dragover 处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setDragOverHandler(DocumentElementDragOverHandler dragOverHandler) {
        handlers.dragOverHandler = dragOverHandler;
        return this;
    }

    /**
     * 返回元素 dragover 处理器。
     *
     * @return dragover 处理器；不存在时返回 null
     */
    public DocumentElementDragOverHandler getDragOverHandler() {
        return handlers.dragOverHandler;
    }

    /**
     * 设置元素 dragend 处理器。
     *
     * @param dragEndHandler dragend 处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setDragEndHandler(DocumentElementDragEndHandler dragEndHandler) {
        handlers.dragEndHandler = dragEndHandler;
        return this;
    }

    /**
     * 返回元素 dragend 处理器。
     *
     * @return dragend 处理器；不存在时返回 null
     */
    public DocumentElementDragEndHandler getDragEndHandler() {
        return handlers.dragEndHandler;
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
        handlers.keyHandler = keyHandler;
        return this;
    }

    /**
     * 返回元素键盘按键处理器。
     *
     * @return 键盘按键处理器；不存在时返回 null
     */
    public DocumentElementKeyHandler getKeyHandler() {
        return handlers.keyHandler;
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
        handlers.textInputHandler = textInputHandler;
        return this;
    }

    /**
     * 返回元素文本输入处理器。
     *
     * @return 文本输入处理器；不存在时返回 null
     */
    public DocumentElementTextInputHandler getTextInputHandler() {
        return handlers.textInputHandler;
    }

    /**
     * 设置元素鼠标按下处理器。
     *
     * @param mouseDownHandler 鼠标按下处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setMouseDownHandler(DocumentElementMouseDownHandler mouseDownHandler) {
        handlers.mouseDownHandler = mouseDownHandler;
        return this;
    }

    /**
     * 返回元素鼠标按下处理器。
     *
     * @return 鼠标按下处理器；不存在时返回 null
     */
    public DocumentElementMouseDownHandler getMouseDownHandler() {
        return handlers.mouseDownHandler;
    }

    /**
     * 设置元素鼠标抬起处理器。
     *
     * @param mouseUpHandler 鼠标抬起处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setMouseUpHandler(DocumentElementMouseUpHandler mouseUpHandler) {
        handlers.mouseUpHandler = mouseUpHandler;
        return this;
    }

    /**
     * 返回元素鼠标抬起处理器。
     *
     * @return 鼠标抬起处理器；不存在时返回 null
     */
    public DocumentElementMouseUpHandler getMouseUpHandler() {
        return handlers.mouseUpHandler;
    }

    /**
     * 设置元素焦点进入处理器（冒泡版 focus）。
     *
     * @param focusInHandler 焦点进入处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setFocusInHandler(DocumentElementFocusInHandler focusInHandler) {
        handlers.focusInHandler = focusInHandler;
        return this;
    }

    /**
     * 返回元素焦点进入处理器（冒泡版 focus）。
     *
     * @return 焦点进入处理器；不存在时返回 null
     */
    public DocumentElementFocusInHandler getFocusInHandler() {
        return handlers.focusInHandler;
    }

    /**
     * 设置元素过渡结束处理器。
     *
     * @param transitionEndHandler 过渡结束处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setTransitionEndHandler(DocumentElementTransitionEndHandler transitionEndHandler) {
        handlers.transitionEndHandler = transitionEndHandler;
        return this;
    }

    /**
     * 返回元素过渡结束处理器。
     *
     * @return 过渡结束处理器；不存在时返回 null
     */
    public DocumentElementTransitionEndHandler getTransitionEndHandler() {
        return handlers.transitionEndHandler;
    }

    /**
     * 设置元素动画结束处理器。
     *
     * @param animationEndHandler 动画结束处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setAnimationEndHandler(DocumentElementAnimationEndHandler animationEndHandler) {
        handlers.animationEndHandler = animationEndHandler;
        return this;
    }

    /**
     * 返回元素动画结束处理器。
     *
     * @return 动画结束处理器；不存在时返回 null
     */
    public DocumentElementAnimationEndHandler getAnimationEndHandler() {
        return handlers.animationEndHandler;
    }

    /**
     * 设置元素滚动事件处理器。
     *
     * <p>当元素内部滚动位置变化时触发。</p>
     *
     * @param scrollHandler 滚动事件处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setScrollHandler(DocumentElementScrollHandler scrollHandler) {
        handlers.scrollHandler = scrollHandler;
        return this;
    }

    /**
     * 返回元素滚动事件处理器。
     *
     * @return 滚动事件处理器；不存在时返回 null
     */
    public DocumentElementScrollHandler getScrollHandler() {
        return handlers.scrollHandler;
    }

    // ========== 捕获阶段 handler ==========

    /**
     * 设置元素捕获阶段点击处理器。
     *
     * <p>捕获阶段 handler 在事件从根元素向目标元素传播时触发，先于冒泡阶段。</p>
     *
     * @param captureClickHandler 捕获阶段点击处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureClickHandler(DocumentElementClickHandler captureClickHandler) {
        handlers.captureClickHandler = captureClickHandler;
        return this;
    }

    /**
     * 返回元素捕获阶段点击处理器。
     *
     * @return 捕获阶段点击处理器；不存在时返回 null
     */
    public DocumentElementClickHandler getCaptureClickHandler() {
        return handlers.captureClickHandler;
    }

    /**
     * 设置元素捕获阶段鼠标按下处理器。
     *
     * @param captureMouseDownHandler 捕获阶段鼠标按下处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureMouseDownHandler(DocumentElementMouseDownHandler captureMouseDownHandler) {
        handlers.captureMouseDownHandler = captureMouseDownHandler;
        return this;
    }

    /**
     * 返回元素捕获阶段鼠标按下处理器。
     *
     * @return 捕获阶段鼠标按下处理器；不存在时返回 null
     */
    public DocumentElementMouseDownHandler getCaptureMouseDownHandler() {
        return handlers.captureMouseDownHandler;
    }

    /**
     * 设置元素捕获阶段鼠标抬起处理器。
     *
     * @param captureMouseUpHandler 捕获阶段鼠标抬起处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureMouseUpHandler(DocumentElementMouseUpHandler captureMouseUpHandler) {
        handlers.captureMouseUpHandler = captureMouseUpHandler;
        return this;
    }

    /**
     * 返回元素捕获阶段鼠标抬起处理器。
     *
     * @return 捕获阶段鼠标抬起处理器；不存在时返回 null
     */
    public DocumentElementMouseUpHandler getCaptureMouseUpHandler() {
        return handlers.captureMouseUpHandler;
    }

    /**
     * 设置元素捕获阶段键盘处理器。
     *
     * @param captureKeyHandler 捕获阶段键盘处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureKeyHandler(DocumentElementKeyHandler captureKeyHandler) {
        handlers.captureKeyHandler = captureKeyHandler;
        return this;
    }

    /**
     * 返回元素捕获阶段键盘处理器。
     *
     * @return 捕获阶段键盘处理器；不存在时返回 null
     */
    public DocumentElementKeyHandler getCaptureKeyHandler() {
        return handlers.captureKeyHandler;
    }

    /**
     * 设置元素自定义绘制回调，供控件在背景/边框之后注入额外渲染。
     *
     * <p>回调会在 paint engine 的 appendBoxCommands 中被包装为 CUSTOM 命令，
     * 在元素背景和边框绘制之后、clip/子树之前执行。</p>
     * <p>回调会影响绘制命令生成，变更时只提升文档 paint version。</p>
     *
     * @param customRenderer 自定义渲染回调
     */
    public void setCustomRenderer(DocumentCustomRenderer customRenderer) {
        if (handlers.customRenderer == customRenderer) {
            return;
        }
        handlers.customRenderer = customRenderer;
        markPaintMutated();
    }

    /**
     * 返回元素自定义绘制回调。
     *
     * @return 自定义绘制回调；不存在时返回 null
     */
    public DocumentCustomRenderer getCustomRenderer() {
        return handlers.customRenderer;
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
