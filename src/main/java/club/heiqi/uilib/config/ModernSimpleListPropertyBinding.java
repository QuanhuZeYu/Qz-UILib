package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentInputType;
import club.heiqi.uilib.ui.control.DocumentTextAreaChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextAreaChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextAreaControl;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置 primitive list 字段绑定。
 */
final class ModernSimpleListPropertyBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private final List<ItemDraft> drafts = new ArrayList<ItemDraft>();
    private ElementNode listElement;
    private DocumentTextInputControl addInput;
    private DocumentTextAreaControl importTextArea;
    private DocumentToggleSwitchControl keepEmptyLinesToggle;
    private TextNode stateText;
    private ForgeConfigTemplateScreen.Theme currentTheme = ForgeConfigTemplateScreen.Theme.defaultTheme();

    ModernSimpleListPropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
        replaceDraftsFromNode(node);
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        currentTheme = theme == null ? ForgeConfigTemplateScreen.Theme.defaultTheme() : theme;
        ElementNode root = document.div();
        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8));
        root.append(createAddToolbar(document, currentTheme));
        root.append(createImportBlock(document, currentTheme));
        listElement = document.div();
        listElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        root.append(listElement);
        refreshListView(document, currentTheme);
        return root;
    }

    @Override
    protected String buildHelperText() {
        String inherited = super.buildHelperText();
        String suffix = "primitive list：支持添加、删除、上移、下移和按行批量导入。";
        return inherited.isEmpty() ? suffix : inherited + " " + suffix;
    }

    @Override
    boolean isDirty() {
        return !Objects.equals(readCurrentValues(), readDraftValues());
    }

    @Override
    void restoreCurrentValue() {
        replaceDraftsFromNode(getCurrentNode());
        refreshListViewIfReady();
    }

    @Override
    void restoreDefaultValue() {
        replaceDraftsFromDefaultValue();
        refreshListViewIfReady();
        notifyDraftChanged();
    }

    @Override
    String validateDraft() {
        for (int index = 0; index < drafts.size(); index++) {
            ModernConfigListModels.ParsedValue parsed = ModernConfigListModels.parseDraftValue(
                    drafts.get(index).kind, drafts.get(index).text);
            if (parsed.hasError()) {
                return "第 " + (index + 1) + " 项" + parsed.getError();
            }
        }
        return null;
    }

    @Override
    void applyDraft() {
        getConfig().set(getPath(), readDraftValues());
    }

    private ElementNode createAddToolbar(final UiDocument document, final ForgeConfigTemplateScreen.Theme theme) {
        ElementNode toolbar = document.div();
        configureToolbar(toolbar);
        addInput = new DocumentTextInputControl(document)
                .setPlaceholder("新增列表项")
                .setMaxLength(ModernConfigPropertyBindings.DEFAULT_TEXT_MAX_LENGTH);
        if (getInference().getListValueKind() == ModernConfigListModels.ValueKind.NUMBER) {
            addInput.setType(DocumentInputType.NUMBER);
        }
        addInput.getElement().style().setWidth(UiStyleLength.px(180));
        toolbar.append(addInput.getElement());
        toolbar.append(createButton(document, theme, "添加", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                addDraft(addInput.getText(), getInference().getListValueKind());
                addInput.setText("");
                refreshListView(document, theme);
                notifyDraftChanged();
            }
        }).getElement());
        ElementNode stateElement = document.div();
        stateElement.style().setTextColor(0xFFCBD5E1).setPadding(UiStyleLength.px(4));
        stateText = stateElement.appendText("");
        toolbar.append(stateElement);
        return toolbar;
    }

    private ElementNode createImportBlock(final UiDocument document, final ForgeConfigTemplateScreen.Theme theme) {
        ElementNode block = document.div();
        block.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        importTextArea = new DocumentTextAreaControl(document)
                .setPlaceholder("批量导入：每行一个列表项")
                .setMaxLength(ModernConfigPropertyBindings.DEFAULT_LONG_TEXT_MAX_LENGTH)
                .setSurfaceColors(0xFF222233, 0xFF555577, theme.focusBorderColor, 0xFF333344, 0xFF444455)
                .setChangeHandler(new DocumentTextAreaChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentTextAreaChangeEvent event) {
                    }
                });
        importTextArea.getElement().style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.px(72));
        block.append(importTextArea.getElement());

        ElementNode importToolbar = document.div();
        configureToolbar(importToolbar);
        keepEmptyLinesToggle = new DocumentToggleSwitchControl(document)
                .setTrackSize(36, 20)
                .setThumbSize(14)
                .setTrackColors(0xFF475569, 0xFF22C55E, 0xFF334155)
                .setFocusBorderColor(theme.focusBorderColor);
        importToolbar.append(keepEmptyLinesToggle.getElement());
        ElementNode keepLabel = document.div();
        keepLabel.style().setTextColor(0xFFCBD5E1);
        keepLabel.appendText("保留空行");
        importToolbar.append(keepLabel);
        importToolbar.append(createButton(document, theme, "导入追加", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                importDrafts(document, theme);
            }
        }).getElement());
        block.append(importToolbar);
        return block;
    }

    private void importDrafts(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        List<String> importedLines = parseImportLines(importTextArea.getText(), keepEmptyLinesToggle.isToggled());
        for (String importedLine : importedLines) {
            addDraft(importedLine, getInference().getListValueKind());
        }
        importTextArea.setText("");
        refreshListView(document, theme);
        if (!importedLines.isEmpty()) {
            notifyDraftChanged();
        }
    }

    private void refreshListViewIfReady() {
        if (listElement == null) {
            return;
        }
        refreshListView(listElement.getOwnerDocument(), currentTheme);
    }

    private void refreshListView(final UiDocument document, final ForgeConfigTemplateScreen.Theme theme) {
        if (listElement == null) {
            return;
        }
        listElement.clearChildren();
        if (drafts.isEmpty()) {
            ElementNode empty = document.div();
            empty.style().setPadding(UiStyleLength.px(8)).setTextColor(0xFF94A3B8);
            empty.appendText("当前列表为空，可添加或批量导入 primitive 值。");
            listElement.append(empty);
        } else {
            for (int index = 0; index < drafts.size(); index++) {
                listElement.append(createRow(document, theme, index, drafts.get(index)));
            }
        }
        updateStateText();
    }

    private ElementNode createRow(final UiDocument document, final ForgeConfigTemplateScreen.Theme theme,
            final int index, final ItemDraft draft) {
        ElementNode row = document.div();
        row.setAttribute("data-modern-config-list-row", Integer.toString(index));
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

        ElementNode indexElement = document.div();
        indexElement.style().setTextColor(0xFF93C5FD).setPadding(UiStyleLength.px(4));
        indexElement.appendText("#" + (index + 1));
        row.append(indexElement);

        final DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setMaxLength(ModernConfigPropertyBindings.DEFAULT_TEXT_MAX_LENGTH)
                .setText(draft.text);
        input.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                draft.text = input.getText();
                notifyDraftChanged();
            }
        });
        if (draft.kind == ModernConfigListModels.ValueKind.NUMBER) {
            input.setType(DocumentInputType.NUMBER);
        }
        input.getElement().style().setWidth(UiStyleLength.percent(1.0F)).setMinWidth(UiStyleLength.px(160));
        row.append(input.getElement());
        row.append(createKindLabel(document, draft.kind));
        row.append(createButton(document, theme, "上移", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                moveDraft(index, index - 1);
                refreshListView(document, theme);
            }
        }).setEnabled(index > 0).getElement());
        row.append(createButton(document, theme, "下移", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                moveDraft(index, index + 1);
                refreshListView(document, theme);
            }
        }).setEnabled(index + 1 < drafts.size()).getElement());
        row.append(createDangerButton(document, theme, "删除", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                deleteDraft(index);
                refreshListView(document, theme);
            }
        }).getElement());
        return row;
    }

    private ElementNode createKindLabel(UiDocument document, ModernConfigListModels.ValueKind kind) {
        ElementNode label = document.div();
        label.style()
                .setPadding(UiStyleLength.px(4))
                .setBackgroundColor(0xFF1E293B)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(6))
                .setTextColor(0xFFCBD5E1);
        label.appendText(kind == null ? "string" : kind.name().toLowerCase(java.util.Locale.ENGLISH));
        return label;
    }

    private void addDraft(String text, ModernConfigListModels.ValueKind kind) {
        drafts.add(new ItemDraft(kind == null ? ModernConfigListModels.ValueKind.STRING : kind, text));
    }

    private void moveDraft(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= drafts.size() || toIndex < 0 || toIndex >= drafts.size()) {
            return;
        }
        ItemDraft draft = drafts.remove(fromIndex);
        drafts.add(toIndex, draft);
        notifyDraftChanged();
    }

    private void deleteDraft(int index) {
        if (index < 0 || index >= drafts.size()) {
            return;
        }
        drafts.remove(index);
        notifyDraftChanged();
    }

    private void replaceDraftsFromNode(ConfigNode node) {
        drafts.clear();
        if (node == null || node.getType() != ConfigNode.NodeType.LIST || node.asList() == null) {
            return;
        }
        for (ConfigNode item : node.asList()) {
            ModernConfigListModels.ValueKind kind = ModernConfigListModels.ValueKind.fromNode(item);
            drafts.add(new ItemDraft(kind == null ? ModernConfigListModels.ValueKind.STRING : kind,
                    ModernConfigListModels.formatNodeValue(item)));
        }
    }

    private void replaceDraftsFromDefaultValue() {
        drafts.clear();
        Object defaultValue = getDefaultValue();
        if (!(defaultValue instanceof List)) {
            return;
        }
        List<?> values = (List<?>) defaultValue;
        for (Object value : values) {
            ModernConfigListModels.ValueKind kind = ModernConfigListModels.resolveRawPrimitiveKind(value,
                    getInference().getListValueKind());
            drafts.add(new ItemDraft(kind, ModernConfigListModels.formatRawPrimitiveValue(value)));
        }
    }

    private List<Object> readCurrentValues() {
        List<Object> values = new ArrayList<Object>();
        ConfigNode node = getCurrentNode();
        if (node == null || node.getType() != ConfigNode.NodeType.LIST || node.asList() == null) {
            return values;
        }
        for (ConfigNode item : node.asList()) {
            values.add(ModernConfigListModels.convertNodeValue(item));
        }
        return values;
    }

    private List<Object> readDraftValues() {
        List<Object> values = new ArrayList<Object>();
        for (ItemDraft draft : drafts) {
            ModernConfigListModels.ParsedValue parsed = ModernConfigListModels.parseDraftValue(draft.kind, draft.text);
            values.add(parsed.hasError() ? draft.text : parsed.getValue());
        }
        return values;
    }

    private DocumentButtonControl createButton(UiDocument document, ForgeConfigTemplateScreen.Theme theme,
            String label, DocumentButtonActionHandler handler) {
        DocumentButtonControl button = new DocumentButtonControl(document, label)
                .setBackgroundColors(0xFF334155, 0xFF475569, 0xFF1E293B)
                .setFocusBorderColor(theme.focusBorderColor)
                .setTextColors(0xFFE2E8F0, 0xFF64748B)
                .setActionHandler(handler);
        button.getElement().style().setPadding(UiStyleLength.px(7));
        return button;
    }

    private DocumentButtonControl createDangerButton(UiDocument document, ForgeConfigTemplateScreen.Theme theme,
            String label, DocumentButtonActionHandler handler) {
        DocumentButtonControl button = new DocumentButtonControl(document, label)
                .setBackgroundColors(0xFF7F1D1D, 0xFF991B1B, 0xFF334155)
                .setFocusBorderColor(theme.focusBorderColor)
                .setTextColors(0xFFFFFFFF, 0xFF94A3B8)
                .setActionHandler(handler);
        button.getElement().style().setPadding(UiStyleLength.px(7));
        return button;
    }

    private void updateStateText() {
        if (stateText != null) {
            stateText.setText("列表项 " + drafts.size() + " 个");
        }
    }

    private static void configureToolbar(ElementNode toolbar) {
        toolbar.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setRowGap(UiStyleLength.px(6));
    }

    static List<String> parseImportLines(String text, boolean keepEmptyLines) {
        List<String> lines = new ArrayList<String>();
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\n", -1);
        for (String part : parts) {
            if (keepEmptyLines || !part.isEmpty()) {
                lines.add(part);
            }
        }
        return lines;
    }

    private static final class ItemDraft {

        private final ModernConfigListModels.ValueKind kind;
        private String text;

        private ItemDraft(ModernConfigListModels.ValueKind kind, String text) {
            this.kind = kind == null ? ModernConfigListModels.ValueKind.STRING : kind;
            this.text = text == null ? "" : text;
        }
    }
}
