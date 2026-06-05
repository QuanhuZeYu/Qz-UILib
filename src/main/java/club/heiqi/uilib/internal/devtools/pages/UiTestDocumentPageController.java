package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.net.transport.NetTransportFactory;
import club.heiqi.uilib.ui.animation.DocumentAnimation;
import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationOptions;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.animation.DocumentTransitionSpec;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentCheckboxControl;
import club.heiqi.uilib.ui.control.DocumentInputType;
import club.heiqi.uilib.ui.control.DocumentRadioGroupControl;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectorControl;
import club.heiqi.uilib.ui.control.DocumentSelectControl;
import club.heiqi.uilib.ui.control.DocumentSliderControl;
import club.heiqi.uilib.ui.control.DocumentTabControl;
import club.heiqi.uilib.ui.control.DocumentTabContentBuilder;
import club.heiqi.uilib.ui.control.DocumentTextAreaControl;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.control.UiRadioOrientation;
import club.heiqi.uilib.ui.control.UiSliderOrientation;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDoubleClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDoubleClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementMouseDownEvent;
import club.heiqi.uilib.ui.dom.DocumentElementMouseDownHandler;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpEvent;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpHandler;
import club.heiqi.uilib.ui.dom.DocumentElementWheelEvent;
import club.heiqi.uilib.ui.dom.DocumentElementWheelHandler;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationEvent;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentFragmentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.style.props.UiAlignContent;
import club.heiqi.uilib.ui.style.props.UiAnimationDirection;
import club.heiqi.uilib.ui.style.props.UiBorderCollapse;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.selector.UiPseudoClass;
import club.heiqi.uilib.ui.style.selector.UiSelector;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.props.UiScrollbarWidth;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.props.UiTextDecoration;
import club.heiqi.uilib.ui.style.props.UiTextTransform;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.props.UiWordBreak;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.values.UiBorderColors;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.values.UiOutline;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `/qzuilib test` P0 首页控制器。
 */
public final class UiTestDocumentPageController extends DocumentPageController {

    private static final String SPEC_PATH = "docs/开发者文档/specs/qzuilib-test-page-rebuild-plan.md";
    private static final String STATUS_NOT_EXECUTED = "未执行";
    private static final String STATUS_RUNNING = "执行中";
    private static final String STATUS_PASSED = "通过：观察结果与预期一致";
    private static final String STATUS_FAILED_PREFIX = "失败：观察结果与预期不一致 - ";
    private static final int CSS_INLINE_TEXT_COLOR = 0xFF69F0AE;
    private static final int CSS_STYLESHEET_TEXT_COLOR = 0xFFFF7A7A;

    private static final TestGroup DOM_GROUP = new TestGroup(
            "DOM",
            "DOM 与选择器语义",
            "节点归属、插入移动、fragment、属性、classList 与 selector 查询。",
            0,
            0);
    private static final TestGroup CSS_GROUP = new TestGroup(
            "CSS",
            "CSS 级联与样式语义",
            "级联优先级、继承、盒模型、背景、边框、阴影、文本样式与可见性。",
            0,
            0);
    private static final TestGroup LAYOUT_GROUP = new TestGroup(
            "LAYOUT",
            "Layout 布局与尺寸语义",
            "block、inline、flex、table、position、sticky、fixed containing block 与滚动范围。",
            0,
            0);
    private static final TestGroup PAINT_GROUP = new TestGroup(
            "PAINT",
            "Paint 绘制、命中与视觉语义",
            "绘制层级、stacking context、clip、transform 命中、top-layer、scrollbar 与 host image。",
            0,
            0);
    private static final TestGroup INPUT_GROUP = new TestGroup(
            "INPUT",
            "Input 输入与事件语义",
            "事件传播、默认行为、键盘、焦点、滚轮与拖拽。",
            0,
            0);
    private static final TestGroup CONTROLS_GROUP = new TestGroup(
            "CTRL",
            "Controls 控件与表单语义",
            "按钮、输入框、选择器、槽位、tooltip 与 overlay 控件。",
            0,
            0);
    private static final TestGroup TEXT_FONT_GROUP = new TestGroup(
            "TEXT",
            "TextFont 文本、字体与国际化语义",
            "文本模式、格式码、字符测量、fallback、reload 与 wrap。",
            0,
            0);
    private static final TestGroup ANIMATION_GROUP = new TestGroup(
            "ANIM",
            "Animation 动画与 Transition 语义",
            "transition、keyframes、timing、fill-mode 与布局/绘制影响。",
            0,
            0);
    private static final TestGroup RUNTIME_HOST_GROUP = new TestGroup(
            "HOST",
            "RuntimeHost 宿主运行时语义",
            "开屏时序、resize、runtime stats、GL 上下文、HUD 与异常面板。",
            0,
            0);
    private static final TestGroup REMOTE_NET_GROUP = new TestGroup(
            "NET",
            "RemoteNet 远程、配置与网络语义",
            "Channel、Fetch、Stream、Store、远程页面、远程 HUD 与配置同步。",
            0,
            0);
    private static final List<TestGroup> TEST_GROUPS = Collections.unmodifiableList(Arrays.asList(
            DOM_GROUP,
            CSS_GROUP,
            LAYOUT_GROUP,
            PAINT_GROUP,
            INPUT_GROUP,
            CONTROLS_GROUP,
            TEXT_FONT_GROUP,
            ANIMATION_GROUP,
            RUNTIME_HOST_GROUP,
            REMOTE_NET_GROUP));
    private final DocumentPageAuthoringSurface diagnosticPage;
    private final DocumentPageRuntimeView runtimeView;
    private final TextMeasureService textMeasureService;
    private final List<RuntimeTestCase> runtimeTestCases;
    private final UiDocument document;
    private final ElementNode rootElement;
    private final int fontEpoch;
    private final String defaultTextMode;
    private final String runtimeAdapterSummary;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private TextNode environmentText;
    private TextNode implementedCaseCountText;
    private TextNode passedCountText;
    private TextNode failedCountText;
    private TextNode manualPendingCountText;
    private TextNode failureSummaryText;
    private RuntimeTestCase lastFailedCase;

    /**
     * 创建 test P0 首页控制器。
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
        this.textMeasureService = resolvedDocumentUi.getTextMeasureService();
        this.runtimeTestCases = createRuntimeTestCases();
        this.fontEpoch = textMeasureService.getEpoch();
        this.defaultTextMode = String.valueOf(resolvedDocumentUi.getDefaultTextContentMode());
        this.runtimeAdapterSummary = buildRuntimeAdapterSummary(resolvedDocumentUi);

        this.document = UiDocument.create();
        document.setDefaultTextContentMode(resolvedDocumentUi.getDefaultTextContentMode());
        this.rootElement = document.getRootElement();
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 760, 520,
                textMeasureService);
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        createHomeDocument(document, rootElement);
    }

    /**
     * 配置 test 首页宿主尺寸。
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
        refreshEnvironmentText();
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
     * 构建 test P0 首页文档。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void createHomeDocument(UiDocument document, ElementNode root) {
        applyRootStyle(root);
        showHomePage();
    }

    /**
     * 应用 test 页面根容器样式。
     *
     * @param root 根元素
     */
    private void applyRootStyle(ElementNode root) {
        root.style()
                .setPadding(UiStyleLength.px(22))
                .setBackgroundColor(0xF0091020)
                .setBorderColor(0xFF4F7CFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(24))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(0xFFEAF1FF);
    }

