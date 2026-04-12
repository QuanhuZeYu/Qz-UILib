package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.InventorySlotGridWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.document.DocumentCardWidget;
import club.heiqi.uilib.ui.document.DocumentPageWidget;
import club.heiqi.uilib.ui.document.DocumentSectionWidget;
import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.document.DocumentToolbarWidget;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * 当前阶段的最小背包诊断页。
 */
public class InventoryOverviewScreen extends BaseScreen {

    private static final UiDocumentTheme DOCUMENT_THEME = UiDocumentTheme.defaultTheme();

    private final DocumentPageWidget pagePanel = new DocumentPageWidget(DOCUMENT_THEME);

    private final DocumentCardWidget overviewCard = new DocumentCardWidget(DOCUMENT_THEME);
    private final DocumentCardWidget hotbarCard = new DocumentCardWidget(DOCUMENT_THEME);
    private final DocumentCardWidget backpackCard = new DocumentCardWidget(DOCUMENT_THEME);

    private final LabelWidget overviewMetricsLabel = new DocumentTextWidget(DOCUMENT_THEME, DocumentTextWidget.Role.EMPHASIS, "", 5);
    private final LabelWidget hotbarMetricsLabel = new DocumentTextWidget(DOCUMENT_THEME, DocumentTextWidget.Role.BODY, "", 4);
    private final LabelWidget backpackMetricsLabel = new DocumentTextWidget(DOCUMENT_THEME, DocumentTextWidget.Role.BODY, "", 4);

    private final InventorySlotGridWidget hotbarGrid = new InventorySlotGridWidget(0, 9, 9, DOCUMENT_THEME.getInventorySlotGridStyle());
    private final InventorySlotGridWidget backpackGrid = new InventorySlotGridWidget(9, 27, 9, DOCUMENT_THEME.getInventorySlotGridStyle());
    private final ButtonWidget backButton = new ButtonWidget("返回原版背包", DOCUMENT_THEME.getButtonStyle());

    @Override
    protected void buildUi(Widget root) {
        configurePage();
        configureControls();
        assembleUi(root);
        refreshMetrics();
    }

