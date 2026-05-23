package club.heiqi.uilib;

import java.io.File;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.event.FontReloadRequest;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

/**
 * 全局配置入口。
 */
public class Config {

    public static final String GENERAL = Configuration.CATEGORY_GENERAL;
    public static Configuration configuration;
    private static File configFile;
    private static final Config CONFIG_LISTENER = new Config();
    public static boolean useDebug = false;
    public static boolean uiDebug = false;
    public static String netTransport = "vanilla";

    /**
     * 初始化并装载全部配置。
     *
     * @param configFile 配置文件路径
     */
    public static void init(File configFile) {
        Config.configFile = configFile;
        if (configuration == null) {
            configuration = new Configuration(configFile);
        }
        registerEvents();
        load();
        applyLoadedFontConfig("config_loaded");
    }

    /**
     * 重新读取当前配置。
     */
    public static void load() {
        useDebug = configuration.getBoolean("useDebug", GENERAL, useDebug, "是否启用调试输出");
        uiDebug = configuration.getBoolean("uiDebug", GENERAL, uiDebug, "是否在屏幕右上角显示当前页面类名");
        netTransport = configuration.getString("netTransport", GENERAL, netTransport,
                "网络传输适配器：vanilla 为默认 early mixin 路径，forge 仅用于兼容排障。",
                new String[] { "vanilla", "forge" });
        FontConfig.load(configuration);

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /**
     * 显式保存并重新装载配置。
     */
    public static void saveAndReload() {
        if (configuration == null) {
            return;
        }
        if (configuration.hasChanged()) {
            configuration.save();
        }
        load();
        boolean fontRuntimeChanged = FontConfig.affectsFontRuntime();
        if (fontRuntimeChanged) {
            MyMod.LOG.info("检测到字体配置更新，准备重载字体系统");
            club.heiqi.uilib.font.FontService.getInstance().reload(new FontReloadRequest("config_changed"));
        }
        FontConfig.onConfigReload();
    }

    /**
     * 应用刚装载完成的字体配置快照。
     *
     * <p>早期资源重载可能在 Forge 配置装载前初始化字体系统；此时必须在配置可用后补一次重载，避免继续使用默认静态配置。</p>
     *
     * @param reason 重载原因
     */
    private static void applyLoadedFontConfig(String reason) {
        boolean fontRuntimeChanged = FontConfig.affectsFontRuntime();
        if (fontRuntimeChanged && FontService.getInstance().isInitialized()) {
            MyMod.LOG.info("字体配置已装载，早期字体运行时将按真实配置重载");
            FontService.getInstance().reload(new FontReloadRequest(reason));
        }
        FontConfig.onConfigReload();
    }

    /**
     * 获取当前配置文件路径。
     *
     * @return 配置文件路径
     */
    public static String getConfigPath() {
        return configFile == null ? "" : configFile.getAbsolutePath();
    }

    private static void registerEvents() {
        MinecraftForge.EVENT_BUS.unregister(CONFIG_LISTENER);
        FMLCommonHandler.instance().bus().unregister(CONFIG_LISTENER);
        MinecraftForge.EVENT_BUS.register(CONFIG_LISTENER);
        FMLCommonHandler.instance().bus().register(CONFIG_LISTENER);
    }

    /**
     * 监听 Forge 配置界面保存事件。
     *
     * @param event 配置变更事件
     */
    @SubscribeEvent
    public void onConfigChangeEvent(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!MyMod.MODID.equalsIgnoreCase(event.modID)) {
            return;
        }
        saveAndReload();
    }
}
