package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.ResponsiveContainerWidget;
import club.heiqi.uilib.ui.control.ResponsivePageWidget;
import club.heiqi.uilib.ui.control.ResponsivePanelWidget;
import club.heiqi.uilib.ui.control.SegmentedSelectorWidget;
import club.heiqi.uilib.ui.control.TextInputWidget;
import club.heiqi.uilib.ui.control.ToggleSwitchWidget;
import club.heiqi.uilib.ui.layout.UiAnchor;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 基于 Div 语义 API 的新版测试界面。
 */
public class UiTestScreen extends BaseScreen {

    private final ResponsivePageWidget menuPage = new ResponsivePageWidget();
    private final ResponsivePageWidget layoutGuidePage = new ResponsivePageWidget();
    private final ResponsivePageWidget formDemoPage = new ResponsivePageWidget();
    private final ResponsivePageWidget interactionPage = new ResponsivePageWidget();

    private final ButtonWidget openLayoutGuideButton = new ButtonWidget("查看 Div 语义概览");
    private final ButtonWidget openFormDemoButton = new ButtonWidget("查看 Div 表单页");
    private final ButtonWidget openInteractionButton = new ButtonWidget("查看 Div 交互页");
    private final LabelWidget menuMetricsLabel = new LabelWidget("");
    private final LabelWidget layoutSummaryLabel = new LabelWidget("");

    private final TextInputWidget formThemeInput = new TextInputWidget();
    private final TextInputWidget formNamespaceInput = new TextInputWidget();
    private final TextInputWidget formAssetPathInput = new TextInputWidget();
    private final ToggleSwitchWidget formAnimationToggle = new ToggleSwitchWidget("启用");
    private final SegmentedSelectorWidget formDensitySelector = new SegmentedSelectorWidget("紧凑", "舒适", "宽松");
    private final LabelWidget formSummaryLabel = new LabelWidget("");
    private final LabelWidget formActionLabel = new LabelWidget("");
    private final ButtonWidget applyFormButton = new ButtonWidget("应用配置");
    private final ButtonWidget resetFormButton = new ButtonWidget("恢复默认");
    private final ButtonWidget backFromFormButton = new ButtonWidget("返回总览");

    private final TextInputWidget interactionNameInput = new TextInputWidget();
    private final TextInputWidget interactionPathInput = new TextInputWidget();
    private final ToggleSwitchWidget interactionKeyboardToggle = new ToggleSwitchWidget("允许");
    private final SegmentedSelectorWidget interactionModeSelector = new SegmentedSelectorWidget("浏览", "编辑", "调试");
    private final LabelWidget interactionSummaryLabel = new LabelWidget("");
    private final ButtonWidget applyInteractionButton = new ButtonWidget("提交状态");
    private final ButtonWidget resetInteractionButton = new ButtonWidget("重置交互页");
    private final ButtonWidget backFromInteractionButton = new ButtonWidget("返回总览");

    private boolean formAnimationsEnabled = true;
    private int formDensityIndex = 1;
    private String formLastAction = "尚未提交";
    private String layoutLastAction = "尚未交互";
    private String interactionLastAction = "尚未操作";
    private Widget currentPage;
    private int viewportWidthHint = 1280;
    private int viewportHeightHint = 720;

    @Override
    protected void buildUi(Widget root) {
        configurePage(menuPage, 0.56F, 0.52F, 420, 340);
        configurePage(layoutGuidePage, 0.82F, 0.86F, 920, 680);
        configurePage(formDemoPage, 0.78F, 0.84F, 820, 620);
        configurePage(interactionPage, 0.74F, 0.82F, 760, 580);

        configureActions();
        assembleMenuPage(root);
        assembleLayoutGuidePage(root);
        assembleFormDemoPage(root);
        assembleInteractionPage(root);

        refreshMenuMetrics();
        refreshLayoutSummary();
        refreshFormState();
        refreshInteractionState();

        currentPage = menuPage;
        showPage(menuPage);
    }

