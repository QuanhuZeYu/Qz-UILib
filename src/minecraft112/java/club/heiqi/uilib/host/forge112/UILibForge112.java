package club.heiqi.uilib.host.forge112;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Forge 1.12.2 只安装客户端宿主；页面和共享服务遵循多版本宿主规格。 */
@Mod(modid = "qz_uilib", name = "Qz UILib", version = "4.11.1", clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*")
public final class UILibForge112 {
    @Mod.EventHandler public void initialize(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) Forge112Client.install();
    }
}
