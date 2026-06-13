package club.heiqi.uilib.config;

import java.util.Objects;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentInputType;
import club.heiqi.uilib.ui.control.DocumentSliderChangeEvent;
import club.heiqi.uilib.ui.control.DocumentSliderChangeHandler;
import club.heiqi.uilib.ui.control.DocumentSliderControl;
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
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置原始类型字段绑定。
 */
final class ModernPrimitivePropertyBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private DocumentTextInputControl textControl;
    private DocumentToggleSwitchControl toggleControl;
    private DocumentSliderControl sliderControl;
    private DocumentTextInputControl inlineNumberControl;
    private TextNode sliderValueLabel;
    private boolean suppressTextSync;
    private boolean suppressSliderSync;

    ModernPrimitivePropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        if (getInference().getTemplateType() == ModernConfigTypeInference.TemplateType.BOOLEAN) {
            return createBooleanEditor(document);
        }
        if (getInference().getTemplateType() == ModernConfigTypeInference.TemplateType.NUMBER) {
            return createNumberEditor(document);
        }
        return createStringEditor(document);
    }

    @Override
    boolean isDirty() {
        if (toggleControl != null) {
            return toggleControl.isToggled() != readCurrentBoolean();
        }
        if (sliderControl != null) {
            Double draft = parseNumberDraft(inlineNumberControl.getText());
            if (draft == null) {
                return true;
            }
            return Math.abs(readCurrentNumber() - draft.doubleValue()) > ModernConfigPropertyBindings.NUMERIC_EPSILON;
        }
        return !Objects.equals(readCurrentString(), textControl.getText());
    }

    @Override
    void restoreCurrentValue() {
        if (toggleControl != null) {
            toggleControl.setToggled(readCurrentBoolean());
            return;
        }
        if (sliderControl != null) {
            applyNumberValue(readCurrentNumber());
            return;
        }
        if (textControl != null) {
            textControl.setText(readCurrentString());
        }
    }

    @Override
    void restoreDefaultValue() {
        Object defaultValue = getDefaultValue();
        if (toggleControl != null) {
            toggleControl.setToggled(Boolean.parseBoolean(String.valueOf(defaultValue)));
            notifyDraftChanged();
            return;
        }
        if (sliderControl != null) {
            Double parsed = parseNumberDraft(String.valueOf(defaultValue));
            applyNumberValue(parsed == null ? 0.0D : parsed.doubleValue());
            notifyDraftChanged();
            return;
        }
        if (textControl != null) {
            textControl.setText(defaultValue == null ? "" : String.valueOf(defaultValue));
            notifyDraftChanged();
        }
    }

    @Override
    String validateDraft() {
        if (sliderControl != null) {
            return validateNumberDraft(inlineNumberControl.getText());
        }
        if (getInference().getTemplateType() == ModernConfigTypeInference.TemplateType.NUMBER) {
            return validateNumberDraft(textControl.getText());
        }
        return null;
    }

    @Override
    void applyDraft() {
        if (toggleControl != null) {
            getConfig().set(getPath(), Boolean.valueOf(toggleControl.isToggled()));
            return;
        }
        if (sliderControl != null) {
            applyNumberDraft(inlineNumberControl.getText());
            return;
        }
        if (getInference().getTemplateType() == ModernConfigTypeInference.TemplateType.NUMBER) {
            applyNumberDraft(textControl.getText());
            return;
        }
        if (getInference().getTemplateType() == ModernConfigTypeInference.TemplateType.NULL
                && textControl.getText().trim().isEmpty()) {
            getConfig().set(getPath(), null);
            return;
        }
        getConfig().set(getPath(), textControl.getText());
    }

    private ElementNode createBooleanEditor(UiDocument document) {
        toggleControl = new DocumentToggleSwitchControl(document)
                .setTrackColors(0xFF475569, 0xFF22C55E, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        notifyDraftChanged();
                    }
                });
        toggleControl.getElement().setAttribute("data-modern-config-control", "toggle");
        toggleControl.setToggled(readCurrentBoolean());
        return toggleControl.getElement();
    }

    private ElementNode createStringEditor(UiDocument document) {
        textControl = new DocumentTextInputControl(document)
                .setPlaceholder(getFieldSpec() == null ? "" : getFieldSpec().getPlaceholder())
                .setMaxLength(ModernConfigPropertyBindings.resolveMaxLength(getFieldSpec(),
                        ModernConfigPropertyBindings.DEFAULT_TEXT_MAX_LENGTH))
                .setChangeHandler(new DocumentTextInputChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentTextInputChangeEvent event) {
                        notifyDraftChanged();
                    }
                });
        textControl.getElement().setAttribute("data-modern-config-control", "text");
        textControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        textControl.setText(readCurrentString());
        return textControl.getElement();
    }

    private ElementNode createNumberEditor(UiDocument document) {
        if (!ModernConfigPropertyBindings.hasFiniteRange(getFieldSpec())) {
            textControl = new DocumentTextInputControl(document)
                    .setType(DocumentInputType.NUMBER)
                    .setMaxLength(ModernConfigPropertyBindings.resolveMaxLength(getFieldSpec(), 64))
                    .setChangeHandler(new DocumentTextInputChangeHandler() {
                        @Override
                        public void onTextChanged(DocumentTextInputChangeEvent event) {
                            notifyDraftChanged();
                        }
                    });
            textControl.getElement().setAttribute("data-modern-config-control", "numeric-text");
            textControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));
            textControl.setText(ModernConfigPropertyBindings.formatNumber(readCurrentNumber(),
                    getInference().isIntegerNumber()));
            return textControl.getElement();
        }

        ElementNode editor = document.div();
        editor.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8));

        double minValue = getFieldSpec().getMinValue().doubleValue();
        double maxValue = getFieldSpec().getMaxValue().doubleValue();
        sliderControl = new DocumentSliderControl(document)
                .setRange(minValue, maxValue)
                .setStep(ModernConfigPropertyBindings.resolveStep(getFieldSpec(), getInference().isIntegerNumber()))
                .setChangeHandler(new DocumentSliderChangeHandler() {
                    @Override
                    public void onSliderChanged(DocumentSliderChangeEvent event) {
                        if (suppressSliderSync) {
                            return;
                        }
                        syncInlineNumberFromSlider();
                        updateSliderValueLabel();
                        notifyDraftChanged();
                    }
                });
        sliderControl.getElement().setAttribute("data-modern-config-control", "numeric-slider");
        sliderControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));

        ElementNode sliderShell = document.div();
        sliderShell.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setWidth(UiStyleLength.percent(1.0F))
                .setMinWidth(UiStyleLength.px(0));
        sliderShell.append(sliderControl.getElement());
        editor.append(sliderShell);
        appendSliderValueLabel(document, editor);
        appendInlineNumberControl(document, editor);
        applyNumberValue(readCurrentNumber());
        return editor;
    }

    private void appendSliderValueLabel(UiDocument document, ElementNode editor) {
        ElementNode labelElement = document.div();
        labelElement.setAttribute("data-modern-config-control", "numeric-slider-label");
        labelElement.style()
                .setPadding(UiStyleLength.px(4))
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(8))
                .setBackgroundColor(0xFF0F172A)
                .setTextColor(0xFFE2E8F0)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        sliderValueLabel = labelElement.appendText("");
        editor.append(labelElement);
    }

    private void appendInlineNumberControl(UiDocument document, ElementNode editor) {
        inlineNumberControl = new DocumentTextInputControl(document)
                .setType(DocumentInputType.NUMBER)
                .setMaxLength(ModernConfigPropertyBindings.resolveMaxLength(getFieldSpec(), 64))
                .setChangeHandler(new DocumentTextInputChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentTextInputChangeEvent event) {
                        if (suppressTextSync) {
                            return;
                        }
                        syncSliderFromInlineNumber();
                        notifyDraftChanged();
                    }
                });
        inlineNumberControl.getElement().setAttribute("data-modern-config-control", "numeric-slider-input");
        inlineNumberControl.getElement().style().setWidth(UiStyleLength.px(88));
        editor.append(inlineNumberControl.getElement());
    }

    private boolean readCurrentBoolean() {
        ConfigNode node = getCurrentNode();
        if (node == null || node.isNull()) {
            Object defaultValue = getDefaultValue();
            return defaultValue instanceof Boolean ? ((Boolean) defaultValue).booleanValue()
                    : Boolean.parseBoolean(String.valueOf(defaultValue));
        }
        return node.asBoolean(false);
    }

    private String readCurrentString() {
        ConfigNode node = getCurrentNode();
        if (node == null || node.isNull()) {
            Object defaultValue = getDefaultValue();
            return defaultValue == null ? "" : String.valueOf(defaultValue);
        }
        return node.asString("");
    }

    private double readCurrentNumber() {
        ConfigNode node = getCurrentNode();
        if (node == null || node.isNull()) {
            Double parsedDefault = parseNumberDraft(String.valueOf(getDefaultValue()));
            return parsedDefault == null ? 0.0D : parsedDefault.doubleValue();
        }
        return node.asDouble(0.0D);
    }

    private void applyNumberValue(double value) {
        if (sliderControl != null) {
            suppressSliderSync = true;
            try {
                sliderControl.setValue(value);
            } finally {
                suppressSliderSync = false;
            }
            syncInlineNumberFromSlider();
            updateSliderValueLabel();
            return;
        }
        if (textControl != null) {
            textControl.setText(ModernConfigPropertyBindings.formatNumber(value, getInference().isIntegerNumber()));
        }
    }

    private void syncInlineNumberFromSlider() {
        if (inlineNumberControl == null || sliderControl == null) {
            return;
        }
        suppressTextSync = true;
        try {
            inlineNumberControl.setText(ModernConfigPropertyBindings.formatNumber(sliderControl.getValue(),
                    getInference().isIntegerNumber()));
        } finally {
            suppressTextSync = false;
        }
    }

    private void syncSliderFromInlineNumber() {
        if (sliderControl == null || inlineNumberControl == null) {
            return;
        }
        Double parsed = parseNumberDraft(inlineNumberControl.getText());
        if (parsed == null) {
            return;
        }
        suppressSliderSync = true;
        try {
            sliderControl.setValue(parsed.doubleValue());
        } finally {
            suppressSliderSync = false;
        }
        updateSliderValueLabel();
    }

    private void updateSliderValueLabel() {
        if (sliderValueLabel != null && sliderControl != null) {
            sliderValueLabel.setText(ModernConfigPropertyBindings.formatNumber(sliderControl.getValue(),
                    getInference().isIntegerNumber()));
        }
    }

    private String validateNumberDraft(String raw) {
        Double parsed = parseNumberDraft(raw);
        if (parsed == null) {
            return "必须是有效数值。";
        }
        if (getFieldSpec() != null && getFieldSpec().getMinValue() != null
                && parsed.doubleValue() < getFieldSpec().getMinValue().doubleValue()) {
            return "不能小于 " + getFieldSpec().getMinValue() + "。";
        }
        if (getFieldSpec() != null && getFieldSpec().getMaxValue() != null
                && parsed.doubleValue() > getFieldSpec().getMaxValue().doubleValue()) {
            return "不能大于 " + getFieldSpec().getMaxValue() + "。";
        }
        return null;
    }

    private void applyNumberDraft(String raw) {
        Double parsed = parseNumberDraft(raw);
        if (parsed == null) {
            return;
        }
        if (getInference().isIntegerNumber()) {
            getConfig().set(getPath(), Long.valueOf(Math.round(parsed.doubleValue())));
        } else {
            getConfig().set(getPath(), Double.valueOf(parsed.doubleValue()));
        }
    }

    private Double parseNumberDraft(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return getInference().isIntegerNumber() ? Double.valueOf(Long.parseLong(text))
                    : Double.valueOf(Double.parseDouble(text));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
