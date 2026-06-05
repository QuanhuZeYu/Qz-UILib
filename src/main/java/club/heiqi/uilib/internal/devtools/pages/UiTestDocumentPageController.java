package club.heiqi.uilib.internal.devtools.pages;

import java.util.Objects;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.net.transport.NetTransportFactory;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `/qzuilib test` 视觉优先测试矩阵首页控制器。
 */
public final class UiTestDocumentPageController extends DocumentPageController {

    private final DocumentPageAuthoringSurface diagnosticPage;
    private final DocumentPageRuntimeView runtimeView;
    private final UiTestMatrixRegistry registry;
    private final UiTestSemanticChecker semanticChecker;
    private final UiTestMatrixState matrixState;
    private final UiTestGroupVisualBuilder visualBuilder;
    private final UiDocument document;
    private final ElementNode rootElement;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final int fontEpoch;
    private final String defaultTextMode;
    private final String runtimeAdapterSummary;
    private UiTestPageBindings pageBindings = UiTestPageBindings.empty();

    /**
     * 创建 test 视觉矩阵首页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param diagnosticPage 文档页面壳
     * @param runtimeView 宿主运行时视图
     */
    public UiTestDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface diagnosticPage,
            DocumentPageRuntimeView runtimeView) {
        DocumentUiScope resolvedDocumentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.diagnosticPage = Objects.requireNonNull(diagnosticPage, "diagnosticPage");
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        TextMeasureService textMeasureService = resolvedDocumentUi.getTextMeasureService();
        this.fontEpoch = textMeasureService.getEpoch();
        this.defaultTextMode = String.valueOf(resolvedDocumentUi.getDefaultTextContentMode());
        this.runtimeAdapterSummary = buildRuntimeAdapterSummary(resolvedDocumentUi);
        this.registry = UiTestMatrixRegistry.createDefault();
        this.semanticChecker = new UiTestSemanticChecker();
        this.matrixState = UiTestMatrixState.create(registry, semanticChecker);
        this.visualBuilder = new UiTestGroupVisualBuilder(registry, matrixState, semanticChecker);

        this.document = UiDocument.create();
        document.setDefaultTextContentMode(resolvedDocumentUi.getDefaultTextContentMode());
        this.rootElement = document.getRootElement();
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 760, 520, textMeasureService);
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        visualBuilder.applyRootStyle(rootElement);
        showHomePage();
    }

    /**
     * 配置 test 页面宿主尺寸。
     */
    @Override
    public void configureDocumentPage() {
        diagnosticPage.setContentWidthRange(720, 1120)
                .setMinContentHeight(540)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    /**
     * 挂载 HTML-like test 首页文档。
     */
    @Override
    public void buildDocument() {
        diagnosticPage.addBlock(htmlLikeDocumentWidget);
    }

    /**
     * 每帧刷新首页环境信息。
     */
    @Override
    public void beforeDocumentFrame() {
        if (pageBindings.getEnvironmentText() != null) {
            pageBindings.getEnvironmentText().setText(buildEnvironmentText());
        }
    }

    /**
     * 返回当前首页使用的 HTML-like 文档适配组件。
     *
     * @return HTML-like 文档适配组件
     */
    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    /**
     * 返回当前测试矩阵 registry，供 JVM 测试验证模型边界。
     *
     * @return 测试矩阵 registry
     */
    UiTestMatrixRegistry getRegistry() {
        return registry;
    }

    /**
     * 返回当前测试矩阵状态，供 JVM 测试验证双维度状态。
     *
     * @return 测试矩阵状态
     */
    UiTestMatrixState getMatrixState() {
        return matrixState;
    }

    /**
     * 显示视觉矩阵首页。
     */
    private void showHomePage() {
        rootElement.clearChildren();
        pageBindings = visualBuilder.buildHomePage(document, rootElement, buildEnvironmentText(), createNavigation());
    }

    /**
     * 显示指定分组的视觉样例页壳。
     *
     * @param group 测试分组规格
     */
    private void showGroupPage(UiTestGroupSpec group) {
        rootElement.clearChildren();
        pageBindings = visualBuilder.buildGroupPage(document, rootElement, group, buildEnvironmentText(),
                createNavigation());
    }

    /**
     * 创建页面导航回调。
     *
     * @return 页面导航回调
     */
    private UiTestGroupVisualBuilder.NavigationHandler createNavigation() {
        return new UiTestGroupVisualBuilder.NavigationHandler() {
            @Override
            public void openHome() {
                showHomePage();
            }

            @Override
            public void openGroup(UiTestGroupSpec group) {
                showGroupPage(group);
            }
        };
    }

    /**
     * 构建环境信息文本。
     *
     * @return 环境信息文本
     */
    private String buildEnvironmentText() {
        UiRuntimeStats stats = runtimeView.getUiRuntimeStats();
        String statsSummary = stats == null ? "无统计" : "frame=" + formatMs(stats.getFrameTimeMs())
                + "ms, render=" + formatMs(stats.getRenderTimeMs()) + "ms";
        return "Minecraft=1.7.10；Forge=GTNH/Forge 运行时；LWJGL3ify=org.lwjglx 输入桥；字体 epoch="
                + fontEpoch + "；默认文本模式=" + defaultTextMode
                + "；窗口尺寸=" + runtimeView.getHostWidth() + "x" + runtimeView.getHostHeight()
                + "；鼠标=" + runtimeView.getMouseX() + "," + runtimeView.getMouseY()
                + "；网络传输模式=" + NetTransportFactory.resolveName(Config.netTransport)
                + "；运行时适配器=" + runtimeAdapterSummary
                + "；运行时统计=" + statsSummary;
    }

    /**
     * 构建运行时适配器摘要。
     *
     * @param documentUi 文档组件作用域
     * @return 运行时适配器摘要
     */
    private String buildRuntimeAdapterSummary(DocumentUiScope documentUi) {
        boolean hasInventoryRenderer = documentUi.getRuntimeAdapters().getInventorySlotGridItemRenderer() != null;
        boolean hasHostImageRenderer = documentUi.getRuntimeAdapters().getHostImageRenderer() != null;
        return "inventoryRenderer=" + hasInventoryRenderer + ", hostImageRenderer=" + hasHostImageRenderer;
    }

    /**
     * 格式化毫秒数。
     *
     * @param value 毫秒值
     * @return 格式化文本
     */
    private String formatMs(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", Double.valueOf(value));
    }
}
