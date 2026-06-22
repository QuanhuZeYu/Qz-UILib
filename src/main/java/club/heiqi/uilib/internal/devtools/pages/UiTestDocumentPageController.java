package club.heiqi.uilib.internal.devtools.pages;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final UiTestAssertionLogger assertionLogger;
    private final UiTestAssertionRunner assertionRunner;
    private final UiTestGroupVisualBuilder visualBuilder;
    private final UiDocument document;
    private final ElementNode rootElement;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final int fontEpoch;
    private final String defaultTextMode;
    private final String runtimeAdapterSummary;
    private final Map<String, UiTestGroupPageState> groupPageStates = new LinkedHashMap<String, UiTestGroupPageState>();
    private UiTestPageBindings pageBindings = UiTestPageBindings.empty();
    private String lastRunSummary = "尚未运行。";

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
        this.assertionLogger = new UiTestAssertionLogger();
        this.assertionRunner = new UiTestAssertionRunner();
        this.visualBuilder = new UiTestGroupVisualBuilder(registry, matrixState, semanticChecker, assertionLogger);

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
     * 页面宿主关闭时释放 HTML-like 文档适配组件持有的响应式作用域与光标状态。
     */
    @Override
    public void onDocumentClosed() {
        htmlLikeDocumentWidget.close();
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
        clearTopLayerElements();
        rootElement.clearChildren();
        pageBindings = visualBuilder.buildHomePage(document, rootElement, buildEnvironmentText(), lastRunSummary,
                createNavigation());
    }

    /**
     * 显示指定分组的视觉样例页壳。
     *
     * @param group 测试分组规格
     */
    private void showGroupPage(UiTestGroupSpec group) {
        clearTopLayerElements();
        rootElement.clearChildren();
        UiTestGroupPageState pageState = resolveGroupPageState(group);
        pageBindings = visualBuilder.buildGroupPage(document, rootElement, group, buildEnvironmentText(),
                lastRunSummary, createNavigation(), pageState, createGroupInteraction(group, pageState));
    }

    /**
     * 清理上一张样例可能注册的运行时顶层元素，避免页面切换后残留弹层。
     */
    private void clearTopLayerElements() {
        for (ElementNode element : new java.util.ArrayList<ElementNode>(document.__getTopLayerElements())) {
            document.__hideTopLayerElement(element);
        }
    }

    /**
     * 返回指定分组的页内状态。
     *
     * @param group 分组规格
     * @return 分组页状态
     */
    private UiTestGroupPageState resolveGroupPageState(UiTestGroupSpec group) {
        UiTestGroupPageState state = groupPageStates.get(group.getCode());
        if (state == null) {
            state = new UiTestGroupPageState(group.getCode());
            groupPageStates.put(group.getCode(), state);
        }
        state.clampToCaseCount(registry.getCases(group.getCode()).size());
        return state;
    }

    /**
     * 创建分组页交互回调。
     *
     * @param group 当前分组
     * @param pageState 当前分组页状态
     * @return 分组页交互回调
     */
    private UiTestGroupVisualBuilder.GroupInteractionHandler createGroupInteraction(final UiTestGroupSpec group,
            final UiTestGroupPageState pageState) {
        return new UiTestGroupVisualBuilder.GroupInteractionHandler() {
            @Override
            public void previousCase() {
                pageState.previous(registry.getCases(group.getCode()).size());
                showGroupPage(group);
            }

            @Override
            public void nextCase() {
                pageState.next(registry.getCases(group.getCode()).size());
                showGroupPage(group);
            }

            @Override
            public void runCurrentCaseAssertion() {
                List<UiTestCaseSpec> cases = registry.getCases(group.getCode());
                if (cases.isEmpty()) {
                    return;
                }
                pageState.clampToCaseCount(cases.size());
                UiTestCaseSpec testCase = cases.get(pageState.getCaseIndex());
                UiTestCaseResult result = assertionRunner.run(htmlLikeDocumentWidget, testCase, assertionLogger,
                        buildAssertionContext(testCase, pageState, cases.size()));
                matrixState.updateCaseResult(testCase, result);
                lastRunSummary = "当前样例：" + testCase.getId() + " "
                        + result.getSemanticStatus().getDisplayText() + "。";
                showGroupPage(group);
            }

            @Override
            public void runAllCaseAssertions() {
                runAllAssertions();
                showGroupPage(group);
            }
        };
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

            @Override
            public void runAllCaseAssertions() {
                runAllAssertions();
                showHomePage();
            }
        };
    }

    /**
     * 依次渲染并运行全部已接入样例，未自动接入的样例只生成诊断并保持待确认。
     */
    private void runAllAssertions() {
        int executed = 0;
        int automaticPassed = 0;
        int automaticFailed = 0;
        int manualPending = 0;
        for (UiTestGroupSpec group : registry.getGroups()) {
            List<UiTestCaseSpec> cases = registry.getCases(group.getCode());
            UiTestGroupPageState pageState = resolveGroupPageState(group);
            for (int index = 0; index < cases.size(); index++) {
                pageState.setCaseIndex(index, cases.size());
                showGroupPage(group);
                UiTestCaseSpec testCase = cases.get(index);
                UiTestCaseResult result = assertionRunner.run(htmlLikeDocumentWidget, testCase, assertionLogger,
                        buildAssertionContext(testCase, pageState, cases.size()));
                matrixState.updateCaseResult(testCase, result);
                executed++;
                if (result.getSemanticStatus() == UiTestSemanticStatus.AUTO_PASSED) {
                    automaticPassed++;
                } else if (result.getSemanticStatus() == UiTestSemanticStatus.AUTO_FAILED) {
                    automaticFailed++;
                } else if (result.getSemanticStatus() == UiTestSemanticStatus.MANUAL_PENDING) {
                    manualPending++;
                }
            }
        }
        lastRunSummary = "全量完成：" + executed + " 个；通过 " + automaticPassed
                + "；失败 " + automaticFailed + "；人工 " + manualPending + "。";
    }

    /**
     * 构建环境信息文本。
     *
     * @return 环境信息文本
     */
    private String buildEnvironmentText() {
        String statsSummary = buildRuntimeStatsSummary();
        return "MC 1.7.10；窗口=" + runtimeView.getHostWidth() + "x" + runtimeView.getHostHeight()
                + "；鼠标=" + runtimeView.getMouseX() + "," + runtimeView.getMouseY()
                + "；字体=" + fontEpoch + "；文本=" + defaultTextMode
                + "；网络=" + NetTransportFactory.resolveName(Config.netTransport)
                + "；适配=" + runtimeAdapterSummary
                + "；" + statsSummary;
    }

    private String buildRuntimeStatsSummary() {
        UiRuntimeStats stats = runtimeView.getUiRuntimeStats();
        return stats == null ? "无统计" : "frame=" + formatMs(stats.getFrameTimeMs())
                + "ms, render=" + formatMs(stats.getRenderTimeMs()) + "ms";
    }

    /**
     * 构建单次样例断言的运行上下文。
     *
     * @param testCase 当前样例
     * @param pageState 当前分组页状态
     * @param caseCount 当前分组样例总数
     * @return 断言运行上下文
     */
    private String buildAssertionContext(UiTestCaseSpec testCase, UiTestGroupPageState pageState, int caseCount) {
        return "group=" + testCase.getGroupCode() + "；case=" + testCase.getId()
                + "；page=" + (pageState.getCaseIndex() + 1) + "/" + caseCount
                + "；env=Minecraft=1.7.10,window=" + runtimeView.getHostWidth() + "x" + runtimeView.getHostHeight()
                + ",mouse=" + runtimeView.getMouseX() + "," + runtimeView.getMouseY()
                + ",fontEpoch=" + fontEpoch
                + ",net=" + NetTransportFactory.resolveName(Config.netTransport);
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
