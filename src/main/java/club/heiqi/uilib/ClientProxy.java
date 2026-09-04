package club.heiqi.uilib;

import club.heiqi.uilib.client.FontRenderTickListener;
import club.heiqi.uilib.client.UiHudRenderListener;
import club.heiqi.uilib.client.UiInputTickListener;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.internal.chat3.input.ChatInputOpenListener;
import club.heiqi.uilib.internal.devtools.DevToolsClientBootstrap;
import club.heiqi.uilib.internal.devtools.NetRuntimeSelfChecks;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.client.NetStoreUiBridge;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.input.UiInputService;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * 客户端代理。
 */
public class ClientProxy extends CommonProxy {

    private final FontRenderTickListener fontRenderTickListener = new FontRenderTickListener();
    private final UiHudRenderListener uiHudRenderListener = new UiHudRenderListener();
    private final UiInputTickListener uiInputTickListener = new UiInputTickListener();
    private final ChatInputOpenListener chatInputOpenListener = new ChatInputOpenListener();

    /**
     * 客户端预初始化时注册字体渲染 Tick 监听。
     *
     * @param event Forge 预初始化事件
     */
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // 字体渲染骨架只在客户端引导，且必须晚于 ModernConfigBootstrap（读新栈配置值）、
        // 早于渲染/输入监听注册。判定权威在 FontService 内部，这里不做侧别判断。
        MyMod.LOG.info("preInit 时序 [client]: FontService.initialize 开始");
        FontService.getInstance().initialize();
        MyMod.LOG.info("字体系统已启用：{}", FontService.getInstance().isInitialized());
        UiInputService.getInstance().initialize();
        NetStoreUiBridge.getInstance().initialize();
        DevToolsClientBootstrap.registerClientDevTools();
        // 运行时自检端点集属于 devtools：唯一驱动者是客户端命令（DevToolsClientBootstrap 注册的
        // qzuilib 命令）。在服务端注册只会白起常驻线程，并把 13 个调试端点暴露给任意客户端。
        NetRuntimeSelfChecks.register();
        MinecraftForge.EVENT_BUS.register(fontRenderTickListener);
        MinecraftForge.EVENT_BUS.register(uiHudRenderListener);
        MinecraftForge.EVENT_BUS.register(chatInputOpenListener);
        FMLCommonHandler.instance().bus().register(fontRenderTickListener);
        FMLCommonHandler.instance().bus().register(uiInputTickListener);
        FMLCommonHandler.instance().bus().register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(this::onJvmShutdown, "QzUiLibShutdown"));
    }

    /**
     * 客户端从服务端断连时清理缓存与网络状态。
     *
     * <p>在 1.7.10 Forge 中 {@link FMLNetworkEvent.ClientDisconnectionFromServerEvent} 同时覆盖单人退出与多人断连。</p>
     *
     * @param event 客户端断连事件
     */
    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        try {
            uiHudRenderListener.clearWorld();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("HUD 断连清理异常", exception);
        }
        try {
            DocumentRemoteImageCache.getInstance().clear();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("远程图片缓存断连清理异常", exception);
        }
        try {
            NetService.getInstance().onClientDisconnected();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("网络层断连清理异常", exception);
        }
    }

    private void onJvmShutdown() {
        try {
            DocumentRemoteImageCache.getInstance().shutdown();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("远程图片缓存关停异常", exception);
        }
        try {
            FontService.getInstance().shutdown();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("字体系统关停异常", exception);
        }
        try {
            NetService.getInstance().shutdown();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("网络层关停异常", exception);
        }
    }
}
