package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.CharacterPlacementDebugWidget;
import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.MouseStressWidget;
import club.heiqi.uilib.ui.control.RelativePanelWidget;
import club.heiqi.uilib.ui.control.ResponsiveContainerWidget;
import club.heiqi.uilib.ui.control.ResponsivePageWidget;
import club.heiqi.uilib.ui.control.ResponsivePanelWidget;
import club.heiqi.uilib.ui.control.ResponsiveProbeWidget;
import club.heiqi.uilib.ui.control.SegmentedSelectorWidget;
import club.heiqi.uilib.ui.control.TextInputWidget;
import club.heiqi.uilib.ui.control.ToggleSwitchWidget;
import club.heiqi.uilib.ui.layout.DivItemStyle;
import club.heiqi.uilib.ui.layout.UiAnchor;
import club.heiqi.uilib.ui.layout.UiInsets;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * UI 系统测试界面。
 */
public class UiTestScreen extends BaseScreen {

    private final ResponsivePageWidget menuPage = new ResponsivePageWidget();
    private final ResponsivePageWidget inputTestPage = new ResponsivePageWidget();
    private final ResponsivePageWidget mouseStressPage = new ResponsivePageWidget();
    private final ResponsivePageWidget characterPlacementPage = new ResponsivePageWidget();
    private final ResponsivePageWidget responsiveLayoutPage = new ResponsivePageWidget();
    private final ResponsivePageWidget settingsFormPage = new ResponsivePageWidget();
    private final ResponsivePageWidget focusNavigationPage = new ResponsivePageWidget();

