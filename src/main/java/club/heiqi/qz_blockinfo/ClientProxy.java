package club.heiqi.qz_blockinfo;

import club.heiqi.qz_blockinfo.gameMenu.GameMenu;
import club.heiqi.skija.GLCanvas;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {
    public static GameMenu gameMenu;
    public static GLCanvas canvas;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        gameMenu = new GameMenu().register();
    }
}
