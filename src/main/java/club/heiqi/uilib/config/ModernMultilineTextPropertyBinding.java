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
    private String draftText;

    ModernMultilineTextPropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
        this.draftText = readCurrentValue();
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
                        draftText = control.getText();
                        notifyDraftChanged();
                    }
                });
        control.getElement().setAttribute("data-modern-config-control", "long-text");
        control.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        control.setText(draftText);
        return control.getElement();
    }

    @Override
    boolean isDirty() {
        if (control != null) {
            draftText = control.getText();
        }
        return !Objects.equals(readCurrentValue(), draftText);
    }

    @Override
    void restoreCurrentValue() {
        draftText = readCurrentValue();
        if (control != null) {
            control.setText(draftText);
        }
    }

    @Override
    void restoreDefaultValue() {
        Object defaultValue = getDefaultValue();
        draftText = defaultValue == null ? "" : String.valueOf(defaultValue);
        if (control != null) {
            control.setText(draftText);
        }
        notifyDraftChanged();
    }

    @Override
    String validateDraft() {
        return null;
    }

    @Override
    void applyDraft() {
        getConfig().set(getPath(), draftText);
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
