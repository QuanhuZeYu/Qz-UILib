package club.heiqi.uilib.ui.screen.example;

import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 诊断首页菜单控制器。
 */
public final class UiTestDocumentPageController extends DocumentPageController {

    private final DocumentPageAuthoringSurface diagnosticPage;
    private final UiTestMenuModel menuModel;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;

    /**
     * 创建诊断首页菜单控制器。
     *
     * @param documentUi 文档组件作用域
     * @param diagnosticPage 文档页面壳
     * @param menuModel 菜单跳转模型
     */
    public UiTestDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface diagnosticPage,
            UiTestMenuModel menuModel) {
        DocumentUiScope resolvedDocumentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.diagnosticPage = Objects.requireNonNull(diagnosticPage, "diagnosticPage");
        this.menuModel = Objects.requireNonNull(menuModel, "menuModel");

        UiDocument document = UiDocument.create();
        document.setDefaultTextContentMode(resolvedDocumentUi.getDefaultTextContentMode());
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 760, 520,
                resolvedDocumentUi.getTextMeasureService());
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        createMenuDocument(document, document.getRootElement());
    }

    @Override
    public void configureDocumentPage() {
        diagnosticPage.setContentWidthRange(700, 1080)
                .setMinContentHeight(540)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    public void buildDocument() {
        diagnosticPage.addBlock(htmlLikeDocumentWidget);
    }

    /**
     * 返回当前诊断菜单使用的 HTML-like 文档适配组件。
     *
     * @return HTML-like 文档适配组件
     */
    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    /**
     * 构建诊断菜单 HTML-like 文档。
     *
     * @param document 文档实例
     * @param root 根元素
     */
    private void createMenuDocument(UiDocument document, ElementNode root) {
        root.style()
                .setPadding(UiStyleLength.px(22))
                .setBackgroundColor(0xF00A1020)
                .setBorderColor(0xFF5B7CFA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(24))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(0xFFE8EEFF);

        appendHero(document, root);
        appendStatusStrip(document, root);
        appendNavigationGrid(document, root);
    }

    private void appendHero(UiDocument document, ElementNode root) {
        ElementNode hero = document.div();
        hero.style()
                .setHeight(UiStyleLength.px(132))
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF101D33)
                .setBorderColor(0xFF6B96FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        hero.appendText("诊断指挥台");
        hero.appendText("所有入口已切换为 UiDocument 驱动：页面作者层不再拼装旧 Widget 卡片、按钮或工具栏。");
        hero.appendText("目标链路：HTML-like DOM -> style -> layout -> paint command -> GL-backed UiRenderContext。");
        root.append(hero);
    }

    private void appendStatusStrip(UiDocument document, ElementNode root) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(92))
                .setMargin(UiStyleLength.px(14));
        root.append(row);

        appendStatusCard(document, row, "核心", "DOM / Style / Layout / Paint");
        appendStatusCard(document, row, "输入", "Click / Text / Tab / Focus-visible");
        appendStatusCard(document, row, "清退", "旧 Widget 作者入口停止扩张");
    }

    private void appendStatusCard(UiDocument document, ElementNode parent, String title, String body) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF17243A)
                .setBorderColor(0xFF2E4C7F)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFD8E4FF);
        card.appendText(title);
        card.appendText(body);
        parent.append(card);
    }

    private void appendNavigationGrid(UiDocument document, ElementNode root) {
        ElementNode outer = document.div();
        outer.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(16));
        root.append(outer);

        ElementNode firstRow = appendNavigationRow(document, outer);
        ElementNode secondRow = appendNavigationRow(document, outer);
        ElementNode thirdRow = appendNavigationRow(document, outer);
        ElementNode fourthRow = appendNavigationRow(document, outer);

        appendNavigationCard(document, firstRow, "布局诊断子页",
                "继续检查页面壳尺寸、文本测量、滚动区域和运行时统计。", "进入布局诊断页",
                new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        menuModel.openLayoutDiagnostics();
                    }
                });
        appendNavigationCard(document, firstRow, "字体性能基线",
                "在真实 GUI 环境压测字体绘制，观察字符上传、四边形与 UI 帧耗时基线。", "进入字体基线",
                new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        menuModel.openFontPerformanceBaseline();
                    }
                });
        appendNavigationCard(document, secondRow, "HTML-like Smoke 子页",
                "验证 HTML 核心链路、控件输入、裁剪、滚动和绘制命令投影。", "进入 HTML-like Smoke",
                new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        menuModel.openHtmlLikeSmoke();
                    }
                });
        appendNavigationCard(document, secondRow, "Large Glass Lab 子页",
                "打开单独的大面积磨玻璃测试页，便于观察同层采样、裁剪和面积放大后的视觉稳定性。", "进入 Glass Lab",
                new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        menuModel.openHtmlLikeGlass();
                    }
                });
        appendNavigationCard(document, thirdRow, "背包概览示例页",
                "打开真实 Minecraft 背包数据驱动的 HTML-like 示例页，检查槽位、tooltip 和交互边界。", "进入背包概览",
                new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        menuModel.openInventoryOverview();
                    }
                });
        appendNavigationCard(document, thirdRow, "列表元素组件拖拽",
                "用普通 HTML-like 元素组合可排序列表，验证列表项把手拖拽和 DOM 状态同步。", "进入拖拽列表",
                new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        menuModel.openListElementDrag();
                    }
                });
        appendNavigationCard(document, fourthRow, "浏览器语义展示",
                "展示已接入运行时的 CSS 选择器、事件传播、DOM 查询、cursor、pointer-events、文本装饰、宽高比、object-fit 与变量容器能力。", "进入语义展示",
                new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        menuModel.openBrowserSemanticsShowcase();
                    }
                });
    }

    private ElementNode appendNavigationRow(UiDocument document, ElementNode parent) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(16))
                .setHeight(UiStyleLength.px(176));
        parent.append(row);
        return row;
    }

    private void appendNavigationCard(UiDocument document, ElementNode parent, String title, String body,
            String buttonText, DocumentButtonActionHandler actionHandler) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(0xFF1D2A44)
                .setBorderColor(0xFF405F9C)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16))
                .setTextColor(0xFFEAF1FF);
        card.appendText(title);
        card.appendText(body);

        DocumentButtonControl button = new DocumentButtonControl(document, buttonText);
        button.setBackgroundColors(0xFF3B82F6, 0xFF1D4ED8, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE)
                .setActionHandler(actionHandler);
        button.getElement().setAttribute("data-diagnostic-nav", buttonText);
        button.getElement().style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        card.append(button.getElement());
        parent.append(card);
    }
}
