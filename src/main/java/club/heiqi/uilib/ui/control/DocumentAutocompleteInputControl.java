package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentTopLayerDetachHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的输入框自动完成控件。
 *
 * <p>控件主体复用单行文本输入框语义，候选面板由调用方提供数据并以内置 top-layer 弹层展示，
 * 可用于字体名补全、配置项输入和业务表单输入等场景。</p>
 */
public final class DocumentAutocompleteInputControl {

    private static final int DEFAULT_OPTION_HEIGHT = 28;
    private static final int DEFAULT_MAX_VISIBLE_OPTIONS = 6;
    private static final int DEFAULT_MAX_SUGGESTION_COUNT = 128;
    private static final String PRESERVE_FOCUS_ON_MOUSE_DOWN_ATTRIBUTE = "data-qz-preserve-focus-on-mousedown";
    private static final String ANCHORED_TOP_LAYER_LISTBOX_ATTRIBUTE = "data-qz-anchored-listbox";

    private final UiDocument document;
    private final DocumentTextInputControl textInputControl;
    private final ElementNode element;
    private final ElementNode popupElement;
    private final List<String> options = new ArrayList<String>();
    private final List<String> suggestions = new ArrayList<String>();
    private final List<ElementNode> suggestionElements = new ArrayList<ElementNode>();
    private final DocumentElementFocusHandler baseFocusHandler;
    private final DocumentAutocompleteSuggestionProvider optionsSuggestionProvider;
    private DocumentAutocompleteSuggestionProvider suggestionProvider;
    private DocumentAutocompleteInputChangeHandler changeHandler;
    private DocumentAutocompleteSelectionHandler selectionHandler;
    private DocumentElementKeyHandler keyHandler;
    private boolean showAllWhenQueryEmpty = true;
    private boolean enabled = true;
    private boolean focused;
    private boolean open;
    private int highlightedIndex = -1;
    private int maxVisibleOptions = DEFAULT_MAX_VISIBLE_OPTIONS;
    private int maxSuggestionCount = DEFAULT_MAX_SUGGESTION_COUNT;
    private int popupBackgroundColor = 0xFF161625;
    private int popupBorderColor = 0xFF555577;
    private int optionBackgroundColor = 0xFF2A2A3A;
    private int highlightedOptionBackgroundColor = 0xFF2563EB;
    private int optionTextColor = 0xFFAAAAEE;
    private int highlightedOptionTextColor = 0xFFEEEEFF;
    private int disabledBackgroundColor = 0xFF333344;
    private int disabledTextColor = 0xFF666677;

    /**
     * 创建空候选的自动完成输入框。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentAutocompleteInputControl(UiDocument document) {
        this(document, new String[0]);
    }

    /**
     * 使用固定候选创建自动完成输入框。
     *
     * @param document 所属 HTML-like 文档
     * @param options 固定候选项
     */
    public DocumentAutocompleteInputControl(UiDocument document, String... options) {
        this.document = document;
        this.textInputControl = new DocumentTextInputControl(document);
        this.element = textInputControl.getElement();
        this.popupElement = document.div();
        this.baseFocusHandler = element.getFocusHandler();
        this.optionsSuggestionProvider = new DocumentAutocompleteSuggestionProvider() {
            @Override
            public List<String> getSuggestions(String query) {
                return filterOptions(query);
            }
        };
        this.suggestionProvider = optionsSuggestionProvider;
        setOptions(options);
        configureElement();
        configurePopup();
        installHandlers();
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
        return textInputControl.getText();
    }

    /**
     * 设置文本内容，程序化设置默认不触发事件。
     *
     * @param text 文本内容；为 null 时清空
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setText(String text) {
        textInputControl.setText(text);
        refreshSuggestions(false);
        return this;
    }

    /**
     * 设置占位文本。
     *
     * @param placeholder 占位文本；为 null 时清空
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setPlaceholder(String placeholder) {
        textInputControl.setPlaceholder(placeholder);
        return this;
    }

    /**
     * 设置最大输入长度。
     *
     * @param maxLength 最大输入长度
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setMaxLength(int maxLength) {
        textInputControl.setMaxLength(maxLength);
        return this;
    }

    /**
     * 设置控件是否启用。
     *
     * @param enabled 是否启用
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        if (!enabled) {
            focused = false;
            setOpen(false);
        }
        textInputControl.setEnabled(enabled);
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
     * 判断输入框当前是否聚焦。
     *
     * @return 是否聚焦
     */
    public boolean isFocused() {
        return focused;
    }

