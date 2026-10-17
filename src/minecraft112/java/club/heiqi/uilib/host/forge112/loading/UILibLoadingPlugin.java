package club.heiqi.uilib.host.forge112.loading;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("club.heiqi.uilib.host.forge112.loading.")
public final class UILibLoadingPlugin implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass() { return new String[] { ClientShutdownTransformer.class.getName() }; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public void injectData(Map<String, Object> data) {}
    public String getAccessTransformerClass() { return null; }
}
