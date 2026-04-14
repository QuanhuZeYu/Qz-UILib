package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.font.FontRuntimeStats;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.SegmentedSelectorWidget;
import club.heiqi.uilib.ui.control.TextInputWidget;
import club.heiqi.uilib.ui.control.ToggleSwitchWidget;
import club.heiqi.uilib.ui.document.DocumentCardWidget;
import club.heiqi.uilib.ui.document.DocumentFlowRowWidget;
import club.heiqi.uilib.ui.document.DocumentSectionWidget;
import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.document.DocumentToolbarWidget;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;

/**
 * 布局诊断页的页面控制器。
 *
 * <p>该控制器承接 `UiTestScreen` 原有的页面私有 widget、诊断刷新逻辑与高频变更探针状态，
 * 让宿主 screen 退化为只保留 concrete 类型身份的薄包装器。</p>
 */
final class UiTestDocumentPageController extends DocumentPageController {

    private final DocumentUiScope documentUi;
    private final DocumentPageAuthoringSurface diagnosticPage;
    private final DocumentPageRuntimeView runtimeView;
    private final String screenName;
    private final FontRuntimeStatsSource fontRuntimeStatsSource;

    private final DocumentCardWidget overviewCard;
    private final DocumentCardWidget formCard;
    private final DocumentCardWidget wrapCard;
    private final DocumentCardWidget performanceCard;
    private final DocumentCardWidget mutationCard;
    private final DocumentCardWidget divScrollCard;
    private final DivWidget divScrollProbe;

    private final LabelWidget viewportMetricsLabel;
    private final LabelWidget scrollMetricsLabel;
    private final LabelWidget wrapMetricsLabel;
    private final LabelWidget performanceFrameLabel;
    private final LabelWidget performanceWidgetLabel;
    private final LabelWidget performanceHotspotLabel;
    private final LabelWidget performancePhaseLabel;
    private final LabelWidget performanceFontLabel;
    private final LabelWidget mutationMetricsLabel;
    private final LabelWidget mutationSampleLabel;
    private final LabelWidget divScrollMetricsLabel;
    private final LabelWidget wrapSampleLabel;
    private final LabelWidget actionStateLabel;

    private final TextInputWidget themeInput;
    private final TextInputWidget namespaceInput;
    private final TextInputWidget pathInput;
    private final ToggleSwitchWidget wrapToggle;
    private final ToggleSwitchWidget mutationToggle;
    private final SegmentedSelectorWidget widthPresetSelector;
    private final SegmentedSelectorWidget mutationModeSelector;
    private final SegmentedSelectorWidget mutationRateSelector;
    private final ButtonWidget refreshButton;
    private final UiTestMutationProbeState mutationProbeState = new UiTestMutationProbeState();
    private final UiTestDiagnosticsPresenter diagnosticsPresenter = new UiTestDiagnosticsPresenter();