    /**
     * 显示 test 首页。
     */
    private void showHomePage() {
        resetPageTextBindings();
        rootElement.clearChildren();
        appendHero(document, rootElement);
        appendOverview(document, rootElement);
        appendGroupIndex(document, rootElement);
        appendFailureSection(document, rootElement);
        appendHomeManualSection(document, rootElement);
        appendEnvironmentSection(document, rootElement);
        appendContractSection(document, rootElement);
    }

    /**
     * 显示指定类型的运行时测试二级页。
     *
     * @param group 测试分组
     */
    private void showGroupPage(TestGroup group) {
        resetPageTextBindings();
        rootElement.clearChildren();
        appendGroupPageHero(document, rootElement, group);
        appendOverview(document, rootElement);
        appendSiblingGroupNavigation(document, rootElement, group);
        appendGroupCaseCardSection(document, rootElement, group);
        appendGroupManualSection(document, rootElement, group);
        appendFailureSection(document, rootElement);
        appendEnvironmentSection(document, rootElement);
        appendContractSection(document, rootElement);
    }

    /**
     * 清理当前页动态文本绑定，避免结果刷新写入已卸载节点。
     */
    private void resetPageTextBindings() {
        environmentText = null;
        implementedCaseCountText = null;
        passedCountText = null;
        failedCountText = null;
        manualPendingCountText = null;
        failureSummaryText = null;
        for (RuntimeTestCase testCase : runtimeTestCases) {
            testCase.clearViewBindings();
        }
    }

    /**
     * 创建当前运行时用例列表。
     *
     * @return 运行时用例列表
     */
    private List<RuntimeTestCase> createRuntimeTestCases() {
        return Collections.emptyList();
    }

    /**
     * 追加顶部说明区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendHero(UiDocument document, ElementNode root) {
        ElementNode hero = document.div();
        hero.style()
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF101D33)
                .setBorderColor(0xFF7AA2FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);

        ElementNode title = document.div();
        title.style()
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        title.appendText("Qz UILib Test 首页");
        hero.append(title);

        ElementNode body = document.div();
        body.style()
                .setMargin(UiStyleLength.px(8))
                .setTextColor(0xFFD9E6FF);
        body.appendText("当前运行时测试内容已清空；首页只保留总览、分组导航和环境信息，等待后续重新规划。系列旧卡片不会再展示或执行。");
        hero.append(body);

        ElementNode spec = document.div();
        spec.style()
                .setMargin(UiStyleLength.px(8))
                .setTextColor(0xFF9FB9EA);
        spec.appendText("规格来源：" + SPEC_PATH);
        hero.append(spec);
        root.append(hero);
    }

    /**
     * 追加分组二级页顶部说明区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param group 当前分组
     */
    private void appendGroupPageHero(UiDocument document, ElementNode root, final TestGroup group) {
        ElementNode hero = document.div();
        hero.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF101D33)
                .setBorderColor(0xFF7AA2FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);

        ElementNode title = document.div();
        title.style()
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        title.appendText("Qz UILib Test / " + group.getCode() + " 二级页");
        hero.append(title);

