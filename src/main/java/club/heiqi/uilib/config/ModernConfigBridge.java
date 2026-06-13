package club.heiqi.uilib.config;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.MutableConfig;
import net.minecraft.client.gui.GuiScreen;

/**
 * 现代 config 模块的入口桥接。
 *
 * <p>该类允许直接引用 {@code club.heiqi.config} 类型，但只能在入口确认模块存在后加载。</p>
 */
final class ModernConfigBridge {

    private ModernConfigBridge() {}

    /**
     * 创建现代配置模板页。
     *
     * @param parentScreen 父界面
     * @param forgeSpec 现有 Forge 模板规格，用于复用标题、主题和文案口径
     * @return 现代配置页
     */
    static GuiScreen createScreen(GuiScreen parentScreen, ForgeConfigTemplateScreen.Spec forgeSpec) {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        ModernConfigTemplateScreen.Spec spec = new ModernConfigTemplateScreen.Spec(
                forgeSpec.getModId(), forgeSpec.getTitle(), config)
                        .setSubtitle(forgeSpec.getSubtitle())
                        .setDescription(forgeSpec.getDescription())
                        .setTheme(forgeSpec.getTheme())
                        .setTextSet(createTextSet(forgeSpec.getTextSet()));
        return new ModernConfigTemplateScreen(parentScreen, spec);
    }

    private static ForgeConfigTemplateScreen.TextSet createTextSet(ForgeConfigTemplateScreen.TextSet textSet) {
        return new ForgeConfigTemplateScreen.TextSet(
                textSet.saveButtonLabel,
                textSet.restoreCurrentButtonLabel,
                textSet.restoreDefaultsButtonLabel,
                textSet.backButtonLabel,
                textSet.statusCardTitle,
                textSet.modIdPrefix,
                textSet.configPathPrefix,
                textSet.shortcutHintText,
                textSet.idleNoChangesText,
                textSet.restoredCurrentValuesText,
                textSet.restoredDefaultValuesText,
                "当前现代配置节点没有可展示的子项。",
                "已检测到 club.heiqi.config 模块；Batch 0 仅提供现代配置页骨架，暂未挂载可编辑模板。");
    }
}
