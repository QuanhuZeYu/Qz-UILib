package club.heiqi.uilib.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public int getSelectedHotbarSlotIndex() {
        InventoryPlayer inventory = getPlayerInventory();
        if (inventory == null || inventory.currentItem < 0 || inventory.currentItem >= HOTBAR_SLOT_COUNT) {
            return -1;
        }
        return inventory.currentItem;
    }

    @Override
    public InventorySlotSnapshot getCarriedSlotSnapshot() {
        InventoryPlayer inventory = getPlayerInventory();
        return inventory == null ? InventorySlotSnapshot.empty()
                : InventorySlotSnapshot.fromRuntimeStack(inventory.getItemStack());
    }

    @Override
    public List<String> getSlotTooltip(boolean hotbar, int localIndex) {
        ItemStack stack = getStackAt(hotbar ? HOTBAR_START_SLOT : BACKPACK_START_SLOT, localIndex);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (stack == null || stack.getItem() == null || minecraft == null || minecraft.thePlayer == null) {
            return Collections.emptyList();
        }
        List<String> tooltipLines = new ArrayList<String>();
        List rawLines = stack.getTooltip(minecraft.thePlayer, minecraft.gameSettings.advancedItemTooltips);
        for (int lineIndex = 0; lineIndex < rawLines.size(); lineIndex++) {
            Object rawLine = rawLines.get(lineIndex);
            String line = rawLine == null ? "" : String.valueOf(rawLine);
            tooltipLines.add(line);
        }
        return tooltipLines;
    }

    @Override
    public boolean handleSlotClick(boolean hotbar, int localIndex, int button) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null || minecraft.playerController == null) {
            return false;
        }
        if (minecraft.thePlayer.inventoryContainer == null) {
            return false;
        }
        if (button != 0 && button != 1) {
            return false;
        }

        int containerSlotId = resolveContainerSlotId(hotbar, localIndex);
        if (containerSlotId < 0 && localIndex >= 0) {
            return false;
        }
        minecraft.playerController.windowClick(minecraft.thePlayer.inventoryContainer.windowId, containerSlotId,
                button, 0, minecraft.thePlayer);
        return true;
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

    private int resolveContainerSlotId(boolean hotbar, int localIndex) {
        if (localIndex < 0) {
            return -999;
        }
        if (hotbar) {
            return localIndex >= 0 && localIndex < HOTBAR_SLOT_COUNT ? 36 + localIndex : -1;
        }
        return localIndex >= 0 && localIndex < BACKPACK_SLOT_COUNT ? 9 + localIndex : -1;
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
