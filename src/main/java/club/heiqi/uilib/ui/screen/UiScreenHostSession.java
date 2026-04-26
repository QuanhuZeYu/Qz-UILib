package club.heiqi.uilib.ui.screen;

import java.util.List;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.control.ViewportWidget;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputRouter;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.render.UiRenderTarget;
import club.heiqi.uilib.ui.widget.WidgetBuildAttachmentTransaction;
import club.heiqi.uilib.ui.widget.UiLayoutInvalidationRegistry;

/**
 * 屏幕宿主运行时会话。
 *
 * <p>负责维护根组件、输入路由、离屏渲染目标以及最近鼠标位置，保证 `GuiScreen` 生命周期
 * 与 UI 宿主行为顺序保持一致，同时把运行时细节从 `BaseScreen` 中剥离出去。</p>
 */
final class UiScreenHostSession {

    private final BaseScreen screen;
    private final ViewportWidget rootWidget = new ViewportWidget();
    private final UiInputRouter inputRouter = new UiInputRouter();
    private final UiHostBackgroundBlurRenderer backgroundBlurRenderer = new UiHostBackgroundBlurRenderer();
    private UiRenderTarget renderTarget;
    private UiRenderTarget deferredPostMainRenderTarget;

    /**
     * 标记宿主会话是否已完成打开流程。
     */
    private boolean sessionOpened;
    private boolean uiBuilt;
    private WidgetBuildAttachmentTransaction buildAttachmentTransaction;
    private int latestMouseX;
    private int latestMouseY;

    UiScreenHostSession(BaseScreen screen) {
        this.screen = screen;
    }

    /**
     * 打开宿主会话并初始化根组件树。
     */
    void open() {
        boolean firstOpen = !sessionOpened;
        try {
            if (firstOpen) {
                beginSessionOpen();
            }

            int[] nativeSize = syncHostSize();
            int nativeWidth = nativeSize[0];
            int nativeHeight = nativeSize[1];

            if (!uiBuilt) {
                UiPerformanceMonitor.getInstance().resetHistory(getRuntimeScreenName());
                buildAttachmentTransaction = WidgetBuildAttachmentTransaction.beginBuildAttempt();
                screen.buildUi(rootWidget);
                buildAttachmentTransaction.commit();
                uiBuilt = true;
            }
            screen.onResize(nativeWidth, nativeHeight);
            sessionOpened = true;
        } catch (RuntimeException exception) {
            if (firstOpen) {
                rollbackOpenFailure();
            }
            throw exception;
        } catch (Error error) {
            if (firstOpen) {
                rollbackOpenFailure();
            }
            throw error;
        }
    }

    /**
     * 渲染当前宿主会话。
     *
     * @param guiWidth GUI 逻辑宽度
     * @param guiHeight GUI 逻辑高度
     * @param partialTicks 部分帧插值
     */
    void render(int guiWidth, int guiHeight, float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int nativeWidth = Math.max(1, minecraft.displayWidth);
        int nativeHeight = Math.max(1, minecraft.displayHeight);
        UiRenderTarget renderTarget = getOrCreateRenderTarget();
        UiRenderTarget deferredPostMainRenderTarget = getOrCreateDeferredPostMainRenderTarget();
        renderTarget.ensureSize(nativeWidth, nativeHeight);
        deferredPostMainRenderTarget.ensureSize(nativeWidth, nativeHeight);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        UiPerformanceMonitor performanceMonitor = UiPerformanceMonitor.getInstance();
        performanceMonitor.beginFrame(getRuntimeScreenName(), guiWidth, guiHeight, nativeWidth, nativeHeight);

        try {
            long renderStartNanos = System.nanoTime();
            try {
                backgroundBlurRenderer.captureCurrentFramebuffer(nativeWidth, nativeHeight);
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
                            backgroundBlurRenderer.drawBlurredBackground(nativeWidth, nativeHeight);
                            prepareMainUiRenderState();
                            UiRenderContext context = new UiRenderContext(nativeWidth, nativeHeight, latestMouseX,
                                    latestMouseY, partialTicks);
                            rootWidget.render(context);
                            flushDeferredPostMainPasses(context, deferredPostMainRenderTarget, nativeWidth,
                                    nativeHeight);
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
                renderTarget.drawToScreen(guiWidth, guiHeight);
            } finally {
                performanceMonitor.recordPresentPhase(System.nanoTime() - presentStartNanos);
            }
        } finally {
            performanceMonitor.finishFrame();
        }
    }

    /**
     * 关闭宿主会话并释放资源。
     */
    void close() {
        if (!sessionOpened) {
            return;
        }

        sessionOpened = false;
        UiInputService.getInstance().endTextInput();
        inputRouter.reset();
        closeRenderTarget();
        UiLayoutInvalidationRegistry.unregisterRoot(rootWidget);
    }

    /**
     * 执行首次打开所需的一次性副作用。
     */
    private void beginSessionOpen() {
        UiInputService.getInstance().beginTextInput();
        UiLayoutInvalidationRegistry.registerRoot(rootWidget);
    }

    /**
     * 同步宿主原生尺寸与根视口布局边界。
     *
     * @return 当前原生宽高
     */
    private int[] syncHostSize() {
        int nativeWidth = Math.max(1, Minecraft.getMinecraft().displayWidth);
        int nativeHeight = Math.max(1, Minecraft.getMinecraft().displayHeight);
        rootWidget.applyLayoutBounds(0, 0, nativeWidth, nativeHeight);
        return new int[] { nativeWidth, nativeHeight };
    }

