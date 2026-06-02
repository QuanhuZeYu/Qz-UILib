package club.heiqi.uilib.internal.devtools.pages;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.net.transport.NetTransportFactory;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
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
            13,
            1);
    private static final TestGroup CSS_GROUP = new TestGroup(
            "CSS",
            "CSS 级联与样式语义",
            "级联优先级、继承、盒模型、背景、边框、阴影、文本样式与可见性。",
            15,
            1);
    private static final TestGroup LAYOUT_GROUP = new TestGroup(
            "LAYOUT",
            "Layout 布局与尺寸语义",
            "block、inline、flex、table、position、sticky、fixed containing block 与滚动范围。",
            16,
            1);
    private static final TestGroup PAINT_GROUP = new TestGroup(
            "PAINT",
            "Paint 绘制、命中与视觉语义",
            "绘制层级、stacking context、clip、transform 命中、top-layer、scrollbar 与 host image。",
            9,
            1);
    private static final List<TestGroup> P0_GROUPS = Collections.unmodifiableList(Arrays.asList(
            DOM_GROUP,
            CSS_GROUP,
            LAYOUT_GROUP,
            PAINT_GROUP));
    private final DocumentPageAuthoringSurface diagnosticPage;
    private final DocumentPageRuntimeView runtimeView;
    private final TextMeasureService textMeasureService;
    private final List<RuntimeTestCase> runtimeTestCases;
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

        UiDocument document = UiDocument.create();
        document.setDefaultTextContentMode(resolvedDocumentUi.getDefaultTextContentMode());
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 760, 520,
                textMeasureService);
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        createHomeDocument(document, document.getRootElement());
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
        document.addStyleSheet(createRuntimeDemoStyleSheet());
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

        appendHero(document, root);
        appendOverview(document, root);
        appendGroupIndex(document, root);
        appendCaseCardSection(document, root);
        appendFailureAndManualSections(document, root);
        appendEnvironmentSection(document, root);
        appendContractSection(document, root);
    }

    /**
     * 创建首页首批运行时用例。
     *
     * @return 运行时用例列表
     */
    private List<RuntimeTestCase> createRuntimeTestCases() {
        return Collections.unmodifiableList(Arrays.asList(
                new RuntimeTestCase(
                        "DOM-001",
                        DOM_GROUP,
                        "appendChild 返回插入节点并移动已有节点",
                        "运行时按钮会执行 appendChild 移动断言，并校验返回节点与最终顺序。",
                        "点击 `执行自动测试`；观察 A 节点移动到 B 后方；需要时点击人工通过或人工失败。",
                        "预期结果：点击执行后 A 节点移动到 B 节点后方，页面显示 `返回节点：A`。"),
                new RuntimeTestCase(
                        "CSS-001",
                        CSS_GROUP,
                        "inline style 高于样式表规则",
                        "运行时按钮会计算目标元素 computed style，并校验 inline textColor 覆盖样式表规则。",
                        "点击 `执行自动测试`；观察样例文本显示为 inline 指定绿色；需要时点击人工通过或人工失败。",
                        "预期结果：同一元素最终显示为 inline 指定颜色。"),
                new RuntimeTestCase(
                        "LAYOUT-001",
                        LAYOUT_GROUP,
                        "block normal flow 垂直布局",
                        "运行时按钮会用 DocumentLayoutEngine 测量三块 block 的 top/height 顺序。",
                        "点击 `执行自动测试`；观察三块内容自上而下排列；需要时点击人工通过或人工失败。",
                        "预期结果：三块内容从上到下排列，垂直间距与标尺一致。"),
                new RuntimeTestCase(
                        "PAINT-001",
                        PAINT_GROUP,
                        "background、border、text 绘制顺序",
                        "运行时按钮会布置绘制样例并校验背景、边框、文本结构；最终层级需要人工确认。",
                        "点击 `执行自动测试`；观察背景、边框和文本层级；再点击人工通过或人工失败记录结果。",
                        "预期结果：背景在最底层，边框压住背景边缘，文本位于最上层。")));
    }

    /**
     * 创建运行时样例样式表。
     *
     * @return 样式表
     */
    private UiStyleSheet createRuntimeDemoStyleSheet() {
        return UiStyleSheet.create()
                .addRule(".css-001-target", new UiStyleDeclaration()
                        .setTextColor(CSS_STYLESHEET_TEXT_COLOR));
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
        body.appendText("P0 阶段建立按语义分组的 test 首页、运行时测试结果模型和统一用例卡片，不恢复旧页面结构。");
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
        appendMetricCard(document, grid, "分组入口数", String.valueOf(P0_GROUPS.size()));
        section.append(grid);
        appendPlanItem(document, section, "当前首页只显示 DOM / CSS / Layout / Paint 语义分组入口；Input、Controls、TextFont、Animation、RuntimeHost、RemoteNet 按规格后续接入。");
        appendPlanItem(document, section, "每个首批卡片已接入 `执行自动测试`、`人工通过`、`人工失败` 操作，状态文本只允许使用固定四类格式。");
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
        for (TestGroup group : P0_GROUPS) {
            appendGroupCard(document, grid, group);
        }
        section.append(grid);
        root.append(section);
    }

    /**
     * 追加统一用例卡片区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendCaseCardSection(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "运行时用例卡片");
        appendPlanItem(document, section, "以下卡片已接入首页运行时操作；自动测试失败或视觉不一致时，可用人工失败保留差异文本。");
        for (RuntimeTestCase testCase : runtimeTestCases) {
            appendRuntimeCaseCard(document, section, testCase);
        }
        root.append(section);
    }

    /**
     * 追加最近失败与人工任务区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendFailureAndManualSections(UiDocument document, ElementNode root) {
        ElementNode failures = createSection(document, "最近失败");
        ElementNode failureItem = createPlanItem(document);
        failureSummaryText = failureItem.appendText(buildFailureSummaryText());
        failures.append(failureItem);
        root.append(failures);

        ElementNode manual = createSection(document, "人工任务");
        for (RuntimeTestCase testCase : runtimeTestCases) {
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
        appendPlanItem(document, section, "固定字段：用例编号、覆盖语义、自动断言、操作步骤、预期结果、实际结果、状态。");
        appendPlanItem(document, section, "固定状态：`" + STATUS_NOT_EXECUTED + "`、`" + STATUS_RUNNING + "`、`" + STATUS_PASSED + "`、`" + STATUS_FAILED_PREFIX + "<差异说明>`。");
        appendPlanItem(document, section, "页面主结构只按 DOM、CSS、Layout、Paint 等语义分组组织，不按旧页面名组织。");
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
    private void appendGroupCard(UiDocument document, ElementNode parent, TestGroup group) {
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
        appendMutedText(document, card, "入口状态：已建立分组索引，分组页待 P1 恢复。");
        parent.append(card);
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
        if ("DOM-001".equals(testCase.getId())) {
            appendDomRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("CSS-001".equals(testCase.getId())) {
            appendCssRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("LAYOUT-001".equals(testCase.getId())) {
            appendLayoutRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("PAINT-001".equals(testCase.getId())) {
            appendPaintRuntimeDemo(document, parent, testCase);
        }
    }

    /**
     * 追加 DOM-001 演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendDomRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode row = createRuntimeDemoRow(document);
        ElementNode nodeA = createDemoBadge(document, "A", 0xFF355CFF);
        ElementNode nodeB = createDemoBadge(document, "B", 0xFF1F9D55);
        row.append(nodeA);
        row.append(nodeB);
        demo.append(row);
        TextNode summary = appendDemoSummary(document, demo, "初始顺序：A, B；返回节点：未执行");
        testCase.setDomDemo(row, nodeA, nodeB, summary);
        parent.append(demo);
    }

    /**
     * 追加 CSS-001 演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendCssRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode target = document.div();
        target.setClassName("css-001-target");
        target.style()
                .setPadding(UiStyleLength.px(8))
                .setBorderColor(0xFF5B76B7)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(CSS_INLINE_TEXT_COLOR);
        target.appendText("CSS-001 inline 色样例");
        demo.append(target);
        TextNode summary = appendDemoSummary(document, demo, "样式表颜色=红色；inline 颜色=绿色；当前=未计算");
        testCase.setCssDemo(target, summary);
        parent.append(demo);
    }

    /**
     * 追加 LAYOUT-001 演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendLayoutRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode stack = document.div();
        stack.style()
                .setBackgroundColor(0xFF0B1220)
                .setBorderColor(0xFF3F5F9F)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(6));
        ElementNode first = createLayoutDemoBlock(document, "一", 0xFF2563EB);
        ElementNode second = createLayoutDemoBlock(document, "二", 0xFF7C3AED);
        ElementNode third = createLayoutDemoBlock(document, "三", 0xFF059669);
        stack.append(first);
        stack.append(second);
        stack.append(third);
        demo.append(stack);
        TextNode summary = appendDemoSummary(document, demo, "布局顺序：未测量");
        testCase.setLayoutDemo(stack, summary);
        parent.append(demo);
    }

    /**
     * 追加 PAINT-001 演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendPaintRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode sample = document.div();
        sample.style()
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF1D4ED8)
                .setBorderColor(0xFFFFF176)
                .setBorderWidth(UiStyleLength.px(4))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFFFFFFF)
                .setFontWeight(UiFontWeight.BOLD);
        sample.appendText("文本位于最上层");
        demo.append(sample);
        TextNode summary = appendDemoSummary(document, demo, "绘制样例：蓝色背景 / 黄色边框 / 白色文本；结构未校验");
        testCase.setPaintDemo(sample, summary);
        parent.append(demo);
    }

    /**
     * 追加运行时操作按钮。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendRuntimeActions(UiDocument document, ElementNode parent, final RuntimeTestCase testCase) {
        ElementNode actions = createGrid(document);
        actions.append(createActionButton(document, "执行自动测试", 0xFF2563EB, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                executeRuntimeTest(testCase);
            }
        }));
        actions.append(createActionButton(document, "人工通过", 0xFF059669, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                markRuntimeTestPassed(testCase, "人工确认：观察结果与预期一致。");
            }
        }));
        actions.append(createActionButton(document, "人工失败", 0xFFB91C1C, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                markRuntimeTestFailed(testCase, "人工确认：观察结果与预期不一致，请截图补充差异。");
            }
        }));
        parent.append(actions);
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
        applyRuntimeTestResult(testCase, RuntimeTestResult.running("自动测试正在执行。"));
        try {
            if ("DOM-001".equals(testCase.getId())) {
                applyRuntimeTestResult(testCase, executeDomRuntimeTest(testCase));
                return;
            }
            if ("CSS-001".equals(testCase.getId())) {
                applyRuntimeTestResult(testCase, executeCssRuntimeTest(testCase));
                return;
            }
            if ("LAYOUT-001".equals(testCase.getId())) {
                applyRuntimeTestResult(testCase, executeLayoutRuntimeTest(testCase));
                return;
            }
            if ("PAINT-001".equals(testCase.getId())) {
                applyRuntimeTestResult(testCase, executePaintRuntimeTest(testCase));
                return;
            }
            applyRuntimeTestResult(testCase, RuntimeTestResult.failed("未知用例，未执行。", "没有匹配的执行器"));
        } catch (RuntimeException e) {
            applyRuntimeTestResult(testCase, RuntimeTestResult.failed("自动测试异常：" + e.getMessage(),
                    e.getClass().getSimpleName()));
        }
    }

    /**
     * 执行 DOM-001 运行时断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeDomRuntimeTest(RuntimeTestCase testCase) {
        ElementNode row = Objects.requireNonNull(testCase.getDomParent(), "domParent");
        ElementNode nodeA = Objects.requireNonNull(testCase.getDomNodeA(), "domNodeA");
        ElementNode nodeB = Objects.requireNonNull(testCase.getDomNodeB(), "domNodeB");
        if (nodeA.getParent() != row) {
            row.append(nodeA);
        }
        if (nodeB.getParent() != row) {
            row.append(nodeB);
        }
        DocumentNode returnedNode = row.appendChild(nodeA);
        List<DocumentNode> children = row.getChildren();
        boolean passed = returnedNode == nodeA && children.size() == 2 && children.get(0) == nodeB
                && children.get(1) == nodeA;
        String summary = "当前顺序：B, A；返回节点：A";
        testCase.updateDemoSummary(summary);
        return passed ? RuntimeTestResult.passed(summary)
                : RuntimeTestResult.failed(summary, "DOM 顺序不是 B, A");
    }

    /**
     * 执行 CSS-001 运行时断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeCssRuntimeTest(RuntimeTestCase testCase) {
        ElementNode target = Objects.requireNonNull(testCase.getCssTarget(), "cssTarget");
        ComputedStyle computedStyle = UiStyleResolver.compute(target);
        int actualColor = computedStyle.getTextColor();
        String summary = "computed textColor=" + formatColor(actualColor)
                + "；inline=" + formatColor(CSS_INLINE_TEXT_COLOR)
                + "；样式表=" + formatColor(CSS_STYLESHEET_TEXT_COLOR);
        testCase.updateDemoSummary(summary);
        return actualColor == CSS_INLINE_TEXT_COLOR ? RuntimeTestResult.passed(summary)
                : RuntimeTestResult.failed(summary, "computed textColor 未使用 inline 声明");
    }

    /**
     * 执行 LAYOUT-001 运行时断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeLayoutRuntimeTest(RuntimeTestCase testCase) {
        ElementNode stack = Objects.requireNonNull(testCase.getLayoutStack(), "layoutStack");
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(stack, 260, 0, textMeasureService);
        List<DocumentLayoutBox> children = rootBox.getChildren();
        if (children.size() < 3) {
            String summary = "布局顺序：只测得 " + children.size() + " 个块";
            testCase.updateDemoSummary(summary);
            return RuntimeTestResult.failed(summary, "缺少三块 block 布局盒");
        }
        DocumentLayoutBox first = children.get(0);
        DocumentLayoutBox second = children.get(1);
        DocumentLayoutBox third = children.get(2);
        boolean stacked = first.getTop() < second.getTop() && second.getTop() < third.getTop()
                && first.getHeight() > 0 && second.getHeight() > 0 && third.getHeight() > 0;
        String summary = "布局顺序：top=" + first.getTop() + "," + second.getTop() + "," + third.getTop()
                + "；height=" + first.getHeight() + "," + second.getHeight() + "," + third.getHeight();
        testCase.updateDemoSummary(summary);
        return stacked ? RuntimeTestResult.passed(summary)
                : RuntimeTestResult.failed(summary, "block top 未严格递增");
    }

    /**
     * 执行 PAINT-001 运行时结构断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executePaintRuntimeTest(RuntimeTestCase testCase) {
        ElementNode sample = Objects.requireNonNull(testCase.getPaintSample(), "paintSample");
        ComputedStyle computedStyle = UiStyleResolver.compute(sample);
        boolean structurePassed = computedStyle.getBackgroundColor() == 0xFF1D4ED8
                && computedStyle.getBorderColor() == 0xFFFFF176
                && computedStyle.getBorderStyle() == UiBorderStyle.SOLID
                && computedStyle.getTextColor() == 0xFFFFFFFF
                && sample.getChildCount() > 0;
        String summary = "绘制结构：背景=" + formatColor(computedStyle.getBackgroundColor())
                + "；边框=" + formatColor(computedStyle.getBorderColor())
                + "；文本=" + formatColor(computedStyle.getTextColor())
                + "；等待人工确认层级";
        testCase.updateDemoSummary(summary);
        return structurePassed ? RuntimeTestResult.running(summary)
                : RuntimeTestResult.failed(summary, "绘制样例结构与预期不一致");
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
        return "暂无失败用例；失败状态将显示为 `失败：观察结果与预期不一致 - <差异说明>`，并保留用例编号用于交接。";
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
         * 设置 DOM-001 演示节点。
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
         * 设置 CSS-001 演示节点。
         *
         * @param cssTarget 目标元素
         * @param demoSummaryText 摘要文本节点
         */
        private void setCssDemo(ElementNode cssTarget, TextNode demoSummaryText) {
            this.cssTarget = cssTarget;
            this.demoSummaryText = demoSummaryText;
        }

        /**
         * 设置 LAYOUT-001 演示节点。
         *
         * @param layoutStack 布局容器
         * @param demoSummaryText 摘要文本节点
         */
        private void setLayoutDemo(ElementNode layoutStack, TextNode demoSummaryText) {
            this.layoutStack = layoutStack;
            this.demoSummaryText = demoSummaryText;
        }

        /**
         * 设置 PAINT-001 演示节点。
         *
         * @param paintSample 绘制样例
         * @param demoSummaryText 摘要文本节点
         */
        private void setPaintDemo(ElementNode paintSample, TextNode demoSummaryText) {
            this.paintSample = paintSample;
            this.demoSummaryText = demoSummaryText;
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
