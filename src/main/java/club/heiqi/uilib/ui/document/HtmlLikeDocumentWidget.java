package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
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
    private final int preferredWidth;
    private final int preferredHeight;
    private int cachedMutationVersion = -1;
    private int cachedTextMeasureEpoch = -1;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int cachedPaintScrollVersion = -1;
    private DocumentLayoutBox cachedLayoutBox;
    private ElementNode pressedElement;
    private ElementNode focusedElement;
    private boolean focusedElementFocusVisible;
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
     * 返回指定元素当前纵向滚动偏移。
     *
     * @param element HTML-like 元素
     * @return 纵向滚动偏移
     */
    public int getScrollTop(ElementNode element) {
        resolveLayoutBox();
        return scrollState.getScrollTop(element);
    }

    /**
     * 返回指定元素最大纵向滚动偏移。
     *
     * @param element HTML-like 元素
     * @return 最大纵向滚动偏移
     */
    public int getMaxScrollTop(ElementNode element) {
        resolveLayoutBox();
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
        return DocumentHitTestEngine.hitTest(resolveLayoutBox(), scrollState, screenX - getAbsoluteX(),
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
        return preferredHeight;
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
        DocumentLayoutBox rootBox = resolveLayoutBox();
        return scrollState.handleWheel(rootBox, event.getMouseX() - getAbsoluteX(), event.getMouseY() - getAbsoluteY(),
                event.getWheelDelta());
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {
        if (event == null || event.getButton() != 0) {
            pressedElement = null;
            return;
        }
        pressedElement = findElementAt(event.getMouseX(), event.getMouseY());
        dispatchActive(pressedElement, true, event);
        focusElement(resolveFocusableElement(pressedElement), false);
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        if (event == null || event.getButton() != 0) {
            pressedElement = null;
            return;
        }
        ElementNode releasedElement = findElementAt(event.getMouseX(), event.getMouseY());
        ElementNode target = pressedElement != null && pressedElement == releasedElement ? releasedElement : null;
        dispatchActive(pressedElement, false, event);
        pressedElement = null;
        if (target != null) {
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
    public void onFocusChanged(boolean focused) {
        if (!focused) {
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
        DocumentLayoutBox rootBox = resolveLayoutBox();
        int scrollVersion = scrollState.getVersion();
        if (cachedPaintScrollVersion == scrollVersion) {
            return cachedPaintCommands;
        }

        cachedPaintCommands = DocumentPaintEngine.buildPaintCommands(rootBox, scrollState);
        cachedPaintScrollVersion = scrollVersion;
        return cachedPaintCommands;
    }

    private DocumentLayoutBox resolveLayoutBox() {
        int mutationVersion = document.getMutationVersion();
        int textMeasureEpoch = textMeasureService.getEpoch();
        if (cachedMutationVersion == mutationVersion && cachedTextMeasureEpoch == textMeasureEpoch
                && cachedWidth == getWidth() && cachedHeight == getHeight()) {
            return cachedLayoutBox;
        }

        cachedLayoutBox = DocumentLayoutEngine.layout(document.getRootElement(), getWidth(), getHeight(),
                textMeasureService);
        scrollState.updateFromLayout(cachedLayoutBox);
        cachedMutationVersion = mutationVersion;
        cachedTextMeasureEpoch = textMeasureEpoch;
        cachedWidth = getWidth();
        cachedHeight = getHeight();
        cachedPaintScrollVersion = -1;
        return cachedLayoutBox;
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
        collectFocusableElements(resolveLayoutBox(), focusableElements);
        return focusableElements;
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
}
