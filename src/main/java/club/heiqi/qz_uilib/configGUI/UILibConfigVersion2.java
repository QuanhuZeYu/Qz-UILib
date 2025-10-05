package club.heiqi.qz_uilib.configGUI;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.MyMod;
import club.heiqi.qz_uilib.client.ConfigGuiTemplate;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import java.util.ArrayList;
import java.util.List;

public class UILibConfigVersion2 extends ConfigGuiTemplate {

    public UILibConfigVersion2(GuiScreen parent) {
        super(parent);
    }

    @Override
    public List<ConfigCategory> getCategory() {
        List<ConfigCategory> result = new ArrayList<>();

        ConfigCategory category = Config.config.getCategory(Configuration.CATEGORY_GENERAL);

        result.add(category);

        return result;
    }

    @Override
    public void saveConfigCallback() {
        MyMod.proxy.config.load();
        Config.config.save();
    }
}
