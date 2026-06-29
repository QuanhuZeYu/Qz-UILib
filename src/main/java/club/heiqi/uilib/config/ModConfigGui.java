package club.heiqi.uilib.config;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Qz UILib 的游戏内配置页入口。
 *
 * <p>入口直接构造 Forge 配置页，旧 Modern 配置模板分支已随旧栈拆除。</p>
 */
public class ModConfigGui extends GuiScreen {

    private final GuiScreen parentScreen;

    /**
     * 创建配置界面。
     *
     * @param parentScreen 父界面
     */
    public ModConfigGui(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null) {
            minecraft.displayGuiScreen(createTargetScreen(parentScreen));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    static GuiScreen createTargetScreen(GuiScreen parentScreen) {
        ForgeConfigTemplateScreen.Spec spec = createForgeSpec();
        return new ForgeConfigTemplateScreen(parentScreen, spec);
    }

    private static ForgeConfigTemplateScreen.Spec createForgeSpec() {
        return createBaseSpec(Config.configuration)
                .setConfigPath(Config.getConfigPath())
                .setSaveHandler(new ForgeConfigTemplateScreen.SaveHandler() {
                    @Override
                    public void onSave(net.minecraftforge.common.config.Configuration configuration) {
                        Config.saveAndReload();
                    }
                })
                .addPropertyEditorFactory(new FontSortPropertyEditorFactory())
                .addPropertyEditorFactory(new FontCharacterRulePropertyEditorFactory())
                .enableQzNetworkSync(ConfigTemplateSyncManager.QZ_UI_LIB_SCREEN_ID);
    }

    private static ForgeConfigTemplateScreen.Spec createBaseSpec(
            net.minecraftforge.common.config.Configuration configuration) {
        ForgeConfigTemplateScreen.Spec spec = new ForgeConfigTemplateScreen.Spec(MyMod.MODID,
                QzUiLibConfigSchema.title(), configuration)
                .setSubtitle(QzUiLibConfigSchema.subtitle())
                .setDescription(QzUiLibConfigSchema.description());
        for (ConfigSyncCategorySpec category : QzUiLibConfigSchema.categories()) {
            spec.addCategory(new ForgeConfigTemplateScreen.CategorySpec(category.getCategoryName())
                    .addAliases(category.getAliases())
                    .setTitle(category.getDisplayTitle())
                    .setDescription(category.getDescription()));
        }
        return spec;
    }
}
