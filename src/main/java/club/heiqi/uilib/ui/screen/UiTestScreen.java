package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.RelativePanelWidget;
import club.heiqi.uilib.ui.control.ResponsivePageWidget;
import club.heiqi.uilib.ui.control.SegmentedSelectorWidget;
import club.heiqi.uilib.ui.control.TextInputWidget;
import club.heiqi.uilib.ui.control.ToggleSwitchWidget;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 当前阶段的最小布局诊断页。
 */
public class UiTestScreen extends BaseScreen {

    private final ResponsivePageWidget diagnosticPage = new ResponsivePageWidget();

    private final DivWidget overviewCard = createCardPanel();
    private final DivWidget formCard = createCardPanel();
    private final DivWidget wrapCard = createCardPanel();
    private final DivWidget divScrollCard = createCardPanel();
    private final DivWidget divScrollProbe = new DivWidget()
            .setSectionColumn()
            .setVerticalScrollOnly()
            .setPadding(14)
            .setGap(10);

    private final LabelWidget viewportMetricsLabel = new LabelWidget("");
    private final LabelWidget scrollMetricsLabel = new LabelWidget("");
    private final LabelWidget wrapMetricsLabel = new LabelWidget("");
    private final LabelWidget divScrollMetricsLabel = new LabelWidget("");
    private final LabelWidget wrapSampleLabel = new LabelWidget("");
    private final LabelWidget actionStateLabel = new LabelWidget("");

    private final TextInputWidget themeInput = new TextInputWidget();
    private final TextInputWidget namespaceInput = new TextInputWidget();
    private final TextInputWidget pathInput = new TextInputWidget();
    private final ToggleSwitchWidget wrapToggle = new ToggleSwitchWidget("启用");
    private final SegmentedSelectorWidget widthPresetSelector = new SegmentedSelectorWidget("窄页", "中页", "宽页");
    private final ButtonWidget refreshButton = new ButtonWidget("刷新诊断文本");

    private String actionStateText = "尚未操作";
    @Override
    protected void buildUi(Widget root) {
        configurePage();
        configureControls();
        assembleUi(root);
        refreshDiagnostics();
    }

    @Override
    protected void onResize(int width, int height) {
        super.onResize(width, height);

        int pageMargin = Math.max(24, width / 34);
        int topMargin = Math.max(28, height / 28);
        RelativePanelWidget rootWidget = (RelativePanelWidget) getRootWidget();
        rootWidget.setPadding(pageMargin, topMargin, pageMargin, pageMargin);

        int pagePaddingX = clampValue(width / 48, 16, 28);
        int pagePaddingY = clampValue(height / 36, 14, 24);
        diagnosticPage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);

        refreshDiagnostics();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refreshDiagnostics();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * 配置页面壳。
     */
    private void configurePage() {
        diagnosticPage.setPadding(24, 22, 24, 22)
                .setFillColor(0xD0151C25)
                .setBorderColor(0xFF86A8F0)
                .setViewportWidthRange(680, 1080)
                .setMinViewportHeight(560)
                .setViewportRatio(0.92F, 0.90F);
    }

