package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.control.InventorySlotSnapshot;
import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.control.DocumentInventorySlotGridControl;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 迁移版背包诊断页控制器。
 */
final class HtmlLikeInventoryOverviewDocumentPageController extends DocumentPageController {

    private final DocumentUiScope documentUi;
    private final DocumentPageAuthoringSurface documentPage;
    private final DocumentPageRuntimeView runtimeView;
    private final InventoryOverviewModel model;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;

    private final TextNode overviewMetricsText;
    private final TextNode hotbarMetricsText;
    private final TextNode backpackMetricsText;
    private final DocumentInventorySlotGridControl hotbarGrid;
    private final DocumentInventorySlotGridControl backpackGrid;

    private static final int TITLE_COLOR = 0xFFF0F4FF;
    private static final int BODY_COLOR = 0xFFC0CAE8;

    HtmlLikeInventoryOverviewDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage, DocumentPageRuntimeView runtimeView,
            InventoryOverviewModel model) {
        this.documentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        this.model = Objects.requireNonNull(model, "model");
        TextMeasureService resolvedTextMeasure = documentUi.getTextMeasureService();

        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xEE121726)
                .setBorderColor(0xFF4A78D8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setTextColor(BODY_COLOR);

        this.overviewMetricsText = appendCardWithHeader(document, root, "当前状态",
                "窗口信息与布局尺寸将在此刷新显示。", true);

        TextNode hotbarTitle = document.text("快捷栏探针");
        this.hotbarMetricsText = appendCardWithHeader(document, root, "快捷栏探针",
                "快捷栏占用与网格尺寸将在此刷新显示。", false);
        this.hotbarGrid = buildGrid(document, 9, 9, 44, 24, 46, true);
        root.append(hotbarGrid.getElement());

        TextNode backpackTitle = document.text("主背包探针");
        this.backpackMetricsText = appendCardWithHeader(document, root, "主背包探针",
                "主背包占用与网格尺寸将在此刷新显示。", false);
        this.backpackGrid = buildGrid(document, 27, 9, 41, 22, 42, false);
        root.append(backpackGrid.getElement());

        appendFooter(document, root);

        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 720, 600, resolvedTextMeasure);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.px(600)));
    }

    @Override
    void configureDocumentPage() {
        documentPage.setContentWidthRange(720, 1040)
                .setMinContentHeight(620)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    void buildDocument() {
        documentPage.addBlock(documentUi.text(DocumentTextWidget.Role.TITLE, "背包诊断页", 2));
        documentPage.addBlock(documentUi.text(DocumentTextWidget.Role.BODY,
                "HTML-like 迁移版：背包格子、卡片与按钮均由 UiDocument 驱动渲染。", 8));
        documentPage.addBlock(htmlLikeDocumentWidget);
    }

    @Override
    void afterDocumentBuilt() {
        super.afterDocumentBuilt();
        refreshMetrics();
    }

    @Override
    void onDocumentResized() {
        super.onDocumentResized();
        refreshMetrics();
    }

    @Override
    void beforeDocumentFrame() {
        super.beforeDocumentFrame();
        refreshMetrics();
    }

    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    private TextNode appendCardWithHeader(UiDocument document, ElementNode parent, String title, String description,
            boolean isFirst) {
        ElementNode card = document.div();
        card.style()
                .setBackgroundColor(0xFF1C2333)
                .setBorderRadius(UiStyleLength.px(12))
                .setPadding(UiStyleLength.px(14))
                .setMargin(UiStyleLength.px(isFirst ? 0 : 16));
        parent.append(card);

        card.appendText(title).getOwnerDocument();
        card.appendText(description);
        TextNode metrics = card.appendText("加载中...");
        return metrics;
    }

    private DocumentInventorySlotGridControl buildGrid(UiDocument document, int slotCount, int columns,
            int preferredSize, int minSize, int maxSize, boolean isHotbar) {
        DocumentInventorySlotGridControl grid = new DocumentInventorySlotGridControl(document, slotCount, columns)
                .setSlotGap(8)
                .setPreferredSlotSize(preferredSize)
                .setSlotSizeRange(minSize, maxSize)
                .setContentProvider(new DocumentInventorySlotGridControl.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        return isHotbar ? model.getHotbarSlotProvider().getSlotSnapshot(localIndex)
                                : model.getBackpackSlotProvider().getSlotSnapshot(localIndex);
                    }
                });
        grid.getElement().style()
                .setMargin(UiStyleLength.px(8))
                .setBackgroundColor(0xFF242D40)
                .setBorderColor(0xFF3B4A66)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(10));
        return grid;
    }

    private void appendFooter(UiDocument document, ElementNode parent) {
        ElementNode footer = document.div();
        footer.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setJustifyContent(UiJustifyContent.END)
                .setPadding(UiStyleLength.px(10))
                .setMargin(UiStyleLength.px(16));
        parent.append(footer);

        DocumentButtonControl backButton = new DocumentButtonControl(document, "返回原版背包");
        backButton.setBackgroundColors(0xFF2B6CB0, 0xFF2C5282, 0xFF4A5568)
                .setFocusBorderColor(0xFFBEE3F8)
                .setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        model.returnToVanillaInventory();
                    }
                });
        footer.append(backButton.getElement());
    }

    private void refreshMetrics() {
        int hotbarUsed = model.getHotbarOccupiedCount();
        int backpackUsed = model.getBackpackOccupiedCount();

        if (overviewMetricsText != null) {
            overviewMetricsText.setText("窗口 " + runtimeView.getHostWidth() + "x" + runtimeView.getHostHeight()
                    + "。HTML-like 迁移版，格子控件通过 CUSTOM paint 命令渲染。");
        }
        if (hotbarMetricsText != null) {
            hotbarMetricsText.setText("快捷栏占用 " + hotbarUsed + " / 9。");
        }
        if (backpackMetricsText != null) {
            backpackMetricsText.setText("主背包占用 " + backpackUsed + " / 27。");
        }
    }
}
