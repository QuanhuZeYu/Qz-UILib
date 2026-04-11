package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.InventorySlotGridWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.ResponsiveContainerWidget;
import club.heiqi.uilib.ui.control.ResponsivePageWidget;
import club.heiqi.uilib.ui.control.ResponsivePanelWidget;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * 当前阶段的最小背包诊断页。
 */
public class InventoryOverviewScreen extends BaseScreen {

    private final ResponsivePageWidget pagePanel = new ResponsivePageWidget();

    private final ResponsivePanelWidget overviewCard = createCardPanel();
    private final ResponsivePanelWidget hotbarCard = createCardPanel();
    private final ResponsivePanelWidget backpackCard = createCardPanel();

    private final LabelWidget overviewMetricsLabel = new LabelWidget("");
    private final LabelWidget hotbarMetricsLabel = new LabelWidget("");
    private final LabelWidget backpackMetricsLabel = new LabelWidget("");

    private final InventorySlotGridWidget hotbarGrid = new InventorySlotGridWidget(0, 9, 9);
    private final InventorySlotGridWidget backpackGrid = new InventorySlotGridWidget(9, 27, 9);
    private final ButtonWidget backButton = new ButtonWidget("返回原版背包");

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
        ResponsiveContainerWidget rootWidget = (ResponsiveContainerWidget) getRootWidget();
        rootWidget.setPadding(pageMargin, topMargin, pageMargin, pageMargin);

        int pagePaddingX = clampValue(width / 48, 16, 28);
        int pagePaddingY = clampValue(height / 36, 14, 24);

        pagePanel.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
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
        pagePanel.setPadding(24, 22, 24, 22)
                .setFillColor(0xD0151C25)
                .setBorderColor(0xFF86A8F0)
                .setViewportWidthRange(720, 1040)
                .setMinViewportHeight(620)
                .setViewportRatio(0.94F, 0.92F);
    }

    /**
     * 配置背包诊断控件。
     */
    private void configureControls() {
        overviewMetricsLabel.setColor(0xFFF6D78E).setShadow(false).setWrap(true).setMaxLines(5);
        hotbarMetricsLabel.setColor(0xFFD7E3FF).setShadow(false).setWrap(true).setMaxLines(4);
        backpackMetricsLabel.setColor(0xFFD7E3FF).setShadow(false).setWrap(true).setMaxLines(4);

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
        DivWidget pageRoot = new DivWidget().setPageColumn();
        DivWidget footer = new DivWidget().setButtonFlow();

        DivWidget overviewDiv = new DivWidget().setSectionColumn();
        overviewDiv.addNoGrowChild(createSectionTitle("当前状态"));
        overviewDiv.addNoGrowChild(createBodyLabel("旧背包测试页已经完全清空。当前只保留单列背包诊断页，优先确认页面壳、格子网格和纵向滚动不会互相干扰，再重建复杂业务页。"));
        overviewDiv.addNoGrowChild(overviewMetricsLabel);
        overviewCard.addChild(overviewDiv);

        DivWidget hotbarDiv = new DivWidget().setSectionColumn();
        hotbarDiv.addNoGrowChild(createSectionTitle("快捷栏探针"));
        hotbarDiv.addNoGrowChild(hotbarMetricsLabel);
        hotbarDiv.addNoGrowChild(hotbarGrid);
        hotbarCard.addChild(hotbarDiv);

        DivWidget backpackDiv = new DivWidget().setSectionColumn();
        backpackDiv.addNoGrowChild(createSectionTitle("主背包探针"));
        backpackDiv.addNoGrowChild(backpackMetricsLabel);
        backpackDiv.addNoGrowChild(backpackGrid);
        backpackCard.addChild(backpackDiv);

        footer.addNoGrowChild(backButton);

        pageRoot.addNoGrowChild(createTitleLabel("背包诊断页"));
        pageRoot.addNoGrowChild(createBodyLabel("这里不再做左右两栏或摘要联排，只验证网格控件在可靠父宽度下是否能稳定缩放、换列和滚动。"));
        pageRoot.addNoGrowChild(overviewCard);
        pageRoot.addNoGrowChild(hotbarCard);
        pageRoot.addNoGrowChild(backpackCard);
        pageRoot.addNoGrowChild(footer);

        pagePanel.getContent().addChild(pageRoot);
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

    private ResponsivePanelWidget createCardPanel() {
        return new ResponsivePanelWidget().setPadding(20).setFillColor(0xAA111721).setBorderColor(0xFF6E8FCB);
    }

    private LabelWidget createTitleLabel(String text) {
        return new LabelWidget(text).setColor(0xFFFFFFFF).setShadow(false).setWrap(true).setMaxLines(2);
    }

    private LabelWidget createSectionTitle(String text) {
        return new LabelWidget(text).setColor(0xFFFFFFFF).setShadow(false).setWrap(true).setMaxLines(2);
    }

    private LabelWidget createBodyLabel(String text) {
        return new LabelWidget(text).setColor(0xFFD7E3FF).setShadow(false).setWrap(true).setMaxLines(8);
    }

    private int clampValue(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
