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

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        String version = Tags.VERSION;
        String modName = MyMod.MOD_NAME;
        File suggestedConfigurationFile = new File("config", modName+"_"+version+".cfg");
        config.init(suggestedConfigurationFile);
        config.registrar();

        networkMain.registrar();
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}
}
