package club.heiqi.qz_uilib;

import club.heiqi.qz_uilib.client.KeyListener;
import club.heiqi.qz_uilib.widget.drawUtil.FixedFunctionMesh;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {
    public KeyListener keyListener = new KeyListener();

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        keyListener.registrar();
    }
}
