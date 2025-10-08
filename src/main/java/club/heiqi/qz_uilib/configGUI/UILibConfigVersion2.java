package club.heiqi.qz_uilib.configGUI;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.MyMod;
import club.heiqi.qz_uilib.gui.ConfigGuiTemplate;
import club.heiqi.qz_uilib.widget.ButtonWithTextWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;

import java.util.ArrayList;
import java.util.List;

public class UILibConfigVersion2 extends ConfigGuiTemplate {

    public UILibConfigVersion2(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        super.initGui();

        ButtonWithTextWidget sortFontButton = new ButtonWithTextWidget().setText("字体排序");
        sortFontButton.perfectWidth = -1;
        sortFontButton.setCallBack(() -> {
            Minecraft.getMinecraft().displayGuiScreen(new QzExFontConfigGUI(this));
        });
        root.addChild(root.children.size()-1, sortFontButton);
    }

    @Override
    public List<ConfigCategory> getCategory() {
        List<ConfigCategory> result = new ArrayList<>();

        ConfigCategory category1 = Config.config.getCategory(Config.GENERAL);
        ConfigCategory category2 = Config.config.getCategory(Config.FONT_SYSTEM.toLowerCase());

        result.add(category1);
        result.add(category2);

        return result;
    }

    @Override
    public void saveConfigCallback() {
        MyMod.proxy.config.load();
        Config.config.save();
    }
}
