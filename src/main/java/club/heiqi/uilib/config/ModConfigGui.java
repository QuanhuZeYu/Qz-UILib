package club.heiqi.uilib.config;

import java.util.Arrays;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.dom.UiDocument;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.Property;

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
                .setSaveHandler(new SaveHandler() {
                    @Override
                    public void onSave(net.minecraftforge.common.config.Configuration configuration) {
                        Config.saveAndReload();
                    }
                })
                .addPropertyEditorFactory(new PropertyEditorFactory() {
                    @Override
                    public PropertyBinding create(UiDocument document, CategorySpec categorySpec, Property property,
                            ForgeConfigTemplateScreen owner) {
                        if (!(owner instanceof ModConfigGui) || !isFontSortProperty(categorySpec, property)) {
                            return null;
                        }
                        return ((ModConfigGui) owner).new FontSortPropertyBinding(document, categorySpec, property);
                    }
                })
                .addCategory(new CategorySpec(Config.GENERAL)
                        .setTitle("General")
                        .setDescription("基础运行开关与通用行为配置。"))
                .addCategory(new CategorySpec(FontConfig.CATEGORY)
                        .setTitle("Font System")
                        .setDescription("字体渲染运行时、排序和 drawString 上传节流相关配置。"))
                .addCategory(new CategorySpec(FontConfig.FONT_SIZE_CATEGORY)
                        .setTitle("Font Size")
                        .setDescription("默认字号、生成分辨率与缩放系数配置。"));
    }

    private static boolean isFontSortProperty(CategorySpec categorySpec, Property property) {
        if (categorySpec == null || property == null) {
            return false;
        }
        return FontConfig.CATEGORY.equalsIgnoreCase(categorySpec.getCategoryName())
                && "fontSort".equalsIgnoreCase(property.getName())
                && property.isList();
    }

    /**
     * 字体排序属性绑定。
     */
    private final class FontSortPropertyBinding extends PropertyBinding {

        private final FontSortListControl control;

        private FontSortPropertyBinding(UiDocument document, CategorySpec categorySpec, Property property) {
            super(document, categorySpec, property);
            this.control = new FontSortListControl(document, ModConfigGui.this)
                    .setChangeListener(new Runnable() {
                        @Override
                        public void run() {
                            requestStatusRefresh();
                        }
                    });
            restoreCurrentValue();
            initializeCard(document, control.getElement());
        }

        @Override
        protected String buildMetadataText() {
            return super.buildMetadataText()
                    + " | 已发现字体：" + FontConfig.getFontSortSnapshot().length
                    + " | 缺失记录：" + FontConfig.getMissingFontSnapshot().length;
        }

        @Override
        protected String buildHelperText() {
            StringBuilder builder = new StringBuilder();
            String baseText = super.buildHelperText();
            if (!baseText.isEmpty()) {
                builder.append(baseText).append(' ');
            }
            builder.append("拖拽模式可直接调整回退顺序；序号模式可输入目标序号后按回车或点击提交。列表值会在每次启动时按已发现字体自动补全。");
            String[] missingFonts = FontConfig.getMissingFontSnapshot();
            if (missingFonts.length > 0) {
                builder.append(" 当前未发现的历史配置字体：").append(Arrays.toString(missingFonts)).append('。');
            }
            return builder.toString();
        }

        @Override
        public boolean isDirty() {
            return !Arrays.equals(getProperty().getStringList(), control.getValues());
        }

        @Override
        public void restoreCurrentValue() {
            control.setValues(getProperty().getStringList());
        }

        @Override
        public void restoreDefaultValue() {
            control.setValues(getProperty().getDefaults());
        }

        @Override
        public String validateDraft() {
            return null;
        }

        @Override
        public void applyDraft() {
            getProperty().set(control.getValues());
        }
    }
}
