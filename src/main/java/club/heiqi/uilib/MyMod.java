package club.heiqi.uilib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
        modid = MyMod.MODID,
        version = Tags.VERSION,
        name = "Qz UILib",
        acceptedMinecraftVersions = "[1.7.10]",
        // 远端版本协商（契约见 FmlRemoteVersionCompatibilityContractTest）：区间必须包含当前
        // Tags.VERSION，否则 FML 判定「mod 拒绝自身版本」——集成服务器在进入世界的握手阶段
        // Rejecting connection CLIENT 后卸载全部维度（症状：无法进入世界）。
        // 注意「显式空串」不是开发期形态：它构成空区间并拒绝一切（含自身）；精确版本相等只属于
        // 整条属性不写的形态。区间不跨 major：下界保留已承诺的 4.9.x，上界取当前制品 4.10.0 的
        // 下一 minor 边界 4.11.0（覆盖 4.9.x 与 4.10.x）。
        acceptableRemoteVersions = "[4.9.0,4.11.0)",
        guiFactory = MyMod.GUI_FACTORY)
public class MyMod {

    public static final String MODID = "qz_uilib";
    public static final String MOD_NAME = "Qz UILib";
    public static final String CLIENT_PROXY = "club.heiqi.uilib.ClientProxy";
    public static final String COMMON_PROXY = "club.heiqi.uilib.CommonProxy";
    public static final String GUI_FACTORY = "club.heiqi.uilib.config.ModGuiFactory";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = COMMON_PROXY)
    public static CommonProxy proxy;

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