    /**
     * 配置诊断控件。
     */
    private void configureControls() {
        viewportMetricsLabel.setColor(0xFFF6D78E).setShadow(false).setWrap(true).setMaxLines(4);
        scrollMetricsLabel.setColor(0xFFB5D0FF).setShadow(false).setWrap(true).setMaxLines(4);
        wrapMetricsLabel.setColor(0xFFB5D0FF).setShadow(false).setWrap(true).setMaxLines(6);
        divScrollMetricsLabel.setColor(0xFFB5D0FF).setShadow(false).setWrap(true).setMaxLines(5);
        wrapSampleLabel.setColor(0xFFD7E3FF).setShadow(false).setWrap(true).setMaxLines(10);
        actionStateLabel.setColor(0xFFB5D0FF).setShadow(false).setWrap(true).setMaxLines(2);

        formCard.setLayoutSpec(new UiLayoutSpec().setFlexBasis(UiLength.px(460)).setMinWidth(320).setMaxWidth(620));
        wrapCard.setLayoutSpec(new UiLayoutSpec().setFlexBasis(UiLength.px(340)).setMinWidth(260).setMaxWidth(480));
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
        DivWidget pageRoot = new DivWidget().setPageColumn();
        DivWidget cardsFlow = new DivWidget().setContentFlow();

        DivWidget overviewDiv = new DivWidget().setSectionColumn();
        overviewDiv.addNoGrowChild(createSectionTitle("当前状态"));
        overviewDiv.addNoGrowChild(createBodyLabel("旧测试页已经完全清空。当前只保留这一张最小诊断页，专门验证页面壳尺寸、卡片换行、中文文本最小宽度和父容器约束是否正确。"));
        overviewDiv.addNoGrowChild(viewportMetricsLabel);
        overviewDiv.addNoGrowChild(scrollMetricsLabel);
        overviewDiv.addNoGrowChild(actionStateLabel);
        overviewCard.addChild(overviewDiv);

        DivWidget formDiv = new DivWidget().setSectionColumn();
        formDiv.addNoGrowChild(createSectionTitle("表单约束探针"));
        formDiv.addNoGrowChild(createBodyLabel("这张卡片只验证 `setFormRow()` 在父宽度变化时能否正确重排。标签列保持固定参考宽度，字段列允许拉伸或换到下一行。"));
        formDiv.addNoGrowChild(createFormRow("主题名称", themeInput));
        formDiv.addNoGrowChild(createFormRow("命名空间", namespaceInput));
        formDiv.addNoGrowChild(createFormRow("资源路径", pathInput));
        formDiv.addNoGrowChild(createFormRow("换行提示", wrapToggle));
        formDiv.addNoGrowChild(createFormRow("宽度档位", widthPresetSelector));
        formDiv.addNoGrowChild(new DivWidget().setButtonFlow().addNoGrowChild(refreshButton));
        formCard.addChild(formDiv);

        DivWidget wrapDiv = new DivWidget().setSectionColumn();
        wrapDiv.addNoGrowChild(createSectionTitle("文本换行与最小宽度探针"));
        wrapDiv.addNoGrowChild(createBodyLabel("这里故意放一段中英混排文本，观察在不同页宽下是否优先正常换行，而不是把整段中文误判为一个不可压缩长词。"));
        wrapDiv.addNoGrowChild(wrapSampleLabel);
        wrapDiv.addNoGrowChild(wrapMetricsLabel);
        wrapCard.addChild(wrapDiv);

        DivWidget divScrollCardDiv = new DivWidget().setSectionColumn();
        divScrollCardDiv.addNoGrowChild(createSectionTitle("统一尺寸契约探针"));
        divScrollCardDiv.addNoGrowChild(createBodyLabel("这块直接验证 Div 父容器是否开始读取统一的 `UiLayoutSpec`：内部探针使用 `width=100%` 和 `height=220px`，如果仍然不产生内部滚动，就说明尺寸契约仍然割裂。"));
        for (int index = 1; index <= 10; index++) {
            divScrollProbe.addNoGrowChild(createBodyLabel("Div 自滚动条目 " + index
                    + "：这里故意放入重复的中英混排说明，只有当 Div 真正认 `UiLayoutSpec.height=220px` 时，这块区域才会产生稳定的内部滚动，而不是继续随外层页面一起长高。"));
        }
        divScrollCardDiv.addNoGrowChild(divScrollProbe);
        divScrollCardDiv.addNoGrowChild(divScrollMetricsLabel);
        divScrollCard.addChild(divScrollCardDiv);

        cardsFlow.addFlexChild(formCard, 1.5F);
        cardsFlow.addFlexChild(wrapCard, 1.0F);

        pageRoot.addNoGrowChild(createTitleLabel("布局诊断页"));
        pageRoot.addNoGrowChild(createBodyLabel("如果这一页的两张卡片仍然在不合理的宽度下并排、中文换行异常、表单行不按父宽度变化，或者卡片不能同时按 flex-basis 和增长权重自然分配空间，那么说明底层尺寸链路仍然有问题。"));
        pageRoot.addNoGrowChild(overviewCard);
        pageRoot.addNoGrowChild(cardsFlow);
        pageRoot.addNoGrowChild(divScrollCard);

        diagnosticPage.getContent().addChild(pageRoot);
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
        DivWidget row = new DivWidget().setFormRow();
        row.addNoGrowChild(label);
        row.addFlexChild(field);
        return row;
    }