    /**
     * 判断候选面板当前是否展开。
     *
     * @return 是否展开
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * 返回当前候选数量。
     *
     * @return 候选数量
     */
    public int getSuggestionCount() {
        return suggestions.size();
    }

    /**
     * 返回当前键盘高亮候选索引。
     *
     * @return 高亮索引；无高亮时为 -1
     */
    public int getHighlightedIndex() {
        return highlightedIndex;
    }

    /**
     * 使用固定候选项，并恢复内置大小写不敏感包含过滤策略。
     *
     * @param options 固定候选项
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setOptions(String... options) {
        this.options.clear();
        if (options != null) {
            for (int index = 0; index < options.length; index++) {
                this.options.add(normalizeSuggestion(options[index]));
            }
        }
        this.suggestionProvider = optionsSuggestionProvider;
        refreshSuggestions(false);
        return this;
    }

    /**
     * 设置自定义候选提供器。
     *
     * @param suggestionProvider 候选提供器；为 null 时清空候选
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setSuggestionProvider(
            DocumentAutocompleteSuggestionProvider suggestionProvider) {
        this.suggestionProvider = suggestionProvider;
        refreshSuggestions(false);
        return this;
    }

    /**
     * 设置空查询时是否展示全部候选。
     *
     * @param showAllWhenQueryEmpty 是否展示全部候选
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setShowAllWhenQueryEmpty(boolean showAllWhenQueryEmpty) {
        if (this.showAllWhenQueryEmpty == showAllWhenQueryEmpty) {
            return this;
        }
        this.showAllWhenQueryEmpty = showAllWhenQueryEmpty;
        refreshSuggestions(false);
        return this;
    }

    /**
     * 判断空查询时是否展示全部候选。
     *
     * @return 是否展示全部候选
     */
    public boolean isShowAllWhenQueryEmpty() {
        return showAllWhenQueryEmpty;
    }

    /**
     * 设置候选面板最多可见的行数，超出后通过滚动查看。
     *
     * @param maxVisibleOptions 最大可见行数
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setMaxVisibleOptions(int maxVisibleOptions) {
        this.maxVisibleOptions = Math.max(1, maxVisibleOptions);
        popupElement.style().setMaxHeight(UiStyleLength.px(DEFAULT_OPTION_HEIGHT * this.maxVisibleOptions));
        revealHighlightedOption();
        return this;
    }

    /**
     * 设置单次刷新最多保留的候选数量。
     *
     * <p>候选提供器应按相关度返回结果，本控件只渲染前 N 项，避免输入事件中同步创建过多节点。</p>
     *
     * @param maxSuggestionCount 最大候选数量
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setMaxSuggestionCount(int maxSuggestionCount) {
        this.maxSuggestionCount = Math.max(1, maxSuggestionCount);
        refreshSuggestions(false);
        return this;
    }

    /**
     * 返回单次刷新最多保留的候选数量。
     *
     * @return 最大候选数量
     */
    public int getMaxSuggestionCount() {
        return maxSuggestionCount;
    }

    /**
     * 设置文本变更处理器。
     *
     * @param changeHandler 文本变更处理器；为 null 时清除
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setChangeHandler(DocumentAutocompleteInputChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置候选选择处理器。
     *
     * @param selectionHandler 候选选择处理器；为 null 时清除
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setSelectionHandler(
            DocumentAutocompleteSelectionHandler selectionHandler) {
        this.selectionHandler = selectionHandler;
        return this;
    }

    /**
     * 设置扩展键盘处理器。
     *
     * <p>自动完成自身未消费按键时，会继续调用该处理器。</p>
     *
     * @param keyHandler 键盘处理器；为 null 时清除
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setKeyHandler(DocumentElementKeyHandler keyHandler) {
        this.keyHandler = keyHandler;
        return this;
    }

    /**
     * 设置输入框正常态背景色。
     *
     * @param normalBackgroundColor 正常态背景色
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setNormalBackgroundColor(int normalBackgroundColor) {
        textInputControl.setNormalBackgroundColor(normalBackgroundColor);
        return this;
    }

    /**
     * 设置输入框正常态边框色。
     *
     * @param normalBorderColor 正常态边框色
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setNormalBorderColor(int normalBorderColor) {
        textInputControl.setNormalBorderColor(normalBorderColor);
        return this;
    }

    /**
     * 设置输入框聚焦态边框色。
     *
     * @param focusBorderColor 聚焦态边框色
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setFocusBorderColor(int focusBorderColor) {
        textInputControl.setFocusBorderColor(focusBorderColor);
        return this;
    }

    /**
     * 设置输入框文本颜色。
     *
     * @param textColor 普通文本颜色
     * @param placeholderColor 占位文本颜色
     * @param disabledTextColor 禁用文本颜色
     * @return 当前控件
     */
    public DocumentAutocompleteInputControl setTextColors(int textColor, int placeholderColor,
            int disabledTextColor) {
        textInputControl.setTextColors(textColor, placeholderColor, disabledTextColor);
        return this;
    }

