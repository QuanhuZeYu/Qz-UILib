package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;

/**
 * Forge 可视化配置主界面。
 */
public class ModConfigGui extends GuiConfig {

    /**
     * 创建配置界面。
     *
     * @param parentScreen 父界面
     */
    public ModConfigGui(GuiScreen parentScreen) {
        super(
                parentScreen,
                getConfigElements(),
                MyMod.MODID,
                false,
                false,
                MyMod.MOD_NAME,
                GuiConfig.getAbridgedConfigPath(Config.getConfigPath()));
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        List<String> categories = Arrays.asList(
                Config.GENERAL,
                FontConfig.CATEGORY,
                FontConfig.FONT_SIZE_CATEGORY);

        for (String categoryName : categories) {
            ConfigCategory category = Config.configuration.getCategory(categoryName.toLowerCase(Locale.ROOT));
            elements.add(new ConfigElement(category));
        }
        return elements;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Config.saveAndReload();
    }
}
