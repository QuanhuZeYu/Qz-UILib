package club.heiqi.qz_uilib.configGUI;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.MyMod;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConfigGUI extends GuiConfig {
    public ConfigGUI(GuiScreen parentScreen) {
        super(
                parentScreen,
                getConfigElements(),// new ConfigElement(Config.config.getCategory(Configuration.CATEGORY_GENERAL)).getChildElements(),
                MyMod.MODID,
                false,
                false,
                MyMod.MOD_NAME,
                GuiConfig.getAbridgedConfigPath(Config.configPath));
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<>();

        List<String> topCategories = Arrays.asList(Configuration.CATEGORY_GENERAL);
        for (String categoryName : topCategories) {
            ConfigCategory category = Config.config.getCategory(categoryName);
            elements.add(new ConfigElement(category));
        }

        return elements;
    }
}
