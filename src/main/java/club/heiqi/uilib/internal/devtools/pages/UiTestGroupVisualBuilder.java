package club.heiqi.uilib.internal.devtools.pages;

import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `/qzuilib test` 分组视觉页面构建器。
 */
final class UiTestGroupVisualBuilder {

    private final UiTestMatrixRegistry registry;
    private final UiTestMatrixState matrixState;
    private final UiTestSemanticChecker semanticChecker;
    private final UiTestAssertionLogger assertionLogger;
    private final UiTestSampleVisualFactory sampleVisualFactory = new UiTestSampleVisualFactory();

    /**
     * 创建分组视觉页面构建器。
     *
     * @param registry 测试矩阵 registry
     * @param matrixState 测试矩阵状态
     * @param semanticChecker 语义 checker
     */
    UiTestGroupVisualBuilder(UiTestMatrixRegistry registry, UiTestMatrixState matrixState,
            UiTestSemanticChecker semanticChecker, UiTestAssertionLogger assertionLogger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.matrixState = Objects.requireNonNull(matrixState, "matrixState");
        this.semanticChecker = Objects.requireNonNull(semanticChecker, "semanticChecker");
        this.assertionLogger = Objects.requireNonNull(assertionLogger, "assertionLogger");
    }

    /**
     * 应用 test 页面根容器样式。
     *
     * @param root 根元素
     */
    void applyRootStyle(ElementNode root) {
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
     * 构建视觉矩阵首页。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param environmentText 环境信息文本
     * @param navigation 页面导航回调
     * @return 页面动态绑定
     */
    UiTestPageBindings buildHomePage(UiDocument document, ElementNode root, String environmentText,
            String runSummary, NavigationHandler navigation) {
        appendHomeHero(document, root, navigation);
        appendOverview(document, root, runSummary);
        appendGroupIndex(document, root, navigation);
        appendFailureSection(document, root);
        appendHomeManualSection(document, root);
        TextNode environmentNode = appendEnvironmentSection(document, root, environmentText);
        return new UiTestPageBindings(environmentNode);
    }

    /**
     * 构建分组二级页壳。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param group 当前分组
     * @param environmentText 环境信息文本
     * @param navigation 页面导航回调
     * @return 页面动态绑定
     */
    UiTestPageBindings buildGroupPage(UiDocument document, ElementNode root, UiTestGroupSpec group,
            String environmentText, String runSummary, NavigationHandler navigation, UiTestGroupPageState pageState,
            GroupInteractionHandler interactionHandler) {
        UiTestGroupState groupState = matrixState.getGroupState(group.getCode());
        appendGroupHero(document, root, group, navigation, pageState, runSummary);
        appendGroupVisualSamples(document, root, group, pageState, interactionHandler);
        appendGroupActions(document, root, group, pageState, interactionHandler);
        appendGroupDiagnostics(document, root, group, groupState, pageState);
        appendSiblingGroupNavigation(document, root, group, navigation);
        TextNode environmentNode = appendEnvironmentSection(document, root, environmentText);
        return new UiTestPageBindings(environmentNode);
    }

    /**
     * 追加首页顶部说明区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendHomeHero(UiDocument document, ElementNode root, final NavigationHandler navigation) {
        ElementNode hero = createHero(document);
        appendHeading(document, hero, "Qz UILib Test");
        appendMutedText(document, hero, "视觉样例 + 自动断言。已接入 " + matrixState.getTotalImplementedCaseCount()
                + " 个，自动 " + countAutomaticCases() + " 个，人工 " + countManualCases() + " 个。");
        ElementNode actions = createGrid(document);
        actions.append(createActionButton(document, "一键测试全部", 0xFF059669, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                navigation.runAllCaseAssertions();
            }
        }));
        hero.append(actions);
        root.append(hero);
    }

    /**
     * 追加分组页顶部说明区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param group 当前分组
     * @param navigation 页面导航回调
     */
    private void appendGroupHero(UiDocument document, ElementNode root, final UiTestGroupSpec group,
            final NavigationHandler navigation, UiTestGroupPageState pageState, String runSummary) {
        ElementNode hero = createHero(document);
        List<UiTestCaseSpec> cases = registry.getCases(group.getCode());
        int currentIndex = cases.isEmpty() ? 0 : Math.min(pageState.getCaseIndex(), cases.size() - 1) + 1;
        appendHeading(document, hero, "Test / " + group.getCode());
        appendMutedText(document, hero, group.getTitle() + " · " + currentIndex + "/" + cases.size()
                + " · 最近：" + runSummary);
        ElementNode actions = createGrid(document);
        actions.append(createActionButton(document, "返回首页", 0xFF475569, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                navigation.openHome();
            }
        }));
        actions.append(createActionButton(document, "一键测试全部", 0xFF059669, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                navigation.runAllCaseAssertions();
            }
        }));
        hero.append(actions);
        root.append(hero);
    }

    /**
     * 追加首页总览区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendOverview(UiDocument document, ElementNode root, String runSummary) {
        ElementNode section = createSection(document, "总览");
        ElementNode grid = createGrid(document);
        appendMetricCard(document, grid, "计划", String.valueOf(matrixState.getTotalPlannedCaseCount()));
        appendMetricCard(document, grid, "已接入", String.valueOf(matrixState.getTotalImplementedCaseCount()));
        appendMetricCard(document, grid, "缺口", String.valueOf(matrixState.getTotalGapCount()));
        appendMetricCard(document, grid, "自动/人工", countAutomaticCases() + "/" + countManualCases());
        section.append(grid);
        appendPlanItem(document, section, "最近：" + runSummary);
        root.append(section);
    }

    /**
     * 追加功能画廊区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendGallery(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "功能画廊");
        ElementNode grid = createGrid(document);
        for (UiTestGalleryItem item : registry.getGalleryItems()) {
            ElementNode card = createCard(document, 210, 1.0F, 1.0F);
            card.style().setBorderColor(item.getAccentColor());
            appendHeading(document, card, item.getTitle());
            appendMutedText(document, card, item.getDescription());
            appendStatusPill(document, card, item.getStatusText(), item.getAccentColor());
            grid.append(card);
        }
        section.append(grid);
        root.append(section);
    }

    /**
     * 追加语义覆盖热力图区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendHeatmap(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "语义覆盖热力图");
        ElementNode grid = createGrid(document);
        for (UiTestGroupState groupState : matrixState.getGroupStates()) {
            UiTestGroupSpec group = groupState.getGroup();
            ElementNode card = createCard(document, 210, 1.0F, 1.0F);
            appendHeading(document, card, group.getCode() + " 热力格");
            appendMutedText(document, card, group.getCode() + "：计划 " + group.getPlannedCaseCount()
                    + "；自动 " + group.getPlannedAutomaticCount()
                    + "；人工 " + group.getPlannedManualCount()
                    + "；缺口 " + groupState.getGapCount());
            appendMutedText(document, card, buildStateLine(groupState));
            appendMutedText(document, card, "浏览器语义：" + group.getSemanticGoal());
            grid.append(card);
        }
        section.append(grid);
        root.append(section);
    }

    /**
     * 追加快速筛选区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendQuickFilters(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "快速筛选");
        appendPlanItem(document, section, "筛选入口：全部 / 视觉展示 / 自动语义 / 人工确认 / 已知缺口 / 失败。");
        appendPlanItem(document, section, "P0 仅建立筛选标签和状态模型；真实样例接入后再按 case 状态过滤卡片。");
        root.append(section);
    }

    /**
     * 追加分组导航区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param navigation 页面导航回调
     */
    private void appendGroupIndex(UiDocument document, ElementNode root, final NavigationHandler navigation) {
        ElementNode section = createSection(document, "分组导航");
        ElementNode grid = createGrid(document);
        for (final UiTestGroupSpec group : registry.getGroups()) {
            UiTestGroupState groupState = matrixState.getGroupState(group.getCode());
            ElementNode card = createCard(document, 180, 1.0F, 1.0F);
            appendHeading(document, card, group.getCode() + " / " + group.getTitle());
            appendMutedText(document, card, "计划 " + group.getPlannedCaseCount()
                    + " · 接入 " + groupState.getImplementedCaseCount()
                    + " · 缺口 " + groupState.getGapCount());
            appendMutedText(document, card, buildStateLine(groupState));
            card.append(createActionButton(document, "打开 " + group.getCode(), 0xFF2563EB,
                    new DocumentButtonActionHandler() {
                        @Override
                        public void onAction(DocumentButtonActionEvent event) {
                            navigation.openGroup(group);
                        }
                    }));
            grid.append(card);
        }
        section.append(grid);
        root.append(section);
    }

    /**
     * 追加最近失败区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendFailureSection(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "最近失败");
        appendPlanItem(document, section, buildFailureSummary());
        root.append(section);
    }

    /**
     * 追加首页人工任务区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendHomeManualSection(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "人工确认");
        StringBuilder manualCases = new StringBuilder();
        for (UiTestCaseSpec testCase : registry.getCases()) {
            if (testCase.requiresManualConfirmation()) {
                if (manualCases.length() > 0) {
                    manualCases.append("、");
                }
                manualCases.append(testCase.getId());
            }
        }
        appendPlanItem(document, section, manualCases.length() == 0 ? "无。"
                : "需截图/交互确认：" + manualCases.toString() + "。");
        root.append(section);
    }

    /**
     * 追加分组说明区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param group 当前分组
     * @param groupState 当前分组状态
     */
    private void appendGroupDescription(UiDocument document, ElementNode root, UiTestGroupSpec group,
            UiTestGroupState groupState) {
        ElementNode section = createSection(document, "分组说明");
        appendPlanItem(document, section, "覆盖范围：" + group.getCoverage());
        appendPlanItem(document, section, "视觉展示目标：" + group.getVisualFocus());
        appendPlanItem(document, section, "浏览器语义目标：" + group.getSemanticGoal());
        appendPlanItem(document, section, "计划用例：" + group.getPlannedCaseCount()
                + "；自动语义：" + group.getPlannedAutomaticCount()
                + "；人工确认：" + group.getPlannedManualCount()
                + "；当前缺口：" + groupState.getGapCount());
        root.append(section);
    }

    /**
     * 追加分组视觉样例区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param group 当前分组
     */
    private void appendGroupVisualSamples(UiDocument document, ElementNode root, UiTestGroupSpec group,
            UiTestGroupPageState pageState, GroupInteractionHandler interactionHandler) {
        ElementNode section = createSection(document, "视觉样例区");
        List<UiTestCaseSpec> cases = registry.getCases(group.getCode());
        if (cases.isEmpty()) {
            appendPlanItem(document, section, "暂无样例。" + compactExpected(group.getExpectedVisualObservation()));
        }
        if (!cases.isEmpty()) {
            pageState.clampToCaseCount(cases.size());
            appendPagerBar(document, section, cases.size(), pageState.getCaseIndex(), interactionHandler);
            appendVisualCaseCard(document, section, cases.get(pageState.getCaseIndex()));
        }
        root.append(section);
        sampleVisualFactory.activateDeferredTopLayerDemos(document, section);
    }

    /**
     * 追加分组语义检查区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param group 当前分组
     * @param groupState 当前分组状态
     */
    private void appendGroupSemanticChecks(UiDocument document, ElementNode root, UiTestGroupSpec group,
            UiTestGroupState groupState, UiTestGroupPageState pageState) {
        ElementNode section = createSection(document, "语义检查区");
        appendPlanItem(document, section, semanticChecker.describeGroupBoundary(group));
        appendPlanItem(document, section, buildStateLine(groupState));
        List<UiTestCaseSpec> cases = registry.getCases(group.getCode());
        if (!cases.isEmpty()) {
            pageState.clampToCaseCount(cases.size());
            UiTestCaseSpec currentCase = cases.get(pageState.getCaseIndex());
            appendPlanItem(document, section, "当前样例：" + currentCase.getId() + "；本分组共 " + cases.size()
                    + " 张样例。 ");
            appendPlanItem(document, section, currentCase.getId() + "：" + currentCase.getSemanticAssertion()
                    + "；" + buildCaseStateLine(getCaseResult(currentCase)));
        }
        root.append(section);
    }

    /**
     * 追加分组操作区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendGroupActions(UiDocument document, ElementNode root, UiTestGroupSpec group,
            UiTestGroupPageState pageState, GroupInteractionHandler interactionHandler) {
        ElementNode section = createSection(document, "操作区");
        List<UiTestCaseSpec> cases = registry.getCases(group.getCode());
        if (cases.isEmpty()) {
            appendPlanItem(document, section, "暂无可执行样例。");
            root.append(section);
            return;
        }
        ElementNode actions = createGrid(document);
        actions.append(createActionButton(document, "上一张", 0xFF334155, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                interactionHandler.previousCase();
            }
        }));
        actions.append(createActionButton(document, "运行当前样例断言", 0xFF2563EB, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                interactionHandler.runCurrentCaseAssertion();
            }
        }));
        actions.append(createActionButton(document, "下一张", 0xFF334155, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                interactionHandler.nextCase();
            }
        }));
        actions.append(createActionButton(document, "一键测试全部", 0xFF059669, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                interactionHandler.runAllCaseAssertions();
            }
        }));
        section.append(actions);
        pageState.clampToCaseCount(cases.size());
        root.append(section);
    }

    /**
     * 追加分组诊断区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param group 当前分组
     * @param groupState 当前分组状态
     */
    private void appendGroupDiagnostics(UiDocument document, ElementNode root, UiTestGroupSpec group,
            UiTestGroupState groupState, UiTestGroupPageState pageState) {
        ElementNode section = createSection(document, "诊断区");
        appendPlanItem(document, section, buildStateLine(groupState) + "；" + compactText(
                semanticChecker.describeGroupBoundary(group), 120));
        List<UiTestCaseSpec> cases = registry.getCases(group.getCode());
        if (!cases.isEmpty()) {
            pageState.clampToCaseCount(cases.size());
            UiTestCaseSpec currentCase = cases.get(pageState.getCaseIndex());
            List<UiTestAssertionLogEntry> tail = assertionLogger.getCaseTail(currentCase.getId(), 3);
            if (tail.isEmpty()) {
                appendPlanItem(document, section, currentCase.getId() + "：未运行。");
            } else {
                for (UiTestAssertionLogEntry entry : tail) {
                    appendPlanItem(document, section, compactLogLine(entry));
                }
            }
        }
        root.append(section);
    }

    /**
     * 追加分页条。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param totalCaseCount 样例总数
     * @param caseIndex 当前样例索引
     * @param interactionHandler 分组页交互回调
     */
    private void appendPagerBar(UiDocument document, ElementNode parent, int totalCaseCount, int caseIndex,
            final GroupInteractionHandler interactionHandler) {
        ElementNode bar = createGrid(document);
        bar.append(createActionButton(document, "上一张", 0xFF334155, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                interactionHandler.previousCase();
            }
        }));
        appendMetricCard(document, bar, "当前样例", (caseIndex + 1) + " / " + totalCaseCount);
        bar.append(createActionButton(document, "下一张", 0xFF334155, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                interactionHandler.nextCase();
            }
        }));
        parent.append(bar);
    }

    /**
     * 追加同级分组导航区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param currentGroup 当前分组
     * @param navigation 页面导航回调
     */
    private void appendSiblingGroupNavigation(UiDocument document, ElementNode root, UiTestGroupSpec currentGroup,
            final NavigationHandler navigation) {
        ElementNode section = createSection(document, "同级分组导航");
        ElementNode grid = createGrid(document);
        for (final UiTestGroupSpec group : registry.getGroups()) {
            if (group == currentGroup) {
                appendMetricCard(document, grid, group.getCode(), "当前页");
            } else {
                grid.append(createActionButton(document, group.getCode(), 0xFF334155,
                        new DocumentButtonActionHandler() {
                            @Override
                            public void onAction(DocumentButtonActionEvent event) {
                                navigation.openGroup(group);
                            }
                        }));
            }
        }
        section.append(grid);
        root.append(section);
    }

    /**
     * 追加环境信息区。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param environmentText 环境信息文本
     * @return 环境信息文本节点
     */
    private TextNode appendEnvironmentSection(UiDocument document, ElementNode root, String environmentText) {
        ElementNode section = createSection(document, "环境信息");
        ElementNode item = createPlanItem(document);
        TextNode textNode = item.appendText(environmentText);
        section.append(item);
        root.append(section);
        return textNode;
    }

    /**
     * 追加状态契约区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendStateContractSection(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "状态模型");
        appendPlanItem(document, section, "视觉状态：未观察 / 展示中 / 人工通过 / 视觉失败 / 已知视觉缺口。");
        appendPlanItem(document, section, "语义状态：未断言 / 自动通过 / 自动失败 / 人工待确认 / 已知语义缺口。");
        appendPlanItem(document, section, "汇总状态：通过 / 部分通过 / 失败 / 待确认 / 缺口；视觉状态与语义状态互不覆盖。");
        root.append(section);
    }

    /**
     * 创建顶部说明容器。
     *
     * @param document 文档实例
     * @return 顶部说明容器
     */
    private ElementNode createHero(UiDocument document) {
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
        return hero;
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
        appendHeading(document, section, title);
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
     * 追加指标卡片。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param label 指标名
     * @param value 指标值
     */
    private void appendMetricCard(UiDocument document, ElementNode parent, String label, String value) {
        ElementNode card = createCard(document, 150, 1.0F, 1.0F);
        appendMutedText(document, card, label);
        ElementNode valueNode = document.div();
        valueNode.style()
                .setMargin(UiStyleLength.px(4))
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        valueNode.appendText(value);
        card.append(valueNode);
        parent.append(card);
    }

    /**
     * 追加标题文本。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param text 标题文本
     */
    private void appendHeading(UiDocument document, ElementNode parent, String text) {
        ElementNode heading = document.div();
        heading.style()
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        heading.appendText(text);
        parent.append(heading);
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
        line.style().setTextColor(0xFFC9D8F8);
        line.appendText(text);
        parent.append(line);
    }

    /**
     * 追加状态标签。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param text 标签文本
     * @param accentColor 强调色
     */
    private void appendStatusPill(UiDocument document, ElementNode parent, String text, int accentColor) {
        ElementNode pill = document.div();
        pill.style()
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(accentColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFFFFFFF);
        pill.appendText(text);
        parent.append(pill);
    }

    /**
     * 追加统一视觉样例卡片。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 样例规格
     */
    private void appendVisualCaseCard(UiDocument document, ElementNode parent, UiTestCaseSpec testCase) {
        ElementNode card = createCard(document, 1, 1.0F, 1.0F);
        card.style().setBorderColor(0xFF537DD6);
        appendHeading(document, card, testCase.getId() + " / " + testCase.getDisplayTarget());
        sampleVisualFactory.appendCaseDemo(document, card, testCase);
        appendCaseField(document, card, "预期", compactExpected(testCase.getObservationPoint()));
        if (testCase.requiresManualConfirmation()) {
            appendCaseField(document, card, "人工", compactText(testCase.getManualReason(), 96));
        }
        appendCaseField(document, card, "状态", buildCaseStateLine(getCaseResult(testCase)));
        parent.append(card);
    }

    /**
     * 追加样例字段行。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param label 字段名
     * @param value 字段值
     */
    private void appendCaseField(UiDocument document, ElementNode parent, String label, String value) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.START)
                .setColumnGap(UiStyleLength.px(8));
        ElementNode labelNode = document.div();
        labelNode.style()
                .setWidth(UiStyleLength.px(82))
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
        valueNode.appendText(value);
        row.append(valueNode);
        parent.append(row);
    }

    /**
     * 创建说明条目并追加文本。
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
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(110))
                .setPadding(UiStyleLength.px(8));
        return button.getElement();
    }

    /**
     * 构建分组状态摘要行。
     *
     * @param state 分组状态
     * @return 分组状态摘要行
     */
    private String buildStateLine(UiTestGroupState state) {
        return "视觉=" + state.getVisualStatus().getDisplayText()
                + "；语义=" + state.getSemanticStatus().getDisplayText()
                + "；汇总=" + state.getSummaryStatus().getDisplayText();
    }

    /**
     * 构建单张样例状态摘要行。
     *
     * @param result 样例结果
     * @return 样例状态摘要行
     */
    private String buildCaseStateLine(UiTestCaseResult result) {
        return "视觉=" + result.getVisualStatus().getDisplayText()
                + "；语义=" + result.getSemanticStatus().getDisplayText()
                + "；汇总=" + result.getSummaryStatus().getDisplayText()
                + "；结果=" + compactResult(result);
    }

    /**
     * 统计已接入自动样例数。
     *
     * @return 已接入自动样例数
     */
    private int countAutomaticCases() {
        int count = 0;
        for (UiTestCaseSpec testCase : registry.getCases()) {
            if (!testCase.requiresManualConfirmation()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计已接入人工样例数。
     *
     * @return 已接入人工样例数
     */
    private int countManualCases() {
        int count = 0;
        for (UiTestCaseSpec testCase : registry.getCases()) {
            if (testCase.requiresManualConfirmation()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 构建失败摘要。
     *
     * @return 失败摘要
     */
    private String buildFailureSummary() {
        StringBuilder ids = new StringBuilder();
        int count = 0;
        for (UiTestCaseSpec testCase : registry.getCases()) {
            UiTestCaseResult result = getCaseResult(testCase);
            if (result.getSummaryStatus() == UiTestSummaryStatus.FAILED) {
                if (ids.length() > 0) {
                    ids.append("、");
                }
                ids.append(testCase.getId());
                count++;
            }
        }
        return count == 0 ? "暂无。" : count + " 个：" + ids.toString() + "。";
    }

    /**
     * 构建页面级短结果，完整诊断仍保留在日志中。
     *
     * @param result 样例结果
     * @return 短结果
     */
    private String compactResult(UiTestCaseResult result) {
        if (result.getDifference().length() > 0) {
            return compactText(result.getDifference(), 96);
        }
        if (result.getSemanticStatus() == UiTestSemanticStatus.AUTO_PASSED) {
            return "通过。";
        }
        if (result.getSemanticStatus() == UiTestSemanticStatus.MANUAL_PENDING) {
            return "待人工确认。";
        }
        return compactText(result.getActualResult(), 96);
    }

    /**
     * 构建短日志行。
     *
     * @param entry 日志条目
     * @return 短日志行
     */
    private String compactLogLine(UiTestAssertionLogEntry entry) {
        String detail = entry.getDifference().length() > 0 && !"-".equals(entry.getDifference())
                ? entry.getDifference() : entry.getActual();
        return entry.getGroupCode() + "/" + entry.getCaseId() + " | " + entry.getPhase()
                + " | " + entry.getMessage() + " | " + compactText(detail, 120);
    }

    /**
     * 去掉“预期结果：”前缀并截断。
     *
     * @param text 原始预期文本
     * @return 页面短预期
     */
    private String compactExpected(String text) {
        String value = text == null ? "" : text;
        if (value.startsWith("预期结果：")) {
            value = value.substring("预期结果：".length());
        }
        return compactText(value, 110);
    }

    /**
     * 截断页面说明文本。
     *
     * @param text 原文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String compactText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    /**
     * 返回样例当前结果。
     *
     * @param testCase 样例规格
     * @return 样例结果
     */
    private UiTestCaseResult getCaseResult(UiTestCaseSpec testCase) {
        UiTestCaseResult result = matrixState.getCaseResultById().get(testCase.getId());
        return result == null ? semanticChecker.createInitialResult(testCase) : result;
    }

    /**
     * 页面导航回调。
     */
    interface NavigationHandler {

        /**
         * 打开首页。
         */
        void openHome();

        /**
         * 打开指定分组页。
         *
         * @param group 分组规格
         */
        void openGroup(UiTestGroupSpec group);

        /**
         * 运行全部已接入样例断言。
         */
        void runAllCaseAssertions();
    }

    /**
     * 分组页内样例交互回调。
     */
    interface GroupInteractionHandler {

        /**
         * 打开上一张样例。
         */
        void previousCase();

        /**
         * 打开下一张样例。
         */
        void nextCase();

        /**
         * 运行当前样例断言。
         */
        void runCurrentCaseAssertion();

        /**
         * 运行全部已接入样例断言。
         */
        void runAllCaseAssertions();
    }
}
