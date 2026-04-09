package club.heiqi.uilib;

import java.io.File;

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
    }

    /**
     * 重新读取当前配置。
     */
    public static void load() {
        useDebug = configuration.getBoolean("useDebug", GENERAL, useDebug, "是否启用调试输出");
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
        FontConfig.onConfigReload();
        if (FontConfig.affectsFontRuntime()) {
            MyMod.LOG.info("检测到字体配置更新，准备重载字体系统");
            club.heiqi.uilib.font.FontService.getInstance().reload(new FontReloadRequest("config_changed"));
        }
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
