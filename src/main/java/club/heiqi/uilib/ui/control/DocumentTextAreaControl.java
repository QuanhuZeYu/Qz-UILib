package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.animation.SystemDocumentAnimationClock;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * 基于 HTML-like 元素实现的多行文本输入控件。
 */
public final class DocumentTextAreaControl {

    private static final int DEFAULT_LINE_HEIGHT = 18;
    private static final int DEFAULT_CARET_WIDTH = 2;
    private static final int CLICK_EMPTY_LINE_HIGHLIGHT_WIDTH = 4;
    private static final long BLINK_PERIOD_NANOS = 530_000_000L;
    private static final UiStyleLength DEFAULT_LINE_HEIGHT_LENGTH = UiStyleLength.px(DEFAULT_LINE_HEIGHT);

    private final ElementNode element;
    private final ElementNode selectionLayer;
    private final ElementNode contentElement;
    private final ElementNode caretLayer;
    private final StringBuilder textBuilder = new StringBuilder();
    private final List<LogicalLine> logicalLines = new ArrayList<LogicalLine>();
    private List<VisualLineMetrics> visualLineMetrics = Collections.emptyList();
    private DocumentTextAreaChangeHandler changeHandler;
    private String placeholder = "";
    private int maxLength = 4096;
    private boolean enabled = true;
    private boolean focused;
    private boolean hovered;
    private boolean readOnly;
    private boolean required;
    private int caretIndex;
    private int selectionAnchorIndex;
    private int preferredColumnCodePoints = -1;
    private int normalBackgroundColor = 0xFF222233;
    private int normalBorderColor = 0xFF555577;
    private int hoverBorderColor = 0xFF7777AA;
    private int focusBorderColor = 0xFF5A9EF7;
    private int disabledBackgroundColor = 0xFF333344;
    private int disabledBorderColor = 0xFF444455;
    private int textColor = 0xFFEEEEFF;
    private int caretColor = 0xFFFFFFFF;
    private int placeholderColor = 0xFF777799;
    private int disabledTextColor = 0xFF666677;
    private int selectionColor = 0x664F86F7;
    private int viewportContentLeft;
    private int viewportContentTop;
    private int viewportContentWidth;
    private int viewportContentHeight;
    private int viewportScreenOffsetX;
    private int viewportScreenOffsetY;
    private long caretBlinkResetNanos;

    /**
     * 创建多行文本输入控件。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentTextAreaControl(UiDocument document) {
        this.element = document.textarea();
        this.selectionLayer = document.div();
        this.contentElement = document.div();
        this.caretLayer = document.div();
        this.element.append(selectionLayer);
        this.element.append(contentElement);
        this.element.append(caretLayer);
        configureElement();
        installHandlers();
        syncContent();
        updateVisualState();
    }

    /**
     * 返回控件根元素。
     *
     * @return 控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前文本内容。
     *
     * @return 当前文本内容
     */
    public String getText() {
        return textBuilder.toString();
    }

    /**
     * 设置当前文本内容。
     *
     * @param text 文本内容；为 null 时清空
     * @return 当前控件
     */
    public DocumentTextAreaControl setText(String text) {
        String normalized = DocumentTextAreaTextSupport.truncateToMaxLength(
                DocumentTextAreaTextSupport.normalizeInput(text), maxLength);
        textBuilder.setLength(0);
        textBuilder.append(normalized);
        caretIndex = textBuilder.length();
        selectionAnchorIndex = caretIndex;
        preferredColumnCodePoints = -1;
        syncContent();
        updateVisualState();
        requestCaretReveal();
        return this;
    }

