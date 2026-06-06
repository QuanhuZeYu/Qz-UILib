package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiTestDocumentPageController` test 首页与二级页的黑盒测试。
 */
public class UiTestDocumentPageControllerTest {

    /**
     * 验证 `/qzuilib test` 首页进入视觉优先矩阵框架。
     */
    @Test
    public void shouldBuildVisualPriorityMatrixHomeDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Qz UILib Test"));
        Assert.assertTrue(containsText(texts, "视觉样例 + 自动断言。已接入 31 个，自动 23 个，人工 8 个。"));
        Assert.assertTrue(containsText(texts, "一键测试全部"));
        Assert.assertTrue(containsText(texts, "总览"));
        Assert.assertTrue(containsText(texts, "计划"));
        Assert.assertTrue(containsText(texts, "59"));
        Assert.assertTrue(containsText(texts, "已接入"));
        Assert.assertTrue(containsText(texts, "31"));
        Assert.assertTrue(containsText(texts, "缺口"));
        Assert.assertTrue(containsText(texts, "28"));
        Assert.assertTrue(containsText(texts, "自动/人工"));
        Assert.assertTrue(containsText(texts, "23/8"));
        Assert.assertTrue(containsText(texts, "最近：尚未运行。"));
        Assert.assertTrue(containsText(texts, "视觉=未观察；语义=未断言；汇总=缺口"));
        Assert.assertTrue(containsText(texts, "视觉=展示中；语义=未断言；汇总=待确认"));
        Assert.assertTrue(containsText(texts, "DOM 与选择器语义"));
        Assert.assertTrue(containsText(texts, "CSS 级联与样式语义"));
        Assert.assertTrue(containsText(texts, "Layout 布局与尺寸语义"));
        Assert.assertTrue(containsText(texts, "Paint 绘制、命中与视觉语义"));
        Assert.assertTrue(containsText(texts, "Input 输入与事件语义"));
        Assert.assertTrue(containsText(texts, "Controls 控件与表单语义"));
        Assert.assertTrue(containsText(texts, "TextFont 文本、字体与国际化语义"));
        Assert.assertTrue(containsText(texts, "Animation 动画与 Transition 语义"));
        Assert.assertTrue(containsText(texts, "RuntimeHost 宿主运行时语义"));
        Assert.assertTrue(containsText(texts, "RemoteNet 远程、配置与网络语义"));
        Assert.assertTrue(containsText(texts, "打开 DOM"));
        Assert.assertTrue(containsText(texts, "计划 7 · 接入 0 · 缺口 7"));
        Assert.assertTrue(containsText(texts, "计划 6 · 接入 6 · 缺口 0"));
        Assert.assertTrue(containsText(texts, "计划 7 · 接入 7 · 缺口 0"));
        Assert.assertTrue(containsText(texts, "计划 5 · 接入 5 · 缺口 0"));
        Assert.assertTrue(containsText(texts, "人工确认"));
        Assert.assertTrue(containsText(texts, "VIS-CTRL-003"));
        Assert.assertFalse(containsText(texts, "功能画廊"));
        Assert.assertFalse(containsText(texts, "语义覆盖热力图"));
        Assert.assertFalse(containsText(texts, "快速筛选"));
        Assert.assertFalse(containsText(texts, "状态模型"));
        Assert.assertFalse(containsText(texts, "DOM-001"));
        Assert.assertFalse(containsText(texts, "执行自动测试"));
        Assert.assertFalse(containsText(texts, "人工失败"));
        Assert.assertFalse(containsText(texts, "已接入 13 张运行时卡片"));
    }

    /**
     * 验证 P0 registry 与 state 使用视觉/语义双状态模型。
     */
    @Test
    public void shouldExposeVisualAndSemanticMatrixModels() {
        TestFixture fixture = new TestFixture();

        UiTestMatrixRegistry registry = fixture.controller.getRegistry();
        UiTestMatrixState state = fixture.controller.getMatrixState();

        Assert.assertEquals(10, registry.getGroups().size());
        Assert.assertEquals(31, registry.getCases().size());
        Assert.assertEquals(59, state.getTotalPlannedCaseCount());
        Assert.assertEquals(31, state.getTotalImplementedCaseCount());
        Assert.assertEquals(28, state.getTotalGapCount());
        Assert.assertEquals(40, state.getTotalPlannedAutomaticCount());
        Assert.assertEquals(19, state.getTotalPlannedManualCount());

        UiTestGroupState domState = state.getGroupState("DOM");
        Assert.assertEquals(7, domState.getGroup().getPlannedCaseCount());
        Assert.assertEquals(UiTestVisualStatus.UNOBSERVED, domState.getVisualStatus());
        Assert.assertEquals(UiTestSemanticStatus.NOT_ASSERTED, domState.getSemanticStatus());
        Assert.assertEquals(UiTestSummaryStatus.GAP, domState.getSummaryStatus());

        UiTestGroupState cssState = state.getGroupState("CSS");
        Assert.assertEquals(6, cssState.getImplementedCaseCount());
        Assert.assertEquals(0, cssState.getGapCount());
        Assert.assertEquals(UiTestVisualStatus.DISPLAYING, cssState.getVisualStatus());
        Assert.assertEquals(UiTestSemanticStatus.NOT_ASSERTED, cssState.getSemanticStatus());

        UiTestGroupState layoutState = state.getGroupState("LAYOUT");
        Assert.assertEquals(6, layoutState.getImplementedCaseCount());
        Assert.assertEquals(0, layoutState.getGapCount());

        UiTestGroupState paintState = state.getGroupState("PAINT");
        Assert.assertEquals(7, paintState.getImplementedCaseCount());
        Assert.assertEquals(0, paintState.getGapCount());
        Assert.assertEquals(UiTestSemanticStatus.MANUAL_PENDING, paintState.getSemanticStatus());

        UiTestGroupState inputState = state.getGroupState("INPUT");
        Assert.assertEquals(5, inputState.getImplementedCaseCount());
        Assert.assertEquals(0, inputState.getGapCount());
        Assert.assertEquals(UiTestSemanticStatus.MANUAL_PENDING, inputState.getSemanticStatus());

        UiTestGroupState controlsState = state.getGroupState("CTRL");
        Assert.assertEquals(7, controlsState.getImplementedCaseCount());
        Assert.assertEquals(0, controlsState.getGapCount());
        Assert.assertEquals(UiTestSemanticStatus.MANUAL_PENDING, controlsState.getSemanticStatus());
    }

    /**
     * 验证分组二级页显示视觉样例框架，不再展示旧用例卡片契约。
     */
    @Test
    public void shouldExposeGroupVisualSampleFrameworkAfterRuntimeCasesCleared() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 DOM", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Test / DOM"));
        Assert.assertTrue(containsText(texts, "视觉样例区"));
        Assert.assertTrue(containsText(texts, "操作区"));
        Assert.assertTrue(containsText(texts, "诊断区"));
        Assert.assertTrue(containsText(texts, "暂无样例。后续样例应直接画出 DOM 结构"));
        Assert.assertTrue(containsText(texts, "自动边界：节点归属、返回值、子节点顺序"));
        Assert.assertTrue(containsText(texts, "暂无可执行样例。"));
        Assert.assertFalse(containsText(texts, "用例编号"));
        Assert.assertFalse(containsText(texts, "分组说明"));
        Assert.assertFalse(containsText(texts, "语义检查区"));
        Assert.assertFalse(containsText(texts, "DOM-001"));
        Assert.assertFalse(containsText(texts, "CSS-001"));
        Assert.assertFalse(containsText(texts, "LAYOUT-001"));
        Assert.assertFalse(containsText(texts, "执行自动测试"));
        Assert.assertFalse(containsText(texts, "人工失败"));
    }

    /**
     * 验证空态下仍可在各分组二级页之间切换。
     */
    @Test
    public void shouldNavigateBetweenReservedGroupPages() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 DOM", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "CSS", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Test / CSS"));
        Assert.assertTrue(containsText(texts, "CSS 级联与样式语义"));
        Assert.assertTrue(containsText(texts, "VIS-CSS-001"));
        Assert.assertTrue(containsText(texts, "Specificity 三阶色块"));
        Assert.assertTrue(containsText(texts, "sample 标签"));
        Assert.assertTrue(containsText(texts, "1 / 6"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-CSS-002"));
        Assert.assertTrue(containsText(texts, "box-sizing 盒模型对比"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-CSS-003"));
        Assert.assertTrue(containsText(texts, "visibility 与 pointer-events 状态牌"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-CSS-004"));
        Assert.assertTrue(containsText(texts, "继承与级联优先级"));
        Assert.assertFalse(containsText(texts, "浏览器语义"));
        Assert.assertFalse(containsText(texts, "语义断言"));
        Assert.assertFalse(containsText(texts, "执行自动测试"));
    }

    /**
     * 验证 Layout 与 Paint 二级页会渲染首批核心视觉样例。
     */
    @Test
    public void shouldRenderCoreVisualSamplesForLayoutAndPaintGroups() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 LAYOUT", 0);

        List<String> layoutTexts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(layoutTexts, "VIS-LAYOUT-001"));
        Assert.assertTrue(containsText(layoutTexts, "block flow 与 margin collapse 标尺"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        layoutTexts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(layoutTexts, "VIS-LAYOUT-002"));
        Assert.assertTrue(containsText(layoutTexts, "flex min-content 收缩轨道"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        layoutTexts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(layoutTexts, "VIS-LAYOUT-003"));
        Assert.assertTrue(containsText(layoutTexts, "table auto 内容列宽"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        layoutTexts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(layoutTexts, "VIS-LAYOUT-004"));
        Assert.assertTrue(containsText(layoutTexts, "inline 与 inline-block 排列"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "PAINT", 0);
        List<String> paintTexts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(paintTexts, "VIS-PAINT-001"));
        Assert.assertTrue(containsText(paintTexts, "stacking 与 opacity 重叠舞台"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        paintTexts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(paintTexts, "VIS-PAINT-002"));
        Assert.assertTrue(containsText(paintTexts, "overflow clip 裁剪窗口"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        paintTexts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(paintTexts, "VIS-PAINT-003"));
        Assert.assertTrue(containsText(paintTexts, "transform 视觉命中舞台"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        paintTexts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(paintTexts, "VIS-PAINT-004"));
        Assert.assertTrue(containsText(paintTexts, "transform 命中舞台"));
        Assert.assertTrue(containsText(paintTexts, "语义=人工待确认"));
    }

    /**
     * 验证运行当前样例断言后会回写状态与日志尾部。
     */
    @Test
    public void shouldRunCurrentCaseAssertionAndExposeAssertionLogs() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 CSS", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "语义=自动通过"));
        Assert.assertTrue(containsText(texts, "结果=通过。"));
        Assert.assertTrue(containsText(texts, "CSS/VIS-CSS-001 | start | 开始运行样例断言"));
        Assert.assertTrue(containsText(texts, "pass | 自动断言通过"));
        Assert.assertTrue(getCaseResult(fixture, "VIS-CSS-001").getActualResult().contains("sampleBg=0xFF334155"));
        Assert.assertFalse(containsText(texts, "context=group=CSS；case=VIS-CSS-001"));
        Assert.assertFalse(containsText(texts, "stageStyle=display=FLEX"));
    }

    /**
     * 验证首页一键运行全部已接入样例，并保留人工确认样例状态。
     */
    @Test
    public void shouldRunAllCaseAssertionsFromHome() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "一键测试全部", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "全量完成：31 个；通过 23；失败 0；人工 8。"));
        Assert.assertTrue(containsText(texts, "视觉=展示中；语义=自动通过；汇总=待确认"));
        Assert.assertTrue(containsText(texts, "视觉=展示中；语义=人工待确认；汇总=待确认"));
        Assert.assertFalse(containsText(texts, "stageStyle=display=FLEX"));
    }

    /**
     * 验证 CSS 后续样例的自动断言直接覆盖继承与 overflow 行为。
     */
    @Test
    public void shouldRunCssInheritanceAndOverflowAssertions() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 CSS", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-CSS-004"));
        Assert.assertTrue(containsText(texts, "语义=自动通过"));
        Assert.assertTrue(getCaseResult(fixture, "VIS-CSS-004").getActualResult().contains("inheritedColor=0xFF38BDF8"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);

        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-CSS-006"));
        Assert.assertTrue(containsText(texts, "语义=自动通过"));
        Assert.assertTrue(getCaseResult(fixture, "VIS-CSS-006").getActualResult().contains("hiddenOverflow=HIDDEN/HIDDEN"));
        Assert.assertTrue(getCaseResult(fixture, "VIS-CSS-006").getActualResult()
                .contains("overflowDiff=expected hidden clip, auto scrollable, visible overflow"));
    }

    /**
     * 验证 block flow 样例断言真实检查相邻 margin collapse。
     */
    @Test
    public void shouldRunLayoutBlockFlowMarginCollapseAssertion() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 LAYOUT", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-LAYOUT-001"));
        Assert.assertTrue(containsText(texts, "语义=自动通过"));
        Assert.assertTrue(getCaseResult(fixture, "VIS-LAYOUT-001").getActualResult()
                .contains("blockFlowDiff=secondTop-firstBottom=24"));
    }

    /**
     * 验证 Paint top-layer 使用真实注册，scrollbar 与 host image 保持人工待确认诊断。
     */
    @Test
    public void shouldRunPaintTopLayerAndManualDiagnostics() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 PAINT", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-PAINT-005"));
        Assert.assertTrue(containsText(texts, "语义=自动通过"));
        Assert.assertTrue(getCaseResult(fixture, "VIS-PAINT-005").getActualResult()
                .contains("topLayerRegistered=true"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-PAINT-006"));
        Assert.assertTrue(containsText(texts, "语义=人工待确认"));
        Assert.assertTrue(getCaseResult(fixture, "VIS-PAINT-006").getActualResult()
                .contains("scrollbarDiff=overflow 与 scroll range 可机器诊断"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-PAINT-007"));
        Assert.assertTrue(containsText(texts, "语义=人工待确认"));
        Assert.assertTrue(getCaseResult(fixture, "VIS-PAINT-007").getActualResult()
                .contains("hostImageDiff=背景图声明可机器诊断"));
    }

    /**
     * 验证 Input 分组接入五张视觉样例，并运行四张自动断言和一张人工诊断。
     */
    @Test
    public void shouldRenderInputSamplesAndRunInputAssertions() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 INPUT", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-INPUT-001"));
        Assert.assertTrue(containsText(texts, "capture/bubble 事件轨道"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-INPUT-001").getActualResult()
                .contains("propagationDiff=expected root-capture"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-INPUT-002").getActualResult()
                .contains("preventDefaultDiff=expected default-click present"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-INPUT-003").getActualResult()
                .contains("wheelDiff=expected wheel log before default scroll"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertEquals(UiTestSemanticStatus.MANUAL_PENDING,
                getCaseResult(fixture, "VIS-INPUT-004").getSemanticStatus());
        Assert.assertTrue(getCaseResult(fixture, "VIS-INPUT-004").getActualResult()
                .contains("focusVisibleDiff=事件日志可机器诊断"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-INPUT-005"));
        Assert.assertTrue(containsText(texts, "keyboard/textInput 日志"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-INPUT-005").getActualResult()
                .contains("keyboardTextDiff=expected key capture/target/bubble"));
        Assert.assertEquals(UiTestSemanticStatus.AUTO_PASSED,
                getCaseResult(fixture, "VIS-INPUT-005").getSemanticStatus());
    }

    /**
     * 验证 Controls 分组接入七张真实控件视觉样例，并运行自动断言与人工诊断。
     */
    @Test
    public void shouldRenderControlsSamplesAndRunControlsAssertions() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 CTRL", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-CTRL-001"));
        Assert.assertTrue(containsText(texts, "button 默认/focus/disabled 状态"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-CTRL-001").getActualResult()
                .contains("buttonDiff=expected primary-click once"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-CTRL-002").getActualResult()
                .contains("inputDiff=expected text=Alpha"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertEquals(UiTestSemanticStatus.MANUAL_PENDING,
                getCaseResult(fixture, "VIS-CTRL-003").getSemanticStatus());
        Assert.assertTrue(getCaseResult(fixture, "VIS-CTRL-003").getActualResult()
                .contains("textareaCaretDiff=selection/value 可机器诊断"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-CTRL-004").getActualResult()
                .contains("choiceDiff=expected checkbox=true"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-CTRL-005"));
        Assert.assertTrue(containsText(texts, "select 弹层与 table 状态表"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertEquals(UiTestSemanticStatus.MANUAL_PENDING,
                getCaseResult(fixture, "VIS-CTRL-005").getSemanticStatus());
        Assert.assertTrue(getCaseResult(fixture, "VIS-CTRL-005").getActualResult()
                .contains("selectTableDiff=select value 与 table 布局可机器诊断"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-CTRL-006").getActualResult()
                .contains("sliderToggleDiff=expected slider 40->50"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-CTRL-007"));
        Assert.assertTrue(containsText(texts, "tab/focus/disabled 组合状态"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-CTRL-007").getActualResult()
                .contains("tabFocusDisabledDiff=expected tab event selects index 1"));
        Assert.assertEquals(UiTestSemanticStatus.AUTO_PASSED,
                getCaseResult(fixture, "VIS-CTRL-007").getSemanticStatus());
    }

    /**
     * 验证 transform 人工确认样例运行后仍保留人工状态，并输出明确 transform 诊断。
     */
    @Test
    public void shouldExposeManualTransformDiagnosticsWithoutAutoPassing() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 PAINT", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "语义=人工待确认"));
        Assert.assertTrue(containsText(texts, "PAINT/VIS-PAINT-003 | skip | 当前样例未接入自动断言"));
        Assert.assertTrue(getCaseResult(fixture, "VIS-PAINT-003").getActualResult()
                .contains("transformDiff=layoutLeftDelta=0, layoutTopDelta=0"));
    }

    /**
     * 验证环境信息会跟随运行时视图刷新。
     */
    @Test
    public void shouldRefreshRuntimeEnvironmentText() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.runtimeView.hostWidth = 960;
        fixture.runtimeView.hostHeight = 540;
        fixture.runtimeView.mouseX = 12;
        fixture.runtimeView.mouseY = 34;
        fixture.controller.beforeDocumentFrame();

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "MC 1.7.10"));
        Assert.assertTrue(containsText(texts, "字体=1"));
        Assert.assertTrue(containsText(texts, "窗口=960x540"));
        Assert.assertTrue(containsText(texts, "鼠标=12,34"));
        Assert.assertTrue(containsText(texts, "网络=vanilla"));
    }

    /**
     * 验证 test 页不替使用方节流运行时统计，普通环境文本每帧反映最新 frame/render。
     */
    @Test
    public void shouldRefreshRuntimeStatsEnvironmentTextEveryFrame() {
        TestFixture fixture = new TestFixture(true);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        fixture.controller.beforeDocumentFrame();
        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "frame=2.00ms, render=1.00ms"));

        fixture.controller.beforeDocumentFrame();
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "frame=3.00ms, render=1.50ms"));
    }

    private static List<String> collectDocumentTexts(HtmlLikeDocumentWidget widget) {
        List<String> texts = new ArrayList<String>();
        if (widget == null || widget.getDocument() == null) {
            return texts;
        }
        collectTextsFromNode(widget.getDocument().getRootElement(), texts);
        return texts;
    }

    private static void collectTextsFromNode(DocumentNode node, List<String> texts) {
        if (node.getNodeType() == DocumentNodeType.TEXT) {
            String text = ((TextNode) node).getText();
            if (text != null && !text.isEmpty()) {
                texts.add(text);
            }
        }
        if (node.getNodeType() == DocumentNodeType.ELEMENT) {
            ElementNode element = (ElementNode) node;
            for (DocumentNode child : element.getChildren()) {
                collectTextsFromNode(child, texts);
            }
        }
    }

    private static boolean containsText(List<String> texts, String expectedSnippet) {
        for (String text : texts) {
            if (text != null && text.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    private static UiTestCaseResult getCaseResult(TestFixture fixture, String caseId) {
        UiTestCaseResult result = fixture.controller.getMatrixState().getCaseResult(caseId);
        Assert.assertNotNull("找不到样例结果：" + caseId, result);
        return result;
    }

    private static void clickButtonByLabel(HtmlLikeDocumentWidget widget, String label, int occurrence) {
        List<ElementNode> buttons = new ArrayList<ElementNode>();
        collectButtonsByLabel(widget.getDocument().getRootElement(), label, buttons);
        Assert.assertTrue("找不到按钮：" + label + " #" + occurrence, buttons.size() > occurrence);
        ElementNode button = buttons.get(occurrence);
        Assert.assertNotNull(button.getClickHandler());
        button.getClickHandler().onClick(new DocumentElementClickEvent(button, button, 0, 0, 0, 0L));
    }

    private static void collectButtonsByLabel(DocumentNode node, String label, List<ElementNode> buttons) {
        if (node.getNodeType() != DocumentNodeType.ELEMENT) {
            return;
        }
        ElementNode element = (ElementNode) node;
        if ("button".equals(element.getTagName()) && containsText(collectElementTexts(element), label)) {
            buttons.add(element);
        }
        for (DocumentNode child : element.getChildren()) {
            collectButtonsByLabel(child, label, buttons);
        }
    }

    private static List<String> collectElementTexts(ElementNode element) {
        List<String> texts = new ArrayList<String>();
        collectTextsFromNode(element, texts);
        return texts;
    }

    private static final class TestFixture {

        private final TextMeasureService textMeasureService;
        private final DocumentUiScope documentUi;
        private final DirectDocumentPageAuthoringSurface pageSurface;
        private final MutableRuntimeView runtimeView;
        private final UiTestDocumentPageController controller;

        private TestFixture() {
            this(false);
        }

        private TestFixture(boolean incrementingRuntimeStats) {
            this.textMeasureService = new DeterministicTextMeasureService();
            this.documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
            this.pageSurface = new DirectDocumentPageAuthoringSurface();
            this.runtimeView = new MutableRuntimeView();
            this.runtimeView.incrementingRuntimeStats = incrementingRuntimeStats;
            this.controller = new UiTestDocumentPageController(documentUi, pageSurface, runtimeView);
        }
    }

    private static final class MutableRuntimeView implements DocumentPageRuntimeView {

        private int hostWidth;
        private int hostHeight;
        private int mouseX;
        private int mouseY;
        private boolean incrementingRuntimeStats;
        private int runtimeStatsCallCount;

        @Override
        public int getHostWidth() {
            return hostWidth;
        }

        @Override
        public int getHostHeight() {
            return hostHeight;
        }

        @Override
        public int getMouseX() {
            return mouseX;
        }

        @Override
        public int getMouseY() {
            return mouseY;
        }

        @Override
        public UiRuntimeStats getUiRuntimeStats() {
            if (!incrementingRuntimeStats) {
                return UiRuntimeStats.empty();
            }
            runtimeStatsCallCount++;
            long frameTimeNanos = runtimeStatsCallCount * 1_000_000L;
            long renderTimeNanos = runtimeStatsCallCount * 500_000L;
            return new UiRuntimeStats("test", 0, 0, 0, 0, frameTimeNanos, frameTimeNanos,
                    frameTimeNanos, 60.0D, renderTimeNanos, renderTimeNanos, 0L, 0, 0, 0, 0L, 0L,
                    0, 0, "", 0L, "", 0L, "", 0, 0);
        }
    }

    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return text == null ? "" : text;
        }

        @Override
        public java.util.List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
