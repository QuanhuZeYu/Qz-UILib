package club.heiqi.uilib.ui.container.experimental.minecraft;

import java.lang.ref.WeakReference;
import java.util.Objects;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;

import club.heiqi.uilib.ui.host.UiHostRenderSupport;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglKeyMapper;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;
import club.heiqi.uilib.ui.scene.host.lwjgl.PlatformStateReader;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;

/** 一个已 attach `GuiContainer` 的 scene phase、输入 owner 与渲染资源宿主。 */
public final class GuiContainerLongEntryHost implements AutoCloseable {
    private final WeakReference<GuiContainer> screenReference;
    private final String screenLabel;
    private final GuiContainerSceneSurface surface;
    private final PaintContextCompositor compositor = new PaintContextCompositor();
    private final UiMainLayerSnapshotService snapshots = new UiMainLayerSnapshotService();
    private final UiRuntimeAdapters runtimeAdapters = UiRuntimeAdapters.minecraftDefaults();
    private final ScenePaintReplayer replayer = new ScenePaintReplayer();
    private final PlatformStateReader stateReader = new LwjglStateReader();

    private ContainerInputClaim pointerClaim;
    private SceneMouseButton pointerButton = SceneMouseButton.NONE;
    private UiRenderContext frameContext;
    private boolean overlayPainted;
    private boolean closed;

    private GuiContainerLongEntryHost(GuiContainer screen, GuiContainerSceneSurface surface) {
        GuiContainer checkedScreen = Objects.requireNonNull(screen, "screen");
        this.screenReference = new WeakReference<GuiContainer>(checkedScreen);
        this.screenLabel = checkedScreen.getClass().getName();
        this.surface = Objects.requireNonNull(surface, "surface");
    }

    /** 将 scene surface attach 到一个真实 `GuiContainer`。 */
    public static GuiContainerLongEntryHost attach(GuiContainer screen, GuiContainerSceneSurface surface) {
        GuiContainerLongEntryHost host = new GuiContainerLongEntryHost(screen, surface);
        GuiContainerScenePhaseHook.attach(screen, host);
        return host;
    }

    boolean pointerDown(int callbackX, int callbackY, int nativeButton) {
        if (closed) return false;
        int logicalX = logicalX(callbackX);
        int logicalY = logicalY(callbackY);
        SceneMouseButton button = mapButton(nativeButton);
        boolean shift = stateReader.shift();
        boolean control = stateReader.control();
        ContainerInputClaim claim = nonNull(surface.claimDown(logicalX, logicalY, button, shift, control));
        pointerClaim = claim.consumesVanilla() ? claim : ContainerInputClaim.vanilla();
        pointerButton = button;
        GuiContainerScenePhaseHook.tempLog("pointer DOWN callback=({}, {}) logical=({}, {}) button={} owner={} pending={}",
                Integer.valueOf(callbackX), Integer.valueOf(callbackY), Integer.valueOf(logicalX),
                Integer.valueOf(logicalY), button, pointerClaim.owner(), Boolean.valueOf(surface.isPending()));
        if (!claim.consumesVanilla()) return false;
        surface.dispatchClaimedPointer(claim, ScenePointerAction.BUTTON_DOWN,
                logicalX, logicalY, button, shift, control);
        return true;
    }

    boolean pointerMove(int callbackX, int callbackY) {
        if (closed || pointerClaim == null || !pointerClaim.consumesVanilla()) return false;
        surface.dispatchClaimedPointer(pointerClaim, ScenePointerAction.MOVE,
                logicalX(callbackX), logicalY(callbackY), pointerButton,
                stateReader.shift(), stateReader.control());
        return true;
    }

    boolean pointerUp(int callbackX, int callbackY, int nativeButton) {
        if (closed || pointerClaim == null) return false;
        ContainerInputClaim claim = pointerClaim;
        SceneMouseButton button = pointerButton != SceneMouseButton.NONE
                ? pointerButton : mapButton(nativeButton);
        pointerClaim = null;
        pointerButton = SceneMouseButton.NONE;
        GuiContainerScenePhaseHook.tempLog("pointer UP logical=({}, {}) owner={}",
                Integer.valueOf(logicalX(callbackX)), Integer.valueOf(logicalY(callbackY)), claim.owner());
        if (!claim.consumesVanilla()) return false;
        surface.dispatchClaimedPointer(claim, ScenePointerAction.BUTTON_UP,
                logicalX(callbackX), logicalY(callbackY), button,
                stateReader.shift(), stateReader.control());
        return true;
    }

