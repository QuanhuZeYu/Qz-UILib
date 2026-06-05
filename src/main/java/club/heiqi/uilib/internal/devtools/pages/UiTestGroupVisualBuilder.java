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
            NavigationHandler navigation) {
        appendHomeHero(document, root);
        appendOverview(document, root);
        appendGallery(document, root);
        appendHeatmap(document, root);
        appendQuickFilters(document, root);
        appendGroupIndex(document, root, navigation);
        appendFailureSection(document, root);
        appendHomeManualSection(document, root);
        TextNode environmentNode = appendEnvironmentSection(document, root, environmentText);
        appendStateContractSection(document, root);
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
            String environmentText, NavigationHandler navigation, UiTestGroupPageState pageState,
            GroupInteractionHandler interactionHandler) {
        UiTestGroupState groupState = matrixState.getGroupState(group.getCode());
        appendGroupHero(document, root, group, navigation, pageState);
        appendGroupDescription(document, root, group, groupState);
        appendGroupVisualSamples(document, root, group, pageState, interactionHandler);
        appendGroupSemanticChecks(document, root, group, groupState, pageState);
        appendGroupActions(document, root, group, pageState, interactionHandler);
        appendGroupDiagnostics(document, root, group, groupState, pageState);
        appendSiblingGroupNavigation(document, root, group, navigation);
        TextNode environmentNode = appendEnvironmentSection(document, root, environmentText);
        appendStateContractSection(document, root);
        return new UiTestPageBindings(environmentNode);
    }

    /**
     * 追加首页顶部说明区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendHomeHero(UiDocument document, ElementNode root) {
        ElementNode hero = createHero(document);
        appendHeading(document, hero, "Qz UILib Test 视觉矩阵");
        appendMutedText(document, hero, "视觉化展示功能优先，浏览器语义验证为重要目标；旧运行时卡片矩阵保持清空，不恢复旧页面名结构。");
        appendMutedText(document, hero, "规格来源：" + UiTestMatrixRegistry.SPEC_PATH);
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
            final NavigationHandler navigation, UiTestGroupPageState pageState) {
        ElementNode hero = createHero(document);
        List<UiTestCaseSpec> cases = registry.getCases(group.getCode());
        int currentIndex = cases.isEmpty() ? 0 : Math.min(pageState.getCaseIndex(), cases.size() - 1) + 1;
        appendHeading(document, hero, "Qz UILib Test / " + group.getCode() + " 视觉样例页");
        appendMutedText(document, hero, group.getTitle() + "：" + group.getCoverage());
        appendMutedText(document, hero, "当前采用低密度单样例翻页视图：第 " + currentIndex + " 张 / 共 "
                + cases.size() + " 张。 ");
        ElementNode actions = createGrid(document);
        actions.append(createActionButton(document, "返回首页", 0xFF475569, new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                navigation.openHome();
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
    private void appendOverview(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "总览");
        ElementNode grid = createGrid(document);
        appendMetricCard(document, grid, "test 系统版本", "P2 核心视觉样例批次");
        appendMetricCard(document, grid, "计划用例数", String.valueOf(matrixState.getTotalPlannedCaseCount()));
        appendMetricCard(document, grid, "已接入用例数", String.valueOf(matrixState.getTotalImplementedCaseCount()));
        appendMetricCard(document, grid, "剩余缺口数", String.valueOf(matrixState.getTotalGapCount()));
        appendMetricCard(document, grid, "计划自动语义", String.valueOf(matrixState.getTotalPlannedAutomaticCount()));
        appendMetricCard(document, grid, "计划人工确认", String.valueOf(matrixState.getTotalPlannedManualCount()));
        appendMetricCard(document, grid, "二级页数量", String.valueOf(registry.getGroups().size()));
        section.append(grid);
        appendPlanItem(document, section, "计划用例：" + matrixState.getTotalPlannedCaseCount()
                + "；已接入：" + matrixState.getTotalImplementedCaseCount()
                + "；缺口：" + matrixState.getTotalGapCount() + "；视觉状态/语义状态分离统计。");
        appendPlanItem(document, section, "旧运行时测试内容已清空；当前已接入 CSS / Layout / Paint 首批真实视觉样例。");
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
            ElementNode card = createCard(document, 230, 1.0F, 1.0F);
            appendHeading(document, card, group.getCode() + " / " + group.getTitle());
            appendMutedText(document, card, group.getCoverage());
            appendMutedText(document, card, "视觉目标：" + group.getVisualFocus());
            appendMutedText(document, card, "计划用例：" + group.getPlannedCaseCount()
                    + "；已接入：" + groupState.getImplementedCaseCount()
                    + "；缺口：" + groupState.getGapCount());
            appendMutedText(document, card, buildStateLine(groupState));
            card.append(createActionButton(document, "打开 " + group.getCode() + " 二级页", 0xFF2563EB,
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
        appendPlanItem(document, section, "暂无失败样例；P0 当前只建立数据模型和首页框架，后续失败摘要由结果 state 回写。");
        root.append(section);
    }

    /**
     * 追加首页人工任务区。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void appendHomeManualSection(UiDocument document, ElementNode root) {
        ElementNode section = createSection(document, "人工任务");
        boolean hasManualCase = false;
        for (UiTestCaseSpec testCase : registry.getCases()) {
            if (testCase.requiresManualConfirmation()) {
                hasManualCase = true;
                appendPlanItem(document, section, testCase.getId() + "：" + testCase.getManualReason()
                        + "；" + testCase.getObservationPoint());
            }
        }
        if (!hasManualCase) {
            appendPlanItem(document, section, "当前没有已接入样例需要人工处理；以下为首轮规划中的人工确认来源。");
        }
        for (UiTestGroupSpec group : registry.getGroups()) {
            if (group.getPlannedManualCount() > 0) {
                appendPlanItem(document, section, group.getCode() + "：计划人工确认 "
                        + group.getPlannedManualCount() + " 项；" + group.getExpectedVisualObservation());
            }
        }
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
            appendPlanItem(document, section, "当前 P0 仅接入数据模型和首页框架；本分组暂无真实视觉样例，不恢复旧卡片矩阵。");
            appendPlanItem(document, section, group.getExpectedVisualObservation());
        }
        if (!cases.isEmpty()) {
            pageState.clampToCaseCount(cases.size());
            appendPagerBar(document, section, cases.size(), pageState.getCaseIndex(), interactionHandler);
            appendVisualCaseCard(document, section, cases.get(pageState.getCaseIndex()));
        }
        root.append(section);
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
            appendPlanItem(document, section, "当前没有样例可执行；本批不恢复旧执行按钮。");
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
        section.append(actions);
        pageState.clampToCaseCount(cases.size());
        appendPlanItem(document, section, "当前页只展示 1 张样例；优先先用结构化断言日志排查问题，再补更多自动语义覆盖。 ");
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
        appendPlanItem(document, section, semanticChecker.buildGroupDiagnosticSummary(group, groupState));
        List<UiTestCaseSpec> cases = registry.getCases(group.getCode());
        if (!cases.isEmpty()) {
            pageState.clampToCaseCount(cases.size());
            UiTestCaseSpec currentCase = cases.get(pageState.getCaseIndex());
            appendPlanItem(document, section, "当前样例日志 tail：");
            List<UiTestAssertionLogEntry> tail = assertionLogger.getCaseTail(currentCase.getId(),
                    assertionLogger.getDefaultTailLimit());
            if (tail.isEmpty()) {
                appendPlanItem(document, section, currentCase.getId() + "：尚未产生日志；先执行“运行当前样例断言”。");
            } else {
                for (UiTestAssertionLogEntry entry : tail) {
                    appendPlanItem(document, section, entry.toDisplayLine());
                }
            }
        }
        appendPlanItem(document, section, "诊断目标：日志按 group/case/phase/context 展示，并持续补 computed style、布局盒、clip、transform 与 stacking 摘要。 ");
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
        appendCaseField(document, card, "用例编号", testCase.getId());
        appendCaseField(document, card, "展示目标", testCase.getDisplayTarget());
        appendCaseField(document, card, "浏览器语义", testCase.getBrowserSemantic());
        appendCaseField(document, card, "视觉样例", testCase.getVisualSample());
        sampleVisualFactory.appendCaseDemo(document, card, testCase);
        appendCaseField(document, card, "观察要点", testCase.getObservationPoint());
        appendCaseField(document, card, "语义断言", testCase.getSemanticAssertion());
        if (testCase.requiresManualConfirmation()) {
            appendCaseField(document, card, "人工原因", testCase.getManualReason());
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
        return "视觉状态=" + state.getVisualStatus().getDisplayText()
                + "；语义状态=" + state.getSemanticStatus().getDisplayText()
                + "；汇总状态=" + state.getSummaryStatus().getDisplayText();
    }

    /**
     * 构建单张样例状态摘要行。
     *
     * @param result 样例结果
     * @return 样例状态摘要行
     */
    private String buildCaseStateLine(UiTestCaseResult result) {
        return "视觉状态=" + result.getVisualStatus().getDisplayText()
                + "；语义状态=" + result.getSemanticStatus().getDisplayText()
                + "；汇总状态=" + result.getSummaryStatus().getDisplayText()
                + "；实际结果=" + result.getActualResult();
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
    }
}
