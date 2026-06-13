package club.heiqi.uilib.config;

import java.util.Objects;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentTextAreaChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextAreaChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextAreaControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置多行文本字段绑定。
 */
final class ModernMultilineTextPropertyBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private DocumentTextAreaControl control;

    ModernMultilineTextPropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        control = new DocumentTextAreaControl(document)
                .setPlaceholder(getFieldSpec() == null ? "" : getFieldSpec().getPlaceholder())
                .setMaxLength(ModernConfigPropertyBindings.resolveMaxLength(getFieldSpec(),
                        ModernConfigPropertyBindings.DEFAULT_LONG_TEXT_MAX_LENGTH))
                .setSurfaceColors(0xFF222233, 0xFF555577, theme.focusBorderColor, 0xFF333344, 0xFF444455)
                .setChangeHandler(new DocumentTextAreaChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentTextAreaChangeEvent event) {
                        notifyDraftChanged();
                    }
                });
        control.getElement().setAttribute("data-modern-config-control", "long-text");
        control.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        restoreCurrentValue();
        return control.getElement();
    }

    @Override
    boolean isDirty() {
        return !Objects.equals(readCurrentValue(), control.getText());
    }

    @Override
    void restoreCurrentValue() {
        if (control != null) {
            control.setText(readCurrentValue());
        }
    }

    @Override
    void restoreDefaultValue() {
        if (control != null) {
            Object defaultValue = getDefaultValue();
            control.setText(defaultValue == null ? "" : String.valueOf(defaultValue));
            notifyDraftChanged();
        }
    }

    @Override
    String validateDraft() {
        return null;
    }

    @Override
    void applyDraft() {
        getConfig().set(getPath(), control.getText());
    }

    private String readCurrentValue() {
        ConfigNode node = getCurrentNode();
        if (node == null || node.isNull()) {
            Object defaultValue = getDefaultValue();
            return defaultValue == null ? "" : String.valueOf(defaultValue);
        }
        return node.asString("");
    }
}
