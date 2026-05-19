package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.animation.DocumentAnimationClock;
import club.heiqi.uilib.ui.animation.DocumentAnimationImpact;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.animation.SystemDocumentAnimationClock;
import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentEventControl;
import club.heiqi.uilib.ui.dom.DocumentEventPhase;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragOverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragStartHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusInEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusInHandler;
import club.heiqi.uilib.ui.dom.DocumentElementMouseDownEvent;
import club.heiqi.uilib.ui.dom.DocumentElementMouseDownHandler;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpEvent;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpHandler;
import club.heiqi.uilib.ui.dom.DocumentElementScrollEvent;
import club.heiqi.uilib.ui.dom.DocumentElementScrollHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.layout.DocumentHitTestEngine;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.paint.DocumentPaintRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiCursor;
import club.heiqi.uilib.ui.style.UiPseudoClass;
import club.heiqi.uilib.ui.style.UiPointerEvents;
import club.heiqi.uilib.ui.style.UiStyleResolver;
import club.heiqi.uilib.ui.style.UiVisibility;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 将 HTML-like 文档模型挂接到现有 retained widget 渲染后端的适配器。
 */
public final class HtmlLikeDocumentWidget extends Widget implements UiDocument.DocumentInteractionRuntime {

    private static final int DRAG_ACTIVATION_THRESHOLD_PX = 4;
    private static final int DRAG_ACTIVATION_THRESHOLD_SQUARED =
            DRAG_ACTIVATION_THRESHOLD_PX * DRAG_ACTIVATION_THRESHOLD_PX;
    private static final String HIT_TEST_PASSTHROUGH_ATTRIBUTE = "data-hit-test-passthrough";

    private final UiDocument document;
    private final TextMeasureService textMeasureService;
    private final DocumentScrollState scrollState = new DocumentScrollState();
    private final DocumentAnimationTimeline animationTimeline = new DocumentAnimationTimeline();
    private final int preferredWidth;
    private final int preferredHeight;
    private DocumentCursorHost cursorHost = DocumentCursorHost.system();
    private DocumentAnimationClock animationClock = SystemDocumentAnimationClock.getInstance();
    private int cachedLayoutVersion = -1;
    private int cachedPaintVersion = -1;
    private int cachedTextMeasureEpoch = -1;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int cachedPaintScrollVersion = -1;
    private int paintCacheGeneration;
    private int staticLayoutGeneration;
    private int runtimeLayoutGeneration;
    private boolean cachedPaintTransientScrollbarActive;
    private DocumentLayoutBox cachedLayoutBox;
    private DocumentLayoutBox cachedRuntimeLayoutBox;
    private int cachedRuntimeLayoutVersion = -1;
    private int cachedRuntimePaintVersion = -1;
    private int cachedRuntimeTextMeasureEpoch = -1;
    private int cachedRuntimeWidth = -1;
    private int cachedRuntimeHeight = -1;
    private boolean cachedRuntimeViewportRootScrollingEnabled;
    private ElementNode pressedElement;
    private ElementNode focusedElement;
    private ElementNode hoveredElement;
    private ElementNode draggingElement;
    private ElementNode htmlDragSourceElement;
    private int scrollEventCount;
    private int lastScrollWheelDelta;
    private boolean lastScrollConsumed;
    private long lastScrollEventTimeNanos;
    private boolean focusedElementFocusVisible;
    private int focusedElementInvalidationVersion;
    private boolean viewportRootScrollingEnabled;
    private boolean cachedLayoutScrollStateUpdated;
    private List<DocumentPaintCommand> cachedPaintCommands = Collections.emptyList();
    private int dragStartDocumentX;
    private int dragStartDocumentY;
    private int lastDragDocumentX;
    private int lastDragDocumentY;
    private boolean dragActivated;
    private boolean htmlDragStarted;
    /** raw button 默认键盘行为：Space 按下状态追踪（元素 uid -> 是否 spacePressed）。 */
    private final java.util.Map<Long, Boolean> rawButtonSpacePressed = new java.util.HashMap<Long, Boolean>();

    /**
     * 创建 HTML-like 文档适配组件。
     *
     * @param document HTML-like 文档
     * @param preferredWidth 作为旧 widget 布局后端子项时的默认宽度
     * @param preferredHeight 作为旧 widget 布局后端子项时的默认高度
     */
    public HtmlLikeDocumentWidget(UiDocument document, int preferredWidth, int preferredHeight) {
        this(document, preferredWidth, preferredHeight, DefaultTextMeasureService.getInstance());
    }

    /**
     * 使用指定文本测量服务创建 HTML-like 文档适配组件。
     *
     * @param document HTML-like 文档
     * @param preferredWidth 作为旧 widget 布局后端子项时的默认宽度
     * @param preferredHeight 作为旧 widget 布局后端子项时的默认高度
     * @param textMeasureService 文本测量服务
     */
    public HtmlLikeDocumentWidget(UiDocument document, int preferredWidth, int preferredHeight,
            TextMeasureService textMeasureService) {
        this.document = Objects.requireNonNull(document, "document");
        this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
        this.preferredWidth = Math.max(0, preferredWidth);
        this.preferredHeight = Math.max(0, preferredHeight);
        this.document.__setInteractionRuntime(this);
    }

    /**
     * 返回当前挂接的 HTML-like 文档。
     *
     * @return 文档实例
     */
    public UiDocument getDocument() {
        return document;
    }

    /**
     * 返回当前布局使用的文本测量服务。
     *
     * @return 文本测量服务
     */
    public TextMeasureService getTextMeasureService() {
        return textMeasureService;
    }

    /**
     * 设置系统光标宿主，供运行时与测试替换真实宿主实现。
     *
     * @param cursorHost 光标宿主
     * @return 当前组件
     */
    HtmlLikeDocumentWidget setCursorHost(DocumentCursorHost cursorHost) {
        this.cursorHost = Objects.requireNonNull(cursorHost, "cursorHost");
        syncCursorFromHoveredElement();
        return this;
    }

    /**
     * 启用或关闭 HTML-like 根元素视口滚动模式。
     *
     * <p>启用后根元素会按当前 widget 尺寸固定为视口盒，页面级滚动交给根元素的
     * `overflow:auto` 与 `DocumentScrollState`，避免外层 retained 页面壳因聚焦或点击而调整滚动位置。</p>
     *
     * @param enabled 是否启用
     * @return 当前组件
     */
    public HtmlLikeDocumentWidget setViewportRootScrollingEnabled(boolean enabled) {
        if (viewportRootScrollingEnabled == enabled) {
            return this;
        }
        viewportRootScrollingEnabled = enabled;
        cachedLayoutVersion = -1;
        cachedPaintScrollVersion = -1;
        cachedLayoutScrollStateUpdated = false;
        invalidateRuntimeLayoutCache();
        requestLayout();
        return this;
    }

    /**
     * 返回当前是否启用根元素视口滚动模式。
     *
     * @return 是否启用
     */
    public boolean isViewportRootScrollingEnabled() {
        return viewportRootScrollingEnabled;
    }

    /**
     * 设置 HTML-like 动画时间源。
     *
     * @param animationClock 动画时间源
     * @return 当前组件
     */
    public HtmlLikeDocumentWidget setAnimationClock(DocumentAnimationClock animationClock) {
        this.animationClock = Objects.requireNonNull(animationClock, "animationClock");
        cachedPaintScrollVersion = -1;
        invalidateRuntimeLayoutCache();
        return this;
    }

    /**
     * 返回当前未完成的动画数量。
     *
     * @return 未完成动画数量
     */
    public int getActiveAnimationCount() {
        return animationTimeline.getActiveAnimationCount(animationClock.getCurrentTimeNanos());
    }

    /**
     * 返回当前动画时间线的只读诊断快照。
     *
     * <p>该方法仅供 Smoke 页和测试诊断使用，状态来自最近一次布局或绘制刷新后的时间线快照。</p>
     *
     * @return 动画运行态诊断快照
     */
    public DocumentAnimationTimeline.DiagnosticsSnapshot getAnimationDiagnosticsSnapshot() {
        return animationTimeline.getDiagnosticsSnapshot(animationClock.getCurrentTimeNanos());
    }