    /**
     * 在首次打开失败时回滚一次性副作用，避免留下半打开状态。
     */
    private void rollbackOpenFailure() {
        sessionOpened = false;
        uiBuilt = false;
        UiInputService.getInstance().endTextInput();
        inputRouter.reset();
        closeRenderTarget();
        if (buildAttachmentTransaction != null) {
            buildAttachmentTransaction.rollback();
            buildAttachmentTransaction = null;
        }
        UiLayoutInvalidationRegistry.unregisterRoot(rootWidget);
    }

    /**
     * 按需创建离屏渲染目标，避免在宿主构造期触碰渲染运行时。
     *
     * @return 可用的离屏渲染目标
     */
    private UiRenderTarget getOrCreateRenderTarget() {
        if (renderTarget == null) {
            renderTarget = new UiRenderTarget();
        }
        return renderTarget;
    }

    /**
     * 按需创建主渲染结束后的补充离屏目标。
     *
     * @return 主后置补充离屏渲染目标
     */
    private UiRenderTarget getOrCreateDeferredPostMainRenderTarget() {
        if (deferredPostMainRenderTarget == null) {
            deferredPostMainRenderTarget = new UiRenderTarget();
        }
        return deferredPostMainRenderTarget;
    }

    /**
     * 关闭已创建的离屏渲染目标。
     */
    private void closeRenderTarget() {
        if (renderTarget == null) {
            if (deferredPostMainRenderTarget != null) {
                deferredPostMainRenderTarget.close();
                deferredPostMainRenderTarget = null;
            }
            backgroundBlurRenderer.close();
            return;
        }
        renderTarget.close();
        renderTarget = null;
        if (deferredPostMainRenderTarget != null) {
            deferredPostMainRenderTarget.close();
            deferredPostMainRenderTarget = null;
        }
        backgroundBlurRenderer.close();
    }

    /**
     * 在主 UI 层完成后回放补充绘制层，再把第二个 FBO 贴回主层。
     *
     * <p>这里故意把回放放在主 FBO 仍然绑定的阶段执行：
     * 先保持主层 alpha 只由控件底图建立，
     * 再把第二个 FBO 的预合成 RGB 回贴回来，同时完全保留主层 coverage alpha。</p>
     */
    private void flushDeferredPostMainPasses(UiRenderContext context, UiRenderTarget deferredRenderTarget,
            int nativeWidth, int nativeHeight) {
        if (context == null || deferredRenderTarget == null || !context.hasDeferredPostMainPasses()) {
            return;
        }

        List<UiRenderContext.DeferredPostMainPass> deferredPasses = context.drainDeferredPostMainPasses();
        if (deferredPasses.isEmpty()) {
            return;
        }

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        deferredRenderTarget.begin();
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
                    for (UiRenderContext.DeferredPostMainPass deferredPass : deferredPasses) {
                        applyDeferredPostMainClip(deferredPass.getClipSnapshot(), nativeHeight);
                        deferredPass.replay();
                    }
                    UiRenderContext.clearClipState();
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
            deferredRenderTarget.end();
            GL11.glMatrixMode(previousMatrixMode);
        }

        deferredRenderTarget.compositeToCurrentFramebuffer();
    }

    /**
     * 准备主 UI 层的稳定 2D OpenGL 状态。
     *
     * <p>宿主世界渲染会把 depth/cull/alpha 等状态留在不可预期的组合中；主 UI 绘制必须在进入
     * widget 树之前统一清理，否则圆角填充这类面片几何可能被背面剔除，只剩线框可见。</p>
     */
    private static void prepareMainUiRenderState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 将主 UI 渲染阶段记录的 clip/scissor 状态回放到物品层。
     *
     * @param clipSnapshot 裁剪快照；为空时表示当前批次不裁剪
     * @param screenHeight 当前原生屏幕高度
     */
    private void applyDeferredPostMainClip(UiRenderContext.ClipSnapshot clipSnapshot, int screenHeight) {
        // 物品层重放必须复用主层已经解析好的裁剪快照，否则卡片圆角只会裁掉底图，
        // 延迟回放的物品图标仍会从卡片拐角露出来。
        UiRenderContext.applyClipSnapshot(clipSnapshot, screenHeight);
    }

    /**
     * 路由一帧输入快照。
     *
     * @param frame 输入快照
     */
    void handleInputFrame(UiInputFrame frame) {
        if (frame == null) {
            return;
        }
        latestMouseX = frame.getMouseX();
        latestMouseY = frame.getMouseY();
        UiPerformanceMonitor performanceMonitor = UiPerformanceMonitor.getInstance();
        performanceMonitor.beginInputRouting(getRuntimeScreenName(), frame);
        try {
            inputRouter.route(rootWidget, frame);
        } finally {
            performanceMonitor.finishInputRouting();
        }
    }

    /**
     * 返回当前会话使用的稳定页面标识。
     *
     * @return 运行时页面标识
     */
    private String getRuntimeScreenName() {
        return UiDocumentScreens.runtimeScreenNameOf(screen);
    }

    /**
     * 清理当前交互状态。
     */
    void clearInteractionState() {
        inputRouter.clearInteractionState();
    }

    /**
     * 更新根视口统一留白。
     *
     * @param padding 四边统一留白
     */
    void setRootPadding(int padding) {
        rootWidget.applyViewportPadding(padding);
    }

    /**
     * 更新根视口统一留白。
     *
     * @param left 左侧留白
     * @param top 上侧留白
     * @param right 右侧留白
     * @param bottom 下侧留白
     */
    void setRootPadding(int left, int top, int right, int bottom) {
        rootWidget.applyViewportPadding(left, top, right, bottom);
    }

    /**
     * 更新根视口布局边界。
     *
     * @param width 宿主宽度
     * @param height 宿主高度
     */
    void applyRootBounds(int width, int height) {
        rootWidget.applyLayoutBounds(0, 0, width, height);
    }
}
