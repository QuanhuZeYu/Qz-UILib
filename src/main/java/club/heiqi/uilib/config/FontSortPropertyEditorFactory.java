package club.heiqi.uilib.config;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.dom.UiDocument;
import net.minecraftforge.common.config.Property;

/**
 * 为 Qz UILib 字体排序配置项提供专用二级排序页面入口。
 */
final class FontSortPropertyEditorFactory implements ForgeConfigTemplateScreen.PropertyEditorFactory {

    @Override
    public ForgeConfigTemplateScreen.PropertyBinding create(UiDocument document,
            ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property, ForgeConfigTemplateScreen owner) {
        if (!matches(categorySpec, property) || owner == null) {
            return null;
        }
        return ConfigTemplatePropertyBindings.createFontSort(owner, document, categorySpec, property);
    }

    boolean matchesForTesting(ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property) {
        return matches(categorySpec, property);
    }

    private boolean matches(ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property) {
        return categorySpec != null && property != null
                && FontConfig.CATEGORY.equalsIgnoreCase(categorySpec.getCategoryName())
                && "fontSort".equals(property.getName())
                && property.isList();
    }
}