        appendMutedText(document, hero, group.getTitle() + "：" + group.getCoverage());
        appendMutedText(document, hero, "本页只展示 " + group.getCode()
                + " 类型运行时卡片；其他类型请回到首页或使用同级分组导航。规格来源：" + SPEC_PATH);
        ElementNode actions = createGrid(document);
        actions.append(createActionButton(document, "返回首页", 0xFF475569, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                showHomePage();
            }
        }));
        hero.append(actions);
        root.append(hero);
    }

    /**
     * 追加总览区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendOverview(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "总览");
        ElementNode grid = createGrid(document);
        appendMetricCard(document, grid, "test 系统版本", "P0 语义首页");
        implementedCaseCountText = appendMetricCard(document, grid, "已实现用例数", buildImplementedCaseCountText());
        passedCountText = appendMetricCard(document, grid, "通过数",
                String.valueOf(countStatus(RuntimeTestStatus.PASSED)));
        failedCountText = appendMetricCard(document, grid, "失败数",
                String.valueOf(countStatus(RuntimeTestStatus.FAILED)));
        manualPendingCountText = appendMetricCard(document, grid, "人工待确认数", String.valueOf(countManualPending()));
        appendMetricCard(document, grid, "二级页数量", String.valueOf(TEST_GROUPS.size()));
        section.append(grid);
        appendPlanItem(document, section, "运行时卡片内容已清空；首页只显示 DOM、CSS、Layout、Paint、Input、Controls、TextFont、Animation、RuntimeHost、RemoteNet 二级入口。");
        appendPlanItem(document, section, "各二级页暂时只保留空态说明，不展示旧用例卡片、不提供旧执行入口或人工确认按钮。");
        root.append(section);
    }

    /**
     * 追加分组索引区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendGroupIndex(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "分组导航");
        ElementNode grid = createGrid(document);
        for (TestGroup group : TEST_GROUPS) {
            appendGroupCard(document, grid, group);
        }
        section.append(grid);
        root.append(section);
    }

    /**
     * 追加同级分组导航区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param currentGroup 当前分组
     */
    private void appendSiblingGroupNavigation(UiDocument document, ElementNode root, TestGroup currentGroup) {
        ElementNode section = createSection(document, "同级分组导航");
        ElementNode grid = createGrid(document);
        for (TestGroup group : TEST_GROUPS) {
            if (group == currentGroup) {
                appendMetricCard(document, grid, group.getCode(), "当前页");
            } else {
                appendCompactGroupButton(document, grid, group);
            }
        }
        section.append(grid);
        root.append(section);
    }

    /**
     * 追加分组运行时用例卡片区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param group 当前分组
     */
    private void appendGroupCaseCardSection(UiDocument document, ElementNode root, TestGroup group) {
        ElementNode section = createSection(document, group.getCode() + " 运行时用例卡片");
        List<RuntimeTestCase> groupCases = getRuntimeTestCases(group);
        if (groupCases.isEmpty()) {
            appendPlanItem(document, section, "本类型运行时测试内容已清空；后续需要重新规划用例文本、样例展示和自动断言后再接入。");
        }
        for (RuntimeTestCase testCase : groupCases) {
            appendRuntimeCaseCard(document, section, testCase);
        }
        root.append(section);
    }

    /**
     * 追加最近失败区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendFailureSection(UiDocument document, ElementNode root) {
        ElementNode failures = createSection(document, "最近失败");
        ElementNode failureItem = createPlanItem(document);
        failureSummaryText = failureItem.appendText(buildFailureSummaryText());
        failures.append(failureItem);
        root.append(failures);
    }

    /**
     * 追加首页人工任务区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendHomeManualSection(UiDocument document, ElementNode root) {
        ElementNode manual = createSection(document, "人工任务");
        appendPlanItem(document, manual, "运行时测试内容已清空；人工任务需要在后续重规划后重新定义。");
        for (TestGroup group : TEST_GROUPS) {
            int caseCount = countRuntimeTestCases(group);
            appendPlanItem(document, manual, group.getCode() + "：已接入 " + caseCount
                    + " 张运行时卡片；规格总数 " + group.getTotalCaseCount()
                    + "；剩余缺口 " + Math.max(0, group.getTotalCaseCount() - caseCount) + "。");
        }
        root.append(manual);
    }

    /**
     * 追加分组人工任务区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param group 当前分组
     */
    private void appendGroupManualSection(UiDocument document, ElementNode root, TestGroup group) {
        ElementNode manual = createSection(document, group.getCode() + " 人工任务");
        List<RuntimeTestCase> groupCases = getRuntimeTestCases(group);
        if (groupCases.isEmpty()) {
            appendPlanItem(document, manual, "本类型暂无运行时卡片，暂不需要人工确认。");
        }
        for (RuntimeTestCase testCase : groupCases) {
            appendPlanItem(document, manual, testCase.getId() + "：" + testCase.getExpectedResult());
        }
        root.append(manual);
    }

    /**
     * 追加环境信息区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendEnvironmentSection(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "环境信息");
        ElementNode item = createPlanItem(document);
        environmentText = item.appendText(buildEnvironmentText());
        section.append(item);
        root.append(section);
    }

    /**
     * 追加运行时用例卡片契约区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendContractSection(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "统一展示规则");
        appendPlanItem(document, section, "当前运行时测试卡片规则已清空，后续由重新规划后的规格重新定义字段、状态和执行入口。");
        appendPlanItem(document, section, "页面主结构暂时保留 DOM、CSS、Layout、Paint 等语义分组入口，不恢复旧页面名组织。");
        root.append(section);
    }

    /**
     * 创建标准章节容器。
     *
     * @param document 文档实例
     * @param title 章节标题
     * @return 章节容器
     */
    private ElementNode createSection(UiDocument document, String title) {
        ElementNode section = document.div();
        section.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8))
                .setMargin(UiStyleLength.px(14))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF17243A)
                .setBorderColor(0xFF2E4C7F)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16));

        ElementNode heading = document.div();
        heading.style()
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        heading.appendText(title);
        section.append(heading);
        return section;
    }

    /**
     * 创建网格容器。
     *
     * @param document 文档实例
     * @return 网格容器
     */
    private ElementNode createGrid(UiDocument document) {
        ElementNode grid = document.div();
        grid.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setRowGap(UiStyleLength.px(8))
                .setColumnGap(UiStyleLength.px(8));
        return grid;
    }

    /**
     * 追加分组卡片。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param group 分组模型
     */
    private void appendGroupCard(UiDocument document, ElementNode parent, final TestGroup group) {
        ElementNode card = createCard(document, 230, 1.0F, 1.0F);
        ElementNode title = document.div();
        title.style()
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        title.appendText(group.getCode() + " / " + group.getTitle());
        card.append(title);
        appendMutedText(document, card, group.getCoverage());
        appendMutedText(document, card, "覆盖用例：" + group.getTotalCaseCount()
                + "；P0 已接入：" + group.getImplementedCaseCount()
                + "；缺口：" + group.getGapCount());
        appendMutedText(document, card, buildGroupEntryStatus(group));
        card.append(createActionButton(document, "打开 " + group.getCode() + " 二级页", 0xFF2563EB,
                new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        showGroupPage(group);
                    }
                }));
        parent.append(card);
    }

    /**
     * 追加紧凑分组跳转按钮。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param group 分组模型
     */
    private void appendCompactGroupButton(UiDocument document, ElementNode parent, final TestGroup group) {
        parent.append(createActionButton(document, group.getCode(), 0xFF334155, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                showGroupPage(group);
            }
        }));
    }

    /**
     * 构建分组入口状态文本。
     *
     * @param group 分组模型
     * @return 入口状态文本
     */
    private String buildGroupEntryStatus(TestGroup group) {
        int caseCount = countRuntimeTestCases(group);
        if (caseCount > 0) {
            return "入口状态：二级页已接入 " + caseCount + " 张运行时卡片。";
        }
        return "入口状态：运行时卡片已清空，等待重新规划。";
    }

    /**
     * 返回指定分组下的运行时用例。
     *
     * @param group 分组模型
     * @return 运行时用例列表
     */
    private List<RuntimeTestCase> getRuntimeTestCases(TestGroup group) {
        List<RuntimeTestCase> result = new ArrayList<RuntimeTestCase>();
        for (RuntimeTestCase testCase : runtimeTestCases) {
            if (testCase.getGroup() == group) {
                result.add(testCase);
            }
        }
        return result;
    }

    /**
     * 统计指定分组下的运行时用例数量。
     *
     * @param group 分组模型
     * @return 运行时用例数量
     */
    private int countRuntimeTestCases(TestGroup group) {
        int count = 0;
        for (RuntimeTestCase testCase : runtimeTestCases) {
            if (testCase.getGroup() == group) {
                count++;
            }
        }
        return count;
    }

    /**
     * 追加统一运行时用例卡片。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendRuntimeCaseCard(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode card = createCard(document, 1, 1.0F, 1.0F);
        card.style()
                .setBackgroundColor(0xFF101B2E)
                .setBorderColor(0xFF537DD6);
        appendCaseField(document, card, "用例编号", testCase.getId());
        appendCaseField(document, card, "覆盖语义", testCase.getSemantic());
        appendCaseField(document, card, "自动断言", testCase.getAutomaticAssertion());
        appendCaseField(document, card, "操作步骤", testCase.getSteps());
        appendCaseField(document, card, "预期结果", testCase.getExpectedResult());
        appendRuntimeDemo(document, card, testCase);
        testCase.setActualResultText(appendCaseField(document, card, "实际结果",
                testCase.getResult().getActualResult()));
        testCase.setStatusText(appendCaseField(document, card, "状态", testCase.getResult().getStatusText()));
        appendRuntimeActions(document, card, testCase);
        parent.append(card);
    }

    /**
     * 追加卡片字段。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param label 字段名
     * @param value 字段值
     */
    private TextNode appendCaseField(UiDocument document, ElementNode parent, String label, String value) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.START)
                .setColumnGap(UiStyleLength.px(8));

        ElementNode labelNode = document.div();
        labelNode.style()
                .setWidth(UiStyleLength.px(76))
                .setFlexShrink(0.0F)
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFF9FC0FF);
        labelNode.appendText(label);
        row.append(labelNode);

        ElementNode valueNode = document.div();
        valueNode.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setTextColor(0xFFEAF1FF);
        TextNode valueText = valueNode.appendText(value);
        row.append(valueNode);
        parent.append(row);
        return valueText;
    }

    /**
     * 追加运行时演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        appendPlanItem(document, parent, "运行时测试内容已清空；暂无样例。");
    }

    /**
     * 追加运行时操作按钮。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendRuntimeActions(UiDocument document, ElementNode parent, final RuntimeTestCase testCase) {
    }

    /**
     * 创建指标卡片并返回其动态文本节点。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param label 指标名
     * @param value 指标值
     * @return 指标值文本节点
     */
    private TextNode appendMetricCard(UiDocument document, ElementNode parent, String label, String value) {
        ElementNode card = createCard(document, 150, 1.0F, 1.0F);
        appendMutedText(document, card, label);
        ElementNode valueNode = document.div();
        valueNode.style()
                .setMargin(UiStyleLength.px(4))
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        TextNode valueText = valueNode.appendText(value);
        card.append(valueNode);
        parent.append(card);
        return valueText;
    }

    /**
     * 创建运行时演示容器。
     *
     * @param document 文档实例
     * @return 演示容器
     */
    private ElementNode createRuntimeDemoContainer(UiDocument document) {
        ElementNode demo = document.div();
        demo.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0D1728)
                .setBorderColor(0xFF2F4D87)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10));
        return demo;
    }

    /**
     * 创建运行时演示横排容器。
     *
     * @param document 文档实例
     * @return 横排容器
     */
    private ElementNode createRuntimeDemoRow(UiDocument document) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8));
        return row;
    }

    /**
     * 创建演示徽标。
     *
     * @param document 文档实例
     * @param label 徽标文本
     * @param backgroundColor 背景色
     * @return 徽标元素
     */
    private ElementNode createDemoBadge(UiDocument document, String label, int backgroundColor) {
        ElementNode badge = document.div();
        badge.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(28))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(0x88FFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        badge.appendText(label);
        return badge;
    }

    /**
     * 创建 DOM 伪类演示徽标。
     *
     * @param document 文档实例
     * @param label 徽标文本
     * @param className 样式类名
     * @return 演示徽标
     */
    private ElementNode createDomPseudoBadge(UiDocument document, String label, String className) {
        ElementNode badge = createDemoBadge(document, label, 0xFF334155);
        badge.setClassName(className);
        badge.style().setWidth(UiStyleLength.px(96));
        return badge;
    }

    /**
     * 创建 CSS specificity 演示样例。
     *
     * @param document 文档实例
     * @param label 样例文本
     * @param className class 名
     * @param id id 值
     * @return 样例元素
     */
    private ElementNode createCssSample(UiDocument document, String label, String className, String id) {
        return createCssSpecificitySample(document, "div", label, className, id);
    }

    /**
     * 创建 CSS specificity 专用演示样例。
     *
     * @param document 文档实例
     * @param tagName 标签名
     * @param label 样例文本
     * @param className class 名
     * @param id id 值
     * @return 样例元素
     */
    private ElementNode createCssSpecificitySample(UiDocument document, String tagName, String label,
            String className, String id) {
        ElementNode sample = document.element(tagName);
        sample.style()
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF64748B)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8));
        if (className != null) {
            sample.setClassName(className);
        }
        if (id != null) {
            sample.setId(id);
        }
        sample.appendText(label);
        return sample;
    }

    /**
     * 创建 display 演示样例。
     *
     * @param document 文档实例
     * @param label 样例文本
     * @param display display 值
     * @return 样例元素
     */
    private ElementNode createCssDisplaySample(UiDocument document, String label, UiDisplay display) {
        ElementNode sample = createDemoPanel(document, label, 0xFF1F2937);
        sample.style()
                .setDisplay(display)
                .setWidth(UiStyleLength.px(86))
                .setHeight(UiStyleLength.px(28));
        return sample;
    }

    /**
     * 创建盒模型演示样例。
     *
     * @param document 文档实例
     * @param label 样例文本
     * @param boxSizing box-sizing 值
     * @return 样例元素
     */
    private ElementNode createBoxSizingSample(UiDocument document, String label, UiBoxSizing boxSizing) {
        ElementNode sample = createDemoPanel(document, label, boxSizing == UiBoxSizing.BORDER_BOX
                ? 0xFF059669 : 0xFF2563EB);
        sample.style()
                .setWidth(UiStyleLength.px(96))
                .setPadding(UiStyleLength.px(12))
                .setBorderWidth(UiStyleLength.px(4))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBoxSizing(boxSizing);
        return sample;
    }

    /**
     * 创建通用演示面板。
     *
     * @param document 文档实例
     * @param label 文本
     * @param backgroundColor 背景色
     * @return 面板元素
     */
    private ElementNode createDemoPanel(UiDocument document, String label, int backgroundColor) {
        ElementNode panel = document.div();
        panel.style()
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(0xFF64748B)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFEAF1FF);
        panel.appendText(label);
        return panel;
    }

    /**
     * 创建绘制演示舞台。
     *
     * @param document 文档实例
     * @return 绘制舞台
     */
    private ElementNode createPaintStage(UiDocument document) {
        ElementNode stage = document.div();
        stage.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setPadding(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(220))
                .setBackgroundColor(0xFF020617)
                .setBorderColor(0xFF475569)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10));
        return stage;
    }

    /**
     * 创建绘制演示块。
     *
     * @param document 文档实例
     * @param label 文本
     * @param backgroundColor 背景色
     * @return 演示块
     */
    private ElementNode createPaintSample(UiDocument document, String label, int backgroundColor) {
        ElementNode sample = document.div();
        sample.style()
                .setWidth(UiStyleLength.px(118))
                .setHeight(UiStyleLength.px(32))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(0xFFFFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFFFFFFF)
                .setFontWeight(UiFontWeight.BOLD);
        sample.appendText(label);
        return sample;
    }

    /**
     * 创建动画方向演示块。
     *
     * @param document 文档实例
     * @param label 文本
     * @param direction 动画方向
     * @return 演示块
     */
    private ElementNode createAnimationDirectionSample(UiDocument document, String label,
            UiAnimationDirection direction) {
        ElementNode sample = createDemoPanel(document, label, 0xFF1F2937);
        sample.style()
                .setAnimationName("runtime-direction")
                .setAnimationDurationMillis(240)
                .setAnimationIterationCount(2)
                .setAnimationDirection(direction);
        return sample;
    }

    /**
     * 创建动画 fill-mode 演示块。
     *
     * @param document 文档实例
     * @param label 文本
     * @param fillMode 填充模式
     * @return 演示块
     */
    private ElementNode createAnimationFillModeSample(UiDocument document, String label,
            DocumentAnimationFillMode fillMode) {
        ElementNode sample = createDemoPanel(document, label, 0xFF1F2937);
        sample.style()
                .setAnimationName("runtime-fill-mode")
                .setAnimationDurationMillis(240)
                .setAnimationFillMode(fillMode);
        return sample;
    }

    /**
     * 创建运行时表格控件样例。
     *
     * @param document 文档实例
     * @return 表格根元素
     */
    private ElementNode createRuntimeTableSample(UiDocument document) {
        ElementNode table = document.div();
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setBorderCollapse(UiBorderCollapse.SEPARATE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF64748B);
        ElementNode header = createRuntimeTableRow(document, "名称", "状态");
        ElementNode body = createRuntimeTableRow(document, "row-1", "ready");
        table.append(header).append(body);
        return table;
    }

    /**
     * 创建运行时表格行样例。
     *
     * @param document 文档实例
     * @param first 第一列文本
     * @param second 第二列文本
     * @return 表格行元素
     */
    private ElementNode createRuntimeTableRow(UiDocument document, String first, String second) {
        ElementNode row = document.div();
        row.style().setDisplay(UiDisplay.TABLE_ROW);
        row.append(createRuntimeTableCell(document, first));
        row.append(createRuntimeTableCell(document, second));
        return row;
    }

    /**
     * 创建运行时表格单元格样例。
     *
     * @param document 文档实例
     * @param text 单元格文本
     * @return 表格单元格元素
     */
    private ElementNode createRuntimeTableCell(UiDocument document, String text) {
        ElementNode cell = document.div();
        cell.style()
                .setDisplay(UiDisplay.TABLE_CELL)
                .setPadding(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569)
                .setTextColor(0xFFEAF1FF);
        cell.appendText(text);
        return cell;
    }

    /**
     * 创建动画轨道容器，让运行时动画的位移路径直接显示在卡片中。
     *
     * @param document 文档实例
     * @param label 轨道说明
     * @return 动画轨道容器
     */
    private ElementNode createAnimationTrack(UiDocument document, String label) {
        ElementNode track = document.div();
        track.style()
                .setWidth(UiStyleLength.px(230))
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF64748B)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8));
        appendMutedText(document, track, label);
        return track;
    }

    /**
     * 追加演示摘要。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param text 摘要文本
     * @return 摘要文本节点
     */
    private TextNode appendDemoSummary(UiDocument document, ElementNode parent, String text) {
        ElementNode summary = document.div();
        summary.style()
                .setTextColor(0xFFC9D8F8);
        TextNode textNode = summary.appendText(text);
        parent.append(summary);
        return textNode;
    }

    /**
     * 创建布局演示块。
     *
     * @param document 文档实例
     * @param label 块文本
     * @param backgroundColor 背景色
     * @return 演示块元素
     */
    private ElementNode createLayoutDemoBlock(UiDocument document, String label, int backgroundColor) {
        ElementNode block = document.div();
        block.style()
                .setHeight(UiStyleLength.px(22))
                .setMargin(UiStyleLength.px(4))
                .setPadding(UiStyleLength.px(4))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(0x88FFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(6))
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextColor(0xFFFFFFFF);
        block.appendText("Block " + label);
        return block;
    }

    /**
     * 创建操作按钮元素。
     *
     * @param document 文档实例
     * @param label 按钮文本
     * @param backgroundColor 背景色
     * @param actionHandler 动作处理器
     * @return 按钮元素
     */
    private ElementNode createActionButton(UiDocument document, String label, int backgroundColor,
            DocumentButtonActionHandler actionHandler) {
        DocumentButtonControl button = new DocumentButtonControl(document, label);
        button.setActionHandler(actionHandler)
                .setBackgroundColors(backgroundColor, backgroundColor, 0xFF334155)
                .setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
        button.getElement().style()
                .setMinWidth(UiStyleLength.px(110))
                .setPadding(UiStyleLength.px(8));
        return button.getElement();
    }

    /**
     * 执行指定运行时用例。
     *
     * @param testCase 用例模型
     */
    private void executeRuntimeTest(RuntimeTestCase testCase) {
        applyRuntimeTestResult(testCase, RuntimeTestResult.running("运行时测试内容已清空，暂无自动断言。"));
    }

    /**
     * 人工标记用例通过。
     *
     * @param testCase 用例模型
     * @param actualResult 实际结果
     */
    private void markRuntimeTestPassed(RuntimeTestCase testCase, String actualResult) {
        testCase.updateDemoSummary(actualResult);
        applyRuntimeTestResult(testCase, RuntimeTestResult.passed(actualResult));
    }

    /**
     * 人工标记用例失败。
     *
     * @param testCase 用例模型
     * @param actualResult 实际结果
     */
    private void markRuntimeTestFailed(RuntimeTestCase testCase, String actualResult) {
        testCase.updateDemoSummary(actualResult);
        applyRuntimeTestResult(testCase, RuntimeTestResult.failed(actualResult, "人工确认不一致"));
    }

    /**
     * 应用运行时测试结果并刷新所有动态文本。
     *
     * @param testCase 用例模型
     * @param result 运行时结果
     */
    private void applyRuntimeTestResult(RuntimeTestCase testCase, RuntimeTestResult result) {
        if (result.getStatus() == RuntimeTestStatus.FAILED) {
            lastFailedCase = testCase;
        }
        testCase.setResult(result);
        refreshRuntimeResultTexts(testCase);
        refreshOverviewTexts();
    }

    /**
     * 刷新单张卡片的结果文本。
     *
     * @param testCase 用例模型
     */
    private void refreshRuntimeResultTexts(RuntimeTestCase testCase) {
        if (testCase.getActualResultText() != null) {
            testCase.getActualResultText().setText(testCase.getResult().getActualResult());
        }
        if (testCase.getStatusText() != null) {
            testCase.getStatusText().setText(testCase.getResult().getStatusText());
        }
    }

    /**
     * 刷新总览和失败摘要。
     */
    private void refreshOverviewTexts() {
        if (implementedCaseCountText != null) {
            implementedCaseCountText.setText(buildImplementedCaseCountText());
        }
        if (passedCountText != null) {
            passedCountText.setText(String.valueOf(countStatus(RuntimeTestStatus.PASSED)));
        }
        if (failedCountText != null) {
            failedCountText.setText(String.valueOf(countStatus(RuntimeTestStatus.FAILED)));
        }
        if (manualPendingCountText != null) {
            manualPendingCountText.setText(String.valueOf(countManualPending()));
        }
        if (failureSummaryText != null) {
            failureSummaryText.setText(buildFailureSummaryText());
        }
    }

    /**
     * 构建已实现用例数量文本。
     *
     * @return 已实现用例数量
     */
    private String buildImplementedCaseCountText() {
        return String.valueOf(runtimeTestCases.size());
    }

    /**
     * 构建失败摘要文本。
     *
     * @return 失败摘要
     */
    private String buildFailureSummaryText() {
        if (lastFailedCase != null && lastFailedCase.getResult().getStatus() == RuntimeTestStatus.FAILED) {
            return buildFailureSummaryText(lastFailedCase);
        }
        for (RuntimeTestCase testCase : runtimeTestCases) {
            if (testCase.getResult().getStatus() == RuntimeTestStatus.FAILED) {
                return buildFailureSummaryText(testCase);
            }
        }
        return "暂无失败用例；运行时测试矩阵已清空，后续重规划后再恢复失败摘要。";
    }

    /**
     * 构建单个失败用例摘要。
     *
     * @param testCase 失败用例
     * @return 失败摘要文本
     */
    private String buildFailureSummaryText(RuntimeTestCase testCase) {
        return "最近失败：" + testCase.getId() + "；" + testCase.getResult().getActualResult()
                + "；状态=" + testCase.getResult().getStatusText();
    }

    /**
     * 格式化 ARGB 颜色。
     *
     * @param color 颜色值
     * @return 颜色文本
     */
    private String formatColor(int color) {
        return String.format(java.util.Locale.ROOT, "#%08X", Integer.valueOf(color));
    }

    /**
     * 格式化 transform 摘要，避免把值对象内部地址暴露到运行时页面。
     *
     * @param transform transform 值
     * @return transform 摘要
     */
    private String formatTransformSummary(UiTransform transform) {
        return String.format(java.util.Locale.ROOT,
                "transform=translate(%.1f,%.1f) scale(%.2f,%.2f) rotate(%.1fdeg)",
                Float.valueOf(transform.getTranslateX()), Float.valueOf(transform.getTranslateY()),
                Float.valueOf(transform.getScaleX()), Float.valueOf(transform.getScaleY()),
                Float.valueOf(transform.getRotateDegrees()));
    }

    /**
     * 格式化样式长度摘要，避免值对象默认地址进入运行时页面。
     *
     * @param length 样式长度
     * @return 可读长度摘要
     */
    private String formatLengthSummary(UiStyleLength length) {
        if (length == null) {
            return "none";
        }
        if (length.getType() == UiStyleLength.Type.AUTO) {
            return "auto";
        }
        if (length.getType() == UiStyleLength.Type.PERCENT) {
            return String.format(java.util.Locale.ROOT, "%.0f%%", Float.valueOf(length.getValue() * 100.0F));
        }
        if (length.getType() == UiStyleLength.Type.CALC) {
            return String.format(java.util.Locale.ROOT, "calc(%.0f%% %+,.1fpx)",
                    Float.valueOf(length.getValue() * 100.0F), Float.valueOf(length.getPixelOffset()));
        }
        return String.format(java.util.Locale.ROOT, "%.1fpx", Float.valueOf(length.getValue()));
    }

    /**
     * 构建直接子节点文本摘要。
     *
     * @param parent 父节点
     * @return 文本摘要
     */
    private String buildChildrenTextSummary(ElementNode parent) {
        StringBuilder builder = new StringBuilder();
        for (DocumentNode child : parent.getChildren()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(child.getTextContent());
        }
        return builder.toString();
    }

    /**
     * 统计直接子节点中指定节点引用出现次数。
     *
     * @param parent 父元素
     * @param expected 目标节点引用
     * @return 出现次数
     */
    private int countDirectChildReferences(ElementNode parent, DocumentNode expected) {
        int count = 0;
        for (DocumentNode child : parent.getChildren()) {
            if (child == expected) {
                count++;
            }
        }
        return count;
    }

    /**
     * 描述节点，供运行时结果和失败摘要展示。
     *
     * @param node 节点
     * @return 可读节点描述
     */
    private String describeNode(DocumentNode node) {
        if (node == null) {
            return "null";
        }
        if (node instanceof DocumentFragmentNode) {
            return "fragment";
        }
        if (node instanceof TextNode) {
            return ((TextNode) node).getText();
        }
        String text = node.getTextContent();
        return text == null || text.length() == 0 ? node.getClass().getSimpleName() : text;
    }

    /**
     * 构建期望/实际差异文本。
     *
     * @param expected 期望摘要
     * @param actual 实际摘要
     * @return 差异文本
     */
    private String buildExpectedActualDifference(String expected, String actual) {
        return "期望：" + expected + "；实际：" + actual;
    }

    /**
     * 将布尔匹配结果格式化为计数样式。
     *
     * @param value 是否匹配
     * @return `1/1` 或 `0/1`
     */
    private String boolCount(boolean value) {
        return value ? "1/1" : "0/1";
    }

    /**
     * 构建 DOM 交互状态摘要。
     *
     * @param interactive 交互演示元素
     * @return 交互状态摘要
     */
    private String buildDomPseudoInteractionSummary(ElementNode interactive) {
        return "交互状态：hover=" + interactive.getAttribute("data-hover")
                + "；active=" + interactive.getAttribute("data-active")
                + "；focusVisible=" + interactive.getAttribute("data-focus-visible")
                + "；结构伪类已由自动断言校验";
    }

    /**
     * 在布局树中查找指定元素布局盒。
     *
     * @param rootBox 根布局盒
     * @param element 目标元素
     * @return 目标布局盒
     */
    private DocumentLayoutBox findRequiredLayoutBox(DocumentLayoutBox rootBox, ElementNode element) {
        DocumentLayoutBox found = findLayoutBox(rootBox, element);
        if (found == null) {
            throw new IllegalStateException("未找到布局盒：" + element.getTextContent());
        }
        return found;
    }

    /**
     * 在布局树中递归查找指定元素布局盒。
     *
     * @param rootBox 根布局盒
     * @param element 目标元素
     * @return 目标布局盒；不存在时返回 null
     */
    private DocumentLayoutBox findLayoutBox(DocumentLayoutBox rootBox, ElementNode element) {
        if (rootBox.getElement() == element) {
            return rootBox;
        }
        for (DocumentLayoutBox child : rootBox.getChildren()) {
            DocumentLayoutBox found = findLayoutBox(child, element);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 创建标准卡片。
     *
     * @param document 文档实例
     * @param minWidth 最小宽度
     * @param flexGrow flex-grow
     * @param flexShrink flex-shrink
     * @return 标准卡片
     */
    private ElementNode createCard(UiDocument document, int minWidth, float flexGrow, float flexShrink) {
        ElementNode card = document.div();
        card.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6))
                .setFlexGrow(flexGrow)
                .setFlexShrink(flexShrink)
                .setMinWidth(UiStyleLength.px(minWidth))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF1D2A44)
                .setBorderColor(0xFF334B7A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10));
        return card;
    }

    /**
     * 追加单条说明。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param text 条目文本
     */
    private void appendPlanItem(UiDocument document, ElementNode parent, String text) {
        ElementNode item = createPlanItem(document);
        item.appendText(text);
        parent.append(item);
    }

    /**
     * 创建说明条目容器。
     *
     * @param document 文档实例
     * @return 说明条目容器
     */
    private ElementNode createPlanItem(UiDocument document) {
        ElementNode item = document.div();
        item.style()
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF1D2A44)
                .setBorderColor(0xFF334B7A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10))
                .setTextColor(0xFFEAF1FF);
        return item;
    }

    /**
     * 追加弱化说明文本。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param text 文本
     */
    private void appendMutedText(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style()
                .setTextColor(0xFFC9D8F8);
        line.appendText(text);
        parent.append(line);
    }

    /**
     * 统计指定状态数量。
     *
     * @param status 状态
     * @return 状态数量
     */
    private int countStatus(RuntimeTestStatus status) {
        int count = 0;
        for (RuntimeTestCase testCase : runtimeTestCases) {
            if (testCase.getResult().getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计待人工确认数量。
     *
     * @return 待人工确认数量
     */
    private int countManualPending() {
        int count = 0;
        for (RuntimeTestCase testCase : runtimeTestCases) {
            if (testCase.getResult().getStatus() == RuntimeTestStatus.NOT_EXECUTED
                    || testCase.getResult().getStatus() == RuntimeTestStatus.RUNNING) {
                count++;
            }
        }
        return count;
    }

    /**
     * 刷新环境信息文本。
     */
    private void refreshEnvironmentText() {
        if (environmentText != null) {
            environmentText.setText(buildEnvironmentText());
        }
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

    /**
     * 格式化纳秒时长为毫秒摘要。
     *
     * @param nanos 纳秒时长
     * @return 毫秒摘要
     */
    private String formatDurationMillis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.0fms", Double.valueOf(nanos / 1_000_000.0D));
    }

    /**
     * 测试分组模型。
     */
    private static final class TestGroup {

        private final String code;
        private final String title;
        private final String coverage;
        private final int totalCaseCount;
        private final int implementedCaseCount;

        /**
         * 创建测试分组模型。
         *
         * @param code 分组代码
         * @param title 分组标题
         * @param coverage 覆盖范围
         * @param totalCaseCount 规格用例总数
         * @param implementedCaseCount P0 已接入数量
         */
        private TestGroup(String code, String title, String coverage, int totalCaseCount, int implementedCaseCount) {
            this.code = code;
            this.title = title;
            this.coverage = coverage;
            this.totalCaseCount = totalCaseCount;
            this.implementedCaseCount = implementedCaseCount;
        }

        /**
         * 返回分组代码。
         *
         * @return 分组代码
         */
        private String getCode() {
            return code;
        }

        /**
         * 返回分组标题。
         *
         * @return 分组标题
         */
        private String getTitle() {
            return title;
        }

        /**
         * 返回覆盖范围。
         *
         * @return 覆盖范围
         */
        private String getCoverage() {
            return coverage;
        }

        /**
         * 返回规格用例总数。
         *
         * @return 规格用例总数
         */
        private int getTotalCaseCount() {
            return totalCaseCount;
        }

        /**
         * 返回 P0 已接入数量。
         *
         * @return 已接入数量
         */
        private int getImplementedCaseCount() {
            return implementedCaseCount;
        }

        /**
         * 返回剩余缺口数量。
         *
         * @return 剩余缺口数量
         */
        private int getGapCount() {
            return Math.max(0, totalCaseCount - implementedCaseCount);
        }

    }

    /**
     * 运行时自动断言使用的无副作用渲染上下文。
     *
     * <p>它只提供布局后自定义 renderer 所需的文本测量能力，所有绘制动作均为空实现，
     * 避免 JVM 测试把游戏内 GL 调用当作断言前提。</p>
     */
    private static final class RuntimeAssertionRenderContext extends UiRenderContext {

        private final TextMeasureService textMeasureService;

        /**
         * 创建运行时断言渲染上下文。
         *
         * @param screenWidth 屏幕宽度
         * @param screenHeight 屏幕高度
         * @param textMeasureService 文本测量服务
         */
        private RuntimeAssertionRenderContext(int screenWidth, int screenHeight,
                TextMeasureService textMeasureService) {
            super(screenWidth, screenHeight, 0, 0, 0.0F);
            this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
        }

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {}

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {}

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow,
                TextContentMode textContentMode, UiFontWeight fontWeight, UiFontStyle fontStyle) {}

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {}

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {}

        @Override
        public void drawHostImage(HostImageSource source, int left, int top, int right, int bottom) {}

        @Override
        public int measureTextWidth(String text, TextContentMode textContentMode) {
            return textMeasureService.getStringWidth(text);
        }

        @Override
        public int getTextLineHeight() {
            return textMeasureService.getLineHeight();
        }

        @Override
        public boolean supportsDeferredTextBatching() {
            return false;
        }

        @Override
        public void pushPaintContext(int left, int top, int right, int bottom, float opacity) {}

        @Override
        public void popPaintContext() {}

        @Override
        public void pushTransform(UiTransform transform, int left, int top, int right, int bottom) {}

        @Override
        public void popTransform() {}

        @Override
        public void pushClip(int left, int top, int right, int bottom,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {}

        @Override
        public void popClip() {}
    }

    /**
     * 运行时 Tab 样例的静态内容构建器。
     */
    private static final class DocumentTabContentBuilderImpl implements DocumentTabContentBuilder {

        private final String text;

        /**
         * 创建内容构建器。
         *
         * @param text 面板文本
         */
        private DocumentTabContentBuilderImpl(String text) {
            this.text = text;
        }

        @Override
        public void build(ElementNode panel, UiDocument document) {
            panel.append(createStaticPanel(document, text));
        }

        private static ElementNode createStaticPanel(UiDocument document, String text) {
            ElementNode panel = document.div();
            panel.style()
                    .setPadding(UiStyleLength.px(6))
                    .setBackgroundColor(0xFF1F2937)
                    .setTextColor(0xFFEAF1FF);
            panel.appendText(text);
            return panel;
        }
    }

    /**
     * 运行时测试用例模型。
     */
    private static final class RuntimeTestCase {

        private final String id;
        private final TestGroup group;
        private final String semantic;
        private final String automaticAssertion;
        private final String steps;
        private final String expectedResult;
        private RuntimeTestResult result;
        private TextNode actualResultText;
        private TextNode statusText;
        private TextNode demoSummaryText;
        private ElementNode domParent;
        private ElementNode domNodeA;
        private ElementNode domNodeB;
        private ElementNode cssTarget;
        private ElementNode layoutStack;
        private ElementNode paintSample;
        private ElementNode demoRoot;
        private ElementNode[] demoElements = new ElementNode[0];
        private DocumentButtonControl[] demoButtonControls = new DocumentButtonControl[0];
        private DocumentTextInputControl demoTextInputControl;
        private DocumentTextAreaControl demoTextAreaControl;

        /**
         * 创建运行时测试用例模型。
         *
         * @param id 用例编号
         * @param group 所属分组
         * @param semantic 覆盖语义
         * @param automaticAssertion 自动断言说明
         * @param steps 操作步骤
         * @param expectedResult 预期结果文本
         */
        private RuntimeTestCase(String id, TestGroup group, String semantic, String automaticAssertion, String steps,
                String expectedResult) {
            this.id = Objects.requireNonNull(id, "id");
            this.group = Objects.requireNonNull(group, "group");
            this.semantic = Objects.requireNonNull(semantic, "semantic");
            this.automaticAssertion = Objects.requireNonNull(automaticAssertion, "automaticAssertion");
            this.steps = Objects.requireNonNull(steps, "steps");
            if (expectedResult == null || !expectedResult.startsWith("预期结果：")) {
                throw new IllegalArgumentException("expectedResult must start with 预期结果：");
            }
            this.expectedResult = expectedResult;
            this.result = RuntimeTestResult.notExecuted();
        }

        /**
         * 返回用例编号。
         *
         * @return 用例编号
         */
        private String getId() {
            return id;
        }

        /**
         * 返回所属分组。
         *
         * @return 所属分组
         */
        private TestGroup getGroup() {
            return group;
        }

        /**
         * 返回覆盖语义。
         *
         * @return 覆盖语义
         */
        private String getSemantic() {
            return semantic;
        }

        /**
         * 返回自动断言说明。
         *
         * @return 自动断言说明
         */
        private String getAutomaticAssertion() {
            return automaticAssertion;
        }

        /**
         * 返回操作步骤。
         *
         * @return 操作步骤
         */
        private String getSteps() {
            return steps;
        }

        /**
         * 返回预期结果文本。
         *
         * @return 预期结果文本
         */
        private String getExpectedResult() {
            return expectedResult;
        }

        /**
         * 返回当前结果。
         *
         * @return 当前结果
         */
        private RuntimeTestResult getResult() {
            return result;
        }

        /**
         * 设置当前结果。
         *
         * @param result 当前结果
         */
        private void setResult(RuntimeTestResult result) {
            this.result = Objects.requireNonNull(result, "result");
        }

        /**
         * 返回实际结果文本节点。
         *
         * @return 实际结果文本节点
         */
        private TextNode getActualResultText() {
            return actualResultText;
        }

        /**
         * 设置实际结果文本节点。
         *
         * @param actualResultText 实际结果文本节点
         */
        private void setActualResultText(TextNode actualResultText) {
            this.actualResultText = actualResultText;
        }

        /**
         * 返回状态文本节点。
         *
         * @return 状态文本节点
         */
        private TextNode getStatusText() {
            return statusText;
        }

        /**
         * 设置状态文本节点。
         *
         * @param statusText 状态文本节点
         */
        private void setStatusText(TextNode statusText) {
            this.statusText = statusText;
        }

        /**
         * 清理当前页面文本和演示节点绑定。
         */
        private void clearViewBindings() {
            actualResultText = null;
            statusText = null;
            demoSummaryText = null;
            domParent = null;
            domNodeA = null;
            domNodeB = null;
            cssTarget = null;
            layoutStack = null;
            paintSample = null;
            demoRoot = null;
            demoElements = new ElementNode[0];
            demoButtonControls = new DocumentButtonControl[0];
            demoTextInputControl = null;
            demoTextAreaControl = null;
        }

        /**
         * 设置 DOM 演示节点。
         *
         * @param domParent 父容器
         * @param domNodeA A 节点
         * @param domNodeB B 节点
         * @param demoSummaryText 摘要文本节点
         */
        private void setDomDemo(ElementNode domParent, ElementNode domNodeA, ElementNode domNodeB,
                TextNode demoSummaryText) {
            this.domParent = domParent;
            this.domNodeA = domNodeA;
            this.domNodeB = domNodeB;
            this.demoSummaryText = demoSummaryText;
        }

        /**
         * 设置 CSS 演示节点。
         *
         * @param cssTarget 目标元素
         * @param demoSummaryText 摘要文本节点
         */
        private void setCssDemo(ElementNode cssTarget, TextNode demoSummaryText) {
            this.cssTarget = cssTarget;
            this.demoSummaryText = demoSummaryText;
        }

        /**
         * 设置 Layout 演示节点。
         *
         * @param layoutStack 布局容器
         * @param demoSummaryText 摘要文本节点
         */
        private void setLayoutDemo(ElementNode layoutStack, TextNode demoSummaryText) {
            this.layoutStack = layoutStack;
            this.demoSummaryText = demoSummaryText;
        }

        /**
         * 设置 Paint 演示节点。
         *
         * @param paintSample 绘制样例
         * @param demoSummaryText 摘要文本节点
         */
        private void setPaintDemo(ElementNode paintSample, TextNode demoSummaryText) {
            this.paintSample = paintSample;
            this.demoSummaryText = demoSummaryText;
        }

        /**
         * 设置通用演示节点。
         *
         * @param demoRoot 演示根节点
         * @param demoSummaryText 摘要文本节点
         * @param demoElements 演示关键元素
         */
        private void setElementDemo(ElementNode demoRoot, TextNode demoSummaryText, ElementNode... demoElements) {
            this.demoRoot = demoRoot;
            this.demoSummaryText = demoSummaryText;
            this.demoElements = demoElements == null ? new ElementNode[0] : demoElements;
        }

        /**
         * 设置按钮控件演示节点。
         *
         * @param demoRoot 演示根节点
         * @param demoSummaryText 摘要文本节点
         * @param buttonControls 页面上的按钮控件实例
         */
        private void setButtonDemo(ElementNode demoRoot, TextNode demoSummaryText,
                DocumentButtonControl... buttonControls) {
            this.demoRoot = demoRoot;
            this.demoSummaryText = demoSummaryText;
            this.demoButtonControls = buttonControls == null ? new DocumentButtonControl[0] : buttonControls;
            this.demoElements = new ElementNode[this.demoButtonControls.length];
            for (int index = 0; index < this.demoButtonControls.length; index++) {
                this.demoElements[index] = this.demoButtonControls[index].getElement();
            }
        }

        /**
         * 设置文本输入控件演示节点。
         *
         * @param demoRoot 演示根节点
         * @param demoSummaryText 摘要文本节点
         * @param textInputControl 页面上的文本输入控件实例
         */
        private void setTextInputDemo(ElementNode demoRoot, TextNode demoSummaryText,
                DocumentTextInputControl textInputControl) {
            this.demoRoot = demoRoot;
            this.demoSummaryText = demoSummaryText;
            this.demoTextInputControl = textInputControl;
            this.demoElements = textInputControl == null ? new ElementNode[0]
                    : new ElementNode[] {textInputControl.getElement()};
        }

        /**
         * 设置 textarea 控件演示节点。
         *
         * @param demoRoot 演示根节点
         * @param demoSummaryText 摘要文本节点
         * @param textAreaControl 页面上的 textarea 控件实例
         */
        private void setTextAreaDemo(ElementNode demoRoot, TextNode demoSummaryText,
                DocumentTextAreaControl textAreaControl) {
            this.demoRoot = demoRoot;
            this.demoSummaryText = demoSummaryText;
            this.demoTextAreaControl = textAreaControl;
            this.demoElements = textAreaControl == null ? new ElementNode[0]
                    : new ElementNode[] {textAreaControl.getElement()};
        }

        /**
         * 更新演示摘要。
         *
         * @param text 摘要文本
         */
        private void updateDemoSummary(String text) {
            if (demoSummaryText != null) {
                demoSummaryText.setText(text);
            }
        }

        /**
         * 返回 DOM 演示父容器。
         *
         * @return DOM 演示父容器
         */
        private ElementNode getDomParent() {
            return domParent;
        }

        /**
         * 返回 DOM 演示 A 节点。
         *
         * @return A 节点
         */
        private ElementNode getDomNodeA() {
            return domNodeA;
        }

        /**
         * 返回 DOM 演示 B 节点。
         *
         * @return B 节点
         */
        private ElementNode getDomNodeB() {
            return domNodeB;
        }

        /**
         * 返回 CSS 目标元素。
         *
         * @return CSS 目标元素
         */
        private ElementNode getCssTarget() {
            return cssTarget;
        }

        /**
         * 返回布局演示容器。
         *
         * @return 布局演示容器
         */
        private ElementNode getLayoutStack() {
            return layoutStack;
        }

        /**
         * 返回绘制样例元素。
         *
         * @return 绘制样例元素
         */
        private ElementNode getPaintSample() {
            return paintSample;
        }

        /**
         * 返回通用演示根节点。
         *
         * @return 演示根节点
         */
        private ElementNode getDemoRoot() {
            return demoRoot;
        }

        /**
         * 返回通用演示关键元素。
         *
         * @param index 元素下标
         * @return 演示关键元素
         */
        private ElementNode getDemoElement(int index) {
            if (index < 0 || index >= demoElements.length) {
                throw new IllegalStateException("缺少演示元素：" + id + " #" + index);
            }
            return Objects.requireNonNull(demoElements[index], "demoElement");
        }

        /**
         * 返回页面上的按钮控件实例。
         *
         * @param index 控件下标
         * @return 按钮控件实例
         */
        private DocumentButtonControl getDemoButtonControl(int index) {
            if (index < 0 || index >= demoButtonControls.length) {
                throw new IllegalStateException("缺少按钮演示控件：" + id + " #" + index);
            }
            return Objects.requireNonNull(demoButtonControls[index], "demoButtonControl");
        }

        /**
         * 返回页面上的文本输入控件实例。
         *
         * @return 文本输入控件实例
         */
        private DocumentTextInputControl getDemoTextInputControl() {
            if (demoTextInputControl == null) {
                throw new IllegalStateException("缺少文本输入演示控件：" + id);
            }
            return demoTextInputControl;
        }

        /**
         * 返回页面上的 textarea 控件实例。
         *
         * @return textarea 控件实例
         */
        private DocumentTextAreaControl getDemoTextAreaControl() {
            if (demoTextAreaControl == null) {
                throw new IllegalStateException("缺少 textarea 演示控件：" + id);
            }
            return demoTextAreaControl;
        }
    }

    /**
     * 运行时测试结果模型。
     */
    private static final class RuntimeTestResult {

        private final RuntimeTestStatus status;
        private final String actualResult;
        private final String difference;

        /**
         * 创建未执行结果。
         *
         * @return 未执行结果
         */
        private static RuntimeTestResult notExecuted() {
            return new RuntimeTestResult(RuntimeTestStatus.NOT_EXECUTED, "尚未执行。", "");
        }

        /**
         * 创建执行中结果。
         *
         * @param actualResult 实际结果文本
         * @return 执行中结果
         */
        private static RuntimeTestResult running(String actualResult) {
            return new RuntimeTestResult(RuntimeTestStatus.RUNNING, actualResult, "");
        }

        /**
         * 创建通过结果。
         *
         * @param actualResult 实际结果文本
         * @return 通过结果
         */
        private static RuntimeTestResult passed(String actualResult) {
            return new RuntimeTestResult(RuntimeTestStatus.PASSED, actualResult, "");
        }

        /**
         * 创建失败结果。
         *
         * @param actualResult 实际结果文本
         * @param difference 差异说明
         * @return 失败结果
         */
        private static RuntimeTestResult failed(String actualResult, String difference) {
            return new RuntimeTestResult(RuntimeTestStatus.FAILED, actualResult, difference);
        }

        /**
         * 创建运行时测试结果。
         *
         * @param status 状态
         * @param actualResult 实际结果文本
         * @param difference 差异说明
         */
        private RuntimeTestResult(RuntimeTestStatus status, String actualResult, String difference) {
            this.status = Objects.requireNonNull(status, "status");
            this.actualResult = actualResult == null || actualResult.length() == 0 ? "尚未记录。" : actualResult;
            this.difference = difference == null ? "" : difference;
        }

        /**
         * 返回状态。
         *
         * @return 状态
         */
        private RuntimeTestStatus getStatus() {
            return status;
        }

        /**
         * 返回实际结果文本。
         *
         * @return 实际结果文本
         */
        private String getActualResult() {
            return actualResult;
        }

        /**
         * 返回状态展示文本。
         *
         * @return 状态展示文本
         */
        private String getStatusText() {
            return status.toDisplayText(difference);
        }
    }

    /**
     * 运行时测试固定状态。
     */
    private enum RuntimeTestStatus {
        NOT_EXECUTED,
        RUNNING,
        PASSED,
        FAILED;

        /**
         * 转为页面展示文本。
         *
         * @param difference 失败差异说明
         * @return 页面展示文本
         */
        private String toDisplayText(String difference) {
            if (this == NOT_EXECUTED) {
                return STATUS_NOT_EXECUTED;
            }
            if (this == RUNNING) {
                return STATUS_RUNNING;
            }
            if (this == PASSED) {
                return STATUS_PASSED;
            }
            String detail = difference == null || difference.length() == 0 ? "<差异说明>" : difference;
            return STATUS_FAILED_PREFIX + detail;
        }
    }
}