    @Override
    protected void onResize(int width, int height) {
        super.onResize(width, height);

        int pageMargin = Math.max(24, width / 34);
        int topMargin = Math.max(28, height / 28);
        setRootPadding(pageMargin, topMargin, pageMargin, pageMargin);

        int pagePaddingX = clampValue(width / 48, 16, 28);
        int pagePaddingY = clampValue(height / 36, 14, 24);

        pagePanel.setShellPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        refreshMetrics();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refreshMetrics();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * 配置页面壳。
     */
    private void configurePage() {
        pagePanel.setShellPadding(24, 22, 24, 22)
                .setContentWidthRange(720, 1040)
                .setMinContentHeight(620)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    /**
     * 配置背包诊断控件。
     */
    private void configureControls() {
        hotbarGrid.setSlotGap(8).setPreferredSlotSize(34).setSlotSizeRange(18, 50);
        backpackGrid.setSlotGap(8).setPreferredSlotSize(32).setSlotSizeRange(18, 46);

        backButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                openVanillaInventory();
            }
        });
    }

    /**
     * 构建背包诊断页。
     */
    private void assembleUi(Widget root) {
        DocumentToolbarWidget footer = new DocumentToolbarWidget(DOCUMENT_THEME);

        DocumentSectionWidget overviewDiv = new DocumentSectionWidget(DOCUMENT_THEME);
        overviewDiv.addChild(new DocumentTextWidget(DOCUMENT_THEME, DocumentTextWidget.Role.TITLE, "当前状态", 2));
        overviewDiv.addChild(new DocumentTextWidget(DOCUMENT_THEME, DocumentTextWidget.Role.BODY,
                "旧背包测试页已经完全清空。当前只保留单列背包诊断页，优先确认页面壳、格子网格和纵向滚动不会互相干扰，再重建复杂业务页。", 8));
        overviewDiv.addChild(overviewMetricsLabel);
        overviewCard.addChild(overviewDiv);

        DocumentSectionWidget hotbarDiv = new DocumentSectionWidget(DOCUMENT_THEME);
        hotbarDiv.addChild(new DocumentTextWidget(DOCUMENT_THEME, DocumentTextWidget.Role.TITLE, "快捷栏探针", 2));
        hotbarDiv.addChild(hotbarMetricsLabel);
        hotbarDiv.addChild(hotbarGrid);
        hotbarCard.addChild(hotbarDiv);

        DocumentSectionWidget backpackDiv = new DocumentSectionWidget(DOCUMENT_THEME);
        backpackDiv.addChild(new DocumentTextWidget(DOCUMENT_THEME, DocumentTextWidget.Role.TITLE, "主背包探针", 2));
        backpackDiv.addChild(backpackMetricsLabel);
        backpackDiv.addChild(backpackGrid);
        backpackCard.addChild(backpackDiv);

        footer.addChild(backButton);

        pagePanel.addBlock(new DocumentTextWidget(DOCUMENT_THEME, DocumentTextWidget.Role.TITLE, "背包诊断页", 2));
        pagePanel.addBlock(new DocumentTextWidget(DOCUMENT_THEME, DocumentTextWidget.Role.BODY,
                "这里不再做左右两栏或摘要联排，只验证网格控件在可靠父宽度下是否能稳定缩放、换列和滚动。", 8));
        pagePanel.addBlock(overviewCard);
        pagePanel.addBlock(hotbarCard);
        pagePanel.addBlock(backpackCard);
        pagePanel.addBlock(footer);

        root.addChild(pagePanel);
    }

    /**
     * 刷新背包诊断指标。
     */
    private void refreshMetrics() {
        InventoryPlayer inventory = getPlayerInventory();
        int hotbarUsed = inventory == null ? 0 : countOccupiedSlots(inventory, 0, 9);
        int backpackUsed = inventory == null ? 0 : countOccupiedSlots(inventory, 9, 27);

        overviewMetricsLabel.setText("窗口 " + width + "x" + height + "；页面壳 " + pagePanel.getWidth() + "x" + pagePanel.getHeight()
                + "；快捷栏卡片 " + hotbarCard.getWidth() + "x" + hotbarCard.getHeight() + "；主背包卡片 "
                + backpackCard.getWidth() + "x" + backpackCard.getHeight() + "。\n如果单列结构下背包格子仍然异常，优先检查 `InventorySlotGridWidget` 的列数和尺寸测量，而不是继续叠加页面复杂度。 ");

        hotbarMetricsLabel.setText("快捷栏占用 " + hotbarUsed + " / 9；网格尺寸 " + hotbarGrid.getWidth() + "x" + hotbarGrid.getHeight()
                + "。当前结构只验证单行网格能否在父宽度变化时稳定缩放。 ");
        backpackMetricsLabel.setText("主背包占用 " + backpackUsed + " / 27；网格尺寸 " + backpackGrid.getWidth() + "x"
                + backpackGrid.getHeight() + "。如果这里出现裁切或列数异常，再回头修 `InventorySlotGridWidget`。 ");
    }

    /**
     * 返回原版背包界面。
     */
    private void openVanillaInventory() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        if (minecraft.thePlayer == null) {
            minecraft.displayGuiScreen(null);
            return;
        }
        minecraft.displayGuiScreen(new GuiInventory(minecraft.thePlayer));
    }

    private InventoryPlayer getPlayerInventory() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return null;
        }
        return minecraft.thePlayer.inventory;
    }

    private int countOccupiedSlots(InventoryPlayer inventory, int startSlot, int slotCount) {
        int occupied = 0;
        for (int index = startSlot; index < startSlot + slotCount && index < inventory.mainInventory.length; index++) {
            if (inventory.mainInventory[index] != null && inventory.mainInventory[index].getItem() != null) {
                occupied++;
            }
        }
        return occupied;
    }

    private int clampValue(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
