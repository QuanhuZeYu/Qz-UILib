package club.heiqi.uilib.ui.document;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationClock;
import club.heiqi.uilib.ui.animation.DocumentAnimationImpact;
import club.heiqi.uilib.ui.animation.DocumentAnimation;
import club.heiqi.uilib.ui.animation.DocumentAnimationOptions;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.animation.SystemDocumentAnimationClock;
import club.heiqi.uilib.ui.dom.DocumentElementScrollEvent;
import club.heiqi.uilib.ui.dom.DocumentElementScrollHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.host.DocumentCursorHost;
import club.heiqi.uilib.ui.layout.DocumentHitTestEngine;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.paint.DocumentPaintRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 将 HTML-like 文档模型挂接到现有 retained widget 渲染后端的适配器。
 */
public final class HtmlLikeDocumentWidget extends Widget implements UiDocument.DocumentInteractionRuntime {

    private static final String HIT_TEST_PASSTHROUGH_ATTRIBUTE = "data-hit-test-passthrough";

    private final UiDocument document;
    private final TextMeasureService textMeasureService;
    private final DocumentScrollState scrollState = new DocumentScrollState();
    private final DocumentAnimationTimeline animationTimeline = new DocumentAnimationTimeline();
    private final DocumentClickEventDispatcher clickEventDispatcher;
    private final DocumentKeyboardEventDispatcher keyboardEventDispatcher;
    private final DocumentFocusManager focusManager;
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
    private ElementNode hoveredElement;
    private final DocumentDragController dragController = new DocumentDragController(new DocumentDragController.Host() {
        @Override
        public int getAbsoluteX() {
            return HtmlLikeDocumentWidget.this.getAbsoluteX();
        }

        @Override
        public int getAbsoluteY() {
            return HtmlLikeDocumentWidget.this.getAbsoluteY();
        }

        @Override
        public ElementNode getPressedElement() {
            return HtmlLikeDocumentWidget.this.pressedElement;
        }

        @Override
        public ElementNode findElementAt(int screenX, int screenY) {
            return HtmlLikeDocumentWidget.this.findElementAt(screenX, screenY);
        }
    });
    private int pressedButton = -1;
    private int scrollEventCount;
    private int lastScrollWheelDelta;
    private boolean lastScrollConsumed;
    private long lastScrollEventTimeNanos;
    private boolean viewportRootScrollingEnabled;
    private boolean cachedLayoutScrollStateUpdated;
    private List<DocumentPaintCommand> cachedPaintCommands = Collections.emptyList();

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
        this.clickEventDispatcher = new DocumentClickEventDispatcher(this.document);
        this.focusManager = new DocumentFocusManager(this.document, scrollState, new DocumentFocusManager.Host() {
            @Override
            public int getWidth() {
                return HtmlLikeDocumentWidget.this.getWidth();
            }

            @Override
            public int getHeight() {
                return HtmlLikeDocumentWidget.this.getHeight();
            }

            @Override
            public DocumentLayoutBox resolveInteractiveLayoutBox() {
                return HtmlLikeDocumentWidget.this.resolveInteractiveLayoutBox();
            }

            @Override
            public boolean isElementAttachedToDocument(ElementNode element) {
                return HtmlLikeDocumentWidget.this.isElementAttachedToDocument(element);
            }

            @Override
            public void dispatchScroll(ElementNode target, long timeNanos) {
                HtmlLikeDocumentWidget.this.dispatchScroll(target, timeNanos);
            }

            @Override
            public void clearNativeButtonState(ElementNode element) {
                HtmlLikeDocumentWidget.this.clearNativeButtonState(element);
            }

            @Override
            public void syncCursorFromHoveredElement() {
                HtmlLikeDocumentWidget.this.syncCursorFromHoveredElement();
            }
        });
        this.keyboardEventDispatcher = new DocumentKeyboardEventDispatcher(new DocumentKeyboardEventDispatcher.Host() {
            @Override
            public void focusElement(ElementNode element, boolean focusVisible) {
                HtmlLikeDocumentWidget.this.focusElementFromKeyboard(element, focusVisible);
            }
        });
        this.animationTimeline.setRuntimeChangeCallback(new Runnable() {
            @Override
            public void run() {
                HtmlLikeDocumentWidget.this.invalidateAnimationRuntimeCaches();
            }
        });
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
     * 返回指定元素当前横向滚动偏移。
     *
     * @param element HTML-like 元素
     * @return 横向滚动偏移
     */
    public int getScrollLeft(ElementNode element) {
        resolveInteractiveLayoutBox();
        return scrollState.getScrollLeft(element);
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
     * 返回指定元素最大横向滚动偏移。
     *
     * @param element HTML-like 元素
     * @return 最大横向滚动偏移
     */
    public int getMaxScrollLeft(ElementNode element) {
        resolveInteractiveLayoutBox();
        return scrollState.getMaxScrollLeft(element);
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
        long currentTimeNanos = animationClock.getCurrentTimeNanos();
        return DocumentHitTestEngine.hitTest(resolveInteractiveLayoutBox(), scrollState, screenX - getAbsoluteX(),
                screenY - getAbsoluteY(), currentTimeNanos, animationTimeline);
    }

    /**
     * 返回当前获得 HTML-like 焦点的元素。
     *
     * @return 聚焦元素；没有元素聚焦时返回 null
     */
    public ElementNode getFocusedElement() {
        return focusManager.getFocusedElement();
    }

    @Override
    public boolean requestFocus(ElementNode element) {
        return focusManager.requestFocus(element);
    }

    @Override
    public boolean requestBlur(ElementNode element) {
        return focusManager.requestBlur(element);
    }

    @Override
    public boolean requestScrollTo(ElementNode element, int scrollLeft, int scrollTop) {
        if (!focusManager.isVisibleLayoutTarget(element)) {
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
    public int requestScrollLeft(ElementNode element) {
        resolveInteractiveLayoutBox();
        return scrollState.getScrollLeft(element);
    }

    @Override
    public int requestScrollTop(ElementNode element) {
        resolveInteractiveLayoutBox();
        return scrollState.getScrollTop(element);
    }

    @Override
    public int requestMaxScrollLeft(ElementNode element) {
        resolveInteractiveLayoutBox();
        return scrollState.getMaxScrollLeft(element);
    }

    @Override
    public int requestMaxScrollTop(ElementNode element) {
        resolveInteractiveLayoutBox();
        return scrollState.getMaxScrollTop(element);
    }

    @Override
    public boolean requestScrollIntoView(ElementNode element) {
        return focusManager.scrollElementIntoView(element);
    }

    @Override
    public DocumentAnimation requestAnimation(ElementNode element, DocumentKeyframes keyframes,
            DocumentAnimationOptions options) {
        ElementNode resolvedElement = Objects.requireNonNull(element, "element");
        DocumentKeyframes resolvedKeyframes = Objects.requireNonNull(keyframes, "keyframes");
        if (!isElementAttachedToDocument(resolvedElement)) {
            return DocumentAnimation.inactive(resolvedElement, resolvedKeyframes.getName(), options);
        }
        long currentTimeNanos = animationClock.getCurrentTimeNanos();
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        DocumentLayoutBox targetBox = findLayoutBox(rootBox, resolvedElement);
        if (targetBox == null) {
            return DocumentAnimation.inactive(resolvedElement, resolvedKeyframes.getName(), options);
        }
        DocumentAnimation animation = animationTimeline.startKeyframeAnimation(targetBox, resolvedKeyframes, options,
                currentTimeNanos);
        invalidateAnimationRuntimeCaches();
        return animation;
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
                    || currentElement.getDoubleClickHandler() != null
                    || currentElement.getContextMenuHandler() != null
                    || currentElement.getDragHandler() != null
                    || currentElement.getDragStartHandler() != null
                    || currentElement.getDragOverHandler() != null
                    || currentElement.getDragEndHandler() != null
                    || "true".equals(currentElement.getAttribute("draggable"))
                    || currentElement.getKeyHandler() != null
                    || currentElement.getTextInputHandler() != null
                    || currentElement.getTransitionStartHandler() != null
                    || currentElement.getTransitionEndHandler() != null
                    || currentElement.getTransitionCancelHandler() != null
                    || currentElement.getAnimationStartHandler() != null
                    || currentElement.getAnimationIterationHandler() != null
                    || currentElement.getAnimationEndHandler() != null) {
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
            releasePressedElement(null);
            dragController.clearDragState();
            return;
        }
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        if (event.getButton() == 0 && scrollState.beginScrollbarDrag(rootBox, event.getMouseX() - getAbsoluteX(),
                event.getMouseY() - getAbsoluteY())) {
            pressedElement = null;
            pressedButton = -1;
            dragController.clearDragState();
            return;
        }
        pressedElement = findElementAt(event.getMouseX(), event.getMouseY());
        pressedButton = pressedElement == null ? -1 : event.getButton();
        updateHoveredElement(pressedElement, event);
        dragController.beginDragIfNeeded(pressedElement, event);
        DocumentMouseEventDispatcher.dispatchMouseDown(pressedElement, event, getAbsoluteX(), getAbsoluteY());
        DocumentMouseEventDispatcher.dispatchActive(pressedElement, true, event);
        focusManager.focusElement(focusManager.resolveFocusableElement(pressedElement), false);
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
        dragController.dispatchDragMove(event);
        updateHoveredElement(findElementAt(event.getMouseX(), event.getMouseY()), event);
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        if (event == null) {
            releasePressedElement(null);
            dragController.clearDragState();
            return;
        }
        if (event.getButton() == 0 && scrollState.endScrollbarDrag()) {
            pressedElement = null;
            pressedButton = -1;
            dragController.clearDragState();
            return;
        }
        ElementNode releasedElement = findElementAt(event.getMouseX(), event.getMouseY());
        updateHoveredElement(releasedElement, event);
        ElementNode target = pressedElement != null && pressedElement == releasedElement ? releasedElement : null;
        boolean dragHandled = dragController.dispatchDragEnd(event);
        DocumentMouseEventDispatcher.dispatchMouseUp(pressedElement, event, getAbsoluteX(), getAbsoluteY());
        DocumentMouseEventDispatcher.dispatchActive(pressedElement, false, event);
        pressedElement = null;
        pressedButton = -1;
        syncCursorFromHoveredElement();
        if (dragHandled || target == null) {
            clickEventDispatcher.clearLastClickState();
            return;
        }
        if (event.getButton() == DocumentClickEventDispatcher.PRIMARY_BUTTON) {
            clickEventDispatcher.dispatchClick(target, event, getAbsoluteX(), getAbsoluteY());
            clickEventDispatcher.dispatchPostClickEvents(target, event, getAbsoluteX(), getAbsoluteY());
        } else if (event.getButton() == DocumentClickEventDispatcher.CONTEXT_MENU_BUTTON) {
            clickEventDispatcher.dispatchContextMenu(target, event, getAbsoluteX(), getAbsoluteY());
            clickEventDispatcher.clearLastClickState();
        } else {
            clickEventDispatcher.clearLastClickState();
        }
    }

    @Override
    public void onKeyEvent(UiKeyEvent event) {
        keyboardEventDispatcher.dispatchKeyAndDefault(focusManager.getFocusedElement(), event);
    }

    @Override
    public void onTextInput(UiTextInputEvent event) {
        keyboardEventDispatcher.dispatchTextInput(focusManager.getFocusedElement(), event);
    }

    @Override
    public void onMouseLeave() {
        releasePressedElement(null);
        updateHoveredElement(null, null);
    }

    @Override
    public void onFocusChanged(boolean focused) {
        if (!focused) {
            releasePressedElement(null);
            updateHoveredElement(null, null);
            focusManager.focusElement(null, false);
        }
    }

    @Override
    public void onFocusTraversalEntered(boolean reverse) {
        focusManager.focusFirstElementInTraversalOrder(reverse);
    }

    @Override
    public boolean onFocusTraversal(boolean reverse) {
        return focusManager.focusTraversal(reverse);
    }

    @Override
    public boolean isFocusable() {
        return focusManager.hasFocusableElement();
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
        animationStateChanged |= flushCompletedAnimationEvents(currentTimeNanos);
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

    private void releasePressedElement(UiMouseEvent event) {
        if (pressedElement == null) {
            return;
        }
        ElementNode previousPressedElement = pressedElement;
        int previousPressedButton = pressedButton;
        pressedElement = null;
        pressedButton = -1;
        if (event != null) {
            DocumentMouseEventDispatcher.dispatchActive(previousPressedElement, false, event);
        } else {
            DocumentMouseEventDispatcher.dispatchActive(previousPressedElement, false,
                    new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, -1, -1, previousPressedButton, 0, 0, 0,
                            System.nanoTime()));
        }
        syncCursorFromHoveredElement();
    }

    private void clearNativeButtonState(ElementNode element) {
        keyboardEventDispatcher.clearNativeButtonState(element);
    }

    private void focusElementFromKeyboard(ElementNode element, boolean focusVisible) {
        focusManager.focusElement(element, focusVisible);
    }

    private DocumentLayoutBox resolveInteractiveLayoutBox() {
        DocumentLayoutBox rootBox = resolvePaintLayoutBox(false);
        long currentTimeNanos = animationClock.getCurrentTimeNanos();
        animationTimeline.updateFromLayout(rootBox, currentTimeNanos);
        flushCompletedAnimationEvents(currentTimeNanos);
        if (animationTimeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT)) {
            return resolveRuntimeLayoutBox(currentTimeNanos,
                    animationTimeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        }
        invalidateRuntimeLayoutCache();
        updateScrollStateFromCachedLayoutIfNeeded();
        return rootBox;
    }

    private static DocumentLayoutBox findLayoutBox(DocumentLayoutBox box, ElementNode element) {
        if (box == null || element == null) {
            return null;
        }
        if (box.getElement() == element) {
            return box;
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            DocumentLayoutBox found = findLayoutBox(child, element);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean flushCompletedAnimationEvents(long currentTimeNanos) {
        DocumentAnimationTimeline.PruneResult pruneResult = animationTimeline.pruneFinishedAnimationsWithResult(
                currentTimeNanos);
        DocumentAnimationEventDispatcher.dispatchCompletedAnimationEvents(pruneResult, currentTimeNanos,
                this::isElementAttachedToDocument);
        return pruneResult.isChanged();
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

    private void invalidateAnimationRuntimeCaches() {
        cachedPaintScrollVersion = -1;
        invalidateRuntimeLayoutCache();
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
        DocumentMouseEventDispatcher.dispatchHoverChangedWithAncestorAwareness(previousElement, false,
                resolvedElement, event, getAbsoluteX(), getAbsoluteY());
        DocumentMouseEventDispatcher.dispatchHoverChangedWithAncestorAwareness(resolvedElement, true,
                previousElement, event, getAbsoluteX(), getAbsoluteY());
        syncCursorFromHoveredElement();
    }

    private void syncCursorFromHoveredElement() {
        cursorHost.applyCursor(DocumentCursorResolver.resolve(hoveredElement, focusManager.getFocusedElement(),
                focusManager.isFocusVisible(), pressedElement, this::isElementAttachedToDocument));
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
