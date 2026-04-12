package club.heiqi.uilib.ui.screen;

import java.util.Locale;

import club.heiqi.uilib.font.FontRuntimeStats;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.DocumentShellWidget;
import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.SegmentedSelectorWidget;
import club.heiqi.uilib.ui.control.TextInputWidget;
import club.heiqi.uilib.ui.control.ToggleSwitchWidget;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 当前阶段的最小布局诊断页。
 */
public class UiTestScreen extends BaseScreen {

    private final DocumentShellWidget diagnosticPage = new DocumentShellWidget();

    private final DivWidget overviewCard = createCardPanel();
    private final DivWidget formCard = createCardPanel();
    private final DivWidget wrapCard = createCardPanel();
    private final DivWidget performanceCard = createCardPanel();
    private final DivWidget mutationCard = createCardPanel();
    private final DivWidget divScrollCard = createCardPanel();
    private final DivWidget divScrollProbe = createSectionBlock()
            .setVerticalScrollOnly()
            .setPadding(14)
            .setGap(10);

    private final LabelWidget viewportMetricsLabel = new LabelWidget("");
    private final LabelWidget scrollMetricsLabel = new LabelWidget("");
    private final LabelWidget wrapMetricsLabel = new LabelWidget("");
    private final LabelWidget performanceFrameLabel = new LabelWidget("");
    private final LabelWidget performanceWidgetLabel = new LabelWidget("");
    private final LabelWidget performanceHotspotLabel = new LabelWidget("");
    private final LabelWidget performancePhaseLabel = new LabelWidget("");
    private final LabelWidget performanceFontLabel = new LabelWidget("");
    private final LabelWidget mutationMetricsLabel = new LabelWidget("");
    private final LabelWidget mutationSampleLabel = new LabelWidget("");
    private final LabelWidget divScrollMetricsLabel = new LabelWidget("");
    private final LabelWidget wrapSampleLabel = new LabelWidget("");
    private final LabelWidget actionStateLabel = new LabelWidget("");

    private final TextInputWidget themeInput = new TextInputWidget();
    private final TextInputWidget namespaceInput = new TextInputWidget();
    private final TextInputWidget pathInput = new TextInputWidget();
    private final ToggleSwitchWidget wrapToggle = new ToggleSwitchWidget("启用");
    private final ToggleSwitchWidget mutationToggle = new ToggleSwitchWidget("启用高频探针");
    private final SegmentedSelectorWidget widthPresetSelector = new SegmentedSelectorWidget("窄页", "中页", "宽页");
    private final SegmentedSelectorWidget mutationModeSelector = new SegmentedSelectorWidget("§k渲染", "同长替换", "长文重排");
    private final SegmentedSelectorWidget mutationRateSelector = new SegmentedSelectorWidget("每帧", "50ms", "200ms");
    private final ButtonWidget refreshButton = new ButtonWidget("刷新诊断文本");

    private String actionStateText = "尚未操作";
    private long lastMutationUpdateNanos;
    private int mutationSequence;
    private int mutationSetTextCount;
    private String lastMutationMode = "";
    private String lastMutationText = "";

    @Override
    protected void buildUi(Widget root) {
        configurePage();
        configureControls();
        assembleUi(root);
        resetMutationProbeState(true);
        refreshDiagnostics();
    }

    @Override
    protected void onResize(int width, int height) {
        super.onResize(width, height);

        int pageMargin = Math.max(24, width / 34);
        int topMargin = Math.max(28, height / 28);
        setRootPadding(pageMargin, topMargin, pageMargin, pageMargin);

        int pagePaddingX = clampValue(width / 48, 16, 28);
        int pagePaddingY = clampValue(height / 36, 14, 24);
        diagnosticPage.setShellPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);

        refreshDiagnostics();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        tickHighFrequencyMutationProbe();
        refreshDiagnostics();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * 配置页面壳。
     */
    private void configurePage() {
        UiDocumentTheme.applyShellSurface(diagnosticPage)
                .setShellPadding(24, 22, 24, 22)
                .setContentWidthRange(680, 1080)
                .setMinContentHeight(560)
                .setViewportFillRatio(0.92F, 0.90F);
    }

