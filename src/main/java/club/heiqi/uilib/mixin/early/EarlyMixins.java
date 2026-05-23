package club.heiqi.uilib.mixin.early;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * 原版类的早期 Mixin 加载器。
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class EarlyMixins implements IEarlyMixinLoader, IFMLLoadingPlugin {

    @Override
    public String getMixinConfig() {
        return "mixins.qz_uilib.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        List<String> mixins = new ArrayList<String>();
        if (FMLLaunchHandler.side() == Side.CLIENT) {
            mixins.add("MixinFontRenderer");
            mixins.add("MixinGuiScreenKeyboardIsolation");
            mixins.add("MixinGuiContainerKeyTypedIsolation");
            mixins.add("network.MixinNetHandlerPlayClient");
        }
        mixins.add("network.MixinNetHandlerPlayServer");
        return mixins;
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
