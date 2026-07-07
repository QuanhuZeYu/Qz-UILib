package club.heiqi.uilib;

import club.heiqi.uilib.client.FontRenderTickListener;
import club.heiqi.uilib.client.UiHudRenderListener;
import club.heiqi.uilib.client.UiInputTickListener;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.internal.devtools.DevToolsClientBootstrap;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.client.NetStoreUiBridge;
import club.heiqi.uilib.ui.hud.UiHudDocumentHost;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.input.UiHostInputCoordinator;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.remote.RemoteHudOverlayClientBridge;
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

    /**
     * 客户端预初始化时注册字体渲染 Tick 监听。
     *
     * @param event Forge 预初始化事件
     */
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        UiInputService.getInstance().initialize();
        UiHostInputCoordinator.getInstance().setCaptureParticipant(UiHudDocumentHost.getInstance());
        NetStoreUiBridge.getInstance().initialize();
        DevToolsClientBootstrap.registerClientDevTools();
        MinecraftForge.EVENT_BUS.register(fontRenderTickListener);
        MinecraftForge.EVENT_BUS.register(uiHudRenderListener);
        FMLCommonHandler.instance().bus().register(fontRenderTickListener);
        FMLCommonHandler.instance().bus().register(uiInputTickListener);
        FMLCommonHandler.instance().bus().register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(this::onJvmShutdown, "QzUiLibShutdown"));
    }

    /**
     * 客户端从服务端断连时清理 HUD 注册表。
     *
     * <p>HUD 入口由作者手动调用 {@code unregister()}，但世界切换时旧 widget 已经失效，
     * 这里统一兜底清空注册表与捕获状态，避免下一个世界继续保留过期引用。
     * 在 1.7.10 Forge 中 {@link FMLNetworkEvent.ClientDisconnectionFromServerEvent} 同时覆盖单人退出与多人断连。</p>
     *
     * @param event 客户端断连事件
     */
    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        try {
            UiHudDocumentHost.getInstance().clearAllRegistrations();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("HUD 断连清理异常", exception);
        }
        try {
            RemoteHudOverlayClientBridge.getInstance().clearAll();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("远程 HUD 断连清理异常", exception);
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
        try {
            RemoteHudOverlayClientBridge.getInstance().clearAll();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("远程 HUD 关停清理异常", exception);
        }
    }
}
