package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.component.UiComponentRuntime;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.BaseScreen;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 声明式三基石（条件 {@code show} / 列表 {@code forEach} / 文本 {@code bindText}）真机接入试点页的
 * <b>GuiScreen 宿主壳</b>。
 *
 * <p>本类只负责 Minecraft 宿主生命周期（构造 widget、注入文档、resize、ESC 返回、关闭收口），声明式三基石的
 * 全部逻辑在 {@link ReactiveTriadDemoView}（纯组件层，可脱离宿主单测）。这与
 * {@code ModernConfigTemplateScreen} 持有 {@code ModernConfigSearchFilter} 的「屏幕宿主 + 可测组件」分工一致。</p>
 *
 * <p><b>关闭收口</b>：{@link #onGuiClosed()} → {@link #cleanupResources()} 调 {@code documentWidget.close()}
 * 回收 forEach/show/bindText 的 effect 与各作用域，并调 {@link ReactiveTriadDemoView#dispose()} 清理构造期
 * Computed，否则 effect 泄漏到全局调度器被其它页帧循环持续重跑。</p>
 */
final class ReactiveTriadDemoScreen extends BaseScreen {

    private final GuiScreen parentScreen;
    private final UiDocument document;
    private final HtmlLikeDocumentWidget documentWidget;
    private final ReactiveTriadDemoView view;

    /**
     * 创建响应式三基石 demo 页。
     *
     * @param parentScreen 父界面（test 页），ESC / 返回按钮回退到它
     */
    ReactiveTriadDemoScreen(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;

        this.document = UiDocument.create();
        this.documentWidget = new HtmlLikeDocumentWidget(document, 720, 600,
                DefaultTextMeasureService.getInstance());
        this.documentWidget.setViewportRootScrollingEnabled(true);
        this.documentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));

        UiComponentRuntime runtime = documentWidget.getComponentRuntime();
        this.view = new ReactiveTriadDemoView(document, runtime);
        document.getRootElement().append(view.getRootElement());
    }

    /**
     * 打开响应式三基石 demo 页（供 {@code /qzuilib test} 的 REACTIVE 组样例按钮调用）。
     *
     * <p>以当前界面（{@code Minecraft.currentScreen}）为 parentScreen，经 {@link UiScreenManager}
     * 在下一帧切换，避免在事件分发中途直接换屏。</p>
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final ReactiveTriadDemoScreen demoScreen = new ReactiveTriadDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }

    @Override
    protected void buildUi(Widget root) {
        root.addChild(documentWidget);
    }

    @Override
    protected void onResize(int width, int height) {
        super.onResize(width, height);
        setRootPadding(0, 0, 0, 0);
        documentWidget.applyLayoutBounds(0, 0, Math.max(0, width), Math.max(0, height));
    }

    @Override
    public void handleInputFrame(UiInputFrame frame) {
        if (handleEscapeShortcut(frame)) {
            return;
        }
        super.handleInputFrame(frame);
    }

    @Override
    public void onGuiClosed() {
        try {
            cleanupResources();
        } finally {
            super.onGuiClosed();
        }
    }

    private void cleanupResources() {
        view.clearHandlers();
        // 回收响应式运行时：dispose forEach/show/bindText 的 reconcile effect 与各作用域。
        documentWidget.close();
        // 清理 view 构造期创建的 Computed（不归属运行时根作用域，需单独 dispose 防订阅泄漏）。
        view.dispose();
    }

    private boolean handleEscapeShortcut(UiInputFrame frame) {
        if (frame == null) {
            return false;
        }
        for (UiKeyEvent keyEvent : frame.getKeyEvents()) {
            if (keyEvent == null || keyEvent.getAction() != UiKeyEvent.Action.PRESSED) {
                continue;
            }
            if (keyEvent.getKeyCode() == UiKeyCodes.KEY_ESCAPE) {
                requestClose();
                return true;
            }
        }
        return false;
    }

    private void requestClose() {
        final GuiScreen targetScreen = parentScreen;
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft != null) {
                    minecraft.displayGuiScreen(targetScreen);
                }
            }
        });
    }
}
