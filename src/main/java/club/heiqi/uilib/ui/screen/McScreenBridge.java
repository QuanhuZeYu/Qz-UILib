package club.heiqi.uilib.ui.screen;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.host.DocumentHostRenderSupport;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.scene.UiSurface;

/**
 * Minecraft GuiScreen 到平台无关 scene 渲染面的桥接外壳。
 */
public abstract class McScreenBridge extends GuiScreen {

    private static final Method KEYBOARD_ENABLE_REPEAT_EVENTS = resolveKeyboardEnableRepeatEvents();
    private static final int KEY_ESCAPE = 1;

    private final GuiScreen returnScreen;
    private final UiSurface surface;

    /** 跨帧复用的绘制上下文合成器，避免每帧借用离屏资源后无法集中释放。 */
    private final PaintContextCompositor paintContextCompositor = new PaintContextCompositor();

    /** 跨帧复用的主图层快照服务，随屏幕关闭统一释放持有的渲染资源。 */
    private final UiMainLayerSnapshotService mainLayerSnapshotService = new UiMainLayerSnapshotService();

    /**
     * 创建 MC 屏幕桥接外壳。
     *
     * @param returnScreen 关闭后返回的父界面
     * @param surface scene 渲染面
     */
    protected McScreenBridge(GuiScreen returnScreen, UiSurface surface) {
        this.returnScreen = returnScreen;
        this.surface = surface;
    }

    @Override
    public void initGui() {
        enableRepeatEventsReflectively(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Minecraft minecraft = Minecraft.getMinecraft();
        int nativeWidth = Math.max(1, minecraft.displayWidth);
        int nativeHeight = Math.max(1, minecraft.displayHeight);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        try {
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, nativeWidth, nativeHeight, 0.0D, -1000.0D, 1000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            try {
                GL11.glLoadIdentity();
                DocumentHostRenderSupport.prepareMainUiRenderState();
                paintContextCompositor.beginFrame();
                mainLayerSnapshotService.beginFrame();
                try {
                    UiRenderContext context = new UiRenderContext(nativeWidth, nativeHeight, mouseX, mouseY,
                            partialTicks, paintContextCompositor, mainLayerSnapshotService);
                    surface.render(nativeWidth, nativeHeight, context, 0, 0);
                } finally {
                    mainLayerSnapshotService.finishFrame();
                    paintContextCompositor.finishFrame();
                }
            } finally {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        surface.onKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
        if (keyCode == KEY_ESCAPE && returnScreen != null) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.currentScreen == null) {
                minecraft.displayGuiScreen(returnScreen);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        try {
            try {
                surface.dispose();
            } finally {
                try {
                    paintContextCompositor.close();
                } finally {
                    mainLayerSnapshotService.close();
                }
            }
        } finally {
            enableRepeatEventsReflectively(false);
            super.onGuiClosed();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * 返回当前桥接的 scene 渲染面，供子类接入宿主旁路桥。
     *
     * @return scene 渲染面
     */
    protected UiSurface getSurface() {
        return surface;
    }

    /**
     * 通过反射调用 Keyboard.enableRepeatEvents。
     *
     * @param enable true 启用键盘重复，false 关闭
     */
    private static void enableRepeatEventsReflectively(boolean enable) {
        if (KEYBOARD_ENABLE_REPEAT_EVENTS == null) {
            return;
        }
        try {
            KEYBOARD_ENABLE_REPEAT_EVENTS.invoke(null, Boolean.valueOf(enable));
        } catch (Exception exception) {
            // 静默降级。
        }
    }

    private static Method resolveKeyboardEnableRepeatEvents() {
        Class<?> keyboardClass = resolveKeyboardClass();
        if (keyboardClass == null) {
            return null;
        }
        try {
            return keyboardClass.getMethod("enableRepeatEvents", boolean.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Class<?> resolveKeyboardClass() {
        try {
            return Class.forName("org.lwjglx.input.Keyboard");
        } catch (Exception exception) {
            try {
                return Class.forName("org.lwjgl.input.Keyboard");
            } catch (Exception fallbackException) {
                return null;
            }
        }
    }
}
