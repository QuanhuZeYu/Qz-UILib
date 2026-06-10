package club.heiqi.uilib.config;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;
import net.minecraft.client.gui.GuiScreen;

/**
 * Qz UILib 的 HTML-like 游戏内配置页。
 */
public class ModConfigGui extends ForgeConfigTemplateScreen {

    /**
     * 创建配置界面。
     *
     * @param parentScreen 父界面
     */
    public ModConfigGui(GuiScreen parentScreen) {
        super(parentScreen, createSpec());
    }

    private static Spec createSpec() {
        return createBaseSpec(Config.configuration)
                .setConfigPath(Config.getConfigPath())
                .setSaveHandler(new SaveHandler() {
                    @Override
                    public void onSave(net.minecraftforge.common.config.Configuration configuration) {
                        Config.saveAndReload();
                    }
                })
                .addPropertyEditorFactory(new FontSortPropertyEditorFactory())
                .addPropertyEditorFactory(new FontCharacterRulePropertyEditorFactory())
                .enableQzNetworkSync(ConfigTemplateSyncManager.QZ_UI_LIB_SCREEN_ID);
    }

    private static Spec createBaseSpec(net.minecraftforge.common.config.Configuration configuration) {
        Spec spec = new Spec(MyMod.MODID, QzUiLibConfigSchema.title(), configuration)
                .setSubtitle(QzUiLibConfigSchema.subtitle())
                .setDescription(QzUiLibConfigSchema.description());
        for (ConfigSyncCategorySpec category : QzUiLibConfigSchema.categories()) {
            spec.addCategory(new CategorySpec(category.getCategoryName())
                    .addAliases(category.getAliases())
                    .setTitle(category.getDisplayTitle())
                    .setDescription(category.getDescription()));
        }
        return spec;
    }
}
