package club.heiqi.uilib.manualtest;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import club.heiqi.uilib.Tags;
import club.heiqi.uilib.internal.devtools.CoreLoopBootstrap;

/** 人工测试附加包的客户端入口，安装后通过显式命令打开广场。 */
@Mod(
        modid = "qz_uilib_manualtest",
        name = "Qz UILib Manual Test",
        version = Tags.VERSION,
        dependencies = "required-after:qz_uilib")
public final class ManualTestMod {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) CoreLoopBootstrap.install();
    }
}
