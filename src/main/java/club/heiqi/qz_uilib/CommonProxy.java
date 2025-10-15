package club.heiqi.qz_uilib;

import club.heiqi.qz_uilib.network.NetworkMain;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

import java.io.File;

public class CommonProxy {
    public NetworkMain networkMain = new NetworkMain();
    public Config config = new Config();

    public void preInit(FMLPreInitializationEvent event) {
        String version = Tags.VERSION;
        String modName = MyMod.MOD_NAME;
        File suggestedConfigurationFile = new File("config", modName+"_"+version+".cfg");
        config.init(suggestedConfigurationFile);
        config.registrar();

        networkMain.registrar();
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