    boolean keyTyped(int nativeKeyCode) {
        if (closed) return false;
        SceneKey key = LwjglKeyMapper.map(nativeKeyCode);
        boolean shift = stateReader.shift();
        boolean control = stateReader.control();
        ContainerInputClaim claim = nonNull(surface.claimKey(key, shift, control));
        GuiContainerScenePhaseHook.tempLog("key activation native={} key={} owner={} pending={}",
                Integer.valueOf(nativeKeyCode), key, claim.owner(), Boolean.valueOf(surface.isPending()));
        if (!claim.consumesVanilla()) return false;
        surface.dispatchClaimedKey(claim, key, shift, control);
        return true;
    }

    void paintMain(int callbackMouseX, int callbackMouseY, float partialTicks) {
        if (closed) return;
        finishFrame();
        Minecraft minecraft = Minecraft.getMinecraft();
        int width = Math.max(1, minecraft.displayWidth);
        int height = Math.max(1, minecraft.displayHeight);
        compositor.beginFrame();
        snapshots.beginFrame();
        frameContext = UiHostRenderSupport.createRenderContext(width, height,
                logicalX(callbackMouseX), logicalY(callbackMouseY), partialTicks,
                compositor, snapshots, runtimeAdapters);
        overlayPainted = false;
        try {
            replay(surface.paintMain(width, height, logicalX(callbackMouseX), logicalY(callbackMouseY), partialTicks),
                    width, height);
        } catch (RuntimeException exception) {
            finishFrame();
            throw exception;
        } catch (LinkageError error) {
            finishFrame();
            throw error;
        }
        GuiContainerScenePhaseHook.tempLog("phase MAIN screen={} native={}x{}", screenLabel,
                Integer.valueOf(width), Integer.valueOf(height));
    }

    void paintOverlay() {
        if (closed) return;
        if (frameContext == null) {
            GuiContainerScenePhaseHook.tempLog("phase drift: OVERLAY reached without MAIN screen={}",
                    screenLabel);
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            replay(surface.paintOverlay(), Math.max(1, minecraft.displayWidth), Math.max(1, minecraft.displayHeight));
            overlayPainted = true;
        } catch (RuntimeException exception) {
            finishFrame();
            throw exception;
        } catch (LinkageError error) {
            finishFrame();
            throw error;
        }
        GuiContainerScenePhaseHook.tempLog("phase OVERLAY screen={}", screenLabel);
    }

    void finishDraw() {
        if (frameContext == null) {
            GuiContainerScenePhaseHook.tempLog("phase drift: DRAW_TAIL reached without MAIN screen={}", screenLabel);
            return;
        }
        if (!overlayPainted) {
            GuiContainerScenePhaseHook.tempLog("phase drift: DRAW_TAIL reached without OVERLAY screen={}", screenLabel);
        }
        finishFrame();
    }

    void finishFrame() {
        if (frameContext == null) return;
        try {
            snapshots.finishFrame();
        } finally {
            try {
                compositor.finishFrame();
            } finally {
                frameContext = null;
                overlayPainted = false;
            }
        }
    }

    /** 幂等 detach，发送 pointer CANCEL 并释放 scene/渲染资源。 */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (pointerClaim != null && pointerClaim.consumesVanilla()) {
            surface.dispatchClaimedPointer(pointerClaim, ScenePointerAction.CANCEL,
                    0, 0, pointerButton, false, false);
        }
        pointerClaim = null;
        pointerButton = SceneMouseButton.NONE;
        try {
            finishFrame();
            surface.dispose();
        } finally {
            try {
                compositor.close();
            } finally {
                snapshots.close();
                GuiContainer boundScreen = screenReference.get();
                if (boundScreen != null) GuiContainerScenePhaseHook.detach(boundScreen, this);
                GuiContainerScenePhaseHook.tempLog("detach/dispose screen={}", screenLabel);
            }
        }
    }

    private void replay(PaintPlan plan, int width, int height) {
        if (plan == null || frameContext == null) return;
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            try {
                GL11.glLoadIdentity();
                GL11.glOrtho(0.0D, width, height, 0.0D, -1000.0D, 1000.0D);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                try {
                    GL11.glLoadIdentity();
                    UiHostRenderSupport.prepareMainUiRenderState();
                    replayer.replay(plan, frameContext);
                } finally {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                }
            } finally {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glMatrixMode(previousMatrixMode);
            GL11.glPopAttrib();
        }
    }

    private int logicalX(int callbackX) {
        return stateReader.mouseX();
    }

    private int logicalY(int callbackY) {
        return stateReader.mouseY();
    }

    private static ContainerInputClaim nonNull(ContainerInputClaim claim) {
        return claim == null ? ContainerInputClaim.none() : claim;
    }

    private static SceneMouseButton mapButton(int button) {
        switch (button) {
            case 0: return SceneMouseButton.LEFT;
            case 1: return SceneMouseButton.RIGHT;
            case 2: return SceneMouseButton.MIDDLE;
            case 3: return SceneMouseButton.BUTTON_4;
            case 4: return SceneMouseButton.BUTTON_5;
            default: return SceneMouseButton.NONE;
        }
    }
}
