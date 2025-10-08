package club.heiqi.qz_uilib;

import club.heiqi.qz_uilib.fontsystem.impl.RuntimeChecker;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        RuntimeChecker.getInstance().register();
    }
}
