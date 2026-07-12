package club.heiqi.uilib.client;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.client.hud.ClientHudServiceImpl;
import club.heiqi.uilib.client.hud.SceneHudHost;
import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudLine;
import club.heiqi.uilib.ui.hud.api.HudSnapshot;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudTone;
import club.heiqi.uilib.ui.hud.api.HudVisibility;
import club.heiqi.uilib.ui.host.UiHostRenderSupport;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.opengl.GL11;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** 唯一 Forge HUD render bridge；不取消事件，不承载业务布局。 */
public final class UiHudRenderListener {
    private final ClientHudServiceImpl service = ClientHudServiceImpl.getInstance();
    private final SceneHudHost host = new SceneHudHost(service);
    private final PaintContextCompositor compositor = new PaintContextCompositor();
    private final UiMainLayerSnapshotService snapshots = new UiMainLayerSnapshotService();

    /** 创建 bridge，并把 UILib debug 文本注册为普通统一 HUD。 */
    public UiHudRenderListener() {
        registerDebugHud();
    }

    /** 注册 UILib 内部 debug HUD。 */
    private void registerDebugHud() {
        service.register(HudSpec.builder("qzuilib:debug").anchor(HudAnchor.TOP_RIGHT)
                .visibility(HudVisibility.IN_WORLD).stackOrder(Integer.MIN_VALUE).build(),
                () -> Config.uiDebug ? HudSnapshot.of(HudLine.text("screen", currentScreenName(), HudTone.MUTED))
                        : HudSnapshot.EMPTY);
    }

    /** 在 Post(ALL) 中以 GUI 逻辑像素执行 scene layout→paint→replay。 */
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event == null || event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) return;
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int width = Math.max(1, resolution.getScaledWidth());
        int height = Math.max(1, resolution.getScaledHeight());
        int previousMatrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            UiHostRenderSupport.prepareMainUiRenderState();
            compositor.beginFrame(); snapshots.beginFrame();
            UiRenderContext context = new UiRenderContext(width, height, 0, 0, event.partialTicks, compositor, snapshots);
            host.render(context, width, height, minecraft.theWorld != null, minecraft.currentScreen != null);
        } finally {
            snapshots.finishFrame(); compositor.finishFrame();
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrix);
        }
    }

    /** 世界生命周期结束时释放 host cache。 */
    public void clearWorld() { host.clearWorld(); service.clearWorld(); registerDebugHud(); }

    /** 客户端世界卸载时立即释放 HUD 注册与保留 scene。 */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event != null && event.world != null && event.world.isRemote) clearWorld();
    }

    private static String currentScreenName() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null || minecraft.currentScreen == null ? "null" : minecraft.currentScreen.getClass().getName();
    }
}
