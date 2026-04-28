package club.heiqi.uilib.client;

import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;
import club.heiqi.uilib.ui.screen.InventoryOverviewModel;
import club.heiqi.uilib.ui.screen.InventoryOverviewSlotContentProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * 基于 Minecraft 客户端状态的背包诊断页模型。
 */
public class MinecraftInventoryOverviewModel implements InventoryOverviewModel {

    private static final int HOTBAR_START_SLOT = 0;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int BACKPACK_START_SLOT = 9;
    private static final int BACKPACK_SLOT_COUNT = 27;

    private final InventoryOverviewSlotContentProvider hotbarSlotProvider = new InventoryOverviewSlotContentProvider() {
        @Override
        public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
            return sampleSlotSnapshot(HOTBAR_START_SLOT, localIndex);
        }
    };

    private final InventoryOverviewSlotContentProvider backpackSlotProvider = new InventoryOverviewSlotContentProvider() {
        @Override
        public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
            return sampleSlotSnapshot(BACKPACK_START_SLOT, localIndex);
        }
    };

    @Override
    public InventoryOverviewSlotContentProvider getHotbarSlotProvider() {
        return hotbarSlotProvider;
    }

    @Override
    public InventoryOverviewSlotContentProvider getBackpackSlotProvider() {
        return backpackSlotProvider;
    }

    @Override
    public int getHotbarOccupiedCount() {
        return countOccupiedSlots(HOTBAR_START_SLOT, HOTBAR_SLOT_COUNT);
    }

    @Override
    public int getBackpackOccupiedCount() {
        return countOccupiedSlots(BACKPACK_START_SLOT, BACKPACK_SLOT_COUNT);
    }

    @Override
    public void returnToVanillaInventory() {
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

    private int countOccupiedSlots(int startSlot, int slotCount) {
        InventoryPlayer inventory = getPlayerInventory();
        if (inventory == null || inventory.mainInventory == null) {
            return 0;
        }

        int occupied = 0;
        for (int index = startSlot; index < startSlot + slotCount && index < inventory.mainInventory.length; index++) {
            if (inventory.mainInventory[index] != null && inventory.mainInventory[index].getItem() != null) {
                occupied++;
            }
        }
        return occupied;
    }

    private ItemStack getStackAt(int startSlot, int localIndex) {
        InventoryPlayer inventory = getPlayerInventory();
        int slotIndex = startSlot + localIndex;
        if (inventory == null || inventory.mainInventory == null || slotIndex < 0 || slotIndex >= inventory.mainInventory.length) {
            return null;
        }
        return inventory.mainInventory[slotIndex];
    }

    /**
     * 采样指定槽位的快照。
     *
     * @param startSlot 分区起始槽位
     * @param localIndex 分区内本地索引
     * @return 槽位快照
     */
    private InventorySlotSnapshot sampleSlotSnapshot(int startSlot, int localIndex) {
        return InventorySlotSnapshot.fromRuntimeStack(getStackAt(startSlot, localIndex));
    }

    private InventoryPlayer getPlayerInventory() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return null;
        }
        return minecraft.thePlayer.inventory;
    }
}
