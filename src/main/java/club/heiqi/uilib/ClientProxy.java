package club.heiqi.uilib;

import club.heiqi.config.ui.field.PickerSourceGuard;
import club.heiqi.config.ui.field.PickerSourceLifecycle;
import club.heiqi.uilib.client.FontRenderTickListener;
import club.heiqi.uilib.client.MinecraftMainThreadOracle;
import club.heiqi.uilib.client.UiHudRenderListener;
import club.heiqi.uilib.client.UiInputTickListener;
import club.heiqi.uilib.config.modern.ChatFrameConfig;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.i18n.LanguageEpochService;
import club.heiqi.uilib.internal.chat3.input.ChatFrameIntent;
import club.heiqi.uilib.internal.chat3.input.ChatInputOpenListener;
import club.heiqi.uilib.internal.devtools.DevToolsClientBootstrap;
import club.heiqi.uilib.internal.devtools.NetRuntimeSelfChecks;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.client.NetStoreUiBridge;
import club.heiqi.uilib.net.core.MainThreadDispatcher;
import club.heiqi.uilib.net.transport.NetSide;
import club.heiqi.uilib.resource.ResourceReloadService;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;
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
        // 候选源 SPI 的线程契约：装配期注入客户端判定源，生产路径恒有判定源；未安装时守卫降级放行，
        // 「装配期是否安装」由 PickerSourceGuardWiringTest 的源码守卫钉死（不靠运行时行为）。
        PickerSourceGuard.installThreadOracle(new MinecraftMainThreadOracle());
        // 资源/语言代际通道：进程单例挂到客户端资源管理器。1.7.10 注册即同步回调一次，
        // 故 resourceEpoch 立即从 0 变 1（初始态可见）。字体 reload 通道保持不动，二者不合并。
        // 分级表失效挂在同一条总线上（资源包换图后旧 UNRENDERABLE 不得驻留，改走一次代际广播）。
        ResourceReloadService.getInstance().addListener(epoch -> ItemRenderTierRegistry.invalidateAll("resource_reload"));
        ResourceReloadService.getInstance().registerToClient();
        LanguageEpochService.getInstance().install();
        NetStoreUiBridge.getInstance().initialize();
        DevToolsClientBootstrap.registerClientDevTools();
        // 聊天框形态切换（4.10.1）：动作注册与配置持久化分居两层——internal.chat3 只发布切换语义，
        // 写盘/回灌实现在 config.modern（internal 不反向依赖配置包），装配层在此注入两个端口：
        // 先写盘、自定义输入屏按既有 CLOSING 动画收回去，关屏完成后才回灌运行态。
        // 与聊天总开关无关：切回原版后动作仍留在注册表，重新接管时按钮自然回来。
        ChatFrameIntent.install(ChatFrameConfig::persistVanilla, ChatFrameConfig::applyVanilla);
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
        try {
            // 世界退出/断连：分级结论与跨世界旧图标不再有效（与候选源 release() 同批语义）。
            ItemRenderTierRegistry.invalidateAll("client_disconnect");
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("渲染分级表失效异常", exception);
        }
        try {
            // 候选源 SPI 的会话级释放点（SPI javadoc：客户端断开 / 世界退出；**不在关屏调用**）。
            // 本事件在网络线程触发，而 SPI 全方法只允许客户端主线程（ADR A-01）⇒ 先派发到客户端主线程
            // 队列，由 ClientTickEvent（ForgeMainThreadDispatcherBridge）排空后再释放；
            // 排空通道在断连后照常运行（主菜单也 tick），故释放不会被漏掉。
            MainThreadDispatcher.getInstance().enqueue(NetSide.CLIENT, new Runnable() {
                @Override
                public void run() {
                    PickerSourceLifecycle.releaseAll("client_disconnect");
                }
            });
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("候选源会话释放派发异常", exception);
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
