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
        return new Spec(MyMod.MODID, MyMod.MOD_NAME + " 配置", Config.configuration)
                .setSubtitle("Forge In-Game Config Replacement")
                .setDescription("使用 Qz UILib 的 HTML-like 文档页面替代默认 Forge 配置页，并作为可复用模板开放给其他开发者。")
                .setConfigPath(Config.getConfigPath())
                .addPropertyEditorFactory(new FontSortPropertyEditorFactory())
                .setSaveHandler(new SaveHandler() {
                    @Override
                    public void onSave(net.minecraftforge.common.config.Configuration configuration) {
                        Config.saveAndReload();
                    }
                })
                .addCategory(new CategorySpec(Config.GENERAL)
                        .setTitle("General")
                        .setDescription("基础运行开关、界面调试显示与通用行为配置。"))
                .addCategory(new CategorySpec(FontConfig.CATEGORY)
                        .setTitle("Font System")
                        .setDescription("字体渲染运行时、排序和 drawString 上传节流相关配置。"))
                .addCategory(new CategorySpec(FontConfig.FONT_SIZE_CATEGORY)
                        .setTitle("Font Size")
                        .setDescription("默认字号、生成分辨率与缩放系数配置。"))
                .setNumericControlOptions(FontConfig.FONT_SIZE_CATEGORY, "fontScale",
                        NumericControlOptions.sliderWithLabel()
                                .withSliderStep(0.05D)
                                .withLabelFormat("%.2f"));
    }
}
