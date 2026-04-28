package club.heiqi.uilib.ui.inventory;

/**
 * 测试使用的空背包网格物品渲染委托。
 */
public final class NoOpInventorySlotGridItemRenderer implements InventorySlotGridItemRenderer {

    @Override
    public void renderItems(InventorySlotGridItemGeometry geometry, InventorySlotSnapshot[] slotSnapshots) {}
}