    /**
     * 刷新诊断文本。
     */
    private void refreshDiagnostics() {
        viewportMetricsLabel.setText("窗口 " + width + "x" + height + "；页面壳 " + diagnosticPage.getWidth() + "x"
                + diagnosticPage.getHeight() + "；总览卡片 " + overviewCard.getWidth() + "x" + overviewCard.getHeight()
                + "；表单卡片 " + formCard.getWidth() + "x" + formCard.getHeight() + "；文本卡片 " + wrapCard.getWidth() + "x"
                + wrapCard.getHeight() + "。\n如果页面壳仍然明显偏窄，优先检查 `ResponsivePageWidget`；如果卡片宽度异常，优先检查 `DivWidget` 的盒模型计算和最小宽度传播。 ");

        scrollMetricsLabel.setText("滚动偏移 " + diagnosticPage.getScrollOffset() + " / " + diagnosticPage.getMaxScrollOffset()
                + "；可视内容区 " + diagnosticPage.getVisibleContentWidth() + "x" + diagnosticPage.getVisibleContentHeight()
                + "；内容区 " + diagnosticPage.getContentWidth() + "x" + diagnosticPage.getContentHeight()
                + "。如果内容高度已经明显超过可视区，但最大滚动仍为 0，说明页面滚动高度计算仍然有问题。 ");

        wrapSampleLabel.setText("诊断文本：当前布局需要同时处理中文说明、English identifier、路径 `assets/qz_uilib/ui/diagnostic` 以及较长的字段值。只要父宽度变化，文本就应该优先自然换行，而不是继续保持单行并把右侧内容裁掉。当前主题为 “"
                + textOrPlaceholder(themeInput.getText()) + "”，命名空间为 “" + textOrPlaceholder(namespaceInput.getText()) + "”。");

        wrapMetricsLabel.setText("文本卡片宽度 " + wrapCard.getWidth() + "；当前操作：" + actionStateText
                + "；宽度档位：" + widthPresetSelector.getSelectedOption()
                + "。如果中文说明不再把整段文本撑成一个极宽最小值，说明 `LabelWidget#getMinContentWidth()` 的修正已经生效。 ");
        divScrollMetricsLabel.setText("Div 自滚动偏移 " + divScrollProbe.getVerticalScrollOffset() + " / "
                + divScrollProbe.getMaxVerticalScrollOffset() + "；可视内容区 " + divScrollProbe.getVisibleContentWidth() + "x"
                + divScrollProbe.getVisibleContentHeight() + "；内容区 " + divScrollProbe.getContentWidth() + "x"
                + divScrollProbe.getContentHeight() + "。如果这里终于出现稳定的内部滚动，说明 Div 组件开始真正读取统一的宽高契约。 ");
        actionStateLabel.setText("最近状态：" + actionStateText);
    }

    private DivWidget createCardPanel() {
        return new DivWidget().setSectionColumn().setPadding(20).setFillColor(0xAA111721).setBorderColor(0xFF6E8FCB);
    }

    private LabelWidget createTitleLabel(String text) {
        return new LabelWidget(text).setColor(0xFFFFFFFF).setShadow(false).setWrap(true).setMaxLines(2);
    }

    private LabelWidget createSectionTitle(String text) {
        return new LabelWidget(text).setColor(0xFFFFFFFF).setShadow(false).setWrap(true).setMaxLines(2);
    }

    private LabelWidget createBodyLabel(String text) {
        return new LabelWidget(text).setColor(0xFFD7E3FF).setShadow(false).setWrap(true).setMaxLines(8);
    }

    private LabelWidget createFormLabel(String text) {
        LabelWidget label = new LabelWidget(text).setColor(0xFFF6D78E).setShadow(false);
        label.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.px(156)).setMinWidth(156).setMaxWidth(156));
        return label;
    }

    private int clampValue(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private String textOrPlaceholder(String value) {
        return value == null || value.isEmpty() ? "<未填写>" : value;
    }
}
