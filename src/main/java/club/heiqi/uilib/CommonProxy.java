package club.heiqi.uilib;

import club.heiqi.uilib.font.FontService;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

import java.io.File;

/**
 * 通用代理，负责主线公共初始化。
 */
public class CommonProxy {

    /**
     * 预初始化阶段先建立配置与字体系统骨架。
     *
     * @param event Forge 预初始化事件
     */
    public void preInit(FMLPreInitializationEvent event) {
        File configFile = event.getSuggestedConfigurationFile();
        Config.init(configFile);
        FontService.getInstance().initialize();

        MyMod.LOG.info("Qz-UILib {} 初始化完成", Tags.VERSION);
        MyMod.LOG.info("字体系统已启用：{}", FontService.getInstance().isInitialized());
        MyMod.LOG.info("I am Qz-UILib at version " + Tags.VERSION);
    }

    /**
     * 初始化阶段预留给后续字体渲染接入。
     *
     * @param event Forge 初始化事件
     */
    public void init(FMLInitializationEvent event) {}

    /**
     * 后初始化阶段预留给兼容层接入。
     *
     * @param event Forge 后初始化事件
     */
    public void postInit(FMLPostInitializationEvent event) {}

    /**
     * 服务端命令注册占位。
     *
     * @param event 服务端启动事件
     */
    public void serverStarting(FMLServerStartingEvent event) {}
}
