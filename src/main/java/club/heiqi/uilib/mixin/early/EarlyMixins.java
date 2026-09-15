package club.heiqi.uilib.mixin.early;

import java.util.ArrayList;
import java.util.Collections;
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

    private static final String ANGELICA_TWEAKER = "com.gtnewhorizons.angelica.loading.AngelicaTweaker";

    @Override
    public String getMixinConfig() {
        return "mixins.qz_uilib.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return buildMixinsForSide(FMLLaunchHandler.side(), loadedCoreMods);
    }

    /**
     * 按 Forge 启动侧生成 early mixin 列表。
     *
     * @param side 当前启动侧
     * @return mixin 列表
     */
    static List<String> buildMixinsForSide(Side side) {
        return buildMixinsForSide(side, Collections.<String>emptySet());
    }

    /**
     * 按 Forge 启动侧与已加载 coremod 生成 early mixin 列表。
     *
     * @param side 当前启动侧
     * @param loadedCoreMods 已加载 coremod/tweaker 类名
     * @return mixin 列表
     */
    static List<String> buildMixinsForSide(Side side, Set<String> loadedCoreMods) {
        List<String> mixins = new ArrayList<String>();
        if (side == Side.CLIENT) {
            mixins.add("MixinFontRenderer");
            mixins.add("MixinMinecraftWorldLoadPump");
            mixins.add("MixinGuiScreenKeyboardIsolation");
            mixins.add("network.MixinNetHandlerPlayClient");
            mixins.add("nametag.MixinEntityRendererPlayerNameTagPass");
            mixins.add("nametag.MixinRendererLivingEntityPlayerNameTag");
            mixins.add("nametag.MixinRenderPlayerScoreboardNameTag");
            if (loadedCoreMods != null && loadedCoreMods.contains(ANGELICA_TWEAKER)) {
                mixins.add("nametag.MixinAngelicaPlayerNameTagReplay");
                // 宿主把 TESR 几何排队延后提交，本库字形却在调用点内立即提交，两者落屏顺序不同源，
                // 需要宿主提交点上的世界文字回放围栏（无 Angelica 时不注册，行为退回即时绘制）。
                mixins.add("tesr.MixinAngelicaTesrBatchReplay");
            }
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
