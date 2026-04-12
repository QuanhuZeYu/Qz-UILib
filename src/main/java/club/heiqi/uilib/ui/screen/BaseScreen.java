package club.heiqi.uilib.ui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.control.ViewportWidget;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputRouter;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.render.UiRenderTarget;
import club.heiqi.uilib.ui.widget.UiLayoutInvalidationRegistry;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 新 UI 系统的界面基类。
 */
public abstract class BaseScreen extends GuiScreen {

    private final ViewportWidget rootWidget = new ViewportWidget();
    private final UiInputRouter inputRouter = new UiInputRouter();
    private final UiRenderTarget renderTarget = new UiRenderTarget();

    private boolean uiBuilt;
    private int latestMouseX;
    private int latestMouseY;

    @Override
    public void initGui() {
        UiInputService.getInstance().beginTextInput();
        UiLayoutInvalidationRegistry.registerRoot(rootWidget);
        int nativeWidth = Math.max(1, Minecraft.getMinecraft().displayWidth);
        int nativeHeight = Math.max(1, Minecraft.getMinecraft().displayHeight);
        renderTarget.ensureSize(nativeWidth, nativeHeight);
        rootWidget.applyLayoutBounds(0, 0, nativeWidth, nativeHeight);
        if (!uiBuilt) {
            UiPerformanceMonitor.getInstance().resetHistory(getClass().getSimpleName());
        }
        if (!uiBuilt) {
            buildUi(rootWidget);
            uiBuilt = true;
        }
        onResize(nativeWidth, nativeHeight);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Minecraft minecraft = Minecraft.getMinecraft();
        int nativeWidth = Math.max(1, minecraft.displayWidth);
        int nativeHeight = Math.max(1, minecraft.displayHeight);
        renderTarget.ensureSize(nativeWidth, nativeHeight);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        UiPerformanceMonitor performanceMonitor = UiPerformanceMonitor.getInstance();
        performanceMonitor.beginFrame(getClass().getSimpleName(), width, height, nativeWidth, nativeHeight);

        try {
            long renderStartNanos = System.nanoTime();
            try {
                renderTarget.begin();
                try {
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glPushMatrix();
                    try {
                        GL11.glLoadIdentity();
                        GL11.glOrtho(0.0D, nativeWidth, nativeHeight, 0.0D, -1000.0D, 1000.0D);
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        GL11.glPushMatrix();
                        try {
                            GL11.glLoadIdentity();
                            UiRenderContext context = new UiRenderContext(nativeWidth, nativeHeight, latestMouseX, latestMouseY, partialTicks);
                            rootWidget.render(context);
                        } finally {
                            GL11.glMatrixMode(GL11.GL_MODELVIEW);
                            GL11.glPopMatrix();
                        }
                    } finally {
                        GL11.glMatrixMode(GL11.GL_PROJECTION);
                        GL11.glPopMatrix();
                        GL11.glMatrixMode(previousMatrixMode);
                    }
                } finally {
                    renderTarget.end();
                    GL11.glMatrixMode(previousMatrixMode);
                }
            } finally {
                performanceMonitor.recordRenderPhase(System.nanoTime() - renderStartNanos);
            }

            long presentStartNanos = System.nanoTime();
            try {
                renderTarget.drawToScreen(width, height);
            } finally {
                performanceMonitor.recordPresentPhase(System.nanoTime() - presentStartNanos);
            }
        } finally {
            performanceMonitor.finishFrame();
        }
    }

    @Override
    public void onGuiClosed() {
        UiInputService.getInstance().endTextInput();
        inputRouter.reset();
        renderTarget.close();
        UiLayoutInvalidationRegistry.unregisterRoot(rootWidget);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * 接收并分发一帧输入事件。
     *
     * @param frame 输入快照
     */
    public void handleInputFrame(UiInputFrame frame) {
        if (frame == null) {
            return;
        }
        latestMouseX = frame.getMouseX();
        latestMouseY = frame.getMouseY();
        UiPerformanceMonitor performanceMonitor = UiPerformanceMonitor.getInstance();
        performanceMonitor.beginInputRouting(getClass().getSimpleName(), frame);
        try {
            inputRouter.route(rootWidget, frame);
        } finally {
            performanceMonitor.finishInputRouting();
        }
    }

    /**
     * 清理界面交互状态，供切页或重建界面时使用。
     */
    protected void clearInteractionState() {
        inputRouter.clearInteractionState();
    }

    /**
     * 构建界面组件树。
     *
     * @param root 根组件
     */
    protected abstract void buildUi(Widget root);

    /**
     * 在界面尺寸变化时更新组件位置。
     *
     * @param width 界面宽度
     * @param height 界面高度
     */
    protected void onResize(int width, int height) {
        rootWidget.applyLayoutBounds(0, 0, width, height);
    }

    protected Widget getRootWidget() {
        return rootWidget;
    }

    /**
     * 获取最近一次完成帧的 UI 运行时统计。
     *
     * @return 运行时统计快照
     */
    protected UiRuntimeStats getUiRuntimeStats() {
        return UiPerformanceMonitor.getInstance().getRuntimeStats();
    }
}
