package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.InventorySlotGridWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.ResponsiveContainerWidget;
import club.heiqi.uilib.ui.control.ResponsivePageWidget;
import club.heiqi.uilib.ui.control.ResponsivePanelWidget;
import club.heiqi.uilib.ui.layout.UiAnchor;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * 基于 Div 语义 API 的背包概览页。
 */
public class InventoryOverviewScreen extends BaseScreen {

    private final ResponsivePageWidget pagePanel = new ResponsivePageWidget();
    private final LabelWidget summaryLabel = new LabelWidget("");
    private final LabelWidget hotbarTitleLabel = new LabelWidget("");
    private final LabelWidget backpackTitleLabel = new LabelWidget("");
    private final InventorySlotGridWidget hotbarGrid = new InventorySlotGridWidget(0, 9, 9);
    private final InventorySlotGridWidget backpackGrid = new InventorySlotGridWidget(9, 27, 9);
    private final ButtonWidget backButton = new ButtonWidget("返回原版背包");

    private int viewportWidthHint = 1280;
    private int viewportHeightHint = 720;

    @Override
    protected void buildUi(Widget root) {
        configurePage();
        configureControls();
        assembleUi(root);
        refreshInventoryText();
    }

    @Override
    protected void onResize(int width, int height) {
        super.onResize(width, height);
        viewportWidthHint = width;
        viewportHeightHint = height;

        int pageMargin = Math.max(24, width / 34);
        int topMargin = Math.max(28, height / 28);
        ResponsiveContainerWidget rootWidget = (ResponsiveContainerWidget) getRootWidget();
        rootWidget.setPadding(pageMargin, topMargin, pageMargin, pageMargin);

        int pagePaddingX = clampValue(viewportWidthHint / 48, 14, 28);
        int pagePaddingY = clampValue(viewportHeightHint / 36, 12, 26);
        int buttonWidth = adaptiveWidth(220, 140, 0.16F);

        pagePanel.setPadding(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        backButton.setSuggestedSize(buttonWidth, backButton.getPreferredHeight());
        refreshInventoryText();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refreshInventoryText();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void configurePage() {
        pagePanel.setPadding(24, 22, 24, 22)
                .setFillColor(0xD0151C25)
                .setBorderColor(0xFF86A8F0)
                .setSuggestedSize(760, 620)
                .setViewportRatio(0.86F, 0.90F)
                .setLayoutSpec(new UiLayoutSpec().setAnchor(UiAnchor.TOP_CENTER));
    }

    private void configureControls() {
        summaryLabel.setColor(0xFFE2ECFF).setShadow(false).setWrap(true).setMaxLines(4);
        hotbarTitleLabel.setColor(0xFFFFFFFF).setShadow(false).setWrap(true).setMaxLines(2);
        backpackTitleLabel.setColor(0xFFFFFFFF).setShadow(false).setWrap(true).setMaxLines(2);

        hotbarGrid.setSlotGap(8).setPreferredSlotSize(34).setSlotSizeRange(18, 50);
        backpackGrid.setSlotGap(8).setPreferredSlotSize(32).setSlotSizeRange(18, 46);

        backButton.setClickHandler(new Runnable() {
            @Override
            public void run() {
                openVanillaInventory();
            }
        });
    }

    private void assembleUi(Widget root) {
        DivWidget pageRoot = new DivWidget().setPageColumn();
        DivWidget cardFlow = new DivWidget().setContentFlow();
        DivWidget footer = new DivWidget().setButtonFlow();

        ResponsivePanelWidget summaryCard = createCardPanel();
        DivWidget summaryCardDiv = new DivWidget().setSectionColumn();
        summaryCard.setSuggestedSize(760, -1);
        summaryCardDiv.addNoGrowChild(createSectionTitle("背包概览"));
        summaryCardDiv.addNoGrowChild(createBodyLabel("旧背包测试页已经清空，当前只保留一张更接近真实使用场景的概览页。它和主测试页一样，完全由 Div 语义 API 组织。"));
        summaryCardDiv.addNoGrowChild(summaryLabel);
        summaryCard.addChild(summaryCardDiv);

        ResponsivePanelWidget hotbarCard = createCardPanel();
        DivWidget hotbarCardDiv = new DivWidget().setSectionColumn();
        hotbarCard.setSuggestedSize(300, -1);
        hotbarCardDiv.addNoGrowChild(hotbarTitleLabel);
        hotbarCardDiv.addNoGrowChild(createBodyLabel("快捷栏保持单行展示，格子尺寸会随可用宽度轻微缩放。"));
        hotbarCardDiv.addNoGrowChild(hotbarGrid);
        hotbarCard.addChild(hotbarCardDiv);

        ResponsivePanelWidget backpackCard = createCardPanel();
        DivWidget backpackCardDiv = new DivWidget().setSectionColumn();
        backpackCard.setSuggestedSize(420, -1);
        backpackCardDiv.addNoGrowChild(backpackTitleLabel);
        backpackCardDiv.addNoGrowChild(createBodyLabel("主背包仍然优先依赖容器收缩与换行，不再把旧测试页里那些额外说明区块堆在同一个页面中。"));
        backpackCardDiv.addNoGrowChild(backpackGrid);
        backpackCard.addChild(backpackCardDiv);

        cardFlow.addFlexChild(hotbarCard);
        cardFlow.addFlexChild(backpackCard);
        footer.addNoGrowChild(backButton);

        pageRoot.addNoGrowChild(createTitleLabel("Div 背包概览页"));
        pageRoot.addNoGrowChild(createBodyLabel("这张页面复用了主测试页同样的结构范式：页面列、卡片流、区块列和按钮流。它现在更像真实背包扩展页，而不是杂糅多种实验内容的旧测试页。"));
        pageRoot.addNoGrowChild(summaryCard);
        pageRoot.addNoGrowChild(cardFlow);
        pageRoot.addNoGrowChild(footer);

        pagePanel.getContent().addChild(pageRoot);
        root.addChild(pagePanel);
    }

    private void refreshInventoryText() {
        InventoryPlayer inventory = getPlayerInventory();
        if (inventory == null) {
            summaryLabel.setText("未找到玩家背包上下文，当前页面无法展示物品数据。\n页面结构仍然使用新的 Div 语义布局。 ");
            hotbarTitleLabel.setText("快捷栏 0 / 9");
            backpackTitleLabel.setText("主背包 0 / 27");
            return;
        }

        int hotbarUsed = countOccupiedSlots(inventory, 0, 9);
        int backpackUsed = countOccupiedSlots(inventory, 9, 27);
        summaryLabel.setText("玩家：" + Minecraft.getMinecraft().thePlayer.getCommandSenderName() + "；快捷栏占用 "
                + hotbarUsed + " / 9；主背包占用 " + backpackUsed + " / 27。\n原生窗口 " + width + "x" + height
                + "，当前页尺寸 " + pagePanel.getWidth() + "x" + pagePanel.getHeight() + "。 ");
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

    private ResponsivePanelWidget createCardPanel() {
        return new ResponsivePanelWidget().setPadding(18).setFillColor(0xAA111721).setBorderColor(0xFF7AA2FF);
    }

    private LabelWidget createTitleLabel(String text) {
        return new LabelWidget(text).setColor(0xFFFFFFFF).setShadow(false).setWrap(true).setMaxLines(2);
    }

    private LabelWidget createSectionTitle(String text) {
        return new LabelWidget(text).setColor(0xFFFFFFFF).setShadow(false).setWrap(true).setMaxLines(2);
    }

    private LabelWidget createBodyLabel(String text) {
        return new LabelWidget(text).setColor(0xFFD7E3FF).setShadow(false).setWrap(true).setMaxLines(6);
    }

    private int adaptiveWidth(int preferred, int floor, float viewportRatio) {
        return clampValue(Math.round(viewportWidthHint * viewportRatio), floor, preferred);
    }

    private int clampValue(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
