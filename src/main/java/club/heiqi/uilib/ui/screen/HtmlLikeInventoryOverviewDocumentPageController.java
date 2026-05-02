package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.control.DocumentInventorySlotGridControl;
import club.heiqi.uilib.ui.dom.control.DocumentToggleChangeEvent;
import club.heiqi.uilib.ui.dom.control.DocumentToggleChangeHandler;
import club.heiqi.uilib.ui.dom.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleInsets;
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
    private final TextNode migrationStateText;

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
        this.migrationStateText = bundle.migrationStateText;
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
                .setBorderRadius(UiStyleLength.px(22))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(BODY_COLOR);

        TextNode overviewMetrics = appendHero(document, root);

        GridSectionBundle hotbarSection = appendGridSection(document, root, "快捷栏探针",
                "9 格热键栏，验证 HTML-like 内容盒中的 CUSTOM paint 与物品延迟回放。", true);
        TextNode hotbarMetrics = hotbarSection.metrics;
        DocumentInventorySlotGridControl hotbarGrid = buildGrid(document, 9, 9, 44, 24, 46, true);
        hotbarSection.card.append(hotbarGrid.getElement());

        GridSectionBundle backpackSection = appendGridSection(document, root, "主背包探针",
                "27 格主背包，验证多行槽位、占用态颜色和运行时内容刷新。", false);
        TextNode backpackMetrics = backpackSection.metrics;
        DocumentInventorySlotGridControl backpackGrid = buildGrid(document, 27, 9, 41, 22, 42, false);
        backpackSection.card.append(backpackGrid.getElement());

        TextNode migrationStateText = appendMigrationValidationCard(document, root);
        appendFooter(document, root);

        return new ContentBundle(overviewMetrics, hotbarMetrics, backpackMetrics, hotbarGrid, backpackGrid,
                migrationStateText);
    }

    private static final class ContentBundle {
        final TextNode overviewMetrics;
        final TextNode hotbarMetrics;
        final TextNode backpackMetrics;
        final DocumentInventorySlotGridControl hotbarGrid;
        final DocumentInventorySlotGridControl backpackGrid;
        final TextNode migrationStateText;

        ContentBundle(TextNode overviewMetrics, TextNode hotbarMetrics, TextNode backpackMetrics,
                DocumentInventorySlotGridControl hotbarGrid, DocumentInventorySlotGridControl backpackGrid,
                TextNode migrationStateText) {
            this.overviewMetrics = overviewMetrics;
            this.hotbarMetrics = hotbarMetrics;
            this.backpackMetrics = backpackMetrics;
            this.hotbarGrid = hotbarGrid;
            this.backpackGrid = backpackGrid;
            this.migrationStateText = migrationStateText;
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
    void configureDocumentPage() {
        documentPage.setContentWidthRange(720, 1040)
                .setMinContentHeight(620)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    void buildDocument() {
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

    private TextNode appendHero(UiDocument document, ElementNode parent) {
        ElementNode hero = document.div();
        hero.style()
                .setHeight(UiStyleLength.px(126))
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF0F2742)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextColor(TITLE_COLOR);
        parent.append(hero);

        hero.appendText("背包诊断中枢");
        hero.appendText("HTML-like 迁移版：卡片、指标、按钮和背包格子均由 UiDocument 驱动渲染。");
        TextNode metrics = hero.appendText("加载中...");
        return metrics;
    }

    private GridSectionBundle appendGridSection(UiDocument document, ElementNode parent, String title,
            String description, boolean isFirst) {
        ElementNode card = document.div();
        card.style()
                .setBackgroundColor(isFirst ? 0xFF18243A : 0xFF1F2937)
                .setBorderColor(isFirst ? 0xFF60A5FA : 0xFF818CF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setPadding(UiStyleLength.px(16))
                .setMargin(UiStyleLength.px(14))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        parent.append(card);

        card.appendText(title);
        card.appendText(description);
        TextNode metrics = card.appendText("加载中...");
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
        backButton.getElement().style().setWidth(UiStyleLength.px(180));
        footer.append(backButton.getElement());
    }

    /**
     * 追加非 Smoke 真实页面迁移验证卡，组合控件、动画、滚动、定位和 inline 文本。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @return 验证状态文本节点
     */
    private TextNode appendMigrationValidationCard(UiDocument document, ElementNode parent) {
        ElementNode card = document.div();
        card.style()
                .setHeight(UiStyleLength.px(170))
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF101827)
                .setBorderColor(0xFF22D3EE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setPadding(UiStyleLength.px(16))
                .setMargin(UiStyleLength.px(14))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextColor(0xFFE0F2FE);
        parent.append(card);

        ElementNode badge = document.div();
        badge.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(12))
                .setRight(UiStyleLength.px(14))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(4), UiStyleLength.px(8), UiStyleLength.px(4),
                        UiStyleLength.px(8)))
                .setBackgroundColor(0xFF0E7490)
                .setBorderColor(0xFF67E8F9)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFE0F2FE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        badge.appendText("REAL PAGE");
        card.append(badge);

        card.appendText("真实迁移验证卡");
        TextNode stateText = card.appendText("状态：紧凑模式，点击左侧卡片或切换开关。 ");
        ElementNode inlinePill = document.span();
        inlinePill.style()
                .setPadding(UiStyleInsets.of(UiStyleLength.px(1), UiStyleLength.px(6), UiStyleLength.px(1),
                        UiStyleLength.px(6)))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(4), UiStyleLength.px(0),
                        UiStyleLength.px(4)))
                .setBackgroundColor(0xFF164E63)
                .setBorderColor(0xFF67E8F9)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFECFEFF);
        inlinePill.appendText("inline pill");
        card.append(inlinePill);
        card.appendText(" 与普通文本混排，用于迁移页内联组合验收。");

        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        card.append(row);

        ElementNode animatedCard = document.div();
        animatedCard.style()
                .setWidth(UiStyleLength.px(150))
                .setHeight(UiStyleLength.px(42))
                .setFlexShrink(0.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF2563EB)
                .setBorderColor(0xFFBFDBFE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFEFF6FF)
                .setTransitionProperties(DocumentAnimationProperty.WIDTH, DocumentAnimationProperty.BACKGROUND_COLOR,
                        DocumentAnimationProperty.BORDER_RADIUS)
                .setTransitionDurationMillis(650L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        TextNode animatedLabel = animatedCard.appendText("迁移卡片：紧凑");
        row.append(animatedCard);

        ElementNode detailPane = document.div();
        detailPane.style()
                .setFlexGrow(1.0F)
                .setHeight(UiStyleLength.px(64))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF1E293B)
                .setBorderColor(0xFF475569)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFCBD5E1)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        detailPane.appendText("滚动说明：真实页面迁移验证同时包含动画卡片、toggle、absolute 角标、fixed 提示、inline pill 和 overflow auto 文本。"
                + "内容略长以便在较小视口下验证滚动裁剪和命中稳定性。");
        row.append(detailPane);

        DocumentToggleSwitchControl migrationToggle = new DocumentToggleSwitchControl(document)
                .setTrackColors(0xFF334155, 0xFF0EA5E9, 0xFF1E293B)
                .setFocusBorderColor(0xFFBAE6FD)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        applyMigrationExpandedState(event.isToggled(), animatedCard, animatedLabel, stateText);
                    }
                });
        migrationToggle.getElement().style().setMargin(UiStyleInsets.of(UiStyleLength.px(16), UiStyleLength.px(0),
                UiStyleLength.px(0), UiStyleLength.px(0)));
        card.append(migrationToggle.getElement());

        animatedCard.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                migrationToggle.setToggled(!migrationToggle.isToggled());
                applyMigrationExpandedState(migrationToggle.isToggled(), animatedCard, animatedLabel, stateText);
                return true;
            }
        });

        ElementNode fixedHint = document.div();
        fixedHint.style()
                .setPosition(UiPosition.FIXED)
                .setRight(UiStyleLength.px(14))
                .setBottom(UiStyleLength.px(52))
                .setZIndex(25)
                .setPadding(UiStyleInsets.of(UiStyleLength.px(5), UiStyleLength.px(8), UiStyleLength.px(5),
                        UiStyleLength.px(8)))
                .setBackgroundColor(0xCC0F766E)
                .setBorderColor(0xFF99F6E4)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFE6FFFA)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        fixedHint.appendText("Inventory fixed hint");
        parent.append(fixedHint);
        return stateText;
    }

    private static void applyMigrationExpandedState(boolean expanded, ElementNode animatedCard, TextNode animatedLabel,
            TextNode stateText) {
        animatedCard.style()
                .setWidth(UiStyleLength.px(expanded ? 220 : 150))
                .setBackgroundColor(expanded ? 0xFF7C3AED : 0xFF2563EB)
                .setBorderRadius(UiStyleLength.px(expanded ? 18 : 14));
        animatedLabel.setText(expanded ? "迁移卡片：展开" : "迁移卡片：紧凑");
        stateText.setText(expanded ? "状态：展开模式，动画和 toggle 已联动。 " : "状态：紧凑模式，点击左侧卡片或切换开关。 ");
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
