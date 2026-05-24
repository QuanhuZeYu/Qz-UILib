package club.heiqi.uilib.ui.dom;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.ui.animation.DocumentAnimation;
import club.heiqi.uilib.ui.animation.DocumentAnimationOptions;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.style.selector.UiSelector;
import club.heiqi.uilib.ui.style.selector.UiPseudoElement;
import club.heiqi.uilib.ui.style.cascade.UiStyleRule;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
import club.heiqi.uilib.ui.style.cascade.UiStyleVariables;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * HTML-like 文档作者入口。
 */
public final class UiDocument {

    private static final AtomicLong NEXT_ELEMENT_UID = new AtomicLong(1L);

    private final ElementNode rootElement;
    private final Map<String, DocumentKeyframes> keyframes = new LinkedHashMap<String, DocumentKeyframes>();
    private final List<UiStyleSheet> styleSheets = new ArrayList<UiStyleSheet>();
    private final List<ElementNode> topLayerElements = new ArrayList<ElementNode>();
    private DocumentLinkActivationHandler linkActivationHandler;
    private UiStyleVariables styleVariables;
    private TextContentMode defaultTextContentMode = TextContentMode.UILIB_RAW;
    private WeakReference<DocumentInteractionRuntime> interactionRuntimeReference;
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
     * 把指定元素注册为文档运行时顶层元素。
     *
     * <p>该入口模拟浏览器 UA top-layer：元素保留原 DOM 父子关系，但布局、绘制与命中由运行时提升到视口顶层。
     * 仅供内置控件表达 select picker、菜单等弹出层语义，页面作者不应直接调用。</p>
     *
     * @param element 待提升的元素
     * @apiNote 框架内部 API，LTS 不承诺兼容性。
     */
    public void __showTopLayerElement(ElementNode element) {
        ElementNode resolvedElement = Objects.requireNonNull(element, "element");
        if (resolvedElement.getOwnerDocument() != this) {
            throw new IllegalArgumentException("top layer element belongs to a different document");
        }
        if (topLayerElements.contains(resolvedElement)) {
            return;
        }
        topLayerElements.add(resolvedElement);
        recordLayoutMutation();
    }

    /**
     * 从文档运行时顶层移除指定元素。
     *
     * @param element 待移除元素
     * @apiNote 框架内部 API，LTS 不承诺兼容性。
     */
    public void __hideTopLayerElement(ElementNode element) {
        if (element == null) {
            return;
        }
        if (topLayerElements.remove(element)) {
            recordLayoutMutation();
        }
    }

    /**
     * 返回当前文档运行时顶层元素快照。
     *
     * @return 顶层元素列表
     * @apiNote 框架内部 API，LTS 不承诺兼容性。
     */
    public List<ElementNode> __getTopLayerElements() {
        return Collections.unmodifiableList(new ArrayList<ElementNode>(topLayerElements));
    }

