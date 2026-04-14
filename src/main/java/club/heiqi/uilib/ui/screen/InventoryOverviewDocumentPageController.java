package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.InventorySlotSnapshot;
import club.heiqi.uilib.ui.control.InventorySlotGridWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.document.DocumentCardWidget;
import club.heiqi.uilib.ui.document.DocumentSectionWidget;
import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.document.DocumentToolbarWidget;

/**
 * 背包诊断页的页面控制器。
 *
 * <p>该控制器承接页面私有 widget 状态与生命周期刷新逻辑，
 * 让 `InventoryOverviewScreen` 退化为只负责宿主桥接的薄包装器。</p>
 */
final class InventoryOverviewDocumentPageController extends DocumentPageController {

    private final DocumentUiScope documentUi;
    private final DocumentPageAuthoringSurface pagePanel;
    private final DocumentPageRuntimeView runtimeView;
    private final InventoryOverviewModel model;

    private final DocumentCardWidget overviewCard;
    private final DocumentCardWidget hotbarCard;
    private final DocumentCardWidget backpackCard;

    private final LabelWidget overviewMetricsLabel;
    private final LabelWidget hotbarMetricsLabel;
    private final LabelWidget backpackMetricsLabel;

    private final InventorySlotGridWidget hotbarGrid;
    private final InventorySlotGridWidget backpackGrid;
    private final ButtonWidget backButton;

    /**
     * 创建背包诊断页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param pagePanel 文档页面壳
     * @param runtimeView 宿主运行时视图
     * @param model 页面模型
     */
    InventoryOverviewDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface pagePanel,
            DocumentPageRuntimeView runtimeView, InventoryOverviewModel model) {
        this.documentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.pagePanel = Objects.requireNonNull(pagePanel, "pagePanel");
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        this.model = Objects.requireNonNull(model, "model");

        this.overviewCard = this.documentUi.card();
        this.hotbarCard = this.documentUi.card();
        this.backpackCard = this.documentUi.card();

        this.overviewMetricsLabel = this.documentUi.text(DocumentTextWidget.Role.EMPHASIS, "", 5);
        this.hotbarMetricsLabel = this.documentUi.text(DocumentTextWidget.Role.BODY, "", 4);
        this.backpackMetricsLabel = this.documentUi.text(DocumentTextWidget.Role.BODY, "", 4);

        this.hotbarGrid = this.documentUi.inventoryGrid(9, 9, adaptSlotContentProvider(this.model.getHotbarSlotProvider()));
        this.backpackGrid = this.documentUi.inventoryGrid(27, 9,
                adaptSlotContentProvider(this.model.getBackpackSlotProvider()));
        this.backButton = this.documentUi.button("返回原版背包");
    }

    /**
     * 在页面层 contract 与控件层 contract 之间做薄适配。
     *
     * @param slotContentProvider 页面层槽位内容提供器
     * @return 控件层槽位内容提供器
     */
    private static InventorySlotGridWidget.SlotContentProvider adaptSlotContentProvider(
            final InventoryOverviewSlotContentProvider slotContentProvider) {
        Objects.requireNonNull(slotContentProvider, "slotContentProvider");
        return new InventorySlotGridWidget.SlotContentProvider() {
            @Override
            public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                return slotContentProvider.getSlotSnapshot(localIndex);
            }
        };
    }

    @Override
    void configureDocumentPage() {
        super.configureDocumentPage();
        pagePanel.setContentWidthRange(720, 1040)
                .setMinContentHeight(620)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    void buildDocument() {
        configureControls();
        assembleDocument();
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

    /**
     * 配置背包诊断页控件。
     */
    private void configureControls() {
        hotbarGrid.setSlotGap(8).setPreferredSlotSize(34).setSlotSizeRange(18, 50);
        backpackGrid.setSlotGap(8).setPreferredSlotSize(32).setSlotSizeRange(18, 46);

        backButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                model.returnToVanillaInventory();
            }
        });
    }

    /**
     * 组装背包诊断页文档树。
     */
    private void assembleDocument() {
        DocumentToolbarWidget footer = documentUi.toolbar();

        DocumentSectionWidget overviewDiv = documentUi.section();
        overviewDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "当前状态", 2));
        overviewDiv.addChild(documentUi.text(DocumentTextWidget.Role.BODY,
                "旧背包测试页已经完全清空。当前只保留单列背包诊断页，优先确认页面壳、格子网格和纵向滚动不会互相干扰，再重建复杂业务页。", 8));
        overviewDiv.addChild(overviewMetricsLabel);
        overviewCard.addChild(overviewDiv);

        DocumentSectionWidget hotbarDiv = documentUi.section();
        hotbarDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "快捷栏探针", 2));
        hotbarDiv.addChild(hotbarMetricsLabel);
        hotbarDiv.addChild(hotbarGrid);
        hotbarCard.addChild(hotbarDiv);

        DocumentSectionWidget backpackDiv = documentUi.section();
        backpackDiv.addChild(documentUi.text(DocumentTextWidget.Role.TITLE, "主背包探针", 2));
        backpackDiv.addChild(backpackMetricsLabel);
        backpackDiv.addChild(backpackGrid);
        backpackCard.addChild(backpackDiv);

        footer.addChild(backButton);

        pagePanel.addBlock(documentUi.text(DocumentTextWidget.Role.TITLE, "背包诊断页", 2));
        pagePanel.addBlock(documentUi.text(DocumentTextWidget.Role.BODY,
                "这里不再做左右两栏或摘要联排，只验证网格控件在可靠父宽度下是否能稳定缩放、换列和滚动。", 8));
        pagePanel.addBlock(overviewCard);
        pagePanel.addBlock(hotbarCard);
        pagePanel.addBlock(backpackCard);
        pagePanel.addBlock(footer);
    }

    /**
     * 刷新背包诊断指标文本。
     */
    private void refreshMetrics() {
        int hotbarUsed = model.getHotbarOccupiedCount();
        int backpackUsed = model.getBackpackOccupiedCount();

        overviewMetricsLabel.setText("窗口 " + runtimeView.getHostWidth() + "x" + runtimeView.getHostHeight() + "；快捷栏卡片 "
                + hotbarCard.getWidth() + "x" + hotbarCard.getHeight() + "；主背包卡片 " + backpackCard.getWidth() + "x"
                + backpackCard.getHeight() + "。\n如果单列结构下背包格子仍然异常，优先检查 `InventorySlotGridWidget` 的列数和尺寸测量，而不是继续叠加页面复杂度。 ");

        hotbarMetricsLabel.setText("快捷栏占用 " + hotbarUsed + " / 9；网格尺寸 " + hotbarGrid.getWidth() + "x"
                + hotbarGrid.getHeight() + "。当前结构只验证单行网格能否在父宽度变化时稳定缩放。 ");
        backpackMetricsLabel.setText("主背包占用 " + backpackUsed + " / 27；网格尺寸 " + backpackGrid.getWidth() + "x"
                + backpackGrid.getHeight() + "。如果这里出现裁切或列数异常，再回头修 `InventorySlotGridWidget`。 ");
    }
}
