package club.heiqi.qz_uilib;

import club.heiqi.qz_uilib.fontsystem.FontManager;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MyMod.fontManager = new FontManager();
        MyMod.fontManager._registerClient(event, null, null);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MyMod.fontManager._registerClient(null, event, null);
    }
}
