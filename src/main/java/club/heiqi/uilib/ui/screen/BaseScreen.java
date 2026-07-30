package club.heiqi.uilib.ui.screen;

import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.input.UiManagedInputScreen;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.render.BackdropBlurPolicy;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 新 UI 系统的界面基类。
 */
public abstract class BaseScreen extends GuiScreen implements UiManagedInputScreen {

    private final UiScreenHostSession hostSession = new UiScreenHostSession(this);
    /** 仅本 screen 拥有的惰性默认实例；覆写 getter 注入的 shared adapter 不会写入或关闭。 */
    private UiRuntimeAdapters defaultRuntimeAdapters;
    private boolean defaultRuntimeAdaptersClosePending;

    @Override
    public void initGui() {
        if (defaultRuntimeAdaptersClosePending) {
            closeDefaultRuntimeAdapters();
        }
        UiInputService.getInstance().setHostKeyboardRepeatEnabled(true);
        try {
            hostSession.open();
        } catch (RuntimeException exception) {
            UiInputService.getInstance().setHostKeyboardRepeatEnabled(false);
            throw exception;
        } catch (Error error) {
            UiInputService.getInstance().setHostKeyboardRepeatEnabled(false);
            throw error;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        SceneFrameAbortBoundary.run(() -> hostSession.render(width, height, partialTicks));
    }

    @Override
    public void onGuiClosed() {
        Throwable[] failure = new Throwable[1];
        closeStep(failure, hostSession::close);
        closeStep(failure, this::closeDefaultRuntimeAdapters);
        closeStep(failure, () -> UiInputService.getInstance().setHostKeyboardRepeatEnabled(false));
        closeStep(failure, this::closeSuperclassScreen);
        rethrowCloseFailure(failure[0]);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        super.keyTyped(typedChar, keyCode);
        UiInputService.getInstance().submitHostTypedCharacter(typedChar, keyCode);
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
        hostSession.handleInputFrame(frame);
    }

    /**
     * 清理界面交互状态，供切页或重建界面时使用。
     */
    protected void clearInteractionState() {
        hostSession.clearInteractionState();
    }

    /**
     * 设置根视口内边距。
     *
     * <p>页面作者通过该入口声明屏幕级留白，而不直接操作根视口实现类。</p>
     *
     * @param padding 四边统一留白
     */
    protected final void setRootPadding(int padding) {
        hostSession.setRootPadding(padding);
    }

    /**
     * 设置根视口内边距。
     *
     * <p>页面作者通过该入口声明屏幕级留白，而不直接操作根视口实现类。</p>
     *
     * @param left 左侧留白
     * @param top 上侧留白
     * @param right 右侧留白
     * @param bottom 下侧留白
     */
    protected final void setRootPadding(int left, int top, int right, int bottom) {
        hostSession.setRootPadding(left, top, right, bottom);
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
        hostSession.applyRootBounds(width, height);
    }

    /**
     * 获取最近一次完成帧的 UI 运行时统计。
     *
     * @return 运行时统计快照
     */
    protected UiRuntimeStats getUiRuntimeStats() {
        return UiPerformanceMonitor.getInstance().getRuntimeStats();
    }

    /**
     * 返回当前界面注入给 HTML-like 渲染链路的运行时适配器集合。
     *
     * <p>默认实例由当前 screen 独占并在关闭时释放。覆写方法返回的自定义或 shared adapter
     * 生命周期仍归覆写方，基类不会关闭。</p>
     *
     * @return 运行时适配器集合
     */
    protected UiRuntimeAdapters getRuntimeAdapters() {
        if (defaultRuntimeAdaptersClosePending) {
            throw new IllegalStateException("default runtime adapter cleanup retry pending");
        }
        if (defaultRuntimeAdapters == null) {
            defaultRuntimeAdapters = UiRuntimeAdapters.minecraftDefaults();
        }
        return defaultRuntimeAdapters;
    }

    private void closeDefaultRuntimeAdapters() {
        if (defaultRuntimeAdapters == null) {
            defaultRuntimeAdaptersClosePending = false;
            return;
        }
        defaultRuntimeAdaptersClosePending = true;
        defaultRuntimeAdapters.close();
        defaultRuntimeAdapters = null;
        defaultRuntimeAdaptersClosePending = false;
    }

    private void closeSuperclassScreen() {
        super.onGuiClosed();
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
        throw new IllegalStateException("screen close failed", failure);
    }

    /**
     * 返回当前界面使用的页面级背景模糊策略。
     *
     * @return 背景模糊策略
     */
    protected BackdropBlurPolicy getBackdropBlurPolicy() {
        return BackdropBlurPolicy.inheritGlobal();
    }

    /**
     * 返回最近一次输入路由记录的鼠标 X。
     *
     * @return 鼠标 X
     */
    protected int getLatestMouseX() {
        return hostSession.getLatestMouseX();
    }

    /**
     * 返回最近一次输入路由记录的鼠标 Y。
     *
     * @return 鼠标 Y
     */
    protected int getLatestMouseY() {
        return hostSession.getLatestMouseY();
    }

    /**
     * 返回最近一次同步的宿主原生宽度。
     *
     * @return 宿主原生宽度
     */
    protected int getLatestHostWidth() {
        return hostSession.getLatestHostWidth();
    }

    /**
     * 返回最近一次同步的宿主原生高度。
     *
     * @return 宿主原生高度
     */
    protected int getLatestHostHeight() {
        return hostSession.getLatestHostHeight();
    }
}
