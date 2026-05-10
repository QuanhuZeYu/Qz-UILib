package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationClock;
import club.heiqi.uilib.ui.animation.DocumentAnimationImpact;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.animation.SystemDocumentAnimationClock;
import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
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
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 将 HTML-like 文档模型挂接到现有 retained widget 渲染后端的适配器。
 */
public final class HtmlLikeDocumentWidget extends Widget {

    private final UiDocument document;
    private final TextMeasureService textMeasureService;
    private final DocumentScrollState scrollState = new DocumentScrollState();
    private final DocumentAnimationTimeline animationTimeline = new DocumentAnimationTimeline();
    private final int preferredWidth;
    private final int preferredHeight;
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
    private boolean focusedElementFocusVisible;
    private boolean viewportRootScrollingEnabled;
    private boolean cachedLayoutScrollStateUpdated;
    private List<DocumentPaintCommand> cachedPaintCommands = Collections.emptyList();
    private int dragStartDocumentX;
    private int dragStartDocumentY;
    private int lastDragDocumentX;
    private int lastDragDocumentY;

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
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        boolean consumed = scrollState.handleWheel(rootBox, event.getMouseX() - getAbsoluteX(),
                event.getMouseY() - getAbsoluteY(), event.getWheelDelta());
        if (consumed) {
            updateHoveredElement(findElementAt(event.getMouseX(), event.getMouseY()), event);
        }
        return consumed;
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {
        if (event == null) {
            pressedElement = null;
            draggingElement = null;
            return;
        }
        DocumentLayoutBox rootBox = resolveInteractiveLayoutBox();
        if (event.getButton() == 0 && scrollState.beginScrollbarDrag(rootBox, event.getMouseX() - getAbsoluteX(),
                event.getMouseY() - getAbsoluteY())) {
            pressedElement = null;
            return;
        }
        pressedElement = findElementAt(event.getMouseX(), event.getMouseY());
        beginDragIfNeeded(pressedElement, event);
        dispatchActive(pressedElement, true, event);
        focusElement(resolveFocusableElement(pressedElement), false);
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
            return;
        }
        if (event.getButton() == 0 && scrollState.endScrollbarDrag()) {
            pressedElement = null;
            return;
        }
        ElementNode releasedElement = findElementAt(event.getMouseX(), event.getMouseY());
        ElementNode target = pressedElement != null && pressedElement == releasedElement ? releasedElement : null;
        boolean dragHandled = dispatchDragEnd(event);
        dispatchActive(pressedElement, false, event);
        pressedElement = null;
        if (!dragHandled && target != null) {
            dispatchClick(target, event);
        }
    }

    @Override
    public void onKeyEvent(UiKeyEvent event) {
        ElementNode target = getActiveFocusedElement();
        if (event != null && target != null) {
            dispatchKey(target, event);
        }
    }

    @Override
    public void onTextInput(UiTextInputEvent event) {
        ElementNode target = getActiveFocusedElement();
        if (event != null && target != null) {
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
        int documentX = event.getMouseX() - getAbsoluteX();
        int documentY = event.getMouseY() - getAbsoluteY();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementClickHandler clickHandler = currentElement.getClickHandler();
            if (clickHandler == null) {
                continue;
            }
            DocumentElementClickEvent clickEvent = new DocumentElementClickEvent(target, currentElement, documentX,
                    documentY, event.getButton(), event.getTimeNanos());
            if (clickHandler.onClick(clickEvent)) {
                return true;
            }
        }
        return false;
    }

    private boolean dispatchKey(ElementNode target, UiKeyEvent event) {
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementKeyHandler keyHandler = currentElement.getKeyHandler();
            if (keyHandler == null) {
                continue;
            }
            if (keyHandler.onKey(new DocumentElementKeyEvent(target, currentElement, event))) {
                return true;
            }
        }
        return false;
    }

    private boolean dispatchTextInput(ElementNode target, UiTextInputEvent event) {
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

    private void beginDragIfNeeded(ElementNode target, UiMouseEvent event) {
        draggingElement = null;
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
                return;
            }
        }
    }

    private boolean dispatchDragMove(UiMouseEvent event) {
        if (draggingElement == null || event == null) {
            return false;
        }
        int documentX = event.getMouseX() - getAbsoluteX();
        int documentY = event.getMouseY() - getAbsoluteY();
        int deltaDocumentX = documentX - lastDragDocumentX;
        int deltaDocumentY = documentY - lastDragDocumentY;
        lastDragDocumentX = documentX;
        lastDragDocumentY = documentY;
        return dispatchDragEvent(draggingElement, pressedElement, event, documentX, documentY, deltaDocumentX,
                deltaDocumentY, DocumentElementDragEvent.DragPhase.DRAG);
    }

    private boolean dispatchDragEnd(UiMouseEvent event) {
        if (draggingElement == null || event == null) {
            draggingElement = null;
            return false;
        }
        int documentX = event.getMouseX() - getAbsoluteX();
        int documentY = event.getMouseY() - getAbsoluteY();
        int deltaDocumentX = documentX - lastDragDocumentX;
        int deltaDocumentY = documentY - lastDragDocumentY;
        ElementNode dragTarget = pressedElement;
        ElementNode dragHandlerTarget = draggingElement;
        draggingElement = null;
        return dispatchDragEvent(dragHandlerTarget, dragTarget, event, documentX, documentY, deltaDocumentX,
                deltaDocumentY, DocumentElementDragEvent.DragPhase.END);
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
        if (element.isFocusable() && box.getWidth() > 0 && box.getHeight() > 0) {
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
    }

    private void ensureFocusedElementVisible() {
        if (focusedElement == null || !focusedElementFocusVisible || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        for (int remainingPasses = 16; remainingPasses > 0; remainingPasses--) {
            List<LayoutPathEntry> path = resolveFocusedElementLayoutPath();
            if (path.isEmpty()) {
                return;
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
                return;
            }
        }
    }

    private List<LayoutPathEntry> resolveFocusedElementLayoutPath() {
        if (focusedElement == null) {
            return Collections.emptyList();
        }
        List<LayoutPathEntry> path = new ArrayList<LayoutPathEntry>();
        if (!collectLayoutPath(resolveInteractiveLayoutBox(), focusedElement, 0, 0, path)) {
            return Collections.emptyList();
        }
        return path;
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
        return scrollState.setScrollOffset(ancestorElement, nextScrollLeft, nextScrollTop);
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

    private void updateHoveredElement(ElementNode nextHoveredElement, UiMouseEvent event) {
        ElementNode resolvedElement = nextHoveredElement != null && isElementAttachedToDocument(nextHoveredElement)
                ? nextHoveredElement : null;
        if (hoveredElement == resolvedElement) {
            return;
        }
        ElementNode previousElement = hoveredElement;
        hoveredElement = resolvedElement;
        dispatchHoverChanged(previousElement, false, event);
        dispatchHoverChanged(hoveredElement, true, event);
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
            if (currentElement.isFocusable()) {
                return currentElement;
            }
        }
        return null;
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
