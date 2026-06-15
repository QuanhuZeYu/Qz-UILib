package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.event.UiKeyCodes;
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
import club.heiqi.uilib.ui.text.layout.LogicalTextLine;
import club.heiqi.uilib.ui.text.layout.TextLayoutEngine;
import club.heiqi.uilib.ui.text.layout.TextMeasureFunction;
import club.heiqi.uilib.ui.text.layout.VisualLineLayout;

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
    private final List<LogicalTextLine> logicalLines = new ArrayList<LogicalTextLine>();
    private final TextLayoutEngine textLayoutEngine = new TextLayoutEngine();
    private List<VisualLineLayout> visualLineMetrics = Collections.emptyList();
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
        if (event.isControlPressed() && keyCode == UiKeyCodes.KEY_A && action == UiKeyEvent.Action.PRESSED) {
            selectionAnchorIndex = 0;
            caretIndex = textBuilder.length();
            preferredColumnCodePoints = -1;
            resetCaretBlink();
            requestCaretReveal();
            return true;
        }
        if ((keyCode == UiKeyCodes.KEY_RETURN || keyCode == UiKeyCodes.KEY_NUMPADENTER) && repeatable) {
            return replaceSelection("\n", true);
        }
        if (keyCode == UiKeyCodes.KEY_BACK && repeatable) {
            preferredColumnCodePoints = -1;
            return deleteBackward();
        }
        if (keyCode == UiKeyCodes.KEY_DELETE && repeatable) {
            preferredColumnCodePoints = -1;
            return deleteForward();
        }
        if (keyCode == UiKeyCodes.KEY_LEFT && repeatable) {
            moveCaretHorizontally(-1, event.isShiftPressed());
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_RIGHT && repeatable) {
            moveCaretHorizontally(1, event.isShiftPressed());
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_HOME && repeatable) {
            moveCaretToLineBoundary(true, event.isShiftPressed());
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_END && repeatable) {
            moveCaretToLineBoundary(false, event.isShiftPressed());
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_UP && repeatable) {
            moveCaretVertically(-1, event.isShiftPressed());
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_DOWN && repeatable) {
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
                appendRenderedLine(index, logicalLines.get(index).getText(), resolvedTextColor);
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
            logicalLines.add(new LogicalTextLine(0, 0, ""));
            caretIndex = 0;
            selectionAnchorIndex = 0;
            return;
        }
        int lineStart = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) != '\n') {
                continue;
            }
            logicalLines.add(new LogicalTextLine(lineStart, index, text.substring(lineStart, index)));
            lineStart = index + 1;
        }
        logicalLines.add(new LogicalTextLine(lineStart, text.length(), text.substring(lineStart)));
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
        VisualLineLayout visualLine = resolveVisualLineMetricsForCaret(caretIndex);
        if (visualLine != null) {
            moveCaretTo(toStart ? visualLine.getVisualStartIndex() : visualLine.getVisualEndIndex(), extendSelection);
            return;
        }
        int lineIndex = resolveLineIndexForCaret(caretIndex);
        LogicalTextLine line = logicalLines.get(lineIndex);
        moveCaretTo(toStart ? line.getStartIndex() : line.getEndIndex(), extendSelection);
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
        LogicalTextLine currentLine = logicalLines.get(currentLineIndex);
        LogicalTextLine targetLine = logicalLines.get(targetLineIndex);
        String currentText = currentLine.getText();
        String targetText = targetLine.getText();
        int currentOffset = Math.max(0, Math.min(caretIndex - currentLine.getStartIndex(), currentText.length()));
        int currentColumnCodePoints = currentText.codePointCount(0, currentOffset);
        if (preferredColumnCodePoints < 0) {
            preferredColumnCodePoints = currentColumnCodePoints;
        }
        int targetColumnCodePoints = Math.min(preferredColumnCodePoints,
                targetText.codePointCount(0, targetText.length()));
        int targetOffset = targetColumnCodePoints <= 0 ? 0 : targetText.offsetByCodePoints(0,
                targetColumnCodePoints);
        moveCaretTo(targetLine.getStartIndex() + targetOffset, extendSelection);
    }

    private void moveCaretVerticallyByVisualLine(int direction, boolean extendSelection) {
        int currentVisualIndex = resolveVisualLineIndexForCaret(caretIndex);
        int targetVisualIndex = Math.max(0, Math.min(visualLineMetrics.size() - 1, currentVisualIndex + direction));
        if (targetVisualIndex == currentVisualIndex && !extendSelection && hasSelection()) {
            collapseSelection(direction < 0 ? getSelectionStart() : getSelectionEnd());
            requestCaretReveal();
            return;
        }
        VisualLineLayout currentLine = visualLineMetrics.get(currentVisualIndex);
        VisualLineLayout targetLine = visualLineMetrics.get(targetVisualIndex);
        String currentText = currentLine.getText();
        String targetText = targetLine.getText();
        int currentOffset = Math.max(0, Math.min(caretIndex - currentLine.getVisualStartIndex(),
                currentText.length()));
        int currentColumnCodePoints = currentText.codePointCount(0, currentOffset);
        if (preferredColumnCodePoints < 0) {
            preferredColumnCodePoints = currentColumnCodePoints;
        }
        int targetColumnCodePoints = Math.min(preferredColumnCodePoints,
                targetText.codePointCount(0, targetText.length()));
        int targetOffset = targetColumnCodePoints <= 0 ? 0 : targetText.offsetByCodePoints(0,
                targetColumnCodePoints);
        moveCaretTo(targetLine.getVisualStartIndex() + targetOffset, extendSelection);
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
        VisualLineLayout visualLine = resolveVisualLineMetricsForCaret(caretIndex);
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
            int lineTop = visualLine == null ? resolveFallbackVisualTop(caretIndex) : visualLine.getVisualTop();
            int lineBottom = lineTop + DEFAULT_LINE_HEIGHT;
            int viewportTop = currentScrollTop;
            int viewportBottom = currentScrollTop + viewportContentHeight;
            if (lineTop < viewportTop) {
                targetScrollTop = lineTop;
            } else if (lineBottom > viewportBottom) {
                targetScrollTop = lineBottom - viewportContentHeight;
            }
        } else {
            int lineTop = visualLine == null ? resolveFallbackVisualTop(caretIndex) : visualLine.getVisualTop();
            targetScrollTop = Math.max(0, lineTop - DEFAULT_LINE_HEIGHT);
        }
        element.scrollTo(Math.max(0, targetScrollLeft), Math.max(0, targetScrollTop));
    }

    private boolean isSoftWrappedVisualLine(VisualLineLayout visualLine) {
        if (visualLine == null) {
            return false;
        }
        if (visualLine.getLogicalLineIndex() < 0 || visualLine.getLogicalLineIndex() >= logicalLines.size()) {
            return false;
        }
        LogicalTextLine logicalLine = logicalLines.get(visualLine.getLogicalLineIndex());
        return visualLine.getVisualStartIndex() > logicalLine.getStartIndex()
                || visualLine.getVisualEndIndex() < logicalLine.getEndIndex();
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
        visualLineMetrics = context == null ? Collections.<VisualLineLayout>emptyList()
                : measureVisualLines(context);
    }

    /**
     * 通过共享布局引擎计算视觉行。
     *
     * <p>引擎按“逻辑行内容 + 可用宽度 + 字体测量纪元”缓存，稳态下（仅 caret 闪烁、滚动、选区移动）
     * 直接复用上一帧结果，不再触发任何文本测量；selection 层与 caret 层共享同一次结果。</p>
     *
     * @param context 渲染上下文
     * @return 视觉行布局列表
     */
    private List<VisualLineLayout> measureVisualLines(UiRenderContext context) {
        int availableWidth = Math.max(0, viewportContentWidth);
        TextMeasureFunction measure = new TextMeasureFunction() {
            @Override
            public int widthOf(String text) {
                return context.measureTextWidth(text, TextContentMode.UILIB_RAW);
            }

            @Override
            public int[] prefixWidths(String text) {
                return context.measurePrefixWidths(text);
            }
        };
        return textLayoutEngine.layout(logicalLines, availableWidth, context.getTextMeasureEpoch(), DEFAULT_LINE_HEIGHT,
                true, measure);
    }

    private void renderSelection(UiRenderContext context) {
        if (context == null || !hasSelection()) {
            return;
        }
        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();
        for (VisualLineLayout lineMetrics : visualLineMetrics) {
            if (selectionEnd < lineMetrics.getVisualStartIndex() || selectionStart > lineMetrics.getVisualEndIndex()) {
                continue;
            }
            int localStart = Math.max(0, Math.min(lineMetrics.getText().length(), selectionStart
                    - lineMetrics.getVisualStartIndex()));
            int localEnd = Math.max(0, Math.min(lineMetrics.getText().length(), selectionEnd
                    - lineMetrics.getVisualStartIndex()));
            int startX = toScreenX(viewportContentLeft + lineMetrics.resolveBoundaryX(localStart));
            int endX = toScreenX(viewportContentLeft + lineMetrics.resolveBoundaryX(localEnd));
            if (startX == endX && lineMetrics.getText().isEmpty()) {
                endX = startX + CLICK_EMPTY_LINE_HIGHLIGHT_WIDTH;
            }
            if (endX <= startX) {
                continue;
            }
            int selectionTop = toScreenY(viewportContentTop + lineMetrics.getVisualTop());
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
        VisualLineLayout lineMetrics = resolveVisualLineMetricsForCaret(caretIndex);
        if (lineMetrics == null) {
            return;
        }
        int localOffset = Math.max(0, Math.min(lineMetrics.getText().length(),
                caretIndex - lineMetrics.getVisualStartIndex()));
        int cursorX = toScreenX(viewportContentLeft + lineMetrics.resolveBoundaryX(localOffset));
        int cursorTop = toScreenY(viewportContentTop + lineMetrics.getVisualTop());
        context.fillRect(cursorX, cursorTop, cursorX + DEFAULT_CARET_WIDTH, cursorTop + DEFAULT_LINE_HEIGHT,
                enabled ? caretColor : disabledTextColor);
    }

    private int toScreenX(int documentX) {
        return documentX + viewportScreenOffsetX;
    }

    private int toScreenY(int documentY) {
        return documentY + viewportScreenOffsetY;
    }

    private VisualLineLayout resolveVisualLineMetricsForCaret(int targetCaretIndex) {
        if (visualLineMetrics.isEmpty()) {
            return null;
        }
        for (VisualLineLayout visualLine : visualLineMetrics) {
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
            LogicalTextLine line = logicalLines.get(index);
            if (targetCaretIndex >= line.getStartIndex() && targetCaretIndex <= line.getEndIndex()) {
                return index;
            }
        }
        return Math.max(0, logicalLines.size() - 1);
    }

    private int resolveCaretX(int targetCaretIndex) {
        VisualLineLayout lineMetrics = resolveVisualLineMetricsForCaret(targetCaretIndex);
        if (lineMetrics == null || targetCaretIndex < lineMetrics.getVisualStartIndex()
                || targetCaretIndex > lineMetrics.getVisualEndIndex()) {
            LogicalTextLine line = logicalLines.get(resolveLineIndexForCaret(targetCaretIndex));
            String lineText = line.getText();
            int localOffset = Math.max(0, Math.min(lineText.length(), targetCaretIndex - line.getStartIndex()));
            if (lineText.isEmpty()) {
                return 0;
            }
            return estimateCaretXFromLineText(lineText, localOffset);
        }
        int localOffset = Math.max(0, Math.min(lineMetrics.getText().length(), targetCaretIndex
                - lineMetrics.getVisualStartIndex()));
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
        VisualLineLayout visualLine = findFirstVisualLineForLogicalLine(safeLineIndex);
        if (visualLine != null) {
            return visualLine.resolveClosestCaretIndex(documentX - viewportContentLeft);
        }
        return logicalLines.get(safeLineIndex).getEndIndex();
    }

    private int resolveCaretIndexOnVisualLine(int visualIndex, int documentX) {
        int safeVisualIndex = Math.max(0, Math.min(visualIndex, visualLineMetrics.size() - 1));
        VisualLineLayout visualLine = visualLineMetrics.get(safeVisualIndex);
        return visualLine.resolveClosestCaretIndex(documentX - viewportContentLeft);
    }

    private VisualLineLayout findFirstVisualLineForLogicalLine(int lineIndex) {
        for (VisualLineLayout visualLine : visualLineMetrics) {
            if (visualLine.getLogicalLineIndex() == lineIndex) {
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
}
