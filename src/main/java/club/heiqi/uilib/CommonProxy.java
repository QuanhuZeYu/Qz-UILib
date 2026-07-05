package club.heiqi.uilib;

import club.heiqi.uilib.config.modern.ModernConfigBootstrap;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.internal.devtools.NetRuntimeSelfChecks;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.transport.ITransport;
import club.heiqi.uilib.net.transport.NetTransportFactory;
import club.heiqi.uilib.net.transport.forge.ForgeMainThreadDispatcherBridge;
import club.heiqi.uilib.ui.remote.RemoteDocumentPages;
import club.heiqi.uilib.ui.remote.RemoteHudOverlays;
import club.heiqi.uilib.ui.remote.RemoteUiLeaseCleanupScheduler;
import cpw.mods.fml.common.FMLCommonHandler;
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
        MyMod.LOG.info("preInit 时序 [1/4]: Config.init 开始");
        Config.init(configFile);
        // 阶段 C C3：启动加载首次回灌，从新栈 YAML 读值覆盖静态字段（必须在 FontService.initialize
        // 之前，让字体系统直接用新栈值初始化；必须在 NetTransportFactory.create 之前，让 netTransport 用新栈值）
        File modernConfigFile = new File(event.getSuggestedConfigurationFile().getParentFile(), "qzuilib-modern.yaml");
        MyMod.LOG.info("preInit 时序 [2/4]: ModernConfigBootstrap.bootstrapAndApply 开始");
        ModernConfigBootstrap.bootstrapAndApply(modernConfigFile);
        MyMod.LOG.info("preInit 时序 [3/4]: FontService.initialize 开始");
        FontService.getInstance().initialize();
        MyMod.LOG.info("preInit 时序 [4/4]: NetTransportFactory.create 开始");
        ITransport transport = NetTransportFactory.create(Config.netTransport);
        NetService.getInstance().bootstrap(transport);
        NetRuntimeSelfChecks.register();
        RemoteDocumentPages.register();
        RemoteHudOverlays.register();
        RemoteUiLeaseCleanupScheduler.register();
        FMLCommonHandler.instance().bus().register(ForgeMainThreadDispatcherBridge.getInstance());

        MyMod.LOG.info("Qz-UILib {} 初始化完成", Tags.VERSION);
        MyMod.LOG.info("字体系统已启用：{}", FontService.getInstance().isInitialized());
        MyMod.LOG.info("网络传输适配器：{}", transport.getName());
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
    public void postInit(FMLPostInitializationEvent event) {
        NetService.getInstance().freeze();
    }

    /**
     * 服务端命令注册占位。
     *
     * @param event 服务端启动事件
     */
    public void serverStarting(FMLServerStartingEvent event) {}
}