    private void configureElement() {
        element.setAttribute("aria-autocomplete", "list");
        element.setAttribute("aria-haspopup", "listbox");
        element.setAttribute("aria-expanded", "false");
    }

    private void configurePopup() {
        popupElement.setAttribute("role", "listbox");
        popupElement.setAttribute(PRESERVE_FOCUS_ON_MOUSE_DOWN_ATTRIBUTE, "true");
        popupElement.setAttribute(ANCHORED_TOP_LAYER_LISTBOX_ATTRIBUTE, "true");
        popupElement.__setTopLayerDetachHandler(new DocumentTopLayerDetachHandler() {
            @Override
            public void onTopLayerDetached(ElementNode topLayerElement) {
                closeFromTopLayerDetach();
            }
        });
        popupElement.style()
                .setDisplay(UiDisplay.NONE)
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.percent(1.0F))
                .setWidth(UiStyleLength.percent(1.0F))
                .setMaxHeight(UiStyleLength.px(DEFAULT_OPTION_HEIGHT * maxVisibleOptions))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setBackgroundColor(popupBackgroundColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(4))
                .setZIndex(20);
        element.append(popupElement);
    }

    private void installHandlers() {
        textInputControl.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                refreshSuggestions(true);
                fireChange();
            }
        });
        textInputControl.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (handleKeyEvent(event)) {
                    return true;
                }
                return keyHandler != null && keyHandler.onKey(event);
            }
        });
        element.setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                if (baseFocusHandler != null) {
                    baseFocusHandler.onFocusChanged(event);
                }
                focused = event.isFocused() && enabled;
                if (focused) {
                    refreshSuggestions(true);
                } else {
                    setOpen(false);
                }
            }
        });
        element.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (!enabled || event.getButton() != 0 || isElementWithinPopup(event.getTarget())) {
                    return false;
                }
                refreshSuggestions(true);
                return false;
            }
        });
    }

    private boolean handleKeyEvent(DocumentElementKeyEvent event) {
        if (!enabled || event == null) {
            return false;
        }
        int keyCode = event.getKeyCode();
        UiKeyEvent.Action action = event.getAction();
        boolean repeatable = action == UiKeyEvent.Action.PRESSED || action == UiKeyEvent.Action.REPEATED;
        if (keyCode == Keyboard.KEY_ESCAPE && action == UiKeyEvent.Action.PRESSED && open) {
            setOpen(false);
            return true;
        }
        if (isEnterKey(keyCode) && action == UiKeyEvent.Action.PRESSED) {
            if (!open) {
                refreshSuggestions(true);
                return isOpen();
            }
            if (highlightedIndex >= 0 && highlightedIndex < suggestions.size()) {
                selectSuggestion(highlightedIndex, true, keyCode, -1, event.getTimeNanos());
                return true;
            }
            setOpen(false);
            return true;
        }
        if (!repeatable) {
            return false;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            if (!open) {
                refreshSuggestions(true);
                highlightedIndex = suggestions.isEmpty() ? -1 : 0;
            } else {
                moveHighlight(1);
            }
            return !suggestions.isEmpty();
        }
        if (keyCode == Keyboard.KEY_UP) {
            if (!open) {
                refreshSuggestions(true);
                highlightedIndex = suggestions.isEmpty() ? -1 : suggestions.size() - 1;
                updateVisualState();
                revealHighlightedOption();
            } else {
                moveHighlight(-1);
            }
            return !suggestions.isEmpty();
        }
        return false;
    }

    private void refreshSuggestions(boolean requestOpen) {
        List<String> nextSuggestions = resolveSuggestions(textInputControl.getText());
        suggestions.clear();
        suggestions.addAll(nextSuggestions);
        rebuildSuggestionElements();
        if (!enabled || !focused || suggestions.isEmpty()) {
            setOpen(false);
            return;
        }
        if (requestOpen || open) {
            setOpen(true);
        } else {
            updateVisualState();
        }
    }

    private List<String> resolveSuggestions(String query) {
        String normalizedQuery = query == null ? "" : query;
        if (normalizedQuery.isEmpty() && !showAllWhenQueryEmpty) {
            return Collections.emptyList();
        }
        if (suggestionProvider == null) {
            return Collections.emptyList();
        }
        List<String> provided = suggestionProvider.getSuggestions(normalizedQuery);
        if (provided == null || provided.isEmpty()) {
            return Collections.emptyList();
        }
        int limit = Math.min(provided.size(), maxSuggestionCount);
        List<String> normalized = new ArrayList<String>(limit);
        for (int index = 0; index < limit; index++) {
            normalized.add(normalizeSuggestion(provided.get(index)));
        }
        return normalized;
    }

    private List<String> filterOptions(String query) {
        String normalizedQuery = query == null ? "" : query;
        if (normalizedQuery.isEmpty()) {
            if (!showAllWhenQueryEmpty) {
                return Collections.emptyList();
            }
            int limit = Math.min(options.size(), maxSuggestionCount);
            List<String> allOptions = new ArrayList<String>(limit);
            for (int index = 0; index < limit; index++) {
                allOptions.add(options.get(index));
            }
            return allOptions;
        }
        String lowerQuery = normalizedQuery.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<String>();
        for (int index = 0; index < options.size(); index++) {
            String option = options.get(index);
            if (option.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                filtered.add(option);
                if (filtered.size() >= maxSuggestionCount) {
                    break;
                }
            }
        }
        return filtered;
    }

    private void rebuildSuggestionElements() {
        popupElement.clearChildren();
        suggestionElements.clear();
        for (int index = 0; index < suggestions.size(); index++) {
            final int suggestionIndex = index;
            ElementNode option = document.option();
            option.appendText(suggestions.get(index));
            option.setAttribute("role", "option");
            option.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.ROW)
                    .setAlignItems(UiAlignItems.CENTER)
                    .setJustifyContent(UiJustifyContent.START)
                    .setWidth(UiStyleLength.percent(1.0F))
                    .setHeight(UiStyleLength.px(DEFAULT_OPTION_HEIGHT))
                    .setCursor(UiCursor.POINTER)
                    .setPadding(UiStyleLength.px(8));
            option.setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    if (!enabled || event.getButton() != 0) {
                        return false;
                    }
                    selectSuggestion(suggestionIndex, false, 0, event.getButton(), event.getTimeNanos());
                    return true;
                }
            });
            suggestionElements.add(option);
            popupElement.append(option);
        }
        if (suggestions.isEmpty()) {
            highlightedIndex = -1;
        } else if (highlightedIndex < 0 || highlightedIndex >= suggestions.size()) {
            highlightedIndex = 0;
        }
    }

    private void moveHighlight(int delta) {
        if (suggestions.isEmpty()) {
            highlightedIndex = -1;
            updateVisualState();
            return;
        }
        int nextIndex = highlightedIndex;
        if (nextIndex < 0 || nextIndex >= suggestions.size()) {
            nextIndex = delta < 0 ? suggestions.size() - 1 : 0;
        } else {
            nextIndex += delta;
            if (nextIndex < 0) {
                nextIndex = 0;
            }
            if (nextIndex >= suggestions.size()) {
                nextIndex = suggestions.size() - 1;
            }
        }
        highlightedIndex = nextIndex;
        updateVisualState();
        revealHighlightedOption();
    }

    private void selectSuggestion(int suggestionIndex, boolean keyboardTriggered, int keyCode, int button,
            long timeNanos) {
        if (suggestionIndex < 0 || suggestionIndex >= suggestions.size()) {
            return;
        }
        String previousQuery = textInputControl.getText();
        String suggestion = suggestions.get(suggestionIndex);
        boolean changed = !suggestion.equals(previousQuery);
        textInputControl.setText(suggestion);
        setOpen(false);
        refreshSuggestions(false);
        if (changed) {
            fireChange();
        }
        fireSelection(suggestionIndex, suggestion, previousQuery, keyboardTriggered, keyCode, button, timeNanos);
    }

    private void setOpen(boolean open) {
        boolean resolvedOpen = enabled && focused && open && !suggestions.isEmpty();
        if (this.open == resolvedOpen) {
            updateVisualState();
            if (this.open) {
                revealHighlightedOption();
            }
            return;
        }
        this.open = resolvedOpen;
        if (!this.open) {
            restorePopupInlinePlacement();
        }
        updateVisualState();
        if (this.open) {
            revealHighlightedOption();
        }
    }

    private void closeFromTopLayerDetach() {
        open = false;
        restorePopupInlinePlacement();
        updateVisualState();
    }

    private void updateVisualState() {
        if (open && !syncPopupTopLayerPlacement()) {
            open = false;
        }
        popupElement.style()
                .setDisplay(open ? UiDisplay.FLEX : UiDisplay.NONE)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setBackgroundColor(enabled ? popupBackgroundColor : disabledBackgroundColor)
                .setBorderColor(popupBorderColor);
        element.setAttribute("aria-expanded", String.valueOf(open));
        for (int index = 0; index < suggestionElements.size(); index++) {
            ElementNode option = suggestionElements.get(index);
            boolean highlighted = index == highlightedIndex;
            option.setAttribute("aria-selected", String.valueOf(highlighted));
            option.style()
                    .setBackgroundColor(enabled ? (highlighted ? highlightedOptionBackgroundColor
                            : optionBackgroundColor) : disabledBackgroundColor)
                    .setCursor(enabled ? UiCursor.POINTER : UiCursor.NOT_ALLOWED)
                    .setTextColor(enabled ? (highlighted ? highlightedOptionTextColor : optionTextColor)
                            : disabledTextColor);
        }
    }

    /**
     * 将候选面板注册到文档运行时顶层，避免被普通祖先 overflow 裁剪。
     *
     * @return 是否成功完成顶层放置
     */
    private boolean syncPopupTopLayerPlacement() {
        if (!open) {
            return false;
        }
        DocumentElementBounds bounds = element.__getVisualDocumentBounds();
        if (!bounds.isAvailable() || bounds.getWidth() <= 0) {
            restorePopupInlinePlacement();
            return false;
        }
        popupElement.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(bounds.getLeft()))
                .setTop(UiStyleLength.px(bounds.getTop() + bounds.getHeight()))
                .setWidth(UiStyleLength.px(bounds.getWidth()))
                .clearZIndex();
        element.getOwnerDocument().__showTopLayerElement(popupElement);
        return true;
    }

    private void restorePopupInlinePlacement() {
        element.getOwnerDocument().__hideTopLayerElement(popupElement);
        if (popupElement.getParent() != element) {
            element.append(popupElement);
        }
        popupElement.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.percent(1.0F))
                .setWidth(UiStyleLength.percent(1.0F))
                .setZIndex(20);
    }

    private void revealHighlightedOption() {
        if (highlightedIndex < 0 || highlightedIndex >= suggestionElements.size()) {
            return;
        }
        int currentScrollTop = popupElement.getScrollTop();
        int viewportHeight = DEFAULT_OPTION_HEIGHT * maxVisibleOptions;
        int optionTop = highlightedIndex * DEFAULT_OPTION_HEIGHT;
        int optionBottom = optionTop + DEFAULT_OPTION_HEIGHT;
        int targetScrollTop = currentScrollTop;
        if (optionTop < currentScrollTop) {
            targetScrollTop = optionTop;
        } else if (optionBottom > currentScrollTop + viewportHeight) {
            targetScrollTop = optionBottom - viewportHeight;
        }
        popupElement.scrollTo(popupElement.getScrollLeft(), Math.max(0, targetScrollTop));
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.onTextChanged(new DocumentAutocompleteInputChangeEvent(this, element,
                    textInputControl.getText()));
        }
    }

    private void fireSelection(int selectedIndex, String suggestion, String query, boolean keyboardTriggered,
            int keyCode, int button, long timeNanos) {
        if (selectionHandler != null) {
            selectionHandler.onSuggestionSelected(new DocumentAutocompleteSelectionEvent(this, element, selectedIndex,
                    suggestion, query, keyboardTriggered, keyCode, button, timeNanos));
        }
    }

    private boolean isElementWithinPopup(ElementNode target) {
        for (DocumentNode current = target; current != null; current = current.getParent()) {
            if (current == popupElement) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEnterKey(int keyCode) {
        return keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER;
    }

    private static String normalizeSuggestion(String suggestion) {
        return suggestion == null ? "" : suggestion;
    }
}
