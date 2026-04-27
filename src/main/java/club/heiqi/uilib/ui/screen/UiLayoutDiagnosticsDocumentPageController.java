package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.font.FontRuntimeStats;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.control.DocumentSegmentedSelectionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentSegmentedSelectionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentSegmentedSelectorControl;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.control.DocumentToggleChangeEvent;
import club.heiqi.uilib.ui.dom.control.DocumentToggleChangeHandler;
import club.heiqi.uilib.ui.dom.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * HTML-like 布局诊断子页控制器。
 */
final class UiLayoutDiagnosticsDocumentPageController extends DocumentPageController {

    private final DocumentPageAuthoringSurface diagnosticPage;
    private final DocumentPageRuntimeView runtimeView;
    private final String screenName;
    private final FontRuntimeStatsSource fontRuntimeStatsSource;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final UiTestMutationProbeState mutationProbeState = new UiTestMutationProbeState();
    private final UiTestDiagnosticsPresenter diagnosticsPresenter = new UiTestDiagnosticsPresenter();

    private final DocumentTextInputControl themeInput;
    private final DocumentTextInputControl namespaceInput;
    private final DocumentTextInputControl pathInput;
    private final DocumentToggleSwitchControl wrapToggle;
    private final DocumentToggleSwitchControl mutationToggle;
    private final DocumentButtonControl refreshButton;
    private final DocumentSegmentedSelectorControl widthPresetSelector;
    private final DocumentSegmentedSelectorControl mutationModeSelector;
    private final DocumentSegmentedSelectorControl mutationRateSelector;

    private final ElementNode overviewCard;
    private final ElementNode formCard;
    private final ElementNode wrapCard;
    private final ElementNode divScrollProbe;
    private final TextNode viewportMetricsText;
    private final TextNode scrollMetricsText;
    private final TextNode wrapMetricsText;
    private final TextNode performanceFrameText;
    private final TextNode performanceWidgetText;
    private final TextNode performanceHotspotText;
    private final TextNode performancePhaseText;
    private final TextNode performanceFontText;
    private final TextNode mutationMetricsText;
    private final TextNode mutationSampleText;
    private final TextNode divScrollMetricsText;
    private final TextNode wrapSampleText;
    private final TextNode actionStateText;

    UiLayoutDiagnosticsDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface diagnosticPage,
            DocumentPageRuntimeView runtimeView, String screenName) {
        this(documentUi, diagnosticPage, runtimeView, screenName, new FontRuntimeStatsSource() {
            @Override
            public FontRuntimeStats getRuntimeStats() {
                return FontService.getInstance().getRuntimeStats();
            }
        });
    }

    UiLayoutDiagnosticsDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface diagnosticPage,
            DocumentPageRuntimeView runtimeView, String screenName, FontRuntimeStatsSource fontRuntimeStatsSource) {
        DocumentUiScope resolvedDocumentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.diagnosticPage = Objects.requireNonNull(diagnosticPage, "diagnosticPage");
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        this.screenName = Objects.requireNonNull(screenName, "screenName");
        this.fontRuntimeStatsSource = Objects.requireNonNull(fontRuntimeStatsSource, "fontRuntimeStatsSource");

        UiDocument document = UiDocument.create();
        this.themeInput = createTextInput(document, "例如：Qz Layout Probe", "Qz Layout Probe", 48);
        this.namespaceInput = createTextInput(document, "例如：qz_uilib", "qz_uilib", 48);
        this.pathInput = createTextInput(document, "例如：assets/qz_uilib/ui/diagnostic",
                "assets/qz_uilib/ui/diagnostic", 96);
        this.wrapToggle = new DocumentToggleSwitchControl(document).setToggled(true);
        this.mutationToggle = new DocumentToggleSwitchControl(document).setToggled(false);
        this.refreshButton = new DocumentButtonControl(document, "刷新诊断文本");
        this.widthPresetSelector = new DocumentSegmentedSelectorControl(document, "窄页", "中页", "宽页");
        this.mutationModeSelector = new DocumentSegmentedSelectorControl(document, "§k渲染", "同长替换", "长文重排");
        this.mutationRateSelector = new DocumentSegmentedSelectorControl(document, "每帧", "50ms", "200ms");
        configureControls();

        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 760, 940,
                resolvedDocumentUi.getTextMeasureService());
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));

        DocumentBundle bundle = createDocumentContent(document, document.getRootElement());
        this.overviewCard = bundle.overviewCard;
        this.formCard = bundle.formCard;
        this.wrapCard = bundle.wrapCard;
        this.divScrollProbe = bundle.divScrollProbe;
        this.viewportMetricsText = bundle.viewportMetricsText;
        this.scrollMetricsText = bundle.scrollMetricsText;
        this.wrapMetricsText = bundle.wrapMetricsText;
        this.performanceFrameText = bundle.performanceFrameText;
        this.performanceWidgetText = bundle.performanceWidgetText;
        this.performanceHotspotText = bundle.performanceHotspotText;
        this.performancePhaseText = bundle.performancePhaseText;
        this.performanceFontText = bundle.performanceFontText;
        this.mutationMetricsText = bundle.mutationMetricsText;
        this.mutationSampleText = bundle.mutationSampleText;
        this.divScrollMetricsText = bundle.divScrollMetricsText;
        this.wrapSampleText = bundle.wrapSampleText;
        this.actionStateText = bundle.actionStateText;
    }

    @Override
    void configureDocumentPage() {
        diagnosticPage.setContentWidthRange(700, 1080)
                .setMinContentHeight(620)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    void buildDocument() {
        diagnosticPage.addBlock(htmlLikeDocumentWidget);
    }

    @Override
    void afterDocumentBuilt() {
        resetMutationProbeState(true);
        refreshDiagnostics();
    }

    @Override
    void onDocumentResized() {
        refreshDiagnostics();
    }

    @Override
    void beforeDocumentFrame() {
        tickHighFrequencyMutationProbe();
        refreshDiagnostics();
    }

    /**
     * 返回当前页面的 HTML-like 文档适配组件。
     *
     * @return HTML-like 文档适配组件
     */
    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    private void configureControls() {
        wrapToggle.setTrackColors(0xFF475569, 0xFF22C55E, 0xFF334155)
                .setFocusBorderColor(0xFFBAE6FD)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        mutationProbeState.onWrapToggleChanged(event.isToggled());
                        refreshDiagnostics();
                    }
                });
        mutationToggle.setTrackColors(0xFF475569, 0xFFF97316, 0xFF334155)
                .setFocusBorderColor(0xFFFED7AA)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        applyMutationTextUpdate(mutationProbeState.onMutationToggleChanged(event.isToggled(),
                                mutationModeSelector.getSelectedOption()));
                        refreshDiagnostics();
                    }
                });
        refreshButton.setBackgroundColors(0xFF2563EB, 0xFF1D4ED8, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE)
                .setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        mutationProbeState.onManualRefresh();
                        refreshDiagnostics();
                    }
                });
        widthPresetSelector.setSelectedIndex(1);
        widthPresetSelector.setSelectionHandler(new DocumentSegmentedSelectionHandler() {
            @Override
            public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                mutationProbeState.onWidthPresetChanged(event.getSelectedOption());
                refreshDiagnostics();
            }
        });
        mutationModeSelector.setSelectedIndex(0);
        mutationModeSelector.setSelectionHandler(new DocumentSegmentedSelectionHandler() {
            @Override
            public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                applyMutationTextUpdate(mutationProbeState.onMutationModeChanged(mutationToggle.isToggled(),
                        event.getSelectedOption()));
                refreshDiagnostics();
            }
        });
        mutationRateSelector.setSelectedIndex(1);
        mutationRateSelector.setSelectionHandler(new DocumentSegmentedSelectionHandler() {
            @Override
            public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                applyMutationTextUpdate(mutationProbeState.onMutationRateChanged(event.getSelectedOption()));
                refreshDiagnostics();
            }
        });
    }

    private DocumentBundle createDocumentContent(UiDocument document, ElementNode root) {
        root.style()
                .setPadding(UiStyleLength.px(20))
                .setBackgroundColor(0xF00A1020)
                .setBorderColor(0xFF60A5FA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(22))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(0xFFDCE7FF);

        appendHero(document, root);
        ElementNode overviewCard = appendCard(document, root, 0xFF14213A, 0xFF38BDF8);
        overviewCard.appendText("当前状态");
        overviewCard.appendText("这一页继续验证布局尺寸链、换行、性能采样和高频字符探针，但页面作者层已经切换到 HTML-like。 ");
        TextNode viewportMetricsText = overviewCard.appendText("");
        TextNode scrollMetricsText = overviewCard.appendText("");
        TextNode actionStateText = overviewCard.appendText("");

        ElementNode cardsRow = document.div();
        cardsRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(14))
                .setMargin(UiStyleLength.px(14));
        root.append(cardsRow);

        ElementNode formCard = appendCard(document, cardsRow, 0xFF17233B, 0xFF6366F1);
        formCard.style().setFlexGrow(1.35F);
        formCard.appendText("表单约束探针");
        formCard.appendText("文本输入、开关与分段选择都由 ElementNode-backed 控件组成。");
        appendControlRow(document, formCard, "主题名称", themeInput.getElement());
        appendControlRow(document, formCard, "命名空间", namespaceInput.getElement());
        appendControlRow(document, formCard, "资源路径", pathInput.getElement());
        appendControlRow(document, formCard, "换行提示", wrapToggle.getElement());
        appendControlRow(document, formCard, "宽度档位", widthPresetSelector.getElement());
        refreshButton.getElement().style().setMargin(UiStyleLength.px(8));
        formCard.append(refreshButton.getElement());

        ElementNode wrapCard = appendCard(document, cardsRow, 0xFF1E293B, 0xFF14B8A6);
        wrapCard.style().setFlexGrow(1.0F);
        wrapCard.appendText("文本换行与最小宽度探针");
        wrapCard.appendText("中英混排、路径与较长字段值应跟随 HTML-like 文本测量结果自然换行。");
        TextNode wrapSampleText = wrapCard.appendText("");
        TextNode wrapMetricsText = wrapCard.appendText("");

        ElementNode performanceCard = appendCard(document, root, 0xFF111827, 0xFFA78BFA);
        performanceCard.appendText("UI 性能统计");
        performanceCard.appendText("运行时采样仍来自宿主统计，但文案和布局由 HTML-like 文档承载。");
        TextNode performanceFrameText = performanceCard.appendText("");
        TextNode performanceWidgetText = performanceCard.appendText("");
        TextNode performanceHotspotText = performanceCard.appendText("");
        TextNode performancePhaseText = performanceCard.appendText("");
        TextNode performanceFontText = performanceCard.appendText("");

        ElementNode mutationCard = appendCard(document, root, 0xFF241A12, 0xFFF97316);
        mutationCard.appendText("高频字符变更探针");
        mutationCard.appendText("切换 §k、同长替换或长文重排，观察 setText 与换行失效压力。");
        appendControlRow(document, mutationCard, "自动运行", mutationToggle.getElement());
        appendControlRow(document, mutationCard, "变更模式", mutationModeSelector.getElement());
        appendControlRow(document, mutationCard, "刷新频率", mutationRateSelector.getElement());
        TextNode mutationMetricsText = mutationCard.appendText("");
        TextNode mutationSampleText = mutationCard.appendText("");

        ElementNode divCard = appendCard(document, root, 0xFF102A2A, 0xFF2DD4BF);
        divCard.appendText("统一尺寸契约探针");
        divCard.appendText("内部区域使用 fixed height + overflow auto，验证 HTML-like 滚动状态与裁剪。 ");
        ElementNode divScrollProbe = createScrollProbe(document);
        divCard.append(divScrollProbe);
        TextNode divScrollMetricsText = divCard.appendText("");

        return new DocumentBundle(overviewCard, formCard, wrapCard, divScrollProbe, viewportMetricsText,
                scrollMetricsText, wrapMetricsText, performanceFrameText, performanceWidgetText,
                performanceHotspotText, performancePhaseText, performanceFontText, mutationMetricsText,
                mutationSampleText, divScrollMetricsText, wrapSampleText, actionStateText);
    }

    private void appendHero(UiDocument document, ElementNode root) {
        ElementNode hero = document.div();
        hero.style()
                .setHeight(UiStyleLength.px(120))
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF93C5FD)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextColor(0xFFF8FAFC);
        hero.appendText("布局诊断页");
        hero.appendText("HTML-like Layout Console");
        hero.appendText("旧 DocumentCardWidget/LabelWidget 表达已从本页作者层清退。");
        root.append(hero);
    }

    private ElementNode appendCard(UiDocument document, ElementNode parent, int backgroundColor, int borderColor) {
        ElementNode card = document.div();
        card.style()
                .setPadding(UiStyleLength.px(16))
                .setMargin(UiStyleLength.px(14))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setTextColor(0xFFE5EEFF);
        parent.append(card);
        return card;
    }

    private void appendControlRow(UiDocument document, ElementNode parent, String label, ElementNode field) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(42))
                .setMargin(UiStyleLength.px(6));
        ElementNode labelElement = document.div();
        labelElement.style()
                .setWidth(UiStyleLength.px(92))
                .setTextColor(0xFFBFD0EE);
        labelElement.appendText(label);
        field.style().setFlexGrow(1.0F);
        row.append(labelElement);
        row.append(field);
        parent.append(row);
    }

    private ElementNode createScrollProbe(UiDocument document) {
        ElementNode probe = document.div();
        probe.style()
                .setHeight(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(14))
                .setMargin(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0F1F1F)
                .setBorderColor(0xFF2DD4BF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(12))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(0xFFD1FAE5);
        for (int index = 1; index <= 10; index++) {
            probe.appendText("HTML-like 自滚动条目 " + index
                    + "：fixed height + overflow auto 应只移动内部文本内容，背景、边框和裁剪框保持固定。");
        }
        return probe;
    }

    private DocumentTextInputControl createTextInput(UiDocument document, String placeholder, String text,
            int maxLength) {
        return new DocumentTextInputControl(document)
                .setPlaceholder(placeholder)
                .setText(text)
                .setMaxLength(maxLength);
    }

    private void refreshDiagnostics() {
        UiTestDiagnosticsPresenter.ViewState viewState = diagnosticsPresenter.present(collectDiagnosticsSnapshot());
        updateText(viewportMetricsText, viewState.viewportText);
        updateText(scrollMetricsText, viewState.scrollText);
        updateText(wrapSampleText, viewState.wrapSampleText);
        updateText(wrapMetricsText, viewState.wrapMetricsText);
        updateText(divScrollMetricsText, viewState.divScrollText);
        updateText(actionStateText, viewState.actionText);
        updateText(mutationMetricsText, viewState.mutationText);
        updateText(performanceFrameText, viewState.performanceFrameText);
        updateText(performanceWidgetText, viewState.performanceWidgetText);
        updateText(performanceHotspotText, viewState.performanceHotspotText);
        updateText(performancePhaseText, viewState.performancePhaseText);
        updateText(performanceFontText, viewState.performanceFontText);
    }

    private void tickHighFrequencyMutationProbe() {
        applyMutationTextUpdate(mutationProbeState.tickMutation(
                mutationToggle.isToggled(),
                mutationModeSelector.getSelectedOption(),
                mutationRateSelector.getSelectedIndex(),
                System.nanoTime()));
    }

    private void resetMutationProbeState(boolean resetText) {
        applyMutationTextUpdate(mutationProbeState.resetMutationProbeState(
                resetText,
                mutationToggle.isToggled(),
                mutationModeSelector.getSelectedOption()));
    }

    private void applyMutationTextUpdate(UiTestMutationProbeState.MutationTextUpdate update) {
        if (update == null || !update.shouldApplyText) {
            return;
        }
        updateText(mutationSampleText, update.text);
    }

    private UiTestDiagnosticsPresenter.Snapshot collectDiagnosticsSnapshot() {
        ElementNode pageRoot = htmlLikeDocumentWidget.getDocument().getRootElement();
        int pageScrollTop = htmlLikeDocumentWidget.getScrollTop(pageRoot);
        int pageMaxScrollTop = htmlLikeDocumentWidget.getMaxScrollTop(pageRoot);
        int probeScrollTop = htmlLikeDocumentWidget.getScrollTop(divScrollProbe);
        int probeMaxScrollTop = htmlLikeDocumentWidget.getMaxScrollTop(divScrollProbe);
        return new UiTestDiagnosticsPresenter.Snapshot(
                runtimeView.getHostWidth(),
                runtimeView.getHostHeight(),
                diagnosticPage.getWidth(),
                diagnosticPage.getHeight(),
                htmlLikeDocumentWidget.getWidth(),
                htmlLikeDocumentWidget.getHeight(),
                htmlLikeDocumentWidget.getWidth(),
                htmlLikeDocumentWidget.getHeight(),
                Math.max(0, htmlLikeDocumentWidget.getWidth() / 2),
                htmlLikeDocumentWidget.getHeight(),
                pageScrollTop,
                pageMaxScrollTop,
                htmlLikeDocumentWidget.getWidth(),
                htmlLikeDocumentWidget.getHeight(),
                htmlLikeDocumentWidget.getWidth(),
                htmlLikeDocumentWidget.getHeight() + pageMaxScrollTop,
                themeInput.getText(),
                namespaceInput.getText(),
                mutationProbeState.getActionStateText(),
                widthPresetSelector.getSelectedOption(),
                probeScrollTop,
                probeMaxScrollTop,
                htmlLikeDocumentWidget.getWidth(),
                220,
                htmlLikeDocumentWidget.getWidth(),
                220 + probeMaxScrollTop,
                mutationToggle.isToggled(),
                mutationModeSelector.getSelectedOption(),
                mutationRateSelector.getSelectedOption(),
                mutationProbeState.getMutationSetTextCount(),
                mutationSampleText.getText(),
                0,
                0,
                screenName,
                runtimeView.getUiRuntimeStats(),
                fontRuntimeStatsSource.getRuntimeStats());
    }

    private void updateText(TextNode textNode, String text) {
        if (textNode == null) {
            return;
        }
        textNode.setText(text == null ? "" : text);
    }

    /**
     * 页面构建后需要保留的节点引用。
     */
    private static final class DocumentBundle {

        final ElementNode overviewCard;
        final ElementNode formCard;
        final ElementNode wrapCard;
        final ElementNode divScrollProbe;
        final TextNode viewportMetricsText;
        final TextNode scrollMetricsText;
        final TextNode wrapMetricsText;
        final TextNode performanceFrameText;
        final TextNode performanceWidgetText;
        final TextNode performanceHotspotText;
        final TextNode performancePhaseText;
        final TextNode performanceFontText;
        final TextNode mutationMetricsText;
        final TextNode mutationSampleText;
        final TextNode divScrollMetricsText;
        final TextNode wrapSampleText;
        final TextNode actionStateText;

        DocumentBundle(ElementNode overviewCard, ElementNode formCard, ElementNode wrapCard,
                ElementNode divScrollProbe, TextNode viewportMetricsText, TextNode scrollMetricsText,
                TextNode wrapMetricsText, TextNode performanceFrameText, TextNode performanceWidgetText,
                TextNode performanceHotspotText, TextNode performancePhaseText, TextNode performanceFontText,
                TextNode mutationMetricsText, TextNode mutationSampleText, TextNode divScrollMetricsText,
                TextNode wrapSampleText, TextNode actionStateText) {
            this.overviewCard = overviewCard;
            this.formCard = formCard;
            this.wrapCard = wrapCard;
            this.divScrollProbe = divScrollProbe;
            this.viewportMetricsText = viewportMetricsText;
            this.scrollMetricsText = scrollMetricsText;
            this.wrapMetricsText = wrapMetricsText;
            this.performanceFrameText = performanceFrameText;
            this.performanceWidgetText = performanceWidgetText;
            this.performanceHotspotText = performanceHotspotText;
            this.performancePhaseText = performancePhaseText;
            this.performanceFontText = performanceFontText;
            this.mutationMetricsText = mutationMetricsText;
            this.mutationSampleText = mutationSampleText;
            this.divScrollMetricsText = divScrollMetricsText;
            this.wrapSampleText = wrapSampleText;
            this.actionStateText = actionStateText;
        }
    }
}
