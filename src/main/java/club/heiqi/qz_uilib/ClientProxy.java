package club.heiqi.qz_uilib;

import club.heiqi.qz_uilib.fontsystem.FontManager;
import club.heiqi.qz_uilib.guiInject.GuiScreenListener;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClientProxy extends CommonProxy {
    public static Logger LOG = LogManager.getLogger();
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MyMod.fontManager = new FontManager();
        MyMod.fontManager._registerClient(event, null, null);
        MyMod.guiScreenListener = new GuiScreenListener();
        MyMod.guiScreenListener._registerClient(event, null, null);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MyMod.fontManager._registerClient(null, event, null);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        MyMod.fontManager._registerClient(null, null, event);
    }
}
