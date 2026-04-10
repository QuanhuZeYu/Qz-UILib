package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.InventorySlotGridWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.ResponsivePanelWidget;
import club.heiqi.uilib.ui.control.VerticalScrollPanelWidget;
import club.heiqi.uilib.ui.control.VerticalStackWidget;
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

    private final ResponsivePanelWidget pagePanel = new ResponsivePanelWidget();
    private final VerticalStackWidget pageStack = new VerticalStackWidget();
    private final LabelWidget titleLabel = new LabelWidget("响应式背包信息测试页");
    private final LabelWidget hintLabel = new LabelWidget("该页面仅展示快捷栏与主背包物品，格子会随着页面宽度自动缩放，用于验证 UI 框架接入原版背包数据的效果。");
    private final LabelWidget summaryLabel = new LabelWidget("");
    private final VerticalScrollPanelWidget contentScrollPanel = new VerticalScrollPanelWidget();
    private final ResponsivePanelWidget hotbarPanel = new ResponsivePanelWidget();
    private final VerticalStackWidget hotbarStack = new VerticalStackWidget();
    private final LabelWidget hotbarTitleLabel = new LabelWidget("");
    private final InventorySlotGridWidget hotbarGrid = new InventorySlotGridWidget(0, 9, 9);
    private final ResponsivePanelWidget backpackPanel = new ResponsivePanelWidget();
    private final VerticalStackWidget backpackStack = new VerticalStackWidget();
    private final LabelWidget backpackTitleLabel = new LabelWidget("");
    private final InventorySlotGridWidget backpackGrid = new InventorySlotGridWidget(9, 27, 9);
    private final ButtonWidget backButton = new ButtonWidget("返回原版背包");

    @Override
    protected void buildUi(Widget root) {
        configurePagePanel();
        configureStacks();
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
        pagePanel.setPadding(30, 28, 30, 28)
                .setFillColor(0xD0151C25)
                .setBorderColor(0xFF86A8F0)
                .setLayoutSpec(new UiLayoutSpec()
                        .setAnchor(UiAnchor.TOP_CENTER)
                        .setWidth(UiLength.percent(0.78F))
                        .setHeight(UiLength.percent(0.86F))
                        .setMinWidth(760)
                        .setMinHeight(560));
    }

    private void configureStacks() {
        pageStack.setSpacing(16)
                .setLayoutSpec(new UiLayoutSpec()
                        .setWidth(UiLength.percent(1.0F))
                        .setHeight(UiLength.percent(1.0F))
                        .setFill(true));
        contentScrollPanel.setPadding(12, 12, 18, 12)
                .setScrollStep(54)
                .setLayoutSpec(new UiLayoutSpec()
                        .setWidth(UiLength.percent(1.0F))
                        .setHeight(UiLength.auto())
                        .setMinHeight(260)
                        .setGrow(1.0F)
                        .setFill(true));

        hotbarStack.setSpacing(12)
                .setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        backpackStack.setSpacing(12)
                .setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true));
        contentScrollPanel.getContent().setSpacing(16);
    }

    private void configureLabels() {
        titleLabel.setColor(0xFFFFFFFF).setShadow(true);
        hintLabel.setColor(0xFFC8D8F3).setShadow(true).setWrap(true);
        summaryLabel.setColor(0xFFE2ECFF).setShadow(true).setWrap(true);
        hotbarTitleLabel.setColor(0xFFFFFFFF).setShadow(true);
        backpackTitleLabel.setColor(0xFFFFFFFF).setShadow(true);
    }

    private void configurePanels() {
        hotbarPanel.setPadding(18)
                .setFillColor(0xAA111721)
                .setBorderColor(0xFF7AA2FF);
        backpackPanel.setPadding(18)
                .setFillColor(0xAA111721)
                .setBorderColor(0xFF7AA2FF);
        hotbarGrid.setSlotGap(10).setSlotSizeRange(24, 50);
        backpackGrid.setSlotGap(10).setSlotSizeRange(24, 50);
    }

    private void configureLayout() {
        titleLabel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
        hintLabel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
        summaryLabel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
        backButton.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.auto()).setHeight(UiLength.auto()).setMinWidth(220));

        hotbarPanel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
        backpackPanel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
        hotbarTitleLabel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
        backpackTitleLabel.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
        hotbarGrid.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
        backpackGrid.setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));
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
        hotbarStack.addChild(hotbarTitleLabel);
        hotbarStack.addChild(hotbarGrid);
        hotbarPanel.addChild(hotbarStack);

        backpackStack.addChild(backpackTitleLabel);
        backpackStack.addChild(backpackGrid);
        backpackPanel.addChild(backpackStack);

        contentScrollPanel.getContent().addChild(hotbarPanel);
        contentScrollPanel.getContent().addChild(backpackPanel);

        pageStack.addChild(titleLabel);
        pageStack.addChild(hintLabel);
        pageStack.addChild(summaryLabel);
        pageStack.addChild(contentScrollPanel);
        pageStack.addChild(backButton);
        pagePanel.addChild(pageStack);
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
