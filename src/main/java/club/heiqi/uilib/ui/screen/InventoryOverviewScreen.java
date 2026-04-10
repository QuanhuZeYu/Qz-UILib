package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.InventorySlotGridWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.ResponsivePageWidget;
import club.heiqi.uilib.ui.control.ResponsivePanelWidget;
import club.heiqi.uilib.ui.layout.DivItemStyle;
import club.heiqi.uilib.ui.layout.UiAnchor;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * 响应式背包信息测试页。
 */
public class InventoryOverviewScreen extends BaseScreen {

    private final ResponsivePageWidget pagePanel = new ResponsivePageWidget();
    private final DivWidget pageDivRoot = new DivWidget().setDirection(DivWidget.Direction.COLUMN)
            .setGap(16)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final LabelWidget titleLabel = new LabelWidget("响应式背包信息测试页");
    private final LabelWidget hintLabel = new LabelWidget("该页面仅展示快捷栏与主背包物品。格子会按可用宽度自动重排列数，窗口变窄时优先缩小格子并增加换行，剩余高度交给滚动容器承接。");
    private final LabelWidget summaryLabel = new LabelWidget("");

    private final ResponsivePanelWidget hotbarPanel = new ResponsivePanelWidget();
    private final DivWidget hotbarDiv = new DivWidget().setDirection(DivWidget.Direction.COLUMN)
            .setGap(12)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final LabelWidget hotbarTitleLabel = new LabelWidget("");
    private final InventorySlotGridWidget hotbarGrid = new InventorySlotGridWidget(0, 9, 9);

    private final ResponsivePanelWidget backpackPanel = new ResponsivePanelWidget();
    private final DivWidget backpackDiv = new DivWidget().setDirection(DivWidget.Direction.COLUMN)
            .setGap(12)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final LabelWidget backpackTitleLabel = new LabelWidget("");
    private final InventorySlotGridWidget backpackGrid = new InventorySlotGridWidget(9, 27, 9);

    private final DivWidget footerDiv = new DivWidget().setDirection(DivWidget.Direction.ROW)
            .setWrap(DivWidget.Wrap.WRAP)
            .setAlignItems(DivWidget.AlignItems.CENTER)
            .setGap(12)
            .setWidthPercent(1.0F)
            .setOverflowX(DivWidget.Overflow.VISIBLE)
            .setOverflowY(DivWidget.Overflow.VISIBLE);
    private final ButtonWidget backButton = new ButtonWidget("返回原版背包");

    @Override
    protected void buildUi(Widget root) {
        configurePagePanel();
        configureDivs();
        configureLabels();
        configurePanels();
        configureLayout();
        configureActions();
        assembleUi(root);
        refreshInventoryText();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refreshInventoryText();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void configurePagePanel() {
        pagePanel.setPadding(24, 22, 24, 22)
                .setFillColor(0xD0151C25)
                .setBorderColor(0xFF86A8F0)
                .setSuggestedSize(760, 620)
                .setViewportRatio(0.86F, 0.90F)
                .setLayoutSpec(new UiLayoutSpec().setAnchor(UiAnchor.TOP_CENTER));
    }

    private void configureDivs() {
        pageDivRoot.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        hotbarDiv.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        backpackDiv.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
    }

    private void configureLabels() {
        titleLabel.setColor(0xFFFFFFFF).setShadow(true).setWrap(true);
        hintLabel.setColor(0xFFC8D8F3).setShadow(true).setWrap(true);
        summaryLabel.setColor(0xFFE2ECFF).setShadow(true).setWrap(true);
        hotbarTitleLabel.setColor(0xFFFFFFFF).setShadow(true);
        backpackTitleLabel.setColor(0xFFFFFFFF).setShadow(true);
    }

    private void configurePanels() {
        hotbarPanel.setPadding(14)
                .setFillColor(0xAA111721)
                .setBorderColor(0xFF7AA2FF);
        backpackPanel.setPadding(14)
                .setFillColor(0xAA111721)
                .setBorderColor(0xFF7AA2FF);
        hotbarGrid.setSlotGap(8).setPreferredSlotSize(34).setSlotSizeRange(18, 50);
        backpackGrid.setSlotGap(8).setPreferredSlotSize(32).setSlotSizeRange(18, 46);
    }

    private void configureLayout() {
        hotbarPanel.setLayoutSpec(null);
        backpackPanel.setLayoutSpec(null);
        titleLabel.setLayoutSpec(null);
        hintLabel.setLayoutSpec(null);
        summaryLabel.setLayoutSpec(null);
        hotbarTitleLabel.setLayoutSpec(null);
        backpackTitleLabel.setLayoutSpec(null);
        hotbarGrid.setLayoutSpec(null);
        backpackGrid.setLayoutSpec(null);
        backButton.setLayoutSpec(null);

        hotbarPanel.setSuggestedSize(-1, -1);
        backpackPanel.setSuggestedSize(-1, -1);
        backButton.setSuggestedSize(220, backButton.getPreferredHeight());
    }

    private void configureActions() {
        backButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                openVanillaInventory();
            }
        });
    }

    private void assembleUi(Widget root) {
        hotbarDiv.addChild(hotbarTitleLabel, DivItemStyle.noGrow());
        hotbarDiv.addChild(hotbarGrid, DivItemStyle.noGrow());
        hotbarPanel.addChild(hotbarDiv);

        backpackDiv.addChild(backpackTitleLabel, DivItemStyle.noGrow());
        backpackDiv.addChild(backpackGrid, DivItemStyle.noGrow());
        backpackPanel.addChild(backpackDiv);

        footerDiv.addChild(backButton, DivItemStyle.noGrow());

        pageDivRoot.addChild(titleLabel, DivItemStyle.noGrow());
        pageDivRoot.addChild(hintLabel, DivItemStyle.noGrow());
        pageDivRoot.addChild(summaryLabel, DivItemStyle.noGrow());
        pageDivRoot.addChild(hotbarPanel, DivItemStyle.noGrow());
        pageDivRoot.addChild(backpackPanel, DivItemStyle.noGrow());
        pageDivRoot.addChild(footerDiv, DivItemStyle.noGrow());

        pagePanel.getContent().addChild(pageDivRoot);
        root.addChild(pagePanel);
    }

    private void refreshInventoryText() {
        InventoryPlayer inventory = getPlayerInventory();
        if (inventory == null) {
            summaryLabel.setText("未找到玩家背包上下文，当前页面无法展示物品数据。");
            hotbarTitleLabel.setText("快捷栏 0 / 9");
            backpackTitleLabel.setText("主背包 0 / 27");
            return;
        }

        int hotbarUsed = countOccupiedSlots(inventory, 0, 9);
        int backpackUsed = countOccupiedSlots(inventory, 9, 27);
        summaryLabel.setText("玩家：" + Minecraft.getMinecraft().thePlayer.getCommandSenderName() + "；快捷栏占用 " + hotbarUsed
                + " / 9；主背包占用 " + backpackUsed + " / 27。");
        hotbarTitleLabel.setText("快捷栏物品（" + hotbarUsed + " / 9 已占用）");
        backpackTitleLabel.setText("主背包物品（" + backpackUsed + " / 27 已占用）");
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
}
