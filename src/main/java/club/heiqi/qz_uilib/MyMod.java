package club.heiqi.qz_uilib;

import club.heiqi.qz_uilib.fontsystem.FontManager;
import club.heiqi.qz_uilib.guiInject.GuiScreenListener;
import club.heiqi.qz_uilib.utils.SkijaLoader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = MOD_INFO.MODID, version = Tags.VERSION, name = "QzUI库", acceptedMinecraftVersions = "[1.7.10]")
public class MyMod {
    public static Logger LOG = LogManager.getLogger();

    public static FontManager fontManager;
    public static GuiScreenListener guiScreenListener;

    @SidedProxy(clientSide = "club.heiqi.qz_uilib.ClientProxy", serverSide = "club.heiqi.qz_uilib.CommonProxy")
    public static CommonProxy proxy;

    @Mod.Instance
    public MyMod INSTANCE = this;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