    /**
     * 配置诊断控件。
     */
    private void configureControls() {
        UiDocumentTheme.applyEmphasisText(viewportMetricsLabel).setWrap(true).setMaxLines(4);
        UiDocumentTheme.applySecondaryText(scrollMetricsLabel).setWrap(true).setMaxLines(4);
        UiDocumentTheme.applySecondaryText(wrapMetricsLabel).setWrap(true).setMaxLines(6);
        UiDocumentTheme.applyEmphasisText(performanceFrameLabel).setWrap(true).setMaxLines(4);
        UiDocumentTheme.applyBodyText(performanceWidgetLabel).setWrap(true).setMaxLines(5);
        UiDocumentTheme.applyBodyText(performanceHotspotLabel).setWrap(true).setMaxLines(5);
        UiDocumentTheme.applyBodyText(performancePhaseLabel).setWrap(true).setMaxLines(6);
        UiDocumentTheme.applySecondaryText(performanceFontLabel).setWrap(true).setMaxLines(6);
        UiDocumentTheme.applyEmphasisText(mutationMetricsLabel).setWrap(true).setMaxLines(6);
        UiDocumentTheme.applyBodyText(mutationSampleLabel).setWrap(true).setMaxLines(12);
        UiDocumentTheme.applySecondaryText(divScrollMetricsLabel).setWrap(true).setMaxLines(5);
        UiDocumentTheme.applyBodyText(wrapSampleLabel).setWrap(true).setMaxLines(10);
        UiDocumentTheme.applySecondaryText(actionStateLabel).setWrap(true).setMaxLines(2);

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
                actionStateText = wrapToggle.isChecked() ? "已开启自动换行提示" : "已关闭自动换行提示";
                refreshDiagnostics();
            }
        });
        widthPresetSelector.setSelectedIndex(1).setChangeHandler(new Runnable() {
            @Override
            public void run() {
                actionStateText = "已切换宽度档位到 " + widthPresetSelector.getSelectedOption();
                refreshDiagnostics();
            }
        });
        mutationToggle.setChecked(false).setToggleHandler(new Runnable() {
            @Override
            public void run() {
                actionStateText = mutationToggle.isChecked() ? "已启用高频字符变更探针" : "已停止高频字符变更探针";
                resetMutationProbeState(true);
                refreshDiagnostics();
            }
        });
        mutationModeSelector.setSelectedIndex(0).setChangeHandler(new Runnable() {
            @Override
            public void run() {
                actionStateText = "已切换变更模式到 " + mutationModeSelector.getSelectedOption();
                resetMutationProbeState(true);
                refreshDiagnostics();
            }
        });
        mutationRateSelector.setSelectedIndex(1).setChangeHandler(new Runnable() {
            @Override
            public void run() {
                actionStateText = "已切换探针频率到 " + mutationRateSelector.getSelectedOption();
                resetMutationProbeState(false);
                refreshDiagnostics();
            }
        });
        refreshButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                actionStateText = "已刷新当前诊断文本";
                refreshDiagnostics();
            }
        });
    }

    /**
     * 构建诊断页组件树。
     */
    private void assembleUi(Widget root) {
        DivWidget cardsFlow = createWrapRow().setColumnGap(16).setRowGap(20);

        DivWidget overviewDiv = createSectionBlock();
        overviewDiv.addNoGrowChild(createSectionTitle("当前状态"));
        overviewDiv.addNoGrowChild(createBodyLabel("旧测试页已经完全清空。当前只保留这一张最小诊断页，专门验证页面壳尺寸、卡片换行、中文文本最小宽度和父容器约束是否正确。"));
        overviewDiv.addNoGrowChild(viewportMetricsLabel);
        overviewDiv.addNoGrowChild(scrollMetricsLabel);
        overviewDiv.addNoGrowChild(actionStateLabel);
        overviewCard.addChild(overviewDiv);

        DivWidget formDiv = createSectionBlock();
        formDiv.addNoGrowChild(createSectionTitle("表单约束探针"));
        formDiv.addNoGrowChild(createBodyLabel("这张卡片只验证标签列和字段列在父宽度变化时能否正确重排。标签列保持固定参考宽度，字段列允许拉伸或换到下一行。"));
        formDiv.addNoGrowChild(createFormRow("主题名称", themeInput));
        formDiv.addNoGrowChild(createFormRow("命名空间", namespaceInput));
        formDiv.addNoGrowChild(createFormRow("资源路径", pathInput));
        formDiv.addNoGrowChild(createFormRow("换行提示", wrapToggle));
        formDiv.addNoGrowChild(createFormRow("宽度档位", widthPresetSelector));
        formDiv.addNoGrowChild(createToolbarRow().addNoGrowChild(refreshButton));
        formCard.addChild(formDiv);

        DivWidget wrapDiv = createSectionBlock();
        wrapDiv.addNoGrowChild(createSectionTitle("文本换行与最小宽度探针"));
        wrapDiv.addNoGrowChild(createBodyLabel("这里故意放一段中英混排文本，观察在不同页宽下是否优先正常换行，而不是把整段中文误判为一个不可压缩长词。"));
        wrapDiv.addNoGrowChild(wrapSampleLabel);
        wrapDiv.addNoGrowChild(wrapMetricsLabel);
        wrapCard.addChild(wrapDiv);

        DivWidget divScrollCardDiv = createSectionBlock();
        divScrollCardDiv.addNoGrowChild(createSectionTitle("统一尺寸契约探针"));
        divScrollCardDiv.addNoGrowChild(createBodyLabel("这块直接验证 Div 父容器是否开始读取统一的 `UiLayoutSpec`：内部探针使用 `width=100%` 和 `height=220px`，如果仍然不产生内部滚动，就说明尺寸契约仍然割裂。"));
        for (int index = 1; index <= 10; index++) {
            divScrollProbe.addNoGrowChild(createBodyLabel("Div 自滚动条目 " + index
                    + "：这里故意放入重复的中英混排说明，只有当 Div 真正认 `UiLayoutSpec.height=220px` 时，这块区域才会产生稳定的内部滚动，而不是继续随外层页面一起长高。"));
        }
        divScrollCardDiv.addNoGrowChild(divScrollProbe);
        divScrollCardDiv.addNoGrowChild(divScrollMetricsLabel);
        divScrollCard.addChild(divScrollCardDiv);

        DivWidget performanceDiv = createSectionBlock();
        performanceDiv.addNoGrowChild(createSectionTitle("UI 性能统计"));
        performanceDiv.addNoGrowChild(createBodyLabel("这一块直接读取框架运行时采样结果，观察帧耗时、输入路由、命中测试次数和最慢组件类型。若这里的数据异常，再继续细分具体控件或布局阶段。"));
        performanceDiv.addNoGrowChild(performanceFrameLabel);
        performanceDiv.addNoGrowChild(performanceWidgetLabel);
        performanceDiv.addNoGrowChild(performanceHotspotLabel);
        performanceDiv.addNoGrowChild(performancePhaseLabel);
        performanceDiv.addNoGrowChild(performanceFontLabel);
        performanceCard.addChild(performanceDiv);

        DivWidget mutationDiv = createSectionBlock();
        mutationDiv.addNoGrowChild(createSectionTitle("高频字符变更探针"));
        mutationDiv.addNoGrowChild(createBodyLabel("这块专门区分三种压力：`§k` 混淆文本只在绘制阶段随机替换字符，不主动触发布局；`同长替换` 会高频调用 `setText()` 但尽量保持长度稳定；`长文重排` 会持续改变长文本内容并触发换行重算。通过它可以直接观察字体绘制和布局失效谁更伤。"));
        mutationDiv.addNoGrowChild(createFormRow("自动运行", mutationToggle));
        mutationDiv.addNoGrowChild(createFormRow("变更模式", mutationModeSelector));
        mutationDiv.addNoGrowChild(createFormRow("刷新频率", mutationRateSelector));
        mutationDiv.addNoGrowChild(mutationMetricsLabel);
        mutationDiv.addNoGrowChild(mutationSampleLabel);
        mutationCard.addChild(mutationDiv);

        cardsFlow.addFlexChild(formCard, 1.5F);
        cardsFlow.addFlexChild(wrapCard, 1.0F);

        diagnosticPage.addDocumentChild(createTitleLabel("布局诊断页"));
        diagnosticPage.addDocumentChild(createBodyLabel("如果这一页的两张卡片仍然在不合理的宽度下并排、中文换行异常、表单行不按父宽度变化，或者卡片不能同时按 flex-basis 和增长权重自然分配空间，那么说明底层尺寸链路仍然有问题。"));
        diagnosticPage.addDocumentChild(overviewCard);
        diagnosticPage.addDocumentChild(cardsFlow);
        diagnosticPage.addDocumentChild(performanceCard);
        diagnosticPage.addDocumentChild(mutationCard);
        diagnosticPage.addDocumentChild(divScrollCard);

        root.addChild(diagnosticPage);
    }

    /**
     * 创建一行表单结构。
     *
     * @param labelText 标签文本
     * @param field 字段控件
     * @return 表单行
     */
    private DivWidget createFormRow(String labelText, Widget field) {
        LabelWidget label = createFormLabel(labelText);
        DivWidget row = createFormFieldRow();
        row.addNoGrowChild(label);
        row.addFlexChild(field);
        return row;
    }

    /**
     * 刷新诊断文本。
     */
    private void refreshDiagnostics() {
        updateLabelText(viewportMetricsLabel, "窗口 " + width + "x" + height + "；页面壳 " + diagnosticPage.getWidth() + "x"
                + diagnosticPage.getHeight() + "；总览卡片 " + overviewCard.getWidth() + "x" + overviewCard.getHeight()
                + "；表单卡片 " + formCard.getWidth() + "x" + formCard.getHeight() + "；文本卡片 " + wrapCard.getWidth() + "x"
                + wrapCard.getHeight() + "。\n如果页面壳仍然明显偏窄，优先检查 `DocumentShellWidget`；如果卡片宽度异常，优先检查 `DivWidget` 的盒模型计算和最小宽度传播。 ");

        updateLabelText(scrollMetricsLabel, "滚动偏移 " + diagnosticPage.getScrollOffset() + " / " + diagnosticPage.getMaxScrollOffset()
                + "；可视内容区 " + diagnosticPage.getVisibleContentWidth() + "x" + diagnosticPage.getVisibleContentHeight()
                + "；内容区 " + diagnosticPage.getContentWidth() + "x" + diagnosticPage.getContentHeight()
                + "。如果内容高度已经明显超过可视区，但最大滚动仍为 0，说明页面滚动高度计算仍然有问题。 ");

        updateLabelText(wrapSampleLabel, "诊断文本：当前布局需要同时处理中文说明、English identifier、路径 `assets/qz_uilib/ui/diagnostic` 以及较长的字段值。只要父宽度变化，文本就应该优先自然换行，而不是继续保持单行并把右侧内容裁掉。当前主题为 “"
                + textOrPlaceholder(themeInput.getText()) + "”，命名空间为 “" + textOrPlaceholder(namespaceInput.getText()) + "”。");

        updateLabelText(wrapMetricsLabel, "文本卡片宽度 " + wrapCard.getWidth() + "；当前操作：" + actionStateText
                + "；宽度档位：" + widthPresetSelector.getSelectedOption()
                + "。如果中文说明不再把整段文本撑成一个极宽最小值，说明 `LabelWidget#getMinContentWidth()` 的修正已经生效。 ");
        updateLabelText(divScrollMetricsLabel, "Div 自滚动偏移 " + divScrollProbe.getVerticalScrollOffset() + " / "
                + divScrollProbe.getMaxVerticalScrollOffset() + "；可视内容区 " + divScrollProbe.getVisibleContentWidth() + "x"
                + divScrollProbe.getVisibleContentHeight() + "；内容区 " + divScrollProbe.getContentWidth() + "x"
                + divScrollProbe.getContentHeight() + "。如果这里终于出现稳定的内部滚动，说明 Div 组件开始真正读取统一的宽高契约。 ");
        updateLabelText(actionStateLabel, "最近状态：" + actionStateText);
        updateLabelText(mutationMetricsLabel, "探针状态：" + (mutationToggle.isChecked() ? "运行中" : "已停止")
                + "；模式：" + mutationModeSelector.getSelectedOption()
                + "；频率：" + mutationRateSelector.getSelectedOption()
                + "；实际 setText 次数：" + mutationSetTextCount
                + "；样本文本长度：" + (mutationSampleLabel.getText() == null ? 0 : mutationSampleLabel.getText().length())
                + "；样本标签尺寸：" + mutationSampleLabel.getWidth() + "x" + mutationSampleLabel.getHeight()
                + "。若 `§k渲染` 模式也慢，优先怀疑字体绘制；若只在 `长文重排` 模式慢，更像布局与换行重算。");

        UiRuntimeStats runtimeStats = getUiRuntimeStats();
        if (runtimeStats.getSampledFrameCount() <= 0 || !getClass().getSimpleName().equals(runtimeStats.getScreenName())) {
            updateLabelText(performanceFrameLabel, "性能采样尚未稳定，进入页面后至少完成一帧渲染才会显示当前统计。");
            updateLabelText(performanceWidgetLabel, "等待统计：组件渲染次数、命中测试访问次数和输入事件数会在本页持续刷新。");
            updateLabelText(performanceHotspotLabel, "等待热点：最慢组件类型会在完成当前页采样后显示。");
            updateLabelText(performancePhaseLabel, "等待阶段：布局分阶段耗时会在当前页完成一帧后显示。");
            updateLabelText(performanceFontLabel, "等待字体：字符页上传、字宽缓存命中和四边形数量会在当前页持续刷新。");
            return;
        }

        updateLabelText(performanceFrameLabel, String.format(
                Locale.ROOT,
                "当前帧 %.2f ms；近 %d 帧均值 %.2f ms；窗口内最大 %.2f ms；平均 FPS %.1f。若刚重新进入页面，前几十帧仍属于历史窗口热身期，应优先看当前帧与当前热点，而不是立刻看均值。",
                Double.valueOf(runtimeStats.getFrameTimeMs()),
                Integer.valueOf(runtimeStats.getSampledFrameCount()),
                Double.valueOf(runtimeStats.getAverageFrameTimeMs()),
                Double.valueOf(runtimeStats.getMaxFrameTimeMs()),
                Double.valueOf(runtimeStats.getAverageFps())));
        updateLabelText(performanceWidgetLabel, String.format(
                Locale.ROOT,
                "渲染 %.2f ms；贴屏 %.2f ms；输入路由 %.2f ms；鼠标/键盘/文本事件 %d/%d/%d；命中测试访问 %d 次；组件渲染 %d 次；最大深度 %d；慢帧 %d/%d。",
                Double.valueOf(runtimeStats.getRenderTimeMs()),
                Double.valueOf(runtimeStats.getPresentTimeMs()),
                Double.valueOf(runtimeStats.getInputRoutingTimeMs()),
                Integer.valueOf(runtimeStats.getMouseEventCount()),
                Integer.valueOf(runtimeStats.getKeyEventCount()),
                Integer.valueOf(runtimeStats.getTextEventCount()),
                Long.valueOf(runtimeStats.getHitTestVisitCount()),
                Integer.valueOf(runtimeStats.getWidgetRenderCount()),
                Integer.valueOf(runtimeStats.getMaxWidgetDepth()),
                Integer.valueOf(runtimeStats.getSlowFrameCount()),
                Integer.valueOf(runtimeStats.getSampledFrameCount())));
        updateLabelText(performanceHotspotLabel, String.format(
                Locale.ROOT,
                "最慢自身组件：%s %.2f ms；最慢总计组件：%s %.2f ms；当前视口 %dx%d GUI / %dx%d 原生。若总计热点总是容器类，说明子树整体太重；若自身热点稳定落在单一控件，说明该控件内部逻辑需要单独优化。",
                displayWidgetClass(runtimeStats.getSlowestWidgetSelfClassName()),
                Double.valueOf(runtimeStats.getSlowestWidgetSelfTimeMs()),
                displayWidgetClass(runtimeStats.getSlowestWidgetTotalClassName()),
                Double.valueOf(runtimeStats.getSlowestWidgetTotalTimeMs()),
                Integer.valueOf(runtimeStats.getGuiWidth()),
                Integer.valueOf(runtimeStats.getGuiHeight()),
                Integer.valueOf(runtimeStats.getNativeWidth()),
                Integer.valueOf(runtimeStats.getNativeHeight())));

        String phaseSummary = runtimeStats.getPhaseSummary();
        updateLabelText(performancePhaseLabel, "阶段热点："
                + (phaseSummary == null || phaseSummary.isEmpty()
                        ? "当前帧暂无阶段采样。若后续出现慢帧，这里会显示 prepare/apply overflow 与 row/column/wrap 测量的累计耗时。"
                        : phaseSummary));

        FontRuntimeStats fontStats = FontService.getInstance().getRuntimeStats();
        updateLabelText(performanceFontLabel, String.format(
                Locale.ROOT,
                "字体统计：待上传 %d；就绪字形 %d；普通/粗体页 %d/%d；最近 1 秒 draw-stage 上传 %d；本帧四边形 %d；字宽缓存命中/未命中 %d/%d。若未命中或待上传在慢帧时突然升高，再优先怀疑字体系统。",
                Integer.valueOf(fontStats.getPendingUploadCount()),
                Integer.valueOf(fontStats.getReadyGlyphCount()),
                Integer.valueOf(fontStats.getNormalPageCount()),
                Integer.valueOf(fontStats.getBoldPageCount()),
                Integer.valueOf(fontStats.getQueuedDrawStageUploadCount()),
                Integer.valueOf(fontStats.getFrameQuadCount()),
                Long.valueOf(fontStats.getWidthCacheHitCount()),
                Long.valueOf(fontStats.getWidthCacheMissCount())));
    }

    private DivWidget createCardPanel() {
        return UiDocumentTheme.applyCardSurface(new DivWidget()
                .setColumn()
                .setGap(12));
    }

    private DivWidget createSectionBlock() {
        return new DivWidget().setColumn().setGap(12);
    }

    private DivWidget createWrapRow() {
        return new DivWidget().setRow().setWrap(DivWidget.Wrap.WRAP).setGap(16).setFillLayout();
    }

    private DivWidget createToolbarRow() {
        return new DivWidget().setRow().setWrap(DivWidget.Wrap.WRAP).setGap(12).setFillLayout();
    }

    private DivWidget createFormFieldRow() {
        return new DivWidget()
                .setRow()
                .setAlignItems(DivWidget.AlignItems.CENTER)
                .setWrap(DivWidget.Wrap.WRAP)
                .setGap(16)
                .setFillLayout();
    }

    private LabelWidget createTitleLabel(String text) {
        return UiDocumentTheme.applyTitleText(new LabelWidget(text)).setWrap(true).setMaxLines(2);
    }

    private LabelWidget createSectionTitle(String text) {
        return UiDocumentTheme.applyTitleText(new LabelWidget(text)).setWrap(true).setMaxLines(2);
    }

    private LabelWidget createBodyLabel(String text) {
        return UiDocumentTheme.applyBodyText(new LabelWidget(text)).setWrap(true).setMaxLines(8);
    }

    private LabelWidget createFormLabel(String text) {
        LabelWidget label = UiDocumentTheme.applyEmphasisText(new LabelWidget(text));
        label.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.px(156)).setMinWidth(156).setMaxWidth(156));
        return label;
    }

    private int clampValue(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private String textOrPlaceholder(String value) {
        return value == null || value.isEmpty() ? "<未填写>" : value;
    }

    private String displayWidgetClass(String className) {
        return className == null || className.isEmpty() ? "<暂无>" : className;
    }

    /**
     * 驱动高频字符变更探针。
     */
    private void tickHighFrequencyMutationProbe() {
        if (!mutationToggle.isChecked()) {
            return;
        }

        String currentMode = mutationModeSelector.getSelectedOption();
        if (!currentMode.equals(lastMutationMode)) {
            resetMutationProbeState(false);
        }

        if ("§k渲染".equals(currentMode)) {
            if (lastMutationText.isEmpty()) {
                applyMutationProbeText(buildObfuscatedProbeText());
            }
            return;
        }

        long now = System.nanoTime();
        long intervalNanos = resolveMutationIntervalNanos();
        if (intervalNanos > 0L && now - lastMutationUpdateNanos < intervalNanos) {
            return;
        }
        lastMutationUpdateNanos = now;
        mutationSequence++;

        if ("同长替换".equals(currentMode)) {
            applyMutationProbeText(buildStableWidthProbeText(mutationSequence));
            return;
        }
        applyMutationProbeText(buildLongReflowProbeText(mutationSequence));
    }

    /**
     * 重置高频字符变更探针状态。
     *
     * @param resetText 是否同时恢复样本文本
     */
    private void resetMutationProbeState(boolean resetText) {
        lastMutationMode = mutationModeSelector.getSelectedOption();
        lastMutationUpdateNanos = 0L;
        mutationSequence = 0;
        mutationSetTextCount = 0;
        lastMutationText = "";
        if (resetText) {
            updateLabelText(mutationSampleLabel, mutationToggle.isChecked()
                    ? "探针已重置，等待下一次文本变更。"
                    : "探针未启用。开启后可以直接观察 `§k` 混淆文本、同长度替换和长文重排在当前容器中的表现。"
            );
        }
    }

    /**
     * 应用新的探针文本，并统计真实的 setText 次数。
     *
     * @param text 新文本
     */
    private void applyMutationProbeText(String text) {
        if (text == null) {
            text = "";
        }
        if (text.equals(lastMutationText)) {
            return;
        }
        lastMutationText = text;
        mutationSetTextCount++;
        mutationSampleLabel.setText(text);
    }

    /**
     * 解析当前探针频率。
     *
     * @return 纳秒间隔；0 表示每帧
     */
    private long resolveMutationIntervalNanos() {
        int selectedIndex = mutationRateSelector.getSelectedIndex();
        if (selectedIndex == 1) {
            return 50_000_000L;
        }
        if (selectedIndex == 2) {
            return 200_000_000L;
        }
        return 0L;
    }

    /**
     * 构造只依赖 `§k` 绘制随机字符的样本文本。
     *
     * @return 混淆样本
     */
    private String buildObfuscatedProbeText() {
        return "§7§kQZUILIB-DIAGNOSTIC-STREAM-00000000-ABCDEFGHIJKLMNOPQRSTUVWXYZ§r\n"
                + "§7这个模式不会持续调用 setText，而是依赖 §k 在绘制阶段随机替换字符。若它本身也出现明显尖峰，更像是字体绘制或 glyph 准备在放大耗时。§r";
    }

    /**
     * 构造同长度高频替换样本。
     *
     * @param sequence 当前序号
     * @return 同长度样本
     */
    private String buildStableWidthProbeText(int sequence) {
        return "同长替换样本 " + formatCounter(sequence) + " / token=" + buildRollingToken(sequence, 24)
                + " / mirror=" + buildRollingToken(sequence * 3 + 7, 24)
                + "。这一模式会高频 setText，但尽量保持字符总长度稳定，用来观察文本替换本身是否会显著拖慢容器。";
    }

    /**
     * 构造会触发换行与布局变化的长文本样本。
     *
     * @param sequence 当前序号
     * @return 长文本样本
     */
    private String buildLongReflowProbeText(int sequence) {
        int extraLength = 6 + Math.abs(sequence % 20);
        return "长文重排样本 " + formatCounter(sequence)
                + "：当前路径片段为 assets/qz_uilib/ui/diagnostic/" + buildRollingToken(sequence, extraLength)
                + "，描述串为 `" + buildRollingToken(sequence * 5 + 11, extraLength + 10)
                + "`。这一模式会持续改变长文本长度和断行位置，用来观察容器换行、最小宽度传播和布局失效是否出现明显放大。";
    }

    /**
     * 构造滚动字母数字串。
     *
     * @param seed 种子
     * @param length 长度
     * @return 结果文本
     */
    private String buildRollingToken(int seed, int length) {
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < length; index++) {
            int alphabetIndex = Math.abs(seed + index * 7) % alphabet.length();
            builder.append(alphabet.charAt(alphabetIndex));
        }
        return builder.toString();
    }

    /**
     * 格式化固定宽度计数器。
     *
     * @param value 计数值
     * @return 补零字符串
     */
    private String formatCounter(int value) {
        String raw = Integer.toString(Math.max(0, value));
        StringBuilder builder = new StringBuilder();
        for (int index = raw.length(); index < 6; index++) {
            builder.append('0');
        }
        builder.append(raw);
        return builder.toString();
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
