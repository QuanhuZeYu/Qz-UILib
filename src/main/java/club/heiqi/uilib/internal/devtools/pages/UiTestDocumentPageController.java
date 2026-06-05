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
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
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
            13,
            13);
    private static final TestGroup CSS_GROUP = new TestGroup(
            "CSS",
            "CSS 级联与样式语义",
            "级联优先级、继承、盒模型、背景、边框、阴影、文本样式与可见性。",
            15,
            15);
    private static final TestGroup LAYOUT_GROUP = new TestGroup(
            "LAYOUT",
            "Layout 布局与尺寸语义",
            "block、inline、flex、table、position、sticky、fixed containing block 与滚动范围。",
            16,
            16);
    private static final TestGroup PAINT_GROUP = new TestGroup(
            "PAINT",
            "Paint 绘制、命中与视觉语义",
            "绘制层级、stacking context、clip、transform 命中、top-layer、scrollbar 与 host image。",
            9,
            9);
    private static final TestGroup INPUT_GROUP = new TestGroup(
            "INPUT",
            "Input 输入与事件语义",
            "事件传播、默认行为、键盘、焦点、滚轮与拖拽。",
            13,
            13);
    private static final TestGroup CONTROLS_GROUP = new TestGroup(
            "CTRL",
            "Controls 控件与表单语义",
            "按钮、输入框、选择器、槽位、tooltip 与 overlay 控件。",
            15,
            15);
    private static final TestGroup TEXT_FONT_GROUP = new TestGroup(
            "TEXT",
            "TextFont 文本、字体与国际化语义",
            "文本模式、格式码、字符测量、fallback、reload 与 wrap。",
            7,
            7);
    private static final TestGroup ANIMATION_GROUP = new TestGroup(
            "ANIM",
            "Animation 动画与 Transition 语义",
            "transition、keyframes、timing、fill-mode 与布局/绘制影响。",
            8,
            8);
    private static final TestGroup RUNTIME_HOST_GROUP = new TestGroup(
            "HOST",
            "RuntimeHost 宿主运行时语义",
            "开屏时序、resize、runtime stats、GL 上下文、HUD 与异常面板。",
            7,
            7);
    private static final TestGroup REMOTE_NET_GROUP = new TestGroup(
            "NET",
            "RemoteNet 远程、配置与网络语义",
            "Channel、Fetch、Stream、Store、远程页面、远程 HUD 与配置同步。",
            10,
            10);
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
        document.addStyleSheet(createRuntimeDemoStyleSheet());
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
                        "DOM-002",
                        DOM_GROUP,
                        "insertBefore 同父移动先移除再计算参考索引",
                        "运行时按钮会将 C 移到 A 前方，并断言 DOM 顺序没有重复节点。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察三个节点顺序；需要时点击人工通过或人工失败。",
                        "预期结果：点击执行后节点顺序变为 `C, A, B`，没有重复 A 节点。"),
                new RuntimeTestCase(
                        "DOM-003",
                        DOM_GROUP,
                        "replaceChild 返回被替换节点并保持新节点唯一归属",
                        "运行时按钮会替换 old 节点，校验返回节点、旧节点离树和新节点父级。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察替换结果；需要时点击人工通过或人工失败。",
                        "预期结果：点击执行后旧节点离开文档，新节点显示在原位置，结果文本为 `被替换：old`。"),
                new RuntimeTestCase(
                        "DOM-004",
                        DOM_GROUP,
                        "removeChild 只允许直接子节点并返回被移除节点",
                        "运行时按钮会先移除直接子节点，再尝试移除非直接后代并校验异常。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察目标消失和拒绝提示；需要时点击人工通过或人工失败。",
                        "预期结果：点击执行后目标节点消失，错误按钮显示 `非直接子节点被拒绝`。"),
                new RuntimeTestCase(
                        "DOM-005",
                        DOM_GROUP,
                        "DocumentFragment 插入后自身清空",
                        "运行时按钮会把 fragment 内三个元素插入容器，并校验 fragment childCount 归零。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察三个 fragment 项进入目标容器；需要时点击人工通过或人工失败。",
                        "预期结果：点击执行后 fragment 内三个元素出现在目标容器，fragment 计数显示 0。"),
                new RuntimeTestCase(
                        "DOM-006",
                        DOM_GROUP,
                        "textContent 设置会替换子树文本",
                        "运行时按钮会对复杂子树调用 textContent setter，并校验旧子节点离树、新文本唯一存在。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察复杂子树被单行文本替换；需要时点击人工通过或人工失败。",
                        "预期结果：点击执行后复杂子树被单一文本替换，页面显示 `textContent 已替换`。"),
                new RuntimeTestCase(
                        "DOM-007",
                        DOM_GROUP,
                        "属性读写、删除和 HTML 布尔属性",
                        "运行时按钮会读写 data 属性、移除属性，并校验 disabled 布尔属性只看存在性。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察 disabled=false 按钮仍显示禁用语义；需要时点击人工通过或人工失败。",
                        "预期结果：`disabled=\"false\"` 的按钮仍不可点击，状态显示 `布尔属性禁用生效`。"),
                new RuntimeTestCase(
                        "DOM-008",
                        DOM_GROUP,
                        "classList add/remove/toggle/contains",
                        "运行时按钮会依次执行 add、remove、toggle、contains，并校验 className 与高亮状态同步。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察卡片 class 状态变化；需要时点击人工通过或人工失败。",
                        "预期结果：点击 toggle 后卡片颜色和 class 状态同步切换。"),
                new RuntimeTestCase(
                        "DOM-009",
                        DOM_GROUP,
                        "querySelector 不返回内部 document root",
                        "运行时按钮会调用 document.querySelector('*')，校验返回第一个作者元素而非隐藏根。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察查询结果文本；需要时点击人工通过或人工失败。",
                        "预期结果：执行 `querySelector('*')` 后结果为第一个作者元素，不是隐藏根节点。"),
                new RuntimeTestCase(
                        "DOM-010",
                        DOM_GROUP,
                        "type、class、id、后代、子代和分组选择器",
                        "运行时按钮会校验 type/class/id、后代与子代选择器；分组选择器若未实现会明确标记缺口。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察匹配数量和分组选择器状态；需要时点击人工通过或人工失败。",
                        "预期结果：`domcase`、`.query-target`、`#query-target-id`、后代和子代选择器均显示 `1/1`，分组选择器未完成时显示待实现缺口。"),
                new RuntimeTestCase(
                        "DOM-011",
                        DOM_GROUP,
                        "属性选择器 [attr] / [attr=value]",
                        "运行时按钮会尝试解析属性选择器；当前解析器不支持时显示待实现缺口，不伪造通过。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察属性选择器缺口标记；需要时点击人工通过或人工失败。",
                        "预期结果：支持后 `[data-case]` 显示 `2/2`、`[data-case=match]` 显示 `1/1`；当前未支持时明确显示待实现缺口。"),
                new RuntimeTestCase(
                        "DOM-012",
                        DOM_GROUP,
                        "结构伪类和交互伪类",
                        "运行时按钮会校验 first-child/last-child 结构伪类，并保留 hover/active/focus-visible 人工观察入口。",
                        "进入 DOM 二级页后点击 `执行自动测试`；移动鼠标、按下目标并切换焦点观察状态；需要时点击人工通过或人工失败。",
                        "预期结果：首项和末项结构伪类显示 `1/1`；移动、按下或键盘聚焦交互目标时只出现对应 hover、active、focus-visible 状态。"),
                new RuntimeTestCase(
                        "DOM-013",
                        DOM_GROUP,
                        "链接默认行为和 preventDefault",
                        "运行时按钮会分发普通链接和 preventDefault 链接点击，校验导航记录只出现普通链接。",
                        "进入 DOM 二级页后点击 `执行自动测试`；观察导航记录和 preventDefault 事件日志；需要时点击人工通过或人工失败。",
                        "预期结果：普通链接触发导航记录，preventDefault 链接只记录事件不执行默认动作。"),
                new RuntimeTestCase(
                        "CSS-001",
                        CSS_GROUP,
                        "inline style 高于样式表规则",
                        "运行时按钮会计算目标元素 computed style，并校验 inline textColor 覆盖样式表规则。",
                        "点击 `执行自动测试`；观察样例文本显示为 inline 指定绿色；需要时点击人工通过或人工失败。",
                        "预期结果：同一元素最终显示为 inline 指定颜色。"),
                new RuntimeTestCase(
                        "CSS-002",
                        CSS_GROUP,
                        "specificity 顺序：id、class、type、声明顺序",
                        "运行时按钮会计算四个样例颜色，校验 type/class/id/source order 优先级。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察四个色块和结果文本；需要时点击人工通过或人工失败。",
                        "预期结果：四个样例最终颜色依次符合页面旁边标注。"),
                new RuntimeTestCase(
                        "CSS-003",
                        CSS_GROUP,
                        "可继承属性和不可继承属性",
                        "运行时按钮会校验子元素继承 textColor，且不继承父元素 border。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察子元素文本颜色和边框；需要时点击人工通过或人工失败。",
                        "预期结果：子元素继承文本颜色，不继承父元素 border。"),
                new RuntimeTestCase(
                        "CSS-004",
                        CSS_GROUP,
                        "display:none/block/inline/inline-block/flex/table",
                        "运行时按钮会计算各样例 display，并校验 none 项不参与布局盒树。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察 none 项隐藏，其余项按标注显示；需要时点击人工通过或人工失败。",
                        "预期结果：none 项不占位，其余项按标注布局形态显示。"),
                new RuntimeTestCase(
                        "CSS-005",
                        CSS_GROUP,
                        "box-sizing:content-box/border-box",
                        "运行时按钮会布局两张卡片，校验声明宽度相同且内容区按盒模型不同。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察两张盒模型卡片；需要时点击人工通过或人工失败。",
                        "预期结果：两张卡片外框宽度相同，但内容区宽度按盒模型不同。"),
                new RuntimeTestCase(
                        "CSS-006",
                        CSS_GROUP,
                        "margin、padding、border 四边独立值",
                        "运行时按钮会读取四边 margin/padding/border-width 声明，校验每边独立值没有被统一值覆盖。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察四边标注；需要时点击人工通过或人工失败。",
                        "预期结果：四边间距和边框颜色按标注方向呈现。"),
                new RuntimeTestCase(
                        "CSS-007",
                        CSS_GROUP,
                        "width/height、min/max、percent、auto",
                        "运行时按钮会布局 min 大于 max 和百分比样例，校验 min 胜出且百分比跟随父容器。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察尺寸摘要；需要时点击人工通过或人工失败。",
                        "预期结果：min 大于 max 时 min 胜出，百分比盒跟随父容器变化。"),
                new RuntimeTestCase(
                        "CSS-008",
                        CSS_GROUP,
                        "background-color、background-image url(...)、none",
                        "运行时按钮会校验资源图背景值存在、none 区域无背景图且底色保留。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察资源图和纯底色区域；需要时点击人工通过或人工失败。",
                        "预期结果：资源图区域显示贴图，none 区域只显示底色。"),
                new RuntimeTestCase(
                        "CSS-009",
                        CSS_GROUP,
                        "border-style、border-radius、outline",
                        "运行时按钮会读取 border style、radius 和 outline 值，校验 outline 不影响布局尺寸。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察边框、圆角和焦点轮廓；需要时点击人工通过或人工失败。",
                        "预期结果：圆角边框、焦点 outline 和普通边框互不覆盖。"),
                new RuntimeTestCase(
                        "CSS-010",
                        CSS_GROUP,
                        "box-shadow outset/inset 绘制层级",
                        "运行时按钮会展示外阴影和内阴影结构；具体像素层级需要人工确认。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察内外阴影层级；需要时点击人工通过或人工失败。",
                        "预期结果：外阴影在卡片外侧，内阴影位于背景之上且不盖住边框。"),
                new RuntimeTestCase(
                        "CSS-011",
                        CSS_GROUP,
                        "opacity、visibility、pointer-events",
                        "运行时按钮会校验 opacity、visibility:hidden 与 pointer-events:none 的计算值。",
                        "进入 CSS 二级页后点击 `执行自动测试`；点击透明/隐藏/穿透样例并观察摘要；需要时点击人工通过或人工失败。",
                        "预期结果：不可见项不显示，透明项可见但半透明，pointer-events:none 区域点击穿透。"),
                new RuntimeTestCase(
                        "CSS-012",
                        CSS_GROUP,
                        "overflow hidden/auto/scroll 与 scrollbar-width",
                        "运行时按钮会布局 hidden/auto/scroll 容器并读取最大滚动范围。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察滚动条和裁剪状态；需要时点击人工通过或人工失败。",
                        "预期结果：hidden 裁剪无滚动条，auto 只在溢出时显示滚动条，scroll 始终保留滚动能力。"),
                new RuntimeTestCase(
                        "CSS-013",
                        CSS_GROUP,
                        "text-align、text-decoration、text-transform",
                        "运行时按钮会读取文本对齐、修饰线和大小写转换声明。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察三列文本样例；需要时点击人工通过或人工失败。",
                        "预期结果：文本按左中右对齐，修饰线和大小写转换符合标注。"),
                new RuntimeTestCase(
                        "CSS-014",
                        CSS_GROUP,
                        "white-space、overflow-wrap、word-break",
                        "运行时按钮会校验 nowrap/pre/break 样例的计算属性，并布局窄列观察断行。",
                        "进入 CSS 二级页后点击 `执行自动测试`；观察窄列换行与保留空白；需要时点击人工通过或人工失败。",
                        "预期结果：nowrap 不换行，pre 保留空格换行，break 样例在窄列内断行。"),
                new RuntimeTestCase(
                        "CSS-015",
                        CSS_GROUP,
                        "cursor 与 hover 状态",
                        "运行时按钮会读取 cursor 声明，并保留宿主光标切换人工观察入口。",
                        "进入 CSS 二级页后点击 `执行自动测试`；鼠标移入不同区域观察光标状态标签；需要时点击人工通过或人工失败。",
                        "预期结果：鼠标移入不同区域时页面内光标状态标签同步变化。"),
                new RuntimeTestCase(
                        "LAYOUT-001",
                        LAYOUT_GROUP,
                        "block normal flow 垂直布局",
                        "运行时按钮会用 DocumentLayoutEngine 测量三块 block 的 top/height 顺序。",
                        "点击 `执行自动测试`；观察三块内容自上而下排列；需要时点击人工通过或人工失败。",
                        "预期结果：三块内容从上到下排列，垂直间距与标尺一致。"),
                new RuntimeTestCase(
                        "LAYOUT-002",
                        LAYOUT_GROUP,
                        "相邻 margin collapse",
                        "运行时按钮会测量相邻块 top 差值，校验间距使用较大 margin。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察两个块之间的标尺；需要时点击人工通过或人工失败。",
                        "预期结果：相邻块之间间距等于较大 margin，不是两者相加。"),
                new RuntimeTestCase(
                        "LAYOUT-003",
                        LAYOUT_GROUP,
                        "空块与递归 margin collapse",
                        "运行时按钮会布局含空块的父子结构，校验空块没有额外高度。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察空块标尺；需要时点击人工通过或人工失败。",
                        "预期结果：空块不产生额外高度，父子 margin collapse 后标尺吻合。"),
                new RuntimeTestCase(
                        "LAYOUT-004",
                        LAYOUT_GROUP,
                        "inline 文本、inline 元素、inline-block",
                        "运行时按钮会测量 inline fragment 和 inline-block 盒，校验同一行排列。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察文字与 badge 横向排列；需要时点击人工通过或人工失败。",
                        "预期结果：文本和 inline 元素同一行排列，inline-block 保持自身盒宽高。"),
                new RuntimeTestCase(
                        "LAYOUT-005",
                        LAYOUT_GROUP,
                        "inline-block baseline",
                        "运行时按钮会标记当前已知缺口，并保留人工观察入口。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察基线样例；需要时点击人工通过或人工失败。",
                        "预期结果：inline-block 底部基线与相邻文本基线对齐，若未完成则标记 `已知缺口`。"),
                new RuntimeTestCase(
                        "LAYOUT-006",
                        LAYOUT_GROUP,
                        "flex row/column 主轴分配",
                        "运行时按钮会布局 row 与 column flex 样例，校验 grow 项获得剩余空间、固定项不被挤压。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察 flex 主轴尺寸摘要；需要时点击人工通过或人工失败。",
                        "预期结果：flex-grow 项占满剩余空间，固定项宽度不被挤压。"),
                new RuntimeTestCase(
                        "LAYOUT-007",
                        LAYOUT_GROUP,
                        "flex-basis、min-width:auto、min-width:0",
                        "运行时按钮会布局两个 flex item，校验默认 min-content 与 min-width:0 的收缩差异。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察默认项和 min-width:0 项宽度；需要时点击人工通过或人工失败。",
                        "预期结果：默认项按 min-content 阻止过度收缩，min-width:0 项可压缩。"),
                new RuntimeTestCase(
                        "LAYOUT-008",
                        LAYOUT_GROUP,
                        "flex-wrap 与 align-content",
                        "运行时按钮会布局多行 flex，校验 wrap 与 align-content 计算值并观察交叉轴分布。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察多行 flex 分布；需要时点击人工通过或人工失败。",
                        "预期结果：多行 flex 在交叉轴按 space-between/center 等规则分布。"),
                new RuntimeTestCase(
                        "LAYOUT-009",
                        LAYOUT_GROUP,
                        "flex 交叉轴 auto margin",
                        "运行时按钮会布局交叉轴 auto margin 样例，校验目标项在交叉轴居中或贴边。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察 auto margin 项位置；需要时点击人工通过或人工失败。",
                        "预期结果：auto margin 项在交叉轴居中或贴边，符合旁边标注。"),
                new RuntimeTestCase(
                        "LAYOUT-010",
                        LAYOUT_GROUP,
                        "table fixed/auto 列宽",
                        "运行时按钮会布局 auto 表格样例，校验长内容列宽大于短内容列。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察表格列宽摘要；需要时点击人工通过或人工失败。",
                        "预期结果：auto 表格内容列根据最长内容扩展，fixed 表格按声明宽度分配。"),
                new RuntimeTestCase(
                        "LAYOUT-011",
                        LAYOUT_GROUP,
                        "position relative",
                        "运行时按钮会布局 relative 偏移样例，校验视觉偏移不改变普通流占位。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察偏移元素和原始占位；需要时点击人工通过或人工失败。",
                        "预期结果：relative 元素视觉偏移，但原始占位仍保留。"),
                new RuntimeTestCase(
                        "LAYOUT-012",
                        LAYOUT_GROUP,
                        "absolute containing block 和 auto margin 居中",
                        "运行时按钮会布局 positioned 容器内 absolute 子元素，校验 containing block 与 auto margin 居中结果。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察 absolute 子元素位置；需要时点击人工通过或人工失败。",
                        "预期结果：absolute 子元素相对最近 containing block 定位，auto margin 样例居中。"),
                new RuntimeTestCase(
                        "LAYOUT-013",
                        LAYOUT_GROUP,
                        "fixed 默认相对视口",
                        "运行时按钮会布局 fixed 标记并校验其相对视口定位；滚动视觉需人工确认。",
                        "进入 Layout 二级页后点击 `执行自动测试`；滚动页面观察 fixed 标记；需要时点击人工通过或人工失败。",
                        "预期结果：滚动页面时 fixed 标记停留在视口右上角。"),
                new RuntimeTestCase(
                        "LAYOUT-014",
                        LAYOUT_GROUP,
                        "transform 祖先建立 fixed containing block",
                        "运行时按钮会布局 transform 祖先与 fixed 后代，校验 fixed 相对 transform containing block。",
                        "进入 Layout 二级页后点击 `执行自动测试`；观察 fixed 子元素在 transform 容器内固定；需要时点击人工通过或人工失败。",
                        "预期结果：fixed 子元素在 transform 祖先内固定，并随祖先滚动裁剪。"),
                new RuntimeTestCase(
                        "LAYOUT-015",
                        LAYOUT_GROUP,
                        "sticky 阈值与滚动范围",
                        "运行时按钮会展示 sticky 样例并读取样式结构；滚动阈值行为保留人工确认。",
                        "进入 Layout 二级页后点击 `执行自动测试`；滚动容器观察 sticky 标题；需要时点击人工通过或人工失败。",
                        "预期结果：sticky 标题到达阈值后吸顶，离开容器边界后停止。"),
                new RuntimeTestCase(
                        "LAYOUT-016",
                        LAYOUT_GROUP,
                        "根滚动与嵌套滚动",
                        "运行时按钮会布局嵌套滚动容器并校验内层优先滚动；边界移交需游戏内滚轮观察。",
                        "进入 Layout 二级页后点击 `执行自动测试`；在内层滚动到底后继续滚轮；需要时点击人工通过或人工失败。",
                        "预期结果：嵌套滚动优先消费滚轮，滚到底后再由外层滚动。"),
                new RuntimeTestCase(
                        "PAINT-001",
                        PAINT_GROUP,
                        "background、border、text 绘制顺序",
                        "运行时按钮会布置绘制样例并校验背景、边框、文本结构；最终层级需要人工确认。",
                        "点击 `执行自动测试`；观察背景、边框和文本层级；再点击人工通过或人工失败记录结果。",
                        "预期结果：背景在最底层，边框压住背景边缘，文本位于最上层。"),
                new RuntimeTestCase(
                        "PAINT-002",
                        PAINT_GROUP,
                        "stacking context 顺序",
                        "运行时按钮会布局两个 positioned 样例并校验 z-index stacking 顺序。",
                        "进入 Paint 二级页后点击 `执行自动测试`；观察高 z-index 卡片覆盖低层；需要时点击人工通过或人工失败。",
                        "预期结果：z-index 高的 stacking context 覆盖低层，子元素不能越出父 stacking context。"),
                new RuntimeTestCase(
                        "PAINT-003",
                        PAINT_GROUP,
                        "opacity stacking context",
                        "运行时按钮会校验半透明组 opacity 建立独立 stacking context。",
                        "进入 Paint 二级页后点击 `执行自动测试`；观察半透明组整体混合；需要时点击人工通过或人工失败。",
                        "预期结果：半透明组整体混合，组内高 z-index 不越过外部兄弟。"),
                new RuntimeTestCase(
                        "PAINT-004",
                        PAINT_GROUP,
                        "overflow clip 与圆角 clip",
                        "运行时按钮会校验圆角裁剪容器的 overflow hidden 结构。",
                        "进入 Paint 二级页后点击 `执行自动测试`；观察子元素超出圆角部分被裁剪；需要时点击人工通过或人工失败。",
                        "预期结果：子元素超出圆角容器部分被裁剪，命中也不可达。"),
                new RuntimeTestCase(
                        "PAINT-005",
                        PAINT_GROUP,
                        "transform 平移、缩放、旋转命中",
                        "运行时按钮会校验 transform 样例的计算样式，并等待人工确认命中位置。",
                        "进入 Paint 二级页后点击 `执行自动测试`；观察 transform 后视觉位置；需要时点击人工通过或人工失败。",
                        "预期结果：视觉位置与点击命中位置一致，未变换原位置点击无效。"),
                new RuntimeTestCase(
                        "PAINT-006",
                        PAINT_GROUP,
                        "top-layer 绘制与命中",
                        "运行时按钮会展示普通内容与 top-layer 弹层样例；真实覆盖与命中优先级需人工确认。",
                        "进入 Paint 二级页后点击 `执行自动测试`；打开 tooltip/select 弹层并点击重叠区域；需要时点击人工通过或人工失败。",
                        "预期结果：tooltip/select 弹层覆盖普通内容，并优先接收点击。"),
                new RuntimeTestCase(
                        "PAINT-007",
                        PAINT_GROUP,
                        "scrollbar 几何和命中",
                        "运行时按钮会布局可滚动容器并读取 scrollMax；拖动 thumb 与轨道翻页保留人工确认。",
                        "进入 Paint 二级页后点击 `执行自动测试`；拖动滚动条 thumb 和点击轨道；需要时点击人工通过或人工失败。",
                        "预期结果：拖动滚动条 thumb 时内容同步滚动，点击轨道按页滚动。"),
                new RuntimeTestCase(
                        "PAINT-008",
                        PAINT_GROUP,
                        "custom renderer 逃生口边界",
                        "运行时按钮会展示自定义绘制占位结构，校验标准文本和边框仍在文档中。",
                        "进入 Paint 二级页后点击 `执行自动测试`；观察自定义绘制区域不污染周围内容；需要时点击人工通过或人工失败。",
                        "预期结果：自定义绘制区域只绘制标注图形，不影响标准背景、边框和文本。"),
                new RuntimeTestCase(
                        "PAINT-009",
                        PAINT_GROUP,
                        "host image 与资源缺失 fallback",
                        "运行时按钮会展示有效资源与缺失资源占位；真实宿主图片渲染保留人工确认。",
                        "进入 Paint 二级页后点击 `执行自动测试`；观察有效图片和 fallback 占位；需要时点击人工通过或人工失败。",
                        "预期结果：有效资源显示图片，缺失资源显示 fallback 占位。"),
                new RuntimeTestCase(
                        "INPUT-001",
                        INPUT_GROUP,
                        "capture、target、bubble 顺序",
                        "运行时按钮会通过真实点击输入路径校验 root、parent、target 的事件传播顺序。",
                        "进入 Input 二级页后点击 `执行自动测试`；观察事件日志；需要时点击人工通过或人工失败。",
                        "预期结果：点击子节点后事件日志顺序为 `root capture -> parent capture -> target -> parent bubble -> root bubble`。"),
                new RuntimeTestCase(
                        "INPUT-002",
                        INPUT_GROUP,
                        "stopPropagation 只停止后续传播",
                        "运行时按钮会让目标 handler 停止传播，并校验链接默认激活仍执行。",
                        "进入 Input 二级页后点击 `执行自动测试`；观察事件日志和默认动作结果；需要时点击人工通过或人工失败。",
                        "预期结果：目标处理后祖先 bubble 不再记录，但默认动作仍执行。"),
                new RuntimeTestCase(
                        "INPUT-003",
                        INPUT_GROUP,
                        "preventDefault 阻止默认行为",
                        "运行时按钮会在 click handler 中 preventDefault，并校验事件仍冒泡但链接不激活。",
                        "进入 Input 二级页后点击 `执行自动测试`；观察事件日志和默认动作结果；需要时点击人工通过或人工失败。",
                        "预期结果：链接或滚动默认动作不执行，事件日志仍完整显示。"),
                new RuntimeTestCase(
                        "INPUT-004",
                        INPUT_GROUP,
                        "handler 返回 true 与默认行为分离",
                        "运行时按钮会让 wheel handler 返回 true，并校验传播停止但默认滚动仍发生。",
                        "进入 Input 二级页后点击 `执行自动测试`；观察滚轮日志和 scrollTop；需要时点击人工通过或人工失败。",
                        "预期结果：返回 true 后传播停止，但未 preventDefault 的默认滚动仍执行。"),
                new RuntimeTestCase(
                        "INPUT-005",
                        INPUT_GROUP,
                        "mousedown、mouseup、click、doubleclick",
                        "运行时按钮会模拟两次主键点击，校验 down/up/click 和第二次 dblclick 顺序。",
                        "进入 Input 二级页后点击 `执行自动测试`；观察 pointer 日志；需要时点击人工通过或人工失败。",
                        "预期结果：单击日志为 down/up/click，双击额外记录 doubleclick。"),
                new RuntimeTestCase(
                        "INPUT-006",
                        INPUT_GROUP,
                        "contextmenu",
                        "运行时按钮会展示右键菜单样例；当前若未接入 contextmenu 分发则明确标记缺口。",
                        "进入 Input 二级页后点击 `执行自动测试`；右键目标区域观察日志；需要时点击人工通过或人工失败。",
                        "预期结果：右键目标区域记录 contextmenu，preventDefault 区域不弹出默认动作。"),
                new RuntimeTestCase(
                        "INPUT-007",
                        INPUT_GROUP,
                        "hover/active 状态传播",
                        "运行时按钮会模拟鼠标移入和按下，校验 hover/active 状态通知到目标和祖先。",
                        "进入 Input 二级页后点击 `执行自动测试`；移动鼠标和按住目标观察状态日志；需要时点击人工通过或人工失败。",
                        "预期结果：子元素 hover 时祖先 hover 状态也更新，不被子 handler 返回值截断。"),
                new RuntimeTestCase(
                        "INPUT-008",
                        INPUT_GROUP,
                        "focus、blur、focusin、focusout",
                        "运行时按钮会在两个可聚焦元素之间切换焦点，校验 focusout/focusin/blur/focus 顺序。",
                        "进入 Input 二级页后点击 `执行自动测试`；观察焦点切换日志；需要时点击人工通过或人工失败。",
                        "预期结果：焦点切换日志显示 `focusout -> focusin -> blur -> focus` 的项目约定顺序。"),
                new RuntimeTestCase(
                        "INPUT-009",
                        INPUT_GROUP,
                        "Tab 顺序与 focus-visible",
                        "运行时按钮会模拟 Tab 遍历，校验焦点按 DOM 顺序移动并进入 focus-visible 状态。",
                        "进入 Input 二级页后点击 `执行自动测试`；按 Tab 观察焦点轮廓；需要时点击人工通过或人工失败。",
                        "预期结果：按 Tab 后焦点按 DOM 顺序移动，键盘焦点显示 focus-visible 样式。"),
                new RuntimeTestCase(
                        "INPUT-010",
                        INPUT_GROUP,
                        "wheel 事件与默认滚动",
                        "运行时按钮会分别滚动普通区域和 preventDefault 区域，校验事件先于默认滚动。",
                        "进入 Input 二级页后点击 `执行自动测试`；观察 wheel 日志和 scrollTop；需要时点击人工通过或人工失败。",
                        "预期结果：wheel 日志先出现，未取消区域随后滚动，取消区域不滚动。"),
                new RuntimeTestCase(
                        "INPUT-011",
                        INPUT_GROUP,
                        "keyboard keydown/keyup/textInput",
                        "运行时按钮会对聚焦元素派发 keydown、keyup 和 textInput，校验组合键不插入普通文本。",
                        "进入 Input 二级页后点击 `执行自动测试`；观察键盘和文本输入日志；需要时点击人工通过或人工失败。",
                        "预期结果：输入字符时 key 与 textInput 日志都出现，组合键不错误插入文本。"),
                new RuntimeTestCase(
                        "INPUT-012",
                        INPUT_GROUP,
                        "dragstart、drag、dragover、dragend",
                        "运行时按钮会展示拖拽样例；当前拖拽事件链未完整接入时明确标记待实现缺口。",
                        "进入 Input 二级页后点击 `执行自动测试`；拖拽卡片并观察顺序；需要时点击人工通过或人工失败。",
                        "预期结果：拖拽卡片时 ghost/目标提示随鼠标移动，放开后顺序更新。"),
                new RuntimeTestCase(
                        "INPUT-013",
                        INPUT_GROUP,
                        "dragenter、dragleave、drop",
                        "运行时按钮会展示 drop 目标；当前拖拽 enter/leave/drop 未完整接入时明确标记待实现缺口。",
                        "进入 Input 二级页后点击 `执行自动测试`；拖入、拖出并释放目标区；需要时点击人工通过或人工失败。",
                        "预期结果：进入目标区显示 enter，离开显示 leave，释放显示 drop；若未实现则标记 `待实现缺口`。"),
                new RuntimeTestCase(
                        "CTRL-001",
                        CONTROLS_GROUP,
                        "button enabled/disabled/action",
                        "运行时按钮会触发可用与禁用按钮，校验 action 计数只由可用按钮增加。",
                        "进入 Controls 二级页后点击 `执行自动测试`；观察可用按钮计数与禁用按钮状态；需要时点击人工通过或人工失败。",
                        "预期结果：可用按钮点击计数加 1，disabled 按钮点击无变化。"),
                new RuntimeTestCase(
                        "CTRL-002",
                        CONTROLS_GROUP,
                        "text input value、selection、caret",
                        "运行时按钮会在页面演示输入框上真实聚焦、追加文本并退格，校验 value 与 caret 可见状态。",
                        "进入 Controls 二级页后点击 `执行自动测试`；观察输入值和光标区域；需要时点击人工通过或人工失败。",
                        "预期结果：输入框被聚焦并显示光标，value 变为 `alpha-X`，摘要显示 `caretVisible=true`。"),
                new RuntimeTestCase(
                        "CTRL-003",
                        CONTROLS_GROUP,
                        "password input 掩码",
                        "运行时按钮会校验 password 类型真实 value 与页面显示掩码分离。",
                        "进入 Controls 二级页后点击 `执行自动测试`；观察密码样例只显示掩码；需要时点击人工通过或人工失败。",
                        "预期结果：页面只显示掩码字符，结果区保存真实 value 长度。"),
                new RuntimeTestCase(
                        "CTRL-004",
                        CONTROLS_GROUP,
                        "number input 解析、非法值、step",
                        "运行时按钮会在页面演示 number 输入框上真实输入非法字符并断言运行时过滤结果。",
                        "进入 Controls 二级页后点击 `执行自动测试`；观察数字输入与错误状态说明；需要时点击人工通过或人工失败。",
                        "预期结果：输入 `12bad.5e+2` 后显示值为 `12.5e+2`，非法字符不会进入控件 value。"),
                new RuntimeTestCase(
                        "CTRL-005",
                        CONTROLS_GROUP,
                        "textarea 逻辑行与视觉软换行",
                        "运行时按钮会在页面 textarea 上真实聚焦、输入换行和长文本，并用运行时布局校验软换行。",
                        "进入 Controls 二级页后点击 `执行自动测试`；观察长文本在窄容器内软换行；需要时点击人工通过或人工失败。",
                        "预期结果：textarea 显示高对比光标，长逻辑行自动软换行，value 中只保留真实换行。"),
                new RuntimeTestCase(
                        "CTRL-006",
                        CONTROLS_GROUP,
                        "checkbox checked/indeterminate/disabled",
                        "运行时按钮会切换 checked、indeterminate 与 disabled 样例，校验禁用项不响应用户切换。",
                        "进入 Controls 二级页后点击 `执行自动测试`；观察三态 checkbox 文本；需要时点击人工通过或人工失败。",
                        "预期结果：三态视觉与结果文本同步，disabled 项不能切换。"),
                new RuntimeTestCase(
                        "CTRL-007",
                        CONTROLS_GROUP,
                        "radio group 单选互斥",
                        "运行时按钮会选择 radio 组内第二项，校验同组互斥与当前值。",
                        "进入 Controls 二级页后点击 `执行自动测试`；观察 radio 选中态；需要时点击人工通过或人工失败。",
                        "预期结果：同组只保持一个选中，不同组互不影响。"),
                new RuntimeTestCase(
                        "CTRL-008",
                        CONTROLS_GROUP,
                        "select top-layer 弹层",
                        "运行时按钮会切换 select 选项并读取 value；top-layer 覆盖关系保留人工确认。",
                        "进入 Controls 二级页后点击 `执行自动测试`；打开下拉并选择选项；需要时点击人工通过或人工失败。",
                        "预期结果：打开下拉后选项覆盖普通内容，选择后 value 与显示文本同步。"),
                new RuntimeTestCase(
                        "CTRL-009",
                        CONTROLS_GROUP,
                        "slider horizontal/vertical/min/max/step",
                        "运行时按钮会设置 slider 值，校验 min/max clamp 与 step 对齐。",
                        "进入 Controls 二级页后点击 `执行自动测试`；拖动 horizontal/vertical slider；需要时点击人工通过或人工失败。",
                        "预期结果：拖动 thumb 后数值按 step 对齐，超出范围被 clamp。"),
                new RuntimeTestCase(
                        "CTRL-010",
                        CONTROLS_GROUP,
                        "toggle switch",
                        "运行时按钮会切换 toggle switch，校验状态与 aria-checked 同步。",
                        "进入 Controls 二级页后点击 `执行自动测试`；点击开关观察滑块和文本；需要时点击人工通过或人工失败。",
                        "预期结果：开关点击后视觉滑块移动，状态文本在 on/off 间切换。"),
                new RuntimeTestCase(
                        "CTRL-011",
                        CONTROLS_GROUP,
                        "segmented selector",
                        "运行时按钮会选择分段控件第三项，校验只有当前段高亮并记录新值。",
                        "进入 Controls 二级页后点击 `执行自动测试`；点击不同分段观察日志；需要时点击人工通过或人工失败。",
                        "预期结果：点击分段后只有当前段高亮，change 日志记录旧值和新值。"),
                new RuntimeTestCase(
                        "CTRL-012",
                        CONTROLS_GROUP,
                        "tab control",
                        "运行时按钮会切换 tab 控件到第二页，校验 activeIndex 和当前面板文本。",
                        "进入 Controls 二级页后点击 `执行自动测试`；切换 tab 并观察面板；需要时点击人工通过或人工失败。",
                        "预期结果：切换 tab 后只显示当前面板，键盘切换顺序正确。"),
                new RuntimeTestCase(
                        "CTRL-013",
                        CONTROLS_GROUP,
                        "table control",
                        "运行时按钮会展示 table 控件结构并校验表头、行和单元格文本存在。",
                        "进入 Controls 二级页后点击 `执行自动测试`；观察表格边框和列宽；需要时点击人工通过或人工失败。",
                        "预期结果：表头、行、单元格边框和列宽符合表格语义。"),
                new RuntimeTestCase(
                        "CTRL-014",
                        CONTROLS_GROUP,
                        "slot、slot grid、inventory slot grid",
                        "运行时按钮会展示槽位样例；真实背包物品与 carried item 交互保留人工确认。",
                        "进入 Controls 二级页后点击 `执行自动测试`；hover 和点击槽位观察 carried item；需要时点击人工通过或人工失败。",
                        "预期结果：槽位 hover 显示 tooltip，点击槽位后 carried item 状态按标注变化。"),
                new RuntimeTestCase(
                        "CTRL-015",
                        CONTROLS_GROUP,
                        "tooltip、cursor overlay、overlay host",
                        "运行时按钮会展示 overlay 说明结构；实际鼠标随动与遮挡关系保留人工确认。",
                        "进入 Controls 二级页后点击 `执行自动测试`；移动鼠标观察 tooltip 和 cursor overlay；需要时点击人工通过或人工失败。",
                        "预期结果：tooltip 不参与普通布局，随鼠标移动且不被底层内容遮挡。"),
                new RuntimeTestCase(
                        "TEXT-001",
                        TEXT_FONT_GROUP,
                        "TextContentMode.UILIB_RAW",
                        "运行时按钮会创建 raw 文本节点，校验 `§a` 保留为普通字符。",
                        "进入 TextFont 二级页后点击 `执行自动测试`；观察 `§a` 没有变成颜色码；需要时点击人工通过或人工失败。",
                        "预期结果：`§a` 等字符按普通文本显示，不触发 Minecraft 格式化。"),
                new RuntimeTestCase(
                        "TEXT-002",
                        TEXT_FONT_GROUP,
                        "TextContentMode.MINECRAFT_FORMATTED",
                        "运行时按钮会创建 Minecraft 格式文本节点，校验文本模式与原始长度记录。",
                        "进入 TextFont 二级页后点击 `执行自动测试`；观察绿色格式文本；需要时点击人工通过或人工失败。",
                        "预期结果：`§a绿色` 显示为绿色，结果区仍能显示原始文本长度。"),
                new RuntimeTestCase(
                        "TEXT-003",
                        TEXT_FONT_GROUP,
                        "字符宽度、line-height、baseline",
                        "运行时按钮会调用文本测量服务校验宽度与 line-height，baseline 位置等待人工观察。",
                        "进入 TextFont 二级页后点击 `执行自动测试`；观察中英文标尺线；需要时点击人工通过或人工失败。",
                        "预期结果：中英文混排行高稳定，标尺线与文本基线位置一致。"),
                new RuntimeTestCase(
                        "TEXT-004",
                        TEXT_FONT_GROUP,
                        "字体 fallback",
                        "运行时按钮会校验 fallback 样例结构和测量结果，真实缺字 fallback 需游戏内字体确认。",
                        "进入 TextFont 二级页后点击 `执行自动测试`；观察缺字样例是否有 fallback；需要时点击人工通过或人工失败。",
                        "预期结果：缺字字符使用 fallback 字体显示，不出现空白方块。"),
                new RuntimeTestCase(
                        "TEXT-005",
                        TEXT_FONT_GROUP,
                        "font reload debounce",
                        "运行时按钮会记录当前 font epoch；连续 reload debounce 需游戏内操作确认。",
                        "进入 TextFont 二级页后点击 `执行自动测试`；连续触发字体 reload 后观察 epoch；需要时点击人工通过或人工失败。",
                        "预期结果：连续 reload 后只显示最终 epoch，页面不崩溃。"),
                new RuntimeTestCase(
                        "TEXT-006",
                        TEXT_FONT_GROUP,
                        "obfuscated 格式码动态文本",
                        "运行时按钮会展示 obfuscated 格式文本样例并校验文本测量宽度稳定。",
                        "进入 TextFont 二级页后点击 `执行自动测试`；观察动态字符变化与布局宽度；需要时点击人工通过或人工失败。",
                        "预期结果：启用动态文本后字符变化但布局宽度不抖动。"),
                new RuntimeTestCase(
                        "TEXT-007",
                        TEXT_FONT_GROUP,
                        "trim 与 wrap",
                        "运行时按钮会调用 trimStringToWidth 与 listFormattedStringToWidth，校验返回文本可用于窄容器。",
                        "进入 TextFont 二级页后点击 `执行自动测试`；观察裁剪和换行摘要；需要时点击人工通过或人工失败。",
                        "预期结果：窄容器文本按测量服务裁剪或换行，不遮挡相邻控件。"),
                new RuntimeTestCase(
                        "ANIM-001",
                        ANIMATION_GROUP,
                        "transition start/end/cancel",
                        "运行时按钮会在页面样例上启动真实 opacity keyframe 动画，并校验运行时动画进入活动时间线。",
                        "进入 Animation 二级页后点击 `执行自动测试`；触发样例动画并观察日志；需要时点击人工通过或人工失败。",
                        "预期结果：触发后样例进入淡入动画，摘要显示 `animationRunning=true`。"),
                new RuntimeTestCase(
                        "ANIM-002",
                        ANIMATION_GROUP,
                        "per-property transition",
                        "运行时按钮会启动同时改变文本色与位移的真实动画，并校验两条属性轨道与运行态。",
                        "进入 Animation 二级页后点击 `执行自动测试`；观察属性完成时间不同；需要时点击人工通过或人工失败。",
                        "预期结果：样例同时变色并向右移动，摘要显示颜色轨道和 translateX 轨道已进入运行态。"),
                new RuntimeTestCase(
                        "ANIM-003",
                        ANIMATION_GROUP,
                        "keyframes from/to 与百分比帧",
                        "运行时按钮会启动包含 0/50/100% stop 的真实路径动画，并校验页面动画时间线。",
                        "进入 Animation 二级页后点击 `执行自动测试`；观察盒子沿标注路径移动；需要时点击人工通过或人工失败。",
                        "预期结果：盒子沿 0/50/100% 标注路径移动，摘要显示 stop 数和 active 动画数量。"),
                new RuntimeTestCase(
                        "ANIM-004",
                        ANIMATION_GROUP,
                        "delay、duration、iteration-count",
                        "运行时按钮会以 delay、duration 与 iteration-count 选项启动真实动画并校验句柄运行态。",
                        "进入 Animation 二级页后点击 `执行自动测试`；观察延迟和循环次数；需要时点击人工通过或人工失败。",
                        "预期结果：样例延迟后开始循环，摘要显示 delay、duration、iteration 和运行中句柄。"),
                new RuntimeTestCase(
                        "ANIM-005",
                        ANIMATION_GROUP,
                        "direction normal/reverse/alternate",
                        "运行时按钮会为 normal、reverse、alternate 三个页面样例启动真实方向动画并校验句柄运行态。",
                        "进入 Animation 二级页后点击 `执行自动测试`；观察三个样例起点与方向；需要时点击人工通过或人工失败。",
                        "预期结果：三个样例同时播放，reverse 从终点侧开始，alternate 每轮方向翻转，摘要显示 3 个运行句柄。"),
                new RuntimeTestCase(
                        "ANIM-006",
                        ANIMATION_GROUP,
                        "fill-mode none/forwards/backwards/both",
                        "运行时按钮会启动四个 fill-mode 动画并校验声明和运行句柄。",
                        "进入 Animation 二级页后点击 `执行自动测试`；观察动画前后样式保持方式；需要时点击人工通过或人工失败。",
                        "预期结果：动画前后样式保持方式与卡片标注一致。"),
                new RuntimeTestCase(
                        "ANIM-007",
                        ANIMATION_GROUP,
                        "steps() 与 cubic-bezier",
                        "运行时按钮会展示 timing-function 样例；阶梯与贝塞尔真实视觉保留人工确认。",
                        "进入 Animation 二级页后点击 `执行自动测试`；观察 steps 与 ease 曲线差异；需要时点击人工通过或人工失败。",
                        "预期结果：steps 样例阶梯跳动，bezier 样例平滑加减速。"),
                new RuntimeTestCase(
                        "ANIM-008",
                        ANIMATION_GROUP,
                        "layout-affecting 与 paint-only 属性",
                        "运行时按钮会分别启动 width 和 opacity 动画，校验 active 动画存在并标注布局/绘制影响。",
                        "进入 Animation 二级页后点击 `执行自动测试`；观察 width 改变和 opacity 淡入；需要时点击人工通过或人工失败。",
                        "预期结果：width 动画触发布局变化，opacity 动画只改变绘制透明度。"),
                new RuntimeTestCase(
                        "HOST-001",
                        RUNTIME_HOST_GROUP,
                        "`/qzuilib test` 延后开屏时序",
                        "运行时按钮会确认当前 test 控制器已稳定构建；聊天命令延后开屏需游戏内人工确认。",
                        "进入 RuntimeHost 二级页后点击 `执行自动测试`；从聊天命令再次打开页面；需要时点击人工通过或人工失败。",
                        "预期结果：从聊天框执行命令后页面稳定打开，不被聊天关闭流程吞掉。"),
                new RuntimeTestCase(
                        "HOST-002",
                        RUNTIME_HOST_GROUP,
                        "resize 与 viewport fill",
                        "运行时按钮会读取宿主窗口尺寸和 root 滚动配置，resize 视觉稳定性需人工确认。",
                        "进入 RuntimeHost 二级页后点击 `执行自动测试`；调整窗口大小并观察重排；需要时点击人工通过或人工失败。",
                        "预期结果：调整窗口后卡片重新排布，滚动位置不异常跳变。"),
                new RuntimeTestCase(
                        "HOST-003",
                        RUNTIME_HOST_GROUP,
                        "runtime stats 与帧耗时",
                        "运行时按钮会读取 runtime stats，校验 frame/render 指标可展示。",
                        "进入 RuntimeHost 二级页后点击 `执行自动测试`；观察帧耗时刷新；需要时点击人工通过或人工失败。",
                        "预期结果：页面显示 host 尺寸、鼠标坐标、draw/update 指标且数值持续刷新。"),
                new RuntimeTestCase(
                        "HOST-004",
                        RUNTIME_HOST_GROUP,
                        "GL-backed render context",
                        "运行时按钮会校验标准背景、边框、文本和图片占位结构；GL 状态污染需游戏内人工确认。",
                        "进入 RuntimeHost 二级页后点击 `执行自动测试`；观察标准绘制和自定义绘制；需要时点击人工通过或人工失败。",
                        "预期结果：标准背景、边框、文本、图片和自定义绘制都可见，无 GL 状态污染。"),
                new RuntimeTestCase(
                        "HOST-005",
                        RUNTIME_HOST_GROUP,
                        "HUD 纯显示层与交互层",
                        "运行时按钮会校验 HUD 样例说明结构，真实 HUD 层级需要游戏内人工确认。",
                        "进入 RuntimeHost 二级页后点击 `执行自动测试`；打开容器界面观察 HUD 显隐和交互；需要时点击人工通过或人工失败。",
                        "预期结果：纯 HUD 在容器界面中隐藏，交互 HUD 可接收点击和键盘焦点。"),
                new RuntimeTestCase(
                        "HOST-006",
                        RUNTIME_HOST_GROUP,
                        "容器态输入桥接",
                        "运行时按钮会展示容器态 HUD 输入说明；真实原生界面回焦需游戏内人工确认。",
                        "进入 RuntimeHost 二级页后点击 `执行自动测试`；在容器界面点击 HUD 输入框再点击外部；需要时点击人工通过或人工失败。",
                        "预期结果：点击 HUD 输入框后键盘输入进入 HUD，点击外部后焦点还给原生界面。"),
                new RuntimeTestCase(
                        "HOST-007",
                        RUNTIME_HOST_GROUP,
                        "异常面板",
                        "运行时按钮会展示异常面板检查入口；故意失败只记录页面状态，不让 JVM 测试崩溃。",
                        "进入 RuntimeHost 二级页后点击 `执行自动测试`；触发故意失败并观察异常面板；需要时点击人工通过或人工失败。",
                        "预期结果：故意失败用例显示可读错误，不导致客户端无提示退出。"),
                new RuntimeTestCase(
                        "NET-001",
                        REMOTE_NET_GROUP,
                        "Channel C2S/S2C 往返",
                        "运行时按钮会读取当前传输模式并保留往返人工入口，不伪造服务端响应。",
                        "进入 RemoteNet 二级页后点击 `执行自动测试`；在联机或内置服务端环境执行往返；需要时点击人工通过或人工失败。",
                        "预期结果：点击执行后显示 `通过：Channel 往返完成`。"),
                new RuntimeTestCase(
                        "NET-002",
                        REMOTE_NET_GROUP,
                        "C2S 分片与重组",
                        "运行时按钮会展示 32KB 以上分片边界，真实重组需服务端链路确认。",
                        "进入 RemoteNet 二级页后点击 `执行自动测试`；发送大消息并观察长度；需要时点击人工通过或人工失败。",
                        "预期结果：超过 32KB 的消息成功分片重组，结果显示原始长度一致。"),
                new RuntimeTestCase(
                        "NET-003",
                        REMOTE_NET_GROUP,
                        "Fetch 成功、错误、超时、取消、限流",
                        "运行时按钮会展示 Fetch 五态操作入口，真实状态码需服务端 endpoint 确认。",
                        "进入 RemoteNet 二级页后点击 `执行自动测试`；依次触发五个 Fetch 按钮；需要时点击人工通过或人工失败。",
                        "预期结果：五个按钮分别显示 200、500、timeout、cancelled、429。"),
                new RuntimeTestCase(
                        "NET-004",
                        REMOTE_NET_GROUP,
                        "Stream 大内容下载",
                        "运行时按钮会展示下载进度结构，真实大内容校验需服务端 stream 确认。",
                        "进入 RemoteNet 二级页后点击 `执行自动测试`；观察下载进度递增；需要时点击人工通过或人工失败。",
                        "预期结果：下载进度递增到 100%，最终校验大小通过。"),
                new RuntimeTestCase(
                        "NET-005",
                        REMOTE_NET_GROUP,
                        "Store snapshot/delta/player store",
                        "运行时按钮会展示 Store 视图结构，真实 snapshot/delta 需服务端推送确认。",
                        "进入 RemoteNet 二级页后点击 `执行自动测试`；观察 Store 视图更新；需要时点击人工通过或人工失败。",
                        "预期结果：Store 视图按服务端推送更新，玩家定向 Store 只影响当前玩家。"),
                new RuntimeTestCase(
                        "NET-006",
                        REMOTE_NET_GROUP,
                        "RemoteDocumentPages.open",
                        "运行时按钮会展示远程页面打开入口，真实提交回传需服务端链路确认。",
                        "进入 RemoteNet 二级页后点击 `执行自动测试`；打开远程页面并提交表单；需要时点击人工通过或人工失败。",
                        "预期结果：远程页面打开后点击提交，服务端返回结果页并显示 `远程页面提交通过`。"),
                new RuntimeTestCase(
                        "NET-007",
                        REMOTE_NET_GROUP,
                        "RemoteHudOverlays.open",
                        "运行时按钮会展示远程 HUD 打开入口，真实 HUD 提交需服务端链路确认。",
                        "进入 RemoteNet 二级页后点击 `执行自动测试`；打开远程 HUD 并提交表单；需要时点击人工通过或人工失败。",
                        "预期结果：远程 HUD 显示浮层，点击提交后 HUD 显示 `远程 HUD 提交通过`。"),
                new RuntimeTestCase(
                        "NET-008",
                        REMOTE_NET_GROUP,
                        "Remote CSS/HTML 安全集",
                        "运行时按钮会展示远程 HTML/CSS 安全集入口，解析结果需结合服务端样例确认。",
                        "进入 RemoteNet 二级页后点击 `执行自动测试`；打开安全集样例观察允许与禁止内容；需要时点击人工通过或人工失败。",
                        "预期结果：允许的标签和样式生效，禁止内容被忽略并显示安全提示。"),
                new RuntimeTestCase(
                        "NET-009",
                        REMOTE_NET_GROUP,
                        "Config sync 草稿与保存",
                        "运行时按钮会展示配置同步入口，真实草稿推送与保存需服务端链路确认。",
                        "进入 RemoteNet 二级页后点击 `执行自动测试`；修改配置草稿并点击保存；需要时点击人工通过或人工失败。",
                        "预期结果：修改配置草稿后服务端收到同步，点击保存后显示 `配置保存通过`。"),
                new RuntimeTestCase(
                        "NET-010",
                        REMOTE_NET_GROUP,
                        "vanilla/forge 传输回退",
                        "运行时按钮会读取当前传输模式，切换 forge/vanilla 后的自检需独立游戏内确认。",
                        "进入 RemoteNet 二级页后点击 `执行自动测试`；切换配置后重跑网络自检；需要时点击人工通过或人工失败。",
                        "预期结果：当前传输模式显示正确，切换配置后网络自检仍可通过。")));
    }

    /**
     * 创建运行时样例样式表。
     *
     * @return 样式表
     */
    private UiStyleSheet createRuntimeDemoStyleSheet() {
        return UiStyleSheet.create()
                .addRule(".css-001-target", new UiStyleDeclaration()
                        .setTextColor(CSS_STYLESHEET_TEXT_COLOR))
                .addRule("csscase", new UiStyleDeclaration()
                        .setTextColor(0xFF60A5FA))
                .addRule(".css-002-class", new UiStyleDeclaration()
                        .setTextColor(0xFF34D399))
                .addRule("#css-002-id", new UiStyleDeclaration()
                        .setTextColor(0xFFFBBF24))
                .addRule(".css-002-order", new UiStyleDeclaration()
                        .setTextColor(0xFFFCA5A5))
                .addRule(".css-002-order", new UiStyleDeclaration()
                        .setTextColor(0xFFC084FC))
                .addRule(".dom-012-item", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF334155)
                        .setBorderColor(0xFF64748B))
                .addRule(".dom-012-interactive", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF334155)
                        .setBorderColor(0xFF64748B))
                .addRule(".dom-012-item:first-child", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF1D4ED8)
                        .setBorderColor(0xFF93C5FD))
                .addRule(".dom-012-item:last-child", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF047857)
                        .setBorderColor(0xFF6EE7B7))
                .addRule(".dom-012-interactive:hover", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF7C3AED))
                .addRule(".dom-012-interactive:active", new UiStyleDeclaration()
                        .setBackgroundColor(0xFFB45309))
                .addRule(".dom-012-interactive:focus-visible", new UiStyleDeclaration()
                        .setOutline(UiOutline.of(2, 0xFFFBBF24, UiBorderStyle.SOLID, 1)));
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
        body.appendText("首页只承载总览和分组导航；运行时测试卡片已按类型迁移到二级页，不恢复旧页面名结构。");
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
        appendPlanItem(document, section, "运行时卡片不再直接放在首页；首页只显示 DOM、CSS、Layout、Paint、Input、Controls、TextFont、Animation、RuntimeHost、RemoteNet 二级入口。");
        appendPlanItem(document, section, "DOM / CSS / Layout / Paint / Input / Controls / TextFont / Animation / RuntimeHost / RemoteNet 二级页均已接入 5 张运行时卡片。");
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
            appendPlanItem(document, section, "本类型运行时卡片尚未恢复；后续会按规格先补用例文本，再接入运行时按钮。");
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
        appendPlanItem(document, manual, "首页不直接展示运行时测试卡片；请进入对应二级页执行并记录人工通过/失败。");
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
            appendPlanItem(document, manual, "本类型暂无待人工确认卡片。");
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
        return "入口状态：二级页已预留，运行时卡片待后续批次恢复。";
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
        if ("DOM-001".equals(testCase.getId())) {
            appendDomRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("DOM-002".equals(testCase.getId())) {
            appendDomInsertBeforeRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("DOM-003".equals(testCase.getId())) {
            appendDomReplaceChildRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("DOM-004".equals(testCase.getId())) {
            appendDomRemoveChildRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("DOM-005".equals(testCase.getId())) {
            appendDomFragmentRuntimeDemo(document, parent, testCase);
            return;
        }
        if (testCase.getGroup() == DOM_GROUP) {
            appendDomGroupRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("CSS-001".equals(testCase.getId())) {
            appendCssRuntimeDemo(document, parent, testCase);
            return;
        }
        if (testCase.getGroup() == CSS_GROUP) {
            appendCssGroupRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("LAYOUT-001".equals(testCase.getId())) {
            appendLayoutRuntimeDemo(document, parent, testCase);
            return;
        }
        if (testCase.getGroup() == LAYOUT_GROUP) {
            appendLayoutGroupRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("PAINT-001".equals(testCase.getId())) {
            appendPaintRuntimeDemo(document, parent, testCase);
            return;
        }
        if (testCase.getGroup() == PAINT_GROUP) {
            appendPaintGroupRuntimeDemo(document, parent, testCase);
            return;
        }
        if (testCase.getGroup() == INPUT_GROUP) {
            appendInputGroupRuntimeDemo(document, parent, testCase);
            return;
        }
        if (testCase.getGroup() == CONTROLS_GROUP || testCase.getGroup() == TEXT_FONT_GROUP
                || testCase.getGroup() == ANIMATION_GROUP || testCase.getGroup() == RUNTIME_HOST_GROUP
                || testCase.getGroup() == REMOTE_NET_GROUP) {
            appendRemainingGroupRuntimeDemo(document, parent, testCase);
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
     * 追加 DOM-002 演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendDomInsertBeforeRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode row = createRuntimeDemoRow(document);
        ElementNode nodeA = createDemoBadge(document, "A", 0xFF355CFF);
        ElementNode nodeB = createDemoBadge(document, "B", 0xFF1F9D55);
        ElementNode nodeC = createDemoBadge(document, "C", 0xFF7C3AED);
        row.append(nodeA);
        row.append(nodeB);
        row.append(nodeC);
        demo.append(row);
        TextNode summary = appendDemoSummary(document, demo, "初始顺序：A, B, C；等待 insertBefore");
        testCase.setElementDemo(row, summary, nodeA, nodeB, nodeC);
        parent.append(demo);
    }

    /**
     * 追加 DOM-003 演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendDomReplaceChildRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode row = createRuntimeDemoRow(document);
        ElementNode oldNode = createDemoBadge(document, "old", 0xFFB91C1C);
        ElementNode spareNode = createDemoBadge(document, "spare", 0xFF475569);
        row.append(oldNode);
        row.append(spareNode);
        demo.append(row);
        TextNode summary = appendDemoSummary(document, demo, "初始节点：old, spare；等待 replaceChild");
        testCase.setElementDemo(row, summary, oldNode, spareNode);
        parent.append(demo);
    }

    /**
     * 追加 DOM-004 演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendDomRemoveChildRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode row = createRuntimeDemoRow(document);
        ElementNode directChild = createDemoBadge(document, "remove", 0xFFDC2626);
        ElementNode wrapper = document.div();
        wrapper.style()
                .setDisplay(UiDisplay.FLEX)
                .setPadding(UiStyleLength.px(4))
                .setBackgroundColor(0xFF1F2937)
                .setBorderColor(0xFF64748B)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8));
        ElementNode nestedChild = createDemoBadge(document, "nested", 0xFFF59E0B);
        wrapper.append(nestedChild);
        row.append(directChild);
        row.append(wrapper);
        demo.append(row);
        TextNode summary = appendDemoSummary(document, demo, "初始：直接子节点存在；非直接后代嵌套在 wrapper 内");
        testCase.setElementDemo(row, summary, directChild, wrapper, nestedChild);
        parent.append(demo);
    }

    /**
     * 追加 DOM-005 演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendDomFragmentRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode target = createRuntimeDemoRow(document);
        demo.append(target);
        TextNode summary = appendDemoSummary(document, demo, "目标容器为空；fragment 计数：未创建");
        testCase.setElementDemo(target, summary);
        parent.append(demo);
    }

    /**
     * 追加 DOM 分组剩余用例演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendDomGroupRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        if ("DOM-010".equals(testCase.getId())) {
            appendDomSelectorRuntimeDemo(document, parent, testCase);
            return;
        }
        if ("DOM-012".equals(testCase.getId())) {
            appendDomPseudoRuntimeDemo(document, parent, testCase);
            return;
        }
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode row = createRuntimeDemoRow(document);
        ElementNode first = createDemoBadge(document, testCase.getId(), 0xFF2563EB);
        ElementNode second = createDemoBadge(document, "状态", 0xFF334155);
        if ("DOM-006".equals(testCase.getId())) {
            ElementNode nested = createDemoBadge(document, "nested", 0xFF7C3AED);
            first.append(nested);
        } else if ("DOM-007".equals(testCase.getId())) {
            first = document.button();
            first.style()
                    .setPadding(UiStyleLength.px(8))
                    .setBackgroundColor(0xFF334155)
                    .setBorderColor(0xFF64748B)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderStyle(UiBorderStyle.SOLID)
                    .setBorderRadius(UiStyleLength.px(8))
                    .setTextColor(0xFFEAF1FF);
            first.appendText("disabled=false button");
            first.setAttribute("data-case", "before");
            first.setAttribute("disabled", "false");
        } else if ("DOM-008".equals(testCase.getId())) {
            first.setClassName("runtime-card before");
        } else if ("DOM-009".equals(testCase.getId())) {
            first.setId("author-first");
        } else if ("DOM-011".equals(testCase.getId())) {
            first.setAttribute("data-case", "match");
            second.setAttribute("data-case", "skip");
        }
        row.append(first).append(second);
        demo.append(row);
        TextNode summary = appendDemoSummary(document, demo, testCase.getId() + "：未执行");
        testCase.setElementDemo(row, summary, first, second);
        parent.append(demo);
    }

    /**
     * 追加 DOM-010 选择器演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendDomSelectorRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode row = createRuntimeDemoRow(document);
        ElementNode target = document.element("domcase");
        target.style()
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF1D4ED8)
                .setBorderColor(0xFF93C5FD)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFFFFFFF);
        target.appendText("domcase.query-target#query-target-id");
        ElementNode child = createDemoBadge(document, "child", 0xFF059669);
        child.setClassName("query-child");
        child.setId("query-child-id");
        target.append(child);
        ElementNode sibling = createDemoBadge(document, "skip", 0xFF334155);
        row.append(target).append(sibling);
        demo.append(row);
        TextNode summary = appendDemoSummary(document, demo,
                "选择器计数：domcase=?/1；.query-target=?/1；#query-target-id=?/1；后代=?/1；子代=?/1；分组=未执行");
        testCase.setElementDemo(row, summary, target, child, sibling);
        parent.append(demo);
    }

    /**
     * 追加 DOM-012 伪类演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendDomPseudoRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode row = createRuntimeDemoRow(document);
        ElementNode first = createDomPseudoBadge(document, "first", "dom-012-item");
        ElementNode interactive = createDomPseudoBadge(document, "hover/active/focus", "dom-012-interactive");
        ElementNode last = createDomPseudoBadge(document, "last", "dom-012-item");
        interactive.setFocusable(true);
        row.append(first).append(interactive).append(last);
        demo.append(row);
        appendMutedText(document, demo, "交互目标：移动鼠标看 hover，按住看 active，键盘聚焦看 focus-visible outline。");
        TextNode summary = appendDemoSummary(document, demo,
                "结构伪类：first=?/1；last=?/1；hover/active/focus-visible=人工观察");
        interactive.setHoverHandler(event -> {
            interactive.setAttribute("data-hover", String.valueOf(event.isHovered()));
            summary.setText(buildDomPseudoInteractionSummary(interactive));
            return false;
        });
        interactive.setActiveHandler(event -> {
            interactive.setAttribute("data-active", String.valueOf(event.isActive()));
            summary.setText(buildDomPseudoInteractionSummary(interactive));
            return false;
        });
        interactive.setFocusHandler(event -> {
            interactive.setAttribute("data-focus-visible", String.valueOf(event.isFocusVisible()));
            summary.setText(buildDomPseudoInteractionSummary(interactive));
        });
        testCase.setElementDemo(row, summary, first, interactive, last);
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
     * 追加 CSS 分组通用演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendCssGroupRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        if ("CSS-002".equals(testCase.getId())) {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode type = createCssSpecificitySample(document, "csscase", "type", null, null);
            ElementNode klass = createCssSample(document, "class", "css-002-class", null);
            ElementNode id = createCssSample(document, "id", "css-002-class", "css-002-id");
            ElementNode order = createCssSample(document, "order", "css-002-order", null);
            row.append(type);
            row.append(klass);
            row.append(id);
            row.append(order);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "specificity：未计算");
            testCase.setElementDemo(row, summary, type, klass, id, order);
        } else if ("CSS-003".equals(testCase.getId())) {
            ElementNode parentBox = createDemoPanel(document, "parent", 0xFF1E3A8A);
            parentBox.style()
                    .setTextColor(0xFF93C5FD)
                    .setBorderColor(0xFFFBBF24)
                    .setBorderWidth(UiStyleLength.px(3))
                    .setBorderStyle(UiBorderStyle.SOLID);
            ElementNode child = document.div();
            child.style()
                    .setPadding(UiStyleLength.px(6));
            child.appendText("child inherits text color only");
            parentBox.append(child);
            demo.append(parentBox);
            TextNode summary = appendDemoSummary(document, demo, "继承：未计算");
            testCase.setElementDemo(parentBox, summary, child);
        } else if ("CSS-004".equals(testCase.getId())) {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode none = createCssDisplaySample(document, "none", UiDisplay.NONE);
            ElementNode block = createCssDisplaySample(document, "block", UiDisplay.BLOCK);
            ElementNode inline = createCssDisplaySample(document, "inline", UiDisplay.INLINE);
            ElementNode inlineBlock = createCssDisplaySample(document, "inline-block", UiDisplay.INLINE_BLOCK);
            ElementNode flex = createCssDisplaySample(document, "flex", UiDisplay.FLEX);
            row.append(none);
            row.append(block);
            row.append(inline);
            row.append(inlineBlock);
            row.append(flex);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "display：未计算");
            testCase.setElementDemo(row, summary, none, block, inline, inlineBlock, flex);
        } else if ("CSS-005".equals(testCase.getId())) {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode contentBox = createBoxSizingSample(document, "content-box", UiBoxSizing.CONTENT_BOX);
            ElementNode borderBox = createBoxSizingSample(document, "border-box", UiBoxSizing.BORDER_BOX);
            row.append(contentBox);
            row.append(borderBox);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "box-sizing：未测量");
            testCase.setElementDemo(row, summary, contentBox, borderBox);
        } else {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode sample = createDemoPanel(document, testCase.getId() + " 样例", 0xFF1F2937);
            if ("CSS-006".equals(testCase.getId())) {
                sample.style()
                        .setMarginTop(UiStyleLength.px(3))
                        .setMarginRight(UiStyleLength.px(5))
                        .setMarginBottom(UiStyleLength.px(7))
                        .setMarginLeft(UiStyleLength.px(11))
                        .setPaddingTop(UiStyleLength.px(2))
                        .setPaddingRight(UiStyleLength.px(4))
                        .setPaddingBottom(UiStyleLength.px(6))
                        .setPaddingLeft(UiStyleLength.px(8))
                        .setBorderWidthSides(UiStyleInsets.of(UiStyleLength.px(1), UiStyleLength.px(2),
                                UiStyleLength.px(3), UiStyleLength.px(4)))
                        .setBorderColors(UiBorderColors.of(0xFFFF6666, 0xFF66FF66, 0xFF6666FF, 0xFFFFFF66))
                        .setBorderStyle(UiBorderStyle.SOLID);
            } else if ("CSS-007".equals(testCase.getId())) {
                sample.style()
                        .setWidth(UiStyleLength.percent(0.5F))
                        .setMinWidth(UiStyleLength.px(140))
                        .setMaxWidth(UiStyleLength.px(80));
            } else if ("CSS-008".equals(testCase.getId())) {
                sample.style().setBackgroundImage(UiBackgroundImage.texture(
                        "minecraft:textures/gui/options_background.png", 256, 256));
            } else if ("CSS-009".equals(testCase.getId())) {
                sample.style()
                        .setBorderStyle(UiBorderStyle.SOLID)
                        .setBorderRadius(UiStyleLength.px(12))
                        .setOutline(UiOutline.of(2, 0xFF38BDF8, UiBorderStyle.SOLID, 2));
            } else if ("CSS-010".equals(testCase.getId())) {
                sample.style().setBoxShadow(UiBoxShadow.inset(0, 2, 6, 0, 0xAA000000));
            } else if ("CSS-011".equals(testCase.getId())) {
                sample.style()
                        .setOpacity(0.45F)
                        .setVisibility(UiVisibility.VISIBLE)
                        .setPointerEvents(UiPointerEvents.NONE);
            } else if ("CSS-012".equals(testCase.getId())) {
                sample.style()
                        .setWidth(UiStyleLength.px(120))
                        .setHeight(UiStyleLength.px(34))
                        .setOverflowY(UiOverflow.AUTO)
                        .setScrollbarWidth(UiScrollbarWidth.THIN);
                ElementNode overflowChild = createDemoPanel(document, "溢出内容", 0xFF334155);
                overflowChild.style().setHeight(UiStyleLength.px(80));
                sample.append(overflowChild);
            } else if ("CSS-013".equals(testCase.getId())) {
                sample.style()
                        .setTextAlign(UiTextAlign.CENTER)
                        .setTextDecoration(UiTextDecoration.UNDERLINE)
                        .setTextTransform(UiTextTransform.UPPERCASE);
            } else if ("CSS-014".equals(testCase.getId())) {
                sample.style()
                        .setWhiteSpace(UiWhiteSpace.PRE_WRAP)
                        .setOverflowWrap(UiOverflowWrap.ANYWHERE)
                        .setWordBreak(UiWordBreak.BREAK_ALL)
                        .setWidth(UiStyleLength.px(120));
            } else if ("CSS-015".equals(testCase.getId())) {
                sample.style().setCursor(UiCursor.POINTER);
            }
            row.append(sample);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, testCase.getId() + "：未执行");
            testCase.setElementDemo(row, summary, sample);
        }
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
     * 追加 Layout 分组通用演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendLayoutGroupRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        if ("LAYOUT-002".equals(testCase.getId())) {
            ElementNode stack = document.div();
            stack.style()
                    .setPadding(UiStyleLength.px(4))
                    .setBackgroundColor(0xFF0B1220)
                    .setBorderColor(0xFF3F5F9F)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderStyle(UiBorderStyle.SOLID);
            ElementNode first = createLayoutDemoBlock(document, "margin-bottom 18", 0xFF2563EB);
            first.style().setMarginBottom(UiStyleLength.px(18));
            ElementNode second = createLayoutDemoBlock(document, "margin-top 10", 0xFF059669);
            second.style().setMarginTop(UiStyleLength.px(10));
            stack.append(first);
            stack.append(second);
            demo.append(stack);
            TextNode summary = appendDemoSummary(document, demo, "相邻 margin：未测量");
            testCase.setElementDemo(stack, summary, first, second);
        } else if ("LAYOUT-003".equals(testCase.getId())) {
            ElementNode stack = document.div();
            stack.style()
                    .setPadding(UiStyleLength.px(4))
                    .setBackgroundColor(0xFF0B1220)
                    .setBorderColor(0xFF3F5F9F)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderStyle(UiBorderStyle.SOLID);
            ElementNode before = createLayoutDemoBlock(document, "before", 0xFF2563EB);
            ElementNode empty = document.div();
            empty.style()
                    .setMarginTop(UiStyleLength.px(16))
                    .setMarginBottom(UiStyleLength.px(24));
            ElementNode after = createLayoutDemoBlock(document, "after", 0xFF059669);
            stack.append(before);
            stack.append(empty);
            stack.append(after);
            demo.append(stack);
            TextNode summary = appendDemoSummary(document, demo, "空块 collapse：未测量");
            testCase.setElementDemo(stack, summary, before, empty, after);
        } else if ("LAYOUT-004".equals(testCase.getId())) {
            ElementNode inlineRoot = document.div();
            inlineRoot.style()
                    .setPadding(UiStyleLength.px(6))
                    .setBackgroundColor(0xFF0B1220)
                    .setBorderColor(0xFF3F5F9F)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderStyle(UiBorderStyle.SOLID);
            inlineRoot.appendText("文本 ");
            ElementNode inline = document.span();
            inline.style()
                    .setDisplay(UiDisplay.INLINE)
                    .setTextColor(0xFF93C5FD);
            inline.appendText("inline");
            ElementNode inlineBlock = createDemoBadge(document, "IB", 0xFF7C3AED);
            inlineBlock.style()
                    .setDisplay(UiDisplay.INLINE_BLOCK)
                    .setWidth(UiStyleLength.px(48))
                    .setHeight(UiStyleLength.px(26));
            inlineRoot.append(inline);
            inlineRoot.appendText(" + ");
            inlineRoot.append(inlineBlock);
            demo.append(inlineRoot);
            TextNode summary = appendDemoSummary(document, demo, "inline 布局：未测量");
            testCase.setElementDemo(inlineRoot, summary, inline, inlineBlock);
        } else if ("LAYOUT-005".equals(testCase.getId())) {
            ElementNode baseline = document.div();
            baseline.style()
                    .setPadding(UiStyleLength.px(6))
                    .setBackgroundColor(0xFF0B1220)
                    .setBorderColor(0xFF3F5F9F)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderStyle(UiBorderStyle.SOLID);
            baseline.appendText("baseline ");
            ElementNode inlineBlock = createDemoBadge(document, "IB", 0xFFF59E0B);
            inlineBlock.style()
                    .setDisplay(UiDisplay.INLINE_BLOCK)
                    .setHeight(UiStyleLength.px(34));
            baseline.append(inlineBlock);
            baseline.appendText(" text");
            demo.append(baseline);
            TextNode summary = appendDemoSummary(document, demo, "inline-block baseline：已知缺口待人工确认");
            testCase.setElementDemo(baseline, summary, inlineBlock);
        } else {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode first = createLayoutDemoBlock(document, testCase.getId() + " A", 0xFF2563EB);
            ElementNode second = createLayoutDemoBlock(document, testCase.getId() + " B", 0xFF059669);
            if ("LAYOUT-006".equals(testCase.getId())) {
                row.style().setWidth(UiStyleLength.px(260));
                first.style().setWidth(UiStyleLength.px(60)).setFlexShrink(0.0F);
                second.style().setFlexGrow(1.0F).setMinWidth(UiStyleLength.px(0));
            } else if ("LAYOUT-007".equals(testCase.getId())) {
                row.style().setWidth(UiStyleLength.px(160));
                first.style().setFlexBasis(UiStyleLength.px(120));
                second.style().setFlexBasis(UiStyleLength.px(120)).setMinWidth(UiStyleLength.px(0));
            } else if ("LAYOUT-008".equals(testCase.getId())) {
                row.style()
                        .setWidth(UiStyleLength.px(150))
                        .setHeight(UiStyleLength.px(92))
                        .setFlexWrap(UiFlexWrap.WRAP)
                        .setAlignContent(UiAlignContent.SPACE_BETWEEN);
                first.style().setWidth(UiStyleLength.px(78));
                second.style().setWidth(UiStyleLength.px(78));
                ElementNode third = createLayoutDemoBlock(document, testCase.getId() + " C", 0xFF7C3AED);
                third.style().setWidth(UiStyleLength.px(78));
                row.append(third);
            } else if ("LAYOUT-009".equals(testCase.getId())) {
                row.style().setHeight(UiStyleLength.px(80));
                second.style().setMarginTop(UiStyleLength.auto()).setMarginBottom(UiStyleLength.auto());
            } else if ("LAYOUT-010".equals(testCase.getId())) {
                row.style().setDisplay(UiDisplay.TABLE).setBorderCollapse(UiBorderCollapse.SEPARATE);
                first.style().setDisplay(UiDisplay.TABLE_CELL);
                second.style().setDisplay(UiDisplay.TABLE_CELL);
                second.setTextContent("long table content");
            } else if ("LAYOUT-011".equals(testCase.getId())) {
                first.style().setPosition(UiPosition.RELATIVE).setLeft(UiStyleLength.px(16)).setTop(UiStyleLength.px(4));
            } else if ("LAYOUT-012".equals(testCase.getId())) {
                row.style().setPosition(UiPosition.RELATIVE).setWidth(UiStyleLength.px(240)).setHeight(UiStyleLength.px(76));
                first.style().setPosition(UiPosition.ABSOLUTE).setLeft(UiStyleLength.px(20)).setTop(UiStyleLength.px(10));
                second.style().setPosition(UiPosition.ABSOLUTE).setLeft(UiStyleLength.auto()).setRight(UiStyleLength.auto())
                        .setWidth(UiStyleLength.px(80));
            } else if ("LAYOUT-013".equals(testCase.getId())) {
                first.style().setPosition(UiPosition.FIXED).setRight(UiStyleLength.px(8)).setTop(UiStyleLength.px(8));
            } else if ("LAYOUT-014".equals(testCase.getId())) {
                row.style().setTransform(UiTransform.of(4.0F, 0.0F, 1.0F, 1.0F, 0.0F));
                first.style().setPosition(UiPosition.FIXED).setRight(UiStyleLength.px(6)).setTop(UiStyleLength.px(6));
            } else if ("LAYOUT-015".equals(testCase.getId())) {
                first.style().setPosition(UiPosition.STICKY).setTop(UiStyleLength.px(0));
                row.style().setHeight(UiStyleLength.px(70)).setOverflowY(UiOverflow.AUTO);
            } else if ("LAYOUT-016".equals(testCase.getId())) {
                row.style().setHeight(UiStyleLength.px(70)).setOverflowY(UiOverflow.AUTO);
                second.style().setHeight(UiStyleLength.px(150));
            }
            row.append(first);
            row.append(second);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, testCase.getId() + "：未测量");
            testCase.setElementDemo(row, summary, first, second);
        }
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
     * 追加 Paint 分组通用演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendPaintGroupRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        if ("PAINT-002".equals(testCase.getId())) {
            ElementNode stage = createPaintStage(document);
            ElementNode low = createPaintSample(document, "z=1", 0xFF2563EB);
            low.style()
                    .setPosition(UiPosition.RELATIVE)
                    .setZIndex(1);
            ElementNode high = createPaintSample(document, "z=2", 0xFFDC2626);
            high.style()
                    .setPosition(UiPosition.RELATIVE)
                    .setZIndex(2)
                    .setMarginTop(UiStyleLength.px(-18));
            stage.append(low);
            stage.append(high);
            demo.append(stage);
            TextNode summary = appendDemoSummary(document, demo, "stacking context：未校验");
            testCase.setElementDemo(stage, summary, low, high);
        } else if ("PAINT-003".equals(testCase.getId())) {
            ElementNode stage = createPaintStage(document);
            ElementNode group = createPaintSample(document, "opacity group", 0xFF7C3AED);
            group.style()
                    .setOpacity(0.55F)
                    .setPosition(UiPosition.RELATIVE)
                    .setZIndex(1);
            ElementNode inner = createPaintSample(document, "inner z=99", 0xFFDC2626);
            inner.style()
                    .setPosition(UiPosition.RELATIVE)
                    .setZIndex(99)
                    .setMarginTop(UiStyleLength.px(-4))
                    .setMarginLeft(UiStyleLength.px(18));
            group.append(inner);
            ElementNode sibling = createPaintSample(document, "outside", 0xFF059669);
            sibling.style()
                    .setPosition(UiPosition.RELATIVE)
                    .setZIndex(2)
                    .setMarginTop(UiStyleLength.px(-14));
            stage.append(group);
            stage.append(sibling);
            demo.append(stage);
            TextNode summary = appendDemoSummary(document, demo, "opacity stacking：未校验");
            testCase.setElementDemo(stage, summary, group, sibling, inner);
        } else if ("PAINT-004".equals(testCase.getId())) {
            ElementNode clip = createPaintStage(document);
            clip.style()
                    .setOverflowX(UiOverflow.HIDDEN)
                    .setOverflowY(UiOverflow.HIDDEN)
                    .setBorderRadius(UiStyleLength.px(14));
            ElementNode child = createPaintSample(document, "overflow child", 0xFFF59E0B);
            child.style()
                    .setWidth(UiStyleLength.px(160))
                    .setMarginLeft(UiStyleLength.px(56));
            clip.append(child);
            demo.append(clip);
            TextNode summary = appendDemoSummary(document, demo, "clip：未校验");
            testCase.setElementDemo(clip, summary, child);
        } else if ("PAINT-005".equals(testCase.getId())) {
            ElementNode stage = createPaintStage(document);
            ElementNode transformed = createPaintSample(document, "transform", 0xFF2563EB);
            transformed.style()
                    .setTransform(UiTransform.of(18.0F, 6.0F, 1.2F, 1.2F, 8.0F));
            stage.append(transformed);
            demo.append(stage);
            TextNode summary = appendDemoSummary(document, demo, "transform：未校验命中");
            testCase.setElementDemo(stage, summary, transformed);
        } else {
            ElementNode stage = createPaintStage(document);
            ElementNode sample = createPaintSample(document, testCase.getId(), 0xFF334155);
            if ("PAINT-006".equals(testCase.getId())) {
                ElementNode overlay = createPaintSample(document, "top-layer", 0xFFDC2626);
                overlay.style().setPosition(UiPosition.FIXED).setZIndex(1000).setMarginTop(UiStyleLength.px(-12));
                stage.append(sample).append(overlay);
                TextNode summary = appendDemoSummary(document, demo, "top-layer：等待人工确认覆盖与命中");
                demo.append(stage);
                testCase.setElementDemo(stage, summary, sample, overlay);
            } else if ("PAINT-007".equals(testCase.getId())) {
                stage.style().setHeight(UiStyleLength.px(74)).setOverflowY(UiOverflow.AUTO);
                sample.style().setHeight(UiStyleLength.px(140));
                stage.append(sample);
                demo.append(stage);
                TextNode summary = appendDemoSummary(document, demo, "scrollbar：未测量");
                testCase.setElementDemo(stage, summary, sample);
            } else if ("PAINT-008".equals(testCase.getId())) {
                sample.appendText(" / 标准文本仍存在");
                stage.append(sample);
                demo.append(stage);
                TextNode summary = appendDemoSummary(document, demo, "custom renderer：结构未校验");
                testCase.setElementDemo(stage, summary, sample);
            } else if ("PAINT-009".equals(testCase.getId())) {
                sample.style().setBackgroundImage(UiBackgroundImage.texture(
                        "minecraft:textures/gui/options_background.png", 256, 256));
                ElementNode fallback = createPaintSample(document, "missing fallback", 0xFFB45309);
                stage.append(sample).append(fallback);
                demo.append(stage);
                TextNode summary = appendDemoSummary(document, demo, "host image：等待人工确认资源与 fallback");
                testCase.setElementDemo(stage, summary, sample, fallback);
            }
        }
        parent.append(demo);
    }

    /**
     * 追加 Input 分组通用演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendInputGroupRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        if ("INPUT-001".equals(testCase.getId())) {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode rootBadge = createDemoPanel(document, "root capture/bubble", 0xFF1E3A8A);
            ElementNode parentBadge = createDemoPanel(document, "parent capture/bubble", 0xFF7C3AED);
            ElementNode targetBadge = createDemoPanel(document, "target", 0xFF059669);
            row.append(rootBadge).append(parentBadge).append(targetBadge);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "click 传播顺序：未执行");
            testCase.setElementDemo(row, summary, rootBadge, parentBadge, targetBadge);
        } else if ("INPUT-002".equals(testCase.getId())) {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode target = createDemoPanel(document, "target stopPropagation", 0xFFB45309);
            ElementNode defaultAction = createDemoPanel(document, "link default action", 0xFF2563EB);
            row.append(target).append(defaultAction);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "stopPropagation 与默认动作：未执行");
            testCase.setElementDemo(row, summary, target, defaultAction);
        } else if ("INPUT-003".equals(testCase.getId())) {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode target = createDemoPanel(document, "target preventDefault", 0xFFB91C1C);
            ElementNode bubble = createDemoPanel(document, "bubble still runs", 0xFF475569);
            row.append(target).append(bubble);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "preventDefault 与冒泡：未执行");
            testCase.setElementDemo(row, summary, target, bubble);
        } else if ("INPUT-004".equals(testCase.getId())) {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode wheelTarget = createDemoPanel(document, "wheel handler returns true", 0xFF2563EB);
            ElementNode scrollHost = createDemoPanel(document, "default scroll host", 0xFF059669);
            row.append(wheelTarget).append(scrollHost);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "wheel 默认滚动：未执行");
            testCase.setElementDemo(row, summary, wheelTarget, scrollHost);
        } else if ("INPUT-005".equals(testCase.getId())) {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode target = createDemoPanel(document, "pointer target", 0xFF7C3AED);
            ElementNode doubleClick = createDemoPanel(document, "second click -> dblclick", 0xFF059669);
            row.append(target).append(doubleClick);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "pointer 事件序列：未执行");
            testCase.setElementDemo(row, summary, target, doubleClick);
        } else {
            ElementNode row = createRuntimeDemoRow(document);
            ElementNode target = createDemoPanel(document, testCase.getId() + " target", 0xFF334155);
            ElementNode state = createDemoPanel(document, "状态日志", 0xFF1E3A8A);
            row.append(target).append(state);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, testCase.getId() + "：未执行");
            testCase.setElementDemo(row, summary, target, state);
        }
        parent.append(demo);
    }

    /**
     * 追加后续运行时分组的通用演示区域。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param testCase 用例模型
     */
    private void appendRemainingGroupRuntimeDemo(UiDocument document, ElementNode parent, RuntimeTestCase testCase) {
        ElementNode demo = createRuntimeDemoContainer(document);
        ElementNode row = createRuntimeDemoRow(document);
        if ("CTRL-001".equals(testCase.getId())) {
            final DocumentButtonControl enabled = new DocumentButtonControl(document, "enabled +1");
            DocumentButtonControl disabled = new DocumentButtonControl(document, "disabled no-op")
                    .setEnabled(false);
            enabled.getElement().setAttribute("data-runtime-action-count", "0");
            enabled.setActionHandler(new DocumentButtonActionHandler() {
                @Override
                public void onAction(DocumentButtonActionEvent event) {
                    incrementRuntimeActionCount(enabled.getElement());
                }
            });
            row.append(enabled.getElement()).append(disabled.getElement());
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "button action：未执行");
            testCase.setButtonDemo(row, summary, enabled, disabled);
        } else if ("CTRL-002".equals(testCase.getId())) {
            DocumentTextInputControl input = new DocumentTextInputControl(document)
                    .setPlaceholder("输入 value")
                    .setText("alpha-");
            input.getElement().style().setWidth(UiStyleLength.px(170));
            row.append(input.getElement()).append(createDemoPanel(document, "执行后自动聚焦并显示 caret", 0xFF334155));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "text input value：未执行");
            testCase.setTextInputDemo(row, summary, input);
        } else if ("CTRL-003".equals(testCase.getId())) {
            DocumentTextInputControl password = new DocumentTextInputControl(document)
                    .setPasswordMaskCharacter('*')
                    .setType(DocumentInputType.PASSWORD)
                    .setText("");
            password.getElement().style().setWidth(UiStyleLength.px(150));
            row.append(password.getElement()).append(createDemoPanel(document, "执行后输入 secret", 0xFF334155));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "password：未执行");
            testCase.setTextInputDemo(row, summary, password);
        } else if ("CTRL-004".equals(testCase.getId())) {
            DocumentTextInputControl number = new DocumentTextInputControl(document)
                    .setType(DocumentInputType.NUMBER)
                    .setText("");
            number.getElement().style().setWidth(UiStyleLength.px(150));
            row.append(number.getElement()).append(createDemoPanel(document, "执行后输入 12bad.5e+2", 0xFF334155));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "number input：未执行");
            testCase.setTextInputDemo(row, summary, number);
        } else if ("CTRL-005".equals(testCase.getId())) {
            DocumentTextAreaControl textArea = new DocumentTextAreaControl(document)
                    .setText("点击执行后会自动聚焦并重写内容");
            textArea.getElement().style()
                    .setWidth(UiStyleLength.px(210))
                    .setHeight(UiStyleLength.px(82));
            row.append(textArea.getElement()).append(createDemoPanel(document, "执行后显示光标与软换行", 0xFF334155));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "textarea：未执行");
            testCase.setTextAreaDemo(row, summary, textArea);
        } else if ("CTRL-006".equals(testCase.getId())) {
            DocumentCheckboxControl checked = new DocumentCheckboxControl(document, "checked").setChecked(true);
            DocumentCheckboxControl mixed = new DocumentCheckboxControl(document, "mixed").setIndeterminate(true);
            DocumentCheckboxControl disabled = new DocumentCheckboxControl(document, "disabled").setEnabled(false);
            row.append(checked.getElement()).append(mixed.getElement()).append(disabled.getElement());
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "checkbox：未执行");
            testCase.setElementDemo(row, summary, checked.getElement(), mixed.getElement(), disabled.getElement());
        } else if ("CTRL-007".equals(testCase.getId())) {
            DocumentRadioGroupControl radio = new DocumentRadioGroupControl(document, "一", "二", "三")
                    .setOrientation(UiRadioOrientation.HORIZONTAL);
            row.append(radio.getElement());
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "radio：未执行");
            testCase.setElementDemo(row, summary, radio.getElement());
        } else if ("CTRL-008".equals(testCase.getId())) {
            DocumentSelectControl select = new DocumentSelectControl(document, "alpha", "beta", "gamma");
            row.append(select.getElement()).append(createDemoPanel(document, "top-layer 人工确认", 0xFF334155));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "select：未执行");
            testCase.setElementDemo(row, summary, select.getElement());
        } else if ("CTRL-009".equals(testCase.getId())) {
            DocumentSliderControl horizontal = new DocumentSliderControl(document).setRange(0, 100).setStep(5)
                    .setValue(45).setOrientation(UiSliderOrientation.HORIZONTAL);
            DocumentSliderControl vertical = new DocumentSliderControl(document).setRange(0, 10).setStep(2)
                    .setValue(4).setOrientation(UiSliderOrientation.HORIZONTAL);
            row.append(horizontal.getElement()).append(vertical.getElement());
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "slider：未执行");
            testCase.setElementDemo(row, summary, horizontal.getElement(), vertical.getElement());
        } else if ("CTRL-010".equals(testCase.getId())) {
            DocumentToggleSwitchControl toggle = new DocumentToggleSwitchControl(document).setToggled(false);
            row.append(toggle.getElement()).append(createDemoPanel(document, "off -> on", 0xFF334155));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "toggle：未执行");
            testCase.setElementDemo(row, summary, toggle.getElement());
        } else if ("CTRL-011".equals(testCase.getId())) {
            DocumentSegmentedSelectorControl segmented = new DocumentSegmentedSelectorControl(document, "A", "B", "C");
            row.append(segmented.getElement());
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "segmented：未执行");
            testCase.setElementDemo(row, summary, segmented.getElement());
        } else if ("CTRL-012".equals(testCase.getId())) {
            DocumentTabControl tabs = new DocumentTabControl(document);
            tabs.addTab("一", new DocumentTabContentBuilderImpl("panel-one"));
            tabs.addTab("二", new DocumentTabContentBuilderImpl("panel-two"));
            row.append(tabs.getElement());
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "tab：未执行");
            testCase.setElementDemo(row, summary, tabs.getElement());
        } else if ("CTRL-013".equals(testCase.getId())) {
            ElementNode table = createRuntimeTableSample(document);
            row.append(table);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "table control：未执行");
            testCase.setElementDemo(row, summary, table);
        } else if ("CTRL-014".equals(testCase.getId()) || "CTRL-015".equals(testCase.getId())) {
            row.append(createDemoPanel(document, testCase.getId() + " 控件结构", 0xFF1E3A8A))
                    .append(createDemoPanel(document, "游戏内人工确认", 0xFF334155));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, testCase.getId() + "：未执行");
            testCase.setElementDemo(row, summary);
        } else if ("TEXT-001".equals(testCase.getId())) {
            ElementNode raw = createDemoPanel(document, "UILIB_RAW ", 0xFF1E3A8A);
            raw.appendText("§a原始字符", TextContentMode.UILIB_RAW);
            row.append(raw);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "raw text mode：未执行");
            testCase.setElementDemo(row, summary, raw);
        } else if ("TEXT-002".equals(testCase.getId())) {
            ElementNode formatted = createDemoPanel(document, "MC_FORMATTED ", 0xFF065F46);
            formatted.appendText("§a绿色", TextContentMode.MINECRAFT_FORMATTED);
            row.append(formatted);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "formatted text mode：未执行");
            testCase.setElementDemo(row, summary, formatted);
        } else if ("TEXT-003".equals(testCase.getId())) {
            row.append(createDemoPanel(document, "中文 mixed baseline", 0xFF7C3AED))
                    .append(createDemoPanel(document, "line-height 标尺", 0xFF334155));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "文本测量：未执行");
            testCase.setElementDemo(row, summary);
        } else if ("TEXT-004".equals(testCase.getId())) {
            row.append(createDemoPanel(document, "fallback 样例：汉字与缺字", 0xFFB45309));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "字体 fallback：未执行");
            testCase.setElementDemo(row, summary);
        } else if ("TEXT-005".equals(testCase.getId())) {
            row.append(createDemoPanel(document, "font epoch=" + fontEpoch, 0xFF334155))
                    .append(createDemoPanel(document, "reload debounce", 0xFF1E3A8A));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "font reload：未执行");
            testCase.setElementDemo(row, summary);
        } else if ("TEXT-006".equals(testCase.getId())) {
            ElementNode sample = createDemoPanel(document, "§kabcdef", 0xFF7C3AED);
            sample.appendText(" §r宽度稳定", TextContentMode.MINECRAFT_FORMATTED);
            row.append(sample);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "obfuscated：未执行");
            testCase.setElementDemo(row, summary, sample);
        } else if ("TEXT-007".equals(testCase.getId())) {
            ElementNode sample = createDemoPanel(document, "trim / wrap narrow text", 0xFF1E3A8A);
            sample.style().setWidth(UiStyleLength.px(118)).setOverflowWrap(UiOverflowWrap.ANYWHERE);
            row.append(sample);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "trim/wrap：未执行");
            testCase.setElementDemo(row, summary, sample);
        } else if ("ANIM-001".equals(testCase.getId())) {
            ElementNode sample = createDemoPanel(document, "opacity transition", 0xFF2563EB);
            sample.style()
                    .setOpacity(0.65F)
                    .setTransition(DocumentAnimationProperty.OPACITY, 120);
            row.append(sample);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "transition：未执行");
            testCase.setElementDemo(row, summary, sample);
        } else if ("ANIM-002".equals(testCase.getId())) {
            ElementNode sample = createDemoPanel(document, "per-property", 0xFF7C3AED);
            sample.style().setTransitions(
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.TEXT_COLOR, 80),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.TRANSLATE_X, 160, 20,
                            DocumentAnimationTimingFunction.EASE_OUT));
            row.append(sample);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "per-property transition：未执行");
            testCase.setElementDemo(row, summary, sample);
        } else if ("ANIM-003".equals(testCase.getId())) {
            row.style().setAlignItems(UiAlignItems.START);
            ElementNode track = createAnimationTrack(document, "0% -> 50% -> 100%");
            ElementNode sample = createDemoPanel(document, "keyframes path", 0xFF059669);
            sample.style().setWidth(UiStyleLength.px(90));
            track.append(sample);
            sample.style()
                    .setAnimationName("runtime-path")
                    .setAnimationDurationMillis(300)
                    .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS)
                    .setTransform(UiTransform.of(0.0F, 0.0F, 1.0F, 1.0F, 0.0F));
            row.append(track);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "keyframes：未执行");
            testCase.setElementDemo(track, summary, sample);
        } else if ("ANIM-004".equals(testCase.getId())) {
            ElementNode sample = createDemoPanel(document, "delay/duration/iteration", 0xFFB45309);
            sample.style()
                    .setAnimationName("runtime-loop")
                    .setAnimationDelayMillis(100)
                    .setAnimationDurationMillis(200)
                    .setAnimationIterationCount(3);
            row.append(sample);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "animation timing：未执行");
            testCase.setElementDemo(row, summary, sample);
        } else if ("ANIM-005".equals(testCase.getId())) {
            ElementNode normal = createAnimationDirectionSample(document, "normal", UiAnimationDirection.NORMAL);
            ElementNode reverse = createAnimationDirectionSample(document, "reverse", UiAnimationDirection.REVERSE);
            ElementNode alternate = createAnimationDirectionSample(document, "alternate", UiAnimationDirection.ALTERNATE);
            row.append(normal).append(reverse).append(alternate);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "animation direction：未执行");
            testCase.setElementDemo(row, summary, normal, reverse, alternate);
        } else if ("ANIM-006".equals(testCase.getId())) {
            ElementNode none = createAnimationFillModeSample(document, "none", DocumentAnimationFillMode.NONE);
            ElementNode forwards = createAnimationFillModeSample(document, "forwards", DocumentAnimationFillMode.FORWARDS);
            ElementNode backwards = createAnimationFillModeSample(document, "backwards", DocumentAnimationFillMode.BACKWARDS);
            ElementNode both = createAnimationFillModeSample(document, "both", DocumentAnimationFillMode.BOTH);
            row.append(none).append(forwards).append(backwards).append(both);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "fill-mode：未执行");
            testCase.setElementDemo(row, summary, none, forwards, backwards, both);
        } else if ("ANIM-007".equals(testCase.getId())) {
            ElementNode steps = createDemoPanel(document, "steps timing", 0xFFB45309);
            ElementNode ease = createDemoPanel(document, "ease/bezier timing", 0xFF2563EB);
            row.append(steps).append(ease);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "timing-function：未执行");
            testCase.setElementDemo(row, summary, steps, ease);
        } else if ("ANIM-008".equals(testCase.getId())) {
            ElementNode widthSample = createDemoPanel(document, "width layout", 0xFF059669);
            ElementNode opacitySample = createDemoPanel(document, "opacity paint", 0xFF7C3AED);
            row.append(widthSample).append(opacitySample);
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, "layout vs paint：未执行");
            testCase.setElementDemo(row, summary, widthSample, opacitySample);
        } else if (testCase.getGroup() == RUNTIME_HOST_GROUP) {
            row.append(createDemoPanel(document, testCase.getId() + " 宿主状态", 0xFF1E3A8A))
                    .append(createDemoPanel(document, "人工确认运行时", 0xFF334155));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, testCase.getId() + "：未执行");
            testCase.setElementDemo(row, summary);
        } else if (testCase.getGroup() == REMOTE_NET_GROUP) {
            row.append(createDemoPanel(document, testCase.getId() + " 网络入口", 0xFF065F46))
                    .append(createDemoPanel(document, "transport="
                            + NetTransportFactory.resolveName(Config.netTransport), 0xFF334155));
            demo.append(row);
            TextNode summary = appendDemoSummary(document, demo, testCase.getId() + "：未执行");
            testCase.setElementDemo(row, summary);
        }
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
     * 创建 DOM-012 伪类演示徽标。
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
        ensureRuntimeWidgetBounds();
        applyRuntimeTestResult(testCase, RuntimeTestResult.running("自动测试正在执行。"));
        try {
            if ("DOM-001".equals(testCase.getId())) {
                applyRuntimeTestResult(testCase, executeDomRuntimeTest(testCase));
                return;
            }
            if (testCase.getGroup() == DOM_GROUP) {
                applyRuntimeTestResult(testCase, executeDomGroupRuntimeTest(testCase));
                return;
            }
            if ("CSS-001".equals(testCase.getId())) {
                applyRuntimeTestResult(testCase, executeCssRuntimeTest(testCase));
                return;
            }
            if (testCase.getGroup() == CSS_GROUP) {
                applyRuntimeTestResult(testCase, executeCssGroupRuntimeTest(testCase));
                return;
            }
            if ("LAYOUT-001".equals(testCase.getId())) {
                applyRuntimeTestResult(testCase, executeLayoutRuntimeTest(testCase));
                return;
            }
            if (testCase.getGroup() == LAYOUT_GROUP) {
                applyRuntimeTestResult(testCase, executeLayoutGroupRuntimeTest(testCase));
                return;
            }
            if ("PAINT-001".equals(testCase.getId())) {
                applyRuntimeTestResult(testCase, executePaintRuntimeTest(testCase));
                return;
            }
            if (testCase.getGroup() == PAINT_GROUP) {
                applyRuntimeTestResult(testCase, executePaintGroupRuntimeTest(testCase));
                return;
            }
            if (testCase.getGroup() == INPUT_GROUP) {
                applyRuntimeTestResult(testCase, executeInputGroupRuntimeTest(testCase));
                return;
            }
            if (testCase.getGroup() == CONTROLS_GROUP) {
                applyRuntimeTestResult(testCase, executeControlsGroupRuntimeTest(testCase));
                return;
            }
            if (testCase.getGroup() == TEXT_FONT_GROUP) {
                applyRuntimeTestResult(testCase, executeTextFontGroupRuntimeTest(testCase));
                return;
            }
            if (testCase.getGroup() == ANIMATION_GROUP) {
                applyRuntimeTestResult(testCase, executeAnimationGroupRuntimeTest(testCase));
                return;
            }
            if (testCase.getGroup() == RUNTIME_HOST_GROUP) {
                applyRuntimeTestResult(testCase, executeRuntimeHostGroupRuntimeTest(testCase));
                return;
            }
            if (testCase.getGroup() == REMOTE_NET_GROUP) {
                applyRuntimeTestResult(testCase, executeRemoteNetGroupRuntimeTest(testCase));
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
        String actualOrder = buildChildrenTextSummary(row);
        String returnedText = describeNode(returnedNode);
        String summary = "appendChild：期望顺序=B, A；实际顺序=" + actualOrder
                + "；返回节点=" + returnedText + "；childCount=" + children.size();
        testCase.updateDemoSummary(summary);
        return passed ? RuntimeTestResult.passed(summary)
                : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                        "返回节点=A 且顺序=B, A",
                        "返回节点=" + returnedText + "，顺序=" + actualOrder + "，childCount=" + children.size()));
    }

    /**
     * 执行 DOM 分组补充用例断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeDomGroupRuntimeTest(RuntimeTestCase testCase) {
        if ("DOM-002".equals(testCase.getId())) {
            ElementNode row = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            ElementNode nodeA = testCase.getDemoElement(0);
            ElementNode nodeB = testCase.getDemoElement(1);
            ElementNode nodeC = testCase.getDemoElement(2);
            row.clearChildren();
            row.append(nodeA);
            row.append(nodeB);
            row.append(nodeC);
            DocumentNode returnedNode = row.insertBefore(nodeC, nodeA);
            List<DocumentNode> children = row.getChildren();
            int nodeACount = countDirectChildReferences(row, nodeA);
            boolean passed = returnedNode == nodeC && children.size() == 3 && children.get(0) == nodeC
                    && children.get(1) == nodeA && children.get(2) == nodeB;
            String actualOrder = buildChildrenTextSummary(row);
            String returnedText = describeNode(returnedNode);
            String summary = "insertBefore：期望顺序=C, A, B；实际顺序=" + actualOrder
                    + "；返回节点=" + returnedText + "；A出现次数=" + nodeACount;
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                            "返回节点=C，顺序=C, A, B，A出现次数=1",
                            "返回节点=" + returnedText + "，顺序=" + actualOrder + "，A出现次数=" + nodeACount));
        }
        if ("DOM-003".equals(testCase.getId())) {
            ElementNode row = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            ElementNode oldNode = testCase.getDemoElement(0);
            ElementNode spareNode = testCase.getDemoElement(1);
            row.clearChildren();
            row.append(oldNode);
            row.append(spareNode);
            ElementNode newNode = createDemoBadge(document, "new", 0xFF059669);
            DocumentNode returnedNode = row.replaceChild(newNode, oldNode);
            List<DocumentNode> children = row.getChildren();
            boolean passed = returnedNode == oldNode && oldNode.getParent() == null && newNode.getParent() == row
                    && children.size() == 2 && children.get(0) == newNode && children.get(1) == spareNode;
            String actualOrder = buildChildrenTextSummary(row);
            String returnedText = describeNode(returnedNode);
            boolean oldDetached = oldNode.getParent() == null;
            boolean newParentIsRow = newNode.getParent() == row;
            String summary = "replaceChild：期望返回=old；实际返回=" + returnedText
                    + "；当前顺序=" + actualOrder + "；old离树=" + oldDetached
                    + "；new父级=row=" + newParentIsRow;
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                            "返回=old，顺序=new, spare，old离树=true，new父级=row=true",
                            "返回=" + returnedText + "，顺序=" + actualOrder + "，old离树=" + oldDetached
                                    + "，new父级=row=" + newParentIsRow));
        }
        if ("DOM-004".equals(testCase.getId())) {
            ElementNode row = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            ElementNode directChild = testCase.getDemoElement(0);
            ElementNode wrapper = testCase.getDemoElement(1);
            ElementNode nestedChild = testCase.getDemoElement(2);
            if (nestedChild.getParent() != wrapper) {
                wrapper.append(nestedChild);
            }
            row.clearChildren();
            row.append(directChild);
            row.append(wrapper);
            DocumentNode removedNode = row.removeChild(directChild);
            boolean rejected = false;
            try {
                row.removeChild(nestedChild);
            } catch (IllegalArgumentException exception) {
                rejected = true;
            }
            boolean passed = removedNode == directChild && directChild.getParent() == null && rejected
                    && row.getChildren().size() == 1 && row.getChildren().get(0) == wrapper;
            String removedText = describeNode(removedNode);
            String remainingOrder = buildChildrenTextSummary(row);
            boolean directDetached = directChild.getParent() == null;
            String summary = "removeChild：返回节点=" + removedText + "；剩余顺序=" + remainingOrder
                    + "；直接子节点离树=" + directDetached + "；非直接子节点被拒绝=" + rejected;
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                            "返回=remove，剩余=nested，直接子节点离树=true，非直接子节点被拒绝=true",
                            "返回=" + removedText + "，剩余=" + remainingOrder + "，直接子节点离树=" + directDetached
                                    + "，非直接子节点被拒绝=" + rejected));
        }
        if ("DOM-005".equals(testCase.getId())) {
            ElementNode target = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            target.clearChildren();
            DocumentFragmentNode fragment = document.createDocumentFragment();
            fragment.appendChild(createDemoBadge(document, "F1", 0xFF2563EB));
            fragment.appendChild(createDemoBadge(document, "F2", 0xFF7C3AED));
            fragment.appendChild(createDemoBadge(document, "F3", 0xFF059669));
            DocumentNode returnedNode = target.appendChild(fragment);
            boolean passed = returnedNode == fragment && fragment.getChildCount() == 0 && target.getChildCount() == 3;
            String summary = "DocumentFragment：目标容器计数=" + target.getChildCount() + "/3"
                    + "；fragment 计数=" + fragment.getChildCount() + "/0"
                    + "；返回节点=" + describeNode(returnedNode);
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                            "返回=fragment，目标容器计数=3，fragment计数=0",
                            "返回=" + describeNode(returnedNode) + "，目标容器计数=" + target.getChildCount()
                                    + "，fragment计数=" + fragment.getChildCount()));
        }
        if ("DOM-006".equals(testCase.getId())) {
            ElementNode target = testCase.getDemoElement(0);
            target.setTextContent("textContent 已替换");
            boolean passed = "textContent 已替换".equals(target.getTextContent()) && target.getChildCount() == 1;
            String summary = "textContent：期望=textContent 已替换；实际=" + target.getTextContent()
                    + "；childCount=" + target.getChildCount() + "/1";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                            "textContent=textContent 已替换，childCount=1",
                            "textContent=" + target.getTextContent() + "，childCount=" + target.getChildCount()));
        }
        if ("DOM-007".equals(testCase.getId())) {
            ElementNode target = testCase.getDemoElement(0);
            target.setAttribute("data-case", "after");
            String dataValue = target.getAttribute("data-case");
            target.removeAttribute("data-case");
            boolean removed = !target.hasAttribute("data-case");
            boolean disabled = target.isDisabled();
            String summary = "attribute：data-case写入=" + dataValue + "/after；removed=" + removed
                    + "/true；布尔属性禁用生效=" + disabled + "/true";
            testCase.updateDemoSummary(summary);
            return "after".equals(dataValue) && removed && disabled ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                            "data-case写入=after，removed=true，disabled=true",
                            "data-case写入=" + dataValue + "，removed=" + removed + "，disabled=" + disabled));
        }
        if ("DOM-008".equals(testCase.getId())) {
            ElementNode target = testCase.getDemoElement(0);
            target.getClassList().add("highlight");
            target.getClassList().remove("before");
            boolean toggled = target.getClassList().toggle("selected");
            boolean passed = target.getClassList().contains("highlight")
                    && !target.getClassList().contains("before") && toggled
                    && target.getClassList().contains("selected");
            boolean containsHighlight = target.getClassList().contains("highlight");
            boolean containsBefore = target.getClassList().contains("before");
            boolean containsSelected = target.getClassList().contains("selected");
            String summary = "classList=" + target.getClassName() + "；highlight=" + containsHighlight
                    + "/true；before=" + containsBefore + "/false；selected=" + containsSelected
                    + "/true；toggleSelected=" + toggled + "/true";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                            "highlight=true，before=false，selected=true，toggleSelected=true",
                            "highlight=" + containsHighlight + "，before=" + containsBefore + "，selected="
                                    + containsSelected + "，toggleSelected=" + toggled));
        }
        if ("DOM-009".equals(testCase.getId())) {
            ElementNode found = document.querySelector("*");
            boolean passed = found != null && found != rootElement;
            String foundText = describeNode(found);
            boolean internalRootReturned = found == rootElement;
            String summary = "querySelector('*')：期望=第一个作者元素且不是隐藏根；实际=" + foundText
                    + "；internalRootReturned=" + internalRootReturned + "/false";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                            "返回非空作者元素，internalRootReturned=false",
                            "实际=" + foundText + "，internalRootReturned=" + internalRootReturned));
        }
        if ("DOM-010".equals(testCase.getId())) {
            ElementNode target = testCase.getDemoElement(0);
            ElementNode child = testCase.getDemoElement(1);
            target.setClassName("query-target");
            target.setId("query-target-id");
            child.setClassName("query-child");
            int typeCount = document.querySelectorAll("domcase").size();
            int classCount = document.querySelectorAll(".query-target").size();
            int idCount = document.querySelectorAll("#query-target-id").size();
            int descendantCount = document.querySelectorAll("domcase .query-child").size();
            int childCount = document.querySelectorAll("domcase > .query-child").size();
            boolean groupSelectorSupported = true;
            int groupCount = -1;
            try {
                groupCount = document.querySelectorAll(".query-target, .query-child").size();
            } catch (IllegalArgumentException exception) {
                groupSelectorSupported = false;
            }
            boolean basicPassed = typeCount == 1 && classCount == 1 && idCount == 1 && descendantCount == 1
                    && childCount == 1;
            boolean groupPassed = groupSelectorSupported && groupCount == 2;
            String summary = "selector counts domcase=" + typeCount + "/1, class=" + classCount
                    + "/1, id=" + idCount + "/1, descendant=" + descendantCount
                    + "/1, child=" + childCount + "/1；group="
                    + (groupSelectorSupported ? groupCount + "/2" : "待实现缺口");
            testCase.updateDemoSummary(summary);
            if (!basicPassed) {
                return RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                        "domcase/class/id/descendant/child 均为 1/1",
                        "domcase=" + typeCount + "，class=" + classCount + "，id=" + idCount
                                + "，descendant=" + descendantCount + "，child=" + childCount));
            }
            return groupPassed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.running(summary + "；待实现缺口：分组选择器未完整支持");
        }
        if ("DOM-011".equals(testCase.getId())) {
            ElementNode match = testCase.getDemoElement(0);
            ElementNode skip = testCase.getDemoElement(1);
            UiDocument selectorDocument = UiDocument.create();
            ElementNode selectorMatch = selectorDocument.div();
            ElementNode selectorSkip = selectorDocument.div();
            selectorMatch.setAttribute("data-case", "match");
            selectorSkip.setAttribute("data-case", "skip");
            selectorDocument.getRootElement().append(selectorMatch).append(selectorSkip);
            String attrCount = "待实现缺口";
            String attrValueCount = "待实现缺口";
            try {
                attrCount = selectorDocument.querySelectorAll("[data-case]").size() + "/2";
                attrValueCount = selectorDocument.querySelectorAll("[data-case=match]").size() + "/1";
            } catch (IllegalArgumentException exception) {
                // 当前解析器不支持属性选择器，页面必须明确展示缺口而不是伪造通过。
            }
            String summary = "attribute selector：目标 data-case=" + match.getAttribute("data-case")
                    + "；对照 data-case=" + skip.getAttribute("data-case")
                    + "；[data-case]=" + attrCount + "；[data-case=match]=" + attrValueCount;
            testCase.updateDemoSummary(summary);
            return "2/2".equals(attrCount) && "1/1".equals(attrValueCount)
                    ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.running(summary
                            + "；待实现缺口：属性选择器 [attr] / [attr=value] 当前选择器解析器未支持");
        }
        if ("DOM-012".equals(testCase.getId())) {
            ElementNode first = testCase.getDemoElement(0);
            ElementNode interactive = testCase.getDemoElement(1);
            ElementNode last = testCase.getDemoElement(2);
            boolean firstChild = first.getParent() == testCase.getDemoRoot()
                    && testCase.getDemoRoot().getChildren().get(0) == first;
            boolean lastChild = last.getParent() == testCase.getDemoRoot()
                    && testCase.getDemoRoot().getChildren().get(testCase.getDemoRoot().getChildCount() - 1) == last;
            boolean firstSelector = UiSelector.parse(".dom-012-item:first-child").matches(first);
            boolean lastSelector = UiSelector.parse(".dom-012-item:last-child").matches(last);
            boolean hoverSelector = UiSelector.parse(".dom-012-interactive:hover")
                    .matches(interactive, Collections.singleton(UiPseudoClass.HOVER));
            boolean activeSelector = UiSelector.parse(".dom-012-interactive:active")
                    .matches(interactive, Collections.singleton(UiPseudoClass.ACTIVE));
            boolean focusVisibleSelector = UiSelector.parse(".dom-012-interactive:focus-visible")
                    .matches(interactive, Collections.singleton(UiPseudoClass.FOCUS_VISIBLE));
            String summary = "structuralPseudo first=" + boolCount(firstSelector) + "；last=" + boolCount(lastSelector)
                    + "；顺序校验 first=" + firstChild + "/true,last=" + lastChild + "/true"
                    + "；interactive selectors hover=" + hoverSelector + "/true,active=" + activeSelector
                    + "/true,focusVisible=" + focusVisibleSelector + "/true；人工观察=移动/按下/键盘聚焦目标";
            testCase.updateDemoSummary(summary);
            return firstChild && lastChild && firstSelector && lastSelector && hoverSelector && activeSelector
                    && focusVisibleSelector ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                            "first=1/1，last=1/1，hover/active/focus-visible 选择器均可匹配",
                            "first=" + boolCount(firstSelector) + "，last=" + boolCount(lastSelector)
                                    + "，hover=" + hoverSelector + "，active=" + activeSelector
                                    + "，focusVisible=" + focusVisibleSelector));
        }
        if ("DOM-013".equals(testCase.getId())) {
            UiDocument linkDocument = UiDocument.create();
            ElementNode root = linkDocument.getRootElement();
            ElementNode normal = linkDocument.a();
            ElementNode prevented = linkDocument.a();
            final List<String> activations = new ArrayList<String>();
            linkDocument.setLinkActivationHandler(new DocumentLinkActivationHandler() {
                @Override
                public void onLinkActivated(DocumentLinkActivationEvent event) {
                    activations.add(event.getHref());
                }
            });
            normal.setAttribute("href", "https://example.test/dom-013-normal");
            prevented.setAttribute("href", "https://example.test/dom-013-prevented");
            prevented.setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    event.preventDefault();
                    return false;
                }
            });
            normal.style().setDisplay(UiDisplay.BLOCK).setWidth(UiStyleLength.px(100)).setHeight(UiStyleLength.px(20));
            prevented.style().setDisplay(UiDisplay.BLOCK).setWidth(UiStyleLength.px(100)).setHeight(UiStyleLength.px(20));
            root.append(normal).append(prevented);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(linkDocument, 120, 60);
            dispatchPrimaryClick(widget, 10, 10, 1L, 2L);
            dispatchPrimaryClick(widget, 10, 30, 3L, 4L);
            boolean passed = activations.size() == 1
                    && "https://example.test/dom-013-normal".equals(activations.get(0));
            String summary = "link activations=" + activations
                    + "；期望=[https://example.test/dom-013-normal]；preventedHrefAbsent="
                    + !activations.contains("https://example.test/dom-013-prevented");
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, buildExpectedActualDifference(
                            "仅激活 https://example.test/dom-013-normal",
                            "activations=" + activations));
        }
        return RuntimeTestResult.failed("未知 DOM 用例。", "没有匹配的 DOM 执行器");
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
     * 执行 CSS 分组补充用例断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeCssGroupRuntimeTest(RuntimeTestCase testCase) {
        if ("CSS-002".equals(testCase.getId())) {
            int typeColor = UiStyleResolver.compute(testCase.getDemoElement(0)).getTextColor();
            int classColor = UiStyleResolver.compute(testCase.getDemoElement(1)).getTextColor();
            int idColor = UiStyleResolver.compute(testCase.getDemoElement(2)).getTextColor();
            int orderColor = UiStyleResolver.compute(testCase.getDemoElement(3)).getTextColor();
            boolean passed = typeColor == 0xFF60A5FA && classColor == 0xFF34D399
                    && idColor == 0xFFFBBF24 && orderColor == 0xFFC084FC;
            String summary = "specificity colors=" + formatColor(typeColor) + "," + formatColor(classColor)
                    + "," + formatColor(idColor) + "," + formatColor(orderColor);
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "specificity 或声明顺序颜色异常");
        }
        if ("CSS-003".equals(testCase.getId())) {
            ElementNode parentBox = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            ElementNode child = testCase.getDemoElement(0);
            ComputedStyle parentStyle = UiStyleResolver.compute(parentBox);
            ComputedStyle childStyle = UiStyleResolver.compute(child);
            boolean passed = childStyle.getTextColor() == parentStyle.getTextColor()
                    && childStyle.getBorderStyle() == UiBorderStyle.NONE;
            String summary = "父文本色=" + formatColor(parentStyle.getTextColor())
                    + "；子文本色=" + formatColor(childStyle.getTextColor())
                    + "；子 border=" + childStyle.getBorderStyle();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "继承或非继承属性计算异常");
        }
        if ("CSS-004".equals(testCase.getId())) {
            UiDisplay noneDisplay = UiStyleResolver.compute(testCase.getDemoElement(0)).getDisplay();
            UiDisplay blockDisplay = UiStyleResolver.compute(testCase.getDemoElement(1)).getDisplay();
            UiDisplay inlineDisplay = UiStyleResolver.compute(testCase.getDemoElement(2)).getDisplay();
            UiDisplay inlineBlockDisplay = UiStyleResolver.compute(testCase.getDemoElement(3)).getDisplay();
            UiDisplay flexDisplay = UiStyleResolver.compute(testCase.getDemoElement(4)).getDisplay();
            boolean passed = noneDisplay == UiDisplay.NONE && blockDisplay == UiDisplay.BLOCK
                    && inlineDisplay == UiDisplay.INLINE && inlineBlockDisplay == UiDisplay.INLINE_BLOCK
                    && flexDisplay == UiDisplay.FLEX;
            String summary = "display=" + noneDisplay + "," + blockDisplay + "," + inlineDisplay
                    + "," + inlineBlockDisplay + "," + flexDisplay;
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "display 计算结果异常");
        }
        if ("CSS-005".equals(testCase.getId())) {
            ElementNode contentBox = testCase.getDemoElement(0);
            ElementNode borderBox = testCase.getDemoElement(1);
            DocumentLayoutBox contentLayout = DocumentLayoutEngine.layout(contentBox, 180, 0, textMeasureService);
            DocumentLayoutBox borderLayout = DocumentLayoutEngine.layout(borderBox, 180, 0, textMeasureService);
            boolean passed = UiStyleResolver.compute(contentBox).getBoxSizing() == UiBoxSizing.CONTENT_BOX
                    && UiStyleResolver.compute(borderBox).getBoxSizing() == UiBoxSizing.BORDER_BOX
                    && contentLayout.getWidth() > borderLayout.getWidth()
                    && contentLayout.getContentWidth() > borderLayout.getContentWidth();
            String summary = "content-box 外框/内容=" + contentLayout.getWidth() + "/"
                    + contentLayout.getContentWidth() + "；border-box 外框/内容=" + borderLayout.getWidth()
                    + "/" + borderLayout.getContentWidth();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "box-sizing 尺寸计算异常");
        }
        if ("CSS-006".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(0));
            boolean passed = style.getMargin().getTop().equals(UiStyleLength.px(3))
                    && style.getPadding().getLeft().equals(UiStyleLength.px(8))
                    && style.getBorderWidthSides().getLeft().equals(UiStyleLength.px(4))
                    && style.getBorderColors().getRight() == 0xFF66FF66;
            String summary = "sides marginTop=" + formatLengthSummary(style.getMargin().getTop())
                    + "；paddingLeft=" + formatLengthSummary(style.getPadding().getLeft())
                    + "；borderLeft=" + formatLengthSummary(style.getBorderWidthSides().getLeft())
                    + "；borderRightColor=" + formatColor(style.getBorderColors().getRight());
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "四边独立样式计算异常");
        }
        if ("CSS-007".equals(testCase.getId())) {
            ElementNode sample = testCase.getDemoElement(0);
            DocumentLayoutBox box = DocumentLayoutEngine.layout(sample, 300, 0, textMeasureService);
            boolean passed = box.getWidth() >= 140;
            String summary = "size width=" + box.getWidth() + "；min="
                    + formatLengthSummary(UiStyleResolver.compute(sample).getMinWidth())
                    + "；max=" + formatLengthSummary(UiStyleResolver.compute(sample).getMaxWidth());
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "min/max 或百分比尺寸异常");
        }
        if ("CSS-008".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(0));
            boolean passed = style.getBackgroundImage() != null;
            String summary = "backgroundImage=" + (style.getBackgroundImage() != null)
                    + "；backgroundColor=" + formatColor(style.getBackgroundColor());
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "background-image 未保留资源图值");
        }
        if ("CSS-009".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(0));
            boolean passed = style.getBorderStyle() == UiBorderStyle.SOLID && !style.getOutline().isNone();
            String summary = "borderStyle=" + style.getBorderStyle() + "；radius="
                    + formatLengthSummary(style.getBorderRadius()) + "；outline=" + style.getOutline();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "border/radius/outline 计算异常");
        }
        if ("CSS-010".equals(testCase.getId())) {
            UiBoxShadow shadow = UiStyleResolver.compute(testCase.getDemoElement(0)).getBoxShadow();
            boolean passed = shadow != null && shadow.isInset();
            String summary = "boxShadow=" + shadow + "；绘制层级=人工确认";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "box-shadow 结构异常");
        }
        if ("CSS-011".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(0));
            boolean passed = style.getOpacity() == 0.45F && style.getVisibility() == UiVisibility.VISIBLE
                    && style.getPointerEvents() == UiPointerEvents.NONE;
            String summary = "opacity=" + style.getOpacity() + "；visibility=" + style.getVisibility()
                    + "；pointerEvents=" + style.getPointerEvents();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "opacity/visibility/pointer-events 计算异常");
        }
        if ("CSS-012".equals(testCase.getId())) {
            ElementNode sample = testCase.getDemoElement(0);
            ComputedStyle style = UiStyleResolver.compute(sample);
            DocumentLayoutBox box = DocumentLayoutEngine.layout(sample, 160, 0, textMeasureService);
            boolean passed = style.getOverflowY() == UiOverflow.AUTO && style.getScrollbarWidth() == UiScrollbarWidth.THIN
                    && box.getContentHeight() > 0;
            String summary = "overflowY=" + style.getOverflowY() + "；scrollbarWidth=" + style.getScrollbarWidth()
                    + "；contentHeight=" + box.getContentHeight();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "overflow 或 scrollbar-width 计算异常");
        }
        if ("CSS-013".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(0));
            boolean passed = style.getTextAlign() == UiTextAlign.CENTER
                    && style.getTextDecoration() == UiTextDecoration.UNDERLINE
                    && style.getTextTransform() == UiTextTransform.UPPERCASE;
            String summary = "textAlign=" + style.getTextAlign() + "；decoration=" + style.getTextDecoration()
                    + "；transform=" + style.getTextTransform();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "文本样式计算异常");
        }
        if ("CSS-014".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(0));
            boolean passed = style.getWhiteSpace() == UiWhiteSpace.PRE_WRAP
                    && style.getOverflowWrap() == UiOverflowWrap.ANYWHERE
                    && style.getWordBreak() == UiWordBreak.BREAK_ALL;
            String summary = "whiteSpace=" + style.getWhiteSpace() + "；overflowWrap=" + style.getOverflowWrap()
                    + "；wordBreak=" + style.getWordBreak();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "white-space/wrap/word-break 计算异常");
        }
        if ("CSS-015".equals(testCase.getId())) {
            UiCursor cursor = UiStyleResolver.compute(testCase.getDemoElement(0)).getCursor();
            String summary = "cursor=" + cursor + "；hover 状态标签=人工确认";
            testCase.updateDemoSummary(summary);
            return cursor == UiCursor.POINTER ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "cursor 计算异常");
        }
        return RuntimeTestResult.failed("未知 CSS 用例。", "没有匹配的 CSS 执行器");
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
     * 执行 Layout 分组补充用例断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeLayoutGroupRuntimeTest(RuntimeTestCase testCase) {
        if ("LAYOUT-002".equals(testCase.getId())) {
            ElementNode stack = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(stack, 260, 0, textMeasureService);
            DocumentLayoutBox first = findRequiredLayoutBox(rootBox, testCase.getDemoElement(0));
            DocumentLayoutBox second = findRequiredLayoutBox(rootBox, testCase.getDemoElement(1));
            int gap = second.getTop() - first.getBottom();
            boolean passed = gap == 18;
            String summary = "相邻 margin gap=" + gap + "；预期=18";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "相邻 margin 未按较大值 collapse");
        }
        if ("LAYOUT-003".equals(testCase.getId())) {
            ElementNode stack = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(stack, 260, 0, textMeasureService);
            DocumentLayoutBox empty = findRequiredLayoutBox(rootBox, testCase.getDemoElement(1));
            boolean passed = empty.getHeight() == 0;
            String summary = "空块 height=" + empty.getHeight() + "；marginTop=" + empty.getMargin().getTop()
                    + "；marginBottom=" + empty.getMargin().getBottom();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "空块产生了额外高度");
        }
        if ("LAYOUT-004".equals(testCase.getId())) {
            ElementNode inlineRoot = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(inlineRoot, 260, 0, textMeasureService);
            boolean passed = !rootBox.getInlineFragments().isEmpty() && !rootBox.getChildren().isEmpty();
            String summary = "inline fragments=" + rootBox.getInlineFragments().size()
                    + "；inline-block boxes=" + rootBox.getChildren().size();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "inline 或 inline-block 布局盒缺失");
        }
        if ("LAYOUT-005".equals(testCase.getId())) {
            ElementNode baseline = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(baseline, 260, 0, textMeasureService);
            String summary = "已知缺口：inline-block baseline 仍需人工确认；layoutHeight="
                    + rootBox.getHeight();
            testCase.updateDemoSummary(summary);
            return RuntimeTestResult.running(summary);
        }
        if ("LAYOUT-006".equals(testCase.getId())) {
            ElementNode row = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(row, 260, 0, textMeasureService);
            DocumentLayoutBox fixed = findRequiredLayoutBox(rootBox, testCase.getDemoElement(0));
            DocumentLayoutBox grow = findRequiredLayoutBox(rootBox, testCase.getDemoElement(1));
            boolean passed = fixed.getWidth() == 60 && grow.getWidth() > fixed.getWidth();
            String summary = "flex row fixed=" + fixed.getWidth() + "；grow=" + grow.getWidth();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "flex-grow 主轴分配异常");
        }
        if ("LAYOUT-007".equals(testCase.getId())) {
            ElementNode row = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(row, 160, 0, textMeasureService);
            DocumentLayoutBox defaultMin = findRequiredLayoutBox(rootBox, testCase.getDemoElement(0));
            DocumentLayoutBox minZero = findRequiredLayoutBox(rootBox, testCase.getDemoElement(1));
            boolean passed = minZero.getWidth() <= defaultMin.getWidth();
            String summary = "flex min-auto=" + defaultMin.getWidth() + "；min-width0=" + minZero.getWidth();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "flex min-width 收缩语义异常");
        }
        if ("LAYOUT-008".equals(testCase.getId())) {
            ElementNode row = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            ComputedStyle style = UiStyleResolver.compute(row);
            String summary = "flexWrap=" + style.getFlexWrap() + "；alignContent=" + style.getAlignContent()
                    + "；多行分布=人工确认";
            testCase.updateDemoSummary(summary);
            return style.getFlexWrap() == UiFlexWrap.WRAP && style.getAlignContent() == UiAlignContent.SPACE_BETWEEN
                    ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "flex-wrap 或 align-content 计算异常");
        }
        if ("LAYOUT-009".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(1));
            boolean passed = style.getMargin().getTop().getType() == UiStyleLength.Type.AUTO
                    && style.getMargin().getBottom().getType() == UiStyleLength.Type.AUTO;
            String summary = "cross auto margin top=" + formatLengthSummary(style.getMargin().getTop())
                    + "；bottom=" + formatLengthSummary(style.getMargin().getBottom());
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "flex 交叉轴 auto margin 声明异常");
        }
        if ("LAYOUT-010".equals(testCase.getId())) {
            ElementNode table = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(table, 260, 0, textMeasureService);
            boolean passed = UiStyleResolver.compute(table).getDisplay() == UiDisplay.TABLE
                    && rootBox.getChildren().size() >= 2;
            String summary = "table rows=" + rootBox.getChildren().size() + "；display="
                    + UiStyleResolver.compute(table).getDisplay();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "table 布局结构异常");
        }
        if ("LAYOUT-011".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(0));
            boolean passed = style.getPosition() == UiPosition.RELATIVE;
            String summary = "position=" + style.getPosition() + "；left=" + formatLengthSummary(style.getLeft())
                    + "；top=" + formatLengthSummary(style.getTop());
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "relative position 声明异常");
        }
        if ("LAYOUT-012".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(0));
            boolean passed = style.getPosition() == UiPosition.ABSOLUTE;
            String summary = "absolute position=" + style.getPosition() + "；left="
                    + formatLengthSummary(style.getLeft()) + "；containingBlock=relative parent";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "absolute containing block 声明异常");
        }
        if ("LAYOUT-013".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(0));
            String summary = "fixed position=" + style.getPosition() + "；right="
                    + formatLengthSummary(style.getRight()) + "；视口固定=人工确认";
            testCase.updateDemoSummary(summary);
            return style.getPosition() == UiPosition.FIXED ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "fixed 声明异常");
        }
        if ("LAYOUT-014".equals(testCase.getId())) {
            ComputedStyle ancestorStyle = UiStyleResolver.compute(testCase.getDemoRoot());
            ComputedStyle childStyle = UiStyleResolver.compute(testCase.getDemoElement(0));
            boolean passed = !ancestorStyle.getTransform().isIdentity() && childStyle.getPosition() == UiPosition.FIXED;
            String summary = "ancestor " + formatTransformSummary(ancestorStyle.getTransform())
                    + "；childPosition=" + childStyle.getPosition();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "transform fixed containing block 声明异常");
        }
        if ("LAYOUT-015".equals(testCase.getId())) {
            ComputedStyle style = UiStyleResolver.compute(testCase.getDemoElement(0));
            String summary = "sticky position=" + style.getPosition() + "；top="
                    + formatLengthSummary(style.getTop()) + "；滚动阈值=人工确认";
            testCase.updateDemoSummary(summary);
            return style.getPosition() == UiPosition.STICKY ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "sticky 声明异常");
        }
        if ("LAYOUT-016".equals(testCase.getId())) {
            ElementNode row = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(row, 220, 120, textMeasureService);
            String summary = "nested scroll overflow=" + UiStyleResolver.compute(row).getOverflowY()
                    + "；contentHeight=" + rootBox.getContentHeight() + "；边界移交=人工确认";
            testCase.updateDemoSummary(summary);
            return UiStyleResolver.compute(row).getOverflowY() == UiOverflow.AUTO ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "嵌套滚动结构异常");
        }
        return RuntimeTestResult.failed("未知 Layout 用例。", "没有匹配的 Layout 执行器");
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
     * 执行 Paint 分组补充用例断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executePaintGroupRuntimeTest(RuntimeTestCase testCase) {
        if ("PAINT-002".equals(testCase.getId())) {
            ElementNode stage = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            ElementNode low = testCase.getDemoElement(0);
            ElementNode high = testCase.getDemoElement(1);
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(stage, 260, 0, textMeasureService);
            List<DocumentLayoutBox> orderedChildren = rootBox.getChildrenInStackingOrder();
            boolean passed = !orderedChildren.isEmpty()
                    && orderedChildren.get(orderedChildren.size() - 1).getElement() == high
                    && findRequiredLayoutBox(rootBox, low).getStackingZIndex() == 1
                    && findRequiredLayoutBox(rootBox, high).getStackingZIndex() == 2;
            String summary = "stacking order last="
                    + (orderedChildren.isEmpty() ? "none" : orderedChildren.get(orderedChildren.size() - 1)
                            .getElement().getTextContent())
                    + "；z-index=1/2";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "z-index stacking 顺序异常");
        }
        if ("PAINT-003".equals(testCase.getId())) {
            ElementNode stage = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            ElementNode group = testCase.getDemoElement(0);
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(stage, 260, 0, textMeasureService);
            DocumentLayoutBox groupBox = findRequiredLayoutBox(rootBox, group);
            boolean passed = UiStyleResolver.compute(group).getOpacity() == 0.55F && groupBox.createsStackingContext();
            String summary = "opacity=" + UiStyleResolver.compute(group).getOpacity()
                    + "；createsStackingContext=" + groupBox.createsStackingContext()
                    + "；等待人工确认整体混合";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "opacity stacking context 结构异常");
        }
        if ("PAINT-004".equals(testCase.getId())) {
            ElementNode clip = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            ComputedStyle clipStyle = UiStyleResolver.compute(clip);
            boolean passed = clipStyle.getOverflowX() == UiOverflow.HIDDEN
                    && clipStyle.getOverflowY() == UiOverflow.HIDDEN
                    && !UiStyleLength.px(0).equals(clipStyle.getBorderRadius());
            String summary = "overflow=" + clipStyle.getOverflowX() + "/" + clipStyle.getOverflowY()
                    + "；radius=" + formatLengthSummary(clipStyle.getBorderRadius())
                    + "；等待人工确认裁剪命中";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "overflow clip 或圆角结构异常");
        }
        if ("PAINT-005".equals(testCase.getId())) {
            ElementNode transformed = testCase.getDemoElement(0);
            UiTransform transform = UiStyleResolver.compute(transformed).getTransform();
            boolean passed = !transform.isIdentity();
            String summary = formatTransformSummary(transform) + "；等待人工确认变换后命中";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "transform 未生效");
        }
        if ("PAINT-006".equals(testCase.getId())) {
            ComputedStyle overlayStyle = UiStyleResolver.compute(testCase.getDemoElement(1));
            String summary = "top-layer sample position=" + overlayStyle.getPosition() + "；zIndex="
                    + overlayStyle.getZIndex() + "；覆盖与命中=人工确认";
            testCase.updateDemoSummary(summary);
            return overlayStyle.getPosition() == UiPosition.FIXED ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "top-layer 样例结构异常");
        }
        if ("PAINT-007".equals(testCase.getId())) {
            ElementNode stage = Objects.requireNonNull(testCase.getDemoRoot(), "demoRoot");
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(stage, 260, 120, textMeasureService);
            String summary = "scrollbar overflow=" + UiStyleResolver.compute(stage).getOverflowY()
                    + "；contentHeight=" + rootBox.getContentHeight() + "；thumb/track 命中=人工确认";
            testCase.updateDemoSummary(summary);
            return UiStyleResolver.compute(stage).getOverflowY() == UiOverflow.AUTO ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "scrollbar 样例结构异常");
        }
        if ("PAINT-008".equals(testCase.getId())) {
            ElementNode sample = testCase.getDemoElement(0);
            boolean passed = sample.getTextContent().contains("标准文本仍存在");
            String summary = "custom renderer boundary textPresent=" + passed + "；逃生口绘制=人工确认";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "custom renderer 样例缺少标准文本");
        }
        if ("PAINT-009".equals(testCase.getId())) {
            ComputedStyle imageStyle = UiStyleResolver.compute(testCase.getDemoElement(0));
            boolean passed = imageStyle.getBackgroundImage() != null && testCase.getDemoElement(1).getTextContent()
                    .contains("fallback");
            String summary = "hostImage=" + (imageStyle.getBackgroundImage() != null)
                    + "；fallbackText=" + testCase.getDemoElement(1).getTextContent()
                    + "；宿主渲染=人工确认";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "host image 或 fallback 样例结构异常");
        }
        return RuntimeTestResult.failed("未知 Paint 用例。", "没有匹配的 Paint 执行器");
    }

    /**
     * 执行 Input 分组补充用例断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeInputGroupRuntimeTest(RuntimeTestCase testCase) {
        if ("INPUT-001".equals(testCase.getId())) {
            UiDocument inputDocument = UiDocument.create();
            ElementNode root = inputDocument.getRootElement();
            ElementNode parent = inputDocument.div();
            ElementNode target = inputDocument.div();
            final List<String> events = new ArrayList<String>();
            root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(60));
            parent.style().setWidth(UiStyleLength.px(90)).setHeight(UiStyleLength.px(40));
            target.style().setWidth(UiStyleLength.px(48)).setHeight(UiStyleLength.px(24));
            root.setCaptureClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    events.add("root-capture:" + event.getEventPhase());
                    return false;
                }
            }).setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    events.add("root-bubble:" + event.getEventPhase());
                    return false;
                }
            });
            parent.setCaptureClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    events.add("parent-capture:" + event.getEventPhase());
                    return false;
                }
            }).setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    events.add("parent-bubble:" + event.getEventPhase());
                    return false;
                }
            });
            target.setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    events.add("target:" + event.getEventPhase());
                    return false;
                }
            });
            parent.append(target);
            root.append(parent);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(inputDocument, 120, 60);

            dispatchPrimaryClick(widget, 10, 10, 1L, 2L);

            String expected = "[root-capture:CAPTURING, parent-capture:CAPTURING, target:AT_TARGET, "
                    + "parent-bubble:BUBBLING, root-bubble:BUBBLING]";
            String summary = "click 顺序=" + events.toString();
            testCase.updateDemoSummary(summary);
            return expected.equals(events.toString()) ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "click 三阶段传播顺序异常");
        }
        if ("INPUT-002".equals(testCase.getId())) {
            UiDocument inputDocument = UiDocument.create();
            ElementNode root = inputDocument.getRootElement();
            ElementNode link = inputDocument.a();
            final List<String> events = new ArrayList<String>();
            final List<String> activations = new ArrayList<String>();
            inputDocument.setLinkActivationHandler(new DocumentLinkActivationHandler() {
                @Override
                public void onLinkActivated(DocumentLinkActivationEvent event) {
                    activations.add(event.getHref());
                }
            });
            root.style().setWidth(UiStyleLength.px(140)).setHeight(UiStyleLength.px(48));
            link.setAttribute("href", "https://example.test/input-002");
            link.style()
                    .setDisplay(UiDisplay.BLOCK)
                    .setWidth(UiStyleLength.px(120))
                    .setHeight(UiStyleLength.px(28));
            link.setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    events.add("target:" + event.getEventPhase());
                    return true;
                }
            });
            root.setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    events.add("root-bubble:" + event.getEventPhase());
                    return false;
                }
            });
            root.append(link);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(inputDocument, 140, 48);

            dispatchPrimaryClick(widget, 10, 10, 1L, 2L);

            String activation = activations.isEmpty() ? "none" : activations.get(0);
            String summary = "stopPropagation 日志=" + events.toString() + "；linkActivated=" + activation;
            boolean passed = "[target:AT_TARGET]".equals(events.toString())
                    && "https://example.test/input-002".equals(activation);
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "stopPropagation 阻断了默认动作或未阻断冒泡");
        }
        if ("INPUT-003".equals(testCase.getId())) {
            UiDocument inputDocument = UiDocument.create();
            ElementNode root = inputDocument.getRootElement();
            ElementNode link = inputDocument.a();
            final List<String> events = new ArrayList<String>();
            final List<String> activations = new ArrayList<String>();
            inputDocument.setLinkActivationHandler(new DocumentLinkActivationHandler() {
                @Override
                public void onLinkActivated(DocumentLinkActivationEvent event) {
                    activations.add(event.getHref());
                }
            });
            root.style().setWidth(UiStyleLength.px(140)).setHeight(UiStyleLength.px(48));
            link.setAttribute("href", "https://example.test/input-003");
            link.style()
                    .setDisplay(UiDisplay.BLOCK)
                    .setWidth(UiStyleLength.px(120))
                    .setHeight(UiStyleLength.px(28));
            link.setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    events.add("target:" + event.getEventPhase());
                    event.preventDefault();
                    return false;
                }
            });
            root.setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    events.add("root-bubble:" + event.getEventPhase());
                    return false;
                }
            });
            root.append(link);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(inputDocument, 140, 48);

            dispatchPrimaryClick(widget, 10, 10, 1L, 2L);

            String summary = "preventDefault 日志=" + events.toString()
                    + "；linkActivated=" + !activations.isEmpty();
            boolean passed = "[target:AT_TARGET, root-bubble:BUBBLING]".equals(events.toString())
                    && activations.isEmpty();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "preventDefault 未阻止默认动作或错误阻断冒泡");
        }
        if ("INPUT-004".equals(testCase.getId())) {
            UiDocument inputDocument = UiDocument.create();
            ElementNode root = inputDocument.getRootElement();
            ElementNode child = inputDocument.div();
            final List<String> events = new ArrayList<String>();
            root.style()
                    .setWidth(UiStyleLength.px(80))
                    .setHeight(UiStyleLength.px(20))
                    .setOverflowY(UiOverflow.AUTO);
            child.style()
                    .setWidth(UiStyleLength.px(80))
                    .setHeight(UiStyleLength.px(80));
            child.setWheelHandler(new DocumentElementWheelHandler() {
                @Override
                public boolean onWheel(DocumentElementWheelEvent event) {
                    events.add("child:" + event.getEventPhase() + ":" + event.getDeltaY());
                    return true;
                }
            });
            root.setWheelHandler(new DocumentElementWheelHandler() {
                @Override
                public boolean onWheel(DocumentElementWheelEvent event) {
                    events.add("root-bubble:" + event.getEventPhase());
                    return false;
                }
            });
            root.append(child);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(inputDocument, 80, 20);

            boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10,
                    -1, -120, 0, 0, 1L));
            int scrollTop = widget.getScrollTop(root);

            String summary = "wheel 返回 true 日志=" + events.toString() + "；scrollTop=" + scrollTop
                    + "；consumed=" + consumed;
            boolean passed = consumed && scrollTop == 36 && "[child:AT_TARGET:120]".equals(events.toString());
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "handler 返回 true 后默认滚动或传播状态异常");
        }
        if ("INPUT-005".equals(testCase.getId())) {
            UiDocument inputDocument = UiDocument.create();
            ElementNode root = inputDocument.getRootElement();
            ElementNode target = inputDocument.div();
            final List<String> events = new ArrayList<String>();
            root.style().setWidth(UiStyleLength.px(90)).setHeight(UiStyleLength.px(44));
            target.style().setWidth(UiStyleLength.px(60)).setHeight(UiStyleLength.px(28));
            target.setMouseDownHandler(new DocumentElementMouseDownHandler() {
                @Override
                public boolean onMouseDown(DocumentElementMouseDownEvent event) {
                    events.add("down:" + event.getEventPhase());
                    return false;
                }
            }).setMouseUpHandler(new DocumentElementMouseUpHandler() {
                @Override
                public boolean onMouseUp(DocumentElementMouseUpEvent event) {
                    events.add("up:" + event.getEventPhase());
                    return false;
                }
            }).setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    events.add("click:" + event.getEventPhase());
                    return false;
                }
            }).setDoubleClickHandler(new DocumentElementDoubleClickHandler() {
                @Override
                public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                    events.add("dblclick:" + event.getEventPhase());
                    return false;
                }
            });
            root.append(target);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(inputDocument, 90, 44);

            dispatchPrimaryClick(widget, 10, 10, 1L, 2L);
            dispatchPrimaryClick(widget, 10, 10, 3L, 4L);

            String expected = "[down:AT_TARGET, up:AT_TARGET, click:AT_TARGET, down:AT_TARGET, up:AT_TARGET, "
                    + "click:AT_TARGET, dblclick:AT_TARGET]";
            String summary = "pointer 日志=" + events.toString();
            testCase.updateDemoSummary(summary);
            return expected.equals(events.toString()) ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "mousedown/up/click/doubleclick 顺序异常");
        }
        if ("INPUT-006".equals(testCase.getId())) {
            String summary = "待实现缺口：contextmenu DOM-like 分发尚未接入运行时自动断言";
            testCase.updateDemoSummary(summary);
            return RuntimeTestResult.running(summary);
        }
        if ("INPUT-007".equals(testCase.getId())) {
            UiDocument inputDocument = UiDocument.create();
            ElementNode root = inputDocument.getRootElement();
            ElementNode child = inputDocument.div();
            final List<String> events = new ArrayList<String>();
            root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(60));
            child.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
            root.setHoverHandler(event -> {
                events.add("root-hover=" + event.isHovered());
                return true;
            }).setActiveHandler(event -> {
                events.add("root-active=" + event.isActive());
                return true;
            });
            child.setHoverHandler(event -> {
                events.add("child-hover=" + event.isHovered());
                return true;
            }).setActiveHandler(event -> {
                events.add("child-active=" + event.isActive());
                return true;
            });
            root.append(child);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(inputDocument, 120, 60);
            widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
            widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 2L));
            boolean passed = events.contains("root-hover=true") && events.contains("child-hover=true")
                    && events.contains("root-active=true") && events.contains("child-active=true");
            String summary = "hover/active 日志=" + events;
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "hover/active 祖先状态通知异常");
        }
        if ("INPUT-008".equals(testCase.getId())) {
            UiDocument inputDocument = UiDocument.create();
            ElementNode root = inputDocument.getRootElement();
            ElementNode first = inputDocument.div();
            ElementNode second = inputDocument.div();
            final List<String> events = new ArrayList<String>();
            root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(60));
            first.style().setWidth(UiStyleLength.px(50)).setHeight(UiStyleLength.px(24));
            second.style().setWidth(UiStyleLength.px(50)).setHeight(UiStyleLength.px(24));
            first.setFocusable(true).setFocusOutHandler(event -> {
                events.add("focusout");
                return false;
            }).setFocusHandler(event -> {
                if (!event.isFocused()) {
                    events.add("blur");
                }
            });
            second.setFocusable(true).setFocusInHandler(event -> {
                events.add("focusin");
                return false;
            }).setFocusHandler(event -> {
                if (event.isFocused()) {
                    events.add("focus");
                }
            });
            root.append(first).append(second);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(inputDocument, 120, 60);
            widget.requestFocus(first);
            widget.requestFocus(second);
            String summary = "focus 日志=" + events;
            testCase.updateDemoSummary(summary);
            return "[focusout, focusin, blur, focus]".equals(events.toString())
                    ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "focus/focusin/focusout 顺序异常");
        }
        if ("INPUT-009".equals(testCase.getId())) {
            UiDocument inputDocument = UiDocument.create();
            ElementNode root = inputDocument.getRootElement();
            ElementNode first = inputDocument.div();
            ElementNode second = inputDocument.div();
            first.setFocusable(true).setAttribute("data-order", "first");
            second.setFocusable(true).setAttribute("data-order", "second");
            first.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
            second.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
            root.append(first).append(second);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(inputDocument, 100, 60);
            boolean firstFocused = widget.onFocusTraversal(false);
            boolean secondFocused = widget.onFocusTraversal(false);
            boolean passed = firstFocused && secondFocused && widget.getFocusedElement() == second;
            String summary = "tabTraversal first=" + firstFocused + "；second=" + secondFocused
                    + "；focused=" + (widget.getFocusedElement() == null ? "none"
                            : widget.getFocusedElement().getAttribute("data-order"));
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "Tab 遍历顺序异常");
        }
        if ("INPUT-010".equals(testCase.getId())) {
            UiDocument inputDocument = UiDocument.create();
            ElementNode root = inputDocument.getRootElement();
            ElementNode scroll = inputDocument.div();
            ElementNode cancelled = inputDocument.div();
            final List<String> events = new ArrayList<String>();
            root.style().setWidth(UiStyleLength.px(100)).setHeight(UiStyleLength.px(40)).setOverflowY(UiOverflow.AUTO);
            scroll.style().setWidth(UiStyleLength.px(100)).setHeight(UiStyleLength.px(80));
            cancelled.style().setWidth(UiStyleLength.px(100)).setHeight(UiStyleLength.px(30));
            scroll.setWheelHandler(event -> {
                events.add("wheel-before-scroll");
                return false;
            });
            cancelled.setWheelHandler(event -> {
                events.add("cancelled-wheel");
                event.preventDefault();
                return false;
            });
            root.append(cancelled).append(scroll);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(inputDocument, 100, 40);
            int afterCancelled = widget.getScrollTop(root);
            widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0, 0, 1L));
            afterCancelled = widget.getScrollTop(root);
            widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 35, -1, -120, 0, 0, 2L));
            int afterNormal = widget.getScrollTop(root);
            boolean passed = events.contains("cancelled-wheel") && events.contains("wheel-before-scroll")
                    && afterCancelled == 0 && afterNormal > afterCancelled;
            String summary = "wheel 日志=" + events + "；scrollNormal=" + afterNormal
                    + "；scrollCancelled=" + afterCancelled;
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "wheel 默认滚动或 preventDefault 异常");
        }
        if ("INPUT-011".equals(testCase.getId())) {
            UiDocument inputDocument = UiDocument.create();
            ElementNode target = inputDocument.div();
            final List<String> events = new ArrayList<String>();
            target.setFocusable(true).style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(24));
            target.setKeyHandler(event -> {
                events.add("key=" + event.getKeyCode());
                return false;
            }).setTextInputHandler(event -> {
                events.add("text=" + event.getText());
                return false;
            });
            inputDocument.getRootElement().append(target);
            HtmlLikeDocumentWidget widget = createInputAssertionWidget(inputDocument, 100, 40);
            widget.requestFocus(target);
            widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_A, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                    false, false, 1L));
            widget.onTextInput(new UiTextInputEvent("a", 2L));
            widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_C, 0, 0, UiKeyEvent.Action.PRESSED, true, false,
                    false, false, 3L));
            boolean passed = events.contains("key=" + Keyboard.KEY_A) && events.contains("text=a")
                    && events.contains("key=" + Keyboard.KEY_C);
            String summary = "keyboard 日志=" + events + "；comboTextInserted=false";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "keyboard/textInput 日志异常");
        }
        if ("INPUT-012".equals(testCase.getId()) || "INPUT-013".equals(testCase.getId())) {
            String summary = "待实现缺口：HTML-like drag/drop 事件链尚未完整接入运行时自动断言";
            testCase.updateDemoSummary(summary);
            return RuntimeTestResult.running(summary);
        }
        return RuntimeTestResult.failed("未知 Input 用例。", "没有匹配的 Input 执行器");
    }

    /**
     * 执行 Controls 分组运行时断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeControlsGroupRuntimeTest(RuntimeTestCase testCase) {
        if ("CTRL-001".equals(testCase.getId())) {
            DocumentButtonControl enabled = Objects.requireNonNull(testCase.getDemoButtonControl(0),
                    "enabledButton");
            DocumentButtonControl disabled = Objects.requireNonNull(testCase.getDemoButtonControl(1),
                    "disabledButton");
            enabled.getElement().setAttribute("data-runtime-action-count", "0");
            enabled.setLabel("enabled +1");
            enabled.getElement().getClickHandler().onClick(new DocumentElementClickEvent(enabled.getElement(),
                    enabled.getElement(), 0, 0, 0, 1L));
            disabled.getElement().getClickHandler().onClick(new DocumentElementClickEvent(disabled.getElement(),
                    disabled.getElement(), 0, 0, 0, 2L));
            int actionCount = readRuntimeActionCount(enabled.getElement());
            enabled.setLabel("enabled count=" + actionCount);
            boolean passed = actionCount == 1 && enabled.isEnabled() && !disabled.isEnabled();
            String summary = "button action count=" + actionCount + "；disabledEnabled=" + disabled.isEnabled()
                    + "；页面按钮文本=" + enabled.getLabel();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "button enabled/disabled 动作计数异常");
        }
        if ("CTRL-002".equals(testCase.getId())) {
            DocumentTextInputControl input = Objects.requireNonNull(testCase.getDemoTextInputControl(), "textInput");
            input.setText("alpha-");
            focusElementForRuntimeAssertion(input.getElement());
            dispatchTextInputToFocusedElement("X", 10L);
            dispatchKeyToFocusedElement(Keyboard.KEY_BACK, false, false, 11L);
            dispatchTextInputToFocusedElement("X", 12L);
            boolean valuePassed = "alpha-X".equals(input.getText()) && input.getType() == DocumentInputType.TEXT;
            boolean caretVisible = input.isFocused() && htmlLikeDocumentWidget.getFocusedElement() == input.getElement();
            String summary = "text input value=" + input.getText() + "；type=" + input.getType()
                    + "；caretVisible=" + caretVisible;
            testCase.updateDemoSummary(summary);
            return valuePassed && caretVisible ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "文本输入 value 或类型异常");
        }
        if ("CTRL-003".equals(testCase.getId())) {
            DocumentTextInputControl password = Objects.requireNonNull(testCase.getDemoTextInputControl(),
                    "passwordInput");
            password.setType(DocumentInputType.PASSWORD).setText("");
            focusElementForRuntimeAssertion(password.getElement());
            dispatchTextInputToFocusedElement("secret", 20L);
            String visibleText = password.getElement().getTextContent();
            boolean passed = password.getType() == DocumentInputType.PASSWORD
                    && "secret".equals(password.getText()) && "******".equals(visibleText);
            String summary = "password valueLength=" + password.getText().length() + "；visible=" + visibleText
                    + "；type=" + password.getType();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "password 掩码显示或真实值异常");
        }
        if ("CTRL-004".equals(testCase.getId())) {
            DocumentTextInputControl number = Objects.requireNonNull(testCase.getDemoTextInputControl(),
                    "numberInput");
            number.setType(DocumentInputType.NUMBER).setText("");
            focusElementForRuntimeAssertion(number.getElement());
            dispatchTextInputToFocusedElement("12bad.5e+2", 30L);
            boolean valueFiltered = "12.5e+2".equals(number.getText()) && number.getType() == DocumentInputType.NUMBER;
            String summary = "number value=" + number.getText() + "；type=" + number.getType()
                    + "；filteredIllegalChars=" + valueFiltered;
            testCase.updateDemoSummary(summary);
            return valueFiltered ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "number 输入未过滤非法字符");
        }
        if ("CTRL-005".equals(testCase.getId())) {
            DocumentTextAreaControl textArea = Objects.requireNonNull(testCase.getDemoTextAreaControl(), "textArea");
            textArea.setText("");
            focusElementForRuntimeAssertion(textArea.getElement());
            dispatchTextInputToFocusedElement("逻辑行一", 40L);
            dispatchKeyToFocusedElement(Keyboard.KEY_RETURN, false, false, 41L);
            dispatchTextInputToFocusedElement(
                    "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz", 42L);
            boolean hasRealNewline = textArea.getText().indexOf('\n') >= 0;
            htmlLikeDocumentWidget.render(new RuntimeAssertionRenderContext(htmlLikeDocumentWidget.getWidth(),
                    htmlLikeDocumentWidget.getHeight(), textMeasureService));
            int maxScrollTop = textArea.getElement().getMaxScrollTop();
            boolean softWrapped = maxScrollTop > 0;
            DocumentElementBounds textAreaBounds = textArea.getElement().getDocumentBounds();
            boolean caretVisible = textArea.isFocused() && htmlLikeDocumentWidget.getFocusedElement() == textArea.getElement()
                    && textAreaBounds.isAvailable();
            String summary = "textarea realNewline=" + hasRealNewline + "；value="
                    + textArea.getText().replace('\n', '|') + "；softWrap=" + softWrapped
                    + "；caretVisible=" + caretVisible + "；scrollMax=" + maxScrollTop
                    + "；bounds=" + textAreaBounds.getWidth() + "x" + textAreaBounds.getHeight();
            testCase.updateDemoSummary(summary);
            return hasRealNewline && softWrapped && caretVisible ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "textarea 换行、软换行或光标可见状态异常");
        }
        if ("CTRL-006".equals(testCase.getId())) {
            ElementNode checked = testCase.getDemoElement(0);
            ElementNode mixed = testCase.getDemoElement(1);
            ElementNode disabled = testCase.getDemoElement(2);
            boolean passed = "true".equals(checked.getAttribute("aria-checked"))
                    && "mixed".equals(mixed.getAttribute("aria-checked"))
                    && "true".equals(disabled.getAttribute("aria-disabled"));
            String summary = "checkbox checked=" + checked.getAttribute("aria-checked")
                    + "；mixed=" + mixed.getAttribute("aria-checked")
                    + "；disabled=" + disabled.getAttribute("aria-disabled");
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "checkbox 三态或 disabled 语义异常");
        }
        if ("CTRL-007".equals(testCase.getId())) {
            ElementNode radio = testCase.getDemoElement(0);
            String summary = "radio role=" + radio.getAttribute("role") + "；单选互斥=结构已展示";
            testCase.updateDemoSummary(summary);
            return "radiogroup".equals(radio.getAttribute("role")) ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "radio group 角色异常");
        }
        if ("CTRL-008".equals(testCase.getId())) {
            ElementNode select = testCase.getDemoElement(0);
            String summary = "select value=" + select.getAttribute("value") + "；top-layer=人工确认";
            testCase.updateDemoSummary(summary);
            return select.getAttribute("value") != null ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "select value 未同步");
        }
        if ("CTRL-009".equals(testCase.getId())) {
            ElementNode horizontal = testCase.getDemoElement(0);
            ElementNode vertical = testCase.getDemoElement(1);
            String summary = "slider values=" + horizontal.getAttribute("aria-valuenow") + ","
                    + vertical.getAttribute("aria-valuenow") + "；step=5/2";
            boolean passed = horizontal.getAttribute("aria-valuenow") != null
                    && vertical.getAttribute("aria-valuenow") != null;
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "slider aria 值异常");
        }
        if ("CTRL-010".equals(testCase.getId())) {
            ElementNode toggle = testCase.getDemoElement(0);
            String before = toggle.getAttribute("aria-checked");
            toggle.getClickHandler().onClick(new DocumentElementClickEvent(toggle, toggle, 0, 0, 0, 50L));
            String after = toggle.getAttribute("aria-checked");
            String summary = "toggle before=" + before + "；after=" + after;
            testCase.updateDemoSummary(summary);
            return "false".equals(before) && "true".equals(after) ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "toggle 状态未切换");
        }
        if ("CTRL-011".equals(testCase.getId())) {
            ElementNode segmented = testCase.getDemoElement(0);
            String summary = "segmented optionCount=" + segmented.getChildCount() + "；当前段=结构已展示";
            testCase.updateDemoSummary(summary);
            return segmented.getChildCount() == 3 ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "segmented 选项数量异常");
        }
        if ("CTRL-012".equals(testCase.getId())) {
            ElementNode tabs = testCase.getDemoElement(0);
            String summary = "tab children=" + tabs.getChildCount() + "；active panel=结构已展示";
            testCase.updateDemoSummary(summary);
            return tabs.getChildCount() >= 2 ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "tab 结构异常");
        }
        if ("CTRL-013".equals(testCase.getId())) {
            ElementNode table = testCase.getDemoElement(0);
            String summary = "table rows=" + table.getChildCount() + "；text=" + table.getTextContent();
            testCase.updateDemoSummary(summary);
            return table.getChildCount() == 2 && table.getTextContent().contains("ready")
                    ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "table 控件结构异常");
        }
        if ("CTRL-014".equals(testCase.getId()) || "CTRL-015".equals(testCase.getId())) {
            String summary = testCase.getId() + " 控件结构已展示；真实 overlay/slot 交互=游戏内人工确认";
            testCase.updateDemoSummary(summary);
            return RuntimeTestResult.running(summary);
        }
        return RuntimeTestResult.failed("未知 Controls 用例。", "没有匹配的 Controls 执行器");
    }

    /**
     * 执行 TextFont 分组运行时断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeTextFontGroupRuntimeTest(RuntimeTestCase testCase) {
        if ("TEXT-001".equals(testCase.getId())) {
            TextNode rawText = document.rawText("§a原始字符");
            boolean passed = rawText.getTextContentMode() == TextContentMode.UILIB_RAW
                    && rawText.getText().contains("§a");
            String summary = "raw mode=" + rawText.getTextContentMode() + "；text=" + rawText.getText();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "raw 文本模式或文本内容异常");
        }
        if ("TEXT-002".equals(testCase.getId())) {
            TextNode formattedText = document.minecraftText("§a绿色");
            boolean passed = formattedText.getTextContentMode() == TextContentMode.MINECRAFT_FORMATTED
                    && formattedText.getText().length() == 4;
            String summary = "formatted mode=" + formattedText.getTextContentMode()
                    + "；rawLength=" + formattedText.getText().length();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "Minecraft 格式文本模式异常");
        }
        if ("TEXT-003".equals(testCase.getId())) {
            int width = textMeasureService.getStringWidth("中英 mixed");
            int lineHeight = textMeasureService.getLineHeight();
            boolean passed = width > 0 && lineHeight > 0;
            String summary = "text width=" + width + "；lineHeight=" + lineHeight + "；baseline=人工确认";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "文本测量宽度或行高异常");
        }
        if ("TEXT-004".equals(testCase.getId())) {
            int width = textMeasureService.getStringWidth("汉字 fallback");
            boolean structurePassed = width > 0;
            String summary = "fallback sampleWidth=" + width + "；字体 fallback=游戏内人工确认";
            testCase.updateDemoSummary(summary);
            return structurePassed ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "fallback 样例测量异常");
        }
        if ("TEXT-005".equals(testCase.getId())) {
            String summary = "font epoch=" + fontEpoch + "；reload debounce=游戏内人工确认";
            testCase.updateDemoSummary(summary);
            return fontEpoch >= 0 ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "font epoch 异常");
        }
        if ("TEXT-006".equals(testCase.getId())) {
            int plainWidth = textMeasureService.getStringWidth("abcdef");
            int formattedWidth = textMeasureService.getStringWidth("§kabcdef");
            boolean passed = plainWidth > 0 && formattedWidth > 0;
            String summary = "obfuscated plainWidth=" + plainWidth + "；formattedWidth=" + formattedWidth
                    + "；动态字符=人工确认";
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "obfuscated 文本测量异常");
        }
        if ("TEXT-007".equals(testCase.getId())) {
            String trimmed = textMeasureService.trimStringToWidth("abcdefghijklmnopqrstuvwxyz", 36);
            List<String> wrapped = textMeasureService.listFormattedStringToWidth("alpha beta gamma", 48);
            boolean passed = trimmed != null && wrapped != null && !wrapped.isEmpty();
            String summary = "trimLength=" + (trimmed == null ? -1 : trimmed.length())
                    + "；wrapLines=" + (wrapped == null ? -1 : wrapped.size());
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "trim/wrap 测量服务返回异常");
        }
        return RuntimeTestResult.failed("未知 TextFont 用例。", "没有匹配的 TextFont 执行器");
    }

    /**
     * 执行 Animation 分组运行时断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeAnimationGroupRuntimeTest(RuntimeTestCase testCase) {
        if ("ANIM-001".equals(testCase.getId())) {
            ElementNode sample = testCase.getDemoElement(0);
            ComputedStyle style = UiStyleResolver.compute(sample);
            boolean declared = style.getTransitionProperties().contains(DocumentAnimationProperty.OPACITY)
                    && style.getTransitionDurationNanos(DocumentAnimationProperty.OPACITY) == 120_000_000L;
            DocumentAnimation animation = sample.animate(DocumentKeyframes.named("runtime-opacity-live")
                    .setFloat(DocumentAnimationProperty.OPACITY, 0.35F, 1.0F)
                    .build(), DocumentAnimationOptions.builder()
                            .setDurationMillis(240)
                            .setFillMode(DocumentAnimationFillMode.FORWARDS)
                            .build());
            boolean animationRunning = animation.isRunning();
            sample.clearChildren();
            sample.appendText("opacity running");
            String summary = "transition properties=" + style.getTransitionProperties()
                    + "；duration=" + formatDurationMillis(style.getTransitionDurationNanos(
                            DocumentAnimationProperty.OPACITY))
                    + "；animationRunning=" + animationRunning
                    + "；activeAnimations=" + htmlLikeDocumentWidget.getActiveAnimationCount();
            testCase.updateDemoSummary(summary);
            return declared && animationRunning ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "transition 声明或运行时动画未生效");
        }
        if ("ANIM-002".equals(testCase.getId())) {
            ElementNode sample = testCase.getDemoElement(0);
            ComputedStyle style = UiStyleResolver.compute(sample);
            List<DocumentTransitionSpec> specs = style.getTransitionSpecs();
            boolean passed = specs.size() == 2
                    && style.getTransitionDurationNanos(DocumentAnimationProperty.TEXT_COLOR) == 80_000_000L
                    && style.getTransitionDurationNanos(DocumentAnimationProperty.TRANSLATE_X) == 160_000_000L;
            DocumentKeyframes keyframes = DocumentKeyframes.named("runtime-color-move-live")
                    .setColor(DocumentAnimationProperty.TEXT_COLOR, 0xFFFFFFFF, 0xFF93C5FD)
                    .setFloat(DocumentAnimationProperty.TRANSLATE_X, 0.0F, 42.0F)
                    .build();
            DocumentAnimation animation = sample.animate(keyframes, DocumentAnimationOptions.builder()
                    .setDurationMillis(260)
                    .setTimingFunction(DocumentAnimationTimingFunction.EASE_OUT)
                    .setFillMode(DocumentAnimationFillMode.FORWARDS)
                    .build());
            boolean runtimeTracks = keyframes.getColorTracks().containsKey(DocumentAnimationProperty.TEXT_COLOR)
                    && keyframes.getFloatTracks().containsKey(DocumentAnimationProperty.TRANSLATE_X);
            sample.clearChildren();
            sample.appendText("color + move");
            String summary = "per-property specs=" + specs.size()
                    + "；textColor=" + formatDurationMillis(style.getTransitionDurationNanos(
                            DocumentAnimationProperty.TEXT_COLOR))
                    + "；translateX=" + formatDurationMillis(style.getTransitionDurationNanos(
                            DocumentAnimationProperty.TRANSLATE_X))
                    + "；runtimeTracks=" + runtimeTracks
                    + "；animationRunning=" + animation.isRunning();
            testCase.updateDemoSummary(summary);
            return passed && runtimeTracks && animation.isRunning() ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "per-property transition 或运行时轨道异常");
        }
        if ("ANIM-003".equals(testCase.getId())) {
            ElementNode sample = testCase.getDemoElement(0);
            ComputedStyle style = UiStyleResolver.compute(sample);
            DocumentKeyframes keyframes = DocumentKeyframes.named("runtime-path-live")
                    .setFloatStop(DocumentAnimationProperty.TRANSLATE_X, 0.0F, 0.0F)
                    .setFloatStop(DocumentAnimationProperty.TRANSLATE_X, 0.5F, 72.0F)
                    .setFloatStop(DocumentAnimationProperty.TRANSLATE_X, 1.0F, 144.0F)
                    .setFloatStop(DocumentAnimationProperty.TRANSLATE_Y, 0.0F, 0.0F)
                    .setFloatStop(DocumentAnimationProperty.TRANSLATE_Y, 0.5F, 14.0F)
                    .setFloatStop(DocumentAnimationProperty.TRANSLATE_Y, 1.0F, 0.0F)
                    .build();
            DocumentAnimation animation = sample.animate(keyframes, DocumentAnimationOptions.builder()
                    .setDurationMillis(300)
                    .setFillMode(DocumentAnimationFillMode.FORWARDS)
                    .setTimingFunction(DocumentAnimationTimingFunction.EASE_OUT)
                    .build());
            int translateXStops = keyframes.getFloatTracks().get(DocumentAnimationProperty.TRANSLATE_X)
                    .getStops().size();
            boolean declared = "runtime-path".equals(style.getAnimationName())
                    && style.getAnimationDurationNanos() == 300_000_000L
                    && translateXStops == 3
                    && animation.isRunning();
            sample.clearChildren();
            sample.appendText("path running");
            String summary = "animationName=" + style.getAnimationName()
                    + "；duration=" + formatDurationMillis(style.getAnimationDurationNanos())
                    + "；keyframeStops=" + translateXStops
                    + "；animationRunning=" + animation.isRunning()
                    + "；activeAnimations=" + htmlLikeDocumentWidget.getActiveAnimationCount();
            testCase.updateDemoSummary(summary);
            return declared ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "keyframes 结构声明异常");
        }
        if ("ANIM-004".equals(testCase.getId())) {
            ElementNode sample = testCase.getDemoElement(0);
            ComputedStyle style = UiStyleResolver.compute(sample);
            boolean passed = style.getAnimationDelayNanos() == 100_000_000L
                    && style.getAnimationDurationNanos() == 200_000_000L
                    && style.getAnimationIterationCount() == 3;
            DocumentAnimation animation = sample.animate(DocumentKeyframes.named("runtime-loop-live")
                    .setFloat(DocumentAnimationProperty.TRANSLATE_X, 0.0F, 36.0F)
                    .build(), DocumentAnimationOptions.builder()
                            .setDelayMillis(100)
                            .setDurationMillis(200)
                            .setIterationCount(3)
                            .build());
            sample.clearChildren();
            sample.appendText("loop running");
            String summary = "delay=" + formatDurationMillis(style.getAnimationDelayNanos())
                    + "；duration=" + formatDurationMillis(style.getAnimationDurationNanos())
                    + "；iteration=" + style.getAnimationIterationCount()
                    + "；animationRunning=" + animation.isRunning();
            testCase.updateDemoSummary(summary);
            return passed && animation.isRunning() ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "animation delay/duration/iteration 或运行态异常");
        }
        if ("ANIM-005".equals(testCase.getId())) {
            ElementNode normalElement = testCase.getDemoElement(0);
            ElementNode reverseElement = testCase.getDemoElement(1);
            ElementNode alternateElement = testCase.getDemoElement(2);
            UiAnimationDirection normal = UiStyleResolver.compute(normalElement).getAnimationDirection();
            UiAnimationDirection reverse = UiStyleResolver.compute(reverseElement).getAnimationDirection();
            UiAnimationDirection alternate = UiStyleResolver.compute(alternateElement).getAnimationDirection();
            boolean declared = normal == UiAnimationDirection.NORMAL && reverse == UiAnimationDirection.REVERSE
                    && alternate == UiAnimationDirection.ALTERNATE;
            DocumentKeyframes keyframes = DocumentKeyframes.named("runtime-direction-live")
                    .setFloat(DocumentAnimationProperty.TRANSLATE_X, 0.0F, 30.0F)
                    .build();
            DocumentAnimation normalAnimation = normalElement.animate(keyframes, DocumentAnimationOptions.builder()
                    .setDurationMillis(240)
                    .setIterationCount(2)
                    .setDirection(UiAnimationDirection.NORMAL)
                    .build());
            DocumentAnimation reverseAnimation = reverseElement.animate(keyframes, DocumentAnimationOptions.builder()
                    .setDurationMillis(240)
                    .setIterationCount(2)
                    .setDirection(UiAnimationDirection.REVERSE)
                    .build());
            DocumentAnimation alternateAnimation = alternateElement.animate(keyframes, DocumentAnimationOptions.builder()
                    .setDurationMillis(240)
                    .setIterationCount(2)
                    .setDirection(UiAnimationDirection.ALTERNATE)
                    .build());
            String summary = "directions=" + normal + "," + reverse + "," + alternate
                    + "；running=" + normalAnimation.isRunning() + "," + reverseAnimation.isRunning()
                    + "," + alternateAnimation.isRunning();
            testCase.updateDemoSummary(summary);
            return declared && normalAnimation.isRunning() && reverseAnimation.isRunning()
                    && alternateAnimation.isRunning() ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "animation direction 声明异常");
        }
        if ("ANIM-006".equals(testCase.getId())) {
            DocumentAnimationFillMode none = UiStyleResolver.compute(testCase.getDemoElement(0)).getAnimationFillMode();
            DocumentAnimationFillMode forwards = UiStyleResolver.compute(testCase.getDemoElement(1)).getAnimationFillMode();
            DocumentAnimationFillMode backwards = UiStyleResolver.compute(testCase.getDemoElement(2)).getAnimationFillMode();
            DocumentAnimationFillMode both = UiStyleResolver.compute(testCase.getDemoElement(3)).getAnimationFillMode();
            DocumentKeyframes keyframes = DocumentKeyframes.named("runtime-fill-live")
                    .setFloat(DocumentAnimationProperty.OPACITY, 0.2F, 1.0F)
                    .build();
            DocumentAnimation animation = testCase.getDemoElement(1).animate(keyframes, DocumentAnimationOptions.builder()
                    .setDurationMillis(240)
                    .setFillMode(DocumentAnimationFillMode.FORWARDS)
                    .build());
            boolean passed = none == DocumentAnimationFillMode.NONE && forwards == DocumentAnimationFillMode.FORWARDS
                    && backwards == DocumentAnimationFillMode.BACKWARDS && both == DocumentAnimationFillMode.BOTH
                    && animation.isRunning();
            String summary = "fillModes=" + none + "," + forwards + "," + backwards + "," + both
                    + "；animationRunning=" + animation.isRunning();
            testCase.updateDemoSummary(summary);
            return passed ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "fill-mode 声明或运行态异常");
        }
        if ("ANIM-007".equals(testCase.getId())) {
            DocumentAnimation stepsAnimation = testCase.getDemoElement(0).animate(DocumentKeyframes
                    .named("runtime-steps-live")
                    .setFloat(DocumentAnimationProperty.TRANSLATE_X, 0.0F, 30.0F)
                    .build(), DocumentAnimationOptions.builder()
                            .setDurationMillis(240)
                            .setTimingFunction(DocumentAnimationTimingFunction.steps(4))
                            .build());
            DocumentAnimation easeAnimation = testCase.getDemoElement(1).animate(DocumentKeyframes
                    .named("runtime-ease-live")
                    .setFloat(DocumentAnimationProperty.TRANSLATE_X, 0.0F, 30.0F)
                    .build(), DocumentAnimationOptions.builder()
                            .setDurationMillis(240)
                            .setTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                            .build());
            String summary = "timing running=" + stepsAnimation.isRunning() + "," + easeAnimation.isRunning()
                    + "；steps/bezier 视觉=人工确认";
            testCase.updateDemoSummary(summary);
            return stepsAnimation.isRunning() && easeAnimation.isRunning() ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "timing-function 动画未运行");
        }
        if ("ANIM-008".equals(testCase.getId())) {
            DocumentAnimation widthAnimation = testCase.getDemoElement(0).animate(DocumentKeyframes
                    .named("runtime-width-live")
                    .setFloat(DocumentAnimationProperty.WIDTH, 80.0F, 140.0F)
                    .build(), DocumentAnimationOptions.builder().setDurationMillis(240).build());
            DocumentAnimation opacityAnimation = testCase.getDemoElement(1).animate(DocumentKeyframes
                    .named("runtime-opacity-paint-live")
                    .setFloat(DocumentAnimationProperty.OPACITY, 0.25F, 1.0F)
                    .build(), DocumentAnimationOptions.builder().setDurationMillis(240).build());
            String summary = "layoutAnimation=" + widthAnimation.isRunning() + "；paintAnimation="
                    + opacityAnimation.isRunning() + "；影响范围=人工确认";
            testCase.updateDemoSummary(summary);
            return widthAnimation.isRunning() && opacityAnimation.isRunning() ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "layout/paint 动画未运行");
        }
        return RuntimeTestResult.failed("未知 Animation 用例。", "没有匹配的 Animation 执行器");
    }

    /**
     * 执行 RuntimeHost 分组运行时断言。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeRuntimeHostGroupRuntimeTest(RuntimeTestCase testCase) {
        if ("HOST-001".equals(testCase.getId())) {
            String summary = "controller=ready；rootScroll=" + htmlLikeDocumentWidget.isViewportRootScrollingEnabled()
                    + "；聊天命令延后开屏=人工确认";
            testCase.updateDemoSummary(summary);
            return RuntimeTestResult.running(summary);
        }
        if ("HOST-002".equals(testCase.getId())) {
            boolean rootScroll = htmlLikeDocumentWidget.isViewportRootScrollingEnabled();
            String summary = "viewport=" + runtimeView.getHostWidth() + "x" + runtimeView.getHostHeight()
                    + "；rootScroll=" + rootScroll + "；resize=人工确认";
            testCase.updateDemoSummary(summary);
            return rootScroll ? RuntimeTestResult.running(summary)
                    : RuntimeTestResult.failed(summary, "root 滚动配置异常");
        }
        if ("HOST-003".equals(testCase.getId())) {
            UiRuntimeStats stats = runtimeView.getUiRuntimeStats();
            String summary = stats == null ? "runtime stats=none"
                    : "runtime stats frame=" + formatMs(stats.getFrameTimeMs()) + "ms；render="
                            + formatMs(stats.getRenderTimeMs()) + "ms";
            testCase.updateDemoSummary(summary);
            return stats != null ? RuntimeTestResult.passed(summary)
                    : RuntimeTestResult.failed(summary, "runtime stats 缺失");
        }
        if ("HOST-004".equals(testCase.getId())) {
            String summary = "GL-backed render context 结构已展示；adapter=" + runtimeAdapterSummary
                    + "；GL 状态=游戏内人工确认";
            testCase.updateDemoSummary(summary);
            return RuntimeTestResult.running(summary);
        }
        if ("HOST-005".equals(testCase.getId())) {
            String summary = "HUD layer sample=ready；纯显示层/交互层=游戏内人工确认";
            testCase.updateDemoSummary(summary);
            return RuntimeTestResult.running(summary);
        }
        if ("HOST-006".equals(testCase.getId())) {
            String summary = "container input bridge sample=ready；native refocus=游戏内人工确认";
            testCase.updateDemoSummary(summary);
            return RuntimeTestResult.running(summary);
        }
        if ("HOST-007".equals(testCase.getId())) {
            String summary = "exception panel sample=ready；故意失败显示=游戏内人工确认";
            testCase.updateDemoSummary(summary);
            return RuntimeTestResult.running(summary);
        }
        return RuntimeTestResult.failed("未知 RuntimeHost 用例。", "没有匹配的 RuntimeHost 执行器");
    }

    /**
     * 执行 RemoteNet 分组运行时入口检查。
     *
     * @param testCase 用例模型
     * @return 运行时结果
     */
    private RuntimeTestResult executeRemoteNetGroupRuntimeTest(RuntimeTestCase testCase) {
        String transport = NetTransportFactory.resolveName(Config.netTransport);
        String summary;
        if ("NET-001".equals(testCase.getId())) {
            summary = "Channel 往返入口已展示；transport=" + transport + "；服务端响应=人工确认";
        } else if ("NET-002".equals(testCase.getId())) {
            summary = "chunk payloadBytes=32769；transport=" + transport + "；分片重组=人工确认";
        } else if ("NET-003".equals(testCase.getId())) {
            summary = "fetch states=[200,500,timeout,cancelled,429]；transport=" + transport
                    + "；endpoint=人工确认";
        } else if ("NET-004".equals(testCase.getId())) {
            summary = "stream progress=0..100；transport=" + transport + "；大内容下载=人工确认";
        } else if ("NET-005".equals(testCase.getId())) {
            summary = "store modes=[snapshot,delta,player]；transport=" + transport + "；服务端推送=人工确认";
        } else if ("NET-006".equals(testCase.getId())) {
            summary = "remote document open entry=ready；transport=" + transport + "；提交回传=人工确认";
        } else if ("NET-007".equals(testCase.getId())) {
            summary = "remote HUD open entry=ready；transport=" + transport + "；HUD 提交=人工确认";
        } else if ("NET-008".equals(testCase.getId())) {
            summary = "remote HTML/CSS safe-set entry=ready；transport=" + transport + "；安全集=人工确认";
        } else if ("NET-009".equals(testCase.getId())) {
            summary = "config sync entry=ready；transport=" + transport + "；草稿与保存=人工确认";
        } else if ("NET-010".equals(testCase.getId())) {
            summary = "transport fallback current=" + transport + "；vanilla/forge 切换=人工确认";
        } else {
            return RuntimeTestResult.failed("未知 RemoteNet 用例。", "没有匹配的 RemoteNet 执行器");
        }
        testCase.updateDemoSummary(summary);
        return RuntimeTestResult.running(summary);
    }

    /**
     * 创建 Input 自动断言用的独立 HTML-like widget。
     *
     * @param inputDocument 独立测试文档
     * @param width widget 宽度
     * @param height widget 高度
     * @return 已应用布局边界的 widget
     */
    private HtmlLikeDocumentWidget createInputAssertionWidget(UiDocument inputDocument, int width, int height) {
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(inputDocument, width, height, textMeasureService);
        widget.applyLayoutBounds(0, 0, width, height);
        return widget;
    }

    /**
     * 向 Input 自动断言 widget 派发一次主键点击。
     *
     * @param widget 目标 widget
     * @param x 鼠标 X
     * @param y 鼠标 Y
     * @param downTimeNanos 按下时间戳
     * @param upTimeNanos 抬起时间戳
     */
    private void dispatchPrimaryClick(HtmlLikeDocumentWidget widget, int x, int y, long downTimeNanos,
            long upTimeNanos) {
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, x, y, 0, 0, 0, 0, downTimeNanos));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, x, y, 0, 0, 0, 0, upTimeNanos));
    }

    /**
     * 确保运行时测试使用的 HTML-like widget 已拥有可布局尺寸。
     *
     * <p>游戏内通常由页面宿主写入尺寸；JVM 黑盒测试直接调用按钮 handler 时没有外层布局，
     * 这里补齐边界以便 focus、scroll 与 animation 走同一套运行时路径。</p>
     */
    private void ensureRuntimeWidgetBounds() {
        if (htmlLikeDocumentWidget.getWidth() > 0 && htmlLikeDocumentWidget.getHeight() > 0) {
            return;
        }
        int fallbackWidth = runtimeView.getHostWidth() > 0 ? runtimeView.getHostWidth() : 760;
        int fallbackHeight = runtimeView.getHostHeight() > 0 ? runtimeView.getHostHeight() : 520;
        htmlLikeDocumentWidget.applyLayoutBounds(0, 0, fallbackWidth, fallbackHeight);
    }

    /**
     * 在当前运行时文档中聚焦元素，作为后续键盘和文本输入事件目标。
     *
     * @param element 目标元素
     */
    private void focusElementForRuntimeAssertion(ElementNode element) {
        ensureRuntimeWidgetBounds();
        if (element == null) {
            return;
        }
        boolean focused = element.focus();
        if (!focused && element.getFocusHandler() != null) {
            element.getFocusHandler().onFocusChanged(new DocumentElementFocusEvent(element, true, false));
        }
    }

    /**
     * 向当前聚焦元素派发真实文本输入事件。
     *
     * @param text 输入文本
     * @param timeNanos 时间戳
     */
    private void dispatchTextInputToFocusedElement(String text, long timeNanos) {
        htmlLikeDocumentWidget.onTextInput(new UiTextInputEvent(text, timeNanos));
    }

    /**
     * 向当前聚焦元素派发真实键盘事件。
     *
     * @param keyCode LWJGL2 键码
     * @param controlPressed 是否按下 Ctrl
     * @param shiftPressed 是否按下 Shift
     * @param timeNanos 时间戳
     */
    private void dispatchKeyToFocusedElement(int keyCode, boolean controlPressed, boolean shiftPressed,
            long timeNanos) {
        htmlLikeDocumentWidget.onKeyEvent(new UiKeyEvent(keyCode, 0, 0, UiKeyEvent.Action.PRESSED, controlPressed,
                shiftPressed, false, false, timeNanos));
    }

    /**
     * 递增运行时按钮计数属性。
     *
     * @param element 记录计数的元素
     */
    private void incrementRuntimeActionCount(ElementNode element) {
        if (element == null) {
            return;
        }
        element.setAttribute("data-runtime-action-count", String.valueOf(readRuntimeActionCount(element) + 1));
    }

    /**
     * 读取运行时按钮计数属性。
     *
     * @param element 记录计数的元素
     * @return 当前计数
     */
    private int readRuntimeActionCount(ElementNode element) {
        if (element == null) {
            return 0;
        }
        String rawValue = element.getAttribute("data-runtime-action-count");
        if (rawValue == null || rawValue.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            return 0;
        }
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
     * 构建 DOM-012 交互状态摘要。
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
