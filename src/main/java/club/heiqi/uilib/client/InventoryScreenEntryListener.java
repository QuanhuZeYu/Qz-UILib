package club.heiqi.uilib.client;

import club.heiqi.uilib.ui.screen.InventoryOverviewScreen;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiScreenEvent;

/**
 * 为原版背包页注入测试入口按钮。
 */
public class InventoryScreenEntryListener {

    private static final int OPEN_INVENTORY_OVERVIEW_BUTTON_ID = 0x51495A31;
    private static final int BUTTON_WIDTH = 66;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 8;

    /**
     * 在原版背包页初始化后追加跳转按钮。
     *
     * @param event GUI 初始化事件
     */
    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onInitGuiPost(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiInventory)) {
            return;
        }
        if (containsEntryButton(event)) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int buttonX = Math.min(resolution.getScaledWidth() - BUTTON_WIDTH - BUTTON_MARGIN,
                resolution.getScaledWidth() / 2 + 100);
        int buttonY = Math.max(BUTTON_MARGIN, (resolution.getScaledHeight() - 166) / 2 + 6);
        event.buttonList.add(new GuiButton(OPEN_INVENTORY_OVERVIEW_BUTTON_ID, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                "背包UI"));
    }

    /**
     * 点击入口按钮后打开响应式背包信息页。
     *
     * @param event 按钮点击事件
     */
    @SubscribeEvent
    public void onActionPerformedPost(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (!(event.gui instanceof GuiInventory) || event.button == null
                || event.button.id != OPEN_INVENTORY_OVERVIEW_BUTTON_ID) {
            return;
        }
        Minecraft.getMinecraft().displayGuiScreen(new InventoryOverviewScreen());
    }

    private boolean containsEntryButton(GuiScreenEvent.InitGuiEvent.Post event) {
        for (Object buttonObject : event.buttonList) {
            if (!(buttonObject instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) buttonObject;
            if (button.id == OPEN_INVENTORY_OVERVIEW_BUTTON_ID) {
                return true;
            }
        }
        return false;
    }
}