    /**
     * 创建布局诊断页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param diagnosticPage 文档页面壳
     * @param runtimeView 宿主运行时视图
     * @param screenName 诊断统计所用 screen 名称
     */
    UiTestDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface diagnosticPage,
            DocumentPageRuntimeView runtimeView, String screenName) {
        this(documentUi, diagnosticPage, runtimeView, screenName, new FontRuntimeStatsSource() {
            @Override
            public FontRuntimeStats getRuntimeStats() {
                return FontService.getInstance().getRuntimeStats();
            }
        });
    }

    /**
     * 创建布局诊断页控制器，并允许注入字体统计来源。
     *
     * @param documentUi 文档组件作用域
     * @param diagnosticPage 文档页面壳
     * @param runtimeView 宿主运行时视图
     * @param screenName 诊断统计所用 screen 名称
     * @param fontRuntimeStatsSource 字体运行时统计来源
     */
    UiTestDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface diagnosticPage,
            DocumentPageRuntimeView runtimeView, String screenName, FontRuntimeStatsSource fontRuntimeStatsSource) {
        this.documentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.diagnosticPage = Objects.requireNonNull(diagnosticPage, "diagnosticPage");
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        this.screenName = Objects.requireNonNull(screenName, "screenName");
        this.fontRuntimeStatsSource = Objects.requireNonNull(fontRuntimeStatsSource, "fontRuntimeStatsSource");

        this.overviewCard = this.documentUi.card();
        this.formCard = this.documentUi.card();
        this.wrapCard = this.documentUi.card();
        this.performanceCard = this.documentUi.card();
        this.mutationCard = this.documentUi.card();
        this.divScrollCard = this.documentUi.card();
        this.divScrollProbe = this.documentUi.scrollDiv()
                .setDirection(DivWidget.Direction.COLUMN)
                .setAlignItems(DivWidget.AlignItems.STRETCH)
                .setJustifyContent(DivWidget.JustifyContent.START)
                .setWrap(DivWidget.Wrap.NOWRAP)
                .setOverflowX(DivWidget.Overflow.HIDDEN)
                .setOverflowY(DivWidget.Overflow.AUTO)
                .setPadding(14)
                .setGap(10);

        this.viewportMetricsLabel = this.documentUi.text(DocumentTextWidget.Role.EMPHASIS, "", 4);
        this.scrollMetricsLabel = this.documentUi.text(DocumentTextWidget.Role.SECONDARY, "", 4);
        this.wrapMetricsLabel = this.documentUi.text(DocumentTextWidget.Role.SECONDARY, "", 6);
        this.performanceFrameLabel = this.documentUi.text(DocumentTextWidget.Role.EMPHASIS, "", 4);
        this.performanceWidgetLabel = this.documentUi.text(DocumentTextWidget.Role.BODY, "", 5);
        this.performanceHotspotLabel = this.documentUi.text(DocumentTextWidget.Role.BODY, "", 5);
        this.performancePhaseLabel = this.documentUi.text(DocumentTextWidget.Role.BODY, "", 6);
        this.performanceFontLabel = this.documentUi.text(DocumentTextWidget.Role.SECONDARY, "", 6);
        this.mutationMetricsLabel = this.documentUi.text(DocumentTextWidget.Role.EMPHASIS, "", 6);
        this.mutationSampleLabel = this.documentUi.text(DocumentTextWidget.Role.BODY, "", 12);
        this.divScrollMetricsLabel = this.documentUi.text(DocumentTextWidget.Role.SECONDARY, "", 5);
        this.wrapSampleLabel = this.documentUi.text(DocumentTextWidget.Role.BODY, "", 10);
        this.actionStateLabel = this.documentUi.text(DocumentTextWidget.Role.SECONDARY, "", 2);

        this.themeInput = this.documentUi.textInput();
        this.namespaceInput = this.documentUi.textInput();
        this.pathInput = this.documentUi.textInput();
        this.wrapToggle = this.documentUi.toggle("启用");
        this.mutationToggle = this.documentUi.toggle("启用高频探针");
        this.widthPresetSelector = this.documentUi.segmented("窄页", "中页", "宽页");
        this.mutationModeSelector = this.documentUi.segmented("§k渲染", "同长替换", "长文重排");
        this.mutationRateSelector = this.documentUi.segmented("每帧", "50ms", "200ms");
        this.refreshButton = this.documentUi.button("刷新诊断文本");
    }

    @Override
    void configureDocumentPage() {
        diagnosticPage.setContentWidthRange(680, 1080)
                .setMinContentHeight(560)
                .setViewportFillRatio(0.92F, 0.90F);
    }

    @Override
    void buildDocument() {
        configureControls();
        assembleDocument();
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
     * 配置诊断控件。
     */
    private void configureControls() {
        formCard.setLayoutSpec(new UiLayoutSpec().setFlexBasis(UiLength.px(460)).setMinWidth(320).setMaxWidth(620));
        wrapCard.setLayoutSpec(new UiLayoutSpec().setFlexBasis(UiLength.px(340)).setMinWidth(260).setMaxWidth(480));
        performanceCard.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)));
        mutationCard.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)));
        divScrollCard.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)));
        divScrollProbe.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.px(220)).setMinHeight(220).setMaxHeight(220));

        themeInput.setPlaceholder("例如：Qz Layout Probe").setText("Qz Layout Probe").setMaxLength(48);
        namespaceInput.setPlaceholder("例如：qz_uilib").setText("qz_uilib").setMaxLength(48);
        pathInput.setPlaceholder("例如：assets/qz_uilib/ui/diagnostic").setText("assets/qz_uilib/ui/diagnostic").setMaxLength(96);
        wrapToggle.setChecked(true).setToggleHandler(new Runnable() {
            @Override
            public void run() {
                mutationProbeState.onWrapToggleChanged(wrapToggle.isChecked());
                refreshDiagnostics();
            }
        });
        widthPresetSelector.setSelectedIndex(1).setChangeHandler(new Runnable() {
            @Override
            public void run() {
                mutationProbeState.onWidthPresetChanged(widthPresetSelector.getSelectedOption());
                refreshDiagnostics();
            }
        });
        mutationToggle.setChecked(false).setToggleHandler(new Runnable() {
            @Override
            public void run() {
                applyMutationTextUpdate(mutationProbeState.onMutationToggleChanged(
                        mutationToggle.isChecked(),
                        mutationModeSelector.getSelectedOption()));
                refreshDiagnostics();
            }
        });
        mutationModeSelector.setSelectedIndex(0).setChangeHandler(new Runnable() {
            @Override
            public void run() {
                applyMutationTextUpdate(mutationProbeState.onMutationModeChanged(
                        mutationToggle.isChecked(),
                        mutationModeSelector.getSelectedOption()));
                refreshDiagnostics();
            }
        });
        mutationRateSelector.setSelectedIndex(1).setChangeHandler(new Runnable() {
            @Override
            public void run() {
                applyMutationTextUpdate(mutationProbeState.onMutationRateChanged(mutationRateSelector.getSelectedOption()));
                refreshDiagnostics();
            }
        });
        refreshButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                mutationProbeState.onManualRefresh();
                refreshDiagnostics();
            }
        });
    }

    /**
     * 构建诊断页组件树。
     */
    private void assembleDocument() {
        DocumentFlowRowWidget cardsFlow = documentUi.flowRow();

        DocumentSectionWidget overviewDiv = documentUi.section();
        overviewDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "当前状态", 2));
        overviewDiv.addChild(documentUi.text(DocumentTextWidget.Role.BODY,
                "旧测试页已经完全清空。当前只保留这一张最小诊断页，专门验证页面壳尺寸、卡片换行、中文文本最小宽度和父容器约束是否正确。", 8));
        overviewDiv.addChild(viewportMetricsLabel);
        overviewDiv.addChild(scrollMetricsLabel);
        overviewDiv.addChild(actionStateLabel);
        overviewCard.addChild(overviewDiv);

        DocumentSectionWidget formDiv = documentUi.section();
        formDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "表单约束探针", 2));
        formDiv.addChild(documentUi.text(DocumentTextWidget.Role.BODY,
                "这张卡片只验证标签列和字段列在父宽度变化时能否正确重排。标签列保持固定参考宽度，字段列允许拉伸或换到下一行。", 8));
        formDiv.addChild(documentUi.formRow("主题名称", themeInput));
        formDiv.addChild(documentUi.formRow("命名空间", namespaceInput));
        formDiv.addChild(documentUi.formRow("资源路径", pathInput));
        formDiv.addChild(documentUi.formRow("换行提示", wrapToggle));
        formDiv.addChild(documentUi.formRow("宽度档位", widthPresetSelector));
        DocumentToolbarWidget refreshToolbar = documentUi.toolbar();
        refreshToolbar.addChild(refreshButton);
        formDiv.addChild(refreshToolbar);
        formCard.addChild(formDiv);

        DocumentSectionWidget wrapDiv = documentUi.section();
        wrapDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "文本换行与最小宽度探针", 2));
        wrapDiv.addChild(documentUi.text(DocumentTextWidget.Role.BODY,
                "这里故意放一段中英混排文本，观察在不同页宽下是否优先正常换行，而不是把整段中文误判为一个不可压缩长词。", 8));
        wrapDiv.addChild(wrapSampleLabel);
        wrapDiv.addChild(wrapMetricsLabel);
        wrapCard.addChild(wrapDiv);

        DocumentSectionWidget divScrollCardDiv = documentUi.section();
        divScrollCardDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "统一尺寸契约探针", 2));
        divScrollCardDiv.addChild(documentUi.text(DocumentTextWidget.Role.BODY,
                "这块直接验证 Div 父容器是否开始读取统一的 `UiLayoutSpec`：内部探针使用 `width=100%` 和 `height=220px`，如果仍然不产生内部滚动，就说明尺寸契约仍然割裂。", 8));
        for (int index = 1; index <= 10; index++) {
            divScrollProbe.addChild(documentUi.text(DocumentTextWidget.Role.BODY,
                    "Div 自滚动条目 " + index
                            + "：这里故意放入重复的中英混排说明，只有当 Div 真正认 `UiLayoutSpec.height=220px` 时，这块区域才会产生稳定的内部滚动，而不是继续随外层页面一起长高。",
                    8));
        }
        divScrollCardDiv.addChild(divScrollProbe);
        divScrollCardDiv.addChild(divScrollMetricsLabel);
        divScrollCard.addChild(divScrollCardDiv);

        DocumentSectionWidget performanceDiv = documentUi.section();
        performanceDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "UI 性能统计", 2));
        performanceDiv.addChild(documentUi.text(DocumentTextWidget.Role.BODY,
                "这一块直接读取框架运行时采样结果，观察帧耗时、输入路由、命中测试次数和最慢组件类型。若这里的数据异常，再继续细分具体控件或布局阶段。", 8));
        performanceDiv.addChild(performanceFrameLabel);
        performanceDiv.addChild(performanceWidgetLabel);
        performanceDiv.addChild(performanceHotspotLabel);
        performanceDiv.addChild(performancePhaseLabel);
        performanceDiv.addChild(performanceFontLabel);
        performanceCard.addChild(performanceDiv);

        DocumentSectionWidget mutationDiv = documentUi.section();
        mutationDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "高频字符变更探针", 2));
        mutationDiv.addChild(documentUi.text(DocumentTextWidget.Role.BODY,
                "这块专门区分三种压力：`§k` 混淆文本只在绘制阶段随机替换字符，不主动触发布局；`同长替换` 会高频调用 `setText()` 但尽量保持长度稳定；`长文重排` 会持续改变长文本内容并触发换行重算。通过它可以直接观察字体绘制和布局失效谁更伤。", 8));
        mutationDiv.addChild(documentUi.formRow("自动运行", mutationToggle));
        mutationDiv.addChild(documentUi.formRow("变更模式", mutationModeSelector));
        mutationDiv.addChild(documentUi.formRow("刷新频率", mutationRateSelector));
        mutationDiv.addChild(mutationMetricsLabel);
        mutationDiv.addChild(mutationSampleLabel);
        mutationCard.addChild(mutationDiv);

        cardsFlow.addFlexibleBlock(formCard, 1.5F);
        cardsFlow.addFlexibleBlock(wrapCard, 1.0F);

        diagnosticPage.addBlock(documentUi.text(DocumentTextWidget.Role.TITLE, "布局诊断页", 2));
        diagnosticPage.addBlock(documentUi.text(DocumentTextWidget.Role.BODY,
                "如果这一页的两张卡片仍然在不合理的宽度下并排、中文换行异常、表单行不按父宽度变化，或者卡片不能同时按 flex-basis 和增长权重自然分配空间，那么说明底层尺寸链路仍然有问题。",
                8));
        diagnosticPage.addBlock(overviewCard);
        diagnosticPage.addBlock(cardsFlow);
        diagnosticPage.addBlock(performanceCard);
        diagnosticPage.addBlock(mutationCard);
        diagnosticPage.addBlock(divScrollCard);
    }

    /**
     * 刷新诊断文本。
     */
    private void refreshDiagnostics() {
        UiTestDiagnosticsPresenter.ViewState viewState = diagnosticsPresenter.present(collectDiagnosticsSnapshot());
        updateLabelText(viewportMetricsLabel, viewState.viewportText);
        updateLabelText(scrollMetricsLabel, viewState.scrollText);
        updateLabelText(wrapSampleLabel, viewState.wrapSampleText);
        updateLabelText(wrapMetricsLabel, viewState.wrapMetricsText);
        updateLabelText(divScrollMetricsLabel, viewState.divScrollText);
        updateLabelText(actionStateLabel, viewState.actionText);
        updateLabelText(mutationMetricsLabel, viewState.mutationText);
        updateLabelText(performanceFrameLabel, viewState.performanceFrameText);
        updateLabelText(performanceWidgetLabel, viewState.performanceWidgetText);
        updateLabelText(performanceHotspotLabel, viewState.performanceHotspotText);
        updateLabelText(performancePhaseLabel, viewState.performancePhaseText);
        updateLabelText(performanceFontLabel, viewState.performanceFontText);
    }

    /**
     * 驱动高频字符变更探针。
     */
    private void tickHighFrequencyMutationProbe() {
        applyMutationTextUpdate(mutationProbeState.tickMutation(
                mutationToggle.isChecked(),
                mutationModeSelector.getSelectedOption(),
                mutationRateSelector.getSelectedIndex(),
                System.nanoTime()));
    }

    /**
     * 重置高频字符变更探针状态。
     *
     * @param resetText 是否同时恢复样本文本
     */
    private void resetMutationProbeState(boolean resetText) {
        applyMutationTextUpdate(mutationProbeState.resetMutationProbeState(
                resetText,
                mutationToggle.isChecked(),
                mutationModeSelector.getSelectedOption()));
    }

    /**
     * 应用新的探针文本，并统计真实的 setText 次数。
     *
     * @param text 新文本
     */
    private void applyMutationTextUpdate(UiTestMutationProbeState.MutationTextUpdate update) {
        if (update == null || !update.shouldApplyText) {
            return;
        }
        updateLabelText(mutationSampleLabel, update.text);
    }

    /**
     * 采集页面当前诊断快照。
     *
     * @return 纯数据快照
     */
    private UiTestDiagnosticsPresenter.Snapshot collectDiagnosticsSnapshot() {
        return new UiTestDiagnosticsPresenter.Snapshot(
                runtimeView.getHostWidth(),
                runtimeView.getHostHeight(),
                diagnosticPage.getWidth(),
                diagnosticPage.getHeight(),
                overviewCard.getWidth(),
                overviewCard.getHeight(),
                formCard.getWidth(),
                formCard.getHeight(),
                wrapCard.getWidth(),
                wrapCard.getHeight(),
                diagnosticPage.getScrollOffset(),
                diagnosticPage.getMaxScrollOffset(),
                diagnosticPage.getVisibleContentWidth(),
                diagnosticPage.getVisibleContentHeight(),
                diagnosticPage.getContentWidth(),
                diagnosticPage.getContentHeight(),
                themeInput.getText(),
                namespaceInput.getText(),
                mutationProbeState.getActionStateText(),
                widthPresetSelector.getSelectedOption(),
                divScrollProbe.getVerticalScrollOffset(),
                divScrollProbe.getMaxVerticalScrollOffset(),
                divScrollProbe.getVisibleContentWidth(),
                divScrollProbe.getVisibleContentHeight(),
                divScrollProbe.getContentWidth(),
                divScrollProbe.getContentHeight(),
                mutationToggle.isChecked(),
                mutationModeSelector.getSelectedOption(),
                mutationRateSelector.getSelectedOption(),
                mutationProbeState.getMutationSetTextCount(),
                mutationSampleLabel.getText(),
                mutationSampleLabel.getWidth(),
                mutationSampleLabel.getHeight(),
                screenName,
                runtimeView.getUiRuntimeStats(),
                fontRuntimeStatsSource.getRuntimeStats());
    }

    private void updateLabelText(LabelWidget label, String text) {
        if (label == null) {
            return;
        }
        String current = label.getText();
        if (current == text || current != null && current.equals(text)) {
            return;
        }
        label.setText(text);
    }
}
