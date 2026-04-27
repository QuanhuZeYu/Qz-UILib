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

    private int lastHotbarUsed = -1;
    private int lastBackpackUsed = -1;
    private int lastHostWidth = -1;
    private int lastHostHeight = -1;

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
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 720, 600, resolvedTextMeasure);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F)));

        ContentBundle bundle = createDocumentContent(document, document.getRootElement());
        this.overviewMetricsText = bundle.overviewMetrics;
        this.hotbarMetricsText = bundle.hotbarMetrics;
        this.backpackMetricsText = bundle.backpackMetrics;
        this.hotbarGrid = bundle.hotbarGrid;
        this.backpackGrid = bundle.backpackGrid;
    }

    /**
     * 构建页面内容的 DOM 子树，返回各关键节点的引用集合。
     */
    private ContentBundle createDocumentContent(UiDocument document, ElementNode root) {
        root.style()
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xEE121726)
                .setBorderColor(0xFF4A78D8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setTextColor(BODY_COLOR);

        TextNode overviewMetrics = appendCardWithHeader(document, root, "当前状态",
                "窗口信息与布局尺寸将在此刷新显示。", true);

        TextNode hotbarMetrics = appendCardWithHeader(document, root, "快捷栏探针",
                "快捷栏占用与网格尺寸将在此刷新显示。", false);
        DocumentInventorySlotGridControl hotbarGrid = buildGrid(document, 9, 9, 44, 24, 46, true);
        root.append(hotbarGrid.getElement());

        TextNode backpackMetrics = appendCardWithHeader(document, root, "主背包探针",
                "主背包占用与网格尺寸将在此刷新显示。", false);
        DocumentInventorySlotGridControl backpackGrid = buildGrid(document, 27, 9, 41, 22, 42, false);
        root.append(backpackGrid.getElement());

        appendFooter(document, root);

        return new ContentBundle(overviewMetrics, hotbarMetrics, backpackMetrics, hotbarGrid, backpackGrid);
    }

    private static final class ContentBundle {
        final TextNode overviewMetrics;
        final TextNode hotbarMetrics;
        final TextNode backpackMetrics;
        final DocumentInventorySlotGridControl hotbarGrid;
        final DocumentInventorySlotGridControl backpackGrid;

        ContentBundle(TextNode overviewMetrics, TextNode hotbarMetrics, TextNode backpackMetrics,
                DocumentInventorySlotGridControl hotbarGrid, DocumentInventorySlotGridControl backpackGrid) {
            this.overviewMetrics = overviewMetrics;
            this.hotbarMetrics = hotbarMetrics;
            this.backpackMetrics = backpackMetrics;
            this.hotbarGrid = hotbarGrid;
            this.backpackGrid = backpackGrid;
        }
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

        card.appendText(title);
        card.appendText(description);
        TextNode metrics = card.appendText("加载中...");
        return metrics;
    }

    /**
     * 创建背包网格控件。
     *
     * @param document 所属文档
     * @param slotCount 槽位数量
     * @param columns 期望列数
     * @param preferredSize 槽位期望尺寸
     * @param minSize 最小槽位尺寸
     * @param maxSize 最大槽位尺寸
     * @param isHotbar 是否快捷栏
     * @return 网格控件
     */
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
        if (documentUi.getRuntimeAdapters().getInventorySlotGridItemRenderer() != null) {
            grid.setItemRenderer(documentUi.getRuntimeAdapters().getInventorySlotGridItemRenderer());
        }
        grid.commitLayout();
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
        int hostWidth = runtimeView.getHostWidth();
        int hostHeight = runtimeView.getHostHeight();

        if (overviewMetricsText != null
                && (lastHostWidth != hostWidth || lastHostHeight != hostHeight)) {
            overviewMetricsText.setText("窗口 " + hostWidth + "x" + hostHeight
                    + "。HTML-like 迁移版，格子控件通过 CUSTOM paint 命令渲染。");
            lastHostWidth = hostWidth;
            lastHostHeight = hostHeight;
        }
        if (hotbarMetricsText != null && lastHotbarUsed != hotbarUsed) {
            hotbarMetricsText.setText("快捷栏占用 " + hotbarUsed + " / 9。");
            lastHotbarUsed = hotbarUsed;
        }
        if (backpackMetricsText != null && lastBackpackUsed != backpackUsed) {
            backpackMetricsText.setText("主背包占用 " + backpackUsed + " / 27。");
            lastBackpackUsed = backpackUsed;
        }
    }
}
