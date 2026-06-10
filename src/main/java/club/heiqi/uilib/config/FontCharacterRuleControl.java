package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import club.heiqi.uilib.font.config.FontCharacterRule;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.control.DocumentAutocompleteInputChangeEvent;
import club.heiqi.uilib.ui.control.DocumentAutocompleteInputChangeHandler;
import club.heiqi.uilib.ui.control.DocumentAutocompleteInputControl;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.control.DocumentToggleChangeEvent;
import club.heiqi.uilib.ui.control.DocumentToggleChangeHandler;
import club.heiqi.uilib.ui.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 字符字体覆盖规则配置控件。
 */
final class FontCharacterRuleControl {

    private static final int MAX_FONT_SUGGESTION_COUNT = 128;

    private final UiDocument document;
    private final FontCharacterRuleChangeListener changeListener;
    private final List<RuleDraft> drafts = new ArrayList<RuleDraft>();
    private final ElementNode rootElement;
    private final ElementNode listElement;
    private final TextNode stateText;
    private final DocumentTextInputControl selectorInput;
    private final DocumentAutocompleteInputControl fontInput;

    /**
     * 创建字符字体覆盖规则控件。
     *
     * @param document 所属文档
     * @param initialRules 初始规则
     * @param changeListener 变更监听器
     */
    FontCharacterRuleControl(UiDocument document, List<String> initialRules,
            FontCharacterRuleChangeListener changeListener) {
        this.document = document;
        this.changeListener = changeListener;
        this.rootElement = document.div();
        configureRoot(rootElement);

        ElementNode toolbar = document.div();
        configureToolbar(toolbar);
        this.selectorInput = createToolbarInput("字符、U+E000 或 A-Z", 120);
        this.fontInput = createFontInput("字体名", 180);
        toolbar.append(selectorInput.getElement());
        toolbar.append(fontInput.getElement());
        toolbar.append(createActionButton("新增", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                addRuleFromToolbar();
            }
        }).getElement());
        toolbar.append(createPresetButton("中文", "一-龥").getElement());
        toolbar.append(createPresetButton("数字", "0-9").getElement());
        toolbar.append(createPresetButton("私用区", "U+E000-U+F8FF").getElement());
        this.stateText = toolbar.appendText("");
        rootElement.append(toolbar);

        this.listElement = document.div();
        configureList(listElement);
        rootElement.append(listElement);
        setRules(initialRules);
    }

    /**
     * 返回控件根元素。
     *
     * @return 根元素
     */
    ElementNode getElement() {
        return rootElement;
    }

    /**
     * 替换当前规则。
     *
     * @param rules 新规则
     */
    void setRules(List<String> rules) {
        drafts.clear();
        if (rules != null) {
            for (String rule : rules) {
                RuleDraft draft = RuleDraft.parse(rule);
                if (!draft.isEmpty()) {
                    drafts.add(draft);
                }
            }
        }
        refreshView();
    }

    /**
     * 返回当前规则配置快照。
     *
     * @return 规则配置快照
     */
    List<String> getRulesSnapshot() {
        List<String> values = new ArrayList<String>();
        for (RuleDraft draft : drafts) {
            if (!draft.isEmpty()) {
                values.add(draft.toConfigValue());
            }
        }
        return values;
    }

    /**
     * 校验当前草稿。
     *
     * @return 错误文本，合法时返回 null
     */
    String validateDraft() {
        return FontCharacterRuleDrafts.validateRules(getRulesSnapshot());
    }

    static List<String> toRuleList(String[] values) {
        if (values == null || values.length == 0) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(Arrays.asList(values));
    }

    private void addRuleFromToolbar() {
        drafts.add(new RuleDraft(true, selectorInput.getText(), fontInput.getText()));
        selectorInput.setText("");
        refreshView();
        fireChange();
    }

    private DocumentButtonControl createPresetButton(String label, final String selector) {
        return createSecondaryButton(label, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                selectorInput.setText(selector);
            }
        });
    }

    private void moveRule(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= drafts.size() || toIndex < 0 || toIndex >= drafts.size()) {
            return;
        }
        RuleDraft draft = drafts.remove(fromIndex);
        drafts.add(toIndex, draft);
        refreshView();
        fireChange();
    }

    private void deleteRule(int index) {
        if (index < 0 || index >= drafts.size()) {
            return;
        }
        drafts.remove(index);
        refreshView();
        fireChange();
    }

    private void refreshView() {
        listElement.clearChildren();
        if (drafts.isEmpty()) {
            ElementNode empty = document.div();
            empty.setAttribute("data-character-font-empty", "true");
            empty.style()
                    .setPadding(UiStyleLength.px(8))
                    .setTextColor(0xFF94A3B8);
            empty.appendText("尚未添加字符字体覆盖规则。");
            listElement.append(empty);
        } else {
            for (int index = 0; index < drafts.size(); index++) {
                listElement.append(createRow(index, drafts.get(index)));
            }
        }
        updateStateText();
    }

    private ElementNode createRow(final int index, final RuleDraft draft) {
        ElementNode row = document.div();
        row.setAttribute("data-character-font-rule-row", Integer.toString(index));
        configureRow(row);

        final DocumentToggleSwitchControl enabledControl = new DocumentToggleSwitchControl(document)
                .setTrackSize(36, 20)
                .setThumbSize(14)
                .setTrackColors(0xFF475569, 0xFF22C55E, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE);
        enabledControl.setToggled(draft.enabled);
        enabledControl.setChangeHandler(new DocumentToggleChangeHandler() {
            @Override
            public void onToggleChanged(DocumentToggleChangeEvent event) {
                draft.enabled = enabledControl.isToggled();
                refreshView();
                fireChange();
            }
        });
        row.append(enabledControl.getElement());

        final DocumentTextInputControl selectorControl = createRowInput("字符或范围", 110);
        selectorControl.setText(draft.selector);
        row.append(selectorControl.getElement());
        final DocumentAutocompleteInputControl fontControl = createFontInput("字体名", 170);
        fontControl.setText(draft.fontName);
        row.append(fontControl.getElement());

        final TextNode statusText = createStatusElement(row);
        selectorControl.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                draft.selector = selectorControl.getText();
                updateRowStatus(statusText, draft);
                updateStateText();
                fireChange();
            }
        });
        fontControl.setChangeHandler(new DocumentAutocompleteInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentAutocompleteInputChangeEvent event) {
                draft.fontName = fontControl.getText();
                updateRowStatus(statusText, draft);
                updateStateText();
                fireChange();
            }
        });
        updateRowStatus(statusText, draft);

        row.append(createSecondaryButton("上移", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                moveRule(index, index - 1);
            }
        }).setEnabled(index > 0).getElement());
        row.append(createSecondaryButton("下移", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                moveRule(index, index + 1);
            }
        }).setEnabled(index + 1 < drafts.size()).getElement());
        row.append(createDangerButton("删除", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                deleteRule(index);
            }
        }).getElement());
        return row;
    }

    private TextNode createStatusElement(ElementNode row) {
        ElementNode statusElement = document.div();
        statusElement.style()
                .setMinWidth(UiStyleLength.px(160))
                .setWidth(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextColor(0xFFCBD5E1);
        row.append(statusElement);
        return statusElement.appendText("");
    }

    private DocumentTextInputControl createToolbarInput(String placeholder, int width) {
        DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setPlaceholder(placeholder)
                .setMaxLength(96);
        input.getElement().style().setWidth(UiStyleLength.px(width));
        return input;
    }

    private DocumentTextInputControl createRowInput(String placeholder, int width) {
        DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setPlaceholder(placeholder)
                .setMaxLength(96);
        input.getElement().style().setWidth(UiStyleLength.px(width));
        return input;
    }

    private DocumentAutocompleteInputControl createFontInput(String placeholder, int width) {
        DocumentAutocompleteInputControl input = new DocumentAutocompleteInputControl(document,
                FontConfig.getFontSortSnapshot())
                .setPlaceholder(placeholder)
                .setMaxLength(96)
                .setShowAllWhenQueryEmpty(true)
                .setMaxVisibleOptions(8)
                .setMaxSuggestionCount(MAX_FONT_SUGGESTION_COUNT)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFEEEEFF, 0xFF777799, 0xFF666677);
        input.getElement().style().setWidth(UiStyleLength.px(width));
        return input;
    }

    private DocumentButtonControl createActionButton(String label, DocumentButtonActionHandler handler) {
        return new DocumentButtonControl(document, label)
                .setBackgroundColors(0xFF2563EB, 0xFF1D4ED8, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFFFFFFF, 0xFF94A3B8)
                .setActionHandler(handler);
    }

    private DocumentButtonControl createSecondaryButton(String label, DocumentButtonActionHandler handler) {
        return new DocumentButtonControl(document, label)
                .setBackgroundColors(0xFF334155, 0xFF475569, 0xFF1E293B)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFE2E8F0, 0xFF64748B)
                .setActionHandler(handler);
    }

    private DocumentButtonControl createDangerButton(String label, DocumentButtonActionHandler handler) {
        return new DocumentButtonControl(document, label)
                .setBackgroundColors(0xFF7F1D1D, 0xFF991B1B, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFFFFFFF, 0xFF94A3B8)
                .setActionHandler(handler);
    }

    private void updateRowStatus(TextNode statusText, RuleDraft draft) {
        if (draft.isEmpty()) {
            statusText.setText("空规则不会保存");
            return;
        }
        FontCharacterRule rule = FontCharacterRule.parse(draft.toConfigValue());
        if (!rule.isValid()) {
            statusText.setText(rule.getErrorMessage());
            return;
        }
        String prefix = rule.isEnabled() ? "有效" : "已禁用";
        String fontState = FontConfig.getFontSortSnapshot().length > 0 && !FontConfig.isFontPresent(rule.getFontName())
                ? "，当前字体目录未发现" : "";
        statusText.setText(prefix + "：" + formatRange(rule) + " -> " + rule.getFontName() + fontState);
    }

    private void updateStateText() {
        int ruleCount = 0;
        int activeCount = 0;
        for (RuleDraft draft : drafts) {
            if (draft.isEmpty()) {
                continue;
            }
            ruleCount++;
            FontCharacterRule rule = FontCharacterRule.parse(draft.toConfigValue());
            if (rule.isActive()) {
                activeCount++;
            }
        }
        String validationError = validateDraft();
        if (validationError == null) {
            String overlapWarning = FontCharacterRuleDrafts.findOverlapWarning(getRulesSnapshot());
            if (overlapWarning == null) {
                stateText.setText("规则 " + ruleCount + " 条，启用 " + activeCount + " 条");
            } else {
                stateText.setText("规则 " + ruleCount + " 条，启用 " + activeCount + " 条，提示："
                        + overlapWarning);
            }
        } else {
            stateText.setText("规则 " + ruleCount + " 条，需修正：" + validationError);
        }
    }

    private static String formatRange(FontCharacterRule rule) {
        String start = formatCodepoint(rule.getStartCodepoint());
        String end = formatCodepoint(rule.getEndCodepoint());
        return start.equals(end) ? start : start + "-" + end;
    }

    private static String formatCodepoint(int codepoint) {
        String hex = Integer.toHexString(codepoint).toUpperCase(Locale.ENGLISH);
        while (hex.length() < 4) {
            hex = "0" + hex;
        }
        return "U+" + hex;
    }

    private void fireChange() {
        if (changeListener != null) {
            changeListener.onRulesChanged(getRulesSnapshot());
        }
    }

    private static void configureRoot(ElementNode root) {
        root.setAttribute("data-config-control", "character-font-rules");
        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8));
    }

    private static void configureToolbar(ElementNode toolbar) {
        toolbar.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.START)
                .setColumnGap(UiStyleLength.px(8))
                .setRowGap(UiStyleLength.px(6))
                .setTextColor(0xFFCBD5E1);
    }

    private static void configureList(ElementNode list) {
        list.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6))
                .setWidth(UiStyleLength.percent(1.0F));
    }

    private static void configureRow(ElementNode row) {
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setRowGap(UiStyleLength.px(6))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(6));
    }

    /**
     * 字符字体覆盖规则变更监听器。
     */
    interface FontCharacterRuleChangeListener {

        /**
         * 规则变更回调。
         *
         * @param rules 当前规则
         */
        void onRulesChanged(List<String> rules);
    }

    private static final class RuleDraft {

        private boolean enabled;
        private String selector;
        private String fontName;

        private RuleDraft(boolean enabled, String selector, String fontName) {
            this.enabled = enabled;
            this.selector = normalize(selector);
            this.fontName = normalize(fontName);
        }

        private static RuleDraft parse(String rawRule) {
            FontCharacterRule rule = FontCharacterRule.parse(rawRule);
            if (rule.isValid()) {
                return new RuleDraft(rule.isEnabled(), rule.getSelector(), rule.getFontName());
            }
            String normalized = rawRule == null ? "" : rawRule.trim();
            boolean enabled = true;
            if (normalized.regionMatches(true, 0, FontCharacterRule.DISABLED_PREFIX, 0,
                    FontCharacterRule.DISABLED_PREFIX.length())) {
                enabled = false;
                normalized = normalized.substring(FontCharacterRule.DISABLED_PREFIX.length()).trim();
            }
            int separatorIndex = normalized.indexOf('=');
            if (separatorIndex < 0) {
                return new RuleDraft(enabled, normalized, "");
            }
            return new RuleDraft(enabled, normalized.substring(0, separatorIndex),
                    normalized.substring(separatorIndex + 1));
        }

        private boolean isEmpty() {
            return selector.isEmpty() && fontName.isEmpty();
        }

        private String toConfigValue() {
            return FontCharacterRule.toConfigValue(enabled, selector, fontName);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
