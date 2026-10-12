package club.heiqi.uilib;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import club.heiqi.uilib.host.CommonProxy;

/** 平台入口只安装宿主；纯核心无需加载本类。 */
@Mod(modid = "qz_uilib", name = "Qz UILib", version = Tags.VERSION,
        guiFactory = "club.heiqi.uilib.host.client.settings.UILibGuiFactory")
public final class UILib {
    @SidedProxy(
            clientSide = "club.heiqi.uilib.host.ClientProxy",
            serverSide = "club.heiqi.uilib.host.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
