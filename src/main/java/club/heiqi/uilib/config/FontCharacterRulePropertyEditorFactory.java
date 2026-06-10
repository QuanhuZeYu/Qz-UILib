package club.heiqi.uilib.config;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.dom.UiDocument;
import net.minecraftforge.common.config.Property;

/**
 * 为 Qz UILib 字符字体覆盖规则提供专用编辑器。
 */
final class FontCharacterRulePropertyEditorFactory implements ForgeConfigTemplateScreen.PropertyEditorFactory {

    @Override
    public ForgeConfigTemplateScreen.PropertyBinding create(UiDocument document,
            ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property, ForgeConfigTemplateScreen owner) {
        if (!matches(categorySpec, property) || owner == null) {
            return null;
        }
        return ConfigTemplatePropertyBindings.createCharacterFontRules(owner, document, categorySpec, property);
    }

    boolean matchesForTesting(ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property) {
        return matches(categorySpec, property);
    }

    private boolean matches(ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property) {
        return categorySpec != null && property != null
                && categorySpec.matchesCategoryName(FontConfig.CATEGORY)
                && "characterFontRules".equals(property.getName())
                && property.isList();
    }
}