    /**
     * 判断元素是否处于文档运行时顶层。
     *
     * @param element 待判断元素
     * @return 是否处于顶层
     * @apiNote 框架内部 API，LTS 不承诺兼容性。
     */
    public boolean __isTopLayerElement(ElementNode element) {
        return topLayerElements.contains(element);
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
     * 为指定原始元素创建伪元素运行时载体。
     *
     * @param originElement 关联的原始元素
     * @param pseudoElement 伪元素类型
     * @return 伪元素运行时载体
     * @apiNote 框架内部 API，仅供 UI 库样式与布局运行时调用，业务代码不应使用。
     *          LTS 不承诺此方法的兼容性，未来可能迁移至 {@code dom.internal} 子包或私有化。
     */
    public ElementNode __createPseudoElementRuntime(ElementNode originElement, UiPseudoElement pseudoElement) {
        return new ElementNode(this, "span", originElement, pseudoElement);
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
     * 创建 a 元素。
     *
     * @return a 元素
     */
    public ElementNode a() {
        return element("a");
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
     * 创建 textarea 元素。
     *
     * @return textarea 元素
     */
    public ElementNode textarea() {
        return element("textarea");
    }

    /**
     * 创建 select 元素。
     *
     * @return select 元素
     */
    public ElementNode select() {
        return element("select");
    }

    /**
     * 创建 option 元素。
     *
     * @return option 元素
     */
    public ElementNode option() {
        return element("option");
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
     * 创建 ul 元素。
     *
     * @return ul 元素
     */
    public ElementNode ul() {
        return element("ul");
    }

    /**
     * 创建 ol 元素。
     *
     * @return ol 元素
     */
    public ElementNode ol() {
        return element("ol");
    }

    /**
     * 创建 li 元素。
     *
     * @return li 元素
     */
    public ElementNode li() {
        return element("li");
    }

    /**
     * 创建 p（段落）元素。
     *
     * @return p 元素
     */
    public ElementNode p() {
        return element("p");
    }

    /**
     * 创建 h1 标题元素。
     *
     * @return h1 元素
     */
    public ElementNode h1() {
        return element("h1");
    }

    /**
     * 创建 h2 标题元素。
     *
     * @return h2 元素
     */
    public ElementNode h2() {
        return element("h2");
    }

    /**
     * 创建 h3 标题元素。
     *
     * @return h3 元素
     */
    public ElementNode h3() {
        return element("h3");
    }

    /**
     * 创建 h4 标题元素。
     *
     * @return h4 元素
     */
    public ElementNode h4() {
        return element("h4");
    }

    /**
     * 创建 h5 标题元素。
     *
     * @return h5 元素
     */
    public ElementNode h5() {
        return element("h5");
    }

    /**
     * 创建 h6 标题元素。
     *
     * @return h6 元素
     */
    public ElementNode h6() {
        return element("h6");
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
     * 创建文档片段。
     *
     * @return 文档片段
     */
    public DocumentFragmentNode createDocumentFragment() {
        return new DocumentFragmentNode(this);
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
     * 设置当前文档挂载后的交互运行时。
     *
     * <p>该入口供 HTML-like 宿主组件绑定焦点与滚动运行态，不作为页面作者业务 API。</p>
     *
     * @param interactionRuntime 交互运行时；为 null 时清除绑定
     * @apiNote 框架内部 API，仅供宿主接入层调用，业务代码不应使用。
     *          LTS 不承诺此方法的兼容性，未来可能迁移至 {@code dom.internal} 子包或私有化。
     */
    public void __setInteractionRuntime(DocumentInteractionRuntime interactionRuntime) {
        this.interactionRuntimeReference = interactionRuntime == null ? null
                : new WeakReference<DocumentInteractionRuntime>(interactionRuntime);
    }

    boolean __focusElement(ElementNode element) {
        DocumentInteractionRuntime runtime = getInteractionRuntime();
        return runtime != null && ownsElement(element) && runtime.requestFocus(element);
    }

    boolean __blurElement(ElementNode element) {
        DocumentInteractionRuntime runtime = getInteractionRuntime();
        return runtime != null && ownsElement(element) && runtime.requestBlur(element);
    }

    boolean __scrollElementTo(ElementNode element, int scrollLeft, int scrollTop) {
        DocumentInteractionRuntime runtime = getInteractionRuntime();
        return runtime != null && ownsElement(element) && runtime.requestScrollTo(element, scrollLeft, scrollTop);
    }

    int __getScrollLeft(ElementNode element) {
        DocumentInteractionRuntime runtime = getInteractionRuntime();
        return runtime != null && ownsElement(element) ? runtime.requestScrollLeft(element) : 0;
    }

    int __getScrollTop(ElementNode element) {
        DocumentInteractionRuntime runtime = getInteractionRuntime();
        return runtime != null && ownsElement(element) ? runtime.requestScrollTop(element) : 0;
    }

    int __getMaxScrollLeft(ElementNode element) {
        DocumentInteractionRuntime runtime = getInteractionRuntime();
        return runtime != null && ownsElement(element) ? runtime.requestMaxScrollLeft(element) : 0;
    }

    int __getMaxScrollTop(ElementNode element) {
        DocumentInteractionRuntime runtime = getInteractionRuntime();
        return runtime != null && ownsElement(element) ? runtime.requestMaxScrollTop(element) : 0;
    }

    boolean __scrollElementIntoView(ElementNode element) {
        DocumentInteractionRuntime runtime = getInteractionRuntime();
        return runtime != null && ownsElement(element) && runtime.requestScrollIntoView(element);
    }

    DocumentElementBounds __getElementBounds(ElementNode element) {
        DocumentInteractionRuntime runtime = getInteractionRuntime();
        return runtime != null && ownsElement(element) ? runtime.requestElementBounds(element)
                : DocumentElementBounds.unavailable();
    }

    DocumentAnimation __animateElement(ElementNode element, DocumentKeyframes keyframes,
            DocumentAnimationOptions options) {
        DocumentKeyframes resolvedKeyframes = Objects.requireNonNull(keyframes, "keyframes");
        DocumentAnimationOptions resolvedOptions = options == null ? DocumentAnimationOptions.ofMillis(0L) : options;
        DocumentInteractionRuntime runtime = getInteractionRuntime();
        if (runtime == null || !ownsElement(element)) {
            return DocumentAnimation.inactive(element, resolvedKeyframes.getName(), resolvedOptions);
        }
        return runtime.requestAnimation(element, resolvedKeyframes, resolvedOptions);
    }

    private DocumentInteractionRuntime getInteractionRuntime() {
        return interactionRuntimeReference == null ? null : interactionRuntimeReference.get();
    }

    private boolean ownsElement(ElementNode element) {
        return element != null && element.getOwnerDocument() == this;
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
     * 设置文档级样式变量（等同于 CSS :root 变量）。
     *
     * <p>变量值变更时会自动触发文档样式重算。</p>
     *
     * @param styleVariables 样式变量容器；为 null 时清除变量
     * @return 当前文档
     */
    public UiDocument setStyleVariables(UiStyleVariables styleVariables) {
        if (this.styleVariables != null) {
            this.styleVariables.setChangeCallback(null);
        }
        this.styleVariables = styleVariables;
        if (styleVariables != null) {
            styleVariables.setChangeCallback(new Runnable() {
                @Override
                public void run() {
                    recordLayoutMutation();
                }
            });
        }
        recordLayoutMutation();
        return this;
    }

    /**
     * 返回文档级样式变量。
     *
     * @return 样式变量容器；未设置时返回 null
     */
    public UiStyleVariables getStyleVariables() {
        return styleVariables;
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
     * 设置文档级链接激活处理器。
     *
     * <p>当作者点击 `a[href]` 且事件未被 `preventDefault()` 阻止时，运行时会先尝试片段跳转，
     * 再把激活事件交给这里的处理器，供业务方接管外部页面跳转、回调或埋点。</p>
     *
     * @param linkActivationHandler 链接激活处理器；为 null 时清除
     * @return 当前文档
     */
    public UiDocument setLinkActivationHandler(DocumentLinkActivationHandler linkActivationHandler) {
        this.linkActivationHandler = linkActivationHandler;
        return this;
    }

    public DocumentLinkActivationHandler getLinkActivationHandler() {
        return linkActivationHandler;
    }

    /**
     * 派发内部链接激活事件。
     *
     * <p>该入口供运行时在 `a[href]` 默认激活链路中桥接文档级回调，不作为普通业务 DOM API。</p>
     *
     * @param event 链接激活事件
     * @apiNote 框架内部 API，仅供运行时在链接激活链路中调用，业务代码不应使用。
     *          LTS 不承诺此方法的兼容性，未来可能迁移至 {@code dom.internal} 子包或私有化。
     */
    public void __dispatchLinkActivation(DocumentLinkActivationEvent event) {
        if (linkActivationHandler != null && event != null) {
            linkActivationHandler.onLinkActivated(event);
        }
    }

    boolean __dispatchCustomEvent(ElementNode target, DocumentCustomEvent event) {
        if (target == null || event == null || target.getOwnerDocument() != this) {
            return true;
        }
        List<ElementNode> path = buildAncestorPath(target);
        DocumentEventControl eventControl = event.getEventControl();
        eventControl.reset();

        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int index = path.size() - 1; index > 0; index--) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            dispatchCustomEventOnCurrentTarget(path.get(index), target, event, true, eventControl);
        }

        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            dispatchCustomEventOnCurrentTarget(target, target, event, true, eventControl);
            if (!eventControl.isImmediatePropagationStopped()) {
                dispatchCustomEventOnCurrentTarget(target, target, event, false, eventControl);
            }
        }

        if (event.isBubbles()) {
            eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
            for (int index = 1; index < path.size(); index++) {
                if (eventControl.isPropagationStopped()) {
                    break;
                }
                dispatchCustomEventOnCurrentTarget(path.get(index), target, event, false, eventControl);
            }
        }
        eventControl.setEventPhase(DocumentEventPhase.NONE);
        return !event.isDefaultPrevented();
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
    public List<UiStyleRule> findMatchingRules(ElementNode element, java.util.Set<club.heiqi.uilib.ui.style.selector.UiPseudoClass> activeStates) {
        if (element == null || styleSheets.isEmpty()) {
            return Collections.emptyList();
        }
        List<MatchedStyleRule> allMatched = new ArrayList<MatchedStyleRule>();
        for (int sheetIndex = 0; sheetIndex < styleSheets.size(); sheetIndex++) {
            UiStyleSheet sheet = styleSheets.get(sheetIndex);
            for (UiStyleRule rule : sheet.findMatchingRules(element, activeStates)) {
                allMatched.add(new MatchedStyleRule(rule, sheetIndex));
            }
        }
        if (allMatched.size() > 1) {
            Collections.sort(allMatched, new java.util.Comparator<MatchedStyleRule>() {
                @Override
                public int compare(MatchedStyleRule a, MatchedStyleRule b) {
                    int cmp = a.rule.getSelector().compareSpecificity(b.rule.getSelector());
                    if (cmp != 0) {
                        return cmp;
                    }
                    cmp = Integer.compare(a.styleSheetIndex, b.styleSheetIndex);
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Integer.compare(a.rule.getSourceOrder(), b.rule.getSourceOrder());
                }
            });
        }
        List<UiStyleRule> matchedRules = new ArrayList<UiStyleRule>(allMatched.size());
        for (MatchedStyleRule matched : allMatched) {
            matchedRules.add(matched.rule);
        }
        return matchedRules;
    }

    public List<UiStyleRule> findMatchingRules(ElementNode element,
            java.util.Set<club.heiqi.uilib.ui.style.selector.UiPseudoClass> activeStates,
            UiPseudoElement pseudoElement) {
        if (element == null || styleSheets.isEmpty()) {
            return Collections.emptyList();
        }
        List<MatchedStyleRule> allMatched = new ArrayList<MatchedStyleRule>();
        for (int sheetIndex = 0; sheetIndex < styleSheets.size(); sheetIndex++) {
            UiStyleSheet sheet = styleSheets.get(sheetIndex);
            for (UiStyleRule rule : sheet.findMatchingRules(element, activeStates, pseudoElement)) {
                allMatched.add(new MatchedStyleRule(rule, sheetIndex));
            }
        }
        if (allMatched.size() > 1) {
            Collections.sort(allMatched, new java.util.Comparator<MatchedStyleRule>() {
                @Override
                public int compare(MatchedStyleRule a, MatchedStyleRule b) {
                    int cmp = a.rule.getSelector().compareSpecificity(b.rule.getSelector());
                    if (cmp != 0) {
                        return cmp;
                    }
                    cmp = Integer.compare(a.styleSheetIndex, b.styleSheetIndex);
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Integer.compare(a.rule.getSourceOrder(), b.rule.getSourceOrder());
                }
            });
        }
        List<UiStyleRule> matchedRules = new ArrayList<UiStyleRule>(allMatched.size());
        for (MatchedStyleRule matched : allMatched) {
            matchedRules.add(matched.rule);
        }
        return matchedRules;
    }

    /**
     * 记录跨样式表匹配规则及其挂载顺序，避免不同样式表的局部 sourceOrder 互相污染。
     */
    private static final class MatchedStyleRule {

        private final UiStyleRule rule;
        private final int styleSheetIndex;

        private MatchedStyleRule(UiStyleRule rule, int styleSheetIndex) {
            this.rule = rule;
            this.styleSheetIndex = styleSheetIndex;
        }
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

    private void dispatchCustomEventOnCurrentTarget(ElementNode currentTarget, ElementNode target,
            DocumentCustomEvent event, boolean capture, DocumentEventControl eventControl) {
        List<DocumentCustomEventHandler> handlers = currentTarget.__getCustomEventHandlers(event.getType(), capture);
        for (DocumentCustomEventHandler handler : handlers) {
            if (eventControl.isImmediatePropagationStopped()) {
                break;
            }
            if (handler.onEvent(event.withDispatchTargets(target, currentTarget))) {
                eventControl.stopPropagation();
            }
        }
    }

    private static List<ElementNode> buildAncestorPath(ElementNode target) {
        List<ElementNode> path = new ArrayList<ElementNode>();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            path.add((ElementNode) current);
        }
        return path;
    }

    /**
     * 文档挂载后的交互运行时桥接。
     *
     * <p>由 `HtmlLikeDocumentWidget` 实现，用于让 `ElementNode` 的作者侧 DOM-like API
     * 操作当前运行时焦点与滚动状态。</p>
     */
    public interface DocumentInteractionRuntime {

        /**
         * 请求聚焦指定元素。
         *
         * @param element 目标元素
         * @return 是否成功聚焦
         */
        boolean requestFocus(ElementNode element);

        /**
         * 请求让指定元素失焦。
         *
         * @param element 目标元素
         * @return 是否发生失焦
         */
        boolean requestBlur(ElementNode element);

        /**
         * 请求设置指定元素滚动偏移。
         *
         * @param element 目标滚动元素
         * @param scrollLeft 横向滚动偏移
         * @param scrollTop 纵向滚动偏移
         * @return 是否存在可滚动运行态并完成调用
         */
        boolean requestScrollTo(ElementNode element, int scrollLeft, int scrollTop);

        /**
         * 请求读取指定元素当前横向滚动偏移。
         *
         * @param element 目标滚动元素
         * @return 当前横向滚动偏移
         */
        int requestScrollLeft(ElementNode element);

        /**
         * 请求读取指定元素当前纵向滚动偏移。
         *
         * @param element 目标滚动元素
         * @return 当前纵向滚动偏移
         */
        int requestScrollTop(ElementNode element);

        /**
         * 请求读取指定元素最大横向滚动偏移。
         *
         * @param element 目标滚动元素
         * @return 最大横向滚动偏移
         */
        int requestMaxScrollLeft(ElementNode element);

        /**
         * 请求读取指定元素最大纵向滚动偏移。
         *
         * @param element 目标滚动元素
         * @return 最大纵向滚动偏移
         */
        int requestMaxScrollTop(ElementNode element);

        /**
         * 请求把指定元素滚动到可见区域。
         *
         * @param element 目标元素
         * @return 是否存在有效布局目标并完成调用
         */
        boolean requestScrollIntoView(ElementNode element);

        /**
         * 请求读取指定元素当前布局边界。
         *
         * @param element 目标元素
         * @return 元素布局边界；不可用时返回不可用边界
         */
        default DocumentElementBounds requestElementBounds(ElementNode element) {
            return DocumentElementBounds.unavailable();
        }

        /**
         * 请求对指定元素启动命令式 keyframe animation。
         *
         * @param element 目标元素
         * @param keyframes keyframes 定义
         * @param options 播放选项
         * @return 动画句柄
         */
        DocumentAnimation requestAnimation(ElementNode element, DocumentKeyframes keyframes,
                DocumentAnimationOptions options);
    }
}
