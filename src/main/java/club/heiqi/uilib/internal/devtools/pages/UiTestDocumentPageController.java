package club.heiqi.uilib.internal.devtools.pages;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `/qzuilib test` 重构规划页控制器。
 */
public final class UiTestDocumentPageController extends DocumentPageController {

    private static final String SPEC_PATH = "docs/开发者文档/specs/qzuilib-test-page-rebuild-plan.md";

    private final DocumentPageAuthoringSurface diagnosticPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;

    /**
     * 创建 test 页面重构规划页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param diagnosticPage 文档页面壳
     */
    public UiTestDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface diagnosticPage) {
        DocumentUiScope resolvedDocumentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.diagnosticPage = Objects.requireNonNull(diagnosticPage, "diagnosticPage");

        UiDocument document = UiDocument.create();
        document.setDefaultTextContentMode(resolvedDocumentUi.getDefaultTextContentMode());
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 760, 520,
                resolvedDocumentUi.getTextMeasureService());
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        createPlannerDocument(document, document.getRootElement());
    }

    /**
     * 配置规划页宿主尺寸。
     */
    @Override
    public void configureDocumentPage() {
        diagnosticPage.setContentWidthRange(720, 1120)
                .setMinContentHeight(540)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    /**
     * 挂载 HTML-like 规划文档。
     */
    @Override
    public void buildDocument() {
        diagnosticPage.addBlock(htmlLikeDocumentWidget);
    }

    /**
     * 返回当前规划页使用的 HTML-like 文档适配组件。
     *
     * @return HTML-like 文档适配组件
     */
    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    /**
     * 构建 test 页面重构规划文档。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void createPlannerDocument(UiDocument document, ElementNode root) {
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
        appendSection(document, root, "当前状态", new String[] {
                "旧 `/qzuilib test` 子页已清空，本入口暂时只展示系统性重构规划。",
                "重构目标：用一个可持续扩展的测试系统覆盖 DOM、CSS、布局、绘制、事件、控件、动画、宿主、远程与网络语义。",
                "详细矩阵：" + SPEC_PATH
        });
        appendSection(document, root, "计划放入的测试分层", new String[] {
                "01 DOM 与选择器语义：节点移动、fragment、属性、classList、querySelector、伪类和语义元素。",
                "02 CSS 级联与样式语义：继承、优先级、单位、盒模型、文本样式、背景、边框、阴影和变量边界。",
                "03 布局与尺寸语义：block、inline、flex、table、position、sticky、fixed containing block、scroll range 和 margin collapse。",
                "04 绘制与视觉语义：stacking context、clip chain、opacity、transform、top-layer、cursor、tooltip 和命中一致性。",
                "05 输入与事件语义：capture/bubble、preventDefault、stopPropagation、focus/focusin/focusout、wheel、keyboard、drag 和 hover/active。",
                "06 控件与表单语义：button、text/password/number input、textarea、checkbox、radio、select、slider、tab、slot、table 与 overlay。",
                "07 文本、字体与国际化语义：Minecraft 格式码、raw 文本、软换行、white-space、overflow-wrap、line-height、font fallback 和 reload。",
                "08 动画与 transition 语义：transition 生命周期、keyframe、direction、iteration、fill-mode、steps/cubic-bezier 与动画中布局/绘制影响。",
                "09 宿主运行时语义：Screen、HUD、容器态输入、resize、runtime stats、GL-backed render context 和异常面板。",
                "10 远程、配置与网络语义：RemoteDocument、RemoteHud、Config sync、Net Channel/Fetch/Stream/Store 与传输回退。"
        });
        appendSection(document, root, "运行时用例统一展示规则", new String[] {
                "每个运行时卡片必须固定显示：用例编号、覆盖语义、操作步骤、预期结果、实际结果、通过/失败。",
                "人工预期文本必须直接写在页面上，格式固定为 `预期结果：...`，不能只写在文档或日志里。",
                "人工执行后状态文本固定为 `通过：观察结果与预期一致` 或 `失败：观察结果与预期不一致 - <差异说明>`。",
                "需要服务端、HUD、远程页面或 GL 视觉判断的用例必须标注 `人工确认`，并保留截图/日志可追溯位置。"
        });
        appendSection(document, root, "首批运行时人工预期文本样例", new String[] {
                "预期结果：点击按钮后计数加 1，按钮保持可再次点击，焦点环停留在按钮上。",
                "预期结果：滚轮先记录 wheel 事件；未 preventDefault 的区域滚动条移动，preventDefault 的区域滚动条不移动。",
                "预期结果：输入框输入中文、英文和 Minecraft 格式码后，光标位置、选择区和最终 value 文本一致。",
                "预期结果：fixed 子元素在普通祖先下相对视口固定，在 transform 祖先下相对该祖先 padding box 固定。",
                "预期结果：远程页面点击提交后服务端返回结果页，页面显示 `远程页面提交通过`。",
                "预期结果：网络全部执行后汇总显示 `通过：Channel/Fetch/Stream/Store 往返均完成`。"
        });
        appendSection(document, root, "重建顺序", new String[] {
                "第一步：建立 test 首页、分组索引、结果记录模型和人工预期文本规范。",
                "第二步：按 DOM/CSS/Layout/Paint/Input/Controls/Animation/Runtime/RemoteNet 顺序逐组补页面。",
                "第三步：每个页面先补 JVM 边界测试，再接运行时卡片，最后接人工验收说明。",
                "第四步：旧 smoke 名称不复用为主结构，只在新矩阵中按语义归档。"
        });
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
        title.appendText("Qz UILib Test 页面重构规划");
        hero.append(title);

        ElementNode body = document.div();
        body.style()
                .setMargin(UiStyleLength.px(8))
                .setTextColor(0xFFD9E6FF);
        body.appendText("当前阶段先删除旧页面集合，重新定义覆盖全面、可人工验收、可持续扩展的运行时测试系统。");
        hero.append(body);
        root.append(hero);
    }

    /**
     * 追加规划章节。
     *
     * @param document 文档实例
     * @param root 根元素
     * @param title 章节标题
     * @param items 章节条目
     */
    private void appendSection(UiDocument document, ElementNode root, String title, String[] items) {
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

        for (int i = 0; i < items.length; i++) {
            appendPlanItem(document, section, items[i]);
        }
        root.append(section);
    }

    /**
     * 追加单条规划说明。
     *
     * @param document 文档实例
     * @param parent 父元素
     * @param text 条目文本
     */
    private void appendPlanItem(UiDocument document, ElementNode parent, String text) {
        ElementNode item = document.div();
        item.style()
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF1D2A44)
                .setBorderColor(0xFF334B7A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10))
                .setTextColor(0xFFEAF1FF);
        item.appendText(text);
        parent.append(item);
    }
}
