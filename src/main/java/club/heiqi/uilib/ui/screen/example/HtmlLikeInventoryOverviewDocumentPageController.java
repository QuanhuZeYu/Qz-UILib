package club.heiqi.uilib.ui.screen.example;

import club.heiqi.uilib.ui.screen.DocumentPageController;
import club.heiqi.uilib.ui.screen.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.DocumentUiScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentCursorOverlayControl;
import club.heiqi.uilib.ui.control.DocumentOverlayHostControl;
import club.heiqi.uilib.ui.control.DocumentTooltipOverlayControl;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.control.DocumentInventorySlotGridControl;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 背包概览页控制器。
 */
public final class HtmlLikeInventoryOverviewDocumentPageController extends DocumentPageController {

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
    private final DocumentOverlayHostControl overlayHostControl;
    private final DocumentTooltipOverlayControl tooltipOverlayControl;
    private final DocumentCursorOverlayControl cursorOverlayControl;
    private final InventorySlotGridItemRenderer inventoryItemRenderer;

    private int lastHotbarUsed = -1;
    private int lastBackpackUsed = -1;
    private int lastSelectedHotbarSlot = -2;
    private boolean lastCarriedSlotOccupied = true;
    private int lastHostWidth = -1;
    private int lastHostHeight = -1;
    private InventorySlotSnapshot currentCarriedSlotSnapshot = InventorySlotSnapshot.empty();

    private static final int TITLE_COLOR = 0xFFF0F4FF;
    private static final int BODY_COLOR = 0xFFC0CAE8;
    private static final int TOOLTIP_MAX_WIDTH = 360;
    private static final float TOOLTIP_MAX_WIDTH_RATIO = 0.4F;
    private static final int TOOLTIP_MIN_WIDTH = 120;
    private static final int TOOLTIP_TITLE_COLOR = 0xFFFDFEFF;
    private static final int TOOLTIP_BODY_COLOR = 0xFFD8E4FF;
    private static final int TOOLTIP_BACKGROUND_COLOR = 0xB8182033;
    private static final int TOOLTIP_BORDER_COLOR = 0xCC8B5CF6;
    private static final int TOOLTIP_CORNER_RADIUS = 16;
    private static final int TOOLTIP_LINE_SPACING = 4;
    private static final int TOOLTIP_VERTICAL_PADDING = 12;
    private static final int TOOLTIP_HORIZONTAL_PADDING = 14;

    public HtmlLikeInventoryOverviewDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage, DocumentPageRuntimeView runtimeView,
            InventoryOverviewModel model) {
        this.documentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        this.model = Objects.requireNonNull(model, "model");
        this.inventoryItemRenderer = documentUi.getRuntimeAdapters().getInventorySlotGridItemRenderer();
        TextMeasureService resolvedTextMeasure = documentUi.getTextMeasureService();

        UiDocument document = UiDocument.create();
        document.setDefaultTextContentMode(documentUi.getDefaultTextContentMode());
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 720, 600, resolvedTextMeasure);
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));

        ContentBundle bundle = createDocumentContent(document, document.getRootElement());
        this.overviewMetricsText = bundle.overviewMetrics;
        this.hotbarMetricsText = bundle.hotbarMetrics;
        this.backpackMetricsText = bundle.backpackMetrics;
        this.hotbarGrid = bundle.hotbarGrid;
        this.backpackGrid = bundle.backpackGrid;
        this.overlayHostControl = bundle.overlayHostControl;
        this.tooltipOverlayControl = bundle.tooltipOverlayControl;
        this.cursorOverlayControl = bundle.cursorOverlayControl;
    }

    /**
     * 构建页面内容的 DOM 子树，返回各关键节点的引用集合。
     */
    private ContentBundle createDocumentContent(UiDocument document, ElementNode root) {
        root.style()
                .setPadding(UiStyleLength.px(20))
                .setBackgroundColor(0xF0101628)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(22))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(BODY_COLOR);
        ElementNode main = document.element("main");
        main.setAttribute("role", "main")
                .setAttribute("data-inventory-drop-zone", "true");
        main.style()
                .setWidth(UiStyleLength.auto());
        main.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (event.getButton() != 0 && event.getButton() != 1) {
                    return false;
                }
                return model.handleSlotClick(false, -1, event.getButton());
            }
        });
        root.append(main);

        TextNode overviewMetrics = appendHero(document, main);

        GridSectionBundle hotbarSection = appendGridSection(document, main, "快捷栏",
                "9 格热键栏，展示当前可快速使用的物品栏内容。", true);
        TextNode hotbarMetrics = hotbarSection.metrics;
        DocumentInventorySlotGridControl hotbarGrid = buildGrid(document, 9, 9, 44, 24, 46, true);
        hotbarSection.card.append(hotbarGrid.getElement());

        GridSectionBundle backpackSection = appendGridSection(document, main, "主背包",
                "27 格主背包，展示当前背包内容与占用状态。", false);
        TextNode backpackMetrics = backpackSection.metrics;
        DocumentInventorySlotGridControl backpackGrid = buildGrid(document, 27, 9, 41, 22, 42, false);
        backpackSection.card.append(backpackGrid.getElement());

        appendFooter(document, main);
        DocumentOverlayBundle overlayBundle = appendOverlayLayers(document, root);

        return new ContentBundle(overviewMetrics, hotbarMetrics, backpackMetrics, hotbarGrid, backpackGrid,
                overlayBundle.overlayHostControl, overlayBundle.tooltipOverlayControl,
                overlayBundle.cursorOverlayControl);
    }

    private static final class ContentBundle {
        final TextNode overviewMetrics;
        final TextNode hotbarMetrics;
        final TextNode backpackMetrics;
        final DocumentInventorySlotGridControl hotbarGrid;
        final DocumentInventorySlotGridControl backpackGrid;
        final DocumentOverlayHostControl overlayHostControl;
        final DocumentTooltipOverlayControl tooltipOverlayControl;
        final DocumentCursorOverlayControl cursorOverlayControl;

        ContentBundle(TextNode overviewMetrics, TextNode hotbarMetrics, TextNode backpackMetrics,
                DocumentInventorySlotGridControl hotbarGrid, DocumentInventorySlotGridControl backpackGrid,
                DocumentOverlayHostControl overlayHostControl, DocumentTooltipOverlayControl tooltipOverlayControl,
                DocumentCursorOverlayControl cursorOverlayControl) {
            this.overviewMetrics = overviewMetrics;
            this.hotbarMetrics = hotbarMetrics;
            this.backpackMetrics = backpackMetrics;
            this.hotbarGrid = hotbarGrid;
            this.backpackGrid = backpackGrid;
            this.overlayHostControl = overlayHostControl;
            this.tooltipOverlayControl = tooltipOverlayControl;
            this.cursorOverlayControl = cursorOverlayControl;
        }
    }

    private static final class DocumentOverlayBundle {
        final DocumentOverlayHostControl overlayHostControl;
        final DocumentTooltipOverlayControl tooltipOverlayControl;
        final DocumentCursorOverlayControl cursorOverlayControl;

        DocumentOverlayBundle(DocumentOverlayHostControl overlayHostControl,
                DocumentTooltipOverlayControl tooltipOverlayControl, DocumentCursorOverlayControl cursorOverlayControl) {
            this.overlayHostControl = overlayHostControl;
            this.tooltipOverlayControl = tooltipOverlayControl;
            this.cursorOverlayControl = cursorOverlayControl;
        }
    }

    private static final class GridSectionBundle {
        final ElementNode card;
        final TextNode metrics;

        GridSectionBundle(ElementNode card, TextNode metrics) {
            this.card = card;
            this.metrics = metrics;
        }
    }

    @Override
    protected void configureDocumentPage() {
        documentPage.setContentWidthRange(720, 1040)
                .setMinContentHeight(620)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    protected void buildDocument() {
        documentPage.addBlock(htmlLikeDocumentWidget);
    }

    @Override
    protected void afterDocumentBuilt() {
        super.afterDocumentBuilt();
        refreshMetrics();
    }

    @Override
    protected void onDocumentResized() {
        super.onDocumentResized();
        refreshMetrics();
    }

    @Override
    protected void beforeDocumentFrame() {
        super.beforeDocumentFrame();
        refreshMetrics();
    }

    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    private TextNode appendHero(UiDocument document, ElementNode parent) {
        ElementNode hero = document.element("header");
        hero.setAttribute("role", "banner");
        hero.style()
                .setHeight(UiStyleLength.px(126))
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF0F2742)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextColor(TITLE_COLOR);
        parent.append(hero);

        ElementNode title = document.element("h1");
        title.appendText("背包概览");
        hero.append(title);
        ElementNode description = document.element("p");
        description.appendText("查看快捷栏与主背包占用情况，并保持与原版背包数据同步。");
        hero.append(description);
        ElementNode metricsParagraph = document.element("p");
        metricsParagraph.setAttribute("role", "status");
        TextNode metrics = metricsParagraph.appendText("加载中...");
        hero.append(metricsParagraph);
        return metrics;
    }

    private GridSectionBundle appendGridSection(UiDocument document, ElementNode parent, String title,
            String description, boolean isFirst) {
        ElementNode card = document.element("section");
        card.setAttribute("aria-label", title);
        card.style()
                .setBackgroundColor(isFirst ? 0xFF18243A : 0xFF1F2937)
                .setBorderColor(isFirst ? 0xFF60A5FA : 0xFF818CF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16))
                .setPadding(UiStyleLength.px(16))
                .setMargin(UiStyleLength.px(14))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        parent.append(card);

        ElementNode heading = document.element("h2");
        heading.appendText(title);
        card.append(heading);
        ElementNode descriptionParagraph = document.element("p");
        descriptionParagraph.appendText(description);
        card.append(descriptionParagraph);
        ElementNode metricsParagraph = document.element("p");
        metricsParagraph.setAttribute("role", "status");
        TextNode metrics = metricsParagraph.appendText("加载中...");
        card.append(metricsParagraph);
        return new GridSectionBundle(card, metrics);
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
                .setCarriedItemOverlayEnabled(false)
                .setContentProvider(new DocumentInventorySlotGridControl.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        return isHotbar ? model.getHotbarSlotProvider().getSlotSnapshot(localIndex)
                                : model.getBackpackSlotProvider().getSlotSnapshot(localIndex);
                    }
                })
                .setSlotClickHandler(new DocumentInventorySlotGridControl.SlotClickHandler() {
                    @Override
                    public boolean onSlotClick(int localIndex, int button, long timeNanos) {
                        return model.handleSlotClick(isHotbar, localIndex, button);
                    }
                })
                .setSlotTooltipProvider(new DocumentInventorySlotGridControl.SlotTooltipProvider() {
                    @Override
                    public List<String> getSlotTooltip(int localIndex) {
                        return model.getSlotTooltip(isHotbar, localIndex);
                    }
                })
                .setSlotHoverHandler(new DocumentInventorySlotGridControl.SlotHoverHandler() {
                    @Override
                    public void onSlotHoverChanged(int localIndex, boolean hovered, List<String> tooltipLines,
                            int documentX, int documentY, long timeNanos) {
                        updateTooltipLayer(hovered, tooltipLines, documentX, documentY);
                    }
                });
        if (inventoryItemRenderer != null) {
            grid.setItemRenderer(inventoryItemRenderer);
        }
        grid.commitLayout();
        grid.getElement().style()
                .setMargin(UiStyleLength.px(8))
                .setBackgroundColor(0xFF242D40)
                .setBorderColor(0xFF3B4A66)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(10));
        return grid;
    }

    private void appendFooter(UiDocument document, ElementNode parent) {
        ElementNode footer = document.element("footer");
        footer.setAttribute("role", "contentinfo");
        footer.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
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
        backButton.getElement().setAttribute("data-inventory-action", "back");
        backButton.getElement().style().setWidth(UiStyleLength.px(180));
        footer.append(backButton.getElement());
    }

    private DocumentOverlayBundle appendOverlayLayers(UiDocument document, ElementNode parent) {
        DocumentOverlayHostControl overlayHostControl = new DocumentOverlayHostControl(document);
        parent.append(overlayHostControl.getElement());

        DocumentTooltipOverlayControl tooltipOverlay = new DocumentTooltipOverlayControl(document,
                documentUi.getTextMeasureService(), new DocumentTooltipOverlayControl.ViewportPointerProvider() {
                    @Override
                    public int getViewportWidth() {
                        return htmlLikeDocumentWidget.getWidth() > 0 ? htmlLikeDocumentWidget.getWidth()
                                : runtimeView.getHostWidth();
                    }

                    @Override
                    public int getViewportHeight() {
                        return htmlLikeDocumentWidget.getHeight() > 0 ? htmlLikeDocumentWidget.getHeight()
                                : runtimeView.getHostHeight();
                    }

                    @Override
                    public int getPointerX() {
                        return runtimeView.getMouseX() - htmlLikeDocumentWidget.getAbsoluteX();
                    }

                    @Override
                    public int getPointerY() {
                        return runtimeView.getMouseY() - htmlLikeDocumentWidget.getAbsoluteY();
                    }
                })
                .setWidthPolicy(TOOLTIP_MIN_WIDTH, TOOLTIP_MAX_WIDTH, TOOLTIP_MAX_WIDTH_RATIO)
                .setTextColors(TOOLTIP_TITLE_COLOR, TOOLTIP_BODY_COLOR)
                .setSurfaceStyle(TOOLTIP_BACKGROUND_COLOR, TOOLTIP_BORDER_COLOR, TOOLTIP_CORNER_RADIUS,
                        TOOLTIP_LINE_SPACING, TOOLTIP_VERTICAL_PADDING, TOOLTIP_HORIZONTAL_PADDING)
                .setBackdropStyle(12, 1.2F);
        ElementNode tooltipElement = tooltipOverlay.getElement();
        tooltipElement.setAttribute("data-inventory-tooltip", "true");
        overlayHostControl.appendOverlay(tooltipElement);

        DocumentCursorOverlayControl cursorOverlayControl = new DocumentCursorOverlayControl(document,
                new DocumentCursorOverlayControl.PointerProvider() {
                    @Override
                    public int getPointerX() {
                        return runtimeView.getMouseX() - htmlLikeDocumentWidget.getAbsoluteX();
                    }

                    @Override
                    public int getPointerY() {
                        return runtimeView.getMouseY() - htmlLikeDocumentWidget.getAbsoluteY();
                    }
                }, CURSOR_PLACEHOLDER_IMAGE_SOURCE)
                .setSize(24)
                .setAnchorOffset(12)
                .setZIndex(1001);
        ElementNode cursorElement = cursorOverlayControl.getElement();
        cursorElement.setAttribute("data-cursor-item-layer", "true");
        overlayHostControl.appendOverlay(cursorElement);
        return new DocumentOverlayBundle(overlayHostControl, tooltipOverlay, cursorOverlayControl);
    }

    private void updateTooltipLayer(boolean hovered, List<String> lines, int documentX, int documentY) {
        if (tooltipOverlayControl == null) {
            return;
        }
        tooltipOverlayControl.setRequestedTooltip(hovered, lines)
                .setSuppressed(currentCarriedSlotSnapshot.isOccupied())
                .refresh();
    }

    private void refreshVisibleTooltipLayer() {
        if (tooltipOverlayControl != null) {
            tooltipOverlayControl.setSuppressed(currentCarriedSlotSnapshot.isOccupied())
                    .refresh();
        }
    }

    private void refreshMetrics() {
        int hotbarUsed = model.getHotbarOccupiedCount();
        int backpackUsed = model.getBackpackOccupiedCount();
        int selectedHotbarSlot = model.getSelectedHotbarSlotIndex();
        InventorySlotSnapshot carriedSlotSnapshot = model.getCarriedSlotSnapshot();
        boolean carriedSlotOccupied = carriedSlotSnapshot != null && carriedSlotSnapshot.isOccupied();
        currentCarriedSlotSnapshot = carriedSlotSnapshot != null ? carriedSlotSnapshot : InventorySlotSnapshot.empty();
        int hostWidth = runtimeView.getHostWidth();
        int hostHeight = runtimeView.getHostHeight();
        int previousHotbarUsed = lastHotbarUsed;
        int previousBackpackUsed = lastBackpackUsed;
        int previousSelectedHotbarSlot = lastSelectedHotbarSlot;
        boolean previousCarriedSlotOccupied = lastCarriedSlotOccupied;

        hotbarGrid.setSelectedSlotIndex(selectedHotbarSlot)
                .setCarriedSnapshot(carriedSlotSnapshot)
                .setCarriedItemOverlayEnabled(false);
        backpackGrid.setSelectedSlotIndex(-1)
                .setCarriedSnapshot(carriedSlotSnapshot)
                .setCarriedItemOverlayEnabled(false);
        hotbarGrid.refreshSlotStates();
        backpackGrid.refreshSlotStates();
        refreshVisibleTooltipLayer();
        refreshCursorItemLayer(carriedSlotSnapshot);

        if (overviewMetricsText != null
                && (lastHostWidth != hostWidth || lastHostHeight != hostHeight)) {
            overviewMetricsText.setText("窗口 " + hostWidth + "x" + hostHeight
                    + "。背包界面已适配当前视口。");
            lastHostWidth = hostWidth;
            lastHostHeight = hostHeight;
        }
        if (hotbarMetricsText != null
                && (previousHotbarUsed != hotbarUsed || previousSelectedHotbarSlot != selectedHotbarSlot)) {
            hotbarMetricsText.setText("快捷栏占用 " + hotbarUsed + " / 9。当前持有槽 "
                    + formatSelectedHotbarSlot(selectedHotbarSlot) + "。");
            lastHotbarUsed = hotbarUsed;
            lastSelectedHotbarSlot = selectedHotbarSlot;
        }
        if (backpackMetricsText != null
                && (previousBackpackUsed != backpackUsed || previousCarriedSlotOccupied != carriedSlotOccupied)) {
            backpackMetricsText.setText("主背包占用 " + backpackUsed + " / 27。鼠标携带 "
                    + formatCarriedSlotState(carriedSlotOccupied) + "。");
            lastBackpackUsed = backpackUsed;
            lastCarriedSlotOccupied = carriedSlotOccupied;
        }
    }

    private static String formatSelectedHotbarSlot(int selectedHotbarSlot) {
        return selectedHotbarSlot >= 0 ? String.valueOf(selectedHotbarSlot + 1) : "无";
    }

    private static String formatCarriedSlotState(boolean carriedSlotOccupied) {
        return carriedSlotOccupied ? "物品" : "空";
    }

    private void refreshCursorItemLayer(InventorySlotSnapshot carriedSlotSnapshot) {
        if (cursorOverlayControl == null) {
            return;
        }
        HostImageSource hostImageSource = carriedSlotSnapshot == null ? null : carriedSlotSnapshot.toHostImageSource();
        cursorOverlayControl.setSource(hostImageSource).refresh();
    }

    private static final HostImageSource CURSOR_PLACEHOLDER_IMAGE_SOURCE = HostImageSource.textureRegion(
            new net.minecraft.util.ResourceLocation("minecraft", "textures/gui/widgets.png"), 256, 256, 0, 0, 1,
            1);
}
