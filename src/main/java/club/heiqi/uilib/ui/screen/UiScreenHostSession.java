package club.heiqi.uilib.ui.screen;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.host.UiHostRenderSupport;
import club.heiqi.uilib.ui.input.UiInputRouter;
import club.heiqi.uilib.ui.input.UiKeyboardCaptureState;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.render.BackdropBlurPolicy;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.render.UiRenderTarget;
import club.heiqi.uilib.ui.screen.internal.InternalScreenIdentity;
import club.heiqi.uilib.ui.widget.WidgetBuildAttachmentTransaction;
import club.heiqi.uilib.ui.widget.UiLayoutInvalidationRegistry;
import club.heiqi.uilib.ui.widget.ViewportWidget;

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
    private int latestMouseX;
    private int latestMouseY;
    private final UiHostBackgroundBlurRenderer backgroundBlurRenderer = new UiHostBackgroundBlurRenderer();
    private final PaintContextCompositor paintContextCompositor = new PaintContextCompositor();
    private final UiMainLayerSnapshotService mainLayerSnapshotService = new UiMainLayerSnapshotService();
    private UiRenderTarget renderTarget;
    private UiRenderTarget deferredPostMainRenderTarget;

    /**
     * 标记宿主会话是否已完成打开流程。
     */
    private boolean sessionOpened;
    private boolean closeRetryPending;
    private boolean uiBuilt;
    private WidgetBuildAttachmentTransaction buildAttachmentTransaction;
    private int latestHostWidth;
    private int latestHostHeight;

    UiScreenHostSession(BaseScreen screen) {
        this.screen = screen;
    }

    /**
     * 打开宿主会话并初始化根组件树。
     */
    void open() {
        if (closeRetryPending) {
            close();
        }
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
            buildAttachmentTransaction = null;
        } catch (RuntimeException exception) {
            if (firstOpen) {
                try {
                    rollbackOpenFailure();
                } catch (RuntimeException cleanupFailure) {
                    if (cleanupFailure != exception) exception.addSuppressed(cleanupFailure);
                } catch (Error cleanupFailure) {
                    if (isFatal(cleanupFailure)) {
                        cleanupFailure.addSuppressed(exception);
                        throw cleanupFailure;
                    }
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw exception;
        } catch (Error error) {
            if (firstOpen) {
                try {
                    rollbackOpenFailure();
                } catch (RuntimeException cleanupFailure) {
                    error.addSuppressed(cleanupFailure);
                } catch (Error cleanupFailure) {
                    if (isFatal(cleanupFailure) && !isFatal(error)) {
                        if (cleanupFailure != error) cleanupFailure.addSuppressed(error);
                        throw cleanupFailure;
                    }
                    if (cleanupFailure != error) error.addSuppressed(cleanupFailure);
                }
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
                BackdropBlurPolicy backdropBlurPolicy = screen.getBackdropBlurPolicy();
                backgroundBlurRenderer.captureCurrentFramebuffer(nativeWidth, nativeHeight, backdropBlurPolicy);
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
                            backgroundBlurRenderer.drawBlurredBackground(nativeWidth, nativeHeight, backdropBlurPolicy);
                            UiHostRenderSupport.prepareMainUiRenderState();
                            paintContextCompositor.beginFrame();
                            mainLayerSnapshotService.beginFrame();
                            UiRenderContext context = UiHostRenderSupport.createRenderContext(nativeWidth,
                                    nativeHeight, latestMouseX,
                                    latestMouseY, partialTicks,
                                    paintContextCompositor, mainLayerSnapshotService, screen.getRuntimeAdapters(),
                                    backdropBlurPolicy);
                            try {
                                rootWidget.render(context);
                                UiHostRenderSupport.DeferredPostMainReplayBatch replayBatch = UiHostRenderSupport
                                        .drainDeferredPostMainReplayBatch(context);
                                if (!replayBatch.isEmpty()) {
                                    deferredPostMainRenderTarget.ensureSize(nativeWidth, nativeHeight);
                                    UiHostRenderSupport.flushDeferredPostMainPasses(replayBatch,
                                            deferredPostMainRenderTarget, nativeWidth, nativeHeight);
                                }
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
        if (!sessionOpened && !closeRetryPending) {
            return;
        }

        sessionOpened = false;
        closeRetryPending = true;
        Throwable[] failure = new Throwable[1];
        closeStep(failure, () -> {
            UiKeyboardCaptureState.getInstance().setScreenTextInputRequested(false);
            if (!UiKeyboardCaptureState.getInstance().shouldKeepTextInputActive()) {
                UiInputService.getInstance().endTextInput();
            }
        });
        closeStep(failure, inputRouter::clearInteractionState);
        closeStep(failure,
                () -> UiKeyboardCaptureState.getInstance().setScreenKeyboardCaptured(false));
        closeStep(failure, this::closeRenderTarget);
        closeStep(failure, this::rollbackBuildAttachmentTransaction);
        closeStep(failure, () -> UiLayoutInvalidationRegistry.unregisterRoot(rootWidget));
        if (failure[0] == null) {
            closeRetryPending = false;
        }
        rethrowCloseFailure(failure[0]);
    }

    /**
     * 执行首次打开所需的一次性副作用。
     */
    private void beginSessionOpen() {
        UiKeyboardCaptureState.getInstance().setScreenTextInputRequested(true);
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
        latestHostWidth = nativeWidth;
        latestHostHeight = nativeHeight;
        rootWidget.applyLayoutBounds(0, 0, nativeWidth, nativeHeight);
        return new int[] { nativeWidth, nativeHeight };
    }

    /**
     * 在首次打开失败时回滚一次性副作用，避免留下半打开状态。
     */
    private void rollbackOpenFailure() {
        sessionOpened = false;
        closeRetryPending = true;
        Throwable[] failure = new Throwable[1];
        closeStep(failure, () -> {
            UiKeyboardCaptureState.getInstance().setScreenTextInputRequested(false);
            if (!UiKeyboardCaptureState.getInstance().shouldKeepTextInputActive()) {
                UiInputService.getInstance().endTextInput();
            }
        });
        closeStep(failure, inputRouter::clearInteractionState);
        closeStep(failure,
                () -> UiKeyboardCaptureState.getInstance().setScreenKeyboardCaptured(false));
        closeStep(failure, this::closeRenderTarget);
        closeStep(failure, this::rollbackBuildAttachmentTransaction);
        closeStep(failure, () -> UiLayoutInvalidationRegistry.unregisterRoot(rootWidget));
        if (failure[0] == null) {
            closeRetryPending = false;
        }
        rethrowCloseFailure(failure[0]);
    }

    private void rollbackBuildAttachmentTransaction() {
        if (buildAttachmentTransaction == null) {
            return;
        }
        WidgetBuildAttachmentTransaction transaction = buildAttachmentTransaction;
        transaction.rollback();
        buildAttachmentTransaction = null;
        uiBuilt = false;
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
        Throwable[] failure = new Throwable[1];
        if (renderTarget != null) {
            UiRenderTarget current = renderTarget;
            try {
                current.close();
                renderTarget = null;
            } catch (RuntimeException closeFailure) {
                rememberCloseFailure(failure, closeFailure);
            } catch (Error closeFailure) {
                rememberCloseFailure(failure, closeFailure);
            }
        }
        try {
            UiHostRenderSupport.closeSharedRenderResources(paintContextCompositor, mainLayerSnapshotService,
                    deferredPostMainRenderTarget);
            deferredPostMainRenderTarget = null;
        } catch (RuntimeException closeFailure) {
            rememberCloseFailure(failure, closeFailure);
        } catch (Error closeFailure) {
            rememberCloseFailure(failure, closeFailure);
        }
        try {
            backgroundBlurRenderer.close();
        } catch (RuntimeException closeFailure) {
            rememberCloseFailure(failure, closeFailure);
        } catch (Error closeFailure) {
            rememberCloseFailure(failure, closeFailure);
        }
        rethrowCloseFailure(failure[0]);
    }

    private static void closeStep(Throwable[] firstFailure, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            rememberCloseFailure(firstFailure, failure);
        } catch (Error failure) {
            rememberCloseFailure(firstFailure, failure);
        }
    }

    private static void rememberCloseFailure(Throwable[] firstFailure, Throwable failure) {
        if (firstFailure[0] == null) {
            firstFailure[0] = failure;
        } else if (isFatal(failure) && !isFatal(firstFailure[0])) {
            if (firstFailure[0] != failure) failure.addSuppressed(firstFailure[0]);
            firstFailure[0] = failure;
        } else if (firstFailure[0] != failure) {
            firstFailure[0].addSuppressed(failure);
        }
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof Error && !(failure instanceof LinkageError);
    }

    private static void rethrowCloseFailure(Throwable failure) {
        if (failure == null) return;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
        throw new IllegalStateException("screen host close failed", failure);
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
        UiKeyboardCaptureState.getInstance().setScreenKeyboardCaptured(inputRouter.hasFocusedWidget());
    }

    /**
     * 返回当前会话使用的稳定页面标识。
     *
     * @return 运行时页面标识
     */
    private String getRuntimeScreenName() {
        return InternalScreenIdentity.runtimeScreenNameOf(screen);
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

    /**
     * 返回最近一次输入路由记录的鼠标 X。
     *
     * @return 鼠标 X
     */
    int getLatestMouseX() {
        return latestMouseX;
    }

    /**
     * 返回最近一次输入路由记录的鼠标 Y。
     *
     * @return 鼠标 Y
     */
    int getLatestMouseY() {
        return latestMouseY;
    }

    /**
     * 返回最近一次同步的宿主原生宽度。
     *
     * @return 宿主原生宽度
     */
    int getLatestHostWidth() {
        return latestHostWidth;
    }

    /**
     * 返回最近一次同步的宿主原生高度。
     *
     * @return 宿主原生高度
     */
    int getLatestHostHeight() {
        return latestHostHeight;
    }
}
