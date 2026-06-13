package club.heiqi.uilib.config;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;

/**
 * 现代配置复杂结构只读摘要绑定。
 */
final class ModernReadOnlyPathBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private final String summary;

    ModernReadOnlyPathBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
        this.summary = ModernConfigPropertyBindings.formatSummary(node);
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        ElementNode value = document.div();
        value.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setTextColor(theme.mutedTextColor);
        value.appendText(summary.isEmpty() ? "空值" : summary);
        return value;
    }

    @Override
    boolean isDirty() {
        return false;
    }

    @Override
    void restoreCurrentValue() {
    }

    @Override
    void restoreDefaultValue() {
    }

    @Override
    String validateDraft() {
        return null;
    }

    @Override
    void applyDraft() {
    }
}