    /**
     * 设置占位文本。
     *
     * @param placeholder 占位文本；为 null 时清空
     * @return 当前控件
     */
    public DocumentTextAreaControl setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        syncContent();
        updateVisualState();
        return this;
    }

    /**
     * 设置最大输入长度（按 code point 计数）。
     *
     * @param maxLength 最大输入长度
     * @return 当前控件
     */
    public DocumentTextAreaControl setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        String truncated = DocumentTextAreaTextSupport.truncateToMaxLength(textBuilder.toString(), this.maxLength);
        if (!truncated.equals(textBuilder.toString())) {
            textBuilder.setLength(0);
            textBuilder.append(truncated);
            caretIndex = DocumentTextAreaTextSupport.normalizeCaretIndex(textBuilder.toString(),
                    Math.min(caretIndex, textBuilder.length()));
            selectionAnchorIndex = DocumentTextAreaTextSupport.normalizeCaretIndex(textBuilder.toString(),
                    Math.min(selectionAnchorIndex, textBuilder.length()));
            syncContent();
        }
        updateVisualState();
        return this;
    }

    /**
     * 设置控件是否启用。
     *
     * @param enabled 是否启用
     * @return 当前控件
     */
    public DocumentTextAreaControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        element.setFocusable(enabled);
        if (!enabled) {
            focused = false;
            element.setAttribute("disabled", "true");
            element.setAttribute("aria-disabled", "true");
        } else {
            element.removeAttribute("disabled");
            element.removeAttribute("aria-disabled");
        }
        syncContent();
        updateVisualState();
        return this;
    }

    /**
     * 判断控件是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 判断控件当前是否聚焦。
     *
     * @return 是否聚焦
     */
    public boolean isFocused() {
        return focused;
    }

    /**
     * 设置文本区是否只读。
     *
     * @param readOnly 是否只读
     * @return 当前文本区
     */
    public DocumentTextAreaControl setReadOnly(boolean readOnly) {
        if (this.readOnly == readOnly) {
            return this;
        }
        this.readOnly = readOnly;
        if (readOnly) {
            element.setAttribute("readonly", "true");
            element.setAttribute("aria-readonly", "true");
        } else {
            element.removeAttribute("readonly");
            element.removeAttribute("aria-readonly");
        }
        return this;
    }

    /**
     * 判断文本区是否只读。
     *
     * @return 是否只读
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 设置文本区是否必填。
     *
     * @param required 是否必填
     * @return 当前文本区
     */
    public DocumentTextAreaControl setRequired(boolean required) {
        if (this.required == required) {
            return this;
        }
        this.required = required;
        if (required) {
            element.setAttribute("required", "true");
            element.setAttribute("aria-required", "true");
        } else {
            element.removeAttribute("required");
            element.removeAttribute("aria-required");
        }
        return this;
    }

    /**
     * 判断文本区是否必填。
     *
     * @return 是否必填
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * 设置文本变更处理器。
     *
     * @param changeHandler 文本变更处理器；为 null 时清除
     * @return 当前控件
     */
    public DocumentTextAreaControl setChangeHandler(DocumentTextAreaChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置背景色与边框色。
     *
     * @param normalBackgroundColor 正常背景色
     * @param normalBorderColor 正常边框色
     * @param focusBorderColor 聚焦边框色
     * @param disabledBackgroundColor 禁用背景色
     * @param disabledBorderColor 禁用边框色
     * @return 当前控件
     */
    public DocumentTextAreaControl setSurfaceColors(int normalBackgroundColor, int normalBorderColor,
            int focusBorderColor, int disabledBackgroundColor, int disabledBorderColor) {
        this.normalBackgroundColor = normalBackgroundColor;
        this.normalBorderColor = normalBorderColor;
        this.focusBorderColor = focusBorderColor;
        this.disabledBackgroundColor = disabledBackgroundColor;
        this.disabledBorderColor = disabledBorderColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置文本颜色。
     *
     * @param textColor 正常文本色
     * @param placeholderColor 占位文本色
     * @param disabledTextColor 禁用文本色
     * @param selectionColor 选区底色
     * @return 当前控件
     */
    public DocumentTextAreaControl setTextColors(int textColor, int placeholderColor, int disabledTextColor,
            int selectionColor) {
        this.textColor = textColor;
        this.placeholderColor = placeholderColor;
        this.disabledTextColor = disabledTextColor;
        this.selectionColor = selectionColor;
        syncContent();
        updateVisualState();
        return this;
    }

    /**
     * 设置文本光标颜色。
     *
     * @param caretColor 光标颜色
     * @return 当前控件
     */
    public DocumentTextAreaControl setCaretColor(int caretColor) {
        if (this.caretColor == caretColor) {
            return this;
        }
        this.caretColor = caretColor;
        caretLayer.style().setTextColor(caretColor);
        return this;
    }

    private void configureElement() {
        element.setAttribute("aria-multiline", "true");
        element.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(220))
                .setHeight(UiStyleLength.px(96))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(normalBackgroundColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(4))
                .setOverflowX(UiOverflow.AUTO)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(textColor)
                .setLineHeight(DEFAULT_LINE_HEIGHT_LENGTH)
                .setCursor(UiCursor.TEXT);
        selectionLayer.setAttribute("data-hit-test-hidden", "true");
        selectionLayer.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setZIndex(0)
                .setPointerEvents(UiPointerEvents.NONE);
        contentElement.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(1)
                .setWidth(UiStyleLength.percent(1.0F));
        caretLayer.setAttribute("data-hit-test-hidden", "true");
        caretLayer.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setZIndex(2)
                .setTextColor(caretColor)
                .setPointerEvents(UiPointerEvents.NONE);
        selectionLayer.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                updateRenderedLineMetrics(context, selectionLayer, contentLeft, contentTop, contentRight,
                        contentBottom);
                renderSelection(context);
            }
        });
        caretLayer.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                updateRenderedLineMetrics(context, caretLayer, contentLeft, contentTop, contentRight,
                        contentBottom);
                renderCaret(context);
            }
        });
    }

    private void installHandlers() {
        element.setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                focused = enabled && event.isFocused();
                updateVisualState();
                if (focused) {
                    resetCaretBlink();
                    requestCaretReveal();
                }
            }
        }).setTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                if (event.isDefaultPrevented()) {
                    return false;
                }
                if (!focused || !enabled) {
                    return false;
                }
                return replaceSelection(DocumentTextAreaTextSupport.normalizeInput(event.getText()), true);
            }
        }).setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (!focused || !enabled) {
                    return false;
                }
                return handleKeyEvent(event);
            }
        }).setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (!enabled || event.getButton() != 0) {
                    return false;
                }
                if (event.getTarget() == element) {
                    collapseSelection(resolveCaretIndexAtDocumentPoint(event.getDocumentX(), event.getDocumentY()));
                    requestCaretReveal();
                    return true;
                }
                return false;
            }
        }).setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hovered = event.isHovered() && enabled;
                updateVisualState();
                return false;
            }
        });
    }

    private boolean handleKeyEvent(DocumentElementKeyEvent event) {
        int keyCode = event.getKeyCode();
        UiKeyEvent.Action action = event.getAction();
        boolean repeatable = action == UiKeyEvent.Action.PRESSED || action == UiKeyEvent.Action.REPEATED;
        if (event.isControlPressed() && keyCode == Keyboard.KEY_A && action == UiKeyEvent.Action.PRESSED) {
            selectionAnchorIndex = 0;
            caretIndex = textBuilder.length();
            preferredColumnCodePoints = -1;
            resetCaretBlink();
            requestCaretReveal();
            return true;
        }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && repeatable) {
            return replaceSelection("\n", true);
        }
        if (keyCode == Keyboard.KEY_BACK && repeatable) {
            preferredColumnCodePoints = -1;
            return deleteBackward();
        }
        if (keyCode == Keyboard.KEY_DELETE && repeatable) {
            preferredColumnCodePoints = -1;
            return deleteForward();
        }
        if (keyCode == Keyboard.KEY_LEFT && repeatable) {
            moveCaretHorizontally(-1, event.isShiftPressed());
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT && repeatable) {
            moveCaretHorizontally(1, event.isShiftPressed());
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME && repeatable) {
            moveCaretToLineBoundary(true, event.isShiftPressed());
            return true;
        }
        if (keyCode == Keyboard.KEY_END && repeatable) {
            moveCaretToLineBoundary(false, event.isShiftPressed());
            return true;
        }
        if (keyCode == Keyboard.KEY_UP && repeatable) {
            moveCaretVertically(-1, event.isShiftPressed());
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN && repeatable) {
            moveCaretVertically(1, event.isShiftPressed());
            return true;
        }
        return false;
    }

    private void syncContent() {
        rebuildLogicalLines();
        contentElement.clearChildren();
        boolean showingPlaceholder = textBuilder.length() == 0 && !placeholder.isEmpty();
        if (showingPlaceholder) {
            appendRenderedLine(0, placeholder, placeholderColor);
        } else {
            int resolvedTextColor = enabled ? textColor : disabledTextColor;
            for (int index = 0; index < logicalLines.size(); index++) {
                appendRenderedLine(index, logicalLines.get(index).text, resolvedTextColor);
            }
        }
        element.setAttribute("value", textBuilder.toString());
        if (placeholder.isEmpty()) {
            element.removeAttribute("placeholder");
        } else {
            element.setAttribute("placeholder", placeholder);
        }
    }

    private void appendRenderedLine(final int lineIndex, String lineText, int lineTextColor) {
        final ElementNode lineElement = element.getOwnerDocument().div();
        ElementNode textSpan = element.getOwnerDocument().span();
        TextNode textNode = textSpan.appendText(lineText == null ? "" : lineText);
        textNode.setTextContentMode(TextContentMode.UILIB_RAW);
        lineElement.style()
                .setDisplay(UiDisplay.BLOCK)
                .setMinHeight(DEFAULT_LINE_HEIGHT_LENGTH)
                .setLineHeight(DEFAULT_LINE_HEIGHT_LENGTH);
        textSpan.style()
                .setWhiteSpace(UiWhiteSpace.PRE_WRAP)
                .setOverflowWrap(UiOverflowWrap.ANYWHERE)
                .setTextColor(lineTextColor);
        lineElement.append(textSpan);
        lineElement.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (!enabled || event.getButton() != 0) {
                    return false;
                }
                setCaretFromLineClick(lineIndex, event.getDocumentX(), event.getDocumentY());
                return true;
            }
        });
        contentElement.append(lineElement);
    }

    private void rebuildLogicalLines() {
        logicalLines.clear();
        String text = textBuilder.toString();
        if (text.isEmpty()) {
            logicalLines.add(new LogicalLine(0, 0, ""));
            caretIndex = 0;
            selectionAnchorIndex = 0;
            return;
        }
        int lineStart = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) != '\n') {
                continue;
            }
            logicalLines.add(new LogicalLine(lineStart, index, text.substring(lineStart, index)));
            lineStart = index + 1;
        }
        logicalLines.add(new LogicalLine(lineStart, text.length(), text.substring(lineStart)));
        caretIndex = DocumentTextAreaTextSupport.normalizeCaretIndex(text, Math.min(caretIndex, text.length()));
        selectionAnchorIndex = DocumentTextAreaTextSupport.normalizeCaretIndex(text, Math.min(selectionAnchorIndex,
                text.length()));
    }

    private void updateVisualState() {
        int backgroundColor;
        int borderColor;
        if (!enabled) {
            backgroundColor = disabledBackgroundColor;
            borderColor = disabledBorderColor;
        } else if (focused) {
            backgroundColor = normalBackgroundColor;
            borderColor = focusBorderColor;
        } else if (hovered) {
            backgroundColor = normalBackgroundColor;
            borderColor = hoverBorderColor;
        } else {
            backgroundColor = normalBackgroundColor;
            borderColor = normalBorderColor;
        }
        element.style()
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setTextColor(enabled ? textColor : disabledTextColor)
                .setCursor(enabled ? UiCursor.TEXT : UiCursor.NOT_ALLOWED);
    }

    private boolean replaceSelection(String replacement, boolean fireChange) {
        String normalized = DocumentTextAreaTextSupport.normalizeInput(replacement);
        if (readOnly) {
            return false;
        }
        if (normalized.isEmpty() && !hasSelection()) {
            return false;
        }
        String currentText = textBuilder.toString();
        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();
        String prefix = currentText.substring(0, selectionStart);
        String suffix = currentText.substring(selectionEnd);
        int remainingCodePoints = maxLength - DocumentTextAreaTextSupport.countCodePoints(prefix)
                - DocumentTextAreaTextSupport.countCodePoints(suffix);
        String inserted = remainingCodePoints <= 0 ? ""
                : DocumentTextAreaTextSupport.truncateToMaxLength(normalized, remainingCodePoints);
        String nextText = prefix + inserted + suffix;
        if (nextText.equals(currentText)) {
            return false;
        }
        textBuilder.setLength(0);
        textBuilder.append(nextText);
        caretIndex = selectionStart + inserted.length();
        selectionAnchorIndex = caretIndex;
        preferredColumnCodePoints = -1;
        resetCaretBlink();
        syncContent();
        updateVisualState();
        requestCaretReveal();
        if (fireChange) {
            fireChange();
        }
        return true;
    }

    private boolean deleteBackward() {
        if (hasSelection()) {
            return replaceSelection("", true);
        }
        if (caretIndex <= 0) {
            return false;
        }
        String currentText = textBuilder.toString();
        int previousIndex = currentText.offsetByCodePoints(caretIndex, -1);
        selectionAnchorIndex = previousIndex;
        caretIndex = currentText.offsetByCodePoints(previousIndex, 1);
        return replaceSelection("", true);
    }

    private boolean deleteForward() {
        if (hasSelection()) {
            return replaceSelection("", true);
        }
        String currentText = textBuilder.toString();
        if (caretIndex >= currentText.length()) {
            return false;
        }
        int nextIndex = currentText.offsetByCodePoints(caretIndex, 1);
        selectionAnchorIndex = caretIndex;
        caretIndex = nextIndex;
        return replaceSelection("", true);
    }

    private void moveCaretHorizontally(int direction, boolean extendSelection) {
        preferredColumnCodePoints = -1;
        if (!extendSelection && hasSelection()) {
            collapseSelection(direction < 0 ? getSelectionStart() : getSelectionEnd());
            requestCaretReveal();
            return;
        }
        String currentText = textBuilder.toString();
        if (direction < 0 && caretIndex > 0) {
            moveCaretTo(currentText.offsetByCodePoints(caretIndex, -1), extendSelection);
            return;
        }
        if (direction > 0 && caretIndex < currentText.length()) {
            moveCaretTo(currentText.offsetByCodePoints(caretIndex, 1), extendSelection);
            return;
        }
        requestCaretReveal();
    }

    private void moveCaretToLineBoundary(boolean toStart, boolean extendSelection) {
        preferredColumnCodePoints = -1;
        VisualLineMetrics visualLine = resolveVisualLineMetricsForCaret(caretIndex);
        if (visualLine != null) {
            moveCaretTo(toStart ? visualLine.visualStartIndex : visualLine.visualEndIndex, extendSelection);
            return;
        }
        int lineIndex = resolveLineIndexForCaret(caretIndex);
        LogicalLine line = logicalLines.get(lineIndex);
        moveCaretTo(toStart ? line.startIndex : line.endIndex, extendSelection);
    }

    private void moveCaretVertically(int direction, boolean extendSelection) {
        if (!visualLineMetrics.isEmpty()) {
            moveCaretVerticallyByVisualLine(direction, extendSelection);
            return;
        }
        int currentLineIndex = resolveLineIndexForCaret(caretIndex);
        int targetLineIndex = Math.max(0, Math.min(logicalLines.size() - 1, currentLineIndex + direction));
        if (targetLineIndex == currentLineIndex && !extendSelection && hasSelection()) {
            collapseSelection(direction < 0 ? getSelectionStart() : getSelectionEnd());
            requestCaretReveal();
            return;
        }
        LogicalLine currentLine = logicalLines.get(currentLineIndex);
        LogicalLine targetLine = logicalLines.get(targetLineIndex);
        int currentOffset = Math.max(0, Math.min(caretIndex - currentLine.startIndex, currentLine.text.length()));
        int currentColumnCodePoints = currentLine.text.codePointCount(0, currentOffset);
        if (preferredColumnCodePoints < 0) {
            preferredColumnCodePoints = currentColumnCodePoints;
        }
        int targetColumnCodePoints = Math.min(preferredColumnCodePoints,
                targetLine.text.codePointCount(0, targetLine.text.length()));
        int targetOffset = targetColumnCodePoints <= 0 ? 0 : targetLine.text.offsetByCodePoints(0,
                targetColumnCodePoints);
        moveCaretTo(targetLine.startIndex + targetOffset, extendSelection);
    }

    private void moveCaretVerticallyByVisualLine(int direction, boolean extendSelection) {
        int currentVisualIndex = resolveVisualLineIndexForCaret(caretIndex);
        int targetVisualIndex = Math.max(0, Math.min(visualLineMetrics.size() - 1, currentVisualIndex + direction));
        if (targetVisualIndex == currentVisualIndex && !extendSelection && hasSelection()) {
            collapseSelection(direction < 0 ? getSelectionStart() : getSelectionEnd());
            requestCaretReveal();
            return;
        }
        VisualLineMetrics currentLine = visualLineMetrics.get(currentVisualIndex);
        VisualLineMetrics targetLine = visualLineMetrics.get(targetVisualIndex);
        int currentOffset = Math.max(0, Math.min(caretIndex - currentLine.visualStartIndex,
                currentLine.text.length()));
        int currentColumnCodePoints = currentLine.text.codePointCount(0, currentOffset);
        if (preferredColumnCodePoints < 0) {
            preferredColumnCodePoints = currentColumnCodePoints;
        }
        int targetColumnCodePoints = Math.min(preferredColumnCodePoints,
                targetLine.text.codePointCount(0, targetLine.text.length()));
        int targetOffset = targetColumnCodePoints <= 0 ? 0 : targetLine.text.offsetByCodePoints(0,
                targetColumnCodePoints);
        moveCaretTo(targetLine.visualStartIndex + targetOffset, extendSelection);
    }

    private void moveCaretTo(int nextCaretIndex, boolean extendSelection) {
        int resolvedCaretIndex = DocumentTextAreaTextSupport.normalizeCaretIndex(textBuilder.toString(), Math.max(0,
                Math.min(nextCaretIndex, textBuilder.length())));
        if (extendSelection) {
            caretIndex = resolvedCaretIndex;
        } else {
            caretIndex = resolvedCaretIndex;
            selectionAnchorIndex = resolvedCaretIndex;
        }
        resetCaretBlink();
        requestCaretReveal();
    }

    private void collapseSelection(int collapsedCaretIndex) {
        int resolvedCaretIndex = DocumentTextAreaTextSupport.normalizeCaretIndex(textBuilder.toString(), Math.max(0,
                Math.min(collapsedCaretIndex, textBuilder.length())));
        caretIndex = resolvedCaretIndex;
        selectionAnchorIndex = resolvedCaretIndex;
        preferredColumnCodePoints = -1;
        resetCaretBlink();
    }

    private void setCaretFromLineClick(int lineIndex, int documentX, int documentY) {
        int caretIndexAtPoint = visualLineMetrics.isEmpty()
                ? resolveCaretIndexOnLine(lineIndex, documentX)
                : resolveCaretIndexAtDocumentPoint(documentX, documentY);
        collapseSelection(caretIndexAtPoint);
        requestCaretReveal();
    }

    private void resetCaretBlink() {
        caretBlinkResetNanos = SystemDocumentAnimationClock.getInstance().getCurrentTimeNanos();
    }

    private void requestCaretReveal() {
        VisualLineMetrics visualLine = resolveVisualLineMetricsForCaret(caretIndex);
        int currentScrollLeft = element.getScrollLeft();
        int currentScrollTop = element.getScrollTop();
        int targetScrollLeft = currentScrollLeft;
        int targetScrollTop = currentScrollTop;
        int caretX = resolveCaretX(caretIndex);
        if (viewportContentWidth > 0 && !isSoftWrappedVisualLine(visualLine)) {
            int viewportLeft = currentScrollLeft;
            int viewportRight = currentScrollLeft + viewportContentWidth - 1;
            if (caretX < viewportLeft) {
                targetScrollLeft = caretX;
            } else if (caretX > viewportRight) {
                targetScrollLeft = caretX - Math.max(0, viewportContentWidth - 1);
            }
        }
        if (viewportContentHeight > 0) {
            int lineTop = visualLine == null ? resolveFallbackVisualTop(caretIndex) : visualLine.visualTop;
            int lineBottom = lineTop + DEFAULT_LINE_HEIGHT;
            int viewportTop = currentScrollTop;
            int viewportBottom = currentScrollTop + viewportContentHeight;
            if (lineTop < viewportTop) {
                targetScrollTop = lineTop;
            } else if (lineBottom > viewportBottom) {
                targetScrollTop = lineBottom - viewportContentHeight;
            }
        } else {
            int lineTop = visualLine == null ? resolveFallbackVisualTop(caretIndex) : visualLine.visualTop;
            targetScrollTop = Math.max(0, lineTop - DEFAULT_LINE_HEIGHT);
        }
        element.scrollTo(Math.max(0, targetScrollLeft), Math.max(0, targetScrollTop));
    }

    private boolean isSoftWrappedVisualLine(VisualLineMetrics visualLine) {
        if (visualLine == null) {
            return false;
        }
        if (visualLine.logicalLineIndex < 0 || visualLine.logicalLineIndex >= logicalLines.size()) {
            return false;
        }
        LogicalLine logicalLine = logicalLines.get(visualLine.logicalLineIndex);
        return visualLine.visualStartIndex > logicalLine.startIndex || visualLine.visualEndIndex < logicalLine.endIndex;
    }

    private int resolveFallbackVisualTop(int targetCaretIndex) {
        return resolveLineIndexForCaret(targetCaretIndex) * DEFAULT_LINE_HEIGHT;
    }

    private void updateRenderedLineMetrics(UiRenderContext context, ElementNode renderLayer, int contentLeft,
            int contentTop, int contentRight, int contentBottom) {
        DocumentElementBounds textBounds = contentElement.getDocumentBounds();
        DocumentElementBounds viewportBounds = element.getDocumentBounds();
        DocumentElementBounds layerBounds = renderLayer.getDocumentBounds();
        viewportScreenOffsetX = layerBounds.isAvailable() ? contentLeft - layerBounds.getContentLeft() : 0;
        viewportScreenOffsetY = layerBounds.isAvailable() ? contentTop - layerBounds.getContentTop() : 0;
        int fallbackDocumentContentLeft = contentLeft - viewportScreenOffsetX;
        int fallbackDocumentContentTop = contentTop - viewportScreenOffsetY;
        viewportContentLeft = textBounds.isAvailable() ? textBounds.getContentLeft() : fallbackDocumentContentLeft;
        viewportContentTop = textBounds.isAvailable() ? textBounds.getContentTop() : fallbackDocumentContentTop;
        viewportContentWidth = viewportBounds.isAvailable() ? viewportBounds.getContentWidth()
                : Math.max(0, contentRight - contentLeft);
        viewportContentHeight = viewportBounds.isAvailable() ? viewportBounds.getContentHeight()
                : Math.max(0, contentBottom - contentTop);
        visualLineMetrics = context == null ? Collections.<VisualLineMetrics>emptyList()
                : measureVisualLines(context);
    }

    private List<VisualLineMetrics> measureVisualLines(UiRenderContext context) {
        int availableWidth = Math.max(0, viewportContentWidth);
        List<VisualLineMetrics> measuredLines = new ArrayList<VisualLineMetrics>();
        int visualTop = 0;
        for (int lineIndex = 0; lineIndex < logicalLines.size(); lineIndex++) {
            LogicalLine logicalLine = logicalLines.get(lineIndex);
            List<VisualLineMetrics> lineMetrics = VisualLineMetrics.measure(logicalLine, lineIndex, visualTop,
                    availableWidth, context);
            measuredLines.addAll(lineMetrics);
            visualTop += lineMetrics.size() * DEFAULT_LINE_HEIGHT;
        }
        return measuredLines;
    }

    private void renderSelection(UiRenderContext context) {
        if (context == null || !hasSelection()) {
            return;
        }
        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();
        for (VisualLineMetrics lineMetrics : visualLineMetrics) {
            if (selectionEnd < lineMetrics.visualStartIndex || selectionStart > lineMetrics.visualEndIndex) {
                continue;
            }
            int localStart = Math.max(0, Math.min(lineMetrics.text.length(), selectionStart
                    - lineMetrics.visualStartIndex));
            int localEnd = Math.max(0, Math.min(lineMetrics.text.length(), selectionEnd
                    - lineMetrics.visualStartIndex));
            int startX = toScreenX(viewportContentLeft + lineMetrics.resolveBoundaryX(localStart));
            int endX = toScreenX(viewportContentLeft + lineMetrics.resolveBoundaryX(localEnd));
            if (startX == endX && lineMetrics.text.isEmpty()) {
                endX = startX + CLICK_EMPTY_LINE_HIGHLIGHT_WIDTH;
            }
            if (endX <= startX) {
                continue;
            }
            int selectionTop = toScreenY(viewportContentTop + lineMetrics.visualTop);
            context.fillRect(startX, selectionTop, endX, selectionTop + DEFAULT_LINE_HEIGHT, selectionColor);
        }
    }

    private void renderCaret(UiRenderContext context) {
        if (context == null || !focused || !enabled) {
            return;
        }
        long elapsed = SystemDocumentAnimationClock.getInstance().getCurrentTimeNanos() - caretBlinkResetNanos;
        if (elapsed < 0) {
            elapsed = 0;
        }
        if ((elapsed / BLINK_PERIOD_NANOS) % 2 != 0) {
            return;
        }
        VisualLineMetrics lineMetrics = resolveVisualLineMetricsForCaret(caretIndex);
        if (lineMetrics == null) {
            return;
        }
        int localOffset = Math.max(0, Math.min(lineMetrics.text.length(), caretIndex - lineMetrics.visualStartIndex));
        int cursorX = toScreenX(viewportContentLeft + lineMetrics.resolveBoundaryX(localOffset));
        int cursorTop = toScreenY(viewportContentTop + lineMetrics.visualTop);
        context.fillRect(cursorX, cursorTop, cursorX + DEFAULT_CARET_WIDTH, cursorTop + DEFAULT_LINE_HEIGHT,
                enabled ? caretColor : disabledTextColor);
    }

    private int toScreenX(int documentX) {
        return documentX + viewportScreenOffsetX;
    }

    private int toScreenY(int documentY) {
        return documentY + viewportScreenOffsetY;
    }

    private VisualLineMetrics resolveVisualLineMetricsForCaret(int targetCaretIndex) {
        if (visualLineMetrics.isEmpty()) {
            return null;
        }
        for (VisualLineMetrics visualLine : visualLineMetrics) {
            if (visualLine.containsCaretIndex(targetCaretIndex)) {
                return visualLine;
            }
        }
        return targetCaretIndex <= 0 ? visualLineMetrics.get(0) : visualLineMetrics.get(visualLineMetrics.size() - 1);
    }

    private int resolveVisualLineIndexForCaret(int targetCaretIndex) {
        for (int index = 0; index < visualLineMetrics.size(); index++) {
            if (visualLineMetrics.get(index).containsCaretIndex(targetCaretIndex)) {
                return index;
            }
        }
        return targetCaretIndex <= 0 ? 0 : Math.max(0, visualLineMetrics.size() - 1);
    }

    private int resolveLineIndexForCaret(int targetCaretIndex) {
        for (int index = 0; index < logicalLines.size(); index++) {
            LogicalLine line = logicalLines.get(index);
            if (targetCaretIndex >= line.startIndex && targetCaretIndex <= line.endIndex) {
                return index;
            }
        }
        return Math.max(0, logicalLines.size() - 1);
    }

    private int resolveCaretX(int targetCaretIndex) {
        VisualLineMetrics lineMetrics = resolveVisualLineMetricsForCaret(targetCaretIndex);
        if (lineMetrics == null || targetCaretIndex < lineMetrics.visualStartIndex
                || targetCaretIndex > lineMetrics.visualEndIndex) {
            LogicalLine line = logicalLines.get(resolveLineIndexForCaret(targetCaretIndex));
            int localOffset = Math.max(0, Math.min(line.text.length(), targetCaretIndex - line.startIndex));
            if (line.text.isEmpty()) {
                return 0;
            }
            return estimateCaretXFromLineText(line.text, localOffset);
        }
        int localOffset = Math.max(0, Math.min(lineMetrics.text.length(), targetCaretIndex
                - lineMetrics.visualStartIndex));
        return lineMetrics.resolveBoundaryX(localOffset);
    }

    private int estimateCaretXFromLineText(String text, int localOffset) {
        if (text == null || text.isEmpty() || viewportContentWidth <= 0) {
            return 0;
        }
        int estimatedLineWidth = element.getMaxScrollLeft() + viewportContentWidth;
        return Math.round(estimatedLineWidth * (localOffset / (float) text.length()));
    }

    private int resolveCaretIndexAtDocumentPoint(int documentX, int documentY) {
        if (visualLineMetrics.isEmpty()) {
            return textBuilder.length();
        }
        int localY = Math.max(0, documentY - viewportContentTop);
        int visualIndex = Math.max(0, Math.min(visualLineMetrics.size() - 1, localY / DEFAULT_LINE_HEIGHT));
        return resolveCaretIndexOnVisualLine(visualIndex, documentX);
    }

    private int resolveCaretIndexOnLine(int lineIndex, int documentX) {
        int safeLineIndex = Math.max(0, Math.min(lineIndex, logicalLines.size() - 1));
        VisualLineMetrics visualLine = findFirstVisualLineForLogicalLine(safeLineIndex);
        if (visualLine != null) {
            return visualLine.resolveClosestCaretIndex(documentX - viewportContentLeft);
        }
        return logicalLines.get(safeLineIndex).endIndex;
    }

    private int resolveCaretIndexOnVisualLine(int visualIndex, int documentX) {
        int safeVisualIndex = Math.max(0, Math.min(visualIndex, visualLineMetrics.size() - 1));
        VisualLineMetrics visualLine = visualLineMetrics.get(safeVisualIndex);
        return visualLine.resolveClosestCaretIndex(documentX - viewportContentLeft);
    }

    private VisualLineMetrics findFirstVisualLineForLogicalLine(int lineIndex) {
        for (VisualLineMetrics visualLine : visualLineMetrics) {
            if (visualLine.logicalLineIndex == lineIndex) {
                return visualLine;
            }
        }
        return null;
    }

    private boolean hasSelection() {
        return selectionAnchorIndex != caretIndex;
    }

    private int getSelectionStart() {
        return Math.min(selectionAnchorIndex, caretIndex);
    }

    private int getSelectionEnd() {
        return Math.max(selectionAnchorIndex, caretIndex);
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.onTextChanged(new DocumentTextAreaChangeEvent(this, element, textBuilder.toString()));
        }
    }

    private static final class LogicalLine {

        private final int startIndex;
        private final int endIndex;
        private final String text;

        private LogicalLine(int startIndex, int endIndex, String text) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.text = text == null ? "" : text;
        }
    }

    private static final class VisualLineMetrics {

        private final int logicalLineIndex;
        private final int visualTop;
        private final int visualStartIndex;
        private final int visualEndIndex;
        private final String text;
        private final int[] charOffsets;
        private final int[] boundaryXs;

        private VisualLineMetrics(int logicalLineIndex, int visualTop, int visualStartIndex, int visualEndIndex,
                String text, int[] charOffsets, int[] boundaryXs) {
            this.logicalLineIndex = logicalLineIndex;
            this.visualTop = visualTop;
            this.visualStartIndex = visualStartIndex;
            this.visualEndIndex = visualEndIndex;
            this.text = text;
            this.charOffsets = charOffsets;
            this.boundaryXs = boundaryXs;
        }

        private static List<VisualLineMetrics> measure(LogicalLine logicalLine, int lineIndex, int visualTop,
                int availableWidth, UiRenderContext context) {
            List<VisualLineMetrics> result = new ArrayList<VisualLineMetrics>();
            if (logicalLine.text.isEmpty()) {
                result.add(create(lineIndex, visualTop, logicalLine.startIndex, logicalLine.endIndex, "", context));
                return result;
            }
            int localStart = 0;
            while (localStart < logicalLine.text.length()) {
                int localEnd = resolveVisualLineEnd(logicalLine.text, localStart, availableWidth, context);
                String visualText = logicalLine.text.substring(localStart, localEnd);
                result.add(create(lineIndex, visualTop + result.size() * DEFAULT_LINE_HEIGHT,
                        logicalLine.startIndex + localStart, logicalLine.startIndex + localEnd, visualText, context));
                localStart = localEnd;
            }
            return result;
        }

        private static VisualLineMetrics create(int lineIndex, int visualTop, int visualStartIndex,
                int visualEndIndex, String text, UiRenderContext context) {
            int codePointCount = text.codePointCount(0, text.length());
            int[] charOffsets = new int[codePointCount + 1];
            int[] boundaryXs = new int[codePointCount + 1];
            charOffsets[0] = 0;
            boundaryXs[0] = 0;
            int currentOffset = 0;
            for (int codePointIndex = 1; codePointIndex <= codePointCount; codePointIndex++) {
                currentOffset = text.offsetByCodePoints(currentOffset, 1);
                charOffsets[codePointIndex] = currentOffset;
                boundaryXs[codePointIndex] = context.measureTextWidth(text.substring(0, currentOffset),
                        TextContentMode.UILIB_RAW);
            }
            return new VisualLineMetrics(lineIndex, visualTop, visualStartIndex, visualEndIndex, text, charOffsets,
                    boundaryXs);
        }

        private static int resolveVisualLineEnd(String text, int localStart, int availableWidth,
                UiRenderContext context) {
            if (availableWidth <= 0) {
                return text.length();
            }
            int currentOffset = localStart;
            int lastFittingOffset = localStart;
            while (currentOffset < text.length()) {
                int nextOffset = text.offsetByCodePoints(currentOffset, 1);
                int width = context.measureTextWidth(text.substring(localStart, nextOffset), TextContentMode.UILIB_RAW);
                if (width > availableWidth && lastFittingOffset > localStart) {
                    return lastFittingOffset;
                }
                if (width > availableWidth) {
                    return nextOffset;
                }
                lastFittingOffset = nextOffset;
                currentOffset = nextOffset;
            }
            return text.length();
        }

        private boolean containsCaretIndex(int targetCaretIndex) {
            if (visualStartIndex == visualEndIndex) {
                return targetCaretIndex == visualStartIndex;
            }
            return targetCaretIndex >= visualStartIndex && targetCaretIndex < visualEndIndex;
        }

        private int resolveBoundaryX(int localCharOffset) {
            int safeOffset = Math.max(0, Math.min(localCharOffset, text.length()));
            for (int index = 0; index < charOffsets.length; index++) {
                if (charOffsets[index] == safeOffset) {
                    return boundaryXs[index];
                }
            }
            return boundaryXs[boundaryXs.length - 1];
        }

        private int resolveClosestCaretIndex(int localX) {
            int closestIndex = 0;
            int closestDistance = Integer.MAX_VALUE;
            for (int index = 0; index < boundaryXs.length; index++) {
                int distance = Math.abs(boundaryXs[index] - localX);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestIndex = index;
                }
            }
            return visualStartIndex + charOffsets[closestIndex];
        }
    }
}
