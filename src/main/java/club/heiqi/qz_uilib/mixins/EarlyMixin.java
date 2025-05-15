package club.heiqi.qz_uilib.mixins;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@IFMLLoadingPlugin.MCVersion("1.7.10")
public class EarlyMixin implements IEarlyMixinLoader, IFMLLoadingPlugin {

    static {
        try {
            LaunchClassLoader loader = Launch.classLoader;
            loader.addTransformerExclusion("io.github.humbleui.");
        } catch (Exception ignore) {

        }
    }

    @Override
    public String getMixinConfig() {
        return "mixins.qz_uilib.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return Arrays.asList(
            "Minecraft_Mixin"
        );
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
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