    @Override
    protected void onResize(int width, int height) {
        super.onResize(width, height);
        viewportWidthHint = width;
        viewportHeightHint = height;

        int pageMargin = Math.max(24, width / 34);
        int topMargin = Math.max(28, height / 28);
        ResponsiveContainerWidget rootWidget = (ResponsiveContainerWidget) getRootWidget();
        rootWidget.setPadding(pageMargin, topMargin, pageMargin, pageMargin);

        applyAdaptiveChrome();
        applyAdaptiveSizes();
        refreshMenuMetrics();
        refreshLayoutSummary();
        refreshFormState();
        refreshInteractionState();
        showPage(currentPage == null ? menuPage : currentPage);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refreshLayoutSummary();
        refreshFormState();
        refreshInteractionState();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void configureActions() {
        menuMetricsLabel.setColor(0xFFF6D78E).setShadow(false).setWrap(true).setMaxLines(3);
        layoutSummaryLabel.setColor(0xFFF6D78E).setShadow(false).setWrap(true).setMaxLines(4);
        formSummaryLabel.setColor(0xFFC8D8F3).setShadow(false).setWrap(true).setMaxLines(8);
        formActionLabel.setColor(0xFFB5D0FF).setShadow(false).setWrap(true).setMaxLines(2);
        interactionSummaryLabel.setColor(0xFFB5D0FF).setShadow(false).setWrap(true).setMaxLines(6);

        openLayoutGuideButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(layoutGuidePage);
            }
        });
        openFormDemoButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(formDemoPage);
            }
        });
        openInteractionButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(interactionPage);
            }
        });

        formThemeInput.setPlaceholder("例如：Qz Native UI").setText("Qz Native UI").setMaxLength(48);
        formNamespaceInput.setPlaceholder("例如：qz_uilib").setText("qz_uilib").setMaxLength(48);
        formAssetPathInput.setPlaceholder("例如：assets/qz_uilib/ui").setText("assets/qz_uilib/ui").setMaxLength(96);
        formAnimationToggle.setChecked(true).setToggleHandler(new Runnable() {
            @Override
            public void run() {
                formAnimationsEnabled = formAnimationToggle.isChecked();
                formLastAction = formAnimationsEnabled ? "已启用界面过渡动画" : "已关闭界面过渡动画";
                refreshFormState();
            }
        });
        formDensitySelector.setSelectedIndex(formDensityIndex).setChangeHandler(new Runnable() {
            @Override
            public void run() {
                formDensityIndex = formDensitySelector.getSelectedIndex();
                formLastAction = "已切换布局密度到 " + formDensitySelector.getSelectedOption();
                refreshFormState();
            }
        });
        applyFormButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                formLastAction = "已应用当前 Div 表单配置";
                refreshFormState();
            }
        });
        resetFormButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                formThemeInput.setText("Qz Native UI");
                formNamespaceInput.setText("qz_uilib");
                formAssetPathInput.setText("assets/qz_uilib/ui");
                formAnimationsEnabled = true;
                formDensityIndex = 1;
                formAnimationToggle.setChecked(true);
                formDensitySelector.setSelectedIndex(formDensityIndex);
                formLastAction = "已恢复表单默认值";
                refreshFormState();
            }
        });
        backFromFormButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(menuPage);
            }
        });

        interactionNameInput.setPlaceholder("例如：Focus Demo").setText("Focus Demo").setMaxLength(48);
        interactionPathInput.setPlaceholder("例如：ui/focus/demo").setText("ui/focus/demo").setMaxLength(96);
        interactionKeyboardToggle.setChecked(true).setToggleHandler(new Runnable() {
            @Override
            public void run() {
                interactionLastAction = interactionKeyboardToggle.isChecked() ? "已启用键盘快捷交互" : "已关闭键盘快捷交互";
                refreshInteractionState();
            }
        });
        interactionModeSelector.setSelectedIndex(1).setChangeHandler(new Runnable() {
            @Override
            public void run() {
                interactionLastAction = "已切换交互模式到 " + interactionModeSelector.getSelectedOption();
                refreshInteractionState();
            }
        });
        applyInteractionButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                interactionLastAction = "已提交当前交互页状态";
                refreshInteractionState();
            }
        });
        resetInteractionButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                interactionNameInput.setText("Focus Demo");
                interactionPathInput.setText("ui/focus/demo");
                interactionKeyboardToggle.setChecked(true);
                interactionModeSelector.setSelectedIndex(1);
                interactionLastAction = "已重置交互页状态";
                refreshInteractionState();
            }
        });
        backFromInteractionButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(menuPage);
            }
        });
    }

    private void assembleMenuPage(Widget root) {
        DivWidget pageRoot = new DivWidget().setPageColumn();
        ResponsivePanelWidget roadmapPanel = createCardPanel();
        DivWidget roadmapDiv = new DivWidget().setSectionColumn();
        DivWidget actionFlow = new DivWidget().setButtonFlow();

        actionFlow.addNoGrowChild(openLayoutGuideButton);
        actionFlow.addNoGrowChild(openFormDemoButton);
        actionFlow.addNoGrowChild(openInteractionButton);

        roadmapDiv.addNoGrowChild(createSectionTitle("当前测试集"));
        roadmapDiv.addNoGrowChild(createBodyLabel("旧的输入、鼠标、字符摆放和响应式试验页已经清空。当前只保留基于 Div 语义 API 重写的三类页面：布局概览、表单页、交互页。"));
        roadmapDiv.addNoGrowChild(createAccentLabel("目标是让页面代码更像写结构，而不是反复手写底层布局配置。"));
        roadmapPanel.addChild(roadmapDiv);

        pageRoot.addNoGrowChild(createTitleLabel("Qz-UILib Div Playground"));
        pageRoot.addNoGrowChild(createBodyLabel("这一轮先推进 Div 组件本身，再用新的语义 API 重写测试页。当前菜单本身也只由页面列、卡片流和按钮流组成。"));
        pageRoot.addNoGrowChild(menuMetricsLabel);
        pageRoot.addNoGrowChild(roadmapPanel);
        pageRoot.addNoGrowChild(actionFlow);
        pageRoot.addNoGrowChild(createBodyLabel("按 RShift 可随时关闭测试界面；背包入口页也已改为同一套 Div 结构。"));

        menuPage.getContent().addChild(pageRoot);
        root.addChild(menuPage);
    }

    private void assembleLayoutGuidePage(Widget root) {
        DivWidget pageRoot = new DivWidget().setPageColumn();
        DivWidget cardFlow = new DivWidget().setContentFlow();
        ButtonWidget backButton = new ButtonWidget("返回总览");
        backButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(menuPage);
            }
        });

        cardFlow.addFlexChild(createPageColumnCard());
        cardFlow.addFlexChild(createFormRowCard());
        cardFlow.addFlexChild(createInlineFlowCard());

        pageRoot.addNoGrowChild(createTitleLabel("Div 语义布局概览"));
        pageRoot.addNoGrowChild(createBodyLabel("这一页集中验证这轮补上的高阶 API：`setPageColumn()`、`setSectionColumn()`、`setContentFlow()`、`setInlineFlow()`、`setFormRow()` 和 `setButtonFlow()`。高层语义只强调文档流、换行和纵向阅读，不再强调横向兜底滚动。"));
        pageRoot.addNoGrowChild(cardFlow);
        pageRoot.addNoGrowChild(layoutSummaryLabel);

        DivWidget footer = new DivWidget().setButtonFlow();
        footer.addNoGrowChild(backButton);
        pageRoot.addNoGrowChild(footer);

        layoutGuidePage.getContent().addChild(pageRoot);
        root.addChild(layoutGuidePage);
    }

    private void assembleFormDemoPage(Widget root) {
        DivWidget pageRoot = new DivWidget().setPageColumn();
        DivWidget cardFlow = new DivWidget().setContentFlow();
        ResponsivePanelWidget formCard = createCardPanel();
        ResponsivePanelWidget summaryCard = createCardPanel();
        DivWidget formCardDiv = new DivWidget().setSectionColumn();
        DivWidget summaryCardDiv = new DivWidget().setSectionColumn();
        DivWidget actions = new DivWidget().setButtonFlow();

        formCard.setSuggestedSize(420, -1);
        summaryCard.setSuggestedSize(320, -1);

        formCardDiv.addNoGrowChild(createSectionTitle("表单结构"));
        formCardDiv.addNoGrowChild(createBodyLabel("所有行都直接写成 `new DivWidget().setFormRow()`，标签列只标记为不增长，输入列保留柔性即可。页面不再维护一大串手写布局配置。"));
        formCardDiv.addNoGrowChild(createFormRow("主题名称", formThemeInput));
        formCardDiv.addNoGrowChild(createFormRow("命名空间", formNamespaceInput));
        formCardDiv.addNoGrowChild(createFormRow("资源路径", formAssetPathInput));
        formCardDiv.addNoGrowChild(createFormRow("过渡动画", formAnimationToggle));
        formCardDiv.addNoGrowChild(createFormRow("布局密度", formDensitySelector));
        formCard.addChild(formCardDiv);

        summaryCardDiv.addNoGrowChild(createSectionTitle("实时摘要"));
        summaryCardDiv.addNoGrowChild(createBodyLabel("右侧区块不再承担旧测试页那种复杂预览任务，只保留配置摘要与最近操作，让页面层次更干净。"));
        summaryCardDiv.addNoGrowChild(formSummaryLabel);
        summaryCardDiv.addNoGrowChild(formActionLabel);
        summaryCard.addChild(summaryCardDiv);

        cardFlow.addFlexChild(formCard);
        cardFlow.addFlexChild(summaryCard);

        actions.addNoGrowChild(applyFormButton);
        actions.addNoGrowChild(resetFormButton);
        actions.addNoGrowChild(backFromFormButton);

        pageRoot.addNoGrowChild(createTitleLabel("Div 表单页"));
        pageRoot.addNoGrowChild(createBodyLabel("旧设置测试页已经清空，改成一张更克制的真实表单页，只验证结构、响应式换行和控件交互。"));
        pageRoot.addNoGrowChild(cardFlow);
        pageRoot.addNoGrowChild(actions);

        formDemoPage.getContent().addChild(pageRoot);
        root.addChild(formDemoPage);
    }

    private void assembleInteractionPage(Widget root) {
        DivWidget pageRoot = new DivWidget().setPageColumn();
        DivWidget cardFlow = new DivWidget().setContentFlow();
        ResponsivePanelWidget focusCard = createCardPanel();
        ResponsivePanelWidget stateCard = createCardPanel();
        DivWidget focusCardDiv = new DivWidget().setSectionColumn();
        DivWidget stateCardDiv = new DivWidget().setSectionColumn();
        DivWidget actions = new DivWidget().setButtonFlow();

        focusCard.setSuggestedSize(420, -1);
        stateCard.setSuggestedSize(300, -1);

        focusCardDiv.addNoGrowChild(createSectionTitle("焦点与输入链路"));
        focusCardDiv.addNoGrowChild(createBodyLabel("用 Tab / Shift+Tab 在以下控件间切换焦点，用 Enter 或 Space 激活控件，左右方向键切换分段选择。"));
        focusCardDiv.addNoGrowChild(createFormRow("配置名称", interactionNameInput));
        focusCardDiv.addNoGrowChild(createFormRow("资源标识", interactionPathInput));
        focusCardDiv.addNoGrowChild(createFormRow("键盘快捷", interactionKeyboardToggle));
        focusCardDiv.addNoGrowChild(createFormRow("工作模式", interactionModeSelector));
        focusCard.addChild(focusCardDiv);

        stateCardDiv.addNoGrowChild(createSectionTitle("状态回显"));
        stateCardDiv.addNoGrowChild(createAccentLabel("这页同时验证文本输入、焦点导航、切换控件和按钮流是否能在同一张 Div 页面里自然协作。"));
        stateCardDiv.addNoGrowChild(interactionSummaryLabel);
        stateCard.addChild(stateCardDiv);

        cardFlow.addFlexChild(focusCard);
        cardFlow.addFlexChild(stateCard);

        actions.addNoGrowChild(applyInteractionButton);
        actions.addNoGrowChild(resetInteractionButton);
        actions.addNoGrowChild(backFromInteractionButton);

        pageRoot.addNoGrowChild(createTitleLabel("Div 交互页"));
        pageRoot.addNoGrowChild(createBodyLabel("旧焦点导航测试页已经并入这张交互页，保留真正有价值的交互验证：焦点顺序、键盘激活、文本输入和状态回显。"));
        pageRoot.addNoGrowChild(cardFlow);
        pageRoot.addNoGrowChild(actions);

        interactionPage.getContent().addChild(pageRoot);
        root.addChild(interactionPage);
    }

    private ResponsivePanelWidget createPageColumnCard() {
        ResponsivePanelWidget panel = createCardPanel();
        DivWidget content = new DivWidget().setSectionColumn();
        DivWidget actionFlow = new DivWidget().setButtonFlow();
        ButtonWidget primaryButton = new ButtonWidget("主操作");
        ButtonWidget secondaryButton = new ButtonWidget("次操作");
        ButtonWidget tertiaryButton = new ButtonWidget("辅助操作");

        panel.setSuggestedSize(280, -1);

        primaryButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                layoutLastAction = "点击了页面列中的主操作按钮";
                refreshLayoutSummary();
            }
        });
        secondaryButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                layoutLastAction = "点击了页面列中的次操作按钮";
                refreshLayoutSummary();
            }
        });
        tertiaryButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                layoutLastAction = "点击了页面列中的辅助操作按钮";
                refreshLayoutSummary();
            }
        });

        actionFlow.addNoGrowChild(primaryButton);
        actionFlow.addNoGrowChild(secondaryButton);
        actionFlow.addNoGrowChild(tertiaryButton);

        content.addNoGrowChild(createSectionTitle("1. 页面列"));
        content.addNoGrowChild(createBodyLabel("`setPageColumn()` 把标题、正文、区块和页脚统一压成纵向流，页面代码只保留结构顺序。"));
        content.addNoGrowChild(actionFlow);
        panel.addChild(content);
        return panel;
    }

    private ResponsivePanelWidget createFormRowCard() {
        ResponsivePanelWidget panel = createCardPanel();
        DivWidget content = new DivWidget().setSectionColumn();
        TextInputWidget sampleNameInput = new TextInputWidget().setPlaceholder("输入条目名称").setText("Div Showcase");
        SegmentedSelectorWidget sampleModeSelector = new SegmentedSelectorWidget("A", "B", "C").setSelectedIndex(1);
        ToggleSwitchWidget sampleToggle = new ToggleSwitchWidget("启用").setChecked(true);

        panel.setSuggestedSize(320, -1);
        sampleNameInput.setSuggestedSize(240, sampleNameInput.getPreferredHeight());
        sampleModeSelector.setSuggestedSize(240, sampleModeSelector.getPreferredHeight());
        sampleToggle.setSuggestedSize(240, sampleToggle.getPreferredHeight());

        content.addNoGrowChild(createSectionTitle("2. 表单行"));
        content.addNoGrowChild(createBodyLabel("`setFormRow()` 负责标签与控件的横向组合，并在空间不足时自动换行，不再需要为每一行重复写方向、换行和对齐策略。"));
        content.addNoGrowChild(createFormRow("条目名称", sampleNameInput));
        content.addNoGrowChild(createFormRow("显示模式", sampleModeSelector));
        content.addNoGrowChild(createFormRow("显示动画", sampleToggle));
        panel.addChild(content);
        return panel;
    }

    private ResponsivePanelWidget createInlineFlowCard() {
        ResponsivePanelWidget panel = createCardPanel();
        DivWidget content = new DivWidget().setSectionColumn();
        DivWidget chipRow = new DivWidget().setInlineFlow().setPadding(0, 0, 0, 10);
        String[] chipTexts = new String[] {
                "Native Scale",
                "Responsive Page",
                "Semantic Form Row",
                "Button Flow",
                "Inline Wrap",
                "Inventory Rebuild",
                "Focus Navigation",
                "Layout Cleanup"
        };

        panel.setSuggestedSize(360, -1);
        for (String chipText : chipTexts) {
            ButtonWidget chipButton = new ButtonWidget(chipText);
            chipButton.setClickHandler(new Runnable() {
                @Override
                public void run() {
                    layoutLastAction = "点击了行内换行流中的标签：" + chipText;
                    refreshLayoutSummary();
                }
            });
            chipRow.addNoGrowChild(chipButton);
        }

        content.addNoGrowChild(createSectionTitle("3. 行内流与自然换行"));
        content.addNoGrowChild(createBodyLabel("更像网页的默认体验应该是内容优先换行、向下延展，而不是把横向滚动当成页面级概念。这里的标签流会像网页里的 inline-block 一样自然换行。"));
        content.addNoGrowChild(chipRow);
        panel.addChild(content);
        return panel;
    }

    private DivWidget createFormRow(String labelText, Widget field) {
        LabelWidget label = createFormLabel(labelText);
        DivWidget row = new DivWidget().setFormRow();
        row.addNoGrowChild(label);
        row.addFlexChild(field);
        return row;
    }

    private void refreshMenuMetrics() {
        menuMetricsLabel.setText("原生窗口 " + width + "x" + height
                + "；当前保留 3 张基于 Div 语义 API 的测试页；旧测试页与旧背包测试页已清空重写。");
    }

    private void refreshLayoutSummary() {
        layoutSummaryLabel.setText("当前窗口 " + width + "x" + height + "；布局概览页尺寸 "
                + layoutGuidePage.getWidth() + "x" + layoutGuidePage.getHeight() + "；最近操作：" + layoutLastAction);
    }

    private void refreshFormState() {
        formAnimationToggle.setChecked(formAnimationsEnabled);
        formDensitySelector.setSelectedIndex(formDensityIndex);

        String theme = textOrPlaceholder(formThemeInput.getText());
        String namespace = textOrPlaceholder(formNamespaceInput.getText());
        String path = textOrPlaceholder(formAssetPathInput.getText());
        formSummaryLabel.setText("主题：" + theme + "；命名空间：" + namespace + "；资源路径：" + path
                + "。动画当前为" + (formAnimationsEnabled ? "启用" : "关闭")
                + "，布局密度为" + formDensitySelector.getSelectedOption() + "。\n这张页面只保留表单本身与摘要区块，用来验证新的 Div 页面结构是否足够简洁。 ");
        formActionLabel.setText("最近操作：" + formLastAction);
    }

    private void refreshInteractionState() {
        String name = textOrPlaceholder(interactionNameInput.getText());
        String path = textOrPlaceholder(interactionPathInput.getText());
        interactionSummaryLabel.setText("名称：" + name + "；资源：" + path + "；键盘快捷："
                + (interactionKeyboardToggle.isChecked() ? "允许" : "关闭") + "；模式："
                + interactionModeSelector.getSelectedOption() + "；最近操作：" + interactionLastAction);
    }

    private void showPage(Widget page) {
        clearInteractionState();
        currentPage = page;
        menuPage.setVisible(page == menuPage);
        layoutGuidePage.setVisible(page == layoutGuidePage);
        formDemoPage.setVisible(page == formDemoPage);
        interactionPage.setVisible(page == interactionPage);
    }

    private void configurePage(ResponsivePageWidget page, float widthPercent, float heightPercent, int suggestedWidth, int suggestedHeight) {
        page.setPadding(30, 28, 30, 28)
                .setSuggestedSize(suggestedWidth, suggestedHeight)
                .setViewportRatio(widthPercent, heightPercent)
                .setLayoutSpec(new UiLayoutSpec().setAnchor(UiAnchor.TOP_CENTER));
    }

    private void applyAdaptiveChrome() {
        int pagePaddingX = clampValue(viewportWidthHint / 48, 14, 30);
        int pagePaddingY = clampValue(viewportHeightHint / 36, 12, 28);

        menuPage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        layoutGuidePage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        formDemoPage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        interactionPage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
    }

    private void applyAdaptiveSizes() {
        int menuButtonWidth = adaptiveWidth(260, 140, 0.18F);
        int inputWidth = adaptiveWidth(320, 170, 0.22F);
        int buttonWidth = adaptiveWidth(180, 120, 0.14F);
        int selectorWidth = adaptiveWidth(320, 180, 0.22F);

        openLayoutGuideButton.setSuggestedSize(menuButtonWidth, openLayoutGuideButton.getPreferredHeight());
        openFormDemoButton.setSuggestedSize(menuButtonWidth, openFormDemoButton.getPreferredHeight());
        openInteractionButton.setSuggestedSize(menuButtonWidth, openInteractionButton.getPreferredHeight());

        formThemeInput.setSuggestedSize(inputWidth, formThemeInput.getPreferredHeight());
        formNamespaceInput.setSuggestedSize(inputWidth, formNamespaceInput.getPreferredHeight());
        formAssetPathInput.setSuggestedSize(inputWidth, formAssetPathInput.getPreferredHeight());
        formAnimationToggle.setSuggestedSize(inputWidth, formAnimationToggle.getPreferredHeight());
        formDensitySelector.setSuggestedSize(selectorWidth, formDensitySelector.getPreferredHeight());
        applyFormButton.setSuggestedSize(buttonWidth, applyFormButton.getPreferredHeight());
        resetFormButton.setSuggestedSize(buttonWidth, resetFormButton.getPreferredHeight());
        backFromFormButton.setSuggestedSize(buttonWidth, backFromFormButton.getPreferredHeight());

        interactionNameInput.setSuggestedSize(inputWidth, interactionNameInput.getPreferredHeight());
        interactionPathInput.setSuggestedSize(inputWidth, interactionPathInput.getPreferredHeight());
        interactionKeyboardToggle.setSuggestedSize(inputWidth, interactionKeyboardToggle.getPreferredHeight());
        interactionModeSelector.setSuggestedSize(selectorWidth, interactionModeSelector.getPreferredHeight());
        applyInteractionButton.setSuggestedSize(buttonWidth, applyInteractionButton.getPreferredHeight());
        resetInteractionButton.setSuggestedSize(buttonWidth, resetInteractionButton.getPreferredHeight());
        backFromInteractionButton.setSuggestedSize(buttonWidth, backFromInteractionButton.getPreferredHeight());
    }

    private ResponsivePanelWidget createCardPanel() {
        return new ResponsivePanelWidget().setPadding(18).setFillColor(0xAA111721).setBorderColor(0xFF7AA2FF);
    }

    private LabelWidget createTitleLabel(String text) {
        return new LabelWidget(text).setColor(0xFFFFFFFF).setShadow(false).setWrap(true).setMaxLines(2);
    }

    private LabelWidget createSectionTitle(String text) {
        return new LabelWidget(text).setColor(0xFFFFFFFF).setShadow(false).setWrap(true).setMaxLines(2);
    }

    private LabelWidget createBodyLabel(String text) {
        return new LabelWidget(text).setColor(0xFFD7E3FF).setShadow(false).setWrap(true).setMaxLines(6);
    }

    private LabelWidget createAccentLabel(String text) {
        return new LabelWidget(text).setColor(0xFFF6D78E).setShadow(false).setWrap(true).setMaxLines(4);
    }

    private LabelWidget createFormLabel(String text) {
        LabelWidget label = new LabelWidget(text).setColor(0xFFF6D78E).setShadow(false);
        label.setSuggestedSize(156, -1);
        return label;
    }

    private String textOrPlaceholder(String value) {
        return value == null || value.isEmpty() ? "<未填写>" : value;
    }

    private int adaptiveWidth(int preferred, int floor, float viewportRatio) {
        return clampValue(Math.round(viewportWidthHint * viewportRatio), floor, preferred);
    }

    private int clampValue(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