    /**
     * 返回当前动画时间线中是否存在 layout 运行态覆盖值。
     *
     * <p>该方法仅供 Smoke 页和测试诊断使用，状态来自最近一次布局或绘制刷新后的时间线快照。</p>
     *
     * @return 是否存在 layout 运行态覆盖值
     */
    public boolean hasLayoutRuntimeValueForDiagnostics() {
        return animationTimeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT);
    }

    /**
     * 返回当前交互布局盒，仅供测试与诊断读取。
     *
     * @return 当前布局盒
     */
    public DocumentLayoutBox resolveLayoutBoxForTest() {
        return resolveInteractiveLayoutBox();
    }

    /**
     * 返回 paint command 缓存重建代数，供诊断和测试确认动画缓存边界。
     *
     * @return paint command 缓存重建代数
     */
    public int getPaintCacheGenerationForDiagnostics() {
        return paintCacheGeneration;
    }

    /**
     * 返回当前 HTML-like 文档组件缓存与布局重建的只读诊断快照。
     *
     * <p>该方法仅供 Smoke 页和测试验证缓存边界使用，不作为页面作者业务 API。</p>
     *
     * @return 缓存与布局重建诊断快照
     */
    public PerformanceDiagnosticsSnapshot getPerformanceDiagnosticsSnapshot() {
        return new PerformanceDiagnosticsSnapshot(paintCacheGeneration, staticLayoutGeneration,
                runtimeLayoutGeneration, textMeasureService.getEpoch());
    }

    /**
     * 返回指定元素当前纵向滚动偏移。
     *
     * @param element HTML-like 元素
     * @return 纵向滚动偏移
     */
    public int getScrollTop(ElementNode element) {
        resolveInteractiveLayoutBox();
        return scrollState.getScrollTop(element);
    }

    /**
     * 返回指定元素最大纵向滚动偏移。
     *
     * @param element HTML-like 元素
     * @return 最大纵向滚动偏移
     */
    public int getMaxScrollTop(ElementNode element) {
        resolveInteractiveLayoutBox();
        return scrollState.getMaxScrollTop(element);
    }

    /**
     * 返回最近一次滚轮输入的诊断快照。
     *
     * <p>仅供 HUD / 诊断页排查输入链路使用，不作为业务作者 API。</p>
     *
     * @return 滚轮输入诊断快照
     */
    public ScrollInputDiagnosticsSnapshot getScrollInputDiagnosticsSnapshot() {
        return new ScrollInputDiagnosticsSnapshot(scrollEventCount, lastScrollWheelDelta, lastScrollConsumed,
                lastScrollEventTimeNanos);
    }

    /**
     * 返回屏幕坐标命中的 HTML-like 元素。
     *
     * @param screenX 屏幕 X
     * @param screenY 屏幕 Y
     * @return 命中的最深元素；未命中时返回 null
     */
    public ElementNode findElementAt(int screenX, int screenY) {
        if (getWidth() <= 0 || getHeight() <= 0 || !contains(screenX, screenY)) {
            return null;
        }
        return DocumentHitTestEngine.hitTest(resolveInteractiveLayoutBox(), scrollState, screenX - getAbsoluteX(),
                screenY - getAbsoluteY());
    }

    /**
     * 返回当前获得 HTML-like 焦点的元素。
     *
     * @return 聚焦元素；没有元素聚焦时返回 null
     */
    public ElementNode getFocusedElement() {
        return getActiveFocusedElement();
    }

    @Override
    public boolean requestFocus(ElementNode element) {
        if (!isProgrammaticFocusTarget(element)) {
            return false;
        }
        focusElement(element, false);
        scrollElementIntoView(element);
        return getActiveFocusedElement() == element;
    }

    @Override
    public boolean requestBlur(ElementNode element) {
        if (element == null || element.getOwnerDocument() != document || getActiveFocusedElement() != element) {
            return false;
        }
        focusElement(null, false);
        return true;
    }

    @Override
    public boolean requestScrollTo(ElementNode element, int scrollLeft, int scrollTop) {
        if (!isVisibleLayoutTarget(element)) {
            return false;
        }
        if (scrollState.getMaxScrollLeft(element) <= 0 && scrollState.getMaxScrollTop(element) <= 0) {
            return false;
        }
        if (scrollState.setScrollOffset(element, scrollLeft, scrollTop)) {
            dispatchScroll(element, System.nanoTime());
        }
        return true;
    }

    @Override
    public boolean requestScrollIntoView(ElementNode element) {
        return scrollElementIntoView(element);
    }

    /**
     * 判断指定元素是否会在当前文档内形成实际交互命中。
     *
     * @param target 待判断元素
     * @return 是否属于可交互命中目标
     */
    public boolean isInteractiveHit(ElementNode target) {
        if (target == null || !isElementAttachedToDocument(target)) {
            return false;
        }
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            if (UiStyleResolver.compute(currentElement).getPointerEvents() == UiPointerEvents.NONE) {
                continue;
            }
            if (currentElement.isFocusable()
                    || currentElement.getClickHandler() != null
                    || currentElement.getDragHandler() != null
                    || currentElement.getDragStartHandler() != null
                    || currentElement.getDragOverHandler() != null
                    || currentElement.getDragEndHandler() != null
                    || "true".equals(currentElement.getAttribute("draggable"))
                    || currentElement.getKeyHandler() != null
                    || currentElement.getTextInputHandler() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断指定元素是否命中了显式声明的可穿透区域。
     *
     * @param target 待判断元素
     * @return 是否属于显式可穿透命中目标
     */
    public boolean isPassthroughHit(ElementNode target) {
        if (target == null || !isElementAttachedToDocument(target)) {
            return false;
        }
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            if ("true".equals(currentElement.getAttribute(HIT_TEST_PASSTHROUGH_ATTRIBUTE))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getPreferredWidth() {
        return preferredWidth;
    }

    @Override
    public int getPreferredHeight() {
        return preferredHeight;
    }

    @Override
    public int getPreferredHeightForWidth(int width) {
        if (viewportRootScrollingEnabled) {
            return preferredHeight;
        }
        if (width <= 0 || width == preferredWidth) {
            return preferredHeight;
        }
        staticLayoutGeneration++;
        DocumentLayoutBox box = DocumentLayoutEngine.layout(document.getRootElement(), width, 0,
                textMeasureService);
        return Math.max(preferredHeight, box.getHeight());
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        DocumentPaintRenderer.render(context, resolvePaintCommands(), getAbsoluteX(), getAbsoluteY());
    }

    @Override
    public boolean onMouseScroll(UiMouseEvent event) {
        if (getWidth() <= 0 || getHeight() <= 0 || event == null) {
            return false;
        }
        scrollEventCount++;
        lastScrollWheelDelta = event.getWheelDelta();
        lastScrollEventTimeNanos = event.getTimeNanos();
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        boolean consumed = scrollState.handleWheel(rootBox, event.getMouseX() - getAbsoluteX(),
                event.getMouseY() - getAbsoluteY(), event.getWheelDelta());
        lastScrollConsumed = consumed;
        if (consumed) {
            updateHoveredElement(findElementAt(event.getMouseX(), event.getMouseY()), event);
        }
        return consumed;
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {
        if (event == null) {
            pressedElement = null;
            clearDragState();
            return;
        }
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        if (event.getButton() == 0 && scrollState.beginScrollbarDrag(rootBox, event.getMouseX() - getAbsoluteX(),
                event.getMouseY() - getAbsoluteY())) {
            pressedElement = null;
            clearDragState();
            return;
        }
        pressedElement = findElementAt(event.getMouseX(), event.getMouseY());
        updateHoveredElement(pressedElement, event);
        beginDragIfNeeded(pressedElement, event);
        dispatchMouseDown(pressedElement, event);
        dispatchActive(pressedElement, true, event);
        focusElement(resolveFocusableElement(pressedElement), false);
        syncCursorFromHoveredElement();
    }

    @Override
    public void onMouseMove(UiMouseEvent event) {
        if (event == null) {
            return;
        }
        if (scrollState.isDraggingScrollbar()) {
            scrollState.updateScrollbarDrag(resolveInteractiveLayoutBox(), event.getMouseX() - getAbsoluteX(),
                    event.getMouseY() - getAbsoluteY());
        }
        dispatchDragMove(event);
        updateHoveredElement(findElementAt(event.getMouseX(), event.getMouseY()), event);
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        if (event == null) {
            pressedElement = null;
            clearDragState();
            return;
        }
        if (event.getButton() == 0 && scrollState.endScrollbarDrag()) {
            pressedElement = null;
            clearDragState();
            return;
        }
        ElementNode releasedElement = findElementAt(event.getMouseX(), event.getMouseY());
        updateHoveredElement(releasedElement, event);
        ElementNode target = pressedElement != null && pressedElement == releasedElement ? releasedElement : null;
        boolean dragHandled = dispatchDragEnd(event);
        dispatchMouseUp(pressedElement, event);
        dispatchActive(pressedElement, false, event);
        pressedElement = null;
        syncCursorFromHoveredElement();
        if (!dragHandled && target != null) {
            dispatchClick(target, event);
        }
    }

    @Override
    public void onKeyEvent(UiKeyEvent event) {
        ElementNode target = getActiveFocusedElement();
        if (event == null || target == null) {
            return;
        }
        boolean consumed = dispatchKey(target, event);
        if (!consumed) {
            dispatchNativeButtonDefaultKeyBehavior(target, event);
        }
    }

    @Override
    public void onTextInput(UiTextInputEvent event) {
        ElementNode target = getActiveFocusedElement();
        if (event != null && target != null && !target.isDisabled()) {
            dispatchTextInput(target, event);
        }
    }

    @Override
    public void onMouseLeave() {
        updateHoveredElement(null, null);
    }

    @Override
    public void onFocusChanged(boolean focused) {
        if (!focused) {
            updateHoveredElement(null, null);
            focusElement(null, false);
        }
    }

    @Override
    public void onFocusTraversalEntered(boolean reverse) {
        focusFirstElementInTraversalOrder(reverse);
    }

    @Override
    public boolean onFocusTraversal(boolean reverse) {
        List<ElementNode> focusableElements = collectFocusableElements();
        if (focusableElements.isEmpty()) {
            return false;
        }

        ElementNode activeElement = getActiveFocusedElement();
        int currentIndex = activeElement == null ? -1 : focusableElements.indexOf(activeElement);
        if (currentIndex < 0) {
            focusElement(reverse ? focusableElements.get(focusableElements.size() - 1) : focusableElements.get(0), true);
            return true;
        }

        int nextIndex = reverse ? currentIndex - 1 : currentIndex + 1;
        if (nextIndex < 0 || nextIndex >= focusableElements.size()) {
            return false;
        }
        focusElement(focusableElements.get(nextIndex), true);
        return true;
    }

    @Override
    public boolean isFocusable() {
        return hasFocusableElement(document.getRootElement());
    }

    /**
     * 滚轮输入诊断快照。
     */
    public static final class ScrollInputDiagnosticsSnapshot {

        private final int eventCount;
        private final int lastWheelDelta;
        private final boolean lastConsumed;
        private final long lastEventTimeNanos;

        private ScrollInputDiagnosticsSnapshot(int eventCount, int lastWheelDelta, boolean lastConsumed,
                long lastEventTimeNanos) {
            this.eventCount = eventCount;
            this.lastWheelDelta = lastWheelDelta;
            this.lastConsumed = lastConsumed;
            this.lastEventTimeNanos = lastEventTimeNanos;
        }

        public int getEventCount() {
            return eventCount;
        }

        public int getLastWheelDelta() {
            return lastWheelDelta;
        }

        public boolean isLastConsumed() {
            return lastConsumed;
        }

        public long getLastEventTimeNanos() {
            return lastEventTimeNanos;
        }
    }

    private List<DocumentPaintCommand> resolvePaintCommands() {
        DocumentLayoutBox rootBox = resolvePaintLayoutBox(false);
        long currentTimeNanos = animationClock.getCurrentTimeNanos();
        boolean animationStateChanged = animationTimeline.updateFromLayout(rootBox, currentTimeNanos);
        animationStateChanged |= animationTimeline.pruneFinishedAnimations(currentTimeNanos);
        boolean layoutRuntimeValueActive = animationTimeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT);
        if (layoutRuntimeValueActive) {
            rootBox = resolveRuntimeLayoutBox(currentTimeNanos,
                    animationTimeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        } else {
            invalidateRuntimeLayoutCache();
            updateScrollStateFromCachedLayoutIfNeeded();
        }
        int scrollVersion = scrollState.getVersion();
        boolean animationWork = animationTimeline.hasAnimationWork();
        boolean transientScrollbarActive = scrollState.hasActiveTransientScrollbars(currentTimeNanos);
        if (!animationStateChanged && !animationWork && cachedPaintScrollVersion == scrollVersion
                && cachedPaintTransientScrollbarActive == transientScrollbarActive) {
            return cachedPaintCommands;
        }

        cachedPaintCommands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState, currentTimeNanos,
                animationTimeline);
        paintCacheGeneration++;
        cachedPaintScrollVersion = scrollVersion;
        cachedPaintTransientScrollbarActive = transientScrollbarActive;
        return cachedPaintCommands;
    }

    private DocumentLayoutBox resolveRuntimeLayoutBox(final long currentTimeNanos, boolean layoutAnimationWork) {
        if (!layoutAnimationWork && isRuntimeLayoutCacheReusable()) {
            scrollState.updateFromLayout(cachedRuntimeLayoutBox);
            return cachedRuntimeLayoutBox;
        }

        DocumentLayoutEngine.LayoutRuntimeValueResolver layoutValueResolver =
                new DocumentLayoutEngine.LayoutRuntimeValueResolver() {
                    @Override
                    public int resolve(ElementNode element, DocumentAnimationProperty property, int baseValue) {
                        return Math.round(animationTimeline.resolveFloat(element, property, baseValue,
                                currentTimeNanos));
                    }
        };
        DocumentLayoutBox rootBox = layoutDocument(layoutValueResolver);
        runtimeLayoutGeneration++;
        if (layoutAnimationWork) {
            invalidateRuntimeLayoutCache();
        } else {
            cacheRuntimeLayoutBox(rootBox);
        }
        scrollState.updateFromLayout(rootBox);
        return rootBox;
    }

    private DocumentLayoutBox resolvePaintLayoutBox(boolean updateScrollState) {
        DocumentLayoutBox rootBox = resolveLayoutBox(updateScrollState);
        int paintVersion = document.getPaintVersion();
        if (cachedPaintVersion == paintVersion) {
            return rootBox;
        }

        cachedLayoutBox = rootBox.refreshComputedStyles();
        cachedPaintVersion = paintVersion;
        cachedPaintScrollVersion = -1;
        invalidateRuntimeLayoutCache();
        return cachedLayoutBox;
    }

    private DocumentLayoutBox resolveLayoutBox() {
        return resolveLayoutBox(true);
    }

    private DocumentLayoutBox resolveLayoutBox(boolean updateScrollState) {
        int layoutVersion = document.getLayoutVersion();
        int textMeasureEpoch = textMeasureService.getEpoch();
        if (cachedLayoutVersion == layoutVersion && cachedTextMeasureEpoch == textMeasureEpoch
                && cachedWidth == getWidth() && cachedHeight == getHeight()) {
            if (updateScrollState) {
                updateScrollStateFromCachedLayoutIfNeeded();
            }
            return cachedLayoutBox;
        }

        cachedLayoutBox = layoutDocument(null);
        staticLayoutGeneration++;
        cachedLayoutScrollStateUpdated = false;
        cachedLayoutVersion = layoutVersion;
        cachedPaintVersion = document.getPaintVersion();
        cachedTextMeasureEpoch = textMeasureEpoch;
        cachedWidth = getWidth();
        cachedHeight = getHeight();
        cachedPaintScrollVersion = -1;
        invalidateRuntimeLayoutCache();
        if (updateScrollState) {
            updateScrollStateFromCachedLayoutIfNeeded();
        }
        return cachedLayoutBox;
    }

    private void updateScrollStateFromCachedLayoutIfNeeded() {
        if (cachedLayoutScrollStateUpdated || cachedLayoutBox == null
                || animationTimeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT)) {
            return;
        }
        scrollState.updateFromLayout(cachedLayoutBox);
        cachedLayoutScrollStateUpdated = true;
    }

    private DocumentLayoutBox layoutDocument(DocumentLayoutEngine.LayoutRuntimeValueResolver layoutValueResolver) {
        return viewportRootScrollingEnabled
                ? DocumentLayoutEngine.layoutViewportRoot(document.getRootElement(), getWidth(), getHeight(),
                        textMeasureService, layoutValueResolver)
                : DocumentLayoutEngine.layout(document.getRootElement(), getWidth(), getHeight(), textMeasureService,
                        layoutValueResolver);
    }

    private boolean dispatchClick(ElementNode target, UiMouseEvent event) {
        if (target == null || event == null) {
            return false;
        }
        int documentX = event.getMouseX() - getAbsoluteX();
        int documentY = event.getMouseY() - getAbsoluteY();
        DocumentEventControl eventControl = new DocumentEventControl();

        // 构建祖先路径（target → root）
        List<ElementNode> path = buildAncestorPath(target);

        // 捕获阶段：从根向目标传播（不含目标）
        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int i = path.size() - 1; i > 0; i--) {
            if (eventControl.isPropagationStopped()) break;
            ElementNode currentElement = path.get(i);
            DocumentElementClickHandler captureHandler = currentElement.getCaptureClickHandler();
            if (captureHandler != null) {
                DocumentElementClickEvent clickEvent = new DocumentElementClickEvent(target, currentElement,
                        documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (captureHandler.onClick(clickEvent)) {
                    eventControl.stopPropagation();
                }
            }
        }

        // 目标阶段
        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            // 目标元素的捕获 handler
            DocumentElementClickHandler targetCaptureHandler = target.getCaptureClickHandler();
            if (targetCaptureHandler != null) {
                DocumentElementClickEvent clickEvent = new DocumentElementClickEvent(target, target,
                        documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (targetCaptureHandler.onClick(clickEvent)) {
                    eventControl.stopPropagation();
                }
            }
            // 目标元素的冒泡 handler
            if (!eventControl.isPropagationStopped()) {
                DocumentElementClickHandler targetHandler = target.getClickHandler();
                if (targetHandler != null) {
                    DocumentElementClickEvent clickEvent = new DocumentElementClickEvent(target, target,
                            documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                    if (targetHandler.onClick(clickEvent)) {
                        eventControl.stopPropagation();
                    }
                }
            }
        }

        // 冒泡阶段：从目标父元素向根传播
        eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
        for (int i = 1; i < path.size(); i++) {
            if (eventControl.isPropagationStopped()) break;
            ElementNode currentElement = path.get(i);
            DocumentElementClickHandler clickHandler = currentElement.getClickHandler();
            if (clickHandler == null) {
                continue;
            }
            DocumentElementClickEvent clickEvent = new DocumentElementClickEvent(target, currentElement,
                    documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
            if (clickHandler.onClick(clickEvent)) {
                eventControl.stopPropagation();
            }
        }
        return eventControl.isPropagationStopped();
    }

    private boolean dispatchKey(ElementNode target, UiKeyEvent event) {
        if (target == null || event == null) {
            return false;
        }
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        // 捕获阶段：从根向目标传播（不含目标）
        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int i = path.size() - 1; i > 0; i--) {
            if (eventControl.isPropagationStopped()) break;
            ElementNode currentElement = path.get(i);
            DocumentElementKeyHandler captureHandler = currentElement.getCaptureKeyHandler();
            if (captureHandler != null) {
                DocumentElementKeyEvent keyEvent = new DocumentElementKeyEvent(target, currentElement, event, eventControl);
                if (captureHandler.onKey(keyEvent)) {
                    eventControl.stopPropagation();
                    ElementNode pendingFocus = keyEvent.getPendingFocusTarget();
                    if (pendingFocus != null) {
                        focusElement(pendingFocus, keyEvent.isPendingFocusVisible());
                    }
                }
            }
        }

        // 目标阶段
        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            DocumentElementKeyHandler targetCaptureHandler = target.getCaptureKeyHandler();
            if (targetCaptureHandler != null) {
                DocumentElementKeyEvent keyEvent = new DocumentElementKeyEvent(target, target, event, eventControl);
                if (targetCaptureHandler.onKey(keyEvent)) {
                    eventControl.stopPropagation();
                    ElementNode pendingFocus = keyEvent.getPendingFocusTarget();
                    if (pendingFocus != null) {
                        focusElement(pendingFocus, keyEvent.isPendingFocusVisible());
                    }
                }
            }
            if (!eventControl.isPropagationStopped()) {
                DocumentElementKeyHandler targetHandler = target.getKeyHandler();
                if (targetHandler != null) {
                    DocumentElementKeyEvent keyEvent = new DocumentElementKeyEvent(target, target, event, eventControl);
                    if (targetHandler.onKey(keyEvent)) {
                        eventControl.stopPropagation();
                        ElementNode pendingFocus = keyEvent.getPendingFocusTarget();
                        if (pendingFocus != null) {
                            focusElement(pendingFocus, keyEvent.isPendingFocusVisible());
                        }
                    }
                }
            }
        }

        // 冒泡阶段：从目标父元素向根传播
        eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
        for (int i = 1; i < path.size(); i++) {
            if (eventControl.isPropagationStopped()) break;
            ElementNode currentElement = path.get(i);
            DocumentElementKeyHandler keyHandler = currentElement.getKeyHandler();
            if (keyHandler == null) {
                continue;
            }
            DocumentElementKeyEvent keyEvent = new DocumentElementKeyEvent(target, currentElement, event, eventControl);
            if (keyHandler.onKey(keyEvent)) {
                eventControl.stopPropagation();
                ElementNode pendingFocus = keyEvent.getPendingFocusTarget();
                if (pendingFocus != null) {
                    focusElement(pendingFocus, keyEvent.isPendingFocusVisible());
                }
            }
        }
        return eventControl.isPropagationStopped();
    }

    private boolean dispatchTextInput(ElementNode target, UiTextInputEvent event) {
        if (target != null && target.isDisabled()) {
            return true;
        }
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementTextInputHandler textInputHandler = currentElement.getTextInputHandler();
            if (textInputHandler == null) {
                continue;
            }
            if (textInputHandler.onTextInput(new DocumentElementTextInputEvent(target, currentElement, event))) {
                return true;
            }
        }
        return false;
    }

    /**
     * raw button 默认键盘行为：Enter 直接触发 click，Space pressed 进入 active，Space released 触发 click。
     *
     * <p>仅对没有 key handler 消费事件的原生 button 元素生效；disabled 时不触发。</p>
     */
    private void dispatchNativeButtonDefaultKeyBehavior(ElementNode target, UiKeyEvent event) {
        if (target == null || !"button".equals(target.getTagName())) {
            return;
        }
        if (target.isDisabled()) {
            rawButtonSpacePressed.remove(target.__getElementUid());
            return;
        }
        int keyCode = event.getKeyCode();
        boolean isEnter = keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER;
        boolean isSpace = keyCode == Keyboard.KEY_SPACE;
        if (!isEnter && !isSpace) {
            return;
        }
        long uid = target.__getElementUid();
        if (isEnter && event.getAction() == UiKeyEvent.Action.PRESSED) {
            DocumentElementClickHandler clickHandler = target.getClickHandler();
            if (clickHandler != null) {
                clickHandler.onClick(new DocumentElementClickEvent(target, target, -1, -1, -1, event.getTimeNanos()));
            }
            return;
        }
        if (isSpace && event.getAction() == UiKeyEvent.Action.PRESSED) {
            rawButtonSpacePressed.put(uid, Boolean.TRUE);
            return;
        }
        if (isSpace && event.getAction() == UiKeyEvent.Action.RELEASED) {
            Boolean pressed = rawButtonSpacePressed.remove(uid);
            if (Boolean.TRUE.equals(pressed)) {
                DocumentElementClickHandler clickHandler = target.getClickHandler();
                if (clickHandler != null) {
                    clickHandler.onClick(new DocumentElementClickEvent(target, target, -1, -1, -1, event.getTimeNanos()));
                }
            }
        }
    }

    private void beginDragIfNeeded(ElementNode target, UiMouseEvent event) {
        clearDragState();
        if (target == null || event == null || event.getButton() != 0) {
            return;
        }
        int documentX = event.getMouseX() - getAbsoluteX();
        int documentY = event.getMouseY() - getAbsoluteY();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementDragHandler dragHandler = currentElement.getDragHandler();
            if (dragHandler == null) {
                continue;
            }
            dragStartDocumentX = documentX;
            dragStartDocumentY = documentY;
            lastDragDocumentX = documentX;
            lastDragDocumentY = documentY;
            DocumentElementDragEvent dragEvent = new DocumentElementDragEvent(target, currentElement, documentX,
                    documentY, documentX, documentY, 0, 0, event.getButton(), event.getTimeNanos(),
                    DocumentElementDragEvent.DragPhase.START);
            if (dragHandler.onDrag(dragEvent)) {
                draggingElement = currentElement;
                dragActivated = false;
                return;
            }
        }
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            if (!"true".equals(currentElement.getAttribute("draggable"))) {
                continue;
            }
            dragStartDocumentX = documentX;
            dragStartDocumentY = documentY;
            lastDragDocumentX = documentX;
            lastDragDocumentY = documentY;
            draggingElement = currentElement;
            htmlDragSourceElement = currentElement;
            dragActivated = false;
            htmlDragStarted = false;
            return;
        }
    }

    private boolean dispatchDragMove(UiMouseEvent event) {
        if (draggingElement == null || event == null) {
            return false;
        }
        int documentX = event.getMouseX() - getAbsoluteX();
        int documentY = event.getMouseY() - getAbsoluteY();
        if (!dragActivated) {
            int distanceX = documentX - dragStartDocumentX;
            int distanceY = documentY - dragStartDocumentY;
            if (distanceX * distanceX + distanceY * distanceY < DRAG_ACTIVATION_THRESHOLD_SQUARED) {
                return false;
            }
            dragActivated = true;
        }
        int deltaDocumentX = documentX - lastDragDocumentX;
        int deltaDocumentY = documentY - lastDragDocumentY;
        lastDragDocumentX = documentX;
        lastDragDocumentY = documentY;
        if (htmlDragSourceElement != null) {
            if (deltaDocumentX == 0 && deltaDocumentY == 0) {
                deltaDocumentX = documentX - dragStartDocumentX;
                deltaDocumentY = documentY - dragStartDocumentY;
            }
            dispatchHtmlDragStartIfNeeded(event, documentX, documentY, deltaDocumentX, deltaDocumentY);
            dispatchHtmlDragOver(event, documentX, documentY, deltaDocumentX, deltaDocumentY);
            return true;
        }
        return dispatchDragEvent(draggingElement, pressedElement, event, documentX, documentY, deltaDocumentX,
                deltaDocumentY, DocumentElementDragEvent.DragPhase.DRAG);
    }

    private boolean dispatchDragEnd(UiMouseEvent event) {
        if (draggingElement == null || event == null) {
            clearDragState();
            return false;
        }
        int documentX = event.getMouseX() - getAbsoluteX();
        int documentY = event.getMouseY() - getAbsoluteY();
        int deltaDocumentX = documentX - lastDragDocumentX;
        int deltaDocumentY = documentY - lastDragDocumentY;
        ElementNode dragTarget = pressedElement;
        ElementNode dragHandlerTarget = draggingElement;
        boolean activated = dragActivated;
        ElementNode htmlDragSource = htmlDragSourceElement;
        clearDragState();
        if (htmlDragSource != null) {
            dispatchHtmlDragEnd(htmlDragSource, event, documentX, documentY, deltaDocumentX, deltaDocumentY);
            return activated;
        }
        boolean handled = dispatchDragEvent(dragHandlerTarget, dragTarget, event, documentX, documentY,
                deltaDocumentX, deltaDocumentY, DocumentElementDragEvent.DragPhase.END);
        return activated && handled;
    }

    private void clearDragState() {
        draggingElement = null;
        htmlDragSourceElement = null;
        dragActivated = false;
        htmlDragStarted = false;
    }

    private void dispatchHtmlDragStartIfNeeded(UiMouseEvent event, int documentX, int documentY, int deltaDocumentX,
            int deltaDocumentY) {
        if (htmlDragStarted) {
            return;
        }
        htmlDragStarted = true;
        DocumentElementDragStartHandler dragStartHandler = htmlDragSourceElement.getDragStartHandler();
        if (dragStartHandler == null) {
            return;
        }
        dragStartHandler.onDragStart(new DocumentElementDragEvent(htmlDragSourceElement, htmlDragSourceElement,
                dragStartDocumentX, dragStartDocumentY, documentX, documentY, deltaDocumentX, deltaDocumentY,
                event.getButton(), event.getTimeNanos(), DocumentElementDragEvent.DragPhase.START));
    }

    private boolean dispatchHtmlDragOver(UiMouseEvent event, int documentX, int documentY, int deltaDocumentX,
            int deltaDocumentY) {
        ElementNode target = findElementAt(event.getMouseX(), event.getMouseY());
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementDragOverHandler dragOverHandler = currentElement.getDragOverHandler();
            if (dragOverHandler == null) {
                continue;
            }
            if (dragOverHandler.onDragOver(new DocumentElementDragEvent(htmlDragSourceElement, currentElement,
                    dragStartDocumentX, dragStartDocumentY, documentX, documentY, deltaDocumentX, deltaDocumentY,
                    event.getButton(), event.getTimeNanos(), DocumentElementDragEvent.DragPhase.DRAG))) {
                return true;
            }
        }
        return false;
    }

    private void dispatchHtmlDragEnd(ElementNode htmlDragSource, UiMouseEvent event, int documentX, int documentY,
            int deltaDocumentX, int deltaDocumentY) {
        DocumentElementDragEndHandler dragEndHandler = htmlDragSource.getDragEndHandler();
        if (dragEndHandler == null) {
            return;
        }
        dragEndHandler.onDragEnd(new DocumentElementDragEvent(htmlDragSource, htmlDragSource, dragStartDocumentX,
                dragStartDocumentY, documentX, documentY, deltaDocumentX, deltaDocumentY, event.getButton(),
                event.getTimeNanos(), DocumentElementDragEvent.DragPhase.END));
    }

    private boolean dispatchDragEvent(ElementNode dragHandlerTarget, ElementNode dragTarget, UiMouseEvent event,
            int documentX, int documentY, int deltaDocumentX, int deltaDocumentY,
            DocumentElementDragEvent.DragPhase phase) {
        if (dragHandlerTarget == null || event == null) {
            return false;
        }
        DocumentElementDragHandler dragHandler = dragHandlerTarget.getDragHandler();
        if (dragHandler == null) {
            return false;
        }
        ElementNode resolvedTarget = dragTarget != null ? dragTarget : dragHandlerTarget;
        return dragHandler.onDrag(new DocumentElementDragEvent(resolvedTarget, dragHandlerTarget,
                dragStartDocumentX, dragStartDocumentY, documentX, documentY, deltaDocumentX, deltaDocumentY,
                event.getButton(), event.getTimeNanos(), phase));
    }

    private boolean focusFirstElementInTraversalOrder(boolean reverse) {
        List<ElementNode> focusableElements = collectFocusableElements();
        if (focusableElements.isEmpty()) {
            focusElement(null, false);
            return false;
        }
        focusElement(reverse ? focusableElements.get(focusableElements.size() - 1) : focusableElements.get(0), true);
        return true;
    }

    private List<ElementNode> collectFocusableElements() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return Collections.emptyList();
        }
        List<ElementNode> focusableElements = new ArrayList<ElementNode>();
        collectFocusableElements(resolveInteractiveLayoutBox(), focusableElements);
        sortFocusableElementsByTabIndex(focusableElements);
        return focusableElements;
    }

    private DocumentLayoutBox resolveInteractiveLayoutBox() {
        DocumentLayoutBox rootBox = resolvePaintLayoutBox(false);
        long currentTimeNanos = animationClock.getCurrentTimeNanos();
        animationTimeline.updateFromLayout(rootBox, currentTimeNanos);
        animationTimeline.pruneFinishedAnimations(currentTimeNanos);
        if (animationTimeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT)) {
            return resolveRuntimeLayoutBox(currentTimeNanos,
                    animationTimeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        }
        invalidateRuntimeLayoutCache();
        updateScrollStateFromCachedLayoutIfNeeded();
        return rootBox;
    }

    private boolean isRuntimeLayoutCacheReusable() {
        return cachedRuntimeLayoutBox != null
                && cachedRuntimeLayoutVersion == cachedLayoutVersion
                && cachedRuntimePaintVersion == cachedPaintVersion
                && cachedRuntimeTextMeasureEpoch == cachedTextMeasureEpoch
                && cachedRuntimeWidth == getWidth()
                && cachedRuntimeHeight == getHeight()
                && cachedRuntimeViewportRootScrollingEnabled == viewportRootScrollingEnabled;
    }

    private void cacheRuntimeLayoutBox(DocumentLayoutBox rootBox) {
        cachedRuntimeLayoutBox = rootBox;
        cachedRuntimeLayoutVersion = cachedLayoutVersion;
        cachedRuntimePaintVersion = cachedPaintVersion;
        cachedRuntimeTextMeasureEpoch = cachedTextMeasureEpoch;
        cachedRuntimeWidth = getWidth();
        cachedRuntimeHeight = getHeight();
        cachedRuntimeViewportRootScrollingEnabled = viewportRootScrollingEnabled;
    }

    private void invalidateRuntimeLayoutCache() {
        cachedRuntimeLayoutBox = null;
        cachedRuntimeLayoutVersion = -1;
        cachedRuntimePaintVersion = -1;
        cachedRuntimeTextMeasureEpoch = -1;
        cachedRuntimeWidth = -1;
        cachedRuntimeHeight = -1;
        cachedRuntimeViewportRootScrollingEnabled = viewportRootScrollingEnabled;
    }

    private void collectFocusableElements(DocumentLayoutBox box, List<ElementNode> focusableElements) {
        ElementNode element = box.getElement();
        if (isSequentiallyFocusable(element) && box.getWidth() > 0 && box.getHeight() > 0) {
            focusableElements.add(element);
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            collectFocusableElements(child, focusableElements);
        }
    }

    private ElementNode getActiveFocusedElement() {
        if (focusedElement != null && (!focusedElement.isFocusable() || !isElementAttachedToDocument(focusedElement))) {
            focusElement(null, false);
        }
        if (focusedElement != null
                && focusedElement.getFocusInvalidationVersion() != focusedElementInvalidationVersion) {
            focusElement(null, false);
        }
        return focusedElement;
    }

    private void focusElement(ElementNode nextFocusedElement, boolean focusVisible) {
        ElementNode resolvedElement = nextFocusedElement != null && nextFocusedElement.isFocusable()
                && isElementAttachedToDocument(nextFocusedElement) ? nextFocusedElement : null;
        boolean resolvedFocusVisible = resolvedElement != null && focusVisible;
        if (focusedElement == resolvedElement && focusedElementFocusVisible == resolvedFocusVisible) {
            return;
        }

        ElementNode previousElement = focusedElement;
        boolean previousFocusVisible = focusedElementFocusVisible;
        focusedElement = resolvedElement;
        focusedElementFocusVisible = resolvedFocusVisible;
        focusedElementInvalidationVersion = focusedElement == null ? 0 : focusedElement.getFocusInvalidationVersion();
        // 失焦时清理 raw button 的 Space 按下状态，避免失焦后 released 仍触发
        if (previousElement != null && previousElement != focusedElement) {
            rawButtonSpacePressed.remove(previousElement.__getElementUid());
        }
        if (previousElement != focusedElement) {
            dispatchFocusChanged(previousElement, false, false);
        }
        if (focusedElement != null && (previousElement != focusedElement || previousFocusVisible != focusedElementFocusVisible)) {
            dispatchFocusChanged(focusedElement, true, focusedElementFocusVisible);
        }
        if (focusedElement != null && focusedElementFocusVisible && focusedElement.isFocusable()
                && isElementAttachedToDocument(focusedElement)) {
            ensureFocusedElementVisible();
        }
        syncCursorFromHoveredElement();
    }

    private void ensureFocusedElementVisible() {
        if (focusedElement == null || !focusedElementFocusVisible || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        scrollElementIntoView(focusedElement);
    }

    private boolean scrollElementIntoView(ElementNode target) {
        if (target == null || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        for (int remainingPasses = 16; remainingPasses > 0; remainingPasses--) {
            List<LayoutPathEntry> path = resolveElementLayoutPath(target);
            if (!isVisibleLayoutPath(path)) {
                return false;
            }
            int firstFixedIndex = findFirstFixedIndex(path);
            boolean changed = false;
            for (int index = path.size() - 2; index >= 0; index--) {
                if (firstFixedIndex >= 0 && index < firstFixedIndex) {
                    break;
                }
                if (scrollAncestorToRevealTarget(path.get(index), path.get(path.size() - 1))) {
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                return true;
            }
        }
        return true;
    }

    private List<LayoutPathEntry> resolveElementLayoutPath(ElementNode target) {
        if (target == null || !isElementAttachedToDocument(target)) {
            return Collections.emptyList();
        }
        List<LayoutPathEntry> path = new ArrayList<LayoutPathEntry>();
        if (!collectLayoutPath(resolveInteractiveLayoutBox(), target, 0, 0, path)) {
            return Collections.emptyList();
        }
        return path;
    }

    private boolean isVisibleLayoutTarget(ElementNode target) {
        return isVisibleLayoutPath(resolveElementLayoutPath(target));
    }

    private boolean isVisibleLayoutPath(List<LayoutPathEntry> path) {
        if (path.isEmpty()) {
            return false;
        }
        for (LayoutPathEntry entry : path) {
            if (entry.box.getComputedStyle().getVisibility() == UiVisibility.HIDDEN) {
                return false;
            }
        }
        DocumentLayoutBox targetBox = path.get(path.size() - 1).box;
        return targetBox.getWidth() > 0 && targetBox.getHeight() > 0;
    }

    private boolean collectLayoutPath(DocumentLayoutBox box, ElementNode target, int offsetX, int offsetY,
            List<LayoutPathEntry> path) {
        int baseOffsetX = box.isFixedPositioned() ? 0 : offsetX;
        int baseOffsetY = box.isFixedPositioned() ? 0 : offsetY;
        int boxOffsetX = baseOffsetX + box.getPositionOffsetX();
        int boxOffsetY = baseOffsetY + box.getPositionOffsetY();
        path.add(new LayoutPathEntry(box, boxOffsetX, boxOffsetY));
        if (box.getElement() == target) {
            return true;
        }
        int childOffsetX = boxOffsetX - scrollState.getScrollLeft(box.getElement());
        int childOffsetY = boxOffsetY - scrollState.getScrollTop(box.getElement());
        for (DocumentLayoutBox child : box.getChildren()) {
            if (collectLayoutPath(child, target, childOffsetX, childOffsetY, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    private int findFirstFixedIndex(List<LayoutPathEntry> path) {
        for (int index = 1; index < path.size(); index++) {
            if (path.get(index).box.isFixedPositioned()) {
                return index;
            }
        }
        return -1;
    }

    private boolean scrollAncestorToRevealTarget(LayoutPathEntry ancestorEntry, LayoutPathEntry targetEntry) {
        ElementNode ancestorElement = ancestorEntry.box.getElement();
        int currentScrollLeft = scrollState.getScrollLeft(ancestorElement);
        int currentScrollTop = scrollState.getScrollTop(ancestorElement);
        int nextScrollLeft = resolveScrollOffsetForTarget(currentScrollLeft,
                ancestorEntry.box.getContentLeft() + ancestorEntry.boxOffsetX,
                ancestorEntry.box.getContentLeft() + ancestorEntry.boxOffsetX + ancestorEntry.box.getContentWidth(),
                targetEntry.box.getLeft() + targetEntry.boxOffsetX,
                targetEntry.box.getRight() + targetEntry.boxOffsetX,
                scrollState.getMaxScrollLeft(ancestorElement));
        int nextScrollTop = resolveScrollOffsetForTarget(currentScrollTop,
                ancestorEntry.box.getContentTop() + ancestorEntry.boxOffsetY,
                ancestorEntry.box.getContentTop() + ancestorEntry.boxOffsetY + ancestorEntry.box.getContentHeight(),
                targetEntry.box.getTop() + targetEntry.boxOffsetY,
                targetEntry.box.getBottom() + targetEntry.boxOffsetY,
                scrollState.getMaxScrollTop(ancestorElement));
        if (currentScrollLeft == nextScrollLeft && currentScrollTop == nextScrollTop) {
            return false;
        }
        if (!scrollState.setScrollOffset(ancestorElement, nextScrollLeft, nextScrollTop)) {
            return false;
        }
        dispatchScroll(ancestorElement, System.nanoTime());
        return true;
    }

    private int resolveScrollOffsetForTarget(int currentOffset, int viewportStart, int viewportEnd, int targetStart,
            int targetEnd, int maxOffset) {
        if (maxOffset <= 0 || (targetStart >= viewportStart && targetEnd <= viewportEnd)) {
            return currentOffset;
        }
        int nextOffset = currentOffset;
        if (targetStart < viewportStart) {
            nextOffset -= viewportStart - targetStart;
        } else if (targetEnd > viewportEnd) {
            nextOffset += targetEnd - viewportEnd;
        }
        return Math.max(0, Math.min(nextOffset, maxOffset));
    }

    private void dispatchFocusChanged(ElementNode target, boolean focused, boolean focusVisible) {
        if (target == null) {
            return;
        }
        DocumentElementFocusHandler focusHandler = target.getFocusHandler();
        if (focusHandler != null) {
            focusHandler.onFocusChanged(new DocumentElementFocusEvent(target, focused, focusVisible));
        }
        // 冒泡分发 focusin/focusout（#25）
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementFocusInHandler focusInHandler = currentElement.getFocusInHandler();
            if (focusInHandler == null) {
                continue;
            }
            DocumentElementFocusInEvent focusInEvent = new DocumentElementFocusInEvent(target, currentElement,
                    focused, focusVisible);
            if (focusInHandler.onFocusIn(focusInEvent)) {
                break;
            }
        }
    }

    private boolean dispatchActive(ElementNode target, boolean active, UiMouseEvent event) {
        if (target == null || event == null) {
            return false;
        }
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementActiveHandler activeHandler = currentElement.getActiveHandler();
            if (activeHandler == null) {
                continue;
            }
            DocumentElementActiveEvent activeEvent = new DocumentElementActiveEvent(target, currentElement, active,
                    event.getButton(), event.getTimeNanos());
            if (activeHandler.onActiveChanged(activeEvent)) {
                return true;
            }
        }
        return false;
    }

    private void dispatchScroll(ElementNode target, long timeNanos) {
        if (target == null) {
            return;
        }
        DocumentElementScrollHandler scrollHandler = target.getScrollHandler();
        if (scrollHandler == null) {
            return;
        }
        scrollHandler.onScroll(new DocumentElementScrollEvent(target, scrollState.getScrollTop(target),
                scrollState.getScrollLeft(target), scrollState.getScrollHeight(target),
                scrollState.getScrollWidth(target), timeNanos));
    }

    private boolean dispatchMouseDown(ElementNode target, UiMouseEvent event) {
        if (target == null || event == null) {
            return false;
        }
        int documentX = event.getMouseX() - getAbsoluteX();
        int documentY = event.getMouseY() - getAbsoluteY();
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        // 捕获阶段
        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int i = path.size() - 1; i > 0; i--) {
            if (eventControl.isPropagationStopped()) break;
            ElementNode currentElement = path.get(i);
            DocumentElementMouseDownHandler captureHandler = currentElement.getCaptureMouseDownHandler();
            if (captureHandler != null) {
                DocumentElementMouseDownEvent mouseDownEvent = new DocumentElementMouseDownEvent(target, currentElement,
                        documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (captureHandler.onMouseDown(mouseDownEvent)) {
                    eventControl.stopPropagation();
                }
            }
        }

        // 目标阶段
        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            DocumentElementMouseDownHandler targetCaptureHandler = target.getCaptureMouseDownHandler();
            if (targetCaptureHandler != null) {
                DocumentElementMouseDownEvent mouseDownEvent = new DocumentElementMouseDownEvent(target, target,
                        documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (targetCaptureHandler.onMouseDown(mouseDownEvent)) {
                    eventControl.stopPropagation();
                }
            }
            if (!eventControl.isPropagationStopped()) {
                DocumentElementMouseDownHandler targetHandler = target.getMouseDownHandler();
                if (targetHandler != null) {
                    DocumentElementMouseDownEvent mouseDownEvent = new DocumentElementMouseDownEvent(target, target,
                            documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                    if (targetHandler.onMouseDown(mouseDownEvent)) {
                        eventControl.stopPropagation();
                    }
                }
            }
        }

        // 冒泡阶段
        eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
        for (int i = 1; i < path.size(); i++) {
            if (eventControl.isPropagationStopped()) break;
            ElementNode currentElement = path.get(i);
            DocumentElementMouseDownHandler handler = currentElement.getMouseDownHandler();
            if (handler == null) {
                continue;
            }
            DocumentElementMouseDownEvent mouseDownEvent = new DocumentElementMouseDownEvent(target, currentElement,
                    documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
            if (handler.onMouseDown(mouseDownEvent)) {
                eventControl.stopPropagation();
            }
        }
        return eventControl.isPropagationStopped();
    }

    private boolean dispatchMouseUp(ElementNode target, UiMouseEvent event) {
        if (target == null || event == null) {
            return false;
        }
        int documentX = event.getMouseX() - getAbsoluteX();
        int documentY = event.getMouseY() - getAbsoluteY();
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        // 捕获阶段
        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int i = path.size() - 1; i > 0; i--) {
            if (eventControl.isPropagationStopped()) break;
            ElementNode currentElement = path.get(i);
            DocumentElementMouseUpHandler captureHandler = currentElement.getCaptureMouseUpHandler();
            if (captureHandler != null) {
                DocumentElementMouseUpEvent mouseUpEvent = new DocumentElementMouseUpEvent(target, currentElement,
                        documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (captureHandler.onMouseUp(mouseUpEvent)) {
                    eventControl.stopPropagation();
                }
            }
        }

        // 目标阶段
        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            DocumentElementMouseUpHandler targetCaptureHandler = target.getCaptureMouseUpHandler();
            if (targetCaptureHandler != null) {
                DocumentElementMouseUpEvent mouseUpEvent = new DocumentElementMouseUpEvent(target, target,
                        documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (targetCaptureHandler.onMouseUp(mouseUpEvent)) {
                    eventControl.stopPropagation();
                }
            }
            if (!eventControl.isPropagationStopped()) {
                DocumentElementMouseUpHandler targetHandler = target.getMouseUpHandler();
                if (targetHandler != null) {
                    DocumentElementMouseUpEvent mouseUpEvent = new DocumentElementMouseUpEvent(target, target,
                            documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                    if (targetHandler.onMouseUp(mouseUpEvent)) {
                        eventControl.stopPropagation();
                    }
                }
            }
        }

        // 冒泡阶段
        eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
        for (int i = 1; i < path.size(); i++) {
            if (eventControl.isPropagationStopped()) break;
            ElementNode currentElement = path.get(i);
            DocumentElementMouseUpHandler handler = currentElement.getMouseUpHandler();
            if (handler == null) {
                continue;
            }
            DocumentElementMouseUpEvent mouseUpEvent = new DocumentElementMouseUpEvent(target, currentElement,
                    documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
            if (handler.onMouseUp(mouseUpEvent)) {
                eventControl.stopPropagation();
            }
        }
        return eventControl.isPropagationStopped();
    }

    private void updateHoveredElement(ElementNode nextHoveredElement, UiMouseEvent event) {
        ElementNode resolvedElement = nextHoveredElement != null && isElementAttachedToDocument(nextHoveredElement)
                ? nextHoveredElement : null;
        if (hoveredElement == resolvedElement) {
            syncCursorFromHoveredElement();
            return;
        }
        ElementNode previousElement = hoveredElement;
        hoveredElement = resolvedElement;
        // 修复 #24：从子元素移到父元素时，父元素不触发 leave（仍在父元素内）
        // 从父元素移到子元素时，父元素不触发 enter（已经在父元素内）
        // 只对不在公共祖先路径上的节点触发 leave/enter
        dispatchHoverChangedWithAncestorAwareness(previousElement, false, resolvedElement, event);
        dispatchHoverChangedWithAncestorAwareness(resolvedElement, true, previousElement, event);
        syncCursorFromHoveredElement();
    }

    private void syncCursorFromHoveredElement() {
        cursorHost.applyCursor(resolveCursorFromHoveredElement());
    }

    private UiCursor resolveCursorFromHoveredElement() {
        if (hoveredElement == null || !isElementAttachedToDocument(hoveredElement)) {
            return UiCursor.DEFAULT;
        }
        for (DocumentNode current = hoveredElement; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            UiCursor declaredCursor = resolveDeclaredCursor(currentElement, buildCursorPseudoStates(currentElement));
            if (declaredCursor != null) {
                return declaredCursor;
            }
        }
        return UiCursor.DEFAULT;
    }

    private UiCursor resolveDeclaredCursor(ElementNode element, java.util.Set<UiPseudoClass> activeStates) {
        if (element == null) {
            return null;
        }
        if (element.style().getCursor() != null) {
            return element.style().getCursor();
        }
        List<club.heiqi.uilib.ui.style.UiStyleRule> matchingRules = element.getOwnerDocument()
                .findMatchingRules(element, activeStates);
        for (int index = matchingRules.size() - 1; index >= 0; index--) {
            UiCursor cursor = matchingRules.get(index).getDeclaration().getCursor();
            if (cursor != null) {
                return cursor;
            }
        }
        return null;
    }

    private java.util.Set<UiPseudoClass> buildCursorPseudoStates(ElementNode element) {
        EnumSet<UiPseudoClass> activeStates = EnumSet.noneOf(UiPseudoClass.class);
        if (element == null) {
            return activeStates;
        }
        if (hoveredElement != null && isAncestorOrSelf(element, hoveredElement)) {
            activeStates.add(UiPseudoClass.HOVER);
        }
        if (element == getActiveFocusedElement()) {
            activeStates.add(UiPseudoClass.FOCUS);
            if (focusedElementFocusVisible) {
                activeStates.add(UiPseudoClass.FOCUS_VISIBLE);
            }
        }
        if (element == pressedElement) {
            activeStates.add(UiPseudoClass.ACTIVE);
        }
        if (element.isDisabled()) {
            activeStates.add(UiPseudoClass.DISABLED);
        }
        return activeStates;
    }

    /**
     * 分发 hover 变化事件，跳过与 otherElement 共享的祖先节点（避免父子切换时多余触发）。
     */
    private boolean dispatchHoverChangedWithAncestorAwareness(ElementNode target, boolean hovered,
            ElementNode otherElement, UiMouseEvent event) {
        if (target == null) {
            return false;
        }
        int documentX = event == null ? -1 : event.getMouseX() - getAbsoluteX();
        int documentY = event == null ? -1 : event.getMouseY() - getAbsoluteY();
        long timeNanos = event == null ? 0L : event.getTimeNanos();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            // 如果 currentElement 是 otherElement 的祖先（或就是 otherElement），跳过
            // 这样从子移到父时，父不会收到 leave；从父移到子时，父不会收到 enter
            if (isAncestorOrSelf(currentElement, otherElement)) {
                continue;
            }
            DocumentElementHoverHandler hoverHandler = currentElement.getHoverHandler();
            if (hoverHandler == null) {
                continue;
            }
            DocumentElementHoverEvent hoverEvent = new DocumentElementHoverEvent(target, currentElement, hovered,
                    documentX, documentY, timeNanos);
            if (hoverHandler.onHoverChanged(hoverEvent)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 ancestor 是否是 descendant 的祖先节点（或就是 descendant 本身）。
     */
    private static boolean isAncestorOrSelf(ElementNode ancestor, ElementNode descendant) {
        if (ancestor == null || descendant == null) {
            return false;
        }
        for (DocumentNode current = descendant; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private boolean dispatchHoverChanged(ElementNode target, boolean hovered, UiMouseEvent event) {
        if (target == null) {
            return false;
        }
        int documentX = event == null ? -1 : event.getMouseX() - getAbsoluteX();
        int documentY = event == null ? -1 : event.getMouseY() - getAbsoluteY();
        long timeNanos = event == null ? 0L : event.getTimeNanos();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementHoverHandler hoverHandler = currentElement.getHoverHandler();
            if (hoverHandler == null) {
                continue;
            }
            DocumentElementHoverEvent hoverEvent = new DocumentElementHoverEvent(target, currentElement, hovered,
                    documentX, documentY, timeNanos);
            if (hoverHandler.onHoverChanged(hoverEvent)) {
                return true;
            }
        }
        return false;
    }

    private ElementNode resolveFocusableElement(ElementNode target) {
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            if (currentElement.isFocusable() && !currentElement.isDisabled()) {
                return currentElement;
            }
        }
        return null;
    }

    private boolean isProgrammaticFocusTarget(ElementNode element) {
        return element != null && element.isFocusable() && !element.isDisabled() && isVisibleLayoutTarget(element);
    }

    private boolean hasFocusableElement(DocumentNode node) {
        if (node instanceof ElementNode && ((ElementNode) node).isFocusable()) {
            return true;
        }
        for (DocumentNode child : node.getChildren()) {
            if (hasFocusableElement(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSequentiallyFocusable(ElementNode element) {
        if (element == null || !element.isFocusable()) {
            return false;
        }
        if (element.isDisabled()) {
            return false;
        }
        Integer tabIndex = element.getTabIndex();
        return tabIndex == null || tabIndex.intValue() >= 0;
    }

    private static void sortFocusableElementsByTabIndex(List<ElementNode> focusableElements) {
        Collections.sort(focusableElements, new java.util.Comparator<ElementNode>() {
            @Override
            public int compare(ElementNode first, ElementNode second) {
                int firstIndex = positiveTabIndexOrZero(first);
                int secondIndex = positiveTabIndexOrZero(second);
                if (firstIndex == secondIndex) {
                    return 0;
                }
                if (firstIndex == 0) {
                    return 1;
                }
                if (secondIndex == 0) {
                    return -1;
                }
                return Integer.compare(firstIndex, secondIndex);
            }
        });
    }

    private static int positiveTabIndexOrZero(ElementNode element) {
        Integer tabIndex = element.getTabIndex();
        if (tabIndex == null || tabIndex.intValue() <= 0) {
            return 0;
        }
        return tabIndex.intValue();
    }

    private boolean isElementAttachedToDocument(ElementNode element) {
        if (element == null || element.getOwnerDocument() != document) {
            return false;
        }
        for (DocumentNode current = element; current != null; current = current.getParent()) {
            if (current == document.getRootElement()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建从目标元素到根元素的祖先路径。
     *
     * <p>返回列表中 index 0 为 target，最后一个为最顶层祖先元素。</p>
     *
     * @param target 目标元素
     * @return 祖先路径列表
     */
    private static List<ElementNode> buildAncestorPath(ElementNode target) {
        List<ElementNode> path = new ArrayList<ElementNode>();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            path.add((ElementNode) current);
        }
        return path;
    }

    private static final class LayoutPathEntry {

        private final DocumentLayoutBox box;
        private final int boxOffsetX;
        private final int boxOffsetY;

        private LayoutPathEntry(DocumentLayoutBox box, int boxOffsetX, int boxOffsetY) {
            this.box = box;
            this.boxOffsetX = boxOffsetX;
            this.boxOffsetY = boxOffsetY;
        }
    }

    /**
     * HTML-like 文档组件缓存与布局重建的只读诊断快照。
     */
    public static final class PerformanceDiagnosticsSnapshot {

        private static final PerformanceDiagnosticsSnapshot EMPTY = new PerformanceDiagnosticsSnapshot(0, 0, 0, 0);

        private final int paintCacheGeneration;
        private final int staticLayoutGeneration;
        private final int runtimeLayoutGeneration;
        private final int textMeasureEpoch;

        private PerformanceDiagnosticsSnapshot(int paintCacheGeneration, int staticLayoutGeneration,
                int runtimeLayoutGeneration, int textMeasureEpoch) {
            this.paintCacheGeneration = paintCacheGeneration;
            this.staticLayoutGeneration = staticLayoutGeneration;
            this.runtimeLayoutGeneration = runtimeLayoutGeneration;
            this.textMeasureEpoch = textMeasureEpoch;
        }

        /**
         * 返回空诊断快照。
         *
         * @return 空诊断快照
         */
        public static PerformanceDiagnosticsSnapshot empty() {
            return EMPTY;
        }

        /**
         * 返回 paint command 缓存重建代数。
         *
         * @return paint command 缓存重建代数
         */
        public int getPaintCacheGeneration() {
            return paintCacheGeneration;
        }

        /**
         * 返回静态布局重建代数。
         *
         * @return 静态布局重建代数
         */
        public int getStaticLayoutGeneration() {
            return staticLayoutGeneration;
        }

        /**
         * 返回 layout 动画运行态布局重建代数。
         *
         * @return layout 动画运行态布局重建代数
         */
        public int getRuntimeLayoutGeneration() {
            return runtimeLayoutGeneration;
        }

        /**
         * 返回当前文本测量服务 epoch。
         *
         * @return 文本测量服务 epoch
         */
        public int getTextMeasureEpoch() {
            return textMeasureEpoch;
        }
    }
}
