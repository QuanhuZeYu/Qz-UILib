package club.heiqi.uilib.ui.screen;

import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiManagedInputScreen;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.render.BackdropBlurPolicy;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 新 UI 系统的界面基类。
 */
public abstract class BaseScreen extends GuiScreen implements UiManagedInputScreen {

    private static final UiRuntimeAdapters DEFAULT_RUNTIME_ADAPTERS = UiRuntimeAdapters.minecraftDefaults();

    private final UiScreenHostSession hostSession = new UiScreenHostSession(this);

    @Override
    public void initGui() {
        hostSession.open();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        hostSession.render(width, height, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        hostSession.close();
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
     * <p>默认使用 Minecraft 宿主能力；需要测试替身或裁剪能力集的界面可覆写该方法。</p>
     *
     * @return 运行时适配器集合
     */
    protected UiRuntimeAdapters getRuntimeAdapters() {
        return DEFAULT_RUNTIME_ADAPTERS;
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
