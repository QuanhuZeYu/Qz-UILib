package club.heiqi.qz_uilib;

import club.heiqi.qz_uilib.client.ErrorCleaner;
import club.heiqi.qz_uilib.client.RenderWorldLast;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        new ErrorCleaner().register();
        new RenderWorldLast().register();
    }
}
