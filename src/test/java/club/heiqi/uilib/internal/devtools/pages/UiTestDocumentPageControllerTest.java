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
        Assert.assertTrue(containsText(texts, "Qz UILib Test 视觉矩阵"));
        Assert.assertTrue(containsText(texts, "视觉化展示功能优先，浏览器语义验证为重要目标"));
        Assert.assertTrue(containsText(texts, "qzuilib-test-page-visual-matrix-plan.md"));
        Assert.assertTrue(containsText(texts, "功能画廊"));
        Assert.assertTrue(containsText(texts, "语义覆盖热力图"));
        Assert.assertTrue(containsText(texts, "快速筛选"));
        Assert.assertTrue(containsText(texts, "计划用例：59；已接入：9；缺口：50"));
        Assert.assertTrue(containsText(texts, "视觉状态=未观察；语义状态=未断言；汇总状态=缺口"));
        Assert.assertTrue(containsText(texts, "视觉状态=展示中；语义状态=未断言；汇总状态=缺口"));
        Assert.assertTrue(containsText(texts, "二级页数量"));
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
        Assert.assertTrue(containsText(texts, "打开 DOM 二级页"));
        Assert.assertTrue(containsText(texts, "DOM：计划 7；自动 7；人工 0；缺口 7"));
        Assert.assertTrue(containsText(texts, "CSS：计划 6；自动 6；人工 0；缺口 3"));
        Assert.assertTrue(containsText(texts, "PAINT：计划 7；自动 5；人工 2；缺口 4"));
        Assert.assertTrue(containsText(texts, "旧运行时测试内容已清空"));
        Assert.assertTrue(containsText(texts, "视觉状态：未观察 / 展示中 / 人工通过 / 视觉失败 / 已知视觉缺口"));
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
        Assert.assertEquals(9, registry.getCases().size());
        Assert.assertEquals(59, state.getTotalPlannedCaseCount());
        Assert.assertEquals(9, state.getTotalImplementedCaseCount());
        Assert.assertEquals(50, state.getTotalGapCount());
        Assert.assertEquals(43, state.getTotalPlannedAutomaticCount());
        Assert.assertEquals(16, state.getTotalPlannedManualCount());

        UiTestGroupState domState = state.getGroupState("DOM");
        Assert.assertEquals(7, domState.getGroup().getPlannedCaseCount());
        Assert.assertEquals(UiTestVisualStatus.UNOBSERVED, domState.getVisualStatus());
        Assert.assertEquals(UiTestSemanticStatus.NOT_ASSERTED, domState.getSemanticStatus());
        Assert.assertEquals(UiTestSummaryStatus.GAP, domState.getSummaryStatus());

        UiTestGroupState cssState = state.getGroupState("CSS");
        Assert.assertEquals(3, cssState.getImplementedCaseCount());
        Assert.assertEquals(3, cssState.getGapCount());
        Assert.assertEquals(UiTestVisualStatus.DISPLAYING, cssState.getVisualStatus());
        Assert.assertEquals(UiTestSemanticStatus.NOT_ASSERTED, cssState.getSemanticStatus());

        UiTestGroupState paintState = state.getGroupState("PAINT");
        Assert.assertEquals(UiTestSemanticStatus.MANUAL_PENDING, paintState.getSemanticStatus());
    }

    /**
     * 验证分组二级页显示视觉样例框架，不再展示旧用例卡片契约。
     */
    @Test
    public void shouldExposeGroupVisualSampleFrameworkAfterRuntimeCasesCleared() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 DOM 二级页", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Qz UILib Test / DOM 视觉样例页"));
        Assert.assertTrue(containsText(texts, "分组说明"));
        Assert.assertTrue(containsText(texts, "视觉样例区"));
        Assert.assertTrue(containsText(texts, "语义检查区"));
        Assert.assertTrue(containsText(texts, "操作区"));
        Assert.assertTrue(containsText(texts, "诊断区"));
        Assert.assertTrue(containsText(texts, "当前 P0 仅接入数据模型和首页框架"));
        Assert.assertTrue(containsText(texts, "预期结果：后续样例应直接画出 DOM 结构"));
        Assert.assertTrue(containsText(texts, "自动边界：节点归属、返回值、子节点顺序"));
        Assert.assertTrue(containsText(texts, "本批不恢复旧执行按钮"));
        Assert.assertFalse(containsText(texts, "用例编号"));
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
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 DOM 二级页", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "CSS", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Qz UILib Test / CSS 视觉样例页"));
        Assert.assertTrue(containsText(texts, "CSS 级联与样式语义"));
        Assert.assertTrue(containsText(texts, "computed style、继承结果、specificity 结果"));
        Assert.assertTrue(containsText(texts, "VIS-CSS-001"));
        Assert.assertTrue(containsText(texts, "Specificity 三阶色块"));
        Assert.assertTrue(containsText(texts, "sample 标签"));
        Assert.assertTrue(containsText(texts, "第 1 张 / 共 3 张"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-CSS-002"));
        Assert.assertTrue(containsText(texts, "box-sizing 盒模型对比"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-CSS-003"));
        Assert.assertTrue(containsText(texts, "visibility 与 pointer-events 状态牌"));
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
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 LAYOUT 二级页", 0);

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
        Assert.assertTrue(containsText(paintTexts, "语义状态=人工待确认"));
    }

    /**
     * 验证运行当前样例断言后会回写状态与日志尾部。
     */
    @Test
    public void shouldRunCurrentCaseAssertionAndExposeAssertionLogs() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 CSS 二级页", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "语义状态=自动通过"));
        Assert.assertTrue(containsText(texts, "当前样例日志 tail"));
        Assert.assertTrue(containsText(texts, "pass | 自动断言通过"));
        Assert.assertTrue(containsText(texts, "sampleBg=0xFF334155"));
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
        Assert.assertTrue(containsText(texts, "Minecraft=1.7.10"));
        Assert.assertTrue(containsText(texts, "字体 epoch=1"));
        Assert.assertTrue(containsText(texts, "窗口尺寸=960x540"));
        Assert.assertTrue(containsText(texts, "鼠标=12,34"));
        Assert.assertTrue(containsText(texts, "网络传输模式=vanilla"));
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

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final MutableRuntimeView runtimeView = new MutableRuntimeView();
        private final UiTestDocumentPageController controller = new UiTestDocumentPageController(documentUi,
                pageSurface, runtimeView);
    }

    private static final class MutableRuntimeView implements DocumentPageRuntimeView {

        private int hostWidth;
        private int hostHeight;
        private int mouseX;
        private int mouseY;

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
            return UiRuntimeStats.empty();
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
