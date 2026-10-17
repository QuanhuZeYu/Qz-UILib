package club.heiqi.uilib.host.forge116;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/** 客户端入口不把 Minecraft 类型带入共享核心。 */
@Mod("qz_uilib")
public final class UILibForge116 {
    public UILibForge116() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> Forge116Client::install);
    }
}
