package club.heiqi.uilib.manualtest;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import club.heiqi.uilib.internal.devtools.CoreLoopBootstrap;

/** 只打入显式构建的人工测试附加包，真实客户端无需额外 JVM 参数。 */
@Mod(
        modid = "qz_uilib_manualtest",
        name = "Qz UILib Manual Test",
        version = "core-loop-1",
        dependencies = "required-after:qz_uilib")
public final class ManualTestMod {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) CoreLoopBootstrap.install();
    }
}
