package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
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
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
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
import club.heiqi.uilib.ui.layout.DocumentRuntimeTransforms;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.BoxLocation;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.paint.DocumentPaintRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 将 HTML-like 文档模型挂接到现有 retained widget 渲染后端的适配器。
 */
public final class HtmlLikeDocumentWidget extends Widget implements UiDocument.DocumentInteractionRuntime {

    private static final String HIT_TEST_PASSTHROUGH_ATTRIBUTE = "data-hit-test-passthrough";
    private static final String PRESERVE_FOCUS_ON_MOUSE_DOWN_ATTRIBUTE = "data-qz-preserve-focus-on-mousedown";

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
    private int lastLayoutReusedSubtreeCount;
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
    private int latestPointerScreenX = Integer.MIN_VALUE;
    private int latestPointerScreenY = Integer.MIN_VALUE;
    private long latestPointerTimeNanos;
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

            @Override
            public void dispatchSyntheticClick(ElementNode element, long timeNanos) {
                HtmlLikeDocumentWidget.this.dispatchSyntheticClick(element, timeNanos);
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
        return hasLayoutRuntimeValue();
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
                runtimeLayoutGeneration, textMeasureService.getEpoch(), lastLayoutReusedSubtreeCount);
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
        int documentX = screenX - getAbsoluteX();
        int documentY = screenY - getAbsoluteY();
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        return DocumentHitTestEngine.hitTest(rootBox, resolveTopLayerLayoutBoxes(rootBox, null), scrollState,
                documentX, documentY, currentTimeNanos, animationTimeline);
    }

    /**
     * 返回屏幕坐标命中的、且属于指定子树的 HTML-like 元素。
     *
     * @param subtreeRoot 目标子树根
     * @param screenX 屏幕 X
     * @param screenY 屏幕 Y
     * @return 命中的最深元素；未命中或命中不在子树内时返回 null
     */
    public ElementNode findElementAtWithin(ElementNode subtreeRoot, int screenX, int screenY) {
        if (subtreeRoot == null || !isElementAttachedToDocument(subtreeRoot)
                || getWidth() <= 0 || getHeight() <= 0 || !contains(screenX, screenY)) {
            return null;
        }
        long currentTimeNanos = animationClock.getCurrentTimeNanos();
        int documentX = screenX - getAbsoluteX();
        int documentY = screenY - getAbsoluteY();
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();

        List<DocumentLayoutBox> topLayerBoxes = resolveTopLayerLayoutBoxes(rootBox, null);
        BoxLocation subtreeLocation = DocumentVisualTraversal.findBoxLocation(rootBox, topLayerBoxes, scrollState,
                subtreeRoot, currentTimeNanos, animationTimeline);
        if (subtreeLocation == null) {
            return null;
        }
        ElementNode hit = DocumentHitTestEngine.hitTest(rootBox, topLayerBoxes, scrollState, documentX, documentY,
                currentTimeNanos, animationTimeline);
        return isElementWithinSubtree(hit, subtreeRoot) ? hit : null;
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
        long currentTimeNanos = animationClock.getCurrentTimeNanos();
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        if (DocumentVisualTraversal.findBoxLocation(rootBox, resolveTopLayerLayoutBoxes(rootBox, null), scrollState,
                element, currentTimeNanos, animationTimeline) == null) {
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
    public DocumentElementBounds requestElementBounds(ElementNode element) {
        if (element == null || !isElementAttachedToDocument(element)) {
            return DocumentElementBounds.unavailable();
        }
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        long currentTimeNanos = animationClock.getCurrentTimeNanos();
        BoxLocation location = DocumentVisualTraversal.findBoxLocation(rootBox, resolveTopLayerLayoutBoxes(rootBox, null),
                scrollState, element, currentTimeNanos, animationTimeline);
        if (location == null) {
            return DocumentElementBounds.unavailable();
        }
        DocumentLayoutBox box = location.getBoxContext().getBox();
        int offsetX = location.getBoxContext().getBoxOffsetX();
        int offsetY = location.getBoxContext().getBoxOffsetY();
        return DocumentElementBounds.of(box.getLeft() + offsetX, box.getTop() + offsetY, box.getWidth(),
                box.getHeight(), box.getContentLeft() + offsetX, box.getContentTop() + offsetY,
                box.getContentWidth(), box.getContentHeight());
    }

    @Override
    public DocumentElementBounds requestVisualElementBounds(ElementNode element) {
        if (element == null || !isElementAttachedToDocument(element)) {
            return DocumentElementBounds.unavailable();
        }
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        VisualBounds bounds = resolveVisualBounds(rootBox, element, animationClock.getCurrentTimeNanos());
        return bounds == null ? DocumentElementBounds.unavailable() : bounds.toElementBounds();
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
        BoxLocation location = DocumentVisualTraversal.findBoxLocation(rootBox,
                resolveTopLayerLayoutBoxes(rootBox, null), scrollState, resolvedElement, currentTimeNanos,
                animationTimeline);
        if (location == null) {
            return DocumentAnimation.inactive(resolvedElement, resolvedKeyframes.getName(), options);
        }
        DocumentAnimation animation = animationTimeline.startKeyframeAnimation(location.getBoxContext().getBox(),
                resolvedKeyframes, options, currentTimeNanos);
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
                    || currentElement.getWheelHandler() != null
                    || currentElement.getCaptureWheelHandler() != null
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
        recordLatestPointer(event);
        scrollEventCount++;
        lastScrollWheelDelta = event.getWheelDelta();
        lastScrollEventTimeNanos = event.getTimeNanos();
        DocumentMouseEventDispatcher.WheelDispatchResult dispatchResult = DocumentMouseEventDispatcher.dispatchWheel(
                findElementAt(event.getMouseX(), event.getMouseY()), event, getAbsoluteX(), getAbsoluteY());
        if (dispatchResult.isDefaultPrevented()) {
            lastScrollConsumed = true;
            return true;
        }
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        int previousScrollVersion = scrollState.getVersion();
        boolean scrolled = scrollState.handleWheel(rootBox, resolveTopLayerLayoutBoxes(rootBox, null),
                event.getMouseX() - getAbsoluteX(), event.getMouseY() - getAbsoluteY(), event.getWheelDelta(),
                event.getTimeNanos(), animationClock.getCurrentTimeNanos(), animationTimeline);
        boolean consumed = scrolled || dispatchResult.isPropagationStopped();
        lastScrollConsumed = consumed;
        if (scrollState.getVersion() != previousScrollVersion) {
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
        recordLatestPointer(event);
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        if (event.getButton() == 0 && scrollState.beginScrollbarDrag(rootBox, resolveTopLayerLayoutBoxes(rootBox,
                null), event.getMouseX() - getAbsoluteX(), event.getMouseY() - getAbsoluteY(), event.getTimeNanos(),
                animationClock.getCurrentTimeNanos(), animationTimeline)) {
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
        if (!shouldPreserveFocusOnMouseDown(pressedElement)) {
            focusManager.focusElement(focusManager.resolveFocusableElement(pressedElement), false);
        }
        syncCursorFromHoveredElement();
    }

    @Override
    public void onMouseMove(UiMouseEvent event) {
        if (event == null) {
            return;
        }
        recordLatestPointer(event);
        if (scrollState.isDraggingScrollbar()) {
            DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
            scrollState.updateScrollbarDrag(rootBox, resolveTopLayerLayoutBoxes(rootBox, null),
                    event.getMouseX() - getAbsoluteX(), event.getMouseY() - getAbsoluteY(), event.getTimeNanos(),
                    animationClock.getCurrentTimeNanos(), animationTimeline);
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
        recordLatestPointer(event);
        try {
            if (event.getButton() == 0 && scrollState.endScrollbarDrag()) {
                pressedElement = null;
                pressedButton = -1;
                dragController.clearDragState();
                return;
            }
            ElementNode releasedElement = findElementAt(event.getMouseX(), event.getMouseY());
            updateHoveredElement(releasedElement, event);
            ElementNode previousPressedElement = pressedElement;
            boolean dragHandled = dragController.dispatchDragEnd(event);
            DocumentMouseEventDispatcher.dispatchMouseUp(releasedElement, event, getAbsoluteX(), getAbsoluteY());
            DocumentMouseEventDispatcher.dispatchActive(previousPressedElement, false, event);
            pressedElement = null;
            pressedButton = -1;
            syncCursorFromHoveredElement();
            if (dragHandled) {
                clickEventDispatcher.clearLastClickState();
                return;
            }
            if (event.getButton() == DocumentClickEventDispatcher.PRIMARY_BUTTON) {
                ElementNode target = clickEventDispatcher.resolveClickTarget(previousPressedElement, releasedElement);
                if (target == null) {
                    clickEventDispatcher.clearLastClickState();
                    return;
                }
                clickEventDispatcher.dispatchClick(target, event, getAbsoluteX(), getAbsoluteY());
                clickEventDispatcher.dispatchPostClickEvents(target, event, getAbsoluteX(), getAbsoluteY());
            } else if (event.getButton() == DocumentClickEventDispatcher.CONTEXT_MENU_BUTTON) {
                clickEventDispatcher.dispatchContextMenu(releasedElement, event, getAbsoluteX(), getAbsoluteY());
                clickEventDispatcher.clearLastClickState();
            } else {
                clickEventDispatcher.clearLastClickState();
            }
        } finally {
            refreshHoverAtLatestPointer(event.getTimeNanos());
        }
    }

    @Override
    public void onKeyEvent(UiKeyEvent event) {
        keyboardEventDispatcher.dispatchKeyAndDefault(focusManager.getFocusedElement(), event);
        if (event != null && event.getAction() == UiKeyEvent.Action.PRESSED) {
            refreshHoverAtLatestPointer(event.getTimeNanos());
        }
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
        boolean animationStateChanged = animationTimeline.updateFromLayout(
                resolveAnimationLayoutRoots(rootBox, null), currentTimeNanos);
        animationStateChanged |= flushCompletedAnimationEvents(currentTimeNanos);
        boolean layoutRuntimeValueActive = hasLayoutRuntimeValue();
        if (layoutRuntimeValueActive) {
            rootBox = resolveRuntimeLayoutBox(currentTimeNanos,
                    hasLayoutAnimationWork());
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

        List<DocumentLayoutBox> topLayerBoxes = resolveTopLayerLayoutBoxes(rootBox, layoutRuntimeValueActive
                ? createAnimationLayoutValueResolver(currentTimeNanos) : null);
        cachedPaintCommands = DocumentPaintEngine.buildPaintCommands(rootBox, topLayerBoxes, scrollState,
                currentTimeNanos, animationTimeline, textMeasureService);
        paintCacheGeneration++;
        cachedPaintScrollVersion = scrollVersion;
        cachedPaintTransientScrollbarActive = transientScrollbarActive;
        return cachedPaintCommands;
    }

    private DocumentLayoutBox resolveRuntimeLayoutBox(final long currentTimeNanos, boolean layoutAnimationWork) {
        if (!layoutAnimationWork && isRuntimeLayoutCacheReusable()) {
            scrollState.updateFromLayout(cachedRuntimeLayoutBox, resolveTopLayerLayoutBoxes(cachedRuntimeLayoutBox,
                    createAnimationLayoutValueResolver(currentTimeNanos)), currentTimeNanos, animationTimeline);
            return cachedRuntimeLayoutBox;
        }

        DocumentLayoutEngine.LayoutRuntimeValueResolver layoutValueResolver =
                createAnimationLayoutValueResolver(currentTimeNanos);
        DocumentLayoutBox rootBox = layoutDocument(layoutValueResolver);
        runtimeLayoutGeneration++;
        if (layoutAnimationWork) {
            invalidateRuntimeLayoutCache();
        } else {
            cacheRuntimeLayoutBox(rootBox);
        }
        scrollState.updateFromLayout(rootBox, resolveTopLayerLayoutBoxes(rootBox, layoutValueResolver),
                currentTimeNanos, animationTimeline);
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
        if (cachedLayoutScrollStateUpdated || cachedLayoutBox == null || hasLayoutRuntimeValue()) {
            return;
        }
        scrollState.updateFromLayout(cachedLayoutBox, resolveTopLayerLayoutBoxes(cachedLayoutBox, null));
        cachedLayoutScrollStateUpdated = true;
    }

    private DocumentLayoutBox layoutDocument(DocumentLayoutEngine.LayoutRuntimeValueResolver layoutValueResolver) {
        boolean staticLayout = layoutValueResolver == null;
        DocumentLayoutBox previousLayoutBox = staticLayout ? cachedLayoutBox : null;
        DocumentLayoutBox rootBox = viewportRootScrollingEnabled
                ? DocumentLayoutEngine.layoutViewportRoot(document.getRootElement(), getWidth(), getHeight(),
                        textMeasureService, layoutValueResolver, previousLayoutBox)
                : DocumentLayoutEngine.layout(document.getRootElement(), getWidth(), getHeight(), textMeasureService,
                        layoutValueResolver, previousLayoutBox);
        lastLayoutReusedSubtreeCount = staticLayout ? rootBox.getLayoutPassReusedSubtreeCountForDiagnostics() : 0;
        return rootBox;
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

    private void dispatchSyntheticClick(ElementNode target, long timeNanos) {
        clickEventDispatcher.dispatchSyntheticClick(target, timeNanos);
    }

    private DocumentLayoutBox resolveInteractiveLayoutBox() {
        DocumentLayoutBox rootBox = resolvePaintLayoutBox(false);
        long currentTimeNanos = animationClock.getCurrentTimeNanos();
        animationTimeline.updateFromLayout(resolveAnimationLayoutRoots(rootBox, null), currentTimeNanos);
        flushCompletedAnimationEvents(currentTimeNanos);
        if (hasLayoutRuntimeValue()) {
            rootBox = resolveRuntimeLayoutBox(currentTimeNanos,
                    hasLayoutAnimationWork());
            return rootBox;
        }
        invalidateRuntimeLayoutCache();
        updateScrollStateFromCachedLayoutIfNeeded();
        return rootBox;
    }

    private DocumentAnimationTimelineLayoutResolver createAnimationLayoutValueResolver(final long currentTimeNanos) {
        return new DocumentAnimationTimelineLayoutResolver(currentTimeNanos);
    }

    private boolean hasLayoutRuntimeValue() {
        return animationTimeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT)
                || hasTransformRuntimeValue();
    }

    private boolean hasLayoutAnimationWork() {
        return animationTimeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT)
                || hasTransformAnimationWork();
    }

    private boolean hasTransformRuntimeValue() {
        return animationTimeline.hasRuntimeValue(DocumentAnimationProperty.TRANSLATE_X)
                || animationTimeline.hasRuntimeValue(DocumentAnimationProperty.TRANSLATE_Y)
                || animationTimeline.hasRuntimeValue(DocumentAnimationProperty.SCALE_X)
                || animationTimeline.hasRuntimeValue(DocumentAnimationProperty.SCALE_Y)
                || animationTimeline.hasRuntimeValue(DocumentAnimationProperty.ROTATE);
    }

    private boolean hasTransformAnimationWork() {
        return animationTimeline.hasAnimationWork(DocumentAnimationProperty.TRANSLATE_X)
                || animationTimeline.hasAnimationWork(DocumentAnimationProperty.TRANSLATE_Y)
                || animationTimeline.hasAnimationWork(DocumentAnimationProperty.SCALE_X)
                || animationTimeline.hasAnimationWork(DocumentAnimationProperty.SCALE_Y)
                || animationTimeline.hasAnimationWork(DocumentAnimationProperty.ROTATE);
    }

    private List<DocumentLayoutBox> resolveTopLayerLayoutBoxes(DocumentLayoutBox rootBox,
            DocumentLayoutEngine.LayoutRuntimeValueResolver layoutValueResolver) {
        List<ElementNode> topLayerElements = document.__getTopLayerElements();
        if (topLayerElements.isEmpty()) {
            return Collections.emptyList();
        }
        List<DocumentLayoutBox> boxes = new ArrayList<DocumentLayoutBox>(topLayerElements.size());
        for (ElementNode topLayerElement : topLayerElements) {
            if (!isElementAttachedToDocument(topLayerElement)) {
                continue;
            }
            syncSelectTopLayerPlacement(rootBox, topLayerElement);
            boxes.add(DocumentLayoutEngine.layoutTopLayerElement(topLayerElement, getWidth(), getHeight(),
                    textMeasureService, layoutValueResolver));
        }
        return boxes;
    }

    private List<DocumentLayoutBox> resolveAnimationLayoutRoots(DocumentLayoutBox rootBox,
            DocumentLayoutEngine.LayoutRuntimeValueResolver layoutValueResolver) {
        List<DocumentLayoutBox> topLayerBoxes = resolveTopLayerLayoutBoxes(rootBox, layoutValueResolver);
        if (topLayerBoxes.isEmpty()) {
            return Collections.singletonList(rootBox);
        }
        List<DocumentLayoutBox> roots = new ArrayList<DocumentLayoutBox>(topLayerBoxes.size() + 1);
        roots.add(rootBox);
        roots.addAll(topLayerBoxes);
        return roots;
    }

    private void syncSelectTopLayerPlacement(DocumentLayoutBox rootBox, ElementNode topLayerElement) {
        if (rootBox == null || topLayerElement == null || !"listbox".equals(topLayerElement.getAttribute("role"))) {
            return;
        }
        DocumentNode parent = topLayerElement.getParent();
        if (!(parent instanceof ElementNode)) {
            return;
        }
        ElementNode anchor = (ElementNode) parent;
        if (!"select".equals(anchor.getTagName())) {
            return;
        }
        VisualBounds anchorBounds = resolveVisualBounds(rootBox, anchor, animationClock.getCurrentTimeNanos());
        if (anchorBounds == null || anchorBounds.getWidth() <= 0) {
            return;
        }
        topLayerElement.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(anchorBounds.getLeft()))
                .setTop(UiStyleLength.px(anchorBounds.getTop() + anchorBounds.getHeight()))
                .setWidth(UiStyleLength.px(anchorBounds.getWidth()))
                .clearZIndex();
    }

    private VisualBounds resolveVisualBounds(DocumentLayoutBox rootBox, ElementNode element, long currentTimeNanos) {
        if (rootBox == null || element == null) {
            return null;
        }
        return findVisualBounds(DocumentVisualTraversal.resolveRootBoxContext(rootBox, scrollState,
                currentTimeNanos, animationTimeline), element, currentTimeNanos, VisualTransform.identity());
    }

    private VisualBounds findVisualBounds(DocumentVisualTraversal.BoxContext boxContext, ElementNode element,
            long currentTimeNanos, VisualTransform transform) {
        DocumentLayoutBox box = boxContext.getBox();
        VisualTransform nextTransform = transform.multiply(resolveBoxTransform(box, boxContext, currentTimeNanos));
        if (box.getElement() == element) {
            return nextTransform.mapBounds(box.getLeft() + boxContext.getBoxOffsetX(),
                    box.getTop() + boxContext.getBoxOffsetY(), box.getWidth(), box.getHeight());
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            VisualBounds bounds = findVisualBounds(DocumentVisualTraversal.resolveChildBoxContext(boxContext, child,
                    scrollState), element, currentTimeNanos, nextTransform);
            if (bounds != null) {
                return bounds;
            }
        }
        return null;
    }

    private VisualTransform resolveBoxTransform(DocumentLayoutBox box, DocumentVisualTraversal.BoxContext boxContext,
            long currentTimeNanos) {
        UiTransform transform = resolveAnimatedTransform(box, currentTimeNanos);
        if (transform == null || transform.isIdentity()) {
            return VisualTransform.identity();
        }
        int left = box.getLeft() + boxContext.getBoxOffsetX();
        int top = box.getTop() + boxContext.getBoxOffsetY();
        return VisualTransform.from(transform, left, top, box.getWidth(), box.getHeight());
    }

    private UiTransform resolveAnimatedTransform(DocumentLayoutBox box, long currentTimeNanos) {
        UiTransform baseTransform = box.getComputedStyle().getTransform();
        if (baseTransform == null) {
            baseTransform = UiTransform.identity();
        }
        float translateX = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.TRANSLATE_X,
                baseTransform.getTranslateX(), currentTimeNanos);
        float translateY = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.TRANSLATE_Y,
                baseTransform.getTranslateY(), currentTimeNanos);
        float scaleX = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.SCALE_X,
                baseTransform.getScaleX(), currentTimeNanos);
        float scaleY = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.SCALE_Y,
                baseTransform.getScaleY(), currentTimeNanos);
        float rotate = animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.ROTATE,
                baseTransform.getRotateDegrees(), currentTimeNanos);
        return UiTransform.of(translateX, translateY, scaleX, scaleY, rotate, baseTransform.getOriginX(),
                baseTransform.getOriginY());
    }

    private final class DocumentAnimationTimelineLayoutResolver
            implements DocumentLayoutEngine.LayoutRuntimeValueResolver {

        private final long currentTimeNanos;

        private DocumentAnimationTimelineLayoutResolver(long currentTimeNanos) {
            this.currentTimeNanos = currentTimeNanos;
        }

        @Override
        public int resolve(ElementNode element, DocumentAnimationProperty property, int baseValue) {
            return Math.round(animationTimeline.resolveFloat(element, property, baseValue, currentTimeNanos));
        }

        @Override
        public boolean createsFixedContainingBlock(ElementNode element, ComputedStyle computedStyle) {
            return DocumentRuntimeTransforms.createsFixedContainingBlock(element, computedStyle, currentTimeNanos,
                    animationTimeline);
        }
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

    private void recordLatestPointer(UiMouseEvent event) {
        latestPointerScreenX = event.getMouseX();
        latestPointerScreenY = event.getMouseY();
        latestPointerTimeNanos = event.getTimeNanos();
    }

    private void refreshHoverAtLatestPointer(long fallbackTimeNanos) {
        if (latestPointerScreenX == Integer.MIN_VALUE) {
            return;
        }
        long timeNanos = latestPointerTimeNanos == 0L ? fallbackTimeNanos : latestPointerTimeNanos;
        UiMouseEvent syntheticEvent = new UiMouseEvent(UiMouseEvent.Action.MOVE, latestPointerScreenX,
                latestPointerScreenY, -1, 0, 0, 0, timeNanos);
        updateHoveredElement(findElementAt(latestPointerScreenX, latestPointerScreenY), syntheticEvent);
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

    private static boolean isElementWithinSubtree(ElementNode element, ElementNode subtreeRoot) {
        if (element == null || subtreeRoot == null) {
            return false;
        }
        for (DocumentNode current = element; current != null; current = current.getParent()) {
            if (current == subtreeRoot) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldPreserveFocusOnMouseDown(ElementNode target) {
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode element = (ElementNode) current;
            if ("true".equals(element.getAttribute(PRESERVE_FOCUS_ON_MOUSE_DOWN_ATTRIBUTE))) {
                return true;
            }
        }
        return false;
    }

    /**
     * HTML-like 文档组件缓存与布局重建的只读诊断快照。
     */
    public static final class PerformanceDiagnosticsSnapshot {

        private static final PerformanceDiagnosticsSnapshot EMPTY = new PerformanceDiagnosticsSnapshot(0, 0, 0, 0, 0);

        private final int paintCacheGeneration;
        private final int staticLayoutGeneration;
        private final int runtimeLayoutGeneration;
        private final int textMeasureEpoch;
        private final int lastLayoutReusedSubtreeCount;

        private PerformanceDiagnosticsSnapshot(int paintCacheGeneration, int staticLayoutGeneration,
                int runtimeLayoutGeneration, int textMeasureEpoch, int lastLayoutReusedSubtreeCount) {
            this.paintCacheGeneration = paintCacheGeneration;
            this.staticLayoutGeneration = staticLayoutGeneration;
            this.runtimeLayoutGeneration = runtimeLayoutGeneration;
            this.textMeasureEpoch = textMeasureEpoch;
            this.lastLayoutReusedSubtreeCount = lastLayoutReusedSubtreeCount;
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

        /**
         * 返回最近一次静态布局 pass 复用的布局子树数量。
         *
         * @return 复用布局子树数量
         */
        public int getLastLayoutReusedSubtreeCount() {
            return lastLayoutReusedSubtreeCount;
        }
    }

    private static final class VisualTransform {

        private static final VisualTransform IDENTITY = new VisualTransform(1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F);

        private final float a;
        private final float b;
        private final float c;
        private final float d;
        private final float e;
        private final float f;

        private VisualTransform(float a, float b, float c, float d, float e, float f) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
        }

        private static VisualTransform identity() {
            return IDENTITY;
        }

        private static VisualTransform from(UiTransform transform, int left, int top, int width, int height) {
            float originX = left + transform.resolveOriginX(width);
            float originY = top + transform.resolveOriginY(height);
            double radians = Math.toRadians(transform.getRotateDegrees());
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            float a = cos * transform.getScaleX();
            float b = sin * transform.getScaleX();
            float c = -sin * transform.getScaleY();
            float d = cos * transform.getScaleY();
            float e = originX + transform.getTranslateX() - a * originX - c * originY;
            float f = originY + transform.getTranslateY() - b * originX - d * originY;
            return new VisualTransform(a, b, c, d, e, f);
        }

        private VisualTransform multiply(VisualTransform next) {
            if (next == IDENTITY) {
                return this;
            }
            if (this == IDENTITY) {
                return next;
            }
            return new VisualTransform(
                    a * next.a + c * next.b,
                    b * next.a + d * next.b,
                    a * next.c + c * next.d,
                    b * next.c + d * next.d,
                    a * next.e + c * next.f + e,
                    b * next.e + d * next.f + f);
        }

        private VisualBounds mapBounds(int left, int top, int width, int height) {
            UiTransform.Point first = mapPoint(left, top);
            UiTransform.Point second = mapPoint(left + width, top);
            UiTransform.Point third = mapPoint(left + width, top + height);
            UiTransform.Point fourth = mapPoint(left, top + height);
            float minX = Math.min(Math.min(first.getX(), second.getX()), Math.min(third.getX(), fourth.getX()));
            float maxX = Math.max(Math.max(first.getX(), second.getX()), Math.max(third.getX(), fourth.getX()));
            float minY = Math.min(Math.min(first.getY(), second.getY()), Math.min(third.getY(), fourth.getY()));
            float maxY = Math.max(Math.max(first.getY(), second.getY()), Math.max(third.getY(), fourth.getY()));
            return new VisualBounds(minX, minY, maxX, maxY);
        }

        private UiTransform.Point mapPoint(float x, float y) {
            return new UiTransform.Point(a * x + c * y + e, b * x + d * y + f);
        }
    }

    private static final class VisualBounds {

        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private VisualBounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private int getLeft() {
            return Math.round(left);
        }

        private int getTop() {
            return Math.round(top);
        }

        private int getWidth() {
            return Math.max(0, Math.round(right - left));
        }

        private int getHeight() {
            return Math.max(0, Math.round(bottom - top));
        }

        private DocumentElementBounds toElementBounds() {
            return DocumentElementBounds.of(getLeft(), getTop(), getWidth(), getHeight());
        }
    }
}
