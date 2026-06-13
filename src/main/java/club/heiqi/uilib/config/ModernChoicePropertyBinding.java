package club.heiqi.uilib.config;

import java.util.List;
import java.util.Objects;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectionEvent;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectionHandler;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectorControl;
import club.heiqi.uilib.ui.control.DocumentSelectChangeEvent;
import club.heiqi.uilib.ui.control.DocumentSelectChangeHandler;
import club.heiqi.uilib.ui.control.DocumentSelectControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置离散选项字段绑定。
 */
final class ModernChoicePropertyBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private DocumentSegmentedSelectorControl segmentedControl;
    private DocumentSelectControl selectControl;
    private String draftValue;

    ModernChoicePropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
        this.draftValue = readCurrentValue();
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        String[] options = getInference().getChoiceOptions().toArray(
                new String[getInference().getChoiceOptions().size()]);
        if (getInference().shouldUseSegmentedChoice()) {
            segmentedControl = new DocumentSegmentedSelectorControl(document, options)
                    .setBackgroundColors(theme.selectedOptionBackgroundColor,
                            theme.selectedOptionActiveBackgroundColor,
                            theme.normalOptionBackgroundColor,
                            theme.normalOptionActiveBackgroundColor,
                            theme.disabledOptionBackgroundColor)
                    .setTextColors(theme.selectedOptionTextColor, theme.normalOptionTextColor,
                            theme.disabledOptionTextColor)
                    .setFocusBorderColor(theme.focusBorderColor)
                    .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        draftValue = event.getSelectedOption();
                        notifyDraftChanged();
                    }
            });
            segmentedControl.getElement().setAttribute("data-modern-config-control", "choice-segmented");
            applySelectedValue(draftValue);
            return segmentedControl.getElement();
        }
        selectControl = new DocumentSelectControl(document, options)
                .setChangeHandler(new DocumentSelectChangeHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSelectChangeEvent event) {
                        draftValue = event.getSelectedOption();
                        notifyDraftChanged();
                    }
        });
        selectControl.getElement().setAttribute("data-modern-config-control", "choice-select");
        selectControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        applySelectedValue(draftValue);
        return selectControl.getElement();
    }

    @Override
    protected String buildHelperText() {
        String inherited = super.buildHelperText();
        String suffix = "可选值：" + String.valueOf(getInference().getChoiceOptions());
        return inherited.isEmpty() ? suffix : inherited + " " + suffix;
    }

    @Override
    boolean isDirty() {
        return !Objects.equals(readCurrentValue(), readDraftValue());
    }

    @Override
    void restoreCurrentValue() {
        draftValue = readCurrentValue();
        applySelectedValue(draftValue);
    }

    @Override
    void restoreDefaultValue() {
        draftValue = String.valueOf(getDefaultValue());
        applySelectedValue(draftValue);
        notifyDraftChanged();
    }

    @Override
    String validateDraft() {
        return getInference().getChoiceOptions().contains(readDraftValue()) ? null : "必须选择有效选项。";
    }

    @Override
    void applyDraft() {
        getConfig().set(getPath(), readDraftValue());
    }

    private String readCurrentValue() {
        ConfigNode node = getCurrentNode();
        if (node == null || node.isNull()) {
            Object defaultValue = getDefaultValue();
            return defaultValue == null ? firstOption() : String.valueOf(defaultValue);
        }
        return node.asString(firstOption());
    }

    private String readDraftValue() {
        return draftValue == null ? firstOption() : draftValue;
    }

    private void applySelectedValue(String value) {
        int targetIndex = resolveOptionIndex(value);
        if (segmentedControl != null) {
            segmentedControl.setSelectedIndex(targetIndex);
        }
        if (selectControl != null) {
            selectControl.setSelectedIndex(targetIndex);
        }
    }

    private int resolveOptionIndex(String value) {
        List<String> options = getInference().getChoiceOptions();
        for (int index = 0; index < options.size(); index++) {
            if (Objects.equals(options.get(index), value)) {
                return index;
            }
        }
        return 0;
    }

    private String firstOption() {
        return getInference().getChoiceOptions().isEmpty() ? "" : getInference().getChoiceOptions().get(0);
    }
}
