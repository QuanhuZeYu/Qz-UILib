package club.heiqi.uilib.mixin.late;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;

/** 可选 Mod 的客户端兼容入口；不在服务端装载渲染类。 */
@LateMixin
public class LateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.qz_uilib.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return buildMixinsForSide(FMLLaunchHandler.side(), loadedMods);
    }

    static List<String> buildMixinsForSide(Side side, Set<String> loadedMods) {
        if (side == Side.CLIENT && loadedMods != null && loadedMods.contains("appliedenergistics2")) {
            return Collections.singletonList("ae2.MixinAbstractPartMonitor");
        }
        return Collections.emptyList();
    }
}
