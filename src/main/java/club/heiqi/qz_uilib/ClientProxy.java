package club.heiqi.qz_uilib;

import club.heiqi.qz_uilib.gameMenu.GameMenu;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {
    public GameMenu gameMenu;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        gameMenu = new GameMenu().register();
    }
}