    private final LabelWidget menuTitleLabel = new LabelWidget("Qz-UILib UI Playground");
    private final LabelWidget menuHintLabel = new LabelWidget("一级菜单本身也由响应式布局驱动，按钮组会随着页面宽度变化自动缩放");
    private final DivWidget menuDivRoot = new DivWidget().setDirection(DivWidget.Direction.COLUMN)
            .setGap(18)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget menuActionsDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.STRETCH)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final ButtonWidget openInputTestButton = new ButtonWidget("进入文本输入测试");
    private final ButtonWidget openMouseStressButton = new ButtonWidget("进入鼠标极限响应测试");
    private final ButtonWidget openCharacterPlacementButton = new ButtonWidget("进入字符摆放调试");
    private final ButtonWidget openResponsiveLayoutButton = new ButtonWidget("进入响应式布局测试");
    private final ButtonWidget openSettingsFormButton = new ButtonWidget("进入设置表单测试");
    private final ButtonWidget openFocusNavigationButton = new ButtonWidget("进入焦点导航测试");

    private final LabelWidget inputTitleLabel = new LabelWidget("文本输入专项测试");
    private final LabelWidget stateLabel = new LabelWidget("等待点击按钮验证输入链路");
    private final LabelWidget inputHintLabel = new LabelWidget("输入测试：点击输入框后直接键入，支持 Unicode 与退格");
    private final DivWidget inputDivRoot = new DivWidget().setDirection(DivWidget.Direction.COLUMN)
            .setGap(16)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget inputActionDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.STRETCH)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final ButtonWidget testButton = new ButtonWidget("点我切换状态");
    private final TextInputWidget textInputWidget = new TextInputWidget();
    private final LabelWidget inputEchoLabel = new LabelWidget("当前输入：");
    private final ButtonWidget backFromInputButton = new ButtonWidget("返回主菜单");

    private final LabelWidget mouseTitleLabel = new LabelWidget("鼠标极限响应专项测试");
    private final LabelWidget mouseHintLabel = new LabelWidget("快速甩动、连点、滚轮滚动，观察是否保持高频刷新");
    private final DivWidget mouseDivRoot = new DivWidget().setDirection(DivWidget.Direction.COLUMN)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget mouseActionDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.STRETCH)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final MouseStressWidget mouseStressWidget = new MouseStressWidget();
    private final ButtonWidget backFromMouseButton = new ButtonWidget("返回主菜单");

    private final LabelWidget characterTitleLabel = new LabelWidget("字符摆放调试页");
    private final LabelWidget characterHintLabel = new LabelWidget("观察文本在容器四角、边缘、中心的落位，并验证相对布局容器自动防止出框");
    private final DivWidget characterDivRoot = new DivWidget().setDirection(DivWidget.Direction.COLUMN)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget characterActionDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.STRETCH)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final RelativePanelWidget characterPreviewPanel = new RelativePanelWidget();
    private final CharacterPlacementDebugWidget characterPlacementDebugWidget = new CharacterPlacementDebugWidget();
    private final LabelWidget characterClampHintLabel = new LabelWidget("如果子元素给了越界坐标，容器会在绘制前自动 clamp 回边界内");
    private final ButtonWidget backFromCharacterButton = new ButtonWidget("返回主菜单");

    private final LabelWidget responsiveTitleLabel = new LabelWidget("响应式布局专项测试页");
    private final LabelWidget responsiveHintLabel = new LabelWidget("观察嵌套面板、百分比宽高、自动尺寸控件和锚点布局在不同窗口大小下的变化");
    private final LabelWidget responsiveMetricsLabel = new LabelWidget("当前尺寸信息：");
    private final DivWidget responsiveDivRoot = new DivWidget().setDirection(DivWidget.Direction.COLUMN)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget responsiveTopDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.STRETCH)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget responsiveBottomDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.STRETCH)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget responsiveActionDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.STRETCH)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final ResponsiveProbeWidget responsiveLeftProbe = new ResponsiveProbeWidget("左侧卡片");
    private final ResponsiveProbeWidget responsiveRightProbe = new ResponsiveProbeWidget("右侧卡片");
    private final ResponsiveProbeWidget responsiveBottomLeftProbe = new ResponsiveProbeWidget("底部左卡片");
    private final ResponsiveProbeWidget responsiveBottomRightProbe = new ResponsiveProbeWidget("底部右卡片");
    private final ResponsivePanelWidget responsiveArenaPanel = new ResponsivePanelWidget();
    private final ResponsiveProbeWidget arenaTopLeftProbe = new ResponsiveProbeWidget("左上锚点");
    private final ResponsiveProbeWidget arenaCenterProbe = new ResponsiveProbeWidget("中心锚点");
    private final ResponsiveProbeWidget arenaBottomRightProbe = new ResponsiveProbeWidget("右下锚点");
    private final LabelWidget responsiveFooterLabel = new LabelWidget("通过拖动窗口或切换分辨率，观察容器与子项是否保持稳定关系");
    private final ButtonWidget backFromResponsiveButton = new ButtonWidget("返回主菜单");

    private final LabelWidget settingsTitleLabel = new LabelWidget("真实设置表单页");
    private final LabelWidget settingsIntroLabel = new LabelWidget("这个页面用真实表单结构验证复杂响应式布局：固定标签列、可增长输入列、按钮行、说明文本换行，以及 grow 面板吞掉剩余高度。拖动窗口后，行布局和说明区都应保持稳定。 ");
    private final LabelWidget settingsProfileLabel = new LabelWidget("主题名称");
    private final LabelWidget settingsAuthorLabel = new LabelWidget("作者标识");
    private final LabelWidget settingsPathLabel = new LabelWidget("资源路径");
    private final LabelWidget settingsAnimationLabel = new LabelWidget("动画选项");
    private final LabelWidget settingsDensityLabel = new LabelWidget("布局密度");
    private final TextInputWidget settingsProfileInput = new TextInputWidget();
    private final TextInputWidget settingsAuthorInput = new TextInputWidget();
    private final TextInputWidget settingsPathInput = new TextInputWidget();
    private final ToggleSwitchWidget settingsAnimationToggle = new ToggleSwitchWidget("动画开关");
    private final SegmentedSelectorWidget settingsDensitySelector = new SegmentedSelectorWidget("紧凑布局", "舒适布局");
    private final DivWidget settingsDivRoot = new DivWidget().setDirection(DivWidget.Direction.COLUMN).setGap(14).setWidthPercent(1.0F);
    private final DivWidget settingsProfileDiv = new DivWidget().setDirection(DivWidget.Direction.ROW).setWrap(DivWidget.Wrap.WRAP).setAlignItems(DivWidget.AlignItems.CENTER).setGap(16).setWidthPercent(1.0F);
    private final DivWidget settingsAuthorDiv = new DivWidget().setDirection(DivWidget.Direction.ROW).setWrap(DivWidget.Wrap.WRAP).setAlignItems(DivWidget.AlignItems.CENTER).setGap(16).setWidthPercent(1.0F);
    private final DivWidget settingsPathDiv = new DivWidget().setDirection(DivWidget.Direction.ROW).setWrap(DivWidget.Wrap.WRAP).setAlignItems(DivWidget.AlignItems.CENTER).setGap(16).setWidthPercent(1.0F);
    private final DivWidget settingsAnimationDiv = new DivWidget().setDirection(DivWidget.Direction.ROW).setWrap(DivWidget.Wrap.WRAP).setAlignItems(DivWidget.AlignItems.CENTER).setGap(16).setWidthPercent(1.0F);
    private final DivWidget settingsDensityDiv = new DivWidget().setDirection(DivWidget.Direction.ROW).setWrap(DivWidget.Wrap.WRAP).setAlignItems(DivWidget.AlignItems.CENTER).setGap(16).setWidthPercent(1.0F);
    private final DivWidget settingsPreviewDiv = new DivWidget().setDirection(DivWidget.Direction.COLUMN).setGap(12).setWidthPercent(1.0F);
    private final DivWidget settingsFooterDiv = new DivWidget().setDirection(DivWidget.Direction.ROW).setAlignItems(DivWidget.AlignItems.STRETCH).setWrap(DivWidget.Wrap.WRAP).setGap(14).setWidthPercent(1.0F);
    private final ResponsivePanelWidget settingsPreviewPanel = new ResponsivePanelWidget();
    private final LabelWidget settingsPreviewTitleLabel = new LabelWidget("预览与摘要");
    private final LabelWidget settingsPreviewSummaryLabel = new LabelWidget("");
    private final LabelWidget settingsFooterHintLabel = new LabelWidget("最近操作：尚未应用");
    private final ButtonWidget settingsApplyButton = new ButtonWidget("应用");
    private final ButtonWidget settingsResetButton = new ButtonWidget("重置");
    private final ButtonWidget backFromSettingsButton = new ButtonWidget("返回主菜单");

    private final LabelWidget focusTitleLabel = new LabelWidget("键盘焦点导航测试页");
    private final LabelWidget focusHintLabel = new LabelWidget("使用 Tab 或 Shift+Tab 在输入框、开关、选择器与按钮间切换焦点；用 Enter/Space 激活当前控件，左右方向键切换分段选择。 ");
    private final LabelWidget focusNameLabel = new LabelWidget("配置名称");
    private final LabelWidget focusPathLabel = new LabelWidget("资源标识");
    private final TextInputWidget focusNameInput = new TextInputWidget();
    private final TextInputWidget focusPathInput = new TextInputWidget();
    private final ToggleSwitchWidget focusToggle = new ToggleSwitchWidget("键盘交互开关");
    private final SegmentedSelectorWidget focusSelector = new SegmentedSelectorWidget("布局A", "布局B", "布局C");
    private final DivWidget focusDivRoot = new DivWidget().setDirection(DivWidget.Direction.COLUMN)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget focusProfileDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.CENTER)
            .setGap(16)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget focusPathDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.CENTER)
            .setGap(16)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget focusModeDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.CENTER)
            .setGap(16)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final DivWidget focusActionDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.STRETCH)
            .setGap(14)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final LabelWidget focusStatusLabel = new LabelWidget("");
    private final ButtonWidget focusPrimaryButton = new ButtonWidget("应用焦点页设置");
    private final ButtonWidget focusSecondaryButton = new ButtonWidget("重置焦点页");
    private final ButtonWidget backFromFocusButton = new ButtonWidget("返回主菜单");

    private boolean toggled;
    private boolean settingsAnimationsEnabled = true;
    private boolean settingsCompactDensity;
    private String settingsLastAction = "尚未应用";
    private String focusLastAction = "尚未操作";
    private Widget currentPage;
    private int viewportWidthHint = 1280;
    private int viewportHeightHint = 720;

    @Override
    protected void buildUi(Widget root) {
        configurePage(menuPage, 0.58F, 0.52F, 420, 340);
        configurePage(inputTestPage, 0.60F, 0.62F, 480, 400);
        configurePage(mouseStressPage, 0.62F, 0.72F, 520, 500);
        configurePage(characterPlacementPage, 0.72F, 0.80F, 620, 520);
        configurePage(responsiveLayoutPage, 0.76F, 0.84F, 720, 560);
        configurePage(settingsFormPage, 0.78F, 0.86F, 760, 620);
        configurePage(focusNavigationPage, 0.72F, 0.78F, 680, 500);

        configureLabels();
        configureButtons();
        configurePanels();
        configureLayoutSpecs();
        assemblePages(root);

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
        configureLayoutSpecs();

        refreshStateText();
        refreshEchoText();
        refreshResponsiveMetrics();
        refreshSettingsState();
        refreshFocusState();
        showPage(currentPage == null ? menuPage : currentPage);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refreshEchoText();
        refreshResponsiveMetrics();
        refreshSettingsState();
        refreshFocusState();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void configureLabels() {
        menuTitleLabel.setShadow(true).setColor(0xFFFFFFFF);
        menuHintLabel.setShadow(true).setColor(0xFFD7E3FF).setWrap(true).setMaxLines(2);

        inputTitleLabel.setShadow(true).setColor(0xFFFFFFFF);
        stateLabel.setShadow(true).setColor(0xFFD7E3FF);
        inputHintLabel.setShadow(true).setColor(0xFFC8D8F3).setWrap(true).setMaxLines(2);
        inputEchoLabel.setShadow(true).setColor(0xFFF6D78E);

        mouseTitleLabel.setShadow(true).setColor(0xFFFFFFFF);
        mouseHintLabel.setShadow(true).setColor(0xFFD7E3FF).setWrap(true).setMaxLines(2);

        characterTitleLabel.setShadow(true).setColor(0xFFFFFFFF);
        characterHintLabel.setShadow(true).setColor(0xFFD7E3FF).setWrap(true).setMaxLines(2);
        characterClampHintLabel.setShadow(true).setColor(0xFFF6D78E).setWrap(true).setMaxLines(2);

        responsiveTitleLabel.setShadow(true).setColor(0xFFFFFFFF);
        responsiveHintLabel.setShadow(true).setColor(0xFFD7E3FF).setWrap(true).setMaxLines(3);
        responsiveMetricsLabel.setShadow(true).setColor(0xFFF6D78E).setWrap(true).setMaxLines(2);
        responsiveFooterLabel.setShadow(true).setColor(0xFFC8D8F3).setWrap(true).setMaxLines(2);

        settingsTitleLabel.setShadow(true).setColor(0xFFFFFFFF);
        settingsIntroLabel.setShadow(true).setColor(0xFFD7E3FF).setWrap(true).setMaxLines(4);
        settingsProfileLabel.setShadow(true).setColor(0xFFF6D78E);
        settingsAuthorLabel.setShadow(true).setColor(0xFFF6D78E);
        settingsPathLabel.setShadow(true).setColor(0xFFF6D78E);
        settingsAnimationLabel.setShadow(true).setColor(0xFFF6D78E);
        settingsDensityLabel.setShadow(true).setColor(0xFFF6D78E);
        settingsPreviewTitleLabel.setShadow(true).setColor(0xFFFFFFFF);
        settingsPreviewSummaryLabel.setShadow(true).setColor(0xFFC8D8F3).setWrap(true).setMaxLines(8);
        settingsFooterHintLabel.setShadow(true).setColor(0xFFB5D0FF).setWrap(true).setMaxLines(2);

        focusTitleLabel.setShadow(true).setColor(0xFFFFFFFF);
        focusHintLabel.setShadow(true).setColor(0xFFD7E3FF).setWrap(true).setMaxLines(3);
        focusNameLabel.setShadow(true).setColor(0xFFF6D78E);
        focusPathLabel.setShadow(true).setColor(0xFFF6D78E);
        focusStatusLabel.setShadow(true).setColor(0xFFB5D0FF).setWrap(true).setMaxLines(3);
    }

    private void configureButtons() {
        openInputTestButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(inputTestPage);
            }
        });
        openMouseStressButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(mouseStressPage);
            }
        });
        openCharacterPlacementButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(characterPlacementPage);
            }
        });
        openResponsiveLayoutButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(responsiveLayoutPage);
            }
        });
        openSettingsFormButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(settingsFormPage);
            }
        });
        openFocusNavigationButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(focusNavigationPage);
            }
        });

        textInputWidget.setPlaceholder("例如：你好，lwjgl3ify 输入链路").setMaxLength(64);
        testButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                toggled = !toggled;
                refreshStateText();
            }
        });

        settingsProfileInput.setPlaceholder("输入主题名称").setText("Qz Native UI");
        settingsAuthorInput.setPlaceholder("输入作者标识").setText("Heiqi");
        settingsPathInput.setPlaceholder("输入资源根路径").setText("assets/qz_uilib/ui");
        settingsAnimationToggle.setToggleHandler(new Runnable() {
            @Override
            public void run() {
                settingsAnimationsEnabled = settingsAnimationToggle.isChecked();
                settingsLastAction = settingsAnimationsEnabled ? "已启用界面动画" : "已关闭界面动画";
                refreshSettingsState();
            }
        });
        settingsDensitySelector.setChangeHandler(new Runnable() {
            @Override
            public void run() {
                settingsCompactDensity = settingsDensitySelector.getSelectedIndex() == 0;
                settingsLastAction = settingsCompactDensity ? "已切换为紧凑布局" : "已切换为舒适布局";
                refreshSettingsState();
            }
        });
        settingsApplyButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                settingsLastAction = "已应用当前表单设置";
                refreshSettingsState();
            }
        });
        settingsResetButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                settingsAnimationsEnabled = true;
                settingsCompactDensity = false;
                settingsProfileInput.setText("Qz Native UI");
                settingsAuthorInput.setText("Heiqi");
                settingsPathInput.setText("assets/qz_uilib/ui");
                settingsLastAction = "已恢复默认表单设置";
                refreshSettingsState();
            }
        });

        focusNameInput.setPlaceholder("输入焦点测试名称").setText("Focus Demo");
        focusPathInput.setPlaceholder("输入焦点测试资源标识").setText("ui/focus/test");
        focusToggle.setChecked(true).setToggleHandler(new Runnable() {
            @Override
            public void run() {
                focusLastAction = focusToggle.isChecked() ? "已启用键盘交互开关" : "已关闭键盘交互开关";
                refreshFocusState();
            }
        });
        focusSelector.setSelectedIndex(1).setChangeHandler(new Runnable() {
            @Override
            public void run() {
                focusLastAction = "已切换布局模式到 " + focusSelector.getSelectedOption();
                refreshFocusState();
            }
        });
        focusPrimaryButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                focusLastAction = "已应用焦点页设置";
                refreshFocusState();
            }
        });
        focusSecondaryButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                focusNameInput.setText("Focus Demo");
                focusPathInput.setText("ui/focus/test");
                focusToggle.setChecked(true);
                focusSelector.setSelectedIndex(1);
                focusLastAction = "已重置焦点页状态";
                refreshFocusState();
            }
        });

        backFromInputButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(menuPage);
            }
        });
        backFromMouseButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(menuPage);
            }
        });
        backFromCharacterButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(menuPage);
            }
        });
        backFromResponsiveButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(menuPage);
            }
        });
        backFromSettingsButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(menuPage);
            }
        });
        backFromFocusButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                showPage(menuPage);
            }
        });
    }

    private void configurePanels() {
        characterPreviewPanel.setPadding(16).setClipChildren(true);
        responsiveArenaPanel.setPadding(18).setFillColor(0xAA121A24).setBorderColor(0xFF8FB3FF);
        responsiveLeftProbe.setFillColor(0xAA202C3B).setBorderColor(0xFF7AA2FF);
        responsiveRightProbe.setFillColor(0xAA273126).setBorderColor(0xFF8ED28E);
        responsiveBottomLeftProbe.setFillColor(0xAA34261F).setBorderColor(0xFFFFB36B);
        responsiveBottomRightProbe.setFillColor(0xAA2B2038).setBorderColor(0xFFC497FF);
        arenaTopLeftProbe.setFillColor(0xAA253446).setBorderColor(0xFF8EC9FF);
        arenaCenterProbe.setFillColor(0xAA2B3A2A).setBorderColor(0xFF91D891);
        arenaBottomRightProbe.setFillColor(0xAA3B2A2A).setBorderColor(0xFFFF9F9F);
        settingsPreviewPanel.setPadding(20).setFillColor(0xAA111721).setBorderColor(0xFF7AA2FF);
        settingsPreviewPanel.setClipChildren(true);
        responsiveArenaPanel.setClipChildren(true);
    }

    private void configureLayoutSpecs() {
        applyMenuLayout();
        applyInputLayout();
        applyMouseLayout();
        applyCharacterLayout();
        applyResponsiveLayoutPage();
        applySettingsLayout();
        applyFocusLayout();
    }

    private void assemblePages(Widget root) {
        menuActionsDiv.addChild(openInputTestButton);
        menuActionsDiv.addChild(openMouseStressButton);
        menuActionsDiv.addChild(openCharacterPlacementButton);
        menuActionsDiv.addChild(openResponsiveLayoutButton);
        menuActionsDiv.addChild(openSettingsFormButton);
        menuActionsDiv.addChild(openFocusNavigationButton);
        menuDivRoot.addChild(menuTitleLabel, DivItemStyle.noGrow());
        menuDivRoot.addChild(menuHintLabel, DivItemStyle.noGrow());
        menuDivRoot.addChild(menuActionsDiv, DivItemStyle.noGrow());
        menuPage.getContent().addChild(menuDivRoot);

        inputActionDiv.addChild(testButton, DivItemStyle.noGrow());
        inputActionDiv.addChild(backFromInputButton, DivItemStyle.noGrow());
        inputDivRoot.addChild(inputTitleLabel, DivItemStyle.noGrow());
        inputDivRoot.addChild(stateLabel, DivItemStyle.noGrow());
        inputDivRoot.addChild(inputHintLabel, DivItemStyle.noGrow());
        inputDivRoot.addChild(textInputWidget, DivItemStyle.noGrow());
        inputDivRoot.addChild(inputEchoLabel, DivItemStyle.noGrow());
        inputDivRoot.addChild(inputActionDiv, DivItemStyle.noGrow());
        inputTestPage.getContent().addChild(inputDivRoot);

        mouseActionDiv.addChild(backFromMouseButton, DivItemStyle.noGrow());
        mouseDivRoot.addChild(mouseTitleLabel, DivItemStyle.noGrow());
        mouseDivRoot.addChild(mouseHintLabel, DivItemStyle.noGrow());
        mouseDivRoot.addChild(mouseStressWidget, DivItemStyle.noGrow());
        mouseDivRoot.addChild(mouseActionDiv, DivItemStyle.noGrow());
        mouseStressPage.getContent().addChild(mouseDivRoot);

        characterPreviewPanel.addChild(characterPlacementDebugWidget);
        characterActionDiv.addChild(backFromCharacterButton, DivItemStyle.noGrow());
        characterDivRoot.addChild(characterTitleLabel, DivItemStyle.noGrow());
        characterDivRoot.addChild(characterHintLabel, DivItemStyle.noGrow());
        characterDivRoot.addChild(characterPreviewPanel, DivItemStyle.noGrow());
        characterDivRoot.addChild(characterClampHintLabel, DivItemStyle.noGrow());
        characterDivRoot.addChild(characterActionDiv, DivItemStyle.noGrow());
        characterPlacementPage.getContent().addChild(characterDivRoot);

        responsiveArenaPanel.addChild(arenaTopLeftProbe);
        responsiveArenaPanel.addChild(arenaCenterProbe);
        responsiveArenaPanel.addChild(arenaBottomRightProbe);
        responsiveTopDiv.addChild(responsiveLeftProbe);
        responsiveTopDiv.addChild(responsiveRightProbe);
        responsiveBottomDiv.addChild(responsiveBottomLeftProbe);
        responsiveBottomDiv.addChild(responsiveBottomRightProbe);
        responsiveActionDiv.addChild(backFromResponsiveButton, DivItemStyle.noGrow());
        responsiveDivRoot.addChild(responsiveTitleLabel, DivItemStyle.noGrow());
        responsiveDivRoot.addChild(responsiveHintLabel, DivItemStyle.noGrow());
        responsiveDivRoot.addChild(responsiveMetricsLabel, DivItemStyle.noGrow());
        responsiveDivRoot.addChild(responsiveTopDiv, DivItemStyle.noGrow());
        responsiveDivRoot.addChild(responsiveBottomDiv, DivItemStyle.noGrow());
        responsiveDivRoot.addChild(responsiveArenaPanel, DivItemStyle.noGrow());
        responsiveDivRoot.addChild(responsiveFooterLabel, DivItemStyle.noGrow());
        responsiveDivRoot.addChild(responsiveActionDiv, DivItemStyle.noGrow());
        responsiveLayoutPage.getContent().addChild(responsiveDivRoot);

        settingsPreviewDiv.addChild(settingsPreviewTitleLabel, DivItemStyle.noGrow());
        settingsPreviewDiv.addChild(settingsPreviewSummaryLabel, DivItemStyle.noGrow());
        settingsPreviewPanel.addChild(settingsPreviewDiv);

        settingsProfileDiv.addChild(settingsProfileLabel, DivItemStyle.fixed());
        settingsProfileDiv.addChild(settingsProfileInput);
        settingsAuthorDiv.addChild(settingsAuthorLabel, DivItemStyle.fixed());
        settingsAuthorDiv.addChild(settingsAuthorInput);
        settingsPathDiv.addChild(settingsPathLabel, DivItemStyle.fixed());
        settingsPathDiv.addChild(settingsPathInput);
        settingsAnimationDiv.addChild(settingsAnimationLabel, DivItemStyle.fixed());
        settingsAnimationDiv.addChild(settingsAnimationToggle);
        settingsDensityDiv.addChild(settingsDensityLabel, DivItemStyle.fixed());
        settingsDensityDiv.addChild(settingsDensitySelector);
        settingsFooterDiv.addChild(settingsApplyButton, DivItemStyle.noGrow());
        settingsFooterDiv.addChild(settingsResetButton, DivItemStyle.noGrow());
        settingsFooterDiv.addChild(backFromSettingsButton, DivItemStyle.noGrow());

        settingsDivRoot.addChild(settingsTitleLabel, DivItemStyle.noGrow());
        settingsDivRoot.addChild(settingsIntroLabel, DivItemStyle.noGrow());
        settingsDivRoot.addChild(settingsProfileDiv, DivItemStyle.noGrow());
        settingsDivRoot.addChild(settingsAuthorDiv, DivItemStyle.noGrow());
        settingsDivRoot.addChild(settingsPathDiv, DivItemStyle.noGrow());
        settingsDivRoot.addChild(settingsAnimationDiv, DivItemStyle.noGrow());
        settingsDivRoot.addChild(settingsDensityDiv, DivItemStyle.noGrow());
        settingsDivRoot.addChild(settingsPreviewPanel);
        settingsDivRoot.addChild(settingsFooterHintLabel, DivItemStyle.noGrow());
        settingsDivRoot.addChild(settingsFooterDiv, DivItemStyle.noGrow());
        settingsFormPage.getContent().addChild(settingsDivRoot);

        focusProfileDiv.addChild(focusNameLabel, DivItemStyle.fixed());
        focusProfileDiv.addChild(focusNameInput);
        focusPathDiv.addChild(focusPathLabel, DivItemStyle.fixed());
        focusPathDiv.addChild(focusPathInput);
        focusModeDiv.addChild(focusToggle);
        focusModeDiv.addChild(focusSelector);
        focusActionDiv.addChild(focusPrimaryButton, DivItemStyle.noGrow());
        focusActionDiv.addChild(focusSecondaryButton, DivItemStyle.noGrow());
        focusActionDiv.addChild(backFromFocusButton, DivItemStyle.noGrow());
        focusDivRoot.addChild(focusTitleLabel, DivItemStyle.noGrow());
        focusDivRoot.addChild(focusHintLabel, DivItemStyle.noGrow());
        focusDivRoot.addChild(focusProfileDiv, DivItemStyle.noGrow());
        focusDivRoot.addChild(focusPathDiv, DivItemStyle.noGrow());
        focusDivRoot.addChild(focusModeDiv, DivItemStyle.noGrow());
        focusDivRoot.addChild(focusStatusLabel, DivItemStyle.noGrow());
        focusDivRoot.addChild(focusActionDiv, DivItemStyle.noGrow());
        focusNavigationPage.getContent().addChild(focusDivRoot);

        root.addChild(menuPage);
        root.addChild(inputTestPage);
        root.addChild(mouseStressPage);
        root.addChild(characterPlacementPage);
        root.addChild(responsiveLayoutPage);
        root.addChild(settingsFormPage);
        root.addChild(focusNavigationPage);
    }

    private void refreshStateText() {
        stateLabel.setText(toggled ? "输入链路正常：按钮点击已触发" : "等待点击按钮验证输入链路");
    }

    private void refreshEchoText() {
        String inputText = textInputWidget.getText();
        inputEchoLabel.setText(inputText.isEmpty() ? "当前输入：<空>" : "当前输入：" + inputText);
    }

    private void refreshResponsiveMetrics() {
        responsiveMetricsLabel.setText("原生窗口 " + width + "x" + height + " | 页面 " + responsiveLayoutPage.getWidth() + "x"
                + responsiveLayoutPage.getHeight() + " | Arena " + responsiveArenaPanel.getWidth() + "x"
                + responsiveArenaPanel.getHeight());
    }

    private void refreshSettingsState() {
        settingsAnimationToggle.setChecked(settingsAnimationsEnabled).setLabel(settingsAnimationsEnabled ? "动画开关：启用" : "动画开关：关闭");
        settingsDensitySelector.setSelectedIndex(settingsCompactDensity ? 0 : 1);
        String profile = settingsProfileInput.getText().isEmpty() ? "<未填写>" : settingsProfileInput.getText();
        String author = settingsAuthorInput.getText().isEmpty() ? "<未填写>" : settingsAuthorInput.getText();
        String path = settingsPathInput.getText().isEmpty() ? "<未填写>" : settingsPathInput.getText();
        String density = settingsCompactDensity ? "紧凑" : "舒适";
        settingsPreviewSummaryLabel.setText("名称：" + profile + "；作者：" + author + "；资源路径：" + path
                + "。动画当前为" + (settingsAnimationsEnabled ? "启用" : "关闭") + "，布局密度为" + density
                + "。预览面板本身设置了 grow=1，它会吞掉设置页中剩余的可用高度，用来模拟真实配置页里的说明、预览或列表区域。 ");
        settingsFooterHintLabel.setText("最近操作：" + settingsLastAction);
    }

    private void refreshFocusState() {
        String name = focusNameInput.getText().isEmpty() ? "<未填写>" : focusNameInput.getText();
        String path = focusPathInput.getText().isEmpty() ? "<未填写>" : focusPathInput.getText();
        focusStatusLabel.setText("当前名称：" + name + "；资源：" + path + "；开关："
                + (focusToggle.isChecked() ? "启用" : "关闭") + "；模式：" + focusSelector.getSelectedOption()
                + "；最近操作：" + focusLastAction);
    }

    private void showPage(Widget page) {
        clearInteractionState();
        currentPage = page;
        menuPage.setVisible(page == menuPage);
        inputTestPage.setVisible(page == inputTestPage);
        mouseStressPage.setVisible(page == mouseStressPage);
        characterPlacementPage.setVisible(page == characterPlacementPage);
        responsiveLayoutPage.setVisible(page == responsiveLayoutPage);
        settingsFormPage.setVisible(page == settingsFormPage);
        focusNavigationPage.setVisible(page == focusNavigationPage);
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
        inputTestPage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        mouseStressPage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        characterPlacementPage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        responsiveLayoutPage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        settingsFormPage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        focusNavigationPage.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
    }

    private void applyMenuLayout() {
        int menuButtonMinWidth = adaptiveWidth(220, 120, 0.16F);
        menuDivRoot.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        menuTitleLabel.setLayoutSpec(null);
        menuHintLabel.setLayoutSpec(null);
        openInputTestButton.setLayoutSpec(null);
        openMouseStressButton.setLayoutSpec(null);
        openCharacterPlacementButton.setLayoutSpec(null);
        openResponsiveLayoutButton.setLayoutSpec(null);
        openSettingsFormButton.setLayoutSpec(null);
        openFocusNavigationButton.setLayoutSpec(null);

        menuTitleLabel.setSuggestedSize(-1, -1);
        menuHintLabel.setSuggestedSize(-1, -1);
        openInputTestButton.setSuggestedSize(menuButtonMinWidth, openInputTestButton.getPreferredHeight());
        openMouseStressButton.setSuggestedSize(menuButtonMinWidth, openMouseStressButton.getPreferredHeight());
        openCharacterPlacementButton.setSuggestedSize(menuButtonMinWidth, openCharacterPlacementButton.getPreferredHeight());
        openResponsiveLayoutButton.setSuggestedSize(menuButtonMinWidth, openResponsiveLayoutButton.getPreferredHeight());
        openSettingsFormButton.setSuggestedSize(menuButtonMinWidth, openSettingsFormButton.getPreferredHeight());
        openFocusNavigationButton.setSuggestedSize(menuButtonMinWidth, openFocusNavigationButton.getPreferredHeight());
    }

    private void applyInputLayout() {
        int fieldMinWidth = adaptiveWidth(320, 170, 0.24F);
        int primaryButtonMinWidth = adaptiveWidth(220, 120, 0.16F);
        int secondaryButtonMinWidth = adaptiveWidth(180, 120, 0.14F);
        inputDivRoot.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        inputTitleLabel.setLayoutSpec(null);
        stateLabel.setLayoutSpec(null);
        inputHintLabel.setLayoutSpec(null);
        textInputWidget.setLayoutSpec(null);
        inputEchoLabel.setLayoutSpec(null);
        testButton.setLayoutSpec(null);
        backFromInputButton.setLayoutSpec(null);

        inputTitleLabel.setSuggestedSize(-1, -1);
        stateLabel.setSuggestedSize(-1, -1);
        inputHintLabel.setSuggestedSize(-1, -1);
        inputEchoLabel.setSuggestedSize(-1, -1);
        textInputWidget.setSuggestedSize(fieldMinWidth, textInputWidget.getPreferredHeight());
        testButton.setSuggestedSize(primaryButtonMinWidth, testButton.getPreferredHeight());
        backFromInputButton.setSuggestedSize(secondaryButtonMinWidth, backFromInputButton.getPreferredHeight());
    }

    private void applyMouseLayout() {
        int panelMinHeight = adaptiveHeight(300, 180, 0.28F);
        int buttonMinWidth = adaptiveWidth(180, 120, 0.14F);
        mouseDivRoot.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        mouseTitleLabel.setLayoutSpec(null);
        mouseHintLabel.setLayoutSpec(null);
        mouseStressWidget.setLayoutSpec(null);
        backFromMouseButton.setLayoutSpec(null);

        mouseTitleLabel.setSuggestedSize(-1, -1);
        mouseHintLabel.setSuggestedSize(-1, -1);
        mouseStressWidget.setSuggestedSize(-1, panelMinHeight);
        backFromMouseButton.setSuggestedSize(buttonMinWidth, backFromMouseButton.getPreferredHeight());
    }

    private void applyCharacterLayout() {
        int previewMinHeight = adaptiveHeight(300, 180, 0.28F);
        int debugMinWidth = adaptiveWidth(480, 220, 0.34F);
        int debugMinHeight = adaptiveHeight(260, 150, 0.22F);
        int buttonMinWidth = adaptiveWidth(180, 120, 0.14F);
        characterDivRoot.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        characterTitleLabel.setLayoutSpec(null);
        characterHintLabel.setLayoutSpec(null);
        characterPreviewPanel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.percent(0.52F)).setMinHeight(previewMinHeight).setGrow(1.0F));
        characterPlacementDebugWidget.setLayoutSpec(new UiLayoutSpec().setAnchor(UiAnchor.CENTER).setWidth(UiLength.percent(1.08F)).setHeight(UiLength.percent(1.08F)).setMinWidth(debugMinWidth).setMinHeight(debugMinHeight));
        characterClampHintLabel.setLayoutSpec(null);
        backFromCharacterButton.setLayoutSpec(null);

        characterTitleLabel.setSuggestedSize(-1, -1);
        characterHintLabel.setSuggestedSize(-1, -1);
        characterPreviewPanel.setSuggestedSize(-1, previewMinHeight);
        characterClampHintLabel.setSuggestedSize(-1, -1);
        backFromCharacterButton.setSuggestedSize(buttonMinWidth, backFromCharacterButton.getPreferredHeight());
    }

    private void applyResponsiveLayoutPage() {
        int topProbeMinWidth = adaptiveWidth(220, 120, 0.16F);
        int bottomLeftMinWidth = adaptiveWidth(180, 100, 0.14F);
        int bottomRightMinWidth = adaptiveWidth(260, 140, 0.20F);
        int arenaMinHeight = adaptiveHeight(320, 190, 0.32F);
        int arenaTopLeftMinWidth = adaptiveWidth(180, 100, 0.14F);
        int arenaCenterMinWidth = adaptiveWidth(220, 120, 0.16F);
        int arenaBottomRightMinWidth = adaptiveWidth(170, 96, 0.13F);
        int buttonMinWidth = adaptiveWidth(180, 120, 0.14F);
        responsiveDivRoot.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        responsiveTitleLabel.setLayoutSpec(null);
        responsiveHintLabel.setLayoutSpec(null);
        responsiveMetricsLabel.setLayoutSpec(null);
        responsiveLeftProbe.setLayoutSpec(null);
        responsiveRightProbe.setLayoutSpec(null);
        responsiveBottomLeftProbe.setLayoutSpec(null);
        responsiveBottomRightProbe.setLayoutSpec(null);
        responsiveArenaPanel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.percent(0.42F)).setMinHeight(arenaMinHeight).setGrow(1.0F));
        arenaTopLeftProbe.setLayoutSpec(new UiLayoutSpec().setAnchor(UiAnchor.TOP_LEFT).setMargin(UiInsets.of(12, 12, 0, 0)).setWidth(UiLength.percent(0.28F)).setHeight(UiLength.px(118)).setMinWidth(arenaTopLeftMinWidth));
        arenaCenterProbe.setLayoutSpec(new UiLayoutSpec().setAnchor(UiAnchor.CENTER).setWidth(UiLength.percent(0.34F)).setHeight(UiLength.px(128)).setMinWidth(arenaCenterMinWidth));
        arenaBottomRightProbe.setLayoutSpec(new UiLayoutSpec().setAnchor(UiAnchor.BOTTOM_RIGHT).setMargin(UiInsets.of(0, 0, 12, 12)).setWidth(UiLength.percent(0.24F)).setHeight(UiLength.px(118)).setMinWidth(arenaBottomRightMinWidth));
        responsiveFooterLabel.setLayoutSpec(null);
        backFromResponsiveButton.setLayoutSpec(null);

        responsiveTitleLabel.setSuggestedSize(-1, -1);
        responsiveHintLabel.setSuggestedSize(-1, -1);
        responsiveMetricsLabel.setSuggestedSize(-1, -1);
        responsiveLeftProbe.setSuggestedSize(topProbeMinWidth, 118);
        responsiveRightProbe.setSuggestedSize(topProbeMinWidth, 118);
        responsiveBottomLeftProbe.setSuggestedSize(bottomLeftMinWidth, 118);
        responsiveBottomRightProbe.setSuggestedSize(bottomRightMinWidth, 118);
        responsiveArenaPanel.setSuggestedSize(-1, arenaMinHeight);
        responsiveFooterLabel.setSuggestedSize(-1, -1);
        backFromResponsiveButton.setSuggestedSize(buttonMinWidth, backFromResponsiveButton.getPreferredHeight());
    }

    private void applySettingsLayout() {
        settingsDivRoot.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        settingsPreviewDiv.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        int scrollMinHeight = adaptiveHeight(280, 150, 0.24F);
        int labelWidth = adaptiveWidth(220, 120, 0.17F);
        int fieldMinWidth = adaptiveWidth(260, 150, 0.20F);
        int selectorMinWidth = adaptiveWidth(420, 180, 0.28F);
        int previewMinHeight = adaptiveHeight(220, 150, 0.20F);
        int buttonMinWidth = adaptiveWidth(180, 120, 0.14F);
        settingsTitleLabel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
        settingsIntroLabel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));

        settingsPreviewPanel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setMinHeight(previewMinHeight).setGrow(1.0F));
        settingsPreviewTitleLabel.setLayoutSpec(null);
        settingsPreviewSummaryLabel.setLayoutSpec(null);
        settingsFooterHintLabel.setLayoutSpec(null);
        settingsProfileLabel.setLayoutSpec(null);
        settingsAuthorLabel.setLayoutSpec(null);
        settingsPathLabel.setLayoutSpec(null);
        settingsAnimationLabel.setLayoutSpec(null);
        settingsDensityLabel.setLayoutSpec(null);
        settingsProfileInput.setLayoutSpec(null);
        settingsAuthorInput.setLayoutSpec(null);
        settingsPathInput.setLayoutSpec(null);
        settingsAnimationToggle.setLayoutSpec(null);
        settingsDensitySelector.setLayoutSpec(null);
        settingsApplyButton.setLayoutSpec(null);
        settingsResetButton.setLayoutSpec(null);
        backFromSettingsButton.setLayoutSpec(null);

        settingsProfileLabel.setSuggestedSize(labelWidth, -1);
        settingsAuthorLabel.setSuggestedSize(labelWidth, -1);
        settingsPathLabel.setSuggestedSize(labelWidth, -1);
        settingsAnimationLabel.setSuggestedSize(labelWidth, -1);
        settingsDensityLabel.setSuggestedSize(labelWidth, -1);

        settingsProfileInput.setSuggestedSize(fieldMinWidth, settingsProfileInput.getPreferredHeight());
        settingsAuthorInput.setSuggestedSize(fieldMinWidth, settingsAuthorInput.getPreferredHeight());
        settingsPathInput.setSuggestedSize(fieldMinWidth, settingsPathInput.getPreferredHeight());
        settingsAnimationToggle.setSuggestedSize(fieldMinWidth, settingsAnimationToggle.getPreferredHeight());
        settingsDensitySelector.setSuggestedSize(selectorMinWidth, settingsDensitySelector.getPreferredHeight());
        settingsApplyButton.setSuggestedSize(buttonMinWidth, settingsApplyButton.getPreferredHeight());
        settingsResetButton.setSuggestedSize(buttonMinWidth, settingsResetButton.getPreferredHeight());
        backFromSettingsButton.setSuggestedSize(buttonMinWidth, backFromSettingsButton.getPreferredHeight());
    }

    private void applyFocusLayout() {
        int labelWidth = adaptiveWidth(220, 120, 0.17F);
        int fieldMinWidth = adaptiveWidth(260, 150, 0.20F);
        int selectorMinWidth = adaptiveWidth(420, 180, 0.28F);
        int buttonMinWidth = adaptiveWidth(180, 120, 0.14F);
        focusDivRoot.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        focusTitleLabel.setLayoutSpec(null);
        focusHintLabel.setLayoutSpec(null);
        focusNameLabel.setLayoutSpec(null);
        focusPathLabel.setLayoutSpec(null);
        focusNameInput.setLayoutSpec(null);
        focusPathInput.setLayoutSpec(null);
        focusToggle.setLayoutSpec(null);
        focusSelector.setLayoutSpec(null);
        focusStatusLabel.setLayoutSpec(null);
        focusPrimaryButton.setLayoutSpec(null);
        focusSecondaryButton.setLayoutSpec(null);
        backFromFocusButton.setLayoutSpec(null);

        focusNameLabel.setSuggestedSize(labelWidth, -1);
        focusPathLabel.setSuggestedSize(labelWidth, -1);
        focusNameInput.setSuggestedSize(fieldMinWidth, focusNameInput.getPreferredHeight());
        focusPathInput.setSuggestedSize(fieldMinWidth, focusPathInput.getPreferredHeight());
        focusToggle.setSuggestedSize(fieldMinWidth, focusToggle.getPreferredHeight());
        focusSelector.setSuggestedSize(selectorMinWidth, focusSelector.getPreferredHeight());
        focusPrimaryButton.setSuggestedSize(buttonMinWidth, focusPrimaryButton.getPreferredHeight());
        focusSecondaryButton.setSuggestedSize(buttonMinWidth, focusSecondaryButton.getPreferredHeight());
        backFromFocusButton.setSuggestedSize(buttonMinWidth, backFromFocusButton.getPreferredHeight());
    }

    private int adaptiveWidth(int preferred, int floor, float viewportRatio) {
        return clampValue(Math.round(viewportWidthHint * viewportRatio), floor, preferred);
    }

    private int adaptiveHeight(int preferred, int floor, float viewportRatio) {
        return clampValue(Math.round(viewportHeightHint * viewportRatio), floor, preferred);
    }

    private int clampValue(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
