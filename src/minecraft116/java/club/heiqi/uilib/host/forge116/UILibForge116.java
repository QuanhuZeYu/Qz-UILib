package club.heiqi.uilib.host.forge116;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/** Forge 客户端生命周期入口，安装共享页面服务的游戏适配。 */
@Mod("qz_uilib")
public final class UILibForge116 {
    public UILibForge116() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> Forge116Client::install);
    }
}
