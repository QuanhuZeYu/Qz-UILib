package club.heiqi.uilib.ui.control;

import net.minecraft.item.ItemStack;

/**
 * 测试使用的空背包网格物品渲染委托。
 */
public final class NoOpInventorySlotGridItemRenderer implements InventorySlotGridItemRenderer {

    @Override
    public void renderItems(InventorySlotGridLayout layout, int absoluteX, int absoluteY, ItemStack[] slotStacks) {}
}
