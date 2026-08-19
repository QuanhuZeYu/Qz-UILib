package club.heiqi.uilib.client;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.client.hud.ClientHudServiceImpl;
import club.heiqi.uilib.client.hud.FramebufferViewportFactory;
import club.heiqi.uilib.client.hud.HudViewportMetrics;
import club.heiqi.uilib.client.hud.LiveMinecraftHudEnvironment;
import club.heiqi.uilib.client.hud.MinecraftHudEnvironment;
import club.heiqi.uilib.client.hud.SceneHudHost;
import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudVisibility;
import club.heiqi.uilib.ui.host.UiHostRenderSupport;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.opengl.GL11;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** 唯一 Forge HUD render bridge；不取消事件，不承载业务布局。 */
public final class UiHudRenderListener {
    private static final HudGlStateGuard HUD_GL_STATE_GUARD = new HudGlStateGuard();
    private final ClientHudServiceImpl service = ClientHudServiceImpl.getInstance();
    private final SceneHudHost host = new SceneHudHost(service);
    private final PaintContextCompositor compositor = new PaintContextCompositor();
    private final UiMainLayerSnapshotService snapshots = new UiMainLayerSnapshotService();
    private final MinecraftHudEnvironment environment;
    /** debug HUD 显示的当前界面名（每帧在 renderHudFrame 更新）。 */
    private final Signal<String> debugScreenName = Signal.create("null");

    /** 创建 bridge，并把 UILib debug 文本注册为普通统一 HUD。 */
    public UiHudRenderListener() {
        this(new LiveMinecraftHudEnvironment());
    }

    /** 创建使用指定 Minecraft 环境的 bridge。 */
    UiHudRenderListener(MinecraftHudEnvironment environment) {
        this.environment = environment;
        registerDebugHud();
    }

    /** 从生产环境提取 framebuffer 视口；GUI scale 只保留在环境诊断面。 */
    HudViewportMetrics viewport() {
        return viewport(environment);
    }

    /** 把指定生产环境提取为 framebuffer 视口。 */
    static HudViewportMetrics viewport(MinecraftHudEnvironment environment) {
        return FramebufferViewportFactory.create(environment.displayWidth(), environment.displayHeight());
    }

    /** 注册 UILib 内部 debug HUD：与业务方同款的窗口工厂 + scene 代码。 */
    private void registerDebugHud() {
        service.register(HudSpec.builder("qzuilib:debug").anchor(HudAnchor.TOP_RIGHT)
                .visibility(HudVisibility.IN_WORLD).stackOrder(Integer.MIN_VALUE).build(),
                rt -> {
                    club.heiqi.uilib.ui.scene.node.SceneNode root = club.heiqi.uilib.ui.scene.node.SceneNode.row()
                            .setHitTestable(false);
                    // uiDebug 关闭时卸载内容树 → 空内容整窗隐藏（对齐旧 EMPTY 快照语义）
                    rt.show(root, club.heiqi.uilib.ui.reactive.Computed.create(() -> Config.uiDebug),
                            () -> {
                                club.heiqi.uilib.ui.scene.node.SceneNode line =
                                        club.heiqi.uilib.ui.scene.node.SceneNode.row()
                                                .setHitTestable(false)
                                                .setTextColor(club.heiqi.uilib.ui.scene.paint.SceneChromeTokens.HUD_TEXT_MUTED)
                                                .setFontSize(14);
                                rt.bindText(line, debugScreenName);
                                return line;
                            });
                    return root;
                });
    }

    /** 在 Post(ALL) 中以 framebuffer 尺寸执行 scene layout→paint→replay。 */
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event == null || event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) return;
        HudViewportMetrics viewport = viewport();
        int width = viewport.getWidth();
        int height = viewport.getHeight();
        HUD_GL_STATE_GUARD.run(() -> renderHudFrame(event, minecraft, viewport, width, height));
    }

    /** 在已捕获入口状态的围栏内完成一整帧 HUD 业务与清理。 */
    private void renderHudFrame(RenderGameOverlayEvent.Post event, Minecraft minecraft,
            HudViewportMetrics viewport, int width, int height) {
        debugScreenName.set(currentScreenName());
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
        Throwable frameFailure = null;
        try {
            UiHostRenderSupport.prepareMainUiRenderState();
            compositor.beginFrame(); snapshots.beginFrame();
            UiRenderContext context = new UiRenderContext(width, height, 0, 0, event.partialTicks, compositor, snapshots);
            host.render(context, viewport, minecraft.theWorld != null, minecraft.currentScreen != null);
        } catch (RuntimeException failure) {
            frameFailure = failure;
        } catch (Error failure) {
            frameFailure = failure;
        }
        Throwable cleanupFailure = finishHudFrame();
        if (cleanupFailure != null) {
            if (frameFailure != null) cleanupFailure.addSuppressed(frameFailure);
            throwUnchecked(cleanupFailure);
        }
        if (frameFailure != null) throwUnchecked(frameFailure);
    }

    /** 两个帧清理互不短路，后续失败作为 suppressed 保留。 */
    private Throwable finishHudFrame() {
        Throwable failure = null;
        try {
            snapshots.finishFrame();
        } catch (RuntimeException cleanupFailure) {
            failure = cleanupFailure;
        } catch (Error cleanupFailure) {
            failure = cleanupFailure;
        }
        try {
            compositor.finishFrame();
        } catch (RuntimeException cleanupFailure) {
            if (failure == null) failure = cleanupFailure; else failure.addSuppressed(cleanupFailure);
        } catch (Error cleanupFailure) {
            if (failure == null) failure = cleanupFailure; else failure.addSuppressed(cleanupFailure);
        }
        return failure;
    }

    /** 重抛 HUD 生命周期捕获的 unchecked 失败。 */
    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        throw (Error) failure;
    }

    /** 世界生命周期结束时仅释放 session scene；mod 级 registration 保留供重连复用。 */
    public void clearWorld() { host.clearWorld(); }

    /** 客户端世界卸载时立即释放保留 scene。 */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event != null && event.world != null && event.world.isRemote) clearWorld();
    }

    private static String currentScreenName() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null || minecraft.currentScreen == null ? "null" : minecraft.currentScreen.getClass().getName();
    }
}
